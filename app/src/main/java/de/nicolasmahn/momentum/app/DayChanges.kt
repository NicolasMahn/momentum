package de.nicolasmahn.momentum.app

import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.DayChange
import de.nicolasmahn.momentum.domain.DayChangeRefusal
import de.nicolasmahn.momentum.domain.DayChangeResult
import de.nicolasmahn.momentum.persistence.TrackerRepository

/**
 * Saves or removes one day of a view, a measurement's number, a questions view's answers or a time
 * view's minutes. Inside the store's update the view must still exist and the day's record must
 * still be the one the draft opened on; then that view's day is changed and nothing else. So a
 * record something else stored since the screen opened, such as a notification's reply, is never
 * undone, on that day or on another, and a refused change writes nothing at all.
 *
 * [stored] is handed the records as they are now, after the change and after one that was refused
 * because they had changed, so the screen is shown what it could not replace.
 */
internal fun TrackerRepository.changeDay(change: DayChange, timestampMillis: Long, stored: (AppState) -> Unit): DayChangeResult {
    var before: AppState? = null
    var refusal: DayChangeRefusal? = null
    val after = update { state ->
        before = state
        refusal = change.refusalIn(state)
        // Thrown so that the store writes nothing: a refused change leaves it exactly as it is.
        check(refusal == null) { "The records are no longer what the draft opened on." }
        change.appliedTo(state, timestampMillis)
    }.updatedState()
    refusal?.let { why ->
        before?.let(stored)
        return DayChangeResult.Refused(why)
    }
    if (after == null) return DayChangeResult.Failed
    stored(after)
    return DayChangeResult.Stored(before = requireNotNull(before), after = after)
}
