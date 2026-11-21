package com.example.workouttracker.app

import com.example.workouttracker.core.time.currentHabitDate
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.substance.SubstanceCheckIn
import com.example.workouttracker.domain.substance.SubstanceEdit
import com.example.workouttracker.domain.substance.SubstanceSettings
import com.example.workouttracker.domain.substance.applyingSubstanceEdits
import com.example.workouttracker.domain.substance.saveSubstanceSettings
import com.example.workouttracker.persistence.TrackerRepository
import com.example.workouttracker.persistence.TrackerUpdateResult
import java.time.LocalDate

/**
 * Stores what the Substances page hands over: the edits made, applied to the latest records on the
 * habit day they are stored, as [ChecklistCommands] applies a checklist's. So a change made before
 * 04:00 and saved after is dated by the day it was saved, a change stored meanwhile stays, and an
 * edit the records refuse by then, such as removing an own substance logged meanwhile, is left out.
 *
 * [reconcile] takes the new records, sets the check-in's alarm and says whether it could be set.
 * A save that asks for a new alarm which cannot be set, with access granted, is taken back, so the
 * check-in never shows on without an alarm behind it; without access, its row explains what is
 * missing.
 */
internal class SubstanceCommands(
    private val repository: TrackerRepository,
    private val reconcile: (AppState) -> Boolean,
    private val exactAlarmsAllowed: () -> Boolean,
    private val refresh: () -> Unit,
    private val today: () -> LocalDate = ::currentHabitDate,
) {
    fun save(edits: List<SubstanceEdit>): Boolean {
        val day = today()
        var before: SubstanceSettings? = null
        val updated = repository.update { state ->
            before = state.substances
            state.applyingSubstanceEdits(edits, day)
        }.updatedState() ?: return false
        val previous = before ?: return false
        val saved = updated.substances
        return repository.settle(updated, needsAnAlarm = saved.checkIn.needsANewAlarmSince(previous.checkIn), reconcile, exactAlarmsAllowed, refresh) { state ->
            if (state.substances == saved) saveSubstanceSettings(state, previous) else state
        }
    }
}

/** Whether a save asks for an alarm it did not have: the check-in newly on, or on at a new time. */
private fun SubstanceCheckIn.needsANewAlarmSince(before: SubstanceCheckIn): Boolean = remind && (!before.remind || before.at != at)

internal fun TrackerUpdateResult.updatedState(): AppState? = (this as? TrackerUpdateResult.Updated)?.state
