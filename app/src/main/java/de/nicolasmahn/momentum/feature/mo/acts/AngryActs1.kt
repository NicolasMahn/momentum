package de.nicolasmahn.momentum.feature.mo.acts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import de.nicolasmahn.momentum.core.ui.theme.MoPropCharcoal
import de.nicolasmahn.momentum.core.ui.theme.MoPropCream
import de.nicolasmahn.momentum.core.ui.theme.MoPropGold
import de.nicolasmahn.momentum.core.ui.theme.MoPropGoldDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropGreyDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropNavy
import de.nicolasmahn.momentum.core.ui.theme.MoPropPurple
import de.nicolasmahn.momentum.core.ui.theme.MoPropShadow
import de.nicolasmahn.momentum.core.ui.theme.MoPropSilver
import de.nicolasmahn.momentum.core.ui.theme.MoPropSteel
import de.nicolasmahn.momentum.core.ui.theme.MoPropTeal
import de.nicolasmahn.momentum.core.ui.theme.MoPropTealDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropWhite
import de.nicolasmahn.momentum.core.ui.theme.MoPropWood
import de.nicolasmahn.momentum.core.ui.theme.MoPropWoodDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropYellow
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
import de.nicolasmahn.momentum.feature.mo.MoPalettes
import de.nicolasmahn.momentum.feature.mo.MoPose
import de.nicolasmahn.momentum.feature.mo.MoRig
import de.nicolasmahn.momentum.feature.mo.angerMark
import de.nicolasmahn.momentum.feature.mo.block
import de.nicolasmahn.momentum.feature.mo.chunk
import de.nicolasmahn.momentum.feature.mo.curve
import de.nicolasmahn.momentum.feature.mo.cycle
import de.nicolasmahn.momentum.feature.mo.ease
import de.nicolasmahn.momentum.feature.mo.floorShadow
import de.nicolasmahn.momentum.feature.mo.limb
import de.nicolasmahn.momentum.feature.mo.mitten
import de.nicolasmahn.momentum.feature.mo.moIdle
import de.nicolasmahn.momentum.feature.mo.polyline
import de.nicolasmahn.momentum.feature.mo.puff
import de.nicolasmahn.momentum.feature.mo.sparkle
import de.nicolasmahn.momentum.feature.mo.twitch
import de.nicolasmahn.momentum.feature.mo.wave
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/*
 * Anger that is not the tantrum yet: sulking, drumming, ringing for service. Each act is a little
 * story told in stretches of a loop, so most of them read their moment from span() and blend
 * between a few named arms and faces. Only the one that really boils over wears Rage.
 */

// The radius the kit draws a mitten with, for whatever has to touch one exactly.
private const val MITTEN = 12.5f

/** How far [at] has got from [from] to [to], as 0 to 1. A stretch of no length is over at once. */
private fun span(at: Float, from: Float, to: Float): Float =
    if (to > from) ((at - from) / (to - from)).coerceIn(0f, 1f) else if (at >= to) 1f else 0f

private fun mix(from: Float, to: Float, amount: Float): Float = from + (to - from) * amount

private fun mix(from: Offset, to: Offset, amount: Float): Offset = from + (to - from) * amount

private fun mix(from: MoArm, to: MoArm, amount: Float): MoArm =
    MoArm(mix(from.reach, to.reach, amount), mix(from.bend, to.bend, amount))

/** A hit that lands fast and lifts slowly: 0 raised, 1 landed, with the landing [lands] of the way through the [beat]. */
private fun strike(beat: Float, lands: Float): Float =
    if (beat < lands) ease(span(beat, 0f, lands)) else 1f - ease(span(beat, lands, 1f))

/** Where a point of his outline is once the flame has swayed and flared; the rig knows that only for the tip. */
private fun MoBody.onFlame(point: Offset, pose: MoPose): Offset {
    val height = ((pivot - point.y) / (pivot - top).coerceAtLeast(1f)).coerceIn(0f, 1.2f)
    return Offset(point.x + pose.sway * height * height, point.y - pose.flare * height * height)
}

