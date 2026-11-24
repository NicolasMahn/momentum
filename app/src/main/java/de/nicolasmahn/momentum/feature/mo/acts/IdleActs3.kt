package de.nicolasmahn.momentum.feature.mo.acts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import de.nicolasmahn.momentum.core.ui.theme.MoFlameBelly
import de.nicolasmahn.momentum.core.ui.theme.MoFlameOuter
import de.nicolasmahn.momentum.core.ui.theme.MoPropBlue
import de.nicolasmahn.momentum.core.ui.theme.MoPropBlueDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropCream
import de.nicolasmahn.momentum.core.ui.theme.MoPropGold
import de.nicolasmahn.momentum.core.ui.theme.MoPropGreen
import de.nicolasmahn.momentum.core.ui.theme.MoPropInk
import de.nicolasmahn.momentum.core.ui.theme.MoPropOrange
import de.nicolasmahn.momentum.core.ui.theme.MoPropPink
import de.nicolasmahn.momentum.core.ui.theme.MoPropPurple
import de.nicolasmahn.momentum.core.ui.theme.MoPropPurpleDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropRed
import de.nicolasmahn.momentum.core.ui.theme.MoPropRedDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropSand
import de.nicolasmahn.momentum.core.ui.theme.MoPropShadow
import de.nicolasmahn.momentum.core.ui.theme.MoPropSilver
import de.nicolasmahn.momentum.core.ui.theme.MoPropSky
import de.nicolasmahn.momentum.core.ui.theme.MoPropSnow
import de.nicolasmahn.momentum.core.ui.theme.MoPropSteel
import de.nicolasmahn.momentum.core.ui.theme.MoPropTeal
import de.nicolasmahn.momentum.core.ui.theme.MoPropTealDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropWhite
import de.nicolasmahn.momentum.core.ui.theme.MoPropWood
import de.nicolasmahn.momentum.core.ui.theme.MoPropWoodDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropYellow
import de.nicolasmahn.momentum.core.ui.theme.MoPropYellowDark
import de.nicolasmahn.momentum.domain.mo.MoIntensity
import de.nicolasmahn.momentum.domain.mo.MoOccasion
import de.nicolasmahn.momentum.feature.mo.CENTRE
import de.nicolasmahn.momentum.feature.mo.FEET
import de.nicolasmahn.momentum.feature.mo.MoArm
import de.nicolasmahn.momentum.feature.mo.MoBodies
import de.nicolasmahn.momentum.feature.mo.MoBody
import de.nicolasmahn.momentum.feature.mo.MoBodyKind
import de.nicolasmahn.momentum.feature.mo.MoBrowKind
import de.nicolasmahn.momentum.feature.mo.MoBrows
import de.nicolasmahn.momentum.feature.mo.MoEye
import de.nicolasmahn.momentum.feature.mo.MoEyeShape
import de.nicolasmahn.momentum.feature.mo.MoLeg
import de.nicolasmahn.momentum.feature.mo.MoLegs
import de.nicolasmahn.momentum.feature.mo.MoMouth
import de.nicolasmahn.momentum.feature.mo.MoPose
import de.nicolasmahn.momentum.feature.mo.MoRig
import de.nicolasmahn.momentum.feature.mo.block
import de.nicolasmahn.momentum.feature.mo.chunk
import de.nicolasmahn.momentum.feature.mo.cycle
import de.nicolasmahn.momentum.feature.mo.ease
import de.nicolasmahn.momentum.feature.mo.flameTongue
import de.nicolasmahn.momentum.feature.mo.floorShadow
import de.nicolasmahn.momentum.feature.mo.grippingMitten
import de.nicolasmahn.momentum.feature.mo.limb
import de.nicolasmahn.momentum.feature.mo.moIdle
import de.nicolasmahn.momentum.feature.mo.oval
import de.nicolasmahn.momentum.feature.mo.polyline
import de.nicolasmahn.momentum.feature.mo.pulse
import de.nicolasmahn.momentum.feature.mo.shape
import de.nicolasmahn.momentum.feature.mo.sparkle
import de.nicolasmahn.momentum.feature.mo.wave
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

/*
 * Idle acts with a toy or a pastime beside him: a loop of several beats, each timed from how far
 * into the loop he is, so every beat eases into the next and the last one hands back to the first.
 */

private const val TURN = 2f * PI.toFloat()
private val FLOOR = Offset(CENTRE, FEET)

/** 0 to 1, eased, while [x] goes from [from] to [to]: one beat of a loop handing over to the next. */
private fun ramp(x: Float, from: Float, to: Float): Float = ease((x - from) / max(to - from, 0.001f))

/** One smooth bump, 0 to 1 to 0, while [x] goes from 0 to [width]. */
private fun bump(x: Float, width: Float): Float = if (x > 0f && x < width) sin(x / width * PI.toFloat()) else 0f

