package com.example.workouttracker.feature.tracker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.SaverScope
import com.example.workouttracker.core.ui.ViewIdentity
import com.example.workouttracker.domain.TrackerRef
import com.example.workouttracker.domain.TrackerTypeId
import com.example.workouttracker.feature.settings.ReminderAccessRequirement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackerVisibilityFlowTest {
    private val ref = TrackerRef(TrackerTypeId("counter"), "water")

    @Test
    fun showingARemindingTrackerWaitsForExactAccessThenNotificationsAndWritesOnce() {
        val view = CounterView()
        val flow = TrackerVisibilityFlow()

        assertEquals(
            TrackerVisibilityEffect.RequestAccess(ReminderAccessRequirement.ExactAlarm),
            flow.change(view, visible = true, exactAlarmAllowed = false, notificationsAllowed = false),
        )
        assertTrue(view.writes.isEmpty())
        assertEquals(PendingTrackerVisibility(ref, ReminderAccessRequirement.ExactAlarm), flow.pending)
        assertEquals(
            TrackerVisibilityEffect.RequestAccess(ReminderAccessRequirement.Notifications),
            flow.answer(true, false, modules(view)),
        )
        assertTrue(view.writes.isEmpty())
        assertEquals(PendingTrackerVisibility(ref, ReminderAccessRequirement.Notifications), flow.pending)
        assertEquals(TrackerVisibilityEffect.Changed(ref, true), flow.answer(true, true, modules(view)))
        assertNull(flow.pending)
        assertEquals(TrackerVisibilityEffect.None, flow.answer(true, true, modules(view)))
        assertEquals(listOf(true), view.writes)
    }

    @Test
    fun aGrantFindsTheLatestFourthKindAndConsumesIntentBeforeExecutingItsCommand() {
        val old = CounterView()
        val flow = awaitingNotifications(old)
        val current = CounterView(beforeWrite = {
            assertNull(flow.pending)
            assertEquals(TrackerVisibilityEffect.None, flow.answer(true, true, modules(old)))
        })

        assertEquals(TrackerVisibilityEffect.Changed(ref, true), flow.answer(true, true, modules(current)))

        assertEquals(listOf(true), current.writes)
        assertTrue(old.writes.isEmpty())
    }

    @Test
    fun denialAtEitherStageLeavesTheTrackerHiddenAndDiscardsTheIntent() {
        val view = CounterView()
        val exact = TrackerVisibilityFlow()
        exact.change(view, true, false, false)
        assertEquals(TrackerVisibilityEffect.Refused(ref), exact.answer(false, true, modules(view)))
        assertNull(exact.pending)
        assertEquals(TrackerVisibilityEffect.None, exact.answer(true, true, modules(view)))

        val notifications = awaitingNotifications(view)
        assertEquals(TrackerVisibilityEffect.Refused(ref), notifications.answer(true, false, modules(view)))
        assertNull(notifications.pending)
        assertTrue(view.writes.isEmpty())
    }

    @Test
    fun notificationAnswersRecheckExactAccessInsteadOfAssumingItIsStillGranted() {
        val view = CounterView()
        val flow = awaitingNotifications(view)

        assertEquals(
            TrackerVisibilityEffect.RequestAccess(ReminderAccessRequirement.ExactAlarm),
            flow.answer(false, true, modules(view)),
        )
        assertEquals(PendingTrackerVisibility(ref, ReminderAccessRequirement.ExactAlarm), flow.pending)
        assertTrue(view.writes.isEmpty())
    }

    @Test
    fun aDeletedTrackerIsReportedWithoutExecutingTheOldVisibilityCommand() {
        val deleted = CounterView()
        val flow = awaitingNotifications(deleted)

        assertEquals(TrackerVisibilityEffect.Unavailable(ref), flow.answer(true, true, modules()))
        assertNull(flow.pending)
        assertTrue(deleted.writes.isEmpty())
    }

    @Test
    fun hidingNeedsNoAccessAndCancelsAnOlderShowIntent() {
        val view = CounterView()
        val flow = awaitingNotifications(view)

        assertEquals(TrackerVisibilityEffect.Changed(ref, true), flow.change(view, false, false, false))
        assertEquals(listOf(false), view.writes)
        assertNull(flow.pending)
        assertEquals(TrackerVisibilityEffect.None, flow.answer(true, true, modules(view)))
    }

    @Test
    fun showingWithoutEnabledRemindersNeedsNoAccess() {
        listOf(emptyList(), listOf(reminder(enabled = false))).forEach { reminders ->
            val view = CounterView(reminders = reminders)
            val flow = TrackerVisibilityFlow()

            assertEquals(TrackerVisibilityEffect.Changed(ref, true), flow.change(view, true, false, false))
            assertEquals(listOf(true), view.writes)
            assertNull(flow.pending)
        }
    }

    @Test
    fun theCurrentConfigurationCanRemoveTheNeedForTheNextPermission() {
        val old = CounterView()
        val flow = TrackerVisibilityFlow()
        flow.change(old, true, false, false)
        val current = CounterView(reminders = emptyList())

        assertEquals(TrackerVisibilityEffect.Changed(ref, true), flow.answer(true, false, modules(current)))
        assertEquals(listOf(true), current.writes)
        assertTrue(old.writes.isEmpty())
    }

    @Test
    fun aStorageFailureIsReportedAndCancelMakesLaterAnswersInert() {
        val refusingStore = CounterView(stored = false)
        val flow = awaitingNotifications(refusingStore)
        assertEquals(TrackerVisibilityEffect.Changed(ref, false), flow.answer(true, true, modules(refusingStore)))
        assertNull(flow.pending)

        val cancelled = awaitingNotifications(refusingStore)
        cancelled.cancel()
        assertNull(cancelled.pending)
        assertEquals(TrackerVisibilityEffect.None, cancelled.answer(true, true, modules(refusingStore)))
        assertEquals(listOf(true), refusingStore.writes)
    }

    @Test
    fun theSaverKeepsOnlyTheStableIdentityAndPermissionStage() {
        val old = CounterView()
        val flow = awaitingNotifications(old)
        val scope = object : SaverScope {
            override fun canBeSaved(value: Any): Boolean = value is String || value is List<*>
        }
        val saved = with(TrackerVisibilityFlow.Saver) { scope.save(flow) }
        assertEquals(listOf("counter", "water", "Notifications"), saved)
        val restored = requireNotNull(TrackerVisibilityFlow.Saver.restore(requireNotNull(saved)))
        assertEquals(flow.pending, restored.pending)
        val current = CounterView()

        assertEquals(TrackerVisibilityEffect.Changed(ref, true), restored.answer(true, true, modules(current)))
        assertTrue(old.writes.isEmpty())
        assertEquals(listOf(true), current.writes)
        assertNull(restored.pending)
    }

    private fun awaitingNotifications(view: TrackerView): TrackerVisibilityFlow = TrackerVisibilityFlow().also {
        it.change(view, true, true, false)
    }

    /** The app can discover a fourth kind without adding its identity to a closed kind switch. */
    private fun modules(vararg views: TrackerView): TrackerModules {
        val builtIns = listOf("measurement", "questions", "time").map { Module(TrackerTypeId(it)) }
        return TrackerModules(builtIns + Module(ref.type, views.toList()))
    }

    private inner class CounterView(
        override val reminders: List<TrackerReminderCommand> = listOf(reminder()),
        private val stored: Boolean = true,
        private val beforeWrite: () -> Unit = {},
    ) : TrackerView {
        override val ref = this@TrackerVisibilityFlowTest.ref
        override val label = "Water"
        override val tagName = "water"
        override val visible = false
        override val opensOnReminderDate = false
        val writes = mutableListOf<Boolean>()

        override fun setVisible(visible: Boolean): Boolean {
            beforeWrite()
            writes += visible
            return stored
        }

        @Composable override fun identity(): ViewIdentity = error("No UI in this test.")
        @Composable override fun Screen(host: TrackerScreenHost) = Unit
        @Composable override fun Settings(host: TrackerSettingsHost) = Unit
    }

    private class Module(
        override val typeId: TrackerTypeId,
        override val views: List<TrackerView> = emptyList(),
    ) : TrackerModule {
        override val creation: TrackerCreation? = null
    }

    private fun reminder(enabled: Boolean = true) = TrackerReminderCommand("daily", enabled) {
        error("Changing visibility must not edit the reminder switch.")
    }
}
