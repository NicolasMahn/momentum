package com.example.workouttracker.feature.mo.acts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import com.example.workouttracker.core.ui.theme.MoPropBlue
import com.example.workouttracker.core.ui.theme.MoPropBlueDark
import com.example.workouttracker.core.ui.theme.MoPropBrown
import com.example.workouttracker.core.ui.theme.MoPropCharcoal
import com.example.workouttracker.core.ui.theme.MoPropGold
import com.example.workouttracker.core.ui.theme.MoPropGoldDark
import com.example.workouttracker.core.ui.theme.MoPropGreen
import com.example.workouttracker.core.ui.theme.MoPropGrey
import com.example.workouttracker.core.ui.theme.MoPropGreyDark
import com.example.workouttracker.core.ui.theme.MoPropInk
import com.example.workouttracker.core.ui.theme.MoPropPink
import com.example.workouttracker.core.ui.theme.MoPropPinkDark
import com.example.workouttracker.core.ui.theme.MoPropRed
import com.example.workouttracker.core.ui.theme.MoPropRedDark
import com.example.workouttracker.core.ui.theme.MoPropSand
import com.example.workouttracker.core.ui.theme.MoPropShadow
import com.example.workouttracker.core.ui.theme.MoPropSilver
import com.example.workouttracker.core.ui.theme.MoPropSky
import com.example.workouttracker.core.ui.theme.MoPropSteel
import com.example.workouttracker.core.ui.theme.MoPropTeal
import com.example.workouttracker.core.ui.theme.MoPropTealDark
import com.example.workouttracker.core.ui.theme.MoPropWater
import com.example.workouttracker.core.ui.theme.MoPropWhite
import com.example.workouttracker.core.ui.theme.MoPropWood
import com.example.workouttracker.core.ui.theme.MoPropWoodDark
import com.example.workouttracker.core.ui.theme.MoPropYellow
import com.example.workouttracker.core.ui.theme.MoSmoke
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
import com.example.workouttracker.feature.mo.MoLeg
import com.example.workouttracker.feature.mo.MoLegs
import com.example.workouttracker.feature.mo.MoMouth
import com.example.workouttracker.feature.mo.MoPose
import com.example.workouttracker.feature.mo.MoRig
import com.example.workouttracker.feature.mo.block
import com.example.workouttracker.feature.mo.chunk
import com.example.workouttracker.feature.mo.cobweb
import com.example.workouttracker.feature.mo.curve
import com.example.workouttracker.feature.mo.cycle
import com.example.workouttracker.feature.mo.drop
import com.example.workouttracker.feature.mo.ease
import com.example.workouttracker.feature.mo.flameTongue
import com.example.workouttracker.feature.mo.floorShadow
import com.example.workouttracker.feature.mo.grippingMitten
import com.example.workouttracker.feature.mo.limb
import com.example.workouttracker.feature.mo.moIdle
import com.example.workouttracker.feature.mo.oval
import com.example.workouttracker.feature.mo.polyline
import com.example.workouttracker.feature.mo.shape
import com.example.workouttracker.feature.mo.sparkle
import com.example.workouttracker.feature.mo.twitch
import com.example.workouttracker.feature.mo.wave
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * Hungry, first part: A task with the food motif is still open. He is never starving, only theatrically peckish, and
 * every joke ends on an empty plate.
 */

private fun mix(from: Float, to: Float, amount: Float): Float = from + (to - from) * amount

private fun mix(from: Offset, to: Offset, amount: Float): Offset = from + (to - from) * amount

private fun mix(from: MoArm, to: MoArm, amount: Float): MoArm =
    MoArm(mix(from.reach, to.reach, amount), mix(from.bend, to.bend, amount))

private fun MoRig.hand(side: Float): Offset = if (side < 0f) leftHand else rightHand

/** His belly growls loudly enough to startle him, so he clamps both mittens over it and checks who heard. */
private object GrowlEveryoneHeard : MoAct("hungry_growl_everyone_heard", MoOccasion.Hungry) {
    private const val PERIOD = 4.8f

    // Full at the wrap from both sides: the growl that opens the loop is the one that built up at its end.
    private fun growl(g: Float) = max(1f - ease(g / 0.2f), ease((g - 0.85f) / 0.15f))