/** The fuming arcs, where the tantrum wears them; [side] -1 moves them to the left of his head. */
internal fun DrawScope.fumingMark(rig: MoRig, size: Float, side: Float = 1f) {
    // The low flame's gloss runs straight through the usual mark on his left temple.
    val at = if (rig.body.kind == MoBodyKind.Low && side < 0f) {
        Offset(CENTRE - 27f, rig.body.top + 82f)
    } else {
        Offset(CENTRE + side * 74f, rig.body.top + 96f)
    }
    angerMark(at, size)
}

/** A huff leaving his mouth to one [side], [gone] of the way to nothing. */
private fun DrawScope.huff(rig: MoRig, side: Float, gone: Float) {
    if (gone <= 0f || gone >= 1f) return
    puff(rig.mouth + Offset(side * (42f + 62f * gone), 5f - 22f * gone), 7f + 7f * gone, MoPropWhite.copy(alpha = 1f - gone * gone))
}

/** The upper half of an oval standing on [base]: a bell. */
private fun DrawScope.dome(base: Offset, rx: Float, ry: Float, colour: Color) {
    val half = Path().apply {
        arcTo(Rect(base.x - rx, base.y - ry, base.x + rx, base.y + ry), 180f, 180f, forceMoveTo = true)
        close()
    }
    drawPath(half, colour)
}

/** Long overdue: he holds it in like a pressure cooker, swelling and trembling, until steam hisses out of his shoulders. */
private object HoldingItIn : MoAct("angry_holding_it_in", MoOccasion.Angry, intensity = MoIntensity.Strong) {
    // He holds until BLOWS of the way through a turn, the steam is out by SAGS, and the rest is the sag.
    private const val BLOWS = 0.74f
    private const val SAGS = 0.88f

    // Starting over half way in, so the still frame already shows him swollen and leaking.
    private fun turn(t: Float, v: MoVariant): Float = cycle(t, v.between(3.6f, 4.8f, salt = 1), offset = 0.55f)

    /** How swollen he is: it builds all through the hold and is gone the moment the steam is out. */
    private fun pressure(c: Float): Float = if (c < BLOWS) ease(c / BLOWS) else 1f - ease(span(c, BLOWS, BLOWS + 0.05f))

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val c = turn(t, v)
        val holding = c < BLOWS
        val pressure = pressure(c)
        val blown = span(c, BLOWS, SAGS)
        val sagged = ease(span(c, SAGS, 1f))
        val burst = ease(span(blown, 0f, 0.2f)) * (1f - ease(span(c, BLOWS + 0.03f, 1f)))

        val clenched = MoArm(Offset(12f + wave(t, 0.09f) * 1.5f * pressure, 28f), bend = 0.05f)
        val flung = MoArm(Offset(30f, 8f), bend = 0.3f)
        val arm = if (holding) {
            mix(MoArm.Down, clenched, pressure)
        } else {
            mix(mix(clenched, flung, ease(span(blown, 0f, 0.25f))), MoArm.Down, sagged)
        }
        val eye = MoEye(
            lid = if (holding) mix(0.3f, 0.7f, ease(span(c, 0f, 0.1f))) else 0.3f * sagged,
            iris = if (holding) 0.6f - 0.2f * pressure else mix(0.4f, 0.6f, span(c, BLOWS, 1f)),
            scale = 1f + 0.12f * pressure,
        )
        val idle = moIdle(t, energy = 1.2f)
        return idle.copy(
            palette = MoPalettes.Rage,
            leftEye = eye,
            rightEye = eye,
            brows = when {
                holding -> MoBrows(MoBrowKind.Angry)
                c < SAGS -> MoBrows(MoBrowKind.Raised, lift = 4f)
                else -> MoBrows(MoBrowKind.Flat)
            },
            mouth = if (holding) MoMouth.Pout else MoMouth.Oh(size = mix(8f, 5f, sagged)),
            leftArm = arm,
            rightArm = arm,
            flare = 6f + 4f * pressure + 26f * burst,
            // Wider and lower while he holds, then up past himself as it all leaves, and down into the sag.
            squash = idle.squash + if (holding) -0.02f - 0.1f * pressure else mix(mix(-0.12f, 0.05f, ease(blown)), -0.02f, sagged),
            shake = wave(t, 0.07f) * 3.5f * pressure,
            blinks = false,
        )
    }

    // The tall body vents through the tongue on each shoulder. The low one has none, so it vents where they would be.
    private fun vent(body: MoBody, side: Float): Offset = when (body.kind) {
        MoBodyKind.Tall -> if (side < 0f) Offset(41f, 158f) else Offset(185f, 148f)
        else -> Offset(CENTRE + side * 82f, 234f)
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val c = turn(t, v)
        val pressure = pressure(c)
        val now = pose(t, v, rig.body.kind)
        val puffs = 2 + v.pick(2, salt = 2)
        val first = if (v.chance(50, salt = 3)) 1f else -1f

        fumingMark(rig, 0.7f + 0.3f * pressure)
        for (side in listOf(-1f, 1f)) {
            val vent = rig.body.onFlame(vent(rig.body, side), now)
            if (pressure > 0f) {
                // A thin leak while he holds, more of it the fuller he is.
                val rise = cycle(t, 0.9f, offset = 0.25f + side * 0.2f)
                puff(vent + Offset(side * 10f * rise, -6f - 26f * rise), 6f + 4f * pressure, MoPropWhite.copy(alpha = 0.85f * pressure * ease(rise / 0.12f) * (1f - rise)))
            }
            for (index in 0 until puffs) {
                val from = BLOWS + (if (side == first) 0f else 0.05f) + index * 0.04f
                val out = span(c, from, from + 0.12f)
                if (out <= 0f || out >= 1f) continue
                val reach = 1f - (1f - out) * (1f - out)
                puff(vent + Offset(side * 58f * reach, -6f - 22f * out), 8f + 7f * out, MoPropWhite.copy(alpha = ease(out / 0.1f) * (1f - out * out)))
            }
        }
    }

    override val sparkles get() = false
}

