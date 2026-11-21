package com.example.workouttracker.core.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.workouttracker.R
import com.example.workouttracker.core.ui.theme.MoFreezeChip
import com.example.workouttracker.core.ui.theme.MoIce
import com.example.workouttracker.core.ui.theme.MoIceLedge
import com.example.workouttracker.core.ui.theme.MoRestToken
import com.example.workouttracker.core.ui.theme.MoRestTokenLedge
import com.example.workouttracker.core.ui.theme.MoRestTokenMoon
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.unit.dp
import com.example.workouttracker.domain.TrackerRef

/**
 * A hollow ring at the foot of a calendar cell: nothing was logged, and nothing was owed. It is
 * hollow because every filled mark in a calendar means a record.
 */
@Composable
internal fun BoxScope.FreeDayMark(color: Color) {
    Canvas(
        Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 5.dp)
            .size(8.dp),
    ) {
        drawCircle(color = color, radius = size.minDimension / 2f - 1.dp.toPx(), style = Stroke(width = 2.dp.toPx()))
    }
}

/**
 * What covered a day a feature went without a record: the week's free day under the old rules, the
 * feature's own token, a planned rest day from activity, or a freeze. Nothing here is a record: on
 * a blank day a token, a rest day or a freeze is a seal behind the date ([DayCoverSeal]), a shape no
 * record ever takes, and anywhere else a small mark at the cell's foot ([DayCoverMark]).
 */
internal enum class DayCover { Free, Token, Rest, Freeze }

/**
 * Each of Mo's tasks' covered days by date key. The streak decides them, so the app provides them
 * once and each calendar only looks up its task's. Without a provider there are none.
 */
internal val LocalDayCovers = staticCompositionLocalOf<Map<TrackerRef, Map<String, DayCover>>> { emptyMap() }

/** What a calendar adds to a covered day's spoken status. */
@get:StringRes
internal val DayCover.spoken: Int
    get() = when (this) {
        DayCover.Free -> R.string.mo_cover_free
        DayCover.Token -> R.string.mo_cover_token
        DayCover.Rest -> R.string.mo_cover_rest
        DayCover.Freeze -> R.string.mo_cover_freeze
    }

/**
 * The cover drawn at the foot of a calendar cell. A token is the feature's own hexagon in its own
 * colours, the same object the streak dialog shows, so a covered day reads as "a token went here".
 */
@Composable
internal fun BoxScope.DayCoverMark(cover: DayCover, contentColor: Color) {
    if (cover == DayCover.Free) {
        FreeDayMark(contentColor)
        return
    }
    val identity = LocalViewColors.current
    Canvas(
        Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 3.dp)
            .size(12.dp),
    ) {
        when (cover) {
            DayCover.Token -> {
                drawPath(hexagon(Offset(size.width / 2f, size.height / 2f + 1.dp.toPx()), size.minDimension / 2f), identity.ledge)
                drawPath(hexagon(Offset(size.width / 2f, size.height / 2f), size.minDimension / 2f), identity.accent)
            }
            DayCover.Rest -> {
                drawCircle(MoRestToken)
                drawCircle(MoRestTokenMoon, size.minDimension * 0.3f)
                drawCircle(MoRestToken, size.minDimension * 0.26f, Offset(size.width * 0.62f, size.height * 0.4f))
            }
            DayCover.Freeze -> {
                drawRoundRect(MoIceLedge, Offset(0f, 1.dp.toPx()), size, CornerRadius(3.dp.toPx()))
                drawRoundRect(MoIce, Offset.Zero, Size(size.width, size.height - 1.dp.toPx()), CornerRadius(3.dp.toPx()))
            }
            DayCover.Free -> Unit
        }
    }
}

/** Whether [this] cover takes over a blank cell as a seal; a free day stays a hollow ring. */
internal val DayCover.hasSeal: Boolean get() = this != DayCover.Free

/**
 * The ink a date wears on [this] cover's seal, made for the seal's face; null for a free day,
 * which is a hollow ring and never a seal.
 */
internal val DayCover.sealInk: Color?
    @Composable get() = when (this) {
        DayCover.Token -> LocalViewColors.current.onAccent
        DayCover.Rest -> MoRestTokenMoon
        DayCover.Freeze -> MoFreezeChip
        DayCover.Free -> null
    }

/**
 * A cover drawn as big as a blank cell, behind its date: the feature's hexagon, the rest day's
 * night-blue coin, or the freeze's ice. It is as prominent as a recorded day, which a covered day
 * earned, yet never a filled square, so it cannot be read as a record. A free day draws nothing.
 */
@Composable
internal fun BoxScope.DayCoverSeal(cover: DayCover) {
    if (!cover.hasSeal) return
    val identity = LocalViewColors.current
    Canvas(
        Modifier
            .matchParentSize()
            .padding(SealInset),
    ) {
        val ledge = SealLedge.toPx()
        val centre = Offset(size.width / 2f, (size.height - ledge) / 2f)
        val radius = (size.minDimension - ledge) / 2f
        when (cover) {
            DayCover.Token -> {
                drawPath(hexagon(centre + Offset(0f, ledge), radius), identity.ledge)
                drawPath(hexagon(centre, radius), identity.accent)
            }
            DayCover.Rest -> {
                drawCircle(MoRestTokenLedge, radius * RestCoinShare, centre + Offset(0f, ledge))
                drawCircle(MoRestToken, radius * RestCoinShare, centre)
                // A small moon above the date, clear of it, so the date's ink never lands on it.
                val moon = centre + Offset(0f, -radius * MoonRise)
                drawCircle(MoRestTokenMoon, radius * MoonShare, moon)
                drawCircle(MoRestToken, radius * MoonShare * 0.86f, moon + Offset(radius * MoonShare * 0.55f, -radius * MoonShare * 0.4f))
            }
            DayCover.Freeze -> {
                val side = radius * 2f * IceShare
                val topLeft = Offset(centre.x - side / 2f, centre.y - side / 2f)
                val corner = CornerRadius(side * 0.22f)
                drawRoundRect(MoIceLedge, topLeft + Offset(0f, ledge), Size(side, side), corner)
                drawRoundRect(MoIce, topLeft, Size(side, side), corner)
            }
            DayCover.Free -> Unit
        }
    }
}

private val SealInset = 2.dp
private val SealLedge = 2.dp

/** A coin as wide as the hexagon reads larger than it; this keeps the three seals one size by eye. */
private const val RestCoinShare = 0.94f
private const val MoonShare = 0.2f
private const val MoonRise = 0.6f
private const val IceShare = 0.8f

private fun DrawScope.hexagon(centre: Offset, radius: Float): Path = Path().apply {
    for (corner in 0 until 6) {
        val angle = PI / 3.0 * corner
        val x = centre.x + radius * cos(angle).toFloat()
        val y = centre.y + radius * sin(angle).toFloat()
        if (corner == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}
