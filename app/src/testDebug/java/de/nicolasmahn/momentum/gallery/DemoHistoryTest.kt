package de.nicolasmahn.momentum.gallery

import de.nicolasmahn.momentum.domain.measurement.recordsOf
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.activity.activityRules
import de.nicolasmahn.momentum.domain.questions.QuestionEntry
import de.nicolasmahn.momentum.domain.questions.saveQuestionsDay
import de.nicolasmahn.momentum.domain.substance.saveSubstanceLog
import de.nicolasmahn.momentum.persistence.json.AppStateJsonCodec
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The demo history is only useful if the app would accept every record in it and if two fills on
 * the same day look the same. Both are easy to break while tuning the invented numbers.
 */
class DemoHistoryTest {
    private val todayKey = "2026-09-19"
    private val state = demoHistory(todayKey)

    @Test
    fun `fills are identical for the same day`() {
        assertEquals(state, demoHistory(todayKey))
    }

    @Test
    fun `survives the store's own encoding`() {
        assertEquals(state, AppStateJsonCodec.decodeVersion32(AppStateJsonCodec.encode(state)))
    }

    @Test
    fun `every substance log passes the app's validation`() {
        val replayed = state.substanceLogs.fold(AppState()) { accumulated, log ->
            saveSubstanceLog(accumulated, log)
        }
        assertEquals(state.substanceLogs.toSet(), replayed.substanceLogs.toSet())
    }

    @Test
    fun `every eating entry passes the app's validation`() {
        val eating = state.questions.single()
        val replayed = state.questionEntries.fold(emptyList<QuestionEntry>()) { accumulated, entry ->
            saveQuestionsDay(accumulated, eating.id, eating.scales, entry.dateKey, entry.timestampMillis, entry.answers, entry.note, newEntryId = entry.id)
        }
        assertEquals(state.questionEntries.toSet(), replayed.toSet())
        assertTrue(state.questionEntries.all { it.viewId == DemoEatingQuestions.id })
    }

    @Test
    fun `every logged exercise belongs to its routine`() {
        state.sessions.forEach { session ->
            val planExerciseIds = state.activityRules.activity(session.activityId)!!.day(session.day)!!
                .exercises.mapTo(mutableSetOf()) { it.id }
            session.exerciseLogs.forEach { log ->
                assertTrue(
                    "${log.exerciseId} is not part of ${session.day}",
                    log.exerciseId in planExerciseIds,
                )
            }
        }
        assertTrue(state.sessions.all { it.totalLoggedSets > 0 })
    }

    @Test
    fun `every activity entry names a listed activity`() {
        val activityIds = state.activityRules.activities.mapTo(mutableSetOf()) { it.id }
        assertTrue(state.activityEntries.all { it.activityId in activityIds })
    }

    @Test
    fun `the weight history spans months and keeps its holiday gap`() {
        val dates = state.recordsOf(DemoWeightMeasurement.id).map { LocalDate.parse(it.dateKey) }.sorted()
        assertTrue(dates.size > 100)
        assertTrue(dates.first().isBefore(LocalDate.parse(todayKey).minusMonths(5)))

        val longestGapDays = dates.zipWithNext { earlier, later ->
            ChronoUnit.DAYS.between(earlier, later)
        }.max()
        assertTrue("Longest gap was $longestGapDays days", longestGapDays >= 9)
    }
}
