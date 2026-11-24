package de.nicolasmahn.momentum.core.time

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class CalendarWeekdayLabelsTest {
    @Test
    fun mondayThroughSundayUseUnambiguousTwoLetterLabels() {
        assertEquals(listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"), shortLabels())
    }

    @Test
    @Config(qualifiers = "de")
    fun germanUsesItsOwnTwoLetterLabels() {
        assertEquals(listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"), shortLabels())
    }

    private fun shortLabels(): List<String> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return CalendarWeekdays.map { context.getString(calendarWeekdayShortLabel(it)) }
    }
}
