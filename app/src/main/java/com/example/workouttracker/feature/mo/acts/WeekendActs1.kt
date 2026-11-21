package com.example.workouttracker.feature.mo.acts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import com.example.workouttracker.core.ui.theme.MoFoam
import com.example.workouttracker.core.ui.theme.MoGloss
import com.example.workouttracker.core.ui.theme.MoPropBlue
import com.example.workouttracker.core.ui.theme.MoPropBlueDark
import com.example.workouttracker.core.ui.theme.MoPropBrown
import com.example.workouttracker.core.ui.theme.MoPropCharcoal
import com.example.workouttracker.core.ui.theme.MoPropCream
import com.example.workouttracker.core.ui.theme.MoPropGold
import com.example.workouttracker.core.ui.theme.MoPropGoldDark
import com.example.workouttracker.core.ui.theme.MoPropGreen
import com.example.workouttracker.core.ui.theme.MoPropGreenDark
import com.example.workouttracker.core.ui.theme.MoPropGrey
import com.example.workouttracker.core.ui.theme.MoPropGreyDark
import com.example.workouttracker.core.ui.theme.MoPropInk
import com.example.workouttracker.core.ui.theme.MoPropLeaf
import com.example.workouttracker.core.ui.theme.MoPropMint
import com.example.workouttracker.core.ui.theme.MoPropOrange
import com.example.workouttracker.core.ui.theme.MoPropPink
import com.example.workouttracker.core.ui.theme.MoPropPinkDark
import com.example.workouttracker.core.ui.theme.MoPropPurple
import com.example.workouttracker.core.ui.theme.MoPropPurpleDark
import com.example.workouttracker.core.ui.theme.MoPropRed
import com.example.workouttracker.core.ui.theme.MoPropRedDark
import com.example.workouttracker.core.ui.theme.MoPropShadow
import com.example.workouttracker.core.ui.theme.MoPropSilver
import com.example.workouttracker.core.ui.theme.MoPropSky
import com.example.workouttracker.core.ui.theme.MoPropSnow
import com.example.workouttracker.core.ui.theme.MoPropSteel
import com.example.workouttracker.core.ui.theme.MoPropTeal
import com.example.workouttracker.core.ui.theme.MoPropTealDark
import com.example.workouttracker.core.ui.theme.MoPropWhite
import com.example.workouttracker.core.ui.theme.MoPropWood
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
import com.example.workouttracker.feature.mo.MoLeg
import com.example.workouttracker.feature.mo.MoLegs
import com.example.workouttracker.feature.mo.MoMouth
import com.example.workouttracker.feature.mo.MoPose
import com.example.workouttracker.feature.mo.MoRig
import com.example.workouttracker.feature.mo.block
import com.example.workouttracker.feature.mo.chunk
import com.example.workouttracker.feature.mo.curve
import com.example.workouttracker.feature.mo.cycle
import com.example.workouttracker.feature.mo.ease
import com.example.workouttracker.feature.mo.fading
import com.example.workouttracker.feature.mo.floorShadow
import com.example.workouttracker.feature.mo.grippingMitten
import com.example.workouttracker.feature.mo.limb
import com.example.workouttracker.feature.mo.mitten
import com.example.workouttracker.feature.mo.moIdle
import com.example.workouttracker.feature.mo.oval
import com.example.workouttracker.feature.mo.polyline
import com.example.workouttracker.feature.mo.puff
import com.example.workouttracker.feature.mo.pulse
import com.example.workouttracker.feature.mo.shape
import com.example.workouttracker.feature.mo.sparkle
import com.example.workouttracker.feature.mo.twitch
import com.example.workouttracker.feature.mo.wave
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/*
 * Weekend, part one: the two days nothing is due, so he is free to potter about with sunglasses, a
 * bell, a flower, a cold drink, a beach ball and a grill. Each act is one loop, and the timing is
 * written as parts of that loop: `span` eases between two of them and `bell` is one soft bump.
 */

/** Eases from 0 to 1 while [p] runs from [from] to [to], and holds either end outside of that. */
private fun span(p: Float, from: Float, to: Float): Float = ease((p - from) / max(to - from, 0.001f))

/** One smooth bump: 1 at [x] = 0, down to 0 at -1 and 1, and 0 beyond. */
private fun bell(x: Float): Float = if (abs(x) < 1f) cos(x * PI.toFloat() / 2f).let { it * it } else 0f

private fun mix(from: Float, to: Float, k: Float): Float = from + (to - from) * k

private fun mix(from: Offset, to: Offset, k: Float): Offset = Offset(mix(from.x, to.x, k), mix(from.y, to.y, k))

private fun mix(from: MoArm, to: MoArm, k: Float): MoArm = MoArm(mix(from.reach, to.reach, k), mix(from.bend, to.bend, k))

/** A point [k] of the way along the quadratic that [curve] draws, for hanging things off a stem. */
private fun quadPoint(from: Offset, control: Offset, to: Offset, k: Float): Offset {
    val a = (1f - k) * (1f - k)
    return from * a + control * (2f * k * (1f - k)) + to * (k * k)
}

/** A unit vector from [from] to [to]; a zero distance would be a NaN pose, so it never divides by it. */
private fun aim(from: Offset, to: Offset): Offset = (to - from) / max((to - from).getDistance(), 0.001f)

/**
 * Sunglasses on a face with no nose or ears: they creep down until the top of both eyes peeks over
 * the rims, he pushes them back with one mitten, stands cool for a beat, and they start to slip again.
 */
