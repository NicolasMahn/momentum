package com.example.workouttracker.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import com.example.workouttracker.core.time.currentHabitDate
import com.example.workouttracker.core.ui.theme.WorkoutTrackerTheme
import com.example.workouttracker.domain.mo.TrackerMoPolicy
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.DayProgress
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.holiday.DayRange
import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.domain.mo.habitStreak
import com.example.workouttracker.domain.mo.only
import com.example.workouttracker.domain.ref
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import com.example.workouttracker.domain.time.TimeEdit
import com.example.workouttracker.domain.time.TimeLog
import com.example.workouttracker.domain.time.TimeLogOrder
import com.example.workouttracker.domain.time.applyingTimeEdits
import com.example.workouttracker.domain.time.testMadeTime
import com.example.workouttracker.domain.time.withReading
import com.example.workouttracker.domain.activity.withActivity
import com.example.workouttracker.feature.weight.transcript
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A time view a person made wears Reading's kind of badge once it counts toward the streak: the
 * coin once today holds minutes, whatever its week still asks for, the socket on a Sunday whose
 * week is judged and still short, and nothing otherwise. Home's tile is stricter on that Sunday:
 * it is done only once the week's goal is reached. Outside the streak or hidden it wears
 * nothing. Home reads it from the streak, and the token page has its row in minutes.
 */
@RunWith(RobolectricTestRunner::class)
class HomeTimeBadgeTest {
    @get:Rule
    val compose = createComposeRule()

    private val sunday = LocalDate.parse("2026-10-18")
    private val wednesday = sunday.minusDays(4)
    private val piano = testMadeTime().copy(mo = TrackerMoPolicy(enabled = true))
    private val socket = DayProgress(done = 0, parts = 1)
    private val coin = DayProgress(done = 1, parts = 1)

    /** The piano with [minutes] logged on the days given, in the streak since [since] or outside it. */
    private fun piano(vararg minutes: Pair<LocalDate, Int>, since: LocalDate? = sunday.minusDays(20), more: List<TimeEdit> = emptyList()): AppState =
        AppState(timeLogs = minutes.map { (day, logged) -> TimeLog(piano.id, day.toString(), 1, logged) }.sortedWith(TimeLogOrder))
            .withReading().withActivity().let { it.copy(time = it.time + piano) }
            .let { state -> if (since == null) state else state.applyingTimeEdits(piano.id, listOf(TimeEdit.JoinRewards) + more, since) }

    private fun badge(state: AppState, today: LocalDate, daysOff: HolidaySchedule = HolidaySchedule(), visibility: FeatureVisibilitySettings = only()): DayProgress? =
        loggedProgressOn(habitStreak(state, visibility, today, daysOff))[piano.ref]

    @Test fun aDayWithMinutesWearsTheCoinAndAShortSundayTheSocket() {
        // In the week, a day is done by its own minutes and never open.
        assertNull(badge(piano(), wednesday))
        assertEquals(coin, badge(piano(wednesday to 5), wednesday))
        assertNull(badge(piano(wednesday.minusDays(1) to 5), wednesday))

        // On its Sunday a short week is open, until today holds minutes.
        assertEquals(socket, badge(piano(wednesday to 45), sunday))
        assertEquals(coin, badge(piano(wednesday to 45, sunday to 5), sunday))
        // A week that reached its goal before today asks nothing more and today holds nothing.
        assertNull(badge(piano(wednesday to 60), sunday))
        assertEquals(coin, badge(piano(wednesday to 45, sunday to 15), sunday))
    }

    @Test fun outsideTheStreakHiddenOrInAWeekThatIsNotWholeItIsNeverOpen() {
        // Outside the streak and hidden it wears nothing, minutes or not.
        listOf(piano(since = null), piano(sunday to 5, since = null)).forEach { assertNull(badge(it, sunday)) }
        val hidden = only().hiding(piano.ref)
        listOf(piano(), piano(sunday to 5)).forEach { assertNull(badge(it, sunday, visibility = hidden)) }

        // Joined on Friday, its first Sunday is not judged; minutes logged that day still show.
        assertNull(badge(piano(since = sunday.minusDays(2)), sunday))
        assertEquals(coin, badge(piano(sunday to 5, since = sunday.minusDays(2)), sunday))
        // Nor is a sick Sunday, or an away one it is not due on.
        assertNull(badge(piano(), sunday, HolidaySchedule().withSickDays(DayRange(sunday, sunday))))
        assertNull(badge(piano(more = listOf(TimeEdit.SetWhenAway(WhenAway.Excused))), sunday, HolidaySchedule(listOf(DayRange(sunday, sunday)))))
        assertEquals(socket, badge(piano(), sunday, HolidaySchedule(listOf(DayRange(sunday, sunday)))))
    }

    private val today = currentHabitDate()
    private var state by mutableStateOf(piano(since = null))

    private fun show(shown: AppState) {
        state = shown
        compose.waitForIdle()
    }

    private val tile = "home_time_${piano.id}"

    /** What TalkBack hears of the tile's badge, or null while it wears none. */
    private fun spokenBadge(): String? {
        compose.onNodeWithTag(tile).assertExists()
        return listOf("Still open today", "Done today", "Kept today").singleOrNull { words ->
            compose.onAllNodes(hasContentDescription(words) and hasAnyAncestor(hasTestTag(tile)), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * The tile through the app, on the real day: done once today holds minutes while it counts,
     * nothing outside the streak, and the socket only where today is a Sunday. The token page has
     * its row, the bank under its name with this week's minutes toward the next token.
     */
    @Test fun theTileIsDoneByTodaysMinutesAndTheTokenPageHasItsRowInMinutes() {
        compose.setContent { WorkoutTrackerTheme { MomentumApp(state = state, onStateChange = { state = it; true }, onActivitySave = ActivitySaveNotExpected) } }
        val weeksAgo = today.minusDays(20)

        show(piano(today to 20, since = null))
        assertEquals("Done today", spokenBadge())

        // In the streak a Sunday judges the week, and 20 of its 60 minutes leave it short.
        show(piano(today to 20, since = weeksAgo))
        assertEquals(if (today.dayOfWeek == DayOfWeek.SUNDAY) "Still open today" else "Done today", spokenBadge())

        // Before its first factual record there is no Mo task to call overdue.
        show(piano(since = weeksAgo))
        assertNull(spokenBadge())

        compose.onNodeWithTag("home_streak").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        val row = "streak_time_${piano.id}"
        compose.onNodeWithTag(row).assertExists()
        assertTrue(compose.transcript().toString(), "[$row] spoken=\"Piano: 1 token. next token: 0/60 min this week\"" in compose.transcript())
        show(piano(today to 20, since = weeksAgo))
        assertTrue(compose.transcript().toString(), "[$row] spoken=\"Piano: 1 token. next token: 20/60 min this week\"" in compose.transcript())
        // Its second token of the week is the next one once the first goal is reached.
        show(piano(today to 75, since = weeksAgo))
        assertTrue(compose.transcript().toString(), "[$row] spoken=\"Piano: 1 token. next token: 75/120 min this week\"" in compose.transcript())

        // Outside the streak it has no row.
        show(piano(today to 20, since = null))
        compose.onNodeWithTag(row).assertDoesNotExist()
    }
}
