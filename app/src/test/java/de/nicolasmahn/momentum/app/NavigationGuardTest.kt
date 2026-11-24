package de.nicolasmahn.momentum.app

import androidx.compose.runtime.saveable.SaverScope
import de.nicolasmahn.momentum.domain.TrackerRef
import de.nicolasmahn.momentum.domain.TrackerTypeId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A way away from a page with unsaved work waits for the person's answer, across process death too. */
class NavigationGuardTest {
    private val page = NavigationTarget(AppDestination.SettingsPage, pageKey = "checklist:chk_plants")
    private val weight = NavigationTarget(AppDestination.Measurement, loggedViewId = "weight")
    private val waist = NavigationTarget(AppDestination.Measurement, loggedViewId = "msr_k2p9")
    private val mood = NavigationTarget(AppDestination.Measurement, loggedViewId = "msr_m7xd")

    @Test
    fun aWayAwayWaitsOnlyWhileThePageHoldsUnsavedWork() {
        val guard = NavigationGuard()
        assertFalse(guard.holds(weight, page))

        guard.unsavedWork = true
        assertTrue(guard.holds(weight, page))
        assertEquals(weight, guard.pending)
        assertNull(guard.released())

        // The same place again is not a way away, whatever happens on arrival.
        assertFalse(guard.holds(page.copy(startSession = true, activityTarget = ActivityLaunchTarget.Activity("gym"), loggedDateKey = "2026-10-14", checklistDateKey = "2026-10-13"), page))
    }

    /** What a reminder asks for on arrival travels with the way: kept while it waits, forgotten with it. */
    @Test
    fun whatHappensOnArrivalTravelsWithTheWay() {
        val eating = NavigationTarget(AppDestination.Questions, loggedViewId = "eating", loggedDateKey = "2026-10-14")
        val bouldering = NavigationTarget(AppDestination.Activity, activityTarget = ActivityLaunchTarget.Activity("bouldering"))
        val guard = NavigationGuard(unsavedWork = true)

        guard.holds(eating, page)
        val saved = with(NavigationGuard.Saver) { SaverScope { true }.save(guard) }
        assertEquals(eating, requireNotNull(NavigationGuard.Saver.restore(requireNotNull(saved))).pending)
        assertNull(guard.answered(discard = false))

        // The activity a workout reminder named waits behind the question with its ID, across process death too.
        guard.holds(bouldering, page)
        val savedActivity = requireNotNull(with(NavigationGuard.Saver) { SaverScope { true }.save(guard) }) as List<*>
        assertEquals("activity:bouldering", savedActivity[5])
        assertEquals(bouldering, requireNotNull(NavigationGuard.Saver.restore(savedActivity)).pending)
        assertEquals(bouldering, guard.answered(discard = true))

        // The day a checklist's reminder asked about travels the same way.
        val lastNight = NavigationTarget(AppDestination.Checklist, checklistId = "chk_night", checklistDateKey = "2026-10-13")
        guard.unsavedWork = true
        guard.holds(lastNight, page)
        val savedChecklist = with(NavigationGuard.Saver) { SaverScope { true }.save(guard) }
        assertEquals(lastNight, requireNotNull(NavigationGuard.Saver.restore(requireNotNull(savedChecklist))).pending)
        assertEquals(lastNight, guard.answered(discard = true))

        // And the day Substances' check-in asked about.
        val yesterday = NavigationTarget(AppDestination.Substances, substanceDateKey = "2026-10-13")
        guard.unsavedWork = true
        guard.holds(yesterday, page)
        val savedSubstances = with(NavigationGuard.Saver) { SaverScope { true }.save(guard) }
        assertEquals(yesterday, requireNotNull(NavigationGuard.Saver.restore(requireNotNull(savedSubstances))).pending)
        assertEquals(yesterday, guard.answered(discard = true))
    }

    /** The measurement's ID is part of what a place is, as a checklist's is: two made measurements are two places. */
    @Test
    fun twoMadeMeasurementsAreTwoPlaces() {
        assertTrue(waist.isAt(waist.copy(startSession = true)))
        assertFalse(waist.isAt(mood))
        assertFalse(waist.isAt(NavigationTarget(AppDestination.Measurement)))
        assertFalse(waist.isAt(NavigationTarget(AppDestination.Checklist, checklistId = "msr_k2p9")))

        // So a way from one measurement's page to the other's waits, and to its own does not.
        val waistPage = NavigationTarget(AppDestination.SettingsPage, pageKey = "measurement:msr_k2p9")
        val guard = NavigationGuard(unsavedWork = true)
        assertFalse(guard.holds(waistPage.copy(checklistDateKey = "2026-10-13"), waistPage))
        assertTrue(guard.holds(waistPage.copy(pageKey = "measurement:msr_m7xd"), waistPage))
        assertTrue(guard.holds(mood, waist))
        assertEquals(mood, guard.pending)
    }