private object WeekendSunglasses : MoAct(
    "weekend_sunglasses_with_no_nose",
    MoOccasion.Weekend,
    bodies = setOf(MoBodyKind.Tall),
    intensity = MoIntensity.Mild,
) {
    private const val PERIOD = 5.6f

    /** The loop read from the peek: cross-eyed at the rims, up at us, push, cool, slow slip. */
    private fun phase(t: Float) = cycle(t, PERIOD, offset = 0.18f)

    /** How far down his face they sit: 1 fully slipped, 0 just pushed back up. */
    private fun slide(p: Float) = 1f - span(p, 0.20f, 0.30f) + span(p, 0.55f, 1f)

    private fun pusherSide(v: MoVariant) = if (v.chance(50, salt = 3)) 1f else -1f

    /** The mitten is on the frame before it starts to rise at p 0.20, so it plainly does the pushing. */
    private fun push(p: Float) = span(p, 0.10f, 0.18f) * (1f - span(p, 0.32f, 0.40f))

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val p = phase(t)
        val s = pusherSide(v)
        val down = slide(p)
        // He squints down at the rims from the end of the slip, then looks up at us over them.
        val cross = if (p > 0.5f) span(p, 0.90f, 1f) else 1f - span(p, 0.08f, 0.14f)
        val up = span(p, 0.10f, 0.16f) * (1f - span(p, 0.20f, 0.26f))
        val cool = span(p, 0.30f, 0.38f) * (1f - span(p, 0.50f, 0.60f))
        // The mitten lands on the outer half of the lens, where it shows against the dark glass.
        val pusher = mix(MoArm.Down, MoArm(Offset(-38f, -36f + 28f * down), bend = 0.5f), push(p))
        val gaze = Offset(0.6f * cross, 0.9f * cross - 0.6f * up)
        return moIdle(t).copy(
            leftEye = MoEye(gaze = gaze),
            rightEye = MoEye(gaze = Offset(-gaze.x, gaze.y)),
            brows = if (up > 0.5f) MoBrows(MoBrowKind.Raised, lift = 3f) else MoBrows(),
            mouth = if (cross > 0.5f) MoMouth.Flat(10f) else MoMouth.Smile(12f - up, 10f - 2f * up),
            leftArm = if (s < 0f) pusher else MoArm.Hip,
            rightArm = if (s > 0f) pusher else MoArm.Hip,
            armsInFront = true,
            flare = 6f * bell((p - 0.29f) / 0.05f),
            tilt = -s * 3f * cool,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val p = phase(t)
        // At the bottom of the slip the rims sit below the middle of his eyes and beside his mouth.
        val drop = Offset(0f, slide(p) * 28f)
        // No yellow: a yellow frame would vanish into his belly.
        val frame = v.of(listOf(MoPropRed, MoPropTeal, MoPropPink, MoPropSky), salt = 2)
        val round = v.chance(50, salt = 1)
        val leftToRight = v.chance(50, salt = 4)
        limb(rig.leftEye + drop + Offset(26f, -8f), rig.rightEye + drop + Offset(-26f, -8f), frame, 6f)
        listOf(rig.leftEye, rig.rightEye).forEachIndexed { index, eye ->
            val centre = eye + drop
            if (round) {
                drawCircle(frame, 28f, centre)
                drawCircle(MoPropInk, 24f, centre)
            } else {
                block(centre, 60f, 56f, frame, corner = 14f)
                block(centre, 52f, 48f, MoPropInk, corner = 11f)
            }
            // One glint crosses the lenses while he stands there being cool, the far one a beat late.
            val g = (p - 0.34f - index * 0.03f) / 0.20f
            if (g > 0f && g < 1f) {
                val gx = if (leftToRight) -14f + 28f * g else 14f - 28f * g
                val ink = MoGloss.copy(alpha = sin(PI.toFloat() * g))
                limb(centre + Offset(gx - 5f, 10f), centre + Offset(gx + 5f, -10f), ink, 5f)
            }
        }
        // His arm is his own colour and disappears into him, so the pushing mitten is drawn on the lens.
        if (push(p) > 0.02f) {
            grippingMitten(rig, pusherSide(v))
        }
    }
}

/** He dings the bell on his own handlebar, and the sound startles him every single time. */
private object WeekendOwnBell : MoAct("weekend_startled_by_his_own_bell", MoOccasion.Weekend, bodies = setOf(MoBodyKind.Tall)) {
    private const val PERIOD = 3.6f

    private fun bellSide(v: MoVariant) = if (v.chance(50, salt = 2)) 1f else -1f

    /** The arm that never lets go of the bar, with the short flick of the thumb that rings it. */
    private fun bellArm(p: Float) = MoArm(Offset(24f, -30f + 6f * bell((p - 0.425f) / 0.025f)), bend = 0.4f, inFront = true)

    /** The fright: 0 at both ends of its window, so nothing of it is left over. */
    private fun startle(p: Float) = if (p > 0.45f && p < 0.62f) sin(PI.toFloat() * (p - 0.45f) / 0.17f) else 0f

