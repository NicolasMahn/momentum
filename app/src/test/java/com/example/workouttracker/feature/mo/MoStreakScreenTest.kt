package com.example.workouttracker.feature.mo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import com.example.workouttracker.core.ui.theme.WorkoutTrackerTheme
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.TrackerRef
import com.example.workouttracker.domain.compatibility.CareChecklistId
import com.example.workouttracker.domain.care.careState
import com.example.workouttracker.domain.checklist.leaveRewards
import com.example.workouttracker.domain.holiday.DayRange
import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.domain.mo.MoMood
import com.example.workouttracker.domain.compatibility.MoNeed
import com.example.workouttracker.domain.mo.moMood
import com.example.workouttracker.domain.mo.only
import com.example.workouttracker.domain.mo.sickToday
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import com.example.workouttracker.domain.time.TimeLog
import com.example.workouttracker.domain.time.withReading
import com.example.workouttracker.domain.activity.withActivity
import com.example.workouttracker.domain.compatibility.ReadingTimeId
import com.example.workouttracker.domain.supplement.withSupplements
import com.example.workouttracker.domain.trackerRef
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Test-only adapters let the screen receive bank metadata while its standings stay TrackerRef keyed. */
internal fun testTrackerBankPresentations(
    mood: MoMood,
    onReservationChange: (TrackerRef, Boolean) -> Unit = { _, _ -> },
): List<TrackerBankPresentation> = MoNeed.entries.map { need ->
    val ref = need.view.trackerRef
    TrackerBankPresentation(
        ref = ref,
        label = mood.trackerLabels[ref] ?: need.name,
        testTag = trackerBankTag(need),
        inMinutes = need == MoNeed.Reading,
        reservation = TrackerBankReservation(
            selected = ref in mood.selectedTrackerChoices,
            count = mood.tokensSetAside(ref),
            allowed = mood.canSetAside(ref),
            onChange = { selected -> onReservationChange(ref, selected) },
        ),
        restDays = if (need == MoNeed.Activity) mood.plannedRestDays() else null,
    )
}

internal fun trackerBankTag(need: MoNeed): String = "streak_bank_${need.name.lowercase(Locale.ROOT)}"

@RunWith(RobolectricTestRunner::class)
class MoStreakScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.parse("2026-10-14")
    private var shown by mutableStateOf<MoMood?>(null)
    private var reservationChange: Pair<TrackerRef, Boolean>? = null

    private fun render() {
        compose.setContent {
            WorkoutTrackerTheme {
                shown?.let { mood ->
                    MoStreakScreen(
                        mood = mood,
                        banks = testTrackerBankPresentations(mood) { need, selected ->
                            reservationChange = need to selected
                        },
                    )
                }
            }
        }
    }

    @Test
    fun aBankIsShownOnlyWhileItsTrackerHasAStanding() {
        val visibility = FeatureVisibilitySettings()
        val state = careState().withSupplements()
        val takingPart = moMood(state, visibility, today)
        assertTrue(MoNeed.Care.view.trackerRef in takingPart.streak.standings)
        assertTrue(MoNeed.Supplements.view.trackerRef in takingPart.streak.standings)

        shown = takingPart
        render()
        compose.onNodeWithTag(trackerBankTag(MoNeed.Care)).assertExists()
        compose.onNodeWithTag(trackerBankTag(MoNeed.Supplements)).assertExists()

        val careLeft = leaveRewards(state, CareChecklistId, today)
        val afterCareLeft = moMood(careLeft, visibility, today)
        assertFalse(MoNeed.Care.view.trackerRef in afterCareLeft.streak.standings)
        assertTrue(MoNeed.Supplements.view.trackerRef in afterCareLeft.streak.standings)
        shown = afterCareLeft
        compose.waitForIdle()

        compose.onNodeWithTag(trackerBankTag(MoNeed.Care)).assertDoesNotExist()
        compose.onNodeWithTag(trackerBankTag(MoNeed.Supplements)).assertExists()
    }

    @Test
    fun aPressableBankUsesItsReservationCallback() {
        val state = readingState()
        val originalLogs = state.timeLogs
        val mood = moMood(state, only(MoNeed.Reading), sunday)
        val reading = MoNeed.Reading.view.trackerRef
        assertTrue(mood.canSetAside(reading))
        shown = mood
        render()

        compose.onNodeWithTag(trackerBankTag(MoNeed.Reading))
            .performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(reading to true, reservationChange)
        assertEquals(originalLogs, state.timeLogs)
    }

    @Test
    fun aSickDayKeepsTheBankAndItsRecordsUntouchedAndCannotReserveIt() {
        val state = readingState()
        val originalLogs = state.timeLogs
        val visibility = only(MoNeed.Reading)
        val ref = MoNeed.Reading.view.trackerRef
        val before = moMood(state, visibility, sunday)
        val sickDays = HolidaySchedule().withSickDays(DayRange(sunday, sunday))
        val sick = moMood(state, visibility, sunday, holidays = sickDays)

        assertTrue(sick.streak.sickToday)
        assertTrue(ref in sick.streak.standings)
        assertEquals(before.streak.standings.getValue(ref).passes, sick.streak.standings.getValue(ref).passes)
        assertFalse(sick.canSetAside(ref))

        shown = sick
        render()
        val week = compose.onNode(hasContentDescription("This week:", substring = true))
            .fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString()
        assertTrue(week, "sick" in week)
        assertFalse(
            compose.onNodeWithTag(trackerBankTag(MoNeed.Reading))
                .fetchSemanticsNode().config.contains(SemanticsActions.OnClick),
        )
        assertEquals(originalLogs, state.timeLogs)
        assertNull(reservationChange)
    }

    private val monday = LocalDate.parse("2026-10-05")
    private val sunday get() = monday.plusDays(13)

    private fun readingState(): AppState = AppState(
        timeLogs = listOf(
            TimeLog(ReadingTimeId, monday.plusDays(1).toString(), 1L, 120),
            TimeLog(ReadingTimeId, monday.plusDays(8).toString(), 8L, 40),
        ),
    ).withReading().withActivity()
}
