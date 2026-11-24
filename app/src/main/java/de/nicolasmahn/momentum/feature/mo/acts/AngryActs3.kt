package de.nicolasmahn.momentum.feature.mo.acts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import de.nicolasmahn.momentum.core.ui.theme.MoFlameBelly
import de.nicolasmahn.momentum.core.ui.theme.MoPropBlueDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropBrown
import de.nicolasmahn.momentum.core.ui.theme.MoPropCharcoal
import de.nicolasmahn.momentum.core.ui.theme.MoPropCream
import de.nicolasmahn.momentum.core.ui.theme.MoPropGold
import de.nicolasmahn.momentum.core.ui.theme.MoPropGrey
import de.nicolasmahn.momentum.core.ui.theme.MoPropGreyDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropInk
import de.nicolasmahn.momentum.core.ui.theme.MoPropPink
import de.nicolasmahn.momentum.core.ui.theme.MoPropPinkDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropPurple
import de.nicolasmahn.momentum.core.ui.theme.MoPropPurpleDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropSand
import de.nicolasmahn.momentum.core.ui.theme.MoPropShadow
import de.nicolasmahn.momentum.core.ui.theme.MoPropSky
import de.nicolasmahn.momentum.core.ui.theme.MoPropSteel
import de.nicolasmahn.momentum.core.ui.theme.MoPropTeal
import de.nicolasmahn.momentum.core.ui.theme.MoPropTealDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropWhite
import de.nicolasmahn.momentum.core.ui.theme.MoPropWood
import de.nicolasmahn.momentum.core.ui.theme.MoPropWoodDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropYellow
import de.nicolasmahn.momentum.core.ui.theme.MoPropYellowDark
import de.nicolasmahn.momentum.core.ui.theme.MoRageBelly
import de.nicolasmahn.momentum.core.ui.theme.MoRageMiddle
import de.nicolasmahn.momentum.core.ui.theme.MoSmoke
import de.nicolasmahn.momentum.core.ui.theme.MoTear
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
import de.nicolasmahn.momentum.feature.mo.MoLeg
import de.nicolasmahn.momentum.feature.mo.MoLegs
import de.nicolasmahn.momentum.feature.mo.MoMouth
import de.nicolasmahn.momentum.feature.mo.MoPalettes
import de.nicolasmahn.momentum.feature.mo.MoPose
import de.nicolasmahn.momentum.feature.mo.MoRig
import de.nicolasmahn.momentum.feature.mo.grippingMitten
import de.nicolasmahn.momentum.feature.mo.angerMark
import de.nicolasmahn.momentum.feature.mo.block
import de.nicolasmahn.momentum.feature.mo.chunk
import de.nicolasmahn.momentum.feature.mo.curve
import de.nicolasmahn.momentum.feature.mo.cycle
import de.nicolasmahn.momentum.feature.mo.drop
import de.nicolasmahn.momentum.feature.mo.ease
import de.nicolasmahn.momentum.feature.mo.flameTongue
import de.nicolasmahn.momentum.feature.mo.fading
import de.nicolasmahn.momentum.feature.mo.floorShadow
import de.nicolasmahn.momentum.feature.mo.limb
import de.nicolasmahn.momentum.feature.mo.moIdle
import de.nicolasmahn.momentum.feature.mo.oval
import de.nicolasmahn.momentum.feature.mo.puff
import de.nicolasmahn.momentum.feature.mo.pulse
import de.nicolasmahn.momentum.feature.mo.shape
import de.nicolasmahn.momentum.feature.mo.smoke
import de.nicolasmahn.momentum.feature.mo.twitch
import de.nicolasmahn.momentum.feature.mo.wave
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * Angry, third batch: his own heat put to work. A kettle that boils in his fist, a clock he cannot
 * stop pointing at, a crater he erupts from, a roar of fire, tears that never make it down his
 * cheek, and a snail that takes its time.
 */

private fun mix(from: Float, to: Float, amount: Float): Float = from + (to - from) * amount

private fun mix(from: Offset, to: Offset, amount: Float): Offset = Offset(mix(from.x, to.x, amount), mix(from.y, to.y, amount))

/** How far up his flame the rig's tip sits, squared: the share of a flare that reaches it. */
private fun tipShare(rig: MoRig): Float {
    val body = rig.body
    val height = (body.pivot - (body.top + 12f)) / max(body.pivot - body.top, 1f)
    return height * height
}

