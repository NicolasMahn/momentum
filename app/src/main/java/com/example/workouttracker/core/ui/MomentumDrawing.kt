package com.example.workouttracker.core.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The celebration rays shared by every Momentum reward: evenly spread, starting at twelve o'clock,
 * fading out as [progress] runs from 0 to 1. The caller owns how far they travel, because a small
 * checkbox and a wide button need different reach; the colors cycle around the circle.
 */
internal fun DrawScope.drawBurst(
    progress: Float,
    colors: List<Color>,
    rayCount: Int,
    innerRadius: Float,
    outerRadius: Float,
    strokeWidth: Float,
) {
    if (rayCount <= 0 || colors.isEmpty() || progress >= 1f) return
    val alpha = (1f - progress).coerceIn(0f, 1f)
    repeat(rayCount) { index ->
        val angle = (2f * PI.toFloat() * index / rayCount) - (PI.toFloat() / 2f)
        drawLine(
            color = colors[index % colors.size].copy(alpha = alpha),
            start = Offset(center.x + cos(angle) * innerRadius, center.y + sin(angle) * innerRadius),
            end = Offset(center.x + cos(angle) * outerRadius, center.y + sin(angle) * outerRadius),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

/** Momentum's one check mark, laid into the box at [topLeft] with [size]. Reuses the receiver. */
internal fun Path.setCheckMark(topLeft: Offset, size: Size) {
    rewind()
    moveTo(topLeft.x + size.width * 0.24f, topLeft.y + size.height * 0.52f)
    lineTo(topLeft.x + size.width * 0.43f, topLeft.y + size.height * 0.71f)
    lineTo(topLeft.x + size.width * 0.77f, topLeft.y + size.height * 0.31f)
}
