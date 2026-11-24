package de.nicolasmahn.momentum.feature.time

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.core.text.UiText
import de.nicolasmahn.momentum.core.text.resolve
import de.nicolasmahn.momentum.core.format.dateText
import de.nicolasmahn.momentum.domain.compatibility.ReadingTimeId
import de.nicolasmahn.momentum.domain.time.TimeLog
import de.nicolasmahn.momentum.domain.time.testReadingTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Timer model messages describe records without assuming what was timed. */
@RunWith(RobolectricTestRunner::class)
class TimeModelsTest {
    private val words = testReadingTime().words()
    private val resources = ApplicationProvider.getApplicationContext<Context>().resources

    private fun UiText?.english(): String? = this?.resolve(resources)

    @Test
    fun minutesMustFitInsideOneDayIncludingWhatIsAlreadyRecorded() {
        assertEquals(null, minutesError(minutes = 15, alreadyRecorded = 0))
        assertEquals(null, minutesError(minutes = 40, alreadyRecorded = 1400))
        assertEquals("1–1440", minutesError(minutes = null, alreadyRecorded = 0)?.let(words::minutesError).english())
        assertEquals("1–1440", minutesError(minutes = 0, alreadyRecorded = 0)?.let(words::minutesError).english())
        assertEquals("Over 24 h", minutesError(minutes = 41, alreadyRecorded = 1400)?.let(words::minutesError).english())
    }

    @Test
    fun removalSaysWhatTheDayHolds() {
        assertEquals(
            "Remove 45 min for Tue, Sep 15",
            words.remove(readingLog("2026-09-15", minutes = 45).minutes, dateText("2026-09-15")).english(),
        )
        // An older checkmark has no duration, so no number is claimed for it.
        assertEquals(
            "Remove the entry for Tue, Sep 15",
            words.remove(readingLog("2026-09-15").minutes, dateText("2026-09-15")).english(),
        )
    }

    @Test
    fun calendarStatusDescriptionsRemainNeutral() {
        assertEquals("minutes recorded", words.dayLogged.english())
        assertEquals("no entry", words.dayEmpty.english())
    }

    @Test
    fun historyCoversFourWholeWeeksAcrossMonthBoundaries() {
        // 2026-03-02 is a Monday: the last row holds today and six days still to come.
        val window = timeHistoryWindow(
            logs = emptyList(),
            todayKey = "2026-03-02",
        )

        assertEquals(28, window.days.size)
        assertEquals("2026-02-09", window.days.first().dateKey)
        assertEquals("2026-03-02", window.days[21].dateKey)
        assertEquals("2026-03-08", window.days.last().dateKey)
    }

    @Test
    fun historyMarksOnlyVisibleRecordedDates() {
        val window = timeHistoryWindow(
            logs = listOf(
                readingLog(dateKey = "2026-01-01"),
                readingLog(dateKey = "2026-02-28"),
                readingLog(dateKey = "2026-03-02"),
                readingLog(dateKey = "2026-03-03"),
            ),
            todayKey = "2026-03-02",
        )
        val days = window.days.associateBy { it.dateKey }

        // Dated ahead: inside the grid's last row, and still never shown as logged.
        assertFalse(days.getValue("2026-03-03").isLogged)

        assertFalse("2026-01-01" in days)
        assertTrue(days.getValue("2026-02-28").isLogged)
        assertTrue(days.getValue("2026-03-02").isLogged)
        assertFalse(days.getValue("2026-02-27").isLogged)
    }

    @Test
    fun duplicateRecordsStillProduceOneCompletedDay() {
        val window = timeHistoryWindow(
            logs = listOf(
                readingLog(dateKey = "2026-03-02", timestampMillis = 1L),
                readingLog(dateKey = "2026-03-02", timestampMillis = 2L),
            ),
            todayKey = "2026-03-02",
        )

        assertEquals(1, window.days.count { it.isLogged })
    }

    private fun readingLog(
        dateKey: String,
        timestampMillis: Long = 1L,
        minutes: Int? = null,
    ) = TimeLog(
        viewId = ReadingTimeId,
        dateKey = dateKey,
        timestampMillis = timestampMillis,
        minutes = minutes,
    )
}
