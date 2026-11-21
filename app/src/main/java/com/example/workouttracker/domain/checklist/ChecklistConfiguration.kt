package com.example.workouttracker.domain.checklist

import com.example.workouttracker.domain.mo.TrackerMoPolicy
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month

/**
 * One configured checklist: what it is called and how it looks, when it is checked, how much is
 * required, what it holds, and which day each day is about, as contract revision 11
 * (`docs/configurable-views.md`) writes it. [applyEdit] is how it changes.
 *
 * Labels are plain text shown exactly as written, never translated after they were saved. Every
 * entry with a `from` or `until` is dated, so a change never re-judges an earlier day; labels and
 * the look are not dated, so a rename shows on old days too.
 */
data class ChecklistConfiguration(
    val id: String,
    val label: String,
    val icon: ViewIcon,
    val hue: ViewHue,
    val checkpoints: List<Checkpoint>,
    val target: ChecklistTarget = ChecklistTarget.PerDay,
    /** Null while the checklist takes no part in rewards. */
    val rewards: ChecklistRewards? = null,
    val items: List<ChecklistItem>,
    /**
     * Which day a due day is about: the day itself, or the day before for a checklist ticked the
     * morning after ([recordDayFor]). A record belongs to the day it is about either way; this only
     * decides which record a due day asks for, so a change is dated.
     */
    val about: Dated<AboutDay> = Dated.always(AboutDay.Today),
    val mo: TrackerMoPolicy = TrackerMoPolicy(),
) {
    val motif: MoMotif? get() = mo.motif

    init {
        requireId(id)
        requireLabel(label)
        require(checkpoints.isNotEmpty()) { "Checklist $id needs a checkpoint." }
        require(checkpoints.distinctBy { it.id }.size == checkpoints.size) { "Checklist $id has two checkpoints with one ID." }
        items.forEach { item ->
            require(item.checkpoints.orEmpty().all { name -> checkpoints.any { it.id == name } }) {
                "Item ${item.id} of checklist $id names a checkpoint it does not have."
            }
            val itemTarget = item.target ?: target
            // A week item once a week could lower before fewer times needed more than once a week; what it stored still reads.
            require(item.whenAway.revisions.none { it.value == WhenAway.LowerTarget } || itemTarget is ChecklistTarget.PerWeek || itemTarget.canAskFewerTimes) {
                "Item ${item.id} of checklist $id is due once a week or less, so there is nothing to lower."
            }
            require(item.awayTimes == null || item.awayTimes < itemTarget.timesAWeek) {
                "Item ${item.id} of checklist $id asks ${item.awayTimes} times a week on holiday, no fewer than its ${itemTarget.timesAWeek}."
            }
            require(itemTarget.canBeExceptionsOnly || item.exceptionsOnly.revisions.none { it.value }) {
                "Item ${item.id} of checklist $id is due a number of times a week, so it logs its ticks."
            }
            // Only misses and fewer times on holiday exclude each other on the days the entry is in
            // use, not across its history: an old fewer-times revision followed by only misses
            // from a later day is valid, and so is a history the entry carried from before it began.
            require(!item.exceptionsOnly.everWith(item.whenAway, item.from, item.until) { onlyMisses, away -> onlyMisses && away == WhenAway.LowerTarget }) {
                "Item ${item.id} of checklist $id cannot log only misses and ask fewer times on holiday on one day."
            }
        }
        // An item's ID appears twice only when one entry ends before the next starts.
        items.groupBy { it.id }.values.forEach { entries ->
            entries.sortedBy { it.from ?: LocalDate.MIN }.zipWithNext().forEach { (earlier, later) ->
                require(earlier.until != null && later.from != null && earlier.until < later.from) {
                    "Item ${earlier.id} of checklist $id has overlapping entries."
                }
            }
        }
    }
}

/** A moment in the day when things are checked off. */
data class Checkpoint(
    val id: String,
    val at: CheckpointTime,
    /** Shown only when a checklist has several checkpoints. */
    val label: String? = null,
    /** Whether a notification goes out; it does only while something due here is open. */
    val remind: Boolean,
    val from: LocalDate? = null,
    val until: LocalDate? = null,
) {
    init {
        requireId(id)
        label?.let(::requireLabel)
        requireDated(from, until)
    }
}

/**
 * The ID of the one checkpoint of a view with a single moment of the day, a measurement or a
 * questions view a person made: the contract's name for it.
 */
const val DayCheckpointId = "day"

/** Whether this is such a view's one checkpoint: [DayCheckpointId], with no name and no dates of its own. */
internal val Checkpoint.isTheDayCheckpoint: Boolean get() = id == DayCheckpointId && label == null && from == null && until == null

/** The reminder such a view starts with when a person makes it: off, at 09:00. */
val StarterDayCheckpoint = Checkpoint(id = DayCheckpointId, at = CheckpointTime.Fixed(LocalTime.of(9, 0)), remind = false)

/**
 * How much is required. A checklist's target is the default for its items, and an item states its
 * own only where it differs.
 */
sealed interface ChecklistTarget {
    /** Due every day, at each of its checkpoints. */
    data object PerDay : ChecklistTarget

