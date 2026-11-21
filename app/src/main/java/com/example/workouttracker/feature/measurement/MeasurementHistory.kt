package com.example.workouttracker.feature.measurement

import android.content.res.Resources
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.workouttracker.R
import com.example.workouttracker.core.format.DateStyle
import com.example.workouttracker.core.format.dateText
import com.example.workouttracker.core.format.formatMetricNumber
import com.example.workouttracker.core.locale.currentLocale
import com.example.workouttracker.core.text.UiText
import com.example.workouttracker.core.text.joinedBy
import com.example.workouttracker.core.text.pluralText
import com.example.workouttracker.core.text.resolve
import com.example.workouttracker.core.text.uiText
import com.example.workouttracker.core.ui.CountUpText
import com.example.workouttracker.core.ui.MomentumChoiceTile
import com.example.workouttracker.core.ui.MomentumChoiceTileRow
import com.example.workouttracker.core.ui.ReservedMessage
import com.example.workouttracker.core.ui.LocalViewColors
import com.example.workouttracker.core.ui.ViewColors
import com.example.workouttracker.core.ui.platformAnimationsEnabled
import java.util.Locale
import kotlin.math.roundToInt

/**
 * A measurement's selected value, chart and legend use its configured label and unit through
 * [words]. [chartSpacing] keeps guides at least five configured steps apart.
 *
 * Pass [onRangeChange] to let the reader change how far back the chart looks; without it the
 * window stays as given, which is what a specimen with a fixed fixture wants.
 */
@Composable
internal fun MeasurementHistory(
    window: MeasurementHistoryWindow,
    selectedDateKey: String,
    onDateSelected: (String) -> Unit,
    words: MeasurementWords,
    chartSpacing: Double,
    /** The selected day's saved value, which the screen knows even when the day lies outside [window]. */
    selectedValue: Double? = window.loggedOn(selectedDateKey),
    range: MeasurementHistoryRange = MeasurementHistoryRange.FourWeeks,
    onRangeChange: ((MeasurementHistoryRange) -> Unit)? = null,
    /** The colors supplied by the measurement's view theme. */
    colors: ViewColors = LocalViewColors.current,
) {
    val motionEnabled = platformAnimationsEnabled()
    val selectedDay = window.days.firstOrNull { it.dateKey == selectedDateKey }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("measurement_history"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MeasurementSelectedValue(
            dateKey = selectedDateKey,
            value = selectedValue,
            trendValue = selectedDay?.trendValue,
            colors = colors,
            motionEnabled = motionEnabled,
            words = words,
        )
        MeasurementTrendChart(
            window = window,
            selectedDateKey = selectedDateKey,
            selectedValue = selectedValue,
            onDateSelected = onDateSelected,
            colors = colors,
            motionEnabled = motionEnabled,
            words = words,
            minimumSpacing = chartSpacing,
            modifier = Modifier.height(ChartHeight),
        )
        MeasurementDateAxis(days = window.days)
        if (onRangeChange != null) {
            MeasurementRangeChoice(range = range, onRangeChange = onRangeChange)
        }
    }
}

