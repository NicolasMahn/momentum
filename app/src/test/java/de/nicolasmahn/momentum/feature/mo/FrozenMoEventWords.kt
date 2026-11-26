package de.nicolasmahn.momentum.feature.mo

import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.core.text.UiText
import de.nicolasmahn.momentum.core.text.pluralText
import de.nicolasmahn.momentum.core.text.uiText
import de.nicolasmahn.momentum.domain.activity.DefaultActivityDaysAWeek
import de.nicolasmahn.momentum.domain.mo.MoContentKey
import de.nicolasmahn.momentum.domain.mo.MoEvent
import de.nicolasmahn.momentum.domain.mo.MoMood
import de.nicolasmahn.momentum.domain.mo.MoViewKind
import de.nicolasmahn.momentum.domain.mo.contentKey

// Fixed event words remain independent. Configurable kinds use their current unit and label.
internal data class FrozenMoEventWords(val headline: UiText, val line: UiText)

internal fun MoEvent.frozenWords(mood: MoMood): FrozenMoEventWords = when (this) {
    is MoEvent.StreakLost -> FrozenMoEventWords(pluralText(R.plurals.mo_lost_days, loss.days), uiText(R.string.mo_lost_title))
    is MoEvent.LevelReached -> FrozenMoEventWords(uiText(arrival.level.labelRes), pluralText(R.plurals.mo_level_days, arrival.level.days))
    is MoEvent.StreakMilestone ->
        FrozenMoEventWords(pluralText(R.plurals.mo_event_milestone, days), uiText(R.string.mo_event_milestone_line))
    MoEvent.PerfectWeek -> FrozenMoEventWords(uiText(R.string.mo_event_perfect_week), uiText(R.string.mo_event_perfect_week_line))
    MoEvent.Comeback -> FrozenMoEventWords(uiText(R.string.mo_event_comeback), uiText(R.string.mo_event_comeback_line))
    is MoEvent.WeekGoalReached -> {
        val week = mood.trackerWeekProgress.getValue(ref)
        FrozenMoEventWords(
            uiText(R.string.mo_event_week_goal, week.done, week.expected),
            uiText(R.string.mo_event_week_goal_line_checklist, mood.label(ref)),
        )
    }
    is MoEvent.TokenEarned -> FrozenMoEventWords(
        uiText(R.string.mo_event_token, mood.label(ref)),
        frozenTokenLine(mood.contentKey(ref), mood.label(ref), mood.tokenEarningBasisByTracker[ref]),
    )
    MoEvent.FreezeEarned -> FrozenMoEventWords(uiText(R.string.mo_event_freeze), uiText(R.string.mo_event_freeze_line))
}

private fun frozenTokenLine(key: MoContentKey, label: UiText, amount: Int?): UiText {
    if (amount == null) return uiText(R.string.mo_tracker_token_earned, label)
    return when (key.kind) {
        MoViewKind.Activity -> if (amount == DefaultActivityDaysAWeek) {
            uiText(R.string.mo_event_token_line_activity)
        } else {
            pluralText(R.plurals.mo_event_token_line_activity_days, amount, amount)
        }
        MoViewKind.Measurement -> pluralText(R.plurals.mo_tracker_token_logged_days, amount, amount)
        MoViewKind.Questions -> pluralText(R.plurals.mo_tracker_token_kept_days, amount, amount)
        MoViewKind.Time -> pluralText(R.plurals.mo_tracker_token_week_minutes, amount, amount)
        MoViewKind.Checklist -> uiText(R.string.mo_tracker_token_earned, label)
    }
}
