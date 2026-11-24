package de.nicolasmahn.momentum.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import de.nicolasmahn.momentum.domain.LoggedViewKind
import de.nicolasmahn.momentum.domain.LoggedViewRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** A tap on a questions view's reminder opens that view on the day it asked about, by an intent no other kind's can be. */
@RunWith(RobolectricTestRunner::class)
class QuestionsDestinationIntentTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun intent(kind: LoggedViewKind, id: String, dateKey: String) = loggedViewDestinationPendingIntent(context, LoggedViewRef(kind, id), dateKey)

    /**
     * The view and the day are both part of what tells two intents apart, and so is the kind: a
     * notification about another day, of another view, or of a measurement or a checklist under
     * the same ID never rewrites where this one leads.
     */
    @Test
    fun theIntentCarriesTheViewAndTheDayAndBothAreItsIdentity() {
        val yesterdays = intent(LoggedViewKind.Questions, "qst_k2p9", "2026-10-13")
        val todays = intent(LoggedViewKind.Questions, "qst_k2p9", "2026-10-14")
        val others = intent(LoggedViewKind.Questions, "qst_m00d", "2026-10-13")
        val measurements = intent(LoggedViewKind.Measurement, "qst_k2p9", "2026-10-13")
        val checklists = checklistDestinationPendingIntent(context, "qst_k2p9", "2026-10-13")

        assertEquals(
            AppNavigationRequest(AppDestination.Questions, loggedViewId = "qst_k2p9", loggedDateKey = "2026-10-13"),
            shadowOf(yesterdays).savedIntent.requestedAppNavigation(),
        )
        assertEquals(
            AppNavigationRequest(AppDestination.Questions, loggedViewId = "qst_m00d", loggedDateKey = "2026-10-13"),
            shadowOf(others).savedIntent.requestedAppNavigation(),
        )
        assertEquals(
            AppNavigationRequest(AppDestination.Measurement, loggedViewId = "qst_k2p9", loggedDateKey = "2026-10-13"),
            shadowOf(measurements).savedIntent.requestedAppNavigation(),
        )
        assertEquals("momentum://questions/qst_k2p9/2026-10-13", shadowOf(yesterdays).savedIntent.dataString)
        assertEquals(ActionOpenQuestions, shadowOf(yesterdays).savedIntent.action)
        listOf(todays, others, measurements, checklists).forEach { other ->
            assertNotEquals(yesterdays, other)
            assertFalse(shadowOf(yesterdays).savedIntent.filterEquals(shadowOf(other).savedIntent))
        }
        assertEquals(yesterdays, intent(LoggedViewKind.Questions, "qst_k2p9", "2026-10-13"))
    }

    /** What an intent cannot say is left out, never guessed: no view opens nothing, and a day that is no day opens the view's own day. */
    @Test
    fun aRequestThatNamesNoViewOrNoDayFallsBack() {
        assertNull(requestedAppNavigation(ActionOpenQuestions, questionsDateKey = "2026-10-13"))
        assertEquals(
            AppNavigationRequest(AppDestination.Questions, loggedViewId = "qst_k2p9"),
            requestedAppNavigation(ActionOpenQuestions, questionsId = "qst_k2p9", questionsDateKey = "2026-02-30"),
        )
        // Eating opens as any questions view does, on the day its reminder asked about, and each kind's request reads its own extras alone.
        assertEquals(AppNavigationRequest(AppDestination.Questions, loggedViewId = "eating", loggedDateKey = "2026-10-13"), requestedAppNavigation(ActionOpenQuestions, questionsId = "eating", questionsDateKey = "2026-10-13"))
        assertEquals(
            AppNavigationRequest(AppDestination.Measurement, loggedViewId = "msr_k2p9", loggedDateKey = "2026-10-12"),
            requestedAppNavigation(ActionOpenMeasurement, measurementId = "msr_k2p9", measurementDateKey = "2026-10-12", questionsId = "qst_k2p9", questionsDateKey = "2026-10-13"),
        )
        assertNull(requestedAppNavigation(ActionOpenQuestions, measurementId = "msr_k2p9", measurementDateKey = "2026-10-13"))
    }
}
