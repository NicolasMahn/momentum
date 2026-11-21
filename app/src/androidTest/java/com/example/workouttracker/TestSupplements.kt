package com.example.workouttracker

import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.checklist.ChecklistDay
import com.example.workouttracker.domain.checklist.tickOf
import com.example.workouttracker.testfixtures.StandardSupplementIds
import com.example.workouttracker.testfixtures.SupplementsCheckpointId
import com.example.workouttracker.testfixtures.SupplementsChecklistId
import com.example.workouttracker.testfixtures.SupplementsLabels
import com.example.workouttracker.testfixtures.defaultSupplementsChecklist

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
internal val TestSecondChecklist = com.example.workouttracker.domain.checklist.ChecklistConfiguration(
    id = "chk_second",
    label = "Medication",
    icon = com.example.workouttracker.domain.checklist.ViewIcon.Checklist,
    hue = com.example.workouttracker.domain.checklist.ViewHue.Sky,
    checkpoints = listOf(
        com.example.workouttracker.domain.checklist.Checkpoint("morning", com.example.workouttracker.domain.checklist.CheckpointTime.AfterWake(30), remind = false),
    ),
    items = listOf(
        com.example.workouttracker.domain.checklist.ChecklistItem("itm_levo", "Levothyroxine"),
        com.example.workouttracker.domain.checklist.ChecklistItem("itm_ibu", "Ibuprofen 400"),
    ),
)
