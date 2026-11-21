package com.example.workouttracker.feature.tracker

import com.example.workouttracker.domain.LoggedViewKind
import com.example.workouttracker.domain.TrackerRef
import com.example.workouttracker.domain.TrackerTypeId
import com.example.workouttracker.feature.settings.ViewWrite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackerModulesTest {
    @Test
    fun anUnlistedKindCanSupplySeveralViewsAndItsOwnCreation() {
        val counterType = TrackerTypeId("counter")
        assertFalse(LoggedViewKind.entries.any { it.name.equals(counterType.value, ignoreCase = true) })
        val water = TestTrackerView(TrackerRef(counterType, "water"))
        val steps = TestTrackerView(TrackerRef(counterType, "steps"))
        val module = TestTrackerModule(counterType, listOf(water, steps))

        val registry = TrackerModules(listOf(module))

        assertEquals(listOf(water, steps), registry.views)
        assertSame(water, registry.view(water.ref))
        assertSame(steps, registry.view(steps.ref))
        assertEquals(listOf(module.creation), registry.creations)
        assertSame(module.creation, registry.creation(counterType))
        assertTrue(registry.view(water.ref)!!.setVisible(false))
        assertFalse(water.visible)
        assertTrue(steps.visible)
    }

    @Test
    fun reminderCommandsResolveWithinTheirViewAndKeepTheirBehavior() {
        var enabled = false
        val reminder = TrackerReminderCommand("refill", false) {
            enabled = true
            ViewWrite.Stored
        }
        val water = TestTrackerView(TrackerRef(TrackerTypeId("counter"), "water"), listOf(reminder))
        val registry = TrackerModules(listOf(TestTrackerModule(views = listOf(water))))

        val resolved = registry.reminder(TrackerReminderRef(water.ref, "refill"))!!

        assertSame(reminder, resolved)
        assertFalse(resolved.enabled)
        assertEquals(ViewWrite.Stored, resolved.enable())
        assertTrue(enabled)
    }

    @Test
    fun differentKindsCanUseTheSameViewAndReminderIds() {
        val counter = TestTrackerView(
            TrackerRef(TrackerTypeId("counter"), "daily"),
            listOf(reminder("evening")),
        )
        val events = TestTrackerView(
            TrackerRef(TrackerTypeId("events"), "daily"),
            listOf(reminder("evening")),
        )
        val registry = TrackerModules(listOf(
            TestTrackerModule(counter.ref.type, listOf(counter)),
            TestTrackerModule(events.ref.type, listOf(events)),
        ))

        assertSame(counter, registry.view(counter.ref))
        assertSame(events, registry.view(events.ref))
        assertSame(counter.reminders.single(), registry.reminder(TrackerReminderRef(counter.ref, "evening")))
        assertSame(events.reminders.single(), registry.reminder(TrackerReminderRef(events.ref, "evening")))
    }

    @Test
    fun reminderIdsCanRepeatInDifferentViewsOfOneKind() {
        val type = TrackerTypeId("counter")
        val water = TestTrackerView(TrackerRef(type, "water"), listOf(reminder("evening")))
        val steps = TestTrackerView(TrackerRef(type, "steps"), listOf(reminder("evening")))

        val registry = TrackerModules(listOf(TestTrackerModule(type, listOf(water, steps))))

        assertSame(water.reminders.single(), registry.reminder(TrackerReminderRef(water.ref, "evening")))
        assertSame(steps.reminders.single(), registry.reminder(TrackerReminderRef(steps.ref, "evening")))
    }

    @Test
    fun kindsWithoutCreationAndUnknownRoutesHaveExplicitlyAbsentResults() {
        val module = TestTrackerModule(creation = null)
        val registry = TrackerModules(listOf(module))
        val missing = TrackerRef(module.typeId, "missing")
        val unknownType = TrackerTypeId("uninstalled")

        assertTrue(registry.creations.isEmpty())
        assertNull(registry.creation(module.typeId))
        assertNull(registry.creation(unknownType))
        assertNull(registry.view(missing))
        assertNull(registry.view(TrackerRef(unknownType, "water")))
        assertNull(registry.reminder(TrackerReminderRef(missing, "evening")))
        assertNull(registry.reminder(TrackerReminderRef(module.views.single().ref, "missing")))
    }

    @Test
    fun duplicateTypeIdsFailRatherThanShadowingAnotherModule() {
        assertThrows(IllegalArgumentException::class.java) {
            TrackerModules(listOf(TestTrackerModule(), TestTrackerModule(views = emptyList())))
        }
    }

    @Test
    fun duplicateViewRefsFailRatherThanRoutingToAnArbitraryView() {
        val first = TestTrackerView(TrackerRef(TrackerTypeId("counter"), "water"))
        val duplicate = TestTrackerView(first.ref)

        assertThrows(IllegalArgumentException::class.java) {
            TrackerModules(listOf(TestTrackerModule(views = listOf(first, duplicate))))
        }
    }

    @Test
    fun aModuleCannotExposeAnotherKindsView() {
        val foreign = TestTrackerView(TrackerRef(TrackerTypeId("events"), "water"))

        assertThrows(IllegalArgumentException::class.java) {
            TrackerModules(listOf(TestTrackerModule(views = listOf(foreign))))
        }
    }

    @Test
    fun aModuleCannotExposeAnotherKindsCreation() {
        assertThrows(IllegalArgumentException::class.java) {
            TrackerModules(listOf(TestTrackerModule(creation = TestTrackerCreation(TrackerTypeId("events")))))
        }
    }

    @Test
    fun aModuleCannotReserveAnotherKindsTracker() {
        val foreign = TrackerRef(TrackerTypeId("events"), "water")
        assertThrows(IllegalArgumentException::class.java) {
            TrackerModules(listOf(TestTrackerModule(chosenPasses = setOf(foreign))))
        }
    }

    @Test
    fun duplicateReminderIdsWithinAViewFailRatherThanEnablingAnArbitraryReminder() {
        val view = TestTrackerView(
            TrackerRef(TrackerTypeId("counter"), "water"),
            listOf(reminder("evening"), reminder("evening")),
        )

        assertThrows(IllegalArgumentException::class.java) {
            TrackerModules(listOf(TestTrackerModule(views = listOf(view))))
        }
    }

    private fun reminder(id: String) = TrackerReminderCommand(id, false) { ViewWrite.Stored }
}