private fun mix(a: Float, b: Float, k: Float): Float = a + (b - a) * k

private fun mix(a: Offset, b: Offset, k: Float): Offset = a + (b - a) * k

private fun mix(a: MoArm, b: MoArm, k: Float): MoArm = MoArm(mix(a.reach, b.reach, k), mix(a.bend, b.bend, k))

private fun side(v: MoVariant, salt: Int): Float = if (v.chance(50, salt)) 1f else -1f

private fun shoulder(body: MoBody, side: Float): Offset = Offset(CENTRE + side * body.shoulderHalf, body.shoulderY)

/** The arm reach that puts the hand of [side] on [point], a point in his own space. */
private fun reachTo(body: MoBody, side: Float, point: Offset): Offset =
    Offset(side * (point.x - shoulder(body, side).x), point.y - shoulder(body, side).y)

private fun MoPose.withArms(side: Float, near: MoArm, far: MoArm): MoPose =
    if (side > 0f) copy(rightArm = near, leftArm = far) else copy(leftArm = near, rightArm = far)

private fun Offset.turned(degrees: Float): Offset {
    val angle = degrees * PI.toFloat() / 180f
    return Offset(x * cos(angle) - y * sin(angle), x * sin(angle) + y * cos(angle))
}

/** Where a point of his own space lands in the world, the way the kit moves him: squash, tilt, offset. */
private fun MoPose.toWorld(point: Offset): Offset {
    val fromFloor = point - FLOOR
    return Offset(fromFloor.x * (1f - squash / 2f), fromFloor.y * (1f + squash)).turned(tilt) + FLOOR + offset + Offset(shake, 0f)
}

/** The reverse of [toWorld], for a hand that has to meet something standing in the world. */
private fun MoPose.toBody(point: Offset): Offset {
    val back = (point - FLOOR - offset - Offset(shake, 0f)).turned(-tilt)
    return Offset(back.x / max(1f - squash / 2f, 0.1f), back.y / max(1f + squash, 0.1f)) + FLOOR
}

private fun DrawScope.heart(centre: Offset, size: Float, colour: Color) {
    drawCircle(colour, size * 0.52f, centre + Offset(-size * 0.45f, -size * 0.3f))
    drawCircle(colour, size * 0.52f, centre + Offset(size * 0.45f, -size * 0.3f))
    shape(colour, centre.x - size * 0.72f to centre.y, centre.x + size * 0.72f to centre.y, centre.x to centre.y + size * 0.8f)
}

/** He cranks a jack-in-the-box, and is startled, then delighted, every single time it pops. */
private object JackInTheBox : MoAct("idle_jack_in_the_box", MoOccasion.Idle) {
    private const val CRANK_TURN = 0.7f
    private const val CRANK_RADIUS = 11f
    private const val BOX_W = 68f
    private const val BOX_H = 60f
    private const val BOX_MIDDLE = FEET - 31f
    private const val BOX_TOP = BOX_MIDDLE - BOX_H / 2f - 2.5f
    private const val SPRING = 52f
    private const val PACKED_HEIGHT = -40f

    /** After the pop: jump, laugh, press it back down, find the crank again. */
    private const val AFTER = 2.8f

    private fun boxX(v: MoVariant) = CENTRE + side(v, 3) * 116f
    private fun axle(v: MoVariant) = Offset(boxX(v) - side(v, 3) * 38f, FEET - 40f)

    private fun paint(v: MoVariant) = v.of(
        listOf(MoPropRed to MoPropRedDark, MoPropBlue to MoPropBlueDark, MoPropPurple to MoPropPurpleDark, MoPropTeal to MoPropTealDark),
        salt = 1,
    )

    // Whole turns only, so the knob ends where the next loop picks it up.
    private fun cranking(v: MoVariant) = (3 + v.pick(3, salt = 4)) * CRANK_TURN

    /** Seconds since the pop; negative while he is still cranking. */
    private fun sincePop(t: Float, v: MoVariant): Float {
        val loop = cranking(v) + AFTER
        return cycle(t, loop) * loop - cranking(v)
    }

    private fun knob(t: Float, v: MoVariant): Offset {
        val p = sincePop(t, v)
        val angle = if (p < 0f) (p + cranking(v)) / CRANK_TURN * TURN else 0f
        return axle(v) + Offset(side(v, 3) * CRANK_RADIUS * cos(angle), CRANK_RADIUS * sin(angle))
    }

    private fun springHeight(p: Float): Float {
        if (p < 0f) return PACKED_HEIGHT
        val bob = 16f * sin(p / 0.38f * TURN) * exp(-2.5f * p)
        return mix(PACKED_HEIGHT, SPRING + bob, ramp(p, 0f, 0.1f) * (1f - ramp(p, 1.75f, 2.15f)))
    }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val s = side(v, 3)
        val p = sincePop(t, v)
        val build = MoBodies.of(body)
        val atTheLid = Offset(0.8f * s, 0.8f)

