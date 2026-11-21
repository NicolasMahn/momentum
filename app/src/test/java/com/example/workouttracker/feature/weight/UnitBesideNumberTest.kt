package com.example.workouttracker.feature.weight

import com.example.workouttracker.feature.measurement.MeasurementHistory
import com.example.workouttracker.feature.measurement.MeasurementWords
import com.example.workouttracker.feature.measurement.chartSpacing
import com.example.workouttracker.feature.measurement.measurementHistoryWindow
import com.example.workouttracker.feature.measurement.words
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.workouttracker.core.format.DateStyle
import com.example.workouttracker.core.format.formatDate
import com.example.workouttracker.core.ui.theme.WorkoutTrackerTheme
import com.example.workouttracker.domain.measurement.MeasurementRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.util.Locale

/**
 * The unit beside the large number never moves what sits below it from one day to the next: where
 * it stands is decided by the unit and the width, not by the day's number, and it keeps its place
 * on a day with nothing logged. A unit with no room beside the number takes a line of its own,
 * which makes the value taller once, for every day alike.
 */
@RunWith(RobolectricTestRunner::class)
class UnitBesideNumberTest {
    @get:Rule
    val compose = createComposeRule()

    /** Narrower than a number of ordinary length and the unit together, as this runner measures text. */
    private val NoRoom = 16.dp

    private val records = listOf(MeasurementRecord("2026-10-08", 3, 123456.78), MeasurementRecord("2026-10-07", 2, 7.0))
    private val window = measurementHistoryWindow(records, "2026-10-09")
    private var width by mutableStateOf(360.dp)
    private var unit by mutableStateOf("cm")
    private var selected by mutableStateOf("2026-10-08")

    private fun chartTop(): Dp = compose.onNodeWithTag("measurement_history_chart", useUnmergedTree = true).getUnclippedBoundsInRoot().top

    private fun topsOfEveryDay(): List<Dp> = listOf("2026-10-08", "2026-10-07", "2026-10-09").map { day ->
        selected = day
        compose.waitForIdle()
        chartTop()
    }

    @Test
    fun whatSitsBelowTheNumberStaysWhereItIsFromDayToDay() {
        compose.setContent {
            WorkoutTrackerTheme {
                Box(Modifier.width(width)) {
                    MeasurementHistory(
                        window = window,
                        selectedDateKey = selected,
                        onDateSelected = {},
                        words = MeasurementWords(name = "Pulse", unit = unit),
                        chartSpacing = 5.0,
                    )
                }
            }
        }
        val tops = mutableMapOf<Pair<Dp, String>, Dp>()
        listOf(360.dp, NoRoom).forEach { available ->
            listOf("cm", "beats / min.", "").forEach { typed ->
                width = available
                unit = typed
                val everyDay = topsOfEveryDay()
                // A long number, a short one and a day with none: the chart under the value is where it was.
                assertEquals("$available '$typed' $everyDay", 1, everyDay.distinct().size)
                tops[available to typed] = everyDay.first()
            }
        }
        // With room the unit stands on the number's line, so the value is as tall as without a unit.
        assertEquals(tops.getValue(360.dp to ""), tops.getValue(360.dp to "cm"))
        // Without room it takes a line of its own.
        assertTrue(tops.toString(), tops.getValue(NoRoom to "beats / min.") > tops.getValue(NoRoom to ""))
    }

    /**
     * With text measured for real, from a short number to one much wider than a number of ordinary
     * length, at every width from a narrow window to a wide one: the date under the value and the
     * chart are where they were, once the number has settled and at every frame while it counts.
     * A unit that stands beside the number keeps its one line there however little room the day's
     * number leaves it; it never wraps under the number for one day.
     */
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun aMuchWiderNumberNeverMovesTheDateOrTheChart() {
        val short = "2026-10-07"
        val wide = "2026-10-08"
        selected = short
        compose.setContent {
            WorkoutTrackerTheme {
                Box(Modifier.width(width)) {
                    MeasurementHistory(
                        window = window,
                        selectedDateKey = selected,
                        onDateSelected = {},
                        words = MeasurementWords(name = "Pulse", unit = unit),
                        chartSpacing = 5.0,
                    )
                }
            }
        }
        fun dateTop(day: String): Dp =
            compose.onNodeWithText(formatDate(LocalDate.parse(day), DateStyle.WeekdayDayMonth, Locale.US), useUnmergedTree = true).getUnclippedBoundsInRoot().top
        fun select(day: String): Pair<Dp, Dp> {
            selected = day
            compose.waitForIdle()
            return dateTop(day) to chartTop()
        }

        val moved = mutableListOf<String>()
        val besideAt = mutableMapOf<String, MutableList<Int>>()
        val belowAt = mutableMapOf<String, MutableList<Int>>()
        (168..432 step 4).forEach { available ->
            width = available.dp
            unit = ""
            val withoutAUnit = select(short)
            listOf("%", "kcal", "beats / min.").forEach { typed ->
                unit = typed
                val before = select(short)
                (if (before == withoutAUnit) besideAt else belowAt).getOrPut(typed) { mutableListOf() } += available

                // Frame by frame while the number counts up to the wide one, then settled.
                compose.mainClock.autoAdvance = false
                selected = wide
                compose.waitForIdle()
                repeat(30) { frame ->
                    compose.mainClock.advanceTimeByFrame()
                    val during = dateTop(wide) to chartTop()
                    if (during != before) moved += "$available dp '$typed' frame $frame: $before to $during"
                }
                compose.mainClock.autoAdvance = true
                val after = select(wide)
                if (after != before) moved += "$available dp '$typed' settled: $before to $after"
                // And back, and to a day with nothing logged, where the unit keeps its place unseen.
                listOf(short, "2026-10-09").forEach { day ->
                    val there = select(day)
                    if (there != before) moved += "$available dp '$typed' on $day: $before to $there"
                }
            }
        }
        assertEquals(moved.joinToString("\n"), 0, moved.size)
        // The widths walked hold both arrangements of "kcal", so also those between them, where it
        // has room beside a number of ordinary length and none beside the wide one. The short unit
        // stands beside the number at more of them, and the long one at none.
        assertTrue("$besideAt $belowAt", besideAt["kcal"].orEmpty().isNotEmpty() && belowAt["kcal"].orEmpty().isNotEmpty())
        assertTrue("$besideAt", besideAt["%"].orEmpty().size > besideAt["kcal"].orEmpty().size)
        assertTrue("$besideAt", besideAt["beats / min."].orEmpty().isEmpty())
    }
}