    /** A way to a measurement that waits behind the question keeps which measurement it leads to across process death. */
    @Test
    fun aWaitingWayToAMeasurementKeepsItsIdAcrossProcessDeath() {
        val guard = NavigationGuard(unsavedWork = true)
        guard.holds(waist, page)

        val saved = requireNotNull(with(NavigationGuard.Saver) { SaverScope { true }.save(guard) })
        val restored = requireNotNull(NavigationGuard.Saver.restore(saved))

        assertEquals(waist, restored.pending)
        assertEquals(waist, restored.answered(discard = true))

        // A guard saved before measurements could be opened is restored as it was, with its way.
        val activity = NavigationTarget(AppDestination.Activity)
        guard.holds(activity, page)
        val older = (requireNotNull(with(NavigationGuard.Saver) { SaverScope { true }.save(guard) }) as List<*>).dropLast(2)
        assertEquals(activity, requireNotNull(NavigationGuard.Saver.restore(older)).pending)
    }

    /** The day a measurement's reminder asked about travels with the way, as a checklist's does: kept while it waits, across process death too. */
    @Test
    fun theDayAMeasurementsReminderAskedAboutTravelsWithTheWay() {
        val yesterdays = waist.copy(loggedDateKey = "2026-10-13")
        assertTrue(yesterdays.isAt(waist))
        val guard = NavigationGuard(unsavedWork = true)
        assertTrue(guard.holds(yesterdays, page))

        val saved = requireNotNull(with(NavigationGuard.Saver) { SaverScope { true }.save(guard) })
        assertEquals(yesterdays, requireNotNull(NavigationGuard.Saver.restore(saved)).pending)
        assertNull(guard.answered(discard = false))
        guard.holds(yesterdays, page)
        assertEquals(yesterdays, guard.answered(discard = true))
    }

    /**
     * A questions view's ID and day travel in the fields a measurement's do, and the destination
     * says which kind it is: under one ID the two are two places, each kept across process death.
     */
    @Test
    fun aQuestionsViewAndAMeasurementUnderOneIdAreTwoPlaces() {
        val measured = NavigationTarget(AppDestination.Measurement, loggedViewId = "same", loggedDateKey = "2026-10-13")
        val asked = NavigationTarget(AppDestination.Questions, loggedViewId = "same", loggedDateKey = "2026-10-13")
        assertFalse(asked.isAt(measured))
        assertTrue(asked.isAt(asked.copy(loggedDateKey = null)))

        val guard = NavigationGuard(unsavedWork = true)
        assertTrue(guard.holds(asked, measured))
        val saved = requireNotNull(with(NavigationGuard.Saver) { SaverScope { true }.save(guard) })
        assertEquals(asked, requireNotNull(NavigationGuard.Saver.restore(saved)).pending)
    }

    /** A time view's ID travels in the same field, and its destination makes it a place of its own, kept across process death too. */
    @Test
    fun aTimeViewUnderAnIdAnotherKindHasIsAPlaceOfItsOwn() {
        val asked = NavigationTarget(AppDestination.Questions, loggedViewId = "same")
        val timed = NavigationTarget(AppDestination.Time, loggedViewId = "same", loggedDateKey = "2026-10-13")
        assertFalse(timed.isAt(asked))
        assertFalse(timed.isAt(NavigationTarget(AppDestination.Time, loggedViewId = "reading")))
        assertTrue(timed.isAt(NavigationTarget(AppDestination.Time, loggedViewId = "same")))

        val guard = NavigationGuard(unsavedWork = true)
        assertTrue(guard.holds(timed, asked))
        val saved = requireNotNull(with(NavigationGuard.Saver) { SaverScope { true }.save(guard) })
        assertEquals(timed, requireNotNull(NavigationGuard.Saver.restore(saved)).pending)
    }

