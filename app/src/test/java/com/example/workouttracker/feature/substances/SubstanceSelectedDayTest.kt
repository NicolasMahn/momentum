package com.example.workouttracker.feature.substances

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import com.example.workouttracker.core.ui.theme.WorkoutTrackerTheme
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.substance.SubstanceSettings
import com.example.workouttracker.feature.weight.selectedHistoryDay
import com.example.workouttracker.feature.weight.transcript
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Substances asks about yesterday, so yesterday is the day its screen opens on and the one it
 * leaves unnamed. Any other day, today included, is picked in the calendar or brought by the
 * check-in, and is named above the tiles.
 */
@RunWith(RobolectricTestRunner::class)
class SubstanceSelectedDayTest {
    @get:Rule
    val compose = createComposeRule()

    private var state by mutableStateOf(AppState(substances = SubstanceSettings.Legacy))
    private var todayKey by mutableStateOf("2026-09-17")
    private var requested by mutableStateOf<String?>(null)

    private fun show() {
        compose.setContent {
            WorkoutTrackerTheme {
                SubstanceScreen(
                    state = state,
                    onStateChange = { state = it; true },
                    todayKey = todayKey,
                    onOpenSettings = {},
                    initialDateKey = requested,
                    onInitialDateConsumed = { requested = null },
                )
            }
        }
        compose.waitForIdle()
    }

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun selectedDay(): String = compose.transcript().selectedHistoryDay("substance")
    private fun dayLine(): String? = compose.transcript().singleOrNull { it.startsWith("[substance_selected_day]") }

    @Test
    fun theDayAskedAboutIsUnnamedAndEveryOtherDayIsNamedTodayToo() {
        show()
        assertEquals("2026-09-16", selectedDay())
        assertEquals(null, dayLine())

        click("substance_history_2026-09-17")
        assertEquals("[substance_selected_day] text=\"Thu, Sep 17\"", dayLine())

        click("substance_history_2026-09-10")
        assertEquals("2026-09-10", selectedDay())
        assertEquals("[substance_selected_day] text=\"Thu, Sep 10\"", dayLine())

        click("substance_history_2026-09-16")
        assertEquals(null, dayLine())
    }

    @Test
    fun theCheckInsDayIsNamedUnlessItIsTheDayAskedAbout() {
        show()
        requested = "2026-09-14"
        compose.waitForIdle()
        assertEquals("2026-09-14", selectedDay())
        assertEquals("[substance_selected_day] text=\"Mon, Sep 14\"", dayLine())
        assertEquals(null, requested)

        requested = "2026-09-16"
        compose.waitForIdle()
        assertEquals("2026-09-16", selectedDay())
        assertEquals(null, dayLine())
    }

    /** A started day asks before it is left, and a save is stored for the day that was picked. */
    @Test
    fun aStartedDayAsksBeforeItIsLeftAndASaveGoesToThePickedDay() {
        show()
        click("substance_history_2026-09-15")
        click("substance_choice_alcohol")
        click("substance_history_2026-09-14")
        click("substance_discard_stay")
        assertEquals("2026-09-15", selectedDay())

        click("substance_save")
        assertEquals(listOf("2026-09-15"), state.substanceLogs.map { it.dateKey })

        // Saved, the day is left without a question.
        click("substance_history_2026-09-14")
        assertEquals("2026-09-14", selectedDay())
    }

    /** An untouched screen follows the day asked about and stays unnamed; a started one stays, and is named once the day asked about has moved on. */
    @Test
    fun anUntouchedScreenFollowsTheDayAskedAboutAndAStartedOneStays() {
        show()
        todayKey = "2026-09-18"
        compose.waitForIdle()
        assertEquals("2026-09-17", selectedDay())
        assertEquals(null, dayLine())

        click("substance_choice_alcohol")
        todayKey = "2026-09-19"
        compose.waitForIdle()
        assertEquals("2026-09-17", selectedDay())
        assertEquals("[substance_selected_day] text=\"Thu, Sep 17\"", dayLine())
    }
}
