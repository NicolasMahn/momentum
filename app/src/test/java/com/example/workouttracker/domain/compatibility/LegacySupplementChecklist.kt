package com.example.workouttracker.domain.compatibility

import com.example.workouttracker.domain.mo.TrackerMoPolicy
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.checklist.ChecklistConfiguration
import com.example.workouttracker.domain.checklist.ChecklistItem
import com.example.workouttracker.domain.checklist.ChecklistRewards
import com.example.workouttracker.domain.checklist.Checkpoint
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.MoMotif
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.checklist.checklist
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month

/** Stable inherited checklist ID used by existing records and reward profiles. */
const val SupplementsChecklistId = "supplements"

/** Supplements has one checkpoint, so every item ever taken was taken there. */
const val SupplementsCheckpointId = "midday"

object StandardSupplementIds {
    const val MULTIVITAMIN = "multivitamin"
    const val CREATINE = "creatine"
    const val VITAMIN_D = "vitamin_d"
}

/** The words the default is written with, in the app language at the moment it is seeded. */
data class SupplementsLabels(
    val checklist: String,
    val multivitamin: String,
    val creatine: String,
    val vitaminD: String,
)

/** Supplements' rewards since they began: a bank of three, a pass for every seven kept days. */
val SupplementsRewards = ChecklistRewards(maxPasses = 3, everyKeptDays = 7)

/**
 * Recovers the checklist from snapshots predating stored configurations. Its dated seasonal
 * defaults preserve earlier days' obligations; after recovery it is ordinary stored configuration.
 */
fun defaultSupplementsChecklist(labels: SupplementsLabels, remind: Boolean) = ChecklistConfiguration(
    id = SupplementsChecklistId,
    label = labels.checklist,
    icon = ViewIcon.Medication,
    hue = ViewHue.Purple,
    mo = TrackerMoPolicy(enabled = true, motif = MoMotif.Pills),
    checkpoints = listOf(Checkpoint(SupplementsCheckpointId, CheckpointTime.Fixed(LocalTime.of(12, 30)), remind = remind)),
    rewards = SupplementsRewards,
    items = listOf(
        ChecklistItem(StandardSupplementIds.MULTIVITAMIN, labels.multivitamin),
        ChecklistItem(StandardSupplementIds.CREATINE, labels.creatine),
        ChecklistItem(
            StandardSupplementIds.VITAMIN_D,
            labels.vitaminD,
            months = setOf(Month.OCTOBER, Month.NOVEMBER, Month.DECEMBER, Month.JANUARY, Month.FEBRUARY, Month.MARCH),
            from = LocalDate.parse("2026-10-01"),
        ),
    ),
)

/** Supplements' configuration, or null in a state that was never loaded through the repository. */
val AppState.supplementsChecklist: ChecklistConfiguration? get() = checklist(SupplementsChecklistId)

/**
 * The checkpoint the Supplements reminder speaks at, or null in a configuration that has none by
 * that ID, which restore accepts like any other checklist.
 */
val ChecklistConfiguration.supplementsCheckpoint: Checkpoint?
    get() = checkpoints.firstOrNull { it.id == SupplementsCheckpointId }
