package com.example.workouttracker.feature.weight

import com.example.workouttracker.feature.measurement.DefaultMeasurementChartSpacing
import com.example.workouttracker.feature.measurement.MeasurementChartAxis
import com.example.workouttracker.feature.measurement.chartSpacing
import com.example.workouttracker.feature.measurement.measurementHistoryWindow
import com.example.workouttracker.feature.measurement.measurementChartAxis
import com.example.workouttracker.feature.measurement.measurementChartGeometry
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.measurement.MeasurementStep
import com.example.workouttracker.domain.measurement.testMadeMeasurement
import com.example.workouttracker.domain.measurement.testWeightMeasurement
import com.example.workouttracker.domain.measurement.withWeight
import com.example.workouttracker.domain.measurement.MeasurementRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The chart's smallest guide spacing and its least padding are five steps of the measurement: the
 * 0.5 Weight's chart always had, 5 in steps of 1, and 0.05 in steps of 0.01, so small numbers are
 * not flattened by the room a weight needs. Nothing else about the chart follows the step.
 */
class MeasurementChartSpacingTest {
    private fun inStepsOf(step: String) = testMadeMeasurement(step = step)

    @Test
    fun theSpacingIsFiveStepsOfTheMeasurement() {
        assertEquals(5.0, inStepsOf("1").chartSpacing, 0.0)
        assertEquals(0.5, inStepsOf("0.1").chartSpacing, 0.0)
        assertEquals(0.05, inStepsOf("0.01").chartSpacing, 0.0)
        assertEquals(0.5, DefaultMeasurementChartSpacing, 0.0)
        assertEquals(DefaultMeasurementChartSpacing, testWeightMeasurement().chartSpacing, 0.0)
    }

    /** Weight's axis is the one it had: asked with Weight's own spacing, it is what the chart drew before the spacing could be chosen. */
    @Test
    fun weightsAxisIsWhatItAlwaysWas() {
        val spacing = testWeightMeasurement().chartSpacing
        listOf(listOf(82.0), listOf(82.0, 82.0), listOf(78.0, 80.0, 82.0), listOf(81.9, 82.1), listOf(60.0, 95.5), listOf(0.0, -1.5, 80.0), emptyList()).forEach { values ->
            assertEquals(values.toString(), measurementChartAxis(values), measurementChartAxis(values, spacing))
        }
        assertEquals(MeasurementChartAxis(minimum = 81.5, maximum = 82.5, guidesDescending = listOf(82.0)), measurementChartAxis(listOf(82.0), spacing))
        assertEquals(MeasurementChartAxis(minimum = 77.4, maximum = 82.6, guidesDescending = listOf(82.0, 80.0, 78.0)), measurementChartAxis(listOf(78.0, 80.0, 82.0), spacing))
    }

    /** In steps of 0.01 values a few hundredths apart use the chart's height, with guides 0.05 apart. */
    @Test
    fun inStepsOfAHundredthSmallNumbersAreNotFlattened() {
        val values = listOf(0.50, 0.52, 0.56)
        val axis = measurementChartAxis(values, inStepsOf("0.01").chartSpacing)
        assertEquals(0.45, axis.minimum, 1e-9)
        assertEquals(0.61, axis.maximum, 1e-9)
        assertEquals(listOf(0.6, 0.55, 0.5), axis.guidesDescending.map { Math.round(it * 100) / 100.0 })
        // The values span more than a third of the height. With a weight's spacing they would lie in a twentieth of it.
        val used = (values.max() - values.min()) / (axis.maximum - axis.minimum)
        assertTrue("$used", used > 0.35)
        val asAWeight = measurementChartAxis(values)
        assertTrue((values.max() - values.min()) / (asAWeight.maximum - asAWeight.minimum) < 0.06)

        // One value alone still has height, five steps above and below it.
        val alone = measurementChartAxis(listOf(0.21), inStepsOf("0.01").chartSpacing)
        assertEquals(0.16, alone.minimum, 1e-9)
        assertEquals(0.26, alone.maximum, 1e-9)
    }

