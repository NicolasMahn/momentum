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
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import com.example.workouttracker.R
import com.example.workouttracker.core.time.currentHabitDate
import com.example.workouttracker.core.ui.theme.WorkoutTrackerTheme
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.checklist.AboutDay
import com.example.workouttracker.domain.checklist.Checkpoint
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.holiday.DayRange
import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.domain.measurement.withWeight
import com.example.workouttracker.domain.compatibility.madeQuestions
import com.example.workouttracker.domain.questions.testMadeQuestions
import com.example.workouttracker.domain.questions.withEating
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
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
import com.example.workouttracker.domain.time.withReading
import com.example.workouttracker.domain.activity.withActivity

/**
 * The Reminders part of a made questions view's page is a made measurement's, by the same code:
 * the switch, the time in three forms, the preview and the test. What it previews and sends is the
 * reminder about the day today asks about, as the draft has it.
 */
@RunWith(RobolectricTestRunner::class)
class MadeQuestionsReminderPartTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val today = currentHabitDate()
    private val sleep = testMadeQuestions(about = AboutDay.Yesterday, madeOn = today.minusDays(30))
    private var state by mutableStateOf(AppState().withWeight().withEating().withReading().withActivity().let { it.copy(questions = it.questions + sleep) })
    private var visibility by mutableStateOf(FeatureVisibilitySettings())
    private var daysOff by mutableStateOf(HolidaySchedule())

    @Before fun allowRemindersToArrive() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @Composable
    private fun App() {
        WorkoutTrackerTheme { MomentumApp(state = state, onStateChange = { state = it; true }, onActivitySave = ActivitySaveNotExpected, featureVisibility = visibility, holidaySchedule = daysOff) }
    }

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun openReminders() {
        click("home_settings")
        click("settings_group_questions_${sleep.id}")
        click("questions_settings_tab_reminders")
    }

    private fun string(id: Int, vararg arguments: Any): String = context.getString(id, *arguments)

    private fun line(tag: String): String = compose.transcript().single { it.startsWith("[$tag]") }

    /** Off at 09:00, it previews the reminder about yesterday; switched on at another time, one Save stores both. */
    @Test
    fun itPreviewsTheDayAskedAboutAndTakesASwitchAndATime() {
        compose.setContent { App() }
        openReminders()

        compose.onNodeWithTag("questions_settings_reminder").assertIsOff()
        assertEquals("[questions_settings_reminder_preview_title] text=\"Sleep\"", line("questions_settings_reminder_preview_title"))
        assertEquals("[questions_settings_reminder_preview_text] text=\"Not answered yet for yesterday\"", line("questions_settings_reminder_preview_text"))
        assertEquals("[questions_settings_reminder_preview_note] text=\"${string(R.string.checklist_editor_reminder_off)}\"", line("questions_settings_reminder_preview_note"))

        // The preview reads the draft: about today on the Questions part, it says today.
        click("questions_settings_tab_questions")
        click("questions_settings_about_today")
        click("questions_settings_tab_reminders")
        assertEquals("[questions_settings_reminder_preview_text] text=\"Not answered yet today\"", line("questions_settings_reminder_preview_text"))

        click("questions_settings_reminder")
        click("questions_settings_reminder_time_after_wake")
        click("questions_settings_save")
        assertEquals(Checkpoint("day", CheckpointTime.AfterWake(15), remind = true), state.madeQuestions.single().checkpoint)
        assertEquals(AboutDay.Today, state.madeQuestions.single().about.on(today))
    }

    /**
     * "Send a test" posts the reminder as the draft has it under the view's own slot, with the
     * switch off too, and its tap opens the view on the day asked about. It sets no alarm and is
     * not kept in the reminders' bookkeeping.
     */
    @Test
    fun aTestSendsTheReminderAboutTheDayAskedAbout() {
        compose.setContent { App() }
        openReminders()
        compose.onNodeWithTag("questions_settings_reminder_test").assertIsEnabled()

        click("questions_settings_reminder_test")

        val posted = requireNotNull(shadowOf(context.getSystemService(NotificationManager::class.java)).getNotification("questions/${sleep.id}/day", 20_000))
        assertEquals("Sleep", posted.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString())
        assertEquals("Not answered yet for yesterday", posted.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())
        assertEquals(
            AppNavigationRequest(AppDestination.Questions, loggedViewId = sleep.id, loggedDateKey = today.minusDays(1).toString()),
            shadowOf(posted.contentIntent).savedIntent.requestedAppNavigation(),
        )
        assertTrue(shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.isEmpty())
        assertEquals(emptyMap<String, Any>(), PreferencesChecklistReminderRuntime(context).posted())
        assertEquals(sleep, state.madeQuestions.single())
    }

    /**
     * Hidden, no reminder of it can come: its row says so and there is no test. Asking about an
     * away day it was not due on, there is nothing to preview or to send; "Not due" chosen today
     * leaves yesterday the rule it had.
     */
    @Test
    fun hiddenOrAskingAboutADayItWasNotDueOnThereIsNothingToSend() {
        visibility = FeatureVisibilitySettings(hiddenQuestions = setOf(sleep.id))
        compose.setContent { App() }
        openReminders()
        assertEquals("[questions_settings_reminder_dependency] text=\"${string(R.string.settings_turn_feature_on, "Sleep")}\"", line("questions_settings_reminder_dependency"))
        compose.onNodeWithTag("questions_settings_reminder_test").assertIsNotEnabled()

        // Away yesterday, and still due then: chosen today, "Not due" starts today.
        visibility = FeatureVisibilitySettings()
        daysOff = HolidaySchedule(listOf(DayRange(today.minusDays(1), today.minusDays(1))))
        compose.waitForIdle()
        click("questions_settings_tab_streak")
        click("questions_settings_away_excused")
        click("questions_settings_tab_reminders")
        compose.onNodeWithTag("questions_settings_reminder_preview_title").assertExists()
        compose.onNodeWithTag("questions_settings_reminder_test").assertIsEnabled()

        // Not due yesterday already, today's reminder has nothing to ask.
        state = state.copy(questions = state.questions.map { if (it.id == sleep.id) it.copy(whenAway = Dated.always(WhenAway.Excused)) else it })
        compose.waitForIdle()
        compose.onNodeWithTag("questions_settings_reminder_preview_title").assertDoesNotExist()
        assertEquals("[questions_settings_reminder_preview_note] text=\"${string(R.string.checklist_editor_reminder_nothing_due)}\"", line("questions_settings_reminder_preview_note"))
        compose.onNodeWithTag("questions_settings_reminder_test").assertIsNotEnabled()
    }
}
