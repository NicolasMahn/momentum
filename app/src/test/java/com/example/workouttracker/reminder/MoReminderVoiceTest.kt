package com.example.workouttracker.reminder

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.workouttracker.core.text.UiText
import com.example.workouttracker.core.text.resolve
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MoReminderVoiceTest {
    private val resources = ApplicationProvider.getApplicationContext<Context>().resources
    private val day = LocalDate.parse("2026-09-20")

    @Test fun activityNamesItsConfiguredTrackerPlannedActivityAndNextRoutineDay() {
        val subject = MoReminderSubject.Activity("gym", UiText.Verbatim("Gym"), UiText.Verbatim("Day B"), "Move my way")
        (0L until 7L).forEach { offset ->
            val message = MoReminderVoice.speak(subject, null, day.plusDays(offset))
            assertEquals("Move my way", message.title.resolve(resources))
            val line = message.text.resolve(resources)
            assertTrue(line, line.contains("Gym"))
            assertTrue(line, line.endsWith(" Day B is next."))
            assertEquals(message, MoReminderVoice.speak(subject, null, day.plusDays(offset)))
        }
    }

    @Test fun anyActivityDoesNotInventAPlannedActivityOrRoutine() {
        val subject = MoReminderSubject.Activity(null, null, null, "Be active")
        (0L until 7L).forEach { offset ->
            val message = MoReminderVoice.speak(subject, null, day.plusDays(offset))
            assertEquals("Be active", message.title.resolve(resources))
            val line = message.text.resolve(resources)
            assertFalse(line, line.contains("Gym") || line.contains("Day B") || line.contains("is next"))
        }
    }
}
