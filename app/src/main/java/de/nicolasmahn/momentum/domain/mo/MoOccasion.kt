package de.nicolasmahn.momentum.domain.mo

import de.nicolasmahn.momentum.domain.TrackerRef
import de.nicolasmahn.momentum.domain.reminder.Bedtime
import de.nicolasmahn.momentum.domain.reminder.BedtimePhase
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.Month

/**
 * What is going on in Mo's life right now, which decides what kind of thing he is up to. There is
 * always exactly one, and it is a pure function of the records and the clock, so every surface shows
 * the same Mo and nothing about it is stored.
 */
enum class MoOccasion {
    /** Something is overdue and he still burns. */
    Angry,

    /** A freeze paid for yesterday and nothing has thawed him yet. */
    Frozen,

    /** He is a Husk. */
    Out,

    /** He is Cold. */
    Cold,

    /** Late in the evening, something asked for still open, and no freeze to save the streak. */
    Scared,

    /** A food-themed task was asked for and is still open. */
    Hungry,

    /** A book-themed task was asked for and is still open. */
    Dumb,

    /** Activity was asked for and is still open. */
    Lazy,

    /** A teeth-themed task was asked for and is still open. */
    Stinky,

    /** A pill-themed task was asked for and is still open. */
    Sickly,

    /** A scale-themed task was asked for and is still open. */
    Unweighed,

    /** Everything due today is done. */
    Happy,

    /** Something has been kept without a gap, or the streak reached a round number. */
    Proud,

    /** It is time to get ready for bed, or past it: then he is in bed, because the person should be too. */
    Sleepy,
    Morning,
    Weekend,
    Spooky,
    Festive,
    NewYear,

    /** An ordinary moment with nothing to say about it. */
    Idle,

    /**
     * A task was logged a moment ago and for a few seconds he does exactly that. The clock never
     * draws it: the screen that saw the save plays it, and then the day's occasion returns.
     */
    Doing,
}

/**
 * How strongly he feels it. Most of his life is Mild: a raised brow, a tapping foot, a pleased hum.
 * The extremes are kept rare on purpose, so a tantrum or a triumph is an event and not wallpaper.
 */
enum class MoIntensity { Mild, Medium, Strong }

/** What is going on and how strongly he feels it. A null intensity means the occasion has no degrees. */
data class MoFeeling(val occasion: MoOccasion, val intensity: MoIntensity?)

/**
 * The occasion of [moOccasion], with its intensity. He starts small and escalates: a task that was
 * just asked for makes him a little hungry or dim, and more so as it waits; once it is overdue he
 * taps his foot; several
 * tasks, many hours, or a habit of neglect bring out the tantrum. Joy climbs the same way, from a
 * pleased hum on an ordinary finished day to a triumph on a day the streak reaches a round number.
 */
