package com.example.workouttracker.feature.mo.acts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.example.workouttracker.core.ui.theme.MoPropBlue
import com.example.workouttracker.core.ui.theme.MoPropBlueDark
import com.example.workouttracker.core.ui.theme.MoPropBrown
import com.example.workouttracker.core.ui.theme.MoPropCream
import com.example.workouttracker.core.ui.theme.MoPropGold
import com.example.workouttracker.core.ui.theme.MoPropGrey
import com.example.workouttracker.core.ui.theme.MoPropGreyDark
import com.example.workouttracker.core.ui.theme.MoPropMoon
import com.example.workouttracker.core.ui.theme.MoPropOrange
import com.example.workouttracker.core.ui.theme.MoPropPink
import com.example.workouttracker.core.ui.theme.MoPropPinkDark
import com.example.workouttracker.core.ui.theme.MoPropPurple
import com.example.workouttracker.core.ui.theme.MoPropPurpleDark
import com.example.workouttracker.core.ui.theme.MoPropSand
import com.example.workouttracker.core.ui.theme.MoPropShadow
import com.example.workouttracker.core.ui.theme.MoPropSilver
import com.example.workouttracker.core.ui.theme.MoPropSteel
import com.example.workouttracker.core.ui.theme.MoPropTeal
import com.example.workouttracker.core.ui.theme.MoPropTealDark
import com.example.workouttracker.core.ui.theme.MoPropWhite
import com.example.workouttracker.core.ui.theme.MoPropWood
import com.example.workouttracker.core.ui.theme.MoPropWoodDark
import com.example.workouttracker.core.ui.theme.MoPropYellow
import com.example.workouttracker.domain.mo.MoIntensity
import com.example.workouttracker.domain.mo.MoOccasion
import com.example.workouttracker.feature.mo.CENTRE
import com.example.workouttracker.feature.mo.FEET
import com.example.workouttracker.feature.mo.MoArm
import com.example.workouttracker.feature.mo.MoBodies
import com.example.workouttracker.feature.mo.MoBodyKind
import com.example.workouttracker.feature.mo.MoBrowKind
import com.example.workouttracker.feature.mo.MoBrows
import com.example.workouttracker.feature.mo.MoEye
import com.example.workouttracker.feature.mo.MoEyeShape
import com.example.workouttracker.feature.mo.MoMouth
import com.example.workouttracker.feature.mo.MoPose
import com.example.workouttracker.feature.mo.MoRig
import com.example.workouttracker.feature.mo.grippingMitten
import com.example.workouttracker.feature.mo.block
import com.example.workouttracker.feature.mo.chunk
import com.example.workouttracker.feature.mo.curve
import com.example.workouttracker.feature.mo.cycle
import com.example.workouttracker.feature.mo.ease
import com.example.workouttracker.feature.mo.floorShadow
import com.example.workouttracker.feature.mo.limb
import com.example.workouttracker.feature.mo.moIdle
import com.example.workouttracker.feature.mo.oval
import com.example.workouttracker.feature.mo.puff
import com.example.workouttracker.feature.mo.shape
import com.example.workouttracker.feature.mo.wave
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * More mornings: a flame that is his own kettle, and a flame that gets the sun up by hand.
 */

/** Eased progress of [p] through the window from [from] to [to]: 0 before it, 1 after it. */
private fun ramp(p: Float, from: Float, to: Float): Float = ease((p - from) / max(to - from, 0.001f))

/** He hugs a mug to his belly to heat it himself, sips when it steams, and warms it up again. */
private object HisOwnKettle : MoAct("morning_his_own_kettle", MoOccasion.Morning, intensity = MoIntensity.Mild) {
    private const val PERIOD = 6f

    /** How hot the drink is: full while he hugs it, thin after the sip, back to full by the wrap. */
    private fun warmth(p: Float) = 1f - 0.5f * ramp(p, 0.26f, 0.34f) + 0.5f * ramp(p, 0.80f, 1f)

