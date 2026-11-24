package de.nicolasmahn.momentum.feature.mo.acts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import de.nicolasmahn.momentum.core.ui.theme.MoFlameBelly
import de.nicolasmahn.momentum.core.ui.theme.MoPropBlueDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropCharcoal
import de.nicolasmahn.momentum.core.ui.theme.MoPropGold
import de.nicolasmahn.momentum.core.ui.theme.MoPropGoldDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropGrey
import de.nicolasmahn.momentum.core.ui.theme.MoPropGreyDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropInk
import de.nicolasmahn.momentum.core.ui.theme.MoPropMint
import de.nicolasmahn.momentum.core.ui.theme.MoPropMoon
import de.nicolasmahn.momentum.core.ui.theme.MoPropPink
import de.nicolasmahn.momentum.core.ui.theme.MoPropPinkDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropPurple
import de.nicolasmahn.momentum.core.ui.theme.MoPropPurpleDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropSand
import de.nicolasmahn.momentum.core.ui.theme.MoPropSilver
import de.nicolasmahn.momentum.core.ui.theme.MoPropSky
import de.nicolasmahn.momentum.core.ui.theme.MoPropTeal
import de.nicolasmahn.momentum.core.ui.theme.MoPropTealDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropWoodDark
import de.nicolasmahn.momentum.domain.mo.MoIntensity
import de.nicolasmahn.momentum.domain.mo.MoOccasion
import de.nicolasmahn.momentum.feature.mo.CENTRE
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
import de.nicolasmahn.momentum.feature.mo.MoPalettes
import de.nicolasmahn.momentum.feature.mo.MoPose
import de.nicolasmahn.momentum.feature.mo.MoRig
import de.nicolasmahn.momentum.feature.mo.block
import de.nicolasmahn.momentum.feature.mo.chunk
import de.nicolasmahn.momentum.feature.mo.curve
import de.nicolasmahn.momentum.feature.mo.cycle
import de.nicolasmahn.momentum.feature.mo.ease
import de.nicolasmahn.momentum.feature.mo.limb
import de.nicolasmahn.momentum.feature.mo.mitten
import de.nicolasmahn.momentum.feature.mo.oval
import de.nicolasmahn.momentum.feature.mo.puff
import de.nicolasmahn.momentum.feature.mo.pulse
import de.nicolasmahn.momentum.feature.mo.shape
import de.nicolasmahn.momentum.feature.mo.sparkle
import de.nicolasmahn.momentum.feature.mo.wave
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/*
 * In bed, past the hour he should be asleep: six ways of saying "so should you" from under the
 * one shared blanket. Every act starts from moInBed, and whatever of him is below BED_LINE is
 * hidden by bedBlanket, so a mitten that should show sits on the blanket's edge or above it.
 */

/** Eased progress of [p] from [from] to [to]; the two are always distinct constants. */
private fun span(p: Float, from: Float, to: Float): Float = ease((p - from) / (to - from))

/** One hump from 0 up to 1 and back, [length] of the loop long, starting at [from]; 0 elsewhere. */
private fun hump(p: Float, from: Float, length: Float): Float {
    val k = (p - from) / length
    return if (k in 0f..1f) sin(k * PI.toFloat()) else 0f
}

private fun mix(a: Float, b: Float, k: Float): Float = a + (b - a) * k

private fun mix(a: Offset, b: Offset, k: Float): Offset = a + (b - a) * k

private fun mix(a: MoArm, b: MoArm, k: Float): MoArm = MoArm(mix(a.reach, b.reach, k), mix(a.bend, b.bend, k))

private fun MoVariant.side(salt: Int): Float = if (chance(50, salt)) 1f else -1f

private val shutEye = MoEye(shape = MoEyeShape.Shut)

/** How far moInBed lifts this body so that its face sits where the tall one's does. */
private fun bedLift(body: MoBodyKind): Float = MoBodies.Tall.mouth.y - MoBodies.of(body).mouth.y

/**
 * The arm that puts a mitten at [target] on screen, on a body lying in bed and moved a further
 * [shift] by the act. Tilt and squash are not accounted for, so an act that aims at a fixed thing
 * keeps them at zero.
 */
private fun bedArm(body: MoBodyKind, target: Offset, side: Float, bend: Float, shift: Offset = Offset.Zero): MoArm {
    val shape = MoBodies.of(body)
    val shoulder = Offset(CENTRE + shift.x + side * shape.shoulderHalf, shape.shoulderY + bedLift(body) + shift.y)
    return MoArm(Offset(side * (target.x - shoulder.x), target.y - shoulder.y), bend)
}

