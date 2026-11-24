package de.nicolasmahn.momentum.feature.questions

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import de.nicolasmahn.momentum.core.ui.theme.MomentumRamps
import de.nicolasmahn.momentum.domain.checklist.ViewHue
import de.nicolasmahn.momentum.domain.questions.AnswersPerQuestion
import de.nicolasmahn.momentum.feature.checklist.ramp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionsPresentationTest {
    /** Five answers span the full configured hue ramp. */
    @Test
    fun fiveAnswersUseEveryStepOfTheirConfiguredHue() {
        assertEquals(
            MomentumRamps.Orange,
            (0 until 5).map { answerStep(ViewHue.Orange, it, 5) },
        )
    }

    /** Fewer answers are spread over the ramp's five steps: two take the first and the last. */
    @Test
    fun answersAreSpreadOverTheFiveSteps() {
        fun places(count: Int) = (0 until count).map { ViewHue.Navy.ramp.indexOf(answerStep(ViewHue.Navy, it, count)) }
        assertEquals(listOf(0, 4), places(2))
        assertEquals(listOf(0, 2, 4), places(3))
        assertEquals(listOf(0, 1, 3, 4), places(4))
        assertEquals(listOf(0, 1, 2, 3, 4), places(5))
    }

    /** In every color a view can wear, a later answer is a visibly brighter fill, and the date stays readable on each. */
    @Test
    fun theAnswersOfEveryHueAreABrightnessScaleWithReadableDateText() {
        ViewHue.entries.forEach { hue ->
            AnswersPerQuestion.forEach { count ->
                val steps = (0 until count).map { answerStep(hue, it, count) }
                steps.zipWithNext().forEach { (lower, higher) ->
                    assertTrue("$hue: a later answer should read as a visibly brighter fill", higher.fill.luminance() > lower.fill.luminance())
                }
                steps.forEach { step ->
                    assertTrue("$hue: calendar date text needs accessible contrast on every step", contrastRatio(step.fill, step.ink) >= 4.5f)
                }
            }
        }
    }

    /** "Not sure" is a neutral of its own, on no hue's ramp, with readable date text. */
    @Test
    fun notSureIsANeutralOutsideEveryRamp() {
        assertTrue(contrastRatio(NotSureStep.fill, NotSureStep.ink) >= 4.5f)
        ViewHue.entries.forEach { hue -> assertFalse(NotSureStep.fill in hue.ramp.map { it.fill }) }
    }

    private fun contrastRatio(first: Color, second: Color): Float {
        val lighter = maxOf(first.luminance(), second.luminance())
        val darker = minOf(first.luminance(), second.luminance())
        return (lighter + 0.05f) / (darker + 0.05f)
    }
}
