package com.example.workouttracker.feature.mo.acts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import com.example.workouttracker.core.ui.theme.MoPropBlue
import com.example.workouttracker.core.ui.theme.MoPropBlueDark
import com.example.workouttracker.core.ui.theme.MoPropBrown
import com.example.workouttracker.core.ui.theme.MoPropGrey
import com.example.workouttracker.core.ui.theme.MoPropGreyDark
import com.example.workouttracker.core.ui.theme.MoPropOrange
import com.example.workouttracker.core.ui.theme.MoPropPink
import com.example.workouttracker.core.ui.theme.MoPropPinkDark
import com.example.workouttracker.core.ui.theme.MoPropRed
import com.example.workouttracker.core.ui.theme.MoPropShadow
import com.example.workouttracker.core.ui.theme.MoPropSky
import com.example.workouttracker.core.ui.theme.MoPropSteel
import com.example.workouttracker.core.ui.theme.MoPropWhite
import com.example.workouttracker.core.ui.theme.MoPropWoodDark
import com.example.workouttracker.core.ui.theme.MoPropYellow
import com.example.workouttracker.core.ui.theme.MoRageOuter
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
import com.example.workouttracker.feature.mo.MoPalettes
import com.example.workouttracker.feature.mo.MoPose
import com.example.workouttracker.feature.mo.MoRig
import com.example.workouttracker.feature.mo.angerMark
import com.example.workouttracker.feature.mo.block
import com.example.workouttracker.feature.mo.chunk
import com.example.workouttracker.feature.mo.cycle
import com.example.workouttracker.feature.mo.ease
import com.example.workouttracker.feature.mo.flameTongue
import com.example.workouttracker.feature.mo.moIdle
import com.example.workouttracker.feature.mo.oval
import com.example.workouttracker.feature.mo.puff
import com.example.workouttracker.feature.mo.pulse
import com.example.workouttracker.feature.mo.shape
import com.example.workouttracker.feature.mo.wave
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/*
 * Six more ways of being cross about a late task, from a sulk to a bellow. Each one is a little story
 * told on a fixed loop: the loop is cut into beats, every beat blends into the next, and the loop is
 * shifted so that t = 0 lands on the frame that tells the story best.
 */

/** How much of the beat from [from] to [to] has passed at [x], held to 0..1. */
private fun span(x: Float, from: Float, to: Float): Float = when {
    to != from -> ((x - from) / (to - from)).coerceIn(0f, 1f)
    x < from -> 0f
    else -> 1f
}

/** Eases in over [inFrom]..[inTo] and out again over [outFrom]..[outTo]: something that comes and goes. */
private fun window(x: Float, inFrom: Float, inTo: Float, outFrom: Float, outTo: Float): Float =
    ease(span(x, inFrom, inTo)) * (1f - ease(span(x, outFrom, outTo)))

/** One smooth bump from 0 to 1 and back over [from]..[to]. */
private fun bump(x: Float, from: Float, to: Float): Float = sin(span(x, from, to) * PI.toFloat())

private fun mix(a: Float, b: Float, x: Float): Float = a + (b - a) * x

private fun mix(a: Offset, b: Offset, x: Float): Offset = Offset(mix(a.x, b.x, x), mix(a.y, b.y, x))

private fun mix(a: MoArm, b: MoArm, x: Float): MoArm = MoArm(mix(a.reach, b.reach, x), mix(a.bend, b.bend, x))

/** Blends two round eyes, so a glare can melt into a stare instead of snapping. */
private fun mix(a: MoEye, b: MoEye, x: Float): MoEye = a.copy(
    gaze = mix(a.gaze, b.gaze, x),
    iris = mix(a.iris, b.iris, x),
    lid = mix(a.lid, b.lid, x),
    lidDrop = mix(a.lidDrop, b.lidDrop, x),
    scale = mix(a.scale, b.scale, x),
)

private fun Color.faded(share: Float): Color = copy(alpha = alpha * share.coerceIn(0f, 1f))

/** The fuming mark beside his head, where the tantrum wears it. Too small to draw, it is left out. */
private fun DrawScope.fuming(rig: MoRig, size: Float) {
    if (size > 0.08f) angerMark(Offset(CENTRE + 74f, rig.body.top + 96f), size)
}

// Both burning bodies stand on the same feet, so the floor can know where they are without a rig.
private fun footX(side: Float): Float = if (side < 0f) MoBodies.Tall.leftFootX else MoBodies.Tall.rightFootX

/** Impatient: he has paced the same few steps for so long that the floor has a rut, and glares out from its ends. */
private object PacingARut : MoAct("angry_pacing_a_rut", MoOccasion.Angry, intensity = MoIntensity.Mild) {
    private const val WALK = 2.2f