    @Test
    fun aLegacyWaitingWayKeepsItsSavedSlotsAndNormalizesAtTheRouteBoundary() {
        val legacy = NavigationTarget(
            AppDestination.Checklist,
            checklistId = "chk_night",
            checklistDateKey = "2026-10-13",
        )
        val guard = NavigationGuard(unsavedWork = true)

        assertTrue(guard.holds(legacy, page))
        val saved = requireNotNull(with(NavigationGuard.Saver) { SaverScope { true }.save(guard) }) as List<*>
        assertEquals(
            listOf(true, "Checklist", "chk_night", "", false, "", "", "2026-10-13", "", "", ""),
            saved,
        )

        val restored = requireNotNull(NavigationGuard.Saver.restore(saved))
        assertEquals(legacy, restored.pending)
        assertEquals(
            NavigationTarget(
                AppDestination.Tracker,
                tracker = TrackerRef(TrackerTypeId("checklist"), "chk_night"),
                loggedDateKey = "2026-10-13",
            ),
            restored.pending!!.normalized(),
        )
    }

    /**
     * Saved state outlives an update of the app. These are a waiting way as the version before this
     * block saved it, ten values, and as the one before measurements could be opened did, nine:
     * each comes back with everything it held.
     */
    @Test
    fun aGuardSavedByAnEarlierVersionIsRestoredAsItWas() {
        val savedBeforeAReminderCouldOpenADay = listOf(true, "Measurement", "", "", false, "", "", "", "", "msr_k2p9")
        val restored = requireNotNull(NavigationGuard.Saver.restore(savedBeforeAReminderCouldOpenADay))
        assertTrue(restored.unsavedWork)
        assertEquals(waist, restored.pending)

        val savedBeforeMeasurementsCouldBeOpened = listOf(true, "Checklist", "chk_night", "", false, "", "", "2026-10-13", "")
        assertEquals(
            NavigationTarget(AppDestination.Checklist, checklistId = "chk_night", checklistDateKey = "2026-10-13"),
            requireNotNull(NavigationGuard.Saver.restore(savedBeforeMeasurementsCouldBeOpened)).pending,
        )
        assertNull(requireNotNull(NavigationGuard.Saver.restore(listOf(false, "", "", "", false, "", "", "", "", ""))).pending)
    }

    @Test
    fun removedScreenNamesDoNotSelectAnOriginalTracker() {
        listOf("Weight", "Eating", "Reading", "Supplements", "Care").forEach { name ->
            val saved = listOf(true, name, "", "", false, "", "", "", "", "", "")
            assertNull(requireNotNull(NavigationGuard.Saver.restore(saved)).pending)
            assertEquals(NavigationTarget(AppDestination.Home), NavigationTarget(AppDestination.Home).atSavedScreen(name))
        }
    }

    @Test
    fun aRestoredSelectedScreenUsesTheGenericTrackerRouteBoundary() {
        val ref = TrackerRef(TrackerTypeId("measurement"), "weight")
        assertEquals(
            NavigationTarget(AppDestination.Tracker, tracker = ref),
            NavigationTarget(AppDestination.Home, loggedViewId = ref.id).atSavedScreen("Measurement"),
        )
        assertEquals(
            NavigationTarget(AppDestination.Home),
            NavigationTarget(AppDestination.SettingsPage, pageKey = "retired:screen").atSavedScreen("Retired"),
        )
    }

    @Test
    fun theAnswerTakesTheWayOnDiscardAndForgetsItOtherwise() {
        val guard = NavigationGuard(unsavedWork = true)
        guard.holds(weight, page)
        assertNull(guard.answered(discard = false))
        assertNull(guard.pending)
        assertTrue(guard.unsavedWork)

        guard.holds(weight, page)
        assertEquals(weight, guard.answered(discard = true))
        assertFalse(guard.unsavedWork)
    }

    /** Restored with a way waiting, the guard trusts the last report: nothing goes until the page answers or reports no work. */
    @Test
    fun aRestoredGuardWaitsForThePageBeforeTakingTheWay() {
        val guard = NavigationGuard(unsavedWork = true)
        guard.holds(weight.copy(startSession = true), page)

        val saved = with(NavigationGuard.Saver) { SaverScope { true }.save(guard) }
        val restored = requireNotNull(NavigationGuard.Saver.restore(requireNotNull(saved)))

        assertTrue(restored.unsavedWork)
        assertEquals(weight.copy(startSession = true), restored.pending)
        assertNull(restored.released())

        restored.unsavedWork = false
        assertEquals(weight.copy(startSession = true), restored.released())
        assertNull(restored.pending)
    }

    @Test
    fun aPageThatClosedItselfGuardsNothing() {
        val guard = NavigationGuard(unsavedWork = true)
        guard.pageClosed()
        assertFalse(guard.holds(weight, page))
    }
}