        val crank = MoArm(reachTo(build, s, knob(t, v)), bend = 0.15f)
        val up = MoArm(Offset(36f, -34f), bend = 0.25f)
        val pressing = MoArm(reachTo(build, s, Offset(boxX(v), BOX_TOP - max(springHeight(p) + 30f, 14f))), bend = 0.3f)
        var near = mix(crank, MoArm(Offset(7f, -56f), bend = 0.3f), ramp(p, 0f, 0.08f))
        near = mix(near, MoArm(Offset(0f, 12f + 4f * wave(t, 0.2f)), bend = 0.4f), ramp(p, 0.55f, 0.85f))
        near = mix(near, pressing, ramp(p, 1.45f, 1.75f))
        near = mix(near, crank, ramp(p, 2.2f, 2.75f))
        var far = mix(MoArm.Hip, up, ramp(p, 0f, 0.08f))
        far = mix(far, MoArm(Offset(30f, 2f - 4f * wave(t, 0.2f)), bend = 0.4f), ramp(p, 0.55f, 0.85f))
        far = mix(far, MoArm.Hip, ramp(p, 1.45f, 1.9f))

        val recoil = ramp(p, 0f, 0.12f) * (1f - ramp(p, 1.45f, 1.72f))
        val idle = moIdle(t).copy(
            armsInFront = true,
            offset = Offset(-s * 16f * recoil, -14f * bump(p, 0.4f)),
            flare = 10f * bump(p, 0.5f),
        ).withArms(s, near, far)
        return when {
            p < 0f || p >= 2.4f -> idle.eyes(MoEye(gaze = atTheLid)).copy(
                // His brows creep up as the tune nears its end, though he has no idea why.
                brows = MoBrows(MoBrowKind.Raised, lift = 4f * ramp(p, -cranking(v), 0f) * (1f - ramp(p, 0f, 0.1f))),
                mouth = MoMouth.Smile(14f, 10f),
            )
            p < 0.6f -> idle.eyes(MoEye(iris = 0.45f, scale = 1.06f, gaze = Offset(0.8f * s, 0.1f))).copy(
                brows = MoBrows(MoBrowKind.Raised, lift = 7f),
                mouth = MoMouth.Oh(9f),
                blinks = false,
            )
            p < 1.6f -> idle.eyes(MoEye(shape = MoEyeShape.Happy)).copy(
                mouth = MoMouth.Grin(open = 0.7f),
                shake = wave(t, 0.1f) * 1.2f,
            )
            else -> idle.eyes(MoEye(gaze = Offset(0.8f * s, 0.5f))).copy(
                brows = MoBrows(MoBrowKind.Flat, lift = 1f),
                mouth = MoMouth.TongueOut,
            )
        }
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val s = side(v, 3)
        val x = boxX(v)
        val p = sincePop(t, v)
        val (colour, dark) = paint(v)
        val open = if (p < 0f) 0f else ramp(p, 0f, 0.06f) * (1f - ramp(p, 2.15f, 2.3f))

        floorShadow(x, 44f, MoPropShadow)
        // The far hinge lets the lid swing clear of the cranking hand.
        withTransform({ rotate(s * 90f * open, Offset(x + s * 32f, BOX_TOP - 7f)) }) {
            block(Offset(x, BOX_TOP - 7f), 64f, 14f, dark, corner = 5f)
        }
        chunk(Offset(x, BOX_MIDDLE), BOX_W, BOX_H, colour, dark)
        sparkle(Offset(x, BOX_MIDDLE - 2f), 15f, 1f, MoPropCream)
    }

    override fun DrawScope.foreground(t: Float, v: MoVariant) {
        // The spring and the crank are drawn over him: his mitten is at the box, and the thing that
        // pops must never end up behind the arm he threw up in fright.
        val s = side(v, 3)
        val x = boxX(v)
        val height = springHeight(sincePop(t, v))
        // The front of the box hides the toy as he presses it back below the rim.
        clipRect(left = x - BOX_W / 2f, top = 0f, right = x + BOX_W / 2f, bottom = BOX_TOP) {
            val zigzag = (0..6).map { step ->
                val swing = if (step == 0 || step == 6) 0f else if (step % 2 == 0) 14f else -14f
                Offset(x + swing, BOX_TOP + 8f - (height + 8f) * step / 6f)
            }
            polyline(MoPropSilver, 6f, zigzag)
            val top = Offset(x, BOX_TOP - height - 14f)
            when (v.pick(3, salt = 2)) {
                0 -> sparkle(top, 24f, 1f, MoPropGold)
                1 -> heart(top, 22f, if (paint(v).first == MoPropRed) MoPropPink else MoPropRed)
                else -> {
                    drawCircle(MoPropYellow, 18f, top)
                    drawCircle(MoPropWhite.copy(alpha = 0.8f), 5f, top + Offset(-6f, -7f))
                }
            }
        }
        val axle = axle(v)
        val knob = knob(t, v)
        limb(Offset(x - s * 34f, axle.y), axle, MoPropSteel, 6f)
        limb(axle, knob, MoPropSteel, 6f)
        drawCircle(if (v.chance(25, salt = 5)) MoPropYellow else MoPropRed, 7.5f, knob)
    }

    override val ownsTheFloor get() = true
}

