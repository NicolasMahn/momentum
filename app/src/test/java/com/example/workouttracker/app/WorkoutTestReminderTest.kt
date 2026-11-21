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
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import com.example.workouttracker.core.ui.theme.WorkoutTrackerTheme
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.activity.WeekPlan
import com.example.workouttracker.domain.activity.testActivityConfiguration
import com.example.workouttracker.domain.activity.withActivity
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.reminder.ReminderSettings
import com.example.workouttracker.domain.reminder.WorkoutReminderSettings
import com.example.workouttracker.reminder.WorkoutReminderNotification
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager

/**
 * "Send a test" on Activity's Reminders part sends the workout reminder whatever the day: the
 * week's days decide only when the real one comes. So it sends on a day the reminder is off, and
 * after midnight it never waits for a weekday the page was opened on.
 */
@RunWith(RobolectricTestRunner::class)
class WorkoutTestReminderTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** A week of tomorrow alone, so today is a day the reminder is off whatever day it is. */
    private val tomorrowOnly = WeekPlan.of(LocalDate.now(ZoneId.systemDefault()).dayOfWeek.plus(1))
    private var state by mutableStateOf(AppState().withActivity(testActivityConfiguration().copy(week = Dated.always(tomorrowOnly), whenAway = Dated.always(WhenAway.Due))))
    private var reminders by mutableStateOf(ReminderSettings(workout = WorkoutReminderSettings(enabled = true)))

    @Before fun allowRemindersToArrive() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @Composable
    private fun App() {
        WorkoutTrackerTheme {
            MomentumApp(state = state, onStateChange = { state = it; true }, onActivitySave = ActivitySaveNotExpected, reminderSettings = reminders, onReminderSettingsChange = { reminders = it; true })
        }
    }

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    @Test
    fun aTestSendsOnADayTheReminderIsOff() {
        compose.setContent { App() }
        click("home_settings")
        click("settings_group_activity")
        click("feature_settings_tab_week")
        compose.onNodeWithTag("settings_workout_reminder_test").assertIsEnabled()

        click("settings_workout_reminder_test")
        assertNotNull(shadowOf(context.getSystemService(NotificationManager::class.java)).getNotification(WorkoutReminderNotification.WorkoutNotificationId))
        compose.onNodeWithTag("settings_reminder_error").assertDoesNotExist()
    }
}
