package com.example.workouttracker.feature.weight

import com.example.workouttracker.persistence.CurrentTrackerStore
import com.example.workouttracker.domain.measurement.MeasurementValuePolicy

import com.example.workouttracker.feature.measurement.MeasurementScreen
import com.example.workouttracker.feature.measurement.words
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import com.example.workouttracker.app.MeasurementCommands
import com.example.workouttracker.core.ui.theme.WorkoutTrackerTheme
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.changingDay
import com.example.workouttracker.domain.measurement.MeasurementConfiguration
import com.example.workouttracker.domain.measurement.MeasurementLog
import com.example.workouttracker.domain.measurement.MeasurementStep
import com.example.workouttracker.domain.compatibility.WeightMeasurementId
import com.example.workouttracker.domain.measurement.MeasurementRecord
import com.example.workouttracker.domain.measurement.recordsOf
import com.example.workouttracker.domain.measurement.testMadeMeasurement
import com.example.workouttracker.domain.measurement.testWeightMeasurement
import com.example.workouttracker.persistence.TrackerStringStore
import com.example.workouttracker.persistence.TrackerUpdateResult
import com.example.workouttracker.persistence.trackerRepository
import com.example.workouttracker.reminder.MeasurementReplyCoordinator
import com.example.workouttracker.reminder.MeasurementReplyResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The measurement screen for a measurement a person made, and for two measurements side by side:
 * its own words with its name and unit, its step on the buttons, numbers read without Weight's
 * "kg" and shown as Weight's are, each measurement reading and writing its own records alone, and
 * a draft that belongs to the measurement's ID and the selected day.
 */
