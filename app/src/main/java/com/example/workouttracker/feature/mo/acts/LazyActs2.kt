package com.example.workouttracker.feature.mo.acts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import com.example.workouttracker.core.ui.theme.MoPropBlue
import com.example.workouttracker.core.ui.theme.MoPropBlueDark
import com.example.workouttracker.core.ui.theme.MoPropCharcoal
import com.example.workouttracker.core.ui.theme.MoPropCream
import com.example.workouttracker.core.ui.theme.MoPropGreen
import com.example.workouttracker.core.ui.theme.MoPropGrey
import com.example.workouttracker.core.ui.theme.MoPropGreyDark
import com.example.workouttracker.core.ui.theme.MoPropInk
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
import com.example.workouttracker.core.ui.theme.MoPropSilver
import com.example.workouttracker.core.ui.theme.MoPropSky
import com.example.workouttracker.core.ui.theme.MoPropSteel
import com.example.workouttracker.core.ui.theme.MoPropTeal
import com.example.workouttracker.core.ui.theme.MoPropTealDark
import com.example.workouttracker.core.ui.theme.MoPropWhite
import com.example.workouttracker.core.ui.theme.MoPropWood
import com.example.workouttracker.core.ui.theme.MoPropWoodDark
import com.example.workouttracker.core.ui.theme.MoPropYellow
import com.example.workouttracker.core.ui.theme.MoPropYellowDark
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
import com.example.workouttracker.feature.mo.floorShadow
import com.example.workouttracker.feature.mo.limb
import com.example.workouttracker.feature.mo.moIdle
import com.example.workouttracker.feature.mo.oval
import com.example.workouttracker.feature.mo.puff
import com.example.workouttracker.feature.mo.pulse
import com.example.workouttracker.feature.mo.quadTo
import com.example.workouttracker.feature.mo.wave
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/*
 * Activity is open and he would rather not: six ways of doing as little as possible. Each loop is
 * offset so that t = 0 is a complete picture and the joke still arrives within the first seconds.
 */

private const val PI_F = PI.toFloat()

private fun lerp(from: Float, to: Float, amount: Float) = from + (to - from) * amount

/** 0 to 1 across [from]..[to] of a phase, eased, and clamped outside it. */
private fun ramp(p: Float, from: Float, to: Float): Float = if (to > from) ease((p - from) / (to - from)) else 0f

/**
 * The kit's body transform, so a prop in the world can stay attached to a point of his body. It must
 * mirror drawPosed: squash about the feet, then tilt about the feet, then the offset.
 */
private class BodyFrame(val tilt: Float, val squash: Float, val offset: Offset) {
    private val angle = tilt * PI_F / 180f
    private val sx = 1f - squash / 2f
    private val sy = 1f + squash

    fun toScreen(point: Offset): Offset {
        val dx = (point.x - CENTRE) * sx
        val dy = (point.y - FEET) * sy
        return Offset(CENTRE + dx * cos(angle) - dy * sin(angle) + offset.x, FEET + dx * sin(angle) + dy * cos(angle) + offset.y)
    }

    fun fromScreen(point: Offset): Offset {
        val x = point.x - offset.x - CENTRE
        val y = point.y - offset.y - FEET
        // A squash never comes near -1 or 2, so neither scale can be zero.
        return Offset(CENTRE + (x * cos(angle) + y * sin(angle)) / sx, FEET + (-x * sin(angle) + y * cos(angle)) / sy)
    }

    /** The arm on [side] whose hand lands on [screen], wherever the body has been moved to. */
    fun armTo(screen: Offset, side: Float, body: MoBodyKind, bend: Float): MoArm {
        val shape = MoBodies.of(body)
        val hand = fromScreen(screen)
        return MoArm(Offset((hand.x - (CENTRE + side * shape.shoulderHalf)) * side, hand.y - shape.shoulderY), bend)
    }
}

/** Activity is overdue: in a hammock with his eyes shut, and one toe push is all the effort it gets. */
private object HammockOneToePush : MoAct("lazy_hammock_one_toe_push", MoOccasion.Lazy, intensity = MoIntensity.Mild) {
    private const val PERIOD = 12f
    private const val SWING_SECONDS = 2.4f
    private const val FULL_SWING = 18f

    // How far outside his own width each post stands, and how high it carries the ropes.
    private const val POST = 32f
    private const val POST_TOP = 236f

    /** How deep he sits in the cloth: enough that it covers his base and his legs only dangle out. */
    private const val SIT = -40f
    private val leftEnd = Offset(-4f, 340f)
    private val rightEnd = Offset(234f, 340f)

    private fun pushSide(v: MoVariant) = if (v.chance(50, salt = 3)) 1f else -1f

    private fun hangs(v: MoVariant) = if (v.chance(50, salt = 4)) 1f else -1f

    // Three swings into the loop, where the hammock has all but stopped: the push is seconds away.
    private fun phase(t: Float) = cycle(t, PERIOD, offset = 0.6f)