    private fun clamp(g: Float) = ease((g - 0.2f) / 0.08f) * (1f - ease((g - 0.62f) / 0.12f))

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val g = cycle(t, PERIOD)
        val growl = growl(g)
        val clamp = clamp(g)
        val first = if (v.chance(50, salt = 2)) 1f else -1f
        val dart = first * sin((g - 0.2f) / 0.42f * 2f * PI.toFloat()) * 0.9f * clamp
        val eye = MoEye(iris = 0.76f - 0.26f * max(growl, clamp), gaze = Offset(dart, 0.9f * growl))
        val hug = MoArm(Offset(-58f, 30f), bend = -0.5f)
        return moIdle(t, energy = 0.8f).copy(
            leftEye = eye,
            rightEye = eye,
            brows = MoBrows(MoBrowKind.Worried, lift = 4f * growl),
            mouth = when {
                growl > 0.5f -> MoMouth.Oh(7f)
                clamp > 0.3f || growl > 0.1f -> MoMouth.Wavy
                else -> MoMouth.Flat(10f)
            },
            leftArm = mix(MoArm.Down, hug, clamp),
            rightArm = mix(MoArm(Offset(-46f, 22f), bend = 0.4f), hug, clamp),
            armsInFront = true,
            shake = wave(t, 0.07f) * 2.2f * growl,
            squash = 0.03f * growl,
            blinks = growl < 0.5f,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val g = cycle(t, PERIOD)
        val growl = growl(g)
        val clamp = clamp(g)
        val marks = 2 + v.pick(2, salt = 1)
        val colour = MoPropGrey.copy(alpha = growl)
        for (side in floatArrayOf(-1f, 1f)) {
            for (index in 0 until marks) {
                // They start on his flank and shake outwards, so the rumble plainly comes out of him. Each
                // row sits a little further in, because he narrows towards his feet on either body.
                val start = if (side < 0f) 115f else 98f - index * 14f + 6f * growl
                val from = Offset(CENTRE + side * start, rig.body.shoulderY + 4f + index * 16f)
                polyline(colour, 5.5f, (0..8).map { from + Offset(side * it * 4.1f, if (it % 2 == 0) 5f else -5f) })
            }
        }
        val sweatSide = if (v.chance(50, salt = 3)) 1f else -1f
        val slide = ((g - 0.25f) / 0.35f).coerceIn(0f, 1f)
        val eye = if (sweatSide < 0f) rig.leftEye else rig.rightEye
        drop(eye + Offset(sweatSide * 38f, -34f + 30f * slide), 7f, MoPropWater.copy(alpha = clamp * (1f - slide)))
    }
}

/** He is a flame, so he fries his one morsel over his own shoulder, flipping it and watching it fall. */
private object OwnStove : MoAct("hungry_own_stove", MoOccasion.Hungry, intensity = MoIntensity.Mild) {
    private const val PERIOD = 4f

    private fun side(v: MoVariant) = if (v.chance(50, salt = 1)) 1f else -1f

    // The tall body has a shoulder flame to cook on, so the pan goes up over it; the low one has none, so
    // the pan goes in over its one broad flame. Either way it sits over him, where his fire can reach it.
    private fun reach(body: MoBodyKind) = if (body == MoBodyKind.Tall) Offset(32f, -51f) else Offset(30f, -51f)

    private fun panFromHand(body: MoBodyKind, side: Float) =
        if (body == MoBodyKind.Tall) Offset(-side * 52f, -101f) else Offset(-side * 59f, -81f)

    private fun shakeX(g: Float) = if (g in 0.25f..0.6f) sin((g - 0.25f) / 0.35f * 4f * PI.toFloat()) * 5f else 0f

    private fun flip(g: Float) = ((g - 0.6f) / 0.3f).coerceIn(0f, 1f)

