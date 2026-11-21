package com.example.workouttracker.persistence

import com.example.workouttracker.domain.measurement.MeasurementValuePolicy

import com.example.workouttracker.domain.measurement.recordsOf
import com.example.workouttracker.domain.trackerRef

import com.example.workouttracker.domain.LoggedViewKind
import com.example.workouttracker.domain.LoggedViewRef
import com.example.workouttracker.domain.measurement.MeasurementRecord
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.domain.compatibility.isLoggedFor
import com.example.workouttracker.domain.compatibility.WeightMeasurementId
import com.example.workouttracker.domain.measurement.MeasurementLog
import com.example.workouttracker.domain.mo.MoLedger
import com.example.workouttracker.domain.compatibility.MoNeed
import com.example.workouttracker.domain.mo.MoRecordIndex
import com.example.workouttracker.domain.mo.MoStatus
import com.example.workouttracker.domain.mo.habitStreak
import com.example.workouttracker.domain.mo.moMood
import com.example.workouttracker.domain.mo.only
import com.example.workouttracker.domain.measurement.MeasurementAverage
import com.example.workouttracker.domain.measurement.sevenDayMeasurementAverages
import com.example.workouttracker.feature.statistics.StatisticsRange
import com.example.workouttracker.feature.statistics.WeekConsistency
import com.example.workouttracker.feature.statistics.statisticsSummary
import com.example.workouttracker.feature.measurement.MeasurementDraft
import com.example.workouttracker.feature.measurement.measurementAverageHistoryWindow
import com.example.workouttracker.feature.measurement.measurementDraftForDate
import com.example.workouttracker.persistence.json.AppStateJsonCodec
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.example.workouttracker.domain.questions.withEating
import com.example.workouttracker.domain.time.withReading
import com.example.workouttracker.domain.activity.withActivity

/**
 * What Weight's readers answer for records as a stored state holds them, with the answers they gave
 * while a record was a date and a number: the records in the order the decoder returns them, the
 * chart's points and its average line, the 7-day average, the draft the screen opens on, what keeps
 * a day for Home, Statistics, Mo and the reward walk, when Weight began, when Mo saw it logged last,
 * and the in-app answer to a reminder.
 *
 * Each reader has its own way of choosing among several records of a day, and the states here are
 * the ones where those ways differ. Every state is a version 23 payload, whose weight records name
 * no measurement, and each expectation holds for every way that payload reaches a reader
 * ([everyWay]): a record migrated to a measurement record of Weight is read exactly as it was.
 */
class WeightRecordReadersTest {
    /** A Friday after the reward rules began, so the walk judges by the rules in force today. */
    private val today = "2026-10-09"
    private val onlyWeight = only(MoNeed.Weight)

    /** One way a stored state reaches its readers. */
    private class Stored(private val read: (String) -> AppState) {
        fun state(weightLogs: String, readingLogs: String = "[]"): AppState = read(frozenVersion23(weightLogs, readingLogs)).also { state ->
            // Each weight record is one stored record of `weight`, with its day, its time and its number.
            assertEquals(state.recordsOf("weight").map { MeasurementLog("weight", it.dateKey, it.timestampMillis, it.value) }, state.measurementLogs)
        }
    }

    /**
     * The payload as version 23's own decoder reads it, as the repository migrates it, as the state
     * that migration wrote loads again, and after one more round trip in the current shape.
     */
    private fun everyWay(check: (Stored) -> Unit) {
        check(Stored { raw -> AppStateJsonCodec.decodeVersion23(raw).withEating().withReading().withActivity() })
        check(Stored { raw -> trackerRepository(MemoryStore(PreferencesTrackerRepository.STATE_V23_KEY to raw)).load() })
        check(
            Stored { raw ->
                val store = MemoryStore(PreferencesTrackerRepository.STATE_V23_KEY to raw)
                trackerRepository(store).load()
                assertEquals(raw, store.values[PreferencesTrackerRepository.STATE_V23_KEY])
                AppStateJsonCodec.decodeVersion30(store.values.getValue(PreferencesTrackerRepository.STATE_V30_KEY))
            },
        )
        check(Stored { raw -> AppStateJsonCodec.decodeVersion30(AppStateJsonCodec.encode(AppStateJsonCodec.decodeVersion23(raw).withEating().withReading().withActivity())) })
    }

