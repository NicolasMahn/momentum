package de.nicolasmahn.momentum.feature.mo.acts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import de.nicolasmahn.momentum.core.ui.theme.MoPropBlue
import de.nicolasmahn.momentum.core.ui.theme.MoPropBrown
import de.nicolasmahn.momentum.core.ui.theme.MoPropCream
import de.nicolasmahn.momentum.core.ui.theme.MoPropGrass
import de.nicolasmahn.momentum.core.ui.theme.MoPropGreen
import de.nicolasmahn.momentum.core.ui.theme.MoPropGreenDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropGrey
import de.nicolasmahn.momentum.core.ui.theme.MoPropGreyDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropInk
import de.nicolasmahn.momentum.core.ui.theme.MoPropLeaf
import de.nicolasmahn.momentum.core.ui.theme.MoPropMint
import de.nicolasmahn.momentum.core.ui.theme.MoPropPink
import de.nicolasmahn.momentum.core.ui.theme.MoPropPurple
import de.nicolasmahn.momentum.core.ui.theme.MoPropRed
import de.nicolasmahn.momentum.core.ui.theme.MoPropRedDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropSand
import de.nicolasmahn.momentum.core.ui.theme.MoPropShadow
import de.nicolasmahn.momentum.core.ui.theme.MoPropSkin
import de.nicolasmahn.momentum.core.ui.theme.MoPropSky
import de.nicolasmahn.momentum.core.ui.theme.MoPropWhite
import de.nicolasmahn.momentum.core.ui.theme.MoPropWood
import de.nicolasmahn.momentum.core.ui.theme.MoPropWoodDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropYellow
import de.nicolasmahn.momentum.domain.mo.MoOccasion
import de.nicolasmahn.momentum.feature.mo.CENTRE
import de.nicolasmahn.momentum.feature.mo.FEET
import de.nicolasmahn.momentum.feature.mo.MoBodyKind
import de.nicolasmahn.momentum.feature.mo.MoPose
import de.nicolasmahn.momentum.feature.mo.curve
import de.nicolasmahn.momentum.feature.mo.cycle
import de.nicolasmahn.momentum.feature.mo.ease
import de.nicolasmahn.momentum.feature.mo.floorShadow
import de.nicolasmahn.momentum.feature.mo.limb
import de.nicolasmahn.momentum.feature.mo.oval
import de.nicolasmahn.momentum.feature.mo.polyline
import de.nicolasmahn.momentum.feature.mo.shape
import de.nicolasmahn.momentum.feature.mo.twitch
import de.nicolasmahn.momentum.feature.mo.wave
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * Out, first part: the Husk does nothing, so every one of these is about something small that still
 * finds him worth being near. Nothing here burns and nothing lights up more than a glint; the spark
 * in his hollow is his. His shell spans x 35..198, his limp arms hang at x 44 and 190 down to y 400,
 * his feet sit at x 66..106 and 126..166, his closed eyes at y 312..324 and his hollow at x 98..134,
 * y 352..400, all of which the props keep clear of.
 */

/** How long one pulse of his spark takes: what breathes beside him breathes with it. */
private const val SPARK_SECONDS = 3.1f

/** A mouse has curled up against his base, because he is still the warmest thing around, and breathes with his spark. */
private object OutMouseAsleep : MoAct("out_mouse_asleep_against_him", MoOccasion.Out, setOf(MoBodyKind.Husk)) {
    // Wood stands in for brown: a mouse-sized patch of MoPropBrown all but vanishes on the dark ground.
    private val furs = listOf(MoPropGrey, MoPropSand, MoPropWood)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose = MoPose()