    private fun swing(t: Float, v: MoVariant): Float {
        val p = phase(t)
        val amplitude = when {
            p < 0.8f -> 1f - 0.85f * ease(p / 0.8f)
            else -> lerp(0.15f, 1f, ramp(p, 0.9f, 1f))
        }
        // The period holds a whole number of swings, so the sine has no seam. He pushes off away from the foot.
        return pushSide(v) * FULL_SWING * amplitude * sin(p * PERIOD / SWING_SECONDS * 2f * PI_F)
    }

    private fun strain(p: Float) = ramp(p, 0.8f, 0.89f) * (1f - ramp(p, 0.9f, 0.94f))

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val p = phase(t)
        val side = pushSide(v)
        val swing = swing(t, v)
        val strain = strain(p)
        val peek = ramp(p, 0.72f, 0.75f) * (1f - ramp(p, 0.93f, 0.96f))
        val idle = moIdle(t, energy = 0.5f)

        val bliss = MoEye(shape = MoEyeShape.Shut)
        val peeking = if (peek > 0.5f) MoEye(lidDrop = 0.5f - 0.15f * strain, gaze = Offset(side * 0.3f, 0.95f)) else bliss
        val dangling = MoLeg(lift = -30f + 2f * wave(t + 0.4f, SWING_SECONDS))
        // At full strain the toe just reaches the floor, which is all the push the hammock gets.
        val pushing = MoLeg(lift = -30f - 13f * strain, swing = 3f * strain)
        val hangs = hangs(v)
        fun arm(armSide: Float) =
            if (armSide == hangs) MoArm(Offset(14f - armSide * swing * 0.4f, 50f), bend = 0.1f)
            else MoArm(Offset(-40f, 20f), bend = 0.4f)

        return idle.copy(
            leftEye = if (side < 0f) peeking else bliss,
            rightEye = if (side > 0f) peeking else bliss,
            brows = if (peek > 0.5f) MoBrows(MoBrowKind.Raised, lift = if (side < 0f) 4f else 0f, skew = side * 4f) else MoBrows(),
            mouth = if (strain > 0.25f) MoMouth.TongueOut else MoMouth.Smile(width = 14f, depth = 10f),
            leftArm = arm(-1f),
            rightArm = arm(1f),
            armsInFront = true,
            legs = MoLegs(if (side < 0f) pushing else dangling, if (side > 0f) pushing else dangling),
            // The tip trails behind the swing, and the whole of him rocks like the pendulum he is.
            sway = idle.sway - swing * 0.7f,
            tilt = -swing * 0.22f + side * 2f * strain,
            offset = Offset(swing, SIT + 3f * strain),
            blinks = false,
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        // The ropes follow the cloth ends through his transform, which does not depend on the body.
        val pose = pose(t, v, MoBodyKind.Tall)
        val frame = BodyFrame(pose.tilt, pose.squash, pose.offset)
        for (x in listOf(-POST, 2f * CENTRE + POST)) {
            val inward = if (x < CENTRE) 1f else -1f
            // No wider than the post stands from the edge of his view.
            floorShadow(x, 10f, MoPropShadow)
            chunk(Offset(x, (POST_TOP + FEET) / 2f), 16f, FEET - POST_TOP, MoPropWood, MoPropWoodDark, corner = 8f)
            // Two ropes fan from each post to the gathered end of the cloth, which is what says hammock.
            val end = frame.toScreen(if (inward > 0f) leftEnd else rightEnd)
            limb(Offset(x, POST_TOP + 8f), end, MoPropSand, 4f)
            limb(Offset(x, POST_TOP + 25f), end, MoPropSand, 4f)
        }
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        // Cool colours only: a warm cloth would read as more of him rather than as something he lies in.
        val (cloth, ledge) = v.of(
            listOf(MoPropTeal to MoPropTealDark, MoPropBlue to MoPropBlueDark, MoPropPurple to MoPropPurpleDark, MoPropPink to MoPropPinkDark),
            salt = 1,
        )
        for (end in listOf(leftEnd, rightEnd)) {
            val inward = if (end == leftEnd) 1f else -1f
            limb(end, end + Offset(inward * 11f, 11f), cloth, 7f)
            drawCircle(MoPropSand, 4f, end)
        }
        fun sling(drop: Float) = Path().apply {
            val from = leftEnd + Offset(0f, drop)
            val to = rightEnd + Offset(0f, drop)
            moveTo(from.x, from.y)
            quadTo(this, from, Offset(CENTRE, 444f + drop), to)
            quadTo(this, to, Offset(CENTRE, 520f + drop), from)
            close()
        }
        // He sits in the cloth, so it covers his base, but the arm that hangs out lies over its rim:
        // the cloth is cut away where that arm is.
        val hangsRight = hangs(v) > 0f
        val hand = if (hangsRight) rig.rightHand else rig.leftHand
        val shoulder = Offset(CENTRE + hangs(v) * rig.body.shoulderHalf, rig.body.shoulderY)
        val along = hand - shoulder
        val length = along.getDistance()
        val across = if (length > 1f) Offset(-along.y, along.x) / length * 13f else Offset(13f, 0f)
        val arm = Path().apply {
            moveTo(shoulder.x + across.x, shoulder.y + across.y)
            lineTo(hand.x + across.x, hand.y + across.y)
            lineTo(hand.x - across.x, hand.y - across.y)
            lineTo(shoulder.x - across.x, shoulder.y - across.y)
            close()
        }
        val mitten = Path().apply { addOval(Rect(hand, 14f)) }
        clipPath(arm, ClipOp.Difference) {
            clipPath(mitten, ClipOp.Difference) {
                drawPath(sling(7f), ledge)
                drawPath(sling(0f), cloth)
                val stripes = when (v.pick(3, salt = 2)) {
                    0 -> emptyList()
                    1 -> listOf(482f)
                    else -> listOf(468f, 496f)
                }
                for (control in stripes) curve(leftEnd, Offset(CENTRE, control), rightEnd, MoPropCream, 5f)
            }
        }
    }

