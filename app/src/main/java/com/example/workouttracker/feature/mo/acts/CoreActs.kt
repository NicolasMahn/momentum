package com.example.workouttracker.feature.mo.acts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.example.workouttracker.core.ui.theme.MoPropGold
import com.example.workouttracker.core.ui.theme.MoPropGrey
import com.example.workouttracker.core.ui.theme.MoPropGreyDark
import com.example.workouttracker.core.ui.theme.MoPropInk
import com.example.workouttracker.core.ui.theme.MoPropMoon
import com.example.workouttracker.core.ui.theme.MoPropOrange
import com.example.workouttracker.core.ui.theme.MoPropPink
import com.example.workouttracker.core.ui.theme.MoPropShadow
import com.example.workouttracker.core.ui.theme.MoPropSky
import com.example.workouttracker.core.ui.theme.MoPropTeal
import com.example.workouttracker.core.ui.theme.MoPropTealDark
import com.example.workouttracker.core.ui.theme.MoPropWater
import com.example.workouttracker.core.ui.theme.MoPropYellow
import com.example.workouttracker.core.ui.theme.MoRageBelly
import com.example.workouttracker.core.ui.theme.MoRageMiddle
import com.example.workouttracker.core.ui.theme.MoSmoke
import com.example.workouttracker.domain.mo.MoIntensity
import com.example.workouttracker.domain.mo.MoOccasion
import com.example.workouttracker.feature.mo.CENTRE
import com.example.workouttracker.feature.mo.FEET
import com.example.workouttracker.feature.mo.MoArm
import com.example.workouttracker.feature.mo.MoBodyKind
import com.example.workouttracker.feature.mo.MoBrowKind
import com.example.workouttracker.feature.mo.MoBrows
import com.example.workouttracker.feature.mo.MoEye
import com.example.workouttracker.feature.mo.MoEyeShape
import com.example.workouttracker.feature.mo.MoLeg
import com.example.workouttracker.feature.mo.MoLegs
import com.example.workouttracker.feature.mo.MoMouth
import com.example.workouttracker.feature.mo.MoPalettes
import com.example.workouttracker.feature.mo.MoPose
import com.example.workouttracker.feature.mo.MoRig
import com.example.workouttracker.feature.mo.chunk
import com.example.workouttracker.feature.mo.cycle
import com.example.workouttracker.feature.mo.drop
import com.example.workouttracker.feature.mo.ease
import com.example.workouttracker.feature.mo.flameTongue
import com.example.workouttracker.feature.mo.floorShadow
import com.example.workouttracker.feature.mo.limb
import com.example.workouttracker.feature.mo.moIdle
import com.example.workouttracker.feature.mo.oval
import com.example.workouttracker.feature.mo.pulse
import com.example.workouttracker.feature.mo.smoke
import com.example.workouttracker.feature.mo.sparkle
import com.example.workouttracker.feature.mo.twitch
import com.example.workouttracker.feature.mo.wave
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/*
 * The first acts, one for each kind of moment, written as the pattern for all the others: start
 * from moIdle, change only what the act is about, keep every motion a function of t, and draw
 * props from the rig so they follow his hands.
 */

/** Overdue: the tantrum. Red, flaring, glaring, teeth gritted, both fists shaking, spitting fire. */
private object Tantrum : MoAct("angry_tantrum", MoOccasion.Angry, intensity = MoIntensity.Strong) {
    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val fists = MoArm(Offset(34f + wave(t, 0.11f) * 3f, -58f), bend = 0.9f)
        return moIdle(t, energy = 2.2f).copy(
            palette = MoPalettes.Rage,
            flare = 16f + 16f * pulse(t, 0.62f),
            shake = wave(t, 0.085f) * 1.8f,
            leftEye = MoEye(lid = 1f, iris = 0.56f, gaze = Offset(0.6f, 0.5f)),
            rightEye = MoEye(lid = 1f, iris = 0.56f, gaze = Offset(-0.6f, 0.5f)),
            brows = MoBrows(MoBrowKind.Angry),
            mouth = MoMouth.Gritted,
            leftArm = fists,
            rightArm = fists,
            blinks = false,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val side = if (v.chance(50)) 1f else -1f
        fumingMark(rig, 0.85f + 0.15f * pulse(t, 0.62f), side)
        // Tongues of fire spit off his tip and burn away, over and over.
        for (index in 0..2) {
            val rise = cycle(t, 1.3f, offset = index * 0.33f)
            val at = rig.tip + Offset((index - 1) * 44f, 20f - rise * 78f)
            flameTongue(at, 6f * (1f - rise * 0.6f), MoRageMiddle.copy(alpha = 1f - rise), MoRageBelly.copy(alpha = 1f - rise))
        }
    }

