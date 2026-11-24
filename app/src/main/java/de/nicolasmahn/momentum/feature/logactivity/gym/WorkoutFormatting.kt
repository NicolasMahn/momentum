package de.nicolasmahn.momentum.feature.logactivity.gym

import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.core.format.formatMetricNumber
import de.nicolasmahn.momentum.core.text.UiText
import de.nicolasmahn.momentum.core.text.joinedBy
import de.nicolasmahn.momentum.core.text.pluralText
import de.nicolasmahn.momentum.core.text.uiText
import de.nicolasmahn.momentum.domain.activity.routine.LoggedSet
import java.util.Locale
import kotlin.math.abs

/** Below this a typed weight is the user meaning "nothing on the bar", not a real load. */
private const val BodyweightEpsilon = 0.005

/**
 * A set's load, in the words the exercise is actually done in. The model stores one signed number,
 * so a pull-up with a 3 kg assist band is `-3.0`; printed bare that reads as "minus three kilos",
 * which is not a thing anyone lifts. Zero and below are bodyweight work, so they say so.
 */
internal fun formatSetLoad(weightKg: Double): UiText = when {
    abs(weightKg) < BodyweightEpsilon -> uiText(R.string.activity_gym_load_bodyweight)
    weightKg < 0 -> uiText(R.string.activity_gym_load_assisted, UiText.Number(-weightKg))
    else -> uiText(R.string.unit_kg, UiText.Number(weightKg))
}

/** How many reps a set was worth, without the unit a count does not have. */
internal fun formatSetReps(reps: Double): UiText = UiText.Number(reps)

/** One set in full: its load and its reps, or its reps alone where it recorded no weight. */
private fun formatSet(weightKg: Double?, reps: Double): UiText =
    weightKg?.let { uiText(R.string.activity_gym_set, formatSetLoad(it), formatSetReps(reps)) } ?: formatSetReps(reps)

/**
 * A workout's average set, in the same words as a single set, "25 kg × 10". Where no set recorded
 * a weight it is the reps named as reps, "6 reps": a bare number on its own line says nothing.
 */
internal fun formatAverageWorkoutSummary(summary: ExerciseWorkoutSummary): UiText =
    summary.averageWeightKg?.let { formatSet(it, summary.averageReps) }
        ?: pluralText(R.plurals.activity_gym_reps, pluralCountOf(summary.averageReps), formatSetReps(summary.averageReps))

/**
 * The count that picks the plural of [value] as it is shown. Android picks a plural by a whole
 * count; a number shown with a fraction, "1.5", takes the plural of many in English and German,
 * as two does.
 */
private fun pluralCountOf(value: Double): Int = formatMetricNumber(value, Locale.ROOT).toIntOrNull() ?: 2

/**
 * Every set of one exercise on one line. A working set is usually the same load for a few sets,
 * so the load is said once and only the reps repeat ("25 kg × 10 · 9 · 9"); sets without a
 * weight say their reps alone ("10 · 9 · 9"). When the load does move between sets there is
 * nothing to factor out, so each set is spelled in full.
 */
internal fun formatExerciseSets(sets: List<LoggedSet>): UiText {
    if (sets.isEmpty()) return UiText.Verbatim("")
    val reps = sets.map { formatSetReps(it.reps) }
    if (sets.all { it.weightKg == null }) return reps.joinedBy(SetSeparator)
    // Compared as shown, so 22.5 and 22.501 count as one load and 22.125 and 22.13 do not. The
    // shown rounding does not depend on the language, so any one locale gives the same answer.
    val sameLoad = sets.map { it.weightKg?.let { load -> formatMetricNumber(load, Locale.ROOT) } }.distinct().size == 1
    return if (sameLoad) {
        uiText(R.string.activity_gym_set, formatSetLoad(requireNotNull(sets.first().weightKg)), reps.joinedBy(SetSeparator))
    } else {
        sets.map { formatSet(it.weightKg, it.reps) }.joinedBy(SetSeparator)
    }
}

private const val SetSeparator = " · "
