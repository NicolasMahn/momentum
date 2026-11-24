package de.nicolasmahn.momentum.domain.measurement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasurementTrendTest {
    @Test
    fun aTrendNeedsSomethingToAverage() {
        assertTrue(measurementTrendSeries(emptyList()).isEmpty())
        assertEquals(
            listOf(MeasurementTrendValue(dayIndex = 4, trendValue = 82.0)),
            measurementTrendSeries(listOf(IndexedMeasurement(4, 82.0))),
        )
    }

    @Test
    fun anUnchangingWeightHasAnUnchangingTrend() {
        val trend = measurementTrendSeries((0..10).map { IndexedMeasurement(it, 84.0) })

        trend.forEach { assertEquals(84.0, it.trendValue, 0.000001) }
    }

    @Test
    fun oneHeavyMorningMovesTheTrendByOneDaysSmoothing() {
        val measurements = (0..3).map { IndexedMeasurement(it, 80.0) } +
            IndexedMeasurement(4, 90.0)

        val trend = measurementTrendSeries(measurements).last().trendValue

        assertEquals(80.0 + measurementTrendSmoothing(DefaultMeasurementTrendDays) * 10.0, trend, 0.000001)
    }

    @Test
    fun aSkippedDayCountsForTheDailyStepsItStandsIn() {
        val gapped = measurementTrendSeries(
            listOf(IndexedMeasurement(0, 80.0), IndexedMeasurement(5, 90.0)),
        )
        val daily = measurementTrendSeries(
            listOf(IndexedMeasurement(0, 80.0)) + (1..5).map { IndexedMeasurement(it, 90.0) },
        )

        assertEquals(daily.last().trendValue, gapped.last().trendValue, 0.000001)
    }

    @Test
    fun theTrendNeverLeavesTheWeightsItAverages() {
        val weights = listOf(83.4, 81.9, 85.2, 84.0, 82.2, 86.1, 84.4)
        val measurements = weights.mapIndexed { index, weight ->
            // Irregular gaps, because a real month of weighings has holes in it.
            IndexedMeasurement(dayIndex = index * index, value = weight)
        }

        val trend = measurementTrendSeries(measurements)

        assertEquals(measurements.size, trend.size)
        trend.forEach {
            assertTrue("${it.trendValue} left the measured range", it.trendValue in weights.min()..weights.max())
        }
    }

    @Test
    fun theTrendFollowsAStepChangeWithoutEverReachingItInOneReading() {
        val measurements = listOf(IndexedMeasurement(0, 80.0)) + (1..30).map { IndexedMeasurement(it, 85.0) }

        val trend = measurementTrendSeries(measurements).map { it.trendValue }

        assertEquals(trend.sorted(), trend)
        assertTrue(trend.last() > 84.0)
        assertTrue(trend.all { it < 85.0 })
    }

    @Test
    fun oppositeFiniteExtremesKeepTheTrendFinite() {
        val trend = measurementTrendSeries(listOf(IndexedMeasurement(0, -Double.MAX_VALUE), IndexedMeasurement(1, Double.MAX_VALUE), IndexedMeasurement(2, 0.0)))
        assertTrue(trend.all { it.trendValue.isFinite() })
        assertTrue(trend[1].trendValue > -Double.MAX_VALUE)
    }
}
