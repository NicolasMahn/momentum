package de.nicolasmahn.momentum.reminder

import de.nicolasmahn.momentum.core.text.UiText
import de.nicolasmahn.momentum.domain.ActivityTrackerRef
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.activity.ActivityEdit
import de.nicolasmahn.momentum.domain.activity.ActivityEntry
import de.nicolasmahn.momentum.domain.activity.GymActivityId
import de.nicolasmahn.momentum.domain.activity.SeededActivityRules
import de.nicolasmahn.momentum.domain.activity.WeekPlan
import de.nicolasmahn.momentum.domain.activity.applyEdit
import de.nicolasmahn.momentum.domain.activity.routine.ExerciseLog
import de.nicolasmahn.momentum.domain.activity.routine.LoggedSet
import de.nicolasmahn.momentum.domain.activity.routine.WorkoutSession
import de.nicolasmahn.momentum.domain.activity.withActivity
import de.nicolasmahn.momentum.domain.reminder.ReminderSettings
import de.nicolasmahn.momentum.domain.reminder.WorkoutReminderSettings
import de.nicolasmahn.momentum.domain.settings.DashboardFeature
import de.nicolasmahn.momentum.domain.settings.FeatureVisibilitySettings
import de.nicolasmahn.momentum.persistence.ReminderSettingsRepository
import de.nicolasmahn.momentum.persistence.TrackerRepository
import de.nicolasmahn.momentum.persistence.TrackerUpdateResult
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The reminder comes on the chosen days of Activity's week, read from the stored configuration,
 * and names the day's planned activity with its routine day next in turn. Anything logged that day,
 * an entry of any activity or a session with a set, keeps it quiet; the alarm for the next day is
 * set either way, on the week's days. 2026-09-16 is a Wednesday.
 */
class WorkoutReminderCoordinatorTest {
    private val wednesday = "2026-09-16"

    /** A state whose week is [days] alone, chosen long before the days the tests look at. */
    private fun weekOf(vararg days: DayOfWeek) = AppState().withActivity(SeededActivityRules.applyEdit(ActivityEdit.SetWeek(WeekPlan.of(*days)), LocalDate.parse("2026-01-05")))

    /** The seeded week plans the gym on Wednesday; the newest session was day B, so day C is next in turn. */
    @Test
    fun dueReminderNamesThePlannedActivityWithItsNextDayAndSchedulesTomorrow() {
        val scheduler = FakeScheduler()
        val notifier = FakeNotifier()
        val state = AppState(sessions = listOf(workout("2026-09-14", "B", completed = true))).withActivity()

        assertEquals(true, coordinator(state, wednesday, scheduler, notifier).onReminderDue())
        assertEquals(listOf(MoReminderSubject.Activity(GymActivityId, UiText.Verbatim("Gym"), UiText.Verbatim("Day C"), label = "Activity")), notifier.notifications)
        assertEquals(LocalDate.parse("2026-09-17"), scheduler.calls.single().earliestDate)
        assertEquals(SeededActivityRules.week.current.chosen, scheduler.calls.single().days)
    }

    /** Tuesday plans Bouldering, which has no routine, so no day follows; Thursday plans any activity, so nothing is named. */
    @Test
    fun aPlannedActivityWithoutARoutineNamesNoDayAndAnyActivityNamesNothing() {
        val tuesday = FakeNotifier()
        assertEquals(true, coordinator(AppState().withActivity(), "2026-09-15", notifier = tuesday).onReminderDue())
        assertEquals(listOf(MoReminderSubject.Activity("bouldering", UiText.Verbatim("Bouldering"), nextDay = null, label = "Activity")), tuesday.notifications)

        val thursday = FakeNotifier()
        assertEquals(true, coordinator(AppState().withActivity(), "2026-09-17", notifier = thursday).onReminderDue())
        assertEquals(listOf(MoReminderSubject.Activity(activityId = null, planned = null, nextDay = null, label = "Activity")), thursday.notifications)
    }