/** A desk fan sweeps past him: his flame streams out in the breeze and he shuts his eyes in bliss. */
private object DeskFanBreeze : MoAct("idle_desk_fan_breeze", MoOccasion.Idle, intensity = MoIntensity.Mild) {
    // The fan head stands above his shoulders, so his mitten is never in front of the cage.
    private const val HEAD_Y = FEET - 130f
    private const val CAGE = 28f

    private fun sweep(v: MoVariant) = v.between(5f, 7f, salt = 3)

    /** 1 when the fan points straight at him, -1 when it faces us. It starts on him. */
    private fun facing(t: Float, v: MoVariant) = cos(cycle(t, sweep(v)) * TURN)

    private fun blowing(t: Float, v: MoVariant) = ease((facing(t, v) - 0.3f) / 0.5f)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val s = side(v, 1)
        val b = blowing(t, v)
        val idle = moIdle(t)
        val comingRound = cycle(t, sweep(v)) > 0.5f
        val pose = idle.copy(
            sway = idle.sway * (1f - b) - s * 48f * v.between(0.7f, 1.1f, salt = 4) * b + wave(t, 0.12f) * 7f * b,
            flare = 5f * b,
            tilt = -s * 2f * b,
        ).arms(MoArm(Offset(12f + 10f * b, 30f - 16f * b), bend = 0.2f))
        return if (b > 0.5f) {
            pose.eyes(MoEye(shape = MoEyeShape.Shut)).copy(mouth = MoMouth.Smile(16f, 13f))
        } else {
            pose.eyes(MoEye(gaze = Offset(0.8f * s, 0.5f), lidDrop = 0.1f)).copy(
                brows = if (comingRound) MoBrows(MoBrowKind.Raised, lift = 2f) else MoBrows(),
                mouth = MoMouth.Smile(12f, 8f),
            )
        }
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val s = side(v, 1)
        // Close enough in that the cage, seen round and with its rim, stays inside the frame.
        val x = CENTRE + s * 128f
        val (colour, dark) = v.of(
            listOf(MoPropTeal to MoPropTealDark, MoPropRed to MoPropRedDark, MoPropBlue to MoPropBlueDark, MoPropYellow to MoPropYellowDark),
            salt = 2,
        )
        val toHim = (facing(t, v) + 1f) / 2f
        // Seen nearly edge-on when it points at him and round when it faces us, with the motor behind.
        val rx = CAGE - 15f * toHim
        val head = Offset(x - s * 9f * toHim, HEAD_Y)

        floorShadow(x, 30f, MoPropShadow)
        limb(Offset(x, FEET - 16f), Offset(x, HEAD_Y + 10f), MoPropSteel, 8f)
        chunk(Offset(x, FEET - 10f), 46f, 18f, colour, dark, corner = 7f)
        oval(head + Offset(s * 18f * toHim, 0f), 13f, 16f, dark)
        oval(head, rx, CAGE, MoPropSky.copy(alpha = 0.25f))
        for (blade in 0..2) {
            withTransform({
                scale(rx / CAGE, 1f, head)
                rotate(cycle(t, 0.09f) * 360f + blade * 120f, head)
            }) { oval(head + Offset(0f, -14f), 8f, 14f, MoPropSky.copy(alpha = 0.7f)) }
        }
        drawOval(MoPropSteel, Offset(head.x - rx, head.y - CAGE), Size(rx * 2f, CAGE * 2f), style = Stroke(width = 5f))
        oval(head, 5f * rx / CAGE + 1.5f, 6f, colour)
    }

    override fun DrawScope.foreground(t: Float, v: MoVariant) {
        val b = blowing(t, v)
        if (b <= 0f) return
        val s = side(v, 1)
        val from = Offset(CENTRE + s * 116f, HEAD_Y)
        // The streaks fan out above his brows and below his mouth, so the breeze never crosses his face.
        listOf(198f, 384f, 412f).forEachIndexed { index, level ->
            val u = cycle(t, 0.7f, offset = index / 3f)
            // Each streak sinks to its level as it leaves the fan, but every dash is drawn flat: a
            // slanted one reads as a scratch across him rather than as moving air.
            val y = mix(from.y, level, ease(u * 4f))
            fun at(along: Float) = Offset(from.x - s * 250f * along, y)
            limb(at(u), at(max(u - 0.22f, 0f)), MoPropWhite.copy(alpha = 0.85f * b * sin(u * PI.toFloat())), 6f)
        }
    }

    override val ownsTheFloor get() = true
}