    private fun giggle(p: Float) = span(p, 0.62f, 0.70f) * (1f - span(p, 0.88f, 0.98f))

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val p = cycle(t, PERIOD, offset = 0.50f)
        val s = bellSide(v)
        val k = startle(p)
        val glee = giggle(p)
        val lean = span(p, 0f, 0.40f) * (1f - span(p, 0.45f, 0.55f))
        // The ring runs on through his tip and dies away long after the rest of him has settled.
        val ring = if (p > 0.45f && p < 0.80f) 1f - (p - 0.45f) / 0.35f else 0f
        val eye = if (glee > 0.5f && p < 0.88f) {
            MoEye(shape = MoEyeShape.Happy)
        } else {
            MoEye(gaze = Offset(s * (0.9f - 0.4f * k), 0f), iris = 0.76f - 0.34f * k, scale = 1f + 0.12f * k, lidDrop = 0.3f * lean)
        }
        val free = mix(MoArm.Hip, MoArm(Offset(40f, -40f), bend = 0.3f), k)
        val idle = moIdle(t, energy = 1.1f)
        return idle.copy(
            leftEye = eye,
            rightEye = eye,
            brows = if (k > 0.25f) MoBrows(MoBrowKind.Raised, lift = 7f) else MoBrows(),
            mouth = if (glee > 0.05f) MoMouth.Grin(open = 0.75f * glee) else MoMouth.Oh(size = 5f + 7f * k),
            leftArm = if (s < 0f) bellArm(p) else free,
            rightArm = if (s > 0f) bellArm(p) else free,
            legs = MoLegs(MoLeg(lift = 8f * k), MoLeg(lift = 8f * k)),
            sway = idle.sway + wave(t, 0.09f) * 5f * ring,
            flare = 26f * k,
            squash = idle.squash + 0.04f * k,
            offset = Offset(-s * 4f * k, -v.between(12f, 20f, salt = 4) * k - 3f * glee * pulse(t, 0.24f)),
            tilt = s * 1.5f * lean + glee * wave(t, 0.35f) * 2.5f,
            blinks = k < 0.05f,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val p = cycle(t, PERIOD, offset = 0.50f)
        val s = bellSide(v)
        val (dome, ledge) = v.of(
            listOf(
                MoPropGold to MoPropGoldDark,
                MoPropTeal to MoPropTealDark,
                MoPropPink to MoPropPinkDark,
                MoPropSilver to MoPropSteel,
            ),
            salt = 1,
        )
        // He holds the bar up beside his shoulder, where there is room for a bell worth ringing.
        val h = if (s > 0f) rig.rightHand else rig.leftHand
        val mount = h + Offset(s * 20f, 7f)
        // The tube curves away from the rubber grip. The striker sits within thumb's reach.
        curve(h + Offset(-s * 36f, 18f), h + Offset(-s * 26f, 7f), mount + Offset(s * 12f, 0f), MoPropSteel, 8f)
        limb(h + Offset(-s * 13f, 8f), h + Offset(s * 6f, 7f), MoPropGreyDark, 13f)
        val b = mount + Offset(0f, -14f)
        val lever = b + Offset(-s * 20f, -15f + 6f * bell((p - 0.425f) / 0.025f))
        limb(mount, lever, MoPropSteel, 4f)
        block(lever, 9f, 6f, MoPropGreyDark, corner = 2f)
        oval(b, 17f, 12f, dome)
        block(b + Offset(0f, 9f), 36f, 7f, ledge, corner = 3f)
        grippingMitten(rig, s)
        drawCircle(MoPropWhite, 3f, b + Offset(-5f, -4f))
        // The ring itself: arcs leaving the dome up and outward, where nothing of him is in the way.
        val out = (p - 0.40f) / 0.35f
        if (out <= 0f || out >= 1f) return
        for (index in 0 until 2 + v.pick(2, salt = 3)) {
            val r = 17f + 13f * out + index * 6f
            // Straight up out of the dome: the only direction with room on either side of him.
            val mid = -90f * PI.toFloat() / 180f
            val half = 21f * PI.toFloat() / 180f
            val ink = MoPropCream.copy(alpha = 1f - out)
            curve(
                b + Offset(cos(mid - half), sin(mid - half)) * r,
                b + Offset(cos(mid), sin(mid)) * (r * 1.1547f),
                b + Offset(cos(mid + half), sin(mid + half)) * r,
                ink,
                5f,
            )
        }
    }
}

/**
 * The potted sunflower beside him has turned its face to him instead of the sky. He tests it by
 * leaning away, it bends after him a beat late, and he gives in, flattered, with a mitten at his cheek.
 */
private object WeekendSunflower : MoAct("weekend_sunflower_thinks_he_is_the_sun", MoOccasion.Weekend, intensity = MoIntensity.Mild) {
    private const val PERIOD = 6.4f

    /** 0 while they rest together, 1 while he holds himself away, and it rests at both ends. */
    private fun away(t: Float) = ease((pulse(t, PERIOD) - 0.25f) / 0.5f)