fun moFeeling(mood: MoMood, asked: TrackerAsked, now: LocalDateTime, bedtime: Bedtime): MoFeeling {
    val occasion = moOccasion(mood, asked, now, bedtime)
    val overdueBy = asked.overdueBy
    val intensity = when (occasion) {
        MoOccasion.Angry -> when {
            overdueBy.size >= 3 || overdueBy.values.max() >= FURIOUS_AFTER_MINUTES || overdueBy.keys.any { it in mood.trackerNeglect } ->
                MoIntensity.Strong
            overdueBy.size == 1 && overdueBy.values.max() < IMPATIENT_UNTIL_MINUTES -> MoIntensity.Mild
            else -> MoIntensity.Medium
        }
        MoOccasion.Hungry, MoOccasion.Dumb, MoOccasion.Lazy, MoOccasion.Stinky, MoOccasion.Sickly, MoOccasion.Unweighed -> {
            // The tasks that put this look into the pool: only one he was asked for, so a view's
            // own neglect never colours a checklist that merely borrows its look. Two can share it
            // once a checklist borrows another view's; the one waited for longest sets the degree.
            val needs = asked.waited.keys.filter { mood.occasionOf(it) == occasion }
            // He makes more of a task the longer it waits, on the same clock as his anger, and a
            // task he has been let down on all week starts higher.
            val waiting = when (needs.maxOfOrNull { asked.waited.getValue(it).max() } ?: 0) {
                in 0 until IMPATIENT_UNTIL_MINUTES -> MoIntensity.Mild
                in IMPATIENT_UNTIL_MINUTES until FURIOUS_AFTER_MINUTES -> MoIntensity.Medium
                else -> MoIntensity.Strong
            }
            val neglected = when (needs.mapNotNull { mood.trackerNeglect[it] }.maxOrNull()) {
                null -> MoIntensity.Mild
                MoNeglect.Mild -> MoIntensity.Medium
                MoNeglect.Severe, MoNeglect.Dire -> MoIntensity.Strong
            }
            maxOf(waiting, neglected)
        }
        MoOccasion.Scared -> when {
            now.hour >= 23 || mood.streak.days >= 30 -> MoIntensity.Strong
            now.hour >= 22 || mood.streak.days >= 7 -> MoIntensity.Medium
            else -> MoIntensity.Mild
        }
        // Strong is in bed. Getting ready for bed starts as a yawn and ends half asleep on his feet.
        MoOccasion.Sleepy -> when {
            bedtime.phaseAt(now.toLocalTime()) == BedtimePhase.InBed -> MoIntensity.Strong
            bedtime.phaseAt(now.toLocalTime().plusMinutes(DROWSY_MINUTES_BEFORE_BED)) == BedtimePhase.InBed -> MoIntensity.Medium
            else -> MoIntensity.Mild
        }
        MoOccasion.Happy, MoOccasion.Proud -> when {
            mood.streak.days.isRoundStreak() -> MoIntensity.Strong
            mood.streak.days >= 7 || mood.thrivingTrackers.isNotEmpty() -> MoIntensity.Medium
            else -> MoIntensity.Mild
        }
        else -> null
    }
    return MoFeeling(occasion, intensity)
}

/** The streak lengths worth a fuss: the first week, then the numbers people actually brag about. */
fun Int.isRoundStreak(): Boolean = this in ROUND_STREAKS || (this > 365 && this % 100 == 0)

private val ROUND_STREAKS = setOf(7, 14, 21, 30, 50, 75, 100, 150, 200, 250, 300, 365)
private const val IMPATIENT_UNTIL_MINUTES = 120
private const val FURIOUS_AFTER_MINUTES = 300
private const val DROWSY_MINUTES_BEFORE_BED = 30L

/** The part of the day. He changes what he is up to with it, and not more often, so he never flickers. */
fun moDaySlot(hour: Int): Int = when (hour) {
    in 0..5 -> 0
    in 6..10 -> 1
    in 11..14 -> 2
    in 15..18 -> 3
    else -> 4
}

/**
 * A freeze paid for yesterday, so today starts in ice. The first record of the day thaws him, which
 * keeps the rule that acting always brings visible relief. A sick day asks nothing, not even a thaw.
 * The block he is drawn in and the act that plays around it both go by this, so one never shows
 * without the other.
 */
val MoMood.isFrozen: Boolean
    get() = streak.frozenTrackersYesterday.isNotEmpty() && latestTrackerToday == null && !streak.sickToday

/**
 * Picks the occasion. His state and the bedtime can decide it outright; otherwise everything that
 * applies goes into a pool, weighted by how much it matters, and the day and the part of the day
 * draw one from it. The same moment always draws the same one.
 *
 * A task only enters the pool once its reminder has waited a moment, and each waiting part counts,
 * so an evening checkpoint does not act overdue after the morning checkpoint was done. Once a task
 * is overdue he is angry and nothing else: acting the task out, or anything carefree, would
 * contradict the check-in that fumes about it.
 *
 * Past the hour to be asleep he is in bed whatever is still open, because sleep matters more than a
 * late task. The one exception is a streak that dies tonight: that can still be saved in a minute,
 * and he goes to bed right after. Only a task already asked for counts: a part asked for late, like
 * a checkpoint before sleep, cannot be done any sooner and is nothing to fear. While getting ready for bed
 * he is sleepy once nothing he has asked for is still missing or overdue.
 */
