package com.example.workouttracker.feature.mo.acts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import com.example.workouttracker.core.ui.theme.MoPropBlue
import com.example.workouttracker.core.ui.theme.MoPropBlueDark
import com.example.workouttracker.core.ui.theme.MoPropCharcoal
import com.example.workouttracker.core.ui.theme.MoPropCream
import com.example.workouttracker.core.ui.theme.MoPropGold
import com.example.workouttracker.core.ui.theme.MoPropGoldDark
import com.example.workouttracker.core.ui.theme.MoPropGrass
import com.example.workouttracker.core.ui.theme.MoPropGreen
import com.example.workouttracker.core.ui.theme.MoPropGreenDark
import com.example.workouttracker.core.ui.theme.MoPropGrey
import com.example.workouttracker.core.ui.theme.MoPropGreyDark
import com.example.workouttracker.core.ui.theme.MoPropInk
import com.example.workouttracker.core.ui.theme.MoPropLeaf
import com.example.workouttracker.core.ui.theme.MoPropMint
import com.example.workouttracker.core.ui.theme.MoPropMoon
import com.example.workouttracker.core.ui.theme.MoPropPink
import com.example.workouttracker.core.ui.theme.MoPropPinkDark
import com.example.workouttracker.core.ui.theme.MoPropPurple
import com.example.workouttracker.core.ui.theme.MoPropPurpleDark
import com.example.workouttracker.core.ui.theme.MoPropRed
import com.example.workouttracker.core.ui.theme.MoPropRedDark
import com.example.workouttracker.core.ui.theme.MoPropShadow
import com.example.workouttracker.core.ui.theme.MoPropSilver
import com.example.workouttracker.core.ui.theme.MoPropSteel
import com.example.workouttracker.core.ui.theme.MoPropTeal
import com.example.workouttracker.core.ui.theme.MoPropTealDark
import com.example.workouttracker.core.ui.theme.MoPropWater
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
import com.example.workouttracker.feature.mo.block
import com.example.workouttracker.feature.mo.chunk
import com.example.workouttracker.feature.mo.curve
import com.example.workouttracker.feature.mo.cycle
import com.example.workouttracker.feature.mo.drop
import com.example.workouttracker.feature.mo.ease
import com.example.workouttracker.feature.mo.flameTongue
import com.example.workouttracker.feature.mo.floorShadow
import com.example.workouttracker.feature.mo.limb
import com.example.workouttracker.feature.mo.mitten
import com.example.workouttracker.feature.mo.moIdle
import com.example.workouttracker.feature.mo.oval
import com.example.workouttracker.feature.mo.pulse
import com.example.workouttracker.feature.mo.shape
import com.example.workouttracker.feature.mo.twitch
import com.example.workouttracker.feature.mo.wave
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/*
 * A task with the book motif is open and his head is empty, part two: things he nearly works out, and things he is
 * quite sure of and wrong about.
 */

private fun mix(from: Float, to: Float, amount: Float): Float = from + (to - from) * amount

private fun mix(from: Offset, to: Offset, amount: Float): Offset = from + (to - from) * amount

private fun mix(from: MoArm, to: MoArm, amount: Float): MoArm =
    MoArm(mix(from.reach, to.reach, amount), mix(from.bend, to.bend, amount))

/** A prop colour with the darker ledge that belongs to it. */
private class PropColours(val face: Color, val ledge: Color)

/** A closed book lying on the floor, seen from its page side. */
private fun DrawScope.lyingBook(centre: Offset, width: Float, height: Float, cover: PropColours) {
    floorShadow(centre.x, width / 2f + 2f, MoPropShadow)
    val top = centre + Offset(0f, -height / 2f)
    block(centre + Offset(0f, height / 2f - 3f), width, 6f, cover.ledge, corner = 2f)
    block(centre, width - 5f, height - 7f, MoPropCream, corner = 3f)
    block(centre + Offset(-width / 2f + 4f, 0f), 8f, height, cover.ledge, corner = 3f)
    block(top, width, 8f, cover.face, corner = 3f)
    limb(centre + Offset(-width * 0.30f, 3f), centre + Offset(width * 0.43f, 3f), MoPropSilver, 2f)
}

