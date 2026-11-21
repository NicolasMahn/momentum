package com.example.workouttracker.feature.mo.acts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import com.example.workouttracker.core.ui.theme.MoFlameBelly
import com.example.workouttracker.core.ui.theme.MoFlameMiddle
import com.example.workouttracker.core.ui.theme.MoPropBlue
import com.example.workouttracker.core.ui.theme.MoPropBlueDark
import com.example.workouttracker.core.ui.theme.MoPropBrown
import com.example.workouttracker.core.ui.theme.MoPropCream
import com.example.workouttracker.core.ui.theme.MoPropGold
import com.example.workouttracker.core.ui.theme.MoPropGoldDark
import com.example.workouttracker.core.ui.theme.MoPropGreen
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
import com.example.workouttracker.core.ui.theme.MoPropSand
import com.example.workouttracker.core.ui.theme.MoPropShadow
import com.example.workouttracker.core.ui.theme.MoPropSky
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
import com.example.workouttracker.feature.mo.MoBody
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
import com.example.workouttracker.feature.mo.flameTongue
import com.example.workouttracker.feature.mo.floorShadow
import com.example.workouttracker.feature.mo.limb
import com.example.workouttracker.feature.mo.moIdle
import com.example.workouttracker.feature.mo.oval
import com.example.workouttracker.feature.mo.puff
import com.example.workouttracker.feature.mo.pulse
import com.example.workouttracker.feature.mo.shape
import com.example.workouttracker.feature.mo.twitch
import com.example.workouttracker.feature.mo.wave
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sign
import kotlin.math.sin

/*
 * Idle, part two: small company. A glance at you, a feather, a flower, a cat, a candle and a snail.
 * Each one runs on a fixed timeline inside its loop, cut into eased ramps so no part moves like a
 * machine and every loop has a stretch where nothing much happens.
 */

/** 0 before [from], 1 after [to], eased in between: one beat on an act's timeline. */
private fun ramp(at: Float, from: Float, to: Float): Float = ease((at - from) / (to - from))

private fun mix(a: Float, b: Float, k: Float): Float = a + (b - a) * k

private fun mix(a: Offset, b: Offset, k: Float): Offset = a + (b - a) * k

private fun mix(a: MoArm, b: MoArm, k: Float): MoArm = MoArm(mix(a.reach, b.reach, k), mix(a.bend, b.bend, k))

/** His eyes wander off one way, then the other, then land on you: brows up, a smile, a shy little wave. */
private object OhHello : MoAct("idle_oh_hello", MoOccasion.Idle, intensity = MoIntensity.Mild) {
    private const val WAG = 0.5f

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        // The wave lasts a whole number of wags, so the hand is back in the middle when it stops.
        val hello = WAG * if (v.chance(40, salt = 3)) 4f else 3f
        val loop = hello + 5.4f
        // He starts a quarter of a wag in, with the hand at the far end of its swing.
        val at = cycle(t, loop, offset = WAG / 4f / loop) * loop
        val d = if (v.chance(50, salt = 2)) 1f else -1f

        val away = ramp(at, hello + 0.8f, hello + 1.6f)
        val across = ramp(at, hello + 2.6f, hello + 3.6f)
        val back = ramp(at, loop - 1f, loop - 0.4f)
        val drift = -away + 2f * across - back
        val noticed = 1f - ramp(at, hello, hello + 0.4f) + ramp(at, loop - 0.4f, loop - 0.2f)
        val grin = 1f - ramp(at, hello + 0.2f, hello + 0.6f) + ramp(at, loop - 0.35f, loop - 0.05f)
        val up = 1f - ramp(at, hello, hello + 0.8f) + ramp(at, loop - 0.5f, loop)
        val wag = if (at < hello) wave(at, WAG) else 0f