@RunWith(RobolectricTestRunner::class)
class MeasurementScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = "2026-10-09"
    private val weight = testWeightMeasurement()
    private val waist = testMadeMeasurement()
    private val mood = testMadeMeasurement(id = "msr_m7xd", label = "Mood", unit = "", step = "1")

    /** Weight, Waist and Mood logged on the same days, with a record of a measurement nothing knows among them. */
    private val logged = AppState(
        measurements = listOf(weight, waist, mood),
        measurementLogs = listOf(
            MeasurementLog(waist.id, "2026-10-08", 9, 92.0),
            MeasurementLog(WeightMeasurementId, "2026-10-08", 9, 82.0),
            MeasurementLog(mood.id, "2026-10-08", 9, 7.0),
            MeasurementLog("msr_gone", "2026-10-08", 99, 1000.0),
            MeasurementLog(waist.id, "2026-10-07", 2, 91.5),
            MeasurementLog(WeightMeasurementId, "2026-10-07", 2, 80.0),
            MeasurementLog(mood.id, "2026-10-07", 2, 6.0),
            MeasurementLog(waist.id, "2026-10-06", 1, 91.0),
            MeasurementLog(WeightMeasurementId, "2026-10-06", 1, 78.0),
            MeasurementLog(mood.id, "2026-10-06", 1, 5.0),
        ),
    )

    private var state by mutableStateOf(logged)
    private var shown by mutableStateOf<MeasurementConfiguration?>(null)
    private var todayKey by mutableStateOf(today)

    /** How many steps the screen handed over to be stored. */
    private var stepStores = 0

    /** The time a save is stored at, later than every record here. */
    private var clock = 1_000L

    private fun show(measurement: MeasurementConfiguration, records: AppState = logged) {
        state = records
        shown = measurement
        compose.setContent {
            WorkoutTrackerTheme {
                shown?.let { measurement ->
                    MeasurementScreen(
                        state = state,
                        measurement = measurement,
                        onDayChange = { change -> state.changingDay(change, timestampMillis = clock++) { updated -> state = updated; true } },
                        onStepChange = { step ->
                            val changed = measurement.copy(step = step)
                            state = state.copy(measurements = state.measurements.map { if (it.id == changed.id) changed else it })
                            shown = changed
                            stepStores += 1
                            true
                        },
                        todayKey = todayKey,
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    /** Picks a day as a person does: by a tap on its place in the chart. */
    private fun select(dateKey: String) = compose.tapChartDay(dateKey, todayKey)

    private fun type(text: String) {
        compose.onNodeWithTag("measurement_value").performTextReplacement(text)
        compose.waitForIdle()
    }

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    /** What the screen shows and says, the two marks that set a unit apart written as ⁨ and ⁩. */
    private fun said(): List<String> = compose.transcript().map { it.replace('⁨', '⁨').replace('⁩', '⁩') }
    private fun field(): String = said().single { it.startsWith("[measurement_value]") }

    @Test
    fun aMadeMeasurementsScreenSpeaksItsOwnWordsWithItsNameAndItsUnit() {
        show(waist)
        assertEquals(
            listOf(
                "[measurement_value] field=\"92\"",
                "text=\"Waist in ⁨cm⁩\"",
                "text=\"−0.1 ⁨cm⁩\"",
                "text=\"+0.1 ⁨cm⁩\"",
                "spoken=\"Change the step\"",
                "[measurement_save] state=\"Not completed\"",
                "text=\"Save\"",
                "[measurement_delete] spoken=\"Remove the entry for Fri, Oct 9\" disabled",
                "text=\"Previous: 92 ⁨cm⁩ (yesterday)\"",
                "text=\"Fri, Oct 9\"",
                "[measurement_selected_note] text=\"Nothing logged\"",
                "[measurement_history_chart] spoken=\"Trend of Waist for the last 28 days, ending at 91.13 cm, records between 91 and 92 cm. Selected Fri, Oct 9: nothing logged\"",
                "text=\"Sep 12\"",
                "text=\"Sep 26\"",
                "text=\"Oct 9\"",
                "[measurement_range_fourweeks] selected",
                "text=\"4W\"",
                "[measurement_range_threemonths] not selected",
                "text=\"3M\"",
                "[measurement_range_all] not selected",
                "text=\"All\"",
            ),
            said(),
        )

        select("2026-10-08")
        val day = said()
        // Another day than today is named above the number.
        assertEquals("[measurement_selected_day] text=\"Thu, Oct 8\"", day.first())
        assertTrue(day.toString(), "[measurement_save] state=\"Completed\" disabled" in day)
        assertTrue(day.toString(), "text=\"Saved\"" in day)
        assertTrue(day.toString(), "[measurement_delete] spoken=\"Remove the entry for Thu, Oct 8\"" in day)
        assertTrue(day.toString(), "text=\"Previous: 91.5 ⁨cm⁩ (yesterday) · +0.5 ⁨cm⁩\"" in day)
        // The large number is spoken with its unit as typed, and nothing else on its line is spoken a second time.
        assertTrue(day.toString(), "spoken=\"92 cm\"" in day)
        assertFalse(day.toString(), "text=\"cm\"" in day)
        assertTrue(day.toString(), "[measurement_selected_note] text=\"Trend 91.13 ⁨cm⁩\"" in day)
        assertTrue(day.toString(), day.any { "Selected Thu, Oct 8: 92 cm, trend 91.13 cm" in it })
    }

    @Test
    @Config(qualifiers = "de")
    fun aMadeMeasurementWithoutAUnitSpeaksGermanWithNoStraySpace() {
        show(mood)
        select("2026-10-08")
        assertEquals(
            listOf(
                "[measurement_selected_day] text=\"Do., 8. Okt.\"",
                "[measurement_value] field=\"7\"",
                "text=\"Mood\"",
                "text=\"−1\"",
                "text=\"+1\"",
                "spoken=\"Schrittweite ändern\"",
                "[measurement_save] state=\"Erledigt\" disabled",
                "text=\"Gespeichert\"",
                "[measurement_delete] spoken=\"Eintrag für Do., 8. Okt. entfernen\"",
                "text=\"Zuvor: 6 (gestern) · +1\"",
                "spoken=\"7\"",
                "text=\"Do., 8. Okt.\"",
                "[measurement_selected_note] text=\"Trend 5,26\"",
                "[measurement_history_chart] spoken=\"Trend für Mood in den letzten 28 Tagen, zuletzt 5,26, Einträge zwischen 5 und 7. Ausgewählt Do., 8. Okt.: 7, Trend 5,26\"",
                "text=\"12. Sept.\"",
                "text=\"26. Sept.\"",
                "text=\"9. Okt.\"",
                "[measurement_range_fourweeks] selected",
                "text=\"4 W\"",
                "[measurement_range_threemonths] not selected",
                "text=\"3 M\"",
                "[measurement_range_all] not selected",
                "text=\"Alle\"",
            ),
            said(),
        )
    }

    /**
     * The step is changed on the screen as Settings changes it: ✓ hands over a step written with
     * either separator, one that is none is refused in the field's label and kept open, and the
     * buttons add the new step once it is stored.
     */
    @Test
    fun theStepIsChangedInAPopOutWhereTheButtonsWere() {
        show(waist)
        click("measurement_step")
        compose.onNodeWithTag("measurement_increment").assertDoesNotExist()
        compose.onNodeWithTag("measurement_step_field").performTextReplacement("0")
        click("measurement_step_save")
        assertEquals(0, stepStores)
        assertTrue(said().any { it.startsWith("[measurement_step_field]") && "Above 0, up to 1000" in it })

        compose.onNodeWithTag("measurement_step_field").performTextReplacement("0,5")
        click("measurement_step_save")
        assertEquals(1, stepStores)
        assertEquals(MeasurementStep("0.5"), state.measurements.single { it.id == waist.id }.step)
        compose.onNodeWithTag("measurement_step_field").assertDoesNotExist()
        click("measurement_increment")
        assertEquals("[measurement_value] field=\"92.5\"", field())
    }

    /** The step is what the two buttons add, and it changes neither what can be typed nor how a number is shown. */
    @Test
    fun theStepDecidesTheButtonsAndNothingElse() {
        fun buttons() = said().filter { it.startsWith("text=\"−") || it.startsWith("text=\"+") }
        show(waist)
        assertEquals(listOf("text=\"−0.1 ⁨cm⁩\"", "text=\"+0.1 ⁨cm⁩\""), buttons())
        click("measurement_increment")
        assertEquals("[measurement_value] field=\"92.1\"", field())

        // The same measurement in steps of 1 and of 0.01: the number on show stays as it was written.
        shown = waist.copy(step = MeasurementStep("1"))
        compose.waitForIdle()
        assertEquals(listOf("text=\"−1 ⁨cm⁩\"", "text=\"+1 ⁨cm⁩\""), buttons())
        assertEquals("[measurement_value] field=\"92.1\"", field())
        click("measurement_increment")
        assertEquals("[measurement_value] field=\"93.1\"", field())
        shown = waist.copy(step = MeasurementStep("0.01"))
        compose.waitForIdle()
        assertEquals(listOf("text=\"−0.01 ⁨cm⁩\"", "text=\"+0.01 ⁨cm⁩\""), buttons())
        click("measurement_decrement")
        assertEquals("[measurement_value] field=\"93.09\"", field())

        // In steps of 1 a number with decimals is typed, saved and shown like any other.
        shown = waist.copy(step = MeasurementStep("1"))
        type("87.45")
        click("measurement_save")
        assertEquals("[measurement_value] field=\"87.45\"", field())
        assertEquals(87.45, state.recordsOf(waist.id).first().value, 0.0)
        assertTrue(said().toString(), "spoken=\"87.45 cm\"" in said())
    }

    /** Typed with more digits than are shown, a number is stored as typed and rounded for display as every number is. */
    @Test
    fun aNumberIsStoredAsTypedAndShownAsEveryNumberIs() {
        show(waist)
        type("87.456")
        click("measurement_save")
        assertEquals(87.456, state.recordsOf(waist.id).first().value, 0.0)
        assertEquals("[measurement_value] field=\"87.46\"", field())
        assertTrue(said().toString(), "spoken=\"87.46 cm\"" in said())
        type("87,5")
        click("measurement_save")
        assertEquals(87.5, state.recordsOf(waist.id).first().value, 0.0)
        assertEquals("[measurement_value] field=\"87.5\"", field())
    }

    @Test
    @Config(qualifiers = "de")
    fun underGermanACommaAndAPointAreBothRead() {
        show(waist)
        type("87,45")
        click("measurement_save")
        assertEquals(87.45, state.recordsOf(waist.id).first().value, 0.0)
        assertEquals("[measurement_value] field=\"87,45\"", field())
        type("88.25")
        click("measurement_save")
        assertEquals(88.25, state.recordsOf(waist.id).first().value, 0.0)
        assertEquals("[measurement_value] field=\"88,25\"", field())
        assertTrue(said().toString(), "text=\"Zuvor: 92 ⁨cm⁩ (gestern) · -3,75 ⁨cm⁩\"" in said())
    }

    @Test
    fun genericScreenSavesNegativeAndZeroValuesAndAdjustsAcrossZero() {
        show(mood)
        val otherRecords = logged.measurementLogs.filterNot { it.measurementId == mood.id }
        type("-1")
        click("measurement_save")
        assertEquals(-1.0, state.recordsOf(mood.id).first().value, 0.0)
        click("measurement_increment")
        assertEquals("[measurement_value] field=\"0\"", field())
        click("measurement_save")
        assertEquals(0.0, state.recordsOf(mood.id).first().value, 0.0)
        click("measurement_decrement")
        click("measurement_save")
        assertEquals(-1.0, state.recordsOf(mood.id).first().value, 0.0)
        assertEquals(otherRecords, state.measurementLogs.filterNot { it.measurementId == mood.id })
        listOf("NaN", "Infinity", "1e309").forEach { typed ->
            type(typed)
            click("measurement_save")
            assertTrue("text=\"Enter a number.\"" in said())
            assertEquals(-1.0, state.recordsOf(mood.id).first().value, 0.0)
        }
    }

    @Test
    fun weightScreenKeepsItsPositiveEntryAndAdjustmentRules() {
        show(weight)
        listOf("0", "-1").forEach { typed ->
            type(typed)
            click("measurement_save")
            assertTrue("text=\"Enter a number above zero.\"" in said())
            assertEquals(logged.measurementLogs, state.measurementLogs)
        }
        type("0.1")
        click("measurement_decrement")
        assertEquals("[measurement_value] field=\"0.1\" error=\"Invalid input\"", field())
        assertEquals(logged.measurementLogs, state.measurementLogs)
    }

    /** A measurement accepts its configured unit after the number but rejects other suffixes and non-positive values. */
    @Test
    fun aMeasurementAcceptsItsConfiguredUnitAndRequiresAValueAboveZero() {
        val inKilograms = testMadeMeasurement(id = "msr_musc", label = "Muscle", unit = "kg").copy(valuePolicy = MeasurementValuePolicy.Positive)
        show(inKilograms, logged.copy(measurements = logged.measurements + inKilograms))
        listOf("41.5 cm", "abc", "0", "-3", "").forEach { typed ->
            type(typed)
            click("measurement_save")
            assertTrue("'$typed': ${said()}", "text=\"Enter a number above zero.\"" in said())
            assertEquals("'$typed' must not save", emptyList<MeasurementRecord>(), state.recordsOf(inKilograms.id))
            assertEquals(logged.measurementLogs, state.measurementLogs)
            if (typed in listOf("41.5 cm", "abc", "")) {
                // A step from what is no number changes nothing either.
                click("measurement_increment")
                assertTrue("'$typed' one step up: ${said()}", "text=\"Enter a number above zero.\"" in said())
                assertEquals("[measurement_value] field=\"$typed\" error=\"Invalid input\"", field())
            }
        }
        assertEquals(emptyList<MeasurementRecord>(), state.recordsOf(inKilograms.id))
        assertEquals(logged.measurementLogs, state.measurementLogs)
        type("41.5 KG")
        click("measurement_save")
        assertEquals(41.5, state.recordsOf(inKilograms.id).single().value, 0.0)
        assertEquals("[measurement_value] field=\"41.5\"", field())

        // The suffix is optional and case-insensitive for every configured unit.
        type("42.5kg")
        click("measurement_save")
        assertEquals(42.5, state.recordsOf(inKilograms.id).single().value, 0.0)
        assertEquals(logged.measurementLogs, state.measurementLogs.filterNot { it.measurementId == inKilograms.id })
    }

    /**
     * Two measurements logged on the same days do not meet: each screen shows and says only its own
     * records, exactly what it shows without the other's, and saving or removing a day of one
     * leaves every record of the other, and of a measurement nothing knows, as it was.
     */
    @Test
    fun twoMeasurementsLoggedOnTheSameDaysDoNotMeet() {
        fun only(measurement: MeasurementConfiguration) = logged.copy(measurementLogs = logged.measurementLogs.filter { it.measurementId == measurement.id })
        fun everyDay(): List<String> = listOf("2026-10-09", "2026-10-08", "2026-10-07", "2026-10-06", "2026-10-05").flatMap { day ->
            select(day)
            said()
        }

        show(waist)
        val waistBesideTheOthers = everyDay()
        state = only(waist)
        compose.waitForIdle()
        assertEquals(everyDay(), waistBesideTheOthers)
        assertFalse(waistBesideTheOthers.toString(), waistBesideTheOthers.any { "82" in it || "kg" in it || "1000" in it })

        // Weight's screen beside them is Weight's screen alone.
        state = logged
        shown = weight
        compose.waitForIdle()
        val weightBesideTheOthers = everyDay()
        state = only(weight)
        compose.waitForIdle()
        assertEquals(everyDay(), weightBesideTheOthers)
        assertFalse(weightBesideTheOthers.toString(), weightBesideTheOthers.any { "cm" in it || "Waist" in it || "91" in it || "1000" in it })

        // A save and a removal on Waist's screen touch Waist's day alone.
        state = logged
        shown = waist
        compose.waitForIdle()
        select("2026-10-08")
        type("93.5")
        click("measurement_save")
        val others = logged.measurementLogs.filter { it.measurementId != waist.id }
        assertEquals(others, state.measurementLogs.filter { it.measurementId != waist.id })
        assertEquals(listOf(93.5, 91.5, 91.0), state.recordsOf(waist.id).map { it.value })
        assertEquals(logged.recordsOf("weight"), state.recordsOf("weight"))
        click("measurement_delete")
        click("measurement_delete")
        assertEquals(others, state.measurementLogs.filter { it.measurementId != waist.id })
        assertEquals(listOf("2026-10-07", "2026-10-06"), state.recordsOf(waist.id).map { it.dateKey })
        assertEquals(logged.recordsOf("weight"), state.recordsOf("weight"))

        // And Weight's save leaves Waist's.
        val beforeWeight = state
        shown = weight
        compose.waitForIdle()
        select("2026-10-08")
        type("83.5 kg")
        click("measurement_save")
        assertEquals(beforeWeight.recordsOf(waist.id), state.recordsOf(waist.id))
        assertEquals(beforeWeight.recordsOf(mood.id), state.recordsOf(mood.id))
        assertEquals(listOf(83.5, 80.0, 78.0), state.recordsOf("weight").map { it.value })
    }

    /**
     * What is typed and not saved belongs to the measurement's ID and the selected day. Another
     * measurement opens on its own records; back again, the first opens afresh. A new name, unit,
     * step or look, and a new day, leave what is typed where it is.
     */
    @Test
    fun aDraftBelongsToTheMeasurementsIdAndTheSelectedDay() {
        show(waist)
        select("2026-10-07")
        type("90.25")

        // Renamed, with another unit, step and look: the same measurement, so the same draft.
        shown = waist.copy(label = "Hips", unit = "in", step = MeasurementStep("0.01"), icon = weight.icon, hue = weight.hue)
        compose.waitForIdle()
        assertEquals("[measurement_value] field=\"90.25\"", field())
        assertEquals("Wed, Oct 7", said().selectedChartDay())
        assertTrue(said().toString(), "text=\"Hips in ⁨in⁩\"" in said())

        // The day turns while something is typed: the draft stays on its day.
        todayKey = "2026-10-10"
        compose.waitForIdle()
        assertEquals("[measurement_value] field=\"90.25\"", field())
        assertEquals("Wed, Oct 7", said().selectedChartDay())

        // Another measurement: its own day and its own number, nothing of what was typed.
        shown = mood
        compose.waitForIdle()
        assertEquals("[measurement_value] field=\"7\"", field())
        assertEquals("Sat, Oct 10", said().selectedChartDay())
        type("9")

        // Back: the first measurement opens on today from its records, and Mood's draft did not follow.
        shown = waist
        compose.waitForIdle()
        assertEquals("[measurement_value] field=\"92\"", field())
        assertEquals("Sat, Oct 10", said().selectedChartDay())
        assertEquals(logged.measurementLogs, state.measurementLogs)
    }

    /**
     * Over the real store: a weight a notification's reply stored after the draft opened is not
     * written over. The screen says the entry changed, keeps what was typed and shows the reply's
     * number, which is what is stored; and a reply for another day stays when today is saved.
     */
    @Test
    fun aReplyStoredWhileTheScreenIsOpenIsNotUndoneAndTheScreenSaysSo() {
        val stored = Stored(logged)
        showStored(weight, stored)
        type("83")
        // The reply arrives for today, and the app has not been told: the screen still shows its own state.
        assertEquals(MeasurementReplyResult.Saved(84.5), stored.reply.submit("84.5", targetDateKey = today))
        click("measurement_save")

        val refused = said()
        assertTrue(refused.toString(), "text=\"This entry changed elsewhere. Reopen Weight before saving.\"" in refused)
        assertEquals("[measurement_value] field=\"83\" error=\"Invalid input\"", field())
        assertTrue(refused.toString(), "spoken=\"84.5 kg\"" in refused)
        assertEquals(84.5, stored.repository.load().recordsOf("weight").first().value, 0.0)

        // Reopened, the day shows the reply's number and can be changed.
        select("2026-10-08")
        click("measurement_discard_confirm")
        select(today)
        assertEquals("[measurement_value] field=\"84.5\"", field())
        type("84")
        // A reply for yesterday arrives meanwhile; saving today leaves it.
        assertEquals(MeasurementReplyResult.Saved(81.0), stored.reply.submit("81", targetDateKey = "2026-10-08"))
        click("measurement_save")
        assertTrue(said().toString(), "text=\"Saved\"" in said())
        assertEquals(listOf(84.0, 81.0, 80.0, 78.0), stored.repository.load().recordsOf("weight").map { it.value })
        assertEquals(logged.recordsOf(waist.id), stored.repository.load().recordsOf(waist.id))
        // Removing a day whose record a reply replaced meanwhile is refused the same way.
        assertEquals(MeasurementReplyResult.Saved(85.0), stored.reply.submit("85", targetDateKey = today))
        click("measurement_delete")
        click("measurement_delete")
        assertTrue(said().toString(), "text=\"This entry changed elsewhere. Review it before deleting.\"" in said())
        assertEquals(85.0, stored.repository.load().recordsOf("weight").first().value, 0.0)
    }

    /** A measurement deleted while its screen is open: a save stores nothing, and the screen says so in the measurement's words and keeps what was typed. */
    @Test
    fun aSaveForAMeasurementDeletedMeanwhileStoresNothingAndTheScreenSaysSo() {
        val stored = Stored(logged)
        showStored(waist, stored)
        type("93.5")
        assertTrue(stored.repository.update { it.copy(measurements = it.measurements.filterNot { made -> made.id == waist.id }, measurementLogs = it.measurementLogs.filterNot { log -> log.measurementId == waist.id }) } is TrackerUpdateResult.Updated)
        val kept = stored.store.everything.toMap()

        click("measurement_save")
        assertTrue(said().toString(), "text=\"Waist was deleted. Nothing was changed.\"" in said())
        assertEquals("[measurement_value] field=\"93.5\" error=\"Invalid input\"", field())
        assertEquals(kept, stored.store.everything.toMap())
        assertEquals(emptyList<MeasurementRecord>(), stored.repository.load().recordsOf(waist.id))
        assertEquals(logged.recordsOf("weight"), stored.repository.load().recordsOf("weight"))
    }

    /** The real store holding [initial], the commands over it and the writer of a notification's reply. */
    private inner class Stored(initial: AppState) {
        val store = CurrentTrackerStore()
        val repository = trackerRepository(store)
        val commands = MeasurementCommands(repository, reconcile = { true }, exactAlarmsAllowed = { true }, refresh = {}, stored = { state = it }, hidden = { emptySet() }, forgetHidden = {}, now = { clock++ })
        val reply = MeasurementReplyCoordinator(WeightMeasurementId, repository, timestampMillis = { clock++ })

        init {
            assertTrue(repository.save(repository.load().copy(measurements = initial.measurements, measurementLogs = initial.measurementLogs)))
        }
    }


    /** The screen as the app shows it: its saves and removals go to the commands over the store. */
    private fun showStored(measurement: MeasurementConfiguration, stored: Stored) {
        state = stored.repository.load()
        shown = measurement
        compose.setContent {
            WorkoutTrackerTheme {
                shown?.let { measurement -> MeasurementScreen(state = state, measurement = measurement, onDayChange = stored.commands::changeDay, onStepChange = { false }, todayKey = todayKey) }
            }
        }
        compose.waitForIdle()
    }

    /** A saved day chosen on 3M keeps its value when the chart narrows to 4W and no longer shows it. */
    @Test
    fun aSavedDayOutsideTheNarrowerChartStillShowsItsValue() {
        show(weight, logged.copy(measurementLogs = logged.measurementLogs + MeasurementLog(WeightMeasurementId, "2026-08-28", 3, 82.4)))
        click("measurement_range_threemonths")
        compose.tapChartDay("2026-08-28", todayKey, dayCount = 91)
        click("measurement_range_fourweeks")

        val day = said()
        assertTrue(day.toString(), "[measurement_selected_day] text=\"Fri, Aug 28\"" in day)
        assertTrue(day.toString(), "spoken=\"82.4 kg\"" in day)
        assertFalse(day.toString(), day.any { it.startsWith("[measurement_selected_note]") })
        assertTrue(day.toString(), day.any { "Selected Fri, Aug 28: 82.4 kg" in it })
    }

    /** An untouched screen follows the day, as Weight's always did. */
    @Test
    fun anUntouchedScreenMovesToTheNewDay() {
        show(waist)
        assertEquals("Fri, Oct 9", said().selectedChartDay())
        todayKey = "2026-10-10"
        compose.waitForIdle()
        assertEquals("Sat, Oct 10", said().selectedChartDay())
        assertTrue(said().toString(), "text=\"Previous: 92 ⁨cm⁩ (2 days ago)\"" in said())
    }
}
