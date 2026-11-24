package de.nicolasmahn.momentum.domain.supplement

import de.nicolasmahn.momentum.domain.compatibility.StandardSupplementIds

import de.nicolasmahn.momentum.domain.compatibility.defaultSupplementsChecklist

import de.nicolasmahn.momentum.domain.compatibility.SupplementsLabels

import de.nicolasmahn.momentum.domain.compatibility.SupplementsCheckpointId

import de.nicolasmahn.momentum.domain.compatibility.SupplementsChecklistId

import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.checklist.ChecklistDay
import de.nicolasmahn.momentum.domain.checklist.tickOf

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