/** He means to stand there with his arms crossed, but they are too short: the mittens slip apart, and he folds them again, harder. */
private object ArmsTooShort : MoAct("angry_arms_too_short", MoOccasion.Angry, intensity = MoIntensity.Mild) {
    private const val SLIP = 0.25f
    private const val GLARE = 0.8f
    private const val HUFF = 0.4f
    private const val REFOLD = 0.5f

    private fun hold(v: MoVariant): Float = v.between(1f, 2f, salt = 2)

    private fun slipSide(v: MoVariant): Float = if (v.chance(50, salt = 1)) 1f else -1f

    /** Seconds into the loop: holding, then the slip, the glare, the huff and the refold. */
    private fun at(t: Float, v: MoVariant): Float {
        val period = hold(v) + SLIP + GLARE + HUFF + REFOLD
        return cycle(t, period) * period
    }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val at = at(t, v)
        val side = slipSide(v)
        val slipAt = hold(v)
        val glareAt = slipAt + SLIP
        val huffAt = glareAt + GLARE
        val refoldAt = huffAt + HUFF

        val folded = MoArm(Offset(-88f, 28f), bend = 0.35f)
        val sprung = MoArm(Offset(10f, 20f), bend = 0.5f)
        val dropped = MoArm(Offset(-40f, 30f), bend = 0.3f)

        val strain = if (at < slipAt) ease(span(at, 0f, slipAt)) else 0f
        val tremble = wave(t, 0.08f) * strain
        // The slipping arm shoots a little past where it comes to rest; the other one only notices a moment later.
        val slip = span(at, slipAt, glareAt)
        val sprungOut = ease(span(slip, 0f, 0.6f)) + 0.09f * sin(PI.toFloat() * span(slip, 0.6f, 1f))
        val droppedOut = ease(span(at, slipAt + 0.08f, glareAt + 0.08f))
        val back = ease(span(at, refoldAt, refoldAt + 0.3f))
        val harder = v.of(listOf(4f, 8f), salt = 3) * back * (1f - ease(span(at, refoldAt + 0.3f, refoldAt + REFOLD)))
        val across = Offset(-harder, 0f)
        val flung = Offset(16f, -24f) * sin(PI.toFloat() * span(at, slipAt + 0.05f, glareAt + 0.2f))
        val slipping = mix(folded, sprung, sprungOut * (1f - back)).let { it.copy(reach = it.reach + across + flung + Offset(tremble * 2f, 0f)) }
        val other = mix(folded, dropped, droppedOut * (1f - back)).let { it.copy(reach = it.reach + across - Offset(tremble * 2f, 0f)) }

