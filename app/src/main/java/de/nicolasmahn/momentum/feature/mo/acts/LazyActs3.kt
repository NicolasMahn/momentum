package de.nicolasmahn.momentum.feature.mo.acts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.util.lerp
import de.nicolasmahn.momentum.core.ui.theme.MoPropBlue
import de.nicolasmahn.momentum.core.ui.theme.MoPropBlueDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropBrown
import de.nicolasmahn.momentum.core.ui.theme.MoPropCharcoal
import de.nicolasmahn.momentum.core.ui.theme.MoPropGreen
import de.nicolasmahn.momentum.core.ui.theme.MoPropGreenDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropGrey
import de.nicolasmahn.momentum.core.ui.theme.MoPropGreyDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropInk
import de.nicolasmahn.momentum.core.ui.theme.MoPropMoon
import de.nicolasmahn.momentum.core.ui.theme.MoPropOrange
import de.nicolasmahn.momentum.core.ui.theme.MoPropPink
import de.nicolasmahn.momentum.core.ui.theme.MoPropPinkDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropPurple
import de.nicolasmahn.momentum.core.ui.theme.MoPropPurpleDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropRed
import de.nicolasmahn.momentum.core.ui.theme.MoPropRedDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropSand
import de.nicolasmahn.momentum.core.ui.theme.MoPropShadow
import de.nicolasmahn.momentum.core.ui.theme.MoPropSky
import de.nicolasmahn.momentum.core.ui.theme.MoPropSteel
import de.nicolasmahn.momentum.core.ui.theme.MoPropTeal
import de.nicolasmahn.momentum.core.ui.theme.MoPropTealDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropWhite
import de.nicolasmahn.momentum.core.ui.theme.MoPropYellow
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
import de.nicolasmahn.momentum.feature.mo.chunk
import de.nicolasmahn.momentum.feature.mo.curve
import de.nicolasmahn.momentum.feature.mo.cycle
import de.nicolasmahn.momentum.feature.mo.ease
import de.nicolasmahn.momentum.feature.mo.floorShadow
import de.nicolasmahn.momentum.feature.mo.moIdle
import de.nicolasmahn.momentum.feature.mo.oval
import de.nicolasmahn.momentum.feature.mo.polyline
import de.nicolasmahn.momentum.feature.mo.pulse
import de.nicolasmahn.momentum.feature.mo.shape
import de.nicolasmahn.momentum.feature.mo.twitch
import de.nicolasmahn.momentum.feature.mo.wave
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * Lazy, third helping: reasons not to move that come from outside him, and one stretch that turns
 * into a nap.
 */

private fun side(right: Boolean) = if (right) 1f else -1f

/** An arm on its way from one pose to another. */
private fun between(from: MoArm, to: MoArm, by: Float) = MoArm(lerp(from.reach, to.reach, by), lerp(from.bend, to.bend, by))

/** 0 up to [start], eased to 1 by [end]; the building block of every timeline below. */
private fun ramp(x: Float, start: Float, end: Float): Float = if (end <= start) 0f else ease((x - start) / (end - start))

/** A cat sleeps against his feet, so he cannot possibly go anywhere: he shrugs at us and looks down again. */
private object WarmSpot : MoAct("lazy_warm_spot", MoOccasion.Lazy, intensity = MoIntensity.Mild) {
    private val coats = listOf(
        MoPropGrey to MoPropGreyDark,
        MoPropGreyDark to MoPropCharcoal,
        MoPropMoon to MoPropGrey,
        MoPropSand to MoPropBrown,
    )
    private val shrugArm = MoArm(Offset(34f, -12f), bend = 0.75f)

    private fun head(v: MoVariant) = side(v.chance(50, salt = 2))

