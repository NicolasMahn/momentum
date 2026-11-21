package com.example.workouttracker.feature.mo.acts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import com.example.workouttracker.core.ui.theme.MoBubble
import com.example.workouttracker.core.ui.theme.MoPropBlue
import com.example.workouttracker.core.ui.theme.MoPropBlueDark
import com.example.workouttracker.core.ui.theme.MoPropBrown
import com.example.workouttracker.core.ui.theme.MoPropCharcoal
import com.example.workouttracker.core.ui.theme.MoPropCream
import com.example.workouttracker.core.ui.theme.MoPropGold
import com.example.workouttracker.core.ui.theme.MoPropGreen
import com.example.workouttracker.core.ui.theme.MoPropGreenDark
import com.example.workouttracker.core.ui.theme.MoPropGreyDark
import com.example.workouttracker.core.ui.theme.MoPropMint
import com.example.workouttracker.core.ui.theme.MoPropPink
import com.example.workouttracker.core.ui.theme.MoPropPinkDark
import com.example.workouttracker.core.ui.theme.MoPropPurple
import com.example.workouttracker.core.ui.theme.MoPropRed
import com.example.workouttracker.core.ui.theme.MoPropRedDark
import com.example.workouttracker.core.ui.theme.MoPropShadow
import com.example.workouttracker.core.ui.theme.MoPropSky
import com.example.workouttracker.core.ui.theme.MoPropTeal
import com.example.workouttracker.core.ui.theme.MoPropTealDark
import com.example.workouttracker.core.ui.theme.MoPropWater
import com.example.workouttracker.core.ui.theme.MoPropWhite
import com.example.workouttracker.core.ui.theme.MoPropWood
import com.example.workouttracker.core.ui.theme.MoPropYellow
import com.example.workouttracker.core.ui.theme.MoPropYellowDark
import com.example.workouttracker.core.ui.theme.MoSpark
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
import com.example.workouttracker.feature.mo.cycle
import com.example.workouttracker.feature.mo.drop
import com.example.workouttracker.feature.mo.ease
import com.example.workouttracker.feature.mo.floorShadow
import com.example.workouttracker.feature.mo.grippingMitten
import com.example.workouttracker.feature.mo.limb
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
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Happy acts, part four: things a pleased flame does with a toy or a fine day. A train set, a
 * cartwheel that never quite happens, lanterns, soap bubbles, a beach towel and a party popper.
 */

private const val TAU = 2f * PI.toFloat()

private fun mix(from: Float, to: Float, amount: Float): Float = from + (to - from) * amount

private fun mix(from: Offset, to: Offset, amount: Float): Offset = Offset(mix(from.x, to.x, amount), mix(from.y, to.y, amount))

private fun mix(from: MoArm, to: MoArm, amount: Float): MoArm =
    MoArm(mix(from.reach, to.reach, amount), mix(from.bend, to.bend, amount))

/** 0 outside [from]..[to], 1 inside, eased over [edge] at both ends: one phase of a cycle. */
private fun during(c: Float, from: Float, to: Float, edge: Float): Float =
    ease((c - from) / edge) * (1f - ease((c - (to - edge)) / edge))

/** A fixed number from 0 to 1 for piece [index], so a crowd of things can differ without randomness. */
private fun scatter(index: Int, salt: Int): Float = (sin(index * 12.9898f + salt * 78.233f) * 43758.547f).mod(1f)

/** He watches a toy train run laps round his feet, and waves at it every time it comes past the front. */
private object ToyTrainLap : MoAct("happy_toy_train_lap", MoOccasion.Happy, intensity = MoIntensity.Mild) {
    private const val LAP_SECONDS = 4.8f
    private const val CAR_GAP = 0.42f
    private const val TOY_SCALE = 1.7f
    private val track = Offset(CENTRE, 410f)
    private const val TRACK_RX = 140f
    private const val TRACK_RY = 14f

    private fun direction(v: MoVariant) = if (v.chance(50, salt = 3)) 1f else -1f

    // At t = 0 the engine has just passed the middle of the front, so the wagons behind it are centred.
    private fun lap(t: Float, v: MoVariant): Float = PI.toFloat() / 2f + direction(v) * (0.35f + cycle(t, LAP_SECONDS) * TAU)

    private fun onTrack(angle: Float) = track + Offset(cos(angle) * TRACK_RX, sin(angle) * TRACK_RY)