        val look = ease(span(at, slipAt + 0.1f, glareAt + 0.1f)) * (1f - ease(span(at, refoldAt, refoldAt + 0.35f)))
        val gaze = mix(Offset(0f, 0.2f), Offset(side * 0.9f, 0.8f), look)
        val startled = at >= slipAt && at < glareAt
        val eye = when {
            startled -> MoEye(iris = 0.5f, gaze = gaze)
            at >= glareAt && at < refoldAt -> MoEye(lid = 1f, iris = 0.6f, gaze = gaze)
            else -> MoEye(lid = 0.8f, iris = 0.6f, gaze = gaze)
        }
        val idle = moIdle(t, energy = 1.1f)
        return idle.copy(
            leftEye = eye,
            rightEye = eye,
            brows = if (startled) MoBrows(MoBrowKind.Raised) else MoBrows(MoBrowKind.Angry),
            mouth = when {
                at < slipAt || at >= refoldAt -> MoMouth.Flat(width = 12f)
                startled -> MoMouth.Oh(size = 7f)
                at < huffAt -> MoMouth.Gritted
                else -> MoMouth.Pout
            },
            leftArm = if (side < 0f) slipping else other,
            rightArm = if (side < 0f) other else slipping,
            armsInFront = true,
            flare = 4f,
            squash = idle.squash - 0.06f * sin(PI.toFloat() * span(at, huffAt, refoldAt)),
            tilt = side * 3f * look,
            shake = tremble,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val huffAt = hold(v) + SLIP + GLARE
        fumingMark(rig, 0.7f)
        huff(rig, slipSide(v), span(at(t, v), huffAt + 0.1f, huffAt + 0.6f))
    }
}

/** He is not speaking to that task: turned away, chin up, eyes shut, with one eye stealing a look at whether anyone noticed. */
private object ColdShoulder : MoAct("angry_cold_shoulder", MoOccasion.Angry, intensity = MoIntensity.Mild) {
    private const val HUFFS = 0.66f

    private fun away(v: MoVariant): Float = if (v.chance(50, salt = 1)) 1f else -1f

    private fun period(v: MoVariant): Float = v.between(4.5f, 6f, salt = 2)

    // Leaning turns him about the point between his feet, which alone would lift one foot and sink the other.
    private fun planted(shape: MoBody, tilt: Float): MoLegs {
        val turned = sin(tilt * PI.toFloat() / 180f)
        return MoLegs(MoLeg(lift = (shape.leftFootX - CENTRE) * turned), MoLeg(lift = (shape.rightFootX - CENTRE) * turned))
    }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val away = away(v)
        val period = period(v)
        val c = cycle(t, period)
        val huffed = span(c * period, HUFFS * period, HUFFS * period + 0.35f)
        val lean = ease(span(c, HUFFS, 0.78f)) * (1f - ease(span(c, 0.8f, 1f)))
        val tilt = away * (9f + 4f * lean)

        val peek = if (v.chance(35, salt = 3)) {
            sin(PI.toFloat() * span(c, 0.5f, 0.56f)) + sin(PI.toFloat() * span(c, 0.58f, 0.64f))
        } else {
            sin(PI.toFloat() * span(c, 0.5f, 0.64f))
        }
        val shut = MoEye(shape = MoEyeShape.Shut)
        val peeking = if (peek > 0.02f) {
            MoEye(open = 0.4f * peek, lid = 0.6f, gaze = mix(Offset(-away * 0.9f, 0f), Offset(0f, 0.1f), ease(span(c, 0.52f, 0.62f))))
        } else {
            shut
        }
        val idle = moIdle(t)
        return idle.copy(
            // Only the eye on the side he turned from can see anything.
            leftEye = if (away > 0f) peeking else shut,
            rightEye = if (away > 0f) shut else peeking,
            brows = MoBrows(MoBrowKind.Angry, lift = 4f),
            mouth = MoMouth.Pout,
            leftArm = MoArm(Offset(0f, 40f), bend = 0f),
            rightArm = MoArm(Offset(0f, 40f), bend = 0f),
            legs = planted(MoBodies.of(body), tilt),
            // His tip flicks back the way he will not look, like the tail of a cross cat.
            sway = idle.sway - away * (14f * twitch(t, 1.7f, beat = 0.45f) + 4f * wave(t, 0.6f)),
            flare = 4f,
            // Chin up, and a short sharp dip with the huff.
            squash = idle.squash + 0.025f - 0.05f * sin(PI.toFloat() * huffed),
            tilt = tilt,
            offset = Offset(away * 6f, 0f),
            blinks = false,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val period = period(v)
        huff(rig, away(v), span(cycle(t, period) * period, HUFFS * period, HUFFS * period + 0.6f))
    }
}

