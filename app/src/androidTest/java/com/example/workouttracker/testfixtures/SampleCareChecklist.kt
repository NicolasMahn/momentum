package com.example.workouttracker.testfixtures

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

const val CareChecklistId = "care"

enum class LegacyCareAction(val storageId: String) {
    TOOTHBRUSHING("toothbrushing"),
    HAIR_FOAM("hair_foam"),
    CLEAN_BETWEEN_TEETH("clean_between_teeth"),
    ;

    companion object {
        fun fromStorageId(storageId: String): LegacyCareAction? =
            entries.firstOrNull { it.storageId == storageId }
    }
}

enum class LegacyCareSession(val storageId: String, val builtInTime: CheckpointTime) {
    MORNING("morning", CheckpointTime.AfterWake(5)),
    EVENING("evening", CheckpointTime.BeforeSleep(15)),
    ;

    companion object {
        fun fromStorageId(storageId: String): LegacyCareSession? =
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

fun defaultCareChecklist(labels: CareLabels, remindMorning: Boolean, remindEvening: Boolean) = ChecklistConfiguration(
    id = CareChecklistId,
    label = labels.checklist,
    icon = ViewIcon.Toothbrush,
    hue = ViewHue.Teal,
    mo = TrackerMoPolicy(enabled = true, motif = MoMotif.Teeth),
    checkpoints = listOf(
        Checkpoint(LegacyCareSession.MORNING.storageId, LegacyCareSession.MORNING.builtInTime, label = labels.morning, remind = remindMorning),
        Checkpoint(LegacyCareSession.EVENING.storageId, LegacyCareSession.EVENING.builtInTime, label = labels.evening, remind = remindEvening),
    ),
    rewards = CareRewards,
    items = listOf(
        ChecklistItem(LegacyCareAction.TOOTHBRUSHING.storageId, labels.toothbrushing),
        ChecklistItem(LegacyCareAction.HAIR_FOAM.storageId, labels.hairFoam),
        ChecklistItem(
            LegacyCareAction.CLEAN_BETWEEN_TEETH.storageId,
            labels.cleanBetweenTeeth,
            from = WeeklyCareStart,
            checkpoints = listOf(LegacyCareSession.EVENING.storageId),
            target = ChecklistTarget.Weekdays(setOf(DayOfWeek.SATURDAY)),
        ),
    ),
)