    /** How much of his attention the front of the lap has: 0 while the engine is behind him. */
    private fun inFront(angle: Float) = ease((sin(angle) + 0.25f) / 0.5f)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val angle = lap(t, v)
        val front = inFront(angle)
        val engineX = onTrack(angle).x
        val look = Offset(cos(angle) * 0.95f, 0.3f + 0.45f * front)
        val wavingRight = v.chance(50, salt = 4)
        val waving = mix(MoArm.Down, MoArm(Offset(40f + 8f * wave(t, 0.35f), -40f), bend = 0.4f), front)
        val shape = MoBodies.of(body)
        // A tiny courteous lift of each foot as the engine passes under it.
        fun lift(footX: Float) = 6f * front * ease(1f - abs(engineX - footX) / 44f)
        return moIdle(t).copy(
            leftEye = MoEye(gaze = look),
            rightEye = MoEye(gaze = look),
            mouth = if (front > 0.3f) MoMouth.Grin(open = 0.3f + 0.4f * front) else MoMouth.Smile(),
            leftArm = if (wavingRight) MoArm.Hip else waving,
            rightArm = if (wavingRight) waving else MoArm.Hip,
            legs = MoLegs(MoLeg(lift = lift(shape.leftFootX)), MoLeg(lift = lift(shape.rightFootX))),
        )
    }

    private fun DrawScope.rails(far: Boolean) {
        val half = if (far) -1f else 1f
        val steps = 24
        val points = (0..steps).map { onTrack(half * it / steps * PI.toFloat()) }
        points.drop(1).dropLast(1).forEach { limb(it + Offset(0f, -3f), it + Offset(0f, 3f), MoPropWood, 3f) }
        polyline(MoPropGreyDark, 3f, points)
    }

    /** The cars on one half of the lap, farthest first, so the train threads behind him car by car. */
    private fun DrawScope.train(t: Float, v: MoVariant, far: Boolean) {
        val direction = direction(v)
        val lead = lap(t, v)
        val (colour, ledge) = v.of(listOf(MoPropRed to MoPropRedDark, MoPropBlue to MoPropBlueDark, MoPropGreen to MoPropGreenDark), salt = 1)
        val wagons = listOf(MoPropYellow to MoPropYellowDark, MoPropPink to MoPropPinkDark, MoPropTeal to MoPropTealDark)
        val count = 1 + v.pick(3, salt = 2)
        (0..count).map { it to lead - direction * CAR_GAP * it }
            .filter { (_, angle) -> (sin(angle) <= 0f) == far }
            .sortedBy { (_, angle) -> sin(angle) }
            .forEach { (index, angle) ->
                val at = onTrack(angle)
                val size = TOY_SCALE * (0.925f + 0.075f * sin(angle))
                // Seen from the side across the front and the back, and nearly end-on at the two turns.
                val heading = if (-sin(angle) * direction >= 0f) 1f else -1f
                val turned = heading * (0.5f + 0.5f * min(1f, abs(sin(angle)) * 2.5f))
                withTransform({
                    translate(at.x, at.y)
                    scale(turned * size, size, Offset.Zero)
                }) {
                    if (index == 0) engine(colour, ledge) else wagons[(index - 1) % wagons.size].let { wagon(it.first, it.second) }
                }
                if (index == 0) {
                    val funnel = at + Offset(turned * size * 10f, -size * 34f)
                    for (n in 0..2) {
                        val rise = cycle(t, 0.8f, offset = n / 3f)
                        puff(funnel + Offset(-turned * 16f * rise, -22f * rise), 4f + 4f * rise, MoPropWhite.copy(alpha = 0.9f * (1f - rise)))
                    }
                }
            }
    }

    // Both cars are drawn facing right with their wheels standing on y = 0.
    private fun DrawScope.engine(colour: Color, ledge: Color) {
        block(Offset(10f, -29f), 7f, 10f, MoPropCharcoal, corner = 2f)
        block(Offset(-9f, -20f), 13f, 24f, colour, corner = 4f)
        block(Offset(-9f, -25f), 7f, 7f, MoPropCream, corner = 2f)
        chunk(Offset(3f, -14f), 30f, 14f, colour, ledge, corner = 5f, drop = 4f)
        drawCircle(MoPropGold, 3f, Offset(14f, -16f))
        drawCircle(MoPropCharcoal, 4.5f, Offset(-8f, -4.5f))
        drawCircle(MoPropCharcoal, 4.5f, Offset(9f, -4.5f))
    }

    private fun DrawScope.wagon(colour: Color, ledge: Color) {
        chunk(Offset(0f, -12f), 24f, 13f, colour, ledge, corner = 4f, drop = 4f)
        drawCircle(MoPropCharcoal, 4f, Offset(-7f, -4f))
        drawCircle(MoPropCharcoal, 4f, Offset(7f, -4f))
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        rails(far = true)
        train(t, v, far = true)
    }

    override fun DrawScope.foreground(t: Float, v: MoVariant) {
        rails(far = false)
        train(t, v, far = false)
    }

    override val ownsTheFloor get() = true
}