    private fun morselUp(g: Float, v: MoVariant): Float {
        val f = flip(g)
        return if (v.chance(30, salt = 4)) abs(sin(f * 2f * PI.toFloat())) * 34f else sin(f * PI.toFloat()) * 62f
    }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val g = cycle(t, PERIOD)
        val side = side(v)
        val f = flip(g)
        val up = morselUp(g, v) / 62f
        val dip = if (g > 0.9f) sin((g - 0.9f) / 0.1f * PI.toFloat()) * 5f else 0f
        val toss = 8f * sin(min(f * 4f, 1f) * PI.toFloat())
        val holding = MoArm(reach(body) + Offset(side * shakeX(g), dip - toss), bend = if (body == MoBodyKind.Tall) 0.3f else 0.5f)
        val eye = MoEye(gaze = Offset(side * 0.8f, -0.5f - 0.5f * up), scale = 1f + 0.04f * ease(up * 3f))
        return moIdle(t).copy(
            leftEye = eye,
            rightEye = eye,
            brows = MoBrows(MoBrowKind.Raised, lift = 5f * up),
            mouth = if (up > 0.12f) MoMouth.Oh(6f) else MoMouth.TongueOut,
            leftArm = if (side < 0f) holding else MoArm.Hip,
            rightArm = if (side > 0f) holding else MoArm.Hip,
            tilt = -side * 2f,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val g = cycle(t, PERIOD)
        val side = side(v)
        val hand = rig.hand(side)
        val tall = rig.body.kind == MoBodyKind.Tall
        val pan = hand + panFromHand(rig.body.kind, side)
        val (face, ledge) = v.of(listOf(MoPropCharcoal to MoPropInk, MoPropRed to MoPropRedDark), salt = 2)

        // His own fire licks the pan and leaves no mark on it.
        val lick = 1f + 0.15f * wave(t, 0.4f)
        if (tall) {
            flameTongue(pan + Offset(-side * 4f, 46f), 13f * lick, rig.palette.middle, rig.palette.belly)
        } else {
            flameTongue(pan + Offset(-side * 10f, 40f), 12f * lick, rig.palette.middle, rig.palette.belly)
            flameTongue(pan + Offset(side * 10f, 35f), 8f * (1f + 0.15f * wave(t + 0.2f, 0.4f)), rig.palette.middle, rig.palette.belly)
        }
        limb(hand, pan + Offset(side * 28f, 4f), MoPropGreyDark, 7f)
        oval(pan + Offset(0f, 5f), 34f, 11f, ledge)
        oval(pan, 34f, 10f, face)
        oval(pan, 27f, 6.5f, MoPropGreyDark)
        grippingMitten(rig, side)

        val f = flip(g)
        val at = pan + Offset(side * shakeX(g) * 0.6f, -3f - morselUp(g, v))
        // It tumbles once on the way, edge on at the top of the arc.
        val flat = 0.35f + 0.65f * abs(cos(f * PI.toFloat()))
        if (v.chance(50, salt = 3)) {
            drawCircle(MoPropGreen, 6.5f, at + Offset(0f, -2f))
        } else {
            oval(at, 7.5f, 5.5f * flat, MoPropYellow)
        }
    }
}

/** He lifts the serving dome, finds the plate bare, puts it back, and then checks again just in case. */
private object UnderTheCloche : MoAct("hungry_under_the_cloche", MoOccasion.Hungry) {
    private const val PERIOD = 5.2f
    private const val APART = 14f
    private const val PLATE = FEET - 66f

    // The mitten grips the knob, the dome hangs under it: this far from his hand down to the dome's rim.
    private const val RIM = 44f

    private fun side(v: MoVariant) = if (v.chance(50, salt = 1)) 1f else -1f

    private fun standX(v: MoVariant) = CENTRE + side(v) * 124f

    private fun lift(g: Float) = 1f - ease((g - 0.25f) / 0.15f) + ease((g - 0.75f) / 0.15f)

