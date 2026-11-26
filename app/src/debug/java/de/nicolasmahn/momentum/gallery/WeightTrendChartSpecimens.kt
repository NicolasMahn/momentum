package de.nicolasmahn.momentum.gallery

import de.nicolasmahn.momentum.domain.measurement.DefaultMeasurementTrendDays
import de.nicolasmahn.momentum.domain.measurement.MeasurementValuePolicy

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.nicolasmahn.momentum.core.ui.ViewTheme
import de.nicolasmahn.momentum.core.ui.theme.WorkoutTrackerTheme
import de.nicolasmahn.momentum.domain.measurement.MeasurementRecord
import de.nicolasmahn.momentum.feature.measurement.chartSpacing
import de.nicolasmahn.momentum.feature.measurement.MeasurementHistory
import de.nicolasmahn.momentum.feature.measurement.words
import de.nicolasmahn.momentum.feature.measurement.measurementHistoryWindow
import de.nicolasmahn.momentum.feature.checklist.presentation
import java.time.LocalDate

internal val weightTrendChartSpecimens = listOf(
    GallerySpecimen("21-day trend, falling") { WeightTrendChartSpecimen() },
    GallerySpecimen("21-day trend, rising") { WeightTrendChartSpecimen(rising = true) },
    GallerySpecimen("7-day trend, falling") { WeightTrendChartSpecimen(trendDays = 7) },
    GallerySpecimen("Two records, no trend yet") { WeightTrendChartSpecimen(recordCount = 2) },
)

@Composable
internal fun WeightTrendChartSpecimen(
    rising: Boolean = false,
    recordCount: Int = 28,
    trendDays: Int = DefaultMeasurementTrendDays,
) {
    val today = LocalDate.of(2026, 10, 5)
    val window = remember(rising, recordCount, trendDays) {
        val logs = (0 until recordCount).filter { it != 12 && it != 13 }.map { index ->
            val date = today.minusDays(27L - index)
            val slope = if (rising) 0.12 else -0.12
            MeasurementRecord(date.toString(), index.toLong(), 82.0 + index * slope + (index % 3 - 1) * 0.7)
        }
        measurementHistoryWindow(logs, today.toString(), trendDays = trendDays, valuePolicy = MeasurementValuePolicy.Positive)
    }
    var selected by remember { mutableStateOf(today.toString()) }
    ViewTheme(DemoWeightMeasurement.presentation().colors) {
        MeasurementHistory(
            window = window,
            selectedDateKey = selected,
            onDateSelected = { selected = it },
            words = DemoWeightMeasurement.words(),
            chartSpacing = DemoWeightMeasurement.chartSpacing,
        )
    }
}

@Preview
@Composable
private fun WeightTrendChartPreview() {
    WorkoutTrackerTheme {
        ScreenFrame(modifier = Modifier.padding(12.dp), height = 420.dp) { WeightTrendChartSpecimen() }
    }
}