/** He tips over for a cartwheel, wobbles on one mitten, gives up, and takes a proud bow for it anyway. */
private object AlmostCartwheel : MoAct("happy_almost_cartwheel", MoOccasion.Happy) {
    private const val LEAN = 27f

    private fun attemptSeconds(v: MoVariant) = v.between(3.6f, 4.8f, salt = 3)

    // The offset puts t = 0 in the middle of a ta-da, and the two cycles stay in step with each other.
    private fun attempt(t: Float, v: MoVariant) = cycle(t, attemptSeconds(v), offset = 0.84f)

    private fun side(t: Float, v: MoVariant): Float {
        val first = if (v.chance(50, salt = 1)) 1f else -1f
        return if (cycle(t, attemptSeconds(v) * 2f, offset = 0.42f) < 0.5f) first else -first
    }

    private fun tipped(h: Float) = ease(h / 0.2f) * (1f - ease((h - 0.6f) / 0.1f))

    private fun taDa(h: Float) = during(h, 0.7f, 1f, 0.07f)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val h = attempt(t, v)
        val d = side(t, v)
        val over = tipped(h)
        val up = taDa(h)
        val wobbling = during(h, 0.2f, 0.62f, 0.04f)
        val degrees = over * (LEAN + 2.5f * wave(t, 0.23f) * wobbling)
        // Leaning about the middle of his feet would push the near foot through the floor.
        val raised = 0.75f * degrees

        // The planted mitten is worked back from the floor, so it stays put while the rest of him wobbles.
        val shape = MoBodies.of(body)
        val lean = degrees / 180f * PI.toFloat()
        val shoulderHeight = (FEET - shape.shoulderY) * cos(lean) - shape.shoulderHalf * sin(lean)
        val down = shoulderHeight + raised - 12f
        val planted = MoArm(Offset(18f * cos(lean) + down * sin(lean), down * cos(lean) - 18f * sin(lean)), bend = 0.1f)
        val near = mix(MoArm.Down, planted, over)
        val far = mix(MoArm.Down, MoArm(Offset(50f, -30f), bend = 0.2f), over)
        val cheer = MoArm(Offset(40f, -60f), bend = 0.25f)
        val kick = MoLeg(lift = 26f * over, swing = 16f * over)

        val eye = when {
            up > 0.5f -> MoEye(shape = MoEyeShape.Star)
            else -> MoEye(iris = mix(0.76f, 0.6f, over), gaze = Offset(d * 0.8f, 0.8f) * over)
        }
        val landing = if (h in 0.62f..0.72f) sin((h - 0.62f) / 0.1f * PI.toFloat()) else 0f
        return moIdle(t, energy = 1.2f).let { idle ->
            idle.copy(
                leftEye = eye,
                rightEye = eye,
                brows = if (over > 0.5f) MoBrows(MoBrowKind.Raised, skew = 6f) else MoBrows(),
                mouth = when {
                    up > 0.5f -> MoMouth.Beam
                    wobbling > 0.5f -> MoMouth.Gritted
                    over > 0.05f -> MoMouth.Oh(size = 6f)
                    else -> MoMouth.Grin(open = 0.6f)
                },
                leftArm = mix(if (d < 0f) near else far, cheer, up),
                rightArm = mix(if (d < 0f) far else near, cheer, up),
                legs = if (d < 0f) MoLegs(right = kick) else MoLegs(left = kick),
                sway = idle.sway + wave(t, 0.09f) * 5f * wobbling,
                flare = 14f * up,
                squash = idle.squash - 0.05f * landing,
                tilt = d * degrees,
                offset = Offset(-d * 56f * over, -raised - 6f * up),
                shake = wave(t, 0.07f) * 1.2f * wobbling,
                blinks = up < 0.5f,
            )
        }
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val h = attempt(t, v)
        val d = side(t, v)
        val up = taDa(h)
        val colour = v.of(listOf(MoPropGold, MoPropPink, MoPropSky), salt = 2)
        if (up > 0f) {
            val spots = listOf(Offset(-74f, 46f), Offset(70f, 34f), Offset(-122f, 128f), Offset(118f, 112f))
            spots.forEachIndexed { index, spot ->
                sparkle(rig.tip + spot, 17f, up * (0.75f + 0.25f * pulse(t + index * 0.17f, 0.6f)), colour)
            }
        }
        // Drops of effort fly off beside his brow on the high side, never across his face.
        val effort = during(h, 0.2f, 0.62f, 0.04f)
        if (effort > 0f) {
            val brow = if (d > 0f) rig.leftEye else rig.rightEye
            for (index in 0..1) {
                val fly = cycle(t, 0.8f, offset = index * 0.5f)
                drop(brow + Offset(-d * (64f + fly * 30f), -34f - fly * 22f + index * 16f), 6f, MoPropWater.copy(alpha = effort * (1f - fly)))
            }
        }
    }

    override val sparkles get() = false
}