    private fun sip(p: Float) = ramp(p, 0.30f, 0.37f) * (1f - ramp(p, 0.45f, 0.53f))

    /** Where the mug rides: low on his belly, a clear span of face above it, clear of the floor below. */
    private fun mugY(body: MoBodyKind) = if (body == MoBodyKind.Low) 388f else 381f

    /** The low body is smaller all over, and so is the mug it can hold. */
    private fun mugScale(body: MoBodyKind) = if (body == MoBodyKind.Low) 0.82f else 1f

    /** How far the mug travels to reach his lips, which sit lower on the low body. */
    private fun sipLift(body: MoBodyKind) = if (body == MoBodyKind.Low) 28f else 36f

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val p = cycle(t, PERIOD)
        val w = warmth(p)
        val q = sip(p)
        val low = body == MoBodyKind.Low
        val pleased = if (low) 0f else ramp(p, 0.53f, 0.59f) * (1f - ramp(p, 0.76f, 0.86f))
        // One warm flare that straddles the wrap, as he hugs the mug close again.
        val hug = (p - 0.8f).mod(1f) / 0.3f
        val flare = if (hug < 1f) 0.5f - 0.5f * cos(hug * 2f * PI.toFloat()) else 0f
        val idle = moIdle(t, energy = 0.6f)
        val eye = when {
            q > 0.4f -> MoEye(shape = MoEyeShape.Shut)
            else -> MoEye(lidDrop = 0.45f - 0.15f * pleased, gaze = Offset(0f, 0.8f * (1f - pleased)))
        }
        val shoulderY = MoBodies.of(body).shoulderY
        val hold = MoArm(Offset(29f * mugScale(body) - MoBodies.of(body).shoulderHalf, mugY(body) + 2f - shoulderY - sipLift(body) * q - 3f * w), bend = 0.3f + 0.15f * w)
        return idle.copy(
            leftEye = eye,
            rightEye = eye,
            mouth = when {
                q > 0.9f -> MoMouth.Hidden
                low -> MoMouth.Smile(12f, 8f)
                else -> MoMouth.Smile(15f + 3f * pleased, 12f + 3f * pleased)
            },
            leftArm = hold,
            rightArm = hold,
            armsInFront = true,
            flare = 8f * flare,
            squash = idle.squash + 0.015f * w,
            tilt = 2f * wave(t, 3f),
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val p = cycle(t, PERIOD)
        val w = warmth(p)
        // Never his own yellow: a mug the colour of his belly would vanish into it.
        val (mug, mugDark) = v.of(
            listOf(MoPropTeal to MoPropTealDark, MoPropPink to MoPropPinkDark, MoPropBlue to MoPropBlueDark, MoPropPurple to MoPropPurpleDark),
            salt = 1,
        )
        val handleSide = if (v.chance(50, salt = 2)) 1f else -1f
        val steamSide = -handleSide
        val c = (rig.leftHand + rig.rightHand) / 2f + Offset(0f, -2f)
        val scale = mugScale(rig.body.kind)
        val width = 62f * scale
        val height = 46f * scale

        // The handle bows out past the mitten on its side, so his hand reads as being through it.
        curve(
            c + Offset(handleSide * width * 0.48f, -15f * scale),
            c + Offset(handleSide * width * 1.34f, scale),
            c + Offset(handleSide * width * 0.48f, 17f * scale),
            mug,
            9f * scale,
        )
        chunk(c, width, height, mug, mugDark, corner = 10f * scale, drop = 5f)
        if (v.chance(50, salt = 4)) block(c + Offset(0f, 5f * scale), width, 9f * scale, v.of(listOf(MoPropCream, MoPropWhite), salt = 6), corner = 0f)
        // A pale ring of rim around the dark drink, so the opening never reads as a second mouth.
        val rim = c + Offset(0f, -height * 0.41f)
        oval(rim, width * 0.42f, 6.5f * scale, MoPropCream)
        oval(rim, width * 0.42f - 5f * scale, 4f * scale, MoPropBrown)
        if (v.chance(30, salt = 5)) {
            // The tag hangs over the lip and down the outside, away from the handle. Drawn across the
            // mug's face it would read as a label painted on the cup instead of a tea bag.
            val swing = wave(t, 3f) * 1.5f
            val tag = c + Offset(steamSide * (width * 0.5f + 8f * scale) + swing, -height * 0.12f)
            limb(rim + Offset(steamSide * width * 0.4f, 0f), tag, MoPropCream, 2.5f)
            block(tag + Offset(0f, 6f * scale), 9f * scale, 11f * scale, MoPropCream, corner = 2f)
        }

        grippingMitten(rig, -1f)
        grippingMitten(rig, 1f)

        val count = 2 + v.pick(2, salt = 3)
        val lifted = sipLift(rig.body.kind) * sip(p)
        for (index in 0 until count) {
            // The phase puts the youngest puff just clear of the rim, so t = 0 already shows steam
            // standing on the mug rather than two clouds that have drifted off beside his head.
            val rise = cycle(t, 2f, offset = index.toFloat() / count + 0.07f)
            // A puff leaves the rim, then swings wide of his cheek and climbs beside his face.
            // Lifting the mug for the sip carries the young puffs with it and leaves the old ones behind.
            val at = c + Offset(
                steamSide * (20f + 92f * ease(rise * 2.4f)),
                -height * 0.52f - 150f * rise + lifted * rise,
            )
            // Solid puffs that swell and then shrink away: overlapping circles would show through a fade.
            // A puff is almost its full width the moment it leaves the rim, so the steam is attached
            // to the mug rather than a string of specks that only fills out beside his head.
            val size = (12f + 12f * sqrt(rise)) * min(1f, rise * 12f) * (1f - ramp(rise, 0.62f, 1f))
            puff(at, size * w, MoPropWhite)
        }
    }