    /** 0 while he looks down at the cat, 1 while he shrugs at us. The loop starts on the shrug. */
    private fun shrug(t: Float, v: MoVariant): Float {
        val p = cycle(t, if (v.chance(50, salt = 4)) 6f else 8f, offset = 0.5f)
        return ramp(p, 0.4f, 0.5f) - ramp(p, 0.75f, 0.85f)
    }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val up = shrug(t, v)
        val arm = between(MoArm.Down, shrugArm, up)
        return moIdle(t, energy = 0.5f).copy(
            mouth = MoMouth.Smile(width = lerp(13f, 10f, up), depth = lerp(9f, 4f, up)),
            brows = if (up > 0.3f) MoBrows(MoBrowKind.Worried, lift = 3f * up) else MoBrows(),
            leftArm = arm,
            rightArm = arm,
            offset = Offset(0f, -2f * up),
        ).eyes(MoEye(lidDrop = 0.3f * (1f - up), gaze = lerp(Offset(head(v) * 0.25f, 1f), Offset(0f, -0.2f), up)))
    }

    override fun DrawScope.foreground(t: Float, v: MoVariant) {
        val h = head(v)
        val (coat, dark) = v.of(coats, salt = 1)
        val flicksTail = v.chance(50, salt = 3)
        val flick = twitch(t, 4.5f, beat = 0.4f)
        val middle = CENTRE - h * 8f
        val breath = 21f + 1.5f * pulse(t, 3.2f)
        floorShadow(middle, 70f, MoPropShadow)

        // The breath lifts his back and leaves his belly on the floor.
        oval(Offset(middle, FEET - breath + 2f), 60f, breath, dark)
        oval(Offset(middle, FEET - 1f - breath), 60f, breath, coat)

        val at = Offset(CENTRE + h * 40f, FEET - 21f)
        for (ear in listOf(-1f, 1f)) {
            val near = ear == h
            val lift = if (near && !flicksTail) 4f * flick else 0f
            val x = at.x + ear * 11f
            shape(if (near) coat else dark, x - 7f to at.y - 11f, x + ear * 2f to at.y - 27f - lift, x + 7f to at.y - 11f)
        }
        drawCircle(dark, 19f, at + Offset(0f, 3f))
        drawCircle(coat, 19f, at)
        for (eye in listOf(-1f, 1f)) {
            val from = at + Offset(eye * 8.5f - 5f, 0f)
            curve(from, from + Offset(5f, 6f), from + Offset(10f, 0f), MoPropInk, 3f)
        }

        // Across the front of the coat and up to the chin. Along its bottom edge the tail would sit
        // on the ledge, in the ledge's own colour, and the cat would read as a blunt loaf.
        val tip = Offset(CENTRE + h * 20f, FEET - 16f - if (flicksTail) 9f * flick else 0f)
        curve(Offset(middle - h * 50f, FEET - 30f), Offset(middle - h * 22f, FEET - 4f), tip, dark, 11f)
    }
}

/** The far end of his yoga mat keeps curling up and rolling back to him; one lazy push of a foot sends it flat again. */
private object YogaMatRollsBack : MoAct("lazy_yoga_mat_rolls_back", MoOccasion.Lazy, intensity = MoIntensity.Mild) {
    private const val CURL = 3.4f
    private const val BUMP = 0.8f
    private const val PUSH = 1.1f
    private const val MAT_TOP = FEET - 12f

    private val mats = listOf(
        MoPropPurple to MoPropPurpleDark,
        MoPropTeal to MoPropTealDark,
        MoPropPink to MoPropPinkDark,
        MoPropGreen to MoPropGreenDark,
    )

    private fun free(v: MoVariant) = side(v.chance(50, salt = 2))

    private fun rest(v: MoVariant) = v.between(1f, 2.2f, salt = 3)

    /** Seconds into the loop: rest, curl, bump, push. It starts halfway through the curl. */
    private fun clock(t: Float, v: MoVariant): Float {
        val total = rest(v) + CURL + BUMP + PUSH
        return cycle(t, total, offset = (rest(v) + CURL / 2f) / total) * total
    }

    /** How far the roll has come, from the free end (0) to his foot (1). */
    private fun rolled(at: Float, v: MoVariant): Float {
        val pushed = rest(v) + CURL + BUMP
        return ramp(at, rest(v), rest(v) + CURL) - ramp(at, pushed + 0.2f, pushed + 1.1f)
    }

    private fun beat(at: Float, from: Float, length: Float): Float =
        if (at > from && at < from + length) sin((at - from) / length * PI.toFloat()) else 0f

    private fun standsAt(v: MoVariant) = -free(v) * 26f