    private fun look(g: Float) = ease((g - 0.4f) / 0.04f) * (1f - ease((g - 0.65f) / 0.1f))

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val g = cycle(t, PERIOD)
        val side = side(v)
        val b = MoBodies.of(body)
        val lift = lift(g)
        val look = look(g)
        val dare = v.between(0.7f, 1f, salt = 4)
        // Hope is up on both sides of the wrap and falls as he takes in the bare plate.
        val hope = max(ease((g - 0.72f) / 0.06f), 1f - ease((g - 0.04f) / 0.14f))
        val find = max(ease((g - 0.93f) / 0.05f), 1f - ease((g - 0.08f) / 0.1f))
        val eye = MoEye(
            gaze = mix(Offset(side * 0.85f, 0.5f), Offset.Zero, look),
            iris = 0.76f - 0.26f * find,
            scale = 1f + 0.08f * hope,
        )
        // Measured from the stand, so the mitten is on the knob on either body.
        val near = MoArm(Offset(124f + APART - b.shoulderHalf, PLATE - RIM - 44f * lift * dare - b.shoulderY), bend = 0.35f)
        val tap = MoLeg(lift = 5f * twitch(t, 0.45f, beat = 0.2f) * look)
        return moIdle(t).copy(
            leftEye = eye,
            rightEye = eye,
            brows = when {
                look > 0.5f -> MoBrows()
                hope > 0.5f -> MoBrows(MoBrowKind.Raised, lift = 6f * hope)
                else -> MoBrows(MoBrowKind.Worried)
            },
            mouth = when {
                look > 0.5f -> MoMouth.Pout
                hope > 0.5f -> MoMouth.Oh(6f)
                else -> MoMouth.Flat(11f)
            },
            leftArm = if (side < 0f) near else MoArm.Down,
            rightArm = if (side > 0f) near else MoArm.Down,
            armsInFront = true,
            legs = if (side > 0f) MoLegs(left = tap) else MoLegs(right = tap),
            offset = Offset(-side * APART, 0f),
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val x = standX(v)
        floorShadow(x, 34f, MoPropShadow)
        chunk(Offset(x, FEET - 27f), 30f, 50f, MoPropWood, MoPropWoodDark)
        chunk(Offset(x, FEET - 58f), 68f, 12f, MoPropWood, MoPropWoodDark)
        oval(Offset(x, PLATE + 2f), 33f, 7f, MoPropGrey)
        oval(Offset(x, PLATE), 33f, 7f, MoPropWhite)
        oval(Offset(x, PLATE), 22f, 4f, MoPropSilver)
        sparkle(Offset(x - 25f, PLATE - 4f), 6f, 1f, MoPropWhite)
        when (v.pick(4, salt = 3)) {
            1 -> drawCircle(MoPropSand, 3f, Offset(x + 4f, PLATE - 2f))
            2 -> drawCircle(MoPropGreen, 4f, Offset(x - 3f, PLATE - 3f))
            3 -> withTransform({
                translate(x + 26f, PLATE + 9f)
                scale(0.8f, 0.8f, Offset.Zero)
            }) { cobweb() }
        }
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val side = side(v)
        val hand = rig.hand(side)
        val rim = hand + Offset(0f, RIM)
        val (face, ledge) = v.of(listOf(MoPropSilver to MoPropSteel, MoPropGold to MoPropGoldDark), salt = 2)
        val dome = Path().apply {
            arcTo(Rect(rim, 31f), 180f, 180f, forceMoveTo = true)
            close()
        }
        drawCircle(ledge, 6f, hand + Offset(0f, 8f))
        drawPath(dome, face)
        block(rim + Offset(0f, 2f), 66f, 6f, ledge, corner = 3f)
        curve(rim + Offset(-23f, -9f), rim + Offset(-21f, -22f), rim + Offset(-10f, -27f), MoPropWhite, 4f)
        grippingMitten(rig, side)
    }

    override fun DrawScope.foreground(t: Float, v: MoVariant) {
        if (!v.chance(15, salt = 5)) return
        // All that was under there: a moth, let out with every lift and gone before the wrap.
        val away = ((cycle(t, PERIOD) - 0.78f) / 0.2f).coerceIn(0f, 1f)
        val alpha = sin(away * PI.toFloat())
        val at = Offset(standX(v) + sin(away * 9f) * 14f, PLATE - 8f - away * 210f)
        val flap = abs(wave(t, 0.09f))
        oval(at + Offset(-6f, -2f - flap * 4f), 7f, 5f, MoPropGrey.copy(alpha = 0.9f * alpha))
        oval(at + Offset(6f, -2f - flap * 4f), 7f, 5f, MoPropGrey.copy(alpha = 0.9f * alpha))
        oval(at, 3f, 5.5f, MoSmoke.copy(alpha = alpha))
    }

    override val ownsTheFloor get() = true
}

/** He waits at a laid table, thumping fork and spoon in turn like a child who was promised dinner. */
private object TableDrumroll : MoAct("hungry_table_drumroll", MoOccasion.Hungry) {
    private const val PERIOD = 2.6f
    private const val BEAT = 0.18f

    // Where the butts of the cutlery land: the top face of the table.
    private const val TABLE_TOP = FEET - 35f