    override fun DrawScope.foreground(t: Float, v: MoVariant) {
        val fur = v.of(furs, salt = 1)
        // +1 sleeps on his left with its head towards him, -1 on his right.
        val toward = if (v.chance(50, salt = 2)) 1f else -1f
        val curl = if (v.chance(50, salt = 3)) -1f else 1f
        val innerEar = v.of(listOf(MoPropPink, MoPropCream), salt = 4)
        val breath = 0.5f + 0.5f * wave(t, SPARK_SECONDS)
        val ear = twitch(t, 6.3f, beat = 0.3f, offset = 0.55f)

        val cx = if (toward > 0f) 12f else 220f
        floorShadow(cx + toward * 4f, 30f, MoPropShadow)
        // The tail goes on first, so the body hides its root and only the curl shows.
        val hub = Offset(cx - toward * 30f, FEET - 11f)
        val start = atan2(5f, toward * 9f)
        polyline(MoPropSkin, 3.5f, (0..16).map { step ->
            val f = step / 16f
            val angle = start + curl * f * 1.7f * PI.toFloat()
            hub + Offset(cos(angle), sin(angle)) * (9.5f - 6f * f)
        })
        val ry = 12f + 1.8f * breath
        oval(Offset(cx, FEET - ry), 20f, ry, fur)
        // The head is tucked low at the front and barely rides the breath, so the flank does the breathing.
        val head = Offset(cx + toward * 17f, FEET - 11f - 0.4f * breath)
        drawCircle(fur, 9.5f, head)
        val earAt = head + Offset(-toward * 3f, -8f - 1.5f * ear)
        drawCircle(fur, 5.5f, earAt)
        drawCircle(innerEar, 3.3f, earAt)
        curve(head + Offset(toward * 1f, -2f), head + Offset(toward * 4f, 1f), head + Offset(toward * 7f, -2f), MoPropInk, 2f)
        drawCircle(MoPropPink, 1.8f, head + Offset(toward * 9f, 1.5f))
    }

    override val ownsTheFloor get() = true
}

/** A seedling has come up through the ash beside him, and leans toward his spark every time it brightens. */
private object OutSproutLeaning : MoAct("out_sprout_leaning_toward_the_spark", MoOccasion.Out, setOf(MoBodyKind.Husk)) {
    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose = MoPose()

    override fun DrawScope.foreground(t: Float, v: MoVariant) {
        val green = v.of(listOf(MoPropLeaf, MoPropGrass, MoPropMint), salt = 1)
        val toward = if (v.chance(50, salt = 2)) 1f else -1f
        val third = v.chance(40, salt = 3)
        val height = v.between(30f, 46f, salt = 4)
        // The same wave as his spark: brightest spark, deepest lean, and half way there at t = 0.
        val lean = 0.5f + 0.5f * wave(t, SPARK_SECONDS)

        val base = Offset(if (toward > 0f) 24f else 208f, FEET - 2f)
        floorShadow(base.x, 12f, MoPropShadow)
        // The split seed case it came out of, lying where it fell.
        oval(base + Offset(-6f, 0f), 4f, 2.4f, MoPropSand)
        oval(base + Offset(6f, 0f), 4f, 2.4f, MoPropSand)

        val top = base + Offset(toward * (3f + 14f * lean), -height + 4f * lean)
        curve(base, base + Offset(toward * 1f, -height * 0.62f), top, MoPropGreenDark, 4f)
        // Leaves turn toward the light the way the stem does, a little further than it.
        val turn = 22f * lean
        leaf(top, facing(toward, -145f + turn), 11f, 5f, green)
        leaf(top, facing(toward, -35f + turn), 11f, 5f, green)
        if (third) {
            val lower = base + Offset(toward * (2f + 7f * lean), -height * 0.55f + 2f * lean)
            leaf(lower, facing(toward, -150f + turn * 0.6f), 7.5f, 3.5f, green)
        }
    }

    /** An angle in degrees for a sprout with him on its right, mirrored when he is on its left. */
    private fun facing(toward: Float, degrees: Float): Float = if (toward > 0f) degrees else 180f - degrees

