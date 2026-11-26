package de.nicolasmahn.momentum.core.time

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.time.Year
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

/**
 * The one definition of a date key: an ISO calendar date that survives a round trip, so
 * `2026-2-3`, `2026-02-30` and anything with a time or sign in it are not keys.

 */
fun parseDateKey(dateKey: String): LocalDate? =
    if (hasKeyShape(dateKey)) keyDate(dateKey) else runCatching { LocalDate.parse(dateKey) }.getOrNull()?.takeIf { it.toString() == dateKey }

/*
 * Every stored key has the shape `yyyy-MM-dd`, and an evaluation of a long history parses the same
 * keys thousands of times, which `LocalDate.parse` makes costly. For that shape a real date always
 * prints back as the same text, so reading the digits directly gives the parser's exact answer. Any
 * other text, such as a year past 9999, still goes through the parser and the round trip.
 */
private fun hasKeyShape(key: String): Boolean =
    key.length == 10 && key[4] == '-' && key[7] == '-' && key.indices.all { it == 4 || it == 7 || key[it] in '0'..'9' }

private fun keyDate(key: String): LocalDate? {
    val year = key.digits(0, 4)
    val month = key.digits(5, 7)
    val day = key.digits(8, 10)
    if (month !in 1..12 || day < 1 || day > Month.of(month).length(Year.isLeap(year.toLong()))) return null
    return LocalDate.of(year, month, day)
}

private fun String.digits(from: Int, until: Int): Int = (from until until).fold(0) { number, index -> number * 10 + (this[index] - '0') }

fun isValidDateKey(dateKey: String): Boolean = parseDateKey(dateKey) != null

/** [subject] names the date in the message, for the one reading a stack trace. */
fun requireValidDateKey(dateKey: String, subject: String = "Date"): LocalDate =
    requireNotNull(parseDateKey(dateKey)) { "$subject must use a valid YYYY-MM-DD value." }

/** The date a habit belongs to; late nights stay with the preceding day until 04:00. */
fun habitDate(now: ZonedDateTime): LocalDate = habitDate(now.toLocalDateTime())

fun habitDate(now: LocalDateTime): LocalDate =
    if (now.toLocalTime() < HabitDayStart) now.toLocalDate().minusDays(1) else now.toLocalDate()

fun currentHabitDate(): LocalDate = habitDate(ZonedDateTime.now(ZoneId.systemDefault()))

/**
 * The habit day, refreshed at local 04:00 and periodically for clock or timezone changes.
 * Calendar boundaries keep the wait correct through short and long daylight-saving days.
 * Collect once in MomentumApp; screens receive the resulting date key.
 */
internal fun localDateKeys(
    now: () -> ZonedDateTime = { ZonedDateTime.now(ZoneId.systemDefault()) },
    recheckMillis: Long = DateKeyRecheckMillis,
): Flow<String> = flow {
    while (true) {
        val current = now()
        emit(habitDate(current).toString())
        delay(minOf(millisUntilNextDay(current), recheckMillis))
    }
}.distinctUntilChanged()

/** How long [now] is still the same habit day. */
internal fun millisUntilNextDay(now: ZonedDateTime): Long {
    val nextDay = habitDate(now).plusDays(1).atTime(HabitDayStart).atZone(now.zone)
    return Duration.between(now.toInstant(), nextDay.toInstant())
        .toMillis()
        .coerceAtLeast(1L)
}

private const val DateKeyRecheckMillis = 15 * 60 * 1000L

val HabitDayStart = LocalTime.of(4, 0)
