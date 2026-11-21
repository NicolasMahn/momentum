package com.example.workouttracker.domain.reminder

import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderSettingsTest {
    @Test
    fun moCheckInIsOnFromTheStartAtEightInTheEvening() {
        assertEquals(
            MoCheckInSettings(enabled = true, fixedTime = ReminderTime(20, 0)),
            ReminderSettings().moCheckIn,
        )
    }
}