    private fun potSide(v: MoVariant) = if (v.chance(50, salt = 1)) 1f else -1f

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val s = potSide(v)
        val a = away(t)
        val idle = moIdle(t, energy = 0.8f)
        val eye = if (a > 0.12f) MoEye(gaze = Offset(s * 0.9f, -0.15f)) else MoEye(shape = MoEyeShape.Happy)
        val lowered = span(a, 0.05f, 0.20f)
        // The hand on his cheek is warm: there the cold tip of his mitten would be a dark spot on his face.
        val cheek = mix(MoArm.Cheek, MoArm.Down, lowered).copy(warm = 1f - lowered)
        return idle.copy(
            leftEye = eye,
            rightEye = eye,
            brows = if (a > 0.12f) MoBrows(MoBrowKind.Raised, lift = 2f, skew = 6f) else MoBrows(),
            mouth = if (a > 0.12f) MoMouth.Flat(9f) else MoMouth.Smile(18f, 16f),
            leftArm = if (s < 0f) cheek else MoArm.Down,
            rightArm = if (s > 0f) cheek else MoArm.Down,
            armsInFront = true,
            squash = idle.squash + 0.015f * (1f - a),
            // At rest he leans a little into it, which is what makes pulling away read as a test.
            offset = Offset(s * (3f - 23f * a), 0f),
            tilt = s * (2f - 7f * a),
        )
    }

    override fun DrawScope.foreground(t: Float, v: MoVariant) {
        val s = potSide(v)
        // Half a second behind him, which is what makes it look like it is following and not posing.
        val lag = away(t - 0.5f)
        val px = CENTRE + s * 124f
        val petal = v.of(listOf(MoPropYellow, MoPropGold, MoPropOrange, MoPropPink), salt = 2)
        val (pot, potLedge) = v.of(
            listOf(
                MoPropTeal to MoPropTealDark,
                MoPropRed to MoPropRedDark,
                MoPropBlue to MoPropBlueDark,
                MoPropPurple to MoPropPurpleDark,
            ),
            salt = 3,
        )
        floorShadow(px, 34f, MoPropShadow)
        val root = Offset(px, FEET - 52f)
        val bend = Offset(px + s * 6f, 330f)
        val head = Offset(px - s * (10f + 12f * lag), 253f + 8f * lag)
        if (v.chance(25, salt = 5)) {
            val bud = Offset(px + s * (10f + 10f * lag), 316f)
            curve(root, Offset(px + s * 14f, 356f), bud, MoPropGreen, 6f)
            drawCircle(MoPropGreenDark, 11f, bud)
            drawCircle(petal, 7f, bud)
        }
        curve(root, bend, head, MoPropGreen, 9f)
        for (index in 0 until 1 + v.pick(2, salt = 4)) {
            val at = quadPoint(root, bend, head, (index + 1) / 3f)
            val leafSide = if (index % 2 == 0) s else -s
            oval(at + Offset(leafSide * 13f, 2f * twitch(t, 3.1f, 0.3f, offset = index * 0.4f)), 18f, 8f, MoPropLeaf)
        }
        for (index in 0 until 12) {
            val angle = index / 12f * 2f * PI.toFloat()
            drawCircle(petal, 10f, head + Offset(cos(angle), sin(angle)) * 24f)
        }
        // The dark face is off-centre toward him, so it is plain who it is looking at.
        drawCircle(MoPropBrown, 17f, head + Offset(-s * (5f + 3f * lag), 0f))
        chunk(Offset(px, FEET - 24f), 56f, 44f, pot, potLedge, corner = 10f)
        block(Offset(px, FEET - 48f), 66f, 12f, pot, corner = 6f)
    }

    override val ownsTheFloor get() = true
}

/** One long greedy pull on the straw, and then the cold hits him right between the eyes. */
private object WeekendBrainFreeze : MoAct("weekend_brain_freeze", MoOccasion.Weekend, bodies = setOf(MoBodyKind.Tall)) {
    private const val PERIOD = 6.0f

    private fun phase(t: Float) = cycle(t, PERIOD, offset = 0.15f)

    /** The freeze itself, eased to 0 at both ends of its window so nothing of it accumulates. */
    private fun freeze(p: Float) = span(p, 0.38f, 0.46f) * (1f - span(p, 0.56f, 0.62f))