/** The night above the headboard: two stars, and the moon when [moon], on the side away from [side]. */
private fun DrawScope.nightAbove(t: Float, side: Float, moon: Boolean) {
    if (moon) {
        val at = Offset(CENTRE - side * 118f, 44f)
        drawCircle(MoPropMoon, 24f, at)
        drawCircle(MoPropGrey.copy(alpha = 0.35f), 5f, at + Offset(-7f, -5f))
    }
    sparkle(Offset(CENTRE + side * 112f, 70f), 7f, 1f + 0.3f * pulse(t, 2.6f), MoPropMoon)
    sparkle(Offset(CENTRE + side * 64f, 28f), 5f, 1f + 0.3f * pulse(t + 1.1f, 3.1f), MoPropMoon)
}

/** One eye opens on the viewer, heavy and reproachful, and a mitten pats the empty half of the pillow twice: bed, now. */
private object BedOneEyeOpen : MoAct("sleepy_bed_one_eye_open", MoOccasion.Sleepy, intensity = MoIntensity.Strong) {
    /** He lies over on his own side of the pillow, which leaves the other half empty to pat. */
    private const val ASIDE = 40f
    private const val PAT_X = 105f
    private const val PILLOW_Y = 336f

    private fun side(v: MoVariant) = v.side(salt = 1)
    private fun shift(v: MoVariant) = Offset(-side(v) * ASIDE, 0f)

    // 0.08 in, the eye is open and the first pat is under way: the frame a still picture needs.
    private fun progress(t: Float, v: MoVariant) = cycle(t, v.between(6.5f, 8f, salt = 2), offset = 0.08f)

    private fun awake(p: Float) = span(p, 0f, 0.05f) * (1f - span(p, 0.42f, 0.50f))

    private fun patLift(p: Float) = hump(p, 0.07f, 0.09f) + hump(p, 0.17f, 0.09f)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val p = progress(t, v)
        val side = side(v)
        val awake = awake(p)
        val inBed = moInBed(t, body, tilt = mix(-side * 3f, side * 2f, awake), shift = shift(v) + Offset(0f, -5f * awake))
        val resting = if (side > 0f) inBed.rightArm else inBed.leftArm
        val patting = bedArm(body, Offset(CENTRE + side * PAT_X, PILLOW_Y - 34f * patLift(p)), side, bend = 0.3f, shift(v))
        val pat = mix(resting, patting, awake)
        val open = if (awake < 0.5f) {
            shutEye
        } else {
            MoEye(open = mix(0.35f, 1f, span(awake, 0.5f, 1f)), lidDrop = 0.45f, lid = 0.3f, gaze = Offset(0f, 0.15f))
        }
        return inBed.copy(
            leftEye = if (side < 0f) open else shutEye,
            rightEye = if (side > 0f) open else shutEye,
            mouth = if (awake > 0.5f) MoMouth.Flat(10f) else MoMouth.Smile(width = 9f, depth = 6f),
            leftArm = if (side < 0f) pat else inBed.leftArm,
            rightArm = if (side > 0f) pat else inBed.rightArm,
            blinks = false,
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        nightAbove(t, side(v), moon = false)
        bedBehind()
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val p = progress(t, v)
        val awake = awake(p)
        if (awake < 0.5f) return
        val side = side(v)
        val hand = if (side > 0f) rig.rightHand else rig.leftHand
        // The pillow gives under the mitten and comes back while the mitten is in the air.
        val press = (1f - patLift(p)) * span(awake, 0.5f, 1f)
        oval(hand + Offset(0f, 9f), 20f, 6f, MoPropSand.copy(alpha = 0.7f * press))
        mitten(rig, hand, side)
    }

    override fun DrawScope.foreground(t: Float, v: MoVariant) = bedBlanket(t, v)

    override val ownsTheFloor get() = true
    override val sparkles get() = false
}

/** A long shh at the viewer, a finger across his lips; then he pulls the blanket up over his nose and his eyes fall shut. */
private object BedShush : MoAct("sleepy_bed_shush", MoOccasion.Sleepy, intensity = MoIntensity.Strong) {
    /** Up to just under his eyes: the eye whites end at 311 on both bodies, and the hem then peaks at 316. */
    private const val OVER_NOSE = 30f

