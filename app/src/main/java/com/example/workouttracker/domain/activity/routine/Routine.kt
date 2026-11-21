package com.example.workouttracker.domain.activity.routine

import com.example.workouttracker.domain.checklist.requireId
import com.example.workouttracker.domain.checklist.requireLabel

/**
 * An activity's routine, a person's own: its days, each with the exercises it asks for. It is one
 * optional part of a listed activity beside its name and icon. It judges no day, so nothing
 * in it is dated.
 *
 * A removed day or exercise stays in the routine, marked, so an old session still names it: its
 * sessions and logs keep their names and count. An activity has a routine to offer while one of
 * its days is retained ([isOffered]); removing the routine marks every day removed and keeps them,
 * so an old session still finds its day's name. The limits count only what is not removed. A
 * day's ID and an exercise's are unique across the whole configuration, removed ones included,
 * which the configuration asks for; here each is unique within the routine.
 */
data class Routine(val days: List<RoutineDay>) {
    init {
        require(days.isNotEmpty()) { "A routine has a day, retained or removed." }
        require(days.distinctBy { it.id }.size == days.size) { "Two routine days share an ID." }
        require(retainedDays.size <= RoutineDays.last) { "A routine has at most ${RoutineDays.last} days." }
        val exerciseIds = days.flatMap { day -> day.exercises.map { it.id } }
        require(exerciseIds.distinct().size == exerciseIds.size) { "Two exercises share an ID." }
    }

    /** The days still offered, in the routine's order. */
    val retainedDays: List<RoutineDay> get() = days.filterNot { it.removed }

    /** Whether the activity has a routine to offer: a day still retained. A removed routine keeps its days for the sessions that name them. */
    val isOffered: Boolean get() = retainedDays.isNotEmpty()

    fun day(dayId: String): RoutineDay? = days.firstOrNull { it.id == dayId }

    fun exercise(exerciseId: String): RoutineExercise? = days.firstNotNullOfOrNull { day -> day.exercises.firstOrNull { it.id == exerciseId } }

    /** The day [exerciseId] belongs to, or null for an exercise the routine never knew. */
    fun dayOf(exerciseId: String): RoutineDay? = days.firstOrNull { day -> day.exercises.any { it.id == exerciseId } }

    /** A day's name, or its ID for one the routine never knew. */
    fun dayLabel(dayId: String): String = day(dayId)?.label ?: dayId

    /** An exercise's name, or the [recordedName] a log keeps for one the routine never knew. */
    fun exerciseLabel(exerciseId: String, recordedName: String): String = exercise(exerciseId)?.label ?: recordedName

    /**
     * The day that is next in turn: the one after the day of the newest of [sessions], the
     * activity's own, in the routine's order, walking past removed days, and the first retained
     * day when there is no session or its day is gone. Null while no day is retained. The picker
     * marks it and the reminder names it; the person still picks.
     */
    fun nextDay(sessions: List<WorkoutSession>): RoutineDay? {
        val retained = retainedDays
        if (retained.isEmpty()) return null
        val newest = sessions.maxWithOrNull(WorkoutSession.ByDate)
        val index = newest?.let { session -> days.indexOfFirst { it.id == session.day } }?.takeIf { it >= 0 }
            ?: return retained.first()
        return (1..days.size).map { days[(index + it) % days.size] }.first { !it.removed }
    }

    /**
     * A day's place in the routine, which its badge shows as a letter and colors by: its index
     * among the retained days, or among all days for a removed one, whose badge is only ever
     * seen on an old session.
     */
    fun placeOf(dayId: String): Int? {
        val day = day(dayId) ?: return null
        return if (day.removed) days.indexOf(day) else retainedDays.indexOf(day)
    }
}

data class RoutineDay(
    val id: String,
    val label: String,
    val exercises: List<RoutineExercise>,
    val removed: Boolean = false,
) {
    init {
        requireId(id)
        requireLabel(label)
        require(retainedExercises.size in ExercisesADay) { "Routine day $id has ${ExercisesADay.first} to ${ExercisesADay.last} exercises." }
    }

    /** The exercises still asked for, in the day's order. */
    val retainedExercises: List<RoutineExercise> get() = exercises.filterNot { it.removed }

    /** The sets the day asks for in all, which the picker names. */
    val totalSets: Int get() = retainedExercises.sumOf { it.sets }
}

/**
 * One exercise of a routine day: how many sets, the rep range to aim for, the weight it is done
 * with if any ([weight]) and the reps a session starts from until one was recorded.
 */
data class RoutineExercise(
    val id: String,
    val label: String,
    val sets: Int,
    val reps: IntRange,
    /** The weight the exercise is done with, or null for one done without: then it has no starting weight and no −/+ step. */
    val weight: ExerciseWeight?,
    val startReps: Int,
    val removed: Boolean = false,
) {
    init {
        requireId(id)
        requireLabel(label)
        require(sets in SetsAnExercise) { "Exercise $id asks ${SetsAnExercise.first} to ${SetsAnExercise.last} sets." }
        require(reps.first in RepsASet && reps.last in RepsASet && reps.first <= reps.last) { "Exercise $id aims for ${RepsASet.first} to ${RepsASet.last} reps, from at most to." }
        require(startReps in RepsASet) { "Exercise $id starts at ${RepsASet.first} to ${RepsASet.last} reps." }
    }

    /** The starting set, which a new empty row and the "Start" line read: the starting weight, or none, and the starting reps. */
    val start: LoggedSet get() = LoggedSet(weightKg = weight?.startKg, reps = startReps.toDouble())
}

/**
 * How an exercise done with a weight starts and steps: the weight a session starts from until one
 * was recorded, and what the − and + buttons add and take. A negative starting weight is help, as
 * the pull-up machine gives.
 */
data class ExerciseWeight(val startKg: Double, val stepKg: Double) {
    init {
        require(startKg in StartWeightKg) { "An exercise starts at ${StartWeightKg.start} to ${StartWeightKg.endInclusive} kg." }
        require(stepKg in StepKg) { "An exercise steps by ${StepKg.start} to ${StepKg.endInclusive} kg." }
    }
}

/** How many days a routine offers, removed ones not counted; a routine whose every day is removed offers none and is kept for its sessions. */
val RoutineDays = 1..7

/** How many exercises a routine day has, removed ones not counted. */
val ExercisesADay = 1..12

val SetsAnExercise = 1..10

/** A rep range's ends and the starting reps. */
val RepsASet = 1..100

/** The starting weight; below zero is help. */
val StartWeightKg = -200.0..500.0

/** What the − and + buttons add and take. */
val StepKg = 0.25..20.0