    /** One leaf growing out of [root] in the direction [degrees], rounded at both ends. */
    private fun DrawScope.leaf(root: Offset, degrees: Float, length: Float, half: Float, colour: Color) {
        withTransform({ rotate(degrees, root) }) {
            oval(root + Offset(length, 0f), length, half, colour)
        }
    }

    override val ownsTheFloor get() = true
}

/** A cat has curled up asleep against his side, where the spark still gives off a little warmth. */
private object OutCatWarmSpot : MoAct("out_cat_who_found_the_warm_spot", MoOccasion.Out, setOf(MoBodyKind.Husk)) {
    private class Coat(val fur: Color, val shade: Color)

    // Never orange, so nothing about it is mistaken for a piece of him.
    private val coats = listOf(
        Coat(MoPropCream, MoPropSand),
        Coat(MoPropGrey, MoPropGreyDark),
        Coat(MoPropSand, MoPropWood),
        Coat(MoPropBrown, MoPropWoodDark),
    )

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose = MoPose()

    override fun DrawScope.foreground(t: Float, v: MoVariant) {
        val coat = v.of(coats, salt = 1)
        // +1 lies on his left with its head towards him, -1 on his right.
        val toward = if (v.chance(50, salt = 2)) 1f else -1f
        val whiteTip = v.chance(50, salt = 3)
        val twitchingEar = if (v.chance(50, salt = 4)) 1f else -1f
        val earPeriod = v.of(listOf(4.7f, 8.3f), salt = 5)
        val breath = 0.5f + 0.5f * wave(t, SPARK_SECONDS)
        // The tail and the ear keep their own time, so the two never move together.
        val flick = twitch(t, 6.7f, beat = 0.5f, offset = 0.3f)
        val ear = twitch(t, earPeriod, beat = 0.25f, offset = 0.6f)

        val cx = if (toward > 0f) 28f else 204f
        floorShadow(cx, 50f, MoPropShadow)
        val ry = 24f + 2f * breath
        oval(Offset(cx, FEET - ry), 40f, ry, coat.fur)
        // The tail wraps round the front of it, and only its tip ever moves.
        val tip = Offset(cx + toward * 30f, FEET - 9f - 7f * flick)
        curve(Offset(cx - toward * 36f, FEET - 20f), Offset(cx - toward * 6f, FEET + 4f), tip, coat.shade, 10f)
        if (whiteTip) drawCircle(MoPropCream, 5f, tip)

        val head = Offset(cx + toward * 18f, FEET - 41f - 0.8f * breath)
        val earLift = { side: Float -> if (side == twitchingEar) 4f * ear else 0f }
        for (side in listOf(-1f, 1f)) {
            shape(
                coat.shade,
                head.x + side * 3f to head.y - 13f,
                head.x + side * 11f to head.y - 22f - earLift(side),
                head.x + side * 13f to head.y - 8f,
            )
        }
        drawCircle(coat.fur, 16f, head)
        for (eye in listOf(-7f, 7f)) {
            curve(head + Offset(eye - 4.5f, -1f), head + Offset(eye, 4f), head + Offset(eye + 4.5f, -1f), MoPropInk, 2.8f)
        }
        drawCircle(MoPropPink, 2.6f, head + Offset(0f, 6.5f))
    }

    override val ownsTheFloor get() = true
}

/** A line of ants carries an unlit match past his feet, stops in front of his hollow to look up at the spark, and carries on. */
private object OutAntsMatchstick : MoAct("out_ants_and_the_matchstick", MoOccasion.Out, setOf(MoBodyKind.Husk)) {
    private const val PERIOD = 16f
    private const val REST_FROM = 0.40f
    private const val REST_TO = 0.55f
    /** Where the group is at [u] of the round when it walks left to right. */
    private fun along(u: Float, picture: Rect): Float = when {
        u < REST_FROM -> mix(picture.left - 50f, CENTRE, glide(u / REST_FROM))
        u < REST_TO -> CENTRE
        else -> mix(CENTRE, picture.right + 90f, glide((u - REST_TO) / (1f - REST_TO)))
    }

