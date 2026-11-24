package de.nicolasmahn.momentum.feature.mo

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import de.nicolasmahn.momentum.core.ui.theme.MoRageBelly
import de.nicolasmahn.momentum.core.ui.theme.MoRageLedge
import de.nicolasmahn.momentum.core.ui.theme.MoRageMiddle
import de.nicolasmahn.momentum.core.ui.theme.MoRageOuter
import de.nicolasmahn.momentum.core.ui.theme.MoShadow
import de.nicolasmahn.momentum.core.ui.theme.MoColdInk
import de.nicolasmahn.momentum.core.ui.theme.MoEyeWhite
import de.nicolasmahn.momentum.core.ui.theme.MoFlameBelly
import de.nicolasmahn.momentum.core.ui.theme.MoStink
import de.nicolasmahn.momentum.core.ui.theme.MoIceShine
import de.nicolasmahn.momentum.core.ui.theme.MoIceLedge
import de.nicolasmahn.momentum.core.ui.theme.MoIceDeep
import de.nicolasmahn.momentum.core.ui.theme.MoIce
import de.nicolasmahn.momentum.core.ui.theme.MoGear
import de.nicolasmahn.momentum.core.ui.theme.MoFoam
import de.nicolasmahn.momentum.core.ui.theme.MoFlameLedge
import de.nicolasmahn.momentum.core.ui.theme.MoFlameMiddle
import de.nicolasmahn.momentum.core.ui.theme.MoFlameOuter
import de.nicolasmahn.momentum.core.ui.theme.MoGloss
import de.nicolasmahn.momentum.core.ui.theme.MoHusk
import de.nicolasmahn.momentum.core.ui.theme.MoHuskCrack
import de.nicolasmahn.momentum.core.ui.theme.MoHuskLedge
import de.nicolasmahn.momentum.core.ui.theme.MoInk
import de.nicolasmahn.momentum.core.ui.theme.MoSlate
import de.nicolasmahn.momentum.core.ui.theme.MoSlateGloss
import de.nicolasmahn.momentum.core.ui.theme.MoSlateLedge
import de.nicolasmahn.momentum.core.ui.theme.MoSmoke
import de.nicolasmahn.momentum.core.ui.theme.MoSpark
import de.nicolasmahn.momentum.core.ui.theme.MoTear
import de.nicolasmahn.momentum.core.ui.theme.MoTongue
import de.nicolasmahn.momentum.core.ui.theme.MoTooth
import de.nicolasmahn.momentum.core.ui.theme.MoToothRotten
import de.nicolasmahn.momentum.core.ui.theme.MoToothStained
import de.nicolasmahn.momentum.domain.mo.MoMood
import de.nicolasmahn.momentum.domain.TrackerRef
import de.nicolasmahn.momentum.domain.mo.MoNeglect
import de.nicolasmahn.momentum.domain.mo.MoProp
import de.nicolasmahn.momentum.domain.mo.MoSaveAct
import de.nicolasmahn.momentum.domain.mo.MoState
import de.nicolasmahn.momentum.domain.mo.MoThrivingLook
import de.nicolasmahn.momentum.domain.mo.contentKey
import de.nicolasmahn.momentum.domain.mo.figure
import de.nicolasmahn.momentum.domain.mo.isFrozen
import de.nicolasmahn.momentum.feature.mo.acts.MoScene
import de.nicolasmahn.momentum.feature.mo.acts.moSessionScene
import de.nicolasmahn.momentum.feature.mo.acts.bodyKind
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Everything that decides how Mo looks, apart from time. */
internal data class MoLook(
    val state: MoState,
    val neglect: List<MoNeglectedDrawing> = emptyList(),
    /** Strong arms, glasses and a bright smile come from content, not task identity. */
    val thriving: Set<MoThrivingLook> = emptySet(),
    /** What was logged last today. While he burns, he is busy doing exactly that. */
    val doing: MoSaveAct? = null,
    /** A freeze was spent for him yesterday and nothing has thawed him yet. */
    val frozen: Boolean = false,
    /** Something is overdue today. Only a Mo who still burns has the fire to be angry about it. */
    val angry: Boolean = false,
    /** What he is up to at this time of this day. A passing [doing] moment takes its place. */
    val scene: MoScene? = null,
) {
    val teethNeglect: MoNeglect?
        get() = neglect.filter { it.prop == MoProp.LostTeeth }.maxOfOrNull { it.neglect }

    /**
     * The act that plays. An activity takes the place of the day's scene while he burns and nothing
     * weightier is going on; a Mo who is cold, frozen or in a tantrum keeps to his day.
     *
     * Looked up when first asked for and not when the look is made: a timer makes a new look on
     * every recomposition, and the book is searched only for the one that is drawn.
     */
    val playingScene: MoScene? by lazy {
        val activity = doing ?: scene?.act?.saveAct ?: return@lazy scene
        val body = state.bodyKind()
        when {
            // What he does not act out leaves his day as it is.
            !actsOut(activity) -> scene?.takeIf { it.act.saveAct == null }
            scene?.act?.saveAct == activity && body in scene.act.bodies -> scene
            // A timer or the gallery asks for an activity without choosing an act.
            else -> moSessionScene(activity, body)
        }
    }

    /**
     * Whether he acts [activity] out as he is now. Only a Mo who burns does, and neither in a
     * tantrum nor in the ice, and a sore mouth keeps him from brushing for show. Home asks this
     * before a save takes the place of his day, so a save he will not act out interrupts nothing.
     */
    fun actsOut(activity: MoSaveAct): Boolean =
        state.bodyKind() in MoBodies.Burning && !frozen && !angry && !(activity == MoSaveAct.Brushing && teethNeglect != null)

    /** The stable task order breaks ties; changing to a drawing vocabulary must not move a prop. */
    val props: List<MoNeglectedDrawing>
        get() = neglect
            .filter { it.prop != MoProp.None && it.prop != MoProp.LostTeeth }
            .sortedWith(compareByDescending<MoNeglectedDrawing> { it.neglect }.thenBy { it.order })
            .take(2)

    companion object {
        /**
         * The one place that turns a mood into a look, so every surface shows the same Mo. What he
         * is [doing] is a passing moment the caller decides, not part of his mood.
         *
         * Apart from his teeth, which do not heal in a day, he only shows what is still open today:
         * nothing lies around on a day that asks nothing, or once the task is done.
         */
        fun of(mood: MoMood, doing: TrackerRef? = null, angry: Boolean = false, scene: MoScene? = null): MoLook = MoLook(
            state = mood.state,
            neglect = mood.trackerNeglect.entries.mapNotNull { (need, level) ->
                val content = mood.contentKey(need).figure
                if (content.persistentNeglect || need in mood.missingTrackersToday || need in mood.freeTrackersToday) {
                    MoNeglectedDrawing(content.prop, level, mood.trackerOrder.indexOf(need).takeIf { it >= 0 } ?: Int.MAX_VALUE)
                } else {
                    null
                }
            },
            thriving = mood.thrivingTrackers.map { mood.contentKey(it).figure.thrivingLook }.filter { it != MoThrivingLook.None }.toSet(),
            doing = doing?.let { mood.contentKey(it).figure.saveAct },
            frozen = mood.isFrozen,
            angry = angry && mood.state >= MoState.Dimming,
            scene = scene,
        )
    }
}

/** Order belongs to the source task; the drawing only knows what lies there and how neglected it is. */
internal data class MoNeglectedDrawing(val prop: MoProp, val neglect: MoNeglect, val order: Int = 0)

