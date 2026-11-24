package de.nicolasmahn.momentum.domain.time

import de.nicolasmahn.momentum.domain.compatibility.ReadingTimeId

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TimeLogTest {
    private val monday = LocalDate.parse("2026-09-14")
    private val piano = "tim_k2p9"

    private fun refused(build: () -> Any) {
        assertThrows(IllegalArgumentException::class.java) { build() }
    }

    /** Adding minutes adds to the day's one log and stamps it; every other day and every other view stays as it was. */
    @Test fun minutesAddUpInTheDaysOneLog() {
        val other = TimeLog(ReadingTimeId, "2026-09-16", 5L, 20)
        val earlier = TimeLog(piano, "2026-09-15", 6L, 30)
        val first = addTimeMinutes(listOf(other, earlier), piano, "2026-09-16", 15, 20L)
        assertEquals(listOf(TimeLog(piano, "2026-09-16", 20L, 15), other, earlier), first)

        val more = addTimeMinutes(first, piano, "2026-09-16", 25, 40L)
        assertEquals(listOf(TimeLog(piano, "2026-09-16", 40L, 40), other, earlier), more)
        assertEquals(40, minutesOn(more, piano, "2026-09-16"))
        assertEquals(20, minutesOn(more, ReadingTimeId, "2026-09-16"))
        assertEquals(0, minutesOn(more, piano, "2026-09-17"))
    }

    /** Reading's old mark holds no minutes: minutes added to it are the day's minutes and nothing more. */
    @Test fun aMarkWithoutMinutesBecomesTheMinutesAddedToIt() {
        val mark = TimeLog(ReadingTimeId, "2026-09-16", 10L)
        assertEquals(0, minutesOn(listOf(mark), ReadingTimeId, "2026-09-16"))
        assertEquals(listOf(TimeLog(ReadingTimeId, "2026-09-16", 20L, 15)), addTimeMinutes(listOf(mark), ReadingTimeId, "2026-09-16", 15, 20L))
    }

    /** A day holds one to 1440 minutes, alone and with what it has already; a date that is none and a time before the epoch are refused. */
    @Test fun minutesADayCannotHoldAreRefused() {
        refused { addTimeMinutes(emptyList(), piano, "2026-09-16", 0, 10L) }
        refused { addTimeMinutes(emptyList(), piano, "2026-09-16", 1441, 10L) }
        refused { addTimeMinutes(emptyList(), piano, "2026-02-30", 15, 10L) }
        refused { addTimeMinutes(emptyList(), piano, "2026-09-16", 15, -1L) }
        val full = addTimeMinutes(emptyList(), piano, "2026-09-16", 1440, 10L)
        refused { addTimeMinutes(full, piano, "2026-09-16", 1, 11L) }
        // Another view's full day leaves this one's empty.
        assertEquals(1, minutesOn(addTimeMinutes(full, ReadingTimeId, "2026-09-16", 1, 11L), ReadingTimeId, "2026-09-16"))
    }

    /** Removing takes the view's whole day and nothing else. */
    @Test fun removingTakesTheWholeDayOfThatViewAlone() {
        val readingsDay = TimeLog(ReadingTimeId, "2026-09-16", 5L, 20)
        val pianosDay = TimeLog(piano, "2026-09-16", 6L, 30)
        val pianosOtherDay = TimeLog(piano, "2026-09-15", 7L, 10)
        val logs = listOf(readingsDay, pianosDay, pianosOtherDay)
        assertEquals(listOf(readingsDay, pianosOtherDay), removeTimeDay(logs, piano, "2026-09-16"))
        assertEquals(logs, removeTimeDay(logs, piano, "2026-09-17"))
        assertEquals(logs, removeTimeDay(logs, "tim_gone", "2026-09-16"))
    }

    /** A week's minutes are those of one view from Monday to Sunday; a mark without minutes counts as none. */
    @Test fun aWeeksMinutesAreOneViewsFromMondayToSunday() {
        val logs = listOf(
            TimeLog(ReadingTimeId, "2026-09-13", 1L, 50),
            TimeLog(ReadingTimeId, "2026-09-14", 2L, 15),
            TimeLog(ReadingTimeId, "2026-09-16", 3L),
            TimeLog(ReadingTimeId, "2026-09-20", 4L, 45),
            TimeLog(ReadingTimeId, "2026-09-21", 5L, 30),
            TimeLog(piano, "2026-09-15", 6L, 100),
        )
        assertEquals(60, minutesInWeek(logs, ReadingTimeId, monday))
        assertEquals(100, minutesInWeek(logs, piano, monday))
        assertEquals(50, minutesInWeek(logs, ReadingTimeId, monday.minusWeeks(1)))
        assertEquals(30, minutesInWeek(logs, ReadingTimeId, monday.plusWeeks(1)))
        assertEquals(0, minutesInWeek(logs, piano, monday.plusWeeks(1)))
        assertThrows(IllegalArgumentException::class.java) { minutesInWeek(logs, piano, monday.plusDays(1)) }
    }

    /** What a stored log must be, each rule alone. */
    @Test fun aStoredLogNamesItsViewHasAValidDateATimeAndMinutesADayCanHold() {
        val log = TimeLog(piano, "2026-09-16", 0L, 1)
        requireStorableTimeLogs(listOf(log, log.copy(dateKey = "2026-09-17", minutes = 1440), log.copy(viewId = ReadingTimeId), TimeLog("tim_gone", "2026-09-16", 9L, 5)))
        refused { requireStorableTimeLogs(listOf(log.copy(viewId = ""))) }
        refused { requireStorableTimeLogs(listOf(log.copy(viewId = " "))) }
        refused { requireStorableTimeLogs(listOf(log.copy(dateKey = "2026-02-30"))) }
        refused { requireStorableTimeLogs(listOf(log.copy(dateKey = "16.09.2026"))) }
        refused { requireStorableTimeLogs(listOf(log.copy(timestampMillis = -1L))) }
        refused { requireStorableTimeLogs(listOf(log.copy(minutes = 0))) }
        refused { requireStorableTimeLogs(listOf(log.copy(minutes = -5))) }
        refused { requireStorableTimeLogs(listOf(log.copy(minutes = 1441))) }
    }

    /** A view has one log per date, and two views may each have theirs. */
    @Test fun aViewHasOneLogPerDate() {
        val log = TimeLog(piano, "2026-09-16", 1L, 10)
        requireStorableTimeLogs(listOf(log, log.copy(viewId = ReadingTimeId), log.copy(dateKey = "2026-09-17")))
        refused { requireStorableTimeLogs(listOf(log, log.copy(timestampMillis = 2L, minutes = 20))) }
        refused { requireStorableTimeLogs(listOf(log, log)) }
    }

    /** A historical mark without minutes is valid for any view; codecs apply version-specific limits. */
    @Test fun aLogWithoutMinutesIsNotTiedToAViewId() {
        val readingMark = TimeLog(ReadingTimeId, "2026-09-16", 1L)
        val madeViewMark = TimeLog(piano, "2026-09-15", 2L)
        val genericMark = TimeLog("custom-time", "2026-09-14", 3L)

        requireStorableTimeLogs(listOf(readingMark, madeViewMark, genericMark))
        assertEquals(null, readingMark.minutes)
        assertEquals(null, madeViewMark.minutes)
        assertEquals(null, genericMark.minutes)
    }
}