    // Four whole strides fit one walk, so both feet are down as he stops.
    private const val STRIDE = 0.55f

    /** Where the pacing has got to: [stood] seconds at [end] (-1 or 1), or [walked] seconds on the way to the other one. */
    private class Pace(val end: Float, val stood: Float, val stay: Float, val walked: Float?, val glares: Boolean)

    private fun reach(v: MoVariant) = v.between(28f, 40f, salt = 2)

    private fun pace(t: Float, v: MoVariant): Pace {
        val first = if (v.chance(50, salt = 1)) -1f else 1f
        val (here, there) = if (v.chance(50, salt = 3)) 1.1f to 0.7f else 0.7f to 1.1f
        val bothEnds = v.chance(60, salt = 4)
        val period = here + there + 2f * WALK
        // Shifted so that it opens on the glare, with the steam just out.
        val now = cycle(t, period, offset = 0.3f / period) * period
        val arrived = here + WALK
        return when {
            now < here -> Pace(first, now, here, null, glares = true)
            now < arrived -> Pace(first, here, here, now - here, glares = true)
            now < arrived + there -> Pace(-first, now - arrived, there, null, bothEnds)
            else -> Pace(-first, there, there, now - arrived - there, bothEnds)
        }
    }

    private fun glare(pace: Pace): Float =
        if (pace.glares && pace.walked == null) window(pace.stood, 0.05f, 0.25f, pace.stay - 0.2f, pace.stay) else 0f

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val pace = pace(t, v)
        val walked = pace.walked
        val heading = -pace.end
        val going = if (walked == null) 0f else window(walked, 0f, 0.25f, WALK - 0.25f, WALK)
        val along = if (walked == null) pace.end else pace.end * (1f - 2f * ease(walked / WALK))
        val step = if (walked == null) 0f else wave(walked, STRIDE)
        val glare = glare(pace)

        // His tip trails behind the walk and whips forward when he stops dead.
        val halt = span(pace.stood, 0f, 0.6f)
        val tip = pace.end * (14f * going + 12f * sin(halt * PI.toFloat()) * (1f - halt))
        // Standing, his eyes turn along the rut to where he goes next, unless he is busy glaring.
        val ahead = if (walked == null) mix(pace.end, heading, ease(span(pace.stood, 0.1f, pace.stay - 0.1f))) else heading
        val eye = MoEye(lid = mix(0.6f, 0.95f, glare), iris = 0.6f, gaze = mix(Offset(ahead * 0.7f, 0.8f), Offset(0f, 0.1f), glare))
        val huff = if (pace.glares && walked == null) bump(pace.stood, 0.1f, 0.4f) else 0f

        // The foot on the side he is heading for leads.
        val lead = MoLeg(lift = 8f * max(0f, step) * going, swing = 5f * max(0f, step) * going)
        val trail = MoLeg(lift = 8f * max(0f, -step) * going, swing = -5f * max(0f, -step) * going)
        val idle = moIdle(t, energy = 1.2f)
        return idle.copy(
            leftEye = eye,
            rightEye = eye,
            brows = MoBrows(MoBrowKind.Angry),
            mouth = if (glare > 0.5f) MoMouth.Gritted else MoMouth.Flat(12f),
            leftArm = MoArm.Hip,
            rightArm = MoArm.Hip,
            legs = if (heading > 0f) MoLegs(left = trail, right = lead) else MoLegs(left = lead, right = trail),
            sway = idle.sway + tip,
            flare = 4f + 6f * glare,
            squash = idle.squash - 0.04f * huff,
            tilt = heading * 3f * going,
            offset = Offset(reach(v) * along, -2f * abs(step) * going),
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val half = 104f + reach(v)
        // Worn through to the bare earth: grey outlines vanish on a dark ground, trodden dirt does not.
        oval(Offset(CENTRE, FEET - 6f), half, 12f, MoPropWoodDark)
        oval(Offset(CENTRE, FEET - 4f), half - 7f, 8.5f, MoPropBrown)
        for (side in listOf(-1f, 1f)) {
            oval(Offset(CENTRE + side * (half + 8f), FEET - 12f), 5f, 3f, MoPropWoodDark)
            oval(Offset(CENTRE + side * (half - 12f), FEET - 22f), 3.5f, 2.5f, MoPropWoodDark)
        }
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val pace = pace(t, v)
        fuming(rig, 0.55f + 0.2f * glare(pace))
        if (!pace.glares || pace.walked != null) return
        // Steam out of both shoulders, the bigger puff on the side he has run out of floor. It is
        // gone just before he walks on, however short the stop: cut off mid-air it would vanish.
        val rise = span(pace.stood, 0.15f, pace.stay - 0.05f)
        if (rise <= 0f || rise >= 1f) return
        for (side in listOf(-1f, 1f)) {
            val size = if (side == pace.end) 11f else 7.5f
            puff(Offset(CENTRE + side * (92f + 8f * rise), rig.body.eyeY - 62f - 40f * rise), size + 7f * rise, MoPropWhite.faded(1f - rise))
        }
    }