    private class MemoryStore(vararg initial: Pair<String, String>) : TrackerStringStore {
        val values = initial.toMap().toMutableMap()
        override fun read(key: String) = values[key]
        override fun write(key: String, value: String): Boolean = true.also { values[key] = value }
        override fun writeAll(values: Map<String, String>): Boolean = values.all { (key, value) -> write(key, value) }
    }

    private fun day(dateKey: String) = LocalDate.parse(dateKey)
    private fun AppState.chart() = measurementAverageHistoryWindow(recordsOf("weight"), today, valuePolicy = MeasurementValuePolicy.Positive)
    private fun AppState.chartPoints() = chart().recordedDays.map { it.log }
    private fun AppState.averageLine() = chart().trendPoints.map { it.dateKey to it.value }
    private fun AppState.averages() = sevenDayMeasurementAverages(recordsOf("weight"), day(today), valuePolicy = MeasurementValuePolicy.Positive)
    private fun AppState.draft(dateKey: String = today) = measurementDraftForDate(recordsOf("weight"), dateKey, Locale.ENGLISH)
    private fun AppState.keptDays(vararg dateKeys: String) = dateKeys.filter { isLoggedFor(MoNeed.Weight, it, HolidaySchedule()) }
    private fun AppState.walkedDays() = MoLedger.of(this, onlyWeight, day(today)).history(MoNeed.Weight.view.trackerRef)
    private fun AppState.daysTowardAToken() = requireNotNull(
        habitStreak(this, onlyWeight, day(today)).standings[MoNeed.Weight.view.trackerRef]?.nextToken,
    ) { "Weight should participate in the token walk for this fixture." }.done
    private fun AppState.statistics() = statisticsSummary(this, today, StatisticsRange.FourWeeks)
    private fun AppState.statisticsPoints() = statistics().measurements.single { it.configuration.id == "weight" }.window.recordedDays.map { it.log }

    /** This week's row in Statistics: the days with a record among those since the first one. */
    private fun AppState.weekInStatistics() = statistics().consistency.single { it.ref == MoNeed.Weight.view.trackerRef }.weeks.last()

    /** Which of Weight and Reading Mo saw logged last on [dateKey], by their records' own times. */
    private fun AppState.loggedLast(dateKey: String) =
        MoLedger.of(this, only(MoNeed.Weight, MoNeed.Reading), day(today)).latestTrackerOn(day(dateKey))

    /** Everything the readers answer for one state, for telling two states apart by what they show. */
    private fun AppState.everyAnswer(): List<Any?> {
        val days = listOf("2026-10-05", "2026-10-06", "2026-10-07", "2026-10-08", "2026-10-09", "2026-10-12")
        return listOf(
            recordsOf("weight"),
            chart(),
            averages(),
            days.map { draft(it) },
            days.filter { isLoggedFor(MoNeed.Weight, it, HolidaySchedule()) },
            walkedDays(),
            days.map { loggedLast(it) },
            habitStreak(this, onlyWeight, day(today)),
            moMood(this, only(MoNeed.Weight, MoNeed.Reading), day(today)),
            statistics().measurements.singleOrNull { it.configuration.id == "weight" },
            statistics().consistency.single { it.ref == MoNeed.Weight.view.trackerRef },
        )
    }

