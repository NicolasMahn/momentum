package com.example.workouttracker.testfixtures

import com.example.workouttracker.domain.mo.TrackerMoPolicy
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

const val SupplementsChecklistId = "supplements"

const val SupplementsCheckpointId = "midday"

object StandardSupplementIds {
    const val MULTIVITAMIN = "multivitamin"
    const val CREATINE = "creatine"
    const val VITAMIN_D = "vitamin_d"
}

data class SupplementsLabels(
    val checklist: String,
    val multivitamin: String,
    val creatine: String,
    val vitaminD: String,
)

val SupplementsRewards = ChecklistRewards(maxPasses = 3, everyKeptDays = 7)

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