    override val ownsTheFloor get() = true
}

/** Activity is overdue and the alarm says so: without really waking he pats it quiet and sags again. */
private object SnoozePat : MoAct("lazy_snooze_pat", MoOccasion.Lazy, intensity = MoIntensity.Mild) {
    private const val PERIOD = 9f

    // The clock is drawn this much larger than its own measures, from the floor up.
    private const val SIZE = 1.45f

    /** On the bells, so his mitten rests on the clock rather than beside it. */
    private const val HAND_ABOVE_FLOOR = 95f

    /** He stands over this far from the middle, away from the clock, so the two never overlap. */
    private const val STANDS_OFF = 18f

    private fun side(v: MoVariant) = if (v.chance(50, salt = 2)) 1f else -1f

    private fun clock(v: MoVariant) = Offset(CENTRE + side(v) * 121f, FEET - 30f)

    // The quiet is long, so the loop starts late in it: the first tremble comes after two seconds.
    private fun phase(t: Float) = cycle(t, PERIOD, offset = 0.3f)

    private fun doublePat(v: MoVariant) = v.chance(35, salt = 4)

    /** How the clock is thrown about: a growing tremble, then whole hops that start and end on the floor. */
    private fun motion(t: Float, v: MoVariant): Offset {
        val p = phase(t)
        val hops = 3 + v.pick(3, salt = 3)
        return when {
            p in 0.5f..0.62f -> Offset(wave(t, 0.06f) * 1.5f * ramp(p, 0.5f, 0.56f), 0f)
            p > 0.62f && p < 0.78f -> Offset(wave(t, 0.05f) * 2.5f, -9f * abs(sin((p - 0.62f) / 0.16f * hops * PI_F)))
            else -> Offset.Zero
        }
    }

    private fun bump(p: Float, from: Float, to: Float) = if (p > from && p < to) sin((p - from) / (to - from) * PI_F) else 0f

    /** How far the mitten has squashed the clock down, 0 to 1. */
    private fun pressed(p: Float, v: MoVariant) =
        // A double pat lets go sooner, so the clock springs back under the mitten as it rises again.
        if (doublePat(v)) bump(p, 0.805f, 0.83f) + bump(p, 0.86f, 0.9f) else bump(p, 0.805f, 0.85f)

    private fun handLift(p: Float, v: MoVariant): Float {
        fun fall(from: Float, to: Float) = ((p - from) / (to - from)).coerceIn(0f, 1f).let { it * it }
        val first = 26f * ramp(p, 0.62f, 0.66f) * (1f - fall(0.78f, 0.805f))
        val second = if (doublePat(v)) 14f * ramp(p, 0.82f, 0.845f) * (1f - fall(0.845f, 0.86f)) else 0f
        return first + second
    }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val p = phase(t)
        val s = side(v)
        val idle = moIdle(t, energy = 0.4f)
        val roused = ramp(p, 0.6f, 0.64f) * (1f - ramp(p, 0.8f, 0.85f))
        // He sags after the pat and slowly straightens through the quiet, across the wrap of the loop.
        val sinceThePat = (p - 0.8f).mod(1f)
        val sag = ease(sinceThePat / 0.06f) * (1f - ramp(sinceThePat, 0.06f, 0.48f))
        val squash = idle.squash - 0.03f * sag - 0.02f * pressed(p, v)
        val frame = BodyFrame(tilt = s * 3f, squash = squash, offset = Offset(-s * STANDS_OFF, 0f))

        val lift = handLift(p, v)
        val riding = 1f - (lift / 26f).coerceIn(0f, 1f)
        val top = Offset(clock(v).x, FEET - HAND_ABOVE_FLOOR + 13f * pressed(p, v))
        val hand = top + motion(t, v) * riding + Offset(0f, -lift + 2f * wave(t, 0.4f) * (1f - riding))
        val near = frame.armTo(hand, s, body, bend = 0.3f)