        val eye = MoEye(
            gaze = Offset(d * drift, -0.2f * away + 0.5f * across - 0.3f * back),
            scale = 1f + 0.03f * noticed,
        )
        val waving = mix(MoArm.Down, MoArm(Offset(40f + 12f * wag, -54f), bend = 0.5f), up)
        val leftWaves = v.chance(50, salt = 1)
        val idle = moIdle(t)
        return idle.copy(
            leftEye = eye,
            rightEye = eye,
            brows = if (noticed > 0.02f) MoBrows(MoBrowKind.Raised, lift = 6f * noticed) else MoBrows(),
            mouth = if (grin > 0.05f) MoMouth.Grin(open = 0.4f * grin, tongue = false) else MoMouth.Smile(12f + 2f * (1f - away + back), 8f + 2f * (1f - away + back)),
            leftArm = if (leftWaves) waving else MoArm.Down,
            rightArm = if (leftWaves) MoArm.Down else waving,
            squash = idle.squash + if (v.chance(50, salt = 4)) 0.03f * up else 0f,
            // The head follows the glance, which is what makes so small a gaze read at all.
            tilt = 3f * d * drift,
        )
    }
}

/** A feather drifts down beside his face; he puffs it back up again, and his eyes never leave it. */
private object FeatherKeepUp : MoAct("idle_feather_keep_up", MoOccasion.Idle, intensity = MoIntensity.Mild) {
    private const val FALL = 3f
    private const val PUFF = 0.6f

    /** When he stops pursing his lips: a little before the loop ends, so its seam carries no face. */
    private const val SETTLED = FALL + PUFF * 0.7f

    // Far enough out to clear his side at mouth height, near enough to stay in the picture.
    private const val FEATHER_OUT = 75f

    // Half a loop in, so the first frame has the feather beside his eye, well into its fall.
    private fun at(t: Float) = cycle(t, FALL + PUFF, offset = 0.5f) * (FALL + PUFF)

    private fun twice(v: MoVariant) = v.chance(35, salt = 3)

    /** How far the puff has sent it back up, 0 to 1; two short puffs send it up in two halves. */
    private fun rise(at: Float, twice: Boolean): Float {
        val k = ((at - FALL) / PUFF).coerceIn(0f, 1f)
        return when {
            !twice -> ease(k)
            k < 0.5f -> 0.5f * ease(k * 2f)
            else -> 0.5f + 0.5f * ease(k * 2f - 1f)
        }
    }

    /** How far down it is, 0 at the top to 1 at his mouth. It lingers a little at both ends. */
    private fun fallen(at: Float, twice: Boolean): Float =
        if (at < FALL) 0.5f * (at / FALL) + 0.5f * ease(at / FALL) else 1f - rise(at, twice)

    private fun blow(at: Float, twice: Boolean): Float {
        val k = ((at - FALL) / PUFF).coerceIn(0f, 1f)
        return abs(sin(k * PI.toFloat() * if (twice) 2f else 1f))
    }