    /** Reconciling schedules the alarm on the stored week's days, a week other than the default included. */
    @Test
    fun reconcilingSchedulesTheStoredWeeksDays() {
        val scheduler = FakeScheduler()
        assertEquals(true, coordinator(weekOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), wednesday, scheduler).reconcile())
        assertEquals(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), scheduler.calls.single().days)
        assertEquals(null, scheduler.calls.single().earliestDate)
    }

    /** A planned activity Activity no longer offers, removed with the week's revision kept as history, is not named. */
    @Test
    fun aPlannedActivityRemovedSinceNamesNothing() {
        val notifier = FakeNotifier()
        val gymRemoved = AppState().withActivity(SeededActivityRules.copy(activities = SeededActivityRules.activities.map { if (it.id == GymActivityId) it.copy(removed = true) else it }))

        assertEquals(true, coordinator(gymRemoved, wednesday, notifier = notifier).onReminderDue())
        assertEquals(listOf(MoReminderSubject.Activity(activityId = null, planned = null, nextDay = null, label = "Activity")), notifier.notifications)
    }

    /** A rest day said in the app, or a token set aside, stops the asking: nothing is shown, and the next day's alarm is still set. */
    @Test
    fun aDaySettledByChoiceKeepsItQuiet() {
        val notifier = FakeNotifier()
        val scheduler = FakeScheduler()
        val settled = coordinator(AppState().withActivity(), wednesday, scheduler, notifier, quietedByChoice = QuietedByChoice { ref, checkpoint -> ref == ActivityTrackerRef && checkpoint == null })

        assertEquals(true, settled.onReminderDue())
        assertEquals(emptyList<MoReminderSubject.Activity>(), notifier.notifications)
        assertEquals(LocalDate.parse(wednesday).plusDays(1), scheduler.calls.single().earliestDate)
    }

    @Test
    fun anyEntryOrASessionWithASetThatDayKeepsItQuiet() {
        listOf(
            AppState(sessions = listOf(workout(wednesday, "A", completed = true))),
            AppState(activityEntries = listOf(activity("boulder", "Bouldering", wednesday))),
            AppState(activityEntries = listOf(activity("own", "Yoga", wednesday))),
        ).forEach { logged ->
            val notifier = FakeNotifier()
            assertEquals(true, coordinator(logged.withActivity(), wednesday, notifier = notifier).onReminderDue())
            assertEquals(logged.toString(), emptyList<MoReminderSubject.Activity>(), notifier.notifications)
        }
    }

    @Test
    fun anEmptySessionOrAnotherDaysRecordDoesNotKeepItQuiet() {
        listOf(
            AppState(sessions = listOf(workout(wednesday, "A", completed = false))),
            AppState(activityEntries = listOf(activity("yesterday", "Bouldering", "2026-09-15"))),
        ).forEach { unanswered ->
            val notifier = FakeNotifier()
            assertEquals(true, coordinator(unanswered.withActivity(), wednesday, notifier = notifier).onReminderDue())
            assertEquals(unanswered.toString(), 1, notifier.notifications.size)
        }
    }

    @Test
    fun aDayOutsideTheWeeksDaysStaysSilentAndContinuesScheduling() {
        val scheduler = FakeScheduler()
        val notifier = FakeNotifier()
        val weekendOnly = weekOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

        assertEquals(true, coordinator(weekendOnly, wednesday, scheduler, notifier).onReminderDue())
        assertEquals(emptyList<MoReminderSubject.Activity>(), notifier.notifications)
        assertEquals(LocalDate.parse("2026-09-17"), scheduler.calls.single().earliestDate)
        assertEquals(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), scheduler.calls.single().days)

        val saturday = FakeNotifier()
        assertEquals(true, coordinator(weekendOnly, "2026-09-19", notifier = saturday).onReminderDue())
        assertEquals(1, saturday.notifications.size)
    }

    /** With nothing chosen the reminder comes Monday to Friday and is silent at the weekend, in a state without a configuration too. */
    @Test
    fun theSeededDaysAreMondayToFriday() {
        listOf(AppState().withActivity(), AppState()).forEach { state ->
            listOf("2026-09-19", "2026-09-20").forEach { weekend ->
                val notifier = FakeNotifier()
                assertEquals(true, coordinator(state, weekend, notifier = notifier).onReminderDue())
                assertEquals(weekend, emptyList<MoReminderSubject.Activity>(), notifier.notifications)
            }
            val weekday = FakeNotifier()
            assertEquals(true, coordinator(state, wednesday, notifier = weekday).onReminderDue())
            assertEquals(1, weekday.notifications.size)
        }
    }

    /**
     * At 02:00 on a Tuesday the clock says Tuesday and the habit day is still Monday. The alarm
     * landed on Tuesday, so Tuesday's days decide whether it speaks; what keeps it quiet is a
     * record of the habit day, Monday's, and the alarm for the next day is set from Monday.
     */
    @Test
    fun beforeFourTheClocksDayIsJudgedAndTheHabitDaysRecordsAreRead() {
        val mondayKey = "2026-09-14"
        val tuesday = LocalDate.parse("2026-09-15")

        val tuesdays = FakeNotifier()
        val scheduler = FakeScheduler()
        assertEquals(true, coordinator(weekOf(DayOfWeek.TUESDAY), mondayKey, scheduler, tuesdays, wallClockDate = tuesday).onReminderDue())
        assertEquals(1, tuesdays.notifications.size)
        assertEquals(tuesday, scheduler.calls.single().earliestDate)

        val mondays = FakeNotifier()
        assertEquals(true, coordinator(weekOf(DayOfWeek.MONDAY), mondayKey, notifier = mondays, wallClockDate = tuesday).onReminderDue())
        assertEquals(emptyList<MoReminderSubject.Activity>(), mondays.notifications)

        val mondayLogged = FakeNotifier()
        val loggedOnMonday = weekOf(DayOfWeek.TUESDAY).copy(activityEntries = listOf(activity("monday", "Bouldering", mondayKey)))
        assertEquals(true, coordinator(loggedOnMonday, mondayKey, notifier = mondayLogged, wallClockDate = tuesday).onReminderDue())
        assertEquals(emptyList<MoReminderSubject.Activity>(), mondayLogged.notifications)

        val tuesdayLogged = FakeNotifier()
        val loggedOnTuesday = weekOf(DayOfWeek.TUESDAY).copy(activityEntries = listOf(activity("tuesday", "Bouldering", tuesday.toString())))
        assertEquals(true, coordinator(loggedOnTuesday, mondayKey, notifier = tuesdayLogged, wallClockDate = tuesday).onReminderDue())
        assertEquals(1, tuesdayLogged.notifications.size)
    }

    @Test
    fun disabledReminderDoesNotNotifyButStillReconcilesTomorrow() {
        val scheduler = FakeScheduler()
        val notifier = FakeNotifier()
        val coordinator = coordinator(
            state = AppState().withActivity(),
            todayKey = wednesday,
            scheduler = scheduler,
            notifier = notifier,
            settings = ReminderSettings(),
        )

        assertEquals(true, coordinator.onReminderDue())
        assertEquals(emptyList<MoReminderSubject.Activity>(), notifier.notifications)
        assertEquals(LocalDate.parse("2026-09-17"), scheduler.calls.single().earliestDate)
    }

    @Test
    fun hiddenActivityCancelsTheEffectiveScheduleWithoutNotifying() {
        val scheduler = FakeScheduler()
        val notifier = FakeNotifier()

        assertEquals(
            true,
            coordinator(
                state = AppState().withActivity(),
                todayKey = wednesday,
                scheduler = scheduler,
                notifier = notifier,
                visibility = FeatureVisibilitySettings(setOf(DashboardFeature.Activity)),
            ).onReminderDue(),
        )

        assertEquals(false, scheduler.calls.single().settings.enabled)
        assertEquals(emptyList<MoReminderSubject.Activity>(), notifier.notifications)
    }

    private fun coordinator(
        state: AppState,
        todayKey: String,
        scheduler: FakeScheduler = FakeScheduler(),
        notifier: FakeNotifier = FakeNotifier(),
        settings: ReminderSettings = ReminderSettings(
            workout = WorkoutReminderSettings(enabled = true),
        ),
        visibility: FeatureVisibilitySettings = FeatureVisibilitySettings(),
        // By day the clock's day is the habit day; only an early hour tells them apart.
        wallClockDate: LocalDate = LocalDate.parse(todayKey),
        quietedByChoice: QuietedByChoice = QuietedByChoice { _, _ -> false },
    ) = WorkoutReminderCoordinator(
        settingsRepository = FakeSettingsRepository(settings),
        trackerRepository = FakeTrackerRepository(state),
        scheduler = scheduler,
        notifier = notifier,
        featureVisibility = FeatureVisibilitySource { visibility },
        todayKey = { todayKey },
        wallClockDate = { wallClockDate },
        quietedByChoice = quietedByChoice,
    )

    private fun workout(
        dateKey: String,
        day: String,
        completed: Boolean,
    ) = WorkoutSession(
        id = "workout-$dateKey-$day",
        activityId = GymActivityId,
        day = day,
        dateKey = dateKey,
        timestampMillis = 1L,
        exerciseLogs = listOf(
            ExerciseLog(
                "bench_press",
                "Bench Press",
                if (completed) listOf(LoggedSet(weightKg = 40.0, reps = 8.0)) else emptyList(),
            ),
        ),
    )

    private fun activity(
        id: String,
        name: String,
        dateKey: String,
    ) = ActivityEntry(
        id = id,
        activityId = name.lowercase(),
        name = name,
        dateKey = dateKey,
        timestampMillis = 2L,
    )

    private class FakeSettingsRepository(
        private var settings: ReminderSettings,
    ) : ReminderSettingsRepository {
        override fun load(): ReminderSettings = settings

        override fun save(settings: ReminderSettings): Boolean {
            this.settings = settings
            return true
        }
    }

    private class FakeTrackerRepository(
        private var state: AppState,
    ) : TrackerRepository {
        override fun load(): AppState = state

        override fun save(state: AppState): Boolean {
            this.state = state
            return true
        }

        override fun update(transform: (AppState) -> AppState): TrackerUpdateResult {
            state = transform(state)
            return TrackerUpdateResult.Updated(state)
        }
    }

    private class FakeScheduler : WorkoutReminderScheduler {
        val calls = mutableListOf<ScheduleCall>()

        override fun replace(
            settings: WorkoutReminderSettings,
            days: Set<DayOfWeek>,
            earliestLocalDate: LocalDate?,
        ): Boolean {
            calls += ScheduleCall(settings, days, earliestLocalDate)
            return true
        }
    }

    private class FakeNotifier : WorkoutReminderNotifier {
        val notifications = mutableListOf<MoReminderSubject.Activity>()

        override fun show(subject: MoReminderSubject.Activity): Boolean {
            notifications += subject
            return true
        }
    }

    private data class ScheduleCall(
        val settings: WorkoutReminderSettings,
        val days: Set<DayOfWeek>,
        val earliestDate: LocalDate?,
    )
}