    /**
     * The screen and the chart take the greatest time of a day, the first in the list winning a
     * tie; the average takes the same record; any of them keeps the day.
     */
    @Test fun severalRecordsOfOneDayWithTwoAtTheSameTime() = everyWay { stored ->
        val records = """[
            {"dateKey":"2026-10-08","timestampMillis":5,"weightKg":70},
            {"dateKey":"2026-10-08","timestampMillis":9,"weightKg":82},
            {"dateKey":"2026-10-08","timestampMillis":9,"weightKg":85},
            {"dateKey":"2026-10-07","timestampMillis":2,"weightKg":80},
            {"dateKey":"2026-10-06","timestampMillis":1,"weightKg":78}
        ]"""
        val state = stored.state(records)
        val firstOfTheLatest = MeasurementRecord("2026-10-08", 9, 82.0)

        assertEquals(
            listOf(
                MeasurementRecord("2026-10-08", 5, 70.0),
                firstOfTheLatest,
                MeasurementRecord("2026-10-08", 9, 85.0),
                MeasurementRecord("2026-10-07", 2, 80.0),
                MeasurementRecord("2026-10-06", 1, 78.0),
            ),
            state.recordsOf("weight"),
        )
        assertEquals(listOf(MeasurementRecord("2026-10-06", 1, 78.0), MeasurementRecord("2026-10-07", 2, 80.0), firstOfTheLatest), state.chartPoints())
        assertEquals(
            listOf(MeasurementAverage(day("2026-10-06"), 78.0, 1), MeasurementAverage(day("2026-10-07"), 79.0, 2), MeasurementAverage(day("2026-10-08"), 80.0, 3)),
            state.averages(),
        )
        assertEquals(listOf("2026-10-08" to 80.0), state.averageLine())
        assertEquals(MeasurementDraft("2026-10-09", "82", "82", originalLog = null), state.draft())
        assertEquals(MeasurementDraft("2026-10-08", "82", "82", originalLog = firstOfTheLatest), state.draft("2026-10-08"))
        assertEquals(listOf("2026-10-06", "2026-10-07", "2026-10-08"), state.keptDays("2026-10-05", "2026-10-06", "2026-10-07", "2026-10-08", "2026-10-09"))
        assertEquals(
            mapOf(day("2026-10-06") to MoStatus.Kept, day("2026-10-07") to MoStatus.Kept, day("2026-10-08") to MoStatus.Kept, day("2026-10-09") to MoStatus.Missed),
            state.walkedDays(),
        )
        assertEquals(3, state.daysTowardAToken())
        assertEquals(state.chartPoints(), state.statisticsPoints())
        assertEquals(WeekConsistency(kept = 3, counted = 4), state.weekInStatistics())

        // Mo's time for the day is its latest record's, 9: a reading at 8 came before it, one at 10 after.
        assertEquals(MoNeed.Weight.view.trackerRef, stored.state(records, """[{"dateKey":"2026-10-08","timestampMillis":8}]""").loggedLast("2026-10-08"))
        assertEquals(MoNeed.Reading.view.trackerRef, stored.state(records, """[{"dateKey":"2026-10-08","timestampMillis":10}]""").loggedLast("2026-10-08"))
    }

    /** Two records alike in everything stay two; every reader that chooses one answers as for one. */
    @Test fun twoIdenticalRecords() = everyWay { stored ->
        val record = MeasurementRecord("2026-10-08", 7, 81.5)
        val state = stored.state(
            """[{"dateKey":"2026-10-08","timestampMillis":7,"weightKg":81.5},{"dateKey":"2026-10-08","timestampMillis":7,"weightKg":81.5}]""",
        )

        assertEquals(listOf(record, record), state.recordsOf("weight"))
        assertEquals(listOf(record), state.chartPoints())
        assertEquals(listOf(MeasurementAverage(day("2026-10-08"), 81.5, 1)), state.averages())
        assertEquals(emptyList<Pair<String, Double>>(), state.averageLine())
        assertEquals(MeasurementDraft("2026-10-09", "81.5", "81.5", originalLog = null), state.draft())
        assertEquals(MeasurementDraft("2026-10-08", "81.5", "81.5", originalLog = record), state.draft("2026-10-08"))
        assertEquals(listOf("2026-10-08"), state.keptDays("2026-10-07", "2026-10-08", "2026-10-09"))
        assertEquals(mapOf(day("2026-10-08") to MoStatus.Kept, day("2026-10-09") to MoStatus.Missed), state.walkedDays())
        assertEquals(1, state.daysTowardAToken())
    }