    // It stays on the viewer's right, because his whistling mouth purses to that side.
    private fun feather(t: Float, v: MoVariant, body: MoBody): Offset {
        val at = at(t)
        val f = fallen(at, twice(v))
        val drift = if (at < FALL) v.between(5f, 9f, salt = 2) * sin(f * 6f * PI.toFloat()) else 0f
        return Offset(body.rightEyeX + FEATHER_OUT + drift, mix(body.eyeY - 120f, body.mouth.y + 10f, f))
    }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val shape = MoBodies.of(body)
        val at = at(t)
        val toFeather = feather(t, v, shape) - Offset(shape.rightEyeX, shape.eyeY)
        val eye = MoEye(gaze = toFeather / max(toFeather.getDistance(), 1f))
        val breathIn = ramp(at, FALL - 0.3f, FALL) * (1f - ramp(at, FALL, FALL + 0.1f))
        val blow = blow(at, twice(v))
        val idle = moIdle(t)
        return idle.copy(
            leftEye = eye,
            rightEye = eye,
            // His face lets go before the feather is back at the top, so nothing of it jumps at the
            // moment the loop comes round.
            brows = if (at > FALL - 0.3f && at < SETTLED) MoBrows(MoBrowKind.Raised, lift = 3f) else MoBrows(),
            mouth = when {
                at > FALL - 0.3f && at < FALL -> MoMouth.Oh(5f)
                at >= FALL && at < SETTLED -> MoMouth.Whistle
                else -> MoMouth.Smile(12f, 8f)
            },
            leftArm = MoArm.Hip,
            squash = idle.squash + 0.04f * breathIn - 0.05f * blow,
            tilt = 1.5f * blow,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val at = at(t)
        val twice = twice(v)
        val feather = feather(t, v, rig.body)
        if (at >= FALL) {
            val from = rig.mouth + Offset(26f, 3f)
            val to = Offset(rig.rightEye.x + FEATHER_OUT, rig.mouth.y + 4f)
            for (index in 0..2) {
                val k = (at - FALL) / PUFF * 1.6f - index * 0.3f
                if (k > 0f && k < 1f) puff(mix(from, to, k), 10f + 7f * k, MoPropWhite.copy(alpha = 1f - k * k))
            }
        }
        // It rocks as it falls and spins once on the way up, which lands it back on the rocking angle.
        val turn = if (at < FALL) 25f * cos(fallen(at, twice) * 6f * PI.toFloat()) else 25f + 360f * rise(at, twice)
        withTransform({ rotate(turn, feather) }) {
            val vane = v.of(listOf(MoPropWhite, MoPropSky, MoPropPink, MoPropMint), salt = 1)
            oval(feather, 13f, 27f, vane)
            oval(feather + Offset(0f, -11f), 9f, 18f, vane)
            // The spine reaches no further than the vane turned sideways, so a spin stays in the box.
            limb(feather + Offset(0f, -23f), feather + Offset(0f, 30f), MoPropSand, 3.5f)
            for (barb in listOf(-1f, 1f)) {
                limb(feather + Offset(0f, 8f), feather + Offset(barb * 9f, -3f), MoPropSand, 2.5f)
                limb(feather + Offset(0f, -5f), feather + Offset(barb * 8f, -14f), MoPropSand, 2.5f)
            }
        }
    }

    override val sparkles get() = false
}

/** A potted flower keeps its face turned to him, not the sky; he sways near and away, flattered. */
private object FlowerThinksHeIsTheSun : MoAct("idle_flower_thinks_he_is_the_sun", MoOccasion.Idle, intensity = MoIntensity.Mild) {
    private const val SWAY = 4f

    private fun side(v: MoVariant) = if (v.chance(50, salt = 3)) 1f else -1f

    private fun near(t: Float) = 0.5f - 0.5f * wave(t, SWAY)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val s = side(v)
        val w = wave(t, SWAY)
        val near = near(t)
        // He only picks his feet up while he is actually moving.
        val stepping = 5f * abs(cos(t / SWAY * 2f * PI.toFloat()))
        // Lower than the kit's cheek, which would cover the eye he watches the flower with.
        val cheek = MoArm(Offset(-28f, -4f), bend = 0.5f)
        return moIdle(t, energy = 0.8f).copy(
            leftEye = MoEye(gaze = Offset(0.9f * s, 0.1f)),
            rightEye = MoEye(gaze = Offset(0.9f * s, 0.1f)),
            brows = MoBrows(MoBrowKind.Raised, lift = 3f, skew = max(5f * s, 0f)),
            mouth = MoMouth.Smile(12f + 5f * near, 8f + 6f * near),
            leftArm = if (s > 0f) cheek else MoArm.Down,
            rightArm = if (s > 0f) MoArm.Down else cheek,
            armsInFront = true,
            legs = MoLegs(MoLeg(lift = stepping * max(wave(t, 1f), 0f)), MoLeg(lift = stepping * max(-wave(t, 1f), 0f))),
            offset = Offset(-s * (5f + 10f * w), 0f),
            tilt = -s * 3f * w,
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) = floorShadow(CENTRE + side(v) * 134f, 24f, MoPropShadow)

