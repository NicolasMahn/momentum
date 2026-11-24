package de.nicolasmahn.momentum.domain.care

import de.nicolasmahn.momentum.domain.mo.isCareSessionComplete
import de.nicolasmahn.momentum.domain.mo.isCareComplete
import de.nicolasmahn.momentum.domain.mo.isDailyCareComplete

import de.nicolasmahn.momentum.domain.compatibility.isCareSessionComplete

import de.nicolasmahn.momentum.domain.compatibility.LegacyCareSession

import de.nicolasmahn.momentum.domain.compatibility.LegacyCareAction

import de.nicolasmahn.momentum.domain.compatibility.MoNeed
import de.nicolasmahn.momentum.domain.CheckpointPart
import de.nicolasmahn.momentum.domain.compatibility.isDone
import de.nicolasmahn.momentum.domain.compatibility.isLoggedFor
import de.nicolasmahn.momentum.domain.mo.MoRecordIndex
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import de.nicolasmahn.momentum.domain.holiday.HolidaySchedule

/** Care's configuration judges every day exactly as the rules written in code did before it. */
class CareChecklistTest {
    private val slots = LegacyCareSession.entries.flatMap { session -> LegacyCareAction.entries.map { session to it } }

    @Test
    fun everyCombinationOfActionsOnEveryDayIsJudgedAsTheOldRulesDid() {
        // Across the weekly rule's start, so Saturdays before and after it are both covered.
        val days = generateSequence(LocalDate.parse("2026-09-14")) { it.plusDays(1) }.takeWhile { it <= LocalDate.parse("2026-10-25") }
        days.forEach { day ->
            val dateKey = day.toString()
            (0 until (1 shl slots.size)).forEach { mask ->
                val done = slots.filterIndexed { index, _ -> mask and (1 shl index) != 0 }
                val state = careState(*done.map { (session, action) -> CareDone(dateKey, session, action, 1L) }.toTypedArray())
                val records = MoRecordIndex(state, HolidaySchedule())
                LegacyCareSession.entries.forEach { session ->
                    val old = OldCareRules.sessionComplete(done, dateKey, session)
                    assertEquals("$dateKey $done $session", old, state.isCareSessionComplete(dateKey, session, HolidaySchedule()))
                    assertEquals("$dateKey $done $session", old, state.isDone(MoNeed.Care, CheckpointPart(session.storageId), dateKey, HolidaySchedule()))
                    assertEquals("$dateKey $done $session", old, records.isCareSessionComplete(day, session))
                }
                val complete = LegacyCareSession.entries.all { OldCareRules.sessionComplete(done, dateKey, it) }
                assertEquals("$dateKey $done", complete, state.isLoggedFor(MoNeed.Care, dateKey, HolidaySchedule()))
                assertEquals("$dateKey $done", complete, records.isCareComplete(day))
                assertEquals("$dateKey $done", OldCareRules.dailyComplete(done), records.isDailyCareComplete(day))
            }
        }
    }

    @Test
    fun theBetweenTeethCleaningIsAskedOnlyOnSaturdayEveningsFromItsStart() {
        val saturday = LocalDate.parse("2026-10-03")
        assertEquals(DayOfWeek.SATURDAY, saturday.dayOfWeek)
        val daily = LegacyCareAction.entries - LegacyCareAction.CLEAN_BETWEEN_TEETH
        fun dailyDone(day: LocalDate) = careState(*LegacyCareSession.entries.flatMap { session -> daily.map { CareDone(day.toString(), session, it) } }.toTypedArray())

        assertTrue(dailyDone(saturday.minusWeeks(1)).isLoggedFor(MoNeed.Care, saturday.minusWeeks(1).toString(), HolidaySchedule()))
        assertFalse(dailyDone(saturday).isLoggedFor(MoNeed.Care, saturday.toString(), HolidaySchedule()))
        assertTrue(dailyDone(saturday).isCareSessionComplete(saturday.toString(), LegacyCareSession.MORNING, HolidaySchedule()))
        assertTrue(dailyDone(saturday.plusDays(1)).isLoggedFor(MoNeed.Care, saturday.plusDays(1).toString(), HolidaySchedule()))
    }

    /** Care's rules as they were written before its configuration, kept here to compare against. */
    private object OldCareRules {
        private val weeklyStart = LocalDate.parse("2026-10-03")
        private val dailyActions = setOf(LegacyCareAction.TOOTHBRUSHING, LegacyCareAction.HAIR_FOAM)

        fun required(dateKey: String, session: LegacyCareSession): Set<LegacyCareAction> {
            val date = LocalDate.parse(dateKey)
            val weekly = !date.isBefore(weeklyStart) && date.dayOfWeek == DayOfWeek.SATURDAY
            return if (session == LegacyCareSession.EVENING && weekly) dailyActions + LegacyCareAction.CLEAN_BETWEEN_TEETH else dailyActions
        }

        fun sessionComplete(done: List<Pair<LegacyCareSession, LegacyCareAction>>, dateKey: String, session: LegacyCareSession) =
            required(dateKey, session).all { (session to it) in done }

        fun dailyComplete(done: List<Pair<LegacyCareSession, LegacyCareAction>>) =
            LegacyCareSession.entries.all { session -> dailyActions.all { (session to it) in done } }
    }
}
