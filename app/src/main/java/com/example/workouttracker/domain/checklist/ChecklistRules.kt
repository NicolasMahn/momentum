package com.example.workouttracker.domain.checklist

import com.example.workouttracker.domain.holiday.DayOff
import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.domain.reminder.Bedtime
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/*
 * The functions between the layers of `docs/configurable-views.md`: rules come from the
 * configuration alone, progress adds the records, obligations add the time of day. Reminders and Mo
 * read obligations; completion and statistics read progress. None of them writes anything.
 */

/**
 * What a checklist asks on one date by its configuration alone: each checkpoint that applies there
 * with the daily items due at it, and the week items in use that day. A week item is offered at its
 * checkpoints every day and due only once its week's free days are used up, which only the week's
 * records can say, so it is never among [checkpoints] here; [progressOn] places it, and also leaves
 * out what the days off excuse, which no configuration knows.
 */
data class ChecklistRules(
    val date: LocalDate,
    val checkpoints: List<CheckpointRules>,
    val weekItems: List<WeekItemRules> = emptyList(),
)

/** A checkpoint with nothing due on a date is left out of that date's rules. */
data class CheckpointRules(val checkpoint: Checkpoint, val items: List<ChecklistItem>)

/**
 * An item judged over several days rather than on one date alone, with the checkpoints it can be
 * ticked at there; a tick at any of them counts for the day. A week item is one every day of its
 * week; an item that asks fewer times on holiday is one on an away day, which [dueOn] reads.
 */
data class WeekItemRules(val item: ChecklistItem, val target: ChecklistTarget, val checkpoints: List<Checkpoint>)

/** Which entries apply on [date]. A dated entry never reaches a day outside its dates. */
fun ChecklistConfiguration.rulesOn(date: LocalDate): ChecklistRules =
    checkpointsWith(date, items.filter { it.isDueOn(date, it.target ?: target) })

/**
 * What is due on [date] once the days off are read as well: an item excused while away is left
 * out, so a calendar and the progress agree on it, and a daily item that asks fewer times on
 * holiday leaves the away day's daily items for its week items. Both are still placed by [progressOn].
 */
fun ChecklistConfiguration.dueOn(date: LocalDate, daysOff: HolidaySchedule): ChecklistRules {
    val rules = checkpointsWith(date, items.filter { it.isDueOn(date, it.target ?: target) && !it.isExcusedOn(date, daysOff) && !it.isLoweredOn(date, daysOff) })
    return rules.copy(weekItems = rules.weekItems + items.mapNotNull { loweredItemOn(it, date, daysOff) })
}

/**
 * What can be ticked on [date]: every item within its dates and season, at its checkpoints, due or
 * not. The target only decides what is due, so an item ticked on a day it is not due is recorded
 * and shown, and counts for nothing.
 */
fun ChecklistConfiguration.recordableOn(date: LocalDate): ChecklistRules =
    checkpointsWith(date, items.filter { it.isRecordableOn(date) })

/** The checkpoints in use on [date], whatever is due at them. */
fun ChecklistConfiguration.checkpointsOn(date: LocalDate): List<Checkpoint> = checkpoints.filter { isWithin(it.from, it.until, date) }

/**
 * The first day something could be ticked: the earliest day an item and a checkpoint it is due at
 * are both in use, by their dates; a season hides an item but does not start it. Null for a
 * checklist that always had something to tick, or that never has. No day
 * before it was ever asked for anything.
 */
val ChecklistConfiguration.firstDay: LocalDate?
    get() = checkpoints
        .flatMap { checkpoint -> items.filter { it.isAt(checkpoint) }.map { item -> item.togetherWith(checkpoint) } }
        .filter { (start, end) -> start == null || end == null || start <= end }
        .map { (start, _) -> start }
        .let { starts -> if (starts.isEmpty() || null in starts) null else starts.filterNotNull().min() }

/** The days this item can be ticked at [checkpoint], from both being in use to either ending; null for from the beginning or for ever. */
private fun ChecklistItem.togetherWith(checkpoint: Checkpoint): Pair<LocalDate?, LocalDate?> =
    listOfNotNull(from, checkpoint.from).maxOrNull() to listOfNotNull(until, checkpoint.until).minOrNull()