    /** In steps of 1 the guides are at least 5 apart, and a value alone has 5 above and below it. */
    @Test
    fun inStepsOfOneTheGuidesAreAtLeastFiveApart() {
        val axis = measurementChartAxis(listOf(60.0, 62.0, 64.0), inStepsOf("1").chartSpacing)
        assertEquals(55.0, axis.minimum, 0.0)
        assertEquals(69.0, axis.maximum, 0.0)
        assertEquals(listOf(65.0, 60.0), axis.guidesDescending)
        val alone = measurementChartAxis(listOf(7.0), inStepsOf("1").chartSpacing)
        assertEquals(MeasurementChartAxis(minimum = 2.0, maximum = 12.0, guidesDescending = listOf(10.0, 5.0)), alone)
        // A wide range spaces its guides by the range, as every chart does, never closer than five steps.
        val wide = measurementChartAxis(listOf(40.0, 180.0), inStepsOf("1").chartSpacing)
        assertTrue(wide.guidesDescending.zipWithNext().all { (higher, lower) -> higher - lower >= 5.0 })
        assertEquals(listOf(150.0, 100.0, 50.0), wide.guidesDescending)
    }

    /** A step of any size the rule allows spaces the chart by five of itself: 2.5 in steps of 0.5, 12.5 in steps of 2.5. */
    @Test
    fun aStepOfAnySizeSpacesTheChartByFiveOfItself() {
        assertEquals(2.5, inStepsOf("0.5").chartSpacing, 0.0)
        assertEquals(12.5, inStepsOf("2.5").chartSpacing, 0.0)
        assertEquals(0.25, inStepsOf("0.05").chartSpacing, 1e-12)

        val halves = measurementChartAxis(listOf(181.5), inStepsOf("0.5").chartSpacing)
        assertEquals(MeasurementChartAxis(minimum = 179.0, maximum = 184.0, guidesDescending = listOf(182.5, 180.0)), halves)
        val coarse = measurementChartAxis(listOf(60.0, 62.0, 64.0), inStepsOf("2.5").chartSpacing)
        assertEquals(47.5, coarse.minimum, 0.0)
        assertEquals(76.5, coarse.maximum, 0.0)
        assertTrue(coarse.guidesDescending.zipWithNext().all { (higher, lower) -> higher - lower >= 12.5 })
    }

    @Test
    fun statisticsKeepsEachMeasurementsOwnChartStep() {
        val fine = inStepsOf("0.01")
        val coarse = inStepsOf("2.5").copy(id = "msr_c0rs")
        val state = AppState(measurements = listOf(fine, coarse))
        val summary = com.example.workouttracker.feature.statistics.statisticsSummary(
            state, "2026-10-16", com.example.workouttracker.feature.statistics.StatisticsRange.FourWeeks,
        )
        assertEquals(listOf(0.05, 12.5), summary.measurements.map { it.configuration.chartSpacing })
    }

    /** The geometry the chart draws takes the measurement's spacing; its window, its points and its trend do not. */
    @Test
    fun theWindowAndTheTrendAreTheSameWhateverTheStep() {
        val records = listOf(MeasurementRecord("2026-10-08", 3, 0.56), MeasurementRecord("2026-10-07", 2, 0.52), MeasurementRecord("2026-10-06", 1, 0.50))
        val window = measurementHistoryWindow(records, "2026-10-09")
        assertEquals(listOf(0.50, 0.52, 0.56), window.recordedDays.map { it.log!!.value })
        assertEquals(listOf("2026-10-06", "2026-10-07", "2026-10-08", "2026-10-09"), window.trendPoints.map { it.dateKey })

        val fine = measurementChartGeometry(window, inStepsOf("0.01").chartSpacing)
        val coarse = measurementChartGeometry(window, inStepsOf("1").chartSpacing)
        assertEquals(0.45, fine.axis.minimum, 1e-9)
        assertEquals(-4.5, coarse.axis.minimum, 1e-9)
        assertEquals(fine.maximumDayIndex, coarse.maximumDayIndex)
        assertEquals(fine.linePoints.map { it.xFraction }, coarse.linePoints.map { it.xFraction })
        // Left to itself the geometry is Weight's.
        assertEquals(measurementChartGeometry(window, DefaultMeasurementChartSpacing).axis, measurementChartGeometry(window).axis)
    }
}
