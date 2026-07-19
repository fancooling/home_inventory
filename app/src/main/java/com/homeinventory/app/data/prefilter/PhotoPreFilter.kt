package com.homeinventory.app.data.prefilter

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.google.mlkit.vision.objects.defaults.PredefinedCategory
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Runs the two-stage on-device pre-filter (all local, no network) that decides whether a photo
 * is eligible to reach Gemini. See CLAUDE.md → On-Device Pre-Filter for the exact policy.
 *
 * Order is significant:
 *  1. **Person exclusion (hard block).** Face detection, then pose detection. Any hit → block,
 *     unconditionally. When in doubt here, block.
 *  2. **Object cost filter.** Only if no person was found: skip only when highly confident there
 *     is no home/fashion object. When in doubt here, send.
 *
 * Not a singleton: instantiated per scan pass and torn down via [close] in the worker's `finally`,
 * so the heavy native ML Kit models aren't held in memory between infrequent scans. Detectors are
 * lazily created so an empty/aborted pass never loads a model it won't use.
 */
class PhotoPreFilter @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val faceDetectorLazy = lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                // Loose threshold: a small/partial/background face is enough to block.
                .setMinFaceSize(MIN_FACE_SIZE)
                .build(),
        )
    }
    private val faceDetector get() = faceDetectorLazy.value

    private val poseDetectorLazy = lazy {
        PoseDetection.getClient(
            PoseDetectorOptions.Builder()
                .setDetectorMode(PoseDetectorOptions.SINGLE_IMAGE_MODE)
                .build(),
        )
    }
    private val poseDetector get() = poseDetectorLazy.value

    private val objectDetectorLazy = lazy {
        ObjectDetection.getClient(
            ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
                .enableMultipleObjects()
                .enableClassification()
                .build(),
        )
    }
    private val objectDetector get() = objectDetectorLazy.value

    /**
     * Classifies [uri]. Runs on [Dispatchers.Default]; ML Kit tasks are awaited synchronously.
     * On any decode/detection error, defaults to [PreFilterDecision.SEND] — a wasted call is
     * cheaper than a missed item (the person exclusion still gets its chance first).
     */
    suspend fun classify(uri: Uri): PreFilterDecision = withContext(Dispatchers.Default) {
        val image = try {
            InputImage.fromFilePath(context, uri)
        } catch (e: Exception) {
            Log.w(TAG, "Could not decode $uri; defaulting to SEND", e)
            return@withContext PreFilterDecision.SEND
        }

        // --- Stage 1: person exclusion (hard block) ---
        if (containsPerson(image)) {
            return@withContext PreFilterDecision.BLOCK_PERSON
        }

        // --- Stage 2: object cost filter ---
        objectDecision(image)
    }

    private fun containsPerson(image: InputImage): Boolean {
        // Face first.
        try {
            val faces = Tasks.await(faceDetector.process(image))
            if (faces.isNotEmpty()) return true
        } catch (e: Exception) {
            // When in doubt on the exclusion filter, block.
            Log.w(TAG, "Face detection failed; blocking as a precaution", e)
            return true
        }
        // Then pose (catches body parts with no visible face).
        return try {
            val pose = Tasks.await(poseDetector.process(image))
            val strongLandmarks = pose.allPoseLandmarks.count {
                it.inFrameLikelihood >= LANDMARK_CONFIDENCE
            }
            strongLandmarks >= MIN_POSE_LANDMARKS
        } catch (e: Exception) {
            Log.w(TAG, "Pose detection failed; blocking as a precaution", e)
            true
        }
    }

    /**
     * Cost filter (see CLAUDE.md → Object Classification). The bias is **when in doubt, send** —
     * a missed item costs more than a wasted scan credit, and the coarse classifier is weak:
     *
     *  - Any confident `home goods`/`fashion goods` label → [PreFilterDecision.SEND].
     *  - Confident labels exist but are *only* food/plant/place (a clear non-inventory shot like
     *    a meal or a landscape) → [PreFilterDecision.SKIP_NO_OBJECT].
     *  - No confident classification at all (unknown/unlabeled objects, or nothing detected) →
     *    ambiguous → [PreFilterDecision.SEND].
     */
    private fun objectDecision(image: InputImage): PreFilterDecision {
        return try {
            val objects = Tasks.await(objectDetector.process(image))
            val confidentCategories = objects
                .flatMap { it.labels }
                .filter { it.confidence >= OBJECT_CONFIDENCE }
                .map { it.text }
            decideFromCategories(confidentCategories)
        } catch (e: Exception) {
            Log.w(TAG, "Object detection failed; defaulting to send", e)
            PreFilterDecision.SEND
        }
    }

    /** Releases only the native detectors that were actually initialized during this pass. */
    fun close() {
        if (faceDetectorLazy.isInitialized()) faceDetector.close()
        if (poseDetectorLazy.isInitialized()) poseDetector.close()
        if (objectDetectorLazy.isInitialized()) objectDetector.close()
    }

    companion object {
        /**
         * Pure cost-filter decision from the set of confidently-detected object categories.
         * Extracted for unit testing (see CLAUDE.md → Object Classification). Bias: when in
         * doubt, send.
         */
        fun decideFromCategories(confidentCategories: List<String>): PreFilterDecision = when {
            confidentCategories.any {
                it == PredefinedCategory.HOME_GOOD || it == PredefinedCategory.FASHION_GOOD
            } -> PreFilterDecision.SEND
            // Confidently classified, but only non-inventory categories (food/plant/place) → skip.
            confidentCategories.isNotEmpty() -> PreFilterDecision.SKIP_NO_OBJECT
            // No confident classification → ambiguous → favor sending.
            else -> PreFilterDecision.SEND
        }

        private const val TAG = "PhotoPreFilter"
        private const val MIN_FACE_SIZE = 0.05f
        private const val LANDMARK_CONFIDENCE = 0.5f
        private const val MIN_POSE_LANDMARKS = 5
        const val OBJECT_CONFIDENCE = 0.3f
    }
}
