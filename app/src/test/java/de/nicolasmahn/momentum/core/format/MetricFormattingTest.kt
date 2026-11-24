package de.nicolasmahn.momentum.core.format

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MetricFormattingTest {
    @Test
    fun displayFollowsTheLocaleWhileParsingAcceptsEither() {
        assertEquals("82.5", formatMetricNumber(82.5, Locale.ENGLISH))
        assertEquals("82,5", formatMetricNumber(82.5, Locale.GERMANY))
        assertEquals(82.5, parseMetricNumber("82.5")!!, 0.0)
        assertEquals(82.5, parseMetricNumber("82,5 kg")!!, 0.0)
    }

    @Test
    fun largeValuesNeverGainAGroupingSeparatorThatParsingWouldMisread() {
        assertEquals("1234.5", formatMetricNumber(1234.5, Locale.ENGLISH))
        assertEquals("1234,5", formatMetricNumber(1234.5, Locale.GERMANY))
        assertEquals(1234.5, parseMetricNumber(formatMetricNumber(1234.5, Locale.GERMANY))!!, 0.0)
    }

    @Test
    fun aChangeIsAlwaysSignedAndZeroIsPlusMinus() {
        assertEquals("+0.4", formatSignedMetric(0.4, Locale.ENGLISH))
        assertEquals("-0,4", formatSignedMetric(-0.4, Locale.GERMANY))
        assertEquals("±0", formatSignedMetric(0.001, Locale.GERMANY))
    }

    @Test
    fun everyLocalesOwnWritingParsesBackToTheSameValue() {
        val values = listOf(-3.0, -0.5, 0.0, 0.25, 82.5, 1234.5)
        for (locale in Locale.getAvailableLocales()) {
            for (value in values) {
                val shown = formatMetricNumber(value, locale)
                assertEquals("$locale wrote $shown", value, parseMetricNumber(shown)!!, 0.0)
            }
        }
    }

    /** A typed number is a number alone: either separator, any locale's digits, and nothing beside it, not even a unit. */
    @Test
    fun aTypedNumberIsReadAsAMetricOneButTakesNoUnit() {
        listOf("82.5", "82,5", " 82.5 ", "0", "-3", "\u22123", "\u0668\u0662\u066B\u0665", "87.456", "1234.5", "0.01").forEach { typed ->
            assertEquals(typed, parseMetricNumber(typed)!!, parseTypedNumber(typed)!!, 0.0)
        }
        for (locale in Locale.getAvailableLocales()) {
            listOf(-3.0, 0.0, 0.25, 82.5, 1234.5).forEach { value ->
                assertEquals("$locale", value, parseTypedNumber(formatMetricNumber(value, locale))!!, 0.0)
            }
        }
        listOf("82.5 kg", "82.5kg", "82,5 KG", "82.5 Kg", "kg", "82.5 cm", "82.5 %", "eighty", "NaN", "Infinity", "", " ").forEach { typed ->
            assertNull(typed, parseTypedNumber(typed))
        }
        // Weight's reader still leaves a trailing "kg" out, in any case.
        listOf("82.5 kg", "82.5kg", "82,5 KG", "82.5 Kg", " 82.5 kG ").forEach { typed -> assertEquals(typed, 82.5, parseMetricNumber(typed)!!, 0.0) }
        assertNull(parseMetricNumber("82.5 cm"))
        assertNull(parseMetricNumber("kg"))
    }

    @Test
    fun aRealMinusSignAndOtherDigitsAreRead() {
        assertEquals("\u22123", formatMetricNumber(-3.0, Locale.forLanguageTag("sv")))
        assertEquals(-3.0, parseMetricNumber("\u22123")!!, 0.0)
        assertEquals(82.5, parseMetricNumber("\u0668\u0662\u066B\u0665")!!, 0.0)
        assertNull(parseMetricNumber("eighty"))
        assertNull(parseMetricNumber("NaN"))
    }
}
