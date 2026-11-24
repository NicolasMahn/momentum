package de.nicolasmahn.momentum.reminder

import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.activity.hasActivityOn

/** A save left [dateKey] with an entry or a session with a set where it had none, or changed what it had. */
internal fun activityWasLoggedForDate(
    before: AppState,
    after: AppState,
    dateKey: String,
): Boolean {
    fun AppState.loggedOn() = activityEntries.filter { it.dateKey == dateKey } to sessions.filter { it.dateKey == dateKey && it.totalLoggedSets > 0 }
    return after.hasActivityOn(dateKey) && after.loggedOn() != before.loggedOn()
}
