package com.example.workouttracker.reminder

import com.example.workouttracker.domain.SubstancesTrackerRef
import com.example.workouttracker.core.time.currentDateKey
import com.example.workouttracker.core.time.nowMillis
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.reminder.ReminderSettings
import com.example.workouttracker.domain.reminder.ReminderTime
import com.example.workouttracker.domain.settings.DashboardFeature
import com.example.workouttracker.domain.substance.canSayNoSubstanceOn
import com.example.workouttracker.domain.substance.isSubstancesLoggedFor
import com.example.workouttracker.domain.substance.saveSubstanceNoneDay
import com.example.workouttracker.domain.substance.substanceAskedDay
import com.example.workouttracker.persistence.TrackerRepository
import com.example.workouttracker.persistence.TrackerUpdateResult
import java.time.LocalDate

internal interface SubstanceCheckInGateway {
    fun canScheduleExact(): Boolean

    /**
     * Sets the check-in's alarm for its next time on or after the habit day [earliestDate],
     * replacing any earlier one. A time relative to waking follows the phone's alarm, or
     * [fallbackAlarmTime]; a time before sleep counts back from [fixedBedtime] when one is set.
     */
    fun schedule(at: CheckpointTime, earliestDate: LocalDate?, fallbackAlarmTime: ReminderTime, fixedBedtime: ReminderTime?): Boolean

    /** Cancels the alarm and takes any check-in on show down. */
    fun cancel()
}

internal fun interface SubstanceCheckInNotifier {
    /** Asks about [dateKey], the day before the one the check-in rang on, which its action records "none" for. */
    fun show(dateKey: String): Boolean
}

/**
 * Substances' check-in: one reminder a day asking whether there is anything to log, at a time in
 * the forms a checkpoint has, stored with the Substances settings. It reads only whether the day
 * is logged, never what was logged, so it stays quiet on a day with a use as on a day that said
 * "none", and it names no substance.
 */
internal class SubstanceCheckInCoordinator(
    private val trackerRepository: TrackerRepository,
    private val featureVisibility: FeatureVisibilitySource,
    private val gateway: SubstanceCheckInGateway,
    private val notifier: SubstanceCheckInNotifier,
    private val reminderSettings: () -> ReminderSettings,
    private val todayKey: () -> String = ::currentDateKey,
    private val quietedByChoice: QuietedByChoice = QuietedByChoice { _, _ -> false },
) {
    /** Sets the next alarm while the check-in is on and Substances is shown, and cancels it otherwise. */
    fun reconcile(earliestDate: LocalDate? = null): Boolean {
        val checkIn = trackerRepository.load().substances.checkIn
        if (!checkIn.remind || !featureVisibility.isVisible(DashboardFeature.Substances)) {
            gateway.cancel()
            return true
        }
        val settings = reminderSettings()
        return gateway.canScheduleExact() && gateway.schedule(checkIn.at, earliestDate, settings.fallbackAlarmTime, settings.fixedBedtime)
    }

    /** The alarm rang: tomorrow's is set, and it asks only while yesterday is not logged yet and no token is set aside for it. */
    fun onReminderDue(): Boolean {
        val today = LocalDate.parse(todayKey())
        val rescheduled = reconcile(earliestDate = today.plusDays(1))
        val state = trackerRepository.load()
        if (!state.substances.checkIn.remind || !featureVisibility.isVisible(DashboardFeature.Substances)) return rescheduled
        val asked = substanceAskedDay(today).toString()
        if (state.isSubstancesLoggedFor(asked) || quietedByChoice.isQuiet(SubstancesTrackerRef, null)) return rescheduled
        return notifier.show(asked) && rescheduled
    }
}

internal sealed interface SubstanceNoneTodayResult {
    data object Saved : SubstanceNoneTodayResult

    /** The day already holds a use, which "none" would overwrite; the day is opened instead. */
    data object HoldsUse : SubstanceNoneTodayResult

    data object SaveFailed : SubstanceNoneTodayResult

    /** The day is not over yet, which an action posted before the check-in asked about yesterday can carry; nothing is saved. */
    data object NotOver : SubstanceNoneTodayResult
}

/** The check-in's action: records "none" for the day it asked about, once that day is over and unless a use was logged meanwhile. */
internal class SubstanceNoneTodayCoordinator(
    private val trackerRepository: TrackerRepository,
    private val todayKey: () -> String = ::currentDateKey,
    private val timestampMillis: () -> Long = ::nowMillis,
) {
    fun record(dateKey: String): SubstanceNoneTodayResult {
        if (!canSayNoSubstanceOn(LocalDate.parse(dateKey), LocalDate.parse(todayKey()))) return SubstanceNoneTodayResult.NotOver
        if (trackerRepository.load().substanceLogs.any { it.dateKey == dateKey }) return SubstanceNoneTodayResult.HoldsUse
        return when (trackerRepository.update { state -> saveSubstanceNoneDay(state, dateKey, timestampMillis()) }) {
            is TrackerUpdateResult.Updated -> SubstanceNoneTodayResult.Saved
            TrackerUpdateResult.SaveFailed,
            TrackerUpdateResult.UnreadableState,
            -> SubstanceNoneTodayResult.SaveFailed
        }
    }
}

/** A save logged [dateKey] where nothing was logged before, with a use or with "none": its check-in is answered. */
internal fun substancesWereLoggedForDate(before: AppState, after: AppState, dateKey: String): Boolean =
    !before.isSubstancesLoggedFor(dateKey) && after.isSubstancesLoggedFor(dateKey)
