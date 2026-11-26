package de.nicolasmahn.momentum.feature.mo

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import de.nicolasmahn.momentum.core.ui.ViewIdentity
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import de.nicolasmahn.momentum.core.ui.LocalTrackerIdentity
import de.nicolasmahn.momentum.core.ui.ViewColors
import de.nicolasmahn.momentum.core.ui.theme.MoCoinShine
import de.nicolasmahn.momentum.core.ui.theme.MoRestToken
import de.nicolasmahn.momentum.core.ui.theme.MoRestTokenLedge
import de.nicolasmahn.momentum.core.ui.theme.MoRestTokenMoon
import de.nicolasmahn.momentum.core.ui.theme.MoSlate
import de.nicolasmahn.momentum.domain.TrackerRef
import de.nicolasmahn.momentum.domain.trackerRef
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/*
 * The tokens Mo's streak runs on, drawn as objects in the same kit as the streak coin and the freeze
 * cube: flat faces on a darker ledge. A habit's pass is a hexagon in that habit's own colours with
 * its Home icon stamped on it, so a token is recognisable before it is read. A rest token is a
 * night-blue coin with the moon, because it is a planned day off and not something earned.
 *
 * Everything is drawn in a 100-unit box scaled to the scope, like the coin.
 */

/** A habit's pass. [stamp] is the habit's icon. */
internal fun DrawScope.drawPassToken(colors: ViewColors, stamp: VectorPainter?) {
    inTokenBox {
        val face = hexagon(Offset(50f, 45f), 40f)
        val base = hexagon(Offset(50f, 45f), 40f, depth = 8f)
        drawPath(base, colors.ledge)
        drawPath(base, colors.ledge, style = Stroke(width = 8f, join = StrokeJoin.Round))
        drawPath(face, colors.accent)
        drawPath(face, colors.accent, style = Stroke(width = 8f, join = StrokeJoin.Round))
        drawPath(hexagon(Offset(50f, 45f), 31f), colors.onAccent.copy(alpha = 0.16f), style = Stroke(width = 3f, join = StrokeJoin.Round))
        drawLine(MoCoinShine.copy(alpha = 0.8f), Offset(22f, 34f), Offset(30f, 20f), strokeWidth = 5f, cap = StrokeCap.Round)
    }
    if (stamp == null) return
    // The icon is drawn at its real size rather than inside the scaled box, where it would blur.
    val unit = min(size.width, size.height) / 100f
    val side = STAMP * unit
    translate(left = size.width / 2f - side / 2f, top = size.height / 2f - 5f * unit - side / 2f) {
        with(stamp) { draw(Size(side, side), colorFilter = ColorFilter.tint(colors.onAccent)) }
    }
}

/** A rest token: a planned day off from activity. */
internal fun DrawScope.drawRestToken() = inTokenBox {
    drawCircle(MoRestTokenLedge, 42f, Offset(50f, 54f))
    drawCircle(MoRestToken, 42f, Offset(50f, 46f))
    // A crescent: the moon with a bite taken out of it by the token's own face.
    drawCircle(MoRestTokenMoon, 20f, Offset(52f, 46f))
    drawCircle(MoRestToken, 17f, Offset(61f, 39f))
    drawStar(Offset(72f, 62f), 6f, MoRestTokenMoon)
    drawStar(Offset(30f, 30f), 4f, MoRestTokenMoon)
}

/** The same pass drawn in Mo's world, where there are no icons: a hexagon in the given colours. */
internal fun DrawScope.drawWorldToken(centre: Offset, radius: Float, face: Color, ledge: Color, mark: Color) {
    drawPath(hexagon(centre, radius, depth = radius * 0.2f), ledge)
    drawPath(hexagon(centre, radius), face)
    drawPath(hexagon(centre, radius * 0.62f), mark, style = Stroke(width = radius * 0.14f, join = StrokeJoin.Round))
}