    /** Where the pulling mitten grips the hem: outside his widest part, inside the view. */
    private const val GRIP_X = CENTRE + 125f

    private fun progress(t: Float, v: MoVariant) = cycle(t, v.between(7.5f, 9f, salt = 2))

    private fun up(p: Float) = span(p, 0.34f, 0.44f) * (1f - span(p, 0.88f, 0.95f))

    private fun shut(p: Float) = span(p, 0.44f, 0.56f) * (1f - span(p, 0.92f, 0.99f))

    private fun shushing(p: Float) = p < 0.28f || p > 0.97f

    /** Two soft beats of his brows while he holds it: a shh, and a shh. */
    private fun beat(p: Float) = hump(p, 0.04f, 0.08f) + hump(p, 0.15f, 0.08f)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val p = progress(t, v)
        val inBed = moInBed(t, body)
        // Under his mouth, so the finger it holds up crosses his lips in the middle.
        val atMouth = bedArm(body, Offset(MoBodies.of(body).mouth.x, 340f), -1f, bend = 0.35f)
        val leaving = span(p, 0.28f, 0.36f) * (1f - span(p, 0.95f, 1f))
        // The other mitten grips the hem, rides it up, slips under the blanket, and comes out to let it down.
        val under = span(p, 0.46f, 0.54f) * (1f - span(p, 0.84f, 0.90f))
        val grip = bedArm(body, Offset(GRIP_X, 338f - OVER_NOSE * up(p) + 42f * under), 1f, bend = 0.25f)
        val toGrip = span(p, 0.28f, 0.34f) * (1f - span(p, 0.95f, 1f))
        val shut = shut(p)
        val eye = if (shut > 0.5f) {
            shutEye
        } else {
            MoEye(open = mix(1f, 0.35f, span(shut, 0f, 0.5f)), lidDrop = 0.4f + 0.3f * shut, gaze = Offset(0f, 0.1f))
        }
        return inBed.copy(
            leftEye = eye,
            rightEye = eye,
            brows = if (shushing(p)) MoBrows(MoBrowKind.Raised, lift = 3f + 3f * beat(p)) else MoBrows(),
            // The act draws his lips for the shh itself, shut whatever state his teeth are in.
            mouth = if (shushing(p)) MoMouth.Hidden else MoMouth.Flat(8f),
            leftArm = mix(atMouth, inBed.leftArm, leaving),
            rightArm = mix(inBed.rightArm, grip, toGrip),
            tilt = -3f * span(p, 0.50f, 0.60f) * (1f - span(p, 0.86f, 0.92f)),
            blinks = false,
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        nightAbove(t, v.side(salt = 3), moon = true)
        bedBehind()
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val p = progress(t, v)
        val raised = (1f - span(p, 0.24f, 0.29f)) + span(p, 0.97f, 1f)
        if (shushing(p)) {
            // Shut lips show no teeth and let nothing out, neither breath nor the smell of neglected
            // teeth: anything leaving his mouth beside the finger reads as something he smokes.
            // Pressed wide under the finger, and back to the width of his resting mouth as it goes.
            val half = 8f + 5f * raised
            limb(rig.mouth + Offset(-half, 2f), rig.mouth + Offset(half, 2f), rig.palette.ink, 5.5f)
        }
        if (raised <= 0.01f) return
        // One finger upright across them: that is a shh.
        limb(rig.leftHand, rig.leftHand + Offset(0f, -26f) * raised, rig.palette.limb, 13f)
    }

    override fun DrawScope.foreground(t: Float, v: MoVariant) = bedBlanket(t, v, pulledUp = OVER_NOSE * up(progress(t, v)))

    override val ownsTheFloor get() = true
    override val sparkles get() = false
}

/** He tugs the cord of the lamp clipped to the headboard: the shade goes dark, the night comes in, and only his own glow is left. */
private object BedLightsOut : MoAct("sleepy_bed_lights_out", MoOccasion.Sleepy, intensity = MoIntensity.Strong) {
    private const val SHADE_X = 120f
    private const val SHADE_TOP = 98f
    private const val SHADE_BOTTOM = 140f
    private const val KNOB_REST_Y = 281f
    private const val PULL = 32f

    private fun side(v: MoVariant) = v.side(salt = 1)

    private fun progress(t: Float, v: MoVariant) = cycle(t, v.between(8.5f, 10f, salt = 2))

