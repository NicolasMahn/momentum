package de.nicolasmahn.momentum.domain.mo.compatibility

import de.nicolasmahn.momentum.domain.time.TokenCover
import de.nicolasmahn.momentum.domain.compatibility.contentKey

import de.nicolasmahn.momentum.domain.*
import de.nicolasmahn.momentum.domain.activity.*
import de.nicolasmahn.momentum.domain.checklist.*
import de.nicolasmahn.momentum.domain.compatibility.*
import de.nicolasmahn.momentum.domain.holiday.*
import de.nicolasmahn.momentum.domain.mo.*
import de.nicolasmahn.momentum.domain.compatibility.MoNeed
import de.nicolasmahn.momentum.domain.reward.*
import de.nicolasmahn.momentum.domain.settings.*
import de.nicolasmahn.momentum.domain.time.*
import java.time.LocalDate

internal data class InheritedMoodReport(
    val state: MoState,
    /** Commitments due today that have no record yet and no free day left, so skipping one is a miss. */
    val missingToday: List<MoNeed>,
    /** Commitments due today that have no record yet, but a free day of this week would cover them. */
    val freeToday: List<MoNeed>,
    /** Commitments due today that already have their record. */
    val keptToday: List<MoNeed>,
    /** The visible task logged last today, so Mo can be shown doing it. Null before the first record. */
    val latestToday: MoNeed?,
    /** How many commitments today has at all, open or not. Zero before anything was ever logged. */
    val dueToday: Int,
    /** This week's days done against the days expected, for every visible task. */
    val weekProgress: Map<MoNeed, MoWeekProgress>,
    /** Consecutive days, ending yesterday, that closed at Cold or Husk. */
    val daysCold: Int,
    val streak: InheritedStreakReport,
    /** Visible tasks that were missed repeatedly over the last week. */
    val neglect: Map<MoNeed, MoNeglect>,
    /** Visible tasks kept on several days of the last week and missed on none. Never also in [neglect]. */
    val thriving: Set<MoNeed>,
    /**
     * The checkmarks still open for each task not kept today. Each waits for its own reminder, so
     * evening Care is not asked for in the morning. Read it through [stillOpen].
     */
    val openParts: Map<MoNeed, List<DayPart>> = emptyMap(),
    /**
     * The open parts that owe nothing, because everything unmet there was marked missed. A token
     * still has to cover each, but no reminder and no lateness waits for them ([stillOwed]).
     */
    val missedParts: Map<MoNeed, List<DayPart>> = emptyMap(),
    val readingMinutesThisWeek: Int = 0,
    val readingMinutesRemaining: Int = 0,
    val readingNudgeDue: Boolean = false,
    val chosenToday: Set<MoNeed> = emptySet(),
    val usesWeeklyReading: Boolean = false,
    /**
     * The stored configuration of each task that is a checklist: lateness reads its checkpoints,
     * and Mo's content for it follows its motif through [contentKey].
     */
    val checklists: Map<MoNeed, ChecklistConfiguration> = emptyMap(),
    /**
     * The checkpoint of each task that is a logged view, Weight's, Eating's and Reading's:
     * lateness reads its time and whether it reminds. Like [checklists] it is a setting, never an
     * outcome.
     */
    val checkpoints: Map<MoNeed, Checkpoint> = emptyMap(),
    /**
     * The logged days a week of Weight had to hold at yesterday's close: the number a token earned
     * then was earned by, which the words after it name. Like [checklists] it is a setting read for
     * what Mo says, never an outcome, and today's number may already differ.
     */
    val weightDaysForATokenYesterday: Int = WeightRewards.keptDaysInWeek.current,
    /** The kept days a token of Eating took at yesterday's close, read as [weightDaysForATokenYesterday] is. */
    val eatingDaysForATokenYesterday: Int = EatingRewards.everyKeptDays.current,
    /**
     * One gap of this week of Reading, a quarter of the goal the week has: the minutes one token
     * covers of what is still missing, which decides whether tokens can be set aside and how many.
     */
    val readingGapMinutes: Int = TokenCover.QuarterOfGoal.minutesOf(DefaultWeeklyGoalMinutes),
    /** The goal a week of Reading had to reach at yesterday's close, in minutes, read as [weightDaysForATokenYesterday] is. */
    val readingGoalForATokenYesterday: Int = DefaultWeeklyGoalMinutes,
    /** The active days a week of Activity had to hold at yesterday's close, read as [weightDaysForATokenYesterday] is. */
    val activityDaysForATokenYesterday: Int = DefaultActivityDaysAWeek,
    val labels: Map<MoNeed, String> = emptyMap(),
    val content: Map<MoNeed, MoContentKey> = emptyMap(),
)