/** He measures his finished little picture with the brush, dabs at it, and leans back, pleased. */
private object ArtistsSquint : MoAct("idle_artists_squint", MoOccasion.Idle, intensity = MoIntensity.Mild) {
    private const val DAB_EVERY = 0.7f
    private const val FIRST_DAB = 2.4f

    private enum class Motif { Sun, Flower, Heart, Mountain, Flame }

    private fun motif(v: MoVariant) = Motif.entries[v.pick(Motif.entries.size, salt = 1)]

    /** The paint on the brush is the paint of the picture. */
    private fun paint(v: MoVariant): Color = when (motif(v)) {
        Motif.Sun -> v.of(listOf(MoPropOrange, MoPropGold), salt = 2)
        Motif.Flower -> v.of(listOf(MoPropPink, MoPropPurple, MoPropRed), salt = 2)
        Motif.Heart -> v.of(listOf(MoPropRed, MoPropPink), salt = 2)
        Motif.Mountain -> v.of(listOf(MoPropBlue, MoPropPurple, MoPropTeal), salt = 2)
        Motif.Flame -> MoFlameOuter
    }

    private fun dabs(v: MoVariant) = if (v.chance(40, salt = 4)) 3 else 2
    private fun dabbingEnds(v: MoVariant) = FIRST_DAB + dabs(v) * DAB_EVERY + 0.2f
    private fun loop(v: MoVariant) = dabbingEnds(v) + 2f

    /** How far each beat has taken over: dabbing from measuring, admiring from dabbing, measuring again. */
    private class Beats(c: Float, dabbingEnds: Float, loop: Float) {
        val dabbing = ramp(c, 1.8f, 2.2f)
        val admiring = ramp(c, dabbingEnds - 0.2f, dabbingEnds + 0.2f)
        val again = ramp(c, loop - 0.4f, loop)

        fun blend(measure: Float, dab: Float, admire: Float) = mix(mix(mix(measure, dab, dabbing), admire, admiring), measure, again)
    }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val s = side(v, 3)
        val loop = loop(v)
        val c = cycle(t, loop) * loop
        val beats = Beats(c, dabbingEnds(v), loop)
        val dab = (0 until dabs(v)).sumOf { bump(c - FIRST_DAB - it * DAB_EVERY, 0.3f).toDouble() }.toFloat()

        val measure = MoArm(Offset(26f, FEET - 154f - MoBodies.of(body).shoulderY), bend = 0.05f)
        var near = mix(measure, MoArm(Offset(24f + 4f * dab, 34f - 4f * dab), bend = 0.3f), beats.dabbing)
        near = mix(near, MoArm(Offset(20f, 20f)), beats.admiring)
        near = mix(near, measure, beats.again)
        val far = mix(MoArm.Down, MoArm.Hip, beats.admiring * (1f - beats.again))

        val base = moIdle(t).copy(
            tilt = s * beats.blend(1f, 0f, -5f),
            offset = Offset(-s * beats.blend(0f, 0f, 6f), 0f),
        ).withArms(s, near, far)
        val sighting = MoEye(gaze = Offset(0.9f * s, -0.2f))
        return when {
            c < 1.9f || c >= loop - 0.2f -> base.copy(
                // The eye on the brush side sights along it; the other one is shut.
                leftEye = if (s > 0f) MoEye(shape = MoEyeShape.Shut) else sighting,
                rightEye = if (s > 0f) sighting else MoEye(shape = MoEyeShape.Shut),
                mouth = MoMouth.TongueOut,
                blinks = false,
            )
            c < dabbingEnds(v) -> base.eyes(MoEye(gaze = Offset(0.9f * s, 0.3f), lidDrop = 0.15f)).copy(mouth = MoMouth.Flat(8f))
            else -> base.copy(brows = MoBrows(MoBrowKind.Raised, lift = 4f), mouth = MoMouth.Smile(17f, 14f)).eyes(
                if (c > loop - 1.5f && c < loop - 0.3f) MoEye(shape = MoEyeShape.Happy) else MoEye(gaze = Offset(0.8f * s, 0.2f)),
            )
        }
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val s = side(v, 3)
        val x = CENTRE + s * 128f
        val top = Offset(x, FEET - 144f)
        floorShadow(x, 32f, MoPropShadow)
        limb(Offset(x + s * 6f, FEET - 6f), top, MoPropWoodDark, 6f)
        limb(Offset(x - 20f, FEET - 2f), top, MoPropWood, 6f)
        limb(Offset(x + 20f, FEET - 2f), top, MoPropWood, 6f)
        chunk(Offset(x, FEET - 102f), 52f, 66f, MoPropCream, MoPropSand, corner = 4f)
        block(Offset(x, FEET - 64f), 56f, 7f, MoPropWoodDark, corner = 3f)

