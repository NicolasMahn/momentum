package com.example.workouttracker.testing

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Picks a day on a measurement's screen as a person does: by a tap on its place in the chart, which
 * shows [dayCount] days ending on [todayKey]. A day outside those cannot be reached.
 */
fun SemanticsNodeInteractionsProvider.tapChartDay(dateKey: String, todayKey: String, dayCount: Int = 28) {
    val index = dayCount - 1 - ChronoUnit.DAYS.between(LocalDate.parse(dateKey), LocalDate.parse(todayKey)).toInt()
    require(index in 0 until dayCount) { "$dateKey is not among the $dayCount days ending on $todayKey" }
    onNodeWithTag("measurement_history_chart").performScrollTo().performTouchInput {
        val padding = ChartHorizontalPadding.toPx()
        click(Offset(padding + (width - 2 * padding) * index / (dayCount - 1), centerY))
    }
}

/** The chart's plot starts and ends this far inside its canvas, as `MeasurementHistory` draws it. */
private val ChartHorizontalPadding = 12.dp