    private fun rollX(r: Float, v: MoVariant): Float {
        val m = free(v)
        val foot = (if (m > 0f) MoBodies.Tall.rightFootX else MoBodies.Tall.leftFootX) + standsAt(v)
        return lerp(CENTRE + m * 148f, foot + m * 34f, r)
    }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val m = free(v)
        val at = clock(t, v)
        val r = rolled(at, v)
        val arrived = rest(v) + CURL
        val bump = beat(at, arrived - 0.1f, 0.6f)
        val kick = beat(at, arrived + BUMP, 0.6f)
        val pushing = MoLeg(lift = 4f * kick, swing = 16f * kick)
        val idle = moIdle(t, energy = 0.45f)
        return idle.copy(
            brows = if (kick > 0f) MoBrows(MoBrowKind.Flat) else MoBrows(),
            mouth = if (bump > 0.3f) MoMouth.Oh(7f) else MoMouth.Flat(12f),
            legs = if (m > 0f) MoLegs(right = pushing) else MoLegs(left = pushing),
            tilt = -m * 5f * bump,
            squash = idle.squash - 0.025f * bump,
            offset = Offset(standsAt(v), MAT_TOP - FEET + 1f),
        ).eyes(MoEye(lidDrop = 0.55f - 0.25f * bump, iris = 0.76f - 0.16f * bump, gaze = Offset(m * (0.9f - 0.5f * r), 0.9f)))
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val m = free(v)
        val (mat, dark) = v.of(mats, salt = 1)
        val fixed = CENTRE - m * 140f
        val roll = rollX(rolled(clock(t, v), v), v)
        floorShadow(CENTRE + m * 5f, 150f, MoPropShadow)
        chunk(Offset((fixed + roll) / 2f, MAT_TOP + 8f), width = (roll - fixed) * m, height = 15f, colour = mat, ledge = dark, corner = 5f, drop = 5f)
    }

    override fun DrawScope.foreground(t: Float, v: MoVariant) {
        val m = free(v)
        val (mat, dark) = v.of(mats, salt = 1)
        val r = rolled(clock(t, v), v)
        val x = rollX(r, v)
        val radius = 8f + 17f * r
        val centre = Offset(x, MAT_TOP + 4f - radius)
        drawCircle(dark, radius, centre + Offset(0f, 3f))
        drawCircle(mat, radius, centre)
        // The spiral turns with the distance covered, which is what makes it roll rather than slide.
        val turned = (x - (CENTRE + m * 148f)) / 13f
        polyline(dark, 3.5f, (0..24).map { step ->
            val along = step / 24f
            val angle = turned + m * along * 4f * PI.toFloat()
            centre + Offset(cos(angle), sin(angle)) * (radius * (0.82f - 0.7f * along))
        })
    }

    override val ownsTheFloor get() = true
}

/** A side stretch he falls asleep in, tipping further and further until he jolts awake and tries the other side. */
private object SideBendNap : MoAct("lazy_side_bend_nap", MoOccasion.Lazy) {
    private const val HALF = 5f
    private const val JOLT = 0.82f
    private fun bend(u: Float): Float = when {
        // He only leans a third of the way on purpose; the rest is him falling asleep into it.
        u < 0.3f -> 0.35f * ease(u / 0.3f)
        // Asleep he barely moves at first and then goes faster, as anything that topples does.
        u < JOLT -> ((u - 0.3f) / (JOLT - 0.3f)).let { 0.35f + 0.65f * it * it }
        else -> 1f - ramp(u, JOLT, JOLT + 0.05f)
    }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        // Both sides are the same five seconds; the side flips while he stands straight.
        val turn = cycle(t, HALF * 2f, offset = 0.15f) * 2f
        val u = turn.mod(1f)
        val first = side(v.chance(50, salt = 1))
        val d = if (turn < 1f) first else -first
        val b = bend(u)
        // His eyes shut a moment after the lean is done, and stay shut until the jolt.
        val asleep = u > 0.34f && u < JOLT
        val startled = u >= JOLT && u < JOLT + 0.08f
        val shaking = u >= JOLT + 0.05f && u < JOLT + 0.13f
        val hop = if (v.chance(40, salt = 3)) sin(((u - JOLT) / 0.1f).coerceIn(0f, 1f) * PI.toFloat()) else 0f

        // The arm is all the way over by the time the deliberate part of the lean is done.
        val reach = (b / 0.35f).coerceAtMost(1f)
        val stretching = between(MoArm.Down, MoArm.Up.copy(bend = 0.45f), reach)
        // Both arms hang while he stands straight, so nothing jumps when the side flips.
        val onHip = between(MoArm.Down, MoArm.Hip, reach)
        val idle = moIdle(t, energy = 0.7f)
        return idle.copy(
            brows = if (startled) MoBrows(MoBrowKind.Raised, lift = 6f) else MoBrows(),
            mouth = when {
                startled -> MoMouth.Oh(9f)
                asleep -> MoMouth.Oh(size = 7f + 2f * pulse(t, 2.6f))
                shaking -> MoMouth.Wavy
                else -> MoMouth.Smile(width = 12f, depth = 8f)
            },
            leftArm = if (d > 0f) stretching else onHip,
            rightArm = if (d > 0f) onHip else stretching,
            legs = MoLegs(MoLeg(lift = 5f * hop), MoLeg(lift = 5f * hop)),
            sway = idle.sway - d * 4f * b,
            // Past about 18 degrees the far side of his flame leaves the visible box and is clipped.
            tilt = d * v.between(15f, 18f, salt = 2) * b,
            squash = idle.squash - 0.02f * b,
            offset = Offset(0f, -12f * b - 10f * hop),
            shake = if (shaking) wave(t, 0.07f) * 1.6f else 0f,
            blinks = !asleep,
        ).eyes(
            when {
                startled -> MoEye(iris = 0.5f, scale = 1.05f)
                asleep -> MoEye(shape = MoEyeShape.Shut)
                else -> MoEye(lidDrop = 0.2f + 1.2f * b)
            },
        )
    }
}

