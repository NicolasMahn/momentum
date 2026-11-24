package de.nicolasmahn.momentum.feature.mo.acts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import de.nicolasmahn.momentum.core.ui.theme.MoPropBlue
import de.nicolasmahn.momentum.core.ui.theme.MoPropBlueDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropCharcoal
import de.nicolasmahn.momentum.core.ui.theme.MoPropCream
import de.nicolasmahn.momentum.core.ui.theme.MoPropGold
import de.nicolasmahn.momentum.core.ui.theme.MoPropGrey
import de.nicolasmahn.momentum.core.ui.theme.MoPropGreyDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropInk
import de.nicolasmahn.momentum.core.ui.theme.MoPropMint
import de.nicolasmahn.momentum.core.ui.theme.MoPropPink
import de.nicolasmahn.momentum.core.ui.theme.MoPropPinkDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropPurple
import de.nicolasmahn.momentum.core.ui.theme.MoPropPurpleDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropShadow
import de.nicolasmahn.momentum.core.ui.theme.MoPropSilver
import de.nicolasmahn.momentum.core.ui.theme.MoPropSky
import de.nicolasmahn.momentum.core.ui.theme.MoPropSteel
import de.nicolasmahn.momentum.core.ui.theme.MoPropTeal
import de.nicolasmahn.momentum.core.ui.theme.MoPropTealDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropWhite
import de.nicolasmahn.momentum.core.ui.theme.MoPropYellow
import de.nicolasmahn.momentum.core.ui.theme.MoSlateGloss
import de.nicolasmahn.momentum.domain.mo.MoIntensity
import de.nicolasmahn.momentum.domain.mo.MoOccasion
import de.nicolasmahn.momentum.feature.mo.CENTRE
import de.nicolasmahn.momentum.feature.mo.FEET
import de.nicolasmahn.momentum.feature.mo.MoArm
import de.nicolasmahn.momentum.feature.mo.MoBodies
import de.nicolasmahn.momentum.feature.mo.MoBodyKind
import de.nicolasmahn.momentum.feature.mo.MoBrowKind
import de.nicolasmahn.momentum.feature.mo.MoBrows
import de.nicolasmahn.momentum.feature.mo.MoEye
import de.nicolasmahn.momentum.feature.mo.MoEyeShape
import de.nicolasmahn.momentum.feature.mo.MoLeg
import de.nicolasmahn.momentum.feature.mo.MoLegs
import de.nicolasmahn.momentum.feature.mo.MoMouth
import de.nicolasmahn.momentum.feature.mo.MoPose
import de.nicolasmahn.momentum.feature.mo.MoRig
import de.nicolasmahn.momentum.feature.mo.block
import de.nicolasmahn.momentum.feature.mo.chunk
import de.nicolasmahn.momentum.feature.mo.cobweb
import de.nicolasmahn.momentum.feature.mo.curve
import de.nicolasmahn.momentum.feature.mo.cycle
import de.nicolasmahn.momentum.feature.mo.drop
import de.nicolasmahn.momentum.feature.mo.ease
import de.nicolasmahn.momentum.feature.mo.floorShadow
import de.nicolasmahn.momentum.feature.mo.limb
import de.nicolasmahn.momentum.feature.mo.moIdle
import de.nicolasmahn.momentum.feature.mo.puff
import de.nicolasmahn.momentum.feature.mo.threadFromAbove
import de.nicolasmahn.momentum.feature.mo.wave
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/*
 * A task with the scale motif is still open: the bathroom scale and Mo, who would rather do anything than step on it.
 * The scale is always the same chunky slab with a blank window, and it never shows a number.
 */

/** Leaves room for the slab and its shadow on either side as the Blazing body breathes. */
private const val BESIDE = 116f

private const val SLAB_TOP = FEET - 22f

private val slabColours = listOf(
    MoPropTeal to MoPropTealDark,
    MoPropBlue to MoPropBlueDark,
    MoPropPink to MoPropPinkDark,
    MoPropPurple to MoPropPurpleDark,
)

private fun side(v: MoVariant, salt: Int): Float = if (v.chance(50, salt)) 1f else -1f

/** How far [p] is from [from] to [to], from 0 to 1 and straight. */
private fun along(p: Float, from: Float, to: Float): Float = ((p - from) / max(to - from, 1e-4f)).coerceIn(0f, 1f)