/**
 * The first day an item of this checklist is due while it logs only misses, by the rules of that
 * day ([rulesOn]: its entry, season, days and checkpoints), or null while there is none. From then
 * on a day can be kept with nothing recorded, so a task that starts with its first record starts
 * here instead when this comes first ([dueFrom]). A setting switched on while the item was never
 * due, as one turned off again before its weekday came, names no day; nor does one with no start to
 * its entry or to the setting.
 */
val ChecklistConfiguration.firstExceptionsOnlyDay: LocalDate?
    get() = firstExceptionsOnlyDayMatching { true }

internal fun ChecklistConfiguration.firstExceptionsOnlyDayMatching(accepts: (LocalDate) -> Boolean): LocalDate? =
    items.flatMap { it.exceptionsOnlySpans() }
        .mapNotNull { (start, end) ->
            // Within a year every season and weekday has come round, so a span with no due day by
            // then has none.
            generateSequence(start) { it.plusDays(1) }
                .take(DaysInAYear)
                .takeWhile { end == null || it <= end }
                .firstOrNull { day -> rulesOn(day).exceptionsOnlyTicks.isNotEmpty() && accepts(day) }
        }
        .minOrNull()

/** The days, from a start to an end or for ever, on which this entry's setting says only misses. */
private fun ChecklistItem.exceptionsOnlySpans(): List<Pair<LocalDate, LocalDate?>> {
    val revisions = exceptionsOnly.revisions
    return revisions.withIndex().mapNotNull { (index, revision) ->
        if (!revision.value) return@mapNotNull null
        val start = listOfNotNull(from, revision.from).maxOrNull() ?: return@mapNotNull null
        val end = listOfNotNull(until, revisions.getOrNull(index + 1)?.from?.minusDays(1)).minOrNull()
        (start to end).takeIf { end == null || start <= end }
    }
}

private const val DaysInAYear = 366

/**
 * The day a task on this checklist is due from, where nothing dates its start: its first record,
 * as a built-in feature has always started, or the first day an item logs only misses, whichever
 * comes first. Null while it has neither, so a feature not taken up yet is never held against anyone.
 */
fun ChecklistConfiguration.dueFrom(firstRecordedDay: LocalDate?): LocalDate? =
    listOfNotNull(firstRecordedDay, firstExceptionsOnlyDay).minOrNull()

private fun ChecklistConfiguration.checkpointsWith(date: LocalDate, items: List<ChecklistItem>) = ChecklistRules(
    date = date,
    checkpoints = checkpointsOn(date).mapNotNull { checkpoint ->
        items.filter { it.isAt(checkpoint) }
            .takeIf { it.isNotEmpty() }
            ?.let { CheckpointRules(checkpoint, it) }
    },
    weekItems = this.items.mapNotNull { weekItemOn(it, date) },
)

/** [item] as a week item on [date]: in use there with a week target and a checkpoint to tick it at, or null. */
private fun ChecklistConfiguration.weekItemOn(item: ChecklistItem, date: LocalDate): WeekItemRules? {
    val target = (item.target ?: target) as? ChecklistTarget.PerWeek ?: return null
    if (!item.isRecordableOn(date)) return null
    return spanItemOn(item, target, date)
}

/**
 * [item] on an away day it asks fewer times on: a daily or some-days item scheduled there, with a
 * checkpoint to tick it at, or null. A week item lowers through its whole week instead.
 */
private fun ChecklistConfiguration.loweredItemOn(item: ChecklistItem, date: LocalDate, daysOff: HolidaySchedule): WeekItemRules? {
    val target = item.target ?: target
    if (target is ChecklistTarget.PerWeek || !item.isDueOn(date, target) || !item.isLoweredOn(date, daysOff)) return null
    return spanItemOn(item, target, date)
}

/** [item] with the checkpoints it can be ticked at on [date], or null while it has none there. */
private fun ChecklistConfiguration.spanItemOn(item: ChecklistItem, target: ChecklistTarget, date: LocalDate): WeekItemRules? =
    checkpointsOn(date).filter { item.isAt(it) }.takeIf { it.isNotEmpty() }?.let { WeekItemRules(item, target, it) }

/** A tick records one item at one checkpoint, such as `midday/creatine`; a miss has the same form. */
fun tickOf(checkpointId: String, itemId: String): String = "$checkpointId$TickSeparator$itemId"

internal const val TickSeparator = '/'

