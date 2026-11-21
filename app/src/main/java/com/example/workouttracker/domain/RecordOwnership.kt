package com.example.workouttracker.domain

import com.example.workouttracker.domain.checklist.ChecklistConfiguration
import com.example.workouttracker.domain.checklist.firstExceptionsOnlyDayMatching
import com.example.workouttracker.domain.checklist.recordJudgedOn
import java.time.LocalDate

/** The first due day that actually judges this record, retaining dated ownership changes. */
fun ChecklistConfiguration.judgmentDayFor(recordedDay: LocalDate): LocalDate? =
    recordJudgmentDay(recordedDay) { recordJudgedOn(it) }

/** A logged view's creation boundary and dated ownership both decide whether a record is judged. */
fun DailyView.judgmentDayFor(recordedDay: LocalDate): LocalDate? =
    recordJudgmentDay(recordedDay, ::recordJudgedOn)

/** Implicit checklist history may begin with a fact or an explicitly assumed task, on its due day. */
fun ChecklistConfiguration.firstJudgmentDay(recordedDates: Iterable<LocalDate>): LocalDate? {
    val recordedStart = recordedDates.asSequence().mapNotNull(::judgmentDayFor).minOrNull()
    val assumedStart = firstExceptionsOnlyDayMatching { judgmentDayFor(it) != null }?.let(::judgmentDayFor)
    return listOfNotNull(recordedStart, assumedStart).minOrNull()
}

private fun recordJudgmentDay(recordedDay: LocalDate, recordOn: (LocalDate) -> LocalDate?): LocalDate? =
    sequenceOf(recordedDay, recordedDay.plusDays(1)).firstOrNull { recordOn(it) == recordedDay }
