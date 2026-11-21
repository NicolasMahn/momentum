package com.example.workouttracker.persistence

import com.example.workouttracker.domain.measurement.MeasurementValuePolicy

import com.example.workouttracker.domain.measurement.MeasurementRecord
import com.example.workouttracker.app.featureProgressOn
import com.example.workouttracker.app.featuresLoggedOn
import com.example.workouttracker.domain.LoggedViewKind
import com.example.workouttracker.domain.LoggedViewRef
import com.example.workouttracker.domain.TrackerRef
import com.example.workouttracker.domain.TrackerTypeId
import com.example.workouttracker.domain.trackerRef
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.checklist.Checkpoint
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.RewardPeriod
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.holiday.DayRange
import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.domain.compatibility.isLoggedFor
import com.example.workouttracker.domain.measurement.MeasurementEdit
import com.example.workouttracker.domain.measurement.MeasurementLog
import com.example.workouttracker.domain.measurement.applyingMeasurementEdits
import com.example.workouttracker.domain.measurement.recordsOf
import com.example.workouttracker.domain.measurement.testMadeMeasurement
import com.example.workouttracker.domain.compatibility.WeightMeasurementId
import com.example.workouttracker.domain.compatibility.weightMeasurement
import com.example.workouttracker.domain.compatibility.weightRewards
import com.example.workouttracker.domain.compatibility.weightWhenAway
import com.example.workouttracker.domain.mo.MoLedger
import com.example.workouttracker.domain.compatibility.MoNeed
import com.example.workouttracker.domain.mo.MoRecordIndex
import com.example.workouttracker.domain.mo.habitStreak
import com.example.workouttracker.domain.compatibility.moFreeDays
import com.example.workouttracker.domain.mo.moMood
import com.example.workouttracker.domain.mo.outcome
import com.example.workouttracker.domain.mo.outcomeText
import com.example.workouttracker.domain.mo.only
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import com.example.workouttracker.domain.measurement.sevenDayMeasurementAverages
import com.example.workouttracker.feature.statistics.StatisticsRange
import com.example.workouttracker.feature.statistics.statisticsSummary
import com.example.workouttracker.feature.measurement.measurementAverageHistoryWindow
import com.example.workouttracker.feature.measurement.measurementDraftForDate
import com.example.workouttracker.persistence.json.AppStateJsonCodec
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.example.workouttracker.domain.questions.withEating
import com.example.workouttracker.domain.time.withReading
import com.example.workouttracker.domain.activity.withActivity

/**
 * A measurement a person made takes part in nothing Weight takes part in. With two of them
 * configured and logged, on the days Weight has records, on a day it has none, today and on a
 * day after today, everything that reads Weight answers exactly as it does without them: Weight's
 * records and its configuration, what is logged on Home and its progress, the reward walk and the
 * streak, Mo's ledger, record index, mood and free days, Statistics, and the in-app answer to
 * Weight's reminder. Through a save and a reload as well.
 */
class MadeMeasurementsReachNoWeightReaderTest {
    private val today = "2026-10-09"
    private val days = listOf("2026-10-05", "2026-10-06", "2026-10-07", "2026-10-08", "2026-10-09", "2026-10-12")
    private val waist = testMadeMeasurement()
    private val mood = testMadeMeasurement(id = "msr_m7xd", label = "Mood", unit = "", step = "1")

    private val weightAlone: AppState = AppStateJsonCodec.decodeVersion23(
        frozenVersion23(
            weightLogs = """[
                {"dateKey":"2026-10-08","timestampMillis":5,"weightKg":70},
                {"dateKey":"2026-10-08","timestampMillis":9,"weightKg":82},
                {"dateKey":"2026-10-07","timestampMillis":2,"weightKg":80},
                {"dateKey":"2026-10-06","timestampMillis":1,"weightKg":78}
            ]""",
            readingLogs = """[{"dateKey":"2026-10-08","timestampMillis":10}]""",
        ),
    ).withEating().withReading().withActivity()

    private val made = listOf(
        MeasurementLog(waist.id, "2026-10-12", 30, 95.0),
        MeasurementLog(mood.id, "2026-10-09", 99, 9.0),
        MeasurementLog(waist.id, "2026-10-09", 20, 92.5),
        MeasurementLog(waist.id, "2026-10-08", 99, 92.0),
        MeasurementLog(waist.id, "2026-10-08", 99, 92.0),
        MeasurementLog(mood.id, "2026-10-08", 98, 0.0),
        MeasurementLog(waist.id, "2026-10-07", 3, 91.5),
        MeasurementLog(waist.id, "2026-10-05", 1, 91.0),
    )