        val cracked = MoEye(lidDrop = 0.65f - 0.3f * roused, gaze = Offset(s * 0.8f * roused, 0.3f + 0.3f * roused))
        val shut = MoEye(shape = MoEyeShape.Shut)
        val yawn = bump(p, 0.9f, 0.99f)
        return idle.copy(
            leftEye = if (s < 0f) cracked else shut,
            rightEye = if (s > 0f) cracked else shut,
            brows = if (roused > 0.3f) MoBrows(MoBrowKind.Flat, lift = if (s < 0f) 3f else 0f, skew = s * 3f) else MoBrows(),
            mouth = if (yawn > 0f) MoMouth.Yawn(open = yawn) else MoMouth.Flat(11f),
            leftArm = if (s < 0f) near else MoArm.Down,
            rightArm = if (s > 0f) near else MoArm.Down,
            tilt = frame.tilt,
            squash = squash,
            offset = frame.offset,
            blinks = false,
        )
    }

    // Behind him, so his mitten and forearm lie over the bells. He stands well clear of it either way.
    override fun DrawScope.world(t: Float, v: MoVariant) {
        val p = phase(t)
        val (colour, ledge) = v.of(
            listOf(MoPropRed to MoPropRedDark, MoPropTeal to MoPropTealDark, MoPropYellow to MoPropYellowDark, MoPropBlue to MoPropBlueDark),
            salt = 1,
        )
        val c = clock(v)
        val ringing = p > 0.62f && p < 0.78f
        val moved = motion(t, v)
        floorShadow(c.x, 30f, MoPropShadow)
        withTransform({
            translate(moved.x, moved.y)
            scale(SIZE, SIZE * (1f - 0.12f * pressed(p, v)), Offset(c.x, FEET))
        }) {
            limb(c + Offset(-12f, 18f), Offset(c.x - 16f, FEET - 2f), MoPropSteel, 6f)
            limb(c + Offset(12f, 18f), Offset(c.x + 16f, FEET - 2f), MoPropSteel, 6f)
            val flip = if (ringing && wave(t, 0.1f) < 0f) -1f else 1f
            val hammer = c + Offset(flip * 6f, -31f)
            limb(c + Offset(0f, -20f), hammer, MoPropCharcoal, 3f)
            drawCircle(MoPropCharcoal, 3.5f, hammer)
            drawCircle(MoPropSteel, 8f, c + Offset(-13f, -22f))
            drawCircle(MoPropSteel, 8f, c + Offset(13f, -22f))
            drawCircle(ledge, 22f, c + Offset(0f, 4f))
            drawCircle(colour, 22f, c)
            drawCircle(MoPropCream, 16f, c)
            for (quarter in 0..3) {
                val angle = quarter * PI_F / 2f
                drawCircle(MoPropInk, 1.8f, c + Offset(cos(angle), sin(angle)) * 12.5f)
            }
            limb(c, c + Offset(6f, -8f), MoPropInk, 3f)
            limb(c, c + Offset(-7f, 0f), MoPropInk, 3f)
        }
        if (ringing) {
            // The noise, as arcs thrown up and outward from the bells. The far arc stays short of x = -45,
            // hop and stroke width included, so nothing of the ringing is cut off by the frame.
            val loud = 0.5f + 0.5f * abs(wave(t, 0.3f))
            val bells = Offset(c.x, FEET - (FEET - c.y + 22f) * SIZE) + motion(t, v)
            for (arc in listOf(-1f, 1f)) for (ring in 0..1) {
                val r = 29f + ring * 9f
                fun at(degrees: Float) = bells + Offset(sin(degrees * PI_F / 180f) * r * arc, -cos(degrees * PI_F / 180f) * r)
                curve(at(16f), at(30f) + Offset(arc * 3f, -2f), at(44f), MoPropSilver.copy(alpha = loud * (1f - ring * 0.3f)), 4.5f)
            }
        }
    }

    override val ownsTheFloor get() = true
}

/** Activity is open: a robot vacuum passes, and lifting one foot and then the other is his whole workout. */
private object RobotVacuumFootLifts : MoAct("lazy_robot_vacuum_foot_lifts", MoOccasion.Lazy, intensity = MoIntensity.Mild) {
    private const val PERIOD = 10f

    // It crosses the floor in front of his feet and no further: the dust it trails has to stay in
    // the frame too, and it fades in and out rather than being cut off at the edge.
    private const val FROM = 20f
    private const val TRAVEL = 190f
    private const val HALF_WIDTH = 42f

    // Where its edge meets his first foot, as a share of the crossing: the variant that bumps turns back here.
    private const val BUMP_AT = 0.12f

    private fun direction(v: MoVariant) = if (v.chance(50, salt = 2)) 1f else -1f

    private fun bumps(v: MoVariant) = v.chance(30, salt = 4)

    // It starts the loop right under his first foot, so the still frame already shows the whole joke.
    private fun crossing(t: Float) = cycle(t, PERIOD, offset = 0.4f)

    private fun vacuumX(t: Float, v: MoVariant): Float {
        val q = crossing(t)
        val plain = FROM + TRAVEL * q
        val along = if (!bumps(v) || q < BUMP_AT || q > BUMP_AT + 0.25f) {
            plain
        } else {
            val touch = FROM + TRAVEL * BUMP_AT
            val back = touch - 14f
            when {
                q < BUMP_AT + 0.03f -> lerp(touch, back, ramp(q, BUMP_AT, BUMP_AT + 0.03f))
                q < BUMP_AT + 0.06f -> back
                // It hurries until it is back on its timetable, so the period stays what it was.
                else -> lerp(back, FROM + TRAVEL * (BUMP_AT + 0.25f), (q - BUMP_AT - 0.06f) / 0.19f)
            }
        }
        return if (direction(v) > 0f) along else 2f * CENTRE - along
    }