    private fun beats(t: Float, v: MoVariant, left: Boolean): Float {
        val phases = when {
            v.chance(50, salt = 5) -> if (left) listOf(0f, 0.4f) else listOf(0.2f, 0.6f)
            else -> if (left) listOf(0f, 0.5f) else listOf(0.25f)
        }
        return phases.maxOf { twitch(t, PERIOD, BEAT, offset = -it) }
    }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val g = cycle(t, PERIOD)
        val left = beats(t, v, left = true)
        val right = beats(t, v, left = false)
        val thump = max(left, right)
        val glance = (if (v.chance(50, salt = 6)) 1f else -1f) * ease((g - 0.78f) / 0.06f) * (1f - ease((g - 0.93f) / 0.06f))
        val eye = MoEye(gaze = Offset(glance * 0.85f, -0.5f + 0.3f * abs(glance)), scale = 1.05f)
        val idle = moIdle(t, energy = 1.2f)
        return idle.copy(
            leftEye = eye,
            rightEye = eye,
            brows = MoBrows(MoBrowKind.Raised, lift = 4f + 3f * thump),
            mouth = if (thump > 0.3f) MoMouth.Oh(4f + 3f * thump) else MoMouth.Smile(13f, 10f),
            leftArm = MoArm(Offset(30f, 4f + 16f * left), bend = 0.6f),
            rightArm = MoArm(Offset(30f, 4f + 16f * right), bend = 0.6f),
            offset = Offset(0f, 3f * thump),
            squash = idle.squash - 0.02f * thump,
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) = floorShadow(CENTRE, 124f, MoPropShadow)

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val colour = v.of(listOf(MoPropSilver, MoPropGold, MoPropWood), salt = 7)
        val forkSide = if (v.chance(50, salt = 1)) 1f else -1f
        // Long enough below the fist to meet the table when the arm is all the way down, on either body.
        val butt = TABLE_TOP - (rig.body.shoulderY + 20f)
        for (side in floatArrayOf(-1f, 1f)) {
            val hand = rig.hand(side)
            limb(hand + Offset(0f, butt), hand + Offset(0f, -44f), colour, 7f)
            if (side == forkSide) {
                block(hand + Offset(0f, -50f), 18f, 12f, colour, corner = 3f)
                for (prong in -1..1) limb(hand + Offset(prong * 7f, -54f), hand + Offset(prong * 7f, -70f), colour, 4f)
            } else {
                oval(hand + Offset(0f, -56f), 10f, 14f, colour)
                oval(hand + Offset(-3f, -60f), 3f, 5f, MoPropWhite.copy(alpha = 0.7f))
            }
            grippingMitten(rig, side)
        }
    }

    override fun DrawScope.foreground(t: Float, v: MoVariant) {
        val (top, ledge) = v.of(
            listOf(MoPropWood to MoPropWoodDark, MoPropTeal to MoPropTealDark, MoPropPink to MoPropPinkDark, MoPropBlue to MoPropBlueDark),
            salt = 2,
        )
        val rim = v.of(listOf(MoPropSky, MoPropRed, MoPropGold, MoPropGreen), salt = 3)
        val hop = 2f * max(beats(t, v, left = true), beats(t, v, left = false))
        block(Offset(CENTRE, FEET - 9f), 30f, 22f, MoPropWoodDark)
        chunk(Offset(CENTRE, FEET - 24f), 270f, 16f, top, ledge, corner = 8f)
        val plate = Offset(CENTRE, FEET - 35f - hop)
        if (v.chance(50, salt = 4)) {
            val hem = plate + Offset(38f, 3f)
            shape(MoPropWhite, hem.x - 18f to hem.y, hem.x + 13f to hem.y, hem.x + 13f to hem.y + 19f, hem.x - 12f to hem.y + 16f)
            limb(hem + Offset(5f, 3f), hem + Offset(6f, 15f), MoPropSilver, 2f)
        }
        oval(plate + Offset(0f, 2.5f), 42f, 8f, MoPropGrey)
        oval(plate, 42f, 8f, MoPropWhite)
        oval(plate, 31f, 5.5f, rim)
        oval(plate, 23f, 3.8f, MoPropWhite)
    }

    override val ownsTheFloor get() = true
}

/** He salts his own mitten with a hungry look, is caught at it, and whistles at the sky until it is safe. */
private object PinchOfSalt : MoAct("hungry_pinch_of_salt", MoOccasion.Hungry) {
    private const val PERIOD = 7f
    private const val CAUGHT = 0.42f

    /** -1 when the viewer's left mitten is the one being seasoned. */
    private fun side(v: MoVariant) = if (v.chance(50, salt = 2)) 1f else -1f

    // The whistle lasts until here; the mitten creeps back first and the shaker follows it.
    private fun back(v: MoVariant) = v.between(0.7f, 0.8f, salt = 3)

