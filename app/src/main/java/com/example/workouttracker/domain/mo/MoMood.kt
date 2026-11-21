package com.example.workouttracker.domain.mo

import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.CheckpointPart
import com.example.workouttracker.domain.TrackerRef
import com.example.workouttracker.domain.reward.TrackerReward
import com.example.workouttracker.domain.reward.TrackerRewardCatalogue
import com.example.workouttracker.domain.reward.RewardTarget
import com.example.workouttracker.domain.checklist.Checkpoint
import com.example.workouttracker.domain.ref
import com.example.workouttracker.domain.holiday.HolidaySchedule
import java.time.DayOfWeek
import com.example.workouttracker.domain.DayPart
import com.example.workouttracker.domain.DayProgress
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import com.example.workouttracker.domain.visibilityInUse
import java.time.LocalDate

/** Mo's five states, coldest first so they compare in the order he warms up. See docs/mo.md. */
enum class MoState(internal val minimumHeat: Int) {
    Husk(0),
    Cold(1),
    Dimming(4),
    Steady(6),
    Blazing(8),
    ;

    internal companion object {
        fun of(heat: Int): MoState = entries.last { heat >= it.minimumHeat }
    }
}

/** One evaluation's current tracker facts and game outcomes. Choices never manufacture records. */
data class MoMood(
    val state: MoState,
    val dueToday: Int,
    val daysCold: Int,
    val streak: MoStreak,
    val trackerLabels: Map<TrackerRef, String> = emptyMap(),
    val trackerContent: Map<TrackerRef, MoContentKey> = emptyMap(),
    val missingTrackersToday: List<TrackerRef> = emptyList(),
    val freeTrackersToday: List<TrackerRef> = emptyList(),
    val keptTrackersToday: List<TrackerRef> = emptyList(),
    val latestTrackerToday: TrackerRef? = null,
    val trackerWeekProgress: Map<TrackerRef, MoWeekProgress> = emptyMap(),
    val trackerNeglect: Map<TrackerRef, MoNeglect> = emptyMap(),
    val thrivingTrackers: Set<TrackerRef> = emptySet(),
    val trackerOpenParts: Map<TrackerRef, List<DayPart>> = emptyMap(),
    val trackerMissedParts: Map<TrackerRef, List<DayPart>> = emptyMap(),
    val trackerCheckpoints: Map<TrackerRef, List<Checkpoint>> = emptyMap(),
    /** The tasks the shared workout reminder asks for today. */
    val workoutRemindedTrackers: Set<TrackerRef> = emptySet(),
    val trackerWeeklyRemainders: Map<TrackerRef, Int> = emptyMap(),
    val trackerWeeklyNudges: Map<TrackerRef, Int> = emptyMap(),
    val trackerTokenRequirements: Map<TrackerRef, Int> = emptyMap(),
    val trackerOrder: List<TrackerRef> = emptyList(),
    /** The tasks a token covers by choice today; a week's amount only on its Sunday. */
    val selectedTrackerChoices: Set<TrackerRef> = emptySet(),
    /**
     * The weeks' amounts a person asked Mo not to ask for today, before their Sunday. It holds no
     * token and covers nothing: tomorrow he asks again, and the Sunday close judges the week.
     */
    val quietTrackersToday: Set<TrackerRef> = emptySet(),
    /** The weeks' amounts that can be quieted today: before their Sunday, short, with a token held. */
    val quietOfferedTrackers: Set<TrackerRef> = emptySet(),
    /** The tasks a free day covers that rest today because a person said so. */
    val restChosenTrackers: Set<TrackerRef> = emptySet(),
    /** The checkpoints a token is set aside for today, one token each. */
    val checkpointsSetAside: Map<TrackerRef, Set<String>> = emptyMap(),
    /**
     * The tasks with something a token could cover now: a miss today, an open day of a bank Mo
     * has no observation to watch by, or a week's amount its Sunday will find short.
     */
    val tokenOpenTrackers: Set<TrackerRef> = emptySet(),
    /** The tasks judged by their week's amount: a token covers them on their Sunday, and before it they can be quieted. */
    val weekJudgedTrackers: Set<TrackerRef> = emptySet(),
    val trackerProgress: Map<TrackerRef, DayProgress> = emptyMap(),
    val weeklyGoalTrackers: Set<TrackerRef> = emptySet(),
    /** The amount yesterday's closed earning policy names, independent of today's edited goal. */
    val tokenEarningBasisByTracker: Map<TrackerRef, Int> = emptyMap(),
    /** The banks yesterday's rule filled by kept days across weeks, where a kind can also earn by its week. */
    val tokenEarnedAcrossWeeks: Set<TrackerRef> = emptySet(),
) {
    val chosenTrackersToday: Set<TrackerRef> get() = selectedTrackerChoices
    val neglectByTracker: Map<TrackerRef, MoNeglect> get() = trackerNeglect
    val contentByTracker: Map<TrackerRef, MoContentKey> get() = trackerContent
    val latestTracker: TrackerRef? get() = latestTrackerToday
    val weekProgressByTracker: Map<TrackerRef, MoWeekProgress> get() = trackerWeekProgress
}