        val m = Offset(x, FEET - 105f)
        val paint = paint(v)
        when (motif(v)) {
            Motif.Sun -> {
                for (ray in 0..5) {
                    val out = Offset(cos(ray * TURN / 6f), sin(ray * TURN / 6f))
                    limb(m + out * 15f, m + out * 21f, paint, 3.5f)
                }
                drawCircle(paint, 10f, m)
            }
            Motif.Flower -> {
                limb(m + Offset(0f, 4f), m + Offset(0f, 24f), MoPropGreen, 3.5f)
                oval(m + Offset(6f, 16f), 6f, 3f, MoPropGreen)
                for (petal in 0..4) drawCircle(paint, 5.5f, m + Offset(0f, -5f) + Offset(cos(petal * TURN / 5f), sin(petal * TURN / 5f)) * 7.5f)
                drawCircle(MoPropYellow, 4.5f, m + Offset(0f, -5f))
            }
            Motif.Heart -> heart(m, 16f, paint)
            Motif.Mountain -> {
                shape(paint, m.x - 18f to m.y + 15f, m.x + 18f to m.y + 15f, m.x to m.y - 14f)
                shape(MoPropSnow, m.x - 4f to m.y - 7f, m.x + 4f to m.y - 7f, m.x to m.y - 13f)
            }
            Motif.Flame -> {
                flameTongue(m + Offset(0f, 16f), 11f, paint, MoFlameBelly)
                drawCircle(MoPropInk, 1.7f, m + Offset(-3f, 6f))
                drawCircle(MoPropInk, 1.7f, m + Offset(3f, 6f))
            }
        }
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val s = side(v, 3)
        val loop = loop(v)
        val hand = if (s > 0f) rig.rightHand else rig.leftHand
        // Upright to measure, leaning onto the canvas to dab, hanging loose while he admires.
        val along = Offset(0f, -1f).turned(s * Beats(cycle(t, loop) * loop, dabbingEnds(v), loop).blend(0f, 26f, 115f))
        limb(hand - along * 9f, hand + along * 26f, MoPropWood, 5f)
        limb(hand + along * 26f, hand + along * 31f, MoPropSilver, 6.5f)
        limb(hand + along * 34f, hand + along * 40f, paint(v), 9f)
    }

    override val ownsTheFloor get() = true
}

/** He lowers the last two cards onto a card house, holding his breath, and freezes when it trembles. */
private object CardHouse : MoAct("idle_card_house", MoOccasion.Idle, intensity = MoIntensity.Mild) {
    private const val HALF = 13f
    private const val RISE = 36f
    private const val STOREY = 44f
    private const val GROUND = FEET - 2f

    /** How far a card leans off upright, which is what puts its top on the apex and its foot [HALF] out. */
    private const val LEAN = 20f

    private fun storeys(v: MoVariant) = if (v.chance(40, salt = 3)) 3 else 2
    private fun twice(v: MoVariant) = v.chance(35, salt = 4)
    private fun loop(v: MoVariant) = if (twice(v)) 8f else 6f
    private fun breaths(v: MoVariant) = if (twice(v)) listOf(2.6f, 5.4f) else listOf(2.6f)
    // Far enough out that the widest card clears his base, and still inside the frame.
    private fun houseX(v: MoVariant) = CENTRE + side(v, 2) * 118f
    private fun back(v: MoVariant) = v.of(listOf(MoPropRed, MoPropBlue, MoPropTeal), salt = 1)

    /** Seconds since he last had to breathe, or a negative number before the first time. */
    private fun sinceBreath(c: Float, v: MoVariant) = breaths(v).map { c - it }.filter { it >= 0f }.minOrNull() ?: -1f

    private fun tremble(c: Float, v: MoVariant): Float {
        val x = sinceBreath(c, v)
        if (x < 0f) return 0f
        // The second scare is a smaller one: he was more careful.
        val size = if (c >= breaths(v).last() && twice(v)) 0.6f else 1f
        return size * ramp(x, 0f, 0.08f) * exp(-2.5f * x) * (1f - ramp(x, 0.6f, 1.4f))
    }

    /** How far the feet of the last pair hang above where they should land. */
    private fun hover(c: Float, v: MoVariant): Float = if (twice(v)) {
        10f - 5.5f * ramp(c, 0.3f, 2.5f) - 2f * ramp(c, 4.2f, 5.3f) + 7.5f * ramp(c, 6.6f, 7.8f)
    } else {
        10f - 7f * ramp(c, 0.3f, 2.5f) + 7f * ramp(c, 4.4f, 5.8f)
    }

