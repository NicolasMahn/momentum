package de.nicolasmahn.momentum.feature.mo

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import de.nicolasmahn.momentum.core.ui.theme.MoCoin
import de.nicolasmahn.momentum.core.ui.theme.MoColdInk
import de.nicolasmahn.momentum.core.ui.theme.MoCoinLedge
import de.nicolasmahn.momentum.core.ui.theme.MoCoinShine
import de.nicolasmahn.momentum.core.ui.theme.MoFlameBelly
import de.nicolasmahn.momentum.core.ui.theme.MoFlameOuter
import de.nicolasmahn.momentum.core.ui.theme.MoIce
import de.nicolasmahn.momentum.core.ui.theme.MoIceLedge
import de.nicolasmahn.momentum.core.ui.theme.MoIceShine
import de.nicolasmahn.momentum.core.ui.theme.MoSlate
import de.nicolasmahn.momentum.core.ui.theme.MoSlateLedge
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The streak coin: a gold coin stamped with Mo's flame. Like everything pressable or precious in
 * Momentum it has a solid ledge under it. Drawn in a 100-unit box and scaled to the scope.
 */
internal fun DrawScope.drawStreakCoin() = inHundred {
    drawCircle(MoCoinLedge, 44f, Offset(50f, 54f))
    drawCircle(MoCoin, 44f, Offset(50f, 46f))
    val flame = Path().apply {
        moveTo(52f, 16f)
        cubicTo(66f, 30f, 76f, 42f, 74f, 56f)
        cubicTo(72f, 70f, 62f, 76f, 50f, 76f)
        cubicTo(37f, 76f, 26f, 68f, 26f, 55f)
        cubicTo(26f, 46f, 31f, 39f, 37f, 34f)
        cubicTo(39f, 40f, 43f, 41f, 45f, 36f)
        cubicTo(46f, 28f, 48f, 22f, 52f, 16f)
        close()
    }
    drawPath(flame, MoFlameOuter)
    val core = Path().apply {
        moveTo(51f, 44f)
        cubicTo(59f, 52f, 63f, 60f, 60f, 67f)
        cubicTo(57f, 73f, 45f, 74f, 41f, 68f)
        cubicTo(36f, 61f, 43f, 52f, 51f, 44f)
        close()
    }
    drawPath(core, MoFlameBelly)
    drawLine(MoCoinShine, Offset(20f, 38f), Offset(27f, 25f), strokeWidth = 5f, cap = StrokeCap.Round)
}

/**
 * A freeze: a chunky ice cube with hard-edged shine, in the same kit as the coin. It is one soft
 * face on a ledge, like the coin beside it, and has no drawn top or side: the owner finds a cube
 * with faces too sharp at the size of a score chip.
 */
internal fun DrawScope.drawFreezeCube() = inHundred {
    withTransform({ rotate(-8f, Offset(50f, 50f)) }) {
        drawRoundRect(MoIceLedge, Offset(12f, 20f), Size(76f, 72f), CornerRadius(16f))
        drawRoundRect(MoIce, Offset(12f, 10f), Size(76f, 72f), CornerRadius(16f))
        drawLine(MoIceShine, Offset(26f, 40f), Offset(26f, 26f), strokeWidth = 7f, cap = StrokeCap.Round)
        drawLine(MoIceShine, Offset(26f, 26f), Offset(40f, 26f), strokeWidth = 7f, cap = StrokeCap.Round)
        drawLine(MoIceLedge, Offset(62f, 66f), Offset(74f, 66f), strokeWidth = 6f, cap = StrokeCap.Round)
        drawLine(MoIceLedge, Offset(74f, 66f), Offset(74f, 54f), strokeWidth = 6f, cap = StrokeCap.Round)
    }
}

private inline fun DrawScope.inHundred(crossinline draw: DrawScope.() -> Unit) {
    val unit = min(size.width, size.height) / 100f
    withTransform({
        translate((size.width - 100f * unit) / 2f, (size.height - 100f * unit) / 2f)
        scale(unit, unit, Offset.Zero)
    }) { draw() }
}

/**
 * A finished workout: tongues of fire fly out of him in every direction and burn away. [progress]
 * runs from 0 to 1. It draws past the scope, because the fire is bigger than he is.
 */
internal fun DrawScope.drawFireBurst(progress: Float) {
    val figureHeight = min(size.height, size.width / MoAspectRatio)
    val centre = Offset(center.x, size.height - figureHeight * 0.43f)
    for (index in 0 until BURST_TONGUES) {
        val delay = index % 3 * 0.045f
        val t = ((progress - delay) / (1f - delay)).coerceIn(0f, 1f)
        if (t == 0f || t == 1f) continue
        val angle = (index + 0.17f * (index % 3)) * 2f * PI.toFloat() / BURST_TONGUES
        val reach = figureHeight * (0.61f + (index * 7 % 5) * 0.025f)
        val travelled = (0.20f + 0.80f * t * (2f - t)) * reach
        val at = centre + Offset(cos(angle) * travelled, sin(angle) * travelled * 0.85f)
        val fade = 1f - t
        val unit = figureHeight * (0.04f + (index % 3) * 0.006f) * (0.3f + 0.7f * fade)
        withTransform({
            translate(at.x, at.y)
            rotate(angle * 180f / PI.toFloat() + 90f, Offset.Zero)
            scale(unit, unit, Offset.Zero)
        }) {
            drawPath(BurstFlame, MoFlameOuter.copy(alpha = fade))
            withTransform({
                translate(0f, 0.34f)
                scale(0.52f, 0.52f, Offset.Zero)
            }) { drawPath(BurstFlame, MoFlameBelly.copy(alpha = fade)) }
        }
    }
}

private val BurstFlame = Path().apply {
    moveTo(0.25f, -1.65f)
    cubicTo(0.65f, -1.4f, 0.52f, -0.64f, 0.57f, -0.5f)
    quadraticTo(0.75f, -0.58f, 0.76f, -0.86f)
    cubicTo(1.38f, -0.02f, 0.9f, 1f, 0f, 1f)
    cubicTo(-0.92f, 1f, -1.12f, 0.05f, -0.78f, -0.54f)
    quadraticTo(-0.63f, -0.18f, -0.45f, -0.22f)
    cubicTo(-0.46f, -0.9f, 0.08f, -1.1f, 0.25f, -1.65f)
    close()
}

private const val BURST_TONGUES = 14

/** The coin gone cold: what a streak that restarts looks like. */
internal fun DrawScope.drawSpentCoin() = inHundred {
    drawCircle(MoSlateLedge, 44f, Offset(50f, 54f))
    drawCircle(MoSlate, 44f, Offset(50f, 46f))
    drawLine(MoColdInk, Offset(36f, 32f), Offset(64f, 60f), strokeWidth = 9f, cap = StrokeCap.Round)
    drawLine(MoColdInk, Offset(64f, 32f), Offset(36f, 60f), strokeWidth = 9f, cap = StrokeCap.Round)
}
