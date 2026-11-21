package com.example.workouttracker.domain.mo

import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.TrackerRef
import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.domain.reward.TrackerReward
import com.example.workouttracker.domain.reward.TrackerRewardCatalogue
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import java.time.LocalDate

/** How a day went for one task that was due on it. */
internal enum class MoStatus { Kept, Free, Missed }

/** All enrolled tracker kinds contribute through the same factual statuses. */
internal class MoDay(private val statuses: Map<TrackerRef, MoStatus>) {
    val keptTrackers: List<TrackerRef> get() = trackers(MoStatus.Kept)
    val freeTrackers: List<TrackerRef> get() = trackers(MoStatus.Free)
    val missedTrackers: List<TrackerRef> get() = trackers(MoStatus.Missed)
    val dueCount: Int get() = statuses.size
    val keptShare: Double? get() = shareOf(keptTrackers.size)
    val heldShare: Double? get() = shareOf(dueCount - missedTrackers.size)
    val isPerfect: Boolean get() = dueCount > 0 && missedTrackers.isEmpty()
    val isComplete: Boolean get() = dueCount > 0 && keptTrackers.size == dueCount
    private fun trackers(status: MoStatus): List<TrackerRef> = statuses.filterValues { it == status }.keys.toList()
    private fun shareOf(count: Int): Double? = if (dueCount == 0) null else count.toDouble() / dueCount
}

/**
 * One evaluation's observed history, shared by heat, neglect, calendars and rewards. The
 * observation supplies its facts and policy; this ledger never knows a preset or record shape.
 */
internal class MoLedger private constructor(
    val catalogue: TrackerRewardCatalogue,
    private val tasks: Map<TrackerRef, TaskHistory>,
    val today: LocalDate,
    val rewardStart: LocalDate,
    val featureVisibility: FeatureVisibilitySettings,
) {
    val observations: Map<TrackerRef, TrackerObservation> = catalogue.observations.associateBy { it.ref }
    val trackers: Set<TrackerRef> = tasks.keys.filterTo(LinkedHashSet()) { observations.getValue(it).visibleOn(today) }
    val firstDay: LocalDate? = tasks.values.mapNotNull { it.statuses.keys.firstOrNull() }.minOrNull()

    fun on(day: LocalDate): MoDay = MoDay(buildMap {
        tasks.forEach { (ref, task) -> task.statuses[day]?.let { put(ref, it) } }
    })

    fun history(ref: TrackerRef): Map<LocalDate, MoStatus> = tasks[ref]?.statuses.orEmpty()

    fun latestTrackerOn(day: LocalDate): TrackerRef? = tasks
        .filter { (ref, task) -> day in task.recordedAt && observations.getValue(ref).visibleOn(day) }
        .maxByOrNull { it.value.recordedAt.getValue(day) }?.key

    fun hasPartialActionOn(day: LocalDate): Boolean = trackers.any { ref ->
        observations.getValue(ref).let { it.partialActionRelief && day in it.recordedAt }
    }

    fun finishedDays(start: LocalDate): Sequence<LocalDate> =
        generateSequence(start) { it.plusDays(1) }.takeWhile { it < today }

    private class TaskHistory(val statuses: Map<LocalDate, MoStatus>, val recordedAt: Map<LocalDate, Long>)

    companion object {
        fun of(state: AppState, featureVisibility: FeatureVisibilitySettings, today: LocalDate, holidays: HolidaySchedule = HolidaySchedule(), rewardStart: LocalDate = DefaultRewardStart): MoLedger =
            of(MoRecordIndex(state, holidays), featureVisibility, today, holidays, rewardStart)

        fun of(records: MoRecordIndex, featureVisibility: FeatureVisibilitySettings, today: LocalDate, holidays: HolidaySchedule, rewardStart: LocalDate): MoLedger =
            fromCatalogue(trackerRewards(records, featureVisibility, holidays, today, rewardStart), featureVisibility, today, holidays, rewardStart)

        /** The history of every view Mo watches ([watchedBy]), whichever kind it is. */
        fun fromCatalogue(catalogue: TrackerRewardCatalogue, featureVisibility: FeatureVisibilitySettings, today: LocalDate, holidays: HolidaySchedule, rewardStart: LocalDate): MoLedger {
            // Before the reward rules began, the older walk reads this ledger for the streak itself,
            // and it knew only the views the stored flag enrolled.
            val banks = if (today < rewardStart) emptyMap() else catalogue.entries.associateBy { it.ref }
            return MoLedger(
                catalogue,
                catalogue.observations.mapNotNull { observation ->
                    observation.watchedBy(banks[observation.ref], today)?.let { watched ->
                        observation.ref to TaskHistory(watched.statuses(today, holidays), observation.recordedAt)
                    }
                }.toMap(),
                today, rewardStart, featureVisibility,
            )
        }
    }
}

/**
 * The observation as Mo watches it, or null for a view he does not watch. The one rule: Mo watches
 * every view that counts toward the streak, on the days the reward walk judges it, so he asks for,
 * grows cross about and warms by exactly what the streak depends on. A view counts while its
 * [bank] is on show today; its first day is the bank's, which may come before its first record.
 *
 * The stored flag ([TrackerObservation.participatesInMood]) stays readable: a view with it on is
 * watched as it always was, from its first record and also outside the streak. Nothing sets it
 * any more, and no screen needs to, since the streak's switch says what Mo watches.
 *
 * Only Mo's ledger reads this. Statistics judges every view by its own rule, in or out of the
 * streak, and the reward walk never reads an observation at all. A bank that comes without an
 * observation, as an extension module's may, gives Mo nothing to watch it by.
 */
internal fun TrackerObservation.watchedBy(bank: TrackerReward?, today: LocalDate): TrackerObservation? = when {
    participatesInMood -> this
    bank == null || !bank.bankShownOn(today) -> null
    else -> bank.participant.let { participant ->
        copy(
            firstDay = participant.began,
            countsOn = { day -> countsOn(day) && participant.began != null && participant.began <= day && participant.takesPartOn(day) },
        )
    }
}
