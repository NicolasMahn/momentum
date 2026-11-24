package de.nicolasmahn.momentum.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.core.format.DateStyle
import de.nicolasmahn.momentum.core.format.formatDate
import de.nicolasmahn.momentum.core.locale.currentLocale
import de.nicolasmahn.momentum.core.ui.FollowTodayEffect
import de.nicolasmahn.momentum.core.ui.MomentumButton
import de.nicolasmahn.momentum.core.ui.MomentumButtonVariant
import de.nicolasmahn.momentum.core.ui.MomentumRemoveButton
import de.nicolasmahn.momentum.core.ui.ReservedMessage
import de.nicolasmahn.momentum.domain.holiday.DayOff
import de.nicolasmahn.momentum.domain.holiday.DayRange
import de.nicolasmahn.momentum.domain.holiday.HolidaySchedule
import java.time.LocalDate
import java.time.YearMonth

/**
 * Holidays or sick days on one page: the calendar to choose days in, then how many days each year
 * holds and each range as a row with its inline removal. A chosen range is saved by the button
 * under the calendar, and a removal by its second tap.
 */
@Composable
internal fun DaysOffSettings(
    kind: DayOff,
    schedule: HolidaySchedule,
    todayKey: String,
    onChange: (HolidaySchedule) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val today = LocalDate.parse(todayKey)
    val words = kind.words
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    var draft by remember { mutableStateOf(DayRangeDraft()) }
    var failed by remember { mutableStateOf(false) }
    // An untouched calendar moves on with the day; a chosen range or a month looked up stays.
    FollowTodayEffect(
        todayKey = todayKey,
        canFollow = { previous -> draft.first == null && month == YearMonth.from(LocalDate.parse(previous)) },
        onFollow = { month = YearMonth.from(LocalDate.parse(it)) },
    )

    fun save(updated: HolidaySchedule): Boolean = onChange(updated).also { failed = !it }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("settings_group_${kind.tag}"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(words.note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DaysOffCalendar(
            kind = kind,
            month = month,
            today = today,
            schedule = schedule,
            draft = draft,
            onMonthChange = { month = it },
            onDayClick = { draft = draft.tap(it) },
            modifier = Modifier.padding(top = 8.dp),
        )
        val range = draft.range
        // Always one line, the hint or the choice, so choosing moves nothing below it.
        Text(
            text = range?.let { "${it.label()} · ${daysLabel(it.days)}" } ?: stringResource(R.string.settings_days_off_hint),
            modifier = Modifier.testTag("${kind.tag}_draft_summary"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        MomentumButton(
            text = stringResource(words.add),
            onClick = { range?.let { if (save(schedule.with(kind, it))) draft = DayRangeDraft() } },
            modifier = Modifier.padding(top = 8.dp),
            enabled = range != null,
            // The page's one action, so the face says plainly whether days are chosen yet.
            variant = MomentumButtonVariant.Primary,
            icon = Icons.Rounded.Add,
            testTag = "${kind.tag}_add",
        )
        ReservedMessage(if (failed) stringResource(words.error) else null, testTag = "${kind.tag}_error")

        shownYears(kind, schedule, today).forEach { year ->
            Text(
                text = daysInYear(schedule.daysIn(kind, year), year),
                modifier = Modifier
                    .padding(top = 8.dp)
                    .testTag("${kind.tag}_count_$year"),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        shownRanges(kind, schedule, today).forEach { shown ->
            DayRangeRow(
                range = shown,
                removeDescription = { label -> stringResource(words.remove, label) },
                tagPrefix = kind.tag,
                onRemove = { save(schedule.removing(kind, shown, today)) },
            )
        }
    }
}

/** "5 days in 2026", as the Settings list and the page both count them. */
@Composable
internal fun daysInYear(days: Int, year: Int): String = pluralStringResource(R.plurals.settings_days_in_year, days, days, year)

@Composable
private fun DayRangeRow(
    range: DayRange,
    removeDescription: @Composable (String) -> String,
    tagPrefix: String,
    onRemove: () -> Unit,
) {
    val label = range.label()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.testTag("${tagPrefix}_range_${range.start}"),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = daysLabel(range.days),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MomentumRemoveButton(
            description = removeDescription(label),
            onRemove = onRemove,
            testTag = "${tagPrefix}_remove_${range.start}",
            resetKey = range,
            variant = MomentumButtonVariant.Quiet,
        )
    }
}

/** The words that differ between the two kinds of days; the calendar and the rows are shared. */
internal class DayOffWords(
    @StringRes val note: Int,
    @StringRes val add: Int,
    @StringRes val remove: Int,
    @StringRes val error: Int,
    @StringRes val declaredDay: Int,
)

internal val DayOff.words: DayOffWords
    get() = when (this) {
        DayOff.Away -> DayOffWords(
            note = R.string.settings_holidays_note,
            add = R.string.settings_holiday_add,
            remove = R.string.settings_holiday_remove,
            error = R.string.settings_holiday_error,
            declaredDay = R.string.settings_holiday_day_planned,
        )
        DayOff.Sick -> DayOffWords(
            note = R.string.settings_sick_note,
            add = R.string.settings_sick_add,
            remove = R.string.settings_sick_remove,
            error = R.string.settings_sick_error,
            declaredDay = R.string.settings_sick_day_declared,
        )
    }

/** The test tags' name for each kind; holidays keep the tags they always had. */
internal val DayOff.tag: String
    get() = when (this) {
        DayOff.Away -> "holiday"
        DayOff.Sick -> "sick"
    }

internal fun HolidaySchedule.rangesOf(kind: DayOff): List<DayRange> = when (kind) {
    DayOff.Away -> holidays
    DayOff.Sick -> sickDays
}

internal fun HolidaySchedule.isDeclared(kind: DayOff, day: LocalDate): Boolean = when (kind) {
    DayOff.Away -> isHoliday(day)
    DayOff.Sick -> isSick(day)
}

internal fun HolidaySchedule.daysIn(kind: DayOff, year: Int): Int = when (kind) {
    DayOff.Away -> holidayDaysIn(year)
    DayOff.Sick -> sickDaysIn(year)
}

private fun HolidaySchedule.with(kind: DayOff, range: DayRange): HolidaySchedule = when (kind) {
    DayOff.Away -> withHoliday(range)
    DayOff.Sick -> withSickDays(range)
}

private fun HolidaySchedule.without(kind: DayOff, range: DayRange): HolidaySchedule = when (kind) {
    DayOff.Away -> withoutHoliday(range)
    DayOff.Sick -> withoutSickDays(range)
}

/**
 * This schedule with [range] taken out of [kind]'s days. A holiday under way is cut to end
 * yesterday instead: the days already lived are history, and a holiday is only planned from today.
 */
internal fun HolidaySchedule.removing(kind: DayOff, range: DayRange, today: LocalDate): HolidaySchedule {
    val rest = without(kind, range)
    val underWay = kind == DayOff.Away && range.start < today && today <= range.end
    return if (underWay) rest.with(kind, DayRange(range.start, today.minusDays(1))) else rest
}

/**
 * This year always. Holidays add every later year with planned days; sick days, declared
 * afterwards, every earlier year with some, newest first.
 */
internal fun shownYears(kind: DayOff, schedule: HolidaySchedule, today: LocalDate): List<Int> {
    val years = (listOf(today.year) + schedule.rangesOf(kind).flatMap { it.start.year..it.end.year }).distinct()
    return when (kind) {
        DayOff.Away -> years.filter { it >= today.year }.sorted()
        DayOff.Sick -> years.filter { it <= today.year }.sortedDescending()
    }
}

/**
 * The ranges a page lists, each removable: holidays from this year on, in order; every sick range,
 * newest first, because one declared by mistake re-judges its days until it is removed.
 */
internal fun shownRanges(kind: DayOff, schedule: HolidaySchedule, today: LocalDate): List<DayRange> = when (kind) {
    DayOff.Away -> schedule.holidays.filter { it.end.year >= today.year }
    DayOff.Sick -> schedule.sickDays.asReversed()
}

/** With the year, because a range can reach into next year or span the turn of one. */
@Composable
private fun DayRange.label(): String {
    val locale = currentLocale()
    val first = formatDate(start, DateStyle.DayMonthYear, locale)
    return if (start == end) first else {
        stringResource(R.string.settings_holiday_range, first, formatDate(end, DateStyle.DayMonthYear, locale))
    }
}

@Composable
private fun daysLabel(days: Int): String = pluralStringResource(R.plurals.settings_holiday_days, days, days)
