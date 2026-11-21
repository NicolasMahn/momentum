package com.example.workouttracker.gallery

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.example.workouttracker.core.ui.theme.MomentumDarkBackground
import com.example.workouttracker.core.ui.theme.MomentumDarkOnSurfaceVariant
import com.example.workouttracker.domain.mo.MoOccasion
import com.example.workouttracker.domain.mo.MoState
import com.example.workouttracker.feature.mo.BOX_HEIGHT
import com.example.workouttracker.feature.mo.BOX_WIDTH
import com.example.workouttracker.feature.mo.CENTRE
import com.example.workouttracker.feature.mo.FEET
import com.example.workouttracker.feature.mo.MoBodies
import com.example.workouttracker.feature.mo.MoLook
import com.example.workouttracker.feature.mo.acts.MoAct
import com.example.workouttracker.feature.mo.acts.MoScene
import com.example.workouttracker.feature.mo.acts.MoVariant
import com.example.workouttracker.feature.mo.acts.bodyKind
import com.example.workouttracker.feature.mo.boxPoint
import com.example.workouttracker.feature.mo.drawMo

/** One picture of an act: the state Mo is in, the variant it plays in, and the moment. */
internal class MoActMoment(val state: MoState, val seed: Int, val seconds: Float)

/** One picture of Mo: how he looks, the moment, and what to write over it. */
internal class MoShot(val look: MoLook, val seconds: Float, val label: String? = null)

/** How much of him a sheet shows in each cell. */
internal enum class MoCloseUp(val cell: Size) {
    /** His whole 320 x 432 box, cut off at its edge as the widget's picture is. */
    Whole(Size(BOX_WIDTH, BOX_HEIGHT)),

    /** His eyes and mouth filling the cell: where a mouth, a tongue or a lid is judged. */
    Face(Size(390f, 300f)),

    /** From above his shoulders to the floor: where his arms meet his body, and what his hands hold. */
    Body(Size(480f, 320f)),
    ;

    /**
     * The part of his box the cell shows. It is the same part of his body in every state, so a
     * smaller Mo is simply drawn larger.
     */
    fun window(state: MoState): Rect {
        val eyeY = MoBodies.of(state.bodyKind()).eyeY
        val design = when (this) {
            Whole -> return Rect(Offset.Zero, cell)
            Face -> Rect(CENTRE - 130f, eyeY - 85f, CENTRE + 130f, eyeY + 115f)
            Body -> Rect(CENTRE - 160f, FEET + 6f - 213f, CENTRE + 160f, FEET + 6f)
        }
        return Rect(boxPoint(state, design.topLeft), boxPoint(state, design.bottomRight))
    }
}

/**
 * Draws Mo as a grid of pictures, one row per list, so he can be looked at without waiting for the
 * day that shows him so. Every cell is cut off at its edge. A [MoCloseUp.Whole] cell is his own
 * box, so whatever is drawn outside the box is cut off here as it is in the widget's bitmap.
 */
internal fun moSheet(rows: List<List<MoShot>>, scale: Float = 1f, close: MoCloseUp = MoCloseUp.Whole): Bitmap {
    val cell = close.cell * scale
    val sheet = ImageBitmap((cell.width * rows.maxOf { it.size }).toInt(), (cell.height * rows.size).toInt())
    val canvas = Canvas(sheet)
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MomentumDarkOnSurfaceVariant.toArgb()
        textSize = LABEL_SIZE
    }
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(sheet.width.toFloat(), sheet.height.toFloat())) {
        drawRect(MomentumDarkBackground)
        rows.forEachIndexed { row, shots ->
            shots.forEachIndexed { column, shot ->
                translate(left = column * cell.width, top = row * cell.height) {
                    val window = close.window(shot.look.state)
                    val zoom = cell.width / window.width
                    clipRect(right = cell.width, bottom = cell.height) {
                        translate(left = -window.left * zoom, top = -window.top * zoom) {
                            // A nested scope gives him a box of his own size, which drawMo scales itself to.
                            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(BOX_WIDTH, BOX_HEIGHT) * zoom) {
                                drawMo(shot.look, shot.seconds, MomentumDarkBackground)
                            }
                        }
                        shot.label?.let { drawContext.canvas.nativeCanvas.drawText(it, 6f, LABEL_SIZE + 2f, text) }
                    }
                }
            }
        }
    }
    return sheet.asAndroidBitmap()
}

/** An act as a grid of moments. */
internal fun moActSheet(act: MoAct, rows: List<List<MoActMoment>>, scale: Float = 1f, close: MoCloseUp = MoCloseUp.Whole): Bitmap =
    moSheet(rows.map { row -> row.map { MoShot(it.look(act), it.seconds) } }, scale, close)

internal fun MoActMoment.look(act: MoAct): MoLook =
    MoLook(state, frozen = act.occasion == MoOccasion.Frozen, scene = MoScene(act, MoVariant(seed)))

/**
 * The states an act can be seen in, brightest first. Blazing is his tightest box and adds the
 * sparkles, so a tall act is judged there before it is judged Steady.
 */
internal fun MoAct.states(): List<MoState> = MoState.entries.reversed().filter { it.bodyKind() in bodies }

/**
 * The sheet an act is reviewed by: the top row in its brightest state in one variant, the bottom
 * row in its dimmest in another, so an act for the burning bodies is seen both tall and low.
 */
internal fun MoAct.reviewRows(moments: List<Float> = ReviewMoments): List<List<MoActMoment>> =
    listOf(states().first() to 1, states().last() to 7).map { (state, seed) -> moments.map { MoActMoment(state, seed, it) } }

// Uneven gaps, so a loop of any usual length is caught at different points of its cycle.
internal val ReviewMoments = listOf(0f, 0.6f, 1.4f, 2.3f, 3.4f, 4.7f, 6.2f, 8.1f)

/**
 * What the day can choose for an act: two dozen variants in its brightest state, each with its
 * number on it. A review sheet shows two variants, so an act that picks among three shapes has one
 * that the sheet never shows. With two dozen, a shape picked one time in four is missed by one act
 * in a thousand.
 */
internal fun MoAct.variantRows(): List<List<MoShot>> = VariantSeeds.chunked(VARIANT_COLUMNS).mapIndexed { row, seeds ->
    seeds.map { seed -> MoShot(MoActMoment(states().first(), seed, VariantMoments[row]).look(this), VariantMoments[row], "$seed") }
}

private const val VARIANT_COLUMNS = 12
private val VariantSeeds = (1..24).toList()

// A row early in the act and one late, since some of what an act draws only shows after a while.
private val VariantMoments = listOf(2.3f, 6.2f)

internal const val CELL_WIDTH = 320
internal const val CELL_HEIGHT = 432
private const val LABEL_SIZE = 15f