    private fun dark(p: Float) = span(p, 0.12f, 0.19f) * (1f - span(p, 0.84f, 0.90f))

    private fun asleep(p: Float) = span(p, 0.22f, 0.34f) * (1f - span(p, 0.86f, 0.92f))

    private fun shadeX(v: MoVariant) = CENTRE + side(v) * SHADE_X

    private fun cordX(v: MoVariant) = shadeX(v) + side(v) * 9f

    /** Where the knob hangs on screen: in his mitten until he lets go, then springing back with a swing. */
    private fun knob(p: Float, v: MoVariant): Offset {
        val pulled = when {
            p < 0.06f -> 0f
            p < 0.12f -> span(p, 0.06f, 0.12f)
            else -> 1f - span(p, 0.12f, 0.17f)
        }
        val since = p - 0.12f
        val swing = if (since > 0f) 7f * sin(since * 52f) * (1f - span(since, 0f, 0.2f)) else 0f
        return Offset(cordX(v) + side(v) * swing, KNOB_REST_Y + PULL * pulled)
    }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val p = progress(t, v)
        val s = side(v)
        val asleep = asleep(p)
        val inBed = moInBed(t, body, tilt = -s * 3f * asleep, shift = Offset(0f, 3f * asleep))
        val resting = if (s > 0f) inBed.rightArm else inBed.leftArm
        val arm = when {
            p < 0.12f -> bedArm(body, knob(p, v), s, bend = 0.2f)
            p < 0.30f -> mix(bedArm(body, Offset(cordX(v), KNOB_REST_Y + PULL), s, bend = 0.2f), resting, span(p, 0.12f, 0.30f))
            p < 0.92f -> resting
            else -> mix(resting, bedArm(body, Offset(cordX(v), KNOB_REST_Y), s, bend = 0.2f), span(p, 0.92f, 1f))
        }
        val eye = if (asleep > 0.5f) {
            shutEye
        } else {
            // Heavier lids for the lamp that came back on; they lift a little once it is out, while his eyes are still open.
            MoEye(open = mix(1f, 0.35f, span(asleep, 0f, 0.5f)), lidDrop = if (p > 0.8f) 0.65f else mix(0.65f, 0.5f, span(p, 0.12f, 0.22f)), gaze = Offset(s * 0.7f, -0.8f))
        }
        return inBed.copy(
            leftEye = eye,
            rightEye = eye,
            // Weary brows for the lamp that came back on, kept through the tug.
            brows = if (p >= 0.88f || p < 0.12f) MoBrows(MoBrowKind.Flat, lift = -1f) else MoBrows(),
            mouth = if (asleep > 0.5f) MoMouth.Smile(width = 9f, depth = 6f) else MoMouth.Flat(9f),
            leftArm = if (s < 0f) arm else inBed.leftArm,
            rightArm = if (s > 0f) arm else inBed.rightArm,
            squash = 0f,
            blinks = false,
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val p = progress(t, v)
        val s = side(v)
        val dark = dark(p)
        val x = shadeX(v)
        bedBehind(night = 0.6f * dark)
        // The lamp is clipped to the headboard, and its light and the dark it leaves fall over the
        // bed, which is why all of it comes after the bed.
        block(Offset(x, 196f), 26f, 12f, MoPropWoodDark, corner = 5f)
        limb(Offset(x, SHADE_BOTTOM - 6f), Offset(x, 196f), MoPropWoodDark, 7f)
        val cone = Path().apply {
            moveTo(x - s * 30f, SHADE_BOTTOM)
            lineTo(x + s * 30f, SHADE_BOTTOM)
            lineTo(x + s * 34f, BED_LINE)
            lineTo(x - s * 130f, BED_LINE)
            close()
        }
        drawPath(cone, MoPropMoon.copy(alpha = 0.16f * (1f - dark)))
        shape(MoPropCharcoal, x - 18f to SHADE_TOP, x + 18f to SHADE_TOP, x + 30f to SHADE_BOTTOM, x - 30f to SHADE_BOTTOM)
        if (dark < 0.99f) {
            shape(MoPropMoon.copy(alpha = 1f - dark), x - 18f to SHADE_TOP, x + 18f to SHADE_TOP, x + 30f to SHADE_BOTTOM, x - 30f to SHADE_BOTTOM)
        }
        val grip = knob(p, v)
        val tug = 1f - span(p, 0.12f, 0.20f) + span(p, 0.92f, 1f)
        val end = grip + Offset(s * 7f * tug, 21f)
        limb(Offset(cordX(v), SHADE_BOTTOM), grip, MoPropGreyDark, 3f)
        limb(grip, end, MoPropGreyDark, 3f)
        bead(end, v)
        // His own glow falls on the bed alone: past the headboard it would light the sky as a disc.
        val glow = dark * (0.9f + 0.1f * pulse(t, BED_BREATH_SECONDS))
        clipRect(CENTRE - 152f, 193f, CENTRE + 152f, 425f) {
            drawCircle(MoFlameBelly.copy(alpha = 0.10f * glow), 165f, Offset(CENTRE, 290f))
            drawCircle(MoFlameBelly.copy(alpha = 0.10f * glow), 118f, Offset(CENTRE, 296f))
        }
    }

