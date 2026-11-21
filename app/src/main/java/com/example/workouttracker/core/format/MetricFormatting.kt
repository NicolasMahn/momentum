package com.example.workouttracker.core.format

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs

/**
 * A measurement for display, in [locale]'s decimal style. The text is for reading only: stored
 * values never pass through it, and [parseMetricNumber] accepts either separator, so a value shown
 * in one language still parses after the user switches to another.
 */
fun formatMetricNumber(value: Double, locale: Locale): String {
    val normalized = if (abs(value) < 0.005) 0.0 else value
    return DecimalFormat("0.##", DecimalFormatSymbols.getInstance(locale)).format(normalized)
}

/** A change between two readings: always signed, and "±0" rather than a bare zero. */
fun formatSignedMetric(value: Double, locale: Locale): String = when {
    abs(value) < 0.005 -> "±0"
    value > 0 -> "+${formatMetricNumber(value, locale)}"
    else -> formatMetricNumber(value, locale)
}

/**
 * Reads a typed or pre-filled number. It accepts whatever [formatMetricNumber] writes in any locale
 * (a Swedish "−3" with a real minus sign, Arabic-Indic digits, the Arabic decimal mark and the
 * direction marks around them) as well as either decimal separator, so a value stays readable when
 * the language changes under it. Anything beside the number, a unit included, makes it no number.
 */
fun parseTypedNumber(raw: String): Double? = typedDecimal(raw).toDoubleOrNull()?.takeIf(Double::isFinite)

/**
 * What was typed as a decimal in one writing, whatever language it was typed or pre-filled in: the
 * digits 0 to 9, a point for either decimal separator and a hyphen for a minus sign, without the
 * direction marks a locale puts around them. Anything else is left as it is, for whoever reads the
 * text to refuse. A rule that must hold of the decimal as written, such as how many places it has,
 * is asked of this text and not of the binary number it becomes.
 */
fun typedDecimal(raw: String): String {
    val text = raw.trim()
    return buildString(text.length) {
        for (char in text) {
            when {
                Character.isDigit(char) -> append(Character.digit(char, 10))
                char == ',' || char == '.' || char == ArabicDecimalSeparator -> append('.')
                char == MinusSign -> append('-')
                Character.getType(char) == Character.FORMAT.toInt() -> Unit
                else -> append(char)
            }
        }
    }
}

/**
 * [parseTypedNumber] accepting an optional trailing "kg", in any case. The suffix is removed
 * without converting the value; callers remain responsible for its unit.
 */
fun parseMetricNumber(raw: String): Double? = parseTypedNumber(raw.trim().lowercase(Locale.ROOT).removeSuffix("kg"))

private const val MinusSign = '\u2212'
private const val ArabicDecimalSeparator = '\u066B'