    override val ownsTheFloor get() = true
}

/** Annoyed: he breathes out slowly with his eyes shut to calm down, until one eye snaps open and it starts over. */
private object CountingToTen : MoAct("angry_counting_to_ten", MoOccasion.Angry) {
    private const val BREATH = 2.6f
    private const val SNAP = 1.2f

    /** A calm [breath] from 0 to 1 (the [last] one before it fails), or the [snap] from 0 to 1. */
    private class Count(val breath: Float?, val last: Boolean, val snap: Float?)

    private fun count(t: Float, v: MoVariant): Count {
        val breaths = 1 + v.pick(3, salt = 2)
        val period = breaths * BREATH + SNAP
        // Shifted so that it opens part way into a breath, with the mittens half way down.
        val now = cycle(t, period, offset = 0.8f / period) * period
        val calm = breaths * BREATH
        if (now >= calm) return Count(null, last = false, snap = (now - calm) / SNAP)
        val index = (now / BREATH).toInt().coerceAtMost(breaths - 1)
        return Count(now / BREATH - index, last = index == breaths - 1, snap = null)
    }

    /** The anger leaking through the last calm breath, which is what gives the snap away. */
    private fun simmer(count: Count): Float = if (count.last && count.breath != null) count.breath * count.breath else 0f

    private fun fury(count: Count): Float = count.snap?.let { window(it, 0f, 0.08f, 0.55f, 0.8f) } ?: 0f

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val count = count(t, v)
        val shape = MoBodies.of(body)
        // The mittens start just under his eyes and press down from there, clear of his mouth.
        val top = shape.eyeY + shape.eyeRadius + 16f - shape.shoulderY
        val raised = MoArm(Offset(-42f, top), bend = 0.5f)
        val pressed = MoArm(Offset(-42f, top + 40f), bend = 0.3f)
        val shut = MoEye(shape = MoEyeShape.Shut)
        val idle = moIdle(t, energy = 0.5f)
        val tremble = wave(t, 0.07f)

        val breath = count.breath
        if (breath != null) {
            val down = window(breath, 0f, 0.8f, 0.81f, 1f)
            val breathingIn = breath > 0.81f
            return idle.copy(
                leftEye = shut,
                rightEye = shut,
                brows = MoBrows(MoBrowKind.Angry, lift = 2f),
                mouth = if (breathingIn) MoMouth.Oh(7f) else MoMouth.Pout,
                leftArm = mix(raised, pressed, down),
                rightArm = mix(raised, pressed, down),
                armsInFront = true,
                flare = 2f + 4f * simmer(count),
                squash = idle.squash - 0.03f * down + 0.025f * bump(breath, 0.81f, 1f),
                shake = tremble * 0.9f * simmer(count),
                blinks = false,
            )
        }

        val snap = count.snap ?: 0f
        val fury = fury(count)
        val spike = window(snap, 0f, 0.08f, 0.2f, 0.6f)
        val open = window(snap, 0f, 0.06f, 0.6f, 0.72f)
        val glaring = if (open > 0.05f) MoEye(open = open, lid = 1f, iris = 0.5f, scale = 1.05f) else shut
        val rightSnaps = v.chance(50, salt = 1)
        val jerk = MoArm(Offset(30f, -30f), bend = 0.8f)
        return idle.copy(
            leftEye = if (rightSnaps) shut else glaring,
            rightEye = if (rightSnaps) glaring else shut,
            brows = MoBrows(MoBrowKind.Angry, lift = 2f * (1f - fury)),
            mouth = if (snap < 0.7f) MoMouth.Gritted else MoMouth.Flat(10f),
            leftArm = mix(raised, jerk, fury),
            rightArm = mix(raised, jerk, fury),
            armsInFront = true,
            flare = mix(6f, 2f, ease(span(snap, 0.7f, 1f))) + v.between(26f, 40f, salt = 3) * spike,
            squash = idle.squash + 0.04f * spike,
            shake = tremble * 3f * fury,
            blinks = false,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val count = count(t, v)
        fuming(rig, mix(0.55f + 0.1f * pulse(t, 0.9f) + 0.25f * simmer(count), 1.1f, fury(count)))
        // One long breath out, drifting from his pursed mouth right off his belly, where white shows.
        val out = span((count.breath ?: return) * BREATH, 0.1f, 2.1f)
        if (out <= 0f || out >= 1f) return
        puff(rig.mouth + Offset(24f + 100f * out, 4f - 14f * out), 6f + 10f * out, MoPropWhite.faded(0.95f * (1f - out * out)))
    }

