package de.nicolasmahn.momentum.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import de.nicolasmahn.momentum.core.time.currentHabitDate
import de.nicolasmahn.momentum.core.ui.theme.WorkoutTrackerTheme
import de.nicolasmahn.momentum.domain.mo.TrackerMoPolicy
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.DayProgress
import de.nicolasmahn.momentum.domain.checklist.WhenAway
import de.nicolasmahn.momentum.domain.holiday.DayRange
import de.nicolasmahn.momentum.domain.holiday.HolidaySchedule
import de.nicolasmahn.momentum.domain.measurement.MeasurementEdit
import de.nicolasmahn.momentum.domain.measurement.MeasurementLog
import de.nicolasmahn.momentum.domain.measurement.applyingMeasurementEdits
import de.nicolasmahn.momentum.domain.measurement.testMadeMeasurement
import de.nicolasmahn.momentum.domain.measurement.testWeightMeasurement
import de.nicolasmahn.momentum.domain.trackerRef
import de.nicolasmahn.momentum.domain.ref
import de.nicolasmahn.momentum.domain.mo.habitStreak
import de.nicolasmahn.momentum.domain.mo.only
import de.nicolasmahn.momentum.domain.settings.FeatureVisibilitySettings
import de.nicolasmahn.momentum.feature.weight.transcript
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A measurement a person made wears the badge a made checklist does once it counts toward the
 * streak: the socket while today is due and open, the coin once today is logged, and nothing while
 * it is outside the streak, hidden, away and not due, or today is a sick day. Home reads it from
 * the streak, so the two never disagree, and speaks a checklist's words for it.
 */
