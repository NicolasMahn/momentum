package com.example.workouttracker.feature.statistics

import com.example.workouttracker.core.ui.neutralViewColors
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.vector.VectorPainter
import com.example.workouttracker.core.ui.LocalDaysOff
import com.example.workouttracker.core.ui.daysOffText
import com.example.workouttracker.core.ui.glyph
import com.example.workouttracker.core.text.resolve
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.workouttracker.core.format.DateStyle
import com.example.workouttracker.core.format.formatDate
import com.example.workouttracker.core.locale.currentLocale
import com.example.workouttracker.core.time.parseDateKey
import com.example.workouttracker.core.ui.CalendarWeekdayHeader
import com.example.workouttracker.core.ui.ViewColors
import com.example.workouttracker.domain.mo.MoDayMark
import com.example.workouttracker.domain.TrackerRef
import com.example.workouttracker.core.ui.ViewIdentity
import com.example.workouttracker.core.ui.LocalTrackerIdentity
import com.example.workouttracker.domain.activity.ActivityConfiguration
import com.example.workouttracker.feature.logactivity.lookOf
import com.example.workouttracker.feature.mo.drawFreezeCube
import com.example.workouttracker.feature.mo.drawPassToken
import com.example.workouttracker.feature.mo.drawRestToken
import com.example.workouttracker.feature.mo.drawSpentCoin
import com.example.workouttracker.feature.mo.drawStreakCoin
import com.example.workouttracker.domain.holiday.DayOff
import java.util.Locale

/**
 * The streak over the range, one Monday-to-Sunday week per row, drawn with the objects the streak
 * is made of: the coin for a kept day, the ice cube for a day a freeze saved, the task's own token
 * for a day a token saved, a plain token where the token was no task's of Mo's, and the cold coin
 * for a miss.
 */
@Composable
internal fun StreakCalendarGrid(
    calendar: StreakCalendar,
    rowHeight: Dp,
    description: String,
    reveal: () -> Float,
    modifier: Modifier = Modifier,
) {
    val looks = tokenLooks(calendar.days.flatMap { it.tokens }.distinct(), LocalTrackerIdentity.current)
    val neutral = neutralViewColors
    val quiet = MaterialTheme.colorScheme.outlineVariant
    val open = MaterialTheme.colorScheme.onSurfaceVariant
    val ghost = MaterialTheme.colorScheme.outline.copy(alpha = GhostAlpha)
    val ring = MaterialTheme.colorScheme.background

    WeekGrid(
        days = calendar.days,
        dateKey = StreakCalendarDay::dateKey,
        upcoming = { it.mark == MoDayMark.Upcoming },
        rowHeight = rowHeight,
        description = description,
        reveal = reveal,
        modifier = modifier.testTag("statistics_streak_calendar"),
    ) { day ->
        when (day.mark) {
            MoDayMark.Upcoming -> drawGhost(ghost)
            // A sick day also wears its mark in the corner, as every calendar draws it.
            MoDayMark.NothingDue, MoDayMark.Sick -> drawQuietDot(quiet)
            MoDayMark.Open -> inObjectSquare {
                drawCircle(open, radius = size.minDimension * 0.36f, style = Stroke(width = 1.5.dp.toPx()))
            }
            MoDayMark.Perfect -> inObjectSquare { drawStreakCoin() }
            MoDayMark.Frozen -> inObjectSquare { drawFreezeCube() }
            // The miss that ended a streak is the event; the ones after it only stay cold, smaller.
            MoDayMark.Missed -> inObjectSquare(if (day.restart) 1f else StayedColdScale) { drawSpentCoin() }
            MoDayMark.Protected -> inObjectSquare {
                val first = day.tokens.firstOrNull()?.let(looks::getValue)
                if (first == null) {
                    // A token of a view Mo does not watch, a made one's or Substances', protected it: a plain one.
                    drawPassToken(neutral, stamp = null)
                } else {
                    // The icon would only blur into the hexagon's face at the smallest rows.
                    drawPassToken(first.colors, first.stamp.takeIf { size.minDimension >= MinimumStampedToken.toPx() })
                    drawExtraTokens(day.tokens.drop(1).map { looks.getValue(it).colors.accent }, ring)
                }
            }
        }
        if (day.rested) drawRestBadge()
    }
}