    private fun bumped(t: Float, v: MoVariant): Float {
        val q = crossing(t)
        return if (bumps(v)) ramp(q, BUMP_AT, BUMP_AT + 0.015f) * (1f - ramp(q, BUMP_AT + 0.08f, BUMP_AT + 0.12f)) else 0f
    }

    // Up only while it is near that foot: his feet are too close together for both to be well out of its way at once.
    private fun lift(vacuum: Float, foot: Float) = ease((36f - abs(vacuum - foot)) / 20f)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val shape = MoBodies.of(body)
        val x = vacuumX(t, v)
        val left = lift(x, shape.leftFootX)
        val right = lift(x, shape.rightFootX)
        val q = crossing(t)
        // While it turns round out of sight his eyes drift back to the middle and nearly close.
        val watching = ease(minOf(q, 1f - q) / 0.12f)
        val annoyed = bumped(t, v)
        val eye = MoEye(
            lidDrop = 0.78f - 0.28f * watching - 0.2f * annoyed,
            gaze = Offset(((x - CENTRE) / 150f).coerceIn(-1f, 1f) * watching, 0.95f),
        )
        fun balance(lift: Float): MoArm {
            val up = if (body == MoBodyKind.Low) lift else 0f
            return MoArm.Down.copy(reach = MoArm.Down.reach + Offset(16f * up, -31f * up))
        }
        return moIdle(t, energy = 0.5f).copy(
            leftArm = balance(left),
            rightArm = balance(right),
            leftEye = eye,
            rightEye = eye,
            brows = if (annoyed > 0.3f) MoBrows(MoBrowKind.Flat) else MoBrows(),
            mouth = if (maxOf(left, right) > 0.95f) MoMouth.Oh(4f) else MoMouth.Flat(12f),
            // His base hides a foot lifted straight up, so the knee swings well out from under it: what
            // reads at a glance is the foot appearing beside him, not how high it goes.
            legs = MoLegs(MoLeg(lift = 30f * left, swing = 40f * left), MoLeg(lift = 30f * right, swing = 40f * right)),
            // The lean onto the standing foot, and the little rise with it, are what read at a glance.
            tilt = 4f * (left - right),
            offset = Offset(5f * (left - right), -5f * maxOf(left, right)),
        )
    }

    override fun DrawScope.foreground(t: Float, v: MoVariant) {
        val (colour, ledge, lid) = v.of(
            listOf(
                Triple(MoPropWhite, MoPropGreyDark, MoPropGrey),
                Triple(MoPropCharcoal, MoPropInk, MoPropGreyDark),
                Triple(MoPropMint, MoPropTealDark, MoPropWhite),
            ),
            salt = 1,
        )
        val light = v.of(listOf(MoPropGreen, MoPropSky, MoPropOrange), salt = 3)
        val dir = direction(v)
        val x = vacuumX(t, v)
        // It fades in and out at the edges instead of being cut off by the frame.
        val seen = minOf(ease((x - FROM) / 25f), ease((FROM + TRAVEL - x) / 25f))
        fun Color.seen() = copy(alpha = alpha * seen)

        floorShadow(x, HALF_WIDTH + 6f, MoPropShadow.seen())
        // The dust it kicks up, as two puffs falling behind. They are kept apart: overlapping
        // translucent circles darken where they cross, which reads as a stone rather than as dust.
        for (index in 0..1) {
            val behind = Offset(x - dir * (HALF_WIDTH + 8f + index * 12f), FEET - 9f - index * 6f)
            drawCircle(MoPropGrey.copy(alpha = 0.45f - index * 0.18f).seen(), 7f - index * 2f + 1.5f * pulse(t + index * 0.3f, 0.7f), behind)
        }
        chunk(Offset(x, FEET - 15f), 2f * HALF_WIDTH, 26f, colour.seen(), ledge.seen(), corner = 12f, drop = 6f)
        // Seen slightly from above: the lid is a disc, and a bumper runs along the leading edge.
        oval(Offset(x, FEET - 25f), HALF_WIDTH - 8f, 7.5f, lid.seen())
        block(Offset(x + dir * (HALF_WIDTH - 5f), FEET - 14f), 8f, 22f, ledge.seen(), corner = 4f)
        drawCircle(light.seen(), 4f, Offset(x + dir * (HALF_WIDTH - 16f), FEET - 25f))
    }

    // It drives along the whole floor, through where the things he has neglected would lie.
    override val ownsTheFloor get() = true
}

/** Activity is open: the treadmill carries him off while he does nothing, until two grudging steps bring him back. */
private object TreadmillDoesTheWalking : MoAct("lazy_treadmill_does_the_walking", MoOccasion.Lazy, intensity = MoIntensity.Mild) {
    // Far enough apart to read as tread rather than as a barrier across the picture.
    private const val STRIPE_GAP = 32f
    private const val NEAR = 2f
    private const val FAR = -46f
    private const val STEPPING_FROM = 0.76f
    private const val BELT_TOP = FEET - 22f
    private const val BELT_HALF = 146f

    private fun console(v: MoVariant) = if (v.chance(50, salt = 1)) 1f else -1f