@RunWith(RobolectricTestRunner::class)
class HomeMeasurementBadgeTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = currentHabitDate()
    private val waist = testMadeMeasurement().copy(mo = TrackerMoPolicy(enabled = true))
    private val socket = DayProgress(done = 0, parts = 1)
    private val coin = DayProgress(done = 1, parts = 1)

    /** The waist with [edits] applied [on] a day, logged on each of the seven days before today, and today or not. */
    private fun edited(vararg edits: MeasurementEdit, on: LocalDate, logged: Boolean): AppState = AppState(
        measurements = listOf(testWeightMeasurement(), waist),
        measurementLogs = (if (logged) 0L..7L else 1L..7L).map { MeasurementLog(waist.id, today.minusDays(it).toString(), it, 91.5) },
    ).applyingMeasurementEdits(waist.id, edits.toList(), on)

    /** The waist in the streak since a week ago, logged today or not. */
    private fun joined(logged: Boolean, vararg more: MeasurementEdit) = edited(MeasurementEdit.JoinRewards, *more, on = today.minusDays(7), logged = logged)

    private val sickToday = HolidaySchedule().withSickDays(DayRange(today, today))
    private val awayToday = HolidaySchedule(listOf(DayRange(today, today)))
    private val hidden = FeatureVisibilitySettings().hiding(waist.ref)

    /** Home's reading for the waist, checked against the walk: a socket exactly when the walk holds it open, a coin exactly when it has it kept. */
    private fun badge(state: AppState, daysOff: HolidaySchedule = HolidaySchedule(), visibility: FeatureVisibilitySettings = only()): DayProgress? {
        val streak = habitStreak(state, visibility, today, daysOff)
        val progress = loggedProgressOn(streak)[waist.ref]
        assertEquals(streak.standings[waist.ref.trackerRef]?.openToday == true, progress == socket)
        assertEquals(streak.standings[waist.ref.trackerRef]?.keptToday == true, progress == coin)
        return progress
    }

    @Test fun anOpenDayWearsTheSocketAndALoggedDayTheCoin() {
        assertEquals(socket, badge(joined(logged = false)))
        assertEquals(coin, badge(joined(logged = true)))
        // Joined today, today is the first day it is due.
        assertEquals(socket, badge(edited(MeasurementEdit.JoinRewards, on = today, logged = false)))
        // Away and still due, it is an ordinary day.
        assertEquals(socket, badge(joined(logged = false), awayToday))
        assertEquals(coin, badge(joined(logged = true), awayToday))
    }

    @Test fun outsideTheStreakOrHiddenItWearsNothing() {
        listOf(true, false).forEach { logged ->
            assertNull(badge(edited(on = today, logged = logged)))
            // Left today, today is no longer in its period.
            assertNull(badge(joined(logged).applyingMeasurementEdits(waist.id, listOf(MeasurementEdit.LeaveRewards), today)))
            assertNull(badge(joined(logged), visibility = hidden))
        }
    }

    @Test fun onADayItAsksNothingOfItWearsNothingWhateverWasLogged() {
        listOf(true, false).forEach { logged ->
            assertNull(badge(joined(logged), sickToday))
            assertNull(badge(joined(logged, MeasurementEdit.SetWhenAway(WhenAway.Excused)), awayToday))
            // Sick wins over away, whatever the holiday rule says.
            assertNull(badge(joined(logged), HolidaySchedule(awayToday.holidays, sickToday.sickDays)))
        }
        // Not due from tomorrow, today's away day is still due.
        val notDueFromTomorrow = joined(logged = false).applyingMeasurementEdits(waist.id, listOf(MeasurementEdit.SetWhenAway(WhenAway.Excused)), today.plusDays(1))
        assertEquals(socket, badge(notDueFromTomorrow, awayToday))
    }

    private var state by mutableStateOf(edited(on = today, logged = true))
    private var visibility by mutableStateOf(FeatureVisibilitySettings())
    private var daysOff by mutableStateOf(HolidaySchedule())

    private fun show(shown: AppState, featureVisibility: FeatureVisibilitySettings = FeatureVisibilitySettings(), holidaySchedule: HolidaySchedule = HolidaySchedule()) {
        state = shown
        visibility = featureVisibility
        daysOff = holidaySchedule
        compose.waitForIdle()
    }

    private val tile = "home_measurement_${waist.id}"

    /** What TalkBack hears of the tile's badge, or null while it wears none. */
    private fun spokenBadge(): String? {
        compose.onNodeWithTag(tile).assertExists()
        return listOf("Still open today", "Done today", "Kept today").singleOrNull { words ->
            compose.onAllNodes(hasContentDescription(words) and hasAnyAncestor(hasTestTag(tile)), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** The tile through the app: each state named above wears what it says, in a checklist's words, and the token page has its row while it is shown and in a period. */
    @Test fun theTileSpeaksAChecklistsWordsAndTheTokenPageHasItsRow() {
        compose.setContent {
            WorkoutTrackerTheme { MomentumApp(state = state, onStateChange = { state = it; true }, onActivitySave = ActivitySaveNotExpected, featureVisibility = visibility, holidaySchedule = daysOff) }
        }

        show(edited(on = today, logged = true))
        assertEquals("Done today", spokenBadge())

        show(joined(logged = false))
        assertEquals("Still open today", spokenBadge())

        show(joined(logged = true))
        assertEquals("Done today", spokenBadge())

        // Hidden, it has no tile to wear anything.
        show(joined(logged = false), featureVisibility = hidden)
        compose.onNodeWithTag(tile).assertDoesNotExist()
        show(joined(logged = false), holidaySchedule = sickToday)
        assertNull(spokenBadge())
        show(joined(logged = false, MeasurementEdit.SetWhenAway(WhenAway.Excused)), holidaySchedule = awayToday)
        assertNull(spokenBadge())
        // Reward participation and Mo enrollment are independent.
        show(joined(logged = false).applyingMeasurementEdits(waist.id, listOf(MeasurementEdit.LeaveRewards), today))
        assertEquals("Still open today", spokenBadge())

        // The token page: its bank under its name, with the days toward the next token. The
        // seven kept days since it joined earned its second token, and today is not kept yet.
        show(joined(logged = false))
        compose.onNodeWithTag("home_streak").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        val row = "streak_measurement_${waist.id}"
        compose.onNodeWithTag(row).assertExists()
        val openBank = hasContentDescription("Waist: 2 tokens. next token: 0/7 days")
        compose.onNodeWithTag(row, useUnmergedTree = true).assert(openBank or hasAnyAncestor(openBank))
        show(joined(logged = true))
        val recordedBank = hasContentDescription("Waist: 2 tokens. next token: 1/7 days")
        compose.onNodeWithTag(row, useUnmergedTree = true).assert(recordedBank or hasAnyAncestor(recordedBank))

        // The row stays on a sick day and on an away day it is not due on, and goes once it is hidden or left.
        show(joined(logged = false), holidaySchedule = sickToday)
        compose.onNodeWithTag(row).assertExists()
        show(joined(logged = false, MeasurementEdit.SetWhenAway(WhenAway.Excused)), holidaySchedule = awayToday)
        compose.onNodeWithTag(row).assertExists()
        show(joined(logged = false), featureVisibility = hidden)
        compose.onNodeWithTag(row).assertDoesNotExist()
        show(joined(logged = false).applyingMeasurementEdits(waist.id, listOf(MeasurementEdit.LeaveRewards), today))
        compose.onNodeWithTag(row).assertDoesNotExist()
        show(edited(on = today, logged = true))
        compose.onNodeWithTag(row).assertDoesNotExist()
    }
}
