package com.example.workouttracker.domain.substance

import com.example.workouttracker.domain.checklist.AboutDay
import com.example.workouttracker.domain.checklist.Dated
import java.time.LocalDate

/**
 * Substances is always about yesterday. Staying away is a thing not done, and "nothing" can only
 * honestly be said of a day that is over, so each day judges the record of the day before and the
 * person has no choice to make.
 */
val SubstancesAbout: Dated<AboutDay> = Dated.always(AboutDay.Yesterday)

/** The record due day [dueDay] judges: the day before it. */
fun substanceRecordJudgedOn(dueDay: LocalDate): LocalDate = dueDay.minusDays(1)

/** The due day that judges the record of [recordedDay]: the day after it. */
fun substanceJudgmentDayFor(recordedDay: LocalDate): LocalDate = recordedDay.plusDays(1)

/** The day the screen opens on and the check-in asks about: yesterday. */
fun substanceAskedDay(today: LocalDate): LocalDate = substanceRecordJudgedOn(today)

/** Whether "nothing" may be said of [day]: only once it is over. */
fun canSayNoSubstanceOn(day: LocalDate, today: LocalDate): Boolean = day < today