/** Mildly cross: he holds a kettle out in one fist, glaring at us, and it boils from his own heat. */
private object KettleInHisFist : MoAct("angry_kettle_in_his_fist", MoOccasion.Angry, intensity = MoIntensity.Mild) {
    private const val BURST_EVERY = 2.2f
    private const val BURST_LASTS = 0.7f
    private const val PUFF_LIVES = 1.1f

    // A tenth of the way into the loop at t = 0, so the first frame already has steam in the air.
    private const val START = 0.1f

    private fun side(v: MoVariant) = if (v.chance(50, salt = 2)) 1f else -1f

    private fun burst(t: Float) = twitch(t, BURST_EVERY, beat = BURST_LASTS, offset = START)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val s = side(v)
        val burst = burst(t)
        val holding = MoArm(Offset(48f, -8f - 3f * burst), bend = 0.18f)
        val free = if (v.chance(50, salt = 4)) MoArm.Hip else MoArm(Offset(8f, 34f), bend = 0.05f)
        val glare = MoEye(lid = 0.7f, iris = 0.6f, gaze = Offset(-s * 0.2f, 0.3f))
        return moIdle(t, energy = 1.1f).copy(
            leftEye = glare,
            rightEye = glare,
            brows = MoBrows(MoBrowKind.Angry),
            mouth = if (burst > 0.3f) MoMouth.Gritted else MoMouth.Flat(12f),
            leftArm = if (s < 0f) holding else free,
            rightArm = if (s > 0f) holding else free,
            // He stands a little aside, so the kettle hangs clear of him and its steam stays in the picture.
            offset = Offset(-s * 32f, 0f),
            flare = 3f + 10f * burst,
            shake = wave(t, 0.085f) * (0.3f + 0.9f * burst),
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val s = side(v)
        val (colour, dark) = v.of(
            listOf(MoPropTeal to MoPropTealDark, MoPropSky to MoPropBlueDark, MoPropPink to MoPropPinkDark, MoPropSteel to MoPropGreyDark),
            salt = 1,
        )
        val burst = burst(t)
        val hand = if (s > 0f) rig.rightHand else rig.leftHand
        val kettle = hand + Offset(wave(t, 0.09f) * 0.8f, 40f)
        val spout = kettle + Offset(s * 31f, -17f)
        val lidHop = 2f * abs(wave(t, 0.09f)) + 5f * burst

        limb(kettle + Offset(s * 16f, -1f), spout, colour, 10f)
        chunk(kettle, 46f, 34f, colour, dark, corner = 15f)
        block(kettle + Offset(0f, -20f - lidHop), 30f, 7f, dark, corner = 3.5f)
        drawCircle(MoPropCharcoal, 4f, kettle + Offset(0f, -26f - lidHop))
        curve(kettle + Offset(-17f, -15f), hand + Offset(0f, -14f), kettle + Offset(17f, -15f), MoPropCharcoal, 5.5f)
        // His mitten closes over the handle, so the kettle hangs from his fist and not in front of it.
        grippingMitten(rig, s)

        val since = cycle(t, BURST_EVERY, offset = START) * BURST_EVERY
        for (index in 0 until 2 + v.pick(2, salt = 3)) {
            val u = (since - index * 0.2f) / PUFF_LIVES
            if (u in 0f..1f) {
                val at = spout + Offset(s * (3f - 10f * u) + sin(u * 5f + index) * 3f, -8f - 76f * u)
                puff(at, 7f + 10f * u, MoPropWhite.copy(alpha = 1f - u * u))
            }
        }
        // Between bursts it still simmers.
        val wisp = cycle(t, 1.1f)
        puff(spout + Offset(s * 3f, -6f - 26f * wisp), 5f, MoPropWhite.copy(alpha = 0.5f * (1f - wisp)))

        fumingMark(rig, 0.6f + 0.15f * burst, -s)
    }

    override val sparkles get() = false
}

/** It is late and he wants us to know: he jabs a mitten at a ringing alarm clock, over and over. */
private object LookAtThatClock : MoAct("angry_look_at_that_clock", MoOccasion.Angry) {
    private const val JAB_EVERY = 1.2f
    private const val RING_EVERY = 0.6f
    private const val START = 0.1f
    private const val STANDS_ASIDE = 20f
    private const val TABLE_TOP = FEET - 51f
    private const val HUB = TABLE_TOP - 29f

    private fun side(v: MoVariant) = if (v.chance(50, salt = 1)) 1f else -1f

    private fun jab(t: Float) = twitch(t, JAB_EVERY, beat = 0.35f, offset = START)

