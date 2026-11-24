package de.nicolasmahn.momentum.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

/** What a number shows when there is no value. One token, so every empty stat reads the same. */
internal const val NoValueText = "—"

/**
 * A number that counts to its [target]. It owns the animation and the read of it, so only this
 * text recomposes while the number travels, never the screen around it.
 *
 * [format] receives the travelling value with `settled = false` and the exact [target] with
 * `settled = true`, so a caller can round mid-flight (a jittering width reads as a glitch) and
 * still show the precise value at rest. With [restartFrom] every change counts up again from the
 * value it returns; without it the number travels on from wherever it stands.
 */
@Composable
internal fun CountUpText(
    target: Double?,
    format: (value: Double, settled: Boolean) -> String,
    modifier: Modifier = Modifier,
    placeholder: String = NoValueText,
    restartFrom: ((Double) -> Double)? = null,
    durationMillis: Int = DefaultCountUpMillis,
    motionEnabled: Boolean = platformAnimationsEnabled(),
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
) {
    val currentRestartFrom by rememberUpdatedState(restartFrom)
    val shown = remember {
        val first = target ?: 0.0
        Animatable((restartFrom?.invoke(first) ?: first).toFloat())
    }
    // The target the count has arrived at. Until the effect below has run for a new target the
    // text keeps showing the travelling value, so the new number never flashes before it counts.
    var reached by remember { mutableStateOf(if (restartFrom == null) target else null) }
    LaunchedEffect(target, motionEnabled) {
        if (target == null) return@LaunchedEffect
        if (motionEnabled) {
            currentRestartFrom?.let { shown.snapTo(it(target).toFloat()) }
            shown.animateTo(target.toFloat(), tween(durationMillis, easing = FastOutSlowInEasing))
        } else {
            shown.snapTo(target.toFloat())
        }
        reached = target
    }
    Text(
        text = when {
            target == null -> placeholder
            reached == target -> format(target, true)
            else -> format(shown.value.toDouble(), false)
        },
        modifier = modifier,
        style = style,
        color = color,
        maxLines = 1,
        softWrap = false,
    )
}

private const val DefaultCountUpMillis = 280
