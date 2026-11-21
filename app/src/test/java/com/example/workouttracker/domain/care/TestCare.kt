package com.example.workouttracker.domain.care

import com.example.workouttracker.domain.compatibility.careChecklist

import com.example.workouttracker.domain.compatibility.defaultCareChecklist

import com.example.workouttracker.domain.compatibility.CareLabels

import com.example.workouttracker.domain.compatibility.LegacyCareSession

import com.example.workouttracker.domain.compatibility.LegacyCareAction

import com.example.workouttracker.domain.compatibility.CareChecklistId

import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.checklist.ChecklistDay
import com.example.workouttracker.domain.checklist.ChecklistItem
import com.example.workouttracker.domain.checklist.progressOf
import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.domain.checklist.ChecklistDayOrder
import com.example.workouttracker.domain.checklist.rulesOn
import com.example.workouttracker.domain.checklist.tickOf
import java.time.LocalDate

/** Care as an installation seeded in English has it. */
internal fun testCareChecklist(remindMorning: Boolean = false, remindEvening: Boolean = false) = defaultCareChecklist(
    CareLabels(
        checklist = "Care",
        morning = "Morning",
        evening = "Evening",
        toothbrushing = "Brush teeth",
        hairFoam = "Hair foam",
        cleanBetweenTeeth = "Clean between teeth",
    ),
    remindMorning = remindMorning,
    remindEvening = remindEvening,
)

/** One action done at one session, the way Care used to store it. */
internal data class CareDone(
    val dateKey: String,
    val session: LegacyCareSession,
    val action: LegacyCareAction,
    val timestampMillis: Long = 0,
)

internal val CareDone.tick: String get() = tickOf(session.storageId, action.storageId)

/** Every action at both sessions of [dateKey], due or not. */
internal fun everyCareAction(dateKey: String, timestampMillis: Long = 0): Array<CareDone> =
    LegacyCareSession.entries.flatMap { session -> LegacyCareAction.entries.map { CareDone(dateKey, session, it, timestampMillis) } }.toTypedArray()

/** The actions due at [session] on [dateKey] under Care's rules. */
internal fun dueCareActions(dateKey: String, session: LegacyCareSession, timestampMillis: Long = 0): Array<CareDone> =
    testCareChecklist().let { checklist ->
        checklist.rulesOn(LocalDate.parse(dateKey)).checkpoints.filter { it.checkpoint.id == session.storageId }.flatMap { it.items }
            .map { CareDone(dateKey, session, requireNotNull(LegacyCareAction.fromStorageId(it.id)), timestampMillis) }
            .toTypedArray()
    }

/**
 * [this] with Care configured, as every loaded state is, and [done] added to its days. A day holds
 * its ticks at the time of its latest one, as the migration from Care's old records builds it.
 */
internal fun AppState.withCare(vararg done: CareDone, remindMorning: Boolean = false, remindEvening: Boolean = false): AppState {
    val kept = checklistDays.filter { it.checklistId == CareChecklistId }
        .flatMap { day -> day.ticks.map { Triple(day.dateKey, it, day.timestampMillis) } }
    val added = done.map { Triple(it.dateKey, it.tick, it.timestampMillis) }
    val days = (kept + added).groupBy { it.first }.map { (dateKey, sameDay) ->
        ChecklistDay(CareChecklistId, dateKey, sameDay.maxOf { it.third }, sameDay.mapTo(LinkedHashSet()) { it.second })
    }
    return copy(
        checklists = checklists.filterNot { it.id == CareChecklistId } + testCareChecklist(remindMorning, remindEvening),
        checklistDays = (checklistDays.filterNot { it.checklistId == CareChecklistId } + days).sortedWith(ChecklistDayOrder),
    )
}

internal fun careState(vararg done: CareDone, remindMorning: Boolean = false, remindEvening: Boolean = false): AppState =
    AppState().withCare(*done, remindMorning = remindMorning, remindEvening = remindEvening)

/** The items due at [session] on [dateKey] that are not ticked yet, in the order Care lists them, as its reminder names them. */
internal fun AppState.openCareItems(dateKey: String, session: LegacyCareSession, daysOff: HolidaySchedule): List<ChecklistItem> =
    careChecklist?.let { progressOf(it, dateKey, daysOff).openAt(session.storageId) }.orEmpty()