    private fun stripeSeconds(v: MoVariant) = if (v.chance(30, salt = 4)) 3.15f else 4.2f

    // Two stripe periods, so belt and Mo come round together; it starts a third of the way down the belt.
    private fun phase(t: Float, v: MoVariant) = cycle(t, stripeSeconds(v) * 2f, offset = 0.3f)

    private fun swinging(stride: Float) = MoArm(MoArm.Down.reach + Offset(0f, -6f * stride), MoArm.Down.bend)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val p = phase(t, v)
        val c = console(v)
        val steps = if (v.chance(40, salt = 3)) 3 else 2
        val idle = moIdle(t, energy = 0.4f)

        val walking = p >= STEPPING_FROM
        val u = ((p - STEPPING_FROM) / (1f - STEPPING_FROM)).coerceIn(0f, 1f)
        val step = floor(u * steps).coerceAtMost(steps - 1f)
        val within = u * steps - step
        val stride = if (walking) sin(within * PI_F) else 0f
        // The foot nearer the console leads, and they take turns.
        val lifted = if (step.toInt() % 2 == 0) c else -c
        val x = if (walking) lerp(FAR, NEAR, (step + ease(within)) / steps) else lerp(NEAR, FAR, p / STEPPING_FROM)

        // He eyes the way back for a moment before he bothers.
        val dread = ramp(p, 0.66f, 0.7f) * (1f - ramp(p, 0.9f, 1f))
        val arrival = ((p + 0.03f).mod(1f) / 0.08f).let { if (it < 1f) sin(it * PI_F) else 0f }
        val eye = MoEye(lidDrop = 0.55f - 0.15f * dread, gaze = Offset(c * 0.85f * dread, 0.2f))
        return idle.copy(
            leftEye = eye,
            rightEye = eye,
            brows = if (dread > 0.4f) MoBrows(MoBrowKind.Flat) else MoBrows(),
            mouth = if (dread > 0.4f) MoMouth.Frown(8f) else MoMouth.Flat(13f),
            leftArm = swinging(if (lifted > 0f) stride else 0f),
            rightArm = swinging(if (lifted < 0f) stride else 0f),
            legs = MoLegs(MoLeg(lift = if (lifted < 0f) 12f * stride else 0f), MoLeg(lift = if (lifted > 0f) 12f * stride else 0f)),
            // He rocks onto the foot that stands.
            tilt = -lifted * 2.5f * stride,
            squash = idle.squash - 0.025f * arrival,
            offset = Offset(c * x, BELT_TOP - FEET + 3f),
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val c = console(v)
        // Tread marks, not hazard tape: bright stripes turn the belt into a barrier across the picture.
        val stripe = v.of(listOf(MoPropSteel, MoPropGrey, MoPropGreyDark), salt = 2)
        val post = CENTRE + c * 136f
        floorShadow(CENTRE, 152f, MoPropShadow)
        block(Offset(post, FEET - 66f), 12f, 108f, MoPropSteel, corner = 5f)
        // The rail he could hold on to, and does not.
        limb(Offset(post, FEET - 106f), Offset(post - c * 52f, FEET - 106f), MoPropCharcoal, 10f)
        // The console stops below his eyes, so it never competes with his face.
        chunk(Offset(post, FEET - 122f), 40f, 24f, MoPropGrey, MoPropGreyDark, corner = 7f)
        drawCircle(MoPropGreen, 3.5f, Offset(post - 9f, FEET - 126f))
        drawCircle(MoPropRed, 3.5f, Offset(post + 9f, FEET - 126f))

        chunk(Offset(CENTRE, FEET - 8f), 2f * BELT_HALF, 24f, MoPropCharcoal, MoPropInk, corner = 11f, drop = 4f)
        // The marks run away from the console at the speed he is carried, and wrap after one gap.
        val slide = -c * STRIPE_GAP * cycle(t, stripeSeconds(v))
        clipRect(left = CENTRE - BELT_HALF + 18f, top = BELT_TOP + 4f, right = CENTRE + BELT_HALF - 18f, bottom = BELT_TOP + 20f) {
            for (index in -1..11) {
                val x = CENTRE - BELT_HALF + index * STRIPE_GAP + slide
                limb(Offset(x, BELT_TOP + 22f), Offset(x + 11f, BELT_TOP + 2f), stripe, 6f)
            }
        }
        // The rollers turn with the belt: one spoke each says so.
        for (end in listOf(-1f, 1f)) {
            val hub = Offset(CENTRE + end * (BELT_HALF - 10f), BELT_TOP + 12f)
            val turn = -c * cycle(t, stripeSeconds(v)) * 2f * PI_F
            drawCircle(MoPropSteel, 8f, hub)
            limb(hub - Offset(cos(turn), sin(turn)) * 5f, hub + Offset(cos(turn), sin(turn)) * 5f, MoPropCharcoal, 3f)
        }
    }

    override val ownsTheFloor get() = true
}

/** Activity is open: the punching bag is a wall to lean on, until it swings away under him and his eyes pop open. */
private object LeaningOnThePunchingBag : MoAct("lazy_leaning_on_the_punching_bag", MoOccasion.Lazy, intensity = MoIntensity.Mild) {
    private const val PERIOD = 8f