    override val sparkles get() = false
}

/** Furious: his flare turns into thrust and lifts him off the floor, until he looks down and drops with a plop. */
private object LiftOff : MoAct("angry_lift_off", MoOccasion.Angry, intensity = MoIntensity.Strong) {
    private const val PERIOD = 4.4f

    // The loop: rise to 0.45, hover to 0.60, fall to 0.68, plop to 0.78, scowl to 1. It opens well into the rise.
    private fun loop(t: Float) = cycle(t, PERIOD, offset = 0.3f)

    private fun height(v: MoVariant) = v.between(34f, 52f, salt = 1)

    /** How much of his height he has gained: up on the thrust, held while he hovers, and gone in a fall. */
    private fun risen(c: Float): Float = when {
        c < 0.45f -> ease(c / 0.45f)
        c < 0.60f -> 1f
        else -> span(c, 0.60f, 0.68f).let { 1f - it * it }
    }

    private fun wobble(c: Float, v: MoVariant): Float =
        (if (v.chance(50, salt = 3)) 5f else -5f) * sin(span(c, 0.45f, 0.60f) * 2f * PI.toFloat())

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val c = loop(t)
        val fists = MoArm(Offset(8f, 34f), bend = 0.05f)
        val balancing = MoArm(Offset(30f, 10f), bend = 0.5f)
        val flailing = MoArm(Offset(34f, -44f), bend = 0.3f)
        val arm = mix(
            mix(mix(mix(fists, balancing, ease(span(c, 0.50f, 0.54f))), flailing, ease(span(c, 0.60f, 0.64f))), MoArm.Down, ease(span(c, 0.68f, 0.71f))),
            fists,
            ease(span(c, 0.80f, 0.92f)),
        )
        val glare = MoEye(lid = 1f, iris = 0.56f, gaze = Offset(0f, 0.3f))
        val airborne = MoEye(iris = 0.45f, scale = 1.08f, gaze = Offset(0f, 1f))
        val wince = MoEye(shape = MoEyeShape.Happy)
        val eye = when {
            c < 0.50f || c >= 0.78f -> glare
            c < 0.68f -> airborne
            else -> wince
        }
        // The thrust shakes him all the way up, stops dead at the top, and builds again with the scowl.
        val shaking = if (c < 0.60f) 1f - ease(span(c, 0.45f, 0.49f)) else ease(span(c, 0.80f, 1f))
        val plop = window(c, 0.68f, 0.69f, 0.70f, 0.80f)
        val idle = moIdle(t, energy = 1.8f)
        return idle.copy(
            palette = MoPalettes.Rage,
            leftEye = eye,
            rightEye = eye,
            brows = if (c in 0.50f..0.78f) MoBrows(MoBrowKind.Raised, lift = 4f) else MoBrows(MoBrowKind.Angry),
            mouth = when {
                c < 0.50f || c >= 0.78f -> MoMouth.Gritted
                c < 0.60f -> MoMouth.Oh(7f)
                c < 0.68f -> MoMouth.Oh(10f)
                else -> MoMouth.Wavy
            },
            leftArm = arm,
            rightArm = arm,
            legs = MoLegs(MoLeg(lift = -2f * risen(c)), MoLeg(lift = -2f * risen(c))),
            // Kept low on the way up so his tip stays in the picture; it streams up in the fall and is flattened by the plop.
            flare = mix(mix(mix(10f, 20f, ease(span(c, 0.60f, 0.68f))), -6f, ease(span(c, 0.68f, 0.70f))), 10f, ease(span(c, 0.78f, 1f))),
            squash = idle.squash - 0.14f * plop,
            offset = Offset(wobble(c, v), -height(v) * risen(c)),
            shake = wave(t, 0.07f) * 2.5f * shaking,
            blinks = false,
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val c = loop(t)
        val lift = height(v) * risen(c)
        // The thrust gives out while he hovers, a beat before he notices.
        val thrust = ease(span(lift, 4f, 14f)) * (1f - ease(span(c, 0.52f, 0.60f)))
        val puffs = 2 + v.pick(2, salt = 2)
        if (thrust > 0f) {
            for (side in listOf(-1f, 1f)) {
                for (index in 0 until puffs) {
                    // Each puff leaves a sole small and solid, and spreads and thins on its way to the floor.
                    val fall = (index + cycle(t, 0.35f)) / puffs
                    val at = Offset(footX(side) + wobble(c, v) * (1f - fall) + sin(fall * 7f + side) * 4f * fall, FEET - lift + 2f + fall * (lift - 8f))
                    puff(at, 9f + 8f * fall, MoPropWhite.faded(0.95f * (1f - fall * fall) * thrust))
                }
            }
        }
        val dust = span(c, 0.68f, 0.68f + 0.45f / PERIOD)
        if (dust > 0f && dust < 1f) {
            for (side in listOf(-1f, 1f)) {
                puff(Offset(CENTRE + side * (98f + 30f * dust), FEET - 8f - 6f * dust), 8f + 6f * dust, MoPropGrey.faded(0.8f * (1f - dust)))
            }
        }
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val c = loop(t)
        fuming(rig, 0.85f * if (c < 0.60f) 1f - ease(span(c, 0.45f, 0.50f)) else ease(span(c, 0.80f, 0.90f)))
    }

