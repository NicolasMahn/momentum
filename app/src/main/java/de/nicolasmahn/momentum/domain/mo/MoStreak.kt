package de.nicolasmahn.momentum.domain.mo

import de.nicolasmahn.momentum.domain.TrackerRef
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters

/** What one day of the current week means for the streak. */
enum class MoDayMark {
    /** A finished day with something due and nothing missed, or today with every due task already kept. */
    Perfect,

    /** A finished day with a miss that a freeze paid for. */
    Frozen,

    /** An earned habit pass protected a real miss; the day does not add to the streak. */
    Protected,

    /** A finished day with a miss and no freeze to pay for it, so the streak restarted. */
    Missed,

    /** Today, while something due is still without its record. */
    Open,

    /** A day after today. */
    Upcoming,

    /** A finished day or today with no task due, such as the days before the first task was taken up. */
    NothingDue,

    /** A day declared sick, which asks nothing of any task and leaves the streak as it was. */
    Sick,
}

/** The shared reward walk's outcomes, keyed by the tracker that owns each bank. */
data class MoStreak(
    val days: Int,
    val freezes: Int,
    val freezeCapacity: Int,
    val week: List<MoDayMark>,
    val weekCanEarnFreeze: Boolean,
    val routineDaysUntilFreeze: Int = 28,
    val freezeEarnedYesterday: Boolean = false,
    val history: List<MoDayRecord> = emptyList(),
    val standings: Map<TrackerRef, MoStreakStanding> = emptyMap(),
    val frozenTrackersYesterday: List<TrackerRef> = emptyList(),
    val protectedTrackersYesterday: List<TrackerRef> = emptyList(),
    val earnedTrackersYesterday: List<TrackerRef> = emptyList(),
    val freeDaysLeftByTracker: Map<TrackerRef, Int> = emptyMap(),
) {
    val participantBanks: Map<TrackerRef, Int> get() = standings.mapValues { it.value.passes }
    val participantCapacities: Map<TrackerRef, Int> get() = standings.mapValues { it.value.passCapacity }
    val participantNextToken: Map<TrackerRef, MoTokenProgress> get() = standings.mapNotNull { (ref, standing) -> standing.nextToken?.let { ref to it } }.toMap()
}

/**
 * One joined view's standing today: its bank, how far its next token is, and whether today is
 * still open or already kept. Neither today: a day it asks nothing of, such as a sick one.
 */
data class MoStreakStanding(
    val passes: Int,
    val passCapacity: Int,
    val nextToken: MoTokenProgress?,
    val openToday: Boolean,
    val keptToday: Boolean,
)

/** Whether today is declared sick, so nothing at all is asked of it. */
val MoStreak.sickToday: Boolean
    get() = history.lastOrNull()?.mark == MoDayMark.Sick

/**
 * Progress toward a token, in the unit that earns it: days, or minutes for a time view. A goal
 * set per week also has [week], Monday first, so each day can be shown where it falls.
 */
data class MoTokenProgress(val done: Int, val goal: Int, val week: List<MoWeekDay> = emptyList())

/** How one day of this week went for one task. */
enum class MoWeekDay {
    Kept,

    /** Nothing was asked: a planned rest day from activity, or a holiday for the scale. */
    Rested,

    /** Declared sick, so nothing was asked and a record made anyway earns nothing. */
    Sick,
    Missed,

    /** Not done, but a token covered it, so the streak held. */
    Covered,

    /** Today, still without its record. */
    Open,
    Upcoming,
}

/** A settled game outcome; protection and rest never count as recorded completion. */
data class MoDayRecord(
    val date: java.time.LocalDate,
    val mark: MoDayMark,
    val coveredTrackers: Set<TrackerRef> = emptySet(),
    val restedTrackers: Set<TrackerRef> = emptySet(),
    /** On a missed day, the views that broke it: those their own bank could not cover first. */
    val missedTrackers: List<TrackerRef> = emptyList(),
)

/** One row of the freeze terms, for explaining them. */
data class MoFreezeTier(val fromStreakDays: Int, val capacity: Int)

/** A longer streak has more to lose, so it may bank more freezes. What a freeze costs never changes. */
private enum class FreezeTier(val minimumStreak: Int, val capacity: Int) {
    Starting(minimumStreak = 0, capacity = 3),
    Committed(minimumStreak = 30, capacity = 5),
    Veteran(minimumStreak = 100, capacity = 7),
    ;