    // He breaks contact with the straw just before his jaw clenches.
    private fun pullAway(p: Float) = span(p, 0.34f, 0.40f) * (1f - span(p, 0.61f, 0.66f))

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val p = phase(t)
        val k = freeze(p)
        val suspicion = span(p, 0.78f, 0.86f)
        // His eyes fall shut again before the shut arc replaces them, so nothing snaps.
        val closing = span(p, 0.88f, 0.94f)
        val shut = p < 0.36f || p > 0.94f
        val eye = if (shut) {
            MoEye(shape = MoEyeShape.Shut)
        } else {
            MoEye(open = 1f - 0.78f * k, lid = 0.5f * k, gaze = Offset(0f, 0.9f * suspicion), lidDrop = 0.35f * suspicion + 0.55f * closing)
        }
        val hold = MoArm(Offset(-56f, 54f + 12f * pullAway(p)), bend = -0.4f)
        val idle = moIdle(t, energy = 0.8f)
        return idle.copy(
            leftEye = eye,
            rightEye = eye,
            brows = when {
                k > 0.3f -> MoBrows(MoBrowKind.Worried, lift = 2f)
                p > 0.62f && p < 0.80f -> MoBrows(MoBrowKind.Raised, lift = 4f)
                else -> MoBrows()
            },
            mouth = when {
                k > 0.3f -> MoMouth.Gritted
                p > 0.62f && p < 0.78f -> MoMouth.Oh(size = 6f)
                p > 0.78f && p < 0.94f -> MoMouth.Flat(9f)
                else -> MoMouth.Whistle
            },
            leftArm = hold,
            rightArm = hold,
            armsInFront = true,
            // The sucking rhythm divides the period exactly, so it carries over the seam.
            squash = idle.squash + 0.015f * pulse(t, 0.5f) - 0.03f * k,
            flare = -10f * k,
            shake = wave(t, 0.07f) * 1.6f * k,
            tilt = -4f * (suspicion - span(p, 0.92f, 1f)),
            blinks = false,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val p = phase(t)
        val k = freeze(p)
        val (cup, cupLedge) = v.of(
            listOf(
                MoPropRed to MoPropRedDark,
                MoPropTeal to MoPropTealDark,
                MoPropPink to MoPropPinkDark,
                MoPropBlue to MoPropBlueDark,
            ),
            salt = 1,
        )
        val c = (rig.leftHand + rig.rightHand) / 2f + Offset(0f, -4f)
        // Orange or lime: a lemon would be the colour of his own belly and vanish into it.
        val slice = if (v.chance(50, salt = 3)) MoPropOrange else MoPropGreen
        drawCircle(slice, 13f, c + Offset(-26f, -32f))
        drawCircle(MoPropCream, 8f, c + Offset(-26f, -32f))
        chunk(c, 52f, 58f, cup, cupLedge, corner = 10f)
        block(c + Offset(0f, -33f), 58f, 11f, MoPropWhite, corner = 5f)

        // The straw leaves the lid and ends on the ring of his pursed mouth, until the cold sends him off it.
        val strawFoot = c + Offset(14f, -36f)
        val away = pullAway(p)
        val strawBend = rig.mouth + Offset(13f + 5f * away, 14f + 13f * away)
        val strawTip = rig.mouth + Offset(10f + 12f * away, 6f + 18f * away)
        val strawInk = v.of(listOf(MoPropWhite, MoPropMint, MoPropSky), salt = 2)
        polyline(strawInk, 5f, listOf(strawFoot, strawBend, strawTip))
        // Both mittens again, over the cup, so he plainly holds it instead of standing behind it.
        grippingMitten(rig, -1f)
        grippingMitten(rig, 1f)
        val sipping = (1f - span(p, 0.32f, 0.40f) + span(p, 0.94f, 1f)).coerceIn(0f, 1f)
        for (index in 0..1) {
            val u = cycle(t, 0.6f, offset = index * 0.5f)
            drawCircle(MoPropWhite.copy(alpha = 0.7f * sipping), 4.5f, quadPoint(strawFoot, strawBend, strawTip, u))
        }

        // Frost stars popping off his tip, in place of Blazing's gold ones.
        if (k > 0.01f) {
            for (index in 0 until 1 + v.pick(2, salt = 4)) {
                val out = ((p - 0.38f) / 0.24f + index * 0.4f).mod(1f)
                sparkle(rig.tip + Offset((index * 2 - 1) * 36f, -4f - out * 24f), 14f * (1f - out), 1f, MoPropSky.copy(alpha = k))
            }
        }
        val mist = span(p, 0.62f, 0.78f)
        if (mist > 0f && mist < 1f) {
            puff(rig.mouth + Offset(30f + mist * 30f, -mist * 10f), 7f + 6f * mist, MoPropSnow.copy(alpha = 0.8f * sin(PI.toFloat() * mist)))
        }
    }

    override val sparkles get() = false
}

/** His breath is his fire, so blowing up a beach ball costs him a size every puff, and then he is dizzy. */
private object WeekendBeachBall : MoAct("weekend_blowing_up_the_beach_ball", MoOccasion.Weekend, bodies = setOf(MoBodyKind.Tall)) {
    private const val PERIOD = 6.4f

    /** How much of the period is spent blowing, before the dizzy spell and the shake-off. */
    private const val BLOWING = 0.60f

    /** The offset lands t = 0 in the middle of the second blow whether there are three puffs or four. */
    private fun phase(t: Float) = cycle(t, PERIOD, offset = 0.24f)

    private fun puffs(v: MoVariant) = 3 + v.pick(2, salt = 2)

    /** How full the ball is, a quarter at both ends of the loop: each blow adds a step, the sag takes it all back. */
    private fun fill(p: Float, n: Int): Float {
        if (p >= BLOWING) return 1f - 0.75f * span(p, 0.66f, 0.98f)
        val step = BLOWING / n
        val index = (p / step).toInt().coerceIn(0, n - 1)
        return 0.25f + 0.75f * (index + ease((p - index * step) / step / 0.65f)) / n
    }

    /** 1 while he is blowing out, 0 while he draws the next breath; 0 at the start and end of every puff. */
    private fun blow(p: Float, n: Int): Float {
        if (p >= BLOWING) return 0f
        val step = BLOWING / n
        val q = (p / step).let { it - it.toInt() }
        return span(q, 0.02f, 0.18f) * (1f - span(q, 0.60f, 0.72f))
    }

    private fun dizzy(p: Float) = span(p, 0.60f, 0.66f) * (1f - span(p, 0.86f, 0.92f))

    private fun radius(f: Float) = 16f + 24f * f

    /** How limp it still is: a flat bag at a quarter full, a proper ball when it is round. */
    private fun squeeze(f: Float) = 0.7f + 0.3f * f

