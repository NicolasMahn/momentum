package de.nicolasmahn.momentum.core.ui

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import de.nicolasmahn.momentum.core.text.resolve
import de.nicolasmahn.momentum.domain.holiday.DayOff
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A calendar read as a whole says which days were away and which sick, a single day or a run of them. */
@RunWith(RobolectricTestRunner::class)
class DaysOffTextTest {
    private val days = (1..9).map { "2026-10-0$it" }

    @Test
    fun daysOffAreSpokenAsDaysAndRangesInEnglishAndGerman() {
        val away = setOf("2026-10-02", "2026-10-03", "2026-10-04", "2026-10-07")
        val text = requireNotNull(daysOffText(days) { DayOff.Away.takeIf { _ -> it in away } })
        assertEquals("On holiday: Fri, Oct 2 to Sun, Oct 4, Wed, Oct 7.", text.resolve(resources(Locale.ENGLISH)))
        assertEquals(true, text.resolve(resources(Locale.GERMAN)).startsWith("Im Urlaub: "))
        assertNull(daysOffText(days) { null })
    }

    @Test
    fun sickDaysAreSpokenAfterTheHolidays() {
        val text = requireNotNull(
            daysOffText(days) { key ->
                when (key) {
                    "2026-10-02" -> DayOff.Away
                    "2026-10-05", "2026-10-06" -> DayOff.Sick
                    else -> null
                }
            },
        )
        assertEquals("On holiday: Fri, Oct 2. Sick: Mon, Oct 5 to Tue, Oct 6.", text.resolve(resources(Locale.ENGLISH)))
    }

    private fun resources(locale: Locale): Resources {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(locale) }).resources
    }
}
