package de.nicolasmahn.momentum.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompletionButtonTest {
    @Test
    fun onlyTheStrongCelebrationBursts() {
        // The button shows its burst exactly when the spec has rays, so this is the one number
        // that decides whether a Restrained completion stays quiet.
        assertEquals(
            0,
            completionFeedbackSpec(CompletionCelebrationIntensity.Restrained).burstRayCount,
        )
        assertTrue(completionFeedbackSpec(CompletionCelebrationIntensity.Strong).burstRayCount > 0)
    }
}