/** The same, eased. */
private fun span(p: Float, from: Float, to: Float): Float = ease(along(p, from, to))

/** Up and down again once while [p] crosses [from]..[to]. */
private fun bump(p: Float, from: Float, to: Float): Float = sin(along(p, from, to) * PI.toFloat())

private fun mix(from: Float, to: Float, amount: Float): Float = from + (to - from) * amount

private fun mix(from: Offset, to: Offset, amount: Float): Offset = from + (to - from) * amount

/** The bathroom scale lying flat, with its blank window. [lift] raises it off the floor. */
private fun DrawScope.slab(x: Float, colours: Pair<Color, Color>, lift: Float = 0f, alpha: Float = 1f) {
    floorShadow(x, 42f - lift, MoPropShadow.copy(alpha = MoPropShadow.alpha * alpha))
    chunk(Offset(x, FEET - 11f - lift), 72f, 16f, colours.first.copy(alpha = alpha), colours.second.copy(alpha = alpha), corner = 7f, drop = 6f)
    block(Offset(x, FEET - 15f - lift), 22f, 6f, MoPropCream.copy(alpha = alpha), corner = 3f)
}

/**
 * Draws something that belongs to the room from inside [MoAct.held], by undoing what [pose] does to
 * him. The web stretches between a foot that moves with him and a scale that stays in the room.
 */
private fun DrawScope.inTheRoom(pose: MoPose, draw: DrawScope.() -> Unit) {
    val feet = Offset(CENTRE, FEET)
    withTransform({
        scale(1f / max(1f - pose.squash / 2f, 0.5f), 1f / max(1f + pose.squash, 0.5f), feet)
        rotate(-pose.tilt, feet)
        translate(-pose.offset.x - pose.shake, -pose.offset.y)
    }, draw)
}

/** The scale sneaks up on him while he looks away, and freezes the moment his eyes snap back. */
private object ScaleCreepsUp : MoAct("unweighed_scale_creeps_up", MoOccasion.Unweighed, intensity = MoIntensity.Mild) {
    private fun phase(t: Float) = cycle(t, 6f)

    /** How much he is looking away, from 0 to 1. Turning away is slow; the snap back is not. */
    private fun away(p: Float) = span(p, 0.30f, 0.40f) - span(p, 0.85f, 0.88f)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val s = side(v, salt = 1)
        val p = phase(t)
        val away = away(p)
        val caught = span(p, 0.85f, 0.87f) * (1f - span(p, 0.92f, 0.98f))
        val eye = MoEye(
            gaze = mix(Offset(s, 0.7f), Offset(-s * 0.8f, -0.7f), away),
            lidDrop = 0.3f * (1f - away),
            iris = 0.76f - 0.16f * caught,
        )
        val carefree = if (v.chance(50, salt = 4)) MoMouth.Whistle else MoMouth.Smile(width = 12f, depth = 9f)
        return moIdle(t).copy(
            leftEye = eye,
            rightEye = eye,
            brows = if (away < 0.5f) MoBrows(MoBrowKind.Raised, skew = 6f) else MoBrows(),
            mouth = if (away < 0.5f) MoMouth.Flat(12f) else carefree,
            // Rocking on his heels, the picture of innocence.
            tilt = away * (-s * 2f + wave(t, 1.5f) * 1.5f),
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val s = side(v, salt = 1)
        val p = phase(t)
        val reach = v.between(24f, 34f, salt = 3)
        val hops = 0.5f * span(p, 0.50f, 0.60f) + 0.5f * span(p, 0.68f, 0.78f)
        val crept = if (p < 0.30f) reach * (1f - span(p, 0.12f, 0.30f)) else reach * hops
        val x = CENTRE + s * (BESIDE - crept)
        val hop = bump(p, 0.50f, 0.60f) + bump(p, 0.68f, 0.78f)
        // Caught mid-step it teeters like a child playing statues, and it waddles on the way home.
        val teeter = wave(t, 0.14f) * 2.5f * span(p, 0.85f, 0.87f) * (1f - along(p, 0.87f, 0.97f))
        val waddle = wave(t, 0.3f) * 2f * bump(p, 0.12f, 0.30f)
        for (landing in floatArrayOf(0.60f, 0.78f)) {
            if (p < landing || p > landing + 0.07f) continue
            val settle = along(p, landing, landing + 0.07f)
            puff(Offset(x + s * 34f, FEET - 6f - settle * 5f), 7f + settle * 3f, MoPropGrey.copy(alpha = 0.8f * (1f - settle)))
        }
        // Each hop tips it toward him, the way anything sneaking leans into its step.
        withTransform({ rotate(teeter + waddle - s * 7f * hop, Offset(x, FEET)) }) {
            slab(x, v.of(slabColours, salt = 2), lift = 8f * hop)
        }
    }

