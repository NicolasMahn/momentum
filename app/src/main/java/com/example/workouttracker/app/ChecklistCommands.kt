package com.example.workouttracker.app

import com.example.workouttracker.core.time.currentHabitDate
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.checklist.ChecklistConfiguration
import com.example.workouttracker.domain.checklist.ChecklistEdit
import com.example.workouttracker.domain.checklist.addChecklist
import com.example.workouttracker.domain.checklist.applyingEdits
import com.example.workouttracker.domain.checklist.checklist
import com.example.workouttracker.domain.checklist.deleteChecklist
import com.example.workouttracker.domain.checklist.saveChecklist
import com.example.workouttracker.domain.checklist.startedOn
import com.example.workouttracker.persistence.TrackerRepository
import com.example.workouttracker.persistence.TrackerUpdateResult
import java.time.LocalDate

/**
 * Stores what a checklist's page or the setup hands over: the edits made, applied to the latest
 * records on the habit day they are stored. So a change made before 04:00 and saved after counts
 * from the day it was saved, a change stored meanwhile to the same checklist stays, and an edit the
 * stored configuration refuses by then, such as a rename of an item removed meanwhile, is left
 * out, as the page left it out of what it showed.
 *
 * [reconcile] takes the new records, sets their alarms and says whether every alarm could be set.
 * A save or creation that asks for a new alarm which cannot be set, with access granted, is taken
 * back, so no reminder ever shows on without an alarm behind it; without access, the reminder's
 * row explains what is missing. A creation taken back leaves nothing behind, so trying again
 * creates the checklist once.
 */
internal class ChecklistCommands(
    private val repository: TrackerRepository,
    private val reconcile: (AppState) -> Boolean,
    private val exactAlarmsAllowed: () -> Boolean,
    private val refresh: () -> Unit,
    private val today: () -> LocalDate = ::currentHabitDate,
    private val hidden: () -> Set<String> = { emptySet() },
) {
    fun save(checklistId: String, edits: List<ChecklistEdit>): Boolean {
        val day = today()
        var before: ChecklistConfiguration? = null
        val updated = repository.update { state ->
            before = state.checklist(checklistId)
            if (before == null) state else state.applyingEdits(checklistId, edits, day)
        }.updatedState() ?: return false
        val previous = before ?: return false
        val saved = requireNotNull(updated.checklist(checklistId))
        return settle(updated, needsAnAlarm = saved.needsANewAlarmSince(previous)) { state ->
            if (state == updated) state.saveChecklist(previous) else state
        }
    }

    fun create(starter: ChecklistConfiguration, edits: List<ChecklistEdit>): Boolean {
        val day = today()
        val updated = repository.update { state ->
            state.addChecklist(starter.startedOn(day), hidden()).applyingEdits(starter.id, edits, day)
        }.updatedState() ?: return false
        val created = requireNotNull(updated.checklist(starter.id))
        return settle(updated, needsAnAlarm = created.checkpoints.any { it.remind }) { state ->
            if (state.checklist(starter.id) != null) state.deleteChecklist(starter.id) else state
        }
    }

    private fun settle(updated: AppState, needsAnAlarm: Boolean, takenBack: (AppState) -> AppState): Boolean =
        repository.settle(updated, needsAnAlarm, reconcile, exactAlarmsAllowed, refresh, takenBack)
}

/** Whether a save asks for an alarm it did not have, at any of its checkpoints. */
private fun ChecklistConfiguration.needsANewAlarmSince(before: ChecklistConfiguration): Boolean = checkpoints.any { checkpoint ->
    checkpoint.needsANewAlarmSince(before.checkpoints.firstOrNull { it.id == checkpoint.id })
}