/**
 * What the day's record says against its rules. Facts only: a tick or a miss that is not due, or
 * names an item the configuration does not know, stays in [recorded] and counts for nothing.
 *
 * Two answers are given where a day has one question: what is still *owed* ([CheckpointProgress.open]),
 * which can still be done and which reminders and Mo's lateness read, and what is *unmet*
 * ([CheckpointProgress.unmet]), owed or missed, which completion and the gaps read. An item that
 * logs only misses is kept at a checkpoint unless marked missed there: never owed, and unmet only
 * by its miss.
 *
 * A week item is due at its checkpoints like a daily item on a day it counts: one of the first
 * days it was ticked on, up to what the week asks, or one that would otherwise be a gap. On a free
 * day, or a day ticked beyond what the week asks, it is not due, so a checkpoint with nothing else
 * due there is left out.
 */
data class ChecklistProgress(
    val date: LocalDate,
    val checkpoints: List<CheckpointProgress>,
    val weekItems: List<WeekItemProgress>,
    val recorded: DayRecord,
) {
    /** Nothing due is unmet: every item ticked or assumed, none owed or missed. A day with nothing due is complete as well. */
    val isComplete: Boolean get() = checkpoints.all { it.unmet.isEmpty() }

    /** Whether anything can still be done: an unticked item that ticks, or a week item past its free days. A miss is settled, never owed. */
    val isOwing: Boolean get() = checkpoints.any { it.open.isNotEmpty() }

    /** What is still owed at [checkpointId], in the checklist's order; see [isOwing]. */
    fun openAt(checkpointId: String): List<ChecklistItem> = checkpoints.filter { it.checkpoint.id == checkpointId }.flatMap { it.open }

    fun owesAt(checkpointId: String): Boolean = openAt(checkpointId).isNotEmpty()

    /**
     * Kept by assumption alone: something is due, nothing is unmet, and nothing due was ticked, so
     * only items that log only misses keep it. Such a day is kept, never done: nothing celebrates
     * it, and nothing counts it as recorded. A tick of something not due changes nothing here.
     */
    val isAssumed: Boolean
        get() = checkpoints.isNotEmpty() && isComplete &&
            checkpoints.none { checkpoint -> checkpoint.due.any { tickOf(checkpoint.checkpoint.id, it.id) in recorded.ticks } }

    /**
     * Nothing is due: no daily item, and no week item that counts today, as on an away day every
     * item is excused on. Such a day is not judged: neither kept nor a gap.
     */
    val asksNothing: Boolean get() = checkpoints.isEmpty()

    /**
     * Each checkpoint with an unmet daily item is one gap, however many are owed or missed there,
     * and each week item past its free days one more, at however many checkpoints it is open.
     */
    val gaps: Int
        get() {
            val weekGaps = weekItems.filter { it.isGap }.mapTo(HashSet()) { it.item.id }
            return checkpoints.count { checkpoint -> checkpoint.unmet.any { it.id !in weekGaps } } + weekGaps.size
        }

    /** Each `checkpointId/itemId` still owed: what a tick would answer, never a miss. */
    val owedTicks: Set<String> get() = checkpoints.flatMapTo(LinkedHashSet()) { checkpoint -> checkpoint.open.map { tickOf(checkpoint.checkpoint.id, it.id) } }

    /** The ticks asked for today, each `checkpointId/itemId`; an item that logs only misses asks for none. */
    val dueTicks: Set<String> get() = checkpoints.flatMapTo(LinkedHashSet()) { it.dueTicks }

    fun dueTicksAt(checkpointId: String): Set<String> =
        checkpoints.filter { it.checkpoint.id == checkpointId }.flatMapTo(LinkedHashSet()) { it.dueTicks }

    /**
     * Each `checkpointId/itemId` due today of an item that logs only misses on this day: kept
     * unless marked missed there, which is the one thing that can be recorded for it.
     */
    val exceptionsOnly: Set<String> get() = checkpoints.flatMapTo(LinkedHashSet()) { it.exceptionsOnlyTicks }
}

