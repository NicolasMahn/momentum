package de.nicolasmahn.momentum.feature.mo

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.runtime.Composable
import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.core.text.UiText
import de.nicolasmahn.momentum.core.text.joinedBy
import de.nicolasmahn.momentum.core.text.pluralText
import de.nicolasmahn.momentum.core.text.uiText
import de.nicolasmahn.momentum.core.ui.DayCover
import de.nicolasmahn.momentum.core.ui.ViewIdentity
import de.nicolasmahn.momentum.core.ui.neutralViewColors
import de.nicolasmahn.momentum.domain.mo.MoDayMark
import de.nicolasmahn.momentum.domain.mo.MoMood
import de.nicolasmahn.momentum.domain.mo.contentKey
import de.nicolasmahn.momentum.domain.mo.MoState
import de.nicolasmahn.momentum.domain.mo.MoStreak
import de.nicolasmahn.momentum.domain.mo.isFrozen
import de.nicolasmahn.momentum.domain.mo.restingTrackersToday
import de.nicolasmahn.momentum.domain.mo.sickToday
import de.nicolasmahn.momentum.domain.SubstancesTrackerRef
import de.nicolasmahn.momentum.domain.TrackerRef
import java.time.LocalDate
import de.nicolasmahn.momentum.feature.substances.SubstancesOwnWord

internal fun MoState.description(): UiText = uiText(
    when (this) {
        MoState.Blazing -> R.string.mo_state_blazing
        MoState.Steady -> R.string.mo_state_steady
        MoState.Dimming -> R.string.mo_state_dimming
        MoState.Cold -> R.string.mo_state_cold
        MoState.Husk -> R.string.mo_state_husk
    },
)

/** Calendars show record dates; a tracker owns any projection from the day it is judged. */
internal fun moFreeDayKeys(
    freeDays: Map<TrackerRef, Set<LocalDate>>,
    recordDateForJudgmentDay: (TrackerRef, LocalDate) -> LocalDate? = { _, day -> day },
): Map<TrackerRef, Set<String>> = freeDays.mapValues { (ref, days) ->
    days.mapNotNullTo(mutableSetOf()) { day -> recordDateForJudgmentDay(ref, day)?.toString() }
}

/** The streak in one line. Freezes only appear once there are some, so a new streak stays quiet. */
internal fun MoStreak.summary(): UiText {
    val streak = pluralText(R.plurals.mo_streak_days, days)
    return if (freezes == 0) streak else listOf(streak, pluralText(R.plurals.mo_freezes, freezes)).joinedBy(" · ")
}

/**
 * Mo is hurt, never angry, and every line names the way out. As a Husk he no longer speaks, so the
 * app speaks about him instead.
 */
internal fun MoMood.line(): UiText {
    val needs = names(missingTrackersToday)
    // He only complains about what is still open today: nobody wants to hear about dusty weights
    // right after a workout.
    val ache = trackerNeglect.entries.filter { it.key in missingTrackersToday }.maxByOrNull { it.value }?.let { (need, neglect) -> MoContentCatalogue[contentKey(need)].presentationAche(neglect, label(need)) }
    // A sick day asks nothing, not even a thaw.
    if (streak.sickToday) return uiText(R.string.mo_line_sick)
    if (isFrozen) {
        // A Husk does not find his voice again in the ice.
        val narrated = state == MoState.Husk
        val thaw = when {
            missingTrackersToday.isEmpty() -> uiText(if (narrated) R.string.mo_line_thaw_anything_narrated else R.string.mo_line_thaw_anything)
            else -> uiText(if (narrated) R.string.mo_line_thaw_needs_narrated else R.string.mo_line_thaw_needs, needs)
        }
        return sentences(uiText(R.string.mo_line_frozen, names(streak.frozenTrackersYesterday)), thaw)
    }
    // Nothing is due only while no visible task has ever been taken up, and then he is Steady.
    if (dueToday == 0) return uiText(R.string.mo_line_nothing_due)
    val offered = freeTrackersToday - restingTrackersToday.toSet()
    if (missingTrackersToday.isEmpty() && offered.isNotEmpty()) {
        // Only things a free day would cover are open: he offers, he does not ask. A rest day is
        // not offered: resting is the point of it.
        val optional = names(offered)
        return when (state) {
            MoState.Husk -> uiText(R.string.mo_line_husk)
            MoState.Dimming, MoState.Cold -> uiText(R.string.mo_line_optional_warm, optional)
            else -> uiText(R.string.mo_line_optional, optional)
        }
    }
    if (missingTrackersToday.isEmpty()) {
        val base = when (state) {
            MoState.Blazing -> ache ?: uiText(R.string.mo_line_done_blazing)
            MoState.Steady -> ache ?: uiText(R.string.mo_line_done_steady)
            MoState.Dimming, MoState.Cold -> uiText(R.string.mo_line_done_warming)
            MoState.Husk -> uiText(R.string.mo_line_husk)
        }
        return (listOf(base) + trackerWeeklyNudges.map { (ref, remaining) ->
            pluralText(R.plurals.mo_tracker_minutes_remaining, remaining, label(ref), remaining)
        }).joinedBy(" ")
    }
    val stillOpen = uiText(R.string.mo_line_still_open, needs)
    return when (state) {
        MoState.Blazing, MoState.Steady -> listOfNotNull(ache, stillOpen).joinedBy(" ")
        MoState.Dimming -> sentences(ache ?: uiText(R.string.mo_line_getting_cold), stillOpen)
        MoState.Cold -> pluralText(R.plurals.mo_line_cold, daysCold, daysCold, needs)
        MoState.Husk -> pluralText(R.plurals.mo_line_husk_open, daysCold, daysCold, needs)
    }
}