    /** The same state with both made measurements configured and their records between Weight's. */
    private val withMade: AppState = weightAlone.copy(
        measurements = listOf(waist) + weightAlone.measurements + mood,
        // By date, newest first, as storage keeps them, and within a date between Weight's.
        measurementLogs = (made.take(3) + weightAlone.measurementLogs.take(1) + made.drop(3).take(3) + weightAlone.measurementLogs.drop(1) + made.drop(6))
            .sortedByDescending { it.dateKey },
    )

    private fun day(dateKey: String) = LocalDate.parse(dateKey)

    /** Everything that reads Weight, for one state, [visibility] and holiday plan. */
    private fun AppState.everyAnswer(visibility: FeatureVisibilitySettings, holidays: HolidaySchedule): List<Any?> = weightsOwnAnswers(visibility, holidays) + listOf(
        outcomeText(habitStreak(this, visibility, day(today), holidays)),
        moMood(this, visibility, day(today), holidays).outcome(),
    )

    /**
     * What reads Weight alone: its records, its configuration, its screen's draft and average,
     * what Home and Statistics say of it, Mo's ledger and free days. The reward walk and the mood
     * it makes are not among them: they judge a day for every view in the streak together.
     */
    private fun AppState.weightsOwnAnswers(visibility: FeatureVisibilitySettings, holidays: HolidaySchedule): List<Any?> {
        val records = MoRecordIndex(this, holidays)
        val weightView = LoggedViewRef(LoggedViewKind.Measurement, WeightMeasurementId)
        return listOf(
            recordsOf("weight"),
            weightMeasurement,
            weightRewards,
            weightWhenAway,
            measurementAverageHistoryWindow(recordsOf("weight"), today, valuePolicy = MeasurementValuePolicy.Positive),
            sevenDayMeasurementAverages(recordsOf("weight"), day(today), valuePolicy = MeasurementValuePolicy.Positive),
            days.map { measurementDraftForDate(recordsOf("weight"), it, Locale.ENGLISH) },
            days.filter { isLoggedFor(MoNeed.Weight, it, holidays) },
            days.map { featuresLoggedOn(this, it, holidays) },
            days.map { featureProgressOn(this, it, holidays) },
            days.map { records.isLogged(weightView, day(it)) },
            MoLedger.of(this, visibility, day(today), holidays).let { ledger ->
                listOf(ledger.history(MoNeed.Weight.view.trackerRef), days.map { ledger.latestTrackerOn(day(it)) })
            },
            moFreeDays(this, visibility, day(today), holidays),
            StatisticsRange.entries.map { range ->
                val summary = statisticsSummary(this, today, range)
                listOf(
                    summary.measurements.single { it.configuration.id == WeightMeasurementId },
                    summary.consistency.single { it.ref == MoNeed.Weight.view.trackerRef },
                )
            },
        )
    }

    @Test
    fun everyReaderOfWeightAnswersAsItDoesWithoutThem() {
        val plans = listOf(HolidaySchedule(), HolidaySchedule(holidays = listOf(DayRange(day("2026-10-07"), day("2026-10-09")))))
        val visibilities = listOf(only(MoNeed.Weight), only(MoNeed.Weight, MoNeed.Reading), FeatureVisibilitySettings())
        plans.forEach { holidays ->
            visibilities.forEach { visibility ->
                assertEquals(weightAlone.everyAnswer(visibility, holidays), withMade.everyAnswer(visibility, holidays))
            }
        }
        // Weight's own records are what they were, and each made measurement has its own.
        assertEquals(weightAlone.recordsOf("weight"), withMade.recordsOf("weight"))
        assertEquals(listOf(95.0, 92.5, 92.0, 92.0, 91.5, 91.0), withMade.recordsOf(waist.id).map { it.value })
        assertEquals(listOf(MeasurementRecord("2026-10-09", 99, 9.0), MeasurementRecord("2026-10-08", 98, 0.0)), withMade.recordsOf(mood.id))
        // A day only a made measurement has a record on, today among them, is a day without a weight.
        assertFalse(withMade.isLoggedFor(MoNeed.Weight, today, HolidaySchedule()))
        assertFalse(withMade.isLoggedFor(MoNeed.Weight, "2026-10-05", HolidaySchedule()))
    }

