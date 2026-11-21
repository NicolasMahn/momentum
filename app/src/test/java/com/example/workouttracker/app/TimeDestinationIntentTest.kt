package com.example.workouttracker.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.workouttracker.domain.LoggedViewKind
import com.example.workouttracker.domain.LoggedViewRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** A tap on a time view's reminder opens that view, by an intent no other kind's can be. */
@RunWith(RobolectricTestRunner::class)
class TimeDestinationIntentTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun intent(kind: LoggedViewKind, id: String, dateKey: String) = loggedViewDestinationPendingIntent(context, LoggedViewRef(kind, id), dateKey)

    /**
     * The view and the day its reminder rang on are both part of what tells two intents apart, and
     * so is the kind: a notification of another view, or of a measurement or a questions view
     * under the same ID, never rewrites where this one leads.
     */
    @Test
    fun theIntentCarriesTheViewAndTheDayAndBothAreItsIdentity() {
        val yesterdays = intent(LoggedViewKind.Time, "tim_k2p9", "2026-10-13")
        val todays = intent(LoggedViewKind.Time, "tim_k2p9", "2026-10-14")
        val others = intent(LoggedViewKind.Time, "tim_m7xd", "2026-10-13")
        val measurements = intent(LoggedViewKind.Measurement, "tim_k2p9", "2026-10-13")
        val questions = intent(LoggedViewKind.Questions, "tim_k2p9", "2026-10-13")

        assertEquals(
            AppNavigationRequest(AppDestination.Time, loggedViewId = "tim_k2p9", loggedDateKey = "2026-10-13"),
            shadowOf(yesterdays).savedIntent.requestedAppNavigation(),
        )
        assertEquals(
            AppNavigationRequest(AppDestination.Measurement, loggedViewId = "tim_k2p9", loggedDateKey = "2026-10-13"),
            shadowOf(measurements).savedIntent.requestedAppNavigation(),
        )
        assertEquals("momentum://time/tim_k2p9/2026-10-13", shadowOf(yesterdays).savedIntent.dataString)
        assertEquals(ActionOpenTime, shadowOf(yesterdays).savedIntent.action)
        listOf(todays, others, measurements, questions).forEach { other ->
            assertNotEquals(yesterdays, other)
            assertFalse(shadowOf(yesterdays).savedIntent.filterEquals(shadowOf(other).savedIntent))
        }
        assertEquals(yesterdays, intent(LoggedViewKind.Time, "tim_k2p9", "2026-10-13"))
    }

    /** What an intent cannot say is left out, never guessed: no view opens nothing, and Reading opens where it lives. */
    @Test
    fun aRequestThatNamesNoViewOrNoDayFallsBack() {
        assertNull(requestedAppNavigation(ActionOpenTime, timeDateKey = "2026-10-13"))
        assertEquals(
            AppNavigationRequest(AppDestination.Time, loggedViewId = "tim_k2p9"),
            requestedAppNavigation(ActionOpenTime, timeId = "tim_k2p9", timeDateKey = "2026-02-30"),
        )
        assertEquals(AppNavigationRequest(AppDestination.Time, loggedViewId = "reading", loggedDateKey = "2026-10-13"), requestedAppNavigation(ActionOpenTime, timeId = "reading", timeDateKey = "2026-10-13"))
        // Each kind's request reads its own extras alone.
        assertEquals(
            AppNavigationRequest(AppDestination.Questions, loggedViewId = "qst_k2p9", loggedDateKey = "2026-10-12"),
            requestedAppNavigation(ActionOpenQuestions, questionsId = "qst_k2p9", questionsDateKey = "2026-10-12", timeId = "tim_k2p9", timeDateKey = "2026-10-13"),
        )
        assertNull(requestedAppNavigation(ActionOpenTime, measurementId = "msr_k2p9", questionsId = "qst_k2p9"))
    }
}
