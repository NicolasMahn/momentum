package de.nicolasmahn.momentum.feature.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.core.format.DateStyle
import de.nicolasmahn.momentum.core.format.formatDate
import de.nicolasmahn.momentum.core.locale.currentLocale
import de.nicolasmahn.momentum.core.ui.CalendarWeekdayHeader
import de.nicolasmahn.momentum.core.ui.LocalViewColors
import de.nicolasmahn.momentum.core.ui.MomentumButton
import de.nicolasmahn.momentum.core.ui.MomentumButtonVariant
import de.nicolasmahn.momentum.domain.holiday.DayOff
import de.nicolasmahn.momentum.domain.holiday.DayRange
import de.nicolasmahn.momentum.domain.holiday.HolidaySchedule
import java.time.LocalDate
import java.time.YearMonth

/**
 * The days being chosen. The first tap picks the first day, which on its own is already a one-day
 * range; a later tap extends it to that day, and any further tap starts over.
 */
internal data class DayRangeDraft(val first: LocalDate? = null, val last: LocalDate? = null) {
    val range: DayRange? get() = first?.let { DayRange(it, last ?: it) }

    fun tap(day: LocalDate): DayRangeDraft =
        if (first == null || last != null || day < first) DayRangeDraft(first = day) else copy(last = day)
}

/**
 * Whether [day] can be chosen as [this] kind of day: a holiday is planned from today on, and a sick
 * day is declared up to today, because nobody knows ahead that they will be ill.
 */
internal fun DayOff.canChoose(day: LocalDate, today: LocalDate): Boolean = when (this) {
    DayOff.Away -> day >= today
    DayOff.Sick -> day <= today
}

/**
 * One month, Monday first like every calendar in the app. Days that cannot be chosen are inert
 * ghosts, days already declared wear the soft container, and the draft wears the accent. It always
 * draws six weeks, so moving between months never changes its height.
 */
@Composable
internal fun DaysOffCalendar(
    kind: DayOff,
    month: YearMonth,
    today: LocalDate,
    schedule: HolidaySchedule,
    draft: DayRangeDraft,
    onMonthChange: (YearMonth) -> Unit,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val thisMonth = YearMonth.from(today)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("${kind.tag}_calendar"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MonthButton(
                icon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                description = stringResource(R.string.settings_holiday_previous_month),
                enabled = kind == DayOff.Sick || month > thisMonth,
                onClick = { onMonthChange(month.minusMonths(1)) },
            )
            Text(
                text = formatDate(month.atDay(1), DateStyle.MonthYear, currentLocale()),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            MonthButton(
                icon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                description = stringResource(R.string.settings_holiday_next_month),
                enabled = kind == DayOff.Away || month < thisMonth,
                onClick = { onMonthChange(month.plusMonths(1)) },
            )
        }
        CalendarWeekdayHeader()
        val leadingBlanks = month.atDay(1).dayOfWeek.value - 1
        (0 until WeeksShown).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(CellSpacing)) {
                (0 until 7).forEach { weekday ->
                    val dayOfMonth = week * 7 + weekday - leadingBlanks + 1
                    val cell = Modifier.weight(1f)
                    if (dayOfMonth in 1..month.lengthOfMonth()) {
                        val day = month.atDay(dayOfMonth)
                        DayCell(
                            kind = kind,
                            day = day,
                            state = when {
                                !kind.canChoose(day, today) -> DayState.Inert
                                draft.range?.contains(day) == true -> DayState.Chosen
                                schedule.isDeclared(kind, day) -> DayState.Declared
                                else -> DayState.Open
                            },
                            onClick = { onDayClick(day) },
                            modifier = cell,
                        )
                    } else {
                        Spacer(cell.aspectRatio(1f))
                    }
                }
            }
        }
    }
}

private enum class DayState { Inert, Open, Declared, Chosen }

@Composable
private fun DayCell(
    kind: DayOff,
    day: LocalDate,
    state: DayState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalViewColors.current
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.small
    val (container, content) = when (state) {
        DayState.Inert -> scheme.surface to scheme.onSurfaceVariant.copy(alpha = InertAlpha)
        DayState.Open -> scheme.surface to scheme.onSurface
        DayState.Declared -> colors.container to colors.onContainer
        DayState.Chosen -> colors.accent to colors.onAccent
    }
    val fullDate = formatDate(day, DateStyle.Full, currentLocale())
    val description = if (state == DayState.Declared) stringResource(kind.words.declaredDay, fullDate) else fullDate
    val border = if (state == DayState.Inert) scheme.outline.copy(alpha = InertAlpha) else scheme.outline
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(container)
            .border(1.dp, border, shape)
            .then(
                if (state == DayState.Inert) {
                    // A day that cannot be chosen offers nothing to touch or hear.
                    Modifier.clearAndSetSemantics {}
                } else {
                    Modifier
                        .testTag("${kind.tag}_day_$day")
                        .clickable(onClick = onClick)
                        .semantics {
                            selected = state == DayState.Chosen
                            contentDescription = description
                        }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = day.dayOfMonth.toString(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = content,
        )
    }
}

@Composable
private fun MonthButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    MomentumButton(
        onClick = onClick,
        variant = MomentumButtonVariant.Quiet,
        enabled = enabled,
        faceModifier = Modifier.semantics { contentDescription = description },
        contentPadding = PaddingValues(0.dp),
        // A month with nothing to choose fades its arrow out, yet the arrow keeps its place.
        modifier = Modifier
            .size(MonthButtonSize)
            .graphicsLayer { alpha = if (enabled) 1f else 0f },
    ) {
        Icon(imageVector = icon, contentDescription = null)
    }
}

private const val WeeksShown = 6
private const val InertAlpha = 0.28f
private val CellSpacing = 4.dp
private val MonthButtonSize = 48.dp
