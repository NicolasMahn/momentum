package de.nicolasmahn.momentum.domain.mo

import de.nicolasmahn.momentum.domain.CheckpointPart
import de.nicolasmahn.momentum.domain.TrackerRef

/**
 * What a person said ahead of a close: leave this as it is. [tasks] names whole tasks for today:
 * one judged by its week's amount is covered on its Sunday and quieted for the day before it;
 * [checkpoints] names single checkpoints of a checklist that has several, for today.
 *
 * A choice decides nothing. The close does what it would have done anyway: it deals the rest day
 * or spends the tokens, if they cover. So a choice only stops the asking, and one the facts no
 * longer bear counts for nothing ([MoEvaluation.moodWith]).
 */
data class MoChoices(
    val tasks: Set<TrackerRef> = emptySet(),
    val checkpoints: Map<TrackerRef, Set<String>> = emptyMap(),
) {
    val isEmpty: Boolean get() = tasks.isEmpty() && checkpoints.values.all { it.isEmpty() }
}

/** The tokens [ref]'s bank holds, none where it has no bank on show. */
fun MoMood.tokensHeld(ref: TrackerRef): Int = streak.standings[ref]?.passes ?: 0

/**
 * Whether the tokens of [ref] cover what it leaves open: today's gaps, or what its week still
 * lacks. The close spends them only when they cover all of it, so nothing less can be set aside.
 */
fun MoMood.tokensCover(ref: TrackerRef): Boolean = tokensCover(ref, tokensHeld(ref))

internal fun MoMood.tokensCover(ref: TrackerRef, available: Int): Boolean {
    val required = trackerTokenRequirements[ref] ?: trackerStillOpen(ref).size
    return ref in tokenOpenTrackers && available > 0 && required <= available
}

/**
 * Whether [ref] can be told to rest today: a free day covers it, so the close deals that day
 * whatever is said, and its plan still asks. Saying so spends nothing.
 */
fun MoMood.canRest(ref: TrackerRef): Boolean =
    ref in freeTrackersToday && ref !in missingTrackersToday && trackerStillOwed(ref).isNotEmpty()

/**
 * Whether [ref]'s tokens are set aside a checkpoint at a time: a checklist Mo watches that has
 * several. Its bank rarely covers a whole day at once, and a morning missed should not have to
 * wait for the evening to be said.
 */
fun MoMood.choosesByCheckpoint(ref: TrackerRef): Boolean = ref in trackerOrder && trackerCheckpoints[ref].orEmpty().size > 1

/**
 * The tokens of [ref] no checkpoint holds yet. An open checkpoint that owes nothing any more, a
 * miss already marked, costs one at the close, so it holds one from the start.
 */
fun MoMood.checkpointTokensLeft(ref: TrackerRef): Int {
    val setAside = checkpointsSetAside[ref].orEmpty()
    val settled = trackerMissedParts[ref].orEmpty().count { it !is CheckpointPart || it.checkpointId !in setAside }
    return (tokensHeld(ref) - settled - setAside.size).coerceAtLeast(0)
}

/**
 * The checkpoints of [ref] a token can be set aside for now, or taken back from: those with a
 * token, and those still owed while the bank has one left.
 */
fun MoMood.checkpointsToSetAside(ref: TrackerRef): Set<String> {
    val setAside = checkpointsSetAside[ref].orEmpty()
    if (!choosesByCheckpoint(ref) || (setAside.isEmpty() && ref !in missingTrackersToday)) return setAside
    val owed = trackerStillOwed(ref).filterIsInstance<CheckpointPart>().map { it.checkpointId }
    return if (checkpointTokensLeft(ref) > 0) setAside + owed else setAside
}

/**
 * The tasks whose asking a choice stopped today: a token covers them, they rest by choice, or a
 * week's amount is quieted for the day. Their reminders stay silent, as Mo does.
 */
val MoMood.settledByChoice: Set<TrackerRef> get() = chosenTrackersToday + restChosenTrackers + quietTrackersToday

/** Whether [ref], a week's amount, can be quieted today, or was and can be asked again. */
fun MoMood.canQuiet(ref: TrackerRef): Boolean = ref in quietTrackersToday || ref in quietOfferedTrackers