    /** Due only on these days of the week. */
    data class Weekdays(val days: Set<DayOfWeek>) : ChecklistTarget {
        init {
            require(days.isNotEmpty()) { "A weekday target needs a day." }
        }
    }

    /**
     * Due [times] a week, on any days: the item is offered every day, and the Monday-to-Sunday week
     * deals its 7 − [times] free days to the empty days in order. What a week spent away asks
     * instead is the item's [ChecklistItem.awayTimes].
     */
    data class PerWeek(val times: Int = 1) : ChecklistTarget {
        init {
            require(times in 1..DaysPerWeek) { "A week target asks for one to seven days, was $times." }
        }
    }
}

internal const val DaysPerWeek = 7

/** How many days a week an item with this target is usually due: every day, its chosen days, or its week's count. */
val ChecklistTarget.timesAWeek: Int
    get() = when (this) {
        ChecklistTarget.PerDay -> DaysPerWeek
        is ChecklistTarget.Weekdays -> days.size
        is ChecklistTarget.PerWeek -> times
    }

/**
 * Whether an item with this target can ask fewer times on holiday ([WhenAway.LowerTarget]): one due
 * once a week or less has nothing between still due and not due.
 */
val ChecklistTarget.canAskFewerTimes: Boolean get() = timesAWeek > 1

/**
 * Whether an item with this target can log only misses ([ChecklistItem.exceptionsOnly]): one due
 * every day or on chosen weekdays can be assumed done on each of them, while one due a number of
 * times a week is a sparse ask that its ticks have to meet, as flossing once a week is.
 */
val ChecklistTarget.canBeExceptionsOnly: Boolean get() = this !is ChecklistTarget.PerWeek

/**
 * What a week asks of an item usually due [times] of its [days] scheduled days, when [awayDays] of
 * them are away and a week spent entirely away asks [awayTimes]: ⌈times − (times − awayTimes) ·
 * awayDays / days⌉. Every day is a week item's day, so five times a week and twice away over seven
 * days is Activity's holiday table of 5, 5, 5, 4, 4, 3, 3, 2; a daily item's seven days and three
 * times away give 7, 7, 6, 6, 5, 5, 4, 3.
 */
fun timesAsked(times: Int, awayTimes: Int, awayDays: Int, days: Int): Int {
    require(awayDays in 0..days) { "A span of $days days has 0 to $days away days, was $awayDays." }
    return times - (times - awayTimes) * awayDays / days
}

/**
 * What this rule means for an item with [target]: fewer times is for an item due more than once a
 * week, so one that becomes due once a week or less goes back to due, dated like the change.
 */
fun WhenAway.forTarget(target: ChecklistTarget): WhenAway =
    if (this == WhenAway.LowerTarget && !target.canAskFewerTimes) WhenAway.Due else this

/**
 * What this rule, said once for a whole checklist, meant for an item with [target]: lowering reached
 * only an item due a number of times a week, and every other stayed due as ever. Tracker states
 * before version 16 said the rule once per checklist and are read so.
 */
fun WhenAway.fromChecklistWideRule(target: ChecklistTarget): WhenAway =
    if (this == WhenAway.LowerTarget && target !is ChecklistTarget.PerWeek) WhenAway.Due else this

data class ChecklistItem(
    val id: String,
    val label: String,
    /** A season: outside these months the item is hidden. Null for the whole year. */
    val months: Set<Month>? = null,
    /** The first day it counts. */
    val from: LocalDate? = null,
    /** The last day it counts; its earlier records still show. */
    val until: LocalDate? = null,
    /** The checkpoints it is due at, by ID; null for every checkpoint of its checklist. */
    val checkpoints: List<String>? = null,
    /** Its own target where it differs from its checklist's; null for the checklist's. */
    val target: ChecklistTarget? = null,
    /**
     * How many times a week on holiday asks while the item asks fewer times when away
     * ([WhenAway.LowerTarget]): in a week with away days among its scheduled days it asks
     * [timesAsked]. Null while no count was chosen, which keeps the item due as ever. The count
     * stays whichever rule the item has, so choosing fewer times again finds it; like the target,
     * a change starts a new dated entry.
     */
    val awayTimes: Int? = null,
    /**
     * Whether an away day leaves it due, excuses it, or lowers what it asks a week, which an item
     * due more than once a week can say; it judges days, so a change is dated. Pills travel, so an
     * item stays due unless it says otherwise.
     */
    val whenAway: Dated<WhenAway> = Dated.always(WhenAway.Due),
    /**
     * Whether the item logs only misses (the contract's `exceptionsOnly`): on a day it is due it is
     * kept unless marked missed there, so success is assumed and only the exception is recorded.
     * Off by default, offered to an item due every day or on chosen weekdays, and never beside
     * fewer times on holiday; it judges days, so a change is dated.
     */
    val exceptionsOnly: Dated<Boolean> = Dated.always(false),
) {
    init {
        requireId(id)
        requireLabel(label)
        require(awayTimes == null || awayTimes >= 0) { "Item $id asks $awayTimes times a week on holiday." }
        require(months == null || months.isNotEmpty()) { "Item $id has an empty season." }
        require(checkpoints == null || (checkpoints.isNotEmpty() && checkpoints.distinct().size == checkpoints.size)) {
            "Item $id names its checkpoints once each, or none for all of them."
        }
        requireDated(from, until)
    }
}

