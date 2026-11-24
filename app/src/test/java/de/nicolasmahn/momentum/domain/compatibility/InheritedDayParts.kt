package de.nicolasmahn.momentum.domain.compatibility

import de.nicolasmahn.momentum.domain.activity.isActivityLoggedFor
import de.nicolasmahn.momentum.domain.checklist.ChecklistConfiguration
import de.nicolasmahn.momentum.domain.checklist.checkpointsOn
import de.nicolasmahn.momentum.domain.checklist.isAssumedFor
import de.nicolasmahn.momentum.domain.checklist.isCompleteFor
import de.nicolasmahn.momentum.domain.checklist.isDoneFor
import de.nicolasmahn.momentum.domain.checklist.progressOf
import de.nicolasmahn.momentum.domain.holiday.HolidaySchedule
import java.time.LocalDate
import de.nicolasmahn.momentum.domain.compatibility.MoNeed
import de.nicolasmahn.momentum.domain.settings.ViewRef
import de.nicolasmahn.momentum.domain.checklist.checklist
import de.nicolasmahn.momentum.domain.compatibility.inheritedDayParts
import de.nicolasmahn.momentum.domain.compatibility.inheritedIsLoggedFor
import de.nicolasmahn.momentum.domain.compatibility.inheritedWholeDayCheckpoint

import de.nicolasmahn.momentum.domain.DayPart
import de.nicolasmahn.momentum.domain.CheckpointPart
import de.nicolasmahn.momentum.domain.WholeDay
import de.nicolasmahn.momentum.domain.AppState

/** The parts of this task's day as it was built, before its checklist could change. */
val MoNeed.dayParts: List<DayPart> get() = inheritedDayParts(this)

private fun AppState.checklistOf(need: MoNeed): ChecklistConfiguration? =
    (need.view as? ViewRef.Checklist)?.let { checklist(it.id) }

/**
 * The parts of [need]'s day on [dateKey], in the order they are asked for. A task on a
 * checklist has one part per checkpoint with something due that day, Care's sessions by their own
 * names, and none on a day it asks nothing; Supplements with one checkpoint that day stays one
 * whole day, as it was. A week item is due at its checkpoint only on a day skipping it would cost,
 * so [daysOff] decides which days of its week count.
 */
fun AppState.dayParts(need: MoNeed, dateKey: String, daysOff: HolidaySchedule): List<DayPart> {
    val checklist = checklistOf(need) ?: return need.dayParts
    val due = progressOf(checklist, dateKey, daysOff).checkpoints
    if (due.isEmpty()) return emptyList()
    if (inheritedWholeDayCheckpoint(need) && checklist.checkpointsOn(LocalDate.parse(dateKey)).size == 1) return listOf(WholeDay)
    return due.map { (checkpoint) ->
        CheckpointPart(checkpoint.id)
    }
}

/** Whether [part] is done: nothing due at it is unmet, every item ticked or assumed. */
fun AppState.isDone(need: MoNeed, part: DayPart, dateKey: String, daysOff: HolidaySchedule): Boolean = when (part) {
    is CheckpointPart -> checklistOf(need)?.let { checklist ->
        progressOf(checklist, dateKey, daysOff).checkpoints.filter { it.checkpoint.id == part.checkpointId }.all { it.unmet.isEmpty() }
    } ?: false
    else -> isLoggedFor(need, dateKey, daysOff)
}

/**
 * The parts of a checklist task's day that still owe something on [dateKey]: a checkpoint with
 * an unticked item that ticks, or a week item past its free days. A part with a miss is not done,
 * yet nothing is owed there: a miss is settled, so no reminder and no lateness asks for it. Null
 * for a task without a checklist, whose one part the ledger alone speaks for.
 */
fun AppState.owedParts(need: MoNeed, dateKey: String, daysOff: HolidaySchedule): List<DayPart>? {
    val checklist = checklistOf(need) ?: return null
    val progress = progressOf(checklist, dateKey, daysOff)
    return dayParts(need, dateKey, daysOff).filter { part ->
        when (part) {
            WholeDay -> progress.isOwing
            is CheckpointPart -> progress.owesAt(part.checkpointId)
            else -> false
        }
    }
}

/**
 * Whether [need]'s day is kept by assumption alone on [dateKey]: complete with nothing due
 * ticked, as items that log only misses keep an untouched day. Home says such a day was kept,
 * never done, and Mo never celebrates it.
 */
fun AppState.isKeptByAssumption(need: MoNeed, dateKey: String, daysOff: HolidaySchedule): Boolean =
    checklistOf(need)?.isAssumedFor(checklistDays, dateKey, daysOff) ?: false

/**
 * Whether [need] needs nothing more on [dateKey]. Each task owns its own rule, because "today's
 * record" is not always dated today: Eating reflects on the day that just ended. "Logged" never
 * judges the value: a poor eating day counts, while Care requires both complete sessions.
 * Supplements counts any tick, due or not, as it always has, unless an item that logs only misses
 * is due, when the day is done as the walk keeps it ([isDoneFor]).
 */
fun AppState.isLoggedFor(need: MoNeed, dateKey: String, daysOff: HolidaySchedule): Boolean =
    inheritedIsLoggedFor(this, need, dateKey, daysOff)
