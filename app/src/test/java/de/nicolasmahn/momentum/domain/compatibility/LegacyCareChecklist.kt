package de.nicolasmahn.momentum.domain.compatibility

import de.nicolasmahn.momentum.domain.mo.TrackerMoPolicy
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.checklist.ChecklistConfiguration
import de.nicolasmahn.momentum.domain.checklist.ChecklistItem
import de.nicolasmahn.momentum.domain.checklist.ChecklistRewards
import de.nicolasmahn.momentum.domain.checklist.ChecklistTarget
import de.nicolasmahn.momentum.domain.checklist.Checkpoint
import de.nicolasmahn.momentum.domain.checklist.CheckpointTime
import de.nicolasmahn.momentum.domain.checklist.MoMotif
import de.nicolasmahn.momentum.domain.checklist.ViewHue
import de.nicolasmahn.momentum.domain.checklist.ViewIcon
import de.nicolasmahn.momentum.domain.checklist.checklist
import de.nicolasmahn.momentum.domain.checklist.progressOf
import de.nicolasmahn.momentum.domain.holiday.HolidaySchedule
import java.time.DayOfWeek
import java.time.LocalDate

/** The ID records, rewards and Mo know Care by. */
const val CareChecklistId = "care"

/** Care's actions; each storage ID is its item's ID, so a tick reads `morning/toothbrushing`. */
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

/**
 * Care's two checkpoints, each a part of its day with its own save, reminder and due time. Each
 * storage ID is its checkpoint's ID.
 */
enum class LegacyCareSession(val storageId: String, val builtInTime: CheckpointTime) {
    MORNING("morning", CheckpointTime.AfterWake(5)),
    EVENING("evening", CheckpointTime.BeforeSleep(15)),
    ;

    companion object {
        fun fromStorageId(storageId: String): LegacyCareSession? =
            entries.firstOrNull { it.storageId == storageId }
    }
}

/** The weekly between-teeth cleaning began with this Saturday; earlier days never asked for it. */
val WeeklyCareStart: LocalDate = LocalDate.parse("2026-10-03")

/** Care's rewards since they began: a bank of one, a pass for every fourteen kept days. */
val CareRewards = ChecklistRewards(maxPasses = 1, everyKeptDays = 14)

/** The words the default is written with, in the app language at the moment it is seeded. */
data class CareLabels(
    val checklist: String,
    val morning: String,
    val evening: String,
    val toothbrushing: String,
    val hairFoam: String,
    val cleanBetweenTeeth: String,
)

/**
 * Recovers the checklist from snapshots predating stored configurations. Dated defaults retain
 * the tasks each historical day asked for; recovered configurations then behave as ordinary
 * configured checklists and are never replaced by these defaults.
 */
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

/** Care's configuration, or null in a state that was never loaded through the repository. */
val AppState.careChecklist: ChecklistConfiguration? get() = checklist(CareChecklistId)

/** Whether nothing due at [session] on [dateKey] is unmet, ticked or assumed; never without Care's configuration. */
fun AppState.isCareSessionComplete(dateKey: String, session: LegacyCareSession, daysOff: HolidaySchedule): Boolean {
    val checklist = careChecklist ?: return false
    return progressOf(checklist, dateKey, daysOff).checkpoints.filter { it.checkpoint.id == session.storageId }.all { it.unmet.isEmpty() }
}