fun moOccasion(mood: MoMood, asked: TrackerAsked, now: LocalDateTime, bedtime: Bedtime): MoOccasion {
    val phase = bedtime.phaseAt(now.toLocalTime())
    val streakDiesTonight =
        now.hour >= SCARED_FROM_HOUR && mood.missingTrackersToday.any { it in asked.waited } && mood.streak.freezes == 0 && mood.streak.days > 0
    when {
        // The ice comes first, whatever is inside it: it is drawn round every body, the Husk's
        // too, and only the acts in the ice are written to go around the block.
        mood.isFrozen -> return MoOccasion.Frozen
        mood.state == MoState.Husk -> return MoOccasion.Out
        mood.state == MoState.Cold -> return MoOccasion.Cold
        phase == BedtimePhase.InBed && !streakDiesTonight -> return MoOccasion.Sleepy
        streakDiesTonight -> return MoOccasion.Scared
        asked.overdueBy.isNotEmpty() -> return MoOccasion.Angry
        phase == BedtimePhase.WindingDown && mood.missingTrackersToday.none { it in asked.waited } -> return MoOccasion.Sleepy
    }

    val pool = mutableMapOf<MoOccasion, Int>()
    fun add(occasion: MoOccasion, weight: Int) = pool.merge(occasion, weight, Int::plus)

    // A task with nothing to act out (a Plain checklist) adds nothing of its own: it would only
    // put idling into the pool.
    fun addTask(need: TrackerRef, weight: Int) = mood.occasionOf(need).takeUnless { it == MoOccasion.Idle }?.let { add(it, weight) }

    asked.waited.forEach { (need, parts) -> addTask(need, parts.size * if (need in mood.missingTrackersToday) 4 else 1) }
    mood.trackerNeglect.keys.filter { it in asked.waited }.forEach { addTask(it, 3) }
    // A free day keeps the streak, but a task he asked for and still waits on is no reason for joy.
    if (mood.missingTrackersToday.isEmpty() && asked.waited.isEmpty() && mood.keptTrackersToday.isNotEmpty()) {
        add(MoOccasion.Happy, 6)
        if (mood.thrivingTrackers.isNotEmpty() || mood.streak.days.isRoundStreak()) add(MoOccasion.Proud, 3)
    }
    if (now.hour in MORNING_HOURS) add(MoOccasion.Morning, 3)
    if (now.dayOfWeek == DayOfWeek.SATURDAY || now.dayOfWeek == DayOfWeek.SUNDAY) add(MoOccasion.Weekend, 2)
    when {
        now.month == Month.OCTOBER && now.dayOfMonth >= 25 -> add(MoOccasion.Spooky, 4)
        now.month == Month.DECEMBER && now.dayOfMonth <= 26 -> add(MoOccasion.Festive, 4)
        (now.month == Month.DECEMBER && now.dayOfMonth == 31) || (now.month == Month.JANUARY && now.dayOfMonth == 1) ->
            add(MoOccasion.NewYear, 6)
    }
    add(MoOccasion.Idle, 3)

    val total = pool.values.sum()
    var ticket = Math.floorMod(moHash(now.toLocalDate().toEpochDay() * DAY_SLOTS + moDaySlot(now.hour)), total)
    // Enum order keeps the draw stable whatever order the pool was filled in.
    for (occasion in MoOccasion.entries) {
        val weight = pool[occasion] ?: continue
        if (ticket < weight) return occasion
        ticket -= weight
    }
    return MoOccasion.Idle
}

/** A small, well-mixed hash, so neighbouring days do not draw neighbouring tickets. */
fun moHash(value: Long): Int {
    var x = value * -0x61c8864680b583ebL
    x = x xor (x ushr 29)
    x *= -0x4b47d0c5a5a3b1e9L
    x = x xor (x ushr 32)
    return x.toInt()
}

const val DAY_SLOTS = 5
private const val SCARED_FROM_HOUR = 21
private val MORNING_HOURS = 6..9

/** What he acts out while [need] waits, as the task is configured. */
private fun MoMood.occasionOf(need: TrackerRef): MoOccasion = contentKey(need).figure.occasion