    /** One card, broadside: drawn as a line it would read as a rung, and the house as a stepladder. */
    private fun DrawScope.card(centre: Offset, degrees: Float, colour: Color) {
        withTransform({ rotate(degrees, centre) }) { block(centre, 21f, 40f, colour, corner = 3f) }
    }

    private fun DrawScope.pair(apex: Offset, back: Color, shiver: Float, t: Float, index: Int) {
        val wobble = shiver * wave(t, 0.07f + index * 0.01f)
        card(apex + Offset(-HALF / 2f, RISE / 2f), LEAN + wobble, MoPropWhite)
        card(apex + Offset(HALF / 2f, RISE / 2f), -LEAN - wobble, back)
    }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val s = side(v, 2)
        val loop = loop(v)
        val c = cycle(t, loop) * loop
        val since = sinceBreath(c, v)
        val startled = since > 0.05f && since < 1f
        val held = 1f - breaths(v).sumOf { (ramp(c, it, it + 0.15f) - ramp(c, it + 1f, it + 1.9f)).toDouble() }.toFloat()
        val idle = moIdle(t)
        val base = idle.copy(
            sway = idle.sway * (0.15f + 0.85f * (1f - held)) + s * 6f * bump(since, 0.5f),
            squash = 0.03f * held - 0.06f * bump(since, 0.45f),
        )

        val landing = Offset(houseX(v), GROUND - (storeys(v) - 1) * STOREY)
        val hand = base.toBody(landing + Offset(0f, -hover(c, v) - RISE - 12f))
        val shiver = tremble(c, v)
        val posed = base.withArms(
            s,
            near = MoArm(reachTo(MoBodies.of(body), s, hand), bend = 0.35f),
            far = MoArm(Offset(24f + 3f * shiver * wave(t, 0.09f), 10f), bend = 0.2f),
        )
        val atTheCards = Offset(0.8f * s, if (storeys(v) == 3) 0.1f else 0.5f)
        return when {
            startled -> posed.eyes(MoEye(iris = 0.45f, scale = 1.05f, gaze = atTheCards)).copy(
                brows = MoBrows(MoBrowKind.Raised, lift = 6f),
                mouth = MoMouth.Oh(5f),
                blinks = false,
            )
            since >= 1f && since < 1.8f -> posed.eyes(MoEye(gaze = atTheCards)).copy(mouth = MoMouth.Flat(7f), blinks = false)
            c > loop - 1.6f && c < loop - 0.2f -> posed.eyes(MoEye(gaze = atTheCards)).copy(mouth = MoMouth.Smile(11f, 7f))
            else -> posed.eyes(MoEye(gaze = atTheCards)).copy(brows = MoBrows(MoBrowKind.Flat, lift = 1f), mouth = MoMouth.TongueOut, blinks = false)
        }
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val x = houseX(v)
        val loop = loop(v)
        val shiver = 2f * tremble(cycle(t, loop) * loop, v)
        val back = back(v)
        floorShadow(x, 44f, MoPropShadow)
        var index = 0
        for (storey in 0 until storeys(v) - 1) {
            // Two frames on the ground and one above, whatever the height, so the house stays a pyramid.
            val frames = if (storey == 0) 2 else 1
            val apexY = GROUND - storey * STOREY - RISE
            for (frame in 0 until frames) {
                pair(Offset(x + (frame - (frames - 1) / 2f) * 2f * HALF, apexY), back, shiver, t, index++)
            }
            // The card lying flat on top, which is what the next storey stands on.
            val slip = shiver * wave(t, 0.07f + index++ * 0.01f)
            block(Offset(x + slip, apexY - 6f), (frames - 1) * 2f * HALF + 36f, 10f, if (storey % 2 == 0) back else MoPropWhite, corner = 3f)
        }
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val loop = loop(v)
        val hand = if (side(v, 2) > 0f) rig.rightHand else rig.leftHand
        pair(hand + Offset(0f, 12f), back(v), 5f * tremble(cycle(t, loop) * loop, v), t, index = 9)
    }

    override val ownsTheFloor get() = true
}

/** He throws a boomerang, waits for it where it left, and is startled when it lands in his other mitten. */
private object BoomerangWrongSide : MoAct("idle_boomerang_wrong_side", MoOccasion.Idle, setOf(MoBodyKind.Tall)) {
    private const val HALF = 3.4f
    private const val FLIGHT = 2.2f