    override val sparkles get() = false
}

/** Everything is done: he juggles sparks, and his eyes follow them. */
private object JugglingSparks : MoAct("happy_juggling_sparks", MoOccasion.Happy) {
    private fun count(v: MoVariant) = 3 + v.pick(2)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val turn = cycle(t, 2.4f) * 2f * PI.toFloat()
        val look = Offset(-cos(turn) * 0.8f, -abs(sin(turn)))
        val beat = 2.4f / count(v)
        return moIdle(t, energy = 1.4f).copy(
            leftEye = MoEye(gaze = look),
            rightEye = MoEye(gaze = look),
            mouth = MoMouth.Grin(open = 0.8f),
            leftArm = MoArm(Offset(30f, -18f - 14f * pulse(t, beat)), bend = 0.5f),
            rightArm = MoArm(Offset(30f, -18f - 14f * pulse(t + 1.2f, beat)), bend = 0.5f),
            offset = Offset(0f, -3f * pulse(t, beat)),
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val colour = v.of(listOf(MoPropGold, MoPropOrange, MoPropYellow), salt = 1)
        val total = count(v)
        for (index in 0 until total) {
            val lap = cycle(t, 2.4f, offset = index.toFloat() / total) * 2f
            val returning = lap >= 1f
            val flight = if (returning) lap - 1f else lap
            val from = if (returning) rig.rightHand else rig.leftHand
            val to = if (returning) rig.leftHand else rig.rightHand
            val base = from + (to - from) * flight
            val crest = rig.tip.y - if (returning) 22f else 55f
            val arc = 4f * flight * (1f - flight)
            val at = Offset(base.x, base.y - 8f - (base.y - crest) * arc)
            sparkle(at, 14f, 1f, colour)
        }
    }

    override val sparkles get() = false
}

/** A task with the food motif is open: he stares at his empty bowl, drooling, one hand on a rumbling belly. */
private object EmptyBowl : MoAct("hungry_empty_bowl", MoOccasion.Hungry, intensity = MoIntensity.Mild) {
    private val bowl = Offset(CENTRE - 118f, FEET - 14f)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose = moIdle(t, energy = 0.7f).copy(
        leftEye = MoEye(gaze = Offset(-0.9f, 0.9f), lid = -0.35f),
        rightEye = MoEye(gaze = Offset(-0.9f, 0.9f), lid = -0.35f),
        brows = MoBrows(MoBrowKind.Worried),
        mouth = MoMouth.Drool,
        rightArm = MoArm(Offset(-46f, 22f), bend = 0.4f),
        armsInFront = true,
        tilt = -3f,
    )

    override fun DrawScope.world(t: Float, v: MoVariant) {
        floorShadow(bowl.x, 40f, MoPropShadow)
        chunk(bowl, width = 70f, height = 26f, colour = v.of(listOf(MoPropTeal, MoPropSky, MoPropPink)), ledge = MoPropTealDark, corner = 13f)
        oval(bowl + Offset(0f, -12f), 30f, 6f, MoPropInk)
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        // The rumble: little arcs that shake loose from his belly.
        val rumble = twitch(t, period = 2.4f, beat = 0.9f)
        for (side in listOf(-1f, 1f)) {
            val at = rig.mouth + Offset(side * (44f + rumble * 8f), 44f)
            limb(at, at + Offset(side * 8f, -8f), MoPropGreyDark.copy(alpha = rumble), 4f)
            limb(at + Offset(side * 8f, -8f), at + Offset(side * 16f, 0f), MoPropGreyDark.copy(alpha = rumble), 4f)
        }
    }

    override val ownsTheFloor get() = true
}

/** A task with the book motif is open: his eyes have drifted apart, his tongue is out, and a fly circles his tip. */
private object FollowingAFly : MoAct("dumb_following_a_fly", MoOccasion.Dumb, intensity = MoIntensity.Mild) {
    private fun angle(t: Float, v: MoVariant) = t * 2.1f + if (v.chance(50)) PI.toFloat() else 0f