    override val ownsTheFloor get() = true
}

/** A small friendly spider lets itself down onto the scale and lights it up: even the spider has been on today. */
private object SpiderGoesFirst : MoAct("unweighed_spider_goes_first", MoOccasion.Unweighed, intensity = MoIntensity.Mild) {
    private const val HANG_TOP = 30f
    private const val SPIDER = 9.5f

    private fun phase(t: Float) = cycle(t, 7f, offset = 0.2f)

    /** How far down its thread the spider is: 0 at the top, 1 on the scale. */
    private fun descent(p: Float) = span(p, 0f, 0.40f) - span(p, 0.60f, 1f)

    private fun press(p: Float) = span(p, 0.385f, 0.415f) - span(p, 0.585f, 0.615f)

    private fun spiderY(p: Float, v: MoVariant): Float {
        val hops = 2 + v.pick(2, salt = 5)
        val hop = abs(sin(along(p, 0.44f, 0.56f) * hops * PI.toFloat())) * 5f
        return HANG_TOP + descent(p) * (SLAB_TOP - SPIDER + 1.5f - HANG_TOP) + 4f * press(p) - hop
    }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val s = side(v, salt = 1)
        val p = phase(t)
        val toViewer = span(p, 0.44f, 0.47f) - span(p, 0.55f, 0.58f)
        val follow = Offset(s * 0.9f, ((spiderY(p, v) - MoBodies.of(body).eyeY) / 110f).coerceIn(-1f, 1f))
        val eye = MoEye(gaze = mix(follow, Offset(0f, -0.3f), toViewer))
        return moIdle(t, energy = 0.8f).copy(
            leftEye = eye,
            rightEye = eye,
            brows = if (toViewer > 0.5f) MoBrows(MoBrowKind.Worried, lift = 2f) else MoBrows(MoBrowKind.Raised),
            mouth = if (toViewer > 0.5f) MoMouth.Oh(size = 6f) else MoMouth.Flat(11f),
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val x = CENTRE + side(v, salt = 1) * (BESIDE - 2f)
        val press = press(phase(t))
        val (face, ledge) = v.of(slabColours.take(3) + (MoPropSteel to MoPropGreyDark), salt = 3)
        floorShadow(x, 42f, MoPropShadow)
        // A chunk taken apart, so the face can sink onto its ledge like a pressed button.
        block(Offset(x, FEET - 8f), 72f, 16f, ledge, corner = 7f)
        block(Offset(x, FEET - 14f + 4f * press), 72f, 16f, face, corner = 7f)
        val window = Offset(x, FEET - 15f + 4f * press)
        block(window, 22f, 6f, MoPropCharcoal, corner = 3f)
        block(window, 22f, 6f, v.of(listOf(MoPropMint, MoPropSky, MoPropGold), salt = 4).copy(alpha = press), corner = 3f)
    }

    override fun DrawScope.foreground(t: Float, v: MoVariant) {
        val s = side(v, salt = 1)
        val p = phase(t)
        val colour = v.of(listOf(MoPropPurple, MoPropTeal, MoPropPink, MoPropYellow), salt = 2)
        val at = Offset(CENTRE + s * (BESIDE + 8f), spiderY(p, v))
        threadFromAbove(at - Offset(0f, SPIDER - 1f), MoPropSilver.copy(alpha = 0.8f), 1.5f)
        val climbing = 1f - press(p)
        for (leg in 0..3) {
            for (legSide in floatArrayOf(-1f, 1f)) {
                val wiggle = wave(t + leg * 0.11f, 0.42f) * 1.6f * climbing * legSide
                val knee = at + Offset(legSide * 16f, -8f + leg * 4f + wiggle)
                limb(at + Offset(legSide * 6f, -3f + leg * 2f), knee, colour, 2.5f)
                limb(knee, knee + Offset(legSide * 3.5f, 6f), colour, 2.5f)
            }
        }
        drawCircle(colour, SPIDER, at)
        for (eyeSide in floatArrayOf(-1f, 1f)) {
            drawCircle(MoPropWhite, 3.1f, at + Offset(eyeSide * 3.8f, -1.5f))
            drawCircle(MoPropInk, 1.6f, at + Offset(eyeSide * 3.8f - s * 0.7f, -1f))
        }
    }

