package de.nicolasmahn.momentum.feature.mo.acts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import de.nicolasmahn.momentum.core.ui.theme.MoFlameBelly
import de.nicolasmahn.momentum.core.ui.theme.MoFlameMiddle
import de.nicolasmahn.momentum.core.ui.theme.MoPropBlue
import de.nicolasmahn.momentum.core.ui.theme.MoPropCream
import de.nicolasmahn.momentum.core.ui.theme.MoPropGold
import de.nicolasmahn.momentum.core.ui.theme.MoPropGrey
import de.nicolasmahn.momentum.core.ui.theme.MoPropPink
import de.nicolasmahn.momentum.core.ui.theme.MoPropPurple
import de.nicolasmahn.momentum.core.ui.theme.MoPropRed
import de.nicolasmahn.momentum.core.ui.theme.MoPropTeal
import de.nicolasmahn.momentum.core.ui.theme.MoPropWhite
import de.nicolasmahn.momentum.core.ui.theme.MoPropWood
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
import de.nicolasmahn.momentum.feature.mo.MoRig
import de.nicolasmahn.momentum.feature.mo.cycle
import de.nicolasmahn.momentum.feature.mo.ease
import de.nicolasmahn.momentum.feature.mo.flameTongue
import de.nicolasmahn.momentum.feature.mo.limb
import de.nicolasmahn.momentum.feature.mo.moIdle
import de.nicolasmahn.momentum.feature.mo.oval
import de.nicolasmahn.momentum.feature.mo.polyline
import de.nicolasmahn.momentum.feature.mo.puff
import de.nicolasmahn.momentum.feature.mo.pulse
import de.nicolasmahn.momentum.feature.mo.wave
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * Idle acts: nothing is going on, so he amuses himself.
 */

private const val TURN = 2f * PI.toFloat()

/** One rise and fall between [from] and [to], 0 outside them. */
private fun bump(x: Float, from: Float, to: Float): Float =
    if (to <= from) 0f else sin(((x - from) / (to - from)).coerceIn(0f, 1f) * PI.toFloat())

private fun mix(a: Float, b: Float, f: Float): Float = a + (b - a) * f

private fun mix(a: MoArm, b: MoArm, f: Float): MoArm =
    MoArm(Offset(mix(a.reach.x, b.reach.x, f), mix(a.reach.y, b.reach.y, f)), mix(a.bend, b.bend, f))

/** He plays with a yo-yo, and every few throws it stays asleep at the bottom until he flicks it awake. */
private object YoyoSleeper : MoAct("idle_yoyo_sleeper", MoOccasion.Idle, setOf(MoBodyKind.Tall), MoIntensity.Mild) {
    private const val THROW = 1.2f
    private const val SLEEPER = 2.4f

    private class Moment(val depth: Float, val dip: Float, val asleep: Boolean, val flick: Float)

    private fun side(v: MoVariant) = if (v.chance(50, salt = 2)) 1f else -1f

    private fun moment(t: Float, v: MoVariant): Moment {
        val clean = (2 + v.pick(3, salt = 3)) * THROW
        // A quarter throw in, so the first frame has the disc halfway down its string.
        val u = (t + THROW / 4f).mod(clean + SLEEPER)
        if (u < clean) {
            val local = u.mod(THROW)
            return Moment(pulse(local, THROW), bump(local, 0f, 0.2f), asleep = false, flick = 0f)
        }
        val w = u - clean
        // Down, asleep at the bottom, a flick, and it zips home faster than it fell, leaving him a beat of rest.
        val depth = ease(w / 0.6f) * (1f - ease((w - 1.8f) / 0.4f))
        return Moment(depth, bump(w, 0f, 0.2f), asleep = w in 0.8f..1.85f, flick = bump(w, 1.62f, 1.9f))
    }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val m = moment(t, v)
        val s = side(v)
        val eye = MoEye(gaze = Offset(0.8f * s, -0.2f + m.depth), iris = if (m.asleep) 0.62f else 0.76f)
        // Held out and high, so the string has room and the disc clears his base. Any further out
        // and the disc leaves the frame when he tilts.
        val throwing = MoArm(Offset(36f, -56f + 6f * m.dip - 10f * m.flick), bend = 0.3f)
        return moIdle(t).copy(
            leftEye = eye,
            rightEye = eye,
            brows = if (m.asleep) MoBrows(MoBrowKind.Raised, lift = 4f, skew = 6f) else MoBrows(),
            mouth = if (m.asleep) MoMouth.Flat(10f) else MoMouth.Smile(15f, 12f),
            leftArm = if (s < 0f) throwing else MoArm.Hip,
            rightArm = if (s > 0f) throwing else MoArm.Hip,
            // The throwing arm reaches out past his base, so drawn in front it shows its whole
            // length and the string plainly leaves his mitten.
            armsInFront = true,
            tilt = 2f * s,
            offset = Offset(0f, -5f * m.flick),
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val m = moment(t, v)
        val (face, hub) = v.of(
            listOf(MoPropRed to MoPropYellow, MoPropTeal to MoPropWhite, MoPropPurple to MoPropGold, MoPropBlue to MoPropPink),
            salt = 1,
        )
        val hand = if (side(v) > 0f) rig.rightHand else rig.leftHand
        // It shivers on the end of the string while it sleeps, and at the bottom it all but
        // touches the floor, which is the length that makes the throw read at a glance.
        val disc = hand + Offset(if (m.asleep) 1.5f * wave(t, 0.3f) else 0f, 20f + 104f * m.depth)
        limb(hand, disc, MoPropCream, 4f)
        drawCircle(face, 21f, disc)
        drawCircle(hub, 10f, disc)
        // The dot keeps going round while it sleeps, which is what a sleeping yo-yo does.
        val spin = cycle(t, 0.45f) * TURN
        drawCircle(MoPropWhite, 4f, disc + Offset(cos(spin), sin(spin)) * 13f)
    }
}

