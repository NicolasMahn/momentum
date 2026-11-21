package com.example.workouttracker.app

import com.example.workouttracker.domain.measurement.recordsOf

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import com.example.workouttracker.core.ui.theme.WorkoutTrackerTheme
import com.example.workouttracker.core.time.currentDateKey
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.measurement.MeasurementLog
import com.example.workouttracker.domain.compatibility.WeightMeasurementId
import com.example.workouttracker.domain.LoggedViewKind
import com.example.workouttracker.domain.LoggedViewRef
import com.example.workouttracker.feature.weight.transcript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.example.workouttracker.domain.measurement.withWeight

/** Weight's tile opens Weight's screen, in Weight's words. */
@RunWith(RobolectricTestRunner::class)
class WeightRouteTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun weightsTileOpensItsScreenInItsWords() {
        compose.setContent { WorkoutTrackerTheme { MomentumApp(state = AppState().withWeight(), onStateChange = { true }, onActivitySave = ActivitySaveNotExpected) } }
        compose.onNodeWithTag("home_measurement_weight").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithTag("measurement_value").performTextReplacement("81.7")
        val said = compose.transcript()
        assertTrue(said.toString(), "text=\"Weight in ⁨kg⁩\"" in said)
        assertTrue(said.toString(), "[measurement_value] field=\"81.7\"" in said)
        assertTrue(said.toString(), "text=\"Save\"" in said)
    }

    /**
     * A host that gives the app nothing but a way to save its state, as a preview and the
     * instrumented flows do, still has Weight's save: the day is changed in the state on show and
     * handed over whole, and a weight saved for today tells the reminders that day was logged.
     */
    @Test
    fun aSaveReachesAHostThatOnlySavesItsStateAndAnswersTheReminder() {
        var state by mutableStateOf(AppState().withWeight())
        val saved = mutableListOf<AppState>()
        val answered = mutableListOf<Pair<LoggedViewRef, String>>()
        compose.setContent {
            WorkoutTrackerTheme {
                MomentumApp(
                    state = state,
                    onStateChange = { updated ->
                        saved += updated
                        state = updated
                        true
                    },
                    onActivitySave = ActivitySaveNotExpected,
                    onDayLogged = { view, dateKey, _, _ -> answered += view to dateKey },
                )
            }
        }
        compose.onNodeWithTag("home_measurement_weight").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithTag("measurement_value").performTextReplacement("81.7 kg")
        compose.onNodeWithTag("measurement_save").performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(listOf(81.7), saved.single().recordsOf("weight").map { it.value })
        assertEquals(listOf(MeasurementLog(WeightMeasurementId, currentDateKey(), saved.single().measurementLogs.single().timestampMillis, 81.7)), saved.single().measurementLogs)
        assertEquals(listOf(LoggedViewRef(LoggedViewKind.Measurement, WeightMeasurementId) to currentDateKey()), answered)
        assertTrue(compose.transcript().toString(), "text=\"Saved\"" in compose.transcript())

        // Removing it hands the state over again and tells the day changed, which brings no reminder back.
        compose.onNodeWithTag("measurement_delete").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithTag("measurement_delete").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertEquals(emptyList<MeasurementLog>(), saved.last().measurementLogs)
        assertEquals(2, saved.size)
        assertEquals(2, answered.size)
    }
}
