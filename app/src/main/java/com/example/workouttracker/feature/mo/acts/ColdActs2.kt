package com.example.workouttracker.feature.mo.acts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import com.example.workouttracker.core.ui.theme.MoPropBrown
import com.example.workouttracker.core.ui.theme.MoPropGreen
import com.example.workouttracker.core.ui.theme.MoPropGrey
import com.example.workouttracker.core.ui.theme.MoPropGreyDark
import com.example.workouttracker.core.ui.theme.MoPropOrange
import com.example.workouttracker.core.ui.theme.MoPropPink
import com.example.workouttracker.core.ui.theme.MoPropPurple
import com.example.workouttracker.core.ui.theme.MoPropRed
import com.example.workouttracker.core.ui.theme.MoPropSand
import com.example.workouttracker.core.ui.theme.MoPropShadow
import com.example.workouttracker.core.ui.theme.MoPropTeal
import com.example.workouttracker.core.ui.theme.MoPropTealDark
import com.example.workouttracker.core.ui.theme.MoPropWhite
import com.example.workouttracker.core.ui.theme.MoPropWoodDark
import com.example.workouttracker.core.ui.theme.MoPropYellow
import com.example.workouttracker.core.ui.theme.MoPropYellowDark
import com.example.workouttracker.domain.mo.MoIntensity
import com.example.workouttracker.domain.mo.MoOccasion
import com.example.workouttracker.feature.mo.CENTRE
import com.example.workouttracker.feature.mo.MoArm
import com.example.workouttracker.feature.mo.MoBodyKind
import com.example.workouttracker.feature.mo.MoBrowKind
import com.example.workouttracker.feature.mo.MoBrows
import com.example.workouttracker.feature.mo.MoEye
import com.example.workouttracker.feature.mo.MoMouth
import com.example.workouttracker.feature.mo.MoPose
import com.example.workouttracker.feature.mo.MoRig
import com.example.workouttracker.feature.mo.block
import com.example.workouttracker.feature.mo.chunk
import com.example.workouttracker.feature.mo.curve
import com.example.workouttracker.feature.mo.cycle
import com.example.workouttracker.feature.mo.ease
import com.example.workouttracker.feature.mo.floorShadow
import com.example.workouttracker.feature.mo.moIdle
import com.example.workouttracker.feature.mo.oval
import com.example.workouttracker.feature.mo.pulse
import com.example.workouttracker.feature.mo.shape
import com.example.workouttracker.feature.mo.smoke
import com.example.workouttracker.feature.mo.twitch
import com.example.workouttracker.feature.mo.wave
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * Cold, part two. He is the Droplet with nothing but his core to hold, and the world around him is
 * no warmer than he is: a flower that has wilted with him, a wind that blows right through.
 */

/** A potted flower beside him has drooped the way he has, and now and then lifts its head to the core in his belly as if it were the sun; he watches it rise. */
private object ColdFlowerTakesHimForTheSun : MoAct("cold_flower_takes_him_for_the_sun", MoOccasion.Cold, setOf(MoBodyKind.Droplet), MoIntensity.Mild) {
    private const val LIFT_PERIOD = 6.4f

    private fun side(v: MoVariant) = if (v.chance(50, salt = 2)) 1f else -1f

