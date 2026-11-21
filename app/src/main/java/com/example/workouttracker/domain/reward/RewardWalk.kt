package com.example.workouttracker.domain.reward

import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.domain.mo.MoDayMark
import com.example.workouttracker.domain.mo.MoTokenProgress
import com.example.workouttracker.domain.mo.MoWeekDay
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** How a finished day went for the streak, with the views whose gaps were covered. */
internal data class RewardDay<K>(
    val date: LocalDate,
    val mark: MoDayMark,
    val covered: Set<K> = emptySet(),
    /** Whether a week target's free day was dealt to it. */
    val rested: Set<K> = emptySet(),
    /**
     * On a missed day, the views that had a gap, those whose own bank could not pay for it first,
     * then the others, each in judging order: the views that took part that day and broke it.
     */
    val missed: List<K> = emptyList(),
)

internal class RewardWalkResult<K>(
    val days: Int,
    val freezes: Int,
    val week: List<MoDayMark>,
    val history: List<RewardDay<K>>,
    val banks: Map<K, Int>,
    val freeDaysLeft: Map<K, Int>,
    val protectedYesterday: List<K>,
    val frozenYesterday: List<K>,
    val routineDays: Int,
    val earnedYesterday: List<K>,
    val freezeEarnedYesterday: Boolean,
    /** How far each shown view is from its next pass, whether or not it is due today. */
    val nextToken: Map<K, MoTokenProgress>,
    /** The views due today that are not kept yet, in judging order. */
    val openToday: List<K>,
    /** The views due today that are already kept; a weekly amount is never kept before Sunday closes. */
    val keptToday: Set<K>,
)

/** Kept on a day it is due: nothing open, on a day it is not excused on. */
private fun RewardTarget.Daily.keptOn(day: LocalDate): Boolean = !excusedOn(day) && openOn(day) == 0

/** A week of every day kept by the daily views earns no pass, but four of them earn a freeze. */
internal const val RoutineDaysPerFreeze = 28
internal const val FreezeCapacity = 2

/**
 * The reward walk: every day since the first view was due, judged by each view's target, covered by
 * passes from each view's own bank or by a freeze, and marked. It sees only the facts the targets
 * read, never a record, so it can change no recorded fact.
 *
 * [participants] are judged in their order, and [earnOrder] decides the order banks fill in. Every
 * participant keeps a bank, starting with one pass, even while it is hidden.
 *
 * A day a view is not shown on is not due, like a sick day, and a view hidden from the beginning
 * and never shown stays out of the walk's start. Earning is decided apart from being due: a daily
 * bank counts kept days and grants the moment its count is reached, so a dated hide adds nothing
 * from its day and revokes nothing before it; a week bank grants on Sunday by the days that were
 * due and kept, and a weekly amount by its minutes whatever was due, in every week the view says
 * can earn. A week with a day hidden by a dated change earns nothing for either of those two: the
 * bank the view had on the day it was hidden waits. A view hidden from the beginning earns as it
 * always has, a weekly amount by its minutes and the two others never, since no day was due.
 *
 * A bank earns by the rule of each day, so one that earned by the week and earns by kept days
 * from a day on has every earlier week judged as it was, and counts kept days from that day.
 *
 * Every view starts with one pass and the streak with one freeze, so a first slip is forgiven. A
 * freeze is earned every [RoutineDaysPerFreeze] days on which every daily view was kept outside
 * holidays, up to [FreezeCapacity].
 *
 * A sick day asks nothing of any view: it is marked sick, spends and earns nothing, and leaves the
 * streak as it was. A week's free days stay for its other days, and a weekly amount says itself
 * what its week asks, a seventh less for each sick day.
 */
