package com.example.workouttracker.feature.mo

import androidx.compose.ui.text.buildAnnotatedString
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.workouttracker.R
import com.example.workouttracker.core.locale.currentLocale
import com.example.workouttracker.core.locale.withAppLanguage
import com.example.workouttracker.core.format.DateStyle
import com.example.workouttracker.core.format.formatDate
import com.example.workouttracker.core.ui.MomentumButton
import com.example.workouttracker.core.ui.MomentumButtonVariant
import com.example.workouttracker.core.ui.MomentumMenu
import com.example.workouttracker.core.ui.MomentumMenuItem
import com.example.workouttracker.core.ui.ViewTheme
import com.example.workouttracker.core.ui.appPlaceViewColors
import com.example.workouttracker.core.ui.platformAnimationsEnabled
import com.example.workouttracker.core.ui.theme.MoAsh
import com.example.workouttracker.core.ui.theme.MoAshDark
import com.example.workouttracker.core.ui.theme.MoCoin
import com.example.workouttracker.core.ui.theme.MoCoinLedge
import com.example.workouttracker.core.ui.theme.MoCoinShine
import com.example.workouttracker.core.ui.theme.MoInk
import com.example.workouttracker.core.ui.theme.MoSmoke
import com.example.workouttracker.core.ui.theme.MoStageColdGlow
import com.example.workouttracker.core.ui.theme.MoStageColdMid
import com.example.workouttracker.core.ui.theme.MoStageDark
import com.example.workouttracker.core.ui.theme.MoStageWarmGlow
import com.example.workouttracker.core.ui.theme.MoStageWarmMid
import com.example.workouttracker.core.ui.theme.MoStreakChipText
import com.example.workouttracker.core.ui.theme.MomentumHues
import com.example.workouttracker.core.ui.theme.MomentumOnVibrant
import com.example.workouttracker.core.ui.viewColors
import com.example.workouttracker.domain.mo.MoLevel
import com.example.workouttracker.domain.mo.MoLevelArrival
import com.example.workouttracker.domain.mo.MoState
import com.example.workouttracker.domain.mo.MoStreakLoss
import com.example.workouttracker.domain.mo.MoThrivingLook
import kotlin.math.PI
import kotlin.math.sin

/** The name a level goes by. */
@get:StringRes
internal val MoLevel.labelRes: Int
    get() = when (this) {
        MoLevel.Spark -> R.string.mo_level_spark
        MoLevel.Flicker -> R.string.mo_level_flicker
        MoLevel.Ember -> R.string.mo_level_ember
        MoLevel.Flame -> R.string.mo_level_flame
        MoLevel.Fire -> R.string.mo_level_fire
        MoLevel.Blaze -> R.string.mo_level_blaze
        MoLevel.Inferno -> R.string.mo_level_inferno
    }

/** Mo at his very best: how he stands on the stage of a level, and on the picture that shares it. */
internal val MoAtHisBest = MoLook(MoState.Blazing, thriving = MoThrivingLook.entries.filter { it != MoThrivingLook.None }.toSet())

/** A view whose miss broke the streak, offered to be filled in on the day its record is about. */
internal class MoBackfill(val label: String, val dateText: String, val onOpen: () -> Unit)

/**
 * A level reached takes over the screen: Mo crouches, leaps and blazes on a warm stage, rays turn
 * behind him and the level's medal lands at his side. Everything moves by drawing only, so nothing
 * is measured again while it plays; with animations off it is the final frame. It waits for a tap.
 */