    private fun mitten(g: Float, v: MoVariant) = 1f - ease((g - CAUGHT) / 0.06f) + ease((g - back(v)) / 0.12f)

    private fun shaker(g: Float, v: MoVariant) = 1f - ease((g - CAUGHT) / 0.06f) + ease((g - back(v) - 0.06f) / 0.12f)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val g = cycle(t, PERIOD)
        val side = side(v)
        val b = MoBodies.of(body)
        val stare = ease((g - CAUGHT + 0.02f) / 0.03f) * (1f - ease((g - 0.5f) / 0.04f))
        val sky = ease((g - 0.5f) / 0.05f) * (1f - ease((g - back(v)) / 0.08f))
        // Work below the mouth so the tipped shaker clears his tongue on either body.
        val pour = shaker(g, v)
        val clearance = if (body == MoBodyKind.Low) 12f else 0f
        val seasoned = mix(MoArm.Down, MoArm(Offset(-42f, b.mouth.y + clearance + 52f - b.shoulderY), bend = 0.3f), mitten(g, v))
        val shaking = mix(MoArm.Down, MoArm(Offset(-53f, b.mouth.y + clearance + 41f + 3f * wave(t, 0.3f) - b.shoulderY), bend = 0.5f), pour)
        val eye = MoEye(
            gaze = mix(mix(Offset(side * 0.3f, 0.95f), Offset.Zero, stare), Offset(-side * 0.6f, -0.8f), sky),
            iris = 0.76f - 0.31f * stare,
            lidDrop = 0.3f * (1f - max(stare, sky)),
        )
        return moIdle(t).copy(
            leftEye = eye,
            rightEye = eye,
            brows = if (stare > 0.3f) MoBrows(MoBrowKind.Raised, lift = 5f * stare) else MoBrows(),
            mouth = when {
                stare > 0.3f -> MoMouth.Flat(9f)
                sky > 0.3f -> MoMouth.Whistle
                else -> MoMouth.TongueOut
            },
            leftArm = if (side < 0f) seasoned else shaking,
            rightArm = if (side > 0f) seasoned else shaking,
            armsInFront = true,
            tilt = 2f * wave(t, 1.6f) * sky,
            blinks = stare < 0.3f,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val g = cycle(t, PERIOD)
        val side = side(v)
        val pepper = v.chance(50, salt = 1)
        val grain = if (pepper) MoPropCharcoal else MoPropWhite
        val tipped = shaker(g, v)
        val hand = rig.hand(-side)
        val target = rig.hand(side)

        val angle = (80f + 5f * wave(t, 0.3f)) * tipped
        withTransform({ rotate(side * angle, hand) }) {
            // Most of it stands clear of his fist, so it still reads as a shaker at the size of a thumbnail.
            chunk(hand + Offset(0f, -24f), 30f, 38f, if (pepper) MoPropSand else MoPropSky, if (pepper) MoPropBrown else MoPropBlue, corner = 10f)
            block(hand + Offset(0f, -48f), 26f, 11f, MoPropGreyDark, corner = 5f)
            for (hole in -1..1) drawCircle(MoPropInk, 1.7f, hand + Offset(hole * 6.5f, -49f))
        }
        grippingMitten(rig, -side)

        // Leaning in, the cap is up and inward of his fist, over the other mitten.
        val pouring = ease((tipped - 0.8f) / 0.2f)
        val tipAngle = angle * PI.toFloat() / 180f
        val cap = hand + Offset(side * 49f * sin(tipAngle), -49f * cos(tipAngle))
        for (index in 0..5) {
            val fall = cycle(t, 0.6f, offset = index / 6f)
            val landing = Offset(target.x + (index - 2.5f) * 4f, target.y - 12f)
            val at = Offset(mix(cap.x, landing.x, fall), mix(cap.y, landing.y, fall * fall))
            drawCircle(grain.copy(alpha = pouring * (1f - fall * fall * fall)), 3f, at)
        }
        // What has landed so far, and the evidence goes down with the mitten.
        val seasoned = ease((mitten(g, v) - 0.5f) / 0.5f)
        for (index in 0..4) {
            drawCircle(grain.copy(alpha = seasoned), 2.4f, target + Offset(-8f + index * 4f, if (index % 2 == 0) -3f else -8f))
        }
    }
}

internal val hungryActs1: List<MoAct> = listOf(
    GrowlEveryoneHeard,
    OwnStove,
    UnderTheCloche,
    TableDrumroll,
    PinchOfSalt,
)