/**
 * Draws Mo, the one implementation every surface uses (see docs/mo.md). The in-app Mo calls it each
 * frame with the running [seconds]; the widget calls it once with 0, which is his resting pose.
 *
 * All motion is a function of [seconds] alone, so it never ends, never drifts, and needs no state.
 * [ground] is the colour behind him: the Husk's hollow is a real hole, so it shows that colour.
 *
 * He is drawn in a design space 230 wide with his feet on y = 425, traced from the approved render.
 * The scope is filled by a 320 x 432 box around that, which leaves room for raised arms, sparkles,
 * and the props that lie beside him.
 *
 * [lift] is how far off the floor he is, in pixels: a hop or a leap. It is drawn here and not
 * by moving the canvas, so his shadow and the things beside him stay on the floor.
 */
internal fun DrawScope.drawMo(look: MoLook, seconds: Float, ground: Color, lift: Float = 0f) {
    val unit = min(size.width / BOX_WIDTH, size.height / BOX_HEIGHT)
    val figure = Figure.of(look.state)
    val scale = figure.scale
    val breath = if (look.frozen) 0f else wave(seconds, figure.breathPeriod) * figure.breathDepth
    val shiver = if (look.state == MoState.Cold && !look.frozen) wave(seconds, 0.11f) * 1.6f else 0f

    withTransform({
        translate(size.width / 2f, size.height - FLOOR_MARGIN * unit)
        scale(unit, unit, Offset.Zero)
    }) {
        val scene = look.playingScene
        // What lies on the floor stays there when he leaves it, and his shadow draws in under him.
        val height = lift / unit
        val shadow = figure.halfWidth * scale * 0.78f * (1f - SHADOW_DRAWS_IN * (height / SHADOW_DRAWN_IN_AT).coerceAtMost(1f))
        if (!look.frozen && look.state != MoState.Husk) oval(Offset.Zero, shadow, 7f, MoShadow)
        if (scene?.act?.ownsTheFloor != true) drawProps(look, halfWidth = figure.halfWidth * scale)
        withTransform({
            scale(scale * (1f - breath / 2f), scale * (1f + breath), Offset.Zero)
            translate(-CENTRE + shiver, -FEET)
        }) {
            // A hop or a leap takes him off the floor with whatever he holds. The world he stands
            // in stays where it is: a scale he has jumped off does not come up under his feet.
            val rise = height / (scale * (1f + breath))
            // Inside the ice nothing moves: he is drawn at rest and only the ice glints.
            val time = if (look.frozen) 0f else seconds
            // The smaller he is, the more of his world the picture shows above him.
            val pictureTop = FEET - (BOX_HEIGHT - FLOOR_MARGIN) / (scale * (1f + breath))
            // What an act is told of the picture is the picture at rest: a thing that comes in
            // from its edge should not start a little nearer with every breath he takes.
            val picture = Rect(
                left = CENTRE - BOX_WIDTH / 2f / scale,
                top = FEET - (BOX_HEIGHT - FLOOR_MARGIN) / scale,
                right = CENTRE + BOX_WIDTH / 2f / scale,
                bottom = FEET + FLOOR_MARGIN / scale,
            )
            if (scene != null) belowThePictureTop(pictureTop) { with(scene.act) { world(seconds, scene.variant, picture) } }
            withTransform({ translate(0f, -rise) }) {
                if (scene != null) drawSceneBody(scene, look, time, ground, rise) else drawClassicBody(look, time, ground)
            }
            val ice = if (look.frozen) iceBounds(figure, look) else null
            if (ice != null) drawIceBlock(ice, seconds)
            if (scene != null) {
                belowThePictureTop(pictureTop) {
                    with(scene.act) {
                        if (ice != null) atIce(seconds, scene.variant, ice)
                        foreground(seconds, scene.variant, picture)
                    }
                }
                if (look.state == MoState.Blazing && scene.act.sparkles && !look.frozen) blazingSparkles(seconds)
            }
        }
    }
}

/**
 * Where a point of his design space lands in his box while he stands at rest in [state]: the way
 * from an act's numbers to the picture, for whatever has to find his face or his hands in it.
 */
internal fun boxPoint(state: MoState, point: Offset): Offset {
    val scale = Figure.of(state).scale
    return Offset(BOX_WIDTH / 2f + (point.x - CENTRE) * scale, BOX_HEIGHT - FLOOR_MARGIN + (point.y - FEET) * scale)
}

internal const val BOX_WIDTH = 320f
internal const val BOX_HEIGHT = 432f
private const val FLOOR_MARGIN = 6f
internal const val CENTRE = 115f
internal const val FEET = 425f
private const val BLAZING_BODY = 343f

/** The share of his canvas above even a blazing flame: room for a hop, or what an act brings along. */
internal const val MoHeadroom = (BOX_HEIGHT - FLOOR_MARGIN - BLAZING_BODY) / BOX_HEIGHT
private const val PROP_SCALE = 1.5f

// Off the floor his shadow draws in by up to this share of its width, which it has lost once he
// is this high, in box units.
private const val SHADOW_DRAWS_IN = 0.3f
private const val SHADOW_DRAWN_IN_AT = 48f

// The web on a neglected thing reaches this far from the thing's middle, and nothing lies closer
// to the edge of his box than a hair.
private const val PROP_WEB_REACH = 38f
private const val PROP_EDGE = 3f

/**
 * Far above any picture of him. Whatever hangs into the picture from above it, a thread, a lamp's
 * rod, a curtain, starts here, and is cut off at the top of his picture.
 */
internal const val ABOVE_THE_PICTURE = -400f

private fun DrawScope.drawClassicBody(look: MoLook, time: Float, ground: Color) {
    when (look.state) {
        MoState.Blazing, MoState.Steady ->
            if (look.angry) drawAngry(TALL_FLAME, look, time) else drawFlame(look, time, look.state == MoState.Blazing)
        MoState.Dimming -> if (look.angry) drawAngry(LOW_FLAME, look, time) else drawDimming(look, time)
        MoState.Cold -> drawCold(look, time)
        MoState.Husk -> drawHusk(time, ground)
    }
}

private fun DrawScope.drawSceneBody(scene: MoScene, look: MoLook, poseTime: Float, ground: Color, lift: Float) {
    val act = scene.act
    val variant = scene.variant
    val kind = look.state.bodyKind()
    if (kind == MoBodyKind.Husk) {
        drawHusk(poseTime, ground)
    } else {
        drawPosed(MoBodies.of(kind), act.pose(poseTime, variant, kind), poseTime, teeth = look.teethNeglect, lift = lift) { rig ->
            with(act) { held(poseTime, variant, rig) }
        }
    }
}

/** A thread or rod from a fixed support above the picture to its visible end. */
internal fun DrawScope.threadFromAbove(to: Offset, colour: Color, width: Float, anchorX: Float = to.x) {
    limb(Offset(anchorX, ABOVE_THE_PICTURE), to, colour, width)
}

private inline fun DrawScope.belowThePictureTop(top: Float, draw: DrawScope.() -> Unit) =
    clipRect(left = -FAR, top = top, right = FAR, bottom = FAR, block = draw)

private const val FAR = 10_000f

private fun DrawScope.blazingSparkles(seconds: Float) {
    sparkle(Offset(-20f, 120f), 17f, twinkle(seconds, 0f))
    sparkle(Offset(18f, 84f), 9f, twinkle(seconds, 0.4f))
    sparkle(Offset(232f, 110f), 14f, twinkle(seconds, 0.7f))
    sparkle(Offset(250f, 170f), 8f, twinkle(seconds, 0.2f))
}