internal data class InheritedStreakReport(
    /** Finished days in a row without an unprotected miss, plus today once every task has its record. */
    val days: Int,
    /** Freezes in the bank. Never more than [freezeCapacity], because a streak only resets with an empty bank. */
    val freezes: Int,
    val freezeCapacity: Int,
    /** The current week, Monday first, always seven entries. */
    val week: List<MoDayMark>,
    /** Whether this week can still end perfect and earn a freeze (ignoring whether the bank is full). */
    val weekCanEarnFreeze: Boolean,
    /** Tasks yesterday missed and a freeze paid for. Empty when yesterday cost none or broke the streak. */
    val frozenYesterday: List<MoNeed>,
    val passes: Map<MoNeed, Int> = emptyMap(),
    /** How many tokens each task's bank can hold. */
    val passCapacity: Map<MoNeed, Int> = emptyMap(),
    val restTokens: Int = 0,
    val protectedYesterday: List<MoNeed> = emptyList(),
    val routineDaysUntilFreeze: Int = 28,
    /** Tasks whose token the close of yesterday added to the bank. */
    val earnedYesterday: List<MoNeed> = emptyList(),
    /** Whether the close of yesterday added a freeze. */
    val freezeEarnedYesterday: Boolean = false,
    /** Every day the streak has walked, oldest first and ending with today, for looking back. */
    val history: List<InheritedDayReport> = emptyList(),
    /** How far each task has come toward its next token, today included once it is kept. */
    val nextToken: Map<MoNeed, MoTokenProgress> = emptyMap(),
    /** The banks of configured checklists that take part in rewards, by checklist ID. */
    val checklistPasses: Map<String, Int> = emptyMap(),
    val checklistPassCapacity: Map<String, Int> = emptyMap(),
    val checklistNextToken: Map<String, MoTokenProgress> = emptyMap(),
    /** Joined checklists due today with something still open; no token of Mo's can cover them. */
    val checklistsOpenToday: Set<String> = emptySet(),
    /** The checklists shown that take part today. One that left or is hidden keeps its bank out of sight. */
    val checklistsTakingPart: Set<String> = emptySet(),
    /**
     * Substances' standing while logging counts toward the streak and it is shown; null otherwise,
     * as a checklist that left or is hidden keeps its bank out of sight. Only that the day was
     * logged is read, never what was logged.
     */
    val substances: MoStreakStanding? = null,
    /**
     * The standing of each measurement a person made that counts toward the streak today and is
     * shown, by measurement ID, in the order they are configured. One that left, never joined or
     * is hidden has no entry and keeps its bank out of sight. Only that a day was logged is read,
     * never the number.
     */
    val measurements: Map<String, MoStreakStanding> = emptyMap(),
    /**
     * The standing of each questions view a person made that counts toward the streak today and is
     * shown, by its ID, as [measurements] holds a measurement's. Only that a day was answered is
     * read, never the answers.
     */
    val questions: Map<String, MoStreakStanding> = emptyMap(),
    /**
     * The standing of each time view a person made that counts toward the streak today and is
     * shown, by its ID, as [measurements] holds a measurement's. Its next token is so many minutes
     * of this week away, and it has none in a week that cannot earn, one it joined in the middle
     * of. Today is open on a Sunday whose week is judged and still short, and kept once today
     * holds a log, whatever the week still asks for.
     */
    val time: Map<String, MoStreakStanding> = emptyMap(),
)

internal data class InheritedDayReport(
    val date: java.time.LocalDate,
    val mark: MoDayMark,
    val covered: Set<MoNeed> = emptySet(),
    val rested: Boolean = false,
    /** Configured checklists covered on this day, by ID. */
    val coveredChecklists: Set<String> = emptySet(),
    /** Measurements a person made covered on this day, by ID. */
    val coveredMeasurements: Set<String> = emptySet(),
    /** Questions views a person made covered on this day, by ID. */
    val coveredQuestions: Set<String> = emptySet(),
    /** Time views a person made covered on this day, by ID. */
    val coveredTime: Set<String> = emptySet(),
)