private val bookCovers = listOf(
    PropColours(MoPropTeal, MoPropTealDark),
    PropColours(MoPropPurple, MoPropPurpleDark),
    PropColours(MoPropRed, MoPropRedDark),
    PropColours(MoPropBlue, MoPropBlueDark),
)

/** A light bulb beside his tip stutters on, his whole face hopes, and it dies again. */
private object NearlyAnIdea : MoAct("dumb_nearly_an_idea", MoOccasion.Dumb, intensity = MoIntensity.Mild) {
    private fun side(v: MoVariant) = if (v.chance(50, salt = 1)) 1f else -1f

    // It starts a little way into the stutter, so the first frame has the bulb on.
    private fun progress(t: Float, v: MoVariant) = cycle(t, v.between(4.2f, 5.6f, salt = 4), offset = 0.03f)

    private fun lit(t: Float, v: MoVariant): Boolean {
        val p = progress(t, v)
        val flashes = 2 + v.pick(2, salt = 2)
        return p < 0.22f && (p / 0.22f * flashes).mod(1f) < 0.6f
    }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        // Hope arrives with the first flash, quickly but not in a single frame, and is whole at t = 0.
        val hope = progress(t, v).let { ease(it / 0.03f) * (1f - ease((it - 0.3f) / 0.4f)) }
        val bulb = Offset(side(v) * 0.7f, -1f)
        return moIdle(t, energy = 0.7f).copy(
            leftEye = MoEye(gaze = mix(Offset(-0.8f, 0.2f), bulb, hope), iris = 0.62f + 0.08f * hope),
            rightEye = MoEye(gaze = mix(Offset(0.7f, -0.5f), bulb, hope), iris = 0.62f + 0.08f * hope),
            brows = MoBrows(MoBrowKind.Raised, lift = 7f * hope, skew = 6f * (1f - hope)),
            mouth = when {
                hope > 0.6f -> MoMouth.Oh(size = 7f)
                hope > 0.25f -> MoMouth.Flat(width = 10f)
                else -> MoMouth.TongueOut
            },
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        // Placed from the body and not from his tip, so the bulb hangs still while the flame sways.
        val c = Offset(CENTRE + side(v) * 88f, rig.body.top - 15f)
        val on = lit(t, v)
        val glass = if (on) v.of(listOf(MoPropYellow, MoPropMoon, MoPropMint), salt = 3) else MoPropGrey
        if (on) {
            for (degrees in listOf(-180f, -135f, -90f, -45f, 0f)) {
                val ray = Offset(cos(degrees * PI.toFloat() / 180f), sin(degrees * PI.toFloat() / 180f))
                limb(c + ray * 33f, c + ray * 46f, glass, 5f)
            }
        }
        drawCircle(glass, 23f, c)
        block(c + Offset(0f, 23f), 20f, 13f, glass, corner = 4f)
        chunk(c + Offset(0f, 35f), 18f, 11f, MoPropSteel, MoPropCharcoal, corner = 3f, drop = 3f)
        // The filament is one plain arch: a zigzag reads as a letter.
        curve(c + Offset(-8f, 12f), c + Offset(0f, -22f), c + Offset(8f, 12f), if (on) MoPropGoldDark else MoPropGreyDark, 3.5f)
    }

    override val sparkles get() = false
}

/** He inspects us through a magnifying glass: one enormous wandering eye, one small one on us. */
private object OneBigEye : MoAct("dumb_one_big_eye", MoOccasion.Dumb, intensity = MoIntensity.Mild) {
    private fun side(v: MoVariant) = if (v.chance(50, salt = 1)) 1f else -1f

    // He pumps the glass nearer and further; at t = 0 it is nearest and the eye at its biggest.
    private fun swell(t: Float, v: MoVariant) = 1.5f - v.between(0.18f, 0.28f, salt = 3) * pulse(t, 3.8f)