/** He warms a paper lantern in his mittens until it lifts off, and watches it join the others in the sky. */
private object SkyLanterns : MoAct("happy_sky_lanterns", MoOccasion.Happy, intensity = MoIntensity.Mild) {
    private const val RELEASE_SECONDS = 5f
    private const val WARMING = 0.6f
    private const val HANDOVER_Y = 320f
    private const val HANDOVER_X = 130f
    private const val SKY_TOP = 10f
    private val colours = listOf(MoPropRed, MoPropPink, MoPropYellow, MoPropMint, MoPropPurple)

    private fun inTheSky(v: MoVariant) = 2 + v.pick(2, salt = 5)

    /**
     * The one clock everything reads: how many lanterns he has let go, counted round a span long
     * enough to tell odd from even, and how far into the present one he is. Layers that each kept
     * their own cycle could disagree for a frame at the handover and flash a lantern on the wrong side.
     */
    private fun clock(t: Float, v: MoVariant): Pair<Int, Float> {
        val span = inTheSky(v) * 2
        val turns = cycle(t, RELEASE_SECONDS * span, offset = 0.35f / span) * span
        val released = floor(turns).toInt().coerceIn(0, span - 1)
        return released to (turns - released).coerceIn(0f, 1f)
    }

    /** Releases drift off to alternate sides. */
    private fun side(release: Int, v: MoVariant): Float =
        (if (v.chance(50, salt = 6)) 1f else -1f) * (if (Math.floorMod(release, 2) == 0) 1f else -1f)

    // Stepping by two from one pick keeps the lanterns that share the sky in different colours.
    private fun colour(index: Int, v: MoVariant) = colours[(v.pick(colours.size, salt = 1) + index * 2) % colours.size]

    /** A paper lantern: a dome narrowing to an open rim, with its little light showing at the bottom. */
    private fun DrawScope.lantern(centre: Offset, size: Float, colour: Color, v: MoVariant) {
        val round = v.chance(50, salt = 7)
        val w = paperWidth(v) * size
        val h = (if (round) 52f else 62f) * size
        // One path, so it stays one flat shape while it is still small and half faded in.
        val alpha = ease(size * 4f)
        val paper = Path().apply {
            moveTo(centre.x - w / 2f, centre.y - h * 0.12f)
            cubicTo(centre.x - w / 2f, centre.y - h * 0.63f, centre.x + w / 2f, centre.y - h * 0.63f, centre.x + w / 2f, centre.y - h * 0.12f)
            lineTo(centre.x + w * 0.3f, centre.y + h / 2f)
            lineTo(centre.x - w * 0.3f, centre.y + h / 2f)
            close()
        }
        drawPath(paper, colour.copy(alpha = alpha))
        for (rib in listOf(-1f, 1f)) {
            limb(centre + Offset(rib * w * 0.2f, -h * 0.36f), centre + Offset(rib * w * 0.13f, h * 0.2f), MoPropWhite.copy(alpha = 0.3f * alpha), 3f * size)
        }
        oval(centre + Offset(0f, h * 0.36f), w * 0.2f, h * 0.1f, MoPropCream.copy(alpha = alpha))
        limb(centre + Offset(-w * 0.3f, h / 2f), centre + Offset(w * 0.3f, h / 2f), MoPropBrown.copy(alpha = alpha), 5f * size)
    }

    private fun paperWidth(v: MoVariant) = if (v.chance(50, salt = 7)) 56f else 46f

    private fun launchHeight(mouth: Offset) = mouth.y + 48f