    private fun centre(mouth: Offset, f: Float, dizzy: Float): Offset {
        val r = radius(f)
        // Kept low and out from the mouth ring, so it never creeps under his eye at full size.
        return mouth + Offset(20f + r, 12f + r * 0.6f) + Offset(14f, 10f) * dizzy
    }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val p = phase(t)
        val n = puffs(v)
        val f = fill(p, n)
        val out = blow(p, n)
        val spin = dizzy(p)
        // Between the puffs he is filling himself back up, which is also how the loop closes.
        val breath = (1f - out) * (1f - span(p, 0.58f, 0.64f) + span(p, 0.96f, 1f)).coerceIn(0f, 1f)
        val wobble = if (v.chance(50, salt = 4)) 1f else -1f
        val shiver = span(p, 0.86f, 0.90f) * (1f - span(p, 0.94f, 1f))
        val figure = MoBodies.of(body)
        val grip = centre(figure.mouth, f, spin) + Offset(radius(f) * 0.55f, radius(f) * squeeze(f) * 0.55f)
        val idle = moIdle(t)
        val eye = if (spin > 0.5f) MoEyeShape.Spiral else MoEyeShape.Round
        return idle.copy(
            leftEye = MoEye(shape = eye, gaze = Offset(0.8f, 0.55f), iris = 0.76f - 0.1f * breath),
            rightEye = MoEye(shape = eye, gaze = Offset(0.8f, 0.55f), iris = 0.76f - 0.1f * breath),
            brows = if (out > 0.4f) MoBrows(MoBrowKind.Flat) else MoBrows(),
            mouth = when {
                out > 0.5f -> MoMouth.Whistle
                spin > 0.5f -> MoMouth.Wavy
                else -> MoMouth.Oh(size = 6f + breath)
            },
            leftArm = mix(MoArm.Hip, MoArm(Offset(40f, -4f + wave(t, 0.5f) * 6f), bend = 0.1f), spin),
            rightArm = MoArm(Offset(grip.x - CENTRE - figure.shoulderHalf, grip.y - figure.shoulderY), bend = 0.4f),
            armsInFront = true,
            squash = idle.squash - 0.05f * out + 0.04f * breath,
            flare = -8f * out + 12f * breath,
            tilt = wobble * 5f * wave(t, 1.1f) * spin,
            shake = wave(t, 0.08f) * 2f * shiver,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val p = phase(t)
        val n = puffs(v)
        val f = fill(p, n)
        val sag = dizzy(p)
        val r = radius(f)
        val b = centre(rig.mouth, f, sag)
        val colours = v.of(
            listOf(
                listOf(MoPropRed, MoPropYellow, MoPropBlue),
                listOf(MoPropPink, MoPropTeal, MoPropYellow),
                listOf(MoPropOrange, MoPropGreen, MoPropPurple),
            ),
            salt = 1,
        )
        withTransform({ scale(1f, squeeze(f), b) }) {
            drawCircle(MoPropWhite, r, b)
            colours.forEachIndexed { index, colour ->
                val from = index * 120f - 30f
                val rim = (0..5).map { step ->
                    val angle = (from + step * 12f) * PI.toFloat() / 180f
                    (b.x + cos(angle) * r) to (b.y + sin(angle) * r)
                }
                shape(colour, *(listOf(b.x to b.y) + rim).toTypedArray())
            }
            drawCircle(MoPropWhite, r * 0.2f, b)
        }
        // His mitten again, over the ball, so he plainly holds it rather than stands behind it.
        mitten(rig, rig.rightHand, 1f)

        // The valve comes away with the ball when he lets the air out.
        val tip = rig.mouth + Offset(14f, 5f) + Offset(14f, 10f) * sag
        val dir = aim(b, tip)
        limb(b + Offset(dir.x * r, dir.y * r * squeeze(f)), tip, MoPropWhite, 6f)
        val escaping = span(p, 0.64f, 0.70f) * (1f - span(p, 0.94f, 1f))
        if (escaping <= 0f) return
        val drift = if (v.chance(50, salt = 3)) Offset(0.7f, -0.7f) else Offset(1f, 0f)
        for (index in 0..2) {
            val away = cycle(t, 0.7f, offset = index / 3f)
            puff(tip + drift * (away * 34f), 4f + 3f * away, MoFoam.copy(alpha = 0.8f * escaping * (1f - away)))
        }
    }

    override val sparkles get() = false
}

/**
 * One patty off the grate, a hang at the top of its arc, a catch on the spatula, and then he beams
 * at us with the spatula up.
 */
private object WeekendTheFlip : MoAct("weekend_the_flip", MoOccasion.Weekend, bodies = setOf(MoBodyKind.Tall)) {
    private const val PERIOD = 4.4f

    // He stands aside, which is what lets the grill stand clear of him: the food and the spatula
    // then read against the dark ground and not against his body.
    private const val STANDS_ASIDE = 34f

    /** How far the middle of the grill is from the middle of the picture. */
    private const val GRILL = 121f

    /** The top of the grate. */
    private const val GRATE = 348f

    /** How far from the middle of the grill each thing on it lies. */
    private const val PLACE = 17f

    private const val FLIGHT = 96f

    /** How far the blade lifts what lies on it. */
    private const val BLADE = 4f

    private fun phase(t: Float) = cycle(t, PERIOD, offset = 0.52f)

    private fun grillSide(v: MoVariant) = if (v.chance(50, salt = 1)) 1f else -1f

    private fun grillX(v: MoVariant) = CENTRE + grillSide(v) * GRILL

    /** How far through its flight the food is: 0 as it leaves the spatula, 1 as it lands on it. */
    private fun flight(p: Float) = ((p - 0.36f) / 0.34f).coerceIn(0f, 1f)

