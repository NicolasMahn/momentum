package de.nicolasmahn.momentum.domain.mo

import java.time.LocalDate

/**
 * A streak's level: the highest threshold its kept days reached. It is derived from the streak and
 * never stored, so a lost streak loses its level with it. Below the first threshold there is none.
 */
enum class MoLevel(val days: Int) {
    Spark(7),
    Flicker(14),
    Ember(30),
    Flame(60),
    Fire(100),
    Blaze(200),
    Inferno(365),
    ;

    /** The level after this one, or null at the top. */
    val next: MoLevel? get() = entries.getOrNull(ordinal + 1)

    companion object {
        /** The level a streak of [days] kept days has, or null below the first. */
        fun of(days: Int): MoLevel? = entries.lastOrNull { days >= it.days }

        /** Whether reaching [days] is reaching a level. */
        fun isThreshold(days: Int): Boolean = entries.any { it.days == days }
    }
}

/** A level reached in the run that began on [runStart]: the moment worth a screen, once per run. */
data class MoLevelArrival(val level: MoLevel, val reachedOn: LocalDate, val runStart: LocalDate) {
    /** What remembers that it was shown; a new run reaching the same level is a new arrival. */
    val seenKey: String get() = "$runStart/${level.name}"
}

/**
 * A streak that ended: [days] kept days, ended by the miss on [lostOn]. Only a run of
 * [StreakLossMinimum] days or more is one: losing a day or two is no moment to stop the app for.
 */
data class MoStreakLoss(val days: Int, val lostOn: LocalDate) {
    val seenKey: String get() = lostOn.toString()
}

/** The shortest run whose end is a loss worth a screen. */
const val StreakLossMinimum = 3

/** How long a loss is still news: a loss older than this is history, not a moment. */
const val StreakLossNewsDays = 7L

/**
 * The streak's runs, folded from the history exactly as the walk counts them: a perfect day adds
 * one, an unprotected miss ends the run, and every other day carries it unchanged.
 */
private class StreakFold(history: List<MoDayRecord>) {
    var count = 0
        private set
    var runStart: LocalDate? = null
        private set

    /** Each level the current run reached, with the day it reached it. */
    val arrivals = mutableListOf<MoLevelArrival>()

    /** The latest run that ended with [StreakLossMinimum] days or more. */
    var loss: MoStreakLoss? = null
        private set

    init {
        history.sortedBy { it.date }.forEach { day ->
            when (day.mark) {
                MoDayMark.Perfect -> {
                    if (count == 0) {
                        runStart = day.date
                        arrivals.clear()
                    }
                    count++
                    MoLevel.entries.firstOrNull { it.days == count }?.let { arrivals += MoLevelArrival(it, day.date, requireNotNull(runStart)) }
                }
                MoDayMark.Missed -> {
                    if (count >= StreakLossMinimum) loss = MoStreakLoss(count, day.date)
                    count = 0
                    runStart = null
                    arrivals.clear()
                }
                else -> Unit
            }
        }
    }
}

/**
 * The highest level the current run reached, with the day it got there, or null below the first.
 * [history] is the streak's, today included: a level reached today is an arrival today.
 */
fun levelArrival(history: List<MoDayRecord>): MoLevelArrival? = StreakFold(history).arrivals.lastOrNull()

/**
 * The streak most recently lost, while it is still news on [today]: lost within
 * [StreakLossNewsDays], and not already outgrown by the run since. Only finished days are read,
 * so today's open day never ends anything. Derived from the records every time, so filling in the
 * day that broke it, if that keeps the day, takes the loss away.
 */
fun streakLoss(history: List<MoDayRecord>, today: LocalDate): MoStreakLoss? {
    val finished = history.filter { it.date < today }
    val fold = StreakFold(finished)
    val loss = fold.loss ?: return null
    val runSince = StreakFold(history).count
    return loss.takeIf { it.lostOn >= today.minusDays(StreakLossNewsDays) && runSince < it.days }
}