    // Half a period in, so the flower is up and turned to him at t = 0.
    private fun lift(t: Float) = ease(pulse(t + LIFT_PERIOD / 2f, LIFT_PERIOD))

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val side = side(v)
        val lift = lift(t)
        val gaze = mix(Offset(0f, -0.7f), Offset(side * 0.9f, 0.5f), ease((lift - 0.3f) / 0.4f))
        // Once the flower is well up, the mitten on its side slides aside to show it the core.
        val nearArm = mix(coldHug, MoArm(Offset(-54f, 40f), bend = -0.55f), ease((lift - 0.5f) / 0.5f))
        // The near eye dries while the flower is up: its one tear wells up as the sag begins and has
        // faded out by the time the flower rises again, so it never appears or vanishes mid-fall.
        val sag = cycle(t, LIFT_PERIOD, offset = -0.25f)
        val nearEye = MoEye(gaze = gaze, lid = -0.3f, tear = (sag * 2f).takeIf { sag < 0.5f })
        val farEye = MoEye(gaze = gaze, lid = -0.3f, tear = cycle(t, 3.4f))
        return moIdle(t, energy = 0f).copy(
            leftEye = if (side < 0f) nearEye else farEye,
            rightEye = if (side < 0f) farEye else nearEye,
            brows = MoBrows(MoBrowKind.Worried, lift = 5f * lift),
            mouth = MoMouth.Frown(width = mix(11f, 7f, lift)),
            leftArm = if (side < 0f) nearArm else coldHug,
            rightArm = if (side < 0f) coldHug else nearArm,
            armsInFront = true,
            tilt = side * 2f * lift,
            shake = wave(t, 0.11f) * 1.6f * (1f - 0.5f * lift),
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val side = side(v)
        val lift = lift(t)
        // Far enough out that the head, lifted toward him, stays clear of his flank.
        val px = CENTRE + side * 135f
        val petal = v.of(listOf(MoPropPink, MoPropPurple, MoPropRed, MoPropWhite), salt = 1)
        val (pot, potLedge) = if (v.chance(50, salt = 5)) MoPropOrange to MoPropWoodDark else MoPropTeal to MoPropTealDark

        floorShadow(px, 24f, MoPropShadow)
        chunk(Offset(px, 407f), 40f, 30f, pot, potLedge, corner = 8f)
        block(Offset(px, 391f), 48f, 10f, pot, corner = 5f)
        // The petals it has already lost lie between the pot and his foot.
        for (index in 0 until 1 + v.pick(2, salt = 4)) {
            val fallen = Offset(px - side * (55f + index * 18f), 422f)
            rotate(side * (15f - index * 32f), fallen) {
                oval(fallen, 7f, 3.5f, petal.copy(alpha = 0.64f))
            }
        }

        // Drooped, the stem arches up and hooks down beside the pot; lifted, it straightens and leans to him.
        val root = Offset(px, 388f)
        val control = mix(Offset(px + side * 8f, 306f), Offset(px + side * 2f, 334f), lift)
        val head = mix(Offset(px - side * 24f, 372f), Offset(px - side * 20f, 312f), lift)
        curve(root, control, head, MoPropGreen, 6f)
        for (index in 0 until 1 + v.pick(2, salt = 3)) {
            val at = along(root, control, head, 0.28f + index * 0.24f)
            oval(at + Offset(side * 8f * (if (index == 0) 1f else -1f), 0f), 10f, 4.5f, MoPropGreen)
        }
        // The head turns as it rises, so its face comes round to the core.
        for (index in 0..4) {
            val angle = lift * 0.6f + index * 2f * PI.toFloat() / 5f
            oval(head + Offset(cos(angle), sin(angle)) * 12f, 8f, 6f, petal)
        }
        drawCircle(MoPropYellow, 8f, head + Offset(-side * 3f * lift, 2f * lift))
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        smoke(rig.tip + Offset(34f, 8f), cycle(t, 3.8f))
    }

    override val ownsTheFloor get() = true
}

/** A gust crosses the picture with dry leaves in it; he leans into it with his mittens clamped on the core, then checks the core and looks back up. */
private object ColdWindAndLeaves : MoAct("cold_wind_and_leaves", MoOccasion.Cold, setOf(MoBodyKind.Droplet)) {
    private const val PERIOD = 5.6f
    private const val GUST = 2.4f
    private const val CHECK = 0.8f

    // Seconds into the beat at which the wind has died down enough (gust under 0.2) for tears; two
    // of them fit in the lull before it is back.
    private const val CRY_START = 2.2f
    private const val TEAR = 1.8f

    // The gust peaks at t = 0, so the first frame is the one that says "wind".
    private const val START = 1.2f / PERIOD

    private fun dir(v: MoVariant) = if (v.chance(50, salt = 1)) 1f else -1f

    /** Seconds into the gust's beat; past [GUST] it is the lull. */
    private fun beat(t: Float) = cycle(t, PERIOD, offset = START) * PERIOD

    private fun gust(t: Float) = ease(twitch(t, PERIOD, beat = GUST, offset = START))

    /** The glance down at the core once the wind has dropped. */
    private fun checking(t: Float): Float {
        val since = beat(t) - GUST
        return if (since in 0f..CHECK) ease(sin(since / CHECK * PI.toFloat())) else 0f
    }

