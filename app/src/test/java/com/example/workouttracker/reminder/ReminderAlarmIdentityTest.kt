package com.example.workouttracker.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.checklist.ChecklistConfiguration
import com.example.workouttracker.domain.checklist.ChecklistItem
import com.example.workouttracker.domain.checklist.Checkpoint
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.measurement.testMadeMeasurement
import com.example.workouttracker.domain.measurement.withWeight
import com.example.workouttracker.domain.reminder.ReminderTime
import com.example.workouttracker.persistence.PreferencesTrackerRepository
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager
import com.example.workouttracker.domain.questions.testMadeQuestions
import com.example.workouttracker.domain.questions.withEating
import com.example.workouttracker.domain.time.testMadeTime
import com.example.workouttracker.domain.time.withReading
import com.example.workouttracker.domain.activity.withActivity

/**
 * What a checklist's reminder left on the phone before a measurement could remind is still that
 * checklist's afterwards: its alarm, the intent that alarm delivers, and its bookkeeping. Each is
 * written out here as the version before wrote it, never built by the code under test.
 */
@RunWith(RobolectricTestRunner::class)
class ReminderAlarmIdentityTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val plants = ChecklistConfiguration(
        id = "chk_plants",
        label = "Plants",
        icon = ViewIcon.Plant,
        hue = ViewHue.Forest,
        checkpoints = listOf(Checkpoint("cpt_morning", CheckpointTime.Fixed(LocalTime.of(8, 0)), remind = true)),
        items = listOf(ChecklistItem("itm_water", "Water the plants")),
    )
    private val plantsSlot = ReminderSlot(ReminderViewKind.Checklist, "chk_plants", "cpt_morning", CheckpointTime.Fixed(LocalTime.of(8, 0)))

    @Before fun allowRemindersToArrive() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    /** The intent of a checklist's alarm exactly as the version before this block built it. */
    private fun alarmIntentSetBefore() = Intent(context, ChecklistReminderReceiver::class.java).apply {
        action = "com.example.workouttracker.action.CHECKLIST_REMINDER"
        data = Uri.parse("momentum://reminder/chk_plants%2Fcpt_morning")
        putExtra("com.example.workouttracker.extra.REMINDER_CHECKLIST_ID", "chk_plants")
        putExtra("com.example.workouttracker.extra.REMINDER_CHECKPOINT_ID", "cpt_morning")
    }

    private fun setAlarmAsBefore() = alarms.setExactAndAllowWhileIdle(
        AlarmManager.RTC_WAKEUP,
        1_000L,
        PendingIntent.getBroadcast(context, 20_000, alarmIntentSetBefore(), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
    )

    @Test fun aChecklistsAlarmSetBeforeIsReplacedByItsSlotNeverSetTwice() {
        setAlarmAsBefore()
        assertEquals(1, shadowOf(alarms).scheduledAlarms.size)

        assertTrue(AndroidChecklistReminderGateway(context).schedule(plantsSlot, earliestDate = null, fallbackAlarmTime = ReminderTime(7, 0), fixedBedtime = null))

        val set = shadowOf(alarms).scheduledAlarms.single()
        assertTrue(set.triggerAtMs > 1_000L)
        // What it delivers names the checklist exactly as it did.
        val delivered = shadowOf(requireNotNull(set.operation)).savedIntent
        assertTrue(delivered.filterEquals(alarmIntentSetBefore()))
        assertEquals("chk_plants", delivered.getStringExtra("com.example.workouttracker.extra.REMINDER_CHECKLIST_ID"))
        assertEquals("cpt_morning", delivered.getStringExtra("com.example.workouttracker.extra.REMINDER_CHECKPOINT_ID"))
        assertEquals(setOf("com.example.workouttracker.extra.REMINDER_CHECKLIST_ID", "com.example.workouttracker.extra.REMINDER_CHECKPOINT_ID"), delivered.extras?.keySet())
    }

    @Test fun aChecklistsAlarmSetBeforeIsCancelledByItsKeyAndByNoMeasurements() {
        setAlarmAsBefore()
        val gateway = AndroidChecklistReminderGateway(context)
        // A measurement under the checklist's ID, with a checkpoint under its checkpoint's.
        val measurement = ReminderSlot(ReminderViewKind.Measurement, "chk_plants", "cpt_morning", CheckpointTime.Fixed(LocalTime.of(9, 0)))
        assertTrue(gateway.schedule(measurement, earliestDate = null, fallbackAlarmTime = ReminderTime(7, 0), fixedBedtime = null))
        assertEquals(2, shadowOf(alarms).scheduledAlarms.size)

        gateway.cancel(measurement.key)
        assertTrue(shadowOf(requireNotNull(shadowOf(alarms).scheduledAlarms.single().operation)).savedIntent.filterEquals(alarmIntentSetBefore()))

        gateway.cancel("chk_plants/cpt_morning")
        assertEquals(0, shadowOf(alarms).scheduledAlarms.size)
    }

    /** Delivered after the update, the alarm rings its checklist and sets tomorrow's, once. */
    @Test fun anAlarmSetBeforeStillRingsItsChecklist() {
        PreferencesTrackerRepository(context).save(AppState(checklists = listOf(plants)).withWeight().withEating().withReading().withActivity())
        setAlarmAsBefore()

        ChecklistReminderReceiver().onReceive(context, alarmIntentSetBefore())

        val posted = requireNotNull(shadowOf(context.getSystemService(NotificationManager::class.java)).getNotification("chk_plants/cpt_morning", 20_000))
        assertEquals("Plants", posted.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString())
        assertTrue(shadowOf(requireNotNull(shadowOf(alarms).scheduledAlarms.single().operation)).savedIntent.filterEquals(alarmIntentSetBefore()))
        assertEquals(setOf("chk_plants/cpt_morning"), PreferencesChecklistReminderRuntime(context).scheduled())
    }

    /**
     * A measurement's alarm, delivered, rings that measurement under its own tag and never the
     * checklist that shares its ID and its checkpoint's; the checklist's alarm rings the checklist.
     */
    @Test fun eachKindsAlarmRingsItsOwnViewUnderOneId() {
        val checklist = plants.copy(id = "same", checkpoints = listOf(plants.checkpoints.single().copy(id = "day")))
        val mood = testMadeMeasurement(id = "same", label = "Mood", unit = "", step = "1").let { it.copy(checkpoint = it.checkpoint.copy(remind = true)) }
        PreferencesTrackerRepository(context).save(AppState(checklists = listOf(checklist)).withWeight().withEating().withReading().withActivity().let { it.copy(measurements = it.measurements + mood) })
        assertTrue(context.checklistReminderCoordinator().reconcile())
        val delivered = shadowOf(alarms).scheduledAlarms.map { shadowOf(requireNotNull(it.operation)).savedIntent }
        assertEquals(
            listOf("momentum://reminder/same%2Fday", "momentum://reminder/measurement%2Fsame%2Fday"),
            delivered.map { it.dataString },
        )
        val notifications = shadowOf(context.getSystemService(NotificationManager::class.java))

        ChecklistReminderReceiver().onReceive(context, delivered[1])
        assertEquals("Mood", notifications.getNotification("measurement/same/day", 20_000)?.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString())
        assertNull(notifications.getNotification("same/day", 20_000))

        ChecklistReminderReceiver().onReceive(context, delivered[0])
        assertEquals("Plants", notifications.getNotification("same/day", 20_000)?.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString())
        assertEquals(2, shadowOf(alarms).scheduledAlarms.size)
        assertEquals(setOf("same/day", "measurement/same/day"), PreferencesChecklistReminderRuntime(context).posted().keys)
    }

    /**
     * A questions view's alarm names its view in an extra of its own and its slot in its data, and
     * delivered it rings that view alone: not the checklist nor the measurement that share its ID
     * and its checkpoint's, whose alarms are what they were.
     */
    @Test fun aQuestionsViewsAlarmRingsItsOwnViewUnderAnIdAllThreeKindsShare() {
        val checklist = plants.copy(id = "qst_same", checkpoints = listOf(plants.checkpoints.single().copy(id = "day")))
        val mood = testMadeMeasurement(id = "qst_same", label = "Mood", unit = "", step = "1").let { it.copy(checkpoint = it.checkpoint.copy(remind = true)) }
        val evening = testMadeQuestions(id = "qst_same", label = "Evening", madeOn = LocalDate.parse("2026-01-01")).let { it.copy(checkpoint = it.checkpoint.copy(remind = true)) }
        PreferencesTrackerRepository(context).save(
            AppState(checklists = listOf(checklist)).withWeight().withEating().withReading().withActivity().let { it.copy(measurements = it.measurements + mood, questions = it.questions + evening) },
        )
        assertTrue(context.checklistReminderCoordinator().reconcile())
        val delivered = shadowOf(alarms).scheduledAlarms.map { shadowOf(requireNotNull(it.operation)).savedIntent }
        assertEquals(
            listOf("momentum://reminder/qst_same%2Fday", "momentum://reminder/measurement%2Fqst_same%2Fday", "momentum://reminder/questions%2Fqst_same%2Fday"),
            delivered.map { it.dataString },
        )
        assertEquals(
            listOf("REMINDER_CHECKLIST_ID", "REMINDER_MEASUREMENT_ID", "REMINDER_QUESTIONS_ID").map { setOf("com.example.workouttracker.extra.$it", "com.example.workouttracker.extra.REMINDER_CHECKPOINT_ID") },
            delivered.map { it.extras?.keySet() },
        )
        val notifications = shadowOf(context.getSystemService(NotificationManager::class.java))

        ChecklistReminderReceiver().onReceive(context, delivered[2])
        assertEquals("Evening", notifications.getNotification("questions/qst_same/day", 20_000)?.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString())
        assertNull(notifications.getNotification("qst_same/day", 20_000))
        assertNull(notifications.getNotification("measurement/qst_same/day", 20_000))
        assertEquals(3, shadowOf(alarms).scheduledAlarms.size)
        assertEquals(setOf("questions/qst_same/day"), PreferencesChecklistReminderRuntime(context).posted().keys)
    }

    /**
     * A time view's alarm names its view in an extra of its own and its slot in its data, and
     * delivered it rings that view alone, saying the minutes its week still asks for: not the
     * measurement that shares its ID and its checkpoint's, whose alarm is what it was.
     */
    @Test fun aTimeViewsAlarmRingsItsOwnViewUnderAnIdAMeasurementShares() {
        val mood = testMadeMeasurement(id = "tim_k2p9", label = "Mood", unit = "", step = "1").let { it.copy(checkpoint = it.checkpoint.copy(remind = true)) }
        val piano = testMadeTime().let { it.copy(checkpoint = it.checkpoint.copy(remind = true)) }
        PreferencesTrackerRepository(context).save(
            AppState().withWeight().withEating().withReading().withActivity().let { it.copy(measurements = it.measurements + mood, time = it.time + piano) },
        )
        assertTrue(context.checklistReminderCoordinator().reconcile())
        val delivered = shadowOf(alarms).scheduledAlarms.map { shadowOf(requireNotNull(it.operation)).savedIntent }
        assertEquals(listOf("momentum://reminder/measurement%2Ftim_k2p9%2Fday", "momentum://reminder/time%2Ftim_k2p9%2Fday"), delivered.map { it.dataString })
        assertEquals(
            listOf("REMINDER_MEASUREMENT_ID", "REMINDER_TIME_ID").map { setOf("com.example.workouttracker.extra.$it", "com.example.workouttracker.extra.REMINDER_CHECKPOINT_ID") },
            delivered.map { it.extras?.keySet() },
        )
        val notifications = shadowOf(context.getSystemService(NotificationManager::class.java))

        ChecklistReminderReceiver().onReceive(context, delivered[1])
        val posted = notifications.getNotification("time/tim_k2p9/day", 20_000)
        assertEquals("Piano", posted?.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString())
        assertEquals("60 minutes to go this week", posted?.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString())
        assertNull(notifications.getNotification("measurement/tim_k2p9/day", 20_000))
        assertEquals(2, shadowOf(alarms).scheduledAlarms.size)
        assertEquals(setOf("time/tim_k2p9/day"), PreferencesChecklistReminderRuntime(context).posted().keys)
    }

    /** The bookkeeping as the version before wrote it, in both forms a posted notification ever had. */
    @Test fun bookkeepingWrittenBeforeIsReadAsTheChecklists() {
        context.getSharedPreferences("checklist_reminder_runtime", Context.MODE_PRIVATE).edit()
            .putStringSet("scheduled", setOf("chk_plants/cpt_morning"))
            .putString("posted", """{"chk_plants/cpt_morning":{"due":"2026-10-13","record":"2026-10-12"},"chk_plants/cpt_evening":"2026-10-12"}""")
            .commit()
        val runtime = PreferencesChecklistReminderRuntime(context)

        assertEquals(setOf("chk_plants/cpt_morning"), runtime.scheduled())
        assertEquals(
            mapOf(
                "chk_plants/cpt_morning" to ReminderDays(LocalDate.parse("2026-10-13"), LocalDate.parse("2026-10-12")),
                "chk_plants/cpt_evening" to ReminderDays(LocalDate.parse("2026-10-12"), LocalDate.parse("2026-10-12")),
            ),
            runtime.posted(),
        )

        // The first reconcile after the update keeps the checklist's alarm as its own.
        PreferencesTrackerRepository(context).save(AppState(checklists = listOf(plants)).withWeight().withEating().withReading().withActivity())
        setAlarmAsBefore()
        assertTrue(context.checklistReminderCoordinator().reconcile())
        assertEquals(1, shadowOf(alarms).scheduledAlarms.size)
        assertEquals(setOf("chk_plants/cpt_morning"), runtime.scheduled())
        assertNull(shadowOf(context.getSystemService(NotificationManager::class.java)).getNotification("chk_plants/cpt_morning", 20_000))
    }
}