    /** 1 while his eyes are on the clock, 0 while they are on us: one glance each way every two jabs. */
    private fun onTheClock(t: Float): Float {
        val glance = cycle(t, JAB_EVERY * 2f, offset = START)
        return ease(glance / 0.05f) * (1f - ease((glance - 0.5f) / 0.05f))
    }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val s = side(v)
        val jab = jab(t)
        // The nightstand lifts the clock to the height of his shoulder on either body, so a straight
        // jab points at it, and the mitten stops at its rim: he raps on it, he does not cover it.
        val pointing = MoArm(Offset(18f + 14f * jab, HUB - MoBodies.of(body).shoulderY), bend = 0.15f)
        val eye = MoEye(lid = 1f, iris = 0.56f, gaze = mix(Offset(0f, 0.1f), Offset(s * 0.9f, 0.5f), onTheClock(t)))
        return moIdle(t, energy = 1.6f).copy(
            leftEye = eye,
            rightEye = eye,
            brows = MoBrows(MoBrowKind.Angry),
            mouth = if (jab > 0.3f) MoMouth.Oh(8f + 6f * jab) else MoMouth.Gritted,
            leftArm = if (s < 0f) pointing else MoArm.Hip,
            rightArm = if (s > 0f) pointing else MoArm.Hip,
            flare = 8f + 8f * jab,
            // He stands a little aside, which is the room the nightstand needs.
            offset = Offset(-s * STANDS_ASIDE, 0f),
            tilt = s * 2f * jab,
            shake = wave(t, 0.085f) * 1.2f,
            blinks = false,
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val s = side(v)
        val (colour, dark) = v.of(
            listOf(MoPropTeal to MoPropTealDark, MoPropPink to MoPropPinkDark, MoPropSky to MoPropBlueDark, MoPropYellow to MoPropYellowDark),
            salt = 2,
        )
        val stands = CENTRE + s * 129f
        val hop = 4f * twitch(t, RING_EVERY, beat = 0.2f)
        // Each rap of his mitten knocks it a little further away.
        val hub = Offset(stands + wave(t, 0.06f) * 1.5f + s * 2f * jab(t), HUB - hop)

        floorShadow(stands, 30f, MoPropShadow)
        chunk(Offset(stands, (TABLE_TOP + FEET) / 2f), 52f, FEET - TABLE_TOP - 5f, MoPropWood, MoPropWoodDark, corner = 6f)
        block(Offset(stands, TABLE_TOP + 20f), 36f, 14f, MoPropWoodDark, corner = 4f)
        drawCircle(MoPropGold, 3f, Offset(stands, TABLE_TOP + 20f))
        oval(Offset(stands, TABLE_TOP + 1f), 22f - hop, 3f, MoPropShadow)
        for (foot in listOf(-1f, 1f)) limb(hub + Offset(foot * 12f, 16f), hub + Offset(foot * 19f, 29f), MoPropCharcoal, 6f)
        val hammer = hub + Offset(wave(t, 0.06f) * 7f, -37f)
        limb(hub + Offset(0f, -20f), hammer, MoPropCharcoal, 4f)
        drawCircle(MoPropCharcoal, 4f, hammer)
        for (bell in listOf(-1f, 1f)) oval(hub + Offset(bell * 16f + wave(t + bell * 0.015f, 0.06f) * 2f, -26f), 10f, 7f, MoPropGold)
        drawCircle(dark, 23f, hub + Offset(0f, 4f))
        drawCircle(colour, 23f, hub)
        drawCircle(MoPropCream, 17f, hub)

        // A blank face: the hands alone say how fast the time is going. The hour hand keeps a
        // quarter turn from the minute hand, so the two never line up into a stroke that reads as a digit.
        val minutes = (cycle(t, JAB_EVERY) + v.between(0f, 1f, salt = 4)) * 2f * PI.toFloat()
        val hours = minutes + PI.toFloat() / 2f
        limb(hub, hub + Offset(sin(hours), -cos(hours)) * 8.5f, MoPropInk, 4f)
        limb(hub, hub + Offset(sin(minutes), -cos(minutes)) * 13f, MoPropInk, 4f)
        drawCircle(MoPropInk, 3f, hub)

        // The ringing ripples away from each bell, and the ripples hand over so it never starts again.
        val ripples = 2 + v.pick(2, salt = 3)
        val u = cycle(t, RING_EVERY)
        for (bell in listOf(-1f, 1f)) {
            val out = Offset(bell * 0.3f, -0.95f)
            val along = Offset(out.y, -out.x)
            for (index in 0 until ripples) {
                val at = hub + out * (42f + (index + u) * 6f)
                val alpha = when (index) {
                    0 -> u
                    ripples - 1 -> 1f - u
                    else -> 1f
                }
                curve(at + along * 6f, at + out * 5f, at - along * 6f, MoPropYellow.copy(alpha = alpha), 3.5f)
            }
        }
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        fumingMark(rig, 0.8f + 0.2f * jab(t), -side(v))
    }

