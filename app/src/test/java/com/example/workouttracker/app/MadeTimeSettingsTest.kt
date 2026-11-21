package com.example.workouttracker.app

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
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import com.example.workouttracker.core.time.currentHabitDate
import com.example.workouttracker.core.ui.theme.WorkoutTrackerTheme
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.checklist.MoMotif
import com.example.workouttracker.domain.checklist.RewardPeriod
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.holiday.DayRange
import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.domain.measurement.withWeight
import com.example.workouttracker.domain.questions.withEating
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import com.example.workouttracker.domain.time.TimeConfiguration
import com.example.workouttracker.domain.time.TimeLog
import com.example.workouttracker.domain.time.TimeLogOrder
import com.example.workouttracker.domain.time.TimeRewards
import com.example.workouttracker.domain.time.TokenStep
import com.example.workouttracker.domain.time.TokenSteps
import com.example.workouttracker.domain.compatibility.madeTime
import com.example.workouttracker.domain.time.testMadeTime
import com.example.workouttracker.domain.time.withReading
import com.example.workouttracker.domain.activity.withActivity
import com.example.workouttracker.feature.weight.selectedHistoryDay
import com.example.workouttracker.feature.weight.transcript
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import com.example.workouttracker.domain.LoggedViewKind
import com.example.workouttracker.domain.LoggedViewRef

/**
 * A time view is made, changed, hidden and deleted through the app as a person meets it: the
 * fourth card under "Track something new", four numbered steps, a page with four parts over one
 * Save, the hide switch on its row and Delete with its logs.
 */