/** Explicit historical verification boundary. These reports are never app state or runtime inputs. */
internal class InheritedMoEvaluation(
    state: AppState,
    private val visibility: FeatureVisibilitySettings,
    private val today: LocalDate,
    private val holidays: HolidaySchedule = HolidaySchedule(),
    private val rewardStart: LocalDate = DefaultRewardStart,
    chosen: Set<MoNeed> = emptySet(),
) {
    private val configuredState = state.withInheritedTrackerConfigurations()
    private val records = MoRecordIndex(configuredState, holidays)
    private val catalogue = trackerRewards(records, visibility, holidays, today, rewardStart)
    private val ledger = MoLedger.fromCatalogue(catalogue, visibility, today, holidays, rewardStart)
    /** Current evaluation of the explicitly materialized historical fixture. */
    val current = MoEvaluation(configuredState, visibility, today, holidays, rewardStart).moodWithTrackerChoices(chosen.mapTo(linkedSetOf()) { it.view.trackerRef })
    val streak: InheritedStreakReport by lazy {
        if (today < rewardStart) current.streak.inheritedReport() else {
            val walk = rewardWalk(catalogue.participants, catalogue.earnOrder, today, holidays)
            val inherited = InheritedNeedsByTracker
            fun Collection<TrackerRef>.needs() = mapNotNull(inherited::get)
            fun Collection<TrackerRef>.ids(kind: String) = filter { it.type.value == kind && it !in inherited }.mapTo(linkedSetOf()) { it.id }
            fun <V> Map<TrackerRef, V>.byNeed() = entries.mapNotNull { (ref, value) -> inherited[ref]?.let { it to value } }.toMap()
            fun <V> Map<TrackerRef, V>.byKind(kind: String) = filterKeys { it.type.value == kind && it !in inherited }.mapKeys { it.key.id }
            val left = inherited.filterValues { !configuredState.takesPart(it, today) }.keys
            val capacity = catalogue.participants.filterNot { it.key in left }.associate { it.key to it.rules.maxPasses }
            val next = walk.nextToken.filterKeys { it !in left }.byNeed()
            val takingPart = current.streak.standings.keys.ids("checklist")
            InheritedStreakReport(
                days = walk.days, freezes = walk.freezes, freezeCapacity = FreezeCapacity,
                week = walk.week, weekCanEarnFreeze = walk.routineDays % RoutineDaysPerFreeze == 0,
                frozenYesterday = walk.frozenYesterday.needs(),
                passes = walk.banks.filterKeys { it !in left }.byNeed(), passCapacity = capacity.byNeed(),
                restTokens = walk.freeDaysLeft[ActivityTrackerRef]?.takeUnless { ActivityTrackerRef in left } ?: 0,
                protectedYesterday = walk.protectedYesterday.needs(), routineDaysUntilFreeze = RoutineDaysPerFreeze - walk.routineDays % RoutineDaysPerFreeze,
                earnedYesterday = walk.earnedYesterday.needs(), freezeEarnedYesterday = walk.freezeEarnedYesterday,
                history = walk.history.map { row -> InheritedDayReport(
                    row.date, row.mark, row.covered.needs().toSet(), ActivityTrackerRef in row.rested,
                    row.covered.ids("checklist"), row.covered.ids("measurement"), row.covered.ids("questions"), row.covered.ids("time"),
                ) },
                nextToken = InheritedNextTokenOrder.mapNotNull { need -> next[need]?.let { need to it } }.toMap(),
                checklistPasses = walk.banks.byKind("checklist"), checklistPassCapacity = capacity.byKind("checklist"),
                checklistNextToken = walk.nextToken.byKind("checklist").filterKeys { it in takingPart },
                checklistsOpenToday = walk.openToday.ids("checklist"), checklistsTakingPart = takingPart,
                substances = current.streak.standings[SubstancesTrackerRef],
                measurements = current.streak.standings.byKind("measurement"), questions = current.streak.standings.byKind("questions"), time = current.streak.standings.byKind("time"),
            )
        }
    }
    val mood: InheritedMoodReport by lazy {
        val timer = configuredState.readingRules
        val monday = today.monday()
        val minutes = records.minutesInWeek(ReadingTimeId, monday)
        val required = if (today >= rewardStart) timer.weekAsks(monday, holidays) else timer.goalOfWeek(monday)
        current.inheritedReport(streak).copy(
            readingMinutesThisWeek = minutes,
            readingMinutesRemaining = if (MoNeed.Reading in ledger.needs) (required - minutes).coerceAtLeast(0) else 0,
            readingNudgeDue = today >= rewardStart && today.dayOfWeek.value >= 5,
            usesWeeklyReading = today >= rewardStart,
            checklists = ledger.observations.mapNotNull { (ref, observation) -> observation.checklist?.let { ref to it } }.toMap().byNeed(),
            checkpoints = ledger.observations.mapNotNull { (ref, observation) -> observation.checkpoint?.let { ref to it } }.toMap().byNeed(),
            readingGapMinutes = TokenCover.QuarterOfGoal.minutesOf(timer.goalOfWeek(monday)),
            readingGoalForATokenYesterday = timer.goalOfWeek(today.minusDays(1).monday()),
            weightDaysForATokenYesterday = configuredState.weightRewards.keptDaysInWeek.on(today.minusDays(1)),
            eatingDaysForATokenYesterday = configuredState.eatingRewards.everyKeptDays.on(today.minusDays(1)),
            activityDaysForATokenYesterday = configuredState.activityRules.daysAWeekOf(today.minusDays(1).monday()),
        )
    }
}