    override val sparkles get() = false
}

/** Bleary but determined, he hauls a little sun up its pole; one big yawn, and it slides back down. */
private object HoistingTheSun : MoAct("morning_hoisting_the_sun", MoOccasion.Morning) {
    private const val PERIOD = 6.6f
    private const val HAULING = 0.48f
    private const val PULL = 0.4f

    /** The top of the pole, where the pulley sits. */
    private const val PULLEY_Y = 34f

    private fun side(v: MoVariant) = v.of(listOf(-1f, 1f), salt = 1)
    private fun hauls(v: MoVariant) = 2 + v.pick(2, salt = 4)

    // Far enough out that his fist clears the pole, near enough that the ray tips and their round
    // stroke stay inside the view box on the mirrored side.
    private fun poleX(v: MoVariant) = CENTRE + side(v) * 138f

    /** The sun hangs on a short arm towards him, where there is room for it to be big. */
    private fun sunX(v: MoVariant) = poleX(v) - side(v) * 20f

    /** His own strand of the rope, a hand's width nearer to him than the sun's. */
    private fun ropeX(v: MoVariant) = sunX(v) - side(v) * 12f

    private fun sunCentre(t: Float, v: MoVariant) = Offset(sunX(v), 236f - 156f * sunHeight(cycle(t, PERIOD), hauls(v)))

    /** Which haul he is on, and how far into it, while [p] is in the hauling part of the loop. */
    private fun haul(p: Float, n: Int): Pair<Int, Float> {
        val at = (p / HAULING * n).coerceIn(0f, n - 0.001f)
        return at.toInt() to at - at.toInt()
    }

    /** How far the pull of one haul has come: the sun only climbs while his hand goes down. */
    private fun pulled(x: Float) = ease(x / PULL)