    override val sparkles get() = false
}

/** Annoyed: he glares with gritted teeth until a hiccup of fire escapes, claps a mitten over his mouth, and glares harder. */
private object FireHiccups : MoAct("angry_fire_hiccups", MoOccasion.Angry) {
    private const val HOP = 0.36f
    private const val SECOND = 0.42f

    /** The seconds since the hiccup, and what this day's hiccup is like. */
    private class Hiccup(val now: Float, val period: Float, val double: Boolean, val hand: Float) {
        val covers = if (double) 0.74f else 0.34f
        val glares = covers + 1.05f
        val hop = bump(now, 0f, HOP) + if (double) 0.6f * bump(now, SECOND, SECOND + 0.2f) else 0f
        val covering = window(now, covers, covers + 0.14f, covers + 0.85f, covers + 1.05f)
        val glare = ease(span(now, glares, period))
    }

    private fun hiccup(t: Float, v: MoVariant): Hiccup {
        val period = v.between(2.6f, 3.4f, salt = 3)
        // Shifted so that it opens in mid-hop, with the flame already out.
        val now = cycle(t, period, offset = 0.12f / period) * period
        return Hiccup(now, period, double = v.chance(35, salt = 1), hand = if (v.chance(50, salt = 2)) 1f else -1f)
    }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val hic = hiccup(t, v)
        val shape = MoBodies.of(body)
        // The reach that lays this hand's mitten flat over his mouth, on either body.
        val over = MoArm(Offset(-shape.shoulderHalf + hic.hand * (shape.mouth.x - CENTRE), shape.mouth.y + 4f - shape.shoulderY), bend = 0.6f)
        val covering = mix(MoArm.Hip, over, hic.covering).copy(warm = hic.covering)
        val startled = hic.now < hic.covers
        val caught = !startled && hic.now < hic.glares
        // Did anyone see that? One look to each side.
        val dart = 0.9f * hic.hand * (1f - 2f * ease(span(hic.now, hic.covers + 0.4f, hic.covers + 0.55f)))
        val eye = when {
            startled -> MoEye(iris = 0.5f, scale = 1.06f)
            caught -> MoEye(iris = 0.5f, gaze = Offset(dart, 0f))
            else -> MoEye(lid = mix(0.6f, 1f, hic.glare), iris = 0.5f, gaze = Offset(0f, 0.1f))
        }
        val idle = moIdle(t, energy = 1.4f)
        return idle.copy(
            leftEye = eye,
            rightEye = eye,
            brows = MoBrows(if (startled) MoBrowKind.Raised else if (caught) MoBrowKind.Worried else MoBrowKind.Angry),
            mouth = when {
                startled -> MoMouth.Oh(if (hic.hop > 0.2f) 10f else 6f)
                hic.covering > 0.96f -> MoMouth.Hidden
                caught -> MoMouth.Oh(6f)
                else -> MoMouth.Gritted
            },
            leftArm = if (hic.hand < 0f) covering else MoArm.Hip,
            rightArm = if (hic.hand > 0f) covering else MoArm.Hip,
            armsInFront = true,
            // The glare has built the flame up, and the hiccup lets it out.
            flare = 6f + 10f * if (hic.now < hic.glares) 1f - ease(span(hic.now, 0f, 0.4f)) else hic.glare,
            squash = idle.squash + 0.06f * hic.hop,
            offset = Offset(0f, -10f * hic.hop),
            shake = wave(t, 0.085f) * 1.6f * hic.glare * ease(span(hic.period - hic.now, 0f, 0.05f)),
            blinks = false,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val hic = hiccup(t, v)
        fuming(rig, 0.8f * hic.glare)
        tongueOfFire(rig, -hic.hand, span(hic.now, 0f, 0.7f), 21f)
        if (hic.double) tongueOfFire(rig, -hic.hand, span(hic.now, SECOND, SECOND + 0.6f), 14f)
    }

