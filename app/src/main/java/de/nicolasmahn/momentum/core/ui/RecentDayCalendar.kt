package de.nicolasmahn.momentum.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Luggage
import androidx.compose.material.icons.rounded.Sick
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.core.format.dateText
import de.nicolasmahn.momentum.core.text.UiText
import de.nicolasmahn.momentum.core.text.joinedBy
import de.nicolasmahn.momentum.core.text.resolve
import de.nicolasmahn.momentum.core.text.uiText
import de.nicolasmahn.momentum.core.time.isFutureDateKey
import de.nicolasmahn.momentum.domain.holiday.DayOff

/**
 * A day cell's ground and the ink made for it. [isBlank] marks the neutral ground of a day with
 * nothing to show, the only ground a cover's seal may take over; build it with [blankDayStyle].
 */
internal data class RecentDayCalendarStyle(
    val containerColor: Color,
    val contentColor: Color,
    val isBlank: Boolean = false,
)

/** The neutral ground of a day without a record. */
@Composable
internal fun blankDayStyle(): RecentDayCalendarStyle = RecentDayCalendarStyle(
    containerColor = MaterialTheme.colorScheme.surfaceVariant,
    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    isBlank = true,
)

internal enum class RecentDayCalendarEmphasis {
    BASELINE,
    SELECTED,
}

internal fun recentDayCalendarEmphasis(isSelected: Boolean): RecentDayCalendarEmphasis = when {
    isSelected -> RecentDayCalendarEmphasis.SELECTED
    else -> RecentDayCalendarEmphasis.BASELINE
}

internal fun recentDayCalendarDescription(
    dateKey: String,
    isToday: Boolean,
    statusDescription: String,
    actionDescription: String,
    dayOff: DayOff? = null,
): UiText {
    val date = dateText(dateKey).let { if (isToday) uiText(R.string.core_calendar_today, it) else it }
    return uiText(
        R.string.core_calendar_day_description,
        when (dayOff) {
            DayOff.Away -> uiText(R.string.core_calendar_away, date)
            DayOff.Sick -> uiText(R.string.core_calendar_sick, date)
            null -> date
        },
        statusDescription,
        actionDescription,
    )
}

/**
 * Whether a date key falls on a declared holiday or sick day. Every calendar marks those days the
 * same way, so what happened stays honest: an empty day away or sick reads as such, not as forgotten.
 */
internal val LocalDaysOff = staticCompositionLocalOf<(String) -> DayOff?> { { null } }

/** The mark a day off wears in a calendar's corner. */
internal val DayOff.glyph: ImageVector
    get() = when (this) {
        DayOff.Away -> Icons.Rounded.Luggage
        DayOff.Sick -> Icons.Rounded.Sick
    }

/**
 * The days off among [dateKeys], in order and as ranges, for a calendar that is read as a whole,
 * such as Statistics' grids: the holidays, then the sick days; null when there are none.
 */
internal fun daysOffText(dateKeys: List<String>, dayOff: (String) -> DayOff?): UiText? {
    val sentences = listOf(
        DayOff.Away to R.string.core_calendar_away_days,
        DayOff.Sick to R.string.core_calendar_sick_days,
    ).mapNotNull { (kind, sentence) ->
        rangesText(dateKeys) { dayOff(it) == kind }?.let { uiText(sentence, it) }
    }
    return sentences.takeIf { it.isNotEmpty() }?.joinedBy(" ")
}

private fun rangesText(dateKeys: List<String>, isIn: (String) -> Boolean): UiText? {
    val ranges = mutableListOf<Pair<String, String>>()
    var previousIn = false
    dateKeys.forEach { key ->
        val inside = isIn(key)
        if (inside && previousIn) ranges[ranges.lastIndex] = ranges.last().first to key
        else if (inside) ranges += key to key
        previousIn = inside
    }
    if (ranges.isEmpty()) return null
    return ranges.map { (first, last) ->
        if (first == last) dateText(first) else uiText(R.string.core_calendar_away_range, dateText(first), dateText(last))
    }.joinedBy(", ")
}