    /** How hard he is pulling right now, from 0 to 1, whichever haul it belongs to. */
    private fun effort(p: Float, n: Int): Float {
        val (_, x) = haul(p, n)
        return when {
            p < HAULING && x < PULL -> sin(x / PULL * PI.toFloat())
            p >= 0.80f && p < 0.92f -> sin((p - 0.80f) / 0.12f * PI.toFloat())
            else -> 0f
        }
    }

    /** His hand on the rope: 0 high, 1 low. The slip drags it up past where he usually grips. */
    private fun hand(p: Float, n: Int): Float = when {
        p < HAULING -> {
            val (index, x) = haul(p, n)
            val regrip = ease((x - PULL) / (1f - PULL))
            // After the last haul he keeps his hand down: the sun is up and he holds it there.
            if (x < PULL) pulled(x) else 1f - regrip * if (index == n - 1) 0.2f else 1f
        }
        p < 0.62f -> 0.8f
        p < 0.80f -> 0.8f - 1.1f * ramp(p, 0.62f, 0.72f)
        p < 0.92f -> -0.3f + 1.3f * ramp(p, 0.80f, 0.92f)
        else -> 1f - ramp(p, 0.92f, 1f)
    }

    /** How high the sun is, from 0 at the foot of the pole to 1 under the pulley. */
    private fun sunHeight(p: Float, n: Int): Float = when {
        p < HAULING -> haul(p, n).let { (index, x) -> 0.5f + 0.5f * (index + pulled(x)) / n }
        p < 0.60f -> 1f
        p < 0.72f -> ((p - 0.60f) / 0.12f).let { 1f - 0.7f * it * it }
        p < 0.80f -> ((p - 0.72f) / 0.08f).let { 0.3f + 0.07f * abs(sin(it * 2f * PI.toFloat())) * (1f - it) }
        else -> 0.3f + 0.2f * ramp(p, 0.80f, 0.92f)
    }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val p = cycle(t, PERIOD)
        val n = hauls(v)
        val s = side(v)
        val effort = effort(p, n)
        val yawn = ramp(p, 0.47f, 0.53f) * (1f - ramp(p, 0.57f, 0.62f))
        val startled = p >= 0.62f && p < 0.80f