    /**
     * In the streak, reminding and logged, a made measurement still reaches nothing that reads
     * Weight alone: with the waist joined days ago, not due on holiday and its reminder on, every
     * such reader answers as it does without it, before and after a save and a reload. Weight's
     * reminder is answered by a weight alone, as it was.
     */
    @Test
    fun whatReadsWeightAloneAnswersTheSameBesideAMeasurementThatCountsAndReminds() {
        val edits = listOf(MeasurementEdit.JoinRewards, MeasurementEdit.SetBankRules(maxPasses = 2, everyKeptDays = 3), MeasurementEdit.SetWhenAway(WhenAway.Excused))
        val counting = withMade.applyingMeasurementEdits(waist.id, edits, day("2026-10-05")).let { state ->
            state.copy(measurements = state.measurements.map { if (it.id == waist.id) it.copy(checkpoint = Checkpoint("day", CheckpointTime.AfterWake(30), remind = true)) else it })
        }
        assertEquals(listOf(RewardPeriod(day("2026-10-05"))), counting.measurements.first().standardRewards?.periods)
        fun reloaded(state: AppState) = MemoryStore().let { store ->
            assertTrue(trackerRepository(store).save(state))
            trackerRepository(store).load()
        }
        val stored = reloaded(counting)
        val storedAlone = reloaded(weightAlone)
        assertEquals(counting.measurements, stored.measurements)
        assertEquals(counting.measurementLogs, stored.measurementLogs)

        val plans = listOf(HolidaySchedule(), HolidaySchedule(holidays = listOf(DayRange(day("2026-10-07"), day("2026-10-09")))), HolidaySchedule().withSickDays(DayRange(day("2026-10-08"), day("2026-10-08"))))
        val visibilities = listOf(only(MoNeed.Weight), only(MoNeed.Weight, MoNeed.Reading), FeatureVisibilitySettings())
        plans.forEach { holidays ->
            visibilities.forEach { visibility ->
                assertEquals(weightAlone.weightsOwnAnswers(visibility, holidays), counting.weightsOwnAnswers(visibility, holidays))
                assertEquals(storedAlone.weightsOwnAnswers(visibility, holidays), stored.weightsOwnAnswers(visibility, holidays))
                // The walk has it: the streak's result holds its standing, and Weight's bank is Weight's still.
                val streak = habitStreak(counting, visibility, day(today), holidays)
                val measurementRef = TrackerRef(TrackerTypeId("measurement"), waist.id)
                val weightRef = MoNeed.Weight.view.trackerRef
                assertEquals(
                    setOf(weightRef, measurementRef),
                    streak.standings.keys.filterTo(linkedSetOf<TrackerRef>()) { it.type == measurementRef.type },
                )
                assertEquals(
                    habitStreak(weightAlone, visibility, day(today), holidays).standings[weightRef]?.passCapacity,
                    streak.standings[weightRef]?.passCapacity,
                )
            }
        }
        assertEquals(weightAlone.recordsOf("weight"), stored.recordsOf("weight"))
        assertEquals(weightAlone.weightMeasurement, stored.weightMeasurement)
    }

    @Test
    fun theyAnswerTheSameAfterASaveAndAReload() {
        fun reloaded(state: AppState) = MemoryStore().let { store ->
            assertTrue(trackerRepository(store).save(state))
            trackerRepository(store).load()
        }
        val stored = reloaded(withMade)
        assertEquals(withMade.measurements, stored.measurements)
        assertEquals(withMade.measurementLogs, stored.measurementLogs)
        assertEquals(reloaded(weightAlone).everyAnswer(FeatureVisibilitySettings(), HolidaySchedule()), stored.everyAnswer(FeatureVisibilitySettings(), HolidaySchedule()))
    }

    private class MemoryStore : TrackerStringStore {
        val values = mutableMapOf<String, String>()
        override fun read(key: String) = values[key]
        override fun write(key: String, value: String): Boolean = true.also { values[key] = value }
        override fun writeAll(values: Map<String, String>): Boolean = values.all { (key, value) -> write(key, value) }
    }
}
