package com.example.workouttracker.feature.measurement

import com.example.workouttracker.domain.measurement.MeasurementConfiguration
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The value range the chart height maps onto, plus the few values worth a faint guide line.
 * The range follows the measurements instead of snapping to round numbers, so small real changes
 * still use the full height; the guides carry the round numbers instead.
 */
internal data class MeasurementChartAxis(
    val minimum: Double,
    val maximum: Double,
    val guidesDescending: List<Double>,
)

/** A point in the chart's own space: 0..1 left to right, 0..1 top to bottom. */
internal data class MeasurementChartPoint(
    val xFraction: Double,
    val yFraction: Double,
)

/** One cubic of the smoothed line, in the same fractional space as [MeasurementChartPoint]. */
internal data class MeasurementChartSegment(
    val start: MeasurementChartPoint,
    val firstControl: MeasurementChartPoint,
    val secondControl: MeasurementChartPoint,
    val end: MeasurementChartPoint,
)

/** [values] must carry every value the chart draws: the trend and the measurements around it. */
/** A stretch of the curve that is stroked in one go, either over readings or across a gap. */
internal data class MeasurementChartRun(
    val estimated: Boolean,
    val segments: List<MeasurementChartSegment>,
)

/**
 * [minimumSpacing] is the least room above and below the values and the smallest distance between
 * two guides: five of the measurement's steps ([chartSpacing]), so a measurement in steps of 0.01
 * is not flattened by the spacing a weight in steps of 0.1 needs ([DefaultMeasurementChartSpacing]).
 */
internal fun measurementChartAxis(values: List<Double>, minimumSpacing: Double = DefaultMeasurementChartSpacing): MeasurementChartAxis {
    if (values.isEmpty()) {
        return MeasurementChartAxis(minimum = 0.0, maximum = 1.0, guidesDescending = emptyList())
    }

    val observedMin = values.min()
    val observedMax = values.max()
    // The padding is never zero, so a single point or a run of identical values still has height.
    val span = observedMax - observedMin
    val proportionalPadding = if (span.isFinite()) span * AxisPaddingFraction
        else (observedMax / 2 - observedMin / 2) * (2 * AxisPaddingFraction)
    val padding = maxOf(minimumSpacing, proportionalPadding)
    var maximum = (observedMax + padding).coerceAtMost(Double.MAX_VALUE)
    // Accepted measurements are finite, but their padded range can overflow. Near the
    // positive numeric limit, omit padding below zero when all readings are positive.
    val lowerLimit = if (observedMin >= 0.0 && maximum > Double.MAX_VALUE / 2) 0.0 else -Double.MAX_VALUE
    val paddedMin = (observedMin - padding).coerceAtLeast(lowerLimit)
    // A tiny step can round away beside the largest negative number.
    if (paddedMin == maximum && maximum < 0.0) maximum = Math.nextUp(maximum)
    val minimum = if (paddedMin < maximum) paddedMin else Math.nextDown(maximum)

    return MeasurementChartAxis(minimum = minimum, maximum = maximum, guidesDescending = guideValues(minimum, maximum, minimumSpacing))
}

/** The chart's smallest guide spacing and least padding for this measurement: five of its steps. */
internal val MeasurementConfiguration.chartSpacing: Double get() = ChartSpacingSteps * step.value

internal fun measurementChartLinePoints(
    trendPoints: List<MeasurementTrendPoint>,
    axis: MeasurementChartAxis,
    maximumDayIndex: Int,
): List<MeasurementChartPoint> {
    val dayRange = maximumDayIndex.coerceAtLeast(1).toDouble()
    return trendPoints.map { point ->
        MeasurementChartPoint(
            xFraction = point.dayIndex / dayRange,
            yFraction = axis.yFraction(point.value),
        )
    }
}

/** Scaling first avoids overflow when finite readings span both ends of Double's range. */
internal fun MeasurementChartAxis.yFraction(value: Double): Double {
    val range = maximum - minimum
    return if (range.isFinite()) (maximum - value) / range.coerceAtLeast(MinimumMeasurementRange)
    else (maximum / 2 - value / 2) / (maximum / 2 - minimum / 2)
}