/**
 * Derives Mo's mood from existing records only, so he has no store of his own and cannot disagree
 * with the data.
 *
 * Heat runs from 0 to 10. Each finished day moves it by how much of that day's commitments held, a
 * free day holding like a record, so from full heat he survives one missed day and is a Husk after
 * five. Today can only warm him, because it is not over yet, and only by its records: a free day is
 * settled at the 04:00 day boundary. While he is Dimming or colder, its first record lifts him at least one state,
 * so that acting always brings visible relief.
 */
fun moMood(
    state: AppState,
    featureVisibility: FeatureVisibilitySettings,
    today: LocalDate,
    holidays: HolidaySchedule = HolidaySchedule(),
    chosenTrackers: Set<TrackerRef> = emptySet(),
    rewardStart: LocalDate = DefaultRewardStart,
    chosenCheckpoints: Map<TrackerRef, Set<String>> = emptyMap(),
): MoMood = MoEvaluation(state, featureVisibility, today, holidays, rewardStart).moodWith(MoChoices(chosenTrackers, chosenCheckpoints))

/**
 * Mo's mood as the app shows it, from the choices stored for [today] ([MoEvaluation.moodWith]):
 * what the widget, the reminders and the check-in ask, as Home asks its own evaluation, so a
 * stored choice counts on all of them or on none.
 */
fun moMoodWithStoredChoices(
    state: AppState,
    featureVisibility: FeatureVisibilitySettings,
    today: LocalDate,
    holidays: HolidaySchedule = HolidaySchedule(),
    storedChoices: MoChoices = MoChoices(),
    rewardStart: LocalDate = DefaultRewardStart,
): MoMood = MoEvaluation(state, featureVisibility, today, holidays, rewardStart).moodWith(storedChoices)

/**
 * Everything Mo derives from one set of inputs before today's pass choices: the ledger, the streak
 * and the mood they make. A screen that shows both the mood and the calendars' free days builds one
 * and asks it for both, and a changed pass choice only reapplies the choice.
 *
 * [featureVisibility] is the switches as stored: a checklist with no item in use on [today] is
 * hidden here ([visibilityInUse]), so every surface that asks for a mood gets the same one.
 *
 * It is a plain value of its inputs and is never updated, so whoever keeps one must replace it as
 * soon as any of them changes. Home keys it on every constructor argument.
 */