    private fun DrawScope.bead(at: Offset, v: MoVariant) {
        val (colour, ledge) = v.of(listOf(MoPropGold to MoPropGoldDark, MoPropPink to MoPropPinkDark, MoPropTeal to MoPropTealDark), salt = 3)
        drawCircle(ledge, 7f, at + Offset(0f, 3f))
        drawCircle(colour, 7f, at)
    }

    override fun DrawScope.foreground(t: Float, v: MoVariant) {
        val p = progress(t, v)
        bedBlanket(t, v, night = 0.6f * dark(p))
    }

    override val ownsTheFloor get() = true
    override val sparkles get() = false
}

/** Fast asleep hugging a plush against his cheek, smiling, with a snore bubble that swells and pops on every breath. */
private object BedPlush : MoAct("sleepy_bed_plush", MoOccasion.Sleepy, intensity = MoIntensity.Strong) {
    private fun side(v: MoVariant) = v.side(salt = 1)

    private fun star(v: MoVariant) = v.chance(45, salt = 2)

    private fun colours(v: MoVariant) = v.of(
        listOf(MoPropPink to MoPropPinkDark, MoPropPurple to MoPropPurpleDark, MoPropMint to MoPropTealDark, MoPropSky to MoPropBlueDark),
        salt = 3,
    )

    private val plushStar by lazy {
        Path().apply {
            moveTo(0f, -23f)
            quadraticTo(2f, -23f, 8f, -10f)
            lineTo(21f, -8f)
            quadraticTo(25f, -7f, 21f, -3f)
            lineTo(12f, 6f)
            lineTo(14f, 19f)
            quadraticTo(15f, 23f, 10f, 21f)
            lineTo(0f, 15f)
            lineTo(-10f, 21f)
            quadraticTo(-15f, 23f, -14f, 19f)
            lineTo(-12f, 6f)
            lineTo(-21f, -3f)
            quadraticTo(-25f, -7f, -21f, -8f)
            lineTo(-8f, -10f)
            quadraticTo(-2f, -23f, 0f, -23f)
            close()
        }
    }

    // Starts part way through a breath, so the still picture already has a bubble.
    private fun breath(t: Float) = cycle(t, BED_BREATH_SECONDS, offset = 0.45f)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val s = side(v)
        val squeeze = pulse(t, BED_BREATH_SECONDS)
        val inBed = moInBed(t, body, tilt = s * (4f + 1.5f * squeeze), pulledUp = 3f * squeeze)
        val shape = MoBodies.of(body)
        val eyeX = if (s > 0f) shape.rightEyeX else shape.leftEyeX
        // The mitten holds the plush from below and outside, low enough to leave its face showing against his cheek.
        val hugging = bedArm(body, Offset(eyeX + s * 64f, shape.eyeY + bedLift(body) + 32f - 2f * squeeze), s, bend = 0.55f)
        return inBed.copy(
            leftEye = shutEye,
            rightEye = shutEye,
            mouth = MoMouth.Smile(width = 11f + 2f * squeeze, depth = 8f + 2f * squeeze),
            leftArm = if (s < 0f) hugging else inBed.leftArm,
            rightArm = if (s > 0f) hugging else inBed.rightArm,
            blinks = false,
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        nightAbove(t, -side(v), moon = false)
        bedBehind()
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val s = side(v)
        val (colour, dark) = colours(v)
        val eye = if (s > 0f) rig.rightEye else rig.leftEye
        val isStar = star(v)
        val at = eye + Offset(s * 58f, if (isStar) 8f else 16f)
        if (isStar) {
            translate(at.x, at.y) {
                translate(top = 3f) { drawPath(plushStar, dark) }
                drawPath(plushStar, colour)
            }
        } else {
            val head = at - Offset(0f, 3f)
            oval(at + Offset(0f, 13f), 14f, 16f, dark)
            for (ear in listOf(-1f, 1f)) {
                val tip = head + Offset(ear * 12f, -10f)
                drawCircle(dark, 7f, tip)
                drawCircle(colour, 4f, tip)
            }
            oval(head, 16f, 14f, colour)
        }
        for (dot in listOf(-4f, 4f)) drawCircle(MoPropInk, 1.7f, at + Offset(dot, -3f))
        curve(at + Offset(-3f, 1.5f), at + Offset(0f, 4.5f), at + Offset(3f, 1.5f), MoPropInk, 2f)
        mitten(rig, if (s > 0f) rig.rightHand else rig.leftHand, s)
        snore(t, rig, away = -s)
    }

