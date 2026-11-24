package de.nicolasmahn.momentum.domain.compatibility

import de.nicolasmahn.momentum.domain.mo.TrackerMoPolicy
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.checklist.Checkpoint
import de.nicolasmahn.momentum.domain.checklist.CheckpointTime
import de.nicolasmahn.momentum.domain.checklist.MoMotif
import de.nicolasmahn.momentum.domain.checklist.Dated
import de.nicolasmahn.momentum.domain.checklist.DayCheckpointId
import de.nicolasmahn.momentum.domain.checklist.ViewHue
import de.nicolasmahn.momentum.domain.checklist.ViewIcon
import de.nicolasmahn.momentum.domain.checklist.WhenAway
import de.nicolasmahn.momentum.domain.time.TimeConfiguration
import de.nicolasmahn.momentum.domain.time.TimeRewards
import de.nicolasmahn.momentum.domain.time.MadeTimeId
import de.nicolasmahn.momentum.domain.time.MadeTimeIdPrefix
import de.nicolasmahn.momentum.domain.time.DefaultWeeklyGoalMinutes
import de.nicolasmahn.momentum.domain.time.DefaultTimeBankSize
import java.time.LocalTime

// These defaults recover the timer inherited from installations before configurable timers.
// They are never a separate feature, and new installations do not create this preset.

/** The ID Reading's configuration is stored under; its logs name it. */
const val ReadingTimeId = "reading"

/** Reading's goal since the token rules began: sixty minutes a week. */
val ReadingGoal: Dated<Int> = Dated.always(DefaultWeeklyGoalMinutes)

/** Reading's bank since the token rules began: three passes. */
val ReadingRewards = TimeRewards(maxPasses = DefaultTimeBankSize)

/** Reading away as holidays always worked: still due. */
val ReadingWhenAway: Dated<WhenAway> = Dated.always(WhenAway.Due)

/** When Reading's reminder comes once it is a checkpoint: six in the evening, the one time a day its four evenings became. */
val ReadingCheckpointTime: CheckpointTime = CheckpointTime.Fixed(LocalTime.of(18, 0))

/** Reading's checkpoint with its switch as [remind]: the one a state from before it was stored is read with, with the switch the reminder settings held. */
fun defaultReadingCheckpoint(remind: Boolean) = Checkpoint(id = DayCheckpointId, at = ReadingCheckpointTime, remind = remind)

/**
 * Reading as it has always been judged and shown, its reminder on as [remind] says. It is written
 * once, with [label] in the app language of that moment, when a state from before time views
 * were stored is first read, and from then on it is stored. A new installation is not seeded
 * with it.
 */
fun defaultReadingTime(label: String, remind: Boolean = false) = TimeConfiguration(
    id = ReadingTimeId,
    label = label,
    icon = ViewIcon.Book,
    hue = ViewHue.Yellow,
    mo = TrackerMoPolicy(enabled = true, motif = MoMotif.Book),
    goal = ReadingGoal,
    rewards = ReadingRewards,
    whenAway = ReadingWhenAway,
    checkpoint = defaultReadingCheckpoint(remind),
)

/** Reading's configuration, or null where the state holds none: one never loaded through the repository, or one Reading was deleted from. */
val AppState.readingTime: TimeConfiguration? get() = time.firstOrNull { it.id == ReadingTimeId }

/**
 * Reading's goal, bank and away rule as a new installation has them, for judging where no
 * configuration is at hand. Nothing reads a name from it, so it wears its ID.
 */
val SeededReadingRules: TimeConfiguration = defaultReadingTime(label = ReadingTimeId)

/**
 * Reading as the walk, Mo's ledger and its reminder judge it: its stored configuration, or the
 * seeded rules in a state that was never loaded through the repository and holds none.
 */
val AppState.readingRules: TimeConfiguration get() = readingTime ?: SeededReadingRules

/** The inherited timer predates dated joining; other persisted timers join explicitly. */
internal fun requireCompatibleTimerBank(id: String, rewards: TimeRewards?) {
    if (id == ReadingTimeId) {
        require(rewards != null) { "The inherited timer has its bank." }
    } else {
        require(MadeTimeId.matches(id)) { "A timer is named ${MadeTimeIdPrefix}_ and four characters, not '$id'." }
        require(rewards == null || rewards.periods != null) { "Timer $id has the bank with its periods, or none." }
    }
}

/** Missing old motif fields retain the inherited book choice. */
fun startingTimeMotif(id: String): MoMotif? = if (id == ReadingTimeId) MoMotif.Book else null

val AppState.madeTime: List<TimeConfiguration> get() = time.filter { it.id != ReadingTimeId }

/** Only the inherited timer's earlier wire format recorded completion without duration. */
internal fun requireCompatibleTimerMark(viewId: String) {
    require(viewId == ReadingTimeId) { "A log of $viewId requires recorded minutes." }
}