/** He hula-hoops with his whole wide base, until the hoop slides to his feet and he hops it back up. */
private object IdleHulaHoop : MoAct("idle_hula_hoop", MoOccasion.Idle, setOf(MoBodyKind.Tall)) {
    private const val SPIN = 0.9f
    private const val SLIP = 1.8f

    /** [level] is how far the hoop has slid down, [swing] how much he is still swinging it. */
    private class Moment(val angle: Float, val swing: Float, val level: Float, val crouch: Float, val hop: Float, val slipped: Boolean)

    private fun moment(t: Float, v: MoVariant): Moment {
        val spinning = (4 + v.pick(3, salt = 3)) * SPIN
        val dir = if (v.chance(50, salt = 2)) 1f else -1f
        val u = t.mod(spinning + SLIP)
        if (u < spinning) return Moment(dir * TURN * u / SPIN, 1f, 0f, 0f, 0f, slipped = false)
        val w = u - spinning
        return Moment(
            // One slow whole turn on the floor, so the stripes are back where they began.
            angle = dir * TURN * w / SLIP,
            swing = 1f - ease(w / 0.3f) + ease((w - 1.5f) / 0.3f),
            level = ease(w / 0.5f) * (1f - ease((w - 1.2f) / 0.4f)),
            crouch = bump(w, 0.9f, 1.15f),
            hop = bump(w, 1.1f, 1.6f),
            slipped = w in 0.3f..1.4f,
        )
    }