    // In front of him, so the head is not lost behind his shoulder when he leans in.
    override fun DrawScope.foreground(t: Float, v: MoVariant) {
        val s = side(v)
        val x = CENTRE + s * 134f
        val near = near(t)
        // The head follows him a beat late.
        val lag = near(t - 0.45f)
        val head = Offset(x - s * (8f + 12f * lag), FEET - 140f + 3f * lag)
        val (pot, potDark) = v.of(listOf(MoPropRed to MoPropRedDark, MoPropTeal to MoPropTealDark, MoPropBlue to MoPropBlueDark), salt = 2)
        val petal = v.of(listOf(MoPropYellow, MoPropPink, MoPropSky, MoPropWhite), salt = 1)

        curve(Offset(x, FEET - 38f), Offset(x, FEET - 104f), head + Offset(0f, 6f), MoPropLeaf, 5f)
        oval(Offset(x + 11f, FEET - 60f), 12f, 6f, MoPropGreen)
        oval(Offset(x - 11f, FEET - 78f), 12f, 6f, MoPropGreen)
        chunk(Offset(x, FEET - 18f), 38f, 32f, pot, potDark, corner = 8f)
        block(Offset(x, FEET - 36f), 44f, 10f, pot, corner = 5f)

        if (v.chance(30, salt = 5)) {
            for (fan in listOf(-1f, 1f, 0f)) {
                withTransform({ rotate(fan * (16f + 16f * near), head + Offset(0f, 10f)) }) {
                    oval(head + Offset(0f, -4f), 9f, 16f, petal)
                }
            }
        } else {
            val petals = 6 + v.pick(3, salt = 4)
            for (index in 0 until petals) {
                val angle = index * 2f * PI.toFloat() / petals
                drawCircle(petal, 9.5f, head + Offset(cos(angle), sin(angle)) * (13f + 3f * near))
            }
            drawCircle(if (petal == MoPropYellow) MoPropBrown else MoPropYellow, 10f, head)
        }
    }

    override val ownsTheFloor get() = true
}

/** A cat has fallen asleep against his foot, the warmest spot around, and he hardly dares to move. */
private object CatInHisWarmth : MoAct("idle_cat_in_his_warmth", MoOccasion.Idle, intensity = MoIntensity.Mild) {
    private fun side(v: MoVariant) = if (v.chance(50, salt = 2)) 1f else -1f

    // The two periods share no beat with each other or with the arm, so the scene never quite repeats.
    private fun earPeriod(v: MoVariant) = if (v.chance(50, salt = 4)) 7.1f else 5.2f

    private fun tailPeriod(v: MoVariant) = if (v.chance(50, salt = 4)) 5.2f else 7.1f

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val s = side(v)
        // His far arm starts to move, thinks better of it, and is lowered twice as carefully.
        val at = cycle(t, 6f) * 6f
        val reach = ramp(at, 2f, 2.9f) * (1f - ramp(at, 3.6f, 5.2f))
        val far = mix(MoArm.Down, MoArm(Offset(30f, -8f), bend = 0.35f), reach * 0.8f)
        val near = MoArm(Offset(14f, 22f))
        // The ear flicks, and for a moment he is sure he has woken it.
        val alarm = twitch(t, earPeriod(v), beat = 0.8f, offset = 0.9f)
        val eye = MoEye(gaze = Offset(0.8f * s, 0.9f), iris = 0.76f - 0.2f * alarm)
        return moIdle(t, energy = 0.5f).copy(
            leftEye = eye,
            rightEye = eye,
            brows = if (alarm > 0.05f) MoBrows(MoBrowKind.Worried, lift = 2f * alarm) else MoBrows(),
            mouth = MoMouth.Smile(12f - 4f * alarm, 9f - 5f * alarm),
            leftArm = if (s > 0f) far else near,
            rightArm = if (s > 0f) near else far,
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) = floorShadow(CENTRE + side(v) * 94f, 48f, MoPropShadow)

