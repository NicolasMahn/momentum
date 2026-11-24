package de.nicolasmahn.momentum.feature.eating

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import de.nicolasmahn.momentum.core.ui.viewColors
import de.nicolasmahn.momentum.core.ui.theme.MomentumDarkBackground
import de.nicolasmahn.momentum.core.ui.theme.MomentumHues
import de.nicolasmahn.momentum.domain.checklist.ViewHue
import de.nicolasmahn.momentum.domain.questions.testEatingQuestions
import de.nicolasmahn.momentum.feature.checklist.momentumHue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EatingPresentationTest {
    private val eating = testEatingQuestions()

    @Test
    fun eatingKeepsItsEstablishedOrangeViewIdentity() {
        assertEquals(ViewHue.Orange, eating.hue)
        assertEquals(MomentumHues.Orange.viewColors(), eating.hue.momentumHue.viewColors())
    }

    @Test
    fun eatingSurfacesStayReadable() {
        val colors = eating.hue.momentumHue.viewColors()
        assertTrue(
            "Eating labels need accessible contrast on the app background",
            contrastRatio(colors.label, MomentumDarkBackground) >= 4.5f,
        )
        assertTrue(
            "Text on the Eating soft container needs accessible contrast",
            contrastRatio(colors.onContainer, colors.container) >= 4.5f,
        )
        assertTrue(
            "Text on a selected Eating choice needs accessible contrast",
            contrastRatio(colors.onAccent, colors.accent) >= 4.5f,
        )
    }

    private fun contrastRatio(first: Color, second: Color): Float {
        val lighter = maxOf(first.luminance(), second.luminance())
        val darker = minOf(first.luminance(), second.luminance())
        return (lighter + 0.05f) / (darker + 0.05f)
    }
}
