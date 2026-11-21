package com.example.workouttracker.feature.tracker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.SaverScope
import com.example.workouttracker.core.ui.ViewIdentity
import com.example.workouttracker.domain.TrackerRef
import com.example.workouttracker.domain.TrackerTypeId
import com.example.workouttracker.feature.settings.ReminderAccessRequirement
import com.example.workouttracker.feature.settings.ViewWrite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackerReminderFlowTest {
    private val counterType = TrackerTypeId("counter")
    private val target = TrackerReminderRef(TrackerRef(counterType, "water"), "daily")

    @Test
    fun ordinaryEditsStandWhileTheSwitchWaitsForExactAccessBeforeNotifications() {
        val flow = TrackerReminderFlow()
        var ordinarySaved = false
        val started = flow.start(
            target,
            exactAlarmAllowed = false,
            notificationsAllowed = false,
            saveOrdinary = { ordinarySaved = true; ViewWrite.Stored },
            saveAll = { error("The reminder cannot be enabled before access.") },
        )

        assertTrue(ordinarySaved)
        assertEquals(ViewWrite.ReminderWaits, started.write)
        assertEquals(TrackerReminderEffect.RequestAccess(ReminderAccessRequirement.ExactAlarm), started.effect)
        assertEquals(PendingTrackerReminder(target, ReminderAccessRequirement.ExactAlarm), flow.pending)
        val enabled = mutableListOf<String>()
        val modules = fourthModule { enabled += "enabled"; ViewWrite.Stored }

        assertEquals(
            TrackerReminderEffect.RequestAccess(ReminderAccessRequirement.Notifications),
            flow.answer(exactAlarmAllowed = true, notificationsAllowed = false, modules),
        )
        assertTrue(enabled.isEmpty())
        assertEquals(PendingTrackerReminder(target, ReminderAccessRequirement.Notifications), flow.pending)
        assertEquals(
            TrackerReminderEffect.Finished(target, ViewWrite.Stored),
            flow.answer(exactAlarmAllowed = true, notificationsAllowed = true, modules),
        )
        assertEquals(listOf("enabled"), enabled)
        assertNull(flow.pending)
    }

    @Test
    fun theGrantResolvesTheLatestCommandOfAnUnknownKindAndConsumesIntentBeforeCallingIt() {
        val flow = awaitingNotifications()
        val enabled = mutableListOf<Pair<String, Int>>()
        // The module shown before leaving for permission must never be captured by the flow.
        val old = fourthModule { enabled += "Water" to 8; ViewWrite.Stored }
        val current = fourthModule {
            assertNull(flow.pending)
            assertEquals(TrackerReminderEffect.None, flow.answer(true, true, fourthModule { error("Reentrant grant.") }))
            enabled += "Glasses of water" to 10
            ViewWrite.Stored
        }

        assertEquals(TrackerReminderEffect.Finished(target, ViewWrite.Stored), flow.answer(true, true, current))
        assertEquals(TrackerReminderEffect.None, flow.answer(true, true, old))
        assertEquals(listOf("Glasses of water" to 10), enabled)
    }

    @Test
    fun aNotificationGrantRechecksExactAccessWhichMayHaveBeenRevokedMeanwhile() {
        val flow = awaitingNotifications()
        val modules = fourthModule { error("Exact alarm access was revoked.") }

        assertEquals(
            TrackerReminderEffect.RequestAccess(ReminderAccessRequirement.ExactAlarm),
            flow.answer(exactAlarmAllowed = false, notificationsAllowed = true, modules),
        )
        assertEquals(PendingTrackerReminder(target, ReminderAccessRequirement.ExactAlarm), flow.pending)
    }

    @Test
    fun denialAtEitherStageLeavesTheSwitchOffAndDiscardsTheIntent() {
        val modules = fourthModule { error("A denied permission cannot enable a reminder.") }
        val exact = TrackerReminderFlow(PendingTrackerReminder(target, ReminderAccessRequirement.ExactAlarm))
        assertEquals(TrackerReminderEffect.Refused(target), exact.answer(false, true, modules))
        assertNull(exact.pending)
        assertEquals(TrackerReminderEffect.None, exact.answer(true, true, modules))

        val notifications = awaitingNotifications()
        assertEquals(TrackerReminderEffect.Refused(target), notifications.answer(true, false, modules))
        assertNull(notifications.pending)
    }

    @Test
    fun aDeletedViewOrRemovedReminderIsReportedExplicitly() {
        val noView = TrackerModules(emptyList())
        val noReminder = fourthModule(reminders = emptyList())

        listOf(noView, noReminder).forEach { modules ->
            val flow = awaitingNotifications()
            assertEquals(TrackerReminderEffect.Unavailable(target), flow.answer(true, true, modules))
            assertNull(flow.pending)
        }
    }

    @Test
    fun aFailedOrdinarySaveDoesNotStartAPermissionContinuation() {
        listOf(ViewWrite.NotStored).forEach { failed ->
            val flow = TrackerReminderFlow()
            val started = flow.start(target, false, false, { failed }, { error("No full save without access.") })

            assertEquals(failed, started.write)
            assertEquals(TrackerReminderEffect.Finished(target, failed), started.effect)
            assertNull(flow.pending)
        }
    }

    @Test
    fun ordinaryEditsStoredWithoutAnAlarmStillAllowThePermissionRetry() {
        val flow = TrackerReminderFlow()
        val started = flow.start(target, false, false, { ViewWrite.StoredWithoutAlarm }, { error("Access is still missing.") })

        assertEquals(ViewWrite.ReminderWaits, started.write)
        assertEquals(TrackerReminderEffect.RequestAccess(ReminderAccessRequirement.ExactAlarm), started.effect)
        assertEquals(target, flow.pending?.target)
    }

    @Test
    fun aReadySaveWritesEverythingImmediatelyAndReturnsTheActualWriteResult() {
        val flow = TrackerReminderFlow()
        val started = flow.start(
            target, true, true,
            saveOrdinary = { error("No separate ordinary write when access is already granted.") },
            saveAll = { ViewWrite.NotStored },
        )

        assertEquals(ViewWrite.NotStored, started.write)
        assertEquals(TrackerReminderEffect.Finished(target, ViewWrite.NotStored), started.effect)
        assertNull(flow.pending)
    }

    @Test
    fun enablingFailureIsReportedWithoutLeavingARetryableOldIntent() {
        val flow = awaitingNotifications()
        val modules = fourthModule { ViewWrite.StoredWithoutAlarm }

        assertEquals(TrackerReminderEffect.Finished(target, ViewWrite.StoredWithoutAlarm), flow.answer(true, true, modules))
        assertNull(flow.pending)
        assertEquals(TrackerReminderEffect.None, flow.answer(true, true, modules))
    }

    @Test
    fun anAlreadyEnabledCurrentReminderDoesNotRunTheEnableCommandAgain() {
        val flow = awaitingNotifications()
        val modules = fourthModule(reminders = listOf(TrackerReminderCommand("daily", enabled = true) { error("Already enabled.") }))

        assertEquals(TrackerReminderEffect.Finished(target, ViewWrite.Stored), flow.answer(true, true, modules))
    }

    @Test
    fun theSavedContinuationContainsOnlyStableIntentAndResolvesCurrentStateAfterRestore() {
        val flow = awaitingNotifications()
        val scope = object : SaverScope {
            override fun canBeSaved(value: Any): Boolean = value is String || value is List<*>
        }
        val saved = with(TrackerReminderFlow.Saver) { scope.save(flow) }
        assertEquals(listOf("counter", "water", "daily", "Notifications"), saved)
        val restored = requireNotNull(TrackerReminderFlow.Saver.restore(requireNotNull(saved)))
        assertEquals(flow.pending, restored.pending)
        var enabled = 0

        assertEquals(
            TrackerReminderEffect.Finished(target, ViewWrite.Stored),
            restored.answer(true, true, fourthModule { enabled++; ViewWrite.Stored }),
        )
        assertEquals(1, enabled)
        assertNull(restored.pending)
    }

    private fun awaitingNotifications(): TrackerReminderFlow {
        val flow = TrackerReminderFlow()
        flow.start(target, true, false, { ViewWrite.Stored }, { error("Notifications are missing.") })
        return flow
    }

    /** A fourth kind contributes its own namespace and capability without changing the coordinator. */
    private fun fourthModule(enable: () -> ViewWrite): TrackerModules =
        fourthModule(listOf(TrackerReminderCommand("daily", enabled = false, enable)))

    private fun fourthModule(reminders: List<TrackerReminderCommand>): TrackerModules {
        val builtIns = listOf("measurement", "questions", "time").map { EmptyModule(TrackerTypeId(it)) }
        val counter = object : TrackerModule {
            override val typeId = counterType
            override val views = listOf(object : TrackerView {
                override val ref = target.view
                override val label = "Water"
                override val tagName = "water"
                override val visible = true
                override val reminders = reminders
                override val opensOnReminderDate = false
                override fun setVisible(visible: Boolean) = true
                @Composable override fun identity(): ViewIdentity = error("No UI in this test.")
                @Composable override fun Screen(host: TrackerScreenHost) = Unit
                @Composable override fun Settings(host: TrackerSettingsHost) = Unit
            })
            override val creation: TrackerCreation? = null
        }
        return TrackerModules(builtIns + counter)
    }

    private class EmptyModule(override val typeId: TrackerTypeId) : TrackerModule {
        override val views = emptyList<TrackerView>()
        override val creation: TrackerCreation? = null
    }
}