    override fun DrawScope.foreground(t: Float, v: MoVariant) {
        val s = side(v)
        val x = CENTRE + s * 92f
        // Each marking is a good step darker than its fur, or the tail is lost against the body.
        val (fur, mark) = v.of(
            listOf(MoPropGrey to MoPropGreyDark, MoPropOrange to MoPropWoodDark, MoPropCream to MoPropWood, MoPropWood to MoPropBrown),
            salt = 1,
        )
        val ry = 25f + 2.5f * pulse(t, 3.6f)
        oval(Offset(x, FEET + 1f - ry), 41f, ry, fur)
        if (v.chance(40, salt = 3)) oval(Offset(x + s * 10f, FEET + 10f - 2f * ry), 14f, 8f, MoPropWhite)

        // Its head is the end against his foot.
        val head = Offset(x - s * 26f, FEET - 21f)
        val flick = twitch(t, earPeriod(v), beat = 0.2f, offset = 0.9f)
        for (ear in listOf(-1f, 1f)) {
            val base = head + Offset(ear * 10.5f, -13f)
            // Only the ear away from him twitches.
            withTransform({ rotate(if (ear == s) ear * 16f * flick else 0f, base) }) {
                shape(fur, base.x - 6.5f to base.y, base.x + ear * 2.5f to base.y - 17f, base.x + 6.5f to base.y)
                shape(mark, base.x - 2f to base.y - 2f, base.x + ear * 2f to base.y - 10f, base.x + 2.5f to base.y - 2f)
            }
        }
        drawCircle(fur, 19.5f, head)
        for (eye in listOf(-1f, 1f)) {
            val centre = head + Offset(eye * 8.5f, -1f)
            curve(centre + Offset(-4.5f, -1.5f), centre + Offset(0f, 4.5f), centre + Offset(4.5f, -1.5f), MoPropInk, 3f)
        }
        drawCircle(MoPropPink, 2.6f, head + Offset(0f, 6.5f))

        // The tail leaves the back, swings clear of the silhouette, and comes round the front to the chin.
        val lift = 9f * twitch(t, tailPeriod(v), beat = 0.4f, offset = 0.5f)
        curve(Offset(x + s * 30f, FEET - 19f), Offset(x + s * 54f, FEET - 3f), Offset(x - s * 12f, FEET - 4f - lift), mark, 9f)
    }

    override val ownsTheFloor get() = true
}

/** A birthday candle stands beside him; he bends and waves at its tiny flame, and it wiggles back. */
private object LittleCousin : MoAct("idle_little_cousin", MoOccasion.Idle, intensity = MoIntensity.Mild) {
    private const val LOOP = 5f
    private const val WAG = 0.4f
    private const val HELLO = 1.6f

    private fun side(v: MoVariant) = if (v.chance(50, salt = 3)) 1f else -1f

    // A little way in, so the first frame catches him bent over with his hand at the top of a wag.
    private fun at(t: Float) = cycle(t, LOOP, offset = 0.1f) * LOOP