private fun MoMood.names(refs: Collection<TrackerRef>): UiText = refs.map { label(it) }.joinedBy(", ")

/** What a task is called: its view's stored name, as typed. Substances without one is called by the app's own word. */
internal fun MoMood.label(ref: TrackerRef): UiText =
    trackerLabels[ref]?.let(UiText::Verbatim) ?: if (ref == SubstancesTrackerRef) SubstancesOwnWord else UiText.Verbatim(ref.id)

private fun sentences(vararg parts: UiText): UiText = parts.toList().joinedBy(" ")

/**
 * What covered each task's days: its own tokens and freezes, from the streak's history, with the
 * older rules' free days where nothing newer applies. A calendar shows these as small marks; none
 * of them is a record. A planned rest day is not among them: resting is what most days of a plan
 * are, so a calendar leaves it as plain as a day nothing was asked of.
 */
internal fun moDayCovers(
    streak: MoStreak,
    freeDayKeys: Map<TrackerRef, Set<String>>,
    recordDateForJudgmentDay: (TrackerRef, LocalDate) -> LocalDate? = { _, day -> day },
    today: LocalDate? = null,
    saidToday: Map<TrackerRef, DayCover> = emptyMap(),
): Map<TrackerRef, Map<String, DayCover>> {
    val covers = mutableMapOf<TrackerRef, MutableMap<String, DayCover>>()
    freeDayKeys.forEach { (ref, keys) -> keys.forEach { covers.getOrPut(ref) { mutableMapOf() }[it] = DayCover.Free } }
    fun cover(ref: TrackerRef, day: LocalDate, kind: DayCover) {
        val key = recordDateForJudgmentDay(ref, day)?.toString() ?: return
        covers.getOrPut(ref) { mutableMapOf() }[key] = kind
    }
    streak.history.forEach { record ->
        when (record.mark) {
            MoDayMark.Protected -> record.coveredTrackers.forEach { cover(it, record.date, DayCover.Token) }
            MoDayMark.Frozen -> record.coveredTrackers.forEach { cover(it, record.date, DayCover.Freeze) }
            else -> Unit
        }
        // The ledger's free days include the planned rests the walk dealt; those stay plain too.
        record.restedTrackers.forEach { ref ->
            recordDateForJudgmentDay(ref, record.date)?.let { covers[ref]?.remove(it.toString()) }
        }
    }
    // Today is not closed yet, so the walk has not dealt it; what was said for it shows at once,
    // as a record does.
    if (today != null) saidToday.forEach { (ref, kind) -> cover(ref, today, kind) }
    return covers
}

/**
 * What a person said for today ahead of the close, as a calendar shows it: a token set aside for
 * the whole task, which for a week's amount is its Sunday. A token for one checkpoint of several
 * leaves the day open, and a quiet day or a rest day covers nothing.
 */
internal fun MoMood.saidTodayCovers(): Map<TrackerRef, DayCover> =
    chosenTrackersToday.associateWith { DayCover.Token }

/**
 * The streak page wears Home's neutral chrome, because the streak is Home's score and no feature's.
 * Its flame is the one on the score that opens it.
 */
@Composable
internal fun streakPresentation(): ViewIdentity =
    ViewIdentity(icon = Icons.Rounded.LocalFireDepartment, colors = neutralViewColors)
