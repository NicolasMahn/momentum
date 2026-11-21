package com.example.workouttracker.domain.compatibility

import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.CheckpointPart
import com.example.workouttracker.domain.DayPart
import com.example.workouttracker.domain.WholeDay
import com.example.workouttracker.domain.activity.isActivityLoggedFor
import com.example.workouttracker.domain.checklist.isCompleteFor
import com.example.workouttracker.domain.checklist.isDoneFor
import com.example.workouttracker.domain.compatibility.eatingDateKeyFor
import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.domain.isLoggedOn
import com.example.workouttracker.domain.compatibility.MoNeed
import com.example.workouttracker.domain.settings.ViewRef

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
