package de.nicolasmahn.momentum.app

import de.nicolasmahn.momentum.domain.TrackerRef
import de.nicolasmahn.momentum.domain.TrackerTypeId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppDestinationTest {
    @Test
    fun activityActionOpensGeneralLogActivityWithoutSelectingGym() {
        assertEquals(
            AppNavigationRequest(destination = AppDestination.Activity),
            requestedAppNavigation(ActionOpenActivity),
        )
        assertNotEquals(ActivityDestinationRequestCode, WorkoutDestinationRequestCode)
    }

    @Test
    fun unrelatedActionsDoNotNavigate() {
        assertNull(requestedAppDestination(null))
        assertNull(requestedAppDestination("unexpected"))
        // Actions of screens the app no longer has lead nowhere.
        listOf("OPEN_WEIGHT", "OPEN_EATING", "OPEN_READING", "OPEN_SUPPLEMENTS", "OPEN_CARE").forEach { name ->
            assertNull(requestedAppDestination("de.nicolasmahn.momentum.action.$name"))
        }
    }

    /** A time view's start action opens that view and starts its session, Reading's as any other's. */
    @Test
    fun startActionOpensTheTimeViewAndStartsItsSession() {
        assertEquals(
            AppNavigationRequest(AppDestination.Time, loggedViewId = "tim_k2p9", startSession = true),
            requestedAppNavigation(ActionStartTime, timeId = "tim_k2p9"),
        )
        assertEquals(AppNavigationRequest(AppDestination.Time, loggedViewId = "reading", startSession = true), requestedAppNavigation(ActionStartTime, timeId = "reading"))
        assertNull(requestedAppNavigation(ActionStartTime))
    }

    @Test
    fun genericTrackerActionCarriesItsKindInstanceAndRequestedDay() {
        val tracker = TrackerRef(TrackerTypeId("counter"), "water:daily:🫗")

        assertEquals(
            AppNavigationRequest(AppDestination.Tracker, tracker = tracker, loggedDateKey = "2026-09-15"),
            requestedAppNavigation(
                ActionOpenTracker,
                trackerType = tracker.type.value,
                trackerId = tracker.id,
                trackerDateKey = "2026-09-15",
            ),
        )
        assertEquals(
            AppNavigationRequest(AppDestination.Tracker, tracker = tracker),
            requestedAppNavigation(
                ActionOpenTracker,
                trackerType = tracker.type.value,
                trackerId = tracker.id,
                trackerDateKey = "2026-02-30",
            ),
        )
    }

    /** The reminder's tap carries the activity it planned; one without an ID opens the screen as it is. */
    @Test
    fun workoutActionRequestsThePlannedActivityInsideActivity() {
        assertEquals(
            AppNavigationRequest(
                destination = AppDestination.Activity,
                activityTarget = ActivityLaunchTarget.Activity("bouldering"),
            ),
            requestedAppNavigation(ActionOpenWorkout, activityId = "bouldering"),
        )
        assertEquals(AppNavigationRequest(destination = AppDestination.Activity), requestedAppNavigation(ActionOpenWorkout))
        assertEquals(AppDestination.Activity, requestedAppDestination(ActionOpenWorkout))
    }

    @Test
    fun homeActionLeavesAnOpenFeatureScreenForHome() {
        assertEquals(
            AppNavigationRequest(destination = AppDestination.Home),
            requestedAppNavigation(ActionOpenHome),
        )
        val others = listOf(
            ActivityDestinationRequestCode,
            WorkoutDestinationRequestCode,
            ChecklistDestinationRequestCode,
            MeasurementDestinationRequestCode,
            QuestionsDestinationRequestCode,
            TimeDestinationRequestCode,
            StartTimeRequestCode,
        )
        assertEquals(false, HomeDestinationRequestCode in others)
    }

    /** Eating's and Weight's reminders open their kind's screen on the day they asked about, as any view's of their kind do; a date that is none is dropped. */
    @Test
    fun weightsAndEatingsRemindersOpenTheirKindsScreenOnTheDayTheyAskedAbout() {
        assertEquals(
            AppNavigationRequest(AppDestination.Questions, loggedViewId = "eating", loggedDateKey = "2026-09-15"),
            requestedAppNavigation(ActionOpenQuestions, questionsId = "eating", questionsDateKey = "2026-09-15"),
        )
        assertEquals(
            AppNavigationRequest(AppDestination.Measurement, loggedViewId = "weight", loggedDateKey = "2026-09-15"),
            requestedAppNavigation(ActionOpenMeasurement, measurementId = "weight", measurementDateKey = "2026-09-15"),
        )
        assertEquals(
            AppNavigationRequest(AppDestination.Questions, loggedViewId = "eating"),
            requestedAppNavigation(ActionOpenQuestions, questionsId = "eating", questionsDateKey = "2026-02-30"),
        )
    }

    /** A checklist's reminder opens the checklist on the day it asked about, Supplements' as any other's. */
    @Test
    fun checklistNavigationCarriesTheDayItsReminderAskedAbout() {
        assertEquals(
            AppNavigationRequest(destination = AppDestination.Checklist, checklistId = "chk_7f3a", checklistDateKey = "2026-09-15"),
            requestedAppNavigation(ActionOpenChecklist, checklistId = "chk_7f3a", checklistDateKey = "2026-09-15"),
        )
        assertEquals(
            AppNavigationRequest(destination = AppDestination.Checklist, checklistId = "chk_7f3a"),
            requestedAppNavigation(ActionOpenChecklist, checklistId = "chk_7f3a", checklistDateKey = "2026-02-30"),
        )
        assertEquals(
            AppNavigationRequest(destination = AppDestination.Checklist, checklistId = "supplements", checklistDateKey = "2026-09-15"),
            requestedAppNavigation(ActionOpenChecklist, checklistId = "supplements", checklistDateKey = "2026-09-15"),
        )
        assertNull(requestedAppNavigation(ActionOpenChecklist, checklistDateKey = "2026-09-15"))
    }

}
