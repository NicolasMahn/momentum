package com.example.workouttracker.feature.mo

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.roundToInt

/**
 * Mo at rest as a bitmap, for the surfaces Compose cannot reach: the widget and notifications. It is
 * the same drawing the app animates, so he looks the same everywhere.
 */
internal fun renderMoBitmap(context: Context, look: MoLook, heightDp: Float, ground: Color): Bitmap {
    val density = Density(context)
    val height = (heightDp * density.density).roundToInt()
    val width = (height * MoAspectRatio).roundToInt()
    val bitmap = ImageBitmap(width, height)
    CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(bitmap), Size(width.toFloat(), height.toFloat())) {
        drawMo(look, seconds = 0f, ground = ground)
    }
    return bitmap.asAndroidBitmap()
}

/** One of Mo's score icons as a bitmap, for the same surfaces. */
internal fun renderMoIcon(context: Context, sizeDp: Float, draw: DrawScope.() -> Unit): Bitmap {
    val density = Density(context)
    val size = (sizeDp * density.density).roundToInt()
    val bitmap = ImageBitmap(size, size)
    CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(bitmap), Size(size.toFloat(), size.toFloat()), draw)
    return bitmap.asAndroidBitmap()
}