    override val ownsTheFloor get() = true
}

/** Furious: he squats in a crater and is the eruption himself, shooting up and spitting lava. */
private object LittleVolcano : MoAct("angry_little_volcano", MoOccasion.Angry, intensity = MoIntensity.Strong) {
    private const val ERUPTION = 0.5f
    private const val FLIGHT = 1.5f
    private const val REST_FLARE = 8f
    private const val PEAK_FLARE = 34f
    private const val BURP_FLARE = 10f

    // The top of the crater's near wall, which hides his legs and nothing above them.
    private const val RIM = 388f

    private fun every(v: MoVariant) = v.between(2f, 3f, salt = 6)

    /** Seconds since the last eruption began. At t = 0 he is 0.3 s into one, at the top of it. */
    private fun since(t: Float, v: MoVariant): Float {
        val period = every(v)
        return cycle(t, period, offset = 0.3f / period) * period
    }

    private fun erupt(since: Float) = if (since < ERUPTION) sin(since / ERUPTION * PI.toFloat()) else 0f

    /** A small belch halfway to the next eruption, which sends the straggler up. */
    private fun burp(since: Float, v: MoVariant): Float {
        val into = since - every(v) / 2f + 0.1f
        return if (into in 0f..0.3f) sin(into / 0.3f * PI.toFloat()) else 0f
    }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val since = since(t, v)
        val erupt = erupt(since)
        // The rumble builds toward the next eruption and lets go just after it.
        val build = ease((since / every(v) - 0.5f) * 2f)
        val release = 1f - ease(since / 0.7f)
        val eye = MoEye(lid = 1f - 0.3f * erupt, iris = 0.56f - 0.1f * erupt, gaze = Offset(0f, -0.5f * erupt))
        val base = moIdle(t, energy = 1.4f)
        return base.copy(
            palette = MoPalettes.Rage,
            leftEye = eye,
            rightEye = eye,
            brows = MoBrows(MoBrowKind.Angry, lift = 4f * erupt),
            mouth = if (erupt > 0.3f) MoMouth.Oh(9f) else MoMouth.Gritted,
            leftArm = MoArm(Offset(-4f, 26f - 8f * erupt), bend = 0.6f),
            rightArm = MoArm(Offset(-4f, 26f - 8f * erupt), bend = 0.6f),
            flare = REST_FLARE + PEAK_FLARE * erupt + BURP_FLARE * burp(since, v),
            squash = base.squash - 0.07f + 0.10f * erupt,
            shake = wave(t, 0.09f) * (0.6f + 2.2f * max(build, release)),
            blinks = false,
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val rock = rock(v)
        // Two rocky peaks flank him and fall away steeply, so he is what comes out of the mountain.
        val flank = listOf(
            -42f to 425f, -42f to 416f, -31f to 394f, -24f to 390f, -13f to 354f, -4f to 326f, 8f to 322f, 20f to 338f, 44f to 358f,
        )
        shape(rock, *(flank + flank.reversed().map { (x, y) -> 2f * CENTRE - x to y }).toTypedArray())
        // The vent he rises from, which the rim in the foreground closes.
        oval(Offset(CENTRE, RIM), 102f, 12f, MoPropInk)

        // Pebbles shake loose down one flank; a run of lava has long since found the other.
        val pebbleSide = if (v.chance(50, salt = 4)) 1f else -1f
        fun onFlank(x: Float, y: Float, side: Float) = Offset(CENTRE - side * (x - CENTRE), y)
        val pebbles = 3 + v.pick(2, salt = 7)
        for (index in 0 until pebbles) {
            val u = cycle(t, 1.8f, offset = index.toFloat() / pebbles)
            val down = mix(Offset(-5f, 326f), Offset(-35f, 406f), u)
            val at = onFlank(down.x, down.y - 5f * abs(sin(u * 9f)), pebbleSide)
            drawCircle(MoPropGrey.copy(alpha = min(1f, u * 8f) * (1f - u)), 3.5f, at)
        }
        val ooze = 4f * pulse(t, 2.6f)
        val drip = onFlank(-21f, 374f + ooze, -pebbleSide)
        curve(onFlank(6f, 324f, -pebbleSide), onFlank(-8f, 346f, -pebbleSide), drip, MoRageMiddle, 10f)
        drawCircle(MoRageMiddle, 6.5f, drip)
        drawCircle(MoRageBelly, 2.5f, drip)
        if (v.chance(50, salt = 5)) smoke(onFlank(4f, 318f, pebbleSide), cycle(t, 3.8f))
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val since = since(t, v)
        val share = tipShare(rig)
        // Where his tip is at the height of an eruption. Lava leaves from there and not from the
        // tip itself, which drops back long before a blob has landed.
        val vent = Offset(rig.tip.x, rig.body.top + 12f - (REST_FLARE + PEAK_FLARE) * share)
        val lean = if (v.chance(50, salt = 3)) 1f else -1f
        for (index in 0 until 2 + v.pick(3, salt = 2)) {
            val dir = if (index == 1) -lean else lean
            lava(vent, (since - 0.12f - 0.07f * index) / FLIGHT, dir * (36f + 16f * index), 10f)
        }
        val burpTip = Offset(rig.tip.x, rig.body.top + 12f - (REST_FLARE + BURP_FLARE) * share)
        lava(burpTip, (since - every(v) / 2f).mod(every(v)) / FLIGHT, -lean * 40f, 7.5f)

        val ash = since - 0.25f
        if (ash in 0f..1f) {
            puff(vent + Offset(34f + 10f * ash, 4f - 30f * ash), 9f + 6f * ash, MoPropGrey.copy(alpha = 0.8f * min(1f, ash * 8f) * (1f - ash)))
        }
        angerMark(Offset(CENTRE + 74f, rig.body.top + 96f), 0.85f + 0.15f * erupt(since))
    }

