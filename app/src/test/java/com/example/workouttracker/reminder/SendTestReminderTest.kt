package com.example.workouttracker.reminder

import com.example.workouttracker.core.text.resolve
import com.example.workouttracker.domain.compatibility.ReadingTimeId
import com.example.workouttracker.domain.time.TimeLog
import com.example.workouttracker.domain.measurement.testMadeMeasurement
import com.example.workouttracker.domain.questions.testMadeQuestions
import com.example.workouttracker.domain.measurement.testWeightMeasurement
import com.example.workouttracker.domain.measurement.withWeight
import com.example.workouttracker.domain.time.testReadingTime
import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.workouttracker.R
import com.example.workouttracker.app.AppDestination
import com.example.workouttracker.app.AppNavigationRequest
import com.example.workouttracker.app.requestedAppNavigation
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.checklist.ChecklistConfiguration
import com.example.workouttracker.domain.checklist.ChecklistItem
import com.example.workouttracker.domain.checklist.Checkpoint
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.domain.mo.MoDayMark
import com.example.workouttracker.domain.mo.MoMood
import com.example.workouttracker.domain.trackerRef
import com.example.workouttracker.domain.compatibility.MoNeed
import com.example.workouttracker.domain.mo.MoState
import com.example.workouttracker.domain.mo.MoStreak
import com.example.workouttracker.domain.time.DefaultWeeklyGoalMinutes
import com.example.workouttracker.persistence.PreferencesTrackerRepository
import com.example.workouttracker.persistence.TrackerRepository
import com.example.workouttracker.domain.reminder.ReminderSettings
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
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
import com.example.workouttracker.domain.time.testMadeTime

/**
 * Sending a test posts the real notification and touches nothing else: no alarm is set and the
 * coordinators' bookkeeping stays as it was, so the real reminder still comes at its time.
 */
