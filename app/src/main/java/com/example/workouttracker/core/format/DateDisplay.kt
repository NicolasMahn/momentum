package com.example.workouttracker.core.format

import android.text.format.DateFormat
import com.example.workouttracker.R
import com.example.workouttracker.core.text.UiText
import com.example.workouttracker.core.text.pluralText
import com.example.workouttracker.core.text.uiText
import com.example.workouttracker.core.time.daysBetween
import com.example.workouttracker.core.time.parseDateKey
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * How a date is shown. Each style names the fields it shows; the locale decides their order and
 * punctuation, so English reads "Sat, Oct 3" where German reads "Sa., 3. Okt.". Date keys are
 * never formatted for storage here: they stay ISO in every language.
 */
enum class DateStyle(internal val skeleton: String) {
    /** Sat, Oct 3 */
    WeekdayDayMonth("EEEMMMd"),

    /** Oct 3 */
    DayMonth("MMMd"),

    /** Oct */
    Month("MMM"),

    /** Oct 3, 2026 */
    DayMonthYear("yMMMd"),

    /** October 2026 */
    MonthYear("yMMMM"),

    /** Saturday, October 3, 2026 */
    Full("yMMMMEEEEd"),
}

fun formatDate(date: LocalDate, style: DateStyle, locale: Locale): String =
    formatters.getOrPut(locale to style) {
        DateTimeFormatter.ofPattern(formatContextMonths(DateFormat.getBestDateTimePattern(locale, style.skeleton)), locale)
    }.format(date)

/**
 * A real moment, such as when a backup was verified, as the phone's clock shows it: its date and
 * time in [zone], to the minute, on the twelve- or twenty-four-hour clock the person chose. A habit
 * day is a date key and goes through [formatDate].
 */
fun formatMoment(instant: Instant, zone: ZoneId, locale: Locale, is24Hour: Boolean): String =
    momentFormatters.getOrPut(locale to is24Hour) {
        val skeleton = DateStyle.DayMonthYear.skeleton + if (is24Hour) "Hm" else "hm"
        DateTimeFormatter.ofPattern(formatContextMonths(DateFormat.getBestDateTimePattern(locale, skeleton)), locale)
    }.format(instant.atZone(zone))

/**
 * A month alone comes back as the stand-alone "LLL", which Android's java.time prints as a number
 * ("8" for August), so it is asked for in the format form, which carries the names.
 */
private fun formatContextMonths(pattern: String): String {
    var quoted = false
    return pattern.map { char ->
        if (char == '\'') quoted = !quoted
        if (char == 'L' && !quoted) 'M' else char
    }.joinToString("")
}

/** A date key as display text; a key that is not a date is shown as it is rather than hidden. */
fun dateText(dateKey: String, style: DateStyle = DateStyle.WeekdayDayMonth): UiText =
    parseDateKey(dateKey)?.let { UiText.Date(it, style) } ?: UiText.Verbatim(dateKey)

/** "today", "yesterday", "3 days ago", then the date itself. Lower case, for use inside a sentence. */
fun relativeDateText(dateKey: String, todayKey: String): UiText {
    val days = daysBetween(dateKey, todayKey) ?: return dateText(dateKey)
    return when (days) {
        0 -> uiText(R.string.date_today)
        1 -> uiText(R.string.date_yesterday)
        in 2..6 -> pluralText(R.plurals.date_days_ago, days)
        else -> dateText(dateKey)
    }
}

private val formatters = ConcurrentHashMap<Pair<Locale, DateStyle>, DateTimeFormatter>()
private val momentFormatters = ConcurrentHashMap<Pair<Locale, Boolean>, DateTimeFormatter>()
