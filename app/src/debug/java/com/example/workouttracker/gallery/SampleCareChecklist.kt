package com.example.workouttracker.gallery

import com.example.workouttracker.domain.mo.TrackerMoPolicy
import com.example.workouttracker.domain.checklist.ChecklistConfiguration
import com.example.workouttracker.domain.checklist.ChecklistItem
import com.example.workouttracker.domain.checklist.ChecklistRewards
import com.example.workouttracker.domain.checklist.ChecklistTarget
import com.example.workouttracker.domain.checklist.Checkpoint
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.MoMotif
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.checklist.checklist
import java.time.DayOfWeek
import java.time.LocalDate

const val DemoCareId = "demo_care"

enum class DemoCareAction(val storageId: String) {
    TOOTHBRUSHING("toothbrushing"),
    HAIR_FOAM("hair_foam"),
    CLEAN_BETWEEN_TEETH("clean_between_teeth"),
    ;

    companion object {
        fun fromStorageId(storageId: String): DemoCareAction? =
            entries.firstOrNull { it.storageId == storageId }
    }
}

enum class DemoCareSession(val storageId: String, val builtInTime: CheckpointTime) {
    MORNING("morning", CheckpointTime.AfterWake(5)),
    EVENING("evening", CheckpointTime.BeforeSleep(15)),
    ;

    companion object {
        fun fromStorageId(storageId: String): DemoCareSession? =
            entries.firstOrNull { it.storageId == storageId }
    }
}

val WeeklyCareStart: LocalDate = LocalDate.parse("2026-10-03")

val CareRewards = ChecklistRewards(maxPasses = 1, everyKeptDays = 14)

data class CareLabels(
    val checklist: String,
    val morning: String,
    val evening: String,
    val toothbrushing: String,
    val hairFoam: String,
    val cleanBetweenTeeth: String,
)

fun sampleCareChecklist(labels: CareLabels, remindMorning: Boolean, remindEvening: Boolean) = ChecklistConfiguration(
    id = DemoCareId,
    label = labels.checklist,
    icon = ViewIcon.Toothbrush,
    hue = ViewHue.Teal,
    mo = TrackerMoPolicy(enabled = true, motif = MoMotif.Teeth),
    checkpoints = listOf(
        Checkpoint(DemoCareSession.MORNING.storageId, DemoCareSession.MORNING.builtInTime, label = labels.morning, remind = remindMorning),
        Checkpoint(DemoCareSession.EVENING.storageId, DemoCareSession.EVENING.builtInTime, label = labels.evening, remind = remindEvening),
    ),
    rewards = CareRewards,
    items = listOf(
        ChecklistItem(DemoCareAction.TOOTHBRUSHING.storageId, labels.toothbrushing),
        ChecklistItem(DemoCareAction.HAIR_FOAM.storageId, labels.hairFoam),
        ChecklistItem(
            DemoCareAction.CLEAN_BETWEEN_TEETH.storageId,
            labels.cleanBetweenTeeth,
            from = WeeklyCareStart,
            checkpoints = listOf(DemoCareSession.EVENING.storageId),
            target = ChecklistTarget.Weekdays(setOf(DayOfWeek.SATURDAY)),
        ),
    ),
)