/** Body heights relative to Blazing are exact and come from docs/mo.md. */
private enum class Figure(
    val relativeHeight: Float,
    val top: Float,
    val halfWidth: Float,
    val breathPeriod: Float,
    val breathDepth: Float,
) {
    Blazing(1.00f, 82f, 125f, 1.9f, 0.022f),
    Steady(0.85f, 82f, 110f, 3.2f, 0.014f),
    Dimming(0.60f, 150f, 108f, 4.2f, 0.010f),
    Cold(0.50f, 157f, 100f, 5.0f, 0.006f),
    Husk(0.40f, 206f, 90f, 1f, 0f),
    ;

    /** From his design space to his box: each body is drawn as tall as its share of Blazing's. */
    val scale: Float get() = relativeHeight * BLAZING_BODY / (FEET - top)

    companion object {
        fun of(state: MoState): Figure = when (state) {
            MoState.Blazing -> Blazing
            MoState.Steady -> Steady
            MoState.Dimming -> Dimming
            MoState.Cold -> Cold
            MoState.Husk -> Husk
        }
    }
}

// ------------------------------------------------------------------------------------ the states

private fun DrawScope.drawFlame(look: MoLook, seconds: Float, blazing: Boolean) {
    val sway = wave(seconds, if (blazing) 1.7f else 2.9f) * (if (blazing) 9f else 6f) +
        wave(seconds, 0.83f) * 1.5f
    val lean = swayAbove(pivot = 250f, top = 82f, amount = sway)
    val outer = OUTER_FLAME.toPath(lean)
    val care = look.teethNeglect
    val strong = MoThrivingLook.StrongArms in look.thriving

    when {
        strong -> flexedArms(pump = 1f + wave(seconds, 1.4f) * 0.08f)
        blazing -> {
            val raise = wave(seconds, 0.95f) * 7f
            limb(Offset(24f, 322f), Offset(-8f + raise, 262f), MoFlameOuter, 21f)
            limb(Offset(206f, 322f), Offset(238f - raise, 262f), MoFlameOuter, 21f)
        }
        else -> {
            limb(Offset(22f, 336f), Offset(18f, 368f), MoFlameOuter, 21f)
            limb(Offset(208f, 336f), Offset(212f, 368f), MoFlameOuter, 21f)
        }
    }
    // He kicks a leg up for joy, unless something lies where the foot would land.
    if (blazing && look.props.size < 2) {
        leg(76f, 85f, MoFlameOuter)
        val kick = wave(seconds, 0.95f) * 4f
        limb(Offset(140f, 384f), Offset(176f, 400f - kick), MoFlameOuter, 28f)
        withTransform({ rotate(25f, Offset(182f, 398f - kick)) }) {
            oval(Offset(182f, 398f - kick), 14f, 19f, MoFlameOuter)
        }
    } else {
        leg(76f, 85f, MoFlameOuter)
        leg(126f, 147f, MoFlameOuter)
    }

    body(outer, MoFlameOuter, MoFlameLedge, lift = 11f)
    drawPath(MIDDLE_FLAME.toPath(swayAbove(250f, 138f, sway * 0.8f)), MoFlameMiddle)
    drawPath(BELLY_FLAME.toPath(swayAbove(262f, 185f, sway * 0.55f)), MoFlameBelly)
    if (strong) sweatband(outer)
    curve(Offset(69f, 151f), Offset(78f, 126f), Offset(92f, 114f), MoGloss, 9f, lean)
    curve(Offset(31f, 260f), Offset(38f, 236f), Offset(51f, 219f), MoGloss.copy(alpha = 0.85f), 9f)

    val eyeY = if (blazing) 280f else 284f
    val gaze = Offset(3f, if (blazing) -2f else 0f)
    val mirrored = Offset(-gaze.x, gaze.y)
    eye(Offset(71f, eyeY), gaze, MoInk, blink(seconds))
    eye(Offset(155f, eyeY), mirrored, MoInk, blink(seconds))
    if (MoThrivingLook.Glasses in look.thriving) glasses(eyeY)

    // His glasses reach lower than his eyes do, so his mouth makes room for the rims.
    val underTheRims = if (MoThrivingLook.Glasses in look.thriving) 6f else 0f
    withTransform({ translate(0f, underTheRims) }) {
        when {
            care != null -> teeth(Offset(113f, 326f), care, seconds)
            MoThrivingLook.BrightSmile in look.thriving -> brightSmile(seconds)
            blazing -> openMouth()
            else -> curve(Offset(96f, 313f), Offset(113f, 328f), Offset(130f, 313f), MoInk, 5.5f)
        }
    }

    if (blazing && !look.frozen) blazingSparkles(seconds)
}

private fun DrawScope.drawDimming(look: MoLook, seconds: Float) {
    val sway = wave(seconds, 3.6f) * 4f + wave(seconds, 0.6f) * 1.2f
    limb(Offset(22f, 340f), Offset(18f, 366f), MoFlameOuter, 21f)
    limb(Offset(208f, 340f), Offset(212f, 366f), MoFlameOuter, 21f)
    drawCircle(MoSlateLedge, 10.5f, Offset(17.5f, 369f))
    drawCircle(MoSlateLedge, 10.5f, Offset(212.5f, 369f))
    leg(76f, 85f, MoSlateLedge, top = 384f)
    leg(126f, 147f, MoSlateLedge, top = 384f)

    // The cold rises from the ground up, so his base is already slate while he still burns.
    body(LOW_OUTER.toPath(swayAbove(260f, 150f, sway)), MoFlameOuter, MoSlateLedge, lift = 13f)
    drawPath(LOW_MIDDLE.toPath(swayAbove(262f, 188f, sway * 0.8f)), MoFlameMiddle)
    drawPath(LOW_BELLY.toPath(swayAbove(270f, 230f, sway * 0.5f)), MoFlameBelly)
    curve(Offset(40f, 262f), Offset(50f, 236f), Offset(66f, 218f), MoGloss.copy(alpha = 0.85f), 8f)

    eye(Offset(74f, 304f), Offset(2f, -5f), MoInk, blink(seconds), radius = 25f)
    eye(Offset(152f, 304f), Offset(-2f, -5f), MoInk, blink(seconds), radius = 25f)
    brows(268f, 74f, 152f, MoInk)
    look.teethNeglect?.let { teeth(Offset(113f, 346f), it, seconds, width = 46f) } ?: frown(Offset(113f, 338f), MoInk)
}