/** A rest token standing in Mo's world, as big as he needs it to lean on. */
internal fun DrawScope.drawWorldRestToken(centre: Offset, radius: Float) {
    drawCircle(MoRestTokenLedge, radius, centre + Offset(0f, radius * 0.2f))
    drawCircle(MoRestToken, radius, centre)
    drawCircle(MoRestTokenMoon, radius * 0.48f, centre + Offset(radius * 0.04f, 0f))
    drawCircle(MoRestToken, radius * 0.41f, centre + Offset(radius * 0.26f, -radius * 0.17f))
    drawStar(centre + Offset(radius * 0.5f, radius * 0.38f), radius * 0.15f, MoRestTokenMoon)
}

/**
 * A habit's pass as a picture, in that habit's own colours and with its Home icon. An [empty] slot
 * shows the same token faded, so what a bank collects is visible even while it holds none.
 */
@Composable
internal fun PassToken(ref: TrackerRef, modifier: Modifier = Modifier, empty: Boolean = false) =
    PassToken(LocalTrackerIdentity.current(ref), modifier, empty)

/** The same token for any view that keeps a bank, a configured checklist included. */
@Composable
internal fun PassToken(identity: ViewIdentity, modifier: Modifier = Modifier, empty: Boolean = false) {
    val stamp = rememberVectorPainter(identity.icon)
    Canvas(modifier.faded(empty)) { drawPassToken(identity.colors, stamp) }
}

@Composable
internal fun RestToken(modifier: Modifier = Modifier, empty: Boolean = false) {
    Canvas(modifier.faded(empty)) { drawRestToken() }
}

/** A freeze as a picture; an [empty] slot shows the cube faded, like an empty token slot. */
@Composable
internal fun FreezeToken(modifier: Modifier = Modifier, empty: Boolean = false) {
    Canvas(modifier.faded(empty)) { drawFreezeCube() }
}

/**
 * Mo's "Zz": a week's amount he does not ask for today. It is ink only, never a hexagon or a
 * coin, because it holds no token.
 */
@Composable
internal fun QuietMark(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val unit = size.minDimension / 24f
        val stroke = Stroke(width = 2.2f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun zed(left: Float, top: Float, width: Float, height: Float) = Path().apply {
            moveTo(left * unit, top * unit)
            lineTo((left + width) * unit, top * unit)
            lineTo(left * unit, (top + height) * unit)
            lineTo((left + width) * unit, (top + height) * unit)
        }
        drawPath(zed(3f, 10f, 8f, 10f), color, style = stroke)
        drawPath(zed(14f, 4f, 6f, 7f), color, style = Stroke(width = 2f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** The whole object faded as one layer, so its ledge does not show through its face. */
private fun Modifier.faded(empty: Boolean): Modifier = if (empty) graphicsLayer { alpha = EMPTY_SLOT_ALPHA } else this

/** A hexagon standing on a flat side, the shape every pass shares. */
private fun hexagon(centre: Offset, radius: Float, depth: Float = 0f): Path = Path().apply {
    val x = centre.x
    val y = centre.y
    val half = radius / 2f
    val height = radius * sin(PI / 3).toFloat()
    moveTo(x + radius, y)
    lineTo(x + radius, y + depth)
    lineTo(x + half, y + depth + height)
    lineTo(x - half, y + depth + height)
    lineTo(x - radius, y + depth)
    lineTo(x - radius, y)
    lineTo(x - half, y - height)
    lineTo(x + half, y - height)
    close()
}

private fun DrawScope.drawStar(centre: Offset, radius: Float, colour: Color) {
    val star = Path().apply {
        for (point in 0 until 8) {
            val angle = PI / 4.0 * point - PI / 2.0
            val r = if (point % 2 == 0) radius else radius * 0.38f
            val at = Offset(centre.x + r * cos(angle).toFloat(), centre.y + r * sin(angle).toFloat())
            if (point == 0) moveTo(at.x, at.y) else lineTo(at.x, at.y)
        }
        close()
    }
    drawPath(star, colour)
}

private inline fun DrawScope.inTokenBox(crossinline draw: DrawScope.() -> Unit) {
    val unit = min(size.width, size.height) / 100f
    withTransform({
        translate((size.width - 100f * unit) / 2f, (size.height - 100f * unit) / 2f)
        scale(unit, unit, Offset.Zero)
    }) { draw() }
}

private const val STAMP = 54f
private const val EMPTY_SLOT_ALPHA = 0.26f
