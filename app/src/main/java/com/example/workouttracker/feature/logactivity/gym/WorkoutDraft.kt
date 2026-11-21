package com.example.workouttracker.feature.logactivity.gym

import com.example.workouttracker.core.format.formatMetricNumber
import com.example.workouttracker.core.format.parseMetricNumber
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.activity.ListedActivity
import com.example.workouttracker.domain.activity.routine.LoggedSet
import com.example.workouttracker.domain.activity.routine.RoutineDay
import java.util.Locale

internal data class SetDraft(
    val weightText: String = "",
    val repsText: String = "",
    /**
     * The set this row shows as it was recorded, until the row is changed: it is written back as
     * it was, whatever the exercise asks of a new set now, so a set recorded without a weight
     * stays a set on an exercise done with one since.
     */
    val recorded: LoggedSet? = null,
    /**
     * The weight a set recorded on an exercise done without one since, which the row does not
     * show: the reps typed into the row are written with it, exactly as it was recorded.
     */
    val unseenWeightKg: Double? = null,
)

internal data class WorkoutDraft(
    val setsByExercise: Map<String, List<SetDraft>>,
)

/**
 * The rows of [exercises] as the editor opens them: each exercise's recorded sets as they were
 * recorded and carrying them, then empty rows up to its count. On an exercise done with a weight
 * a set without one shows an empty weight field; on one done without, a set's recorded weight
 * stays with its row unseen ([SetDraft.unseenWeightKg]). An empty row of an exercise done with a
 * weight starts from the last recorded weight, else the last workout's average weight, else the
 * routine's starting weight; one of an exercise done without starts from nothing. The fields are
 * written in [locale]'s decimal style; parsing accepts either separator.
 */
internal fun createDraft(
    activity: ListedActivity,
    day: RoutineDay,
    exercises: List<SessionExercise>,
    state: AppState,
    todayKey: String,
    locale: Locale,
): WorkoutDraft {
    val currentSession = state.sessionFor(activity, day, todayKey)
    val lastSummaries = latestWorkoutSummaries(state, exercises)

    return WorkoutDraft(
        setsByExercise = exercises.associate { exercise ->
            val savedSets = currentSession
                ?.exerciseLogs
                ?.firstOrNull { it.exerciseId == exercise.id }
                ?.sets
                .orEmpty()
            val fallbackWeight = if (!exercise.weighted) null else savedSets.lastOrNull()?.weightKg ?: lastSummaries[exercise.id]?.averageWeightKg ?: exercise.configured?.weight?.startKg

            exercise.id to List(exercise.rows) { index ->
                val savedSet = savedSets.getOrNull(index)
                SetDraft(
                    weightText = (if (savedSet != null) savedSet.weightKg else fallbackWeight)?.takeIf { exercise.weighted }?.let { formatMetricNumber(it, locale) }.orEmpty(),
                    repsText = savedSet?.reps?.let { formatMetricNumber(it, locale) }.orEmpty(),
                    recorded = savedSet,
                    unseenWeightKg = savedSet?.weightKg?.takeUnless { exercise.weighted },
                )
            }
        },
    )
}

/** The row at [index] as typed: a row changed by hand is a new set and no longer the one it was recorded as. */
internal fun WorkoutDraft.updateSet(
    exerciseId: String,
    index: Int,
    updated: SetDraft,
): WorkoutDraft {
    val currentSets = setsByExercise[exerciseId] ?: return this
    if (index !in currentSets.indices) return this
    val updatedSets = currentSets.toMutableList().also { it[index] = updated.copy(recorded = null) }
    return copy(setsByExercise = setsByExercise + (exerciseId to updatedSets))
}

/**
 * Every row of [exercise] as [set]: its reps, and its weight where the exercise has a weight to
 * record. On one done without, "Last" fills reps alone: a row keeps the weight it was recorded
 * with, unseen, since that weight is a fact, and a new row stays without one.
 */
internal fun WorkoutDraft.fillExerciseWith(
    exercise: SessionExercise,
    set: LoggedSet,
    locale: Locale,
): WorkoutDraft {
    val rows = setsByExercise[exercise.id].orEmpty()
    val weightText = set.weightKg?.let { formatMetricNumber(it, locale) }.orEmpty()
    return copy(
        setsByExercise = setsByExercise + (
            exercise.id to List(exercise.rows) { index ->
                SetDraft(
                    weightText = if (exercise.weighted) weightText else "",
                    repsText = formatMetricNumber(set.reps, locale),
                    unseenWeightKg = rows.getOrNull(index)?.unseenWeightKg.takeUnless { exercise.weighted },
                )
            }
            ),
    )
}

internal fun WorkoutDraft.adjustExerciseWeight(
    exerciseId: String,
    delta: Double,
    locale: Locale,
): WorkoutDraft {
    val updatedSets = setsByExercise[exerciseId]?.map { draft ->
        // An empty field starts from the bar; text that does not read as a number is left for the
        // user to fix rather than silently counted as zero.
        val current = if (draft.weightText.isBlank()) 0.0 else parseMetricNumber(draft.weightText)
        if (current == null) draft else draft.copy(weightText = formatMetricNumber(current + delta, locale), recorded = null)
    } ?: return this
    return copy(setsByExercise = setsByExercise + (exerciseId to updatedSets))
}

/**
 * The set this row holds, or null while it is no set: the set it was recorded as while it is
 * unchanged, else reps above zero with, on an exercise done with a weight ([weighted]), the
 * weight typed beside them, and on one done without, the weight the row carries unseen, which a
 * new row has none of.
 */
internal fun SetDraft.toLoggedSetOrNull(weighted: Boolean = true): LoggedSet? {
    if (recorded != null) return recorded
    val weight = if (weighted) parseMetricNumber(weightText) ?: return null else unseenWeightKg
    val reps = parseMetricNumber(repsText) ?: return null
    if (reps <= 0.0) return null
    return LoggedSet(weightKg = weight, reps = reps)
}