    // The handle hangs down and outward, far enough that the mitten holding it clears his silhouette:
    // an arm over his body is drawn in his own colour, so a hand that ends inside him ends in nothing.
    private fun handle(side: Float) = Offset(side * 0.6f, 0.8f)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val side = side(v)
        val b = MoBodies.of(body)
        val scale = swell(t, v)
        // Every so often the big eye stops wandering and darts straight at us.
        val dart = twitch(t, if (v.chance(40, salt = 4)) 3.4f else 5.2f, beat = 0.7f, offset = 0.5f)
        val big = MoEye(
            scale = scale,
            iris = 0.66f - 0.16f * dart,
            gaze = Offset(cos(t * 0.9f) * 0.8f, sin(t * 1.3f) * 0.6f) * (1f - dart),
        )
        val small = MoEye(gaze = Offset(0f, 0.1f))
        // The arm is worked out from the body, so the handle ends in his mitten on both of them.
        val eye = Offset(if (side > 0f) b.rightEyeX else b.leftEyeX, b.eyeY)
        val hand = eye + handle(side) * (b.eyeRadius * scale + 58f)
        val shoulder = Offset(CENTRE + side * b.shoulderHalf, b.shoulderY)
        val glassArm = MoArm(Offset(side * (hand.x - shoulder.x), hand.y - shoulder.y), bend = 0.5f)
        return moIdle(t, energy = 0.7f).copy(
            leftEye = if (side < 0f) big else small,
            rightEye = if (side > 0f) big else small,
            mouth = MoMouth.Oh(size = 6f),
            leftArm = if (side < 0f) glassArm else MoArm.Hip,
            rightArm = if (side > 0f) glassArm else MoArm.Hip,
            armsInFront = true,
            tilt = side * 2f,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val side = side(v)
        val eye = if (side > 0f) rig.rightEye else rig.leftEye
        val hand = if (side > 0f) rig.rightHand else rig.leftHand
        val radius = rig.body.eyeRadius * swell(t, v) + 5f
        val rim = v.of(listOf(MoPropSilver, MoPropTeal, MoPropRed, MoPropPurple), salt = 2)
        val neck = eye + handle(side) * (radius + 3f)
        val end = hand + handle(side) * 17f
        limb(neck, end, MoPropWood, 9f)
        limb(mix(neck, end, 0.55f), end, MoPropWoodDark, 12f)
        mitten(rig, hand, side)
        drawCircle(rim, radius, eye, style = Stroke(width = 7f))
        curve(
            eye + Offset(-radius * 0.55f, -radius * 0.45f),
            eye + Offset(-radius * 0.25f, -radius * 0.75f),
            eye + Offset(radius * 0.1f, -radius * 0.78f),
            MoPropWhite.copy(alpha = 0.8f),
            4f,
        )
    }
}

/** He hiccups a little flame off his tip, hunts left, right and up for whoever did that, and forgets. */
private object WhoDidThat : MoAct("dumb_who_did_that", MoOccasion.Dumb) {
    private fun double(v: MoVariant) = v.chance(30, salt = 2)

    /** Seconds since the hiccup. It starts 0.7 s in, with the little flame already in the air. */
    private fun since(t: Float, v: MoVariant): Float {
        val gap = v.between(3.2f, 4f, salt = 3) + if (double(v)) 0.4f else 0f
        return cycle(t, gap, offset = 0.7f / gap) * gap
    }

    private fun dip(s: Float) = if (s > 0f && s < 0.1f) sin(s / 0.1f * PI.toFloat()) else 0f

    private fun pop(s: Float) = if (s > 0.1f && s < 0.3f) sin((s - 0.1f) / 0.2f * PI.toFloat()) else 0f

    private fun again(v: MoVariant) = if (double(v)) 0.4f else 0f