private fun DrawScope.drawCold(look: MoLook, seconds: Float) {
    leg(80f, 88f, MoSlateLedge, top = 388f)
    leg(122f, 142f, MoSlateLedge, top = 388f)
    body(DROPLET.toPath(), MoSlate, MoSlateLedge, lift = 11f)
    curve(Offset(58f, 270f), Offset(66f, 248f), Offset(80f, 232f), MoSlateGloss, 8f)

    for ((x, side) in listOf(78f to -1f, 150f to 1f)) {
        eye(Offset(x, 296f), Offset(0f, -4f), MoColdInk, blink(seconds), radius = 25f)
        tear(Offset(x + side * 20f, 312f), fall = phase(seconds + x, 3.4f), side = side)
    }
    brows(262f, 78f, 150f, MoColdInk)
    // Between his eyes and the core he holds there is room for a small mouth only.
    look.teethNeglect?.let { teeth(Offset(114f, 325f), it, seconds, width = 34f) } ?: frown(Offset(114f, 328f), MoColdInk)

    // His fire is now a core he has to hold. It never goes out while he is Cold.
    val glow = 1f + wave(seconds, 2.4f) * 0.07f
    drawCircle(MoFlameOuter, 25f * glow, Offset(115f, 362f))
    drawCircle(MoSpark, 17f * glow, Offset(115f, 362f))
    drawCircle(MoFlameBelly, 8f * glow, Offset(115f, 364f))
    curve(Offset(32f, 330f), Offset(50f, 372f), Offset(96f, 372f), MoSlateLedge, 19f)
    curve(Offset(198f, 330f), Offset(180f, 372f), Offset(134f, 372f), MoSlateLedge, 19f)

    if (!look.frozen) smoke(Offset(166f, 150f), phase(seconds, 3.8f))
    for ((x, r) in ASH_COLD) oval(Offset(x, 422f), r, r * 0.6f, MoSlate)
}

private fun DrawScope.drawHusk(seconds: Float, ground: Color) {
    curve(Offset(44f, 350f), Offset(36f, 380f), Offset(44f, 400f), MoHuskLedge, 17f)
    curve(Offset(190f, 350f), Offset(198f, 380f), Offset(190f, 400f), MoHuskLedge, 17f)
    body(HUSK_SHELL.toPath(), MoHusk, MoHuskLedge, lift = 9f)
    oval(Offset(86f, 412f), 20f, 13f, MoHuskLedge)
    oval(Offset(146f, 412f), 20f, 13f, MoHuskLedge)

    polyline(MoHuskCrack, 3.5f, listOf(Offset(104f, 222f), Offset(112f, 238f), Offset(103f, 248f), Offset(110f, 262f)))
    polyline(MoHuskCrack, 3.5f, listOf(Offset(170f, 300f), Offset(160f, 310f), Offset(166f, 322f)))
    curve(Offset(66f, 312f), Offset(80f, 324f), Offset(94f, 312f), MoHuskCrack, 5f)
    curve(Offset(136f, 312f), Offset(150f, 324f), Offset(164f, 312f), MoHuskCrack, 5f)
    limb(Offset(108f, 336f), Offset(122f, 336f), MoHuskCrack, 4.5f)

    // On a transparent ground, as in a notification icon, painting the ground would paint nothing:
    // there the hollow is cut out of the shell instead.
    drawPath(HOLLOW.toPath(), ground, blendMode = if (ground.alpha == 0f) BlendMode.Clear else BlendMode.SrcOver)
    // The one thing that still moves: the spark that says he can be rekindled.
    val pulse = 0.55f + 0.45f * (0.5f + 0.5f * wave(seconds, 3.1f))
    drawCircle(MoSpark.copy(alpha = pulse), 3f + pulse, Offset(115f, 390f))
    for ((x, r) in ASH_HUSK) oval(Offset(x, 423f), r, r * 0.55f, MoHusk)
}

// ------------------------------------------------------------------------------------------ anger

/** Where a burning body keeps its parts, so anger can be drawn on the tall flame and the low one alike. */
private class FlameBody(
    val outer: Outline,
    val middle: Outline,
    val belly: Outline,
    val top: Float,
    val pivot: Float,
    val eyeY: Float,
    val leftEye: Float,
    val rightEye: Float,
    val eyeRadius: Float,
    val mouth: Offset,
    val shoulderY: Float,
    val legTop: Float,
)

/**
 * Something is overdue and he still has the fire to mind. Anger is his fire turned up: he goes red,
 * his flame shoots up and flickers fast, he shakes his fists and grits his teeth. It is a tantrum,
 * not a threat, and it is over the moment the task is done.
 */
private fun DrawScope.drawAngry(body: FlameBody, look: MoLook, seconds: Float) {
    val surge = 0.5f + 0.5f * wave(seconds, 0.62f)
    val sway = wave(seconds, 0.47f) * 7f + wave(seconds, 0.19f) * 2.5f
    val flare: (Offset) -> Offset = { point ->
        val height = ((body.pivot - point.y) / (body.pivot - body.top)).coerceIn(0f, 1.2f)
        Offset(point.x + sway * height * height, point.y - (16f + 16f * surge) * height * height)
    }
    val shake = wave(seconds, 0.085f) * 1.8f

    withTransform({ translate(shake, 0f) }) {
        for (side in floatArrayOf(-1f, 1f)) {
            val tremble = wave(seconds + side, 0.11f) * 3f
            val shoulder = Offset(CENTRE + side * 92f, body.shoulderY)
            val elbow = Offset(CENTRE + side * 130f, body.shoulderY - 6f)
            val fist = Offset(CENTRE + side * 126f + tremble, body.shoulderY - 60f)
            limb(shoulder, elbow, MoRageOuter, 22f)
            limb(elbow, fist, MoRageOuter, 20f)
            drawCircle(MoRageOuter, 15f, fist)
        }
        leg(76f, 85f, MoRageOuter, top = body.legTop)
        leg(126f, 147f, MoRageOuter, top = body.legTop)

        body(body.outer.toPath(flare), MoRageOuter, MoRageLedge, lift = 11f)
        drawPath(body.middle.toPath(flare), MoRageMiddle)
        drawPath(body.belly.toPath { point -> flare(point).let { Offset(it.x, (it.y + point.y) / 2f) } }, MoRageBelly)

        for ((x, side) in listOf(body.leftEye to -1f, body.rightEye to 1f)) {
            val centre = Offset(x, body.eyeY)
            val r = body.eyeRadius
            // The lid cuts the eye along a line that drops toward the middle of his face: a glare.
            val outerY = centre.y - r * 0.95f
            val innerY = centre.y - r * 0.12f
            val outerX = x + side * (r + 5f)
            val innerX = x - side * (r + 5f)
            val glare = Path().apply {
                moveTo(outerX, outerY)
                lineTo(innerX, innerY)
                lineTo(innerX, centre.y + r + 5f)
                lineTo(outerX, centre.y + r + 5f)
                close()
            }
            clipPath(glare) { eye(centre, Offset(-side * 4f, 3f), MoInk, open = 1f, radius = r, iris = 0.56f) }
            limb(Offset(outerX, outerY - 8f), Offset(innerX + side * 4f, innerY - 8f), MoInk, 9f)
        }
        look.teethNeglect?.let { teeth(body.mouth, it, seconds) } ?: grittedTeeth(body.mouth)

        angerMark(Offset(CENTRE + 74f, body.top + 96f), 0.85f + 0.15f * surge)
    }

    // Tongues of fire spit off the tip and burn away, over and over.
    for (index in 0..2) {
        val rise = phase(seconds + index * 0.43f, 1.3f)
        val at = Offset(CENTRE + (index - 1) * 46f + sway, body.top + 30f - rise * 74f - index % 2 * 12f)
        val size = 13f * (1f - rise * 0.6f)
        val tongue = Path().apply {
            moveTo(at.x, at.y - size * 1.6f)
            cubicTo(at.x + size, at.y - size * 0.2f, at.x + size * 0.8f, at.y + size, at.x, at.y + size)
            cubicTo(at.x - size * 0.8f, at.y + size, at.x - size, at.y - size * 0.2f, at.x, at.y - size * 1.6f)
            close()
        }
        drawPath(tongue, (if (index == 1) MoRageBelly else MoRageMiddle).copy(alpha = 1f - rise))
    }
}