/**
 * The days something active was recorded, in Activity's own colour, each marked with what it was:
 * the dumbbell for gym, the activity's own icon for anything else, and a dot when there was more.
 */
@Composable
internal fun ActivityCalendarGrid(
    days: List<ActivityCalendarDay>,
    configuration: ActivityConfiguration,
    colors: ViewColors,
    rowHeight: Dp,
    description: String,
    reveal: () -> Float,
    modifier: Modifier = Modifier,
) {
    val glyphs = activityGlyphs(days.flatMap { it.kinds }.distinct(), configuration)
    val empty = MaterialTheme.colorScheme.surfaceVariant
    val ghost = MaterialTheme.colorScheme.outline.copy(alpha = GhostAlpha)

    WeekGrid(
        days = days,
        dateKey = ActivityCalendarDay::dateKey,
        upcoming = ActivityCalendarDay::upcoming,
        rowHeight = rowHeight,
        description = description,
        reveal = reveal,
        modifier = modifier.testTag("statistics_activity_calendar"),
    ) { day ->
        val kind = day.kinds.firstOrNull()
        when {
            day.upcoming -> drawGhost(ghost)
            kind == null -> drawCell(empty)
            else -> {
                drawCell(colors.accent)
                drawGlyph(glyphs.getValue(kind), colors.onAccent)
                if (day.kinds.size > 1) {
                    drawCircle(
                        colors.onAccent,
                        radius = size.height * 0.08f,
                        center = Offset(size.width - size.height * 0.2f, size.height * 0.2f),
                    )
                }
            }
        }
    }
}

/** A small picture of an activity kind, the same as its calendar cell, for the key under the grid. */
@Composable
internal fun ActivityKindSwatch(kind: ActivityKind, configuration: ActivityConfiguration, colors: ViewColors, modifier: Modifier = Modifier) {
    val glyph = rememberVectorPainter(configuration.lookOf(kind.mark).icon)
    Canvas(modifier) {
        drawCell(colors.accent)
        drawGlyph(glyph, colors.onAccent)
    }
}

/**
 * Seven columns under the shared weekday header and one row per week, all in one canvas: a year is
 * 364 cells, which as composables would cost far more than it shows. A quiet seam runs through the
 * gaps where one month ends and the next begins, and the month's name sits at its right end, so a
 * month that starts mid-week still reads exactly. Days after today draw no seam. The rows are
 * uncovered from the top by [reveal], so the grid draws in without ever being measured again.
 */