    override val ownsTheFloor get() = true

    // Blazing's sparkles would sit on the spider's thread.
    override val sparkles get() = false
}

/** He blows the dust off the scale, and the dust answers with a sneeze that shoots his tip up. */
private object DustSneeze : MoAct("unweighed_dust_sneeze", MoOccasion.Unweighed) {
    private const val X = BESIDE - 2f

    // The offset puts the blow at t = 0 and the first sneeze a good three seconds in.
    private fun phase(t: Float) = cycle(t, 5.2f, offset = 0.08f)

    private fun twice(v: MoVariant) = v.chance(30, salt = 5)

    private fun sneeze(p: Float, v: MoVariant): Float =
        bump(p, 0.70f, 0.78f) + if (twice(v)) 0.7f * bump(p, 0.82f, 0.88f) else 0f

    /** The long breath out over the scale. It is over well before the phase comes round again. */
    private fun blow(p: Float) = span(p, 0f, 0.03f) - span(p, 0.15f, 0.18f)

    /** This scale always stands to his right: the kit purses his lips for a blow on that side only. */
    private const val SIDE = 1f

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val s = SIDE
        val p = phase(t)
        val sneeze = sneeze(p, v)
        val settled = if (twice(v)) 0.88f else 0.78f
        val lean = 1f - span(p, 0.18f, 0.32f) + span(p, settled + 0.02f, 0.99f)
        val inhale = span(p, 0.45f, 0.69f) * (1f - span(p, 0.69f, 0.71f))
        val sniff = bump(p, settled + 0.05f, settled + 0.09f)
        val down = Offset(s * 0.8f, 0.9f)
        val watery = MoEye(lidDrop = 0.45f + 0.2f * inhale, lid = -0.3f, gaze = Offset(0f, -0.4f - 0.4f * inhale))
        val tear = along(p, 0.18f, 0.45f)
        val eye = when {
            // Squeezed shut, the way a sneeze shuts them. A cross is for someone knocked out.
            sneeze > 0.05f -> MoEye(shape = MoEyeShape.Happy)
            p < 0.18f -> MoEye(gaze = down)
            p < 0.70f -> watery
            else -> MoEye(gaze = down, lidDrop = 0.2f)
        }
        val wells = p in 0.18f..0.45f
        val arm = MoArm(
            mix(MoArm.Down.reach, Offset(20f, 8f), inhale) + Offset(14f, 6f) * sneeze,
            bend = mix(0.1f, 0.4f, inhale),
        )
        return moIdle(t).copy(
            leftEye = if (wells && s < 0f) eye.copy(tear = tear) else eye,
            rightEye = if (wells && s > 0f) eye.copy(tear = tear) else eye,
            brows = when {
                sneeze > 0.05f -> MoBrows(MoBrowKind.Worried, lift = 4f)
                p < 0.18f -> MoBrows()
                p < 0.45f -> MoBrows(MoBrowKind.Worried, lift = 3f)
                p < 0.70f -> MoBrows(MoBrowKind.Raised, lift = 6f * inhale)
                else -> MoBrows(MoBrowKind.Worried)
            },
            mouth = when {
                sneeze > 0.05f -> MoMouth.Oh(size = 8f + 6f * sneeze)
                p < 0.18f -> MoMouth.Whistle
                p < 0.45f -> MoMouth.Wavy
                p < 0.70f -> MoMouth.Oh(size = 6f + 5f * inhale)
                else -> MoMouth.Flat(10f)
            },
            leftArm = arm,
            rightArm = arm,
            tilt = s * (6f * lean - 5f * inhale + 7f * sneeze),
            squash = moIdle(t).squash + 0.07f * inhale - 0.10f * sneeze,
            flare = v.between(20f, 32f, salt = 4) * sneeze,
            shake = wave(t, 0.06f) * 2f * sneeze,
            offset = Offset(0f, -2f * sniff),
            blinks = false,
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val x = CENTRE + SIDE * X
        val p = phase(t)
        slab(x, v.of(slabColours, salt = 2))
        val coat = 1f - 0.65f * span(p, 0f, 0.15f) + 0.65f * span(p, 0.78f, 0.97f)
        block(Offset(x, SLAB_TOP - 1f), 66f, 6f, MoPropGrey.copy(alpha = coat), corner = 3f)
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val p = phase(t)
        // The same breath says both things: aimed at the scale, then blown out again by the sneeze.
        val gust = max(blow(p), sneeze(p, v)).coerceIn(0f, 1f)
        if (gust < 0.02f) return
        val lips = rig.mouth + if (blow(p) > 0f) Offset(9f, 4f) else Offset.Zero
        // The scale stands so close that his own belly lies between his lips and the dust, so the
        // breath only reads as breath by leaving his lips in a fan and reaching most of the way there.
        val aim = Offset(CENTRE + SIDE * (X - 28f), SLAB_TOP - 8f) - lips
        val far = aim.getDistance()
        for (streak in 0..2) {
            val turn = (streak - 1) * 9f * PI.toFloat() / 180f
            val dir = Offset(aim.x * cos(turn) - aim.y * sin(turn), aim.x * sin(turn) + aim.y * cos(turn)) / far
            val travel = cycle(t, 0.42f, offset = streak / 3f)
            val from = lips + dir * (10f + 0.53f * far * travel)
            val strength = sin(travel * PI.toFloat()) * gust
            limb(from, from + dir * (6f + 13f * strength), MoPropCream.copy(alpha = 0.8f * strength), 3f)
        }
    }