    private fun DrawScope.snore(t: Float, rig: MoRig, away: Float) {
        val breath = breath(t)
        val grown = if (breath < 0.86f) breath else 0.86f
        val size = 4f + 16f * grown
        // Out of the corner of his mouth, low enough to pass under the shut eye.
        val at = rig.mouth + Offset(away * (11f + size * 0.85f), 4f - size * 0.45f)
        if (breath < 0.86f) {
            // Nearly solid: over his yellow cheek a thinner blue turns green.
            drawCircle(MoPropSky.copy(alpha = 0.85f), size, at)
            drawCircle(MoPropMoon, size * 0.22f, at + Offset(-size * 0.4f, -size * 0.4f))
        } else {
            val burst = (breath - 0.86f) / 0.14f
            for (index in 0..4) {
                val angle = index * 1.26f + 0.3f
                drawCircle(MoPropSky.copy(alpha = 0.7f * (1f - burst)), 2.5f, at + Offset(cos(angle), sin(angle)) * (size + 10f * burst))
            }
        }
    }

    override fun DrawScope.foreground(t: Float, v: MoVariant) = bedBlanket(t, v, pulledUp = 3f * pulse(t, BED_BREATH_SECONDS))

    override val ownsTheFloor get() = true
    override val sparkles get() = false
}

/** He cannot get comfortable: rolls one way, then the other, the blanket bunching, until he finds the spot and sighs. */
private object BedTossing : MoAct("sleepy_bed_tossing", MoOccasion.Sleepy, intensity = MoIntensity.Strong) {
    /** How far the blanket slides with him. */
    private const val SLIDE = 40f

    private fun side(v: MoVariant) = v.side(salt = 1)

    private fun progress(t: Float, v: MoVariant) = cycle(t, v.between(8f, 9.5f, salt = 2))

    /** Which way he lies: 1 on his first side, -1 on the other, and a little past the middle once he has found the spot. */
    private fun roll(p: Float) = 1f - 2f * span(p, 0.12f, 0.24f) + 1.15f * span(p, 0.36f, 0.48f) + 0.85f * span(p, 0.92f, 1f)

    private fun turning(p: Float) = hump(p, 0.12f, 0.12f) + hump(p, 0.36f, 0.12f) + hump(p, 0.92f, 0.08f)

    private fun restless(p: Float) = p < 0.46f || p > 0.92f

    private fun sigh(p: Float) = span(p, 0.46f, 0.49f) * (1f - span(p, 0.53f, 0.57f))

    private fun pulledUp(p: Float) = 8f * abs(roll(p)) + 7f * turning(p)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val p = progress(t, v)
        val s = side(v)
        val roll = roll(p)
        val wiggle = span(p, 0.25f, 0.28f) * (1f - span(p, 0.33f, 0.36f))
        val sigh = sigh(p)
        val inBed = moInBed(
            t, body, tilt = s * roll * 8f + 2.5f * wiggle * wave(t, 0.35f),
            shift = Offset(s * roll * 18f, 0f), pulledUp = pulledUp(p),
        )
        return inBed.copy(
            leftEye = shutEye,
            rightEye = shutEye,
            brows = if (restless(p)) MoBrows(MoBrowKind.Worried) else MoBrows(),
            mouth = when {
                sigh > 0.05f -> MoMouth.Oh(size = 3f + 4f * sigh)
                restless(p) -> MoMouth.Pout
                else -> MoMouth.Smile(width = 10f, depth = 7f)
            },
            blinks = false,
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        nightAbove(t, v.side(salt = 3), moon = true)
        bedBehind()
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val k = (progress(t, v) - 0.47f) / 0.10f
        if (k !in 0f..1f) return
        val at = rig.mouth + Offset(-side(v) * (14f + 44f * ease(k)), 6f + 8f * k)
        puff(at, 8f * (1f - 0.3f * k), MoPropSilver.copy(alpha = ease(k / 0.15f) * (1f - k * k)))
    }

