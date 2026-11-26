package de.nicolasmahn.momentum.feature.weight

import de.nicolasmahn.momentum.domain.measurement.MeasurementValuePolicy

import de.nicolasmahn.momentum.domain.measurement.recordsOf

import de.nicolasmahn.momentum.feature.measurement.MeasurementHistory
import de.nicolasmahn.momentum.feature.measurement.MeasurementHistoryWindow
import de.nicolasmahn.momentum.feature.measurement.MeasurementScreen
import de.nicolasmahn.momentum.feature.measurement.MeasurementTrendChart
import de.nicolasmahn.momentum.feature.measurement.chartSpacing
import de.nicolasmahn.momentum.feature.measurement.measurementHistoryWindow
import de.nicolasmahn.momentum.feature.measurement.measurementValueText
import de.nicolasmahn.momentum.feature.measurement.words
import de.nicolasmahn.momentum.feature.checklist.presentation
import android.content.Context
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import de.nicolasmahn.momentum.core.locale.locale
import de.nicolasmahn.momentum.core.ui.theme.WorkoutTrackerTheme
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.changingDay
import de.nicolasmahn.momentum.domain.compatibility.WeightMeasurementId
import de.nicolasmahn.momentum.domain.measurement.replaceMeasurementForDate
import de.nicolasmahn.momentum.domain.measurement.testWeightMeasurement
import de.nicolasmahn.momentum.domain.compatibility.weightMeasurement
import de.nicolasmahn.momentum.domain.measurement.MeasurementRecord
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every word Weight's screen shows and every sentence it speaks, in English and German, written
 * down from the screen itself: each node's text, its field value, what TalkBack says for it and
 * its state, in the order the screen holds them. The expected files were written by the screen as
 * it was before it took a measurement, so a change that rewords Weight, drops a sentence or moves
 * one fails here. The screen is shown here as the app shows Weight's: the measurement screen with
 * Weight's stored configuration.
 *
 * It covers the record states of `WeightRecordReadersTest` on every day that test asks about, what
 * the screen answers to input it refuses, to a save or a removal that fails and to a record that
 * changed elsewhere, the question before unsaved input is dropped, the chart as Statistics and the
 * gallery show it, whose sentences the screen itself never reaches, and the large number as it is
 * drawn, which has no text of its own for TalkBack.
 *
 * The expected files are never rewritten to make a change pass. `WEIGHT_WORDS_WRITE=1` writes them
 * for a wording that changes on purpose; what a run produced is in `build/weight-words` either way.
 */