    private fun fly(angle: Float, tip: Offset): Offset {
        return tip + Offset(cos(angle) * 58f, -34f + sin(angle * 2f) * 18f)
    }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val angle = angle(t, v)
        return moIdle(t, energy = 0.6f).copy(
            // The eyes chase the fly, but never quite together.
            leftEye = MoEye(gaze = Offset(cos(angle), -0.9f), iris = 0.6f),
            rightEye = MoEye(gaze = Offset(cos(angle - 1.1f), -0.2f), iris = 0.6f),
            brows = MoBrows(MoBrowKind.Raised, skew = 7f),
            mouth = MoMouth.TongueOut,
            tilt = 4f * sin(angle),
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val at = fly(angle(t, v), rig.tip)
        val flap = abs(wave(t, 0.07f))
        oval(at + Offset(-5f, -5f - flap * 3f), 6f, 3.5f, MoPropSky.copy(alpha = 0.85f))
        oval(at + Offset(5f, -5f - flap * 3f), 6f, 3.5f, MoPropSky.copy(alpha = 0.85f))
        oval(at, 5.5f, 4.5f, MoPropInk)
    }
}

/** No freeze and the day is running out: pale, tiny pupils, hands on his cheeks, knees knocking. */
private object KnockingKnees : MoAct("scared_knocking_knees", MoOccasion.Scared, intensity = MoIntensity.Strong) {
    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val knock = wave(t, 0.16f) * 4f
        val cheek = MoArm(Offset(-32f, 4f), bend = 0.45f)
        val gaze = Offset(if (v.chance(50)) 0.65f else -0.65f, -0.4f)
        return moIdle(t, energy = 0.5f).copy(
            palette = MoPalettes.Pale,
            leftEye = MoEye(iris = 0.3f, scale = 1.06f, gaze = gaze),
            rightEye = MoEye(iris = 0.3f, scale = 1.06f, gaze = gaze),
            brows = MoBrows(MoBrowKind.Worried, lift = 5f),
            mouth = MoMouth.Zigzag,
            leftArm = cheek,
            rightArm = cheek,
            armsInFront = true,
            legs = MoLegs(MoLeg(swing = -7f + knock), MoLeg(swing = -7f + knock)),
            shake = wave(t, 0.06f) * 1.6f,
            squash = -0.03f,
            blinks = false,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        for (index in 0..2) {
            val fly = cycle(t, 0.9f, offset = index * 0.33f)
            val side = if (index % 2 == 0) -1f else 1f
            drop(rig.tip + Offset(side * (50f + fly * 40f), 60f - fly * 50f + index * 22f), 5f, MoPropWater.copy(alpha = 1f - fly))
        }
    }
}

/** Late at night: asleep on his feet, with a snore bubble that grows and pops, under a moon. */
private object SnoreBubble : MoAct("sleepy_snore_bubble", MoOccasion.Sleepy) {
    private fun breath(t: Float) = cycle(t, 3.4f, offset = 0.6f)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose = moIdle(t, energy = 0.4f).copy(
        leftEye = MoEye(shape = MoEyeShape.Shut),
        rightEye = MoEye(shape = MoEyeShape.Shut),
        mouth = MoMouth.Oh(size = 5f + 2f * pulse(breath(t), 1f)),
        tilt = 5f + 2f * pulse(breath(t), 1f),
        squash = 0.03f * pulse(breath(t), 1f) - 0.015f,
        blinks = false,
    )

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val moon = Offset(if (v.chance(50)) 230f else 0f, 46f)
        drawCircle(MoPropMoon, 26f, moon)
        drawCircle(MoPropGrey.copy(alpha = 0.35f), 6f, moon + Offset(-8f, -6f))
        drawCircle(MoPropGrey.copy(alpha = 0.35f), 4f, moon + Offset(9f, 8f))
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        // The bubble swells with each breath out and pops at the top of it.
        val breath = breath(t)
        if (breath < 0.86f) {
            val size = 27f * breath
            val at = rig.mouth + Offset(2f + size * 0.92f, 4f - size * 0.18f)
            drawCircle(MoPropSky.copy(alpha = 0.55f), size, at)
            drawCircle(MoPropMoon, size * 0.22f, at + Offset(-size * 0.4f, -size * 0.4f))
        }
    }

    override val sparkles get() = false
}