internal class MoEvaluation(
    state: AppState,
    featureVisibility: FeatureVisibilitySettings,
    val today: LocalDate,
    holidays: HolidaySchedule = HolidaySchedule(),
    rewardStart: LocalDate = DefaultRewardStart,
    additionalRewards: List<TrackerReward> = emptyList(),
    additionalObservations: List<TrackerObservation> = emptyList(),
) {
    private val ledger: MoLedger
    val observations: List<TrackerObservation>
    private val unchosen: MoMood

    init {
        val shown = state.visibilityInUse(featureVisibility, today)
        val records = MoRecordIndex(state, holidays)
        val configured = trackerRewards(records, shown, holidays, today, rewardStart)
        val catalogue = TrackerRewardCatalogue(configured.entries + additionalRewards, configured.observations + additionalObservations)
        observations = catalogue.observations.toList()
        ledger = MoLedger.fromCatalogue(catalogue, shown, today, holidays, rewardStart)
        unchosen = moodBeforePasses(ledger, holidays)
    }

    fun trackerFreeDays(): Map<TrackerRef, Set<LocalDate>> = ledger.trackerFreeDays()

    fun todayProgress(): Map<TrackerRef, DayProgress> = unchosen.trackerProgress

    /** The mood with whole tasks chosen; see [moodWith]. */
    fun moodWithTrackerChoices(chosen: Set<TrackerRef>): MoMood = moodWith(MoChoices(chosen))

    /**
     * The mood with [choices] applied, those the facts still bear: a record or a setting saved
     * since can have settled what was chosen, or left the tokens short. A choice reserves
     * protection without creating a record or a completion, and the streak is left as the walk
     * made it.
     *
     * A task rests while a free day covers it and its plan still asks. A token covers a task
     * while its bank covers all it leaves open. A checklist with several checkpoints takes its
     * tokens one checkpoint at a time, in their order while the bank lasts, and a choice of the
     * whole checklist stands for all it still owes. Such a checklist is covered once every
     * checkpoint it owes has a token.
     */
    fun moodWith(choices: MoChoices): MoMood {
        if (choices.isEmpty) return unchosen
        val resting = choices.tasks.filterTo(LinkedHashSet()) { unchosen.canRest(it) }
        val quiet = choices.tasks.filterTo(LinkedHashSet()) { it in unchosen.quietOfferedTrackers }
        val checkpoints = unchosen.missingTrackersToday.filter { unchosen.choosesByCheckpoint(it) }.mapNotNull { ref ->
            val owed = unchosen.trackerStillOwed(ref).filterIsInstance<CheckpointPart>().map { it.checkpointId }
            val budget = unchosen.checkpointTokensLeft(ref)
            val whole = ref in choices.tasks && owed.size <= budget
            val setAside = if (whole) owed else owed.filter { it in choices.checkpoints[ref].orEmpty() }.take(budget)
            Triple(ref, setAside.toSet(), whole || (setAside.isNotEmpty() && setAside.size == owed.size)).takeIf { (_, _, covered) -> covered || setAside.isNotEmpty() }
        }
        val covered = choices.tasks.filterTo(LinkedHashSet()) { !unchosen.choosesByCheckpoint(it) && unchosen.tokensCover(it) } +
            checkpoints.filter { (_, _, whole) -> whole }.map { (ref) -> ref }
        if (resting.isEmpty() && quiet.isEmpty() && checkpoints.isEmpty() && covered.isEmpty()) return unchosen
        // What a choice settles is no longer asked for: it joins the parts Mo does not ask today.
        val unasked = resting.associateWith { unchosen.trackerStillOpen(it) } +
            checkpoints.associate { (ref, setAside) -> ref to unchosen.trackerMissedParts[ref].orEmpty() + setAside.map(::CheckpointPart) }
        return unchosen.copy(
            missingTrackersToday = unchosen.missingTrackersToday - covered,
            selectedTrackerChoices = covered,
            restChosenTrackers = resting,
            quietTrackersToday = quiet,
            checkpointsSetAside = checkpoints.associate { (ref, setAside) -> ref to setAside },
            trackerMissedParts = unchosen.trackerMissedParts + unasked,
            trackerWeeklyNudges = unchosen.trackerWeeklyNudges - covered - quiet,
            trackerProgress = unchosen.trackerProgress.filterNot { (ref, progress) -> ref in covered && !progress.isStarted },
        )
    }

}