/** A balloon tied to his kettlebell does the lifting; he points at it, sleepily satisfied that this counts. */
private object BalloonSpotter : MoAct("lazy_balloon_spotter", MoOccasion.Lazy, intensity = MoIntensity.Mild) {
    private val balloons = listOf(MoPropPink, MoPropTeal, MoPropSky, MoPropYellow)
    private val bells = listOf(
        Triple(MoPropGreyDark, MoPropCharcoal, MoPropGrey),
        Triple(MoPropRed, MoPropRedDark, MoPropOrange),
        Triple(MoPropBlue, MoPropBlueDark, MoPropSky),
    )
    private val resting = MoArm(Offset(14f, 30f), bend = 0.15f)
    // Short of the handle, which comes up to meet it on every lift.
    private val pointing = MoArm(Offset(24f, 2f), bend = 0.15f)

    private fun bellSide(v: MoVariant) = side(v.chance(50, salt = 2))

    /** The one driver of the scene, already halfway up when the loop starts. */
    private fun bob(t: Float) = pulse(t + 0.9f, 3.6f)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val s = bellSide(v)
        val bob = bob(t)
        val point = between(resting, pointing, ease(bob))
        return moIdle(t, energy = 0.5f).copy(
            brows = if (bob > 0.6f) MoBrows(MoBrowKind.Raised, lift = 3f * bob) else MoBrows(),
            mouth = MoMouth.Smile(width = 12f, depth = 8f),
            leftArm = if (s < 0f) point else MoArm.Hip,
            rightArm = if (s < 0f) MoArm.Hip else point,
            offset = Offset(-s * 22f, 1.5f * bob),
        ).eyes(MoEye(lidDrop = 0.5f, gaze = Offset(s * 0.8f, 0.55f - 0.5f * bob)))
    }

    private fun DrawScope.balloon(at: Offset, size: Float, colour: Color, handle: Offset, slack: Float, s: Float) {
        val knot = at + Offset(0f, 34f * size + 3f)
        val middle = (handle + knot) / 2f
        curve(handle, middle + Offset(s * 14f * slack, 0f), knot, MoPropWhite, 2.5f)
        shape(colour, knot.x to knot.y - 4f, knot.x - 4f to knot.y + 3f, knot.x + 4f to knot.y + 3f)
        oval(at, 27f * size, 34f * size, colour)
        oval(at + Offset(-11f * size, -15f * size), 6f * size, 9f * size, MoPropWhite.copy(alpha = 0.8f))
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val s = bellSide(v)
        val (bell, dark, mark) = v.of(bells, salt = 4)
        val main = v.pick(balloons.size, salt = 1)
        val lift = ease(bob(t))
        val bellAt = Offset(CENTRE + s * 128f, FEET - 27f - 14f * lift)
        val handle = bellAt + Offset(0f, -38f)
        floorShadow(bellAt.x, 30f - 10f * lift, MoPropShadow)

        if (v.chance(30, salt = 3)) {
            val late = bob(t - 0.4f)
            // Up and toward him, beside the narrow top of his flame, so the pair reads as a bunch instead of one hiding the other.
            val at = Offset(bellAt.x - s * 20f + 2f * wave(t, 4.3f), 140f - 14f * late)
            balloon(at, 0.72f, balloons[(main + 1) % balloons.size], handle + Offset(0f, -4f), 1f - late, s)
        }
        val at = Offset(bellAt.x - s * 2f + 3f * wave(t, 5.1f), 192f - 14f * bob(t))
        balloon(at, 1f, balloons[main], handle + Offset(0f, -4f), 1f - bob(t), s)

        rotate(-s * 4f * lift, bellAt) {
            // A round loop, which is what tells a kettlebell from a padlock at thumbnail size.
            val loop = (0..10).map { step ->
                val angle = PI.toFloat() * (1f - step / 10f)
                handle + Offset(16f * cos(angle), 10f - 12f * sin(angle))
            }
            polyline(MoPropSteel, 8f, listOf(handle + Offset(-14f, 22f)) + loop + listOf(handle + Offset(14f, 22f)))
            drawCircle(dark, 24f, bellAt + Offset(0f, 3f))
            drawCircle(bell, 24f, bellAt)
            drawCircle(mark, 8f, bellAt)
        }
    }

    override val ownsTheFloor get() = true
}

internal val lazyActs3: List<MoAct> = listOf(
    WarmSpot,
    YogaMatRollsBack,
    SideBendNap,
    BalloonSpotter,
)
