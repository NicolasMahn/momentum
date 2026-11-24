package de.nicolasmahn.momentum.feature.time

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import de.nicolasmahn.momentum.core.ui.theme.WorkoutTrackerTheme
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.holiday.HolidaySchedule
import de.nicolasmahn.momentum.domain.time.testReadingTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * On a narrow phone with a large font, German's "Eintragen" does not fit beside the minutes field,
 * so the field stands above the buttons and the label reads on one line. Opening the minutes row
 * today moves nothing under it. Real glyph widths are needed, which only native graphics give.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "de-w320dp-h800dp-xxhdpi")
class TimeAddLargeFontTest {
    @get:Rule
    val compose = createComposeRule()

    private val reading = testReadingTime()
    private val tag = reading.testTagPrefix

    private fun show(fontScale: Float) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                WorkoutTrackerTheme {
                    TimeScreen(
                        state = AppState(time = listOf(reading)),
                        view = reading,
                        words = reading.words(),
                        onDayChange = { error("nothing is added here") },
                        todayKey = "2026-10-21",
                        daysOff = HolidaySchedule(),
                        onStartSession = {},
                        sessionNote = null,
                        testTagPrefix = tag,
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun typeMinutes() {
        compose.onNodeWithTag("${tag}_type_minutes").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun calendarTop() = compose.onNodeWithTag("${tag}_history_2026-10-21").getUnclippedBoundsInRoot().top

    @Test
    fun theAddLabelReadsOnOneLineAtALargeFont() {
        show(fontScale = 1.3f)
        typeMinutes()
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("Eintragen", useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action!!(layouts)
        assertEquals(1, layouts.single().lineCount)
    }

    @Test
    fun openingTheMinutesRowMovesNothingUnderIt() {
        show(fontScale = 1f)
        val before = calendarTop()
        typeMinutes()
        // The field stands above the buttons here, in the room the timer's row kept for it.
        val field = compose.onNodeWithTag("${tag}_minutes_input").getUnclippedBoundsInRoot()
        val add = compose.onNodeWithTag("${tag}_log_today").getUnclippedBoundsInRoot()
        assertTrue("the field is not above Add: $field, $add", field.bottom <= add.top)
        assertEquals(before.value, calendarTop().value, 0.5f)
    }
}
