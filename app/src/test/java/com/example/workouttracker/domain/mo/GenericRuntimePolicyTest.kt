package com.example.workouttracker.domain.mo

import com.example.workouttracker.domain.ActivityTrackerRef
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.CheckpointPart
import com.example.workouttracker.domain.TrackerRef
import com.example.workouttracker.domain.TrackerTypeId
import com.example.workouttracker.domain.activity.ActivityEntry
import com.example.workouttracker.domain.activity.requireStorableActivity
import com.example.workouttracker.domain.activity.testActivityConfiguration
import com.example.workouttracker.domain.checklist.AboutDay
import com.example.workouttracker.domain.checklist.ChecklistConfiguration
import com.example.workouttracker.domain.checklist.ChecklistDay
import com.example.workouttracker.domain.checklist.ChecklistItem
import com.example.workouttracker.domain.checklist.ChecklistRewards
import com.example.workouttracker.domain.checklist.ChecklistTarget
import com.example.workouttracker.domain.checklist.Checkpoint
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.checklist.MoMotif
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.domain.measurement.MeasurementBank
import com.example.workouttracker.domain.measurement.MeasurementLog
import com.example.workouttracker.domain.measurement.testMadeMeasurement
import com.example.workouttracker.domain.questions.QuestionEntry
import com.example.workouttracker.domain.questions.testMadeQuestions
import com.example.workouttracker.domain.ref
import com.example.workouttracker.domain.reminder.ReminderSettings
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import com.example.workouttracker.domain.substance.SubstanceSettings
import com.example.workouttracker.domain.time.TimeLog
import com.example.workouttracker.domain.time.TimeRewards
import com.example.workouttracker.domain.time.testMadeTime
import com.example.workouttracker.domain.trackerRef
import com.example.workouttracker.feature.statistics.StatisticsRange
import com.example.workouttracker.feature.statistics.statisticsSummary
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GenericRuntimePolicyTest {
    private val monday = LocalDate.parse("2026-10-19")
    private val today = monday.plusDays(3)
    private val visibility = FeatureVisibilitySettings()
    private val daysOff = HolidaySchedule()
    private val reservedNames = listOf("weight", "eating", "reading", "care", "supplements")
    private val checkpoint = Checkpoint("day", CheckpointTime.Fixed(LocalTime.of(13, 17)), remind = true)
    private val bank = ChecklistRewards(maxPasses = 4, everyKeptDays = 2)

    private fun checklist(id: String = "arbitrary-checklist", label: String = "Routine") = ChecklistConfiguration(
        id, label, ViewIcon.Checklist, ViewHue.Sky,
        checkpoints = listOf(checkpoint), rewards = bank,
        items = listOf(ChecklistItem("task", "Task")), mo = TrackerMoPolicy(true, MoMotif.Teeth),
    )

    private fun evaluation(state: AppState) = MoEvaluation(state, visibility, today)
    private fun catalogue(state: AppState) = trackerRewards(MoRecordIndex(state, daysOff), visibility, daysOff, today)

    private fun snapshot(state: AppState, ref: TrackerRef): List<Any?> {
        val observation = evaluation(state).observations.single { it.ref == ref }
        val ledger = MoLedger.of(state, visibility, today)
        val mood = evaluation(state).moodWithTrackerChoices(emptySet())
        val reward = catalogue(state).entries.single { it.ref == ref }
        return listOf(
            ledger.history(ref), observation.recordedAt, observation.content, observation.participatesInMood,
            observation.partsOn(today), observation.partKeptOn(CheckpointPart("day"), today),
            reward.participant.rules, reward.participant.began, mood.state,
            mood.streak.standings[ref], mood.trackerProgress[ref], mood.trackerWeekProgress[ref],
            mood.trackerObligations(ReminderSettings())[ref]?.map { it.since },
        )
    }

    @Test fun reservedChecklistIdsAndLabelsHaveExactlyTheConfiguredRuntime() {
        fun state(configuration: ChecklistConfiguration) = AppState(
            checklists = listOf(configuration),
            checklistDays = listOf(ChecklistDay(configuration.id, monday.toString(), 1, setOf("day/task"))),
        )
        val ordinary = checklist()
        val expected = snapshot(state(ordinary), TrackerRef(TrackerTypeId("checklist"), ordinary.id))
        reservedNames.forEach { name ->
            listOf(checklist(id = name), checklist(label = name)).forEach { configured ->
                val ref = TrackerRef(TrackerTypeId("checklist"), configured.id)
                assertEquals(name, expected, snapshot(state(configured), ref))
            }
        }
    }

    @Test fun eachLoggedKindUsesConfigurationUnderBothArbitraryAndFormerIds() {
        fun measurement(id: String) = testMadeMeasurement(id).copy(
            rewards = MeasurementBank.Standard(bank), checkpoint = checkpoint, mo = TrackerMoPolicy(true),
        ).let { configured ->
            AppState(measurements = listOf(configured), measurementLogs = listOf(MeasurementLog(id, monday.toString(), 1, -2.0))) to configured.ref.trackerRef
        }
        fun questions(id: String) = testMadeQuestions(id, madeOn = monday).copy(
            rewards = bank, checkpoint = checkpoint, mo = TrackerMoPolicy(true),
        ).let { configured ->
            AppState(questions = listOf(configured), questionEntries = listOf(QuestionEntry("entry", id, monday.toString(), 1, mapOf("qn_rest" to "a1")))) to configured.ref.trackerRef
        }
        fun timer(id: String) = testMadeTime(id, rewards = TimeRewards(4)).copy(
            checkpoint = checkpoint, mo = TrackerMoPolicy(true, MoMotif.Book),
        ).let { configured ->
            AppState(time = listOf(configured), timeLogs = listOf(TimeLog(id, monday.toString(), 1, 10))) to configured.ref.trackerRef
        }
        listOf(::measurement to "weight", ::questions to "eating", ::timer to "reading").forEach { (make, oldId) ->
            val (ordinary, ref) = make("my-own-id")
            val (reserved, reservedRef) = make(oldId)
            assertEquals(oldId, snapshot(ordinary, ref), snapshot(reserved, reservedRef))
        }
    }

    @Test fun moWatchesWhatCountsTowardTheStreakWhateverItIsCalled() {
        reservedNames.forEach { id ->
            val configured = checklist(id).copy(mo = TrackerMoPolicy())
            val state = AppState(checklists = listOf(configured), checklistDays = listOf(ChecklistDay(id, monday.toString(), 1, setOf("day/task"))))
            // The stored flag is off, and the bank is what makes it one of his tasks.
            assertFalse(evaluation(state).observations.single { it.ref.id == id }.participatesInMood)
            assertTrue(catalogue(state).entries.any { it.ref.id == id })
            assertEquals(1, evaluation(state).moodWithTrackerChoices(emptySet()).dueToday)
            val outside = state.copy(checklists = listOf(configured.copy(rewards = null)))
            assertEquals(0, evaluation(outside).moodWithTrackerChoices(emptySet()).dueToday)
        }
        val withoutBank = checklist().copy(rewards = null)
        val state = AppState(checklists = listOf(withoutBank), checklistDays = listOf(ChecklistDay(withoutBank.id, monday.toString(), 1, setOf("day/task"))))
        assertTrue(catalogue(state).entries.isEmpty())
        assertEquals(1, evaluation(state).moodWithTrackerChoices(emptySet()).dueToday)
        assertTrue(testActivityConfiguration().mo.enabled)
        assertFalse(SubstanceSettings().mo.enabled)
        assertFalse(testMadeMeasurement("weight").mo.enabled)
        assertFalse(testMadeQuestions("eating").mo.enabled)
        assertFalse(testMadeTime("reading").mo.enabled)
    }

    @Test fun lookBackOwnershipAndDeadlinesComeFromConfigurationForEveryName() {
        (reservedNames + "my-own-id").forEach { id ->
            val configured = checklist(id).copy(about = Dated.always(AboutDay.Yesterday))
            val state = AppState(checklists = listOf(configured), checklistDays = listOf(ChecklistDay(id, monday.toString(), 1, setOf("day/task"))))
            val ref = TrackerRef(TrackerTypeId("checklist"), id)
            val observation = evaluation(state).observations.single { it.ref == ref }
            assertEquals(setOf(monday.plusDays(1)), observation.recordedAt.keys)
            assertEquals(monday.plusDays(1), catalogue(state).entries.single { it.ref == ref }.participant.began)
            assertEquals(MoStatus.Kept, observation.statuses(today, daysOff)[monday.plusDays(1)])
            assertEquals(listOf(LocalTime.of(13, 17)), evaluation(state).moodWithTrackerChoices(emptySet()).trackerObligations(ReminderSettings()).getValue(ref).map { it.since })
        }
    }

    @Test fun enrollmentWithoutRecordsInventsNeitherRecordsNorStartedTasks() {
        val state = AppState(
            checklists = listOf(checklist()),
            measurements = listOf(testMadeMeasurement("weight").copy(mo = TrackerMoPolicy(true))),
            questions = listOf(testMadeQuestions("eating").copy(mo = TrackerMoPolicy(true))),
            time = listOf(testMadeTime("reading").copy(mo = TrackerMoPolicy(true))),
            activity = testActivityConfiguration(), substances = SubstanceSettings(mo = TrackerMoPolicy(true)),
        )
        assertTrue(evaluation(state).observations.all { it.firstDay == null && it.recordedAt.isEmpty() })
        assertEquals(0, evaluation(state).moodWithTrackerChoices(emptySet()).dueToday)
        assertTrue(evaluation(state).todayProgress().isEmpty())
    }

    @Test fun checklistActionsDistinguishNewOwedTicksFromUndoingMissesAndOffScheduleTicks() {
        val configured = checklist().copy(items = listOf(
            ChecklistItem("task", "Task"),
            ChecklistItem("assumed", "Assumed", exceptionsOnly = Dated.always(true)),
            ChecklistItem("saturday", "Saturday", target = ChecklistTarget.Weekdays(setOf(DayOfWeek.SATURDAY))),
        ))
        fun observe(ticks: Set<String>, misses: Set<String> = emptySet(), configuration: ChecklistConfiguration = configured): TrackerObservation {
            val state = AppState(checklists = listOf(configuration), checklistDays = listOf(ChecklistDay(configuration.id, today.toString(), 100, ticks, misses)))
            return evaluation(state).observations.single { it.ref.id == configured.id }
        }
        fun answered(before: TrackerObservation, after: TrackerObservation): Set<String> =
            (after.recordedActionKeysOn(today) - before.recordedActionKeysOn(today)) intersect requireNotNull(before.owedActionKeysOn).invoke(today)
        val before = observe(setOf("day/task"), setOf("day/assumed"))
        assertTrue(answered(before, observe(setOf("day/task"))).isEmpty())
        assertTrue(answered(before, observe(setOf("day/task", "day/saturday"))).isEmpty())
        val untouched = observe(emptySet(), setOf("day/assumed"))
        assertEquals(setOf("day/task"), answered(untouched, before))
        val offScheduleOnly = observe(setOf("day/saturday"))
        assertFalse(today in offScheduleOnly.recordedAt)
        val assumed = observe(setOf("day/task"), configuration = configured.copy(items = configured.items.map { it.copy(exceptionsOnly = Dated.always(true)) }))
        assertTrue(assumed.recordedAt.isEmpty())
        assertTrue(answered(before, assumed).isEmpty())
    }

    @Test fun rewardProtectionNeverChangesMeasurementFactsOrStatistics() {
        val configured = testMadeMeasurement("weight").copy(rewards = MeasurementBank.Standard(bank), mo = TrackerMoPolicy(true))
        val logs = (0L..2L).map { MeasurementLog(configured.id, monday.plusDays(it).toString(), it + 1, -2.0 + it) }
        val state = AppState(measurements = listOf(configured), measurementLogs = logs)
        val withoutRewards = state.copy(measurements = listOf(configured.copy(rewards = null)))
        fun facts(input: AppState) = statisticsSummary(input, today.toString(), StatisticsRange.FourWeeks).let { summary ->
            summary.consistency to summary.measurements.map { listOf(it.window, it.latestValue, it.change) }
        }
        assertEquals(facts(withoutRewards), facts(state))
        val evaluation = evaluation(state)
        val protected = evaluation.moodWithTrackerChoices(setOf(configured.ref.trackerRef))
        assertTrue(protected.keptTrackersToday.isEmpty())
        assertFalse(configured.ref.trackerRef in protected.missingTrackersToday)
        assertEquals(logs, state.measurementLogs)
        assertEquals(facts(withoutRewards), facts(state))
        assertEquals(3, facts(state).first.single { it.ref == configured.ref.trackerRef }.weeks.sumOf { it.kept })
    }

    @Test fun anAbsentActivityConfigurationPreservesOrphanedRecordsWithoutEnrollment() {
        val entries = listOf(ActivityEntry("entry", "unknown-activity", "My activity", monday.toString(), 1))
        requireStorableActivity(null, entries, emptyList())
        val state = AppState(activityEntries = entries)
        assertTrue(catalogue(state).entries.none { it.ref == ActivityTrackerRef })
        assertTrue(evaluation(state).observations.none { it.ref == ActivityTrackerRef })
        assertEquals(entries, state.activityEntries)
    }

    @Test fun evaluationExposesTheExactAdditionalObservationUsedByItsCatalogue() {
        val extra = TrackerObservation(
            TrackerRef(TrackerTypeId("future-kind"), "own-id"), monday, emptyMap(),
            ObservationTarget.Daily({ false }), { true }, { true }, participatesInMood = true,
            content = MoContentKey(MoViewKind.Checklist),
        )
        val evaluation = MoEvaluation(AppState(), visibility, today, additionalObservations = listOf(extra))
        assertSame(extra, evaluation.observations.single { it.ref == extra.ref })
        assertEquals(listOf(extra.ref), evaluation.moodWithTrackerChoices(emptySet()).missingTrackersToday)
    }
}
