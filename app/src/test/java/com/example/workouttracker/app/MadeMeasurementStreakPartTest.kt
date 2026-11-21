package com.example.workouttracker.app

import com.example.workouttracker.domain.measurement.EarlierWeeks
import com.example.workouttracker.domain.measurement.MeasurementBank
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import com.example.workouttracker.core.time.currentHabitDate
import com.example.workouttracker.core.ui.theme.WorkoutTrackerTheme
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.checklist.ChecklistRewards
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.checklist.RewardPeriod
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.measurement.MeasurementEdit
import com.example.workouttracker.domain.measurement.MeasurementLog
import com.example.workouttracker.domain.measurement.MeasurementStep
import com.example.workouttracker.domain.measurement.applyingMeasurementEdits
import com.example.workouttracker.domain.compatibility.madeMeasurements
import com.example.workouttracker.domain.measurement.testMadeMeasurement
import com.example.workouttracker.domain.measurement.testWeightMeasurement
import com.example.workouttracker.domain.compatibility.weightMeasurement
import com.example.workouttracker.feature.weight.transcript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The Streak part of a made measurement's page, through the app as a person meets it: "Counts
 * toward the streak" joins and leaves, the two steppers set the bank a made checklist has, and
 * "On holiday" is there whether or not it counts. All of it is the page's one draft, stored by its
 * one Save on the day it is pressed.
 */
