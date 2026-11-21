package com.example.workouttracker.reminder

import com.example.workouttracker.domain.ref
import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.workouttracker.core.time.currentDateKey
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.checklist.ChecklistConfiguration
import com.example.workouttracker.domain.checklist.ChecklistDay
import com.example.workouttracker.domain.checklist.ChecklistItem
import com.example.workouttracker.domain.checklist.Checkpoint
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.measurement.MeasurementLog
import com.example.workouttracker.domain.measurement.testMadeMeasurement
import com.example.workouttracker.domain.measurement.withWeight
import com.example.workouttracker.persistence.PreferencesTrackerRepository
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager
import com.example.workouttracker.domain.questions.withEating
import com.example.workouttracker.domain.time.withReading
import com.example.workouttracker.domain.activity.withActivity

/**
 * A test uses its slot's own notification and writes no bookkeeping. So a test on its own is left
 * alone when the day is logged, and one that replaced a real reminder on show goes with it: that
 * reminder's own course still applies. It is so for a measurement, and for a checklist as ever.
 */
@RunWith(RobolectricTestRunner::class)
class TestReminderLoggingTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val todayKey = currentDateKey()
    private val today = LocalDate.parse(todayKey)
    private val waist = testMadeMeasurement().let { it.copy(checkpoint = it.checkpoint.copy(remind = true)) }
    private val plants = ChecklistConfiguration(
        id = "chk_plants",
        label = "Plants",
        icon = ViewIcon.Plant,
        hue = ViewHue.Forest,
        checkpoints = listOf(Checkpoint("cpt_morning", CheckpointTime.Fixed(LocalTime.of(8, 0)), remind = true)),
        items = listOf(ChecklistItem("itm_water", "Water the plants")),
    )
    private val state = AppState(checklists = listOf(plants)).withWeight().withEating().withReading().withActivity().let { it.copy(measurements = it.measurements + waist) }
    private val waistLogged = state.copy(measurementLogs = listOf(MeasurementLog(waist.id, todayKey, 1L, 91.5)))
    private val plantsWatered = state.copy(checklistDays = listOf(ChecklistDay(plants.id, todayKey, 1L, setOf("cpt_morning/itm_water"))))
    private val waistTest = TestReminder.Logged(waist, ReminderDays(today, today))
    private val plantsTest = TestReminder.Checklist(plants, plants.checkpoints.single(), plants.items, today)

    @Before fun allowRemindersToArrive() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        assertTrue(PreferencesTrackerRepository(context).save(state))
    }

    private fun onShow(): Set<String?> = context.getSystemService(NotificationManager::class.java).activeNotifications.mapTo(HashSet()) { it.tag }

    @Test fun aTestOnItsOwnIsLeftAloneWhenTheDayIsLogged() {
        assertTrue(context.sendTestReminder(waistTest))
        assertTrue(context.sendTestReminder(plantsTest))
        val coordinator = context.checklistReminderCoordinator()

        coordinator.onDayLogged(state, waistLogged, waist.ref, todayKey)
        coordinator.onRecordsSaved(state, plantsWatered, plants.id)

        assertEquals(setOf<String?>("measurement/msr_k2p9/day", "chk_plants/cpt_morning"), onShow())
        assertEquals(emptyMap<String, ReminderDays>(), PreferencesChecklistReminderRuntime(context).posted())
    }

    @Test fun aTestThatReplacedARealReminderIsTakenDownWhenTheDayIsLogged() {
        val coordinator = context.checklistReminderCoordinator()
        assertTrue(coordinator.onReminderDue(ReminderViewKind.Measurement, waist.id, "day"))
        assertTrue(coordinator.onReminderDue(ReminderViewKind.Checklist, plants.id, "cpt_morning"))
        val posted = PreferencesChecklistReminderRuntime(context).posted()
        assertEquals(setOf("measurement/msr_k2p9/day", "chk_plants/cpt_morning"), posted.keys)

        // The test takes the real one's place under the same tag and leaves the bookkeeping as it is.
        assertTrue(context.sendTestReminder(waistTest))
        assertTrue(context.sendTestReminder(plantsTest))
        assertEquals(setOf<String?>("measurement/msr_k2p9/day", "chk_plants/cpt_morning"), onShow())
        assertEquals(posted, PreferencesChecklistReminderRuntime(context).posted())

        coordinator.onDayLogged(state, waistLogged, waist.ref, todayKey)
        assertEquals(setOf<String?>("chk_plants/cpt_morning"), onShow())
        coordinator.onRecordsSaved(state, plantsWatered, plants.id)
        assertEquals(emptySet<String?>(), onShow())
        assertEquals(emptyMap<String, ReminderDays>(), PreferencesChecklistReminderRuntime(context).posted())
    }
}
