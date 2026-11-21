package com.example.workouttracker.feature.weight

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Everything on show, a dialog's window included, one line for each node that shows or says
 * something: its text, its field value, what TalkBack says for it and its state, after its test
 * tag, in the order the screen holds them.
 */
internal fun ComposeContentTestRule.transcript(): List<String> {
    waitForIdle()
    return buildList { onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes().forEach { root -> root.writeDown(this) } }
}

private fun SemanticsNode.writeDown(lines: MutableList<String>) {
    val said = listOfNotNull(
        config.getOrNull(SemanticsProperties.Text)?.let { texts -> "text=\"${texts.joinToString(" | ") { it.text }}\"" },
        config.getOrNull(SemanticsProperties.EditableText)?.let { "field=\"${it.text}\"" },
        config.getOrNull(SemanticsProperties.ContentDescription)?.let { "spoken=\"${it.joinToString(" | ")}\"" },
        config.getOrNull(SemanticsProperties.StateDescription)?.let { "state=\"$it\"" },
        config.getOrNull(SemanticsProperties.Error)?.let { "error=\"$it\"" },
        config.getOrNull(SemanticsProperties.Selected)?.let { if (it) "selected" else "not selected" },
        config.getOrNull(SemanticsProperties.Disabled)?.let { "disabled" },
        config.getOrNull(SemanticsActions.OnClick)?.label?.let { "click=\"$it\"" },
    )
    if (said.isNotEmpty()) {
        lines += (listOfNotNull(config.getOrNull(SemanticsProperties.TestTag)?.let { "[$it]" }) + said).joinToString(" ")
    }
    children.forEach { it.writeDown(lines) }
}

/**
 * The day a recent-day calendar shows as selected, among the cells tagged `<prefix>_history_<date>`.
 * It is how a screen without a date field tells which day its form is on.
 */
internal fun List<String>.selectedHistoryDay(prefix: String): String =
    single { it.startsWith("[${prefix}_history_") && "\" selected" in it }
        .substringAfter("[${prefix}_history_")
        .substringBefore("]")

/**
 * Picks a day on a measurement's screen as a person does: by a tap on its place in the chart, which
 * shows [dayCount] days ending on [todayKey]. A day outside those cannot be reached.
 */
internal fun ComposeContentTestRule.tapChartDay(dateKey: String, todayKey: String, dayCount: Int = 28) {
    val index = dayCount - 1 - ChronoUnit.DAYS.between(LocalDate.parse(dateKey), LocalDate.parse(todayKey)).toInt()
    require(index in 0 until dayCount) { "$dateKey is not among the $dayCount days ending on $todayKey" }
    onNodeWithTag("measurement_history_chart").performScrollTo().performTouchInput {
        val padding = ChartHorizontalPadding.toPx()
        click(Offset(padding + (width - 2 * padding) * index / (dayCount - 1), centerY))
    }
    waitForIdle()
}

/** The day a measurement's screen is on, as its chart says it ("Fri, Oct 9"): the screen has no date field. */
internal fun List<String>.selectedChartDay(): String =
    single { it.startsWith("[measurement_history_chart]") }.substringAfterLast("Selected ").substringBefore(":")

/** The chart's plot starts and ends this far inside its canvas, as `MeasurementHistory` draws it. */
private val ChartHorizontalPadding = 12.dp
