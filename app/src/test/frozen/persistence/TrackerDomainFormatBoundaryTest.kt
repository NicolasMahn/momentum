package com.example.workouttracker.persistence

import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.activity.withActivity
import com.example.workouttracker.domain.care.testCareChecklist
import com.example.workouttracker.domain.checklist.AboutDay
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.compatibility.LegacyQuestionPreset
import com.example.workouttracker.domain.measurement.MeasurementValuePolicy
import com.example.workouttracker.domain.measurement.MeasurementBank
import com.example.workouttracker.domain.measurement.MeasurementRewards
import com.example.workouttracker.domain.measurement.testMadeMeasurement
import com.example.workouttracker.domain.measurement.testWeightMeasurement
import com.example.workouttracker.domain.questions.QuestionEntry
import com.example.workouttracker.domain.questions.QuestionScale
import com.example.workouttracker.domain.questions.QuestionSource
import com.example.workouttracker.domain.questions.requireStorableQuestions
import com.example.workouttracker.domain.questions.testEatingQuestions
import com.example.workouttracker.domain.questions.testMadeQuestions
import com.example.workouttracker.domain.questions.testQuestion
import com.example.workouttracker.domain.time.TimeLog
import com.example.workouttracker.domain.time.TimeRewards
import com.example.workouttracker.domain.time.requireStorableTime
import com.example.workouttracker.domain.time.testMadeTime
import com.example.workouttracker.domain.time.testReadingTime
import com.example.workouttracker.persistence.json.AppStateJsonCodec
import java.time.LocalDate
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class TrackerDomainFormatBoundaryTest {
    @Test fun currentModelsDescribeCapabilitiesIndependentlyOfHistoricalIds() {
        val weekly = testMadeMeasurement(id = "arbitrary-measurement").copy(rewards = MeasurementBank.Week(MeasurementRewards(2, 3)))
        assertEquals(2, weekly.weekRewards!!.maxPasses.current)
        val timer = testMadeTime().copy(id = "arbitrary-timer", rewards = TimeRewards(2))
        assertEquals(null, timer.rewards!!.periods)
        val questions = testMadeQuestions().copy(id = "arbitrary-questions", madeOn = null, rewards = testEatingQuestions().rewards)
        assertEquals(questions.rewards, questions.standardRewards)
        assertEquals(LocalDate.parse("2020-01-01"), questions.recordDayFor(LocalDate.parse("2020-01-01")))
    }

    @Test fun catalogueSchemaAndEditabilityComeFromTheDeclaredSource() {
        val schema = listOf(QuestionScale("feeling", listOf("lower", "higher")))
        val questions = testMadeQuestions().copy(
            id = "any-tracker",
            questions = emptyList(),
            source = QuestionSource.Catalogue("wellbeing", schema),
        )
        assertEquals(schema, questions.scales)
        assertFalse(questions.questionsEditable)
        assertEquals(schema, questions.copy(id = "another-tracker").scales)
        assertThrows(IllegalArgumentException::class.java) { questions.copy(questions = listOf(testQuestion())) }
    }

    @Test fun inheritedQuestionWireShapeRecoversItsExplicitSourceWithoutAddingFields() {
        val view = testEatingQuestions()
        val state = AppState(questions = listOf(view)).withActivity()
        val encoded = AppStateJsonCodec.encode(state)
        val stored = JSONObject(encoded).getJSONArray("questions").getJSONObject(0)
        assertFalse(stored.has("questions"))
        assertFalse(stored.has("source"))
        val decoded = AppStateJsonCodec.decodeVersion30(encoded)
        assertEquals(view, decoded.questions.single())
        assertEquals(LegacyQuestionPreset.source, decoded.questions.single().source)
    }

    @Test fun theExistingFormatRefusesShapesItCannotRoundTripBeforeWritingAnything() {
        val catalogue = testMadeQuestions().copy(questions = emptyList(), source = QuestionSource.Catalogue("other", listOf(QuestionScale("question", listOf("one", "two")))))
        val states = listOf(
            AppState(measurements = listOf(testWeightMeasurement().copy(valuePolicy = MeasurementValuePolicy.Finite))),
            AppState(measurements = listOf(testMadeMeasurement().copy(valuePolicy = MeasurementValuePolicy.Positive))),
            AppState(measurements = listOf(testWeightMeasurement().copy(rewards = null))),
            AppState(measurements = listOf(testMadeMeasurement().copy(rewards = MeasurementBank.Week(MeasurementRewards(2, 3))))),
            AppState(questions = listOf(testEatingQuestions().copy(about = Dated.always(AboutDay.Today)))),
            AppState(questions = listOf(catalogue)),
            AppState(questions = listOf(testMadeQuestions().copy(madeOn = null))),
            AppState(time = listOf(testReadingTime().copy(rewards = null))),
            AppState(time = listOf(testMadeTime().copy(rewards = TimeRewards(2)))),
            AppState(checklists = listOf(testCareChecklist().copy(about = Dated.always(AboutDay.Yesterday)))),
        )
        states.forEach { state ->
            assertThrows(IllegalArgumentException::class.java) { AppStateJsonCodec.encode(state) }
            val store = MemoryStore()
            assertFalse(trackerRepository(store).save(state.withActivity()))
            assertEquals(emptyMap<String, String>(), store.values)
        }
    }

    @Test fun historicalRecordRestrictionsBelongToTheWireBoundary() {
        val entry = QuestionEntry("entry", "eating", "2026-10-18", 1L, mapOf("custom" to "answer"))
        requireStorableQuestions(emptyList(), listOf(entry))
        assertThrows(IllegalArgumentException::class.java) { AppStateJsonCodec.encode(AppState(questionEntries = listOf(entry))) }
        val mark = TimeLog("timer-without-duration", "2026-10-18", 1L)
        requireStorableTime(emptyList(), listOf(mark))
        assertThrows(IllegalArgumentException::class.java) { AppStateJsonCodec.encode(AppState(timeLogs = listOf(mark))) }
    }

    private class MemoryStore : TrackerStringStore {
        val values = mutableMapOf<String, String>()
        override fun read(key: String): String? = values[key]
        override fun write(key: String, value: String): Boolean = true.also { values[key] = value }
        override fun writeAll(values: Map<String, String>): Boolean = true.also { this.values.putAll(values) }
    }
}
