package de.nicolasmahn.momentum.app

import de.nicolasmahn.momentum.domain.LoggedViewKind
import de.nicolasmahn.momentum.domain.LoggedViewRef
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** A tap on a measurement's reminder opens that measurement on the day it asked about. */
@RunWith(RobolectricTestRunner::class)
class MeasurementDestinationIntentTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    /**
     * The measurement and the day are both part of what tells two intents apart: a notification
     * about another day, of another measurement, or of a checklist under the same ID never
     * rewrites where this one leads.
     */
    @Test
    fun theIntentCarriesTheMeasurementAndTheDayAndBothAreItsIdentity() {
        val yesterdays = loggedViewDestinationPendingIntent(context, LoggedViewRef(LoggedViewKind.Measurement, "msr_k2p9"), "2026-10-13")
        val todays = loggedViewDestinationPendingIntent(context, LoggedViewRef(LoggedViewKind.Measurement, "msr_k2p9"), "2026-10-14")
        val others = loggedViewDestinationPendingIntent(context, LoggedViewRef(LoggedViewKind.Measurement, "msr_m7xd"), "2026-10-13")
        val checklists = checklistDestinationPendingIntent(context, "msr_k2p9", "2026-10-13")

        assertEquals(
            AppNavigationRequest(AppDestination.Measurement, loggedViewId = "msr_k2p9", loggedDateKey = "2026-10-13"),
            shadowOf(yesterdays).savedIntent.requestedAppNavigation(),
        )
        assertEquals(
            AppNavigationRequest(AppDestination.Measurement, loggedViewId = "msr_k2p9", loggedDateKey = "2026-10-14"),
            shadowOf(todays).savedIntent.requestedAppNavigation(),
        )
        assertEquals(
            AppNavigationRequest(AppDestination.Measurement, loggedViewId = "msr_m7xd", loggedDateKey = "2026-10-13"),
            shadowOf(others).savedIntent.requestedAppNavigation(),
        )
        assertEquals(
            AppNavigationRequest(AppDestination.Checklist, checklistId = "msr_k2p9", checklistDateKey = "2026-10-13"),
            shadowOf(checklists).savedIntent.requestedAppNavigation(),
        )
        assertEquals("momentum://measurement/msr_k2p9/2026-10-13", shadowOf(yesterdays).savedIntent.dataString)
        listOf(todays, others, checklists).forEach { other ->
            assertNotEquals(yesterdays, other)
            assertFalse(shadowOf(yesterdays).savedIntent.filterEquals(shadowOf(other).savedIntent))
        }
        assertEquals(yesterdays, loggedViewDestinationPendingIntent(context, LoggedViewRef(LoggedViewKind.Measurement, "msr_k2p9"), "2026-10-13"))
    }

    /** What an intent cannot say is left out, never guessed: no measurement opens nothing, and a day that is no day opens today. */
    @Test
    fun aRequestThatNamesNoMeasurementOrNoDayFallsBack() {
        assertNull(requestedAppNavigation(ActionOpenMeasurement, measurementDateKey = "2026-10-13"))
        assertEquals(
            AppNavigationRequest(AppDestination.Measurement, loggedViewId = "msr_k2p9"),
            requestedAppNavigation(ActionOpenMeasurement, measurementId = "msr_k2p9", measurementDateKey = "2026-02-30"),
        )
        assertEquals(
            AppNavigationRequest(AppDestination.Measurement, loggedViewId = "msr_k2p9"),
            requestedAppNavigation(ActionOpenMeasurement, measurementId = "msr_k2p9"),
        )
        // Weight opens as any measurement does, on the day named; a checklist's request reads no measurement.
        assertEquals(
            AppNavigationRequest(AppDestination.Measurement, loggedViewId = "weight", loggedDateKey = "2026-10-13"),
            requestedAppNavigation(ActionOpenMeasurement, measurementId = "weight", measurementDateKey = "2026-10-13"),
        )
        assertEquals(
            AppNavigationRequest(AppDestination.Checklist, checklistId = "chk_7f3a"),
            requestedAppNavigation(ActionOpenChecklist, checklistId = "chk_7f3a", measurementId = "msr_k2p9", measurementDateKey = "2026-10-13"),
        )
    }
}
