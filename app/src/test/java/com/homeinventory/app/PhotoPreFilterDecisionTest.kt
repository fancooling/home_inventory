package com.homeinventory.app

import com.google.mlkit.vision.objects.defaults.PredefinedCategory
import com.homeinventory.app.data.prefilter.PhotoPreFilter
import com.homeinventory.app.data.prefilter.PreFilterDecision
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression tests for the cost-filter policy (CLAUDE.md → Object Classification).
 * Bias: when in doubt, send — only skip when confidently non-inventory.
 */
class PhotoPreFilterDecisionTest {

    @Test
    fun homeGoodIsSent() {
        assertEquals(
            PreFilterDecision.SEND,
            PhotoPreFilter.decideFromCategories(listOf(PredefinedCategory.HOME_GOOD)),
        )
    }

    @Test
    fun fashionGoodIsSent() {
        assertEquals(
            PreFilterDecision.SEND,
            PhotoPreFilter.decideFromCategories(listOf(PredefinedCategory.FASHION_GOOD)),
        )
    }

    @Test
    fun homeGoodAmongOthersIsSent() {
        assertEquals(
            PreFilterDecision.SEND,
            PhotoPreFilter.decideFromCategories(
                listOf(PredefinedCategory.PLANT, PredefinedCategory.HOME_GOOD),
            ),
        )
    }

    @Test
    fun confidentlyOnlyFoodOrPlantIsSkipped() {
        assertEquals(
            PreFilterDecision.SKIP_NO_OBJECT,
            PhotoPreFilter.decideFromCategories(
                listOf(PredefinedCategory.FOOD, PredefinedCategory.PLANT),
            ),
        )
    }

    @Test
    fun noConfidentCategoryIsSent() {
        // The critical fix: unknown/unlabeled objects must NOT be skipped.
        assertEquals(
            PreFilterDecision.SEND,
            PhotoPreFilter.decideFromCategories(emptyList()),
        )
    }
}