/**
 * Clenched teeth. They fill his mouth and his lips cut them off, so they sit in it and not on it:
 * a block of teeth with its own corners, laid inside a mouth that has none, reads as painted on.
 */
internal fun DrawScope.grittedTeeth(centre: Offset) {
    val width = 54f
    val height = 24f
    val left = centre.x - width / 2f
    val top = centre.y - height / 2f
    val mouth = Path().apply {
        // Corners pulled down: the opposite of a smile.
        moveTo(left, top + height)
        cubicTo(left - 2f, top + 2f, left + width * 0.2f, top, centre.x, top)
        cubicTo(left + width * 0.8f, top, left + width + 2f, top + 2f, left + width, top + height)
        close()
    }
    // The mouth's own edge is this fill's: the edge of a clip is not smoothed on every phone.
    drawPath(mouth, MoInk)
    val gap = 2.8f
    clipPath(mouth) {
        drawPath(mouth, MoFoam)
        // Where the two rows meet. It sags toward the corners as his upper lip does, so the row
        // turns away from us there and does not end flat against his cheek.
        curve(Offset(left, top + 16f), Offset(centre.x, top + 8f), Offset(left + width, top + 16f), MoInk, gap)
        for (column in -1..1) {
            // Two wide teeth in front and a narrow one either side, as of a row that goes back
            // there. The gaps fan out downward, as the sides of his mouth do.
            val x = centre.x + column * 15f
            limb(Offset(x - column * 2f, top), Offset(x + column * 2f, top + height), MoInk, gap)
        }
        // His lips are the inner half of this line, which keeps them one width all the way round.
        drawPath(mouth, MoInk, style = Stroke(width = 7f))
    }
}

/** The four little arcs that mean "fuming" in every cartoon. */
internal fun DrawScope.angerMark(centre: Offset, size: Float) {
    val reach = 15f * size
    for ((dx, dy) in listOf(-1f to -1f, 1f to -1f, -1f to 1f, 1f to 1f)) {
        val corner = centre + Offset(dx * reach * 0.42f, dy * reach * 0.42f)
        curve(
            from = corner + Offset(dx * reach, 0f),
            control = corner + Offset(dx * reach * 0.12f, dy * reach * 0.12f),
            to = corner + Offset(0f, dy * reach),
            colour = MoRageLedge,
            width = 5.5f * size,
        )
    }
}

// --------------------------------------------------------------------------------------- symptoms

/**
 * Neglect with the teeth motif shows in his mouth: stains first, then rot and a gap, and in the
 * end a few crooked stumps and a smell. The teeth are clipped by the mouth itself, so they grow out
 * of his lips instead of sitting on top of his face.
 */
internal fun DrawScope.teeth(centre: Offset, neglect: MoNeglect, seconds: Float, width: Float = 56f) {
    val height = width * 0.56f
    val left = centre.x - width / 2f
    val top = centre.y - height / 2f
    val mouth = Path().apply {
        // A wince: flat on top, sagging at the corners.
        moveTo(left, top + height * 0.30f)
        cubicTo(left + width * 0.2f, top - height * 0.06f, left + width * 0.8f, top - height * 0.06f, left + width, top + height * 0.30f)
        cubicTo(left + width * 1.02f, top + height * 0.82f, left + width * 0.82f, top + height, centre.x, top + height)
        cubicTo(left + width * 0.18f, top + height, left - width * 0.02f, top + height * 0.82f, left, top + height * 0.30f)
        close()
    }
    drawPath(mouth, MoInk)

    // Upper and lower row, outside in. Null is a tooth that is gone.
    val (upper, lower) = when (neglect) {
        MoNeglect.Mild -> listOf(MoTooth, MoToothStained, MoTooth, MoTooth, MoToothStained) to
            listOf(MoToothStained, MoTooth, MoTooth, MoToothStained, MoTooth)
        MoNeglect.Severe -> listOf(MoToothStained, null, MoToothRotten, MoToothStained, MoToothStained) to
            listOf(MoToothRotten, MoToothStained, null, MoToothStained, MoToothRotten)
        MoNeglect.Dire -> listOf(null, MoToothRotten, null, null, MoToothStained) to
            listOf(null, null, MoToothRotten, null, null)
    }
    val tooth = width / 5f
    clipPath(mouth) {
        upper.forEachIndexed { index, colour ->
            if (colour == null) return@forEachIndexed
            // Rotten teeth stand crooked and short.
            val short = if (colour == MoToothRotten) 0.72f else 1f
            drawRoundRect(
                colour,
                Offset(left + index * tooth + 1f, top - 4f),
                Size(tooth - 2f, (height * 0.50f + 4f) * short),
                CornerRadius(3.5f),
            )
        }
        lower.forEachIndexed { index, colour ->
            if (colour == null) return@forEachIndexed
            val short = if (colour == MoToothRotten) 0.7f else 1f
            val toothHeight = height * 0.40f * short
            drawRoundRect(
                colour,
                Offset(left + index * tooth + 1f, top + height - toothHeight),
                Size(tooth - 2f, toothHeight + 4f),
                CornerRadius(3.5f),
            )
        }
    }
    if (neglect == MoNeglect.Dire) {
        // The smell drifts off past his cheek. Straight up from his mouth is his eye.
        val reach = width * 0.3f
        for (index in 0..1) {
            val rise = phase(seconds + index * 1.3f, 2.6f)
            val x = centre.x + width * (0.62f + index * 0.2f + rise * 0.5f)
            val y = centre.y + reach - rise * width * 0.18f
            val stink = Path().apply {
                moveTo(x, y)
                cubicTo(x + reach / 3f, y - reach / 3f, x - reach / 3f, y - reach * 2f / 3f, x, y - reach)
            }
            drawPath(stink, MoStink.copy(alpha = 0.9f * (1f - rise)), style = Stroke(width = width / 14f, cap = StrokeCap.Round))
        }
    }
}

/**
 * What he is missing lies beside him, unused and in the colour of things gone cold. Only the two
 * most neglected show, so a bad week never turns him into clutter.
 */
private fun DrawScope.drawProps(look: MoLook, halfWidth: Float) {
    look.props.forEachIndexed { index, (prop, neglect) ->
        val side = if (index == 0) -1f else 1f
        val webbed = neglect >= MoNeglect.Severe
        // The widget cuts his picture at his box, so a thing lies as far out as it still fits
        // whole, web and all.
        val outer = max(prop.halfWidth, if (webbed) PROP_WEB_REACH else 0f) * PROP_SCALE
        val x = side * min(halfWidth + 40f, BOX_WIDTH / 2f - PROP_EDGE - outer)
        withTransform({
            translate(x, 0f)
            scale(PROP_SCALE, PROP_SCALE, Offset.Zero)
        }) {
            prop(prop)
            // The web hangs on the side away from him, where it cannot catch on his mitten.
            if (webbed) withTransform({ scale(-side, 1f, Offset.Zero) }) { cobweb(PROP_WEB_REACH) }
        }
    }
    val lost = when (look.teethNeglect) {
        MoNeglect.Severe -> 1
        MoNeglect.Dire -> 2
        else -> 0
    }
    repeat(lost) { index ->
        val x = halfWidth * (0.5f + index * 0.22f)
        withTransform({ rotate(-28f + index * 70f, Offset(x, -6f)) }) {
            drawRoundRect(MoTooth, Offset(x - 6f, -13f), Size(12f, 13f), CornerRadius(3.5f))
        }
    }
}