    private fun hips(m: Moment) = 7f * m.swing * sin(m.angle)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val m = moment(t, v)
        val idle = moIdle(t, 1.2f)
        val bob = 4f * m.swing * sin(m.angle)
        val eye = MoEye(gaze = Offset(0f, if (m.slipped) 1f else 0.2f))
        return idle.copy(
            leftEye = eye,
            rightEye = eye,
            brows = if (m.slipped) MoBrows(MoBrowKind.Raised, lift = 5f) else MoBrows(),
            mouth = if (m.slipped) MoMouth.Oh(7f) else MoMouth.Grin(open = 0.6f, tongue = false),
            // Held high out of the hoop's way; they sink while he stares at it on the floor.
            leftArm = MoArm(Offset(40f + 6f * m.level, -40f + 22f * m.level + bob), bend = 0.5f),
            rightArm = MoArm(Offset(40f + 6f * m.level, -40f + 22f * m.level - bob), bend = 0.5f),
            legs = MoLegs(MoLeg(lift = 8f * m.hop), MoLeg(lift = 8f * m.hop)),
            sway = idle.sway - 0.9f * hips(m),
            squash = idle.squash - 0.04f * m.crouch + 0.04f * m.hop,
            offset = Offset(hips(m), -18f * m.hop),
        )
    }

    /** Half of the hoop. It stays in the world, because it does not hop when he does. */
    private fun DrawScope.half(front: Boolean, t: Float, v: MoVariant) {
        val m = moment(t, v)
        val (hoop, stripe) = v.of(listOf(MoPropPink to MoPropWhite, MoPropTeal to MoPropYellow, MoPropPurple to MoPropGold), salt = 1)
        // The hoop rolls round his belly a little behind his hips, hanging lower on the side that swings out.
        val lag = m.angle + 1.4f
        val centre = Offset(CENTRE + hips(m) + 12f * m.swing * sin(lag), mix(MoBodies.Tall.shoulderY + 28f, FEET - 15f, m.level))
        val rx = mix(125f, 114f, m.level)
        val ry = mix(20f + 4f * sin(m.angle + 0.8f), 13f, m.level)
        val lean = 6f * m.swing * sin(lag)
        fun at(a: Float) = centre + Offset(rx * cos(a), ry * sin(a) + lean * cos(a))
        fun arc(from: Float, to: Float, steps: Int) = (0..steps).map { at(mix(from, to, it / steps.toFloat())) }

        val start = if (front) 0f else PI.toFloat()
        val end = start + PI.toFloat()
        polyline(hoop, 11f, arc(start, end, 24))
        for (index in 0 until 8) {
            val head = (m.angle + index * TURN / 8f).mod(TURN)
            // A stripe that straddles the seam of the circle shows up again one turn earlier.
            for (from in listOf(head, head - TURN)) {
                val lo = max(from, start)
                val hi = min(from + TURN / 16f, end)
                if (lo < hi) polyline(stripe, 11f, arc(lo, hi, 4))
            }
        }
    }

    override fun DrawScope.world(t: Float, v: MoVariant) = half(front = false, t, v)

    override fun DrawScope.foreground(t: Float, v: MoVariant) = half(front = true, t, v)

    override val ownsTheFloor get() = true
}

/** Eyes blissfully shut, he conducts an orchestra that is not there, and peeks once to see if anyone watches. */
private object ConductorOfNobody : MoAct("idle_conductor_of_nobody", MoOccasion.Idle, setOf(MoBodyKind.Tall), MoIntensity.Mild) {
    private const val BEAT = 0.6f

    private fun side(v: MoVariant) = if (v.chance(50, salt = 1)) 1f else -1f

    private fun beats(v: MoVariant) = (if (v.chance(40, salt = 2)) 3 else 4) * BEAT

    /** Small beats, a swell, two taps and a peek. Every part is a whole number of beats, so the foot never skips. */
    private fun phrase(v: MoVariant) = beats(v) + 3.6f

    // Half a beat in, so the first frame has the baton raised.
    private fun at(t: Float, v: MoVariant) = (t + BEAT / 2f).mod(phrase(v))

    /** How high the baton is in its beat, from 0 on the beat to 1 between two. */
    private fun lift(u: Float, v: MoVariant): Float {
        val b = beats(v)
        return when {
            u < b -> pulse(u, BEAT)
            u < b + 1.8f -> 0f
            u < b + 2.4f -> 0.7f * pulse(u - b - 1.8f, 0.3f)
            // He keeps the time going, faintly, while his mind is on the audience.
            else -> 0.35f * pulse(u - b - 2.4f, BEAT)
        }
    }

    /** The baton's sideways swing, one way on one beat and back on the next, dying away towards the swell. */
    private fun across(u: Float, v: MoVariant): Float =
        if (u < beats(v)) sin(u / (2f * BEAT) * TURN) * min(1f, 2f * bump(u, 0f, beats(v))) else 0f

    /** The one great swell: up, held for a moment at the top, and down again. */
    private fun swell(u: Float, v: MoVariant): Float {
        val w = u - beats(v)
        return if (w < 0f) 0f else ease(w / 0.5f) * (1f - ease((w - 1.2f) / 0.6f))
    }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val u = at(t, v)
        val s = side(v)
        val swell = swell(u, v)
        // The swing is built so the far end of the baton stays inside the frame on a Blazing Mo,
        // who is drawn at full size: hand 92 + 42 out, baton 18 further, against a half box of 160.
        val baton = mix(
            MoArm(Offset(26f + 16f * across(u, v), -24f - 32f * lift(u, v)), bend = 0.5f),
            MoArm(Offset(34f, -64f), bend = 0.5f),
            swell,
        )
        // The other hand mirrors at half size and a touch late, so he is not a machine.
        val late = (u - 0.1f).mod(phrase(v))
        val other = MoArm(Offset(30f, -8f - 11f * lift(late, v) - 48f * swell(late, v)), bend = 0.4f)

