package de.nicolasmahn.momentum.app

import de.nicolasmahn.momentum.core.time.currentHabitDate
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.activity.ActivityEdit
import de.nicolasmahn.momentum.domain.activity.applyingActivityEdits
import de.nicolasmahn.momentum.feature.settings.ViewWrite
import de.nicolasmahn.momentum.persistence.TrackerRepository
import de.nicolasmahn.momentum.reminder.activityDays
import java.time.LocalDate

/**
 * Stores what Activity's page hands over: the edits made, applied to the latest records on the
 * habit day they are stored, as [SubstanceCommands] applies Substances'.
 *
 * Activity's week is the days its reminder comes on ([activityDays]), so every save goes through
 * [reconcile], which moves the alarm to the week as stored. A save that moves the days while the
 * reminder is on ([reminderOn]), whose alarm cannot be set with access granted, is taken back, so the
 * reminder never waits for a day the week no longer has. With the reminder off there is no alarm to
 * move, so another reminder's failure never takes a week back.
 */
internal class ActivityCommands(
    private val repository: TrackerRepository,
    private val reconcile: (AppState) -> Boolean,
    private val exactAlarmsAllowed: () -> Boolean,
    private val refresh: () -> Unit,
    private val reminderOn: () -> Boolean,
    private val today: () -> LocalDate = ::currentHabitDate,
) {
    fun save(edits: List<ActivityEdit>): ViewWrite {
        val day = today()
        var before: AppState? = null
        val updated = repository.update { state ->
            before = state
            state.applyingActivityEdits(edits, day)
        }.updatedState() ?: return ViewWrite.NotStored
        val previous = requireNotNull(before)
        val saved = updated.activity
        return when {
            repository.settle(updated, needsAnAlarm = updated.activityDays() != previous.activityDays() && reminderOn(), reconcile, exactAlarmsAllowed, refresh) { state ->
                if (state.activity == saved) state.copy(activity = previous.activity) else state
            } -> ViewWrite.Stored
            repository.load().activity == saved -> ViewWrite.StoredWithoutAlarm
            else -> ViewWrite.NotStored
        }
    }
}
