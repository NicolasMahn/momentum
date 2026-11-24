package de.nicolasmahn.momentum.feature.weight

import de.nicolasmahn.momentum.domain.measurement.MeasurementValuePolicy

import de.nicolasmahn.momentum.feature.measurement.MeasurementScreen
import de.nicolasmahn.momentum.feature.measurement.MeasurementScreenError
import de.nicolasmahn.momentum.feature.measurement.MeasurementWords
import de.nicolasmahn.momentum.feature.measurement.words
import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import de.nicolasmahn.momentum.core.format.DateStyle
import de.nicolasmahn.momentum.core.format.relativeDateText
import de.nicolasmahn.momentum.core.text.UiText
import de.nicolasmahn.momentum.core.text.resolve
import de.nicolasmahn.momentum.core.ui.theme.WorkoutTrackerTheme
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.changingDay
import de.nicolasmahn.momentum.domain.measurement.MeasurementConfiguration
import de.nicolasmahn.momentum.domain.measurement.MeasurementLog
import de.nicolasmahn.momentum.domain.measurement.MeasurementStep
import de.nicolasmahn.momentum.domain.compatibility.WeightMeasurementId
import de.nicolasmahn.momentum.domain.measurement.recordsOf
import de.nicolasmahn.momentum.domain.measurement.testMadeMeasurement
import de.nicolasmahn.momentum.domain.measurement.testWeightMeasurement
import de.nicolasmahn.momentum.domain.measurement.withWeight
import de.nicolasmahn.momentum.feature.statistics.StatisticsRange
import de.nicolasmahn.momentum.feature.statistics.StatisticsScreen
import de.nicolasmahn.momentum.feature.statistics.caption
import de.nicolasmahn.momentum.feature.statistics.headline
import de.nicolasmahn.momentum.feature.statistics.statisticsSummary
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A configured measurement speaks with its configured name and unit, including Weight. The same
 * wording reaches its screen, chart, reminder reply and Statistics, in English and German.
 *
 * On screen a unit is set apart by two invisible marks; what TalkBack hears has neither.
 */