internal fun <K> rewardWalk(
    participants: List<RewardParticipant<K>>,
    earnOrder: List<K>,
    today: LocalDate,
    daysOff: HolidaySchedule,
): RewardWalkResult<K> {
    val byKey = participants.associateBy { it.key }
    fun RewardParticipant<K>.dueOn(day: LocalDate) = isDueOn(day, daysOff)
    fun RewardParticipant<K>.pausedIn(monday: LocalDate) = (0L until 7L).any { hiddenByAChangeOn(monday.plusDays(it)) }

    val banks = participants.associateTo(LinkedHashMap()) { it.key to 1 }
    var freezes = 1
    var days = 0
    var routineDays = 0
    // Every bank has both counts, since the rule of a day says which one its kept day adds to: the
    // kept days toward the next pass across weeks, and the kept days of the week in progress.
    val earnCounts = participants.associateTo(HashMap()) { it.key to 0 }
    val weekKept = participants.associateTo(HashMap()) { it.key to 0 }
    // A bank that earned by the week and earns across weeks from [day] on takes the kept days of
    // its week in progress along, so none logged toward a pass is lost. They stop one short of a
    // pass: the days before the change were judged by the week rule, so they grant nothing by
    // themselves, and the next kept day does.
    fun RewardParticipant<K>.carryOpenWeekOn(day: LocalDate) {
        val open = weekKept.getValue(key)
        if (open == 0) return
        val earn = rulesOn(day).earn as? Earn.EveryKeptDays ?: return
        earnCounts[key] = (earnCounts.getValue(key) + open).coerceAtMost(earn.days - 1)
        weekKept[key] = 0
    }
    // A week target's free days are dealt from the start of its span, the Monday or a later day
    // its target changed on: how many the span has used so far, and where it started.
    val freeDaysUsed = participants.filter { it.target is RewardTarget.WeekTimes }.associateTo(LinkedHashMap()) { it.key to 0 }
    val spanStarts = HashMap<K, LocalDate>()
    fun RewardParticipant<K>.freeDaysLeftOn(date: LocalDate): Int {
        val target = target as RewardTarget.WeekTimes
        val start = target.spanStartOn(date)
        if (spanStarts[key] != start) {
            spanStarts[key] = start
            freeDaysUsed[key] = 0
        }
        return target.freeDaysOn(date) - freeDaysUsed.getValue(key)
    }
    var frozenYesterday = emptyList<K>()
    var protectedYesterday = emptyList<K>()
    val weekMarks = mutableListOf<MoDayMark>()
    val history = mutableListOf<RewardDay<K>>()

    // From the Monday of the first week anything was due, so every week of the walk is a whole one.
    var day = (participants.filter { it.everShown }.mapNotNull { it.began }.minOrNull() ?: today)
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    // What the close of yesterday added to the banks, so Mo can present it this morning.
    val earnedYesterday = mutableListOf<K>()
    var freezeEarnedYesterday = false
    fun grant(key: K) {
        val before = banks.getValue(key)
        banks[key] = (before + 1).coerceAtMost(byKey.getValue(key).rulesOn(day).maxPasses)
        if (day == today.minusDays(1) && banks.getValue(key) > before) earnedYesterday += key
    }

    while (day < today) {
        val monday = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val sunday = day.dayOfWeek == DayOfWeek.SUNDAY
        // A smaller bank holds no more than it allows from its first day; earned passes are not lost before.
        participants.forEach { banks[it.key] = banks.getValue(it.key).coerceAtMost(it.rulesOn(day).maxPasses) }
        val due = participants.filter { it.dueOn(day) }
        val kept = mutableSetOf<K>()
        val rested = mutableSetOf<K>()
        val gaps = LinkedHashMap<K, Int>()
        due.forEach { participant ->
            when (val target = participant.target) {
                is RewardTarget.Daily -> if (!target.excusedOn(day)) {
                    val open = target.openOn(day)
                    if (open == 0) kept += participant.key else gaps[participant.key] = open
                }
                is RewardTarget.WeekTimes -> when {
                    target.keptOn(day) -> kept += participant.key
                    participant.freeDaysLeftOn(day) > 0 -> {
                        freeDaysUsed[participant.key] = freeDaysUsed.getValue(participant.key) + 1
                        rested += participant.key
                    }
                    else -> gaps[participant.key] = 1
                }
                is RewardTarget.WeekAmount -> if (sunday) {
                    val amount = target.amountIn(monday)
                    val required = target.requiredIn(monday)
                    val gapSize = target.gapSizeIn(monday)
                    if (amount >= required) kept += participant.key
                    else gaps[participant.key] = (required - amount + gapSize - 1) / gapSize
                }
            }
        }
        val hasDue = due.any { it.target !is RewardTarget.WeekAmount || sunday }
        val canPass = gaps.all { (key, count) -> count <= banks.getValue(key) }
        val mark = when {
            daysOff.isSick(day) -> MoDayMark.Sick
            !hasDue -> MoDayMark.NothingDue
            gaps.isEmpty() -> MoDayMark.Perfect
            canPass -> MoDayMark.Protected
            freezes > 0 -> MoDayMark.Frozen
            else -> MoDayMark.Missed
        }
        when (mark) {
            MoDayMark.Perfect -> days++
            MoDayMark.Protected -> gaps.forEach { (key, count) -> banks[key] = banks.getValue(key) - count }
            MoDayMark.Frozen -> freezes--
            MoDayMark.Missed -> days = 0
            else -> Unit
        }
        protectedYesterday = if (mark == MoDayMark.Protected) gaps.keys.toList() else emptyList()
        frozenYesterday = if (mark == MoDayMark.Frozen) gaps.keys.toList() else emptyList()
        weekMarks += mark
        val covered = if (mark == MoDayMark.Protected || mark == MoDayMark.Frozen) gaps.keys.toSet() else emptySet()
        val missed = if (mark == MoDayMark.Missed) gaps.keys.sortedBy { key -> if (gaps.getValue(key) > banks.getValue(key)) 0 else 1 } else emptyList()
        history += RewardDay(day, mark, covered, rested, missed)

        val routine = due.filter { it.target is RewardTarget.Daily }
        if (!daysOff.isHoliday(day) && routine.isNotEmpty() && routine.all { it.key in kept }) {
            routineDays++
            if (routineDays % RoutineDaysPerFreeze == 0 && freezes < FreezeCapacity) {
                freezes++
                if (day == today.minusDays(1)) freezeEarnedYesterday = true
            }
        }
        participants.forEach { it.carryOpenWeekOn(day) }
        earnOrder.forEach { key ->
            if (key !in kept) return@forEach
            when (val earn = byKey.getValue(key).rulesOn(day).earn) {
                is Earn.EveryKeptDays -> {
                    val total = earnCounts.getValue(key) + 1
                    earnCounts[key] = total % earn.days
                    repeat(total / earn.days) { grant(key) }
                }
                is Earn.KeptDaysInWeek -> weekKept[key] = weekKept.getValue(key) + 1
                is Earn.AmountStepsInWeek -> Unit
            }
        }
        if (sunday) {
            earnOrder.forEach { key ->
                val participant = byKey.getValue(key)
                // A week with a day hidden by a dated change earns nothing for a bank that grants weekly.
                val paused = participant.pausedIn(monday)
                when (val earn = participant.rulesOn(day).earn) {
                    is Earn.KeptDaysInWeek -> {
                        if (!paused) repeat(earn.passesFor(weekKept.getValue(key))) { grant(key) }
                        weekKept[key] = 0
                    }
                    // A recorded amount earns whether or not the view is due, as it always has,
                    // in a week the view says can earn.
                    is Earn.AmountStepsInWeek -> {
                        val target = participant.target as RewardTarget.WeekAmount
                        if (!paused && target.earnsIn(monday)) repeat(earn.passesFor(target.amountIn(monday))) { grant(key) }
                    }
                    is Earn.EveryKeptDays -> Unit
                }
            }
            weekMarks.clear()
        }
        day = day.plusDays(1)
    }

    participants.forEach { banks[it.key] = banks.getValue(it.key).coerceAtMost(it.rulesOn(today).maxPasses) }
    // Today's rule decides what the next pass is shown by, on the day a bank changes how it earns too.
    participants.forEach { it.carryOpenWeekOn(today) }
    val thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val todaySunday = today.dayOfWeek == DayOfWeek.SUNDAY
    // What is left for today and the rest of its span, before today itself is dealt a free day.
    val freeDaysLeft = freeDaysUsed.keys.associateWith { key -> byKey.getValue(key).freeDaysLeftOn(today).coerceAtLeast(0) }
    val todayNeeds = participants.filter { it.dueOn(today) }
    val todayDue = todayNeeds.any { it.target !is RewardTarget.WeekAmount || todaySunday }
    // Only views due today are asked about, so a hidden view's records are never read. A day the
    // view is excused on is not kept by a record made anyway, today as on a finished day: the
    // close counts it for nothing, so nothing shows it as counted before.
    val keptToday = todayNeeds.filterTo(mutableSetOf()) { participant ->
        when (val target = participant.target) {
            is RewardTarget.Daily -> target.keptOn(today)
            is RewardTarget.WeekTimes -> target.keptOn(today)
            is RewardTarget.WeekAmount -> false
        }
    }.mapTo(mutableSetOf()) { it.key }
    val openToday = todayNeeds.filterNot { participant ->
        when (val target = participant.target) {
            is RewardTarget.Daily -> target.excusedOn(today) || participant.key in keptToday
            is RewardTarget.WeekTimes -> participant.key in keptToday
            is RewardTarget.WeekAmount -> !todaySunday || target.amountIn(thisMonday) >= target.requiredIn(thisMonday)
        }
    }.map { it.key }
    val todayComplete = todayDue && openToday.isEmpty()
    if (todayComplete) days++
    // Today counts toward the next pass as soon as it is kept, although the pass only arrives when
    // the day or the week closes.
    fun counted(key: K, finished: Int) = finished + if (key in keptToday) 1 else 0
    val settled = history.associateBy { it.date }
    fun thisWeek(key: K, kept: (LocalDate) -> Boolean, rested: (LocalDate) -> Boolean): List<MoWeekDay> =
        (0L until 7L).map { thisMonday.plusDays(it) }.map { date ->
            when {
                date > today -> MoWeekDay.Upcoming
                // Nothing was asked, so a record made while sick counts for nothing either.
                daysOff.isSick(date) -> MoWeekDay.Sick
                kept(date) -> MoWeekDay.Kept
                rested(date) -> MoWeekDay.Rested
                date == today -> MoWeekDay.Open
                settled[date]?.let { it.mark == MoDayMark.Protected && key in it.covered } == true -> MoWeekDay.Covered
                else -> MoWeekDay.Missed
            }
        }
    // Every bank on show says how far its next pass is, also on a day its view asks nothing, so a
    // view not taken up yet starts from nothing instead of looking blank. Only a bank that grants
    // weekly, in a week that cannot earn, says nothing: a week paused by a dated hide, or a weekly
    // amount's week that says it cannot. Nothing done in it leads to a pass.
    val nextToken = participants.filter { it.visibleOn(today) }.mapNotNull { participant ->
        val key = participant.key
        val target = participant.target
        val progress = when (val earn = participant.rules.earn) {
            is Earn.EveryKeptDays -> MoTokenProgress(counted(key, earnCounts.getValue(key)), earn.days)
            is Earn.KeptDaysInWeek -> {
                if (participant.pausedIn(thisMonday)) return@mapNotNull null
                val kept: (LocalDate) -> Boolean
                val rested: (LocalDate) -> Boolean
                when (target) {
                    is RewardTarget.Daily -> {
                        kept = target::keptOn
                        rested = target.excusedOn
                    }
                    is RewardTarget.WeekTimes -> {
                        // A day the view takes no part in, an away day it is not due on, is
                        // none of the week's days: a record on it adds nothing to the pass, so
                        // the square rests rather than counts.
                        kept = { participant.takesPartOn(it) && target.keptOn(it) }
                        rested = { !participant.takesPartOn(it) || settled[it]?.rested?.contains(key) == true }
                    }
                    is RewardTarget.WeekAmount -> error("A weekly amount earns by amount, not by kept days.")
                }
                // The next pass is the next step's days, until the week's last one is reached.
                val keptSoFar = counted(key, weekKept.getValue(key))
                MoTokenProgress(keptSoFar, earn.nextDaysAfter(keptSoFar), thisWeek(key, kept, rested))
            }
            // The next pass is the next step's amount, until the week's last one is reached.
            is Earn.AmountStepsInWeek -> {
                target as RewardTarget.WeekAmount
                if (participant.pausedIn(thisMonday) || !target.earnsIn(thisMonday)) return@mapNotNull null
                val amount = target.amountIn(thisMonday)
                MoTokenProgress(amount, earn.nextAmountAfter(amount))
            }
        }
        key to progress.copy(done = progress.done.coerceAtMost(progress.goal))
    }.toMap()
    val todayMark = when {
        daysOff.isSick(today) -> MoDayMark.Sick
        !todayDue -> MoDayMark.NothingDue
        todayComplete -> MoDayMark.Perfect
        else -> MoDayMark.Open
    }
    val week = weekMarks + todayMark + List(7 - today.dayOfWeek.value) { MoDayMark.Upcoming }
    return RewardWalkResult(
        days = days,
        freezes = freezes,
        week = week,
        history = history + RewardDay(today, week[today.dayOfWeek.value - 1]),
        banks = banks,
        freeDaysLeft = freeDaysLeft,
        protectedYesterday = protectedYesterday,
        frozenYesterday = frozenYesterday,
        routineDays = routineDays,
        earnedYesterday = earnedYesterday.distinct(),
        freezeEarnedYesterday = freezeEarnedYesterday,
        nextToken = nextToken,
        openToday = openToday,
        keptToday = keptToday,
    )
}