    private fun kick(s: Float, v: MoVariant) = pop(s) + if (double(v)) pop(s - again(v)) else 0f

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val s = since(t, v)
        val again = again(v)
        val dip = dip(s) + if (again > 0f) dip(s - again) else 0f
        val pop = kick(s, v)
        // The face runs on its own clock, which a second hiccup holds back.
        val f = s - again
        val startled = s >= 0.1f && f < 0.5f
        val relaxed = if (s < 0.1f) 1f else ease((f - 2.3f) / 0.3f)
        val first = if (v.chance(50, salt = 1)) 1f else -1f
        fun hunt(at: Float) = first * (ease((at - 0.5f) / 0.15f) - 2f * ease((at - 1.2f) / 0.18f) + ease((at - 1.9f) / 0.15f))
        val up = mix(0.1f, -0.9f, ease((f - 1.9f) / 0.15f))
        val narrowed = if (startled) 0f else 0.3f * ease((f - 0.5f) / 0.1f) * (1f - relaxed)
        val iris = if (startled) 0.45f else mix(mix(0.45f, 0.7f, ease((f - 0.5f) / 0.15f)), 0.62f, relaxed)
        val idle = moIdle(t, energy = 0.8f)
        val b = MoBodies.of(body)
        val hushed = MoArm(Offset(CENTRE - b.shoulderHalf - b.mouth.x, b.mouth.y - b.shoulderY + 2f), bend = 0.8f)
        val hush = if (v.chance(40, salt = 5)) ease((s - 0.25f) / 0.15f) * (1f - ease((s - 1f) / 0.25f)) else 0f
        return idle.copy(
            leftEye = MoEye(
                gaze = if (startled) Offset.Zero else mix(Offset(hunt(f), up), Offset(-0.7f, -0.2f), relaxed),
                iris = iris,
                lidDrop = narrowed,
            ),
            // The right eye arrives a moment after the left.
            rightEye = MoEye(
                gaze = if (startled) Offset.Zero else mix(Offset(hunt(f - 0.12f), up), Offset(0.8f, 0.3f), relaxed),
                iris = iris,
                lidDrop = narrowed,
            ),
            brows = when {
                startled -> MoBrows(MoBrowKind.Raised, lift = 7f)
                relaxed > 0.5f -> MoBrows()
                else -> MoBrows(MoBrowKind.Flat)
            },
            mouth = when {
                startled -> MoMouth.Oh(size = 7f)
                relaxed > 0.5f -> MoMouth.TongueOut
                else -> MoMouth.Flat(width = 10f)
            },
            leftArm = mix(MoArm.Down, hushed, hush).copy(inFront = true, warm = hush),
            squash = idle.squash - 0.06f * dip + 0.05f * pop,
            offset = Offset(0f, -10f * pop),
            flare = 14f * pop,
            tilt = if (startled) 0f else 2f * hunt(f) * (1f - relaxed),
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val size = v.between(15f, 19f, salt = 4)
        val s = since(t, v)
        // His pop lifts and stretches everything drawn here, so the little flame may come no nearer
        // the top of the frame than that lift still leaves room for.
        val pop = kick(s, v)
        val ceiling = FEET - (FEET - 3f - 10f * pop) / (1.014f + 0.05f * pop)
        for (start in if (double(v)) listOf(0f, again(v)) else listOf(0f)) {
            val rise = (s - start) / 1.3f
            if (rise <= 0f || rise >= 1f) continue
            val now = size * (1f - rise * 0.4f)
            val base = rig.tip + Offset(6f + 5f * sin(rise * 5f), -12f - rise * 40f)
            // It stays whole for the first frame and only fades over the last of its rise.
            val alpha = 1f - ease((rise - 0.6f) / 0.4f)
            flameTongue(
                base.copy(y = maxOf(base.y, ceiling + now * 2.6f)),
                now,
                rig.palette.middle.copy(alpha = alpha),
                rig.palette.belly.copy(alpha = alpha),
            )
        }
    }
}

