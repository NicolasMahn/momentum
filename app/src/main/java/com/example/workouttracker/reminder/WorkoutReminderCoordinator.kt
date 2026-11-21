package com.example.workouttracker.reminder

import com.example.workouttracker.core.time.currentDateKey
import com.example.workouttracker.domain.activity.activityRules
import com.example.workouttracker.domain.activity.hasActivityOn
import com.example.workouttracker.domain.reminder.WorkoutReminderSettings
import com.example.workouttracker.domain.ActivityTrackerRef
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.settings.DashboardFeature
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import com.example.workouttracker.persistence.ReminderSettingsRepository
import com.example.workouttracker.persistence.TrackerRepository
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

internal interface WorkoutReminderScheduler {
    /** The alarm lands on the next of [days], Activity's week; [earliestLocalDate] is the earliest habit date eligible for a reminder. */
    fun replace(
        settings: WorkoutReminderSettings,
        days: Set<DayOfWeek>,
        earliestLocalDate: LocalDate? = null,
    ): Boolean
}

internal fun interface WorkoutReminderNotifier {
    fun show(subject: MoReminderSubject.Activity): Boolean
}

/** The days the workout reminder comes on: the chosen days of Activity's week as it is now, the seeded week in a state that stores none. */
internal fun AppState.activityDays(): Set<DayOfWeek> = activityRules.week.current.chosen

/**
 * Activity's reminder: on the chosen days of its week, read from the stored configuration's
 * current revision, at its settings' time, unless anything was logged that day already or a
 * person said they rest today. It names
 * the day's planned activity and its routine day next in turn ([activityReminderSubject]), and
 * the alarm for the next day is set before it speaks.
 *
 * Its days are days of the clock, as the alarm lands on them ([wallClockDate]); what was logged is
 * the habit day's ([todayKey]), which before 04:00 is still the day before.
 */
internal class WorkoutReminderCoordinator(
    private val settingsRepository: ReminderSettingsRepository,
    private val trackerRepository: TrackerRepository,
    private val scheduler: WorkoutReminderScheduler,
    private val notifier: WorkoutReminderNotifier,
    private val featureVisibility: FeatureVisibilitySource = FeatureVisibilitySource {
        FeatureVisibilitySettings()
    },
    private val todayKey: () -> String = ::currentDateKey,
    private val wallClockDate: () -> LocalDate = { LocalDate.now(ZoneId.systemDefault()) },
    /** Whether a choice settled Activity today: it rests, or a token covers it, so nothing is asked. */
    private val quietedByChoice: QuietedByChoice = QuietedByChoice { _, _ -> false },
) {
    fun reconcile(): Boolean {
        val settings = settingsRepository.load()
        return scheduler.replace(settings.gatedBy(featureVisibility.load()).workout, trackerRepository.load().activityDays())
    }

    fun onReminderDue(): Boolean {
        val visibility = featureVisibility.load()
        val settings = settingsRepository.load()
            .gatedBy(visibility)
            .workout
        val today = LocalDate.parse(todayKey())
        val state = trackerRepository.load()
        val days = state.activityDays()
        val nextScheduled = scheduler.replace(
            settings = settings,
            days = days,
            earliestLocalDate = today.plusDays(1),
        )
        if (!settings.enabled || !visibility.isVisible(DashboardFeature.Activity)) {
            return nextScheduled
        }

        // The alarm lands only on the week's days, but a day that moved in or out of them since it was set is judged as it is now.
        val clockDay = wallClockDate().dayOfWeek
        if (clockDay !in days) return nextScheduled
        val settled = state.hasActivityOn(today.toString()) || quietedByChoice.isQuiet(ActivityTrackerRef, null)
        val shown = settled || notifier.show(activityReminderSubject(state, clockDay))
        return nextScheduled && shown
    }
}
