package de.nicolasmahn.momentum.app

import de.nicolasmahn.momentum.core.time.requireValidDateKey
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.DayProgress
import de.nicolasmahn.momentum.domain.LoggedViewRef
import de.nicolasmahn.momentum.domain.checklist.checklist
import de.nicolasmahn.momentum.domain.checklist.progressOf
import de.nicolasmahn.momentum.domain.checklist.recordJudgedOn
import de.nicolasmahn.momentum.domain.compatibility.dayParts
import de.nicolasmahn.momentum.domain.holiday.HolidaySchedule
import de.nicolasmahn.momentum.domain.compatibility.isDone
import de.nicolasmahn.momentum.domain.compatibility.isKeptByAssumption
import de.nicolasmahn.momentum.domain.compatibility.isLoggedFor
import de.nicolasmahn.momentum.domain.mo.MoStreak
import de.nicolasmahn.momentum.domain.mo.MoStreakStanding
import de.nicolasmahn.momentum.domain.SubstancesTrackerRef
import de.nicolasmahn.momentum.domain.settings.ViewRef
import de.nicolasmahn.momentum.domain.viewRefOrNull
import de.nicolasmahn.momentum.domain.compatibility.MoNeed

/**
 * How far each of Mo's tasks has come on [todayKey], in parts of its day. Most tasks have one part,
 * so they are either open or done; a task with several, such as Care, can be in between. A week
 * item counts in a part only on a day skipping it would cost, which [daysOff] helps decide. A day
 * kept by assumption alone is complete and says so, so Home calls it kept.
 */
internal fun featureProgressOn(state: AppState, todayKey: String, daysOff: HolidaySchedule): Map<MoNeed, DayProgress> =
    MoNeed.entries
        .mapNotNull { need ->
            // A checklist that asks nothing today, its every item ended or out of season, has no parts.
            state.dayParts(need, todayKey, daysOff).takeIf { it.isNotEmpty() }?.let { parts ->
                need to DayProgress(
                    done = parts.count { state.isDone(need, it, todayKey, daysOff) },
                    parts = parts.size,
                    assumed = state.isKeptByAssumption(need, todayKey, daysOff),
                )
            }
        }
        .toMap()

/** Reads checkpoint progress for enrolled checklist trackers without treating protection as a record. */
internal fun checklistProgressOn(state: AppState, streak: MoStreak, todayKey: String, daysOff: HolidaySchedule): Map<String, DayProgress> {
    val today = requireValidDateKey(todayKey)
    if (daysOff.isSick(today)) return emptyMap()
    return streak.standings.filterKeys { it.type.value == "checklist" }.mapNotNull { (ref, standing) ->
        val id = ref.id
        val checklist = state.checklist(id) ?: return@mapNotNull null
        val recordDay = checklist.recordJudgedOn(today)?.takeUnless(daysOff::isSick) ?: return@mapNotNull null
        val progress = state.progressOf(checklist, recordDay.toString(), daysOff)
        val checkpoints = progress.checkpoints
        val done = checkpoints.count { it.unmet.isEmpty() }
        if (checkpoints.isEmpty() || (done < checkpoints.size && !standing.openToday)) return@mapNotNull null
        id to DayProgress(done = done, parts = checkpoints.size, assumed = progress.isAssumed)
    }.toMap()
}

/** A bank's kept/open state, used by the inherited Home badge fixtures. */
internal fun substanceProgressOn(streak: MoStreak): DayProgress? = streak.standings[SubstancesTrackerRef]?.dayProgress()

/** The typed-view boundary used by existing Home badge fixtures. */
internal fun loggedProgressOn(streak: MoStreak): Map<LoggedViewRef, DayProgress> =
    streak.standings.mapNotNull { (ref, standing) ->
        val view = (ref.viewRefOrNull() as? ViewRef.Logged)?.view ?: return@mapNotNull null
        standing.dayProgress()?.let { view to it }
    }.toMap()

/** A joined view's day as its one part: done once the walk has it kept, open while it holds it open, and nothing on a day it asks nothing of. */
private fun MoStreakStanding.dayProgress(): DayProgress? = when {
    keptToday -> DayProgress(done = 1, parts = 1)
    openToday -> DayProgress(done = 0, parts = 1)
    else -> null
}
