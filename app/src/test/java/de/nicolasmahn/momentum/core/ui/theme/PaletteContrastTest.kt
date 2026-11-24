package de.nicolasmahn.momentum.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every hue a view can wear stays readable, the deep tones included: its ink on its face, its
 * label and its face as a mark on the background, its face as the icon on its own container, and
 * its ledge as a Home tile's edge, as a Home tile draws them.
 */
class PaletteContrastTest {
    @Test fun everyHueKeepsItsContrast() {
        MomentumHueRegistry.forEach { (name, hue) ->
            assertAtLeast(4.5, hue.ink, hue.face, "$name ink on its face")
            assertAtLeast(4.5, hue.label, MomentumDarkBackground, "$name label on the background")
            assertAtLeast(3.0, hue.face, MomentumDarkBackground, "$name face on the background")
            assertAtLeast(3.0, hue.face, hue.container, "$name face on its container")
            // The ledge is also a Home tile's edge; the darkest vivid ones, Indigo's and Red's, set the floor.
            assertAtLeast(2.6, hue.ledge, MomentumDarkBackground, "$name ledge on the background")
        }
    }

    /**
     * Every hue a view can wear has a ramp of its own, five steps that only grow brighter, so a
     * step reads as "more" and nothing else. One ink is readable on every step, and every step
     * keeps the 3:1 a mark needs on the background.
     */
    @Test fun everyHueHasARampOfFiveStepsThatOnlyGrowBrighter() {
        val ramps = MomentumRampRegistry.toMap()
        MomentumHueRegistry.forEach { (name, _) ->
            val ramp = ramps.getValue(name)
            assertTrue("$name has ${ramp.size} steps", ramp.size == 5)
            assertTrue("$name has one ink", ramp.map { it.ink }.toSet().size == 1)
            ramp.zipWithNext().forEach { (lower, higher) -> assertTrue("$name grows brighter", higher.fill.luminance() > lower.fill.luminance()) }
            ramp.forEachIndexed { index, step ->
                assertAtLeast(4.5, step.ink, step.fill, "$name ink on step $index")
                assertAtLeast(3.0, step.fill, MomentumDarkBackground, "$name step $index on the background")
            }
        }
    }

    /**
     * A missed item's cross is the theme's `outline` on the checkbox's empty face, the lowest
     * surface. It is a mark to be read, so it keeps the 3:1 a graphic needs.
     */
    @Test fun aCheckboxCrossReadsOnTheEmptyFace() {
        assertAtLeast(3.0, MomentumDarkOutline, MomentumDarkSurfaceLowest, "The cross on the empty face")
    }

    private fun assertAtLeast(ratio: Double, a: Color, b: Color, what: String) {
        val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
        val contrast = (light + 0.05) / (dark + 0.05)
        assertTrue("$what is ${"%.2f".format(contrast)}:1, under $ratio:1", contrast >= ratio)
    }
}
