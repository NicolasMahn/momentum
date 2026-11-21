package com.example.workouttracker.feature.weight

import com.example.workouttracker.feature.measurement.MeasurementChartAxis
import com.example.workouttracker.feature.measurement.MeasurementChartPoint
import com.example.workouttracker.feature.measurement.MeasurementChartSegment
import com.example.workouttracker.feature.measurement.MeasurementTrendPoint
import com.example.workouttracker.feature.measurement.measurementChartAxis
import com.example.workouttracker.feature.measurement.measurementChartCurve
import com.example.workouttracker.feature.measurement.measurementChartLinePoints
import com.example.workouttracker.feature.measurement.measurementChartRuns
import com.example.workouttracker.feature.measurement.measurementChartYAt
import com.example.workouttracker.feature.measurement.measurementDayAtChartFraction
import com.example.workouttracker.feature.measurement.measurementHistoryWindow
import com.example.workouttracker.feature.measurement.measurementRecordedDayAtChartFraction
import com.example.workouttracker.domain.measurement.MeasurementRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeightChartMathTest {
    @Test
    fun everyAcceptedFiniteWeightKeepsTheAxisAndCoordinatesFinite() {
        for (values in listOf(listOf(80.0, Double.MAX_VALUE), listOf(Double.MAX_VALUE))) {
            val axis = measurementChartAxis(values)
            assertTrue(axis.minimum.isFinite())
            assertTrue(axis.maximum.isFinite())
            assertTrue((axis.maximum - axis.minimum).isFinite())
            assertTrue(axis.maximum > axis.minimum)
            assertTrue(axis.guidesDescending.all { it.isFinite() && it in axis.minimum..axis.maximum })
            val points = values.mapIndexed { index, kg -> MeasurementTrendPoint("2026-10-05", index, kg) }
            assertTrue(measurementChartLinePoints(points, axis, 1).all { it.yFraction.isFinite() && it.yFraction in 0.0..1.0 })
        }
    }

    @Test
    fun axisKeepsEveryMeasurementInsideAFewRoundGuides() {
        val weights = listOf(88.32, 88.45)

        val axis = measurementChartAxis(weights)

        assertTrue(axis.minimum < weights.min())
        assertTrue(axis.maximum > weights.max())
        assertTrue(axis.guidesDescending.size <= 3)
        assertEquals(axis.guidesDescending.sortedDescending(), axis.guidesDescending)
        assertTrue(axis.guidesDescending.all { it > axis.minimum && it < axis.maximum })
        assertTrue(axis.guidesDescending.all { guide -> guide * 2.0 == (guide * 2.0).toInt().toDouble() })
    }

    @Test
    fun axisGivesHeightToASinglePointAndToIdenticalWeights() {
        val single = measurementChartAxis(listOf(83.0))
        val identical = measurementChartAxis(listOf(83.0, 83.0))

        listOf(single, identical).forEach { axis ->
            assertTrue(axis.maximum - axis.minimum >= 1.0)
            val points = measurementChartLinePoints(
                listOf(MeasurementTrendPoint("2026-09-16", 27, 83.0)),
                axis,
                maximumDayIndex = 27,
            )
            assertEquals(0.5, points.single().yFraction, 0.0001)
        }
    }

    @Test
    fun linePointsMapDaysAndWeightsOntoTheChartSpace() {
        val points = listOf(
            MeasurementTrendPoint("2026-08-20", 0, 80.0),
            MeasurementTrendPoint("2026-09-01", 12, 88.32),
            MeasurementTrendPoint("2026-09-16", 27, 85.0),
        )
        val axis = MeasurementChartAxis(minimum = 80.0, maximum = 90.0, guidesDescending = listOf(85.0))

        val linePoints = measurementChartLinePoints(points, axis, maximumDayIndex = 27)

        assertEquals(points.size, linePoints.size)
        assertEquals(0.0, linePoints[0].xFraction, 0.0001)
        assertEquals(12.0 / 27.0, linePoints[1].xFraction, 0.0001)
        assertEquals(1.0, linePoints[2].xFraction, 0.0001)
        assertEquals((90.0 - 88.32) / 10.0, linePoints[1].yFraction, 0.0001)
    }

    @Test
    fun smoothedCurveNeverLeavesTheRangeOfTheMeasurementsItConnects() {
        val points = listOf(
            MeasurementChartPoint(0.0, 0.9),
            MeasurementChartPoint(0.2, 0.1),
            MeasurementChartPoint(0.5, 0.15),
            MeasurementChartPoint(0.7, 0.85),
            MeasurementChartPoint(1.0, 0.2),
        )

        val segments = measurementChartCurve(points)

        assertEquals(points.size - 1, segments.size)
        segments.forEach { segment ->
            val low = minOf(segment.start.yFraction, segment.end.yFraction)
            val high = maxOf(segment.start.yFraction, segment.end.yFraction)
            (0..20).forEach { step ->
                val y = segment.yAt(step / 20.0)
                assertTrue("$y left [$low, $high]", y >= low - 0.0001 && y <= high + 0.0001)
            }
        }
    }

    @Test
    fun smoothedCurveIsFlatBetweenEqualMeasurements() {
        val segments = measurementChartCurve(
            listOf(
                MeasurementChartPoint(0.0, 0.4),
                MeasurementChartPoint(0.5, 0.4),
                MeasurementChartPoint(1.0, 0.1),
            ),
        )

        assertEquals(0.4, segments.first().yAt(0.5), 0.0001)
    }

    @Test
    fun fewerThanTwoPointsHaveNoCurveToDraw() {
        assertTrue(measurementChartCurve(emptyList()).isEmpty())
        assertTrue(measurementChartCurve(listOf(MeasurementChartPoint(0.5, 0.5))).isEmpty())
    }

    @Test
    fun scrubbingSnapsToTheNearestDayThatWasMeasured() {
        val window = measurementHistoryWindow(
            logs = listOf(MeasurementRecord("2026-09-10", 1L, 84.0), MeasurementRecord("2026-09-16", 2L, 83.0)),
            todayKey = "2026-09-16",
        )

        // 2026-09-10 is day 21 of 28, so the middle of the chart still belongs to it.
        assertEquals("2026-09-10", measurementRecordedDayAtChartFraction(window.days, 0.5f))
        assertEquals("2026-09-10", measurementRecordedDayAtChartFraction(window.days, -0.2f))
        assertEquals("2026-09-16", measurementRecordedDayAtChartFraction(window.days, 1.2f))
    }

    @Test
    fun scrubbingFallsBackToTheTouchedDayWhenNothingWasMeasured() {
        val window = measurementHistoryWindow(logs = emptyList(), todayKey = "2026-09-16")

        assertEquals("2026-08-20", measurementRecordedDayAtChartFraction(window.days, 0f))
        assertEquals("2026-09-16", measurementRecordedDayAtChartFraction(window.days, 1f))
    }

    @Test
    fun aTapPicksTheDayUnderItEvenWhenItWasNeverWeighed() {
        val window = measurementHistoryWindow(
            logs = listOf(MeasurementRecord("2026-09-16", 1L, 83.0)),
            todayKey = "2026-09-16",
        )

        // Day 14 of 28 was never weighed, and is exactly what the middle of the chart offers.
        assertEquals("2026-09-03", measurementDayAtChartFraction(window.days, 0.5f))
        assertEquals("2026-08-20", measurementDayAtChartFraction(window.days, -0.2f))
        assertEquals("2026-09-16", measurementDayAtChartFraction(window.days, 1.2f))
    }

    @Test
    fun onlyStretchesWithoutReadingsAreDrawnAsEstimates() {
        val points = listOf(
            MeasurementTrendPoint("2026-08-20", 0, 84.0),
            MeasurementTrendPoint("2026-08-22", 2, 83.8),
            MeasurementTrendPoint("2026-09-05", 16, 83.0),
            MeasurementTrendPoint("2026-09-06", 17, 82.9),
        )
        val segments = measurementChartCurve(measurementChartLinePoints(points, measurementChartAxis(points.map { it.value }), 27))

        val runs = measurementChartRuns(segments, points)

        assertEquals(listOf(false, true, false), runs.map { it.estimated })
        assertEquals(listOf(1, 1, 1), runs.map { it.segments.size })
    }

    @Test
    fun theCurveCanBeReadAtADayThatCarriesNoPointOfItsOwn() {
        val segments = measurementChartCurve(
            listOf(
                MeasurementChartPoint(0.0, 0.2),
                MeasurementChartPoint(0.5, 0.6),
                MeasurementChartPoint(1.0, 0.8),
            ),
        )

        assertEquals(0.6, measurementChartYAt(segments, 0.5)!!, 0.0001)
        assertEquals(0.2, measurementChartYAt(segments, -1.0)!!, 0.0001)
        assertEquals(0.8, measurementChartYAt(segments, 2.0)!!, 0.0001)
        val middle = measurementChartYAt(segments, 0.25)!!
        assertTrue("$middle left the points it lies between", middle in 0.2..0.6)
        assertNull(measurementChartYAt(emptyList(), 0.5))
    }
}

/** Evaluates the segment's cubic so a test can look between the points, where smoothing lives. */
private fun MeasurementChartSegment.yAt(t: Double): Double {
    val inverse = 1 - t
    return inverse * inverse * inverse * start.yFraction +
        3 * inverse * inverse * t * firstControl.yFraction +
        3 * inverse * t * t * secondControl.yFraction +
        t * t * t * end.yFraction
}
