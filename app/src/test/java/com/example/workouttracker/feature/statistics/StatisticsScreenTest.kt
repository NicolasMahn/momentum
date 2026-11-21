package com.example.workouttracker.feature.statistics

import com.example.workouttracker.domain.compatibility.ReadingTimeId
import com.example.workouttracker.domain.time.TimeLog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import com.example.workouttracker.core.ui.theme.WorkoutTrackerTheme
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.TrackerRef
import com.example.workouttracker.domain.TrackerTypeId
import com.example.workouttracker.domain.trackerRef
import com.example.workouttracker.domain.measurement.withWeight
import com.example.workouttracker.domain.mo.MoDayMark
import com.example.workouttracker.domain.mo.MoDayRecord
import com.example.workouttracker.domain.compatibility.MoNeed
import com.example.workouttracker.domain.mo.MoStreak
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import com.example.workouttracker.domain.measurement.MeasurementRecord
import com.example.workouttracker.domain.measurement.asMeasurementLogs
import com.example.workouttracker.feature.weight.transcript
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Statistics says what it counts: one line at the top, each task's tokens as the days they
 * covered, a plain mark for a day a token no task of Mo's protected, and the protected days
 * counted under the calendar and in what TalkBack hears of it. A view that is gone has no row.
 */
@RunWith(RobolectricTestRunner::class)
class StatisticsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.parse("2026-10-16")
    private val tuesday = LocalDate.parse("2026-10-13")
    private val thursday = LocalDate.parse("2026-10-15")

    /**
     * Weight's token covered Tuesday and a made view's token Thursday, the streak's reading of
     * the week; Weight was hidden from Wednesday. Every other day was kept.
     */
    private val streak = MoStreak(
        days = 4,
        freezes = 0,
        freezeCapacity = 3,
        week = List(7) { MoDayMark.Upcoming },
        weekCanEarnFreeze = true,
        history = (0L..3L).map { tuesday.minusDays(1).plusDays(it) }.map { day ->
            when (day) {
                tuesday -> MoDayRecord(day, MoDayMark.Protected, coveredTrackers = setOf(MoNeed.Weight.view.trackerRef))
                thursday -> MoDayRecord(day, MoDayMark.Protected, coveredTrackers = setOf(TrackerRef(TrackerTypeId("measurement"), "msr_k2p9")))
                else -> MoDayRecord(day, MoDayMark.Perfect)
            }
        },
    )
    private val hiddenFromWednesday = FeatureVisibilitySettings(disabledFeatures = emptySet())
        .withVisibility(MoNeed.Weight.view, visible = false, today = tuesday.plusDays(1))

    private var state by mutableStateOf(AppState())

    private fun show(shown: AppState, visibility: FeatureVisibilitySettings = FeatureVisibilitySettings(disabledFeatures = emptySet())) {
        state = shown
        compose.setContent {
            WorkoutTrackerTheme {
                StatisticsScreen(state = state, todayKey = today.toString(), featureVisibility = visibility, streak = streak, initialRange = StatisticsRange.FourWeeks)
            }
        }
    }

    private fun said(text: String) = assertTrue(compose.transcript().toString(), compose.transcript().any { text in it })

    /** The line that tried to say what the numbers count is gone; the calendar's own key says it in German. */
    @Test
    @Config(qualifiers = "de")
    fun noScopeLineAndTheKeySpeaksGerman() {
        show(AppState().withWeight())
        compose.onNodeWithTag("statistics_scope").assertDoesNotExist()
        said("2 geschützte Tage")
    }

    /**
     * Tuesday's Weight token stays in the key after Weight was hidden on Wednesday, counted as the
     * day it covered; the made view's token is named by the plain mark; and the days protected are
     * counted, once each, under the calendar and in what is heard of it.
     */
    @Test
    fun theKeyCountsDaysCoveredAndProtectedWhateverIsShownToday() {
        show(AppState().withWeight(), hiddenFromWednesday)

        compose.onNodeWithTag("statistics_measurement_weight").assertDoesNotExist()
        said("1 day covered")
        said("protected")
        said("2 protected days")
        compose.onNodeWithContentDescription("2 protected days", substring = true).assertExists()
    }

    /** A view that is gone, Reading here, has no row and Weight no block, though their records would fill them. */
    @Test
    fun aViewThatIsGoneHasNoRowAndWeightNoBlock() {
        val records = AppState(
            timeLogs = listOf(TimeLog(ReadingTimeId, today.toString(), 0L, minutes = 20)),
            measurementLogs = listOf(MeasurementRecord(today.toString(), 0L, 80.0)).asMeasurementLogs("weight"),
        )
        show(records)

        compose.onNodeWithTag("statistics_consistency_time_reading").assertDoesNotExist()
        compose.onNodeWithTag("statistics_consistency_measurement_weight").assertDoesNotExist()
        compose.onNodeWithTag("statistics_measurement_weight").assertDoesNotExist()

        state = records.withWeight()
        compose.waitForIdle()
        compose.onNodeWithTag("statistics_consistency_measurement_weight").assertExists()
        compose.onNodeWithTag("statistics_measurement_weight").assertExists()
        compose.onNodeWithTag("statistics_consistency_time_reading").assertDoesNotExist()
    }
}