    // Puts them in front of his near foot with the match at t = 0.
    private const val OFFSET = 0.336f

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose = MoPose()

    override fun DrawScope.foreground(t: Float, v: MoVariant, picture: Rect) {
        val count = 2 + v.pick(3, salt = 1)
        val d = if (v.chance(50, salt = 2)) 1f else -1f
        val matchHead = v.of(listOf(MoPropRed, MoPropBlue, MoPropGreen), salt = 3)
        val youngStraggler = v.chance(50, salt = 4)

        val u = cycle(t, PERIOD, offset = OFFSET)
        val x = CENTRE + d * (along(u, picture) - CENTRE)
        val resting = ramp(u, 0.385f, 0.415f) * (1f - ramp(u, 0.535f, 0.565f))
        val moving = 1f - resting
        val look = ramp(u, 0.43f, 0.46f) * (1f - ramp(u, 0.50f, 0.53f))
        clipRect(picture.left, picture.top, picture.right, picture.bottom) {
            val step = 0.42f
            val matchY = 410f + 1.3f * wave(t, step) * moving
            limb(Offset(x - d * 40f, matchY), Offset(x + d * 40f, matchY), MoPropWood, 6f)
            drawCircle(matchHead, 7f, Offset(x + d * 40f, matchY))

            val carriers = max(count - 1, 1)
            for (index in 0 until carriers) {
                // Spread out to the ends of the match: an ant is 26 long, and closer they merge into one red line.
                val place = if (carriers == 1) 0f else -34f + 68f * index / (carriers - 1)
                val backY = matchY + 6f
                // Only the lead ant looks up at the spark.
                val ax = x + d * place
                ant(Offset(ax, backY), d, wave(t + index * 0.17f, step) * moving, if (index == carriers - 1) look else 0f, 1f, MoPropRedDark)
            }
            // The last one is behind the match and hurrying, with quicker steps and always a little further back.
            val lag = 26f + 8f * (0.5f + 0.5f * wave(t, 3.7f))
            val hurry = 0.28f
            val sx = x - d * (40f + lag)
            ant(Offset(sx, 416f + 1.6f * wave(t, hurry) * moving), d, wave(t, hurry) * moving, 0f, if (youngStraggler) 0.8f else 1f, MoPropRedDark)
        }
    }

    /** One ant at [at] walking in direction [d]: three beads, two feelers and legs that scissor with [stride]; [look] lifts its head. */
    private fun DrawScope.ant(at: Offset, d: Float, stride: Float, look: Float, size: Float, colour: Color) {
        val head = at + Offset(d * 8f * size, -4.5f * look)
        for (side in listOf(-1f, 1f)) {
            val swing = d * 3f * stride * side
            limb(at, at + Offset(swing - d * 2f * size, 6f * size), colour, 1.5f)
            limb(at, at + Offset(-swing + d * 4f * size, 6f * size), colour, 1.5f)
        }
        drawCircle(colour, 5f * size, at - Offset(d * 9f * size, 0f))
        drawCircle(colour, 4f * size, at)
        drawCircle(colour, 4.5f * size, head)
        // Looking up tilts the feelers up with the head.
        val lift = 5f * look
        limb(head, head + Offset(d * (6f - lift * 0.6f) * size, (-6f - lift) * size), colour, 1.5f)
        limb(head, head + Offset(d * (7f - lift * 0.4f) * size, (-2f - lift) * size), colour, 1.5f)
    }

    override val ownsTheFloor get() = true
}

/** A lost balloon hangs beside him, swings in on a draught, bumps his shoulder, and drifts back out, as if trying to wake him. */
private object OutLostBalloon : MoAct("out_lost_balloon", MoOccasion.Out, setOf(MoBodyKind.Husk)) {
    private const val SWING_SECONDS = 5.5f
    private const val REACH = 34f