    /** A record the state held no time for has the time 0, so any record with a time is the day's later one. */
    @Test fun aRecordWithoutATime() = everyWay { stored ->
        val records = """[
            {"dateKey":"2026-10-08","weightKg":81.5},
            {"dateKey":"2026-10-08","timestampMillis":4,"weightKg":79},
            {"dateKey":"2026-10-07","weightKg":80}
        ]"""
        val state = stored.state(records)
        val timed = MeasurementRecord("2026-10-08", 4, 79.0)
        val alone = MeasurementRecord("2026-10-07", 0, 80.0)

        assertEquals(listOf(MeasurementRecord("2026-10-08", 0, 81.5), timed, alone), state.recordsOf("weight"))
        assertEquals(listOf(alone, timed), state.chartPoints())
        assertEquals(listOf(MeasurementAverage(day("2026-10-07"), 80.0, 1), MeasurementAverage(day("2026-10-08"), 79.5, 2)), state.averages())
        assertEquals(MeasurementDraft("2026-10-09", "79", "79", originalLog = null), state.draft())
        assertEquals(MeasurementDraft("2026-10-07", "80", "80", originalLog = alone), state.draft("2026-10-07"))
        assertEquals(listOf("2026-10-07", "2026-10-08"), state.keptDays("2026-10-06", "2026-10-07", "2026-10-08", "2026-10-09"))
        assertEquals(day("2026-10-07"), state.walkedDays().keys.first())

        // Logged, with the time 0: Weight is the day's task until anything with a time comes after.
        assertEquals(MoNeed.Weight.view.trackerRef, state.loggedLast("2026-10-07"))
        assertEquals(MoNeed.Reading.view.trackerRef, stored.state(records, """[{"dateKey":"2026-10-07","timestampMillis":1}]""").loggedLast("2026-10-07"))
        assertEquals(MoNeed.Weight.view.trackerRef, stored.state(records, """[{"dateKey":"2026-10-08","timestampMillis":3}]""").loggedLast("2026-10-08"))
        assertEquals(MoNeed.Reading.view.trackerRef, stored.state(records, """[{"dateKey":"2026-10-08","timestampMillis":5}]""").loggedLast("2026-10-08"))
    }

    /**
     * A value the screen would not accept is a record like any other: it keeps its day and is what
     * the screen and the chart show. Only the average leaves it out, and it takes the day's latest
     * record first, so a day whose latest value is zero is left out although an earlier one counts.
     */
    @Test fun aValueOfZeroAndOneBelow() = everyWay { stored ->
        val state = stored.state(
            """[
                {"dateKey":"2026-10-08","timestampMillis":3,"weightKg":0},
                {"dateKey":"2026-10-08","timestampMillis":2,"weightKg":81},
                {"dateKey":"2026-10-07","timestampMillis":1,"weightKg":-1.5},
                {"dateKey":"2026-10-06","timestampMillis":1,"weightKg":80}
            ]""",
        )
        val zero = MeasurementRecord("2026-10-08", 3, 0.0)
        val below = MeasurementRecord("2026-10-07", 1, -1.5)
        val ordinary = MeasurementRecord("2026-10-06", 1, 80.0)

        assertEquals(listOf(zero, MeasurementRecord("2026-10-08", 2, 81.0), below, ordinary), state.recordsOf("weight"))
        assertEquals(listOf(ordinary, below, zero), state.chartPoints())
        assertEquals(listOf(MeasurementAverage(day("2026-10-06"), 80.0, 1)), state.averages())
        assertEquals(MeasurementDraft("2026-10-09", "0", "0", originalLog = null), state.draft())
        assertEquals(MeasurementDraft("2026-10-08", "0", "0", originalLog = zero), state.draft("2026-10-08"))
        assertEquals(MeasurementDraft("2026-10-07", "-1.5", "-1.5", originalLog = below), state.draft("2026-10-07"))
        assertEquals(listOf("2026-10-06", "2026-10-07", "2026-10-08"), state.keptDays("2026-10-06", "2026-10-07", "2026-10-08", "2026-10-09"))
        assertEquals(
            mapOf(day("2026-10-06") to MoStatus.Kept, day("2026-10-07") to MoStatus.Kept, day("2026-10-08") to MoStatus.Kept, day("2026-10-09") to MoStatus.Missed),
            state.walkedDays(),
        )
        assertEquals(3, state.daysTowardAToken())
        assertEquals(listOf(ordinary, below, zero), state.statisticsPoints())
        assertEquals(WeekConsistency(kept = 3, counted = 4), state.weekInStatistics())
    }

