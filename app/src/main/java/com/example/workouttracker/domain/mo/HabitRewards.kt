package com.example.workouttracker.domain.mo

import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.activity.ActivityConfiguration
import com.example.workouttracker.domain.checklist.ChecklistRewards
import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.domain.measurement.MeasurementBank
import com.example.workouttracker.domain.measurement.MeasurementRewards
import com.example.workouttracker.domain.reward.Earn
import com.example.workouttracker.domain.reward.FreezeCapacity
import com.example.workouttracker.domain.reward.RewardRules
import com.example.workouttracker.domain.reward.RewardTarget
import com.example.workouttracker.domain.reward.RoutineDaysPerFreeze
import com.example.workouttracker.domain.reward.rewardWalk
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import com.example.workouttracker.domain.time.TimeConfiguration
import com.example.workouttracker.domain.time.TokenCover
import com.example.workouttracker.domain.time.stepMinutes
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** The day an install first ran the token rules. Before it, the old rules still decide everything. */
internal val DefaultRewardStart: LocalDate = LocalDate.parse("2026-09-28")

private const val DaysInAWeek = 7L

/**
 * This is a derived game ledger. It never writes completion records or fabricated minutes.
 *
 * Every day since the first record is judged by these rules, including days before [rewardStart]:
 * the old rules handed each week's free days to its first empty days, so a history kept under them
 * would show kept Mondays that nothing was done on.
 */
fun habitStreak(
    state: AppState,
    visibility: FeatureVisibilitySettings,
    today: LocalDate,
    holidays: HolidaySchedule = HolidaySchedule(),
    rewardStart: LocalDate = DefaultRewardStart,
): MoStreak {
    val records = MoRecordIndex(state, holidays)
    return habitStreak(MoLedger.of(records, visibility, today, holidays, rewardStart), holidays)
}

/**
 * The walk itself, for a caller that already built the [ledger] of the same records. The ledger
 * names the visible tasks, today and the reward-rule start; before that start it decides alone.
 */
internal fun habitStreak(ledger: MoLedger, holidays: HolidaySchedule): MoStreak {
    val today = ledger.today
    if (today < ledger.rewardStart) return ledger.streak()
    val catalogue = ledger.catalogue
    val walk = rewardWalk(catalogue.participants, catalogue.earnOrder, today, holidays)
    val standings = catalogue.entries.filter { it.bankShownOn(today) }.associateTo(LinkedHashMap()) { entry ->
        val participant = entry.participant
        entry.ref to MoStreakStanding(
            passes = walk.banks.getValue(entry.ref),
            passCapacity = participant.rules.maxPasses,
            nextToken = walk.nextToken[entry.ref],
            openToday = entry.ref in walk.openToday,
            keptToday = entry.recordedOn?.invoke(today) ?: (entry.ref in walk.keptToday),
        )
    }
    return MoStreak(
        days = walk.days,
        freezes = walk.freezes,
        freezeCapacity = FreezeCapacity,
        week = walk.week,
        weekCanEarnFreeze = walk.routineDays % RoutineDaysPerFreeze == 0,
        routineDaysUntilFreeze = RoutineDaysPerFreeze - walk.routineDays % RoutineDaysPerFreeze,
        freezeEarnedYesterday = walk.freezeEarnedYesterday,
        history = walk.history.map { MoDayRecord(it.date, it.mark, it.covered, it.rested, it.missed) },
        standings = standings,
        freeDaysLeftByTracker = walk.freeDaysLeft.filterKeys { it in standings },
        frozenTrackersYesterday = walk.frozenYesterday,
        protectedTrackersYesterday = walk.protectedYesterday,
        earnedTrackersYesterday = walk.earnedYesterday,
    )
}

/** The rules of [date], as the walk reads a checklist's: a bank, and a pass for a number of kept days. */
internal fun ChecklistRewards.rulesOn(date: LocalDate): RewardRules =
    RewardRules(maxPasses = maxPasses.on(date), earn = Earn.EveryKeptDays(everyKeptDays.on(date)))