    // He stands well clear of the bag, which needs the whole side of the frame to swing in.
    private const val STANDS_ASIDE = 44f

    /** Where his mitten is planted on the bag: low on its flank, within an arm's reach of his shoulder. */
    private const val HAND_Y = 294f

    // The bag hangs from a stand, so nothing has to reach out of the frame to hold it up.
    private const val HOOK_Y = 14f

    private fun side(v: MoVariant) = if (v.chance(50, salt = 3)) 1f else -1f

    private fun maxTilt(v: MoVariant) = v.between(8f, 12f, salt = 4)

    private fun phase(t: Float) = cycle(t, PERIOD, offset = 0.15f)

    /** A slow drift away, a held moment at the far end, a quicker swing back, and a rest upright. */
    private fun lean(p: Float) = ramp(p, 0f, 0.5f) * (1f - ramp(p, 0.62f, 0.86f))

    private fun startle(p: Float) = ramp(p, 0.43f, 0.47f) * (1f - ramp(p, 0.64f, 0.74f))

    /** Where his mitten is planted: it keeps its height on the bag and goes sideways with it. */
    private fun hand(t: Float, v: MoVariant): Offset {
        val p = phase(t)
        val wobble = wave(t, 0.5f) * 1.5f * startle(p)
        // His mitten travels with the bag, which is what tips him: far enough that the drift is plain.
        return Offset(CENTRE + side(v) * (92f + 1.6f * maxTilt(v) * lean(p) + wobble), HAND_Y)
    }

    // Pulled in from the edge: swung out, the bag's lower corner still stops short of x = 275.
    private fun hook(v: MoVariant) = CENTRE + side(v) * 98f

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val p = phase(t)
        val s = side(v)
        val startle = startle(p)
        val idle = moIdle(t, energy = 0.45f)
        val frame = BodyFrame(tilt = s * (1.5f + maxTilt(v) * lean(p)), squash = idle.squash, offset = Offset(-s * STANDS_ASIDE, 0f))
        val leaning = frame.armTo(hand(t, v), s, body, bend = 0.15f)
        // The free hand shoots up rather than out: he already stands aside from the bag, and one more
        // arm's length outward would put the mitten past the edge of the frame.
        val other = MoArm(
            Offset(lerp(MoArm.Hip.reach.x, 24f, startle), lerp(MoArm.Hip.reach.y, -10f + 4f * wave(t, 0.3f), startle)),
            bend = lerp(MoArm.Hip.bend, 0.2f, startle),
        )
        val freeFoot = MoLeg(swing = 12f)
        val planted = MoLeg(lift = 7f * lean(p))
        val eye = MoEye(lidDrop = 0.6f * (1f - startle), iris = 0.76f - 0.26f * startle, gaze = Offset(s * 0.6f * startle, 0.3f * startle))
        return idle.copy(
            leftEye = eye,
            rightEye = eye,
            brows = if (startle > 0.2f) MoBrows(MoBrowKind.Raised, lift = 6f * startle) else MoBrows(),
            mouth = if (startle > 0.4f) MoMouth.Oh(7f) else MoMouth.Smile(width = 11f, depth = 6f),
            leftArm = if (s < 0f) leaning else other,
            rightArm = if (s > 0f) leaning else other,
            legs = MoLegs(if (s > 0f) freeFoot else planted, if (s < 0f) freeFoot else planted),
            tilt = frame.tilt,
            offset = frame.offset,
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val s = side(v)
        val (colour, ledge) = v.of(
            listOf(MoPropRed to MoPropRedDark, MoPropBlue to MoPropBlueDark, MoPropPurple to MoPropPurpleDark),
            salt = 1,
        )
        val mark = if (v.chance(50, salt = 5)) MoPropWhite else MoPropYellow
        val hookX = hook(v)
        val hand = hand(t, v)
        // The bag hangs so that the spot under his mitten, a little to his side of its middle, stays there.
        val swung = atan2(hand.x + s * 12f - hookX, hand.y - HOOK_Y)
        val post = CENTRE + s * 152f
        floorShadow(post - s * 12f, 18f, MoPropShadow)
        block(Offset(post - s * 12f, FEET - 4f), 34f, 8f, MoPropCharcoal, corner = 4f)
        limb(Offset(post, FEET - 6f), Offset(post, HOOK_Y), MoPropGreyDark, 8f)
        limb(Offset(post, HOOK_Y), Offset(hookX - s * 8f, HOOK_Y), MoPropGreyDark, 8f)
        withTransform({ rotate(-swung * 180f / PI_F, Offset(hookX, HOOK_Y)) }) {
            limb(Offset(hookX, HOOK_Y), Offset(hookX, 176f), MoPropSteel, 5f)
            chunk(Offset(hookX, 250f), 58f, 160f, colour, ledge, corner = 26f, drop = 6f)
            // The collar the straps gather into, which is what makes it a bag rather than a column.
            chunk(Offset(hookX, 182f), 42f, 16f, MoPropCharcoal, MoPropInk, corner = 6f, drop = 4f)
            if (v.chance(50, salt = 2)) block(Offset(hookX, 220f), 58f, 14f, mark, corner = 2f) else drawCircle(mark, 16f, Offset(hookX, 224f))
        }
    }