/** How far a prop reaches to either side of its middle, in its own units. */
private val MoProp.halfWidth: Float
    get() = when (this) {
        MoProp.Weights -> 32f
        // The bottle lies on its side, and the bowl's rim ends in a round cap past the bowl itself.
        MoProp.PillBottle, MoProp.EmptyBowl -> 27f
        else -> 25f
    }

/** Props are drawn around x = 0 with the floor at y = 0, about 50 wide. */
private fun DrawScope.prop(prop: MoProp) {
    when (prop) {
        MoProp.Book -> {
            drawRoundRect(MoSlateLedge, Offset(-24f, -16f), Size(48f, 16f), CornerRadius(3f))
            drawRoundRect(MoSlateGloss, Offset(-20f, -13f), Size(42f, 10f), CornerRadius(2f))
            drawRoundRect(MoSlate, Offset(-24f, -19f), Size(48f, 6f), CornerRadius(3f))
        }
        MoProp.Weights -> {
            limb(Offset(-16f, -12f), Offset(16f, -12f), MoSlateGloss, 5f)
            for (x in floatArrayOf(-20f, 20f)) {
                drawRoundRect(MoSlateLedge, Offset(x - 5f, -24f), Size(10f, 24f), CornerRadius(3.5f))
            }
            for (x in floatArrayOf(-28f, 28f)) {
                drawRoundRect(MoSlate, Offset(x - 4f, -20f), Size(8f, 16f), CornerRadius(3f))
            }
        }
        MoProp.PillBottle -> withTransform({ rotate(-78f, Offset(0f, -10f)) }) {
            drawRoundRect(MoSlate, Offset(-10f, -26f), Size(20f, 30f), CornerRadius(5f))
            drawRoundRect(MoSlateGloss, Offset(-10f, -16f), Size(20f, 9f), CornerRadius(1f))
            drawRoundRect(MoSlateLedge, Offset(-12f, -33f), Size(24f, 9f), CornerRadius(3f))
        }
        MoProp.EmptyBowl -> {
            val bowl = Path().apply {
                moveTo(-24f, -16f)
                cubicTo(-22f, 4f, 22f, 4f, 24f, -16f)
                close()
            }
            drawPath(bowl, MoSlate)
            limb(Offset(-25f, -16f), Offset(25f, -16f), MoSlateGloss, 4f)
        }
        MoProp.Scale -> {
            drawRoundRect(MoSlateLedge, Offset(-25f, -9f), Size(50f, 9f), CornerRadius(4f))
            drawRoundRect(MoSlate, Offset(-25f, -13f), Size(50f, 9f), CornerRadius(4f))
            drawRoundRect(MoSlateGloss, Offset(-9f, -12f), Size(18f, 5f), CornerRadius(2f))
        }
        MoProp.None, MoProp.LostTeeth -> Unit
    }
}

/**
 * A small web fanning out from the top corner of whatever has been left lying, as far as [reach]
 * to the left of the thing's middle.
 */
internal fun DrawScope.cobweb(reach: Float = 46f) {
    val anchor = Offset(-22f, -17f)
    val web = MoSlateGloss.copy(alpha = 0.85f)
    val wide = (reach + anchor.x) / 24f
    val tips = listOf(Offset(-24f, 3f), Offset(-20f, -15f), Offset(-6f, -25f), Offset(12f, -23f)).map { anchor + Offset(if (it.x < 0f) it.x * wide else it.x, it.y) }
    tips.forEach { limb(anchor, it, web, 1.3f) }
    for (reach in floatArrayOf(0.5f, 0.85f)) {
        tips.zipWithNext { from, to ->
            val a = anchor + (from - anchor) * reach
            val b = anchor + (to - anchor) * reach
            curve(a, anchor + ((a + b) / 2f - anchor) * 0.86f, b, web, 1.3f)
        }
    }
}

// ------------------------------------------------------------------------------ what he is up to

private fun DrawScope.flexedArms(pump: Float) {
    for (side in floatArrayOf(-1f, 1f)) {
        val shoulder = Offset(CENTRE + side * 94f, 326f)
        val elbow = Offset(CENTRE + side * 142f, 312f)
        val fist = Offset(CENTRE + side * 132f, 250f)
        limb(shoulder, elbow, MoFlameOuter, 24f)
        limb(elbow, fist, MoFlameOuter, 21f)
        val bicep = Offset(CENTRE + side * 121f, 303f)
        drawCircle(MoFlameOuter, 23f * pump, bicep)
        curve(bicep + Offset(-side * 12f, -11f), bicep + Offset(0f, -19f), bicep + Offset(side * 11f, -12f), MoGloss.copy(alpha = 0.85f), 4.5f)
        drawCircle(MoFlameOuter, 15f, fist)
    }
}

private fun DrawScope.sweatband(outline: Path) {
    clipPath(outline) {
        drawRect(MoGear, Offset(0f, 222f), Size(230f, 17f))
        drawRect(MoFoam, Offset(0f, 228.5f), Size(230f, 4f))
    }
}

private fun DrawScope.glasses(eyeY: Float) {
    for (x in floatArrayOf(71f, 155f)) {
        drawCircle(MoInk, 31f, Offset(x, eyeY), style = Stroke(width = 6f))
    }
    curve(Offset(102f, eyeY - 4f), Offset(113f, eyeY - 12f), Offset(124f, eyeY - 4f), MoInk, 6f)
}

/** A thriving teeth motif: a wide smile full of white teeth, with a glint that comes and goes. */
private fun DrawScope.brightSmile(seconds: Float) {
    val smile = Path().apply {
        moveTo(88f, 316f)
        cubicTo(104f, 313f, 122f, 313f, 138f, 316f)
        cubicTo(136f, 340f, 126f, 348f, 113f, 348f)
        cubicTo(100f, 348f, 90f, 340f, 88f, 316f)
        close()
    }
    toothyMouth(smile, teethDownTo = 327f, MoInk)
    // The glint sits at the end of his teeth, below his eye: a mouth this wide ends right under it.
    sparkle(Offset(142f, 325f), 10f, 0.45f + 0.55f * max(wave(seconds, 2.4f), 0f), MoFoam)
}

private fun iceBounds(figure: Figure, look: MoLook): Rect {
    val bodyHalf = figure.halfWidth * 0.98f
    val resting = if (look.state >= MoState.Dimming) max(bodyHalf, RESTING_MITTENS) else bodyHalf
    val half = when {
        look.playingScene != null || look.state < MoState.Dimming -> resting
        look.angry -> 148f
        look.state >= MoState.Steady && MoThrivingLook.StrongArms in look.thriving -> 157f
        look.state == MoState.Blazing -> 138f
        else -> resting
    }
    return Rect(CENTRE - half, figure.top - 34f, CENTRE + half, FEET + 6f)
}