data class CheckpointProgress(
    val checkpoint: Checkpoint,
    val due: List<ChecklistItem>,
    /** Owed: an unticked item that ticks, or a week item past its free days. What a reminder names and Mo waits for. */
    val open: List<ChecklistItem>,
    /** Items that log only misses today and were marked missed here: settled, never owed, and unmet. */
    val missed: List<ChecklistItem>,
    /** Items that log only misses today and are due here: kept unless among [missed], so no tick is asked of them. */
    val exceptionsOnly: List<ChecklistItem>,
) {
    /** Owed or missed, in the checklist's order: what keeps the day from being complete. */
    val unmet: List<ChecklistItem> get() = due.filter { it in open || it in missed }

    val dueTicks: List<String> get() = due.filter { it !in exceptionsOnly }.map { tickOf(checkpoint.id, it.id) }

    val exceptionsOnlyTicks: List<String> get() = exceptionsOnly.map { tickOf(checkpoint.id, it.id) }
}

/** How a week item stands on one day of its week. */
enum class WeekStanding {
    /** Ticked that day, at any of its checkpoints, as one of the days the week asked for. */
    Done,

    /**
     * Ticked beyond what the week asks: recorded and shown, but nothing was asked, so like a free
     * day it is neither judged nor earns anything. A once-a-week item ticked every day keeps one day.
     */
    Extra,

    /** Not ticked, and one of the week's free days covers it. */
    Free,

    /** Not ticked, with no free day left: one gap, unless it is ticked after all. */
    Gap,
}

/**
 * A week item's standing on its date, and how its week goes: [done] days ticked through the date,
 * extra ones included, against the [asked] days of the week, which are its target less the days
 * that are not the week's. For an item that asks fewer times on holiday the week is its away days
 * alone ([isWeekItem] false): the ordinary days are judged on their own, as for any daily item.
 */
data class WeekItemProgress(
    val item: ChecklistItem,
    val target: ChecklistTarget,
    /** Null on a day that is not one of its week's: sick, away while excused, or outside the entry. */
    val standing: WeekStanding?,
    val done: Int,
    val asked: Int,
) {
    val isFree: Boolean get() = standing == WeekStanding.Free
    val isGap: Boolean get() = standing == WeekStanding.Gap

    /** Whether it is due today: one of the asked days, done or a gap until it is. On a free or an extra day it is not. */
    val countsToday: Boolean get() = standing == WeekStanding.Done || isGap

    /** Whether the week is a week item's, every day of it, or the away days of a daily or some-days item on holiday. */
    val isWeekItem: Boolean get() = target is ChecklistTarget.PerWeek
}

/**
 * The progress of [date], read from the day's record in [records] and, for each week item, those
 * of its whole week. [daysOff] says what each item's away rule makes of the day: an excused item
 * is not due on an away day, and for a week item such a day, like a sick one, is not its week's,
 * so it neither uses a free day nor makes a gap; while an item asks fewer times when away, the
 * week's away days lower what it asks, and for a daily or some-days item they alone are dealt as a
 * week. A week item is dealt by its ticks alone: a miss recorded for it judges nothing.
 */
fun ChecklistConfiguration.progressOn(date: LocalDate, records: ChecklistRecords, daysOff: HolidaySchedule): ChecklistProgress {
    val rules = dueOn(date, daysOff)
    val today = records.on(date)
    val weekItems = rules.weekItems.map { it to weekProgress(it, date, records, daysOff) }
    val checkpoints = checkpointsOn(date).mapNotNull { checkpoint ->
        val daily = rules.checkpoints.firstOrNull { it.checkpoint.id == checkpoint.id }?.items.orEmpty()
        val week = weekItems.filter { (week, progress) -> progress.countsToday && checkpoint in week.checkpoints }
        val due = daily + week.map { (week) -> week.item }
        if (due.isEmpty()) return@mapNotNull null
        // An item that logs only misses today asks for no tick: it is kept unless marked missed
        // here, which settles it without its ever being owed. A miss on any other item is a
        // recorded fact that judges nothing, like a tick for an item no configuration knows.
        val (onlyMisses, ticking) = daily.partition { it.exceptionsOnly.on(date) }
        val open = ticking.filterNot { tickOf(checkpoint.id, it.id) in today.ticks } + week.filter { (_, progress) -> progress.isGap }.map { (week) -> week.item }
        val missed = onlyMisses.filter { tickOf(checkpoint.id, it.id) in today.misses }
        CheckpointProgress(checkpoint, inListOrder(due), inListOrder(open), inListOrder(missed), inListOrder(onlyMisses))
    }
    return ChecklistProgress(date, checkpoints, weekItems.map { (_, progress) -> progress }, today)
}