    /** How far in of the swing it is at [t], from 0 far out to 1 pressing his shoulder. */
    private fun inward(t: Float): Float = 0.5f + 0.5f * sin(t / SWING_SECONDS * 2f * PI.toFloat())

    /** The last stretch of the swing is spent squashing against him rather than moving further in. */
    private const val TOUCH_FROM = 0.86f

    /**
     * The balloon's centre where its flank meets his slope for a hang height [cy]: both slopes are
     * near 45 degrees, so a lower balloon sits further out. The right slope is the collapsed tip's
     * and sits further in than a mirror of the left would, so each side has its own fit.
     */
    private fun contactX(cy: Float, outward: Float): Float =
        if (outward < 0f) 59f - (cy - 240f) * 0.89f else 175.5f + (cy - 248f) * 0.85f

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose = MoPose()

    override fun DrawScope.foreground(t: Float, v: MoVariant) {
        val colour = v.of(listOf(MoPropRed, MoPropPink, MoPropSky, MoPropYellow, MoPropPurple), salt = 1)
        val outward = if (v.chance(50, salt = 2)) -1f else 1f
        // A tired balloon hangs lower; on his right it hangs lower still, below the fold of his tip.
        val cy = v.between(238f, 252f, salt = 3) + if (outward > 0f) 10f else 0f
        val stringLength = v.between(150f, 185f, salt = 4)

        val now = inward(t)
        val touch = ((now - TOUCH_FROM) / (1f - TOUCH_FROM)).coerceIn(0f, 1f)
        val reach = min(now, TOUCH_FROM) / TOUCH_FROM
        val rx = 17f * (1f - 0.26f * touch)
        val ry = 20f * (1f + 0.16f * touch)
        val contact = contactX(cy, outward)
        // The face towards him stays where it landed while the rest of it squashes outward.
        val bx = contact + outward * REACH * (1f - reach) - outward * (17f - rx)
        val by = cy + 3f * wave(t, 3.7f)
        val balloon = Offset(bx, by)

        // The string hangs from the knot and its loose end lags behind the balloon.
        val knot = balloon + Offset(0f, ry + 4f)
        val lateReach = min(inward(t - 0.7f), TOUCH_FROM) / TOUCH_FROM
        val endX = contact + outward * REACH * (1f - lateReach)
        val endY = min(FEET, knot.y + stringLength)
        val end = Offset(endX, endY)
        val string = MoPropGrey.copy(alpha = 0.8f)
        curve(knot, Offset((knot.x * 0.65f + end.x * 0.35f), (knot.y + end.y) / 2f), end, string, 2f)
        val trailing = knot.y + stringLength - FEET
        if (trailing > 0f) curve(end, end + Offset(outward * trailing * 0.5f, 0f), end + Offset(outward * trailing * 0.9f, -1.5f), string, 2f)

        val knotShape = Path().apply {
            moveTo(balloon.x, balloon.y + ry - 2f)
            lineTo(balloon.x - 4.5f, knot.y + 2f)
            lineTo(balloon.x + 4.5f, knot.y + 2f)
            close()
        }
        drawPath(knotShape, colour)
        oval(balloon, rx, ry, colour)
        curve(
            balloon + Offset(-rx * 0.55f, -ry * 0.1f),
            balloon + Offset(-rx * 0.55f, -ry * 0.62f),
            balloon + Offset(-rx * 0.12f, -ry * 0.76f),
            MoPropWhite.copy(alpha = 0.8f),
            3.5f,
        )
    }

    override val ownsTheFloor get() = true
}

