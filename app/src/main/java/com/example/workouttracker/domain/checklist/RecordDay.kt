package com.example.workouttracker.domain.checklist

import java.time.LocalDate

/**
 * The day whose record due day [dueDay] asks for, by the rule of the due day: the day itself, or
 * the day before for a checklist about yesterday. Everything that judges or asks about a due day
 * reads the record through this one place: the reward walk, the reminders and where they open the
 * checklist, the Reminders page's preview, and the day the checklist screen opens on. The record
 * itself stays the day's it is about, judged by that day's rules.
 */
fun ChecklistConfiguration.recordDayFor(dueDay: LocalDate): LocalDate = about.recordDayFor(dueDay)

/**
 * [recordDayFor], or null on the one day the day before already asked for the same record: the
 * day a checklist switches to being about yesterday. A record is asked for once, so that day is
 * not judged, neither kept nor a gap. The reward walk reads it this way, and Home's badge follows
 * the walk; a reminder on that day still asks for the record, because a late tick keeps the day it
 * is for.
 */
fun ChecklistConfiguration.recordJudgedOn(dueDay: LocalDate): LocalDate? = about.recordJudgedOn(dueDay)

/** The day whose record [dueDay] asks for under this rule, for every view that says which day a due day is about. */
fun Dated<AboutDay>.recordDayFor(dueDay: LocalDate): LocalDate = when (on(dueDay)) {
    AboutDay.Today -> dueDay
    AboutDay.Yesterday -> dueDay.minusDays(1)
}

/** [recordDayFor], or null on the day a view switches to being about yesterday, whose record the day before already asked for. */
fun Dated<AboutDay>.recordJudgedOn(dueDay: LocalDate): LocalDate? =
    recordDayFor(dueDay).takeUnless { it == recordDayFor(dueDay.minusDays(1)) }
