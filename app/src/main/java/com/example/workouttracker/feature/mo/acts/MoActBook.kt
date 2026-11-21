package com.example.workouttracker.feature.mo.acts

import com.example.workouttracker.domain.mo.DAY_SLOTS
import com.example.workouttracker.domain.mo.TrackerAsked
import com.example.workouttracker.domain.mo.MoIntensity
import com.example.workouttracker.domain.mo.MoMood
import com.example.workouttracker.domain.mo.MoOccasion
import com.example.workouttracker.domain.mo.MoSaveAct
import com.example.workouttracker.domain.mo.MoState
import com.example.workouttracker.domain.mo.moDaySlot
import com.example.workouttracker.domain.mo.moHash
import com.example.workouttracker.domain.mo.moFeeling
import com.example.workouttracker.domain.reminder.Bedtime
import com.example.workouttracker.feature.mo.MoBodyKind
import java.time.LocalDateTime
import kotlin.math.abs

/** Every act there is. A file of acts adds its list here, and that is all it takes to release them. */
internal val MoActBook: List<MoAct> = listOf(
    coreActs,
    doingActs1,
    doingActs2,
    doingActs3,
    tokenActs,
    angryActs1,
    angryActs2,
    angryActs3,
    angryActs4,
    happyActs1,
    happyActs2,
    happyActs3,
    happyActs4,
    hungryActs1,
    hungryActs2,
    hungryActs3,
    dumbActs1,
    dumbActs2,
    dumbActs3,
    lazyActs1,
    lazyActs2,
    lazyActs3,
    stinkyActs1,
    stinkyActs2,
    stinkyActs3,
    sicklyActs1,
    sicklyActs2,
    unweighedActs1,
    unweighedActs2,
    scaredActs1,
    scaredActs2,
    scaredActs3,
    sleepyActs1,
    sleepyActs2,
    morningActs1,
    morningActs2,
    proudActs1,
    proudActs2,
    idleActs1,
    idleActs2,
    idleActs3,
    idleActs4,
    weekendActs1,
    weekendActs2,
    festiveActs2,
    newYearActs1,
    coldActs1,
    coldActs2,
    frozenActs1,
    frozenActs2,
    outActs1,
    outActs2,
    spookyActs1,
    festiveActs1,
    sleepyBedActs1,
    sleepyBedActs2,
).flatten()

internal fun MoState.bodyKind(): MoBodyKind = when (this) {
    MoState.Blazing, MoState.Steady -> MoBodyKind.Tall
    MoState.Dimming -> MoBodyKind.Low
    MoState.Cold -> MoBodyKind.Droplet
    MoState.Husk -> MoBodyKind.Husk
}

/**
 * What Mo is up to right now. It changes with the day and the part of the day and with nothing else,
 * so the app, the widget and a notification posted in the same hour all show the same scene.
 * [asked] is what his reminders have asked for and how late it is, and [bedtime] is the night
 * ahead, which sends him to bed.
 */
internal fun moScene(
    mood: MoMood,
    asked: TrackerAsked,
    now: LocalDateTime,
    bedtime: Bedtime,
    book: List<MoAct> = MoActBook,
): MoScene? {
    val turn = now.toLocalDate().toEpochDay() * DAY_SLOTS + moDaySlot(now.hour)
    val feeling = moFeeling(mood, asked, now, bedtime)
    val act = pickMoAct(book, feeling.occasion, mood.state.bodyKind(), turn, feeling.intensity) ?: return null
    return MoScene(act, MoVariant(moHash(turn * 131 + act.id.hashCode())))
}

/**
 * What he acts out after a save. Consecutive turns go through the activity's acts one by one; Home's
 * turn is the day, so a task kept every day is acted out differently from one day to the next.
 */
internal fun moDoingScene(saveAct: MoSaveAct, body: MoBodyKind, turn: Int, book: List<MoAct> = MoActBook): MoScene? {
    val fitting = book.doing(saveAct, body)
    if (fitting.isEmpty()) return null
    return MoScene(fitting[Math.floorMod(turn, fitting.size)], MoVariant(moHash(turn.toLong())))
}

/**
 * What he does for as long as a timer runs, or wherever an activity is shown with nobody having
 * chosen an act: the one written to be watched that long, and failing that the first there is.
 */
internal fun moSessionScene(saveAct: MoSaveAct, body: MoBodyKind, book: List<MoAct> = MoActBook): MoScene? {
    val fitting = book.doing(saveAct, body)
    val act = fitting.firstOrNull { it.forASession } ?: fitting.firstOrNull() ?: return null
    return MoScene(act, MoVariant(moHash(0)))
}

private fun List<MoAct>.doing(saveAct: MoSaveAct, body: MoBodyKind): List<MoAct> =
    filter { it.occasion == MoOccasion.Doing && it.saveAct == saveAct && body in it.bodies }.sortedBy { it.id }

/**
 * The act for [turn], a count of day parts. Acts of an occasion come round in a shuffled order that
 * is reshuffled every time the whole set has been seen, so none repeats before all have had a turn.
 * When the feeling has an [intensity], only acts of that intensity play, or of the nearest one the
 * occasion has. An occasion without an act for this body falls back to an idle one.
 */
internal fun pickMoAct(
    book: List<MoAct>,
    occasion: MoOccasion,
    body: MoBodyKind,
    turn: Long,
    intensity: MoIntensity? = null,
): MoAct? {
    val fitting = book.filter { body in it.bodies }
    val ofOccasion = fitting.filter { it.occasion == occasion }
    val pool = when {
        ofOccasion.isEmpty() -> fitting.filter { it.occasion == MoOccasion.Idle }
        intensity == null -> ofOccasion
        else -> {
            val nearest = ofOccasion.minOf { abs(it.intensity.ordinal - intensity.ordinal) }
            ofOccasion.filter { abs(it.intensity.ordinal - intensity.ordinal) == nearest }
        }
    }.sortedBy { it.id }
    if (pool.isEmpty()) return null
    val round = Math.floorDiv(turn, pool.size.toLong())
    val shuffled = pool.sortedBy { moHash(round * 1_000_003 + it.id.hashCode()) }
    return shuffled[Math.floorMod(turn, pool.size.toLong()).toInt()]
}