        val peek = u - beats(v) - 2.4f
        val shut = MoEye(shape = MoEyeShape.Shut)
        val peeking = if (peek in 0.2f..0.9f) MoEye(lidDrop = 0.3f, gaze = Offset(if (peek > 0.5f) -0.8f * s else 0f, 0f)) else shut
        val peeksLeft = v.chance(50, salt = 3)
        val tap = MoLeg(lift = 3f * pulse(u + BEAT / 2f, BEAT))
        val idle = moIdle(t)
        return idle.copy(
            leftEye = if (peeksLeft) peeking else shut,
            rightEye = if (peeksLeft) shut else peeking,
            brows = if (swell > 0.4f) MoBrows(MoBrowKind.Raised, lift = 4f) else MoBrows(),
            mouth = if (swell > 0.4f) MoMouth.Grin(open = 0.5f, tongue = false) else MoMouth.Smile(14f, 10f),
            leftArm = if (s < 0f) baton else other,
            rightArm = if (s > 0f) baton else other,
            // Both hands work out beside his flame, so drawn in front the whole arm shows and the
            // baton is plainly held rather than floating.
            armsInFront = true,
            legs = if (s > 0f) MoLegs(left = tap) else MoLegs(right = tap),
            squash = idle.squash + 0.06f * swell,
            flare = 14f * swell,
            tilt = 2f * s * swell,
            blinks = false,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val u = at(t, v)
        val s = side(v)
        val hand = if (s > 0f) rig.rightHand else rig.leftHand
        // At the top of the swell the baton trembles with feeling.
        val tremble = 3f * wave(t, 0.12f) * swell(u, v)
        limb(hand, hand + Offset(s * (4f + 14f * across(u, v)) + tremble, -58f), MoPropCream, 5.5f)
        drawCircle(MoPropWood, 7f, hand)
    }
}

/** He keeps a striped ball bouncing on the tip of his flame like a seal, and one bounce goes worryingly high. */
private object TipHeader : MoAct("idle_tip_header", MoOccasion.Idle, setOf(MoBodyKind.Tall)) {
    private const val SMALL = 0.8f
    private const val HIGH = 1.2f

    /** [p] is how far through its bounce the ball is, [since] the seconds since it last touched his tip. */
    private class Moment(val p: Float, val since: Float, val high: Boolean, val relief: Float)

    private fun moment(t: Float, v: MoVariant): Moment {
        val small = (3 + v.pick(2, salt = 2)) * SMALL
        // The first frame is the top of the second small bounce: the first one still carries his relief.
        val u = (t + SMALL * 1.5f).mod(small + HIGH)
        val relief = ease(u / 0.08f) * (1f - ease((u - 0.08f) / 0.6f))
        if (u < small) return Moment(u.mod(SMALL) / SMALL, u.mod(SMALL), high = false, relief)
        return Moment((u - small) / HIGH, u - small, high = true, relief = 0f)
    }

    private fun shuffle(t: Float) = 8f * wave(t, 3.2f)

    /** Where the ball is relative to his tip. It hangs in the air while he shuffles about underneath. */
    private fun ball(t: Float, v: MoVariant): Offset {
        val m = moment(t, v)
        val air = 4f * m.p * (1f - m.p)
        val dir = if (v.chance(50, salt = 3)) 1f else -1f
        // It all but touches his tip at the bottom, and the high peak is as high as the frame allows.
        return Offset(air * (dir * 6f * wave(t, 2.1f) - 0.7f * shuffle(t)), -22f - air * if (m.high) 46f else 24f)
    }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val m = moment(t, v)
        val waiting = m.high && m.p in 0.12f..0.92f
        val eye = MoEye(gaze = Offset((0.15f + ball(t, v).x / 8f).coerceIn(-1f, 1f), -1f), iris = if (waiting) 0.5f else 0.76f)
        val seesaw = 6f * wave(t, 1.6f)
        val step = wave(t, 0.8f)
        // The tip gives under the ball and springs it back up.
        val spring = m.since / 0.24f
        val kick = if (spring < 1f) -sin(spring * TURN) * (if (spring < 0.5f) 6f else 10f) else 0f
        val idle = moIdle(t)
        return idle.copy(
            leftEye = eye,
            rightEye = eye,
            brows = MoBrows(MoBrowKind.Raised, lift = if (waiting) 7f else 3f),
            mouth = when {
                waiting -> MoMouth.Oh(6f)
                m.relief > 0.25f -> MoMouth.Smile(15f, 12f)
                else -> MoMouth.TongueOut
            },
            leftArm = MoArm(Offset(34f, 16f + seesaw), bend = 0.35f),
            rightArm = MoArm(Offset(34f, 16f - seesaw), bend = 0.35f),
            legs = MoLegs(MoLeg(lift = 4f * max(step, 0f)), MoLeg(lift = 4f * max(-step, 0f))),
            flare = kick,
            // He balances in a slight crouch, and ducks further under the high one, which also buys it headroom.
            squash = idle.squash - 0.03f - 0.03f * bump(m.since, 0f, 0.15f) - 0.05f * m.relief - if (m.high) 0.04f * bump(m.p, 0f, 1f) else 0f,
            offset = Offset(shuffle(t), 0f),
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val (face, band) = v.of(listOf(MoPropRed to MoPropWhite, MoPropBlue to MoPropYellow, MoPropTeal to MoPropPink), salt = 1)
        val at = rig.tip + ball(t, v)
        drawCircle(face, 20f, at)
        rotate(cycle(t, 1.1f) * 360f, at) {
            oval(at, 20f, 7f, band)
            drawCircle(MoPropWhite, 4f, at + Offset(0f, -13f))
        }
    }