@RunWith(RobolectricTestRunner::class)
class WeightInAnotherUnitTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = "2026-10-09"
    private val inKilograms = testWeightMeasurement()
    private val inPounds = inKilograms.copy(unit = "lb", step = MeasurementStep("0.5"))
    private val withoutAUnit = inKilograms.copy(unit = "")

    /** Three days of Weight, so the chart has its trend and Statistics a change to say. */
    private val records = listOf(
        MeasurementLog(WeightMeasurementId, "2026-10-08", 9, 181.5),
        MeasurementLog(WeightMeasurementId, "2026-10-07", 2, 182.0),
        MeasurementLog(WeightMeasurementId, "2026-10-06", 1, 183.5),
    )

    private val saturday = UiText.Date(LocalDate.of(2026, 10, 3), DateStyle.WeekdayDayMonth)
    private val yesterday = relativeDateText("2026-10-02", "2026-10-03")

    private fun resources(): Resources = ApplicationProvider.getApplicationContext<Context>().resources
    private fun UiText.read() = resolve(resources())

    /** Every sentence [words] can say: on the screen, in the chart, in the reply to the reminder and in Statistics. */
    private fun everySentence(words: MeasurementWords): List<String> = listOf(
        words.fieldLabel,
        words.save,
        words.saved,
        words.remove(saturday),
        words.previous(181.5, yesterday),
        words.previousWithChange(181.5, yesterday, 0.5),
        words.previousWithChange(181.5, yesterday, -1.25),
        words.error(MeasurementScreenError.Invalid),
        words.error(MeasurementScreenError.ChangedBeforeSave),
        words.error(MeasurementScreenError.SaveFailed),
        words.error(MeasurementScreenError.ChangedBeforeRemoval),
        words.error(MeasurementScreenError.RemovalFailed),
        words.error(MeasurementScreenError.Deleted),
        words.drawn("181.5"),
        words.spoken(181.5),
        words.noneLogged,
        words.trend(181.25),
        words.chartEmpty(28),
        words.chartEmpty(1),
        words.chartLine(28, 181.5, smoothed = true),
        words.chartLine(1, 181.5, smoothed = false),
        words.chartLineBetween(28, 181.5, 180.0, 183.25, smoothed = true),
        words.chartLineBetween(1, 181.5, 180.0, 183.25, smoothed = false),
        words.selectedNone(saturday),
        words.selected(saturday, 181.5),
        words.selectedWithTrend(saturday, 181.5, 181.25),
        words.replyPrompt,
        words.replyInvalid,
        words.statisticsChange(-2.0),
        words.statisticsChange(0.5),
        words.statisticsCaption(181.5),
        words.statisticsCaption(null),
    ).map { it.read() } + listOfNotNull(words.unitBesideTheNumber)

    private fun assertNoKilograms(what: List<String>) {
        what.forEach { sentence ->
            listOf("kg", "kilogram").forEach { word -> assertFalse("\"$sentence\" says \"$word\"", sentence.lowercase().contains(word)) }
        }
    }

    /** Every configuration uses the same wording model, with its own name and unit. */
    @Test
    fun everyConfigurationUsesItsConfiguredNameAndUnit() {
        assertEquals(MeasurementWords(name = "Weight", unit = "kg", valuePolicy = MeasurementValuePolicy.Positive), inKilograms.words())
        assertEquals(MeasurementWords(name = "Weight", unit = "lb", valuePolicy = MeasurementValuePolicy.Positive), inPounds.words())
        assertEquals(MeasurementWords(name = "Weight", unit = "", valuePolicy = MeasurementValuePolicy.Positive), withoutAUnit.words())
        assertEquals(MeasurementWords(name = "Morning weight", unit = "st", valuePolicy = MeasurementValuePolicy.Positive), inKilograms.copy(label = "Morning weight", unit = "st").words())
        assertEquals(MeasurementWords(name = "Waist", unit = "kg"), testMadeMeasurement(unit = "kg").words())
    }

    /** A configured unit is an optional suffix for every measurement, without case sensitivity. */
    @Test
    fun anOptionalUnitSuffixIsAcceptedOnlyWhenItMatchesTheConfiguration() {
        listOf("82 kg", "82kg", "82,5 KG").forEach { typed -> assertTrue(typed, inKilograms.words().parse(typed) != null) }
        listOf("181.5 lb", "181.5lb", "181,5 LB").forEach { typed -> assertTrue(typed, inPounds.words().parse(typed) != null) }
        assertEquals(82.5, inKilograms.copy(unit = "KG").words().parse("82,5 kg")!!, 0.0)
        assertNull(inPounds.words().parse("82 kg"))
        assertNull(inKilograms.words().parse("82,5 lb"))
        listOf("82 kg", "82,5 lb").forEach { typed -> assertNull(typed, withoutAUnit.words().parse(typed)) }
        listOf("82" to 82.0, "181,5" to 181.5, " 181.5 " to 181.5).forEach { (typed, value) ->
            assertEquals(typed, value, inKilograms.words().parse(typed)!!, 0.0)
            assertEquals(typed, value, inPounds.words().parse(typed)!!, 0.0)
        }
    }

    @Test
    fun inPoundsUsesItsConfiguredNameAndUnitInEnglish() {
        val said = everySentence(inPounds.words())
        assertNoKilograms(said)
        assertEquals("Weight in ⁨lb⁩", said.first())
        assertTrue(said.toString(), "Previous: 181.5 ⁨lb⁩ (yesterday) · +0.5 ⁨lb⁩" in said)
        assertTrue(said.toString(), "Enter a number above zero." in said)
        assertTrue(said.toString(), "Trend of Weight for the last 28 days, ending at 181.5 lb, records between 180 and 183.25 lb" in said)
        assertTrue(said.toString(), "Selected Sat, Oct 3: 181.5 lb, trend 181.25 lb" in said)
        // The reply to its reminder asks and refuses in the field's words.
        assertEquals("Weight in ⁨lb⁩", inPounds.words().replyPrompt.read())
        assertEquals("Enter a number above zero.", inPounds.words().replyInvalid.read())
        // Statistics: the change, and the caption under it.
        assertEquals("-2 ⁨lb⁩", inPounds.words().statisticsChange(-2.0).read())
        assertEquals("+0.5 ⁨lb⁩", inPounds.words().statisticsChange(0.5).read())
        assertEquals("Weight trend · now 181.5 ⁨lb⁩", inPounds.words().statisticsCaption(181.5).read())
        assertEquals("Weight · nothing logged yet", inPounds.words().statisticsCaption(null).read())
    }

    @Test
    @Config(qualifiers = "de")
    fun inPoundsUsesItsConfiguredNameAndUnitInGerman() {
        val weight = inPounds.copy(label = "Gewicht")
        val said = everySentence(weight.words())
        assertNoKilograms(said)
        assertEquals("Gewicht in ⁨lb⁩", said.first())
        assertTrue(said.toString(), "Gib eine Zahl über null ein." in said)
        assertEquals("Gewicht in ⁨lb⁩", weight.words().replyPrompt.read())
        assertEquals("-2 ⁨lb⁩", weight.words().statisticsChange(-2.0).read())
        assertEquals("Trend für Gewicht · jetzt 181,5 ⁨lb⁩", weight.words().statisticsCaption(181.5).read())
        assertEquals("Gewicht · noch nichts erfasst", weight.words().statisticsCaption(null).read())
    }

    /** Without a unit a value is its number alone, and no sentence is left with a stray space or a dangling "in". */
    @Test
    fun withoutAUnitWeightSaysItsNumbersAlone() {
        val said = everySentence(withoutAUnit.words())
        assertNoKilograms(said)
        assertEquals("Weight", said.first())
        assertEquals("Weight", withoutAUnit.words().replyPrompt.read())
        assertEquals("-2", withoutAUnit.words().statisticsChange(-2.0).read())
        assertEquals("Weight trend · now 181.5", withoutAUnit.words().statisticsCaption(181.5).read())
        said.forEach { sentence -> assertFalse("\"$sentence\"", sentence.endsWith(" ") || sentence.contains("  ") || sentence.endsWith(" in")) }
    }

    /** Kilograms use the same generic wording, including in the reminder reply and Statistics. */
    @Test
    fun inKilogramsUsesGenericWording() {
        val words = inKilograms.words()
        assertEquals("Weight in ⁨kg⁩", words.fieldLabel.read())
        assertEquals("Save", words.save.read())
        assertEquals("82.5 kg", words.spoken(82.5).read())
        assertEquals("82.5", words.drawn("82.5").read())
        assertEquals("kg", words.unitBesideTheNumber)
        assertEquals("Enter a number above zero.", words.error(MeasurementScreenError.Invalid).read())
        assertEquals("Weight in ⁨kg⁩", words.replyPrompt.read())
        assertEquals("Enter a number above zero.", words.replyInvalid.read())
        assertEquals("-2 ⁨kg⁩", words.statisticsChange(-2.0).read())
        assertEquals("Weight trend · now 78 ⁨kg⁩", words.statisticsCaption(78.0).read())
        assertEquals("Weight · nothing logged yet", words.statisticsCaption(null).read())
    }

    @Test
    @Config(qualifiers = "de")
    fun inKilogramsUsesGenericWordingInGerman() {
        val words = inKilograms.words()
        assertEquals("Weight in ⁨kg⁩", words.fieldLabel.read())
        assertEquals("82,5 kg", words.spoken(82.5).read())
        assertEquals("Weight in ⁨kg⁩", words.replyPrompt.read())
        assertEquals("Gib eine Zahl über null ein.", words.replyInvalid.read())
        assertEquals("-2 ⁨kg⁩", words.statisticsChange(-2.0).read())
        assertEquals("Trend für Weight · jetzt 78 ⁨kg⁩", words.statisticsCaption(78.0).read())
        assertEquals("Weight · noch nichts erfasst", words.statisticsCaption(null).read())
    }

    private var state by mutableStateOf(AppState())
    private var shown by mutableStateOf<MeasurementConfiguration?>(null)
    private var clock = 1_000L

    private fun showScreen(weight: MeasurementConfiguration) {
        state = AppState(measurementLogs = records).withWeight(weight)
        shown = weight
        compose.setContent {
            WorkoutTrackerTheme {
                shown?.let { measurement ->
                    MeasurementScreen(
                        state = state,
                        measurement = measurement,
                        onDayChange = { change -> state.changingDay(change, timestampMillis = clock++) { updated -> state = updated; true } },
                        onStepChange = { false },
                        todayKey = today,
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun type(text: String) {
        compose.onNodeWithTag("measurement_value").performTextReplacement(text)
        compose.waitForIdle()
    }

    private fun said(): List<String> = compose.transcript()
    private fun field(): String = said().single { it.startsWith("[measurement_value]") }

    /**
     * Weight's screen in pounds uses the configured name and unit, and accepts that unit as an
     * optional suffix. A different configured unit is rejected and stores nothing.
     */
    @Test
    fun weightsScreenInPoundsAcceptsItsUnitSuffixAndRejectsAnotherUnit() {
        showScreen(inPounds)

        val shownInPounds = said()
        assertNoKilograms(shownInPounds)
        assertTrue(shownInPounds.toString(), "text=\"Weight in ⁨lb⁩\"" in shownInPounds)
        assertTrue(shownInPounds.toString(), "text=\"Save\"" in shownInPounds)
        assertTrue(shownInPounds.toString(), "text=\"Previous: 181.5 ⁨lb⁩ (yesterday)\"" in shownInPounds)
        assertTrue(shownInPounds.toString(), shownInPounds.any { it.startsWith("[measurement_history_chart] spoken=\"Trend of Weight for the last 28 days, ending at ") })
        assertEquals(listOf("text=\"−0.5 ⁨lb⁩\"", "text=\"+0.5 ⁨lb⁩\""), shownInPounds.filter { it.startsWith("text=\"−") || it.startsWith("text=\"+") })

        type("82 kg")
        click("measurement_save")
        assertTrue(said().toString(), "text=\"Enter a number above zero.\"" in said())
        assertEquals(records, state.measurementLogs)
        // A step from what is no number changes nothing either.
        click("measurement_increment")
        assertTrue(field(), field().startsWith("[measurement_value] field=\"82 kg\""))
        assertEquals(records, state.measurementLogs)

        type("180 lb")
        click("measurement_increment")
        assertEquals("[measurement_value] field=\"180.5\"", field())
        click("measurement_save")
        assertEquals(180.5, state.recordsOf(WeightMeasurementId).first { it.dateKey == today }.value, 0.0)
        assertNoKilograms(said())
        assertTrue(said().toString(), "spoken=\"180.5 lb\"" in said())
        // The records of the days before are the numbers they were: nothing was converted.
        assertEquals(records, state.measurementLogs.filter { it.dateKey != today })
    }

    /** The same screen in German, with the name a German installation is seeded with: no sentence on it says kilograms either. */
    @Test
    @Config(qualifiers = "de")
    fun weightsScreenInPoundsRejectsAnUnconfiguredUnitInGerman() {
        showScreen(inPounds.copy(label = "Gewicht"))

        assertNoKilograms(said())
        assertTrue(said().toString(), "text=\"Gewicht in \u2068lb\u2069\"" in said())
        assertTrue(said().toString(), "text=\"Zuvor: 181,5 \u2068lb\u2069 (gestern)\"" in said())
        type("82 kg")
        click("measurement_save")
        assertTrue(said().toString(), "text=\"Gib eine Zahl über null ein.\"" in said())
        assertNoKilograms(said().filterNot { it.startsWith("[measurement_value]") })
        assertEquals(records, state.measurementLogs)
    }

    /** A configured unit suffix is accepted for Weight as it is for any other measurement. */
    @Test
    fun weightsScreenInKilogramsUsesGenericWordsAndAcceptsItsUnitSuffix() {
        showScreen(inKilograms)

        assertTrue(said().toString(), "text=\"Weight in ⁨kg⁩\"" in said())
        assertTrue(said().toString(), "text=\"Save\"" in said())
        assertEquals(listOf("text=\"−0.1 ⁨kg⁩\"", "text=\"+0.1 ⁨kg⁩\""), said().filter { it.startsWith("text=\"−") || it.startsWith("text=\"+") })

        type("82 kg")
        click("measurement_save")
        assertEquals(82.0, state.recordsOf(WeightMeasurementId).first { it.dateKey == today }.value, 0.0)
        assertTrue(said().toString(), "spoken=\"82 kg\"" in said())
    }

    /** Steps of 0.5 and of 2.5: the buttons add and take that, and a result is written as every number is. */
    @Test
    fun theButtonsAddAndTakeAStepOfAnySize() {
        showScreen(inKilograms.copy(step = MeasurementStep("2.5")))
        assertEquals(listOf("text=\"−2.5 ⁨kg⁩\"", "text=\"+2.5 ⁨kg⁩\""), said().filter { it.startsWith("text=\"−") || it.startsWith("text=\"+") })
        type("80")
        click("measurement_increment")
        assertEquals("[measurement_value] field=\"82.5\"", field())
        click("measurement_decrement")
        click("measurement_decrement")
        assertEquals("[measurement_value] field=\"77.5\"", field())

        // A step of 0.05 from a number typed with three decimals is rounded to two, as a step always was.
        shown = inKilograms.copy(step = MeasurementStep("0.05"))
        compose.waitForIdle()
        assertEquals(listOf("text=\"−0.05 ⁨kg⁩\"", "text=\"+0.05 ⁨kg⁩\""), said().filter { it.startsWith("text=\"−") || it.startsWith("text=\"+") })
        type("80.001")
        click("measurement_increment")
        assertEquals("[measurement_value] field=\"80.05\"", field())
    }

    /** Two records four weeks apart: with fewer than three the chart draws them as they are, so the change is theirs. */
    private val twoRecords = listOf(MeasurementLog(WeightMeasurementId, today, 9, 181.5), MeasurementLog(WeightMeasurementId, "2026-09-15", 1, 183.5))

    private fun showStatistics(weight: MeasurementConfiguration) {
        state = AppState(measurementLogs = twoRecords).withWeight(weight)
        compose.setContent { WorkoutTrackerTheme { StatisticsScreen(state = state, todayKey = today, initialRange = StatisticsRange.FourWeeks) } }
        compose.waitForIdle()
    }

    /** Statistics in pounds shows the configured unit in its change, caption and spoken summary. */
    @Test
    fun statisticsSaysWeightInItsUnitAndNoKilograms() {
        showStatistics(inPounds)

        val shownInPounds = said()
        assertNoKilograms(shownInPounds)
        assertTrue(shownInPounds.toString(), "text=\"-2 ⁨lb⁩\"" in shownInPounds)
        assertTrue(shownInPounds.toString(), "text=\"Weight trend · now 181.5 ⁨lb⁩\"" in shownInPounds)
        assertTrue(shownInPounds.toString(), shownInPounds.any { "spoken=\"Weight for the last" in it && "ending at 181.5 lb, records between 181.5 and 183.5 lb" in it })
    }

    @Test
    @Config(qualifiers = "de")
    fun statisticsSaysWeightInItsUnitAndNoKilogramsInGerman() {
        showStatistics(inPounds.copy(label = "Gewicht"))

        val shownInPounds = said()
        assertNoKilograms(shownInPounds)
        assertTrue(shownInPounds.toString(), "text=\"Trend für Gewicht · jetzt 181,5 ⁨lb⁩\"" in shownInPounds)
    }

    /** Statistics uses generic units in its spoken summary as well. */
    @Test
    fun statisticsInKilogramsUsesGenericWording() {
        showStatistics(inKilograms)

        assertTrue(said().toString(), "text=\"-2 ⁨kg⁩\"" in said())
        assertTrue(said().toString(), "text=\"Weight trend · now 181.5 ⁨kg⁩\"" in said())
        assertTrue(said().toString(), said().any { "spoken=\"Weight for the last" in it && "ending at 181.5 kg, records between 181.5 and 183.5 kg" in it })
    }

    /** The numbers Statistics works out are the records', whatever Weight's unit and step are: nothing is converted and nothing rounded by the step. */
    @Test
    fun statisticsNumbersDoNotFollowTheUnitOrTheStep() {
        val inKilogramsSummary = statisticsSummary(AppState(measurementLogs = twoRecords).withWeight(inKilograms), today, StatisticsRange.FourWeeks)
        val inPoundsSummary = statisticsSummary(AppState(measurementLogs = twoRecords).withWeight(inPounds), today, StatisticsRange.FourWeeks)
        assertEquals(inKilogramsSummary.consistency, inPoundsSummary.consistency)
        assertEquals(inKilogramsSummary.measurements.single().window, inPoundsSummary.measurements.single().window)
        assertEquals(inKilogramsSummary.measurements.single().latestValue, inPoundsSummary.measurements.single().latestValue)
        assertEquals(inKilogramsSummary.measurements.single().change, inPoundsSummary.measurements.single().change)
        assertEquals("-2 ⁨kg⁩", inKilogramsSummary.measurements.single().headline(inKilograms.words()).read())
        assertEquals("Weight trend · now 181.5 ⁨kg⁩", inKilogramsSummary.measurements.single().caption(inKilograms.words()).read())
        assertEquals("-2 ⁨lb⁩", inPoundsSummary.measurements.single().headline(inPounds.words()).read())
        assertEquals("Weight trend · now 181.5 ⁨lb⁩", inPoundsSummary.measurements.single().caption(inPounds.words()).read())
    }
}
