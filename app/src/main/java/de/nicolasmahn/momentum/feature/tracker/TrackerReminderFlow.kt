package de.nicolasmahn.momentum.feature.tracker

import androidx.compose.runtime.saveable.listSaver
import de.nicolasmahn.momentum.domain.TrackerRef
import de.nicolasmahn.momentum.domain.TrackerTypeId
import de.nicolasmahn.momentum.feature.settings.ReminderAccessRequirement
import de.nicolasmahn.momentum.feature.settings.ViewWrite
import de.nicolasmahn.momentum.feature.settings.reminderAccessRequirement

/** Only the intent survives a trip through the phone's permission screens, never a draft or command. */
internal data class PendingTrackerReminder(
    val target: TrackerReminderRef,
    val waitingFor: ReminderAccessRequirement,
) {
    init {
        require(waitingFor == ReminderAccessRequirement.ExactAlarm || waitingFor == ReminderAccessRequirement.Notifications)
    }

}

internal sealed interface TrackerReminderEffect {
    data class RequestAccess(val requirement: ReminderAccessRequirement) : TrackerReminderEffect
    data class Finished(val target: TrackerReminderRef, val write: ViewWrite) : TrackerReminderEffect
    data class Refused(val target: TrackerReminderRef) : TrackerReminderEffect
    data class Unavailable(val target: TrackerReminderRef) : TrackerReminderEffect
    data object None : TrackerReminderEffect
}

internal data class TrackerReminderSave(val write: ViewWrite, val effect: TrackerReminderEffect)

/**
 * The permission continuation owns only a reminder's identity. The ordinary edits can be stored
 * immediately; after access is granted the current module supplies a new enable command against
 * its latest state. Platform launchers interpret the effects and report the current access back.
 */
internal class TrackerReminderFlow(initialPending: PendingTrackerReminder? = null) {
    var pending: PendingTrackerReminder? = initialPending
        private set

    fun cancel() {
        pending = null
    }

    fun start(
        target: TrackerReminderRef,
        exactAlarmAllowed: Boolean,
        notificationsAllowed: Boolean,
        saveOrdinary: () -> ViewWrite,
        saveAll: () -> ViewWrite,
    ): TrackerReminderSave {
        pending = null
        val requirement = reminderAccessRequirement(true, exactAlarmAllowed, notificationsAllowed)
        if (requirement == ReminderAccessRequirement.Ready) {
            val write = saveAll()
            return TrackerReminderSave(write, TrackerReminderEffect.Finished(target, write))
        }
        val write = saveOrdinary()
        if (write == ViewWrite.NotStored) {
            return TrackerReminderSave(write, TrackerReminderEffect.Finished(target, write))
        }
        pending = PendingTrackerReminder(target, requirement)
        return TrackerReminderSave(ViewWrite.ReminderWaits, TrackerReminderEffect.RequestAccess(requirement))
    }

    fun answer(
        exactAlarmAllowed: Boolean,
        notificationsAllowed: Boolean,
        modules: TrackerModules,
    ): TrackerReminderEffect {
        val waiting = pending ?: return TrackerReminderEffect.None
        // Consume before calling a module: a duplicate or reentrant answer must not enable twice.
        pending = null
        val granted = when (waiting.waitingFor) {
            ReminderAccessRequirement.ExactAlarm -> exactAlarmAllowed
            ReminderAccessRequirement.Notifications -> notificationsAllowed
            else -> false
        }
        if (!granted) return TrackerReminderEffect.Refused(waiting.target)

        val reminder = modules.reminder(waiting.target) ?: return TrackerReminderEffect.Unavailable(waiting.target)
        val requirement = reminderAccessRequirement(true, exactAlarmAllowed, notificationsAllowed)
        if (requirement != ReminderAccessRequirement.Ready) {
            pending = waiting.copy(waitingFor = requirement)
            return TrackerReminderEffect.RequestAccess(requirement)
        }
        val write = if (reminder.enabled) ViewWrite.Stored else reminder.enable()
        return TrackerReminderEffect.Finished(waiting.target, write)
    }

    companion object {
        val Saver = listSaver<TrackerReminderFlow, String>(
            save = { it.pending?.savedValues().orEmpty() },
            restore = { TrackerReminderFlow(restorePending(it)) },
        )
    }
}

private fun PendingTrackerReminder.savedValues(): List<String> =
    listOf(target.view.type.value, target.view.id, target.id, waitingFor.name)

private fun restorePending(values: List<String>): PendingTrackerReminder? = runCatching {
    if (values.size != 4) return null
    PendingTrackerReminder(
        TrackerReminderRef(TrackerRef(TrackerTypeId(values[0]), values[1]), values[2]),
        ReminderAccessRequirement.valueOf(values[3]),
    )
}.getOrNull()