    /** One blob of his own fire, [u] of the way along its arc. It fades out high in the air, so nothing lands. */
    private fun DrawScope.lava(from: Offset, u: Float, across: Float, size: Float) {
        if (u !in 0f..1f) return
        val at = from + Offset(across * u, -176f * u + 276f * u * u)
        val alpha = 1f - u * u
        drawCircle(MoRageMiddle.copy(alpha = alpha), size * (1f - 0.35f * u), at)
        drawCircle(MoRageBelly.copy(alpha = alpha), size * (0.5f - 0.18f * u), at)
    }

    override fun DrawScope.foreground(t: Float, v: MoVariant) {
        // The near wall of the crater stands up to his knees: he is in the mountain, not in front of it.
        shape(rock(v), -42f to 425f, -42f to 416f, -26f to 398f, 14f to RIM, 216f to RIM, 256f to 398f, 272f to 416f, 272f to 425f)
        // Lava has slopped over the rim on the side the run is on, and creeps down the front.
        val side = if (v.chance(50, salt = 4)) -1f else 1f
        val lip = Offset(CENTRE + side * 66f, RIM - 1f)
        val drip = lip + Offset(side * 9f, 16f + 4f * pulse(t, 2.6f))
        oval(lip, 17f, 6f, MoRageMiddle)
        limb(lip + Offset(side * 5f, 0f), drip, MoRageMiddle, 11f)
        drawCircle(MoRageMiddle, 7.5f, drip)
        drawCircle(MoRageBelly, 3f, drip)
        limb(Offset(-39f, 428f), Offset(269f, 428f), MoPropInk, 6f)
    }

    private fun rock(v: MoVariant) = v.of(listOf(MoPropCharcoal, MoPropGreyDark, MoPropBrown), salt = 1)

    override val ownsTheFloor get() = true
    override val sparkles get() = false
}

/** Furious: he rears back, lunges, and roars a jet of fire at nothing at all, then stands there smoking. */
private object DragonBreath : MoAct("angry_dragon_breath", MoOccasion.Angry, intensity = MoIntensity.Strong) {
    private const val EVERY = 4.2f
    private const val BLOBS = 18

    private fun side(v: MoVariant) = if (v.chance(50, salt = 1)) 1f else -1f

    private fun twice(v: MoVariant) = v.chance(40, salt = 2)

    /** Where in the loop he is: wind-up, roar, simmer. At t = 0 he is mid-roar on either kind of day. */
    private fun at(t: Float, v: MoVariant) = cycle(t, EVERY, offset = if (twice(v)) 0.27f else 0.35f)

    private fun windUp(c: Float) = ease(c / 0.10f) * (1f - ease((c - 0.15f) / 0.04f))

    private fun lunge(c: Float) = ease((c - 0.15f) / 0.05f) * (1f - ease((c - 0.58f) / 0.08f))

    private fun burst(c: Float, from: Float, to: Float) = ease((c - from) / 0.05f) * (1f - ease((c - to + 0.06f) / 0.06f))

    /** How much fire is coming out, 0 to 1. */
    private fun jet(c: Float, v: MoVariant) =
        if (twice(v)) burst(c, 0.18f, 0.35f) + burst(c, 0.43f, 0.60f) else burst(c, 0.18f, 0.60f)

