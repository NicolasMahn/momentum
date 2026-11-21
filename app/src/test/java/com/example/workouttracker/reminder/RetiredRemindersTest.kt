package com.example.workouttracker.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.workouttracker.domain.activity.withActivity
import com.example.workouttracker.domain.care.careState
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.measurement.withWeight
import com.example.workouttracker.domain.questions.withEating
import com.example.workouttracker.domain.reminder.ReminderTime
import com.example.workouttracker.domain.time.withReading
import com.example.workouttracker.persistence.PreferencesTrackerRepository
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager

/**
 * What the build before this one set for Weight's, Supplements', Reading's, Care's and Eating's
 * own reminders is retired once, by the identity it was set with, and nothing of the shared
 * coordinator's is touched. Each old alarm and notification is written out here as that build
 * wrote it, never built by the code under test.
 */
@RunWith(RobolectricTestRunner::class)
class RetiredRemindersTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val notifications = shadowOf(context.getSystemService(NotificationManager::class.java))
    private val oldAlarms = listOf(
        WeightReminderReceiver::class.java to 8_001,
        SupplementReminderReceiver::class.java to 8_201,
        ReadingReminderReceiver::class.java to 8_301,
        MorningCareReminderReceiver::class.java to 8_401,
        EveningCareReminderReceiver::class.java to 8_402,
        EatingReminderReceiver::class.java to 8_501,
    )

    @Before fun allowRemindersToArrive() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    private fun setOldAlarm(receiver: Class<out BroadcastReceiver>, code: Int) = alarms.setExactAndAllowWhileIdle(
        AlarmManager.RTC_WAKEUP,
        1_000L,
        PendingIntent.getBroadcast(context, code, Intent(context, receiver), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
    )

    private fun notification() = Notification.Builder(context, "reminders").setSmallIcon(android.R.drawable.ic_dialog_info).build()

    private fun alarmReceivers() = shadowOf(alarms).scheduledAlarms.map { shadowOf(requireNotNull(it.operation)).savedIntent.component?.className }

    /** Every old alarm is cancelled and every old notification taken down; a slot's alarm and notification stay. */
    @Test fun theOldAlarmsAndNotificationsGoAndTheSharedOnesStay() {
        oldAlarms.forEach { (receiver, code) -> setOldAlarm(receiver, code) }
        val slot = ReminderSlot(ReminderViewKind.Checklist, "care", "morning", CheckpointTime.Fixed(LocalTime.of(8, 0)))
        assertTrue(AndroidChecklistReminderGateway(context).schedule(slot, earliestDate = null, fallbackAlarmTime = ReminderTime(7, 0), fixedBedtime = null))
        val manager = context.getSystemService(NotificationManager::class.java)
        oldAlarms.forEach { (_, code) -> manager.notify(code, notification()) }
        manager.notify("care/morning", ChecklistReminderRequestCode, notification())
        assertEquals(7, shadowOf(alarms).scheduledAlarms.size)

        context.retireOldFeatureReminders()

        assertEquals(listOf(ChecklistReminderReceiver::class.java.name), alarmReceivers())
        oldAlarms.forEach { (receiver, code) ->
            assertNull(receiver.simpleName, PendingIntent.getBroadcast(context, code, Intent(context, receiver), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE))
            assertNull(receiver.simpleName, notifications.getNotification(null, code))
        }
        assertNotNull(notifications.getNotification("care/morning", ChecklistReminderRequestCode))
    }

    @Test fun retiringAlarmsDoesNotDeriveCurrentTrackerStateFromOriginalNames() {
        val oldRuntime = context.getSharedPreferences("care_reminder_runtime", Context.MODE_PRIVATE)
        oldRuntime.edit().putString("MORNING", "2026-10-15").putString("EVENING", "2026-10-15").commit()
        val bounds = mapOf("care/evening" to LocalDate.parse("2026-10-20"))
        PreferencesChecklistReminderRuntime(context).earliest("care/evening", bounds.getValue("care/evening"))

        context.retireOldFeatureReminders()
        context.retireOldFeatureReminders()

        assertEquals(bounds, PreferencesChecklistReminderRuntime(context).earliest())
        assertEquals("2026-10-15", oldRuntime.getString("MORNING", null))
    }

    /** An old alarm delivered before anything else ran retires itself and sets the slots the views remind at. */
    @Test fun anOldAlarmDeliveredSetsTheSharedSlots() {
        PreferencesTrackerRepository(context).save(careState(remindMorning = true).withWeight().withEating().withReading().withActivity())
        setOldAlarm(MorningCareReminderReceiver::class.java, 8_401)

        MorningCareReminderReceiver().onReceive(context, Intent(context, MorningCareReminderReceiver::class.java))

        val slots = shadowOf(alarms).scheduledAlarms.map { shadowOf(requireNotNull(it.operation)).savedIntent }
            .filter { it.component?.className == ChecklistReminderReceiver::class.java.name }
        assertEquals(listOf("momentum://reminder/care%2Fmorning"), slots.map { it.dataString })
        assertTrue(oldAlarms.none { (receiver, _) -> receiver.name in alarmReceivers() })
    }
}