    /** A record dated after today is kept and keeps its own day; the chart, the average and the pre-filled draft stop at today. */
    @Test fun aDateInTheFuture() = everyWay { stored ->
        val state = stored.state(
            """[{"dateKey":"2026-10-12","timestampMillis":6,"weightKg":90},{"dateKey":"2026-10-08","timestampMillis":1,"weightKg":80}]""",
        )
        val ahead = MeasurementRecord("2026-10-12", 6, 90.0)
        val past = MeasurementRecord("2026-10-08", 1, 80.0)

        assertEquals(listOf(ahead, past), state.recordsOf("weight"))
        assertEquals(listOf(past), state.chartPoints())
        assertEquals(listOf(MeasurementAverage(day("2026-10-08"), 80.0, 1)), state.averages())
        assertEquals(MeasurementDraft("2026-10-09", "80", "80", originalLog = null), state.draft())
        assertEquals(MeasurementDraft("2026-10-12", "90", "90", originalLog = ahead), state.draft("2026-10-12"))
        assertEquals(listOf("2026-10-08", "2026-10-12"), state.keptDays("2026-10-08", "2026-10-09", "2026-10-12"))
        assertEquals(mapOf(day("2026-10-08") to MoStatus.Kept, day("2026-10-09") to MoStatus.Missed), state.walkedDays())
        assertEquals(listOf(past), state.statisticsPoints())
        assertEquals(WeekConsistency(kept = 1, counted = 2), state.weekInStatistics())

        // Alone, it means Weight has not begun: no day up to today is asked for.
        val onlyAhead = stored.state("""[{"dateKey":"2026-10-12","timestampMillis":6,"weightKg":90}]""")
        assertEquals(listOf(ahead), onlyAhead.recordsOf("weight"))
        assertEquals(emptyMap<LocalDate, MoStatus>(), onlyAhead.walkedDays())
        assertEquals(MeasurementDraft("2026-10-09", "", "", originalLog = null), onlyAhead.draft())
        assertTrue(
            MoRecordIndex(onlyAhead, HolidaySchedule()).isLogged(
                LoggedViewRef(LoggedViewKind.Measurement, WeightMeasurementId),
                day("2026-10-12"),
            ),
        )
    }

    /** Stored in any order, the records come back by date, newest first, and those of one date in their stored order. */
    @Test fun recordsStoredOutOfDateOrder() = everyWay { stored ->
        val state = stored.state(
            """[
                {"dateKey":"2026-10-06","timestampMillis":1,"weightKg":78},
                {"dateKey":"2026-10-08","timestampMillis":3,"weightKg":82},
                {"dateKey":"2026-10-07","timestampMillis":2,"weightKg":80},
                {"dateKey":"2026-10-08","timestampMillis":3,"weightKg":84}
            ]""",
        )
        val storedFirst = MeasurementRecord("2026-10-08", 3, 82.0)

        assertEquals(
            listOf(storedFirst, MeasurementRecord("2026-10-08", 3, 84.0), MeasurementRecord("2026-10-07", 2, 80.0), MeasurementRecord("2026-10-06", 1, 78.0)),
            state.recordsOf("weight"),
        )
        assertEquals(listOf(MeasurementRecord("2026-10-06", 1, 78.0), MeasurementRecord("2026-10-07", 2, 80.0), storedFirst), state.chartPoints())
        assertEquals(
            listOf(MeasurementAverage(day("2026-10-06"), 78.0, 1), MeasurementAverage(day("2026-10-07"), 79.0, 2), MeasurementAverage(day("2026-10-08"), 80.0, 3)),
            state.averages(),
        )
        assertEquals(listOf("2026-10-08" to 80.0), state.averageLine())
        assertEquals(MeasurementDraft("2026-10-09", "82", "82", originalLog = null), state.draft())
        assertEquals(MeasurementDraft("2026-10-08", "82", "82", originalLog = storedFirst), state.draft("2026-10-08"))
        assertEquals(day("2026-10-06"), state.walkedDays().keys.first())
        assertEquals(3, state.daysTowardAToken())
    }

    /**
     * An entry the decoder cannot read is left out and every good one loads: one without a date or
     * a number, one whose number is no number, and one that is no record at all. A date, a time or
     * a number written loosely is read as what it says.
     */
    @Test fun aDamagedEntryBetweenGoodOnes() = everyWay { stored ->
        val state = stored.state(
            """[
                {"dateKey":"2026-10-08","timestampMillis":3,"weightKg":82},
                {"dateKey":"","timestampMillis":4,"weightKg":83},
                {"timestampMillis":5,"weightKg":84},
                {"dateKey":"2026-10-08","timestampMillis":6},
                {"dateKey":"2026-10-08","timestampMillis":7,"weightKg":"heavy"},
                "2026-10-08",
                {"dateKey":" 2026-10-07 ","timestampMillis":"2","weightKg":"80.5"}
            ]""",
        )

        assertEquals(listOf(MeasurementRecord("2026-10-08", 3, 82.0), MeasurementRecord("2026-10-07", 2, 80.5)), state.recordsOf("weight"))
        assertEquals(listOf(MeasurementRecord("2026-10-07", 2, 80.5), MeasurementRecord("2026-10-08", 3, 82.0)), state.chartPoints())
        assertEquals(MeasurementDraft("2026-10-08", "82", "82", originalLog = MeasurementRecord("2026-10-08", 3, 82.0)), state.draft("2026-10-08"))
        assertEquals(listOf("2026-10-07", "2026-10-08"), state.keptDays("2026-10-07", "2026-10-08", "2026-10-09"))
    }