/** He sits in a block of ice and only the ice moves: a glint that slides across it now and then. */
private fun DrawScope.drawIceBlock(ice: Rect, seconds: Float) {
    val top = ice.top
    val block = Path().apply {
        addRoundRect(RoundRect(ice, CornerRadius(34f)))
    }
    drawPath(block, MoIceLedge.copy(alpha = 0.34f))
    clipPath(block) {
        withTransform({ translate(0f, -14f) }) { drawPath(block, MoIce.copy(alpha = 0.30f)) }
        // The bands slant, so they reach sideways past their own width: they start and end that
        // much further out, or the glint would appear inside the ice and vanish inside it. A third
        // of the way across at rest, so the still picture has its glint.
        val reach = sin(GLINT_SLANT * PI.toFloat() / 180f) * (FEET - top + 80f) / 2f
        val glint = phase(seconds + 1.2f, 3.6f)
        val x = ice.left - GLINT_WIDTH - reach + glint * (ice.width + GLINT_WIDTH + reach * 2f)
        withTransform({ rotate(GLINT_SLANT, Offset(x, (top + FEET) / 2f)) }) {
            drawRect(MoIceShine.copy(alpha = 0.29f), Offset(x, top - 40f), Size(26f, FEET - top + 80f))
            drawRect(MoIceShine.copy(alpha = 0.22f), Offset(x + 38f, top - 40f), Size(GLINT_WIDTH - 38f, FEET - top + 80f))
        }
    }
    limb(Offset(ice.left + 22f, top + 62f), Offset(ice.left + 22f, top + 26f), MoIceShine, 8f)
    limb(Offset(ice.left + 22f, top + 26f), Offset(ice.left + 56f, top + 26f), MoIceShine, 8f)
    limb(Offset(ice.right - 22f, FEET - 48f), Offset(ice.right - 22f, FEET - 20f), MoIceDeep, 7f)
    limb(Offset(ice.right - 22f, FEET - 20f), Offset(ice.right - 50f, FEET - 20f), MoIceDeep, 7f)
}

// Half the width of a burning body with its arms down, mittens included, and a little ice round them.
private const val RESTING_MITTENS = 113f
private const val GLINT_SLANT = 18f
private const val GLINT_WIDTH = 47f


// ------------------------------------------------------------------------------------------ parts

internal fun DrawScope.body(outline: Path, fill: Color, ledge: Color, lift: Float) {
    drawPath(outline, ledge)
    clipPath(outline) {
        withTransform({ translate(0f, -lift) }) { drawPath(outline, fill) }
    }
}

private fun DrawScope.eye(
    centre: Offset,
    gaze: Offset,
    ink: Color,
    open: Float,
    radius: Float = 27f,
    iris: Float = 0.76f,
) {
    withTransform({ scale(1f, max(open, 0.08f), centre) }) {
        drawCircle(MoEyeWhite, radius, centre)
        val pupil = centre + gaze
        drawCircle(ink, radius * iris, pupil)
        drawCircle(MoEyeWhite, radius * iris * 0.32f, pupil + Offset(-radius * iris * 0.34f, -radius * iris * 0.43f))
        drawCircle(MoEyeWhite, radius * iris * 0.18f, pupil + Offset(radius * iris * 0.43f, radius * iris * 0.43f))
    }
}

private fun DrawScope.openMouth() {
    val mouth = Path().apply {
        moveTo(92f, 310f)
        cubicTo(106f, 307f, 120f, 307f, 134f, 310f)
        cubicTo(132f, 331f, 126f, 342f, 113f, 342f)
        cubicTo(100f, 342f, 94f, 331f, 92f, 310f)
        close()
    }
    drawPath(mouth, MoInk)
    clipPath(mouth) { oval(Offset(113f, 340f), 15f, 10f, MoTongue) }
}

private fun DrawScope.brows(y: Float, left: Float, right: Float, ink: Color) {
    curve(Offset(left - 16f, y + 4f), Offset(left - 4f, y - 8f), Offset(left + 12f, y - 8f), ink, 5.5f)
    curve(Offset(right + 16f, y + 4f), Offset(right + 4f, y - 8f), Offset(right - 12f, y - 8f), ink, 5.5f)
}

private fun DrawScope.frown(centre: Offset, ink: Color) =
    curve(centre + Offset(-9f, 5f), centre + Offset(0f, -5f), centre + Offset(9f, 5f), ink, 5f)

private fun DrawScope.leg(x: Float, footX: Float, colour: Color, top: Float = 378f) {
    drawRoundRect(colour, Offset(x, top), Size(30f, 414f - top), CornerRadius(8f))
    oval(Offset(footX, 413f), 19f, 12f, colour)
}

internal fun DrawScope.sparkle(centre: Offset, radius: Float, size: Float, colour: Color = MoSpark) {
    val r = radius * size
    val star = Path().apply {
        var from = Offset(centre.x, centre.y - r)
        moveTo(from.x, from.y)
        for ((dx, dy) in listOf(1f to 0f, 0f to 1f, -1f to 0f, 0f to -1f)) {
            val to = Offset(centre.x + dx * r, centre.y + dy * r)
            quadTo(this, from, centre, to)
            from = to
        }
        close()
    }
    drawPath(star, colour)
}

/** A tear at [top]. It leans away from his nose: [side] is -1 under his left eye and 1 under his right. */
internal fun DrawScope.tear(top: Offset, fall: Float, side: Float = 1f) {
    val drop = Path().apply {
        moveTo(0f, 0f)
        cubicTo(-5f, 8f, -6f, 16f, -1f, 20f)
        cubicTo(6f, 25f, 12f, 20f, 11f, 13f)
        cubicTo(10f, 7f, 5f, 3f, 0f, 0f)
        close()
    }
    // A tear wells up, rolls down a little, dwindles and is gone, over and over. It keeps its
    // colour until the last: half see-through it is olive on his belly and tan on his flame, a
    // smudge on his cheek and no longer water.
    val gone = ease((fall - 0.8f) / 0.2f)
    val size = ease(fall / 0.12f) * (1f - 0.55f * ease((fall - 0.5f) / 0.5f))
    withTransform({
        translate(top.x, top.y + fall * 14f)
        scale(side * size, size, Offset.Zero)
    }) {
        drawPath(drop, MoTear.copy(alpha = 1f - gone))
    }
}

internal fun DrawScope.smoke(base: Offset, rise: Float, strength: Float = 1f) {
    val wisp = Path().apply {
        moveTo(base.x, base.y)
        cubicTo(base.x - 12f, base.y - 14f, base.x + 12f, base.y - 28f, base.x, base.y - 42f)
        cubicTo(base.x - 8f, base.y - 50f, base.x + 4f, base.y - 54f, base.x, base.y - 58f)
    }
    val drift = (rise + 0.25f).mod(1f)
    withTransform({ translate(0f, -drift * 16f) }) {
        drawPath(
            wisp,
            MoSmoke.copy(alpha = 0.8f * strength * pulse(drift, 1f)),
            style = Stroke(width = 6f, cap = StrokeCap.Round),
        )
    }
}

internal fun DrawScope.limb(from: Offset, to: Offset, colour: Color, width: Float) =
    drawLine(colour, from, to, strokeWidth = width, cap = StrokeCap.Round)

internal fun DrawScope.oval(centre: Offset, rx: Float, ry: Float, colour: Color) =
    drawOval(colour, Offset(centre.x - rx, centre.y - ry), Size(rx * 2f, ry * 2f))