/** The oldest mime for being late: one arm across his belly, the other mitten tapping its wrist, then a look at you, and faster. */
private object TappingTheWrist : MoAct("angry_tapping_the_wrist", MoOccasion.Angry, intensity = MoIntensity.Mild) {
    private const val SLOW = 0.26f
    private const val FAST = 0.19f
    private const val WAIT = 0.9f
    private const val LANDS = 0.35f

    private const val RESTING_BEND = 0.3f

    // They meet low in the middle, so each arm only has half his belly to cross.
    private val resting = MoArm(Offset(-88f, 44f), bend = RESTING_BEND)
    private val lift = Offset(30f, -8f)

    /** Where a watch sits on an arm the kit bends from [shoulder] to [hand]: on the forearm, short of the mitten. */
    private fun wrist(shoulder: Offset, hand: Offset, side: Float): Offset {
        val length = (hand - shoulder).getDistance()
        val elbow = (shoulder + hand) / 2f + Offset(side * RESTING_BEND * length * 0.5f, RESTING_BEND * length * 0.12f)
        return mix(elbow, hand, 0.74f)
    }

    /** The reach that lands the tapping mitten on top of the resting forearm, just past the watch. Both arms mirror alike. */
    private fun landed(shape: MoBody): Offset {
        val shoulder = Offset(CENTRE - shape.shoulderHalf, shape.shoulderY)
        val wrist = wrist(shoulder, shoulder + Offset(-resting.reach.x, resting.reach.y), side = -1f)
        return wrist + Offset(16f, -15f) - Offset(CENTRE + shape.shoulderHalf, shape.shoulderY)
    }

    /** 1 when his right mitten does the tapping, -1 when his left one does. */
    private fun tapper(v: MoVariant): Float = if (v.chance(50, salt = 1)) 1f else -1f

    private class Beat(val down: Float, val lookingUp: Float)

    private fun beat(t: Float, v: MoVariant): Beat {
        val taps = 2 + v.pick(3, salt = 4)
        val second = taps * SLOW + WAIT
        val period = second + taps * FAST + WAIT
        // Starting on a landed tap, so the still frame shows what he is doing.
        val at = cycle(t + LANDS * SLOW, period) * period
        val down = when {
            at < taps * SLOW -> strike((at / SLOW).mod(1f), LANDS)
            at >= second && at < second + taps * FAST -> strike(((at - second) / FAST).mod(1f), LANDS)
            else -> 0f
        }
        return Beat(down, glance(at - taps * SLOW) + glance(at - second - taps * FAST))
    }

