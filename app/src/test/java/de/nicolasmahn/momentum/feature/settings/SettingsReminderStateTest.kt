package de.nicolasmahn.momentum.feature.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.core.text.UiText
import de.nicolasmahn.momentum.core.text.resolve
import de.nicolasmahn.momentum.core.text.uiText
import de.nicolasmahn.momentum.domain.reminder.MoCheckInSettings
import de.nicolasmahn.momentum.domain.reminder.ReminderSettings
import de.nicolasmahn.momentum.domain.reminder.ReminderTime
import de.nicolasmahn.momentum.domain.reminder.WorkoutReminderSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale
import de.nicolasmahn.momentum.domain.time.testReadingTime

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