    // He lets go with the arm flung out here and catches with the other arm held here, so the flight
    // joins both. The waiting arm is high and the unaware one hangs low, so the wrong side reads.
    private val release = MoArm(Offset(34f, -40f), bend = 0.2f)
    private val unaware = MoArm(Offset(18f, 40f), bend = 0.3f)
    private val ready = MoArm(Offset(40f, -54f), bend = 0.2f)

    /** How far into the current throw he is, and the side it was thrown from. The loop starts mid-flight. */
    private fun beat(t: Float, v: MoVariant): Pair<Float, Float> {
        val c = cycle(t, 2f * HALF, offset = 0.35f / (2f * HALF)) * 2f * HALF
        val first = side(v, 2)
        return if (c < HALF) c to first else c - HALF to -first
    }

    private fun flying(h: Float, d: Float, v: MoVariant): Offset {
        val u = (h / FLIGHT).coerceIn(0f, 1f)
        val from = shoulder(MoBodies.Tall, d) + Offset(d * release.reach.x, release.reach.y)
        val to = shoulder(MoBodies.Tall, -d) + Offset(-d * unaware.reach.x, unaware.reach.y)
        val out = mix(from.x - CENTRE, CENTRE - to.x, u)
        return Offset(CENTRE + out * cos(u * PI.toFloat()), mix(from.y, to.y, u) - v.between(140f, 180f, salt = 3) * sin(u * PI.toFloat()))
    }

    private fun DrawScope.boomerang(at: Offset, degrees: Float, v: MoVariant) {
        val (colour, stripe) = v.of(listOf(MoPropTeal to MoPropYellow, MoPropPink to MoPropWhite, MoPropYellow to MoPropRed), salt = 1)
        withTransform({ rotate(degrees, at) }) {
            val elbow = at + Offset(0f, 13f)
            val left = at + Offset(-24f, -11f)
            limb(elbow, left, colour, 14f)
            limb(elbow, at + Offset(24f, -11f), colour, 14f)
            limb(mix(elbow, left, 0.35f), mix(elbow, left, 0.9f), stripe, 5f)
        }
    }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val (h, d) = beat(t, v)
        val thrower = mix(release, ready, ramp(h, 0f, 0.4f))
        var catcher = mix(ready, unaware, ramp(h, 0f, 0.5f))
        // The catch knocks his mitten down a little; then he winds up and flings it from right there.
        catcher = catcher.copy(reach = catcher.reach + Offset(0f, 7f * bump(h - FLIGHT, 0.3f)))
        catcher = mix(catcher, MoArm(Offset(20f, -40f), bend = 0.7f), ramp(h, 2.8f, 3.15f))
        catcher = mix(catcher, release, ramp(h, 3.2f, HALF))
        val tap = MoLeg(lift = 3f * pulse(t, 0.5f) * ramp(h, 0.3f, 0.6f) * (1f - ramp(h, 1.9f, FLIGHT)))

        val base = moIdle(t).copy(
            legs = if (d > 0f) MoLegs(left = tap) else MoLegs(right = tap),
            // Upright at the throw and at the catch, so his mittens are where the flight starts and ends.
            tilt = d * (2f * bump(h, FLIGHT) + 3f * bump(h - 2.8f, HALF - 2.8f)),
            offset = Offset(0f, -10f * bump(h - FLIGHT, 0.4f)),
        ).withArms(d, near = thrower, far = catcher)
        return when {
            h < FLIGHT -> base.eyes(MoEye(gaze = Offset(0.9f * d, -0.3f))).copy(
                brows = MoBrows(MoBrowKind.Raised, lift = 5f + 2f * ramp(h, 1f, FLIGHT)),
                mouth = MoMouth.Smile(12f, 8f),
            )
            h < 2.8f -> base.eyes(MoEye(iris = 0.45f, gaze = Offset(-0.9f * d, 0.1f))).copy(
                brows = MoBrows(MoBrowKind.Raised, lift = 8f),
                mouth = MoMouth.Oh(8f),
                blinks = false,
            )
            else -> base.eyes(MoEye(gaze = Offset(-0.9f * d, -0.2f))).copy(mouth = MoMouth.Grin(open = 0.4f, tongue = false))
        }
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val (h, d) = beat(t, v)
        if (h >= FLIGHT) {
            boomerang(if (d > 0f) rig.leftHand else rig.rightHand, d * 30f, v)
            grippingMitten(rig, -d)
        }
    }

    /** The whole flight passes in front of him: a boomerang lost behind his flame is no joke at all. */
    override fun DrawScope.foreground(t: Float, v: MoVariant) {
        val (h, d) = beat(t, v)
        if (h < FLIGHT) boomerang(flying(h, d, v), cycle(t, 0.4f) * 360f, v)
    }
}

internal val idleActs3: List<MoAct> = listOf(
    JackInTheBox,
    DeskFanBreeze,
    ArtistsSquint,
    CardHouse,
    BoomerangWrongSide,
)
