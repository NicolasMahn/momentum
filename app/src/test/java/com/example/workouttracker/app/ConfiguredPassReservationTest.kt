package com.example.workouttracker.app

import com.example.workouttracker.persistence.CurrentTrackerStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import com.example.workouttracker.core.time.currentHabitDate
import com.example.workouttracker.core.ui.theme.WorkoutTrackerTheme
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.ref
import com.example.workouttracker.domain.checklist.ChecklistRewards
import com.example.workouttracker.domain.measurement.MeasurementBank
import com.example.workouttracker.domain.measurement.MeasurementLog
import com.example.workouttracker.domain.measurement.testMadeMeasurement
import com.example.workouttracker.domain.mo.TrackerMoPolicy
import com.example.workouttracker.domain.trackerRef
import com.example.workouttracker.persistence.PassChoices
import com.example.workouttracker.persistence.PreferencesPassChoiceRepository
import com.example.workouttracker.persistence.TrackerStringStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ConfiguredPassReservationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun aConfiguredMeasurementReservesThroughTheSharedGenericStore() {
        val today = currentHabitDate()
        val measurement = testMadeMeasurement(id = "practice").copy(
            rewards = MeasurementBank.Standard(ChecklistRewards(maxPasses = 3, everyKeptDays = 1)),
            mo = TrackerMoPolicy(enabled = true),
        )
        val ref = measurement.ref.trackerRef
        val state = AppState(
            measurements = listOf(measurement),
            measurementLogs = listOf(MeasurementLog(measurement.id, today.minusDays(1).toString(), 1L, 0.0)),
        )
        val repository = PreferencesPassChoiceRepository(CurrentTrackerStore())
        var choices by mutableStateOf<PassChoices?>(repository.load())
        compose.setContent {
            WorkoutTrackerTheme {
                MomentumApp(
                    state = state,
                    rewardStart = today.minusDays(7),
                    onStateChange = { error("A reservation cannot change recorded facts") },
                    onActivitySave = ActivitySaveNotExpected,
                    passChoices = choices,
                    onPassChoicesChange = { updated ->
                        repository.save(updated).also { if (it) choices = updated }
                    },
                )
            }
        }
        compose.onNodeWithTag("home_streak").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNodeWithTag("streak_measurement_practice").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(setOf(ref), repository.load()!!.forDay(today))
        }
        compose.onNodeWithTag("streak_measurement_practice").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(repository.load()!!.forDay(today).isEmpty()) }
    }

}