    companion object {
        fun of(streakDays: Int): FreezeTier = entries.last { streakDays >= it.minimumStreak }
    }
}

/** The terms the streak walk applies, mildest first. */
val MoFreezeTiers: List<MoFreezeTier> = FreezeTier.entries.map { MoFreezeTier(it.minimumStreak, it.capacity) }

/**
 * Whether a week with this day in it can still have seven perfect days. A task stays due once it is
 * taken up, so a day that asked nothing means the week's Monday asked nothing either.
 */
private val MoDayMark.keepsWeekPerfect: Boolean
    get() = this == MoDayMark.Perfect || this == MoDayMark.Open || this == MoDayMark.Upcoming

/**
 * A freeze pays for one bad day, however many tasks it missed, and keeps the streak without growing
 * it. A bad day that finds the bank empty ends the streak. A free day is no miss, so it costs nothing
 * and the day still counts as perfect.
 *
 * A freeze is earned by the week the free days are dealt in, Monday to Sunday: a week whose seven
 * days were all perfect adds one to the bank as its Sunday ends, if the bank has room.
 */
internal fun MoLedger.streak(): MoStreak {
    var days = 0
    var freezes = 0
    var frozenTrackersLastDay = emptyList<TrackerRef>()
    // The week the walk is in, Monday first and as far as the walk has come.
    val week = mutableListOf<MoDayMark>()
    val history = mutableListOf<MoDayRecord>()

    // Starting on a Monday makes every week of the walk a whole one. The days before the first task
    // was taken up ask nothing and change nothing, except that their week cannot be a perfect one.
    val firstMonday = (firstDay ?: today).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    finishedDays(start = firstMonday).forEach { day ->
        val finished = on(day)
        val mark = when {
            finished.dueCount == 0 -> MoDayMark.NothingDue
            finished.isPerfect -> MoDayMark.Perfect
            freezes > 0 -> MoDayMark.Frozen
            else -> MoDayMark.Missed
        }
        when (mark) {
            MoDayMark.Perfect -> days += 1
            MoDayMark.Frozen -> freezes -= 1
            MoDayMark.Protected -> Unit
            MoDayMark.Missed -> days = 0
            // A day that asked nothing leaves the streak as it found it.
            else -> Unit
        }
        // The walk ends on yesterday, so what the last finished day leaves here is yesterday's.
        frozenTrackersLastDay = if (mark == MoDayMark.Frozen) finished.missedTrackers else emptyList()
        history += MoDayRecord(
            day, mark,
            coveredTrackers = if (mark == MoDayMark.Frozen) finished.missedTrackers.toSet() else emptySet(),
        )

        week += mark
        if (day.dayOfWeek == DayOfWeek.SUNDAY) {
            // The bank is measured against the streak this Sunday leaves behind, the one Monday shows.
            if (week.all { it == MoDayMark.Perfect } && freezes < FreezeTier.of(days).capacity) freezes += 1
            week.clear()
        }
    }

    // Today is not over, so it can only add to the streak, never cost anything. It adds early only
    // once every task has its record: whether a free day covers the rest is settled at the 04:00 day boundary.
    // Counting early is for the day count alone. A week closes when its Sunday is finished, so even
    // a complete Sunday waits for Monday to bring its freeze.
    val todaySoFar = on(today)
    val todayMark = when {
        todaySoFar.dueCount == 0 -> MoDayMark.NothingDue
        todaySoFar.isComplete -> MoDayMark.Perfect
        else -> MoDayMark.Open
    }
    if (todayMark == MoDayMark.Perfect) days += 1
    history += MoDayRecord(today, todayMark)
    week += todayMark
    week += List(DayOfWeek.SUNDAY.value - today.dayOfWeek.value) { MoDayMark.Upcoming }

    return MoStreak(
        days = days,
        freezes = freezes,
        freezeCapacity = FreezeTier.of(days).capacity,
        week = week.toList(),
        weekCanEarnFreeze = week.all { it.keepsWeekPerfect },
        history = history.toList(),
        frozenTrackersYesterday = frozenTrackersLastDay,
    )
}