    /** Up to the viewer and back down, over one wait that began [since] seconds ago. */
    private fun glance(since: Float): Float = ease(span(since, 0f, 0.15f)) * (1f - ease(span(since, WAIT - 0.15f, WAIT)))

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val beat = beat(t, v)
        val tapper = tapper(v)
        val landed = landed(MoBodies.of(body))
        val tapping = MoArm(mix(landed + lift, landed, beat.down), bend = 0.4f)
        val eye = MoEye(
            lid = 0.6f,
            iris = 0.6f,
            lidDrop = 0.2f * beat.lookingUp,
            gaze = mix(Offset(-tapper * 0.3f, 0.9f), Offset.Zero, beat.lookingUp),
        )
        return moIdle(t, energy = 1.1f).copy(
            leftEye = eye,
            rightEye = eye,
            brows = MoBrows(MoBrowKind.Angry, skew = 7f * beat.lookingUp),
            mouth = MoMouth.Flat(width = 12f),
            leftArm = if (tapper > 0f) resting else tapping,
            rightArm = if (tapper > 0f) tapping else resting,
            armsInFront = true,
            flare = 4f,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val tapper = tapper(v)
        val tapHand = if (tapper > 0f) rig.rightHand else rig.leftHand
        val wristHand = if (tapper > 0f) rig.leftHand else rig.rightHand

        if (v.chance(50, salt = 2)) {
            val shoulder = Offset(CENTRE - tapper * rig.body.shoulderHalf, rig.body.shoulderY)
            val watch = wrist(shoulder, wristHand, side = -tapper)
            val forearm = wristHand - watch
            val across = Offset(-forearm.y, forearm.x) / forearm.getDistance().coerceAtLeast(1f)
            limb(watch - across * 15f, watch + across * 15f, v.of(listOf(MoPropTeal, MoPropPurple, MoPropNavy), salt = 3), 12f)
            drawCircle(MoPropGoldDark, 14f, watch)
            drawCircle(MoPropCream, 11f, watch)
            polyline(MoPropCharcoal, 2.5f, listOf(watch - Offset(0f, 7f), watch, watch + Offset(5f, 2f)))
        }
        if (tapper < 0f) {
            // The kit draws his right arm over his left, which would put a tapping left mitten under the wrist.
            mitten(rig, tapHand, -1f)
        }
        val down = beat(t, v).down
        for (angle in listOf(0.18f, 0.58f)) {
            val out = Offset(tapper * cos(angle), -sin(angle))
            limb(tapHand + out * 18f, tapHand + out * 28f, MoPropWhite.copy(alpha = down), 4f)
        }
        fumingMark(rig, 0.7f)
    }
}

/** He rings for service, glaring at you between slaps, until the plunger sticks and he has to poke it free. */
private object ServiceBell : MoAct("angry_service_bell", MoOccasion.Angry) {
    private const val SLAP = 0.32f
    private const val LANDS = 0.25f
    private const val STUCK = 0.7f
    private const val POKE = 0.4f
    private const val BACK = 0.3f

    // The bell stands on a pedestal that brings its plunger up to where his mitten can slap it.
    private const val PLUNGER_TOP = FEET - 106f
    private const val PRESSED = 6f
    private const val REACH = 125f
    private const val BELL_BASE = FEET - 76f
    private const val SWING = 30f

    private fun side(v: MoVariant): Float = if (v.chance(50, salt = 1)) 1f else -1f

    /** [hand] and [plunger] run from 0, up, to 1, pressed down; [ring] fades after every ding. */
    private class Ringing(val hand: Float, val plunger: Float, val ring: Float, val puzzled: Float)