    override fun DrawScope.foreground(t: Float, v: MoVariant) {
        val s = SIDE
        val x = CENTRE + s * X
        val p = phase(t)
        val sneeze = sneeze(p, v)
        // Both clouds stay over the scale and clear of his eyes, so the face is never dusted over.
        val rise = along(p, 0f, 0.45f)
        puff(Offset(x - s * 8f, FEET - 46f - 56f * rise), 19f + 13f * rise, MoPropGrey.copy(alpha = 0.85f * span(p, 0f, 0.04f) * (1f - rise * rise)))
        val kick = along(p, 0.70f, 0.90f)
        puff(Offset(x + s * 6f, FEET - 40f - 36f * kick), 15f + 11f * kick, MoPropGrey.copy(alpha = 0.8f * span(p, 0.70f, 0.72f) * (1f - kick * kick)))

        val afloat = span(p, 0.01f, 0.40f) - span(p, 0.78f, 0.96f)
        for (mote in 0 until 3 + v.pick(3, salt = 3)) {
            val drift = sin(t * 1.7f + mote * 2.1f) * 5f * afloat
            val at = Offset(x - 26f + mote * 11f + drift, SLAB_TOP - 5f - afloat * (16f + mote % 3 * 13f) - sneeze * 9f)
            drawCircle(MoPropGreyDark.copy(alpha = 0.9f), 2.5f, at)
        }
    }

    override val ownsTheFloor get() = true
}

/** The scale has stood so long that its cobweb has reached his foot, and the web always wins. */
private object WebbedFoot : MoAct("unweighed_webbed_foot", MoOccasion.Unweighed) {
    private fun phase(t: Float) = cycle(t, 4.4f, offset = 0.3f)

    private fun pull(p: Float) = span(p, 0.10f, 0.50f) - span(p, 0.62f, 0.66f)

    private fun squash(t: Float): Float {
        val p = phase(t)
        return moIdle(t).squash + 0.03f * pull(p) - 0.05f * bump(p, 0.66f, 0.76f)
    }

    /** How far he has heaved himself away from the scale. The stuck foot stays where it was. */
    private fun heave(p: Float, v: MoVariant) = v.between(14f, 20f, salt = 5) * pull(p)