/** He hides behind mittens far too small for his eyes, peeks, is thrilled we are still here, and hides again. */
private object YouCannotSeeMe : MoAct("dumb_you_cannot_see_me", MoOccasion.Dumb) {
    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val b = MoBodies.of(body)
        val hiddenFor = v.between(1f, 1.6f, salt = 3)
        val period = 3.2f + hiddenFor
        val s = cycle(t, period) * period
        // Peeking for 2.2 s, the mitten snaps up, a short stillness, and it slides slowly down again.
        val down = 1f - ease((s - 2.2f) / 0.2f) + ease((s - 2.4f - hiddenFor) / 0.8f)
        val recheck = if (v.chance(30, salt = 4) && s > 0.6f && s < 1f) sin((s - 0.6f) / 0.4f * PI.toFloat()) else 0f
        val slide = (down * (1f - recheck)).coerceIn(0f, 1f)
        val delight = ease((s - 1.4f) / 0.15f) * (1f - ease((s - 2.05f) / 0.15f))
        val hops = v.chance(50, salt = 2)
        // He is startled to find us again the moment the mitten clears his eye, and the loop starts
        // on that same beat: without this the grin and the brows would jump at the seam.
        val seeing = s < 1.4f || s > 2.4f + hiddenFor

        val peekSide = if (v.chance(50, salt = 1)) 1f else -1f
        val leftCover = Offset(-(b.leftEyeX - (CENTRE - b.shoulderHalf)), b.eyeY - b.shoulderY)
        val rightCover = Offset(-((CENTRE + b.shoulderHalf) - b.rightEyeX), b.eyeY - b.shoulderY)
        // The peeking mitten comes to rest on his cheek, below the eye and clear of the mouth.
        val rest = Offset(8f, 36f) * slide
        val covered = MoEye(shape = MoEyeShape.Happy)
        val peeking = if (slide > 0.4f) {
            MoEye(open = ease((slide - 0.4f) / 0.4f), iris = if (s < 2.4f) 0.5f + 0.26f * ease(s) else 0.5f)
        } else {
            covered
        }
        val idle = moIdle(t, energy = 0.5f)
        return idle.copy(
            leftEye = if (peekSide < 0f) peeking else covered,
            rightEye = if (peekSide > 0f) peeking else covered,
            brows = if (seeing && slide > 0.6f) MoBrows(MoBrowKind.Raised, lift = 5f) else MoBrows(),
            mouth = when {
                s < 0.5f -> MoMouth.Oh(size = 6f)
                s < 1.4f -> MoMouth.Smile()
                s < 2.3f -> MoMouth.Grin(open = 0.8f)
                seeing && slide > 0.4f -> MoMouth.Oh(size = 6f)
                else -> MoMouth.Grin(open = 0.5f)
            },
            leftArm = MoArm(leftCover + if (peekSide < 0f) rest else Offset.Zero, bend = 0.5f, warm = 1f),
            rightArm = MoArm(rightCover + if (peekSide > 0f) rest else Offset.Zero, bend = 0.5f, warm = 1f),
            armsInFront = true,
            // Hidden, he holds very still and stands a little taller.
            sway = idle.sway * (0.3f + 0.7f * down),
            squash = idle.squash + 0.02f * (1f - down),
            tilt = if (hops) 0f else 4f * wave(t, 0.28f) * delight,
            offset = if (hops) Offset(0f, -7f * abs(wave(t, 0.4f)) * delight) else Offset.Zero,
            blinks = false,
        )
    }
}

/** He stares hard at one book while a little worm in glasses pops out of the other, every time. */
private object TheBookworm : MoAct("dumb_the_bookworm", MoOccasion.Dumb, intensity = MoIntensity.Mild) {
    private const val HOLE_Y = FEET - 32f

    /** Which side the worm is on, and how far through that half of the loop we are. */
    private fun half(t: Float, v: MoVariant): Pair<Float, Float> {
        val p = cycle(t, 5.6f, offset = 0.15f) * 2f
        val first = if (v.chance(50, salt = 1)) 1f else -1f
        return (if (p < 1f) first else -first) to p.mod(1f)
    }