@RunWith(RobolectricTestRunner::class)
class SendTestReminderTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val today = LocalDate.parse("2026-10-14")
    private val plants = ChecklistConfiguration(
        id = "chk_plants",
        label = "Plants",
        icon = ViewIcon.Checklist,
        hue = ViewHue.Sky,
        checkpoints = listOf(Checkpoint("cpt_morning", CheckpointTime.Fixed(LocalTime.of(8, 0)), remind = false)),
        items = listOf(ChecklistItem("itm_water", "Water the plants")),
    )

    @Before fun allowNotifications() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    /** Settings said Home can open the checklist today, so a tap opens it on the day it asks about. */
    @Test fun aChecklistsTestIsItsReminderAndOpensItWhereSettingsSaysItCan() {
        val test = TestReminder.Checklist(plants, plants.checkpoints.single(), plants.items, today).opening(true)
        assertTrue(context.sendTestReminder(test))

        val posted = requireNotNull(notifications().getNotification(slotKey("chk_plants", "cpt_morning"), ChecklistReminderRequestCode))
        assertEquals("Plants", posted.title())
        assertEquals(context.getString(R.string.checklist_reminder_open, "Water the plants"), posted.text())
        assertEquals(
            AppNavigationRequest(AppDestination.Checklist, checklistId = "chk_plants", checklistDateKey = today.toString()),
            shadowOf(posted.contentIntent).savedIntent.requestedAppNavigation(),
        )
        assertNothingElseTouched()
    }

    /** Nothing said the view opens, as in the setup or with only a draft's item in use: a tap only opens the app. */
    @Test fun aTestOfAViewHomeCannotOpenOpensTheAppWhereItWas() {
        assertTrue(context.sendTestReminder(TestReminder.Checklist(plants, plants.checkpoints.single(), plants.items, today)))

        val checklist = requireNotNull(notifications().getNotification(slotKey("chk_plants", "cpt_morning"), ChecklistReminderRequestCode))
        assertEquals("Plants", checklist.title())
        assertNull(shadowOf(checklist.contentIntent).savedIntent.requestedAppNavigation())
        assertNothingElseTouched()
    }

    /**
     * A measurement's test is its reminder, posted under its slot's own tag as the real one is, with
     * the reminder switched off too. Sending it sets no alarm and writes no bookkeeping.
     */
    @Test fun aMeasurementsTestIsItsReminderUnderItsOwnSlot() {
        val waist = testMadeMeasurement()
        assertEquals(false, waist.checkpoint?.remind)
        assertTrue(context.sendTestReminder(TestReminder.Logged(waist, ReminderDays(today, today))))

        val posted = requireNotNull(notifications().getNotification("measurement/msr_k2p9/day", ChecklistReminderRequestCode))
        assertEquals("Waist", posted.title())
        assertEquals(context.getString(R.string.measurement_reminder_open), posted.text())
        assertEquals(setOf("measurement/msr_k2p9/day"), AndroidChecklistReminderGateway(context).shown())
        // Nothing said it opens, as in the setup: a tap opens the app where it was.
        assertNull(shadowOf(posted.contentIntent).savedIntent.requestedAppNavigation())

        // Where Settings said Home can open it, a tap opens it on the day it asks about.
        assertTrue(context.sendTestReminder(TestReminder.Logged(waist, ReminderDays(today, today)).opening(true)))
        assertEquals(
            AppNavigationRequest(AppDestination.Measurement, loggedViewId = "msr_k2p9", loggedDateKey = today.toString()),
            shadowOf(requireNotNull(notifications().getNotification("measurement/msr_k2p9/day", ChecklistReminderRequestCode)).contentIntent).savedIntent.requestedAppNavigation(),
        )
        assertNothingElseTouched()
    }

    /**
     * A questions view's test is its reminder, under its slot's own tag, about the day today asks
     * about; where Settings said Home can open it, a tap opens it on that day.
     */
    @Test fun aQuestionsViewsTestIsItsReminderUnderItsOwnSlot() {
        val sleep = testMadeQuestions()
        assertTrue(context.sendTestReminder(TestReminder.Logged(sleep, ReminderDays(today, today.minusDays(1)))))

        val posted = requireNotNull(notifications().getNotification("questions/qst_k2p9/day", ChecklistReminderRequestCode))
        assertEquals("Sleep", posted.title())
        assertEquals(context.getString(R.string.questions_reminder_open_yesterday), posted.text())
        assertNull(shadowOf(posted.contentIntent).savedIntent.requestedAppNavigation())

        assertTrue(context.sendTestReminder(TestReminder.Logged(sleep, ReminderDays(today, today.minusDays(1))).opening(true)))
        assertEquals(
            AppNavigationRequest(AppDestination.Questions, loggedViewId = "qst_k2p9", loggedDateKey = today.minusDays(1).toString()),
            shadowOf(requireNotNull(notifications().getNotification("questions/qst_k2p9/day", ChecklistReminderRequestCode)).contentIntent).savedIntent.requestedAppNavigation(),
        )
        assertNothingElseTouched()
    }

    /**
     * A time view's test is its reminder, under its slot's own tag, naming the minutes the test
     * carries; where Settings said Home can open it, a tap opens it.
     */
    @Test fun aTimeViewsTestIsItsReminderUnderItsOwnSlot() {
        val piano = testMadeTime()
        assertTrue(context.sendTestReminder(TestReminder.Logged(piano, ReminderDays(today, today), minutesToGo = 60)))

        val posted = requireNotNull(notifications().getNotification("time/tim_k2p9/day", ChecklistReminderRequestCode))
        assertEquals("Piano", posted.title())
        assertEquals("60 minutes to go this week", posted.text())
        assertNull(shadowOf(posted.contentIntent).savedIntent.requestedAppNavigation())

        assertTrue(context.sendTestReminder(TestReminder.Logged(piano, ReminderDays(today, today), minutesToGo = 60).opening(true)))
        assertEquals(
            AppNavigationRequest(AppDestination.Time, loggedViewId = "tim_k2p9", loggedDateKey = today.toString()),
            shadowOf(requireNotNull(notifications().getNotification("time/tim_k2p9/day", ChecklistReminderRequestCode)).contentIntent).savedIntent.requestedAppNavigation(),
        )
        assertNothingElseTouched()
    }

    /** The check-in's test carries "None today" for the day and opens Substances on it; where Home cannot open it, the app. */
    @Test fun substancesTestCarriesNoneTodayAndOpensTheDayItAsksAbout() {
        assertTrue(context.sendTestReminder(TestReminder.Substances(today.toString())))

        val posted = requireNotNull(notifications().getNotification(SubstanceCheckInNotification.NotificationId))
        assertEquals(context.getString(R.string.feature_substances), posted.title())
        assertEquals(context.getString(R.string.substance_check_in_question), posted.text())
        assertEquals(context.getString(R.string.substance_check_in_none_today), posted.actions.single().title.toString())
        assertEquals(
            AppNavigationRequest(AppDestination.Substances, substanceDateKey = today.toString()),
            shadowOf(posted.contentIntent).savedIntent.requestedAppNavigation(),
        )

        assertTrue(context.sendTestReminder(TestReminder.Substances(today.toString(), opens = false)))
        val closed = requireNotNull(notifications().getNotification(SubstanceCheckInNotification.NotificationId))
        assertNull(shadowOf(closed.contentIntent).savedIntent.requestedAppNavigation())
        assertNothingElseTouched()
    }

    /** Weight's test is its reminder under its slot's key, in Mo's voice, carrying "Add weight", and sets no alarm. */
    @Test fun weightsTestCarriesItsReplyAndSetsNoAlarm() {
        val weight = testWeightMeasurement()
        assertTrue(context.sendTestReminder(requireNotNull(loggedTestReminder(weight, today, HolidaySchedule()))))

        val posted = requireNotNull(notifications().getNotification("measurement/weight/day", ChecklistReminderRequestCode))
        assertEquals(weight.label, posted.title())
        assertEquals(context.getString(R.string.reminder_action_log), posted.actions.single().title.toString())
        assertNothingElseTouched()
    }

    /** A timer test still previews its whole goal when recorded minutes have already reached it. */
    @Test fun aTimerTestPreviewsItsWholeGoalOnceTheWeekIsComplete() {
        PreferencesTrackerRepository(context).save(AppState(timeLogs = listOf(TimeLog(ReadingTimeId, "2026-10-12", 1L, minutes = DefaultWeeklyGoalMinutes))).withWeight())

        assertTrue(context.sendTestReminder(requireNotNull(loggedTestReminder(testReadingTime(), today, HolidaySchedule()))))

        val posted = requireNotNull(notifications().getNotification("time/reading/day", ChecklistReminderRequestCode))
        assertEquals(testReadingTime().label, posted.title())
        assertEquals(
            context.resources.getQuantityString(R.plurals.time_reminder_week_remaining, DefaultWeeklyGoalMinutes, DefaultWeeklyGoalMinutes),
            posted.text(),
        )
        assertEquals(context.getString(R.string.time_start), posted.actions.single().title.toString())
        assertNothingElseTouched()
    }

    /**
     * A test leaves no alarm and no bookkeeping, so the reconcile after a hide, a delete or a
     * checkpoint taken away takes it down by what is on show: its view asks nothing any more.
     * Weight's, with its reminder off, goes once Weight is hidden; a made measurement's once it
     * is deleted; a checklist's once its checkpoint is gone. While its view is shown, a test
     * stays, its reminder on or off.
     */
    @Test fun aTestGoesOnceItsViewIsHiddenDeletedOrWithoutItsCheckpoint() {
        val weight = testWeightMeasurement()
        val waist = testMadeMeasurement()
        val days = ReminderDays(today, today)
        listOf(
            TestReminder.Logged(weight, days),
            TestReminder.Logged(waist, days),
            TestReminder.Checklist(plants, plants.checkpoints.single(), plants.items, today),
        ).forEach { assertTrue(context.sendTestReminder(it)) }
        var state = AppState(measurements = listOf(weight, waist), checklists = listOf(plants))
        var visibility = FeatureVisibilitySettings()
        val notification = ChecklistReminderNotification(context)
        val coordinator = ChecklistReminderCoordinator(
            trackerRepository = object : TrackerRepository {
                override fun load() = state
                override fun save(state: AppState) = error("Nothing is written here.")
                override fun update(transform: (AppState) -> AppState) = error("Nothing is written here.")
            },
            featureVisibility = FeatureVisibilitySource { visibility },
            gateway = AndroidChecklistReminderGateway(context),
            runtime = PreferencesChecklistReminderRuntime(context),
            notifier = notification,
            loggedNotifier = notification,
            reminderSettings = { ReminderSettings() },
            todayKey = { today.toString() },
        )
        fun shown() = AndroidChecklistReminderGateway(context).shown()
        val weightKey = slotKey(ReminderViewKind.Measurement, weight.id, "day")
        val waistKey = slotKey(ReminderViewKind.Measurement, waist.id, "day")
        val plantsKey = slotKey(plants.id, "cpt_morning")

        coordinator.reconcile()
        assertEquals(setOf(weightKey, waistKey, plantsKey), shown())

        visibility = FeatureVisibilitySettings(hiddenMeasurements = setOf(weight.id))
        coordinator.reconcile()
        assertEquals(setOf(waistKey, plantsKey), shown())

        state = state.copy(measurements = listOf(weight))
        coordinator.reconcile()
        assertEquals(setOf(plantsKey), shown())

        state = state.copy(checklists = listOf(plants.copy(checkpoints = listOf(Checkpoint("cpt_evening", CheckpointTime.Fixed(LocalTime.of(20, 0)), remind = false)))))
        coordinator.reconcile()
        assertEquals(emptySet<String>(), shown())
    }

    /**
     * Resuming the app, and every save, takes the check-in down when nothing is really overdue,
     * which is the moment a test arrives after the permission dialog. The test is its own
     * notification, so that cannot reach it.
     */
    @Test fun mosTestSurvivesTheCheckInBeingTakenDown() {
        val mood = mood(missingToday = listOf(MoNeed.Reading))
        assertTrue(context.sendTestReminder(TestReminder.MoCheckIn(mood, listOf(MoNeed.Reading.view.trackerRef))))
        val posted = requireNotNull(notifications().getNotification(MoCheckInNotification.MoCheckInTestNotificationId))
        // Which fuming line he picks turns with the clock; any of them is him fuming.
        val fuming = listOf(R.string.reminder_fuming_title_1, R.string.reminder_fuming_title_2, R.string.reminder_fuming_title_3, R.string.reminder_fuming_title_4)
        assertTrue(posted.title() in fuming.map(context::getString))

        context.moCheckInCoordinator().retireIfSettled()
        MoCheckInNotification(context).cancel()

        assertEquals(posted, notifications().getNotification(MoCheckInNotification.MoCheckInTestNotificationId))
        assertNull(notifications().getNotification(MoCheckInNotification.MoCheckInNotificationId))
        assertNothingElseTouched()
    }

    private fun mood(missingToday: List<MoNeed>) = MoMood(
        state = MoState.Steady,
        missingTrackersToday = missingToday.map { it.view.trackerRef },
        trackerLabels = MoNeed.entries.associate { it.view.trackerRef to it.name },
        dueToday = missingToday.size,
        daysCold = 0,
        streak = MoStreak(
            days = 0,
            freezes = 0,
            freezeCapacity = 3,
            week = listOf(MoDayMark.Open) + List(6) { MoDayMark.Upcoming },
            weekCanEarnFreeze = true,
        ),
    )

    /**
     * Each kind's reminder offers what its kind does: a checklist's "Mark all done", a measurement's
     * reply "Log it" asking in its field's words, a time view's start of its session, which opens
     * it, and a questions view nothing.
     */
    @Test fun everyKindsReminderOffersWhatItsKindDoes() {
        val waist = testMadeMeasurement()
        val meditation = testMadeTime()
        val mood = testMadeQuestions()
        listOf(
            TestReminder.Checklist(plants, plants.checkpoints.single(), plants.items, today),
            TestReminder.Logged(waist, ReminderDays(today, today)),
            TestReminder.Logged(meditation, ReminderDays(today, today), minutesToGo = 30),
            TestReminder.Logged(mood, ReminderDays(today, today)),
        ).forEach { assertTrue(context.sendTestReminder(it)) }
        fun posted(key: String) = requireNotNull(notifications().getNotification(key, ChecklistReminderRequestCode))

        val markAllDone = posted(slotKey("chk_plants", "cpt_morning")).actions.single()
        assertEquals(context.getString(R.string.checklist_mark_all_done), markAllDone.title)
        assertEquals(ActionMarkAllDone, shadowOf(markAllDone.actionIntent).savedIntent.action)

        val reply = posted("measurement/msr_k2p9/day").actions.single()
        assertEquals(context.getString(R.string.reminder_action_log), reply.title)
        assertEquals(ReplyResultKey, reply.remoteInputs.single().resultKey)
        // The field's words, its unit set apart from the sentence around it.
        assertTrue(reply.remoteInputs.single().label.toString().startsWith("Waist in "))
        assertEquals(ActionReplyMeasurement, shadowOf(reply.actionIntent).savedIntent.action)

        val start = posted("time/${meditation.id}/day").actions.single()
        assertEquals(context.getString(R.string.time_start), start.title)
        assertEquals(
            AppNavigationRequest(AppDestination.Time, loggedViewId = meditation.id, startSession = true),
            shadowOf(start.actionIntent).savedIntent.requestedAppNavigation(),
        )

        assertNull(posted("questions/${mood.id}/day").actions)
        assertNothingElseTouched()
    }

    private fun assertNothingElseTouched() {
        assertTrue(shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.isEmpty())
        val runtime = PreferencesChecklistReminderRuntime(context)
        assertEquals(emptySet<String>(), runtime.scheduled())
        assertEquals(emptyMap<String, String>(), runtime.posted())
    }

    private fun notifications() = shadowOf(context.getSystemService(NotificationManager::class.java))

    private fun Notification.title() = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()

    private fun Notification.text() = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
}