        val bleary = MoEye(lidDrop = 0.28f, gaze = Offset(s * 0.9f, -1f))
        val eye = when {
            yawn > 0.25f -> MoEye(shape = MoEyeShape.Shut)
            startled -> MoEye(iris = 0.5f, gaze = Offset(s * 0.9f, -1f + 1.6f * ramp(p, 0.62f, 0.72f)))
            else -> bleary
        }
        // His fist reaches out past his own silhouette, where a mitten on the rope can be seen at all.
        val hauling = MoArm(Offset(28f, -40f + 48f * hand(p, n)), bend = 0.5f)
        // The free arm leaves his hip for a stretch while he yawns.
        val free = MoArm(Offset(-6f + 32f * yawn, 30f - 86f * yawn), bend = 0.75f - 0.45f * yawn)
        val idle = moIdle(t, energy = 0.7f)
        return idle.copy(
            leftEye = eye,
            rightEye = eye,
            brows = when {
                startled -> MoBrows(MoBrowKind.Raised, lift = 5f)
                yawn > 0.25f -> MoBrows()
                else -> MoBrows(MoBrowKind.Flat)
            },
            mouth = when {
                yawn > 0.05f -> MoMouth.Yawn(open = yawn)
                startled -> MoMouth.Oh(7f)
                effort > 0.45f -> MoMouth.Gritted
                else -> MoMouth.Flat(12f)
            },
            leftArm = if (s < 0f) hauling else free,
            rightArm = if (s < 0f) free else hauling,
            armsInFront = true,
            flare = 5f * effort + 10f * yawn,
            // The rope is drawn from the pulley to his mitten, so his transform has to be the identity:
            // a lean, a sink or even his breath would carry the end drawn at the pulley off the pulley.
            // The pull reads in the arm, the flare and the gritted teeth instead.
            offset = Offset.Zero,
            squash = 0f,
            tilt = 0f,
            blinks = p < 0.58f || p >= 0.82f,
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val s = side(v)
        val x = poleX(v)
        floorShadow(x, 16f, MoPropShadow)
        // Steel rather than silver: the pole is scenery, and the sun and the rope should lead the eye.
        block(Offset(x, (PULLEY_Y + FEET) / 2f), 10f, FEET - PULLEY_Y, MoPropSteel, corner = 5f)
        chunk(Offset(x, FEET - 7f), 30f, 14f, MoPropGrey, MoPropGreyDark, corner = 6f)
        // One bob per loop, so the cloud is where it started when the act wraps.
        if (v.chance(40, salt = 5)) puff(Offset(x - s * 64f, 48f + wave(t, PERIOD) * 2f), 16f, MoPropWhite)
        // The arm the sun hangs from, and the far strand of the rope running down to it.
        limb(Offset(x, PULLEY_Y), Offset(sunX(v), PULLEY_Y), MoPropGreyDark, 6f)
        limb(Offset(sunX(v), PULLEY_Y + 2f), sunCentre(t, v), MoPropSand, 4f)
        drawCircle(MoPropGreyDark, 9f, Offset(sunX(v), PULLEY_Y))
        drawCircle(MoPropSilver, 4f, Offset(sunX(v), PULLEY_Y))
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val p = cycle(t, PERIOD)
        val s = side(v)
        val mitten = if (s < 0f) rig.leftHand else rig.rightHand
        val top = Offset(ropeX(v), PULLEY_Y + 4f)
        val grip = mitten + Offset(0f, -7f)
        limb(top, grip, MoPropSand, 5f)
        chunk(mitten, 38f, 11f, MoPropWood, MoPropWoodDark, corner = 5f, drop = 4f)
        val swing = wave(t, 1.1f) * 4f
        curve(mitten + Offset(0f, 9f), mitten + Offset(swing * 0.3f, 22f), mitten + Offset(swing, 32f), MoPropSand, 4f)
        grippingMitten(rig, s)

        // While it slips, dashes run up the rope towards the pulley.
        val slip = (p - 0.60f) / 0.12f
        if (slip > 0f && slip < 1f) {
            val rope = top - grip
            val along = rope / max(rope.getDistance(), 1f)
            for (index in 0..2) {
                val run = (slip * 2f + index / 3f).mod(1f)
                val at = grip + rope * (0.1f + 0.7f * run)
                limb(at, at + along * 12f, MoPropWoodDark.copy(alpha = sin(slip * PI.toFloat()) * (1f - run)), 3.5f)
            }
        }
    }

    /** The sun rides in front of everything, so his flame never swallows it on the way down. */
    override fun DrawScope.foreground(t: Float, v: MoVariant) {
        val (sun, core) = v.of(listOf(MoPropGold to MoPropYellow, MoPropYellow to MoPropMoon, MoPropOrange to MoPropGold), salt = 2)
        val rays = if (v.chance(50, salt = 3)) 6 else 8
        val centre = sunCentre(t, v)
        // One ray's spacing per loop, so the turning rays wrap without a jump.
        val turn = cycle(t, PERIOD) * 2f * PI.toFloat() / rays
        for (index in 0 until rays) {
            val a = turn + index * 2f * PI.toFloat() / rays
            fun at(angle: Float, r: Float) = (centre.x + cos(angle) * r) to (centre.y + sin(angle) * r)
            // Stubby rays on a fat disc: a sun, and not a star.
            shape(sun, at(a - 0.3f, 26f), at(a, 36f), at(a + 0.3f, 26f))
        }
        drawCircle(sun, 28f, centre)
        drawCircle(core, 19f, centre)
    }

    override val ownsTheFloor get() = true
    override val sparkles get() = false
}

internal val morningActs2: List<MoAct> = listOf(
    HisOwnKettle,
    HoistingTheSun,
)
