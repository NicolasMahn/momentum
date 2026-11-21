package com.example.workouttracker.domain.activity.routine

/** One set as it was recorded: its weight, or none for an exercise done without one, and its reps. */
data class LoggedSet(
    val weightKg: Double?,
    val reps: Double,
)

/** What one exercise of a session recorded, under the ID and the name the routine had for it then; a log the routine no longer names is kept as it is. */
data class ExerciseLog(
    val exerciseId: String,
    val exerciseName: String,
    val sets: List<LoggedSet>,
)

/**
 * One session of an activity's routine, naming its activity by the ID it has in the configuration
 * ([activityId]) and its day by the ID it has in that routine ([day]). A new session's ID is
 * `<date>-<day ID>`; an old one keeps the ID it has. A session is a recorded fact the routine
 * does not limit: it holds every exercise it recorded, removed or unknown ones included, and
 * every set as it was recorded, a weight or none.
 */
data class WorkoutSession(
    val id: String,
    val activityId: String,
    val day: String,
    val dateKey: String,
    val timestampMillis: Long,
    val exerciseLogs: List<ExerciseLog>,
    val durationMinutes: Int? = null,
) {
    val totalLoggedSets: Int
        get() = exerciseLogs.sumOf { it.sets.size }

    companion object {
        /**
         * Sessions in the order they happened: by habit date, and on one date by when each was
         * saved. [timestampMillis] alone would not do, as every save moves it: an earlier day
         * corrected today would become the newest workout.
         */
        val ByDate: Comparator<WorkoutSession> = compareBy<WorkoutSession> { it.dateKey }.thenBy { it.timestampMillis }
    }
}
