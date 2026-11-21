package com.example.workouttracker.feature.mo

import com.example.workouttracker.R
import com.example.workouttracker.core.text.UiText
import com.example.workouttracker.core.text.joinedBy
import com.example.workouttracker.core.text.pluralText
import com.example.workouttracker.core.text.uiText
import com.example.workouttracker.domain.mo.MoMood
import com.example.workouttracker.domain.compatibility.MoNeed
import com.example.workouttracker.domain.mo.MoState
import com.example.workouttracker.domain.TrackerRef
import com.example.workouttracker.domain.mo.isFrozen
import com.example.workouttracker.domain.trackerRef

// Stable lines stay independent from the catalogue; configurable trackers supply their own labels.
internal fun MoMood.frozenLine(): UiText {
    val needs = missingTrackersToday.frozenLabels(this)
    // He only complains about what is still open today: nobody wants to hear about dusty weights
    // right after a workout.
    val ache = trackerNeglect.entries.filter { it.key in missingTrackersToday }
        .maxByOrNull { it.value }?.key?.frozenPresentationAche()
    if (isFrozen) {
        val narrated = state == MoState.Husk
        val thaw = when {
            missingTrackersToday.isEmpty() -> uiText(if (narrated) R.string.mo_line_thaw_anything_narrated else R.string.mo_line_thaw_anything)
            else -> uiText(if (narrated) R.string.mo_line_thaw_needs_narrated else R.string.mo_line_thaw_needs, needs)
        }
        return sentences(uiText(R.string.mo_line_frozen, streak.frozenTrackersYesterday.frozenLabels(this)), thaw)
    }
    // Nothing is due only while no visible task has ever been taken up, and then he is Steady.
    if (dueToday == 0) return uiText(R.string.mo_line_nothing_due)
    if (missingTrackersToday.isEmpty() && freeTrackersToday.isNotEmpty()) {
        // Only things a free day would cover are open: he offers, he does not ask.
        val optional = freeTrackersToday.frozenLabels(this)
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
        val nudge = trackerWeeklyNudges.map { (ref, remaining) ->
            pluralText(R.plurals.mo_tracker_minutes_remaining, remaining, label(ref), remaining)
        }
        return (listOf(base) + nudge).joinedBy(" ")
    }
    val stillOpen = uiText(R.string.mo_line_still_open, needs)
    return when (state) {
        MoState.Blazing, MoState.Steady -> listOfNotNull(ache, stillOpen).joinedBy(" ")
        MoState.Dimming -> sentences(ache ?: uiText(R.string.mo_line_getting_cold), stillOpen)
        MoState.Cold -> pluralText(R.plurals.mo_line_cold, daysCold, daysCold, needs)
        MoState.Husk -> pluralText(R.plurals.mo_line_husk_open, daysCold, daysCold, needs)
    }
}

private fun Collection<TrackerRef>.frozenLabels(mood: MoMood): UiText = map { ref -> mood.label(ref) }.joinedBy(", ")

private fun TrackerRef.frozenPresentationAche(): UiText? =
    MoNeed.entries.firstOrNull { it.view.trackerRef == this }?.frozenPresentationAche()

private fun sentences(vararg parts: UiText): UiText = parts.toList().joinedBy(" ")

/** What a neglected task feels like to him. It names the symptom the figure is showing. */
internal fun MoNeed.frozenPresentationAche(): UiText = uiText(
    when (this) {
        MoNeed.Care -> R.string.mo_ache_care
        MoNeed.Reading -> R.string.mo_ache_reading
        MoNeed.Activity -> R.string.mo_ache_activity
        MoNeed.Supplements -> R.string.mo_ache_supplements
        MoNeed.Eating -> R.string.mo_ache_eating
        MoNeed.Weight -> R.string.mo_ache_weight
    },
)