    override val ownsTheFloor get() = true
}

/** Activity is open: he starts a big stretch with a full yawn, stalls halfway, and gives up lower than he began. */
private object TheStretchThatGivesUp : MoAct("lazy_the_stretch_that_gives_up", MoOccasion.Lazy, intensity = MoIntensity.Mild) {
    private const val RISE_AT = 0.85f
    private const val STALL_AT = 2.55f
    private const val FLOP_AT = 3.65f
    private const val DOWN_AT = 4.2f

    private fun period(v: MoVariant) = 5f + v.between(2f, 4f, salt = 4)

    // The loop opens at the top of the yawn, which says what this is at once.
    private fun seconds(t: Float, v: MoVariant) = cycle(t, period(v), offset = STALL_AT / period(v)) * period(v)

    private fun up(at: Float): Float = when {
        at < RISE_AT -> 0f
        at < STALL_AT -> ease((at - RISE_AT) / (STALL_AT - RISE_AT))
        at < FLOP_AT -> 1f
        at < DOWN_AT -> 1f - ((at - FLOP_AT) / (DOWN_AT - FLOP_AT)).let { it * it }
        else -> 0f
    }

    /** How flattened he is by giving up: all the way as he lands, gone again before the next try. */
    private fun flopped(at: Float, period: Float): Float = when {
        at < FLOP_AT -> 0f
        at < DOWN_AT -> ((at - FLOP_AT) / (DOWN_AT - FLOP_AT)).let { it * it }
        else -> 1f - ramp(at, DOWN_AT + 0.4f, period - 0.3f)
    }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val at = seconds(t, v)
        val up = up(at)
        val flopped = flopped(at, period(v))
        val reach = v.between(0.65f, 0.95f, salt = 2)
        val higher = if (v.chance(50, salt = 1)) 1f else -1f
        val trembling = ramp(at, STALL_AT - 0.2f, STALL_AT + 0.1f) * (1f - ramp(at, FLOP_AT - 0.1f, FLOP_AT))
        val idle = moIdle(t, energy = 0.5f)

        // A stretch throws the arms out as well as up, so even a half-hearted one clears his silhouette.
        // As far out as the mitten can go before the tremble and the lean carry it out of the frame.
        val stretched = Offset(48f, -76f)
        fun arm(side: Float): MoArm {
            // Never past the full stretch: beyond it the elbow would bow out of the frame.
            val raised = ((reach + if (side == higher) 0.15f else -0.1f) * up).coerceAtMost(1f)
            return MoArm(
                Offset(
                    lerp(MoArm.Down.reach.x, stretched.x, raised) + wave(t + side * 0.02f, 0.09f) * 2.5f * trembling,
                    lerp(MoArm.Down.reach.y, stretched.y, raised) + 3f * flopped,
                ),
                bend = lerp(MoArm.Down.bend, 0.45f, raised),
            )
        }
        // The lids sink as the yawn takes over, so the eyes close instead of switching off.
        val eye = if (up > 0.5f) MoEye(shape = MoEyeShape.Shut) else MoEye(lidDrop = lerp(0.45f + 0.27f * flopped, 0.92f, up / 0.5f))
        return idle.copy(
            leftEye = eye,
            rightEye = eye,
            brows = if (up > 0.3f) MoBrows(MoBrowKind.Raised, lift = 4f * up) else MoBrows(),
            mouth = when {
                up > 0.15f -> MoMouth.Yawn(open = up)
                // The yawn hands straight over to the little open mouth of giving up: a flat mouth
                // in between would blink on for a frame.
                at >= FLOP_AT && at < DOWN_AT + 0.6f -> MoMouth.Oh(5f)
                else -> MoMouth.Flat(12f)
            },
            leftArm = arm(-1f),
            rightArm = arm(1f),
            sway = idle.sway + 9f * flopped,
            flare = 6f * up - 4f * flopped,
            squash = idle.squash + 0.07f * reach * up - 0.05f * flopped,
            tilt = higher * 2f * up,
            offset = Offset(0f, -3f * up),
            shake = wave(t, 0.07f) * 0.8f * trembling,
            blinks = up < 0.05f,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        // The breath he lets go as he gives up, leaving from beside his mouth and clear of his eyes.
        val drift = ((seconds(t, v) - DOWN_AT + 0.15f) / 1.7f)
        if (drift <= 0f || drift >= 1f) return
        val toward = if (v.chance(50, salt = 3)) 1f else -1f
        // It leaves level with his mouth and past his cheek, so it never sits on his face.
        val at = rig.mouth + Offset(toward * (58f + 56f * drift), 10f - 18f * drift)
        puff(at, 13f + 11f * drift, MoPropWhite.copy(alpha = 0.85f * (1f - drift) * ease(drift / 0.1f)))
    }
}

internal val lazyActs2: List<MoAct> = listOf(
    HammockOneToePush,
    SnoozePat,
    RobotVacuumFootLifts,
    TreadmillDoesTheWalking,
    LeaningOnThePunchingBag,
    TheStretchThatGivesUp,
)