    private fun simmer(c: Float) = (1f - windUp(c) - lunge(c)).coerceIn(0f, 1f)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val s = side(v)
        val c = at(t, v)
        val wind = windUp(c)
        val lunge = lunge(c)
        val rest = simmer(c)
        val jet = jet(c, v)
        fun arm(windUp: MoArm, roaring: MoArm) = MoArm(
            reach = MoArm.Hip.reach * rest + windUp.reach * wind + roaring.reach * lunge,
            bend = MoArm.Hip.bend * rest + windUp.bend * wind + roaring.bend * lunge,
        )
        val pulledBack = MoArm(Offset(-2f, 22f), bend = 0.7f)
        val near = arm(pulledBack, MoArm.Down)
        val far = arm(pulledBack, MoArm(Offset(24f, -22f), bend = 0.7f))
        val eye = MoEye(
            lid = 0.9f - 0.3f * wind + 0.1f * lunge,
            iris = 0.6f - 0.1f * lunge,
            scale = 1f + 0.06f * wind,
            gaze = Offset(s * 0.9f * lunge, 0.2f * lunge),
        )
        val base = moIdle(t, energy = 1.5f)
        return base.copy(
            palette = MoPalettes.Rage,
            leftEye = eye,
            rightEye = eye,
            brows = MoBrows(MoBrowKind.Angry),
            mouth = when {
                jet > 0.01f -> MoMouth.Oh(size = 6f + 10f * jet)
                wind > 0.5f -> MoMouth.Pout
                else -> MoMouth.Gritted
            },
            leftArm = if (s < 0f) near else far,
            rightArm = if (s > 0f) near else far,
            // He rears away from the side he roars at, which also leaves the fire room inside the frame.
            offset = Offset(-s * (30f * wind + 26f * lunge), 0f),
            tilt = s * (-4f * wind + 10f * lunge),
            squash = base.squash + 0.09f * wind - 0.03f * lunge,
            flare = 8f + 14f * lunge,
            shake = wave(t, 0.08f) * 1.5f * jet,
            blinks = false,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val s = side(v)
        val c = at(t, v)
        val jet = jet(c, v)
        if (jet > 0.01f) {
            val from = rig.mouth + Offset(s * (2f + 5f * jet), 3f)
            val reach = v.between(120f, 140f, salt = 3) * jet
            // Aimed a little upward in his own frame, because he is leaning into it.
            fun blob(index: Int): Pair<Offset, Float> {
                val grow = index / BLOBS.toFloat()
                val along = reach * 0.8f * grow
                return from + Offset(s * along, -along * 0.06f + wave(t, 0.1f + 0.004f * index) * 4f * grow) to grow
            }
            // Three nested flames, like him. The blobs sit closer than the thinnest layer is wide, and
            // each layer is drawn whole, so the jet is one fat shape and not a row of beads.
            for ((colour, share) in listOf(MoRageMiddle to 1f, MoRageBelly to 0.68f, MoFlameBelly to 0.36f)) {
                for (index in 0..BLOBS) {
                    val (at, grow) = blob(index)
                    val size = share * (0.5f + 0.5f * jet) * ease(jet / 0.25f)
                    // The near end stays slim enough to pass under his eye.
                    oval(at, (11f + 20f * grow) * size, (8f + 21f * grow * grow) * size, colour)
                }
            }
            val end = blob(BLOBS).first
            for (index in 0..2) {
                val u = cycle(t, 0.5f, offset = index / 3f)
                val fade = (1f - u) * jet
                flameTongue(end + Offset(s * (18f + 14f * u), (index - 1) * 18f + 10f - 24f * u), 9f - 4f * u, MoRageMiddle.copy(alpha = fade), MoRageBelly.copy(alpha = fade))
            }
        }

        // While he simmers, smoke leaks from the corner of his mouth and drifts off under his eye,
        // out past his cheek, where it shows against the dark.
        val rest = simmer(c)
        for (index in 0..2) {
            val u = cycle(t, 2.1f, offset = index / 3f)
            val at = rig.mouth + Offset(s * (30f + 85f * u), 8f - 22f * u)
            puff(at, 7f + 9f * u, MoSmoke.copy(alpha = 0.9f * rest * min(1f, u * 6f) * (1f - u * u)))
        }
        fumingMark(rig, 0.8f, -s)
    }

    override val sparkles get() = false
}

/** So cross that tears come, but he is a flame: each one fizzes into steam before it is down his cheek. */
private object TooHotToCry : MoAct("angry_too_hot_to_cry", MoOccasion.Angry) {
    // The share of a tear's life it spends rolling; the rest of it is steam.
    private const val ROLLS = 0.45f

