package com.example.workouttracker.app

import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.checklist.progressOf
import com.example.workouttracker.domain.compatibility.dayParts
import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.domain.compatibility.careChecklist
import com.example.workouttracker.domain.checklist.recordOn
import com.example.workouttracker.domain.compatibility.supplementsChecklist
import java.io.Serializable
import com.example.workouttracker.domain.compatibility.isLoggedFor
import com.example.workouttracker.domain.compatibility.MoNeed

/** Factual adapter for the saved inherited celebration snapshot; current Home badges use tracker progress. */
internal fun featuresLoggedOn(state: AppState, todayKey: String, daysOff: HolidaySchedule): Set<MoNeed> =
    MoNeed.entries
        // A checklist that asks nothing today has nothing to be done, so it is not done either.
        .filterTo(mutableSetOf()) { need -> state.isLoggedFor(need, todayKey, daysOff) && state.dayParts(need, todayKey, daysOff).isNotEmpty() }

/** What a task on a checklist has ticked on a day, due or not, and what a tick there would still answer. */
internal data class DayTicks(val ticked: Set<String>, val owed: Set<String>)

/** Each task on a checklist's [DayTicks] on [todayKey], which [MoSeen] compares. */
internal fun featureTicksOn(state: AppState, todayKey: String, daysOff: HolidaySchedule): Map<MoNeed, DayTicks> = listOfNotNull(
    state.supplementsChecklist?.let { MoNeed.Supplements to it },
    state.careChecklist?.let { MoNeed.Care to it },
).associate { (need, checklist) ->
    need to DayTicks(state.recordOn(checklist.id, todayKey).ticks, state.progressOf(checklist, todayKey, daysOff).owedTicks)
}

/**
 * What Mo has seen of [dayKey]: the tasks that read as logged ([featuresLoggedOn]) and, for each
 * task on a checklist, what was ticked and what was still owed, as of the last state he saw.
 * [pending] are the tasks he still acts out. A checklist's task earns that only by a change that
 * keeps its day with a tick that answered something owed, so a day kept by assumption, a miss
 * taken back, a settings change or a tick of something not due never does; any other task, by
 * being newly logged. Plain strings, so it survives being saved.
 */
internal data class MoSeen(
    val dayKey: String,
    val logged: List<String>,
    val ticks: Map<String, List<String>>,
    val owed: Map<String, List<String>>,
    val pending: List<String> = emptyList(),
) : Serializable {
    constructor(dayKey: String, logged: Set<MoNeed>, ticks: Map<MoNeed, DayTicks>) : this(
        dayKey,
        logged.map { it.name },
        ticks.entries.associate { (need, day) -> need.name to day.ticked.sorted() },
        ticks.entries.associate { (need, day) -> need.name to day.owed.sorted() },
    )

    /** The task Mo acts out next, if any. */
    val celebrating: MoNeed? get() = pending.firstOrNull()?.let(MoNeed::valueOf)

    /**
     * Mo's view after seeing [logged] and [ticks] on [dayKey]. A new day starts from what is already
     * there, and a task no longer logged leaves [pending], so a removed record never leaves him
     * acting out something that is gone.
     */
    fun seeing(dayKey: String, logged: Set<MoNeed>, ticks: Map<MoNeed, DayTicks>): MoSeen {
        val next = MoSeen(dayKey, logged, ticks)
        if (dayKey != this.dayKey) return next
        val earned = logged.filter { need ->
            val day = ticks[need]
            need.name !in this.logged &&
                (day == null || day.ticked.any { it !in this.ticks[need.name].orEmpty() && it in this.owed[need.name].orEmpty() })
        }
        return next.copy(pending = (pending.filter { it in next.logged } + earned.map { it.name }).distinct())
    }
}