@Composable
internal fun MoLevelTakeover(
    arrival: MoLevelArrival,
    days: Int,
    onShare: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = platformAnimationsEnabled()
    val progress = remember(arrival) { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(arrival) { progress.animateTo(1f, tween(LeapMillis, easing = LinearEasing)) }
    val level = arrival.level
    MomentStage(warm = true, testTag = "mo_level_takeover", modifier = modifier) {
        MomentKicker(stringResource(R.string.mo_level_new, level.ordinal + 1, MoLevel.entries.size), MoCoin)
        // Mo takes the height the words and buttons leave, so nothing below him is ever squeezed out.
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.BottomCenter) {
            Canvas(Modifier.fillMaxSize()) { drawRays(progress.value) }
            Mo(
                look = MoAtHisBest,
                modifier = Modifier.fillMaxHeight(StageFigureHeight).leaping { progress.value },
                lift = { leapLift(progress.value) },
            )
            Medal(
                days = level.days,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 4.dp, bottom = 8.dp).landing { progress.value },
            )
        }
        Text(
            text = stringResource(level.labelRes),
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Black,
            color = MoCoin,
            textAlign = TextAlign.Center,
        )
        Text(
            text = pluralStringResource(R.plurals.mo_level_days, days, days),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        LevelLadder(reached = level.ordinal + 1, color = MoCoin, rest = MoAshDark)
        Text(
            text = level.next?.let { next ->
                val left = (next.days - days).coerceAtLeast(1)
                pluralStringResource(R.plurals.mo_level_next, left, left, stringResource(next.labelRes))
            } ?: stringResource(R.string.mo_level_top),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        ViewTheme(MomentumHues.Yellow.viewColors()) {
            MomentButtons {
                MomentumButton(text = stringResource(R.string.mo_level_share), onClick = onShare, modifier = Modifier.fillMaxWidth(), testTag = "mo_level_share")
                MomentumButton(text = stringResource(R.string.mo_event_continue), onClick = onDone, modifier = Modifier.fillMaxWidth(), variant = MomentumButtonVariant.Quiet, testTag = "mo_level_continue")
            }
        }
    }
}

/**
 * A streak lost is the level's mirror: the same stage gone cold, Mo flickering down from his flame
 * to an ember while smoke rises, and the days the run had. Filling in what was forgotten is offered
 * quietly at the bottom, like a forgotten password: there for whoever looks, never the first thing
 * seen. [backfills] are the views whose miss broke it; one opens straight away, several are listed.
 */
@Composable
internal fun MoLossTakeover(
    loss: MoStreakLoss,
    backfills: List<MoBackfill>,
    onRestart: () -> Unit,
    onStrategy: () -> Unit,
    onTalk: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = platformAnimationsEnabled()
    val progress = remember(loss) { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(loss) { progress.animateTo(1f, tween(LossMillis, easing = LinearEasing)) }
    var listing by remember(loss) { mutableStateOf(false) }
    val locale = currentLocale()
    MomentStage(warm = false, testTag = "mo_loss_takeover", modifier = modifier) {
        MomentKicker(stringResource(R.string.mo_lost_title), MaterialTheme.colorScheme.onSurfaceVariant)
        // Mo takes the height the words and actions leave, so every action keeps its room.
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.BottomCenter) {
            Canvas(Modifier.fillMaxSize()) { drawSmoke(progress.value) }
            // He burns down through his own states, so every frame is a drawing he already has.
            val state = when {
                progress.value < 0.3f -> MoState.Steady
                progress.value < 0.6f -> MoState.Dimming
                else -> MoState.Cold
            }
            Mo(
                look = MoLook(state),
                modifier = Modifier
                    .fillMaxHeight(LossFigureHeight)
                    .graphicsLayer {
                        // Shrink towards the height of the next body before handing the pose to it.
                        val p = progress.value
                        val shrink = when (state) {
                            MoState.Steady -> 1f - 0.29f * ((p - 0.18f) / 0.12f).coerceIn(0f, 1f)
                            MoState.Dimming -> 1f - 0.17f * ((p - 0.48f) / 0.12f).coerceIn(0f, 1f)
                            else -> 1f
                        }
                        val flicker = if (p < 0.6f) sin(p * 60f) * 0.02f else 0f
                        scaleX = shrink + flicker
                        scaleY = shrink - flicker
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                    },
            )
        }
        // One sentence, so it reads whole in every language and to TalkBack; its first line is the count, shown large.
        val sentence = pluralStringResource(R.plurals.mo_lost_sentence, loss.days, loss.days, formatDate(loss.lostOn, DateStyle.WeekdayDayMonth, locale))
        val headline = MaterialTheme.typography.displayMedium.toSpanStyle().copy(fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
        Text(
            text = buildAnnotatedString {
                append(sentence)
                val lineEnd = sentence.indexOf('\n').takeIf { it >= 0 } ?: sentence.length
                addStyle(headline, 0, lineEnd)
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        LevelLadder(reached = MoLevel.of(loss.days)?.let { it.ordinal + 1 } ?: 0, color = MoAsh, rest = MoAshDark)
        Spacer(Modifier.height(12.dp))
        ViewTheme(appPlaceViewColors()) {
            MomentButtons {
                MomentumButton(text = stringResource(R.string.mo_lost_restart), onClick = onRestart, modifier = Modifier.fillMaxWidth(), testTag = "mo_lost_restart")
                MomentumButton(text = stringResource(R.string.mo_lost_strategy), onClick = onStrategy, modifier = Modifier.fillMaxWidth(), variant = MomentumButtonVariant.Secondary, testTag = "mo_lost_strategy")
                MomentumButton(text = stringResource(R.string.mo_lost_talk), onClick = onTalk, modifier = Modifier.fillMaxWidth(), variant = MomentumButtonVariant.Secondary, testTag = "mo_lost_talk")
                Box(Modifier.align(Alignment.CenterHorizontally)) {
                    // Always laid out, so offering it or not never moves the buttons above.
                    MomentumButton(
                        onClick = { if (backfills.size == 1) backfills.single().onOpen() else listing = !listing },
                        enabled = backfills.isNotEmpty(),
                        variant = MomentumButtonVariant.Quiet,
                        modifier = Modifier.graphicsLayer { alpha = if (backfills.isEmpty()) 0f else 1f },
                        testTag = "mo_lost_backfill",
                    ) {
                        // One text, so a large font wraps it instead of squeezing "Nachtragen" out of the row.
                        val forgot = stringResource(R.string.mo_lost_forgot)
                        val backfill = stringResource(R.string.mo_lost_backfill)
                        val link = MaterialTheme.colorScheme.onSurface
                        Text(
                            text = androidx.compose.ui.text.buildAnnotatedString {
                                append("$forgot ")
                                val start = length
                                append(backfill)
                                addStyle(androidx.compose.ui.text.SpanStyle(color = link, textDecoration = TextDecoration.Underline), start, length)
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                    // Anchor the choices to their link so a closed list takes no room from Mo.
                    MomentumMenu(expanded = listing, onDismissRequest = { listing = false }) {
                        backfills.forEach { backfill ->
                            MomentumMenuItem(
                                text = stringResource(R.string.mo_lost_backfill_view, backfill.label, backfill.dateText),
                                onClick = {
                                    listing = false
                                    backfill.onOpen()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The stage every moment stands on: a glow behind Mo fading into the dark, over all of Home. */
@Composable
internal fun MomentStage(
    warm: Boolean,
    testTag: String,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(testTag)
            // Nothing under the stage can be touched while it stands.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Image, onClick = {}),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val (glow, mid) = if (warm) MoStageWarmGlow to MoStageWarmMid else MoStageColdGlow to MoStageColdMid
            drawRect(
                Brush.radialGradient(
                    0f to glow,
                    0.45f to mid,
                    1f to MoStageDark,
                    center = Offset(size.width / 2f, size.height * 0.34f),
                    radius = maxOf(size.width, size.height) * 0.8f,
                ),
            )
        }
        Column(
            modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = content,
        )
    }
}

@Composable
internal fun MomentButtons(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

/** The small line over Mo that names the moment. */
@Composable
internal fun MomentKicker(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Black,
        letterSpacing = 2.sp,
        color = color,
        textAlign = TextAlign.Center,
    )
}

/** The level's medal: a gold coin with the days it stands for. */
@Composable
private fun Medal(days: Int, modifier: Modifier = Modifier) {
    Box(modifier.size(MedalSize), contentAlignment = Alignment.Center) {
        val ledge = 3.dp
        Canvas(Modifier.fillMaxSize()) {
            val depth = ledge.toPx()
            val radius = (size.minDimension - depth) / 2f
            val face = center - Offset(0f, depth / 2f)
            drawCircle(MoCoinLedge, radius, face + Offset(0f, depth))
            drawCircle(MoCoin, radius, face)
            drawCircle(MoCoinShine, radius * 0.76f, face, style = androidx.compose.ui.graphics.drawscope.Stroke(depth))
        }
        val style = MaterialTheme.typography.titleLarge
        BasicText(
            text = days.toString(),
            modifier = Modifier.fillMaxWidth(0.58f).padding(bottom = ledge),
            style = style.copy(color = MoInk, fontWeight = FontWeight.Black, textAlign = TextAlign.Center),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = style.fontSize),
        )
    }
}

/** One bar per level, the reached ones lit: how far up the ladder the streak stands. */
@Composable
private fun LevelLadder(reached: Int, color: Color, rest: Color) {
    Canvas(Modifier.width(LadderWidth).height(8.dp)) {
        val count = MoLevel.entries.size
        val gap = 4.dp.toPx()
        val width = (size.width - gap * (count - 1)) / count
        repeat(count) { index ->
            drawRoundRect(
                if (index < reached) color else rest,
                Offset(index * (width + gap), 0f),
                Size(width, size.height),
                CornerRadius(size.height / 2),
            )
        }
    }
}

/** Rays turning slowly behind him once he has leapt. */
internal fun DrawScope.drawRays(progress: Float) {
    val shown = ((progress - 0.25f) / 0.3f).coerceIn(0f, 1f)
    if (shown == 0f) return
    val figureHeight = minOf(size.height * StageFigureHeight, size.width / MoAspectRatio)
    val hub = Offset(center.x, size.height - figureHeight * 0.43f)
    val radius = size.width * 0.55f
    if (radius <= 0f) return
    // Keep the turning fan inside the stage even when large type leaves a shallow slot.
    val verticalScale = ((size.height - hub.y) * 0.96f / radius).coerceAtMost(1f)
    val ray = androidx.compose.ui.graphics.Path().apply {
        moveTo(hub.x, hub.y)
        lineTo(hub.x - radius * 0.09f, hub.y - radius)
        lineTo(hub.x + radius * 0.09f, hub.y - radius)
        close()
    }
    scale(1f, verticalScale, pivot = hub) {
        repeat(Rays) { index ->
            rotate(degrees = progress * 40f + index * 360f / Rays, pivot = hub) {
                drawPath(ray, MoCoin.copy(alpha = 0.12f * shown))
            }
        }
    }
}

/** Smoke rising from him as he goes out, each puff on its own path, fixed by its index. */
private fun DrawScope.drawSmoke(progress: Float) {
    val rising = ((progress - 0.45f) / 0.55f).coerceIn(0f, 1f)
    if (rising == 0f) return
    val figureHeight = minOf(size.height * LossFigureHeight, size.width / MoAspectRatio)
    val origin = Offset(center.x + figureHeight * 0.045f, size.height - figureHeight * 0.42f)
    repeat(SmokePuffs) { index ->
        val t = ((rising - index * 0.12f) / 0.52f).coerceIn(0f, 1f)
        if (t == 0f || t == 1f) return@repeat
        val drift = sin(t * 4f + index) * figureHeight * 0.06f * t
        val at = origin + Offset(drift, -figureHeight * 0.42f * t)
        val radius = minOf(20.dp.toPx(), figureHeight * 0.075f) * (0.22f + t)
        drawCircle(MoSmoke.copy(alpha = 0.32f * sin(t * PI.toFloat())), radius, at)
    }
}

/**
 * The crouch and the stretch of his leap. How high it carries him is [leapLift], which Mo draws
 * himself, so that his shadow stays on the floor.
 */
internal fun Modifier.leaping(progress: () -> Float): Modifier = graphicsLayer {
    val squash = leap(progress()).second
    scaleY = squash
    scaleX = 2f - squash
    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
}

/** How far off the floor his leap has him, in pixels of the canvas he is drawn on. */
internal fun DrawScope.leapLift(progress: Float): Float = leap(progress).first * minOf(40.dp.toPx(), size.height * 0.09f)

/** What a moment hands over lands at Mo's side after he does, with an overshoot. */
internal fun Modifier.landing(progress: () -> Float): Modifier = graphicsLayer {
    val landed = medalArrival(progress())
    translationY = (landed - 1f) * 14.dp.toPx()
    scaleX = landed
    scaleY = landed
    alpha = (landed * 2f).coerceAtMost(1f)
}

/** How high he is in his leap, and how squashed: a crouch, the jump, the landing. */
private fun leap(progress: Float): Pair<Float, Float> = when {
    progress < 0.12f -> 0f to 1f - 0.10f * sin(progress / 0.12f * PI.toFloat() / 2f)
    progress < 0.42f -> {
        val t = (progress - 0.12f) / 0.30f
        val lift = sin(t * PI.toFloat())
        val crouch = (1f - t * 4f).coerceAtLeast(0f)
        lift to 1f - 0.10f * crouch + 0.035f * lift
    }
    progress < 0.52f -> 0f to 1f - 0.06f * sin(((progress - 0.42f) / 0.1f) * PI.toFloat())
    else -> 0f to 1f
}

private fun medalArrival(progress: Float): Float {
    val t = ((progress - 0.5f) / 0.25f).coerceIn(0f, 1f)
    val left = 1f - t
    return 1f - left * left * left + 1.25f * t * left * left
}

/**
 * The picture a streak is shared as: Mo as he is now, the days in a row and the level, on the warm
 * stage. Square, so every app shows it whole. It holds nothing else: no statistics, no view names.
 */
internal fun renderStreakShareImage(context: Context, look: MoLook, days: Int): Bitmap {
    val resources = context.withAppLanguage().resources
    val bitmap = Bitmap.createBitmap(ShareSide, ShareSide, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val side = ShareSide.toFloat()
    canvas.drawRect(0f, 0f, side, side, Paint().apply {
        shader = RadialGradient(
            side / 2f, side * 0.42f, side * 0.75f,
            intArrayOf(MoStageWarmGlow.toArgb(), MoStageWarmMid.toArgb(), MoStageDark.toArgb()),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
    })
    fun text(value: String, size: Float, y: Float, color: Color, bold: Boolean, spacing: Float = 0f) {
        canvas.drawText(value, side / 2f, y, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = size
            letterSpacing = spacing
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            this.color = color.toArgb()
        })
    }
    text(resources.getString(R.string.mo_share_brand), 40f, 112f, MoStreakChipText, bold = true, spacing = 0.25f)
    val moHeight = 560f
    val mo = renderMoBitmap(context, look, heightDp = moHeight / context.resources.displayMetrics.density, ground = MoStageWarmMid)
    canvas.drawBitmap(mo, (side - mo.width) / 2f, 150f + (moHeight - mo.height).coerceAtLeast(0f), null)
    text(resources.getQuantityString(R.plurals.mo_share_days, days, days), 150f, 870f, MomentumOnVibrant, bold = true)
    val level = MoLevel.of(days)
    val second = resources.getString(R.string.mo_share_in_a_row) + " · " +
        (level?.let { resources.getString(R.string.mo_share_level, resources.getString(it.labelRes)) } ?: resources.getString(R.string.mo_share_no_level))
    text(second, 50f, 960f, MoStreakChipText, bold = true)
    return bitmap
}

internal const val StageFigureHeight = 0.92f

/** How long the leap and what lands after it play. */
internal const val LeapMillis = 1_800
private const val LossMillis = 1_600
private const val Rays = 14
private const val LossFigureHeight = 0.94f
private const val SmokePuffs = 5
private val MedalSize = 64.dp
private val LadderWidth = 160.dp
private const val ShareSide = 1080
