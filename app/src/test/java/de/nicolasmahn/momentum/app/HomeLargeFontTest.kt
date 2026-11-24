package de.nicolasmahn.momentum.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Science
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import de.nicolasmahn.momentum.core.ui.theme.MomentumHues
import de.nicolasmahn.momentum.core.ui.theme.WorkoutTrackerTheme
import de.nicolasmahn.momentum.core.ui.viewColors
import de.nicolasmahn.momentum.feature.tracker.TrackerHomeStatus
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Home's names and its Statistics and Settings buttons read whole in German, at the usual size and
 * on a narrow phone with a large font. Real glyph widths are needed, which only Robolectric's
 * native graphics give.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeLargeFontTest {
    @get:Rule
    val compose = createComposeRule()

    private fun showHome(titles: List<String>, fontScale: Float) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                WorkoutTrackerTheme {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        titles.forEach { title ->
                            HomeRow(
                                title = title,
                                icon = if (title.startsWith("Sub")) Icons.Rounded.Science else Icons.Rounded.Medication,
                                colors = MomentumHues.Purple.viewColors(),
                                picture = HomeDayPicture.Segments(listOf(1f, 0f, 0f)),
                                status = TrackerHomeStatus.Open(1, 3),
                                onClick = {},
                                testTag = "home_$title",
                            )
                        }
                        HomeBottomBar(onOpenStatistics = {}, onOpenSettings = {})
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun assertWhole(text: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action!!(layouts)
        assertFalse("\"$text\" is cut", layouts.single().hasVisualOverflow)
    }

    @Test
    @Config(qualifiers = "de-w411dp-h900dp-xxhdpi")
    fun theLongestDefaultNameReadsWholeAtTheUsualSize() {
        showHome(listOf("Nahrungsergänzung", "Substanzen"), fontScale = 1f)
        listOf("Nahrungsergänzung", "Substanzen", "Statistik", "Einstellungen").forEach(::assertWhole)
    }

    @Test
    @Config(qualifiers = "de-w320dp-h700dp-xxhdpi")
    fun namesAndButtonsReadWholeOnANarrowPhoneWithALargeFont() {
        showHome(listOf("Supplements", "Substanzen"), fontScale = 1.3f)
        listOf("Supplements", "Substanzen", "Statistik", "Einstellungen").forEach(::assertWhole)
    }
}