/**
 * Monotone cubic interpolation (Fritsch–Carlson). A chart must not invent a peak its records
 * never showed, so the curve is constrained to stay between the two points it connects.
 */
internal fun measurementChartCurve(points: List<MeasurementChartPoint>): List<MeasurementChartSegment> {
    if (points.size < 2) return emptyList()

    val slopes = monotoneSlopes(points)
    return points.zipWithNext().mapIndexed { index, (start, end) ->
        val third = (end.xFraction - start.xFraction) / 3.0
        MeasurementChartSegment(
            start = start,
            firstControl = MeasurementChartPoint(
                xFraction = start.xFraction + third,
                yFraction = start.yFraction + slopes[index] * third,
            ),
            secondControl = MeasurementChartPoint(
                xFraction = end.xFraction - third,
                yFraction = end.yFraction - slopes[index + 1] * third,
            ),
            end = end,
        )
    }
}

/**
 * Turns a horizontal position into the day under it. A tap is a deliberate pick, so any day can be
 * chosen, including one the user forgot to weigh and now wants to fill in.
 */
internal fun measurementDayAtChartFraction(days: List<MeasurementHistoryDay>, xFraction: Float): String? {
    if (days.isEmpty() || !xFraction.isFinite()) return null

    return days[dayIndexAtChartFraction(days, xFraction)].dateKey
}

/**
 * The same position for a scrub: dragging across the chart reads the measurements out one by one,
 * so it settles on the nearest measured day instead of the empty days between them.
 */
internal fun measurementRecordedDayAtChartFraction(
    days: List<MeasurementHistoryDay>,
    xFraction: Float,
): String? {
    if (days.isEmpty() || !xFraction.isFinite()) return null

    val dayIndex = dayIndexAtChartFraction(days, xFraction)
    val recorded = days.filter { it.log != null }
    if (recorded.isEmpty()) return days[dayIndex].dateKey
    return recorded.minBy { abs(it.dayIndex - dayIndex) }.dateKey
}

/**
 * Splits the curve into runs the chart can stroke differently: a run over measured days, and one
 * bridging a gap wide enough that the line there is an estimate rather than a reading.
 */
internal fun measurementChartRuns(
    segments: List<MeasurementChartSegment>,
    points: List<MeasurementTrendPoint>,
    maximumMeasuredGapDays: Int = MaximumMeasuredGapDays,
): List<MeasurementChartRun> {
    if (segments.isEmpty()) return emptyList()

    val estimated = points.zipWithNext().map { (start, end) ->
        end.dayIndex - start.dayIndex > maximumMeasuredGapDays
    }
    val runs = mutableListOf<Pair<Boolean, MutableList<MeasurementChartSegment>>>()
    segments.forEachIndexed { index, segment ->
        val isEstimate = estimated.getOrElse(index) { false }
        val current = runs.lastOrNull()
        if (current != null && current.first == isEstimate) {
            current.second += segment
        } else {
            runs += isEstimate to mutableListOf(segment)
        }
    }
    return runs.map { (isEstimate, group) -> MeasurementChartRun(estimated = isEstimate, segments = group) }
}

/** Reads the drawn curve at [xFraction], so a marker for an unmeasured day sits on the line. */
internal fun measurementChartYAt(segments: List<MeasurementChartSegment>, xFraction: Double): Double? {
    if (segments.isEmpty() || !xFraction.isFinite()) return null

    val clamped = xFraction.coerceIn(segments.first().start.xFraction, segments.last().end.xFraction)
    val segment = segments.firstOrNull { clamped <= it.end.xFraction } ?: segments.last()
    var low = 0.0
    var high = 1.0
    repeat(CurveSolveSteps) {
        val middle = (low + high) / 2.0
        if (segment.cubicX(middle) < clamped) low = middle else high = middle
    }
    return segment.cubicY((low + high) / 2.0)
}

private fun dayIndexAtChartFraction(days: List<MeasurementHistoryDay>, xFraction: Float): Int =
    (xFraction.coerceIn(0f, 1f) * days.lastIndex).roundToInt()

private fun MeasurementChartSegment.cubicX(t: Double): Double =
    cubic(t, start.xFraction, firstControl.xFraction, secondControl.xFraction, end.xFraction)