/**
 * How [week]'s item stands on [date], its week read Monday to Sunday. A week item's week is every
 * day of it, and asks its target, less by [timesAsked] while the item lowers when away; the week
 * of an item that asks fewer times on holiday is its away scheduled days alone, which together ask
 * what the lowered week asks beyond the ordinary days: ⌈N − (N − M) · A / N⌉ − (N − A). Either way
 * the week's away days are counted whatever each day's rule is, as Block 14 counts them.
 */
private fun ChecklistConfiguration.weekProgress(week: WeekItemRules, date: LocalDate, records: ChecklistRecords, daysOff: HolidaySchedule): WeekItemProgress {
    val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val days = (0L until DaysPerWeek).map(monday::plusDays)
    val item = week.item
    return when (val target = week.target) {
        is ChecklistTarget.PerWeek -> {
            val awayDays = days.count(daysOff::isHoliday)
            val dayOf = { day: LocalDate -> weekDayCheckpoints(item, day, daysOff) }
            val asksOn = { day: LocalDate -> timesAsked(target.times, item.awayTimes ?: target.times, if (item.whenAway.on(day) == WhenAway.LowerTarget) awayDays else 0, DaysPerWeek) }
            // Block 14's reading: the week keeps up to what it asks whatever its days, and shows its
            // target less the days that are not its.
            dealt(
                week, date, days, records, dayOf, asksOn,
                keepsOn = asksOn,
                size = DaysPerWeek,
                asked = (days.count { dayOf(it) != null } - (DaysPerWeek - asksOn(date))).coerceAtLeast(0),
            )
        }
        else -> {
            val usual = target.timesAWeek
            val isAwayDay = { day: LocalDate -> target.isScheduledOn(day) && daysOff.isHoliday(day) }
            val awayDays = days.count(isAwayDay)
            val asks = timesAsked(usual, requireNotNull(item.awayTimes) { "Item ${item.id} lowers without a count." }, awayDays, usual) - (usual - awayDays)
            val dayOf = { day: LocalDate -> if (item.isDueOn(day, target) && item.isLoweredOn(day, daysOff)) spanItemOn(item, target, day)?.checkpoints else null }
            // As of each day the allowance asks its count less the away days so far that are not
            // its: sick, or the item's as a daily item by that day's rule. No later day or later
            // edit reaches an earlier day, and a sick week's ticks beyond what it asks keep nothing.
            val keepsOn = { day: LocalDate -> (asks - days.count { it <= day && isAwayDay(it) && dayOf(it) == null }).coerceAtLeast(0) }
            dealt(week, date, days, records, dayOf, asksOn = { asks }, keepsOn = keepsOn, size = awayDays, asked = keepsOn(date))
        }
    }
}

/**
 * Deals a week's free days, its [size] less what it asks, to the item's empty days in order, Monday
 * first, and reads where [date] stands. [dayOf] names the checkpoints a day of the week can be
 * ticked at, and null for a day that is not the week's: it uses no free day and makes no gap, and a
 * tick on it counts for nothing, as nothing was asked, and the week asks one less for it. Of the
 * days ticked, the first as many as are kept are the ones asked; every tick after them is recorded
 * and asks nothing.
 *
 * Each day is judged by the item's away rule of its own day ([asksOn]), as every dated setting is:
 * a day already judged keeps the free day it was dealt, and from a change on the week's free days
 * are the new rule's, less those already used. So lowering the target midweek frees the days from
 * then on, and taking it back makes the next empty day a gap once the usual free days are spent.
 *
 * [keepsOn] says how many ticks are kept by a day, and [asked] what the week asks of its days as
 * shown on [date]; each caller reads them its own way, so a day's standing never depends on a day
 * after it unless that reading says so.
 */
private fun dealt(
    week: WeekItemRules,
    date: LocalDate,
    days: List<LocalDate>,
    records: ChecklistRecords,
    dayOf: (LocalDate) -> List<Checkpoint>?,
    asksOn: (LocalDate) -> Int,
    keepsOn: (LocalDate) -> Int,
    size: Int,
    asked: Int,
): WeekItemProgress {
    var freeDaysUsed = 0
    var kept = 0
    var done = 0
    var standing: WeekStanding? = null
    days.forEach { day ->
        val checkpoints = dayOf(day) ?: return@forEach
        if (day > date) return@forEach
        val today = when {
            checkpoints.any { tickOf(it.id, week.item.id) in records.on(day).ticks } -> if (kept < keepsOn(day)) WeekStanding.Done.also { kept++ } else WeekStanding.Extra
            freeDaysUsed < size - asksOn(day) -> WeekStanding.Free.also { freeDaysUsed++ }
            else -> WeekStanding.Gap
        }
        if (today == WeekStanding.Done || today == WeekStanding.Extra) done++
        if (day == date) standing = today
    }
    return WeekItemProgress(week.item, week.target, standing, done, asked)
}