    /** How high the food is, on a quartic arc, so it hangs at the top a beat too long. */
    private fun height(p: Float): Float {
        val u = flight(p)
        if (u <= 0f || u >= 1f) return 0f
        val e = 2f * u - 1f
        return 1f - e * e * e * e
    }

    /** While the spatula is down at the grate, level, and not up where its blade faces us. */
    private fun working(p: Float) = span(p, 0.18f, 0.26f) * (1f - span(p, 0.78f, 0.86f))

    /** While the blade is under the food and not short of it. */
    private fun slid(p: Float) = span(p, 0.26f, 0.30f) * (1f - span(p, 0.75f, 0.78f))

    /**
     * How far above the grate he holds the blade: up with the flick that throws the food, then
     * half as high to catch it, and down again to set it on the grate.
     */
    private fun raise(p: Float) = 16f * span(p, 0.30f, 0.36f) - 8f * span(p, 0.36f, 0.46f) - 8f * span(p, 0.70f, 0.75f)

    /**
     * How far above the grate the food is carried: on the blade, which lifts it by its own
     * thickness as it slides under, or along the line its flight starts and ends on.
     */
    private fun carried(p: Float) =
        if (p > 0.36f && p < 0.70f) BLADE + mix(16f, 8f, flight(p)) else raise(p) + BLADE * slid(p)

    private fun pride(p: Float) = span(p, 0.78f, 0.86f) * (1f - span(p, 0.94f, 1f))

    /** He looks up at us while it hangs, which is the whole point of holding it up there. */
    private fun glance(p: Float): Float {
        val u = flight(p)
        return span(u, 0.40f, 0.46f) * (1f - span(u, 0.54f, 0.60f))
    }

    /** At the ready beside the grill, with the blade up where we can see it. */
    private val ready = MoArm(Offset(22f, -24f), bend = 0.3f)

    /** His fist over the near edge of the grate, the blade level and short of the food. */
    private val poised = MoArm(Offset(-1.5f, -4.5f), bend = 0.3f)

    /** The same, with the blade slid under the food. */
    private val under = MoArm(Offset(10.5f, -4.5f), bend = 0.3f)

    private fun spatulaArm(p: Float): MoArm {
        val atTheGrate = mix(poised, under, slid(p)).let { it.copy(reach = it.reach - Offset(0f, raise(p))) }
        return mix(mix(ready, atTheGrate, working(p)), MoArm.Up, pride(p))
    }

    /** Which way the spatula points from his fist, in degrees: 0 straight down, 180 straight up, outward in between. */
    private fun pointing(p: Float) = mix(mix(150f, 40f, working(p)), 172f, pride(p))

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val p = phase(t)
        val s = grillSide(v)
        val h = height(p)
        val proud = pride(p)
        val atUs = glance(p)
        val onTheGrate = Offset(s * 0.8f, 0.6f)
        val watching = Offset(s * 0.8f, 0.5f - 1.3f * h)
        val gaze = mix(if (h > 0f) watching else onTheGrate, Offset.Zero, max(atUs, proud))
        val idle = moIdle(t, energy = 1.1f)
        return idle.copy(
            leftEye = MoEye(gaze = gaze),
            rightEye = MoEye(gaze = gaze),
            brows = when {
                proud > 0.4f -> MoBrows(MoBrowKind.Raised, lift = 5f)
                atUs > 0.4f -> MoBrows(MoBrowKind.Raised, lift = 4f)
                else -> MoBrows()
            },
            mouth = when {
                proud > 0.4f -> MoMouth.Beam
                p > 0.30f && p < 0.78f -> MoMouth.Oh(size = 6f)
                else -> MoMouth.Smile(14f, 10f)
            },
            leftArm = if (s < 0f) spatulaArm(p).copy(inFront = true) else MoArm.Hip,
            rightArm = if (s > 0f) spatulaArm(p).copy(inFront = true) else MoArm.Hip,
            sway = idle.sway + s * 6f * h,
            flare = 10f * h,
            squash = idle.squash + 0.03f * bell((p - 0.33f) / 0.03f) - 0.02f * bell((p - 0.73f) / 0.03f),
            // Rocking on his toes while he holds it up for us.
            offset = Offset(-s * STANDS_ASIDE, -4f * proud * pulse(t, 0.7f)),
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val gx = grillX(v)
        val (kettle, kettleLedge) = v.of(
            listOf(
                MoPropCharcoal to MoPropInk,
                MoPropRed to MoPropRedDark,
                MoPropTeal to MoPropTealDark,
            ),
            salt = 2,
        )
        floorShadow(gx, 40f, MoPropShadow)
        for (leg in listOf(0f, -22f, 22f)) {
            limb(Offset(gx + leg, GRATE + 34f), Offset(gx + leg * 1.4f, FEET), MoPropGreyDark, 6f)
        }
        val rim = GRATE + 8f
        drawPath(bowl(gx, rim + 5f), kettleLedge)
        drawPath(bowl(gx, rim), kettle)
        block(Offset(gx, rim), 78f, 8f, kettle, corner = 4f)
        // Three bars, so the grate reads as a grate and not as one stray line.
        for (bar in 0..2) {
            limb(Offset(gx - 35f, GRATE + 2f + bar * 4f), Offset(gx + 35f, GRATE + 2f + bar * 4f), MoPropGrey, 3f)
        }
        // Steam only: nothing near him burns, so the grill never has a flame of its own.
        for (index in 0..1) {
            val rise = cycle(t, 2.6f, offset = index * 0.5f)
            val at = Offset(gx + (index - 0.5f) * 30f + 4f * sin(rise * 5f + index), GRATE - 10f - rise * 48f)
            // It goes by getting smaller: fading out, it would pass through a grey that reads as
            // soot on his dark ground.
            val size = (4f + 5f * rise) * (1f - ease((rise - 0.65f) / 0.35f))
            // As one shape: three circles each at their own alpha show where they overlap.
            fading(0.85f * ease(rise / 0.12f), Rect(at, size * 2f + 1f)) { puff(at, size, MoPropSnow) }
        }
    }

