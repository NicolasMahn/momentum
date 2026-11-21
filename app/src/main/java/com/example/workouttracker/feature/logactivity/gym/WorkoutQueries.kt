package com.example.workouttracker.feature.logactivity.gym

import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.activity.ListedActivity
import com.example.workouttracker.domain.activity.routine.LoggedSet
import com.example.workouttracker.domain.activity.routine.RoutineDay
import com.example.workouttracker.domain.activity.routine.RoutineExercise
import com.example.workouttracker.domain.activity.routine.WorkoutSession

/**
 * One exercise as a session's editor holds it: the routine's, a removed one the session recorded,
 * or one the routine never knew. [rows] is the larger of the sets recorded and the sets the
 * routine asks now, so a recorded session is never cut to the routine. [configured] carries the
 * routine's numbers; it is null for an exercise the routine never knew, which has no step and no
 * rep range, and so no "−", "+" and no "Increase weight".
 */
internal data class SessionExercise(
    val id: String,
    val label: String,
    val rows: Int,
    val configured: RoutineExercise?,
)

/** Whether the editor asks for a weight with every set: the routine says the exercise has one, or it never knew the exercise, whose sets are edited as they were recorded. */
internal val SessionExercise.weighted: Boolean get() = configured == null || configured.weight != null

/** A routine's exercise as a new session holds it: as many rows as it asks. */
internal val RoutineExercise.planned: SessionExercise get() = SessionExercise(id, label, sets, this)

/**
 * The exercises [activity]'s session of [day] on [dateKey] is edited with: the day's retained
 * exercises in its order, then every exercise the session recorded that the day no longer names,
 * removed from the routine or never in it, with the rows it recorded. A new session starts with
 * the routine's exercises and counts.
 */
internal fun AppState.sessionExercises(activity: ListedActivity, day: RoutineDay, dateKey: String): List<SessionExercise> {
    val recorded = sessionFor(activity, day, dateKey)?.exerciseLogs.orEmpty()
    fun recordedSets(exerciseId: String) = recorded.firstOrNull { it.exerciseId == exerciseId }?.sets?.size ?: 0
    val asked = day.retainedExercises.map { exercise -> SessionExercise(exercise.id, exercise.label, maxOf(exercise.sets, recordedSets(exercise.id)), exercise) }
    val askedIds = asked.mapTo(HashSet()) { it.id }
    val others = recorded.filter { it.exerciseId !in askedIds && it.sets.isNotEmpty() }.map { log ->
        val known = activity.routine?.exercise(log.exerciseId)
        SessionExercise(log.exerciseId, known?.label ?: log.exerciseName, maxOf(known?.sets ?: 0, log.sets.size), known)
    }
    return asked + others
}

/**
 * What the newest session of an exercise recorded on average, or its starting set until there is
 * one. The weight is over the sets that recorded one and null where none did, so a weightless
 * session shows its reps alone; a starting weight is never shown as recorded.
 */
data class ExerciseWorkoutSummary(
    val averageWeightKg: Double?,
    val averageReps: Double,
    val setCount: Int,
    /** Whether an earlier session recorded the exercise; without one the summary is its starting set. */
    val fromHistory: Boolean,
) {
    fun asLoggedSet(): LoggedSet = LoggedSet(
        weightKg = averageWeightKg,
        reps = averageReps,
    )
}

/**
 * Each exercise's summary from the newest session that logged a set of it, or its starting set
 * where the routine has one; null for an exercise the routine never knew and no session recorded.
 * The history is sorted once for all [exercises], so an editor showing a whole day pays for one sort.
 */
internal fun latestWorkoutSummaries(
    state: AppState,
    exercises: List<SessionExercise>,
): Map<String, ExerciseWorkoutSummary?> {
    val newestFirst = state.sessions.sortedWith(WorkoutSession.ByDate.reversed())
    return exercises.associate { exercise -> exercise.id to latestWorkoutSummaryIn(newestFirst, exercise) }
}

private fun latestWorkoutSummaryIn(
    newestFirst: List<WorkoutSession>,
    exercise: SessionExercise,
): ExerciseWorkoutSummary? {
    newestFirst.forEach { session ->
        val sets = session.exerciseLogs
            .firstOrNull { it.exerciseId == exercise.id }
            ?.sets
            .orEmpty()
        if (sets.isNotEmpty()) {
            return ExerciseWorkoutSummary(
                averageWeightKg = sets.mapNotNull { it.weightKg }.takeIf { it.isNotEmpty() }?.average(),
                averageReps = sets.map { it.reps }.average(),
                setCount = sets.size,
                fromHistory = true,
            )
        }
    }

    val start = exercise.configured ?: return null
    return ExerciseWorkoutSummary(
        averageWeightKg = start.weight?.startKg,
        averageReps = start.startReps.toDouble(),
        setCount = 1,
        fromHistory = false,
    )
}

/**
 * Only after an earlier session of the exercise that recorded a weight, and only where the
 * routine gives it a weight and a rep range: its top reached on average.
 */
internal fun shouldIncreaseWeight(summary: ExerciseWorkoutSummary?, exercise: SessionExercise): Boolean {
    val configured = exercise.configured ?: return false
    if (configured.weight == null) return false
    return summary != null && summary.fromHistory && summary.averageWeightKg != null && summary.averageReps >= configured.reps.last
}