    /**
     * The wind takes his tears with it; they only come in the lull. A tear wells up [delay] seconds
     * into it, and only [count] whole tears run, so none is cut off mid-fall when the gust returns.
     */
    private fun lullTear(t: Float, delay: Float, count: Int): Float? {
        val since = (beat(t) - CRY_START).mod(PERIOD) - delay
        return if (since >= 0f && since < TEAR * count) (since / TEAR).mod(1f) else null
    }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val dir = dir(v)
        val gust = gust(t)
        val push = gust * v.between(0.8f, 1.2f, salt = 4)
        val check = checking(t)
        val viewer = Offset(0f, -0.7f)
        val gaze = viewer + (Offset(-dir * 0.8f, 0f) - viewer) * gust + (Offset(0f, 0.9f) - viewer) * check
        val lidDrop = 0.6f * gust + 0.35f * check
        val arm = mix(coldHug, MoArm(Offset(-68f, 35f), bend = -0.7f), gust)
        return moIdle(t, energy = 0f).copy(
            leftEye = MoEye(gaze = gaze, lidDrop = lidDrop, tear = lullTear(t, delay = 0f, count = 2)),
            rightEye = MoEye(gaze = gaze, lidDrop = lidDrop, tear = lullTear(t, delay = TEAR / 2f, count = 1)),
            brows = MoBrows(MoBrowKind.Worried, lift = -3f * gust),
            // His mouth tightens in the wind rather than switching shape, so nothing pops.
            mouth = MoMouth.Frown(width = mix(11f, 7f, gust)),
            leftArm = arm,
            rightArm = arm,
            armsInFront = true,
            sway = dir * 26f * push + wave(t, 0.23f) * 4f * gust,
            tilt = -dir * 4f * push,
            offset = Offset(dir * 3f * gust, 0f),
            squash = -0.03f * gust,
            shake = wave(t, 0.11f) * (1.6f + 1.2f * gust),
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val dir = dir(v)
        val gust = gust(t)
        val base = rig.tip + Offset(34f + dir * 14f * gust, 8f)
        // The kit's wisp only rises, so it is turned at its base to stream downwind.
        rotate(degrees = dir * 55f * gust, pivot = base) { smoke(base, cycle(t, 3.8f)) }
        for (offset in listOf(0f, 0.5f)) {
            val r = cycle(t, 0.8f, offset)
            oval(rig.tip + Offset(dir * (12f + 60f * r), 4f + 10f * r), 2.6f, 2f, MoPropGreyDark.copy(alpha = (1f - r) * gust))
        }
    }

    override fun DrawScope.foreground(t: Float, v: MoVariant, picture: Rect) {
        val gust = gust(t)
        if (gust <= 0f) return
        val dir = dir(v)
        val w = beat(t) / GUST
        fun across(progress: Float, margin: Float) = mix(picture.left + margin, picture.right - margin, if (dir > 0f) progress else 1f - progress)

        // None of the streaks runs at eye height.
        for ((index, y) in listOf(215f, 360f, 398f).withIndex()) {
            val p = lagged(w, index * 0.15f)
            val x = across(p, 36f)
            curve(Offset(x - 36f, y), Offset(x, y + 7f), Offset(x + 36f, y), MoPropGrey.copy(alpha = 0.8f * gust * ease(p * 6f)), 5f)
        }

        val colours = listOf(MoPropOrange, MoPropYellowDark, MoPropBrown, MoPropSand)
        val first = v.pick(colours.size, salt = 3)
        val bases = listOf(240f, 385f, 404f)
        for (index in 0 until 2 + v.pick(2, salt = 2)) {
            val p = lagged(w, index * 0.2f)
            val at = Offset(across(p, 12f), bases[index] + sin(w * 7f + index) * 14f)
            val colour = colours[(first + index) % colours.size]
            leaf(at, angle = dir * (w * 9f + index * 2f), colour.copy(alpha = gust * ease(p * 6f)))
        }
    }

    /** Progress of something that sets off [lag] into the gust and still arrives with it. */
    private fun lagged(w: Float, lag: Float) = ((w - lag) / (1f - lag)).coerceIn(0f, 1f)
}

/** A dry leaf: a diamond turned by [angle], long enough to read as a leaf at thumbnail size. */
private fun DrawScope.leaf(at: Offset, angle: Float, colour: Color) {
    val c = cos(angle)
    val s = sin(angle)
    fun corner(x: Float, y: Float) = (at.x + x * c - y * s) to (at.y + x * s + y * c)
    shape(colour, corner(9f, 0f), corner(0f, 4.5f), corner(-9f, 0f), corner(0f, -4.5f))
}

/** A point along a quadratic curve, for what sits on a stem. */
private fun along(from: Offset, control: Offset, to: Offset, s: Float): Offset {
    val u = 1f - s
    return from * (u * u) + control * (2f * u * s) + to * (s * s)
}

private fun mix(a: Float, b: Float, k: Float): Float = a + (b - a) * k

private fun mix(a: Offset, b: Offset, k: Float): Offset = a + (b - a) * k

private fun mix(a: MoArm, b: MoArm, k: Float): MoArm = MoArm(mix(a.reach, b.reach, k), mix(a.bend, b.bend, k))

internal val coldActs2: List<MoAct> = listOf(
    ColdFlowerTakesHimForTheSun,
    ColdWindAndLeaves,
)