    /**
     * Records of a measurement nothing knows are stored beside Weight's and seen by no reader: with
     * two alike on a day Weight has records on, one on a day before Weight began, one today and one
     * after it, every answer is what it is without them, before and after a save and a reload.
     */
    @Test fun recordsOfAnotherMeasurementAreSeenByNoReader() {
        fun other(dateKey: String, timestampMillis: Long, value: Double) = MeasurementLog("msr_k2p9", dateKey, timestampMillis, value)
        fun reloaded(state: AppState) = MemoryStore().let { store ->
            assertTrue(trackerRepository(store).save(state))
            trackerRepository(store).load()
        }
        val weightOnly = AppStateJsonCodec.decodeVersion23(
            frozenVersion23(
                weightLogs = """[
                    {"dateKey":"2026-10-08","timestampMillis":5,"weightKg":70},
                    {"dateKey":"2026-10-08","timestampMillis":9,"weightKg":82},
                    {"dateKey":"2026-10-08","timestampMillis":9,"weightKg":85},
                    {"dateKey":"2026-10-07","timestampMillis":2,"weightKg":80},
                    {"dateKey":"2026-10-06","timestampMillis":1,"weightKg":78}
                ]""",
                readingLogs = """[{"dateKey":"2026-10-08","timestampMillis":10}]""",
            ),
        ).withEating().withReading().withActivity()
        val mixed = weightOnly.copy(
            measurementLogs = listOf(other("2026-10-12", 30, 61.0), other("2026-10-09", 20, 61.0), other("2026-10-08", 99, 60.0)) +
                weightOnly.measurementLogs.take(1) + other("2026-10-08", 99, 60.0) + weightOnly.measurementLogs.drop(1) + other("2026-10-05", 1, 59.0),
        )
        val stored = reloaded(mixed)

        assertEquals(mixed.measurementLogs, stored.measurementLogs)
        assertEquals(5, stored.measurementLogs.count { it.measurementId == "msr_k2p9" })
        assertEquals(weightOnly.recordsOf("weight"), stored.recordsOf("weight"))
        assertEquals(weightOnly.everyAnswer(), mixed.everyAnswer())
        assertEquals(reloaded(weightOnly).everyAnswer(), stored.everyAnswer())

        // A day only the other measurement has a record on is a day without a weight.
        assertEquals(listOf("2026-10-06", "2026-10-07", "2026-10-08"), stored.keptDays("2026-10-05", "2026-10-06", "2026-10-07", "2026-10-08", "2026-10-09", "2026-10-12"))
        assertEquals(day("2026-10-06"), stored.walkedDays().keys.first())
        assertEquals(MoStatus.Missed, stored.walkedDays().getValue(day("2026-10-09")))
        // Its record at 99 is the day's latest of all, and Mo still saw the reading at 10 last.
        assertEquals(MoNeed.Reading.view.trackerRef, stored.loggedLast("2026-10-08"))
        assertNull(stored.loggedLast("2026-10-09"))
        assertEquals(MeasurementDraft("2026-10-09", "82", "82", originalLog = null), stored.draft())
    }

    /** Nothing recorded: no reader has anything to answer. */
    @Test fun noRecords() = everyWay { stored ->
        val state = stored.state("[]")

        assertEquals(emptyList<MeasurementRecord>(), state.recordsOf("weight"))
        assertEquals(emptyList<MeasurementRecord?>(), state.chartPoints())
        assertEquals(emptyList<MeasurementAverage>(), state.averages())
        assertEquals(MeasurementDraft("2026-10-09", "", "", originalLog = null), state.draft())
        assertEquals(emptyList<String>(), state.keptDays("2026-10-08", "2026-10-09"))
        assertEquals(emptyMap<LocalDate, MoStatus>(), state.walkedDays())
        assertNull(state.loggedLast("2026-10-09"))
    }
}