    private fun bookX(side: Float) = CENTRE + side * 124f

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val (worm, u) = half(t, v)
        // He starts on the wrong book and swings across; the eye nearer the worm gets there first.
        fun gaze(delay: Float) = Offset(mix(-worm, worm, ease((u - 0.45f - delay) / 0.2f)), 0.75f)
        val left = gaze(if (worm < 0f) 0f else 0.1f)
        val right = gaze(if (worm > 0f) 0f else 0.1f)
        val amazed = ease((u - 0.65f) / 0.05f) * (1f - ease((u - 0.92f) / 0.08f))
        val greeting = mix(MoArm.Down, MoArm(Offset(40f, -30f + 6f * wave(t, 0.5f)), bend = 0.5f), amazed)
        return moIdle(t, energy = 0.8f).copy(
            leftEye = MoEye(gaze = left, iris = 0.7f - 0.2f * amazed, lidDrop = 0.15f * (1f - amazed)),
            rightEye = MoEye(gaze = right, iris = 0.7f - 0.2f * amazed, lidDrop = 0.15f * (1f - amazed)),
            brows = if (amazed > 0.5f) MoBrows(MoBrowKind.Raised, lift = 6f) else MoBrows(MoBrowKind.Flat),
            mouth = if (amazed > 0.5f) MoMouth.Oh(size = 8f) else MoMouth.Flat(width = 9f),
            leftArm = if (worm < 0f) greeting else MoArm.Down,
            rightArm = if (worm > 0f) greeting else MoArm.Down,
            tilt = 1.25f * (left.x + right.x),
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val (worm, u) = half(t, v)
        for ((side, salt) in listOf(-1f to 3, 1f to 4)) {
            lyingBook(Offset(bookX(side), FEET - 15f), 66f, 30f, v.of(bookCovers, salt))
            oval(Offset(bookX(side), HOLE_Y), 15f, 5f, MoPropInk)
        }

        val out = ease(u / 0.15f) * (1f - ease((u - v.between(0.6f, 0.78f, salt = 5)) / 0.2f))
        val bare = v.chance(25, salt = 6)
        val colour = v.of(listOf(PropColours(MoPropGreen, MoPropGreenDark), PropColours(MoPropPink, MoPropPinkDark), PropColours(MoPropMint, MoPropGreen)), salt = 2)
        // Clipped at the hole, so it rises out of the book instead of sliding up in front of it.
        clipRect(left = -45f, top = 0f, right = 275f, bottom = HOLE_Y) {
            val sunk = 54f * (1f - out)
            val sideways = if (bare) 3f * wave(t, 0.7f) * out else 0f
            for (segment in 3 downTo 1) {
                val at = Offset(bookX(worm) + sideways * (3 - segment) / 3f, HOLE_Y + 2f - 13f * (3 - segment) + sunk)
                drawCircle(colour.face, 10.5f, at)
                drawCircle(colour.ledge, 3.5f, at + Offset(-worm * 3.5f, 1f))
            }
            val head = Offset(bookX(worm) + sideways, HOLE_Y - 38f + sunk)
            drawCircle(colour.face, 13f, head)
            for (eye in listOf(-1f, 1f)) {
                val at = head + Offset(eye * 6f - worm * 1.5f, -2f)
                drawCircle(MoPropWhite, 4.2f, at)
                drawCircle(MoPropInk, 2f, at + Offset(-worm * 1.5f, 0.3f))
                if (!bare) drawCircle(MoPropGold, 6f, at, style = Stroke(width = 2.2f))
            }
        }
    }

    override val ownsTheFloor get() = true
}

/** He waters a closed book and leans in, full of hope, to see whether anything has grown. */
private object WateringTheBook : MoAct("dumb_watering_the_book", MoOccasion.Dumb, intensity = MoIntensity.Strong) {
    private const val BOOK_TOP = FEET - 28f

    private fun side(v: MoVariant) = if (v.chance(50, salt = 1)) 1f else -1f

    private fun progress(t: Float) = cycle(t, 4.4f, offset = 0.1f)

