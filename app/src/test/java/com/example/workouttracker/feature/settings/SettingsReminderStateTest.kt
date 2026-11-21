package com.example.workouttracker.feature.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.workouttracker.R
import com.example.workouttracker.core.text.UiText
import com.example.workouttracker.core.text.resolve
import com.example.workouttracker.core.text.uiText
import com.example.workouttracker.domain.reminder.MoCheckInSettings
import com.example.workouttracker.domain.reminder.ReminderSettings
import com.example.workouttracker.domain.reminder.ReminderTime
import com.example.workouttracker.domain.reminder.WorkoutReminderSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale
import com.example.workouttracker.domain.time.testReadingTime

@RunWith(RobolectricTestRunner::class)
class SettingsReminderStateTest {
    /** A change waiting for the phone's answer comes back whole; saved screen state never crosses an update, so this is the one shape it is read in. */
    @Test
    fun pendingPermissionStateKeepsGlobalReminderSettings() {
        val settings = ReminderSettings(
            workout = WorkoutReminderSettings(
                enabled = true,
                fixedTime = ReminderTime(19, 30),
            ),
            moCheckIn = MoCheckInSettings(enabled = false, fixedTime = ReminderTime(21, 15)),
            fallbackAlarmTime = ReminderTime(7, 45),
            fixedBedtime = ReminderTime(0, 30),
        )

        assertEquals(
            settings,
            reminderSettingsFromSaveValues(reminderSettingsSaveValues(settings)),
        )
        val automatic = settings.copy(fixedBedtime = null)
        assertEquals(automatic, reminderSettingsFromSaveValues(reminderSettingsSaveValues(automatic)))
        assertEquals(null, reminderSettingsFromSaveValues(emptyList()))
    }

    @Test
    fun timingCopyUsesTheSharedFallbackAndWrapsAcrossMidnight() {
        assertEquals(
            "15 min after the next alarm · 00:10 fallback",
            reminderTimingText(
                minutesAfterAlarm = 15,
                fallbackAlarmTime = ReminderTime(23, 55),
            ).english(),
        )
    }

    private fun UiText.english(): String =
        resolve(ApplicationProvider.getApplicationContext<Context>().resources)
}