    override fun DrawScope.foreground(t: Float, v: MoVariant) {
        val p = progress(t, v)
        val roll = roll(p)
        val slide = side(v) * roll * SLIDE
        bedBlanket(t, v, pulledUp(p), bunch = slide)
    }

    override val ownsTheFloor get() = true
    override val sparkles get() = false
}

/** Sat up with the blanket held open, he points down into the empty half of the bed and waits, half-lidded: you, bed, now. */
private object BedBlanketHeldOpen : MoAct("sleepy_bed_blanket_held_open", MoOccasion.Sleepy, intensity = MoIntensity.Strong) {
    private fun side(v: MoVariant) = v.side(salt = 1)

    private fun progress(t: Float, v: MoVariant) = cycle(t, v.between(7.5f, 9f, salt = 2))

    private fun yawn(p: Float) = span(p, 0.30f, 0.36f) * (1f - span(p, 0.44f, 0.49f))

    private fun shake(p: Float) = span(p, 0.50f, 0.52f) * (1f - span(p, 0.56f, 0.58f))

    /** Two little pushes of the pointing mitten, at the start and again after the yawn. */
    private fun jab(p: Float): Float {
        for (from in listOf(0.02f, 0.58f)) {
            val k = (p - from) / 0.08f
            if (k in 0f..1f) return abs(sin(k * 2f * PI.toFloat()))
        }
        return 0f
    }

    private fun holdingHand(t: Float, v: MoVariant): Offset {
        val p = progress(t, v)
        return Offset(CENTRE - side(v) * 91f, 301f + 14f * yawn(p) + 4f * wave(t, 0.18f) * shake(p))
    }

    private fun pointingHand(t: Float, v: MoVariant): Offset {
        val tap = jab(progress(t, v))
        return Offset(CENTRE + side(v) * (85f + 8f * tap), 339f + 14f * tap)
    }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val p = progress(t, v)
        val s = side(v)
        val yawn = yawn(p)
        val inBed = moInBed(t, body)
        val sittingUp = if (body == MoBodyKind.Low) 16f else 30f
        val shift = Offset(-s * 27f, -sittingUp - 7f * yawn)
        val pointing = bedArm(body, pointingHand(t, v), s, bend = 0.5f, shift)
        val holding = bedArm(body, holdingHand(t, v), -s, bend = 0.45f, shift)
        val eye = if (yawn > 0.5f) {
            shutEye
        } else {
            MoEye(open = mix(1f, 0.35f, span(yawn, 0f, 0.5f)), lidDrop = 0.5f, gaze = Offset(s * 0.65f, 0.65f))
        }
        return inBed.copy(
            leftEye = eye,
            rightEye = eye,
            brows = if (yawn > 0.5f) MoBrows(MoBrowKind.Raised, lift = 2f) else MoBrows(MoBrowKind.Flat),
            mouth = if (yawn > 0.02f) MoMouth.Yawn(open = yawn) else MoMouth.Flat(11f),
            leftArm = if (s > 0f) holding else pointing,
            rightArm = if (s > 0f) pointing else holding,
            legs = MoLegs(MoLeg(lift = 50f), MoLeg(lift = 50f)),
            offset = inBed.offset + shift,
            squash = 0f,
            flare = 6f * yawn,
            blinks = yawn < 0.5f,
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        nightAbove(t, -side(v), moon = false)
        bedBehind()
    }

    override fun DrawScope.foreground(t: Float, v: MoVariant) {
        val s = side(v)
        val grip = holdingHand(t, v)
        openBedBlanket(v, s, grip)
        mitten(MoPalettes.Warm, grip, -s)
    }

    override val ownsTheFloor get() = true
    override val sparkles get() = false
}

internal val sleepyBedActs1: List<MoAct> = listOf(
    BedOneEyeOpen,
    BedShush,
    BedLightsOut,
    BedPlush,
    BedTossing,
    BedBlanketHeldOpen,
)
