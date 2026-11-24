package de.nicolasmahn.momentum.domain.compatibility

import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.CheckpointPart
import de.nicolasmahn.momentum.domain.DayPart
import de.nicolasmahn.momentum.domain.WholeDay
import de.nicolasmahn.momentum.domain.activity.isActivityLoggedFor
import de.nicolasmahn.momentum.domain.checklist.isCompleteFor
import de.nicolasmahn.momentum.domain.checklist.isDoneFor
import de.nicolasmahn.momentum.domain.compatibility.eatingDateKeyFor
import de.nicolasmahn.momentum.domain.holiday.HolidaySchedule
import de.nicolasmahn.momentum.domain.isLoggedOn
import de.nicolasmahn.momentum.domain.compatibility.MoNeed
import de.nicolasmahn.momentum.domain.settings.ViewRef

/** Defaults used only when evaluating an inherited task without its stored configuration. */
internal fun inheritedDayParts(need: MoNeed): List<DayPart> = when (need) {
    MoNeed.Care -> listOf(CheckpointPart("morning"), CheckpointPart("evening"))
    else -> listOf(WholeDay)
}

internal fun inheritedWholeDayCheckpoint(need: MoNeed): Boolean = need == MoNeed.Supplements

/** Historical completion rules are independent of a tracker's current label, icon or motif. */
internal fun inheritedIsLoggedFor(state: AppState, need: MoNeed, day: String, daysOff: HolidaySchedule): Boolean =
    when (val ref = need.view) {
        is ViewRef.Logged -> state.isLoggedOn(ref.view, if (need == MoNeed.Eating) eatingDateKeyFor(day) else day)
        is ViewRef.Checklist -> state.checklists.firstOrNull { it.id == ref.id }?.let { checklist ->
            if (need == MoNeed.Care) checklist.isCompleteFor(state.checklistDays, day, daysOff)
            else checklist.isDoneFor(state.checklistDays, day, daysOff)
        } ?: false
        is ViewRef.Feature -> isActivityLoggedFor(state.activityEntries, state.sessions, day)
    }