    private fun reply(at: Float) = ramp(at, HELLO, HELLO + 0.4f) * (1f - ramp(at, 2.6f, 3f))

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val s = side(v)
        val at = at(t)
        val bent = 1f - ramp(at, 3f, 3.8f) + ramp(at, 4.4f, LOOP)
        val wag = if (at < HELLO) wave(at, WAG) else 0f
        // The hand waves at a fixed height above the floor rather than at a fixed reach from a
        // shoulder the low body carries lower, and his lean swings it a good step further down and
        // out: with the tallest candle lit up to reply, the mitten still clears its tip by a hand's
        // width on either body, so it never looks dipped in the flame. The elbow is tucked in rather
        // than bowed out, or the crook of his arm passes straight over that little flame.
        val waving = MoArm(Offset(30f, FEET - 166f - MoBodies.of(body).shoulderY + 6f * wag), bend = -0.3f)
        val near = mix(MoArm.Down, waving, bent)
        val delighted = at > HELLO + 0.15f && at < 2.9f
        val eye = MoEye(
            shape = if (delighted) MoEyeShape.Happy else MoEyeShape.Round,
            gaze = mix(Offset(0.6f * s, 0.7f), Offset(0.8f * s, 0.9f), bent),
        )
        return moIdle(t).copy(
            leftEye = eye,
            rightEye = eye,
            mouth = when {
                at < HELLO -> MoMouth.Grin(open = 0.35f, tongue = false)
                // The smile rides the same lean the rest of him does, rather than shrinking in one frame.
                at < 4.7f -> MoMouth.Smile(12f + 4f * bent, 8f + 5f * bent)
                else -> MoMouth.Grin(open = 0.35f * ramp(at, 4.7f, LOOP), tongue = false)
            },
            leftArm = if (s > 0f) MoArm.Hip else near,
            rightArm = if (s > 0f) near else MoArm.Hip,
            tilt = s * mix(1.5f, 7f, bent),
            blinks = !delighted,
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val s = side(v)
        val at = at(t)
        val (wax, stripe) = v.of(listOf(MoPropPink to MoPropWhite, MoPropSky to MoPropYellow, MoPropMint to MoPropRed), salt = 1)
        if (v.chance(30, salt = 4)) {
            val x = CENTRE + s * 134f
            // No wider than the holder, which is as far out as the visible box reaches.
            floorShadow(x, 24f, MoPropShadow)
            chunk(Offset(x, FEET - 8f), 46f, 10f, MoPropGold, MoPropGoldDark, corner = 5f)
            // Both stand clear of his silhouette, or a little orange flame on his orange belly is no
            // flame at all. The tall one, out under his hand, wears the colours the other way round
            // and answers late.
            candle(x - s * 11f, 48f, wax, stripe, s, reply(at), t)
            candle(x + s * 11f, 64f, stripe, wax, s, reply(at - 0.4f), t + 0.5f)
        } else {
            val x = CENTRE + s * 136f
            floorShadow(x, 22f, MoPropShadow)
            chunk(Offset(x, FEET - 8f), 32f, 10f, MoPropGold, MoPropGoldDark, corner = 5f)
            candle(x, if (v.chance(50, salt = 2)) 50f else 66f, wax, stripe, s, reply(at), t)
        }
    }

    private fun DrawScope.candle(x: Float, height: Float, wax: Color, stripe: Color, s: Float, reply: Float, t: Float) {
        val top = FEET - 15f - height
        block(Offset(x, top + height / 2f), 18f, height, wax, corner = 6f)
        for (index in 1..3) block(Offset(x, top + height * index / 4f), 18f, 5f, stripe, corner = 0f)
        val wick = Offset(x, top - 6f)
        limb(Offset(x, top), wick, MoPropInk, 2.5f)
        // It leans his way and wiggles while it answers, and otherwise only flickers.
        val lean = -s * 26f * reply + 12f * wave(t, 0.3f) * reply + 3f * wave(t, 1.1f)
        withTransform({ rotate(lean, wick) }) {
            flameTongue(wick + Offset(0f, 3f), 8f + 3f * reply, MoFlameMiddle, MoFlameBelly)
        }
    }

    override val ownsTheFloor get() = true
}

/** A snail crosses the floor in front of him, and he politely lifts each foot to let it under. */
private object SnailCrossing : MoAct("idle_snail_crossing", MoOccasion.Idle, intensity = MoIntensity.Mild) {
    private const val SIZE = 1.3f

    // His base hides anything raised higher than this, and a foot nobody can see is no gesture at
    // all, so a lifted foot also swings out beside his base, and the snail is drawn in front of the
    // sole rather than under it.
    private const val LIFT = 24f
    private const val ASIDE = 32f