/** The reading of the chart is the number itself, so it is type rather than a tooltip. */
@Composable
private fun MeasurementSelectedValue(
    dateKey: String,
    value: Double?,
    trendValue: Double?,
    colors: ViewColors,
    motionEnabled: Boolean,
    words: MeasurementWords,
) {
    // A day with no recorded value says so and invites the entry; it never borrows the trend's
    // number, which would read as a measurement that was never taken.
    val locale = currentLocale()
    val note = when {
        value == null -> words.noneLogged
        trendValue != null -> words.trend(trendValue)
        else -> null
    }?.resolve()
    val spokenValue = value?.let { words.spoken(it).resolve() }
    val resources = LocalResources.current
    val numberStyle = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold)

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        NumberWithUnit(unit = words.unitBesideTheNumber, unitShown = value != null, style = numberStyle, color = colors.label) {
            CountUpText(
                target = value,
                format = { value, settled -> measurementValueText(words, value, settled, resources, locale) },
                motionEnabled = motionEnabled,
                // With no measurement the dash is decoration; the note below carries the meaning.
                modifier = Modifier.clearAndSetSemantics {
                    if (spokenValue != null) contentDescription = spokenValue
                },
                style = numberStyle,
                color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant else colors.label,
            )
        }
        Text(
            text = dateText(dateKey).resolve(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ReservedMessage(
            text = note,
            testTag = "measurement_selected_note",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/**
 * The large number as it is drawn, in the measurement's [words]. Mid-count values are rounded so
 * the number does not jitter in width while it travels.
 */
internal fun measurementValueText(words: MeasurementWords, value: Double, settled: Boolean, resources: Resources, locale: Locale): String {
    val shown = if (settled) value else (value * 10.0).roundToInt() / 10.0
    return words.drawn(formatMetricNumber(shown, locale)).resolve(resources)
}

/**
 * The large [number] with its [unit] beside it as text of its own, or [number] alone without one.
 *
 * Whether the unit stands on the number's line is decided by the unit and the width alone, against
 * the room a number of ordinary length takes ([ReservedNumber]), never by the day's own number: so
 * stepping through the days cannot move what sits below. A unit that has no room there takes a line
 * to itself; the number is never broken. With no number to show the unit is not drawn, and keeps
 * its place. TalkBack hears the unit with the number, from [number]'s own description.
 *
 * The unit is measured in that same room, whatever the day's number is and while it counts, so it
 * is as tall every day. Beside a number wider than an ordinary one it keeps its one line and runs
 * past the edge rather than wrapping under the number, which would push the date and the chart
 * down for that day alone.
 */
@Composable
private fun NumberWithUnit(
    unit: String?,
    unitShown: Boolean,
    style: TextStyle,
    color: Color,
    number: @Composable () -> Unit,
) {
    if (unit == null) {
        number()
        return
    }
    Layout(
        content = {
            number()
            Text(
                text = unit,
                modifier = Modifier
                    .graphicsLayer { alpha = if (unitShown) 1f else 0f }
                    .clearAndSetSemantics {},
                style = style,
                color = color,
            )
            Text(
                text = ReservedNumber,
                modifier = Modifier.clearAndSetSemantics {},
                style = style,
                maxLines = 1,
                softWrap = false,
            )
        },
        modifier = Modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val (numberMeasurable, unitMeasurable, reservedMeasurable) = measurables
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val gap = UnitGap.roundToPx()
        val shown = numberMeasurable.measure(loose)
        val reserved = reservedMeasurable.measure(loose).width
        val unitWidth = unitMeasurable.maxIntrinsicWidth(Constraints.Infinity)
        val beside = !constraints.hasBoundedWidth || reserved + gap + unitWidth <= constraints.maxWidth
        val unitRoom = when {
            !constraints.hasBoundedWidth -> Constraints.Infinity
            // The room beside the reserved number, which holds the unit on one line by the decision above.
            beside -> constraints.maxWidth - reserved - gap
            else -> constraints.maxWidth
        }
        val unitText = unitMeasurable.measure(loose.copy(maxWidth = unitRoom))
        val unitX = if (beside) shown.width + gap else 0
        val numberBaseline = shown[FirstBaseline]
        val unitBaseline = unitText[FirstBaseline]
        val unitY = when {
            !beside -> shown.height
            numberBaseline == AlignmentLine.Unspecified || unitBaseline == AlignmentLine.Unspecified -> 0
            // On one line the two stand on one baseline, as the letters of one text would.
            else -> (numberBaseline - unitBaseline).coerceAtLeast(0)
        }
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else unitX + unitText.width
        layout(width, maxOf(shown.height, unitY + unitText.height)) {
            shown.placeRelative(0, 0)
            unitText.placeRelative(unitX, unitY)
        }
    }
}

@Composable
internal fun MeasurementDateAxis(days: List<MeasurementHistoryDay>) {
    val labelled = listOf(0, days.size / 2, days.lastIndex).distinct()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ChartHorizontalPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        labelled.forEach { index ->
            Text(
                text = dateText(days[index].dateKey, DateStyle.DayMonth).resolve(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MeasurementRangeChoice(
    range: MeasurementHistoryRange,
    onRangeChange: (MeasurementHistoryRange) -> Unit,
) {
    MomentumChoiceTileRow {
        MeasurementHistoryRange.entries.forEach { option ->
            MomentumChoiceTile(
                label = stringResource(option.label),
                selected = option == range,
                onClick = { onRangeChange(option) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                testTag = "measurement_range_${option.name.lowercase()}",
            )
        }
    }
}

/**
 * Without [onDateSelected] the chart is a picture: it takes no touches and marks no day, which is
 * how Statistics shows the same trend without becoming a second place to pick a date.
 */
@Composable
internal fun MeasurementTrendChart(
    window: MeasurementHistoryWindow,
    selectedDateKey: String?,
    onDateSelected: ((String) -> Unit)?,
    colors: ViewColors,
    motionEnabled: Boolean,
    /** What the chart says for TalkBack: the measurement's own words. */
    words: MeasurementWords,
    /** The smallest distance between two guides and the least room around the values: five of the measurement's steps. */
    minimumSpacing: Double,
    modifier: Modifier = Modifier,
    selectedValue: Double? = window.loggedOn(selectedDateKey),
) {
    val selectDate by rememberUpdatedState(onDateSelected)
    val interactive = onDateSelected != null
    val geometry = remember(window, minimumSpacing) { measurementChartGeometry(window, minimumSpacing) }
    val axis = geometry.axis
    val maximumDayIndex = geometry.maximumDayIndex
    val linePoints = geometry.linePoints
    val curve = geometry.curve
    val runs = geometry.runs
    // Reused across frames: the reveal redraws the chart every frame, and a path is not free.
    val wholePath = remember { Path() }
    val fillPath = remember { Path() }
    val runPath = remember { Path() }

    val measurer = rememberTextMeasurer()
    val locale = currentLocale()
    val summary = chartSummary(window, selectedDateKey, selectedValue, words).resolve()
    val guideColor = MaterialTheme.colorScheme.outlineVariant
    val guideLabelStyle = MaterialTheme.typography.labelSmall.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    // The dots sit on the app background, so their ring is cut out of the line with that color.
    val ringColor = MaterialTheme.colorScheme.background
    val fillBrush = Brush.verticalGradient(
        listOf(colors.accent.copy(alpha = FillTopAlpha), colors.accent.copy(alpha = 0f)),
    )

    val reveal = remember { Animatable(0f) }
    LaunchedEffect(motionEnabled) {
        if (motionEnabled) reveal.animateTo(1f, tween(RevealMillis, easing = FastOutSlowInEasing))
        else reveal.snapTo(1f)
    }
    val selectionPop = remember { Animatable(1f) }
    LaunchedEffect(selectedDateKey, motionEnabled) {
        if (!motionEnabled) return@LaunchedEffect
        selectionPop.snapTo(SelectionPopStartScale)
        selectionPop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 650f))
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            // A tap picks a day, even one that was never weighed, so it can be filled in now.
            .pointerInput(window.days, interactive) {
                if (!interactive) return@pointerInput
                detectTapGestures { offset ->
                    dayAt(offset.x) { measurementDayAtChartFraction(window.days, it) }
                        ?.let { selectDate?.invoke(it) }
                }
            }
            .pointerInput(window.days, interactive) {
                if (!interactive) return@pointerInput
                var scrubX = 0f
                fun scrub() {
                    dayAt(scrubX) { measurementRecordedDayAtChartFraction(window.days, it) }
                        ?.let { selectDate?.invoke(it) }
                }
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        scrubX = offset.x
                        scrub()
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        scrubX += dragAmount
                        scrub()
                    },
                )
            }
            // Before the summary, which clears whatever semantics follow it on this node.
            .testTag("measurement_history_chart")
            .clearAndSetSemantics { contentDescription = summary },
    ) {
        val left = ChartHorizontalPadding.toPx()
        val right = size.width - left
        val top = ChartVerticalPadding.toPx()
        val bottom = size.height - top
        val chartWidth = (right - left).coerceAtLeast(1f)
        val chartHeight = (bottom - top).coerceAtLeast(1f)

        fun xForDay(dayIndex: Int): Float = left + chartWidth * dayIndex / maximumDayIndex

        fun yForValue(value: Double): Float =
            if ((axis.maximum - axis.minimum).isFinite()) {
                // Keep the established drawing arithmetic for ordinary ranges.
                bottom - chartHeight * ((value - axis.minimum) / (axis.maximum - axis.minimum).coerceAtLeast(0.0001)).toFloat()
            } else top + chartHeight * axis.yFraction(value).toFloat()

        fun offsetFor(point: MeasurementChartPoint): Offset = Offset(
            x = left + chartWidth * point.xFraction.toFloat(),
            y = top + chartHeight * point.yFraction.toFloat(),
        )

        if (linePoints.isEmpty() && window.recordedDays.isEmpty()) {
            drawLine(
                color = guideColor,
                start = Offset(left, size.height / 2f),
                end = Offset(right, size.height / 2f),
                strokeWidth = GuideStroke.toPx(),
                cap = StrokeCap.Round,
            )
            return@Canvas
        }

        axis.guidesDescending.forEach { guide ->
            val y = yForValue(guide)
            drawLine(
                color = guideColor,
                start = Offset(left, y),
                end = Offset(right, y),
                strokeWidth = GuideStroke.toPx(),
            )
            val label = measurer.measure(formatMetricNumber(guide, locale), guideLabelStyle)
            drawText(label, topLeft = Offset(left, y - label.size.height - GuideLabelGap.toPx()))
        }

        clipRect(right = size.width * reveal.value) {
            if (curve.isNotEmpty()) {
                val start = offsetFor(linePoints.first())
                val whole = wholePath.apply {
                    rewind()
                    moveTo(start.x, start.y)
                    curve.forEach { segment -> cubicThrough(segment, ::offsetFor) }
                }
                val fill = fillPath.apply {
                    rewind()
                    addPath(whole)
                    lineTo(offsetFor(linePoints.last()).x, bottom)
                    lineTo(start.x, bottom)
                    close()
                }
                drawPath(fill, brush = fillBrush)
                // A stretch with no readings behind it is drawn as the estimate it is.
                runs.forEach { run ->
                    val head = offsetFor(run.segments.first().start)
                    val path = runPath.apply {
                        rewind()
                        moveTo(head.x, head.y)
                        run.segments.forEach { segment -> cubicThrough(segment, ::offsetFor) }
                    }
                    drawPath(
                        path = path,
                        color = colors.accent.copy(alpha = if (run.estimated) EstimateAlpha else 1f),
                        style = Stroke(
                            width = LineStroke.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                            pathEffect = if (run.estimated) {
                                PathEffect.dashPathEffect(
                                    floatArrayOf(EstimateDash.toPx(), EstimateGap.toPx()),
                                )
                            } else {
                                null
                            },
                        ),
                    )
                }
            }

            // Only measured days get a dot; the line may bridge a gap, a dot may never invent one.
            window.recordedDays.forEach { day ->
                val center = Offset(xForDay(day.dayIndex), yForValue(day.log!!.value))
                if (window.smoothed) {
                    // A hairline ties the reading to the trend it moved, without competing with it.
                    day.trendValue?.let { trendValue ->
                        drawLine(
                            color = colors.accent.copy(alpha = StemAlpha),
                            start = center,
                            end = Offset(center.x, yForValue(trendValue)),
                            strokeWidth = StemStroke.toPx(),
                        )
                    }
                    drawDot(
                        center = center,
                        radius = MeasurementRadius.toPx(),
                        color = colors.accent.copy(alpha = MeasurementAlpha),
                        ringColor = ringColor,
                        ring = MeasurementRing,
                    )
                } else {
                    drawDot(center, DotRadius.toPx(), colors.accent, ringColor, DotRing)
                }
            }

            val selectedDay = window.days.firstOrNull { it.dateKey == selectedDateKey }
            if (selectedDay != null) {
                val x = xForDay(selectedDay.dayIndex)
                val measured = selectedDay.log?.value
                if (measured != null) {
                    drawSelectedDot(Offset(x, yForValue(measured)), selectionPop.value, colors, ringColor)
                } else {
                    // An empty day still has to be visible as the pick it was: a ring on the line.
                    val onCurve = measurementChartYAt(curve, ((x - left) / chartWidth).toDouble())
                    val y = onCurve?.let { top + chartHeight * it.toFloat() } ?: bottom
                    drawEmptyMarker(Offset(x, y), selectionPop.value, colors, ringColor)
                }
            }
        }
    }
}

/** Geometry is independent of selection and motion, so animation frames reuse it. */
internal class MeasurementChartGeometry(
    val axis: MeasurementChartAxis,
    val maximumDayIndex: Int,
    val linePoints: List<MeasurementChartPoint>,
    val curve: List<MeasurementChartSegment>,
    val runs: List<MeasurementChartRun>,
)

internal fun measurementChartGeometry(window: MeasurementHistoryWindow, minimumSpacing: Double = DefaultMeasurementChartSpacing): MeasurementChartGeometry {
    val axis = measurementChartAxis(
        window.trendPoints.map { it.value } + window.recordedDays.map { it.log!!.value },
        minimumSpacing,
    )
    val maximumDayIndex = (window.days.size - 1).coerceAtLeast(1)
    val linePoints = measurementChartLinePoints(window.trendPoints, axis, maximumDayIndex)
    val curve = measurementChartCurve(linePoints)
    return MeasurementChartGeometry(
        axis = axis,
        maximumDayIndex = maximumDayIndex,
        linePoints = linePoints,
        curve = curve,
        runs = measurementChartRuns(curve, window.trendPoints),
    )
}

private fun Path.cubicThrough(
    segment: MeasurementChartSegment,
    offsetFor: (MeasurementChartPoint) -> Offset,
) {
    val first = offsetFor(segment.firstControl)
    val second = offsetFor(segment.secondControl)
    val end = offsetFor(segment.end)
    cubicTo(first.x, first.y, second.x, second.y, end.x, end.y)
}

private fun DrawScope.drawDot(
    center: Offset,
    radius: Float,
    color: Color,
    ringColor: Color,
    ring: Dp,
) {
    drawCircle(color = ringColor, radius = radius + ring.toPx(), center = center)
    drawCircle(color = color, radius = radius, center = center)
}

/** The selected dot carries the app's ledge: a darker lower edge peeking out under the face. */
private fun DrawScope.drawSelectedDot(
    center: Offset,
    scale: Float,
    colors: ViewColors,
    ringColor: Color,
) {
    val radius = SelectedDotRadius.toPx() * scale
    drawCircle(color = ringColor, radius = radius + SelectedDotRing.toPx(), center = center)
    drawCircle(
        color = colors.ledge,
        radius = radius,
        center = center.copy(y = center.y + SelectedDotLedge.toPx()),
    )
    drawCircle(color = colors.accent, radius = radius, center = center)
}

/** Hollow, because there is nothing inside it: the day is picked but nothing was weighed. */
private fun DrawScope.drawEmptyMarker(
    center: Offset,
    scale: Float,
    colors: ViewColors,
    ringColor: Color,
) {
    val radius = SelectedDotRadius.toPx() * scale
    drawCircle(color = ringColor, radius = radius + SelectedDotRing.toPx(), center = center)
    drawCircle(
        color = colors.accent,
        radius = radius,
        center = center,
        style = Stroke(width = EmptyMarkerStroke.toPx()),
    )
}

/** Turns a touch into the day under it, in the plot's own space rather than the canvas's. */
private fun PointerInputScope.dayAt(x: Float, lookUp: (Float) -> String?): String? {
    val padding = ChartHorizontalPadding.toPx()
    val plotWidth = (size.width - padding * 2).coerceAtLeast(1f)
    return lookUp((x - padding) / plotWidth)
}

private fun MeasurementHistoryWindow.loggedOn(dateKey: String?): Double? = days.firstOrNull { it.dateKey == dateKey }?.log?.value

private fun chartSummary(window: MeasurementHistoryWindow, selectedDateKey: String?, selectedValue: Double?, words: MeasurementWords): UiText {
    val recorded = window.recordedDays
    val dayCount = window.days.size
    if (window.trendPoints.isEmpty() && recorded.isEmpty()) return words.chartEmpty(dayCount)

    val latest = window.trendPoints.lastOrNull()?.value ?: recorded.last().log!!.value
    val hasTrend = window.smoothed && window.trendPoints.isNotEmpty()
    val lead = if (recorded.isEmpty()) {
        words.chartLine(dayCount, latest, smoothed = hasTrend)
    } else {
        words.chartLineBetween(
            dayCount = dayCount,
            latest = latest,
            lowest = recorded.minOf { it.log!!.value },
            highest = recorded.maxOf { it.log!!.value },
            smoothed = hasTrend,
        )
    }
    if (selectedDateKey == null) return lead

    val selected = window.days.firstOrNull { it.dateKey == selectedDateKey }
    val date = dateText(selectedDateKey)
    val value = selectedValue
    val trend = selected?.trendValue
    val selection = when {
        value == null -> words.selectedNone(date)
        trend == null -> words.selected(date, value)
        else -> words.selectedWithTrend(date, value, trend)
    }
    return listOf(lead, selection).joinedBy(". ")
}

/** The room the unit's place is decided against: a number of ordinary length, so the day's own number never moves the unit. */
private const val ReservedNumber = "0000.00"
private val UnitGap = 8.dp
private val ChartHeight = 180.dp
private val ChartHorizontalPadding = 12.dp
private val ChartVerticalPadding = 14.dp
private val GuideStroke = 1.dp
private val GuideLabelGap = 3.dp
private val LineStroke = 4.dp
private val DotRadius = 4.dp
private val DotRing = 2.dp
private val MeasurementRadius = 2.5.dp
private val MeasurementRing = 1.5.dp
private val StemStroke = 1.dp
private val EstimateDash = 7.dp
private val EstimateGap = 6.dp
private val SelectedDotRadius = 7.dp
private val SelectedDotRing = 2.5.dp
private val SelectedDotLedge = 2.5.dp
private val EmptyMarkerStroke = 2.5.dp
private const val FillTopAlpha = 0.3f
private const val EstimateAlpha = 0.45f
private const val MeasurementAlpha = 0.7f
private const val StemAlpha = 0.35f
private const val RevealMillis = 500
private const val SelectionPopStartScale = 0.65f
