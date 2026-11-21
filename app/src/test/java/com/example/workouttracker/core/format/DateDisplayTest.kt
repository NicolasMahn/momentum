package com.example.workouttracker.core.format

import com.example.workouttracker.core.time.daysBetween
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DateDisplayTest {
    private val day = LocalDate.of(2026, 10, 3)

    @Test
    fun eachStyleReadsNaturallyInEnglishAndGerman() {
        assertEquals("Sat, Oct 3", formatDate(day, DateStyle.WeekdayDayMonth, Locale.ENGLISH))
        assertEquals("Sa., 3. Okt.", formatDate(day, DateStyle.WeekdayDayMonth, Locale.GERMANY))
        assertEquals("Oct 3", formatDate(day, DateStyle.DayMonth, Locale.ENGLISH))
        assertEquals("3. Okt.", formatDate(day, DateStyle.DayMonth, Locale.GERMANY))
        assertEquals("Oct", formatDate(day, DateStyle.Month, Locale.ENGLISH))
        assertEquals("Okt.", formatDate(day, DateStyle.Month, Locale.GERMANY))
        assertEquals("October 2026", formatDate(day, DateStyle.MonthYear, Locale.ENGLISH))
        assertEquals("Oktober 2026", formatDate(day, DateStyle.MonthYear, Locale.GERMANY))
        assertEquals("Samstag, 3. Oktober 2026", formatDate(day, DateStyle.Full, Locale.GERMANY))
    }

    @Test
    fun aMomentReadsAsThePhonesOwnDateAndTime() {
        val berlin = ZoneId.of("Europe/Berlin")
        // Summer time: 20:12 UTC is 22:12 in Berlin, and 22:12 UTC is already the next day there.
        assertEquals("Sep 15, 2026, 22:12", formatMoment(Instant.parse("2026-09-15T20:12:30.034Z"), berlin, Locale.ENGLISH, is24Hour = true))
        assertEquals("16. Sept. 2026, 00:12", formatMoment(Instant.parse("2026-09-15T22:12:30.034Z"), berlin, Locale.GERMANY, is24Hour = true))
        assertEquals("Sep 16, 2026, 12:12 AM", formatMoment(Instant.parse("2026-09-15T22:12:30.034Z"), berlin, Locale.ENGLISH, is24Hour = false))
    }

    @Test
    fun aKeyThatIsNotADateIsShownAsItIs() {
        assertEquals(com.example.workouttracker.core.text.UiText.Verbatim("2026-02-30"), dateText("2026-02-30"))
    }

    @Test
    fun countingDaysIgnoresADaylightSavingNight() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))
            // 2026-03-29 has only 23 hours in Berlin.
            assertEquals(1, daysBetween("2026-03-29", "2026-03-30"))
            assertEquals(2, daysBetween("2026-03-28", "2026-03-30"))
        } finally {
            TimeZone.setDefault(original)
        }
    }
}