    /** The flame shoots sideways out of his mouth, away from the hand that is coming, and rights itself and climbs once it is past his eye. */
    private fun DrawScope.tongueOfFire(rig: MoRig, side: Float, out: Float, size: Float) {
        if (out <= 0f || out >= 1f) return
        val at = rig.mouth + Offset(side * (10f + 118f * out), 7f - 53f * out * out * out)
        // Red round orange round yellow: three tiers read as fire on his belly, on his orange and on the ground alike.
        val fade = 1f - out * out
        val length = size * (1f - 0.4f * out)
        rotate(side * 80f * fade, at) {
            flameTongue(at, length, MoRageOuter.faded(fade))
            flameTongue(at, length * 0.74f, MoPropOrange.faded(fade), MoPropYellow.faded(fade))
        }
    }

    override val sparkles get() = false
}

/** Sulking: he boots a pebble off one side of the picture, and it rolls back in from the other and bumps his foot. */
private object PebbleComesBack : MoAct("angry_pebble_comes_back", MoOccasion.Angry, intensity = MoIntensity.Mild) {
    // One half of the loop: a kick, the thing's way round, and the bump against the other foot.
    private const val HALF = 3f
    private const val GONE = 0.4f
    private const val BACK = 1.6f
    private const val BUMP = 2.45f
    private const val SETTLED = BUMP + 0.25f

    // Where it leaves the picture and comes back into it, from the centre. It has faded out by then.
    private const val EDGE = 150f

    /** One kick: the [side] of the foot that struck, and the seconds [since]. */
    private class Kick(val side: Float, val since: Float) {
        val strike = ease(span(since, 0f, 0.07f))
        val lowering = 1f - ease(span(since, 0.15f, 0.42f))
        val windUp = ease(span(since, HALF - 0.4f, HALF - 0.1f))
        val outrage = window(since, BUMP, BUMP + 0.08f, BUMP + 0.3f, BUMP + 0.55f)
    }

    private fun kick(t: Float, v: MoVariant): Kick {
        val first = if (v.chance(50, salt = 3)) 1f else -1f
        // Shifted so that it opens a moment before the first kick, with the leg already drawn back.
        val now = cycle(t, 2f * HALF, offset = 1f - 0.25f / (2f * HALF)) * 2f * HALF
        return if (now < HALF) Kick(first, now) else Kick(-first, now - HALF)
    }

    /** Where it lies beside the foot on [side], a little clear of it, and where it touches that foot. */
    private fun rest(side: Float) = footX(side) + side * 40f

    private fun touch(side: Float) = footX(side) + side * 35f

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val kick = kick(t, v)
        val side = kick.side
        val since = kick.since
        val kicking = MoLeg(lift = mix(4f, 6f, kick.strike) * kick.lowering, swing = mix(-8f, 16f, kick.strike) * kick.lowering)
        val drawnBack = MoLeg(lift = 4f * kick.windUp, swing = -8f * kick.windUp)

        // He watches it go, sulks at nothing in particular, and looks down only when it bumps him.
        val gaze = mix(
            mix(mix(Offset(side * 0.55f, 1f), Offset(side * 0.9f, 0.35f), ease(span(since, 0.05f, 0.4f))), Offset(0f, 0.15f), ease(span(since, 1f, 1.3f))),
            Offset(-side * 0.55f, 1f),
            ease(span(since, BUMP, BUMP + 0.12f)),
        )
        val sulk = window(since, 1.1f, 1.4f, BUMP, BUMP + 0.08f)
        val eye = MoEye(lid = mix(0.8f, 1f, kick.outrage), lidDrop = 0.35f * sulk, iris = 0.56f, scale = 1f + 0.06f * kick.outrage, gaze = gaze)
        val idle = moIdle(t, energy = 1.2f)
        return idle.copy(
            leftEye = eye,
            rightEye = eye,
            brows = MoBrows(MoBrowKind.Angry),
            mouth = if (since < 0.2f || since > BUMP + 0.05f) MoMouth.Gritted else MoMouth.Pout,
            leftArm = MoArm.Hip,
            rightArm = MoArm.Hip,
            legs = if (side > 0f) MoLegs(left = drawnBack, right = kicking) else MoLegs(left = kicking, right = drawnBack),
            flare = 4f + 14f * kick.outrage,
            // He leans over the thing while he winds up, and away from it as he strikes.
            tilt = side * mix(2f, -3f, kick.strike) * kick.lowering - side * 2f * kick.windUp,
            offset = Offset(0f, -3f * kick.strike * kick.lowering),
            shake = wave(t, 0.08f) * 1.5f * kick.outrage,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) = fuming(rig, 0.55f + 0.35f * kick(t, v).outrage)