    // His feet are 62 apart, which is what times one foot against the other.
    private const val STRIDE = 62f

    // It crawls in from one edge of the view and out at the other, turning round out of sight. The
    // little one follows far enough behind that both feet are back where they belong before it arrives.
    private const val IN = CENTRE - 160f
    private const val SEEN = 320f
    private const val BEHIND = 210f

    private fun direction(v: MoVariant) = if (v.chance(50, salt = 3)) 1f else -1f

    private fun hasFollower(v: MoVariant) = v.chance(20, salt = 5)

    /**
     * Where a snail is, worked out as if it came from the left. The first frame is the widget's still,
     * so it has the leader already under him, past the foot he set aside and towards the one he holds
     * up: a snail just short of a rising foot looks as if it is about to be stepped on.
     */
    private fun fromLeft(t: Float, v: MoVariant, behind: Float): Float {
        val way = SEEN + if (hasFollower(v)) BEHIND else 0f
        val period = way * v.between(16f, 22f, salt = 4) / 270f
        return IN + way * cycle(t, period, offset = (CENTRE - IN) / way) - behind
    }

    /** His gaze settles back in front of him as the snail gets further from his feet. */
    private fun attention(x: Float) = ramp(x, -20f, 10f) * (1f - ramp(x, 212f, 242f))

    // The snail is longer than the gap between his feet, so he can never have both up at once: the
    // first foot goes up before the feelers reach it and comes down out to the side the moment the
    // tail is out from under it, and only then does the second go up, in time for the feelers, to
    // stay up until the whole snail is past. near is the snail's centre past the foot in question.
    private fun firstUp(near: Float) = ramp(near, -66f, -54f) * (1f - ramp(near, -1f, 11f))

    private fun secondUp(near: Float) = ramp(near, -51f, -39f) * (1f - ramp(near, 42f, 54f))

    /** The first foot stays out where it landed until the second is down again, then steps back home. */
    private fun firstOut(near: Float) = ramp(near, -66f, -54f) * (1f - ramp(near, 120f, 136f))