    override val sparkles get() = false
}

/** Hiccups: a jolt pops a flamelet off his tip, he holds his breath behind a mitten, and the next one gets him anyway. */
private object Hiccups : MoAct("idle_hiccups", MoOccasion.Idle, intensity = MoIntensity.Mild) {
    private const val HIC = 0.3f
    private const val SECOND = 0.45f

    private fun double(v: MoVariant) = v.chance(35, salt = 1)

    private fun hold(v: MoVariant) = v.between(1.8f, 2.6f, salt = 4)

    // The first frame is the top of a hic: in the air, mouth round, the flamelet just off his tip.
    private fun at(t: Float, v: MoVariant) = (t + HIC / 2f).mod(2f + hold(v) + if (double(v)) SECOND else 0f)

    private fun hic(u: Float, v: MoVariant) = bump(u, 0f, HIC) + if (double(v)) bump(u, SECOND, SECOND + HIC) else 0f

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val u = at(t, v)
        val h = hic(u, v)
        val start = 0.4f + if (double(v)) SECOND else 0f
        val release = start + hold(v)
        val covered = ease((u - start) / 0.25f) * (1f - ease((u - release) / 0.5f))
        val relaxed = bump(u, release + 0.2f, release + 1.5f)

        // His eyes dart from side to side while he holds it in, as if the next one could come from anywhere.
        val dart = cycle(u - start, 0.9f)
        val look = if (dart < 0.5f) -1f + 2f * ease(dart / 0.12f) else 1f - 2f * ease((dart - 0.5f) / 0.12f)
        val eye = MoEye(
            gaze = Offset(0.8f * look * covered, 0f),
            iris = 0.76f - 0.26f * min(h, 1f),
            scale = 1f + 0.05f * min(h, 1f),
            lidDrop = 0.15f * relaxed,
        )

        val shape = MoBodies.of(body)
        val side = if (v.chance(50, salt = 2)) 1f else -1f
        // The cold tip rests against the corner of the closed lips, so the mouth still shows.
        val corner = if (body == MoBodyKind.Low) 16f else 0f
        val onMouth = MoArm(Offset(corner - shape.shoulderHalf + side * (shape.mouth.x - CENTRE), shape.mouth.y + 2f - shape.shoulderY), bend = -0.8f)
        // On the low flame the cold tip of his mitten would be a dark spot where his mouth is.
        val covering = mix(MoArm.Down, onMouth, covered).copy(warm = covered)
        val idle = moIdle(t)
        return idle.copy(
            leftEye = eye,
            rightEye = eye,
            brows = when {
                h > 0.05f -> MoBrows(MoBrowKind.Raised, lift = 5f * h)
                covered > 0.5f -> MoBrows(MoBrowKind.Worried, lift = 2f)
                else -> MoBrows()
            },
            mouth = when {
                h > 0.05f -> MoMouth.Oh(8f)
                covered > 0.05f -> MoMouth.Flat(8f)
                else -> MoMouth.Smile(13f, 9f)
            },
            leftArm = if (side < 0f) covering else MoArm.Down,
            rightArm = if (side > 0f) covering else MoArm.Down,
            armsInFront = true,
            flare = 12f * h,
            // The jolt stretches him, and so does holding his breath.
            squash = idle.squash + 0.04f * h + 0.03f * covered,
            offset = Offset(0f, -9f * h),
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val u = at(t, v)
        val side = if (v.chance(50, salt = 3)) 1f else -1f
        val pops = if (double(v)) listOf(0f to side, SECOND to -side) else listOf(0f to side)
        for ((from, towards) in pops) {
            val rise = (u - from - 0.1f) / 0.7f
            if (rise !in 0f..1f) continue
            // It stays its own bright self most of the way and only goes out at the end, shrinking
            // as it fades so the last frames are a vanishing spark and not a dark speck.
            val fade = 1f - ease(rise)
            flameTongue(rig.tip + Offset(towards * (18f + 20f * rise), -8f - rise * 20f), 14f * (1f - 0.7f * rise), MoFlameMiddle.copy(alpha = fade), MoFlameBelly.copy(alpha = fade))
        }
    }
}

