package com.example.workouttracker.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class ChecklistDestinationIntentTest {
    /**
     * Monday evening's notification about Sunday can still be on show when Tuesday morning's about
     * Monday posts: each keeps the day it asked about, and each checklist its own.
     */
    @Test
    fun aNewerNotificationNeverRewritesTheDayAnOlderOneOpensOn() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val older = checklistDestinationPendingIntent(context, "chk_night", "2026-10-11")
        val newer = checklistDestinationPendingIntent(context, "chk_night", "2026-10-12")
        val other = checklistDestinationPendingIntent(context, "chk_other", "2026-10-11")

        assertEquals(
            AppNavigationRequest(AppDestination.Checklist, checklistId = "chk_night", checklistDateKey = "2026-10-11"),
            shadowOf(older).savedIntent.requestedAppNavigation(),
        )
        assertEquals(
            AppNavigationRequest(AppDestination.Checklist, checklistId = "chk_night", checklistDateKey = "2026-10-12"),
            shadowOf(newer).savedIntent.requestedAppNavigation(),
        )
        assertEquals(
            AppNavigationRequest(AppDestination.Checklist, checklistId = "chk_other", checklistDateKey = "2026-10-11"),
            shadowOf(other).savedIntent.requestedAppNavigation(),
        )
    }
}
