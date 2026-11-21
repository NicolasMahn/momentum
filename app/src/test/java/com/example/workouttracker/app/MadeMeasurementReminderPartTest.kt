package com.example.workouttracker.app

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
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
import com.example.workouttracker.domain.holiday.DayRange
import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.domain.measurement.MeasurementEdit
import com.example.workouttracker.domain.measurement.MeasurementLog
import com.example.workouttracker.domain.measurement.applyingMeasurementEdits
import com.example.workouttracker.domain.compatibility.madeMeasurements
import com.example.workouttracker.domain.measurement.testMadeMeasurement
import com.example.workouttracker.domain.measurement.testWeightMeasurement
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import com.example.workouttracker.feature.settings.ViewWrite
import com.example.workouttracker.feature.weight.transcript
import com.example.workouttracker.reminder.PreferencesChecklistReminderRuntime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager

/**
 * The Reminders part of a made measurement's page, through the app as a person meets it: one
 * reminder with a switch and a time in three forms, the row every reminder has, and the preview
 * of what it will say with a test. Look, Reminders and Streak are one draft, stored by one Save.
 */
@RunWith(RobolectricTestRunner::class)
class MadeMeasurementReminderPartTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val today = currentHabitDate()
    private val waist = testMadeMeasurement()
    private val records = listOf(MeasurementLog(waist.id, today.toString(), 2L, 92.0), MeasurementLog("weight", today.toString(), 1L, 81.4))
    private var state by mutableStateOf(AppState(measurements = listOf(testWeightMeasurement(), waist), measurementLogs = records))
    private var visibility by mutableStateOf(FeatureVisibilitySettings())
    private var daysOff by mutableStateOf(HolidaySchedule())

    /** Every Save the page made: the edits it handed over, in order. */
    private val saves = mutableListOf<List<MeasurementEdit>>()

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
                featureVisibility = visibility,
                holidaySchedule = daysOff,
                onMeasurementSave = { measurementId, edits ->
                    saves += edits
                    state = state.applyingMeasurementEdits(measurementId, edits, today)
                    ViewWrite.Stored
                },
            )
        }
    }

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun openReminders() {
        click("home_settings")
        click("settings_group_measurement_${waist.id}")
        click("measurement_settings_tab_reminders")
        compose.onNodeWithTag("measurement_settings_tab_reminders").assertIsSelected()
    }

    private fun stored() = state.madeMeasurements.single()

    private fun string(id: Int, vararg arguments: Any): String = context.getString(id, *arguments)

    private fun line(tag: String): String = compose.transcript().single { it.startsWith("[$tag]") }

    @Test
    fun itStartsOffAtNineAndTakesASwitchAndATimeInThreeForms() {
        compose.setContent { App() }
        openReminders()

        // Off, at 09:00, with the preview of what it would say and why nothing goes out.
        compose.onNodeWithTag("measurement_settings_reminder").assertIsOff()
        compose.onNodeWithTag("measurement_settings_reminder_time_fixed").assertIsSelected()
        compose.onNodeWithTag("measurement_settings_reminder_time").assertTextEquals("09:00")
        assertEquals("[measurement_settings_reminder_timing] text=\"${string(R.string.settings_timing_every_day, "09:00")}\"", line("measurement_settings_reminder_timing"))
        assertEquals("[measurement_settings_reminder_preview_title] text=\"Waist\"", line("measurement_settings_reminder_preview_title"))
        assertEquals("[measurement_settings_reminder_preview_text] text=\"${string(R.string.measurement_reminder_open)}\"", line("measurement_settings_reminder_preview_text"))
        assertEquals("[measurement_settings_reminder_preview_note] text=\"${string(R.string.checklist_editor_reminder_off)}\"", line("measurement_settings_reminder_preview_note"))
        compose.onNodeWithTag("measurement_settings_reminder_dependency").assertDoesNotExist()
        compose.onNodeWithTag("measurement_settings_save").assertIsNotEnabled()

        click("measurement_settings_reminder")
        compose.onNodeWithTag("measurement_settings_reminder").assertIsOn()
        compose.onNodeWithTag("measurement_settings_reminder_preview_note").assertDoesNotExist()

        // After waking and before sleep each take their minutes and say what they come to today.
        click("measurement_settings_reminder_time_after_wake")
        compose.onNodeWithTag("measurement_settings_reminder_time").assertDoesNotExist()
        compose.onNodeWithTag("measurement_settings_reminder_today").assertExists()
        click("measurement_settings_reminder_offset_more")
        click("measurement_settings_reminder_time_before_sleep")
        compose.onNodeWithTag("measurement_settings_reminder_time_before_sleep").assertIsSelected()
        click("measurement_settings_reminder_time_after_wake")
        click("measurement_settings_reminder_offset_more")

        // Nothing is stored before Save, which stores the edits in the order they were made.
        assertEquals(waist, stored())
        click("measurement_settings_save")
        compose.onNodeWithTag("measurement_settings").assertDoesNotExist()
        assertEquals(Checkpoint("day", CheckpointTime.AfterWake(20), remind = true), stored().checkpoint)
        assertEquals(waist.copy(checkpoint = stored().checkpoint), stored())
        assertEquals(1, saves.size)
        assertEquals(records, state.measurementLogs)

        // Opened again, the part shows what was stored.
        click("settings_group_measurement_${waist.id}")
        click("measurement_settings_tab_reminders")
        compose.onNodeWithTag("measurement_settings_reminder").assertIsOn()
        compose.onNodeWithTag("measurement_settings_reminder_time_after_wake").assertIsSelected()
    }

    /** Look, Reminders and Streak change one draft, and one Save stores all of it in one update. */
    @Test
    fun allThreePartsAreOneDraftStoredByOneSave() {
        compose.setContent { App() }
        click("home_settings")
        click("settings_group_measurement_${waist.id}")

        compose.onNodeWithTag("measurement_settings_name").performTextReplacement("Hips")
        click("measurement_settings_tab_reminders")
        // The preview reads the draft: it already says the name not yet saved.
        assertEquals("[measurement_settings_reminder_preview_title] text=\"Hips\"", line("measurement_settings_reminder_preview_title"))
        click("measurement_settings_reminder")
        click("measurement_settings_tab_streak")
        click("measurement_settings_rewards")
        click("measurement_settings_away_excused")
        // Each part kept what was done on it while another was looked at.
        click("measurement_settings_tab_reminders")
        compose.onNodeWithTag("measurement_settings_reminder").assertIsOn()
        click("measurement_settings_tab_look")
        assertTrue(compose.transcript().toString(), "[measurement_settings_name] field=\"Hips\"" in compose.transcript())
        assertEquals(waist, stored())

        click("measurement_settings_save")

        assertEquals(
            listOf(listOf(MeasurementEdit.Rename("Hips"), MeasurementEdit.SetRemind(true), MeasurementEdit.JoinRewards, MeasurementEdit.SetWhenAway(WhenAway.Excused))),
            saves,
        )
        assertEquals("Hips", stored().label)
        assertEquals(Checkpoint("day", CheckpointTime.Fixed(java.time.LocalTime.of(9, 0)), remind = true), stored().checkpoint)
        assertEquals(ChecklistRewards(maxPasses = 3, everyKeptDays = 7, periods = listOf(RewardPeriod(today))), stored().standardRewards)
        assertEquals(Dated(listOf(Dated.Revision(WhenAway.Due), Dated.Revision(WhenAway.Excused, today))), stored().whenAway)
        assertEquals(records, state.measurementLogs)
        assertEquals(testWeightMeasurement(), state.measurements.first())
        compose.onNodeWithTag("measurement_settings").assertDoesNotExist()
    }

    /**
     * "Send a test" posts the reminder as the draft has it, as if today were not logged, with the
     * switch off too. It is the slot's own notification and opens the measurement on today; it sets
     * no alarm and is not kept in the reminders' bookkeeping.
     */
    @Test
    fun aTestSendsWhatTheDraftSaysAndTouchesNothingElse() {
        compose.setContent { App() }
        openReminders()
        click("measurement_settings_tab_look")
        compose.onNodeWithTag("measurement_settings_name").performTextReplacement("Hips")
        click("measurement_settings_tab_reminders")
        compose.onNodeWithTag("measurement_settings_reminder").assertIsOff()
        compose.onNodeWithTag("measurement_settings_reminder_test").assertIsEnabled()

        click("measurement_settings_reminder_test")

        val posted = requireNotNull(shadowOf(context.getSystemService(NotificationManager::class.java)).getNotification("measurement/${waist.id}/day", 20_000))
        assertEquals("Hips", posted.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString())
        assertEquals(string(R.string.measurement_reminder_open), posted.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())
        assertEquals(
            AppNavigationRequest(AppDestination.Measurement, loggedViewId = waist.id, loggedDateKey = today.toString()),
            shadowOf(posted.contentIntent).savedIntent.requestedAppNavigation(),
        )
        assertTrue(shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.isEmpty())
        assertEquals(emptyMap<String, Any>(), PreferencesChecklistReminderRuntime(context).posted())
        assertEquals(emptySet<String>(), PreferencesChecklistReminderRuntime(context).scheduled())
        // The draft is still a draft.
        assertEquals(waist, stored())
        assertEquals(emptyList<List<MeasurementEdit>>(), saves)
    }

    /** Hidden, no reminder of it can come: its row says so, and there is no test to send. Its settings stay open. */
    @Test
    fun aHiddenMeasurementsRowSaysSoAndHasNoTest() {
        visibility = FeatureVisibilitySettings(hiddenMeasurements = setOf(waist.id))
        compose.setContent { App() }
        openReminders()

        assertEquals(
            "[measurement_settings_reminder_dependency] text=\"${string(R.string.settings_turn_feature_on, "Waist")}\"",
            line("measurement_settings_reminder_dependency"),
        )
        compose.onNodeWithTag("measurement_settings_reminder").assertIsNotEnabled()
        compose.onNodeWithTag("measurement_settings_reminder_test").assertIsNotEnabled()
        compose.onNodeWithTag("measurement_settings_reminder_allow").assertDoesNotExist()
    }

    /** On an away day the draft is not due on, the reminder would stay quiet: the preview says so and there is nothing to send. */
    @Test
    fun onAnAwayDayItIsNotDueOnThereIsNothingToPreviewOrSend() {
        daysOff = HolidaySchedule(listOf(DayRange(today, today)))
        compose.setContent { App() }
        openReminders()
        compose.onNodeWithTag("measurement_settings_reminder_preview_title").assertExists()
        compose.onNodeWithTag("measurement_settings_reminder_test").assertIsEnabled()

        click("measurement_settings_tab_streak")
        click("measurement_settings_away_excused")
        click("measurement_settings_tab_reminders")

        compose.onNodeWithTag("measurement_settings_reminder_preview_title").assertDoesNotExist()
        assertEquals(
            "[measurement_settings_reminder_preview_note] text=\"${string(R.string.checklist_editor_reminder_nothing_due)}\"",
            line("measurement_settings_reminder_preview_note"),
        )
        compose.onNodeWithTag("measurement_settings_reminder_test").assertIsNotEnabled()
    }

    /** The part, the switch and the time picked on it survive process death with the rest of the draft. */
    @Test
    fun thePartAndItsEditsSurviveProcessDeath() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { App() }
        openReminders()
        click("measurement_settings_reminder")
        click("measurement_settings_reminder_time_before_sleep")

        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()

        compose.onNodeWithTag("measurement_settings_tab_reminders").assertIsSelected()
        compose.onNodeWithTag("measurement_settings_reminder").assertIsOn()
        compose.onNodeWithTag("measurement_settings_reminder_time_before_sleep").assertIsSelected()
        click("measurement_settings_save")
        assertEquals(Checkpoint("day", CheckpointTime.BeforeSleep(15), remind = true), stored().checkpoint)
    }
}