@RunWith(RobolectricTestRunner::class)
class MadeMeasurementStreakPartTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = currentHabitDate()
    private val waist = testMadeMeasurement()
    private val records = listOf(MeasurementLog(waist.id, today.minusDays(1).toString(), 2L, 92.0), MeasurementLog("weight", today.minusDays(1).toString(), 1L, 81.4))
    private var state by mutableStateOf(AppState(measurements = listOf(testWeightMeasurement(), waist), measurementLogs = records))

    @Composable
    private fun App() {
        WorkoutTrackerTheme { MomentumApp(state = state, onStateChange = { state = it; true }, onActivitySave = ActivitySaveNotExpected) }
    }

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun openStreak() {
        click("home_settings")
        click("settings_group_measurement_${waist.id}")
        compose.onNodeWithTag("measurement_settings_tab_look").assertIsSelected()
        click("measurement_settings_tab_streak")
        compose.onNodeWithTag("measurement_settings_tab_streak").assertIsSelected()
    }

    private fun stored() = state.madeMeasurements.single()

    @Test
    fun itJoinsWithItsBankAndItsHolidayRuleOnOneSave() {
        compose.setContent { App() }
        openStreak()

        // Outside the streak: the switch is off, no bank to set, and the holiday choice is there, still due.
        compose.onNodeWithTag("measurement_settings_rewards").assertIsOff()
        compose.onNodeWithTag("measurement_settings_max_passes_value").assertDoesNotExist()
        compose.onNodeWithTag("measurement_settings_every_kept_days_value").assertDoesNotExist()
        compose.onNodeWithTag("measurement_settings_away_due").assertIsSelected()
        compose.onNodeWithTag("measurement_settings_away_excused").assertIsNotSelected()
        compose.onNodeWithTag("measurement_settings_save").assertIsNotEnabled()
        val said = compose.transcript().toString()
        listOf("Counts toward the streak", "On holiday", "Still due", "Not due", "Changes count from today. Earlier days keep the rules they had.").forEach { words ->
            assertTrue("$words in $said", words in said)
        }

        // Joined, the bank every made view starts with: three tokens, one for every seven kept days.
        click("measurement_settings_rewards")
        compose.onNodeWithTag("measurement_settings_rewards").assertIsOn()
        compose.onNodeWithTag("measurement_settings_max_passes_value").assertTextEquals("3")
        compose.onNodeWithTag("measurement_settings_every_kept_days_value").assertTextEquals("7 kept days")
        assertNull(stored().rewards)

        click("measurement_settings_max_passes_more")
        click("measurement_settings_every_kept_days_less")
        click("measurement_settings_every_kept_days_less")
        compose.onNodeWithTag("measurement_settings_max_passes_value").assertTextEquals("4")
        compose.onNodeWithTag("measurement_settings_every_kept_days_value").assertTextEquals("5 kept days")
        click("measurement_settings_away_excused")
        compose.onNodeWithTag("measurement_settings_away_excused").assertIsSelected()

        // Nothing is stored before Save; one Save stores all of it, dated today, and returns to the list.
        assertEquals(waist, stored())
        compose.onNodeWithTag("measurement_settings_save").assertIsEnabled()
        click("measurement_settings_save")
        compose.onNodeWithTag("measurement_settings").assertDoesNotExist()
        assertEquals(ChecklistRewards(maxPasses = 4, everyKeptDays = 5, periods = listOf(RewardPeriod(today))), stored().standardRewards)
        assertEquals(Dated(listOf(Dated.Revision(WhenAway.Due), Dated.Revision(WhenAway.Excused, today))), stored().whenAway)
        assertEquals(waist.copy(rewards = stored().rewards, whenAway = stored().whenAway), stored())
        // Weight and every record are what they were.
        assertEquals(testWeightMeasurement(), state.weightMeasurement)
        assertEquals(records, state.measurementLogs)

        // Its page shows what was chosen, and nothing waits for Save.
        click("settings_group_measurement_${waist.id}")
        click("measurement_settings_tab_streak")
        compose.onNodeWithTag("measurement_settings_rewards").assertIsOn()
        compose.onNodeWithTag("measurement_settings_max_passes_value").assertTextEquals("4")
        compose.onNodeWithTag("measurement_settings_every_kept_days_value").assertTextEquals("5 kept days")
        compose.onNodeWithTag("measurement_settings_away_excused").assertIsSelected()
        compose.onNodeWithTag("measurement_settings_save").assertIsNotEnabled()
    }

    /**
     * Weight's bank as it was stored earns by the week. Its Streak part shows the two steppers
     * every daily view has, with its six days in them and a line that says they still count
     * within a week. One step and Save make it earn by kept days from today: the week rule stays
     * for every earlier day, the line is gone, and no record moves.
     */
    @Test
    fun weightsWeekBankShowsTheSameSteppersAndEarnsByKeptDaysOnceANumberIsSaved() {
        compose.setContent { App() }
        click("home_settings")
        click("settings_group_measurement_weight")
        click("measurement_settings_tab_streak")

        compose.onNodeWithTag("measurement_settings_rewards").assertIsOn()
        compose.onNodeWithTag("measurement_settings_max_passes_value").assertTextEquals("7")
        compose.onNodeWithTag("measurement_settings_every_kept_days_value").assertTextEquals("6 kept days")
        compose.onNodeWithTag("measurement_settings_days_in_week_value").assertDoesNotExist()
        compose.onNodeWithTag("measurement_settings_earn_note")
            .assertTextEquals("These days still count within a week, Monday to Sunday. Change a number and save, and from today they count across weeks.")
        compose.onNodeWithTag("measurement_settings_save").assertIsNotEnabled()

        click("measurement_settings_every_kept_days_more")
        compose.onNodeWithTag("measurement_settings_every_kept_days_value").assertTextEquals("7 kept days")
        compose.onNodeWithTag("measurement_settings_earn_note").assertDoesNotExist()
        // Nothing is stored before Save.
        assertEquals(testWeightMeasurement(), state.weightMeasurement)
        compose.onNodeWithTag("measurement_settings_save").assertIsEnabled()
        click("measurement_settings_save")
        compose.onNodeWithTag("measurement_settings").assertDoesNotExist()

        val weeks = EarlierWeeks(keptDaysInWeek = Dated.always(6), until = today.minusDays(1))
        assertEquals(MeasurementBank.Standard(ChecklistRewards(maxPasses = 7, everyKeptDays = 7), weeks), state.weightMeasurement!!.rewards)
        assertEquals(testWeightMeasurement().copy(rewards = state.weightMeasurement!!.rewards), state.weightMeasurement)
        assertEquals(waist, stored())
        assertEquals(records, state.measurementLogs)

        // Its page shows the bank it has now, as every daily view's, and nothing waits for Save.
        click("settings_group_measurement_weight")
        click("measurement_settings_tab_streak")
        compose.onNodeWithTag("measurement_settings_max_passes_value").assertTextEquals("7")
        compose.onNodeWithTag("measurement_settings_every_kept_days_value").assertTextEquals("7 kept days")
        compose.onNodeWithTag("measurement_settings_earn_note").assertDoesNotExist()
        compose.onNodeWithTag("measurement_settings_save").assertIsNotEnabled()
    }

    /** Leaving keeps the days it took part and its holiday rule, and the bank's steppers go with the switch; joined again it has the rules it had. */
    @Test
    fun itLeavesAfterYesterdayAndKeepsItsHolidayRule() {
        state = state.applyingMeasurementEdits(
            waist.id,
            listOf(MeasurementEdit.JoinRewards, MeasurementEdit.SetBankRules(maxPasses = 4, everyKeptDays = 5), MeasurementEdit.SetWhenAway(WhenAway.Excused)),
            today.minusDays(10),
        )
        val joined = stored()
        compose.setContent { App() }
        openStreak()
        compose.onNodeWithTag("measurement_settings_rewards").assertIsOn()

        click("measurement_settings_rewards")
        compose.onNodeWithTag("measurement_settings_rewards").assertIsOff()
        compose.onNodeWithTag("measurement_settings_max_passes_value").assertDoesNotExist()
        // The holiday choice stays, as its reminder reads it too.
        compose.onNodeWithTag("measurement_settings_away_excused").assertIsSelected()
        // Switched on again before Save, the leave is taken back and nothing is left to save.
        click("measurement_settings_rewards")
        compose.onNodeWithTag("measurement_settings_max_passes_value").assertTextEquals("4")
        compose.onNodeWithTag("measurement_settings_save").assertIsNotEnabled()

        click("measurement_settings_rewards")
        click("measurement_settings_save")
        assertEquals(listOf(RewardPeriod(today.minusDays(10), today.minusDays(1))), stored().standardRewards?.periods)
        assertEquals(joined.standardRewards?.maxPasses, stored().standardRewards?.maxPasses)
        assertEquals(joined.whenAway, stored().whenAway)
    }

    /** The Streak part is the same draft as Unit: what was typed and chosen on both survives process death, with the part that was open, and one Save stores it. */
    @Test
    fun theStreakPartIsTheSameDraftAsUnitAcrossProcessDeath() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { App() }
        click("home_settings")
        click("settings_group_measurement_${waist.id}")
        click("measurement_settings_tab_unit")
        compose.onNodeWithTag("measurement_settings_step").performTextReplacement("0.01")
        click("measurement_settings_tab_streak")
        click("measurement_settings_rewards")
        click("measurement_settings_max_passes_less")
        click("measurement_settings_away_excused")

        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()

        compose.onNodeWithTag("measurement_settings_tab_streak").assertIsSelected()
        compose.onNodeWithTag("measurement_settings_rewards").assertIsOn()
        compose.onNodeWithTag("measurement_settings_max_passes_value").assertTextEquals("2")
        compose.onNodeWithTag("measurement_settings_away_excused").assertIsSelected()
        assertEquals(waist, stored())
        // Leaving asks, as the choices would be lost.
        click("top_back")
        compose.onNodeWithTag("measurement_settings_discard_dialog").assertExists()
        click("measurement_settings_discard_dialog_stay")

        click("measurement_settings_tab_unit")
        compose.onNodeWithTag("measurement_settings_step").assertTextContains("0.01")
        click("measurement_settings_save")
        assertEquals(MeasurementStep("0.01"), stored().step)
        assertEquals(ChecklistRewards(maxPasses = 2, everyKeptDays = 7, periods = listOf(RewardPeriod(today))), stored().standardRewards)
        assertEquals(WhenAway.Excused, stored().whenAway.on(today))
    }
}
