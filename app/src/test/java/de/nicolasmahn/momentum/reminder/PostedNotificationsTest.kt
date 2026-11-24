package de.nicolasmahn.momentum.reminder

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.core.text.uiText
import de.nicolasmahn.momentum.app.AppDestination
import de.nicolasmahn.momentum.app.AppNavigationRequest
import de.nicolasmahn.momentum.app.requestedAppNavigation
import de.nicolasmahn.momentum.domain.checklist.AboutDay
import de.nicolasmahn.momentum.domain.checklist.ChecklistConfiguration
import de.nicolasmahn.momentum.domain.checklist.ChecklistItem
import de.nicolasmahn.momentum.domain.checklist.Checkpoint
import de.nicolasmahn.momentum.domain.checklist.CheckpointTime
import de.nicolasmahn.momentum.domain.checklist.Dated
import de.nicolasmahn.momentum.domain.checklist.ViewHue
import de.nicolasmahn.momentum.domain.checklist.ViewIcon
import de.nicolasmahn.momentum.domain.measurement.testMadeMeasurement
import de.nicolasmahn.momentum.domain.questions.testMadeQuestions
import de.nicolasmahn.momentum.domain.time.testMadeTime
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * The notifications on show as the app reads them back: the days each asked about, and which are
 * still on show at all.
 */