    private fun every(v: MoVariant) = v.between(2f, 3f, salt = 2)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val eye = MoEye(lid = 0.75f, iris = 0.64f, gaze = Offset(0f, -0.15f))
        val fist = MoArm(Offset(8f, 34f), bend = 0.05f)
        val base = moIdle(t, energy = 1.3f)
        return base.copy(
            leftEye = eye,
            rightEye = eye,
            brows = MoBrows(MoBrowKind.Angry),
            mouth = MoMouth.Gritted,
            leftArm = fist,
            rightArm = fist,
            flare = 5f + 5f * pulse(t, 0.8f),
            // A sob he will not admit to, every time a tear lets go.
            squash = base.squash - 0.025f * twitch(t, every(v) / 2f, beat = 0.3f, offset = 0.5f),
            shake = wave(t, 0.075f) * 1.6f,
            blinks = false,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val period = every(v)
        val leftFirst = v.chance(50, salt = 1)
        val steam = if (v.chance(50, salt = 3)) 10f else 14f
        val r = rig.body.eyeRadius
        for ((eye, side) in listOf(rig.leftEye to -1f, rig.rightEye to 1f)) {
            // The eyes take turns, so a tear and a puff are on screen at every moment, the first frame included.
            val u = cycle(t, period, offset = if ((side < 0f) == leftFirst) 0.2f else 0.7f)
            val wells = eye + Offset(side * r * 0.5f, r * 0.72f)
            val fizzes = wells + Offset(side * 3f, 26f)
            // The eye it came from stays brimful the whole time. The water lies along the bottom
            // of it, inside its rim and deepest in the middle, so it is in his eye and not across it.
            val rim = Path().apply { addOval(Rect(eye, r)) }
            val dry = Path().apply { addOval(Rect(eye.x - r * 1.49f, eye.y - r * 1.46f, eye.x + r * 1.49f, eye.y + r * 0.66f)) }
            drawPath(Path.combine(PathOperation.Difference, rim, dry), MoTear)
            if (u < ROLLS) {
                drop(mix(wells, fizzes, ease(u / ROLLS)), 7f * min(1f, u * 8f), MoTear)
            } else {
                val w = (u - ROLLS) / (1f - ROLLS)
                val spit = (1f - w * 8f).coerceIn(0f, 1f)
                for (tick in listOf(-1f, 1f)) {
                    val from = fizzes + Offset(tick * 9f, 10f)
                    limb(from, from + Offset(tick * 7f, -7f), MoPropWhite.copy(alpha = spit), 3.5f)
                }
                // It swings out round the eye before it rises, so it never crosses it.
                val at = fizzes + Offset(side * (30f * ease(w * 2f) + 12f * w), 6f - 86f * w)
                val size = steam * (0.6f + 0.6f * w)
                // As one shape: three circles each at their own alpha show where they overlap.
                fading(1f - w * w, Rect(at, size * 1.3f)) { puff(at, size, MoPropWhite) }
            }
        }
        angerMark(Offset(CENTRE + 74f, rig.body.top + 96f), 0.75f)
    }

    override val sparkles get() = false
}

/** Mildly cross: arms crossed, lids low, foot tapping, he watches a cheerful snail take forever. */
private object TheSnail : MoAct("angry_the_snail", MoOccasion.Angry, intensity = MoIntensity.Mild) {
    private const val FROM = -5f
    private const val TO = 235f
    private const val STEPS = 20
    private const val FADE = 35f
    private const val TAP = 0.24f

    private fun heading(v: MoVariant) = if (v.chance(50, salt = 2)) 1f else -1f

    /**
     * How far its rear and its front have come. It moves like an inchworm: the front stretches out
     * for half a step, then the rear catches up. At t = 0 it is mid-stretch beside him, fully faded
     * in and just short of his feet, so the first frame shows the whole snail and his raised foot.
     */
    private fun ends(t: Float, v: MoVariant): Pair<Float, Float> {
        val steps = cycle(t, v.between(40f, 55f, salt = 5), offset = 3.25f / STEPS) * STEPS
        val step = floor(steps)
        val into = steps - step
        val stride = (TO - FROM) / STEPS
        return (step + ease(into * 2f - 1f)) * stride to (step + ease(into * 2f)) * stride
    }

