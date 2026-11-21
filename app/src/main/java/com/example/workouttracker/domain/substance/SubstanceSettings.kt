package com.example.workouttracker.domain.substance

import com.example.workouttracker.domain.mo.TrackerMoPolicy
import com.example.workouttracker.domain.checklist.ChecklistRewards
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.checklist.MoMotif
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.checklist.requireLabel
import java.time.LocalDate

/**
 * Which substances a person tracks, the ones they named themselves, and what Substances asks of
 * the day. Only a tracked substance is offered when logging and gets a stat; a record of any other
 * stays a fact and is never hidden. Nothing is tracked until the person chooses.
 *
 * Each tracked substance is one the person is staying away from, whose stat counts days without,
 * or one they are just tracking, whose stat only says when it was last logged. The [checkIn] asks
 * once a day whether there is anything to log, and [rewards] lets Substances count toward the
 * streak with the [goal] of the day: logging, where any record keeps the day, or staying away,
 * where a use of a substance in that day's stance is a gap. All of it is off until switched on.
 *
 * Three things judge days once Substances takes part, so each is dated and every earlier day keeps
 * the rule it had: the goal, [exceptionsOnly] and the [stayingAway] stance. Only today's stance
 * must be a subset of what is tracked today; an earlier revision may name a substance since
 * untracked, which is why one it names cannot be removed.
 *
 * The look judges no day, so it is not dated: the [label] a person gave the view, its [icon], its
 * [hue] and Mo's flavor ([mo]). Someone who tracks one thing calls the view by it.
 */
data class SubstanceSettings(
    val tracked: Set<String> = emptySet(),
    val own: List<OwnSubstance> = emptyList(),
    val stayingAway: Dated<Set<String>> = Dated.always(emptySet()),
    val checkIn: SubstanceCheckIn = SubstanceCheckIn(),
    /** Null while Substances never counted toward the streak; with dated periods once it did, as a checklist's that joined. */
    val rewards: ChecklistRewards? = null,
    /** What an away day asks: logging needs nothing but the phone, so it stays due unless the person says otherwise. */
    val whenAway: Dated<WhenAway> = Dated.always(WhenAway.Due),
    /** What keeps a day while Substances takes part; read on no other day. */
    val goal: Dated<SubstanceGoal> = Dated.always(SubstanceGoal.Logging),
    /** Under staying away, whether a day with nothing logged is kept; a logging goal never reads it. */
    val exceptionsOnly: Dated<Boolean> = Dated.always(true),
    val mo: TrackerMoPolicy = TrackerMoPolicy(),
    /**
     * The name a person gave the view, shown as typed. Null until they do: the view is then called
     * by the app's own word, which follows the app's language as no stored text could.
     */
    val label: String? = null,
    val icon: ViewIcon = DefaultSubstancesIcon,
    val hue: ViewHue = DefaultSubstancesHue,
) {
    init {
        label?.let(::requireLabel)
        val ownIds = own.map { it.id }
        require(ownIds.toSet().size == ownIds.size) { "Two own substances share an ID." }
        require(ownIds.none { SubstanceKind.fromId(it) != null }) { "An own substance cannot take a catalogue ID." }
        require(tracked.all { substance(it) != null }) { "Only a known substance can be tracked." }
        require(stayingAway.current.all { it in tracked }) { "Only a tracked substance can be one to stay away from." }
        require(stayingAway.revisions.all { revision -> revision.value.all { substance(it) != null } }) { "A stance names known substances." }
        // Logging joined the streak after periods existed, so it is never in without a dated start.
        require(rewards?.periods != null || rewards == null) { "Substances' streak has dated periods." }
        // The goal and its rule exist only beside a bank; without one no day was ever judged by them.
        require(rewards != null || (goal == Dated.always(SubstanceGoal.Logging) && exceptionsOnly == Dated.always(true))) { "Without a bank there is no goal." }
        require(whenAway.revisions.none { it.value == WhenAway.LowerTarget }) { "Substances has no count a week to lower." }
    }

    /** Every substance that can be named, the catalogue in its order and the person's own after it. */
    val all: List<Substance>
        get() = SubstanceKind.entries + own

    val trackedSubstances: List<Substance>
        get() = all.filter { it.id in tracked }

    fun substance(id: String): Substance? = SubstanceKind.fromId(id) ?: own.firstOrNull { it.id == id }

    fun isTracked(id: String): Boolean = id in tracked

    /** Whether [id] is stayed away from today; the stats read this, the walk reads each day's own stance. */
    fun isStayingAway(id: String): Boolean = id in stayingAway.current

    /** Whether Substances counts toward the streak on [date]. */
    fun countsTowardStreakOn(date: LocalDate): Boolean = rewards?.takesPartOn(date) == true

    /** The goal that keeps a day on [date], or null while Substances takes no part in the streak then. */
    fun goalOn(date: LocalDate): SubstanceGoal? = goal.on(date).takeIf { countsTowardStreakOn(date) }

    /**
     * Whether Substances asks anything of [date] while it takes part: logging always does; staying
     * away only while that day's stance names a substance, as a checklist asks nothing on a day
     * none of its items is due.
     */
    fun asksOn(date: LocalDate): Boolean = goal.on(date) == SubstanceGoal.Logging || stayingAway.on(date).isNotEmpty()

    companion object {
        /**
         * What tracker state 18 and older meant without saying so: the three everyone had, and none
         * of their own. Every substance tracked before a person could choose otherwise is one they
         * stay away from, so its days without stay.
         */
        val Legacy = SubstanceKind.Legacy.mapTo(LinkedHashSet()) { it.id }.let { three -> SubstanceSettings(tracked = three, stayingAway = Dated.always(three)) }
    }
}

/**
 * What keeps a day while Substances counts toward the streak. Each is a doing or a logging goal in
 * the contract's words; Substances is the one view that offers both, because what a person wants
 * there can be not doing something.
 */
enum class SubstanceGoal(val storageId: String) {
    /** Any record keeps the day, a use or "none"; what was logged is never read. */
    Logging("logging"),

    /** A day is kept unless it holds a use of a substance stayed away from that day; amounts are never read. */
    StayingAway("stayingAway"),
    ;

    companion object {
        fun fromStorageId(storageId: String): SubstanceGoal? = entries.firstOrNull { it.storageId == storageId }
    }
}

/**
 * The check-in reminder: once a day at [at], in the three forms a checkpoint's time has, asking
 * whether there is anything to log for yesterday ([substanceAskedDay]). It stays quiet once that
 * day is logged.
 */
data class SubstanceCheckIn(
    val remind: Boolean = false,
    val at: CheckpointTime = DefaultCheckInTime,
)

/** The look Substances wore while it was fixed, which a stored choice without one still wears. */
val DefaultSubstancesIcon: ViewIcon = ViewIcon.Science
val DefaultSubstancesHue: ViewHue = ViewHue.Pink

/**
 * The flavors offered for Substances: every motif but the pills. He holds them up, pleased, once
 * a day is logged, and pills are among what a person may be staying away from here. Stored
 * settings may still hold any motif, as they always could, so this limits the choice and refuses
 * nothing.
 */
val SubstanceMotifs: List<MoMotif> = MoMotif.entries - MoMotif.Pills

/** After waking, as the check-in asks about yesterday, which is over by then. */
val DefaultCheckInTime: CheckpointTime = CheckpointTime.AfterWake(30)