/**
 * Past the hour he should be asleep: tucked in and breathing slowly, which says "so should you".
 * The pattern for every act in bed: [bedBehind], [moInBed], then [bedBlanket] in front.
 */
private object TuckedIn : MoAct("sleepy_bed_tucked_in", MoOccasion.Sleepy, intensity = MoIntensity.Strong) {
    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose = moInBed(t, body, tilt = if (v.chance(50)) 4f else -4f).copy(
        leftEye = MoEye(shape = MoEyeShape.Shut),
        rightEye = MoEye(shape = MoEyeShape.Shut),
        mouth = MoMouth.Smile(width = 9f, depth = 6f),
        blinks = false,
    )

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val side = if (v.chance(50, salt = 1)) 1f else -1f
        val moon = Offset(CENTRE + side * 118f, 44f)
        drawCircle(MoPropMoon, 24f, moon)
        drawCircle(MoPropGrey.copy(alpha = 0.35f), 5f, moon + Offset(-7f, -5f))
        sparkle(Offset(CENTRE - side * 112f, 70f), 7f, 1f + 0.3f * pulse(t, 2.6f), MoPropMoon)
        sparkle(Offset(CENTRE - side * 64f, 28f), 5f, 1f + 0.3f * pulse(t + 1.1f, 3.1f), MoPropMoon)
        bedBehind()
    }

    override fun DrawScope.foreground(t: Float, v: MoVariant) = bedBlanket(t, v)

    override val ownsTheFloor get() = true
    override val sparkles get() = false
}

/** Nothing going on: he whistles a tune and watches the notes float away. */
private object Whistling : MoAct("idle_whistling", MoOccasion.Idle, intensity = MoIntensity.Mild) {
    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val look = Offset(0.7f, -0.5f + 0.3f * sin(t * 0.9f))
        return moIdle(t).copy(
            leftEye = MoEye(gaze = look, lidDrop = 0.18f),
            rightEye = MoEye(gaze = look, lidDrop = 0.18f),
            mouth = MoMouth.Whistle,
            leftArm = MoArm.Hip,
            tilt = 2f * wave(t, 1.6f),
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val colour = v.of(listOf(MoPropTeal, MoPropPink, MoPropYellow, MoPropSky), salt = 2)
        for (index in 0..2) {
            val rise = cycle(t, 2.7f, offset = index * 0.33f)
            // They leave from beside him, clear of his face, and drift up and away.
            val head = Offset(rig.rightEye.x + 66f + rise * 22f + sin(rise * 6f + index) * 5f, rig.mouth.y - 10f - rise * 150f)
            val fade = colour.copy(alpha = ease(rise * 12f) * (1f - rise * rise))
            oval(head, 7f, 5.5f, fade)
            limb(head + Offset(6f, 0f), head + Offset(6f, -22f), fade, 3.5f)
            limb(head + Offset(6f, -22f), head + Offset(15f, -17f), fade, 3.5f)
        }
    }
}

/** Cold: he hugs what is left of his fire, shivering, with tears that keep coming. */
private object HuggingTheCore : MoAct("cold_hugging_the_core", MoOccasion.Cold, setOf(MoBodyKind.Droplet)) {
    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose = moIdle(t, energy = 0f).copy(
        leftEye = MoEye(gaze = Offset(0f, -0.7f), tear = cycle(t, 3.4f)),
        rightEye = MoEye(gaze = Offset(0f, -0.7f), tear = cycle(t, 3.4f, offset = 0.5f)),
        brows = MoBrows(MoBrowKind.Worried),
        mouth = MoMouth.Frown(),
        leftArm = MoArm(Offset(-62f, 38f), bend = -0.55f),
        rightArm = MoArm(Offset(-62f, 38f), bend = -0.55f),
        armsInFront = true,
        shake = wave(t, 0.11f) * 1.6f,
    )

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        smoke(rig.tip + Offset(34f, 8f), cycle(t, v.between(3.2f, 4.4f)))
    }
}

/** A Husk: nothing moves but his spark, and a moth that has come to look at it. */
private object MothAtTheSpark : MoAct("out_moth_at_the_spark", MoOccasion.Out, setOf(MoBodyKind.Husk)) {
    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose = MoPose()

