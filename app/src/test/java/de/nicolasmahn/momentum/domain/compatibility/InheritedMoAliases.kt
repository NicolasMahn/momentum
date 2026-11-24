package de.nicolasmahn.momentum.domain.compatibility

import de.nicolasmahn.momentum.domain.compatibility.MoNeed
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.LoggedView
import de.nicolasmahn.momentum.domain.TrackerRef
import de.nicolasmahn.momentum.domain.ref
import de.nicolasmahn.momentum.domain.trackerRef
import de.nicolasmahn.momentum.domain.mo.DefaultRewardStart
import de.nicolasmahn.momentum.domain.mo.MoDay
import de.nicolasmahn.momentum.domain.mo.MoEvaluation
import de.nicolasmahn.momentum.domain.mo.MoLedger
import de.nicolasmahn.momentum.domain.mo.MoMood
import de.nicolasmahn.momentum.domain.mo.MoNeglect
import de.nicolasmahn.momentum.domain.mo.MoObligation
import de.nicolasmahn.momentum.domain.mo.MoStatus
import de.nicolasmahn.momentum.domain.mo.MoWeekProgress
import de.nicolasmahn.momentum.domain.mo.TrackerObservation
import de.nicolasmahn.momentum.domain.mo.trackerFreeDays
import de.nicolasmahn.momentum.domain.mo.trackerNeglect
import de.nicolasmahn.momentum.domain.mo.thrivingTrackers
import de.nicolasmahn.momentum.domain.mo.trackerObligations
import de.nicolasmahn.momentum.domain.mo.trackerWeekProgress
import de.nicolasmahn.momentum.domain.holiday.HolidaySchedule
import de.nicolasmahn.momentum.domain.settings.FeatureVisibilitySettings
import de.nicolasmahn.momentum.domain.settings.ViewRef
import de.nicolasmahn.momentum.domain.reminder.ReminderSettings
import java.time.LocalDate

/** The inherited identity of a stored view, independent of its current enrollment in Mo. */
val ViewRef.moNeed: MoNeed? get() = MoNeed.entries.firstOrNull { it.view == this }

/** The inherited identity of this configuration, if it has one. */
val LoggedView.moNeed: MoNeed? get() = ViewRef.Logged(ref).moNeed

/** Finished days protected by the observed task's configured weekly rest allowance. */
fun moFreeDays(
    state: AppState,
    featureVisibility: FeatureVisibilitySettings,
    today: LocalDate,
    holidays: HolidaySchedule = HolidaySchedule(),
    rewardStart: LocalDate = DefaultRewardStart,
): Map<MoNeed, Set<LocalDate>> = MoLedger.of(state, featureVisibility, today, holidays, rewardStart).freeDays()

/** Explicit projections used by inherited histories and stored-choice adapters. */
internal fun <V> Map<TrackerRef, V>.inheritedProjection(): Map<MoNeed, V> =
    entries.mapNotNull { (ref, value) -> InheritedNeedsByTracker[ref]?.let { it to value } }.toMap()

internal fun MoEvaluation.freeDays(): Map<MoNeed, Set<LocalDate>> = trackerFreeDays().inheritedProjection()
internal fun MoEvaluation.mood(chosenPasses: Set<MoNeed>): MoMood = moodWithTrackerChoices(chosenPasses.mapTo(linkedSetOf()) { it.view.trackerRef })
internal fun MoEvaluation.moodWithStoredChoices(stored: Set<MoNeed>): MoMood = moodWithTrackerChoices(stored.mapTo(linkedSetOf()) { it.view.trackerRef })
internal val MoLedger.needs: Set<MoNeed> get() = trackers.mapNotNullTo(linkedSetOf(), InheritedNeedsByTracker::get)
internal fun MoLedger.history(need: MoNeed): Map<LocalDate, MoStatus> = history(need.view.trackerRef)
internal fun MoLedger.observation(need: MoNeed): TrackerObservation? = observations[need.view.trackerRef]
internal fun MoLedger.latestOn(day: LocalDate): MoNeed? = latestTrackerOn(day)?.let(InheritedNeedsByTracker::get)
internal val MoDay.kept: List<MoNeed> get() = keptTrackers.mapNotNull(InheritedNeedsByTracker::get)
internal val MoDay.free: List<MoNeed> get() = freeTrackers.mapNotNull(InheritedNeedsByTracker::get)
internal val MoDay.missed: List<MoNeed> get() = missedTrackers.mapNotNull(InheritedNeedsByTracker::get)
internal fun MoLedger.freeDays(): Map<MoNeed, Set<LocalDate>> = trackerFreeDays().inheritedProjection()
internal fun MoLedger.weekProgress(): Map<MoNeed, MoWeekProgress> = trackerWeekProgress().inheritedProjection()
internal fun MoLedger.neglect(): Map<MoNeed, MoNeglect> = trackerNeglect().inheritedProjection()
internal fun MoLedger.thriving(): Set<MoNeed> = thrivingTrackers().mapNotNullTo(linkedSetOf(), InheritedNeedsByTracker::get)
internal fun MoMood.obligations(reminders: ReminderSettings): Map<MoNeed, List<MoObligation>> = trackerObligations(reminders).inheritedProjection()
