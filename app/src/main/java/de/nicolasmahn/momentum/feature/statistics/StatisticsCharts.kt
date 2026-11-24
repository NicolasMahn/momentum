package de.nicolasmahn.momentum.feature.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp
import de.nicolasmahn.momentum.core.ui.ViewColors

/**
 * One bar per week, oldest on the left: its height is how much of the week's goal was done. A
 * week before the feature's first record is only a tick, so "not started yet" never looks like
 * "missed", and the week that is not over stands on stripes, so its short bar reads as "so far"
 * rather than as a bad week. The row that hosts the bars speaks for them.
 */
@Composable
internal fun WeekBars(
    weeks: List<WeekConsistency>,
    colors: ViewColors,
    reveal: () -> Float,
    modifier: Modifier = Modifier,
) {
    val idleColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier = modifier.fillMaxWidth()) {
        if (weeks.isEmpty()) return@Canvas

        val slot = size.width / weeks.size
        val barWidth = (slot * BarWidthFraction).coerceAtLeast(1f)
        val corner = CornerRadius(minOf(barWidth / 2f, MaximumBarCorner.toPx()))
        val progress = reveal()

        weeks.forEachIndexed { index, week ->
            val left = slot * index + (slot - barWidth) / 2f
            if (!week.started) {
                val tick = IdleTickHeight.toPx()
                drawRoundRect(
                    color = idleColor,
                    topLeft = Offset(left, size.height - tick),
                    size = Size(barWidth, tick),
                    cornerRadius = corner,
                )
                return@forEachIndexed
            }

            drawRoundRect(
                color = colors.container,
                topLeft = Offset(left, 0f),
                size = Size(barWidth, size.height),
                cornerRadius = corner,
            )
            if (week.running) {
                drawStripes(left, barWidth, corner, colors.ledge, StripeGap.toPx(), StripeWidth.toPx())
            }
            if (week.fill > 0f) {
                // Even one day in a week stays visible as more than a hairline. The floor is a fixed
                // sliver, not the bar's width: these bars are wide and short, so a width-sized floor
                // would raise a two-day week to three quarters of its goal.
                val height = (size.height * week.fill * progress)
                    .coerceAtLeast(minOf(MinimumFillHeight.toPx(), size.height))
                drawRoundRect(
                    color = colors.accent,
                    topLeft = Offset(left, size.height - height),
                    size = Size(barWidth, height),
                    cornerRadius = corner,
                )
            }
        }
    }
}

/** Diagonal stripes over one bar's ground, clipped to its rounded shape. */
private fun DrawScope.drawStripes(left: Float, width: Float, corner: CornerRadius, color: Color, gap: Float, stroke: Float) {
    val shape = Path().apply {
        addRoundRect(RoundRect(left, 0f, left + width, size.height, corner))
    }
    clipPath(shape) {
        var x = left - size.height
        while (x < left + width) {
            drawLine(color, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = stroke)
            x += gap
        }
    }
}

private const val BarWidthFraction = 0.72f
private val MaximumBarCorner = 4.dp
private val IdleTickHeight = 3.dp
private val MinimumFillHeight = 4.dp
private val StripeGap = 5.dp
private val StripeWidth = 1.5.dp