/**
 * The rules of [date], as the walk reads Activity's: its bank's size on that day, and the steps
 * the week of that day earns by, each as the recorded active days it asks, the days a week its
 * Sunday has and the step's days more, with what the week has earned in all once it reaches it,
 * whatever holidays or sickness lowered the asking. The walk earns by the highest step reached,
 * so handing it the running totals is what makes every step reached count. The walk counts only
 * days it judged, so a sick day's record, or an away day's the view is not due on, adds nothing.
 */
internal fun ActivityConfiguration.rulesOn(date: LocalDate): RewardRules {
    val sunday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusDays(DaysInAWeek - 1)
    val days = daysAWeekOn(sunday)
    val steps = rewards.tokenSteps.on(sunday)
    return RewardRules(
        maxPasses = rewards.maxPasses.on(date),
        earn = Earn.KeptDaysInWeek(steps.steps.zip(steps.runningTotals) { step, earned -> Earn.KeptDaysStep(days + step.daysMore, earned) }),
    )
}

/** The rules of [date], as the walk reads a measurement's week bank: a bank, and a pass for a week with a number of logged days. */
internal fun MeasurementRewards.rulesOn(date: LocalDate): RewardRules =
    RewardRules(maxPasses = maxPasses.on(date), earn = Earn.KeptDaysInWeek(keptDaysInWeek.on(date)))

/**
 * The rules of [date], as the walk reads a measurement's bank: a pass for every so many kept
 * days, and on a day an earlier week rule reaches, a pass for a week with that rule's logged days,
 * in the bank as large as it was that day.
 */
internal fun MeasurementBank.rulesOn(date: LocalDate): RewardRules = when (this) {
    is MeasurementBank.Week -> rewards.rulesOn(date)
    is MeasurementBank.Standard -> when (val weeks = earlierWeeks?.takeIf { date <= it.until }) {
        null -> rewards.rulesOn(date)
        else -> RewardRules(maxPasses = rewards.maxPasses.on(date), earn = Earn.KeptDaysInWeek(weeks.keptDaysInWeek.on(date)))
    }
}

/**
 * The rules of [date], as the walk reads a time view's: its bank's size on that day, the steps
 * the week of that day earns by, each as the minutes its share of the Sunday's goal comes to
 * with what the week has earned in all once it reaches it, and the gaps the kind covers. The
 * walk earns by the highest step reached, so handing it the running totals is what makes every
 * step reached count, two that round to the same minute among them. The view has a bank:
 * each configured bank belongs to its own time view.
 */
internal fun TimeConfiguration.rulesOn(date: LocalDate): RewardRules {
    val bank = requireNotNull(rewards) { "Time view $id has no bank." }
    val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val goal = goalOfWeek(monday)
    val steps = bank.stepsOfWeek(monday)
    return RewardRules(
        maxPasses = bank.maxPasses.on(date),
        earn = Earn.AmountStepsInWeek(steps.steps.zip(steps.runningTotals) { step, earned -> Earn.AmountStep(stepMinutes(step.percent, goal), earned) }),
    )
}

/**
 * A time view's target as the walk judges it: what each week asks ([TimeConfiguration.weekAsks]),
 * in gaps of what one token covers that week ([TimeRewards.coverOfWeek]), against the minutes
 * [minutesIn] the week holds. Every
 * week's minutes earn unless [earnsIn] limits the earning weeks.
 */
internal fun TimeConfiguration.weekTarget(
    daysOff: HolidaySchedule,
    minutesIn: (monday: LocalDate) -> Int,
    earnsIn: (monday: LocalDate) -> Boolean = { true },
) = RewardTarget.WeekAmount(
    requiredIn = { monday -> weekAsks(monday, daysOff) },
    gapSizeIn = { monday -> (rewards?.coverOfWeek(monday) ?: TokenCover.QuarterOfGoal).minutesOf(goalOfWeek(monday)) },
    amountIn = minutesIn,
    earnsIn = earnsIn,
)
