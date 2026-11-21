package com.example.workouttracker.feature.tracker

import androidx.compose.runtime.saveable.listSaver
import com.example.workouttracker.domain.TrackerRef
import com.example.workouttracker.domain.TrackerTypeId
import com.example.workouttracker.feature.settings.ReminderAccessRequirement
import com.example.workouttracker.feature.settings.reminderAccessRequirement

/** Showing a tracker is the intent; its current configuration is resolved after permission returns. */
internal data class PendingTrackerVisibility(
    val view: TrackerRef,
    val waitingFor: ReminderAccessRequirement,
) {
    init {
        require(waitingFor == ReminderAccessRequirement.ExactAlarm || waitingFor == ReminderAccessRequirement.Notifications)
    }
}

internal sealed interface TrackerVisibilityEffect {
    data class RequestAccess(val requirement: ReminderAccessRequirement) : TrackerVisibilityEffect
    data class Changed(val view: TrackerRef, val stored: Boolean) : TrackerVisibilityEffect
    data class Refused(val view: TrackerRef) : TrackerVisibilityEffect
    data class Unavailable(val view: TrackerRef) : TrackerVisibilityEffect
    data object None : TrackerVisibilityEffect
}

/** A hidden tracker with enabled reminders becomes visible only after its alarms can arrive. */
internal class TrackerVisibilityFlow(initialPending: PendingTrackerVisibility? = null) {
    var pending: PendingTrackerVisibility? = initialPending
        private set

    fun cancel() {
        pending = null
    }

    fun change(
        view: TrackerView,
        visible: Boolean,
        exactAlarmAllowed: Boolean,
        notificationsAllowed: Boolean,
    ): TrackerVisibilityEffect {
        pending = null
        val requirement = reminderAccessRequirement(
            enabled = visible && view.reminders.any { it.enabled },
            exactAlarmAllowed = exactAlarmAllowed,
            notificationsAllowed = notificationsAllowed,
        )
        if (requirement == ReminderAccessRequirement.Ready) {
            return TrackerVisibilityEffect.Changed(view.ref, view.setVisible(visible))
        }
        pending = PendingTrackerVisibility(view.ref, requirement)
        return TrackerVisibilityEffect.RequestAccess(requirement)
    }

    fun answer(
        exactAlarmAllowed: Boolean,
        notificationsAllowed: Boolean,
        modules: TrackerModules,
    ): TrackerVisibilityEffect {
        val waiting = pending ?: return TrackerVisibilityEffect.None
        // Consume before calling a module so a duplicate or reentrant grant cannot repeat a write.
        pending = null
        val granted = when (waiting.waitingFor) {
            ReminderAccessRequirement.ExactAlarm -> exactAlarmAllowed
            ReminderAccessRequirement.Notifications -> notificationsAllowed
            else -> false
        }
        if (!granted) return TrackerVisibilityEffect.Refused(waiting.view)
        val current = modules.view(waiting.view) ?: return TrackerVisibilityEffect.Unavailable(waiting.view)
        if (current.visible) return TrackerVisibilityEffect.Changed(waiting.view, stored = true)
        val requirement = reminderAccessRequirement(
            enabled = current.reminders.any { it.enabled },
            exactAlarmAllowed = exactAlarmAllowed,
            notificationsAllowed = notificationsAllowed,
        )
        if (requirement != ReminderAccessRequirement.Ready) {
            pending = waiting.copy(waitingFor = requirement)
            return TrackerVisibilityEffect.RequestAccess(requirement)
        }
        return TrackerVisibilityEffect.Changed(current.ref, current.setVisible(true))
    }

    companion object {
        val Saver = listSaver<TrackerVisibilityFlow, String>(
            save = { flow ->
                flow.pending?.let { listOf(it.view.type.value, it.view.id, it.waitingFor.name) }.orEmpty()
            },
            restore = { values ->
                val pending = runCatching {
                    if (values.size != 3) null else PendingTrackerVisibility(
                        TrackerRef(TrackerTypeId(values[0]), values[1]),
                        ReminderAccessRequirement.valueOf(values[2]),
                    )
                }.getOrNull()
                TrackerVisibilityFlow(pending)
            },
        )
    }
}
