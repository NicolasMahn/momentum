package de.nicolasmahn.momentum.feature.statistics

import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.core.text.UiText
import de.nicolasmahn.momentum.core.text.joinedBy
import de.nicolasmahn.momentum.core.text.pluralText
import de.nicolasmahn.momentum.core.text.uiText
import de.nicolasmahn.momentum.feature.measurement.MeasurementWords
import kotlin.math.roundToInt

/**
 * The one big number of a consistency row. Activity is not a daily habit, so a share of days would
 * read as failing at three sessions a week; it is told as days a week instead.
 */
internal fun FeatureConsistency.headline(): UiText = when (unit) {
    ConsistencyUnit.DaysPerWeek -> daysPerWeek?.let(::tenths) ?: NoValue
    else -> rate?.let { uiText(R.string.statistics_percent, percent(it)) } ?: NoValue
}

/**
 * Short enough to stay on one line beside the bars; the day counts are spoken, not printed. Every
 * row's number means the same, the share of the goal its finished weeks reached, so the name is
 * enough unless the number still stands for this week alone.
 */
internal fun FeatureConsistency.caption(): UiText = when {
    rate == null -> uiText(R.string.statistics_caption_nothing, UiText.Verbatim(label))
    unit == ConsistencyUnit.DaysPerWeek -> uiText(R.string.statistics_caption_days_a_week, UiText.Verbatim(label))
    counted == 0 -> uiText(R.string.statistics_caption_this_week, UiText.Verbatim(label))
    else -> UiText.Verbatim(label)
}

/** What the row's bars say, for a reader who cannot see them. */
internal fun FeatureConsistency.spokenSummary(range: StatisticsRange): UiText {
    val title = UiText.Verbatim(label)
    val during = uiText(range.spokenLabel)
    val rate = rate ?: return uiText(R.string.statistics_consistency_nothing, title, during)
    val minutes = thisWeek?.minutes
    // Activity counts days without a goal of seven, so it is told in days, not as a share of a goal.
    if (unit == ConsistencyUnit.DaysPerWeek) {
        val days = if (counted == 0) thisWeek else null
        return uiText(R.string.statistics_consistency_days_a_week, title, days?.kept ?: kept, days?.counted ?: counted, during, tenths(rate * 7))
    }
    if (counted == 0) {
        return if (minutes != null) {
            pluralText(R.plurals.statistics_consistency_first_week, minutes.asked, title, minutes.recorded, minutes.asked, percent(rate))
        } else {
            uiText(R.string.statistics_consistency_first_week_share, title, percent(rate))
        }
    }
    val finished = if (unit == ConsistencyUnit.Weeks) {
        pluralText(R.plurals.statistics_consistency_weeks_share, counted, title, percent(rate), counted, during, kept)
    } else {
        pluralText(R.plurals.statistics_consistency_rate, counted, title, kept, counted, during, percent(rate))
    }
    val week = thisWeek ?: return finished
    val running = when {
        minutes != null -> pluralText(R.plurals.statistics_consistency_this_week, minutes.asked, minutes.recorded, minutes.asked)
        else -> uiText(R.string.statistics_consistency_this_week_share, percent(week.fill.toDouble()))
    }
    return listOf(finished, running).joinedBy(" ")
}

/** The measurement keeps its configured unit and normal numeric precision. */
internal fun MeasurementSummary.headline(words: MeasurementWords): UiText =
    change?.let(words::statisticsChange) ?: NoValue

internal fun MeasurementSummary.caption(words: MeasurementWords): UiText = words.statisticsCaption(latestValue)

/** The activity row's words, plus which kinds of activity filled the calendar. */
internal fun ActivitySummary.spokenSummary(range: StatisticsRange): UiText {
    val lead = consistency.spokenSummary(range)
    if (kinds.isEmpty()) return lead
    val perKind = kinds
        .map { (kind, days) -> pluralText(R.plurals.statistics_activity_kind_days, days, kind.labelText, days) }
        .joinedBy(", ")
    return listOf(lead, uiText(R.string.statistics_activity_kinds, perKind)).joinedBy(" ")
}

/** The streak calendar in words. It speaks of protection, never of anything done. */
internal fun StreakCalendar.spokenSummary(range: StatisticsRange): UiText {
    val parts = buildList {
        add(pluralText(R.plurals.statistics_streak_kept, keptDays))
        if (frozenDays > 0) add(pluralText(R.plurals.statistics_streak_frozen, frozenDays))
        if (protectedDays > 0) add(pluralText(R.plurals.statistics_streak_protected, protectedDays))
        if (missedDays > 0) add(pluralText(R.plurals.statistics_streak_missed, missedDays))
        if (restarts > 0) add(pluralText(R.plurals.statistics_streak_restarts, restarts))
    }
    return uiText(R.string.statistics_streak_summary, uiText(range.spokenLabel), parts.joinedBy(", "))
}

/** The gym's or an activity's name as the configuration has it, shown as written. */
internal val ActivityKind.labelText: UiText
    get() = UiText.Verbatim(label)

private val NoValue = uiText(R.string.no_value)

private fun percent(rate: Double): Int = (rate * 100).roundToInt()

private fun roundToTenths(value: Double): Double = (value * 10).roundToInt() / 10.0

private fun tenths(value: Double): UiText = UiText.Number(roundToTenths(value))