    private fun place(along: Float, v: MoVariant) = if (heading(v) > 0f) FROM + along else TO - along

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val (rear, front) = ends(t, v)
        val snail = place((rear + front) / 2f, v)
        val eye = MoEye(lid = 0.5f, lidDrop = 0.42f, iris = 0.6f, gaze = Offset(((snail - CENTRE) / 150f).coerceIn(-1f, 1f), 0.9f))
        // Five quick taps, then the foot rests. The first frame catches it in the air.
        val since = cycle(t, 2f, offset = 0.03f) * 2f
        val tap = if (since < TAP * 5f) max(0f, sin(since / TAP * 2f * PI.toFloat())) else 0f
        val tapping = MoLeg(lift = 7f * tap)
        val leftTaps = v.chance(50, salt = 4)
        // Folded over his belly, where they show against the yellow, one a little above the other.
        val fold = if (body == MoBodyKind.Low) 20f else 16f
        val upper = MoArm(Offset(-88f, fold), bend = 0.3f)
        val lower = MoArm(Offset(-88f, fold + 13f), bend = 0.3f)
        val base = moIdle(t, energy = 0.9f)
        return base.copy(
            leftEye = eye,
            rightEye = eye,
            brows = MoBrows(MoBrowKind.Angry),
            mouth = MoMouth.Flat(13f),
            leftArm = if (leftTaps) upper else lower,
            rightArm = if (leftTaps) lower else upper,
            armsInFront = true,
            legs = MoLegs(left = if (leftTaps) tapping else MoLeg(), right = if (leftTaps) MoLeg() else tapping),
            sway = base.sway + 3f * tap,
            flare = 3f,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        angerMark(Offset(CENTRE + 74f, rig.body.top + 96f), 0.6f + 0.1f * pulse(t, 2f))
    }

    override fun DrawScope.foreground(t: Float, v: MoVariant) {
        val h = heading(v)
        val (shell, shellDark) = v.of(
            listOf(MoPropPink to MoPropPinkDark, MoPropTeal to MoPropTealDark, MoPropYellow to MoPropYellowDark, MoPropPurple to MoPropPurpleDark),
            salt = 1,
        )
        val (rear, front) = ends(t, v)
        val stretch = front - rear
        val middle = (rear + front) / 2f
        // It fades in at one edge and out at the other, so starting over happens unseen.
        val alpha = ease(middle / FADE) * (1f - ease((middle - (TO - FROM - FADE)) / FADE))
        val tail = place(rear - 26f, v)
        val nose = place(front + 26f, v)
        // Shell, eye stalks and trail fade together as the snail leaves the floor.
        fading(alpha, Rect(FROM - 40f, FEET - 60f, TO + 40f, FEET + 8f)) {
            oval(Offset((tail + nose) / 2f, FEET + 2f), abs(nose - tail) / 2f + 4f, 5f, MoPropShadow.copy(alpha = 0.28f))
            if (v.chance(50, salt = 3)) {
                val length = min(60f, rear)
                limb(Offset(tail, FEET - 2f), Offset(tail - h * length, FEET - 2f), MoPropWhite.copy(alpha = 0.25f), 3.5f)
            }
            block(Offset((tail + nose) / 2f, FEET - 7.5f), abs(nose - tail), 15f, MoPropSand, corner = 7.5f)

            val head = Offset(nose - h * 4f, FEET - 14f)
            val bob = wave(t, 1.3f) * 2f
            for (stalk in listOf(-1f, 1f)) {
                val top = head + Offset(h * 4f + stalk * 7f, -23f + bob * stalk)
                limb(head + Offset(stalk * 3.5f, -4f), top, MoPropSand, 4.5f)
                drawCircle(MoPropSand, 4.8f, top)
                drawCircle(MoPropInk, 3f, top + Offset(h * 1f, 0f))
            }
            drawCircle(MoPropSand, 10f, head)
            curve(head + Offset(h * 1f, 1f), head + Offset(h * 5f, 7f), head + Offset(h * 9f, 0f), MoPropInk, 3f)

            // The shell rides on the rear half and lifts a little each time it is dragged along.
            val drag = stretch / ((TO - FROM) / STEPS)
            val centre = Offset(place(rear + 2f, v), FEET - 29f - 2f * sin(drag * PI.toFloat()))
            drawCircle(shellDark, 20f, centre)
            drawCircle(shell, 16.5f, centre + Offset(0f, -1.5f))
            drawCircle(shellDark, 8f, centre + Offset(0f, -1.5f))
            drawCircle(shell, 4.5f, centre + Offset(0f, -1.5f))
        }
    }

    override val ownsTheFloor get() = true
}

internal val angryActs3: List<MoAct> = listOf(
    KettleInHisFist,
    LookAtThatClock,
    LittleVolcano,
    DragonBreath,
    TooHotToCry,
    TheSnail,
)
