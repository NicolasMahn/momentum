package com.example.workouttracker.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderActionTargetTest {
    @Test
    fun storedNotificationDateWinsOverCurrentDate() {
        assertEquals(
            "2026-09-16",
            reminderTargetDateKey("2026-09-16"),
        )
    }

    @Test
    fun oldNotificationWithoutDateFailsClosed() {
        assertNull(reminderTargetDateKey(null))
    }

    @Test
    fun impossibleDateIsRejected() {
        assertNull(reminderTargetDateKey("2026-02-30"))
    }
}
