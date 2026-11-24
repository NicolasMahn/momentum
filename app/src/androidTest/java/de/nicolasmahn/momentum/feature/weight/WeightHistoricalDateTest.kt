package de.nicolasmahn.momentum.feature.weight

import de.nicolasmahn.momentum.domain.measurement.recordsOf

import de.nicolasmahn.momentum.feature.measurement.MeasurementScreen
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import de.nicolasmahn.momentum.TestWeightMeasurement
import de.nicolasmahn.momentum.core.format.formatMetricNumber
import de.nicolasmahn.momentum.core.locale.locale
import de.nicolasmahn.momentum.core.time.nowMillis
import de.nicolasmahn.momentum.core.ui.theme.WorkoutTrackerTheme
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.changingDay
import de.nicolasmahn.momentum.domain.measurement.MeasurementLog
import de.nicolasmahn.momentum.testfixtures.WeightMeasurementId
import de.nicolasmahn.momentum.testing.tapChartDay
import de.nicolasmahn.momentum.domain.measurement.asMeasurementLogs
import de.nicolasmahn.momentum.domain.measurement.MeasurementRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Weight on days before [Today], each picked by a tap on its place in the chart. */
class WeightHistoricalDateTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun historicalDateTargetsOnlyThatDaysWeightAfterExplicitSave() {
        val existing = MeasurementRecord("2026-09-16", 1L, 84.0)
        val saves = mutableListOf<AppState>()
        showWeight(AppState(measurementLogs = listOf(existing).asMeasurementLogs("weight"))) {
            saves.add(it)
            true
        }

        selectDate("2026-09-15")
        composeRule.onNodeWithTag("measurement_value").performTextReplacement("83.5")
        composeRule.onNodeWithTag("measurement_increment").performClick()
        composeRule.runOnIdle { assertTrue(saves.isEmpty()) }
        composeRule.onNodeWithTag("measurement_save").performScrollTo().performClick()