    /** The kettle's bowl: flat at the [rim], round underneath. */
    private fun bowl(gx: Float, rim: Float) = Path().apply {
        moveTo(gx - 36f, rim)
        lineTo(gx + 36f, rim)
        cubicTo(gx + 36f, rim + 24f, gx + 20f, rim + 34f, gx, rim + 34f)
        cubicTo(gx - 20f, rim + 34f, gx - 36f, rim + 24f, gx - 36f, rim)
        close()
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val p = phase(t)
        val s = grillSide(v)
        val fist = if (s > 0f) rig.rightHand else rig.leftHand
        val degrees = pointing(p)
        val along = (degrees * PI.toFloat() / 180f).let { Offset(s * sin(it), cos(it)) }
        // Up, the blade faces us. Down at the grate it lies level, as a turner does, and we see it
        // as we see the food, from a little above: a blade that faced us there would be hidden by
        // the food lying on it.
        val level = ease((125f - degrees) / 60f)
        val neck = fist + along * 26f
        limb(fist - along * 8f, fist + along * 12f, MoPropWood, 7f)
        limb(fist + along * 12f, neck, MoPropSteel, 4f)
        val blade = neck + mix(along * 9f, Offset(s * 19f, 0f), level)
        // The throw tips its far end up.
        val throwing = bell((p - 0.35f) / 0.03f)
        withTransform({ rotate(s * ((180f - degrees) * (1f - level) - 14f * throwing), blade) }) {
            val width = mix(25f, 38f, level)
            val height = mix(19f, 7f, level)
            chunk(blade, width, height, MoPropSteel, MoPropGreyDark, corner = mix(5f, 3f, level), drop = 3f)
            for (slot in -1..1) {
                val at = blade + Offset(slot * width * 0.2f, -1.5f)
                limb(at - Offset(0f, height * 0.2f), at + Offset(0f, height * 0.2f), MoPropGreyDark, 2.5f)
            }
        }
        mitten(rig, fist, s)
    }

    override fun DrawScope.foreground(t: Float, v: MoVariant) {
        val p = phase(t)
        val s = grillSide(v)
        val gx = grillX(v)
        val kind = v.pick(3, salt = 3)
        // Nothing too dark: on his night-blue ground a brown patty would be a hole in the air.
        val food = listOf(MoPropWood, MoPropRed, MoPropYellow)[kind]
        val rest = Offset(gx - s * PLACE, GRATE - 6f)
        if (v.chance(50, salt = 5)) item(Offset(gx + s * PLACE, GRATE - 6f), kind, 0f, food, 0f)
        val turns = 2 + v.pick(2, salt = 4)
        item(rest + Offset(0f, -FLIGHT * height(p) - carried(p)), kind, abs(sin(PI.toFloat() * turns * flight(p))), food, bell((p - 0.72f) / 0.03f))
        // It sizzles as it meets the grate again.
        val set = (p - 0.74f) / 0.12f
        if (set > 0f && set < 1f) {
            // From between the two places, so the spatula he pulls away does not pass through it.
            val at = Offset(gx + s * (16f * set - 4f), GRATE - 14f - 26f * set)
            val size = (5f + 6f * set) * (1f - ease((set - 0.55f) / 0.45f))
            fading(0.85f * ease(set / 0.15f), Rect(at, size * 2f + 1f)) { puff(at, size, MoPropSnow) }
        }
    }

    /** The patty, sausage or corn cob, thin edge-on at the top of each turn and flat again on landing. */
    private fun DrawScope.item(centre: Offset, kind: Int, spin: Float, colour: Color, squash: Float) {
        // Even flat on the grate it keeps some thickness, or the turn would read as a stray bar.
        val half = when (kind) {
            0 -> 7f + 8f * spin
            1 -> 5.5f + 3f * spin
            else -> 6.5f + 5f * spin
        }
        val width = 16f + 3f * squash
        oval(centre + Offset(0f, 2f), width, half, MoPropBrown)
        oval(centre, width, half, colour)
        if (kind == 2) {
            for (column in -2..2) for (row in -1..1) {
                oval(centre + Offset(column * 5f, row * half * 0.43f), 1.9f, half * 0.14f, MoPropGoldDark)
            }
        } else {
            for (dx in listOf(-6.5f, 0f, 6.5f)) {
                limb(centre + Offset(dx - 2f, half * 0.45f), centre + Offset(dx + 2f, -half * 0.45f), MoPropBrown, 2.5f)
            }
        }
    }

    override val ownsTheFloor get() = true
}

internal val weekendActs1: List<MoAct> = listOf(
    WeekendSunglasses,
    WeekendOwnBell,
    WeekendSunflower,
    WeekendBrainFreeze,
    WeekendBeachBall,
    WeekendTheFlip,
)