    override fun DrawScope.foreground(t: Float, v: MoVariant) {
        val kick = kick(t, v)
        val side = kick.side
        val since = kick.since

        val dust = span(since, 0.02f, 0.42f)
        if (dust > 0f && dust < 1f) {
            puff(Offset(footX(side) + side * (24f + 10f * dust), FEET - 6f - 10f * dust), 7f + 5f * dust, MoPropGrey.faded(0.8f * (1f - dust)))
        }

        // It skids off one side still moving, and comes back in from the other to bump the far foot.
        val from = rest(side)
        val to = rest(-side)
        val x: Float
        val seen: Float
        val home: Float
        when {
            since < GONE -> {
                val glide = (since / GONE).let { it * (1.6f - 0.6f * it) }
                x = mix(from, CENTRE + side * EDGE, glide)
                seen = 1f - span(glide, 0.65f, 1f)
                home = from
            }
            since < BACK -> return
            since < BUMP -> {
                val glide = span(since, BACK, BUMP).let { it * (1.4f - 0.4f * it) }
                x = mix(CENTRE - side * EDGE, touch(-side), glide)
                seen = span(glide, 0f, 0.15f)
                home = to
            }
            else -> {
                x = mix(touch(-side), to, ease(span(since, BUMP, SETTLED)))
                seen = 1f
                home = to
            }
        }
        val hop = 4f * bump(since, BUMP, SETTLED)
        // It turns with the floor it has covered, and lies the same way up whenever it is at rest.
        val turn = (x - home) / 13f
        kicked(v, x, hop, turn, seen)
    }

    private fun DrawScope.kicked(v: MoVariant, x: Float, hop: Float, turn: Float, seen: Float) {
        oval(Offset(x, FEET + 1f), 17f, 4f, MoPropShadow.faded(seen))
        val spot = Offset(cos(turn - 2.4f), sin(turn - 2.4f))
        when (v.pick(3, salt = 1)) {
            0 -> {
                val at = Offset(x, FEET - 11f - hop)
                oval(at, 16f, 11.5f, MoPropGreyDark.faded(seen))
                oval(at - Offset(0f, 3f), 14.5f, 9f, MoPropGrey.faded(seen))
                drawCircle(MoPropGreyDark.faded(seen), 3.5f, at - Offset(0f, 3f) + Offset(spot.x * 8f, spot.y * 5f))
            }
            1 -> {
                val (colour, ledge) = v.of(listOf(MoPropSky to MoPropBlue, MoPropPink to MoPropPinkDark), salt = 2)
                val at = Offset(x, FEET - 14f - hop)
                drawCircle(ledge.faded(seen), 14f, at)
                drawCircle(colour.faded(seen), 12.5f, at - Offset(0f, 2f))
                drawCircle(MoPropWhite.faded(0.85f * seen), 3.5f, at - Offset(0f, 2f) + spot * 7f)
            }
            else -> {
                val label = v.of(listOf(MoPropRed, MoPropBlueDark, MoPropYellow), salt = 2)
                val at = Offset(x, FEET - 13f - hop)
                rotate(turn * 180f / PI.toFloat(), at) {
                    chunk(at, 28f, 20f, MoPropSteel.faded(seen), MoPropGreyDark.faded(seen), corner = 6f, drop = 5f)
                    block(at - Offset(0f, 2.5f), 28f, 8f, label.faded(seen), corner = 1f)
                }
            }
        }
    }

    override val ownsTheFloor get() = true
}

/** Furious: he bellows at the sky with both arms up until the shout runs out, sags, fills up again, and goes on. */
private object ShoutingHimselfEmpty : MoAct("angry_shouting_himself_empty", MoOccasion.Angry, intensity = MoIntensity.Strong) {
    private const val DEFLATED = 0.8f
    private const val BREATHES_IN = 1.3f
    private const val REST = 2.4f

    /** The seconds since the bellow began, and how long today's bellow lasts. */
    private class Shout(val now: Float, val bellow: Float) {
        val spent = ease(span(now, bellow, bellow + DEFLATED))
        val filling = ease(span(now, bellow + BREATHES_IN, bellow + REST))
        val breathingIn = now >= bellow + BREATHES_IN

        // The breath in hands over to the bellow with the arms already up, and the bellow takes a moment to take hold.
        val settled = ease(span(now, 0f, 0.15f))
        val air = if (breathingIn) filling else 1f - spent
        val loud = (1f - spent) * settled * if (breathingIn) 0f else 1f
    }

