package de.nicolasmahn.momentum.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import de.nicolasmahn.momentum.core.time.calendarWeekDateKeys
import de.nicolasmahn.momentum.core.ui.theme.WorkoutTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RecentDayCalendarFutureDaysTest {
    @get:Rule
    val composeRule = createComposeRule()

    // A Monday: the last row is today and six days that have not happened yet.
    private val todayKey = "2026-09-14"
    private val clicked = mutableListOf<String>()

    private fun showCalendar(selectedDateKey: String?) {
        composeRule.setContent {
            WorkoutTrackerTheme {
                RecentDayCalendar(
                    days = calendarWeekDateKeys(todayKey),
                    dateKey = { it },
                    selectedDateKey = selectedDateKey,
                    todayKey = todayKey,
                    onDayClick = { clicked += it },
                    dayStyle = {
                        RecentDayCalendarStyle(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    statusDescription = { "no entry" },
                    actionDescription = { "Select date" },
                    testTagPrefix = "calendar",
                )
            }
        }
    }

    @Test
    fun daysAfterTodayCannotBeFoundDescribedOrPressed() {
        showCalendar(selectedDateKey = todayKey)

        composeRule.onNodeWithTag("calendar_2026-09-14").assertIsSelected()
        composeRule.onNodeWithTag("calendar_2026-09-15").assertDoesNotExist()
        composeRule.onNodeWithTag("calendar_2026-09-20").assertDoesNotExist()
        composeRule.onAllNodes(hasText("15")).assertCountEquals(0)
        composeRule.onAllNodes(hasContentDescription("Sep 15", substring = true)).assertCountEquals(0)

        // Tuesday's ghost sits one cell to the right of today, in the same row.
        val today = composeRule.onNodeWithTag("calendar_2026-09-14").getUnclippedBoundsInRoot()
        val grid = composeRule.onNodeWithTag("calendar")
        val gridBounds = grid.getUnclippedBoundsInRoot()
        grid.performTouchInput {
            click(
                Offset(
                    x = (today.right - gridBounds.left + today.width / 2).toPx(),
                    y = (today.top - gridBounds.top + today.height / 2).toPx(),
                ),
            )
        }

        composeRule.runOnIdle { assertEquals(emptyList<String>(), clicked) }
    }

    @Test
    fun aSelectedFutureDateSelectsNoCell() {
        showCalendar(selectedDateKey = "2026-09-16")

        composeRule.onNodeWithTag("calendar_2026-09-16").assertDoesNotExist()
        composeRule.onNodeWithTag("calendar_2026-09-14").performClick()
        composeRule.runOnIdle { assertEquals(listOf("2026-09-14"), clicked) }
    }

    @Test
    fun theGridKeepsItsShapeWhateverTheWeekday() {
        showCalendar(selectedDateKey = null)

        // Today is the first cell of the last row; the ghosts after it still fill that row, so
        // the cell is exactly as wide as the ones in the full rows above.
        val today = composeRule.onNodeWithTag("calendar_2026-09-14").getUnclippedBoundsInRoot()
        val lastMonday = composeRule.onNodeWithTag("calendar_2026-09-07").getUnclippedBoundsInRoot()

        assertEquals(lastMonday.left, today.left)
        assertEquals(lastMonday.width, today.width)
        assertEquals(lastMonday.height, today.height)
    }
}