    private fun ringing(t: Float, v: MoVariant): Ringing {
        val slapsEnd = (3 + v.pick(3, salt = 4)) * SLAP
        val pokeAt = slapsEnd + STUCK
        val popAt = pokeAt + 0.3f
        val period = pokeAt + POKE + BACK
        // Starting just after a slap, so the still frame shows the bell ringing and not a mitten hiding it.
        val at = cycle(t + 0.6f * SLAP, period) * period
        val beat = (at / SLAP).mod(1f)
        val slapping = at < slapsEnd

        val hand = if (slapping) {
            strike(beat, LANDS)
        } else {
            // A careful poke, a jerk back as it pops, and then up to where the slaps start from.
            (ease(span(at, pokeAt, pokeAt + 0.28f)) - 0.4f * ease(span(at, popAt, popAt + 0.06f))) * (1f - ease(span(at, pokeAt + POKE, period)))
        }
        val popped = span(at, popAt, popAt + 0.25f)
        val plunger = when {
            at < slapsEnd - SLAP || (slapping && beat < LANDS) -> hand
            at < popAt -> 1f
            // It jumps up past where it rests and drops back.
            else -> 1f - ease(span(popped, 0f, 0.35f)) - 0.5f * sin(PI.toFloat() * span(popped, 0.35f, 1f))
        }
        val ring = when {
            slapping -> if (beat >= LANDS) 1f - span(beat, LANDS, 1f) else 0f
            at >= popAt -> 1f - popped
            else -> 0f
        }
        val puzzled = ease(span(at, slapsEnd + 0.05f, slapsEnd + 0.25f)) * (1f - ease(span(at, popAt + 0.1f, popAt + 0.3f)))
        return Ringing(hand, plunger, ring, puzzled)
    }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val s = side(v)
        val now = ringing(t, v)
        val shape = MoBodies.of(body)
        val lifted = 1f - now.hand
        val near = MoArm(
            Offset(REACH - shape.shoulderHalf + 4f * lifted, PLUNGER_TOP + PRESSED - MITTEN - shape.shoulderY - SWING * lifted),
            bend = 0.5f,
        )
        val wondering = now.puzzled > 0.5f
        val eye = MoEye(
            lid = if (wondering) 0.3f else 0.9f,
            iris = 0.56f,
            gaze = mix(Offset(s * 0.3f * now.hand, 0.1f), Offset(s * 0.9f, 0.55f), now.puzzled),
        )
        // No tilt, hop or shake in this one: his mitten has to meet a bell that stands in the world.
        return moIdle(t, energy = 1.2f).copy(
            leftEye = eye,
            rightEye = eye,
            brows = if (wondering) MoBrows(MoBrowKind.Raised, skew = 6f) else MoBrows(MoBrowKind.Angry),
            mouth = if (wondering) MoMouth.Flat(width = 10f) else MoMouth.Gritted,
            leftArm = if (s < 0f) near else MoArm.Hip,
            rightArm = if (s < 0f) MoArm.Hip else near,
            flare = 8f,
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val now = ringing(t, v)
        val x = CENTRE + side(v) * REACH
        val (bell, bellLedge) = v.of(listOf(MoPropSilver to MoPropSteel, MoPropGold to MoPropGoldDark), salt = 2)
        val (stand, standLedge) = v.of(listOf(MoPropWood to MoPropWoodDark, MoPropTeal to MoPropTealDark), salt = 3)
        val dip = 3f * now.ring

        // A thin column between a foot and a wide top: a column as wide as the bell reads as a bottle with a cap.
        floorShadow(x, 30f, MoPropShadow)
        block(Offset(x, FEET - 32f), 20f, 58f, standLedge, corner = 4f)
        chunk(Offset(x, FEET - 7f), 44f, 10f, stand, standLedge, corner = 4f, drop = 4f)
        chunk(Offset(x, BELL_BASE + 15f), 64f, 8f, stand, standLedge, corner = 4f, drop = 4f)
        chunk(Offset(x, PLUNGER_TOP + 6f + PRESSED * now.plunger), 9f, 12f, MoPropGreyDark, MoPropCharcoal, corner = 3f, drop = 2f)
        dome(Offset(x, BELL_BASE + 4f + dip), 26f, 26f, bellLedge)
        dome(Offset(x, BELL_BASE + dip), 26f, 22f, bell)
        curve(Offset(x - 18f, BELL_BASE - 6f + dip), Offset(x - 17f, BELL_BASE - 15f + dip), Offset(x - 8f, BELL_BASE - 18f + dip), MoPropWhite, 3.5f)
        chunk(Offset(x, BELL_BASE + 6f), 58f, 8f, MoPropGreyDark, MoPropCharcoal, corner = 4f, drop = 4f)
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) = fumingMark(rig, 0.8f, side = -side(v))

    override fun DrawScope.foreground(t: Float, v: MoVariant) {
        // The ding: ticks flying off both sides of the dome, in front of the arm that would hide them.
        val ring = ringing(t, v).ring
        val centre = Offset(CENTRE + side(v) * REACH, BELL_BASE - 8f)
        for (dir in listOf(-1f, 1f)) {
            for (angle in listOf(0.5f, 0.95f)) {
                val out = Offset(dir * sin(angle), -cos(angle))
                val from = 28f + 5f * (1f - ring)
                limb(centre + out * from, centre + out * (from + 7f), MoPropYellow.copy(alpha = ring), 3.5f)
            }
        }
    }

    override val ownsTheFloor get() = true
}

/** His own little storm cloud grumbles along with him, until its thunder makes him jump, and he scowls at it all the harder. */
private object OwnStormCloud : MoAct("angry_own_storm_cloud", MoOccasion.Angry) {
    private const val PERIOD = 4.2f
    private const val STARTLED = 0.5f

    private fun side(v: MoVariant): Float = if (v.chance(50, salt = 1)) 1f else -1f

