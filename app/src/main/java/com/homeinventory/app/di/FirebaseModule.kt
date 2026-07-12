package com.homeinventory.app.di

import com.google.firebase.Firebase
import com.google.firebase.vertexai.GenerativeModel
import com.google.firebase.vertexai.vertexAI
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Provides the Firebase AI Logic (Gemini relay) model.
 *
 * Firebase AI Logic keeps the Gemini credentials on Google's managed backend — no raw API key
 * ships in the client (see CLAUDE.md → Gemini Integration). App Check (installed in
 * [com.homeinventory.app.HomeInventoryApp]) attests each call.
 *
 * Phase 1 only configures the model for `gemini-2.0-flash`; prompt/photo handling arrives in
 * Phase 3. This provider requires a configured Firebase project — it will throw at first use if
 * `google-services.json` is missing, which is the intended signal during development.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GeminiModel

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    private const val GEMINI_MODEL_NAME = "gemini-2.0-flash"

    @Provides
    @Singleton
    @GeminiModel
    fun provideGeminiModel(): GenerativeModel =
        Firebase.vertexAI.generativeModel(modelName = GEMINI_MODEL_NAME)
}