private fun MeasurementChartSegment.cubicY(t: Double): Double =
    cubic(t, start.yFraction, firstControl.yFraction, secondControl.yFraction, end.yFraction)

private fun cubic(t: Double, from: Double, first: Double, second: Double, to: Double): Double {
    val inverse = 1.0 - t
    return inverse * inverse * inverse * from +
        3 * inverse * inverse * t * first +
        3 * inverse * t * t * second +
        t * t * t * to
}

private fun monotoneSlopes(points: List<MeasurementChartPoint>): DoubleArray {
    val count = points.size
    val secants = DoubleArray(count - 1) { index ->
        val run = points[index + 1].xFraction - points[index].xFraction
        if (run <= 0.0) 0.0 else (points[index + 1].yFraction - points[index].yFraction) / run
    }

    val slopes = DoubleArray(count)
    slopes[0] = secants.first()
    slopes[count - 1] = secants.last()
    for (index in 1 until count - 1) {
        val before = secants[index - 1]
        val after = secants[index]
        slopes[index] = if (before * after <= 0.0) 0.0 else (before + after) / 2.0
    }

    for (index in 0 until count - 1) {
        val secant = secants[index]
        if (secant == 0.0) {
            slopes[index] = 0.0
            slopes[index + 1] = 0.0
            continue
        }
        val startRatio = slopes[index] / secant
        val endRatio = slopes[index + 1] / secant
        val magnitude = hypot(startRatio, endRatio)
        if (magnitude > MonotoneSlopeLimit) {
            val scale = MonotoneSlopeLimit / magnitude
            slopes[index] = scale * startRatio * secant
            slopes[index + 1] = scale * endRatio * secant
        }
    }
    return slopes
}

private fun guideValues(minimum: Double, maximum: Double, minimumSpacing: Double): List<Double> {
    if (!(maximum - minimum).isFinite()) return emptyList()
    // A guide sitting on the very edge would push its label out of the chart, so the ends are free.
    val inset = (maximum - minimum) * GuideInsetFraction
    val lowest = minimum + inset
    val highest = maximum - inset

    var step = niceStepAtLeast((maximum - minimum) / (MaximumGuides + 1), minimumSpacing)
    var values = multiplesWithin(lowest, highest, step)
    var attempts = 0
    while (values.size > MaximumGuides && attempts++ < MaximumGuideAttempts) {
        step = niceStepAtLeast(step * 1.01, minimumSpacing)
        values = multiplesWithin(lowest, highest, step)
    }
    return values.sortedDescending()
}

private fun multiplesWithin(minimum: Double, maximum: Double, step: Double): List<Double> {
    val first = ceil(minimum / step)
    val last = floor(maximum / step)
    if (last < first) return emptyList()
    // Keep the guide count bounded without narrowing very large multiples to an Int.
    return (0..MaximumGuides + 1).map { normalizedZero((first + it) * step) }
        .filter { it.isFinite() && it >= minimum && it <= maximum }
        .distinct()
}

private fun niceStepAtLeast(minimumStep: Double, minimumSpacing: Double): Double {
    if (minimumStep <= minimumSpacing) return minimumSpacing

    val magnitude = 10.0.pow(floor(log10(minimumStep)))
    val normalized = minimumStep / magnitude
    val multiplier = NiceStepMultipliers.first { it >= normalized }
    return maxOf(minimumSpacing, multiplier * magnitude)
}

private fun normalizedZero(value: Double): Double = if (abs(value) < 0.0001) 0.0 else value

/** How many of a measurement's steps the chart's smallest guide spacing and its least padding are. */
private const val ChartSpacingSteps = 5

/** Default guide spacing used when a chart has no configured measurement step. */
internal const val DefaultMeasurementChartSpacing = 0.5
private const val MinimumMeasurementRange = 0.0001
private const val AxisPaddingFraction = 0.15
private const val MaximumGuides = 3
private const val GuideInsetFraction = 0.06
private const val MaximumGuideAttempts = 8
private const val MonotoneSlopeLimit = 3.0

/** A missed day or two still reads as a measured stretch; a week of silence does not. */
internal const val MaximumMeasuredGapDays = 3
private const val CurveSolveSteps = 20
private val NiceStepMultipliers = listOf(1.0, 2.0, 5.0, 10.0)