    /** Seconds since the last bolt. Starting nearly half way round, he is scowling at a half-grown cloud. */
    private fun sinceBolt(t: Float): Float = cycle(t, PERIOD, offset = 0.47f) * PERIOD

    /** The fright: there at once when the bolt strikes, and shaken off within a second. */
    private fun fright(at: Float): Float = ease(span(at, 0f, 0.07f)) * (1f - ease(span(at, 0.35f, 0.8f)))

    /** What builds between two bolts, in him and in the cloud alike: spent in the strike, then slowly back. */
    private fun charge(at: Float): Float = max(1f - span(at, 0f, 0.12f), ease(span(at, 0.6f, PERIOD)))

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val s = side(v)
        val at = sinceBolt(t)
        val fright = fright(at)
        val charge = charge(at)
        val startled = at < STARTLED
        val eye = if (startled) {
            MoEye(iris = 0.4f, scale = 1.08f, gaze = Offset(s * 0.5f, -0.1f))
        } else {
            MoEye(lid = 0.8f, iris = 0.56f, gaze = mix(Offset(s * 0.5f, -0.1f), Offset(s * 0.8f, -0.8f), ease(span(at, STARTLED, 0.85f))))
        }
        val arm = mix(MoArm.Hip, MoArm(Offset(20f, 6f), bend = 0.6f), fright)
        return moIdle(t, energy = 1.4f).copy(
            leftEye = eye,
            rightEye = eye,
            brows = if (startled) MoBrows(MoBrowKind.Raised, lift = 5f) else MoBrows(MoBrowKind.Angry),
            mouth = if (startled) MoMouth.Oh(size = 8f) else MoMouth.Gritted,
            leftArm = arm,
            rightArm = arm,
            flare = 5f + 11f * charge,
            offset = Offset(-s * 5f * fright, -7f * sin(PI.toFloat() * span(at, 0f, 0.3f))),
            shake = wave(t, 0.085f) * (0.4f + 0.8f * charge) * (1f - fright),
            blinks = false,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val s = side(v)
        val at = sinceBolt(t)
        val charge = charge(at)
        val now = pose(t, v, rig.body.kind)
        // The cloud hangs in the room, not on him, so his jump and his trembling are taken back out.
        val room = Offset(-now.offset.x - now.shake, -now.offset.y)
        val cloud = Offset(CENTRE + s * 118f + wave(t, 0.1f) * 1.5f, rig.body.top + 38f) + room
        val size = 29f * (0.85f + 0.25f * charge)
        val (top, underside) = v.of(listOf(MoPropGreyDark to MoPropCharcoal, MoPropSteel to MoPropNavy), salt = 3)

        val flashes = if (v.chance(40, salt = 2)) listOf(0f, 0.25f) else listOf(0f)
        flashes.forEachIndexed { index, from ->
            val lit = 1f - span(at, from + 0.1f, from + 0.2f)
            if (at < from || lit <= 0f) return@forEachIndexed
            // The second flash takes the other zigzag, so it reads as a new bolt and not the old one again.
            val zig = if (index == 0) 10f else -10f
            val x = cloud.x + s * 8f
            val floor = FEET + room.y
            val points = (0..4).map { step ->
                val jag = if (step == 4) 0f else if (step % 2 == 0) -zig else zig
                Offset(x + jag, mix(cloud.y + size * 0.5f, floor, step / 4f))
            }
            polyline(MoPropYellow.copy(alpha = lit), 7f, points)
            sparkle(Offset(x, floor - 4f), 13f, 1f, MoPropYellow.copy(alpha = lit))
        }
        puff(cloud + Offset(0f, 7f), size, underside)
        puff(cloud, size, top)
        // It crackles to itself while it fills up again.
        val crackle = twitch(t, period = 0.7f, beat = 0.2f, offset = 0.1f) * charge
        sparkle(cloud + Offset(-s * size * 0.35f, size * 0.75f), 6f, 1f, MoPropYellow.copy(alpha = crackle))
    }

    override val sparkles get() = false
}

internal val angryActs1: List<MoAct> = listOf(
    HoldingItIn,
    ArmsTooShort,
    ColdShoulder,
    TappingTheWrist,
    ServiceBell,
    OwnStormCloud,
)
