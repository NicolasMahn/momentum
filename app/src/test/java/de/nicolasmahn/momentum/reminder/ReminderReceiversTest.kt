package de.nicolasmahn.momentum.reminder

import android.app.AlarmManager
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderReceiversTest {
    @Test
    fun exactAlarmPermissionGrantRequestsReminderReconciliation() {
        assertTrue(
            isReminderRescheduleAction(
                AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            ),
        )
    }

    @Test
    fun phoneClockAlarmChangeRequestsReminderReconciliation() {
        assertTrue(
            isReminderRescheduleAction(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED),
        )
    }

    /** After an update of the app every slot is set again, as after a restart. */
    @Test
    fun anUpdateOfTheAppRequestsReminderReconciliation() {
        assertTrue(isReminderRescheduleAction(Intent.ACTION_MY_PACKAGE_REPLACED))
    }

    @Test
    fun unrelatedBroadcastDoesNotRequestReminderReconciliation() {
        assertFalse(isReminderRescheduleAction("com.example.UNRELATED"))
        assertFalse(isReminderRescheduleAction(null))
    }

    @Test
    fun reconciliationAlwaysRunsAllReminderProducts() {
        val calls = mutableListOf<String>()

        assertFalse(
            reconcileReminders(
                workout = {
                    calls += "workout"
                    false
                },
                moCheckIn = {
                    calls += "moCheckIn"
                    true
                },
                checklists = {
                    calls += "checklists"
                    true
                },
                substances = {
                    calls += "substances"
                    true
                },
            ),
        )
        assertEquals(listOf("workout", "moCheckIn", "checklists", "substances"), calls)
    }
}
