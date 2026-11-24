package de.nicolasmahn.momentum.feature.logactivity.gym

import de.nicolasmahn.momentum.core.time.nowMillis
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.activity.ListedActivity
import de.nicolasmahn.momentum.domain.activity.routine.ExerciseLog
import de.nicolasmahn.momentum.domain.activity.routine.LoggedSet
import de.nicolasmahn.momentum.domain.activity.routine.RoutineDay
import de.nicolasmahn.momentum.domain.activity.routine.RoutineExercise
import de.nicolasmahn.momentum.domain.activity.routine.WorkoutSession

/*
 * A day's session is edited exercise by exercise, each as the editor holds it ([SessionExercise]):
 * the day's retained exercises in their order, then the exercises the session recorded that the
 * day no longer names, which are kept as they are. A recorded session is a fact the routine does
 * not limit: an exercise has as many rows as it recorded, at least as many as the routine asks.
 * A session is one per activity, day and date: a new one's ID is `<date>-<day ID>`, it names its
 * activity, and one left without a set is dropped.
 */

internal fun AppState.withSavedWorkoutSet(
    activity: ListedActivity,
    day: RoutineDay,
    exercise: SessionExercise,
    setIndex: Int,
    set: LoggedSet,
    todayKey: String,
    timestampMillis: Long = nowMillis(),
): AppState {
    if (setIndex !in 0 until exercise.rows) return this
    return storing(activity, day, exercise, todayKey, timestampMillis) { it.withSetAt(setIndex, set) }
}

internal fun AppState.withRemovedWorkoutSet(
    activity: ListedActivity,
    day: RoutineDay,
    exercise: SessionExercise,
    setIndex: Int,
    todayKey: String,
    timestampMillis: Long = nowMillis(),
): AppState {
    val existingLog = sessionFor(activity, day, todayKey)?.exerciseLogs?.firstOrNull { it.exerciseId == exercise.id } ?: return this
    if (setIndex !in existingLog.sets.indices) return this
    return storing(activity, day, exercise, todayKey, timestampMillis) { it.withoutSetAt(setIndex) }
}

internal fun AppState.withReplacedWorkoutSets(
    activity: ListedActivity,
    day: RoutineDay,
    exercise: SessionExercise,
    sets: List<LoggedSet>,
    todayKey: String,
    timestampMillis: Long = nowMillis(),
): AppState {
    if (sets.size > exercise.rows) return this
    if (sessionFor(activity, day, todayKey) == null && sets.isEmpty()) return this
    return storing(activity, day, exercise, todayKey, timestampMillis) { sets }
}

internal fun AppState.withDeletedWorkoutSession(sessionId: String): AppState {
    val sessionIndex = sessions.indexOfFirst { it.id == sessionId }
    if (sessionIndex < 0) return this

    return copy(
        sessions = sessions.toMutableList().also { it.removeAt(sessionIndex) },
    )
}

internal fun AppState.withWorkoutDuration(
    activity: ListedActivity,
    day: RoutineDay,
    todayKey: String,
    durationMinutes: Int?,
): AppState {
    val existingSession = sessionFor(activity, day, todayKey) ?: return this
    if (existingSession.durationMinutes == durationMinutes) return this
    return withWorkoutSession(existingSession.copy(durationMinutes = durationMinutes))
}

/**
 * This state with [exercise]'s sets in [activity]'s session of [day] on [todayKey] rewritten by
 * [change]. A session that does not exist yet is made for the day's own exercises, and for
 * [exercise] where the day does not ask for it: the editor still holds a removed or unknown
 * exercise after its last set was cleared and the session dropped with it, and what is saved for
 * it again must be found.
 */
private fun AppState.storing(
    activity: ListedActivity,
    day: RoutineDay,
    exercise: SessionExercise,
    todayKey: String,
    timestampMillis: Long,
    change: (List<LoggedSet>) -> List<LoggedSet>,
): AppState {
    val existingSession = sessionFor(activity, day, todayKey)
    val logs = (existingSession?.exerciseLogsFor(day) ?: day.emptyExerciseLogs()).let { logs ->
        if (logs.any { it.exerciseId == exercise.id }) logs else logs + ExerciseLog(exercise.id, exercise.label, sets = emptyList())
    }
    val updatedLogs = logs.map { log -> if (log.exerciseId == exercise.id) log.copy(sets = change(log.sets)) else log }
    val updatedSession = existingSession?.copy(
        timestampMillis = timestampMillis,
        exerciseLogs = updatedLogs,
    ) ?: WorkoutSession(
        id = "$todayKey-${day.id}",
        activityId = activity.id,
        day = day.id,
        dateKey = todayKey,
        timestampMillis = timestampMillis,
        exerciseLogs = updatedLogs,
    )
    return withWorkoutSession(updatedSession)
}

private fun AppState.withWorkoutSession(session: WorkoutSession): AppState {
    val remainingSessions = sessions.filterNot { existing ->
        existing.dateKey == session.dateKey && existing.activityId == session.activityId && existing.day == session.day
    }
    val updatedSessions = if (session.totalLoggedSets == 0) {
        remainingSessions
    } else {
        remainingSessions + session
    }
    return copy(sessions = updatedSessions.sortedByDescending { it.timestampMillis })
}

internal fun AppState.sessionFor(
    activity: ListedActivity,
    day: RoutineDay,
    todayKey: String,
): WorkoutSession? = sessions.firstOrNull { session ->
    session.dateKey == todayKey && session.activityId == activity.id && session.day == day.id
}

/** The session's logs in the day's order, one for each exercise it asks for, then the logs the day no longer names. */
private fun WorkoutSession.exerciseLogsFor(day: RoutineDay): List<ExerciseLog> {
    val exerciseIds = day.retainedExercises.mapTo(mutableSetOf()) { it.id }
    val dayLogs = day.retainedExercises.map { exercise ->
        exerciseLogs.firstOrNull { it.exerciseId == exercise.id } ?: exercise.emptyLog()
    }
    val logsNoLongerInDay = exerciseLogs.filterNot { it.exerciseId in exerciseIds }
    return dayLogs + logsNoLongerInDay
}

private fun RoutineDay.emptyExerciseLogs(): List<ExerciseLog> =
    retainedExercises.map { it.emptyLog() }

private fun RoutineExercise.emptyLog(): ExerciseLog = ExerciseLog(
    exerciseId = id,
    exerciseName = label,
    sets = emptyList(),
)

private fun List<LoggedSet>.withSetAt(index: Int, set: LoggedSet): List<LoggedSet> {
    val updatedSets = toMutableList()
    if (index in updatedSets.indices) {
        updatedSets[index] = set
    } else {
        updatedSets.add(set)
    }
    return updatedSets
}

private fun List<LoggedSet>.withoutSetAt(index: Int): List<LoggedSet> =
    toMutableList().also { it.removeAt(index) }
