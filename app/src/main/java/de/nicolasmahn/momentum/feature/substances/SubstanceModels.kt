package de.nicolasmahn.momentum.feature.substances

import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.core.format.DateStyle
import de.nicolasmahn.momentum.core.format.dateText
import de.nicolasmahn.momentum.core.text.UiText
import de.nicolasmahn.momentum.core.text.pluralText
import de.nicolasmahn.momentum.core.text.uiText
import de.nicolasmahn.momentum.core.time.calendarWeeksWindow
import de.nicolasmahn.momentum.domain.substance.Substance
import de.nicolasmahn.momentum.domain.substance.SubstanceLog
import de.nicolasmahn.momentum.domain.substance.SubstanceNoneDay
import de.nicolasmahn.momentum.domain.substance.SubstanceSettings
import java.time.LocalDate
import java.time.temporal.ChronoUnit

internal data class SubstanceHistoryDay(
    val dateKey: String,
    val logs: List<SubstanceLog>,
    /** Whether the day said "none": logged, with nothing in it. */
    val none: Boolean = false,
) {
    val substanceIds: Set<String>
        get() = logs.mapTo(linkedSetOf()) { it.substanceId }
}

/**
 * Where one tracked substance stands: [daysWithout] is whole days with nothing logged between the
 * last record and today. A substance the person is [stayingAway] from shows that count; one just
 * tracked only says when it was last logged, so logging it never touches a count.
 */
internal data class SubstanceStatus(
    val substance: Substance,
    val latestDateKey: String?,
    val daysWithout: Long?,
    val isLoggedToday: Boolean,
    val stayingAway: Boolean = true,
)

/**
 * The calendar's weeks and a stat for each tracked substance, over the choice they were read
 * with, so every cell and tile resolves a substance the same way.
 */
internal data class SubstanceHistoryWindow(
    val days: List<SubstanceHistoryDay>,
    val statuses: List<SubstanceStatus>,
    val substances: SubstanceSettings,
)

/** A day's substances in the order the screen lists them, tracked or not: a record is never hidden. */
internal fun SubstanceHistoryWindow.substancesOn(day: SubstanceHistoryDay): List<Substance> =
    substances.all.filter { it.id in day.substanceIds }

/**
 * What one summary tile says. The tile is a stat, so the state is split into the hero it can
 * show big and the line that gives it meaning; [description] stays the full sentence, so
 * TalkBack hears exactly what the old text lines said.
 */
internal data class SubstanceStat(
    /** The streak to count up to, or null when this state has no number to show. */
    val count: Int?,
    /** The hero when there is no [count]. Null means "nothing to say yet": a quiet dash. */
    val word: UiText?,
    val unit: UiText,
    val description: UiText,
)

/**
 * The tone is deliberately flat: a long streak reads big, and a day with use is simply the day
 * it was logged. The app rewards honest logging, never the content of the log.
 */
internal fun SubstanceStatus.stat(): SubstanceStat = with(reading()) {
    SubstanceStat(
        count = count,
        word = word,
        unit = unit,
        description = uiText(R.string.substance_stat_description, substance.label(), sentence),
    )
}

/** The same state as one plain sentence, without the substance's name. */
internal fun SubstanceStatus.statusText(): UiText = reading().sentence

/**
 * One state, said once. A counted state's sentence and its unit are the same plural of the same
 * count ("76 days without"), so the tile and the sentence cannot drift apart.
 */
private class SubstanceReading(
    val count: Int?,
    val word: UiText?,
    val unit: UiText,
    val sentence: UiText,
)

private fun SubstanceStatus.reading(): SubstanceReading = when {
    latestDateKey == null -> SubstanceReading(
        count = null,
        word = null,
        unit = uiText(R.string.substance_stat_nothing_unit),
        sentence = uiText(R.string.substance_stat_nothing),
    )

    isLoggedToday -> SubstanceReading(
        count = null,
        word = uiText(R.string.substance_stat_today_word),
        unit = uiText(R.string.substance_stat_today_unit),
        sentence = uiText(R.string.substance_stat_today),
    )

    // A date after today may carry a mistyped year, so the year is shown.
    daysWithout == null -> SubstanceReading(
        count = null,
        word = null,
        unit = uiText(R.string.substance_stat_ahead_unit),
        sentence = uiText(R.string.substance_stat_ahead, dateText(latestDateKey, DateStyle.DayMonthYear)),
    )

    // "without", not "since": the count is whole days with nothing logged, so the day of the
    // last record and today are both outside it.
    stayingAway -> daysWithout.toInt().let { days ->
        SubstanceReading(
            count = days,
            word = null,
            unit = pluralText(R.plurals.substance_stat_days_without_unit, days),
            sentence = pluralText(R.plurals.substance_stat_days_without, days),
        )
    }

    // Just tracking: when it was last logged, said plainly, and never as a count of anything.
    daysWithout == 0L -> SubstanceReading(
        count = null,
        word = uiText(R.string.substance_stat_yesterday_word),
        unit = uiText(R.string.substance_stat_today_unit),
        sentence = uiText(R.string.substance_stat_yesterday),
    )

    else -> (daysWithout + 1).toInt().let { days ->
        SubstanceReading(
            count = days,
            word = null,
            unit = pluralText(R.plurals.substance_stat_days_ago_unit, days),
            sentence = pluralText(R.plurals.substance_stat_days_ago, days),
        )
    }
}

/** Only a tracked substance gets a stat; the calendar still shows every record, a day's "none" included. */
internal fun substanceHistoryWindow(
    logs: List<SubstanceLog>,
    todayKey: String,
    substances: SubstanceSettings,
    noneDays: List<SubstanceNoneDay> = emptyList(),
): SubstanceHistoryWindow {
    val today = LocalDate.parse(todayKey)
    val weeks = calendarWeeksWindow(todayKey)
    val logsByDate = weeks.groupVisible(logs) { it.dateKey }
        .mapValues { (_, dayLogs) ->
            dayLogs.sortedWith(
                compareByDescending<SubstanceLog> { it.timestampMillis }
                    .thenBy { it.id },
            )
        }
    val noneByDate = weeks.groupVisible(noneDays) { it.dateKey }
    val days = weeks.dateKeys.map { dateKey ->
        val dayLogs = logsByDate[dateKey].orEmpty()
        SubstanceHistoryDay(dateKey, dayLogs, none = dayLogs.isEmpty() && dateKey in noneByDate)
    }

    return SubstanceHistoryWindow(
        days = days,
        statuses = substances.trackedSubstances.map { substance ->
            val latestDate = logs
                .asSequence()
                .filter { it.substanceId == substance.id }
                .mapNotNull { log -> runCatching { LocalDate.parse(log.dateKey) }.getOrNull() }
                .maxOrNull()
            SubstanceStatus(
                substance = substance,
                latestDateKey = latestDate?.toString(),
                daysWithout = latestDate
                    ?.takeUnless { it.isAfter(today) }
                    ?.let { maxOf(0L, ChronoUnit.DAYS.between(it, today) - 1L) },
                isLoggedToday = latestDate == today,
                stayingAway = substances.isStayingAway(substance.id),
            )
        },
        substances = substances,
    )
}