    private fun paperSize(c: Float) = ease(c / WARMING * 0.8f + 0.2f) * ease(c / 0.03f + 0.3f)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val (released, c) = clock(t, v)
        val drift = side(released + 1, v)
        val away = ease((c - WARMING) / 0.28f) * (1f - ease((c - 0.93f) / 0.07f))
        // The lantern is still below his eyes when the sky takes it over, so his gaze follows it there.
        val look = mix(Offset(0f, 0.8f), Offset(drift * 0.95f, 0.25f), away)
        val open = during(c, WARMING, 0.98f, 0.1f)
        val size = mix(paperSize(c.coerceAtMost(WARMING)), paperSize(0f), ease((c - 0.9f) / 0.1f))
        val shape = MoBodies.of(body)
        val grip = paperWidth(v) * size / 2f + 7f - shape.shoulderHalf
        val cupped = mix(MoArm(Offset(grip, launchHeight(shape.mouth) - shape.shoulderY), bend = 0.3f), MoArm(Offset(-34f, 28f), bend = 0.3f), open)
        return moIdle(t, energy = 0.7f).copy(
            leftEye = MoEye(gaze = look, iris = 0.8f),
            rightEye = MoEye(gaze = look, iris = 0.8f),
            brows = if (c in WARMING..0.9f) MoBrows(MoBrowKind.Raised, lift = 3f) else MoBrows(),
            mouth = if (c in WARMING..0.76f) MoMouth.Oh(size = 6f) else MoMouth.Smile(width = 15f, depth = 12f),
            leftArm = cupped,
            rightArm = cupped,
            armsInFront = true,
            flare = 8f * ease(c / WARMING) * (1f - ease((c - WARMING) / 0.08f)),
            // He rocks a little, but stands straight at the moment a lantern leaves him for the sky,
            // so it does not jump when the world takes it over.
            tilt = 1.2f * sin((c - WARMING) * 5f * PI.toFloat()),
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val count = inTheSky(v)
        val (released, c) = clock(t, v)
        for (index in 0 until count) {
            // The sky lantern at [index] is the latest release that landed on its turn.
            val age = Math.floorMod(released - index, count)
            val rise = (age + c) / count
            // They lean in over his shoulders as they climb, where the narrowing flame leaves them room.
            val at = Offset(
                CENTRE + side(released - age, v) * (HANDOVER_X - 40f * rise) + 6f * sin(rise * 9f),
                mix(HANDOVER_Y, SKY_TOP, rise),
            )
            // Far away at the top, they dwindle to nothing before they reach the edge of the picture.
            lantern(at, (1f - 0.3f * rise) * (1f - ease((rise - 0.8f) / 0.2f)), colour(index, v), v)
        }
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val (released, c) = clock(t, v)
        val release = released + 1
        val index = Math.floorMod(release, inTheSky(v))
        val hands = (rig.leftHand + rig.rightHand) / 2f
        if (c < WARMING) {
            lantern(hands, paperSize(c), colour(index, v), v)
        } else {
            // Out to the side first and then up, so it never crosses his eyes, arriving at the speed
            // the sky lanterns rise with.
            val r = (c - WARMING) / (1f - WARMING)
            val startY = launchHeight(rig.mouth)
            val climb = max(startY - HANDOVER_Y, 1f)
            val skySpeed = (HANDOVER_Y - SKY_TOP) / (RELEASE_SECONDS * inTheSky(v))
            val slope = (skySpeed * RELEASE_SECONDS * (1f - WARMING) / climb).coerceIn(0f, 2f)
            val up = (3f - slope) * r * r + (slope - 2f) * r * r * r
            val at = Offset(hands.x + (CENTRE + side(release, v) * HANDOVER_X - hands.x) * ease(r * 1.25f), startY - climb * up)
            lantern(at, 1f, colour(index, v), v)
        }
    }

    override val sparkles get() = false
}

/** He blows soap bubbles up the side, and goes cross-eyed when one drifts back to sit on his tip. */
private object BlowingBubbles : MoAct("happy_blowing_bubbles", MoOccasion.Happy, intensity = MoIntensity.Mild) {
    private const val ROUND_SECONDS = 5.2f
    private const val STREAM_SECONDS = 2.6f
    private const val BLOWING = 0.55f
    private const val VISITOR = 16f

    private fun round(t: Float) = cycle(t, ROUND_SECONDS, offset = 0.4f)

    private fun wandSide(v: MoVariant) = if (v.chance(50, salt = 2)) 1f else -1f

    private fun onTheTip(v: MoVariant) = v.chance(60, salt = 4)

    private fun bubbles(v: MoVariant) = 4 + v.pick(3, salt = 3)

