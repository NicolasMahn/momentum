package de.nicolasmahn.momentum

import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.checklist.ChecklistDay
import de.nicolasmahn.momentum.domain.checklist.tickOf
import de.nicolasmahn.momentum.testfixtures.StandardSupplementIds
import de.nicolasmahn.momentum.testfixtures.SupplementsCheckpointId
import de.nicolasmahn.momentum.testfixtures.SupplementsChecklistId
import de.nicolasmahn.momentum.testfixtures.SupplementsLabels
import de.nicolasmahn.momentum.testfixtures.defaultSupplementsChecklist

/** What every day asks for. A day outside Vitamin D's season can show and save nothing else. */
internal val YearRoundSupplementIds = setOf(StandardSupplementIds.MULTIVITAMIN, StandardSupplementIds.CREATINE)

/** Vitamin D too, which only a day in its season can show or save: October to March, from 2026-10-01. */
internal val AllSupplementIds = YearRoundSupplementIds + StandardSupplementIds.VITAMIN_D

/** Supplements as an English installation is seeded, so screens built without the repository have it. */
internal val TestSupplementsChecklist = defaultSupplementsChecklist(
    SupplementsLabels(checklist = "Supplements", multivitamin = "Multivitamin", creatine = "Creatine", vitaminD = "Vitamin D"),
    remind = false,
)

internal fun supplementTicks(takenIds: Set<String>): Set<String> = takenIds.mapTo(LinkedHashSet()) { tickOf(SupplementsCheckpointId, it) }

internal fun supplementDay(dateKey: String, timestampMillis: Long, takenIds: Set<String>) =
    ChecklistDay(SupplementsChecklistId, dateKey, timestampMillis, supplementTicks(takenIds))

/** A state as the repository loads it: Supplements configured, holding [days]. */
internal fun supplementsAppState(vararg days: ChecklistDay) =
    AppState(checklists = listOf(TestSupplementsChecklist), checklistDays = days.toList())

/** A second checklist beside Supplements, half an hour after waking. */
internal val TestSecondChecklist = de.nicolasmahn.momentum.domain.checklist.ChecklistConfiguration(
    id = "chk_second",
    label = "Medication",
    icon = de.nicolasmahn.momentum.domain.checklist.ViewIcon.Checklist,
    hue = de.nicolasmahn.momentum.domain.checklist.ViewHue.Sky,
    checkpoints = listOf(
        de.nicolasmahn.momentum.domain.checklist.Checkpoint("morning", de.nicolasmahn.momentum.domain.checklist.CheckpointTime.AfterWake(30), remind = false),
    ),
    items = listOf(
        de.nicolasmahn.momentum.domain.checklist.ChecklistItem("itm_levo", "Levothyroxine"),
        de.nicolasmahn.momentum.domain.checklist.ChecklistItem("itm_ibu", "Ibuprofen 400"),
    ),
)