        composeRule.runOnIdle {
            val byDate = saves.single().recordsOf("weight").associateBy { it.dateKey }
            assertEquals(existing, byDate["2026-09-16"])
            assertEquals(83.6, byDate.getValue("2026-09-15").value, 0.00001)
            assertEquals(2, byDate.size)
        }
        composeRule.onNodeWithTag("measurement_selected_day").performScrollTo().assertTextEquals("Tue, Sep 15")
    }

    @Test
    fun typingKeyboardDoneAndIncrementDoNotPersistDraft() {
        val saves = mutableListOf<AppState>()
        showWeight(AppState()) {
            saves.add(it)
            true
        }

        selectDate("2026-09-15")
        composeRule.onNodeWithTag("measurement_value").performScrollTo().performTextReplacement("83.5")
        composeRule.runOnIdle { assertTrue(saves.isEmpty()) }
        composeRule.onNodeWithTag("measurement_value").performClick().performImeAction()
        composeRule.runOnIdle { assertTrue(saves.isEmpty()) }
        composeRule.onNodeWithTag("measurement_increment").performClick()
        composeRule.onNodeWithTag("measurement_value").assertEditableText(shownWeight(83.6))
        composeRule.runOnIdle { assertTrue(saves.isEmpty()) }
    }

    @Test
    fun changingDateWithUnsavedWeightCanBeCanceledOrDiscarded() {
        val first = MeasurementRecord("2026-09-15", 1L, 83.5)
        val second = MeasurementRecord("2026-09-16", 2L, 84.0)
        val saves = mutableListOf<AppState>()
        showWeight(AppState(measurementLogs = listOf(first, second).asMeasurementLogs("weight"))) {
            saves.add(it)
            true
        }

        selectDate(first.dateKey)
        composeRule.onNodeWithTag("measurement_value").performScrollTo().performTextReplacement("83.7")
        selectDate(second.dateKey)
        composeRule.onNodeWithTag("measurement_discard_stay").performClick()
        composeRule.onNodeWithTag("measurement_selected_day").performScrollTo().assertTextEquals("Tue, Sep 15")
        composeRule.onNodeWithTag("measurement_value").assertEditableText("83.7")

        selectDate(second.dateKey)
        composeRule.onNodeWithTag("measurement_discard_confirm").performClick()
        composeRule.onNodeWithTag("measurement_selected_day").performScrollTo().assertTextEquals("Wed, Sep 16")
        composeRule.onNodeWithTag("measurement_value").assertEditableText("84")
        composeRule.runOnIdle { assertTrue(saves.isEmpty()) }
    }

    @Test
    fun deletingSavedDateTakesTwoTapsAndPreservesOtherDates() {
        val target = MeasurementRecord("2026-09-15", 1L, 83.5)
        val other = MeasurementRecord("2026-09-16", 2L, 84.0)
        val saves = mutableListOf<AppState>()
        showWeight(AppState(measurementLogs = listOf(target, other).asMeasurementLogs("weight"))) {
            saves.add(it)
            true
        }

        // An empty day keeps the button in place so the row never moves; it is simply disabled.
        selectDate("2026-09-14")
        composeRule.onNodeWithTag("measurement_delete").performScrollTo().assertIsNotEnabled()

        selectDate(target.dateKey)
        composeRule.onNodeWithTag("measurement_delete").performScrollTo().performClick()
        composeRule.runOnIdle { assertTrue(saves.isEmpty()) }
        composeRule.onNodeWithTag("measurement_value").assertEditableText(shownWeight(83.5))

        composeRule.onNodeWithTag("measurement_delete").performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(other), saves.single().recordsOf("weight"))
        }
        composeRule.onNodeWithTag("measurement_selected_day").assertTextEquals("Tue, Sep 15")
        composeRule.onNodeWithTag("measurement_delete").assertIsNotEnabled()
    }

    /** A save from the screen touches Weight's day alone: another measurement's records of that day stay as they were. */
    @Test
    fun savingLeavesAnotherMeasurementsRecordsOfTheDayAsTheyWere() {
        val other = MeasurementLog("msr_k2p9", "2026-09-15", 9L, 61.0)
        val saves = mutableListOf<AppState>()
        showWeight(AppState(measurementLogs = listOf(other, MeasurementLog(WeightMeasurementId, "2026-09-15", 1L, 83.5), other))) {
            saves.add(it)
            true
        }

        selectDate("2026-09-15")
        composeRule.onNodeWithTag("measurement_value").performTextReplacement("83.7")
        composeRule.onNodeWithTag("measurement_save").performScrollTo().performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(other, other), saves.single().measurementLogs.filter { it.measurementId == other.measurementId })
            assertEquals(83.7, saves.single().recordsOf("weight").single().value, 0.0)
        }
    }

    /** A removal from the screen touches Weight's day alone: another measurement's records of that day stay as they were. */
    @Test
    fun deletingLeavesAnotherMeasurementsRecordsOfTheDayAsTheyWere() {
        val other = MeasurementLog("msr_k2p9", "2026-09-15", 9L, 61.0)
        val saves = mutableListOf<AppState>()
        showWeight(AppState(measurementLogs = listOf(other, MeasurementLog(WeightMeasurementId, "2026-09-15", 1L, 83.5), other))) {
            saves.add(it)
            true
        }

        selectDate("2026-09-15")
        composeRule.onNodeWithTag("measurement_delete").performScrollTo().performClick()
        composeRule.runOnIdle { assertTrue(saves.isEmpty()) }
        composeRule.onNodeWithTag("measurement_delete").performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(other, other), saves.single().measurementLogs)
        }
    }

    @Test
    fun failedDeletePreservesSavedRecordAndUnsavedDraft() {
        val target = MeasurementRecord("2026-09-15", 1L, 83.5)
        val other = MeasurementRecord("2026-09-16", 2L, 84.0)
        val initialState = AppState(measurementLogs = listOf(target, other).asMeasurementLogs("weight"))
        val attempts = mutableListOf<AppState>()
        showWeight(initialState) { updated ->
            attempts.add(updated)
            false
        }

        selectDate(target.dateKey)
        composeRule.onNodeWithTag("measurement_value").performTextReplacement("83.7")
        composeRule.onNodeWithTag("measurement_delete").performScrollTo().performClick()
        composeRule.onNodeWithTag("measurement_delete").performClick()

        composeRule.onNodeWithTag("measurement_selected_day").assertTextEquals("Tue, Sep 15")
        composeRule.onNodeWithTag("measurement_value").assertEditableText("83.7")
        composeRule.runOnIdle {
            assertEquals(listOf(other), attempts.single().recordsOf("weight"))
        }

        // A second delete must still target the saved record after the rejected write.
        composeRule.onNodeWithTag("measurement_delete").performScrollTo().performClick()
        composeRule.onNodeWithTag("measurement_delete").performClick()
        composeRule.runOnIdle {
            assertEquals(2, attempts.size)
            assertEquals(listOf(other), attempts.last().recordsOf("weight"))
        }
    }

    @Test
    fun failedSaveRetainsDateAndDraftForRetry() {
        val other = MeasurementRecord("2026-09-16", 1L, 84.0)
        val attempts = mutableListOf<AppState>()
        showWeight(AppState(measurementLogs = listOf(other).asMeasurementLogs("weight"))) {
            attempts.add(it)
            false
        }

        selectDate("2026-09-15")
        composeRule.onNodeWithTag("measurement_value").performTextReplacement("83.5")
        composeRule.onNodeWithTag("measurement_save").performScrollTo().performClick()
        composeRule.onNodeWithTag("measurement_selected_day").assertTextEquals("Tue, Sep 15")
        composeRule.onNodeWithTag("measurement_value").assertEditableText("83.5")
        composeRule.onNodeWithTag("measurement_save").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(2, attempts.size)
            attempts.forEach { attempt ->
                assertEquals(other, attempt.recordsOf("weight").single { it.dateKey == other.dateKey })
                assertEquals(83.5, attempt.recordsOf("weight").single { it.dateKey == "2026-09-15" }.value, 0.0)
            }
        }
    }

    /** Picks a day as a person does: by a tap on its place in the chart. */
    private fun selectDate(date: String) = composeRule.tapChartDay(date, Today)

    /** Shows Weight over [initialState] given Weight's configuration, which every write asks for. */
    private fun showWeight(initialState: AppState, save: (AppState) -> Boolean) {
        composeRule.setContent {
            WorkoutTrackerTheme {
                var state by remember { mutableStateOf(initialState.copy(measurements = listOf(TestWeightMeasurement))) }
                MeasurementScreen(
                    state = state,
                    measurement = TestWeightMeasurement,
                    onStepChange = { false },
                    todayKey = Today,
                    // The state on show is all there is here, so a change is decided against it and saved whole.
                    onDayChange = { change ->
                        state.changingDay(change, nowMillis()) { updated ->
                            val saved = save(updated)
                            if (saved) state = updated
                            saved
                        }
                    },
                )
            }
        }
    }
}

/** A fixed day, so the days these tests pick are always among the 28 the chart shows. */
private const val Today = "2026-09-17"

private fun SemanticsNodeInteraction.assertEditableText(expected: String): SemanticsNodeInteraction =
    assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(expected)))

/** The field writes a weight in the device language's decimal style. */
private fun shownWeight(kg: Double): String = formatMetricNumber(
    kg,
    InstrumentationRegistry.getInstrumentation().targetContext.resources.locale,
)