@Composable
internal fun <T> RecentDayCalendar(
    days: List<T>,
    dateKey: (T) -> String,
    selectedDateKey: String?,
    todayKey: String,
    onDayClick: (T) -> Unit,
    dayStyle: @Composable (T) -> RecentDayCalendarStyle,
    statusDescription: (T) -> String,
    actionDescription: (T) -> String,
    testTagPrefix: String,
    modifier: Modifier = Modifier,
    cover: (T) -> DayCover? = { null },
    background: @Composable BoxScope.(T) -> Unit = {},
    decoration: @Composable BoxScope.(T, Color) -> Unit = { _, _ -> },
    footer: @Composable ColumnScope.() -> Unit = {},
) {
    // The cells are already bounded objects, so the grid reads as one group on the bare
    // background; a container around it would only box a box.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTagPrefix),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CalendarWeekdayHeader()
        days.chunked(7).forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                week.forEach { day ->
                    val key = dateKey(day)
                    if (isFutureDateKey(key, todayKey)) {
                        NotYetDayCell(dateKey = key, modifier = Modifier.weight(1f))
                        return@forEach
                    }
                    RecentDayCalendarCell(
                        dateKey = key,
                        isSelected = key == selectedDateKey,
                        isToday = key == todayKey,
                        style = dayStyle(day),
                        cover = cover(day),
                        statusDescription = { statusDescription(day) },
                        actionDescription = { actionDescription(day) },
                        onClick = { onDayClick(day) },
                        modifier = Modifier.weight(1f),
                        testTag = "${testTagPrefix}_$key",
                        background = { background(day) },
                        decoration = { contentColor -> decoration(day, contentColor) },
                    )
                }
                repeat(7 - week.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
        footer()
    }
}

/** Faint enough to recede behind every real day, present enough to show where the week goes on. */
private const val NotYetDayAlpha = 0.28f

/**
 * A day that has not happened yet. It holds its place so the grid never changes shape, and it
 * is nothing else: no surface to press, no status, and no semantics, so neither a tap, a test,
 * nor TalkBack can reach a future day.
 */
@Composable
private fun NotYetDayCell(
    dateKey: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = NotYetDayAlpha),
                shape = MaterialTheme.shapes.small,
            )
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = dayOfMonthLabel(dateKey),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = NotYetDayAlpha),
        )
    }
}

private fun dayOfMonthLabel(dateKey: String): String = dateKey.takeLast(2).trimStart('0')

@Composable
private fun RecentDayCalendarCell(
    dateKey: String,
    isSelected: Boolean,
    isToday: Boolean,
    style: RecentDayCalendarStyle,
    cover: DayCover?,
    // Built only when accessibility or a test asks for them, not for 28 cells on every pass.
    statusDescription: () -> String,
    actionDescription: () -> String,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    background: @Composable BoxScope.() -> Unit,
    decoration: @Composable BoxScope.(Color) -> Unit,
) {
    val emphasis = recentDayCalendarEmphasis(isSelected)
    val border = when (emphasis) {
        RecentDayCalendarEmphasis.SELECTED -> BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface)
        RecentDayCalendarEmphasis.BASELINE -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    }
    val resources = LocalResources.current
    val dayOff = LocalDaysOff.current(dateKey)
    // A seal takes over a blank ground only: on a recorded day it would hide the record, so there
    // the cover stays a small mark at the foot.
    val seal = cover?.takeIf { style.isBlank && it.hasSeal }
    val ink = seal?.sealInk ?: style.contentColor

    Surface(
        onClick = onClick,
        modifier = modifier
            .aspectRatio(1f)
            .testTag(testTag)
            .clearAndSetSemantics {
                val action = actionDescription()
                selected = isSelected
                contentDescription = recentDayCalendarDescription(
                    dateKey = dateKey,
                    isToday = isToday,
                    statusDescription = cover?.let {
                        resources.getString(R.string.core_calendar_covered, statusDescription(), resources.getString(it.spoken))
                    } ?: statusDescription(),
                    actionDescription = action,
                    dayOff = dayOff,
                ).resolve(resources)
                onClick(label = action) {
                    onClick()
                    true
                }
            },
        shape = MaterialTheme.shapes.small,
        color = style.containerColor,
        contentColor = style.contentColor,
        border = border,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (seal != null) DayCoverSeal(seal)
            background()
            Box(modifier = Modifier.fillMaxSize().padding(5.dp)) {
                Text(
                    text = dayOfMonthLabel(dateKey),
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = ink,
                )
                decoration(ink)
                if (cover != null && seal == null) DayCoverMark(cover, style.contentColor)
                if (dayOff != null) {
                    Icon(
                        imageVector = dayOff.glyph,
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .size(11.dp),
                        tint = style.contentColor,
                    )
                }
                if (isToday) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(7.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface),
                    ) {}
                }
            }
        }
    }
}