/** His tip sags into his face like a fringe until one toss of his body whips it back up, and then it sags again. */
private object FloppingTip : MoAct("idle_flopping_tip", MoOccasion.Idle, intensity = MoIntensity.Mild) {
    private const val SAG = 0.85f

    // Almost halfway in, so the first frame has the tip half sagged and the look that goes with it.
    private fun at(t: Float, v: MoVariant) = cycle(t, v.between(4.5f, 6.5f, salt = 1), offset = 0.45f)

    /** How far through the toss he is, from 0 to 1 over the last part of the cycle. */
    private fun toss(c: Float) = ((c - SAG) / (1f - SAG)).coerceIn(0f, 1f)

    /** The first toss of the variant where it is not enough. */
    private fun failed(c: Float, v: MoVariant) = if (v.chance(35, salt = 2)) ((c - 0.62f) / 0.12f).coerceIn(0f, 1f) else 0f

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val c = at(t, v)
        val g = ease(c / SAG)
        val x = toss(c)
        val f = failed(c, v)
        // The whip rings out on a damped wave that ends exactly where the sag begins.
        val whip = exp(-2f * x) * cos(1.5f * TURN * x) * (1f - ease((x - 0.6f) / 0.4f))
        // The Low tip is already bent over, and pulling it as far as the Tall one draws it out into a spike.
        val reach = if (body == MoBodyKind.Low) 56f else 80f
        val sag = reach * if (x > 0f) whip else g - 0.4f * bump(f, 0f, 1f)
        val satisfied = x > 0.3f || c < 0.1f
        val fedUp = f > 0.5f && x == 0f
        val idle = moIdle(t)
        return idle.copy(
            leftEye = if (satisfied) MoEye(shape = MoEyeShape.Happy) else MoEye(lidDrop = 0.35f, gaze = Offset(0.5f + 0.4f * g, -0.7f)),
            rightEye = if (satisfied) MoEye(shape = MoEyeShape.Happy) else MoEye(lidDrop = 0.35f, gaze = Offset(0.5f + 0.4f * g, -0.7f)),
            brows = when {
                satisfied -> MoBrows()
                fedUp -> MoBrows(MoBrowKind.Flat, lift = -2f)
                g > 0.6f -> MoBrows(MoBrowKind.Flat)
                else -> MoBrows()
            },
            mouth = if (satisfied) MoMouth.Smile(15f, 12f) else MoMouth.Flat(12f),
            leftArm = MoArm.Hip,
            sway = idle.sway * 0.3f + 8f + sag,
            flare = -32f * g * (1f - ease(x / 0.25f)),
            tilt = -9f * bump(x, 0f, 0.4f) - 5f * bump(f, 0f, 0.6f),
            offset = Offset(0f, -4f * bump(x, 0f, 0.4f)),
        )
    }

    override fun DrawScope.foreground(t: Float, v: MoVariant) {
        if (!v.chance(40, salt = 3)) return
        // His feet scuff the floor on the toss.
        val k = (toss(at(t, v)) - 0.15f) / 0.6f
        if (k in 0f..1f) puff(Offset(CENTRE - 116f, FEET - 16f), 10f + 8f * k, MoPropGrey.copy(alpha = 0.6f * (1f - k)))
    }
}

internal val idleActs1: List<MoAct> = listOf(
    YoyoSleeper,
    IdleHulaHoop,
    ConductorOfNobody,
    TipHeader,
    Hiccups,
    FloppingTip,
)