/**
 * One bank of passes, earned by complete days, and the [periods] the checklist takes part in
 * rewards: null for its whole history, and an empty list for no day,
 * which keeps the settings of a checklist that is out. A checklist joins on today's habit day and
 * leaves after yesterday, so a day judged once is never judged differently, and a day outside
 * every period is not part of the walk at all.
 *
 * The bank's size and the days a pass takes are dated: passes earned under an earlier rule stay
 * earned, the bank carries over, and a smaller bank holds no more than it allows from its first day.
 */
data class ChecklistRewards(
    val maxPasses: Dated<Int>,
    val everyKeptDays: Dated<Int>,
    val periods: List<RewardPeriod>? = null,
) {
    constructor(maxPasses: Int, everyKeptDays: Int, periods: List<RewardPeriod>? = null) :
        this(Dated.always(maxPasses), Dated.always(everyKeptDays), periods)

    init {
        require(maxPasses.revisions.all { it.value >= 1 } && everyKeptDays.revisions.all { it.value >= 1 }) {
            "Rewards need a bank and a way to fill it."
        }
        periods?.requireInOrder()
    }

    fun takesPartOn(date: LocalDate): Boolean = periods.include(date)
}

/** The days from [from] to [until], or on while [until] is null. */
data class RewardPeriod(val from: LocalDate, val until: LocalDate? = null) {
    init {
        requireDated(from, until)
    }
}

/*
 * The periods a bank takes part in, whichever bank it is: a checklist's, which every so many kept
 * days fill, or a time view's, which its weekly goal fills. Null stands for a whole history.
 */

/** Whether [date] is in one of these periods; a whole history holds every day. */
internal fun List<RewardPeriod>?.include(date: LocalDate): Boolean = this == null || any { isWithin(it.from, it.until, date) }

/** Refuses periods that overlap or are out of order: every one but the last is closed, and ends before the next begins. */
internal fun List<RewardPeriod>.requireInOrder() = zipWithNext().forEach { (earlier, later) ->
    require(earlier.until != null && earlier.until < later.from) { "Reward periods overlap or are out of order." }
}

/**
 * These periods with one more from [today]. Joining again the day after a period closed reopens it
 * instead, which takes part on exactly the same days, so a leave taken back leaves nothing.
 */
internal fun List<RewardPeriod>.joinedFrom(today: LocalDate): List<RewardPeriod> {
    val last = lastOrNull()
    return if (last?.until == today.minusDays(1)) dropLast(1) + last.copy(until = null) else this + RewardPeriod(today)
}

/** These periods taking part from [today]: as they are while they do already, and otherwise with one more ([joinedFrom]). */
internal fun List<RewardPeriod>.takingPartFrom(today: LocalDate): List<RewardPeriod> = if (include(today)) this else joinedFrom(today)

/**
 * These periods out from [today]: the open one ends yesterday, or goes if it only began today, and
 * every closed one stays, so the days they held keep their judgments.
 */
internal fun List<RewardPeriod>.endingBefore(today: LocalDate): List<RewardPeriod> {
    val open = lastOrNull()?.takeIf { it.until == null } ?: return this
    return if (open.from >= today) dropLast(1) else dropLast(1) + open.copy(until = today.minusDays(1))
}

/** Whether a day before [today] is in one of these periods; a whole history reaches before every day. */
internal fun List<RewardPeriod>?.reachBefore(today: LocalDate): Boolean = this?.any { it.from < today } ?: true

/**
 * These periods out from [today], for every bank there is: a whole history, which took part since
 * [wholeHistoryFrom] or on no day yet while that is unknown, becomes its one period, and the open
 * period ends yesterday ([endingBefore]). The rules stay for when the bank joins again.
 */
internal fun List<RewardPeriod>?.leavingOn(today: LocalDate, wholeHistoryFrom: LocalDate?): List<RewardPeriod> =
    (this ?: listOf(RewardPeriod(wholeHistoryFrom ?: today))).endingBefore(today)

/** These periods taking part from [today], for every bank there is: a whole history already does, and any other takes part from today ([takingPartFrom]). */
internal fun List<RewardPeriod>?.joiningOn(today: LocalDate): List<RewardPeriod>? = this?.takingPartFrom(today)

internal fun isWithin(from: LocalDate?, until: LocalDate?, date: LocalDate): Boolean =
    (from == null || date >= from) && (until == null || date <= until)

/** IDs are never shown, and a tick joins two of them with a slash. */
internal fun requireId(id: String) =
    require(id.isNotEmpty() && id.none { it.isWhitespace() || it == TickSeparator }) { "Invalid ID: '$id'." }

internal fun requireLabel(label: String) =
    require(label.isNotBlank() && label == label.trim()) { "Invalid label: '$label'." }

private fun requireDated(from: LocalDate?, until: LocalDate?) =
    require(from == null || until == null || from <= until) { "An entry ends before it starts: $from to $until." }