/** An inchworm climbs him like a hill, rears up beside his collapsed tip to look about, finds nothing, and humps down the other side. */
private object OutInchwormExpedition : MoAct("out_inchworm_expedition", MoOccasion.Out, setOf(MoBodyKind.Husk)) {
    /** The worm's path, 5 outside his silhouette: floor, hanging arm, slope, tip, slope, arm, floor. */
    private val forward = listOf(
        Offset(-90f, 420f), Offset(14f, 420f), Offset(27f, 407f),
        Offset(26f, 392f), Offset(27f, 372f), Offset(30f, 352f), Offset(30f, 335f),
        Offset(33f, 318f), Offset(38f, 296f), Offset(46f, 278f), Offset(53f, 267f), Offset(60f, 257f),
        Offset(68f, 248f), Offset(76f, 239f), Offset(86f, 229f), Offset(97f, 220f), Offset(108f, 211f),
        Offset(121f, 205f), Offset(133f, 205f), Offset(146f, 210f), Offset(156f, 222f), Offset(158f, 234f),
        Offset(155f, 246f), Offset(167f, 257f), Offset(172f, 263f), Offset(185f, 280f), Offset(196f, 301f),
        Offset(201f, 318f), Offset(203f, 335f), Offset(204f, 352f), Offset(207f, 380f), Offset(203f, 404f),
        Offset(210f, 418f), Offset(224f, 420f), Offset(320f, 420f),
    )
    private val trails = listOf(OutTrail(forward, outward = 1f), OutTrail(forward.reversed(), outward = -1f))

    /** Where it stops to look about: the flat of the top, beside the collapsed tip. */
    private val summit = Offset(121f, 205f)

    /** Where the first frame finds it, mid-arch on his shoulder. */
    private val shoulders = listOf(Offset(60f, 257f), Offset(185f, 280f))

    private const val THIN = 18f
    private const val LIFT_SECONDS = 0.6f
    private const val SWAY_SECONDS = 1.3f

    private class Gait(val stride: Float, val seconds: Float)

    private val gaits = listOf(Gait(16f, 1.5f), Gait(20f, 1.25f), Gait(26f, 1f))

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose = MoPose()

    override fun DrawScope.foreground(t: Float, v: MoVariant, picture: Rect) {
        val colour = v.of(listOf(MoPropLeaf, MoPropMint, MoPropYellow), salt = 1)
        val way = v.pick(2, salt = 2)
        val sways = 1 + v.pick(3, salt = 3)
        val gait = v.of(gaits, salt = 4)
        val trail = trails[way]

        val stride = gait.stride
        val extraFloor = forward.first().x - picture.left + THIN + stride
        val strides = floor((trail.total + 2f * extraFloor) / stride) + 1f
        val topStride = floor((extraFloor + trail.along(summit)) / stride)
        val swayTime = 2f * LIFT_SECONDS + sways * SWAY_SECONDS
        val loop = strides * gait.seconds + swayTime
        val start = (floor((extraFloor + trail.along(shoulders[way])) / stride) + 0.85f) * gait.seconds
        val time = cycle(t, loop, offset = start / loop) * loop

        // The stride clock stops while it looks about, then picks up where it left off.
        val atTop = ((time - topStride * gait.seconds) / swayTime).coerceIn(0f, 1f) * swayTime
        val striding = (time - atTop) / gait.seconds
        val step = floor(striding)
        val phase = striding - step
        // The front reaches out first, then the rear is drawn up after it into an arch.
        val rear = trail.at(step * stride - extraFloor + if (phase < 0.5f) 0f else stride * ease(phase * 2f - 1f))
        val frontS = step * stride - extraFloor + THIN + if (phase < 0.5f) stride * ease(phase * 2f) else stride
        val front = trail.at(frontS)
        val span = (front - rear).getDistance()
        val normal = trail.normal(frontS - THIN / 2f)
        val arch = max(THIN + stride - span, 0f) * 0.75f
        val crawlControl = (rear + front) / 2f + normal * (2f * arch)

        // Rearing up: the head lifts off him and sways like a periscope, then settles back down.
        val lift = ramp(atTop, 0f, LIFT_SECONDS) * (1f - ramp(atTop, swayTime - LIFT_SECONDS, swayTime))
        val sway = if (atTop > LIFT_SECONDS && atTop < swayTime - LIFT_SECONDS) 0.55f * sin((atTop - LIFT_SECONDS) / SWAY_SECONDS * 2f * PI.toFloat()) else 0f
        val direction = trail.direction(frontS - THIN / 2f)
        val up = trail.normal(trail.along(summit))
        val raisedHead = rear + (up * cos(sway) + direction * sin(sway)) * (THIN + stride) * 0.8f
        val raisedControl = rear + up * (THIN + stride) * 0.55f
        val head = mix(front, raisedHead, lift)
        val control = mix(crawlControl, raisedControl, lift)
        val eyeSide = mix(normal, direction, lift)

        clipRect(picture.left, picture.top, picture.right, picture.bottom) {
            polyline(colour, 9f, (0..14).map { i -> quad(rear, control, head, i / 14f) })
            drawCircle(MoPropInk, 1.7f, head + eyeSide * 2.2f + direction * 1.5f)
        }
    }

