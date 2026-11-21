package com.example.workouttracker.app

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.example.workouttracker.R
import com.example.workouttracker.core.time.currentHabitDate
import com.example.workouttracker.core.ui.theme.WorkoutTrackerTheme
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.checklist.ChecklistRewards
import com.example.workouttracker.domain.checklist.Checkpoint
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.checklist.RewardPeriod
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.measurement.MeasurementConfiguration
import com.example.workouttracker.domain.measurement.MeasurementEdit
import com.example.workouttracker.domain.measurement.MeasurementStep
import com.example.workouttracker.domain.measurement.addMeasurement
import com.example.workouttracker.domain.measurement.applyingMeasurementEdits
import com.example.workouttracker.domain.compatibility.madeMeasurements
import com.example.workouttracker.domain.measurement.withWeight
import com.example.workouttracker.feature.settings.ViewWrite
import com.example.workouttracker.feature.weight.transcript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager

/**
 * A measurement is set up in three numbered steps, the three parts of its page, with Back, Next
 * and Create, through the app as a person meets it. The stepping is the one a checklist's setup
 * walks its four parts with, which still wears its four numbers.
 */
@RunWith(RobolectricTestRunner::class)
class MeasurementSetupStepsTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val today = currentHabitDate()
    private var state by mutableStateOf(AppState().withWeight())

    /** What each press of Create came to, and the ID it was asked under. */
    private var outcomes = ArrayDeque<ViewWrite>()
    private val askedUnder = mutableListOf<String>()

    @Before fun allowRemindersToArrive() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @Composable
    private fun App() {
        WorkoutTrackerTheme {
            MomentumApp(
                state = state,
                onStateChange = { state = it; true },
                onActivitySave = ActivitySaveNotExpected,
                onMeasurementCreate = { starter: MeasurementConfiguration, edits: List<MeasurementEdit> ->
                    askedUnder += starter.id
                    val outcome = outcomes.removeFirstOrNull() ?: ViewWrite.Stored
                    if (outcome != ViewWrite.NotStored) state = state.addMeasurement(starter).applyingMeasurementEdits(starter.id, edits, today)
                    outcome
                },
            )
        }
    }

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun openSetup() {
        click("home_settings")
        click("settings_new")
        click("settings_measurement_new")
    }

    private fun line(tag: String): String = compose.transcript().single { it.startsWith("[$tag]") }

    private fun string(id: Int): String = context.getString(id)

    @Test
    fun aMeasurementIsMadeStepByStepWithItsReminderAndTheStreak() {
        compose.setContent { App() }
        openSetup()

        // Four numbered steps, its own in the place of Items; the three ahead are shown and held back until their turn.
        compose.onNodeWithTag("measurement_setup_tab_look").assertTextEquals("1 Look")
        compose.onNodeWithTag("measurement_setup_tab_unit").assertTextEquals("2 Unit")
        compose.onNodeWithTag("measurement_setup_tab_reminders").assertTextEquals("3 Reminders")
        compose.onNodeWithTag("measurement_setup_tab_streak").assertTextEquals("4 Streak")
        compose.onNodeWithTag("measurement_setup_tab_items").assertDoesNotExist()
        compose.onNodeWithTag("measurement_setup_tab_look").assertIsSelected()
        compose.onNodeWithTag("measurement_setup_tab_unit").assertIsNotEnabled()
        compose.onNodeWithTag("measurement_setup_tab_reminders").assertIsNotEnabled()
        compose.onNodeWithTag("measurement_setup_tab_streak").assertIsNotEnabled()
        compose.onNodeWithTag("measurement_setup_next").assertIsNotEnabled()
        compose.onNodeWithTag("measurement_setup_create").assertDoesNotExist()

        // Look waits for the name alone: the unit and the step are the next step's.
        compose.onNodeWithTag("measurement_setup_unit").assertDoesNotExist()
        compose.onNodeWithTag("measurement_setup_name").performTextReplacement("Waist")
        click("measurement_setup_next")

        // Step 2 is the page's Unit part over the setup's draft: no unit yet, steps of 0.1, and it waits while either field is refused.
        compose.onNodeWithTag("measurement_setup_tab_unit").assertIsSelected()
        // The next step's tab opens exactly when Next does.
        compose.onNodeWithTag("measurement_setup_tab_reminders").assertIsEnabled()
        assertEquals("[measurement_setup_step] field=\"0.1\"", line("measurement_setup_step"))
        compose.onNodeWithTag("measurement_setup_next").assertIsEnabled()
        compose.onNodeWithTag("measurement_setup_step").performTextReplacement("0.125")
        compose.onNodeWithTag("measurement_setup_next").assertIsNotEnabled()
        compose.onNodeWithTag("measurement_setup_tab_reminders").assertIsNotEnabled()
        compose.onNodeWithTag("measurement_setup_step").performTextReplacement("0.5")
        compose.onNodeWithTag("measurement_setup_unit").performTextReplacement("cm")
        compose.onNodeWithTag("measurement_setup_next").assertIsEnabled()
        click("measurement_setup_next")

        // Step 3 is the page's Reminders part over the same draft: off at 09:00, previewing under the name just typed.
        compose.onNodeWithTag("measurement_setup_tab_reminders").assertIsSelected()
        compose.onNodeWithTag("measurement_setup_tab_streak").assertIsEnabled()
        compose.onNodeWithTag("measurement_setup_reminder").assertIsOff()
        assertEquals("[measurement_setup_reminder_preview_title] text=\"Waist\"", line("measurement_setup_reminder_preview_title"))
        click("measurement_setup_reminder")
        click("measurement_setup_reminder_time_before_sleep")

        // A test from the setup is of a measurement that does not exist yet: its tap opens the app where it was.
        click("measurement_setup_reminder_test")
        val posted = context.getSystemService(NotificationManager::class.java).activeNotifications.single()
        assertTrue(posted.tag, posted.tag.matches(Regex("measurement/msr_[a-z2-9]{4}/day")))
        assertNull(shadowOf(posted.notification.contentIntent).savedIntent.requestedAppNavigation())

        click("measurement_setup_next")
        compose.onNodeWithTag("measurement_setup_tab_streak").assertIsSelected()
        compose.onNodeWithTag("measurement_setup_next").assertDoesNotExist()
        compose.onNodeWithTag("measurement_setup_create").assertIsEnabled()
        // A setup is where a person first meets a token, so what the switch decides is said before it is on.
        compose.onNodeWithTag("measurement_setup_rewards").assertIsOff()
        compose.onNodeWithTag("measurement_setup_streak_explained").assertExists()
        click("measurement_setup_rewards")
        click("measurement_setup_away_excused")

        // Back (the header's arrow) and a passed step's tab lead back, each step keeping what was done on it.
        click("top_back")
        compose.onNodeWithTag("measurement_setup_reminder").assertIsOn()
        compose.onNodeWithTag("measurement_setup_reminder_time_before_sleep").assertIsSelected()
        click("measurement_setup_tab_look")
        assertTrue(compose.transcript().toString(), "[measurement_setup_name] field=\"Waist\"" in compose.transcript())
        compose.onNodeWithTag("measurement_setup_tab_unit").assertIsEnabled()
        compose.onNodeWithTag("measurement_setup_tab_reminders").assertIsNotEnabled()
        click("measurement_setup_next")
        assertEquals("[measurement_setup_step] field=\"0.5\"", line("measurement_setup_step"))
        click("measurement_setup_next")
        click("measurement_setup_next")
        compose.onNodeWithTag("measurement_setup_rewards").assertIsOn()
        assertEquals(emptyList<MeasurementConfiguration>(), state.madeMeasurements)

        click("measurement_setup_create")

        val made = state.madeMeasurements.single()
        assertEquals("Waist" to "cm", made.label to made.unit)
        assertEquals(MeasurementStep("0.5"), made.step)
        assertEquals(Checkpoint("day", CheckpointTime.BeforeSleep(15), remind = true), made.checkpoint)
        assertEquals(ChecklistRewards(maxPasses = 3, everyKeptDays = 7, periods = listOf(RewardPeriod(today))), made.standardRewards)
        assertEquals(Dated(listOf(Dated.Revision(WhenAway.Due), Dated.Revision(WhenAway.Excused, today))), made.whenAway)
        assertEquals(listOf(made.id), askedUnder)
        compose.onNodeWithTag("measurement_setup").assertDoesNotExist()
        compose.onNodeWithTag("settings_group_measurement_${made.id}").assertExists()

        // Its page shows the four parts with what was chosen.
        click("settings_group_measurement_${made.id}")
        click("measurement_settings_tab_unit")
        assertEquals("[measurement_settings_unit] field=\"cm\"", line("measurement_settings_unit"))
        assertEquals("[measurement_settings_step] field=\"0.5\"", line("measurement_settings_step"))
        click("measurement_settings_tab_reminders")
        compose.onNodeWithTag("measurement_settings_reminder").assertIsOn()
        compose.onNodeWithTag("measurement_settings_reminder_time_before_sleep").assertIsSelected()
        click("measurement_settings_tab_streak")
        compose.onNodeWithTag("measurement_settings_rewards").assertIsOn()
        compose.onNodeWithTag("measurement_settings_away_excused").assertIsSelected()
    }

    /** The step, and what was done on it, survive process death. */
    @Test
    fun theSetupSurvivesProcessDeathOnTheStepItWasOn() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { App() }
        openSetup()
        compose.onNodeWithTag("measurement_setup_name").performTextReplacement("Waist")
        click("measurement_setup_next")
        click("measurement_setup_next")
        click("measurement_setup_reminder")

        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()

        compose.onNodeWithTag("measurement_setup_tab_reminders").assertIsSelected()
        compose.onNodeWithTag("measurement_setup_reminder").assertIsOn()
        compose.onNodeWithTag("measurement_setup_tab_streak").assertIsEnabled()
        click("measurement_setup_next")
        click("measurement_setup_create")
        assertEquals(true, state.madeMeasurements.single().checkpoint?.remind)
    }

    /**
     * A Create taken back leaves nothing: the setup stays on its step, says it could not save and
     * keeps its ID, and the next press creates the measurement once, under that ID.
     */
    @Test
    fun aCreateTakenBackKeepsTheSetupAndItsIdToTryAgain() {
        outcomes = ArrayDeque(listOf(ViewWrite.NotStored))
        compose.setContent { App() }
        openSetup()
        compose.onNodeWithTag("measurement_setup_name").performTextReplacement("Waist")
        click("measurement_setup_next")
        click("measurement_setup_next")
        click("measurement_setup_reminder")
        click("measurement_setup_next")

        click("measurement_setup_create")
        compose.onNodeWithTag("measurement_setup_tab_streak").assertIsSelected()
        assertEquals("[measurement_setup_error] text=\"${string(R.string.settings_error_view)}\"", line("measurement_setup_error"))
        assertEquals(emptyList<MeasurementConfiguration>(), state.madeMeasurements)

        click("measurement_setup_create")
        assertEquals(2, askedUnder.size)
        assertEquals(askedUnder[0], askedUnder[1])
        assertEquals(askedUnder[0], state.madeMeasurements.single().id)
        compose.onNodeWithTag("measurement_setup").assertDoesNotExist()
    }

    /**
     * A creation stored without the alarm it asked for is made: the setup closes as after any
     * creation, never saying that nothing was made, and the list it returns to holds the
     * measurement.
     */
    @Test
    fun aCreateStoredWithoutItsAlarmIsMadeAndClosesTheSetup() {
        outcomes = ArrayDeque(listOf(ViewWrite.StoredWithoutAlarm))
        compose.setContent { App() }
        openSetup()
        compose.onNodeWithTag("measurement_setup_name").performTextReplacement("Waist")
        click("measurement_setup_next")
        click("measurement_setup_next")
        click("measurement_setup_reminder")
        click("measurement_setup_next")

        click("measurement_setup_create")

        val made = state.madeMeasurements.single()
        compose.onNodeWithTag("measurement_setup").assertDoesNotExist()
        compose.onNodeWithTag("settings_group_measurement_${made.id}").assertExists()
        assertEquals(listOf(made.id), askedUnder)
    }

    /** A checklist's setup walks its four parts on the same stepping, numbered 1 to 4 as before. */
    @Test
    fun aChecklistsSetupStillWearsItsFourNumbers() {
        compose.setContent { App() }
        click("home_settings")
        click("settings_new")
        click("settings_checklist_new")

        listOf("look" to "1 Look", "items" to "2 Items", "reminders" to "3 Reminders", "streak" to "4 Streak").forEach { (part, label) ->
            compose.onNodeWithTag("checklist_setup_step_$part").assertTextEquals(label)
        }
        compose.onNodeWithTag("checklist_setup_step_look").assertIsSelected()
        compose.onNodeWithTag("checklist_setup_step_items").assertIsNotEnabled()
        compose.onNodeWithTag("checklist_setup_next").assertIsNotEnabled()
        compose.onNodeWithTag("checklist_setup_create").assertDoesNotExist()

        compose.onNodeWithTag("checklist_setup_name").performTextReplacement("Plants")
        click("checklist_setup_next")
        compose.onNodeWithTag("checklist_setup_step_items").assertIsSelected()
        // Items waits for an item, which the bar says.
        compose.onNodeWithTag("checklist_setup_next").assertIsNotEnabled()
        // A step that is not ready says why only when its button is pressed.
        compose.onNodeWithTag("checklist_setup_next").performClick()
        compose.waitForIdle()
        assertEquals("[checklist_setup_hint] text=\"${string(R.string.checklist_setup_items_needed)}\"", line("checklist_setup_hint"))
        assertFalse(compose.transcript().any { it.startsWith("[measurement_setup") })
        click("top_back")
        compose.onNodeWithTag("checklist_setup_step_look").assertIsSelected()
    }
}
