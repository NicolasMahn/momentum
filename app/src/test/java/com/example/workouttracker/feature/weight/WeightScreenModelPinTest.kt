package com.example.workouttracker.feature.weight

import com.example.workouttracker.domain.measurement.MeasurementValuePolicy

import com.example.workouttracker.domain.measurement.recordsOf

import com.example.workouttracker.feature.measurement.MeasurementHistoryRange
import com.example.workouttracker.feature.measurement.measurementHistoryWindow
import com.example.workouttracker.feature.measurement.measurementChartGeometry
import com.example.workouttracker.feature.measurement.measurementDraftForDate
import com.example.workouttracker.feature.measurement.words
import com.example.workouttracker.core.format.formatMetricNumber
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What Weight's screen works out before it draws or says anything, for the record states of
 * `WeightRecordReadersTest`: the window of each range with its recorded days and its trend line, the chart's geometry down to the guides and every point of the curve, and
 * the draft each day opens on in both languages. Numbers are written as they are, so a change in
 * the last digit of a position fails here.
 *
 * The expected file was written by the code as it was before the screen took a measurement and is
 * never rewritten to make a change pass. `WEIGHT_WORDS_WRITE=1` writes it for a change made on
 * purpose; what a run produced is in `build/weight-words` either way.
 */
class WeightScreenModelPinTest {
    @Test
    fun everyStateWorksOutWhatWasRecorded() {
        val actual = WeightRecordStates.joinToString("\n") { it.rendered() }
        val expectedFile = File("src/test/resources/weight/screen-model.txt")
        File("build/weight-words").apply { mkdirs() }.resolve("screen-model.txt").writeText(actual)
        if (System.getenv("WEIGHT_WORDS_WRITE") == "1") {
            expectedFile.parentFile?.mkdirs()
            expectedFile.writeText(actual)
        }
        assertEquals(expectedFile.readText(), actual)
    }

    private fun WeightRecordState.rendered(): String = buildString {
        val logs = state.recordsOf("weight")
        appendLine("== $name")
        appendLine("records $logs")
        WeightRecordStatesDays.forEach { day ->
            appendLine("draft $day en ${measurementDraftForDate(logs, day, Locale.ENGLISH)}")
            appendLine("draft $day de ${measurementDraftForDate(logs, day, Locale.GERMANY)}")
        }
        MeasurementHistoryRange.entries.forEach { range ->
            val window = measurementHistoryWindow(logs, WeightRecordStatesToday, range, valuePolicy = MeasurementValuePolicy.Positive)
            appendLine("window $range days=${window.days.size} from=${window.days.first().dateKey} to=${window.days.last().dateKey} smoothed=${window.smoothed}")
            window.days.filter { it.log != null || it.trendValue != null }.forEach { appendLine("  day $it") }
            window.trendPoints.forEach { appendLine("  line $it") }
            val geometry = measurementChartGeometry(window)
            appendLine("  axis ${geometry.axis} over ${geometry.maximumDayIndex} days")
            appendLine("  guides en ${geometry.axis.guidesDescending.map { formatMetricNumber(it, Locale.ENGLISH) }} de ${geometry.axis.guidesDescending.map { formatMetricNumber(it, Locale.GERMANY) }}")
            geometry.linePoints.forEach { appendLine("  point $it") }
            geometry.curve.forEach { appendLine("  curve $it") }
            geometry.runs.forEach { appendLine("  run estimated=${it.estimated} segments=${it.segments.size}") }
        }
    }
}