    // No tilt or shake here: the web is anchored to the floor and has to meet his foot, and only an
    // offset is simple enough to take back out of it.
    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val s = side(v, salt = 1)
        val p = phase(t)
        val pull = pull(p)
        val tremble = span(p, 0.44f, 0.50f) * (1f - span(p, 0.62f, 0.64f))
        // The foot barely leaves the floor: lifted any higher it would vanish behind his own base and
        // take the ends of the strands with it. What sells the strain is the rest of him heaving away
        // from a foot the web keeps where it is.
        val near = MoLeg(
            lift = max(0f, 5f * pull + wave(t, 0.09f) * 1.4f * tremble),
            swing = 6f + heave(p, v),
        )
        val toViewer = span(p, 0.76f, 0.80f) - span(p, 0.90f, 0.94f)
        val eye = MoEye(gaze = mix(Offset(s * 0.7f, 1f), Offset(0f, -0.4f), toViewer), iris = 0.76f - 0.16f * toViewer)
        val straining = p in 0.10f..0.66f
        return moIdle(t).copy(
            leftEye = eye,
            rightEye = eye,
            brows = if (straining) MoBrows(MoBrowKind.Flat, lift = -2f) else MoBrows(MoBrowKind.Worried),
            mouth = if (straining) MoMouth.Gritted else MoMouth.Flat(10f),
            leftArm = MoArm(Offset(32f, 22f - 30f * pull), bend = 0.55f),
            rightArm = MoArm(Offset(32f, 22f - 30f * pull), bend = 0.55f),
            legs = if (s > 0f) MoLegs(right = near) else MoLegs(left = near),
            // Away from the scale while he pulls, and one lurch back toward it when the web wins.
            offset = Offset(-s * (heave(p, v) - 9f * bump(p, 0.66f, 0.80f)), 0f),
            flare = 10f * pull,
            squash = squash(t),
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val s = side(v, salt = 1)
        val x = CENTRE + s * BESIDE
        slab(x, v.of(slabColours, salt = 3))
        // The kit's web fans up and to the left of its anchor; mirrored, it always fans back over the slab.
        withTransform({
            translate(x + s * 44f, SLAB_TOP + 17f)
            scale(s, 1f, Offset.Zero)
        }) { cobweb() }
        if (v.chance(35, salt = 4)) {
            val spider = Offset(x + s * 10f, SLAB_TOP - 13f)
            for (side in listOf(-1f, 1f)) {
                for (leg in 0..3) {
                    val foot = spider + Offset(side * (11f + leg), -6f + leg * 4f)
                    curve(spider, foot - Offset(0f, 9f), foot, MoPropPurple, 2f)
                }
            }
            drawCircle(MoPropPurple, 6.5f, spider)
            for (side in listOf(-1f, 1f)) drawCircle(MoPropWhite, 1.5f, spider + Offset(side * 2.5f, -2f))
        }
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val s = side(v, salt = 1)
        val p = phase(t)
        val pull = pull(p)
        val foot = if (s > 0f) rig.rightFoot else rig.leftFoot
        val pose = pose(t, v, rig.body.kind)
        val twang = wave(t, 0.1f) * 4f * span(p, 0.64f, 0.66f) * (1f - along(p, 0.66f, 0.82f))
        val sag = 10f * (1f - pull) + twang
        inTheRoom(pose) {
            val corner = Offset(CENTRE + s * (BESIDE - 33f), SLAB_TOP + 2f)
            // The foot is his, so his squash and his lean are put back into it before the strands reach.
            val squash = pose.squash
            val toe = Offset(CENTRE + (foot.x - CENTRE) * (1f - squash / 2f), FEET + (foot.y - FEET) * (1f + squash)) + pose.offset
            // Several thin threads fanning from the slab and gathering on the foot: a web reads as a
            // web only while its strands stay apart, and a thick bundle of them is a hose.
            for (strand in 0 until 4 + v.pick(3, salt = 2)) {
                val from = corner + Offset(s * strand * 4f, strand * 3.2f)
                val to = toe + Offset(s * (16f - strand * 6f), -10f - strand * 1.5f)
                curve(from, (from + to) / 2f + Offset(0f, sag + strand * 2.5f * (1f - pull)), to, MoSlateGloss.copy(alpha = 0.95f), 2.8f - 1f * pull)
            }
        }
    }

    override val ownsTheFloor get() = true
}

internal val unweighedActs1: List<MoAct> = listOf(
    ScaleCreepsUp,
    SpiderGoesFirst,
    DustSneeze,
    WebbedFoot,
)