    private fun visiting(c: Float) = during(c, 0.5f, 1f, 0.1f)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val c = round(t)
        val s = wandSide(v)
        val visit = visiting(c)
        val sitting = c in 0.62f..0.9f
        val free = if (onTheTip(v)) MoArm.Hip else mix(MoArm.Hip, MoArm(Offset(44f, -8f), bend = 0.1f), visit)
        val wand = MoArm(Offset(-12f, -10f), bend = 0.55f)
        val stream = Offset(s * 0.7f, -0.3f)
        fun eye(side: Float) = MoEye(
            gaze = mix(stream, if (onTheTip(v)) Offset(-side * 0.35f, -1f) else Offset(-s * 0.95f, 0.15f), visit),
        )
        // His cheeks give a little with every bubble he sends off.
        val huff = twitch(t, STREAM_SECONDS / bubbles(v), beat = 0.3f) * (1f - visit)
        return moIdle(t).let { idle ->
            idle.copy(
                leftEye = eye(-1f),
                rightEye = eye(1f),
                brows = if (visit > 0.5f) MoBrows(MoBrowKind.Raised, lift = 5f) else MoBrows(),
                mouth = when {
                    sitting -> MoMouth.Oh(size = 6f)
                    c > BLOWING -> MoMouth.Smile()
                    s > 0f -> MoMouth.Whistle
                    // The whistling mouth purses to the right, so blowing to the left takes a plain round one.
                    else -> MoMouth.Oh(size = 5.5f)
                },
                leftArm = if (s < 0f) wand else free,
                rightArm = if (s < 0f) free else wand,
                armsInFront = true,
                // He holds himself still and gentle so the bubble can sit.
                sway = idle.sway * (1f - 0.5f * visit),
                flare = -4f * visit,
                squash = idle.squash - 0.02f * huff,
            )
        }
    }

    private fun DrawScope.bubble(centre: Offset, radius: Float, alpha: Float) {
        drawCircle(MoBubble.copy(alpha = 0.8f * alpha), radius, centre)
        drawCircle(MoPropWhite.copy(alpha = 0.9f * alpha), radius * 0.22f, centre + Offset(-radius * 0.4f, -radius * 0.4f))
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val c = round(t)
        val s = wandSide(v)
        val colour = v.of(listOf(MoPropPink, MoPropSky, MoPropMint), salt = 1)
        val hand = if (s < 0f) rig.leftHand else rig.rightHand
        val ring = rig.mouth + Offset(s * 40f, 7f)
        limb(hand, ring + Offset(s * 12f, 0f), colour, 5f)
        grippingMitten(rig, s)
        drawCircle(colour, 12f, ring, style = Stroke(width = 5f))
        drawCircle(MoBubble.copy(alpha = 0.35f), 10f, ring)

        val total = bubbles(v)
        for (index in 0 until total) {
            val r = cycle(t, STREAM_SECONDS, offset = index.toFloat() / total)
            // A bubble exists only if it left the ring while he was blowing.
            val bornAt = (c - r * STREAM_SECONDS / ROUND_SECONDS).mod(1f)
            if (bornAt !in 0.01f..BLOWING) continue
            val out = 1f - (1f - r) * (1f - r)
            val at = ring + Offset(s * (4f + 88f * out) + 6f * sin(r * 7f + index), -190f * r)
            bubble(at, 6f + 12f * r, alpha = 1f - r * r * r * r)
        }

        // The one that comes back, settles for a moment, and floats on without popping.
        val arrive = ease((c - 0.44f) / 0.18f)
        val leave = ease((c - 0.9f) / 0.1f)
        if (arrive > 0f) {
            val freeHand = if (s < 0f) rig.rightHand else rig.leftHand
            // rig.tip lies a little inside the flame, so the bubble sits higher, on its very top.
            val seat = if (onTheTip(v)) rig.tip + Offset(4f, -22f) else freeHand + Offset(0f, -12.5f - VISITOR + 2f)
            val from = if (onTheTip(v)) seat + Offset(s * 96f, 34f) else seat + Offset(s * 14f, -110f)
            val wobble = Offset(2f * wave(t, 1.1f), 1.5f * wave(t, 0.7f))
            val at = mix(from, seat, arrive) + wobble * arrive + Offset(-s * 12f, -38f) * leave
            bubble(at, VISITOR, alpha = min(1f, arrive * 2f) * (1f - leave))
        }
    }

    override val sparkles get() = false
}

/** He sits on a beach towel with his eyes shut, soaking up the sun, and now and then his tip reaches for it. */
private object Sunbathing : MoAct("happy_sunbathing", MoOccasion.Happy, intensity = MoIntensity.Mild) {
    private fun sunSide(v: MoVariant) = if (v.chance(50, salt = 1)) 1f else -1f

    private fun bask(t: Float, v: MoVariant) = cycle(t, v.between(6f, 9f, salt = 4), offset = 0.2f)