    override fun DrawScope.foreground(t: Float, v: MoVariant) {
        val angle = t * 1.3f + if (v.chance(50)) PI.toFloat() else 0f
        val at = Offset(CENTRE + cos(angle) * 46f, 372f + sin(angle * 2f) * 16f)
        val flap = abs(wave(t, 0.09f))
        oval(at + Offset(-6f, -2f - flap * 4f), 7f, 5f, MoPropGrey.copy(alpha = 0.9f))
        oval(at + Offset(6f, -2f - flap * 4f), 7f, 5f, MoPropGrey.copy(alpha = 0.9f))
        oval(at, 3f, 5.5f, MoSmoke)
    }
}

/** Frozen by a freeze: he is in the ice, so only the world can move. A snowflake keeps falling past. */
private object SnowOnTheIce : MoAct("frozen_snow_on_the_ice", MoOccasion.Frozen, MoBodyKind.entries.toSet()) {
    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose = frozenRest(body).copy(
        leftEye = MoEye(iris = 0.5f),
        rightEye = MoEye(iris = 0.5f),
        mouth = MoMouth.Oh(size = 6f),
    )

    override fun DrawScope.foreground(t: Float, v: MoVariant) {
        val colour = v.of(listOf(MoPropSky, MoPropMoon))
        for (index in 0..5) {
            val fall = cycle(t, 5.5f, offset = index / 6f)
            val x = -26f + index * 56f + sin(fall * 5f + index) * 12f
            sparkle(Offset(x, 8f + fall * (FEET - 16f)), 7f, 1f, colour.copy(alpha = 0.9f * sin(fall * PI.toFloat())))
        }
    }

    override val sparkles get() = false
}

/** A triumph: golden, star-eyed, leaping with both arms up. The act of his biggest days. */
private object Triumph : MoAct("proud_triumph", MoOccasion.Proud, intensity = MoIntensity.Strong) {
    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val leap = pulse(t, 0.74f)
        val reach = MoArm(Offset(34f + wave(t, 0.37f) * 8f, -66f), bend = 0.3f)
        return moIdle(t, energy = 2f).copy(
            palette = MoPalettes.Golden,
            leftEye = MoEye(shape = MoEyeShape.Star),
            rightEye = MoEye(shape = MoEyeShape.Star),
            mouth = MoMouth.Beam,
            leftArm = reach,
            rightArm = reach,
            legs = MoLegs(MoLeg(lift = 10f * leap, swing = 6f * leap), MoLeg(lift = 10f * leap, swing = 6f * leap)),
            offset = Offset(0f, -26f * leap),
            squash = 0.05f * leap - 0.02f,
            flare = 18f * leap,
            blinks = false,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val colour = v.of(listOf(MoPropGold, MoPropYellow, MoPropOrange), salt = 1)
        for (index in 0..5) {
            val out = cycle(t, 1.48f, offset = index / 6f)
            val angle = index * 1.05f + 0.4f
            sparkle(rig.tip + Offset(cos(angle) * (40f + out * 90f), 70f - sin(angle) * (32f + out * 42f)), 15f * (1f - out), ease(out * 12f), colour)
        }
    }

    override val sparkles get() = false
}

/** Relief after a bad day: eyes squeezed shut in joy, tears rolling, hugging himself. */
private object TearsOfJoy : MoAct("happy_tears_of_joy", MoOccasion.Happy, intensity = MoIntensity.Strong) {
    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose = moIdle(t, energy = 1.2f).copy(
        leftEye = MoEye(shape = MoEyeShape.Happy, tear = cycle(t, 1.9f)),
        rightEye = MoEye(shape = MoEyeShape.Happy, tear = cycle(t, 1.9f, offset = 0.5f)),
        mouth = MoMouth.Grin(open = 0.6f + 0.3f * pulse(t, 0.9f)),
        leftArm = MoArm(Offset(-58f, 30f), bend = -0.5f),
        rightArm = MoArm(Offset(-58f, 30f), bend = -0.5f),
        armsInFront = true,
        tilt = (if (v.chance(50)) 3f else -3f) + 2f * wave(t, 1.8f),
        squash = 0.02f * pulse(t, 0.9f),
    )
}

internal val coreActs: List<MoAct> = listOf(
    Tantrum,
    JugglingSparks,
    EmptyBowl,
    FollowingAFly,
    KnockingKnees,
    SnoreBubble,
    TuckedIn,
    Whistling,
    HuggingTheCore,
    MothAtTheSpark,
    SnowOnTheIce,
    Triumph,
    TearsOfJoy,
)