internal fun DrawScope.polyline(colour: Color, width: Float, points: List<Offset>) {
    val path = Path().apply {
        moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
    }
    drawPath(path, colour, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** A quadratic stroke; [warp] lets a mark ride along with the part of the flame it sits on. */
internal fun DrawScope.curve(
    from: Offset,
    control: Offset,
    to: Offset,
    colour: Color,
    width: Float,
    warp: (Offset) -> Offset = { it },
) {
    val path = Path().apply {
        val start = warp(from)
        moveTo(start.x, start.y)
        quadTo(this, start, warp(control), warp(to))
    }
    drawPath(path, colour, style = Stroke(width = width, cap = StrokeCap.Round))
}

/** A quadratic segment as a cubic, which every Compose version draws the same way. */
internal fun quadTo(path: Path, from: Offset, control: Offset, to: Offset) {
    path.cubicTo(
        from.x + (control.x - from.x) * 2f / 3f, from.y + (control.y - from.y) * 2f / 3f,
        to.x + (control.x - to.x) * 2f / 3f, to.y + (control.y - to.y) * 2f / 3f,
        to.x, to.y,
    )
}

// ----------------------------------------------------------------------------------------- motion

/** A sine between -1 and 1 that is 0 at rest, so the still pose is simply [seconds] = 0. */
internal fun wave(seconds: Float, period: Float): Float = sin(seconds / period * 2f * PI.toFloat())

internal fun phase(seconds: Float, period: Float): Float = (seconds / period).mod(1f)

internal fun twinkle(seconds: Float, offset: Float): Float = 0.75f + 0.25f * wave(seconds + offset * 1.9f, 1.9f)

/** Open most of the time, shut for a moment every few seconds. */
internal fun blink(seconds: Float): Float {
    val t = phase(seconds + 1.2f, 4.6f) * 4.6f
    return if (t < 0.16f) abs(t - 0.08f) / 0.08f else 1f
}

/** Leans everything above [pivot] sideways, more the higher it is, so a flame sways from its base. */
internal fun swayAbove(pivot: Float, top: Float, amount: Float): (Offset) -> Offset = { point ->
    val height = ((pivot - point.y) / (pivot - top)).coerceIn(0f, 1.2f)
    Offset(point.x + amount * height * height, point.y)
}

// ----------------------------------------------------------------------------------------- shapes

/** A closed outline of cubic segments: a start point, then control, control, end for each segment. */
internal class Outline(private vararg val numbers: Float) {
    fun toPath(warp: (Offset) -> Offset = { it }): Path = Path().apply {
        val points = List(numbers.size / 2) { warp(Offset(numbers[it * 2], numbers[it * 2 + 1])) }
        moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size step 3) {
            cubicTo(points[i].x, points[i].y, points[i + 1].x, points[i + 1].y, points[i + 2].x, points[i + 2].y)
        }
        close()
    }
}

private const val K = 0.5523f

/** The rounded bottom shared by every body: half an ellipse from the right edge to the left one. */
internal fun bottom(right: Float, left: Float, y: Float, depth: Float): FloatArray {
    val centre = (left + right) / 2f
    val rx = (right - left) / 2f
    return floatArrayOf(
        right, y + K * depth, centre + K * rx, y + depth, centre, y + depth,
        centre - K * rx, y + depth, left, y + K * depth, left, y,
    )
}

internal val OUTER_FLAME = Outline(
    8f, 287f,
    8f, 240f, 20f, 200f, 34f, 176f,
    34f, 162f, 48f, 158f, 50f, 172f,
    51f, 180f, 53f, 186f, 56f, 186f,
    64f, 140f, 84f, 104f, 110f, 89f,
    122f, 82f, 134f, 88f, 132f, 102f,
    130f, 128f, 146f, 152f, 161f, 170f,
    164f, 160f, 172f, 150f, 182f, 150f,
    192f, 150f, 192f, 164f, 191f, 174f,
    202f, 202f, 222f, 242f, 222f, 287f,
    *bottom(222f, 8f, 287f, 107f),
)

internal val MIDDLE_FLAME = Outline(
    28f, 292f,
    28f, 255f, 42f, 222f, 58f, 206f,
    62f, 198f, 74f, 198f, 76f, 210f,
    82f, 178f, 94f, 152f, 107f, 142f,
    114f, 138f, 119f, 143f, 118f, 152f,
    117f, 176f, 136f, 196f, 154f, 208f,
    158f, 198f, 168f, 194f, 173f, 199f,
    179f, 205f, 177f, 216f, 179f, 224f,
    191f, 246f, 202f, 268f, 202f, 292f,
    *bottom(202f, 28f, 292f, 82f),
)

internal val BELLY_FLAME = Outline(
    48f, 302f,
    48f, 278f, 58f, 252f, 68f, 242f,
    72f, 233f, 81f, 233f, 83f, 242f,
    86f, 226f, 99f, 196f, 112f, 188f,
    118f, 185f, 123f, 189f, 122f, 198f,
    121f, 216f, 141f, 236f, 159f, 252f,
    172f, 266f, 180f, 284f, 180f, 302f,
    *bottom(180f, 48f, 302f, 61f),
)

internal val LOW_OUTER = Outline(
    12f, 300f,
    12f, 252f, 40f, 208f, 80f, 182f,
    100f, 168f, 120f, 150f, 141f, 150f,
    160f, 150f, 170f, 164f, 161f, 174f,
    151f, 169f, 142f, 174f, 147f, 186f,
    162f, 208f, 218f, 252f, 218f, 300f,
    *bottom(218f, 12f, 300f, 95f),
)

internal val LOW_MIDDLE = Outline(
    34f, 304f,
    34f, 266f, 60f, 230f, 92f, 207f,
    104f, 198f, 118f, 188f, 128f, 192f,
    137f, 196f, 131f, 206f, 135f, 216f,
    151f, 236f, 196f, 268f, 196f, 304f,
    *bottom(196f, 34f, 304f, 75f),
)

internal val LOW_BELLY = Outline(
    54f, 312f,
    54f, 286f, 76f, 256f, 100f, 238f,
    108f, 232f, 118f, 230f, 120f, 238f,
    124f, 252f, 176f, 282f, 176f, 312f,
    *bottom(176f, 54f, 312f, 57f),
)

internal val DROPLET = Outline(
    25f, 315f,
    25f, 270f, 60f, 232f, 95f, 197f,
    108f, 182f, 118f, 160f, 136f, 158f,
    152f, 157f, 160f, 170f, 151f, 178f,
    141f, 181f, 135f, 188f, 141f, 198f,
    166f, 226f, 205f, 270f, 205f, 315f,
    *bottom(205f, 25f, 315f, 85f),
)

internal val HUSK_SHELL = Outline(
    35f, 335f,
    35f, 296f, 60f, 262f, 90f, 234f,
    104f, 220f, 116f, 206f, 133f, 210f,
    152f, 215f, 160f, 234f, 148f, 246f,
    170f, 264f, 198f, 298f, 198f, 335f,
    *bottom(198f, 35f, 335f, 70f),
)

internal val HOLLOW = Outline(
    115f, 352f,
    126f, 364f, 134f, 374f, 132f, 386f,
    130f, 396f, 122f, 400f, 115f, 400f,
    106f, 400f, 98f, 394f, 98f, 384f,
    98f, 378f, 101f, 373f, 105f, 369f,
    107f, 374f, 110f, 374f, 111f, 370f,
    112f, 364f, 113f, 358f, 115f, 352f,
)

internal val ASH_COLD = listOf(30f to 6f, 48f to 3.5f, 196f to 5f, 212f to 3f)
internal val ASH_HUSK = listOf(22f to 7f, 40f to 4f, 56f to 2.5f, 180f to 3f, 200f to 6f, 216f to 3.5f)

// These read the outlines above, so they have to come after them.
private val TALL_FLAME = FlameBody(OUTER_FLAME, MIDDLE_FLAME, BELLY_FLAME, 82f, 250f, 284f, 71f, 155f, 27f, Offset(113f, 328f), 326f, 378f)
private val LOW_FLAME = FlameBody(LOW_OUTER, LOW_MIDDLE, LOW_BELLY, 150f, 262f, 304f, 74f, 152f, 25f, Offset(113f, 346f), 340f, 384f)
