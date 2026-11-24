package de.nicolasmahn.momentum.feature.time

import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.DayChange
import de.nicolasmahn.momentum.domain.DayChangeRefusal
import de.nicolasmahn.momentum.domain.time.TimeLog
import de.nicolasmahn.momentum.domain.time.TimeSession
import de.nicolasmahn.momentum.domain.time.addTimeMinutes
import de.nicolasmahn.momentum.domain.time.finishTimeSession
import de.nicolasmahn.momentum.domain.time.removeTimeDay
import de.nicolasmahn.momentum.domain.time.timeLog

/**
 * What a time view's screen asks to be stored for one day: minutes to add to it or the day's log
 * to remove. It names the view and the day, and holds the log the screen showed for that day
 * ([opened], null for a day without one), so it is decided against the logs as they are stored at
 * that moment and never against the state the screen happened to be shown.
 */
internal sealed interface TimeDayChange : DayChange {
    val viewId: String
    val dateKey: String
    val opened: TimeLog?

    /** [minutes] more for the day, added to what its log holds; a day cannot be lowered. */
    data class Add(
        override val viewId: String,
        override val dateKey: String,
        override val opened: TimeLog?,
        val minutes: Int,
    ) : TimeDayChange

    /** The whole day goes. */
    data class Remove(
        override val viewId: String,
        override val dateKey: String,
        override val opened: TimeLog?,
    ) : TimeDayChange

    /** The view must still exist, and the day's log must still be the one the screen showed. */
    override fun refusalIn(state: AppState): DayChangeRefusal? = when {
        !state.hasTimeView(viewId) -> DayChangeRefusal.Deleted
        state.timeLog(viewId, dateKey) != opened -> DayChangeRefusal.ChangedElsewhere
        else -> null
    }

    override fun appliedTo(state: AppState, timestampMillis: Long): AppState = when (this) {
        is Add -> state.copy(timeLogs = addTimeMinutes(state.timeLogs, viewId, dateKey, minutes, timestampMillis))
        is Remove -> state.copy(timeLogs = removeTimeDay(state.timeLogs, viewId, dateKey))
    }
}

/**
 * What a timed session asks to be stored as it ends: the whole minutes it timed, added to the day
 * it started on. It was timed and not typed over a day a screen showed, so it adds to whatever
 * that day holds by then and no log has to be the one it opened on; only its view must still
 * exist. A session whose view was deleted while it waited has nothing to credit.
 */
internal data class TimedMinutes(val session: TimeSession, val endMillis: Long) : DayChange {
    override fun refusalIn(state: AppState): DayChangeRefusal? = DayChangeRefusal.Deleted.takeUnless { state.hasTimeView(session.viewId) }

    /** The day's log is stamped with the session's end, not with the time of writing: saved again later, it timed no longer. */
    override fun appliedTo(state: AppState, timestampMillis: Long): AppState = finishTimeSession(state, session, endMillis).state
}

/** A day or session that outlives its configured timer cannot create a record. */
private fun AppState.hasTimeView(viewId: String): Boolean = time.any { it.id == viewId }