@Composable
private fun <T> WeekGrid(
    days: List<T>,
    dateKey: (T) -> String,
    upcoming: (T) -> Boolean,
    rowHeight: Dp,
    description: String,
    reveal: () -> Float,
    modifier: Modifier = Modifier,
    drawDay: DrawScope.(T) -> Unit,
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val seamColor = MaterialTheme.colorScheme.outline.copy(alpha = SeamAlpha)
    val weeks = remember(days) { days.chunked(DaysPerWeek) }
    val locale = currentLocale()
    val starts = remember(weeks, locale) {
        monthStarts(weeks.map { week -> week.map { day -> dateKey(day).takeUnless { upcoming(day) } } }, locale)
    }
    val labels = remember(starts, labelStyle) { starts.associate { it.row to measurer.measure(it.name, labelStyle) } }
    val density = LocalDensity.current
    val labelColumn = with(density) { (labels.values.maxOfOrNull { it.size.width } ?: 0).toDp() } + LabelGap
    val rowGap = if (rowHeight < CompactRow) CompactRowGap else RowGap
    val dayOff = LocalDaysOff.current
    // The marks are drawn, so the grid also says which days were away or sick.
    val spoken = listOfNotNull(description, daysOffText(days.map(dateKey), dayOff)?.resolve()).joinToString(" ")
    val glyphs = DayOff.entries.associateWith { rememberVectorPainter(it.glyph) }
    val dayOffTint = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CalendarWeekdayHeader(Modifier.padding(end = labelColumn))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(rowHeight * weeks.size + rowGap * (weeks.size - 1).coerceAtLeast(0))
                .clearAndSetSemantics { contentDescription = spoken },
        ) {
            val gap = ColumnGap.toPx()
            val gridWidth = size.width - labelColumn.toPx()
            val cellWidth = (gridWidth - gap * (DaysPerWeek - 1)) / DaysPerWeek
            val cellHeight = rowHeight.toPx()
            val rowStep = cellHeight + rowGap.toPx()
            val seam = Stroke(width = SeamWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)

            clipRect(bottom = size.height * reveal()) {
                weeks.forEachIndexed { row, week ->
                    val top = row * rowStep
                    week.forEachIndexed { column, day ->
                        val left = column * (cellWidth + gap)
                        inset(left, top, size.width - left - cellWidth, size.height - top - cellHeight) {
                            drawDay(day)
                            dayOff(dateKey(day))?.let { drawDayOffMark(glyphs.getValue(it), dayOffTint) }
                        }
                    }
                }
                starts.forEach { start ->
                    if (start.row > 0 || start.column > 0) {
                        drawPath(seamPath(start, gridWidth, cellWidth + gap, rowStep, gap, rowGap.toPx()), seamColor, style = seam)
                    }
                    labels[start.row]?.takeIf { start.labelled }?.let { text ->
                        val top = start.row * rowStep
                        drawText(text, topLeft = Offset(gridWidth + LabelGap.toPx(), top + (cellHeight - text.size.height) / 2f))
                    }
                }
            }
        }
    }
}

/** The first shown day of a month, where its seam turns, and whether its name is the one its row shows. */
private data class MonthStart(val row: Int, val column: Int, val name: String, val labelled: Boolean)

/**
 * Each month's first shown day: the first day of the grid, then every 1st. A name sits at the right
 * end of its row, so when a month begins in the first row the earlier month gives that row up.
 * Days after today are null and begin nothing.
 */
private fun monthStarts(weeks: List<List<String?>>, locale: Locale): List<MonthStart> {
    val starts = weeks.flatMapIndexed { row, week ->
        week.mapIndexedNotNull { column, key ->
            key?.takeIf { (row == 0 && column == 0) || it.endsWith("-01") }
                ?.let(::parseDateKey)
                ?.let { MonthStart(row, column, formatDate(it, DateStyle.Month, locale), labelled = true) }
        }
    }
    return starts.mapIndexed { index, start -> start.copy(labelled = starts.getOrNull(index + 1)?.row != start.row) }
}

/**
 * The seam above a month's first row from the right edge to its first day, down that day's left
 * side, and along the row's foot back to the left edge. Every line runs in the middle of a gap.
 */
private fun seamPath(start: MonthStart, gridWidth: Float, columnStep: Float, rowStep: Float, gap: Float, rowGap: Float) = Path().apply {
    val above = start.row * rowStep - rowGap / 2f
    val below = above + rowStep
    val turn = start.column * columnStep - gap / 2f
    if (start.column == 0) {
        moveTo(gridWidth, above)
        lineTo(0f, above)
    } else {
        if (start.row == 0) moveTo(turn, 0f) else {
            moveTo(gridWidth, above)
            lineTo(turn, above)
        }
        lineTo(turn, below)
        lineTo(0f, below)
    }
}

/** What a task's token looks like here: the colours and icon it wears on Home. */
private class TokenLook(val colors: ViewColors, val stamp: VectorPainter)