    private fun shout(t: Float, v: MoVariant): Shout {
        val bellow = v.between(2.2f, 3.2f, salt = 3)
        val period = bellow + REST
        // Shifted so that it opens in full bellow.
        return Shout(cycle(t, period, offset = 0.4f / period) * period, bellow)
    }

    /** One value through the whole loop: [roar] while he bellows, [limp] once he is empty, [gasp] at the top of the breath in. */
    private fun <T> Shout.through(roar: T, limp: T, gasp: T, mix: (T, T, Float) -> T): T =
        if (breathingIn) mix(limp, gasp, filling) else mix(mix(gasp, roar, settled), limp, spent)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val shout = shout(t, v)
        val wild = moIdle(t, energy = 2.2f)
        val tired = moIdle(t, energy = 0.4f)
        val up = MoArm(MoArm.Up.reach + Offset(wave(t, 0.11f) * 3f * shout.loud, 0f), MoArm.Up.bend)
        val arm = mix(MoArm.Down, up, shout.air)
        val eye = shout.through(
            roar = MoEye(lid = 1f, lidDrop = 0.3f, iris = 0.56f, gaze = Offset(0f, -0.6f)),
            limp = MoEye(lid = 0.3f, lidDrop = 0.5f, iris = 0.66f, gaze = Offset(0f, 0.5f)),
            gasp = MoEye(iris = 0.6f, scale = 1.05f, gaze = Offset(0f, -0.3f)),
            mix = ::mix,
        )
        val voice = 1f - span(shout.now, shout.bellow, shout.bellow + 0.55f)
        return wild.copy(
            palette = MoPalettes.Rage,
            leftEye = eye,
            rightEye = eye,
            brows = when {
                shout.breathingIn -> MoBrows(MoBrowKind.Angry, lift = 5f * shout.filling)
                shout.spent < 0.7f -> MoBrows(MoBrowKind.Angry, lift = 5f * (1f - shout.settled))
                else -> MoBrows()
            },
            mouth = when {
                shout.breathingIn -> MoMouth.Oh(mix(4f, 9f, shout.filling))
                // A round roar rather than a grin, which would make the bellow look pleased.
                voice > 0.35f -> MoMouth.Yawn(open = voice)
                else -> MoMouth.Oh(mix(4f, 11f, voice / 0.35f))
            },
            leftArm = arm,
            rightArm = arm,
            sway = mix(tired.sway, wild.sway, shout.air),
            flare = shout.through(roar = 30f + 6f * pulse(t, 0.3f), limp = -4f, gasp = 20f, mix = ::mix),
            squash = wild.squash + shout.through(roar = 0.05f, limp = -0.12f, gasp = 0.09f, mix = ::mix),
            tilt = mix(3f, -2f, shout.air),
            shake = wave(t, 0.085f) * 1.8f * shout.loud,
            blinks = false,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val shout = shout(t, v)
        val colour = v.of(listOf(MoPropYellow, MoPropOrange, MoPropWhite), salt = 2)
        val wedges = 2 + v.pick(2, salt = 1)
        val volume = window(shout.now, 0f, 0.12f, shout.bellow - 0.25f, shout.bellow + 0.4f)
        if (volume > 0.02f && !shout.breathingIn) {
            for (side in listOf(-1f, 1f)) {
                // Up beside his flame, where there is room either side of both bodies and his raised mittens are not.
                val origin = Offset(CENTRE + side * 96f, rig.body.eyeY - 95f)
                for (index in 0 until wedges) {
                    val angle = (if (wedges == 3) 15f + 30f * index else 25f + 35f * index) * PI.toFloat() / 180f
                    val along = Offset(side * cos(angle), -sin(angle))
                    val across = Offset(-along.y, along.x)
                    // They start well away from their shared origin, so the fan is separate rays and not one crumpled shape.
                    val near = origin + along * 20f
                    val far = origin + along * (20f + 34f * volume * (0.8f + 0.2f * wave(t, 0.14f + 0.02f * index)))
                    val wide = across * (7f * volume)
                    shape(colour.faded(volume), near.x to near.y, (far + wide).x to (far + wide).y, (far - wide).x to (far - wide).y)
                }
            }
        }
        // The last of his air.
        val sigh = span(shout.now, shout.bellow + 0.55f, shout.bellow + 1.05f)
        if (sigh > 0f && sigh < 1f) {
            puff(rig.mouth + Offset(14f + 16f * sigh, -6f * sigh), 4f + 3f * sigh, MoPropWhite.faded(1f - sigh))
        }
    }

    override val sparkles get() = false
}

internal val angryActs2: List<MoAct> = listOf(
    PacingARut,
    CountingToTen,
    LiftOff,
    FireHiccups,
    PebbleComesBack,
    ShoutingHimselfEmpty,
)
