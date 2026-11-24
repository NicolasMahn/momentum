package de.nicolasmahn.momentum.domain.activity.routine

/** The names the seeded gym and its routine are written with, in the app language of that moment; [exercise] names one by its ID. */
data class GymLabels(
    val gym: String,
    val dayA: String,
    val dayB: String,
    val dayC: String,
    val exercise: (exerciseId: String) -> String,
)

/** The routine the gym starts with: the three days and the exercises the app always had, under the IDs the sessions name. */
fun defaultGymRoutine(labels: GymLabels): Routine {
    fun exercise(id: String, sets: Int, reps: IntRange, startWeightKg: Double, startReps: Int, stepKg: Double) =
        RoutineExercise(id, labels.exercise(id), sets, reps, ExerciseWeight(startWeightKg, stepKg), startReps)
    return Routine(
        days = listOf(
            RoutineDay(
                id = SeededGymDayIds[0],
                label = labels.dayA,
                exercises = listOf(
                    exercise("bench_press", 3, 8..10, 22.5, 10, 2.5),
                    exercise("back_squat", 3, 8..10, 60.0, 9, 1.0),
                    exercise("row", 3, 8..12, 40.0, 8, 1.0),
                    exercise("lateral_raises", 3, 12..15, 4.0, 12, 1.0),
                    exercise("triceps_pushdowns", 2, 12..15, 12.5, 15, 2.5),
                ),
            ),
            RoutineDay(
                id = SeededGymDayIds[1],
                label = labels.dayB,
                exercises = listOf(
                    exercise("pull_ups", 3, 6..10, -19.0, 8, 4.0),
                    exercise("dips", 3, 8..10, 0.0, 8, 1.0),
                    exercise("bulgarian_split_squat", 3, 8..10, 15.0, 10, 2.5),
                    exercise("preacher_hammer_curls", 3, 10..12, 10.0, 10, 2.5),
                ),
            ),
            RoutineDay(
                id = SeededGymDayIds[2],
                label = labels.dayC,
                exercises = listOf(
                    exercise("barbell_deadlift", 3, 6..8, 70.0, 8, 1.0),
                    exercise("overhead_press", 3, 6..10, 10.0, 8, 1.0),
                    exercise("face_pulls", 3, 15..20, 17.5, 15, 2.5),
                    exercise("hamstrings", 3, 10..12, 50.0, 10, 2.5),
                ),
            ),
        ),
    )
}

/** The IDs of the seeded days, which every session before the routine was stored names by its letter. */
val SeededGymDayIds: List<String> = listOf("A", "B", "C")

/** The IDs of the seeded exercises, which old logs name. */
val SeededGymExerciseIds: List<String> = defaultGymRoutine(GymLabels("gym", "A", "B", "C") { it }).days.flatMap { day -> day.exercises.map { it.id } }