@Composable
private fun tokenLooks(refs: List<TrackerRef>, identityOf: @Composable (TrackerRef) -> ViewIdentity): Map<TrackerRef, TokenLook> {
    // The same order on every composition, so the painters are remembered one to one.
    return refs.associateWith { ref ->
        val identity = identityOf(ref)
        TokenLook(identity.colors, rememberVectorPainter(identity.icon))
    }
}

@Composable
private fun activityGlyphs(kinds: List<ActivityKind>, configuration: ActivityConfiguration): Map<ActivityKind, VectorPainter> =
    kinds.associateWith { kind -> rememberVectorPainter(configuration.lookOf(kind.mark).icon) }

/** The objects are square; the cell may be wider, so they stand in its middle at its full height. */
private inline fun DrawScope.inObjectSquare(scale: Float = 1f, draw: DrawScope.() -> Unit) {
    val side = size.minDimension * scale
    inset(horizontal = (size.width - side) / 2f, vertical = (size.height - side) / 2f) { draw() }
}

private fun DrawScope.drawGhost(color: Color) = drawRoundRect(
    color = color,
    cornerRadius = CornerRadius(CellCorner.toPx()),
    style = Stroke(width = 1.dp.toPx()),
    topLeft = Offset(0.5.dp.toPx(), 0.5.dp.toPx()),
    size = Size(size.width - 1.dp.toPx(), size.height - 1.dp.toPx()),
)

private fun DrawScope.drawQuietDot(color: Color) =
    drawCircle(color, radius = size.minDimension * 0.12f)

private fun DrawScope.drawCell(color: Color) =
    drawRoundRect(color, cornerRadius = CornerRadius(CellCorner.toPx()))

private fun DrawScope.drawGlyph(glyph: VectorPainter, tint: Color) {
    val side = size.minDimension * GlyphFraction
    translate(left = (size.width - side) / 2f, top = (size.height - side) / 2f) {
        with(glyph) { draw(Size(side, side), colorFilter = ColorFilter.tint(tint)) }
    }
}

/** A declared holiday or sick day, as a small mark in the cell's upper left, as on every other calendar. */
private fun DrawScope.drawDayOffMark(glyph: VectorPainter, tint: Color) {
    val side = size.minDimension * DayOffMarkFraction
    with(glyph) { draw(Size(side, side), colorFilter = ColorFilter.tint(tint)) }
}

private const val DayOffMarkFraction = 0.42f

/**
 * The other tasks a day's tokens covered, as dots in their colours on the token's upper right, each
 * cut out of what lies under it so it reads as a mark and not as a stain.
 */
private fun DrawScope.drawExtraTokens(colors: List<Color>, ring: Color) {
    val radius = size.minDimension * 0.14f
    colors.take(MaximumExtraTokens).forEachIndexed { index, color ->
        val centre = Offset(size.width - radius - index * radius * 1.8f, radius)
        drawCircle(ring, radius = radius + 1.dp.toPx(), center = centre)
        drawCircle(color, radius = radius, center = centre)
    }
}

/** A planned rest day: the rest token, small, in the cell's lower right corner. */
private fun DrawScope.drawRestBadge() {
    val side = size.minDimension * 0.5f
    inset(left = size.width - side, top = size.height - side, right = 0f, bottom = 0f) {
        drawRestToken()
    }
}

private const val DaysPerWeek = 7
private const val GhostAlpha = 0.28f
private const val GlyphFraction = 0.62f
private const val StayedColdScale = 0.55f
private const val MaximumExtraTokens = 2
private val LabelGap = 8.dp
private val SeamWidth = 1.dp
private const val SeamAlpha = 0.55f
private val ColumnGap = 6.dp
private val RowGap = 6.dp
private val CompactRowGap = 3.dp
private val CompactRow = 20.dp
private val CellCorner = 6.dp
private val MinimumStampedToken = 22.dp
