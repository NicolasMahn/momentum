package de.nicolasmahn.momentum.app

import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.checklist.Checkpoint
import de.nicolasmahn.momentum.persistence.TrackerRepository

/**
 * Whether [updated] stands, the records as a save or a creation just stored them. [reconcile]
 * takes them, sets their alarms and says whether every alarm could be set. The write stands with
 * its alarms set, with the access to set them missing, or with no new alarm asked for. Otherwise
 * it is undone through [takenBack] and the alarms are set for what is stored then, so no reminder
 * ever shows on without an alarm behind it.
 *
 * A change that cannot be taken back stays stored: where the store cannot write, [refresh] shows
 * the screen what it holds.
 */
internal fun TrackerRepository.settle(
    updated: AppState,
    needsAnAlarm: Boolean,
    reconcile: (AppState) -> Boolean,
    exactAlarmsAllowed: () -> Boolean,
    refresh: () -> Unit,
    takenBack: (AppState) -> AppState,
): Boolean {
    if (reconcile(updated) || !exactAlarmsAllowed() || !needsAnAlarm) return true
    update(takenBack).updatedState()?.let(reconcile) ?: refresh()
    return false
}

/**
 * Whether this checkpoint asks for an alarm it did not have as [before]: it reminds at a new time,
 * or newly. Every other change only keeps or cancels one.
 */
internal fun Checkpoint.needsANewAlarmSince(before: Checkpoint?): Boolean = remind && (before == null || !before.remind || before.at != at)