/**
 * The checkpoints [item] can be ticked at on [day] while the day is one of its week's: the entry
 * is in use there, and the day is neither sick nor away while the item is excused.
 */
private fun ChecklistConfiguration.weekDayCheckpoints(item: ChecklistItem, day: LocalDate, daysOff: HolidaySchedule): List<Checkpoint>? =
    if (daysOff.dayOff(day) == DayOff.Sick || item.isExcusedOn(day, daysOff)) null else weekItemOn(item, day)?.checkpoints

/**
 * Whether [date] is away and this item is not due then. A sick day wins over an away day and asks
 * nothing of any view, yet its reminders still come, so a day that is both is not excused.
 */
fun ChecklistItem.isExcusedOn(date: LocalDate, daysOff: HolidaySchedule): Boolean =
    daysOff.dayOff(date) == DayOff.Away && whenAway.on(date) == WhenAway.Excused

/**
 * Whether [date] is away and this item asks fewer times then, with a count to ask; without one it
 * stays due as ever. A day that is both sick and away is a sick day, so the item is due and
 * reminded of there like any daily item, and the day is not one of its away days.
 */
fun ChecklistItem.isLoweredOn(date: LocalDate, daysOff: HolidaySchedule): Boolean =
    daysOff.dayOff(date) == DayOff.Away && whenAway.on(date) == WhenAway.LowerTarget && awayTimes != null

/** In the order the checklist lists its items, whatever their targets. */
private fun ChecklistConfiguration.inListOrder(items: List<ChecklistItem>): List<ChecklistItem> = items.sortedBy { this.items.indexOf(it) }

/**
 * The app-wide times obligations are read against: the [night] a relative checkpoint counts from,
 * after its wake time or before its sleep, and the check-in time a checkpoint without a reminder is
 * due at. Mo reads relative times at the fallback alarm time, because the phone's real alarm is not
 * part of the settings.
 */
data class Timing(val night: Bedtime, val checkIn: LocalTime)

/** Items still open at a checkpoint, owed since [since]. */
data class Obligation(
    val checkpoint: Checkpoint,
    val open: List<ChecklistItem>,
    val since: LocalTime,
)

/**
 * What is still owed and since when. A reminder decides whether to notify, Mo whether it is late.
 * A week item is owed only on a day skipping it would cost; on its free days it is offered without a nudge.
 */
fun ChecklistProgress.obligations(timing: Timing): List<Obligation> = checkpoints
    .filter { it.open.isNotEmpty() }
    .map { Obligation(it.checkpoint, it.open, it.checkpoint.dueTime(timing)) }

/**
 * When Mo starts counting: the checkpoint's own time while it reminds, and the check-in time while
 * it does not, as for every task today.
 */
fun Checkpoint.dueTime(timing: Timing): LocalTime = at.dueTime(remind, timing)

/** The same reminder/check-in rule also applies to views with no checklist items. */
fun CheckpointTime.dueTime(remind: Boolean, timing: Timing): LocalTime =
    if (remind) on(timing.night) else timing.checkIn

private fun ChecklistItem.isDueOn(date: LocalDate, target: ChecklistTarget): Boolean = isRecordableOn(date) && target.isScheduledOn(date)

/** Whether [date] is one of the target's days by the calendar alone, whatever the entry's dates. */
private fun ChecklistTarget.isScheduledOn(date: LocalDate): Boolean = when (this) {
    ChecklistTarget.PerDay -> true
    is ChecklistTarget.Weekdays -> date.dayOfWeek in days
    // Only its week's records say which days a week item is due on; see [progressOn].
    is ChecklistTarget.PerWeek -> false
}

/** Within its dates and, for a seasonal item, in its season: outside them an item is hidden. */
private fun ChecklistItem.isRecordableOn(date: LocalDate): Boolean =
    isWithin(from, until, date) && (months == null || date.month in months)

private fun ChecklistItem.isAt(checkpoint: Checkpoint): Boolean = checkpoints?.contains(checkpoint.id) ?: true