@RunWith(RobolectricTestRunner::class)
class PostedNotificationsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val tuesday = LocalDate.parse("2026-10-13")
    private val monday = tuesday.minusDays(1)
    private val lastNight = ChecklistConfiguration(
        id = "chk_night",
        label = "Yesterday",
        icon = ViewIcon.Journal,
        hue = ViewHue.Plum,
        checkpoints = listOf(Checkpoint("cpt_morning", CheckpointTime.Fixed(LocalTime.of(8, 0)), remind = true)),
        items = listOf(ChecklistItem("itm_violin", "Practiced violin")),
        // Switched to about today on Tuesday, after Tuesday's reminder asked about Monday.
        about = Dated(listOf(Dated.Revision(AboutDay.Yesterday), Dated.Revision(AboutDay.Today, tuesday))),
    )

    @Before fun allowNotifications() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    /** Said again about the days it kept, it asks about Monday as yesterday's and opens Monday, whatever the setting says now. */
    @Test fun aChecklistsNotificationOpensTheRecordItAskedAbout() {
        val key = slotKey(lastNight.id, "cpt_morning")
        ChecklistReminderNotification(context).show(lastNight, lastNight.checkpoints.single(), lastNight.items, key, ReminderDays(tuesday, monday), quietly = true)

        val posted = requireNotNull(notifications().getNotification(key, ChecklistReminderRequestCode))
        assertEquals(context.getString(R.string.checklist_reminder_open_yesterday, "Practiced violin"), posted.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())
        assertEquals(
            AppNavigationRequest(AppDestination.Checklist, checklistId = lastNight.id, checklistDateKey = monday.toString()),
            shadowOf(posted.contentIntent).savedIntent.requestedAppNavigation(),
        )
        assertEquals(setOf(key), AndroidChecklistReminderGateway(context).shown())
        // Swiped away, it is no longer on show.
        context.getSystemService(NotificationManager::class.java).cancel(key, ChecklistReminderRequestCode)
        assertEquals(emptySet<String>(), AndroidChecklistReminderGateway(context).shown())
    }

    /** A measurement's notification opens the measurement on the day it asked about, also once that day is an earlier one. */
    @Test fun aMeasurementsNotificationOpensTheDayItAskedAbout() {
        val waist = testMadeMeasurement()
        val key = slotKey(ReminderViewKind.Measurement, waist.id, "day")
        ChecklistReminderNotification(context).show(waist, key, ReminderDays(monday, monday), quietly = true)

        val posted = requireNotNull(notifications().getNotification(key, ChecklistReminderRequestCode))
        assertEquals("Waist", posted.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString())
        assertEquals(context.getString(R.string.measurement_reminder_open), posted.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())
        assertEquals(
            AppNavigationRequest(AppDestination.Measurement, loggedViewId = waist.id, loggedDateKey = monday.toString()),
            shadowOf(posted.contentIntent).savedIntent.requestedAppNavigation(),
        )
        assertEquals(setOf(key), AndroidChecklistReminderGateway(context).shown())
    }

    /** A questions view's notification says which day it asks about and opens the view on that day, also once it is an earlier one. */
    @Test fun aQuestionsViewsNotificationSaysTheDayItAsksAboutAndOpensIt() {
        val sleep = testMadeQuestions()
        val key = slotKey(ReminderViewKind.Questions, sleep.id, "day")
        ChecklistReminderNotification(context).show(sleep, key, ReminderDays(tuesday, monday), quietly = true)

        val posted = requireNotNull(notifications().getNotification(key, ChecklistReminderRequestCode))
        assertEquals("Sleep", posted.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString())
        assertEquals("Not answered yet for yesterday", posted.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())
        assertEquals(
            AppNavigationRequest(AppDestination.Questions, loggedViewId = sleep.id, loggedDateKey = monday.toString()),
            shadowOf(posted.contentIntent).savedIntent.requestedAppNavigation(),
        )
        assertEquals(setOf(key), AndroidChecklistReminderGateway(context).shown())

        ChecklistReminderNotification(context).show(sleep, key, ReminderDays(tuesday, tuesday), quietly = true)
        assertEquals("Not answered yet today", notifications().getNotification(key, ChecklistReminderRequestCode)?.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString())
    }

    /**
     * A time view's notification says the minutes its week still asks for and opens the view.
     * It asks for the view with the day it rang on, which a time view's screen does not open on:
     * its reminder is about the week.
     */
    @Test fun aTimeViewsNotificationSaysTheMinutesToGoAndOpensItsView() {
        val piano = testMadeTime()
        val key = slotKey(ReminderViewKind.Time, piano.id, "day")
        ChecklistReminderNotification(context).show(piano, minutesToGo = 45, key, ReminderDays(monday, monday), quietly = true)

        val posted = requireNotNull(notifications().getNotification(key, ChecklistReminderRequestCode))
        assertEquals("Piano", posted.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString())
        assertEquals("45 minutes to go this week", posted.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())
        assertEquals(
            AppNavigationRequest(AppDestination.Time, loggedViewId = piano.id, loggedDateKey = monday.toString()),
            shadowOf(posted.contentIntent).savedIntent.requestedAppNavigation(),
        )
        assertEquals(setOf(key), AndroidChecklistReminderGateway(context).shown())

        ChecklistReminderNotification(context).show(piano, minutesToGo = 1, key, ReminderDays(tuesday, tuesday), quietly = true)
        assertEquals("1 minute to go this week", notifications().getNotification(key, ChecklistReminderRequestCode)?.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString())
    }

    /** A notification keeps both its days; one written before it kept its due day asks about the day it names. */
    @Test fun theBookkeepingKeepsBothDays() {
        val runtime = PreferencesChecklistReminderRuntime(context)
        runtime.posted("chk_night/cpt_morning", ReminderDays(tuesday, monday))
        assertEquals(mapOf("chk_night/cpt_morning" to ReminderDays(tuesday, monday)), PreferencesChecklistReminderRuntime(context).posted())

        context.getSharedPreferences("checklist_reminder_runtime", Context.MODE_PRIVATE).edit()
            .putString("posted", """{"chk_night/cpt_evening":"2026-10-12"}""").commit()
        assertEquals(mapOf("chk_night/cpt_evening" to ReminderDays(monday, monday)), runtime.posted())
    }

    /**
     * Said again without its view, as an answer that met unreadable records says it: the text
     * says why, and its title, its tap and its reply stay as they were on show. Nothing is said
     * where nothing is on show.
     */
    @Test fun aNotificationKeptUnsavedSaysWhyAndKeepsItsTitleTapAndReply() {
        val waist = testMadeMeasurement()
        val key = slotKey(ReminderViewKind.Measurement, waist.id, "day")
        ChecklistReminderNotification(context).show(waist, key, ReminderDays(monday, monday), quietly = false)
        val before = requireNotNull(notifications().getNotification(key, ChecklistReminderRequestCode))

        assertTrue(AndroidChecklistReminderGateway(context).keepUnsaved(key, uiText(R.string.measurement_error_save_failed)))

        val after = requireNotNull(notifications().getNotification(key, ChecklistReminderRequestCode))
        assertEquals(context.getString(R.string.measurement_error_save_failed), after.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())
        assertEquals("Waist", after.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString())
        assertEquals(before.contentIntent, after.contentIntent)
        assertEquals(before.actions.single().actionIntent, after.actions.single().actionIntent)
        assertEquals(ReplyResultKey, after.actions.single().remoteInputs.single().resultKey)
        assertFalse(AndroidChecklistReminderGateway(context).keepUnsaved(slotKey(ReminderViewKind.Measurement, "msr_none", "day"), uiText(R.string.measurement_error_save_failed)))
    }

    private fun notifications() = shadowOf(context.getSystemService(NotificationManager::class.java))
}
