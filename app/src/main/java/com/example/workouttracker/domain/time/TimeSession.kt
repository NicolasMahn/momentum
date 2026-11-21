package com.example.workouttracker.domain.time

import com.example.workouttracker.core.time.habitDate
import com.example.workouttracker.domain.AppState
import java.time.Instant
import java.time.ZoneId

/**
 * Minutes of a time view timed as they happen. A session names its view and belongs to the habit
 * day it started on, so one that runs past midnight or past 04:00 still counts for the evening it
 * began. One session runs at a time in the whole app.
 *
 * [endedAtMillis] is set once a session has ended and stays stored until its minutes are recorded:
 * the clock stops there, and saving again later cannot add time that did not pass.
 */
data class TimeSession(
    val viewId: String,
    val startedAtMillis: Long,
    val dateKey: String,
    val endedAtMillis: Long? = null,
) {
    companion object {
        fun startingAt(viewId: String, millis: Long, zone: ZoneId = ZoneId.systemDefault()): TimeSession =
            TimeSession(
                viewId = viewId,
                startedAtMillis = millis,
                dateKey = habitDate(Instant.ofEpochMilli(millis).atZone(zone)).toString(),
            )
    }
}

/** Whole minutes timed by [nowMillis]. A clock set backwards reads as nothing, never as negative. */
fun TimeSession.minutesAt(nowMillis: Long): Int {
    val end = endedAtMillis ?: nowMillis
    return ((end - startedAtMillis).coerceAtLeast(0L) / MillisPerMinute)
        .coerceAtMost(MinutesInADay.last.toLong())
        .toInt()
}

/** What finishing a session changes: the state to save and the minutes it adds to its day. */
data class FinishedSession(
    val state: AppState,
    val savedMinutes: Int,
)

/**
 * Adds the whole minutes timed to the session's own day of its own view. Under a minute saves
 * nothing, and the day never goes past 24 hours: the timer only ever records time that really
 * passed.
 *
 * Finishing is idempotent: the session's start joins [AppState.recordedTimeSessions] in the
 * same save as its minutes, so a session that comes back after an attempt died before it could
 * forget the session changes nothing, whatever other minutes were saved in between. One session
 * runs at a time, so a start names a session whichever view it was of.
 */
fun finishTimeSession(
    state: AppState,
    session: TimeSession,
    endMillis: Long,
): FinishedSession {
    if (session.startedAtMillis in state.recordedTimeSessions) {
        return FinishedSession(state, savedMinutes = session.minutesAt(endMillis))
    }
    val alreadyRecorded = minutesOn(state.timeLogs, session.viewId, session.dateKey)
    val minutes = session.minutesAt(endMillis).coerceAtMost(MinutesInADay.last - alreadyRecorded)
    if (minutes < 1) return FinishedSession(state, savedMinutes = 0)
    return FinishedSession(
        state = state.copy(
            timeLogs = addTimeMinutes(state.timeLogs, session.viewId, session.dateKey, minutes, timestampMillis = session.endedAtMillis ?: endMillis),
            recordedTimeSessions = state.recordedTimeSessions + session.startedAtMillis,
        ),
        savedMinutes = minutes,
    )
}

private const val MillisPerMinute = 60_000L
