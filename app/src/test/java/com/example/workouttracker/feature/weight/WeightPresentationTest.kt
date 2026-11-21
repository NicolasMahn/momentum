package com.example.workouttracker.feature.weight

import org.junit.Assert.assertEquals
import com.example.workouttracker.feature.checklist.momentumHue
import com.example.workouttracker.domain.compatibility.defaultWeightMeasurement
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.core.ui.viewColors
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.example.workouttracker.core.ui.theme.MomentumDarkBackground
import com.example.workouttracker.core.ui.theme.MomentumHues
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeightPresentationTest {
    private val colors = defaultWeightMeasurement(label = "Weight").hue.momentumHue.viewColors()

    @Test
    fun selectedDateMarkerIsQuieterThanTrendAndVisible() {
        // MeasurementHistory draws the selected-date marker with the ledge, behind the accent trend line.
        val accent = colors.accent
        val marker = colors.ledge
        assertNotEquals(accent, marker)
        assertTrue(
            "The marker must stay quieter than the trend line it sits behind",
            marker.luminance() < accent.luminance(),
        )
        assertTrue(contrastRatio(marker, MomentumDarkBackground) >= 3f)
    }

    @Test
    fun weightSurfacesStayReadable() {
        assertTrue(
            "Weight labels need accessible contrast on the app background",
            contrastRatio(colors.label, MomentumDarkBackground) >= 4.5f,
        )
        assertTrue(
            "Text on the Weight soft container needs accessible contrast",
            contrastRatio(colors.onContainer, colors.container) >= 4.5f,
        )
    }

    /** Weight's default configuration names the scale and sky, so storing its look changed nothing a person sees. */
    @Test
    fun theDefaultConfigurationWearsTheLookWeightAlwaysHad() {
        val seeded = defaultWeightMeasurement(label = "Weight")
        assertEquals(ViewIcon.Scale, seeded.icon)
        assertEquals(ViewHue.Sky, seeded.hue)
        assertEquals(seeded.hue.momentumHue.viewColors(), MomentumHues.Sky.viewColors())
    }

    private fun contrastRatio(first: Color, second: Color): Float {
        val lighter = maxOf(first.luminance(), second.luminance())
        val darker = minOf(first.luminance(), second.luminance())
        return (lighter + 0.05f) / (darker + 0.05f)
    }
}
