package com.example.workouttracker.feature.questions

import com.example.workouttracker.core.ui.theme.MomentumDarkOnSurfaceVariant
import com.example.workouttracker.core.ui.theme.MomentumDarkSurfaceLowest
import com.example.workouttracker.core.ui.theme.MomentumRampStep
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.feature.checklist.ramp

/**
 * The color of the answer at [index] of the [count] its question has, for a view's first question.
 * The order of the answers is an ordinal scale, not a judgement: it stays inside the view's own hue
 * and only climbs in brightness, so a later answer reads as "more" of the same thing. The answers
 * are spread over the ramp's five steps, two taking the first and the last.
 *
 * This is the one place an answer becomes a color. The calendar paints a day with it and the first
 * question wears it on its tiles, so choosing an answer shows the color that day will get.
 */
internal fun answerStep(hue: ViewHue, index: Int, count: Int): MomentumRampStep {
    val ramp = hue.ramp
    return ramp[(index * ramp.lastIndex + (count - 1) / 2) / (count - 1)]
}

/**
 * "Not sure" is an answer, not a gap, so it owns a color too: one neutral grey, clearly lighter
 * than an untouched day, shared by the calendar day and the tile's swatch. Grey keeps it outside
 * the ramp, because not knowing is not a place on the scale.
 */
internal val NotSureStep = MomentumRampStep(
    fill = MomentumDarkOnSurfaceVariant,
    ink = MomentumDarkSurfaceLowest,
)