@RunWith(RobolectricTestRunner::class)
class WeightScreenWordsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun english() = compareWithTheRecordedWords("en")

    @Test
    @Config(qualifiers = "de")
    fun german() = compareWithTheRecordedWords("de")

    private fun compareWithTheRecordedWords(language: String) {
        val actual = transcript()
        val expectedFile = File("src/test/resources/weight/screen-words-$language.txt")
        File("build/weight-words").apply { mkdirs() }.resolve("screen-words-$language.txt").writeText(actual)
        if (System.getenv("WEIGHT_WORDS_WRITE") == "1") {
            expectedFile.parentFile?.mkdirs()
            expectedFile.writeText(actual)
        }
        assertEquals(expectedFile.readText(), actual)
    }

    /** What one scene shows: the screen over records that a save may change, or the chart alone. */
    private sealed interface Scene {
        class Screen(val records: Records) : Scene

        /** The value, the chart and its legend as the gallery shows them for a window that is no average. */
        class History(val window: MeasurementHistoryWindow, val selectedDateKey: String) : Scene

        /** The chart as a picture, as Statistics shows it: no day is picked. */
        class Chart(val window: MeasurementHistoryWindow) : Scene
    }

    /** The records the screen is shown, and what happens to a save it asks for. */
    private class Records(initial: AppState) {
        var state by mutableStateOf(initial)
        var refuseSaves = false

        /** The time a save is stored at: later than any record of the states here, so a saved number is its day's latest. */
        var clock = 1_000L

        /** A record of [dateKey] stored behind the screen's back, as a notification's reply stores one. */
        fun storeElsewhere(dateKey: String, timestampMillis: Long, weightKg: Double) {
            state = state.copy(measurementLogs = replaceMeasurementForDate(state.measurementLogs, WeightMeasurementId, dateKey, timestampMillis, weightKg))
        }

        val weights: List<MeasurementRecord> get() = state.recordsOf("weight")
    }

    /** Weight as it starts, in kilograms and steps of 0.1: the words and the chart spacing are asked of it, as the app asks them. */
    private val weight = testWeightMeasurement()

    private var scene by mutableStateOf<Scene?>(null)
    private var sceneNumber by mutableIntStateOf(0)
    private val lines = mutableListOf<String>()

    @Composable
    private fun Show(scene: Scene) {
        when (scene) {
            is Scene.Screen -> MeasurementScreen(
                state = scene.records.state,
                measurement = requireNotNull(scene.records.state.weightMeasurement),
                onStepChange = { false },
                onDayChange = { change ->
                    scene.records.state.changingDay(change, timestampMillis = scene.records.clock++) { updated ->
                        (!scene.records.refuseSaves).also { saved -> if (saved) scene.records.state = updated }
                    }
                },
                todayKey = WeightRecordStatesToday,
            )

            is Scene.History -> MeasurementHistory(
                window = scene.window,
                selectedDateKey = scene.selectedDateKey,
                onDateSelected = {},
                words = weight.words(),
                chartSpacing = weight.chartSpacing,
            )

            is Scene.Chart -> MeasurementTrendChart(
                window = scene.window,
                selectedDateKey = null,
                onDateSelected = null,
                colors = weight.presentation().colors,
                motionEnabled = false,
                words = weight.words(),
                minimumSpacing = weight.chartSpacing,
                modifier = Modifier.height(180.dp),
            )
        }
    }

    private fun transcript(): String {
        compose.setContent {
            WorkoutTrackerTheme {
                scene?.let { shown -> key(sceneNumber) { Show(shown) } }
            }
        }

        WeightRecordStates.forEach { recorded ->
            open(recorded.state)
            // The chart ends today, so the day after it is one the screen cannot be put on.
            WeightRecordStatesDays.filter { it <= WeightRecordStatesToday }.forEach { day ->
                select(day)
                say("${recorded.name} / $day")
            }
        }
        val several = WeightRecordStates.first().state

        open(several)
        click("measurement_range_threemonths")
        say("the last three months")
        click("measurement_range_all")
        say("everything")

        open(several)
        type("abc")
        click("measurement_save")
        say("saving what is no number")
        type("0")
        click("measurement_save")
        say("saving zero")
        type("-3")
        click("measurement_save")
        say("saving a number below zero")

        open(WeightRecordStates.last().state)
        click("measurement_decrement")
        say("one step down from an empty field")
        click("measurement_increment")
        say("one step up from an empty field")

        open(several).let { records ->
            type("83.5 KG")
            say("typed, not saved")
            click("measurement_save")
            say("saved with a trailing KG")
            assertEquals(MeasurementRecord(WeightRecordStatesToday, records.weights.first().timestampMillis, 83.5), records.weights.first())
            click("measurement_increment")
            say("one step up from the saved weight")
            click("measurement_decrement")
            click("measurement_decrement")
            say("one step down from the saved weight")
        }

        open(several).let { records ->
            type("87.45")
            click("measurement_save")
            say("saved with two decimal places")
            assertEquals(87.45, records.weights.first().value, 0.0)
            type("87.456")
            click("measurement_save")
            say("saved with three decimal places")
            assertEquals(87.456, records.weights.first().value, 0.0)
            type("87,5")
            click("measurement_save")
            say("saved with a decimal comma")
            assertEquals(87.5, records.weights.first().value, 0.0)
            type("87.25")
            click("measurement_save")
            say("saved with a decimal point")
            assertEquals(87.25, records.weights.first().value, 0.0)
        }

        open(several).let { records ->
            records.refuseSaves = true
            type("83.5")
            click("measurement_save")
            say("a save that fails")
            select("2026-10-08")
            say("the question before unsaved input is dropped")
            click("measurement_discard_stay")
            say("kept editing")
            select("2026-10-08")
            click("measurement_discard_confirm")
            say("unsaved input dropped")
            click("measurement_delete")
            say("removal armed")
            click("measurement_delete")
            say("a removal that fails")
            records.refuseSaves = false
            click("measurement_delete")
            click("measurement_delete")
            say("removed")
            assertEquals(listOf("2026-10-07", "2026-10-06"), records.weights.map { it.dateKey })
        }

        open(several).let { records ->
            select("2026-10-08")
            type("83")
            records.storeElsewhere("2026-10-08", 20, 84.0)
            click("measurement_save")
            say("saving over a record that changed elsewhere")
            click("measurement_delete")
            click("measurement_delete")
            say("removing a record that changed elsewhere")
            assertEquals(MeasurementRecord("2026-10-08", 20, 84.0), records.weights.first())
        }

        open(several).let { records ->
            type("83")
            records.storeElsewhere(WeightRecordStatesToday, 20, 84.0)
            click("measurement_save")
            say("saving a first record over one stored elsewhere")
            assertEquals(MeasurementRecord(WeightRecordStatesToday, 20, 84.0), records.weights.first())
        }

        WeightRecordStates.forEach { recorded ->
            val logs = recorded.state.recordsOf("weight")
            show(Scene.History(measurementHistoryWindow(logs, WeightRecordStatesToday), "2026-10-08"))
            say("${recorded.name} / the history without an average, 2026-10-08")
            show(Scene.History(measurementHistoryWindow(logs, WeightRecordStatesToday), WeightRecordStatesToday))
            say("${recorded.name} / the history without an average, 2026-10-09")
            show(Scene.Chart(measurementHistoryWindow(logs, WeightRecordStatesToday, dayCount = 28)))
            say("${recorded.name} / the chart in Statistics")
            show(Scene.Chart(measurementHistoryWindow(logs, WeightRecordStatesToday, dayCount = 1)))
            say("${recorded.name} / the chart of one day")
            show(Scene.Chart(measurementHistoryWindow(logs, "2026-10-08", dayCount = 1)))
            say("${recorded.name} / the chart of one recorded day")
            show(Scene.Chart(measurementHistoryWindow(logs, "2026-10-12", dayCount = 3)))
            say("${recorded.name} / the chart of three later days")
            show(Scene.Chart(measurementHistoryWindow(logs, WeightRecordStatesToday, valuePolicy = MeasurementValuePolicy.Positive)))
            say("${recorded.name} / the screen's chart with no day picked")
        }

        // The large number is drawn without a text of its own for TalkBack, so it is asked for here.
        val resources = ApplicationProvider.getApplicationContext<Context>().resources
        lines += "== the number as it is drawn, at rest and while it counts up"
        listOf(82.0, 81.5, 87.45, 87.456, 80.88, 78.58, 0.0, -1.5, 0.04, 123456.789).forEach { value ->
            lines += "$value: \"${measurementValueText(weight.words(), value, settled = true, resources, resources.locale)}\" \"${measurementValueText(weight.words(), value, settled = false, resources, resources.locale)}\""
        }
        return lines.joinToString("\n", postfix = "\n")
    }

    private fun show(next: Scene) {
        scene = next
        sceneNumber += 1
        compose.waitForIdle()
    }

    private fun open(state: AppState): Records = Records(state).also { show(Scene.Screen(it)) }

    /** Picks a day as a person does: by a tap on its place in the chart. */
    private fun select(dateKey: String) = compose.tapChartDay(dateKey, WeightRecordStatesToday)

    private fun type(text: String) {
        compose.onNodeWithTag("measurement_value").performTextReplacement(text)
        compose.waitForIdle()
    }

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    /** Writes down everything on show under [title], windows of a dialog included. */
    private fun say(title: String) {
        lines += "== $title"
        lines += compose.transcript()
        lines += ""
    }
}