private fun moodBeforePasses(ledger: MoLedger, holidays: HolidaySchedule): MoMood {
    val today = ledger.today
    val firstDay = maxOf(today.minusDays(HISTORY_DAYS), ledger.firstDay ?: today)

    var heat = STARTING_HEAT
    var daysCold = 0
    ledger.finishedDays(start = firstDay).forEach { day ->
        heat = (heat + finishedDayDelta(ledger.on(day).heldShare)).coerceIn(0, MAX_HEAT)
        daysCold = if (MoState.of(heat) <= MoState.Cold) daysCold + 1 else 0
    }

    val todaySoFar = ledger.on(today)
    val keptShareToday = todaySoFar.keptShare
    val hasKeptTask = keptShareToday != null && keptShareToday > 0.0
    val hasPartialAction = ledger.hasPartialActionOn(today)
    if (hasKeptTask || hasPartialAction) {
        // A configured partial action brings relief; only complete commitments add completion heat.
        val warmed = heat + if (hasKeptTask) finishedDayDelta(keptShareToday).coerceAtLeast(0) else 0
        // Relief is for rekindling. A Mo who already burns has to earn Blazing with a full day.
        val before = MoState.of(heat)
        val relief = if (before < MoState.Steady) MoState.entries[before.ordinal + 1].minimumHeat else 0
        heat = maxOf(warmed, relief).coerceAtMost(MAX_HEAT)
    }

    val streak = habitStreak(ledger, holidays)
    val monday = today.monday()
    val weeklyProgress = ledger.trackerWeekProgress().toMutableMap()
    ledger.trackers.forEach { ref -> ledger.observations.getValue(ref).weekProgress?.let { weeklyProgress[ref] = it(monday) } }
    val openParts = (todaySoFar.missedTrackers + todaySoFar.freeTrackers).associateWith { ref ->
        val observation = ledger.observations.getValue(ref)
        val parts = observation.partsOn(today)
        if (parts.size == 1) parts else parts.filterNot { observation.partKeptOn(it, today) }
    }
    // The parts Mo does not ask for today: those a checklist no longer owes, or all of a free day
    // its plan does not ask for. The day's status, and with it the streak, stays as it is.
    val missedParts = openParts.mapValues { (ref, parts) ->
        val observation = ledger.observations.getValue(ref)
        if (ref !in todaySoFar.missedTrackers && !observation.asksWhileFreeOn(today)) parts
        else observation.owedPartsOn?.invoke(today)?.let { parts - it.toSet() }.orEmpty()
    }.filterValues { it.isNotEmpty() }
    val weeklyRemainders = ledger.trackers.mapNotNull { ref ->
        val target = ledger.observations.getValue(ref).target as? ObservationTarget.WeeklyAmount
        target?.let { ref to (it.requiredIn(monday) - it.amountIn(monday)).coerceAtLeast(0) }
    }.toMap()
    val weekGaps = ledger.catalogue.participants.mapNotNull { participant ->
        (participant.target as? RewardTarget.WeekAmount)?.let { target ->
            val gap = target.gapSizeIn(monday)
            participant.key to ((target.requiredIn(monday) - target.amountIn(monday)).coerceAtLeast(0) + gap - 1) / gap
        }
    }.toMap()
    val requirements = ledger.catalogue.participants.associate { participant ->
        val key = participant.key
        // A bank Mo has no observation for has no parts here: its open day costs what its target counts.
        val open = openParts[key]?.size
            ?: (participant.target as? RewardTarget.Daily)?.takeIf { streak.standings[key]?.openToday == true }?.openOn(today)
            ?: 1
        key to maxOf(weekGaps[key] ?: 0, open)
    }
    // A week's amount is covered on its Sunday, the day the walk judges it. Before it the week can
    // still be made up, so a token only quiets the asking for a day.
    val sunday = monday.plusDays(DayOfWeek.SUNDAY.value - 1L)
    val shortWeeks = ledger.catalogue.participants.filter { (weekGaps[it.key] ?: 0) > 0 && it.isDueOn(sunday, holidays) }.map { it.key }
    val coverableWeeks = shortWeeks.takeIf { today == sunday }.orEmpty()
    val quietOffered = shortWeeks.filterTo(mutableSetOf()) { today != sunday && (streak.standings[it]?.passes ?: 0) > 0 }
    // Every bank with an observation is watched, so this is a bank that came without one, as an
    // extension module's may. A week target with a rest day left is dealt that day at the close,
    // so no token is due.
    val openWithoutMo = streak.standings.filter { (ref, standing) ->
        standing.openToday && ref !in ledger.trackers && (streak.freeDaysLeftByTracker[ref] ?: 0) == 0
    }.keys
    val labels = ledger.observations.mapNotNull { (ref, observation) -> observation.label?.let { ref to it } }.toMap()
    val content = ledger.observations.mapValues { it.value.content }
    return MoMood(
        state = MoState.of(heat),
        dueToday = todaySoFar.dueCount,
        daysCold = daysCold,
        streak = streak,
        trackerLabels = labels,
        trackerContent = content,
        missingTrackersToday = todaySoFar.missedTrackers,
        freeTrackersToday = todaySoFar.freeTrackers,
        keptTrackersToday = todaySoFar.keptTrackers,
        latestTrackerToday = ledger.latestTrackerOn(today),
        trackerWeekProgress = weeklyProgress,
        trackerNeglect = ledger.trackerNeglect(),
        thrivingTrackers = ledger.thrivingTrackers(),
        trackerOpenParts = openParts,
        trackerMissedParts = missedParts,
        trackerCheckpoints = ledger.observations.mapValues { (_, observation) -> observation.checklist?.checkpoints ?: listOfNotNull(observation.checkpoint) },
        workoutRemindedTrackers = ledger.observations.filterValues { it.workoutReminderOn(today) }.keys,
        trackerWeeklyRemainders = weeklyRemainders,
        trackerWeeklyNudges = weeklyRemainders.filterValues { it > 0 }.takeIf { today.dayOfWeek.value >= DayOfWeek.FRIDAY.value }.orEmpty(),
        trackerTokenRequirements = requirements,
        tokenOpenTrackers = todaySoFar.missedTrackers.toSet() + openWithoutMo + coverableWeeks,
        quietOfferedTrackers = quietOffered,
        weekJudgedTrackers = weekGaps.keys,
        trackerOrder = ledger.trackers.toList(),
        tokenEarningBasisByTracker = ledger.catalogue.entries.associate { it.ref to it.earningBasisOn(today.minusDays(1)) },
        tokenEarnedAcrossWeeks = ledger.catalogue.entries.filter { it.earnsAcrossWeeksOn(today.minusDays(1)) }.mapTo(HashSet()) { it.ref },
        weeklyGoalTrackers = ledger.observations.filterValues { it.celebratesWeeklyGoal }.keys,
        trackerProgress = ledger.observations.mapNotNull { (ref, observation) ->
            observation.todayProgress(today, streak.standings[ref], ref in todaySoFar.missedTrackers || ref in todaySoFar.freeTrackers)?.let { ref to it }
        }.toMap(),
    )
}

private fun TrackerObservation.todayProgress(day: LocalDate, standing: MoStreakStanding?, openToday: Boolean): DayProgress? {
    if (progressPolicy == TrackerProgressPolicy.RecordedParts && !countsOn(day)) return null
    if (progressPolicy == TrackerProgressPolicy.ParticipatingDay) return when {
        standing?.keptToday == true -> DayProgress(1, 1)
        standing?.openToday == true -> DayProgress(0, 1)
        else -> null
    }
    if (progressPolicy == TrackerProgressPolicy.ParticipatingParts && standing?.keptToday != true && standing?.openToday != true) return null
    val parts = partsOn(day)
    if (parts.isEmpty()) return null
    val done = parts.count { partKeptOn(it, day) }
    if (progressPolicy == TrackerProgressPolicy.RecordedParts && done == 0 && !openToday) return null
    return DayProgress(done, parts.size, assumedOn(day))
}

private const val MAX_HEAT = 10
private const val STARTING_HEAT = 6
private const val HISTORY_DAYS = 21L

private fun finishedDayDelta(share: Double?): Int = when {
    share == null -> 0
    share >= 1.0 -> 2
    share >= 0.5 -> 1
    share > 0.0 -> -1
    else -> -2
}