    private fun stepHome(near: Float) = sin(((near - 120f) / 16f).coerceIn(0f, 1f) * PI.toFloat())

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val direction = direction(v)
        val snails = if (hasFollower(v)) listOf(0f, BEHIND) else listOf(0f)
        // Worked out as if it came from the left; the feet swap over when it comes from the right.
        val firstFoot = CENTRE - STRIDE / 2f
        var first = MoLeg()
        var second = MoLeg()
        var firstRaised = 0f
        var secondRaised = 0f
        var look = 0f
        var seen = 0f
        for (behind in snails) {
            val x = fromLeft(t, v, behind)
            val near = x - firstFoot
            val one = firstUp(near)
            val two = secondUp(near - STRIDE)
            first = MoLeg(
                lift = max(first.lift, LIFT * one + 7f * stepHome(near)),
                swing = max(first.swing, ASIDE * firstOut(near)),
            )
            // The second comes straight back down where it stood, so its swing follows its lift.
            second = MoLeg(lift = max(second.lift, LIFT * two), swing = max(second.swing, ASIDE * two))
            firstRaised = max(firstRaised, one)
            secondRaised = max(secondRaised, two)
            look += direction * ((x - CENTRE) / 150f).coerceIn(-1f, 1f) * attention(x)
            seen += attention(x)
        }
        val up = max(firstRaised, secondRaised)
        // Only the arm over the foot in the air goes out, which reads as care and not as a windmill.
        val leftUp = if (direction > 0f) firstRaised else secondRaised
        val rightUp = if (direction > 0f) secondRaised else firstRaised
        val balance = MoArm(Offset(34f, 6f), bend = 0.2f)
        // With nothing in sight his eyes rest on the floor in front of him.
        val eye = MoEye(gaze = Offset(look / max(seen, 1f), 0.95f))
        return moIdle(t, energy = 0.7f).copy(
            leftEye = eye,
            rightEye = eye,
            brows = if (up > 0.1f) MoBrows(MoBrowKind.Raised, lift = 3f * up) else MoBrows(),
            mouth = MoMouth.Smile(12f, 8f),
            leftArm = mix(MoArm.Down, balance, leftUp),
            rightArm = mix(MoArm.Down, balance, rightUp),
            legs = if (direction > 0f) MoLegs(first, second) else MoLegs(second, first),
            // He leans away from the foot in the air.
            tilt = 2f * direction * (firstRaised - secondRaised),
        )
    }

    override fun DrawScope.foreground(t: Float, v: MoVariant, picture: Rect) =
        clipRect(picture.left, picture.top, picture.right, picture.bottom) {
            val direction = direction(v)
            for ((behind, size) in if (hasFollower(v)) listOf(BEHIND to 0.6f * SIZE, 0f to SIZE) else listOf(0f to SIZE)) {
                val fromLeft = fromLeft(t, v, behind)
                // Keep the crawl under his feet in step with his pose. Only the approach and departure
                // lengthen when a smaller body leaves more floor on either side.
                val distance = fromLeft - CENTRE
                val outer = ((abs(distance) - 90f) / 70f).coerceAtLeast(0f)
                val extension = outer * outer * (picture.width / 2f + 50f - 160f)
                val x = CENTRE + direction * (distance + sign(distance) * extension)
                snail(x, direction, size, t + behind, v)
            }
        }

    private fun DrawScope.snail(x: Float, direction: Float, size: Float, t: Float, v: MoVariant) {
        val (shell, shellDark) = v.of(
            listOf(MoPropPink to MoPropPinkDark, MoPropTeal to MoPropTealDark, MoPropPurple to MoPropPurpleDark, MoPropGold to MoPropGoldDark),
            salt = 1,
        )
        val skin = MoPropSand
        // The head reaches out and the rest bunches up behind it.
        val stretch = 8f * pulse(t, 1.4f)
        val tail = x - direction * 13f * size
        val head = x + direction * (13f + stretch) * size
        // The trail is the one part that may still be behind the edge while the snail is in sight.
        val trailEnd = tail - direction * 22f * size
        limb(Offset(tail - direction * 3f, FEET - 1f), Offset(trailEnd, FEET - 1f), MoPropWhite.copy(alpha = 0.25f), 2f)
        for (stalk in 0..1) {
            val from = Offset(head - direction * (2f + stalk * 5f) * size, FEET - 6f * size)
            val to = from + Offset(direction * (2f + 3f * wave(t + stalk * 0.4f, 1.9f)) * size, -10f * size)
            limb(from, to, skin, 2.5f * size)
            drawCircle(skin, 2.5f * size, to)
        }
        block(Offset((tail + head) / 2f, FEET - 4f * size), (26f + stretch) * size, 8f * size, skin, corner = 4f * size)
        val centre = Offset(x - direction * 4f * size, FEET - 13f * size)
        drawCircle(shell, 9f * size, centre)
        if (v.chance(50, salt = 2)) {
            drawCircle(shellDark, 5f * size, centre)
            drawCircle(shell, 2f * size, centre)
        } else {
            for (stripe in listOf(-1f, 1f)) {
                val at = centre + Offset(stripe * 3.5f * size, 0f)
                curve(at + Offset(0f, -7f * size), at + Offset(stripe * 3f * size, 0f), at + Offset(0f, 7f * size), shellDark, 2.5f * size)
            }
        }
    }

    override val ownsTheFloor get() = true
}

internal val idleActs2: List<MoAct> = listOf(
    OhHello,
    FeatherKeepUp,
    FlowerThinksHeIsTheSun,
    CatInHisWarmth,
    LittleCousin,
    SnailCrossing,
)
