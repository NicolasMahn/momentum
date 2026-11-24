package de.nicolasmahn.momentum.app

import androidx.compose.runtime.saveable.SaverScope
import de.nicolasmahn.momentum.domain.TrackerRef
import de.nicolasmahn.momentum.domain.TrackerTypeId
import de.nicolasmahn.momentum.feature.settings.SettingsPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackerRoutingTest {
    private val counter = TrackerRef(TrackerTypeId("counter"), "water:daily:🫗")

    @Test
    fun anUnknownKindsSettingsAndCreationPagesRoundTripWithoutLosingTheViewId() {
        val settings = SettingsPage.Tracker(counter)
        val creation = SettingsPage.NewTracker(counter.type)

        assertEquals(settings, SettingsPage.fromKey(settings.key))
        assertEquals(creation, SettingsPage.fromKey(creation.key))
    }

    /** A schedule is a checklist started another way; its page must come back as that way, not the plain one. */
    @Test
    fun aCreationsVariantRoundTripsApartFromTheKindsPlainCreation() {
        val schedule = SettingsPage.NewTracker(TrackerTypeId("checklist"), "schedule")

        assertEquals(schedule, SettingsPage.fromKey(schedule.key))
        assertFalse(SettingsPage.NewTracker(TrackerTypeId("checklist")) == SettingsPage.fromKey(schedule.key))
    }

    @Test
    fun malformedGenericPageKeysHaveNoRoute() {
        listOf(
            "tracker:", "tracker:counter", "tracker:counter:", "tracker:counter:   ",
            "tracker::water", "tracker:Counter:water", "tracker:counter.invalid:water",
            "new_tracker:", "new_tracker:Counter", "new_tracker:counter:", "new_tracker:counter:Water", "new_tracker:counter:water:",
        ).forEach { key -> assertNull(key, SettingsPage.fromKey(key)) }
    }

    @Test
    fun differentKindsAndDifferentViewIdsAreDifferentPlaces() {
        val target = NavigationTarget(AppDestination.Tracker, tracker = counter)

        assertTrue(target.isAt(target.copy(loggedDateKey = "2026-10-18")))
        assertFalse(target.isAt(target.copy(tracker = counter.copy(id = "steps"))))
        assertFalse(target.isAt(target.copy(tracker = counter.copy(type = TrackerTypeId("events")))))
        assertFalse(target.isAt(NavigationTarget(AppDestination.Tracker)))
    }

    @Test
    fun formerKindRoutesNormalizeToGenericReferencesAndKeepTheRequestedDay() {
        val day = "2026-10-17"
        val oldRoutes = listOf(
            NavigationTarget(AppDestination.Checklist, checklistId = "chk_7f3a", checklistDateKey = day) to
                TrackerRef(TrackerTypeId("checklist"), "chk_7f3a"),
            NavigationTarget(AppDestination.Measurement, loggedViewId = "msr_k2p9", loggedDateKey = day) to
                TrackerRef(TrackerTypeId("measurement"), "msr_k2p9"),
            NavigationTarget(AppDestination.Questions, loggedViewId = "qst_k2p9", loggedDateKey = day) to
                TrackerRef(TrackerTypeId("questions"), "qst_k2p9"),
            NavigationTarget(AppDestination.Time, loggedViewId = "tim_k2p9", loggedDateKey = day) to
                TrackerRef(TrackerTypeId("time"), "tim_k2p9"),
        )

        oldRoutes.forEach { (old, ref) ->
            assertEquals(
                NavigationTarget(AppDestination.Tracker, tracker = ref, loggedDateKey = day),
                old.normalized(),
            )
        }
        assertTrue(oldRoutes.first().first.isAt(NavigationTarget(AppDestination.Tracker, tracker = oldRoutes.first().second)))
        assertEquals(
            NavigationTarget(
                AppDestination.SettingsPage,
                pageKey = SettingsPage.Tracker(TrackerRef(TrackerTypeId("checklist"), "water:daily:🫗")).key,
            ),
            NavigationTarget(AppDestination.SettingsPage, pageKey = "checklist:water:daily:🫗").normalized(),
        )
    }

    @Test
    fun aPendingGenericTrackerAndItsArrivalDateSurviveProcessDeath() {
        val target = NavigationTarget(AppDestination.Tracker, tracker = counter, loggedDateKey = "2026-10-17")
        val current = NavigationTarget(AppDestination.SettingsPage, pageKey = SettingsPage.Tracker(counter).key)
        val guard = NavigationGuard(unsavedWork = true)
        assertTrue(guard.holds(target, current))

        val saved = requireNotNull(with(NavigationGuard.Saver) { SaverScope { true }.save(guard) })
        val restored = requireNotNull(NavigationGuard.Saver.restore(saved))

        assertTrue(restored.unsavedWork)
        assertEquals(target, restored.pending)
        assertNull(restored.released())
        assertEquals(target, restored.answered(discard = true))
        assertFalse(restored.unsavedWork)
        assertNull(restored.pending)
    }

    @Test
    fun decliningToLeaveKeepsTheGenericSettingsPageDirtyAndForgetsThePendingRoute() {
        val target = NavigationTarget(AppDestination.Tracker, tracker = counter)
        val current = NavigationTarget(AppDestination.SettingsPage, pageKey = SettingsPage.Tracker(counter).key)
        val guard = NavigationGuard(unsavedWork = true)
        assertTrue(guard.holds(target, current))

        assertNull(guard.answered(discard = false))

        assertTrue(guard.unsavedWork)
        assertNull(guard.pending)
    }

    @Test
    fun savedGuardFromBeforeGenericTrackersRestoresWithoutTheNewFields() {
        // Actual eleven-slot format from the preceding version, rather than today's saver output.
        val oldSaved = listOf(true, "Measurement", "", "", false, "", "", "", "", "waist", "2026-10-17")

        val restored = requireNotNull(NavigationGuard.Saver.restore(oldSaved))

        assertTrue(restored.unsavedWork)
        assertEquals(
            NavigationTarget(AppDestination.Measurement, loggedViewId = "waist", loggedDateKey = "2026-10-17"),
            restored.pending,
        )
        assertNull(restored.pending!!.tracker)
        assertEquals(
            NavigationTarget(
                AppDestination.Tracker,
                tracker = TrackerRef(TrackerTypeId("measurement"), "waist"),
                loggedDateKey = "2026-10-17",
            ),
            restored.pending!!.normalized(),
        )
    }
}