    private fun sigh(c: Float) = if (c > 0.9f) sin((c - 0.9f) / 0.1f * PI.toFloat()) else 0f

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val c = bask(t, v)
        val s = sunSide(v)
        val reach = during(c, 0.7f, 0.92f, 0.07f)
        val peek = ease((reach - 0.15f) / 0.85f)
        val peeking = if (reach > 0.15f) MoEye(gaze = Offset(s * 0.9f, -0.55f), lidDrop = 1f - 0.7f * peek) else MoEye(shape = MoEyeShape.Shut)
        val shut = MoEye(shape = MoEyeShape.Shut)
        // Mittens folded on his belly, rising and falling with it.
        val folded = MoArm(Offset(-72f, 34f - 3f * wave(t, 3.4f)), bend = 0.35f)
        return moIdle(t, energy = 0.5f).let { idle ->
            idle.copy(
                leftEye = if (s < 0f) peeking else shut,
                rightEye = if (s < 0f) shut else peeking,
                mouth = if (sigh(c) > 0.2f) MoMouth.Oh(size = 5f) else MoMouth.Smile(width = 16f + 4f * reach, depth = 13f + 3f * reach),
                leftArm = folded,
                rightArm = folded,
                armsInFront = true,
                legs = MoLegs(MoLeg(lift = 16f, swing = 7f), MoLeg(lift = 16f, swing = 7f)),
                offset = Offset(0f, 16f),
                sway = idle.sway + s * 14f * reach,
                flare = 12f * reach,
                squash = -0.015f + 0.012f * wave(t, 3.4f) - 0.03f * sigh(c),
                tilt = -s * 3f,
                blinks = false,
            )
        }
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val s = sunSide(v)
        val sun = Offset(CENTRE + s * 108f, 65f)
        for (index in 0 until 10) {
            val angle = index / 10f * TAU + t * 0.15f
            val ray = Offset(cos(angle), sin(angle))
            limb(sun + ray * 34f, sun + ray * (44f + 5f * pulse(t + index * 0.3f, 2.4f)), MoPropYellow, 6f)
        }
        drawCircle(MoPropGold, 26f, sun + Offset(0f, 3f))
        drawCircle(MoPropYellow, 26f, sun)

        val (towel, towelLedge) = v.of(
            listOf(MoPropRed to MoPropRedDark, MoPropTeal to MoPropTealDark, MoPropBlue to MoPropBlueDark, MoPropPink to MoPropPinkDark),
            salt = 2,
        )
        floorShadow(CENTRE, 156f, MoPropShadow)
        chunk(Offset(CENTRE, 422f), 300f, 12f, towel, towelLedge, corner = 5f, drop = 4f)
        for (x in listOf(-138f, -118f, 118f, 138f)) block(Offset(CENTRE + x, 420f), 9f, 12f, MoPropWhite, corner = 2f)

        // One thing beside him on the side away from the sun.
        val beside = Offset(CENTRE - s * 140f, 414f)
        if (v.chance(50, salt = 3)) {
            limb(beside + Offset(3f, -26f), beside + Offset(3f + s * 9f, -52f), MoPropRed, 4f)
            chunk(beside + Offset(0f, -17f), 24f, 32f, MoPropSky, MoPropBlue, corner = 6f, drop = 4f)
        } else {
            val ball = beside + Offset(0f, -17f)
            drawCircle(MoPropRed, 17f, ball)
            oval(ball, 6.5f, 17f, MoPropWhite)
            drawCircle(MoPropYellow, 3.5f, ball + Offset(0f, -13.5f))
        }
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val out = sigh(bask(t, v))
        if (out > 0f) {
            val s = sunSide(v)
            val drift = 1f - out
            puff(rig.mouth + Offset(-s * (22f + 16f * drift), 2f - 10f * drift), 5f + 3f * drift, MoPropWhite.copy(alpha = 0.7f * out))
        }
    }

    override val ownsTheFloor get() = true
    override val sparkles get() = false
}

/** He sets off a party popper over his shoulder and watches the confetti flutter all the way down. */
private object ConfettiPopper : MoAct("happy_confetti_popper", MoOccasion.Happy) {
    private const val POP_SECONDS = 3f
    private const val POP_OFFSET = 0.12f

    private fun sincePop(t: Float) = cycle(t, POP_SECONDS, offset = POP_OFFSET)

    private fun popperSide(v: MoVariant) = if (v.chance(50, salt = 3)) 1f else -1f

    private fun popperArm(t: Float) = MoArm(Offset(30f, -50f + 8f * twitch(t, POP_SECONDS, beat = 0.25f, offset = POP_OFFSET)), bend = 0.4f)