@RunWith(RobolectricTestRunner::class)
class MadeTimeSettingsTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = currentHabitDate()
    private var state by mutableStateOf(AppState().withWeight().withEating().withReading().withActivity())
    private var visibility by mutableStateOf(FeatureVisibilitySettings())
    private var daysOff by mutableStateOf(HolidaySchedule())

    @Composable
    private fun App() {
        WorkoutTrackerTheme {
            MomentumApp(
                state = state,
                onStateChange = { state = it; true },
                onActivitySave = ActivitySaveNotExpected,
                featureVisibility = visibility,
                onFeatureVisibilityChange = { visibility = it; true },
                holidaySchedule = daysOff,
            )
        }
    }

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun type(tag: String, text: String) {
        compose.onNodeWithTag(tag).performTextReplacement(text)
        compose.waitForIdle()
    }

    private fun line(tag: String): String = compose.transcript().single { it.startsWith("[$tag]") }

    private fun said(text: String): Boolean = "text=\"$text\"" in compose.transcript()

    private fun openSetup() {
        click("home_settings")
        click("settings_new")
        click("settings_time_new")
    }

    private fun made(): TimeConfiguration = state.madeTime.single()

    @Test
    fun aTimeViewIsMadeInFourStepsAndHasItsRowItsTileAndItsScreen() {
        compose.setContent { App() }
        click("home_settings")
        click("settings_new")
        assertTrue(compose.transcript().toString(), said("Timer") && said("Such as reading, meditating or practising an instrument."))
        click("settings_time_new")
        assertTrue(compose.transcript().toString(), said("New timer"))

        compose.onNodeWithTag("time_setup_tab_look").assertTextEquals("1 Look").assertIsSelected()
        compose.onNodeWithTag("time_setup_tab_goal").assertTextEquals("2 Goal").assertIsNotEnabled()
        compose.onNodeWithTag("time_setup_tab_reminders").assertTextEquals("3 Reminders")
        compose.onNodeWithTag("time_setup_tab_streak").assertTextEquals("4 Streak")
        compose.onNodeWithTag("time_setup_next").assertIsNotEnabled()
        // Look has Mo's flavor, on Plain, with the one motif Mo has something for while a timer runs.
        compose.onNodeWithTag("time_setup_motif_none").assertIsSelected()
        compose.onNodeWithTag("time_setup_motif_reading").assertIsNotSelected()
        compose.onNodeWithTag("time_setup_motif_pills").assertDoesNotExist()
        type("time_setup_name", "Piano")
        click("time_setup_next")

        // Step 2: an hour a week, with what a token takes under it. Text that is no such number holds the step.
        compose.onNodeWithTag("time_setup_tab_goal").assertIsSelected()
        assertEquals("[time_setup_goal] field=\"60\"", line("time_setup_goal"))
        assertTrue(compose.transcript().toString(), said("Minutes a week"))
        assertEquals("[time_setup_goal_note] text=\"60 minutes in a week earn a token. More minutes earn up to 2 tokens a week.\"", line("time_setup_goal_note"))
        type("time_setup_goal", "an hour")
        assertEquals("[time_setup_goal] field=\"an hour\" error=\"A whole number from 1 to 10080\"", line("time_setup_goal"))
        compose.onNodeWithTag("time_setup_next").assertIsNotEnabled()
        type("time_setup_goal", "90")
        assertEquals("[time_setup_goal] field=\"90\"", line("time_setup_goal"))
        assertEquals("[time_setup_goal_note] text=\"90 minutes in a week earn a token. More minutes earn up to 2 tokens a week.\"", line("time_setup_goal_note"))
        click("time_setup_next")

        // Step 3: the reminder every made view has, off at 09:00, saying all the week asks as if nothing were logged.
        compose.onNodeWithTag("time_setup_tab_reminders").assertIsSelected()
        compose.onNodeWithTag("time_setup_reminder").assertIsOff()
        compose.onNodeWithTag("time_setup_reminder_time").assertTextEquals("09:00")
        assertEquals("[time_setup_reminder_preview_title] text=\"Piano\"", line("time_setup_reminder_preview_title"))
        assertEquals("[time_setup_reminder_preview_text] text=\"90 minutes to go this week\"", line("time_setup_reminder_preview_text"))
        click("time_setup_next")

        // Step 4: whether it counts, how many tokens its bank holds, holidays and, last, the steps a week earns tokens by. No kept days earn anything here.
        compose.onNodeWithTag("time_setup_tab_streak").assertIsSelected()
        compose.onNodeWithTag("time_setup_rewards").assertIsOff()
        compose.onNodeWithTag("time_setup_max_passes_value").assertDoesNotExist()
        compose.onNodeWithTag("time_setup_step_1_minutes").assertDoesNotExist()
        compose.onNodeWithTag("time_setup_away_due").assertIsSelected()
        click("time_setup_rewards")
        compose.onNodeWithTag("time_setup_max_passes_value").assertTextEquals("3")
        compose.onNodeWithTag("time_setup_every_kept_days_value").assertDoesNotExist()
        compose.onNodeWithTag("time_setup_step_1_minutes").assertTextEquals("90 min")
        compose.onNodeWithTag("time_setup_step_2_minutes").assertTextEquals("180 min")
        compose.onNodeWithTag("time_setup_step_2_tokens_value").assertTextEquals("+1")
        click("time_setup_add_step")
        compose.onNodeWithTag("time_setup_step_3_minutes").assertTextEquals("270 min")
        // A percent that cannot be one holds Create, as a goal that cannot be one holds Next.
        type("time_setup_step_3_percent", "half")
        assertEquals("[time_setup_step_3_percent] field=\"half\" error=\"201–1000\"", line("time_setup_step_3_percent"))
        compose.onNodeWithTag("time_setup_create").assertIsNotEnabled()
        type("time_setup_step_3_percent", "250")
        compose.onNodeWithTag("time_setup_step_3_minutes").assertTextEquals("225 min")
        compose.onNodeWithTag("time_setup_create").assertTextEquals("Create timer").assertIsEnabled()
        assertEquals(emptyList<TimeConfiguration>(), state.madeTime)
        click("time_setup_create")

        val view = made()
        assertEquals("Piano", view.label)
        assertEquals(ViewIcon.Timer, view.icon)
        assertNull(view.motif)
        assertEquals(Dated.always(90), view.goal)
        assertEquals(TimeRewards(maxPasses = 3, periods = listOf(RewardPeriod(today)), tokenSteps = TokenSteps(listOf(TokenStep(100, 1), TokenStep(200, 1), TokenStep(250, 1)))), view.rewards)
        assertEquals(false, view.checkpoint?.remind)
        assertTrue(view.id.matches(Regex("tim_[a-z2-9]{4}")))

        // The setup closed onto the list, where the view has its row with the hide switch.
        compose.onNodeWithTag("time_setup").assertDoesNotExist()
        compose.onNodeWithTag("settings_group_time_${view.id}").assertExists()
        compose.onNodeWithTag("settings_feature_time_${view.id}").assertIsOn()

        // Its tile opens its screen on today, against its own goal.
        click("top_back")
        click("home_time_${view.id}")
        assertEquals("[time_${view.id}_week_minutes] text=\"0 / 90 min\"", line("time_${view.id}_week_minutes"))
        assertEquals("$today", compose.transcript().selectedHistoryDay("time_${view.id}"))
    }

    /** A setup under way survives process death with its ID, its step and what was typed, a refused goal too, and creates the view once. */
    @Test
    fun aSetupUnderWaySurvivesProcessDeath() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { App() }
        openSetup()
        type("time_setup_name", "Piano")
        click("time_setup_next")
        type("time_setup_goal", "9o")

        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()

        compose.onNodeWithTag("time_setup_tab_goal").assertIsSelected()
        assertEquals("[time_setup_goal] field=\"9o\" error=\"A whole number from 1 to 10080\"", line("time_setup_goal"))
        compose.onNodeWithTag("time_setup_next").assertIsNotEnabled()
        type("time_setup_goal", "90")
        click("time_setup_next")
        click("time_setup_next")
        click("time_setup_create")
        assertEquals("Piano" to 90, made().label to made().goal.current)
    }

    private val piano = testMadeTime()
    private val logged = listOf(
        TimeLog(piano.id, today.minusDays(1).toString(), 2L, 20),
        TimeLog(piano.id, today.minusDays(2).toString(), 1L, 15),
        TimeLog("reading", today.minusDays(2).toString(), 1L, 30),
    ).sortedWith(TimeLogOrder)

    private fun withPiano() = AppState(timeLogs = logged).withWeight().withEating().withReading().withActivity().let { it.copy(time = it.time + piano) }

    /**
     * The page has four parts over one Save: a rename, a goal, the reminder's switch and time,
     * counting toward the streak with a smaller bank and the holiday rule are stored together. A
     * goal that is no goal holds Save back, on whichever part the page stands.
     */
    @Test
    fun thePageChangesTheViewOverOneSave() {
        // Exact alarms allowed, the reminder switched on is stored with the rest of the Save.
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        state = withPiano()
        compose.setContent { App() }
        click("home_settings")
        click("settings_group_time_${piano.id}")

        compose.onNodeWithTag("time_settings_tab_look").assertIsSelected()
        compose.onNodeWithTag("time_settings_tab_goal").assertTextEquals("Goal")
        compose.onNodeWithTag("time_settings_save").assertIsNotEnabled()
        type("time_settings_name", "Keys")
        compose.onNodeWithTag("time_settings_save").assertIsEnabled()
        compose.onNodeWithTag("time_settings_motif_none").assertIsSelected()
        click("time_settings_motif_reading")
        compose.onNodeWithTag("time_settings_motif_reading").assertIsSelected()

        click("time_settings_tab_goal")
        assertEquals("[time_settings_goal] field=\"60\"", line("time_settings_goal"))
        type("time_settings_goal", "0")
        assertEquals("[time_settings_goal] field=\"0\" error=\"A whole number from 1 to 10080\"", line("time_settings_goal"))
        compose.onNodeWithTag("time_settings_save").assertIsNotEnabled()
        click("time_settings_tab_streak")
        compose.onNodeWithTag("time_settings_save").assertIsNotEnabled()
        click("time_settings_tab_goal")
        assertEquals("[time_settings_goal] field=\"0\" error=\"A whole number from 1 to 10080\"", line("time_settings_goal"))
        type("time_settings_goal", "45")

        click("time_settings_tab_reminders")
        assertEquals("[time_settings_reminder_preview_title] text=\"Keys\"", line("time_settings_reminder_preview_title"))
        assertEquals("[time_settings_reminder_preview_text] text=\"45 minutes to go this week\"", line("time_settings_reminder_preview_text"))
        click("time_settings_reminder")
        click("time_settings_reminder_time_after_wake")

        click("time_settings_tab_streak")
        compose.onNodeWithTag("time_settings_rewards").assertIsOff()
        click("time_settings_rewards")
        click("time_settings_max_passes_less")
        compose.onNodeWithTag("time_settings_max_passes_value").assertTextEquals("2")
        compose.onNodeWithTag("time_settings_every_kept_days_value").assertDoesNotExist()
        click("time_settings_away_excused")
        click("time_settings_step_2_remove")
        click("time_settings_step_2_remove")
        compose.onNodeWithTag("time_settings_step_2_percent").assertDoesNotExist()
        // The Goal part's line follows what Streak holds, before anything is saved.
        click("time_settings_tab_goal")
        assertEquals("[time_settings_goal_note] text=\"45 minutes in a week earn a token.\"", line("time_settings_goal_note"))

        assertEquals(piano, made())
        click("time_settings_save")

        val saved = made()
        assertEquals("Keys", saved.label)
        assertEquals(MoMotif.Book, saved.motif)
        // Earlier days hold minutes shown against 60, so the goal starts today; the bank, joined today, judged nothing yet.
        assertEquals(Dated(listOf(Dated.Revision(60), Dated.Revision(45, today))), saved.goal)
        assertEquals(TimeRewards(maxPasses = 2, periods = listOf(RewardPeriod(today)), tokenSteps = TokenSteps(listOf(TokenStep(100, 1)))), saved.rewards)
        assertEquals(WhenAway.Excused, saved.whenAway.on(today))
        assertEquals(WhenAway.Due, saved.whenAway.on(today.minusDays(1)))
        assertTrue(saved.checkpoint?.remind == true && saved.checkpoint?.at is CheckpointTime.AfterWake)
        // Saved, the page returned to the list; the logs are as they were.
        compose.onNodeWithTag("time_settings").assertDoesNotExist()
        assertEquals(logged, state.timeLogs)
    }

    /** Hidden, the view has no tile and keeps its page, without a test to send; shown again it is back. Deleted, it goes with every day's minutes and Reading's stay. */
    @Test
    fun theViewIsHiddenShownAndDeletedWithItsLogs() {
        state = withPiano()
        compose.setContent { App() }
        compose.onNodeWithTag("home_time_${piano.id}").assertExists()

        click("home_settings")
        click("settings_feature_time_${piano.id}")
        assertEquals(FeatureVisibilitySettings().withVisibility(LoggedViewRef(LoggedViewKind.Time, piano.id), visible = false, today = currentHabitDate()), visibility)
        click("top_back")
        compose.onNodeWithTag("home_time_${piano.id}").assertDoesNotExist()

        click("home_settings")
        click("settings_group_time_${piano.id}")
        compose.onNodeWithTag("time_settings").assertExists()
        click("time_settings_tab_reminders")
        compose.onNodeWithTag("time_settings_reminder").assertIsNotEnabled()
        compose.onNodeWithTag("time_settings_reminder_test").assertIsNotEnabled()
        click("top_back")
        click("settings_feature_time_${piano.id}")
        assertEquals(FeatureVisibilitySettings(), visibility)

        click("settings_group_time_${piano.id}")
        click("time_settings_delete")
        val dialog = compose.transcript()
        assertTrue(dialog.toString(), "text=\"Delete Piano?\"" in dialog && "text=\"Its settings and 2 recorded days are deleted. This cannot be undone.\"" in dialog)
        click("time_settings_delete_cancel")
        assertEquals(piano, made())
        click("time_settings_delete")
        click("time_settings_delete_confirm")

        assertEquals(emptyList<TimeConfiguration>(), state.madeTime)
        assertEquals(logged.filter { it.viewId == "reading" }, state.timeLogs)
        compose.onNodeWithTag("time_settings").assertDoesNotExist()
        compose.onNodeWithTag("settings_group_time_${piano.id}").assertDoesNotExist()
        compose.onNodeWithTag("settings_new").assertExists()
    }

    /** On an away day the draft is not due on, its reminder would stay quiet: the preview says so and there is nothing to send. */
    @Test
    fun onAnAwayDayItIsNotDueOnThereIsNothingToPreviewOrToSend() {
        state = withPiano()
        daysOff = HolidaySchedule(listOf(DayRange(today, today)))
        compose.setContent { App() }
        click("home_settings")
        click("settings_group_time_${piano.id}")
        click("time_settings_tab_reminders")

        // Still due while away, it speaks, for all the week asks.
        assertEquals("[time_settings_reminder_preview_text] text=\"60 minutes to go this week\"", line("time_settings_reminder_preview_text"))
        compose.onNodeWithTag("time_settings_reminder_test").assertIsEnabled()

        click("time_settings_tab_streak")
        click("time_settings_away_excused")
        click("time_settings_tab_reminders")
        compose.onNodeWithTag("time_settings_reminder_preview_title").assertDoesNotExist()
        compose.onNodeWithTag("time_settings_reminder_test").assertIsNotEnabled()
        assertFalse(compose.transcript().toString(), compose.transcript().any { "minutes to go" in it })
    }

    /** The setup speaks German: its parts, its goal and what its last step creates. */
    @Test
    @Config(qualifiers = "de")
    fun theSetupSpeaksGerman() {
        compose.setContent { App() }
        click("home_settings")
        click("settings_new")
        assertTrue(compose.transcript().toString(), said("Timer") && said("Etwa Lesen, Meditieren oder ein Instrument üben."))
        click("settings_time_new")
        assertTrue(compose.transcript().toString(), said("Neuer Timer"))
        compose.onNodeWithTag("time_setup_tab_goal").assertTextEquals("2 Ziel")
        type("time_setup_name", "Klavier")
        click("time_setup_next")
        assertTrue(compose.transcript().toString(), said("Minuten pro Woche"))
        assertEquals("[time_setup_goal_note] text=\"Für 60 Minuten in einer Woche gibt es ein Token. Für mehr Minuten gibt es bis zu 2 Tokens pro Woche.\"", line("time_setup_goal_note"))
        type("time_setup_goal", "viel")
        assertEquals("[time_setup_goal] field=\"viel\" error=\"Eine ganze Zahl von 1 bis 10080\"", line("time_setup_goal"))
        type("time_setup_goal", "30")
        click("time_setup_next")
        assertEquals("[time_setup_reminder_preview_text] text=\"Noch 30 Minuten diese Woche\"", line("time_setup_reminder_preview_text"))
        click("time_setup_next")
        compose.onNodeWithTag("time_setup_create").assertTextEquals("Timer erstellen")
        click("time_setup_create")
        assertEquals("Klavier" to 30, made().label to made().goal.current)
        assertEquals(LocalTime.of(9, 0), (made().checkpoint?.at as CheckpointTime.Fixed).time)
    }
}