    private fun quad(a: Offset, c: Offset, b: Offset, s: Float): Offset {
        val r = 1f - s
        return a * (r * r) + c * (2f * r * s) + b * (s * s)
    }
}

/** A path along his silhouette by arc length, with the normal that points away from him. */
private class OutTrail(private val points: List<Offset>, private val outward: Float) {
    private val marks: List<Float> = points.zipWithNext { a, b -> (b - a).getDistance() }.runningFold(0f) { sum, d -> sum + d }
    val total: Float = marks.last()

    /** The arc length of the point on the path nearest to [near]. */
    fun along(near: Offset): Float = points.indices.minByOrNull { (points[it] - near).getDistance() }?.let { marks[it] } ?: 0f

    private fun segment(s: Float): Int {
        val clamped = s.coerceIn(0f, total)
        var index = 0
        while (index < marks.size - 2 && marks[index + 1] < clamped) index++
        return index
    }

    fun at(s: Float): Offset {
        if (s < 0f) return points.first() + Offset(outward * s, 0f)
        if (s > total) return points.last() + Offset(outward * (s - total), 0f)
        val i = segment(s)
        val length = marks[i + 1] - marks[i]
        val share = if (length > 0f) ((s.coerceIn(0f, total) - marks[i]) / length).coerceIn(0f, 1f) else 0f
        return points[i] + (points[i + 1] - points[i]) * share
    }

    fun direction(s: Float): Offset {
        val i = segment(s)
        val d = points[i + 1] - points[i]
        val length = d.getDistance()
        return if (length > 0f) d / length else Offset(1f, 0f)
    }

    fun normal(s: Float): Offset {
        val d = direction(s)
        return Offset(d.y, -d.x) * outward
    }
}

private fun mix(from: Float, to: Float, amount: Float): Float = from + (to - from) * amount

private fun mix(from: Offset, to: Offset, amount: Float): Offset = from + (to - from) * amount

private fun ramp(x: Float, from: Float, to: Float): Float = if (to <= from) 0f else ease((x - from) / (to - from))

/** 0 to 1 at a steady pace with a soft start and stop, for anything that walks somewhere and halts. */
private fun glide(s: Float): Float {
    val soft = 0.15f
    val c = s.coerceIn(0f, 1f)
    val distance = when {
        c < soft -> c * c / (2f * soft)
        c > 1f - soft -> (1f - soft) - (1f - c) * (1f - c) / (2f * soft)
        else -> c - soft / 2f
    }
    return distance / (1f - soft)
}

internal val outActs1: List<MoAct> = listOf(
    OutMouseAsleep,
    OutSproutLeaning,
    OutCatWarmSpot,
    OutAntsMatchstick,
    OutLostBalloon,
    OutInchwormExpedition,
)
