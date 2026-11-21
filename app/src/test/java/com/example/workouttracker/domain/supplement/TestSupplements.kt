package com.example.workouttracker.domain.supplement

import com.example.workouttracker.domain.compatibility.StandardSupplementIds

import com.example.workouttracker.domain.compatibility.defaultSupplementsChecklist

import com.example.workouttracker.domain.compatibility.SupplementsLabels

import com.example.workouttracker.domain.compatibility.SupplementsCheckpointId

import com.example.workouttracker.domain.compatibility.SupplementsChecklistId

import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.checklist.ChecklistDay
import com.example.workouttracker.domain.checklist.tickOf

internal val YearRoundSupplementIds = setOf(StandardSupplementIds.MULTIVITAMIN, StandardSupplementIds.CREATINE)
internal val AllSupplementIds = YearRoundSupplementIds + StandardSupplementIds.VITAMIN_D

/** Supplements as an installation seeded in English has it. */
internal fun testSupplementsChecklist(remind: Boolean = false) = defaultSupplementsChecklist(
    SupplementsLabels(checklist = "Supplements", multivitamin = "Multivitamin", creatine = "Creatine", vitaminD = "Vitamin D"),
    remind = remind,
)

/** One day's record with the given items taken at Supplements' one checkpoint. */
internal fun supplementDay(dateKey: String, timestampMillis: Long, takenIds: Set<String>) = ChecklistDay(
    checklistId = SupplementsChecklistId,
    dateKey = dateKey,
    timestampMillis = timestampMillis,
    ticks = takenIds.mapTo(LinkedHashSet()) { tickOf(SupplementsCheckpointId, it) },
)

/** [this] with Supplements configured, as every loaded state is, and [days] added to its records. */
internal fun AppState.withSupplements(vararg days: ChecklistDay, remind: Boolean = false): AppState = copy(
    checklists = checklists.filterNot { it.id == SupplementsChecklistId } + testSupplementsChecklist(remind),
    checklistDays = checklistDays + days,
)

internal fun supplementsState(vararg days: ChecklistDay, remind: Boolean = false): AppState =
    AppState().withSupplements(*days, remind = remind)

internal fun supplementTicks(takenIds: Set<String>): Set<String> =
    takenIds.mapTo(LinkedHashSet()) { tickOf(SupplementsCheckpointId, it) }
