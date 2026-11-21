package com.example.workouttracker.domain.questions

import com.example.workouttracker.domain.compatibility.EatingQuestionsId

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class QuestionEntryTest {
    private val eating = testEatingQuestions()
    private val sleep = testMadeQuestions(questions = listOf(testQuestion(id = "qn_rest"), testQuestion(id = "qn_mood", answers = 3)))

    private fun entry(id: String, dateKey: String, viewId: String = "eating", answers: Map<String, String> = emptyMap(), note: String? = null, at: Long = 100L) =
        QuestionEntry(id = id, viewId = viewId, dateKey = dateKey, timestampMillis = at, answers = answers, note = note)

    private fun refused(vararg entries: QuestionEntry) {
        assertThrows(IllegalArgumentException::class.java) { requireStorableQuestionEntries(entries.toList()) }
    }

    /** Each rule of point 8 on its own: one entry that breaks it is refused, whatever else is stored. */
    @Test fun anEntryThatBreaksARuleIsRefused() {
        val good = entry("good", "2026-09-15", answers = mapOf("quality" to "good"), note = "x".repeat(240))
        requireStorableQuestionEntries(listOf(good, entry("zero", "2026-09-14", at = 0L)))
        refused(good, entry("", "2026-09-14"))
        refused(good, entry(" ", "2026-09-14"))
        refused(good, entry("view", "2026-09-14", viewId = ""))
        refused(good, entry("date", "2026-02-30"))
        refused(good, entry("date", "2026-9-14"))
        refused(good, entry("time", "2026-09-14", at = -1L))
        refused(good, entry("note", "2026-09-14", note = "x".repeat(241)))
        refused(good, entry("blank", "2026-09-14", viewId = "qst_k2p9", answers = mapOf("qn_rest" to " ")))
        refused(good, entry("blank", "2026-09-14", viewId = "qst_k2p9", answers = mapOf("" to "a1")))
    }

    /** IDs are unique over every view, and a view has one entry per date. */
    @Test fun idsAreUniqueAndAViewHasOneEntryPerDate() {
        val first = entry("first", "2026-09-15")
        requireStorableQuestionEntries(listOf(first, entry("second", "2026-09-15", viewId = "qst_k2p9"), entry("third", "2026-09-14")))
        refused(first, entry("first", "2026-09-14"))
        refused(first, entry("first", "2026-09-14", viewId = "qst_k2p9"))
        refused(first, entry("second", "2026-09-15"))
    }

    /** The domain keeps answer facts as stored; historical schema restrictions belong to the wire-format boundary. */
    @Test fun storedEntriesKeepAnswersWithoutKnowingTheirQuestionSchema() {
        val inheritedId = entry("inherited", "2026-09-15", viewId = EatingQuestionsId, answers = mapOf("new_question" to "new_answer"))
        val customId = entry("custom", "2026-09-14", viewId = "sleep", answers = mapOf("qn_gone" to "a9", "qn_rest" to "removed"))

        requireStorableQuestionEntries(listOf(inheritedId, customId))
        assertEquals(mapOf("new_question" to "new_answer"), inheritedId.answers)
        assertEquals(mapOf("qn_gone" to "a9", "qn_rest" to "removed"), customId.answers)
    }

    /** A first save stores one entry under the new ID, stamped with the time of writing, with the note trimmed. */
    @Test fun aFirstSaveStoresOneEntryStampedWithItsTime() {
        val saved = saveQuestionsDay(emptyList(), "eating", eating.scales, "2026-09-15", 500L, mapOf("quality" to "good"), "  Dinner with friends  ", newEntryId = "new")
        assertEquals(listOf(QuestionEntry("new", "eating", "2026-09-15", 500L, mapOf("quality" to "good"), "Dinner with friends")), saved)

        // No answer and no note is an entry too: it keeps the day.
        val unsure = saveQuestionsDay(saved, "eating", eating.scales, "2026-09-14", 600L, emptyMap(), "   ", newEntryId = "unsure")
        assertEquals(QuestionEntry("unsure", "eating", "2026-09-14", 600L), unsure.last())
        assertEquals(listOf("new", "unsure"), unsure.map { it.id })
    }

    /** Saving a day again keeps the entry's ID, takes the new time and the new answers, and touches no other entry. */
    @Test fun savingADayAgainKeepsItsIdAndStampsTheNewTime() {
        val other = entry("other", "2026-09-15", viewId = "qst_k2p9", answers = mapOf("qn_rest" to "a1"))
        val untouched = entry("untouched", "2026-09-14", answers = mapOf("amount" to "much_more"))
        val target = entry("target", "2026-09-15", answers = mapOf("quality" to "fair", "amount" to "much_less"), note = "Before")

        val edited = saveQuestionsDay(listOf(other, target, untouched), "eating", eating.scales, "2026-09-15", 900L, mapOf("quality" to "very_good"), null, newEntryId = "unused")

        assertEquals(QuestionEntry("target", "eating", "2026-09-15", 900L, mapOf("quality" to "very_good")), edited.first { it.id == "target" })
        assertEquals(other, edited.first { it.id == "other" })
        assertEquals(untouched, edited.first { it.id == "untouched" })
        assertEquals(3, edited.size)
    }

    /**
     * A save changes only what its form could show. The answer of a question removed since stays,
     * and so does the mood's, an answer removed from a question the view still asks, which the form
     * showed as "Not sure": through an edit of another question, a visible answer cleared, and a
     * change of the note alone.
     */
    @Test fun aSaveLeavesWhatItsFormCouldNotShow() {
        val stored = entry("day", "2026-10-14", viewId = sleep.id, answers = mapOf("qn_gone" to "a2", "qn_rest" to "a5", "qn_mood" to "a9"), note = "Before")
        fun saveOver(entry: QuestionEntry, answers: Map<String, String>, note: String?) =
            saveQuestionsDay(listOf(entry), sleep.id, sleep.scales, entry.dateKey, entry.timestampMillis + 1, answers, note, newEntryId = "unused").single()

        val rested = saveOver(stored, mapOf("qn_rest" to "a1"), "Before")
        assertEquals(stored.copy(timestampMillis = 101L, answers = mapOf("qn_gone" to "a2", "qn_rest" to "a1", "qn_mood" to "a9")), rested)

        val cleared = saveOver(rested, emptyMap(), "Before")
        assertEquals(mapOf("qn_gone" to "a2", "qn_mood" to "a9"), cleared.answers)

        val noted = saveOver(cleared, emptyMap(), "After")
        assertEquals(stored.copy(timestampMillis = 103L, answers = mapOf("qn_gone" to "a2", "qn_mood" to "a9"), note = "After"), noted)
    }

    /** What the screen cannot offer is refused: an answer the view does not have, a date that is none, a note too long, an ID already taken. */
    @Test fun aSaveRefusesWhatTheViewDoesNotAskAndWhatCannotBeStored() {
        fun save(dateKey: String = "2026-10-14", answers: Map<String, String> = emptyMap(), note: String? = null, id: String = "new", at: Long = 1L) =
            saveQuestionsDay(listOf(entry("taken", "2026-10-13", viewId = sleep.id)), sleep.id, sleep.scales, dateKey, at, answers, note, id)
        assertEquals(2, save().size)
        listOf<() -> Any>(
            { save(answers = mapOf("qn_rest" to "a6")) },
            { save(answers = mapOf("quality" to "good")) },
            { save(dateKey = "2026-02-30") },
            { save(note = "x".repeat(241)) },
            { save(id = "taken") },
            { save(at = -1L) },
        ).forEach { attempt -> assertThrows(IllegalArgumentException::class.java) { attempt() } }
    }

    /** Removing a day removes that view's entry of that date and nothing else. */
    @Test fun removingADayTouchesThatViewsDateAlone() {
        val eatingDay = entry("eating", "2026-09-15")
        val sleepDay = entry("sleep", "2026-09-15", viewId = sleep.id)
        val earlier = entry("earlier", "2026-09-14")
        assertEquals(listOf(sleepDay, earlier), deleteQuestionsDay(listOf(eatingDay, sleepDay, earlier), "eating", "2026-09-15"))
        assertEquals(listOf(eatingDay), deleteQuestionsDay(listOf(eatingDay), "eating", "2026-09-16"))
    }
    @Test fun aDueDaysCutoffIsInclusiveAndLateEntriesRemainRecorded() {
        val due = LocalDate.parse("2026-10-12")
        val recordDate = due.minusDays(1).toString()
        val cutoffMillis = due.plusDays(1).atTime(4, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val atCutoff = entry("cutoff", recordDate, at = cutoffMillis)
        val late = atCutoff.copy(timestampMillis = cutoffMillis + 1)

        assertTrue(areQuestionsLoggedOnTimeFor(listOf(atCutoff), EatingQuestionsId, recordDate, due.toString()))
        assertFalse(areQuestionsLoggedOnTimeFor(listOf(late), EatingQuestionsId, recordDate, due.toString()))
        assertTrue(hasQuestionsEntryOn(listOf(late), EatingQuestionsId, recordDate))
    }

    @Test fun anEntryOnlySatisfiesItsOwnViewAndRequestedRecordDateEvenWithoutAnswers() {
        val due = "2026-10-12"
        val saved = entry("day", due, viewId = sleep.id, at = 0)

        assertTrue(hasQuestionsEntryOn(listOf(saved), sleep.id, due))
        assertTrue(areQuestionsLoggedOnTimeFor(listOf(saved), sleep.id, due, due))
        assertFalse(hasQuestionsEntryOn(listOf(saved), EatingQuestionsId, due))
        assertFalse(areQuestionsLoggedOnTimeFor(listOf(saved), EatingQuestionsId, due, due))
        assertFalse(areQuestionsLoggedOnTimeFor(listOf(saved), sleep.id, "2026-10-11", due))
    }

}