    private fun pour(t: Float) = progress(t).let { ease(it / 0.08f) * (1f - ease((it - 0.5f) / 0.12f)) }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val side = side(v)
        val pour = pour(t)
        val leanIn = progress(t).let { ease((it - 0.68f) / 0.1f) * (1f - ease((it - 0.9f) / 0.1f)) }
        val eye = MoEye(gaze = Offset(side * 0.9f, 0.8f), scale = 1f + 0.05f * leanIn, iris = 0.74f)
        val canArm = MoArm(Offset(-8f, -12f - 6f * pour), bend = 0.5f)
        return moIdle(t, energy = 0.9f).copy(
            leftEye = eye,
            rightEye = eye,
            brows = MoBrows(MoBrowKind.Raised, lift = 5f + 3f * leanIn),
            mouth = when {
                pour > 0.5f -> MoMouth.Beam
                leanIn > 0.5f -> MoMouth.Oh(size = 6f)
                else -> MoMouth.Smile()
            },
            leftArm = if (side < 0f) canArm else MoArm.Hip,
            rightArm = if (side > 0f) canArm else MoArm.Hip,
            armsInFront = true,
            tilt = side * (1.5f + 3f * leanIn),
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val x = CENTRE + side(v) * 123f
        val covers = listOf(bookCovers[1], bookCovers[2], bookCovers[3])
        lyingBook(Offset(x, FEET - 13f), 70f, 26f, v.of(covers, salt = 3))
        if (v.chance(40, salt = 4)) {
            // The sprout stands right under the spout: it is what he is watering.
            val root = Offset(x + side(v) * 16f, BOOK_TOP)
            val top = root + Offset(3f * wave(t, 2.1f), -22f)
            curve(root, root + Offset(-side(v) * 4f, -12f), top, MoPropLeaf, 4f)
            oval(top + Offset(-8f, -1f), 8f, 4.5f, MoPropGrass)
            oval(top + Offset(8f, -3f), 8f, 4.5f, MoPropGrass)
            if (v.chance(50, salt = 5)) oval(top + Offset(0f, -8f), 4.5f, 7f, MoPropGrass)
        }
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val side = side(v)
        val pour = pour(t)
        val hand = if (side > 0f) rig.rightHand else rig.leftHand
        val can = if (rig.body.kind == MoBodyKind.Low) {
            PropColours(MoPropSteel, MoPropCharcoal)
        } else {
            v.of(
                listOf(PropColours(MoPropTeal, MoPropTealDark), PropColours(MoPropBlue, MoPropBlueDark), PropColours(MoPropGreen, MoPropGreenDark), PropColours(MoPropPink, MoPropPinkDark)),
                salt = 2,
            )
        }
        val degrees = side * (8f + 22f * pour)
        val spout = Offset(side * 56f, -12f)

        // Drops leave the tipped spout and fall straight onto the cover.
        val turn = degrees * PI.toFloat() / 180f
        val tip = hand + Offset(spout.x * cos(turn) - spout.y * sin(turn), spout.x * sin(turn) + spout.y * cos(turn))
        val tempo = v.between(0.75f, 1.1f, salt = 6)
        for (index in 0..2) {
            val fall = cycle(t, tempo, offset = index / 3f)
            drop(Offset(tip.x + side * 2f, mix(tip.y, BOOK_TOP - 12f, fall * fall)), 5f, MoPropWater.copy(alpha = pour * (1f - fall * fall * fall)))
        }

        withTransform({ rotate(degrees, hand) }) {
            val centre = hand + Offset(side * 24f, -2f)
            curve(centre + Offset(-side * 20f, -13f), centre + Offset(-side * 42f, 0f), centre + Offset(-side * 20f, 12f), can.ledge, 6f)
            shape(
                can.face,
                centre.x + side * 10f to centre.y + 11f,
                centre.x + side * 10f to centre.y - 1f,
                hand.x + spout.x - side * 2f to hand.y + spout.y - 2f,
                hand.x + spout.x + side * 1f to hand.y + spout.y + 3f,
            )
            oval(hand + spout, 5f, 8f, can.ledge)
            chunk(centre, 31f, 32f, can.face, can.ledge, corner = 6f, drop = 4f)
        }
        mitten(rig, hand, side)
    }

    override val ownsTheFloor get() = true
}

internal val dumbActs2: List<MoAct> = listOf(
    NearlyAnIdea,
    OneBigEye,
    WhoDidThat,
    YouCannotSeeMe,
    TheBookworm,
    WateringTheBook,
)