    /** The way the cone points: up, and inward over his shoulder. */
    private fun aim(s: Float): Offset = Offset(-s * 0.33f, -0.944f)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val c = sincePop(t)
        val s = popperSide(v)
        val kick = twitch(t, POP_SECONDS, beat = 0.4f, offset = POP_OFFSET)
        val thrown = 1f - ease((c - 0.4f) / 0.2f) + ease((c - 0.94f) / 0.06f)
        val free = mix(MoArm(Offset(42f, -18f), bend = 0.4f), MoArm(Offset(36f, -56f), bend = 0.3f), thrown.coerceIn(0f, 1f))
        val fall = ease((c - 0.1f) / 0.8f)
        val eye = if (c < 0.1f) {
            MoEye(shape = MoEyeShape.Happy)
        } else {
            MoEye(iris = 0.8f, gaze = mix(Offset(-s * 0.3f, -0.9f), Offset(0f, 0.4f), fall))
        }
        val seconds = c * POP_SECONDS
        return moIdle(t, energy = 1.3f).let { idle ->
            idle.copy(
                leftEye = eye,
                rightEye = eye,
                mouth = if (c < 0.35f) MoMouth.Beam else MoMouth.Grin(open = 0.8f),
                leftArm = if (s < 0f) popperArm(t) else free,
                rightArm = if (s < 0f) free else popperArm(t),
                // He ducks at the bang and springs back up.
                squash = idle.squash + if (seconds < 0.4f) -0.04f * sin(seconds / 0.4f * TAU) else 0f,
                flare = 16f * kick,
                tilt = -s * 2f,
                offset = Offset(0f, -8f * kick),
            )
        }
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val s = popperSide(v)
        val colour = v.of(listOf(MoPropPurple, MoPropBlue, MoPropRed, MoPropTeal), salt = 2)
        val hand = if (s < 0f) rig.leftHand else rig.rightHand
        val axis = aim(s)
        val across = Offset(-axis.y, axis.x)
        val point = hand - axis * 8f
        fun along(f: Float, w: Float) = point + axis * (62f * f) + across * (17f * f * w)
        shape(colour, point.x to point.y, along(1f, 1f).let { it.x to it.y }, along(1f, -1f).let { it.x to it.y })
        limb(along(0.45f, 0.6f), along(0.45f, -0.6f), MoPropWhite, 5f)
        limb(along(0.7f, 0.75f), along(0.7f, -0.75f), MoPropWhite, 5f)
        limb(along(1f, 1f), along(1f, -1f), MoPropGold, 6f)
        grippingMitten(rig, s)
        val bang = sincePop(t) * POP_SECONDS / 0.25f
        if (bang < 1f) sparkle(along(1.2f, 0f), 24f, 1f - bang, MoSpark)
    }

    override fun DrawScope.foreground(t: Float, v: MoVariant) {
        val c = sincePop(t)
        val s = popperSide(v)
        val palette = listOf(MoPropPink, MoPropSky, MoPropYellow, MoPropMint, MoPropPurple, MoPropRed)
        val first = v.pick(palette.size, salt = 1)
        val pieces = 18 + v.pick(13, salt = 4)
        // The pieces scatter at once, so where the cone's mouth is on a body in between is near enough.
        val arm = popperArm(t)
        val mouth = Offset(CENTRE + s * (92f + arm.reach.x), 336f + arm.reach.y) + aim(s) * 56f
        for (index in 0 until pieces) {
            // Up and inward in a fan, never far outward, where the edge of the picture is.
            val degrees = mix(-75f, 12f, scatter(index, 1))
            val angle = degrees / 180f * PI.toFloat()
            val height = mix(70f, 190f, scatter(index, 2))
            val burst = 1f - (1f - min(c / 0.16f, 1f)).let { it * it }
            val peak = mouth + Offset(s * sin(angle), -cos(angle)) * (height * burst)
            // Quick pieces land and lie there; slow ones are still in the air at the next pop.
            val floor = mix(414f, 426f, scatter(index, 3))
            val speed = mix(190f, 460f, scatter(index, 4))
            val falling = max(c - 0.14f, 0f) / 0.86f
            val landing = max(floor - peak.y, 0f) / speed
            val landed = falling >= landing
            // A landed piece keeps the place and the turn it had when it touched down.
            val inTheAir = min(falling, landing)
            val airTime = t - (falling - inTheAir) * 0.86f * POP_SECONDS
            val flutter = 10f * sin(airTime * 4f + index) * ease(inTheAir * 4f)
            val at = Offset(
                (peak.x + flutter + s * mix(-110f, 30f, scatter(index, 5)) * sqrt(inTheAir)).coerceIn(-38f, 268f),
                min(peak.y + falling * speed, floor),
            )
            val colour = palette[(first + index % 4) % palette.size].copy(alpha = 1f - ease((c - 0.88f) / 0.12f))
            if (index % 5 == 0) {
                drawCircle(colour, 5f, at)
            } else {
                // It settles flat once it lies on the floor.
                val flat = if (landed) ease((falling - landing) / 0.03f) else 0f
                val flip = mix(abs(cos(airTime * 6f + index)), 1f, flat)
                block(at, 14f, max(8f * flip, 2.5f), colour, corner = 3f)
            }
        }
    }

    override val sparkles get() = false
}

internal val happyActs4: List<MoAct> = listOf(
    ToyTrainLap,
    AlmostCartwheel,
    SkyLanterns,
    BlowingBubbles,
    Sunbathing,
    ConfettiPopper,
)