private fun <V> Map<TrackerRef, V>.byNeed(): Map<MoNeed, V> = entries.mapNotNull { (ref, value) -> InheritedNeedsByTracker[ref]?.let { it to value } }.toMap()
private fun Collection<TrackerRef>.needs(): List<MoNeed> = mapNotNull(InheritedNeedsByTracker::get)

/** A visible-only projection for tests comparing current evaluation results, never golden history. */
internal fun MoMood.inheritedReport(streak: InheritedStreakReport = this.streak.inheritedReport()) = InheritedMoodReport(
    state, missingTrackersToday.needs(), freeTrackersToday.needs(), keptTrackersToday.needs(),
    latestTrackerToday?.let(InheritedNeedsByTracker::get), dueToday, trackerWeekProgress.byNeed(), daysCold, streak,
    trackerNeglect.byNeed(), thrivingTrackers.needs().toSet(), trackerOpenParts.byNeed(), trackerMissedParts.byNeed(),
    chosenToday = selectedTrackerChoices.needs().toSet(),
)

internal fun MoStreak.inheritedReport(): InheritedStreakReport {
    fun <V> Map<TrackerRef, V>.byKind(kind: String) = filterKeys { it.type.value == kind && it !in InheritedNeedsByTracker }.mapKeys { it.key.id }
    return InheritedStreakReport(
        days, freezes, freezeCapacity, week, weekCanEarnFreeze, frozenTrackersYesterday.needs(),
        participantBanks.byNeed(), participantCapacities.byNeed(), freeDaysLeftByTracker[ActivityTrackerRef] ?: 0,
        protectedTrackersYesterday.needs(), routineDaysUntilFreeze, earnedTrackersYesterday.needs(), freezeEarnedYesterday,
        history.map { it.inheritedReport() }, participantNextToken.byNeed(),
        standings.byKind("checklist").mapValues { it.value.passes }, standings.byKind("checklist").mapValues { it.value.passCapacity },
        standings.byKind("checklist").mapNotNull { (id, value) -> value.nextToken?.let { id to it } }.toMap(),
        standings.byKind("checklist").filterValues { it.openToday }.keys, standings.byKind("checklist").keys,
        standings[SubstancesTrackerRef], standings.byKind("measurement"), standings.byKind("questions"), standings.byKind("time"),
    )
}

internal fun MoDayRecord.inheritedReport() = InheritedDayReport(
    date, mark, coveredTrackers.needs().toSet(), ActivityTrackerRef in restedTrackers,
    coveredTrackers.ids("checklist"), coveredTrackers.ids("measurement"), coveredTrackers.ids("questions"), coveredTrackers.ids("time"),
)
private fun Set<TrackerRef>.ids(kind: String): Set<String> = filter { it.type.value == kind && it !in InheritedNeedsByTracker }.mapTo(linkedSetOf()) { it.id }

internal fun inheritedHabitStreak(
    state: AppState, visibility: FeatureVisibilitySettings, today: LocalDate,
    holidays: HolidaySchedule = HolidaySchedule(), rewardStart: LocalDate = DefaultRewardStart,
): InheritedStreakReport = InheritedMoEvaluation(state, visibility, today, holidays, rewardStart).streak

internal fun inheritedMoMood(
    state: AppState, featureVisibility: FeatureVisibilitySettings, today: LocalDate,
    holidays: HolidaySchedule = HolidaySchedule(), chosenPasses: Set<MoNeed> = emptySet(),
    rewardStart: LocalDate = DefaultRewardStart,
): InheritedMoodReport = InheritedMoEvaluation(state, featureVisibility, today, holidays, rewardStart, chosenPasses).mood

/** Reconstructs the configurations an inherited archive supplied, without changing any recorded fact. */
internal fun AppState.withInheritedTrackerConfigurations(): AppState = copy(
    activity = activity ?: de.nicolasmahn.momentum.domain.activity.testActivityConfiguration(),
    measurements = if (measurements.any { it.id == WeightMeasurementId }) measurements else measurements + de.nicolasmahn.momentum.domain.measurement.testWeightMeasurement(),
    questions = if (questions.any { it.id == EatingQuestionsId }) questions else questions + de.nicolasmahn.momentum.domain.questions.testEatingQuestions(),
    time = if (time.any { it.id == ReadingTimeId }) time else time + de.nicolasmahn.momentum.domain.time.testReadingTime(),
    checklists = checklists + listOfNotNull(
        de.nicolasmahn.momentum.domain.care.testCareChecklist().takeUnless { fallback -> checklists.any { it.id == fallback.id } },
        de.nicolasmahn.momentum.domain.supplement.testSupplementsChecklist().takeUnless { fallback -> checklists.any { it.id == fallback.id } },
    ),
)
