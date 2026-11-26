package de.nicolasmahn.momentum.feature.mo.acts

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import de.nicolasmahn.momentum.core.ui.theme.MoPropBlue
import de.nicolasmahn.momentum.core.ui.theme.MoPropBlueDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropCream
import de.nicolasmahn.momentum.core.ui.theme.MoPropGlass
import de.nicolasmahn.momentum.core.ui.theme.MoPropGold
import de.nicolasmahn.momentum.core.ui.theme.MoPropGoldDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropGreen
import de.nicolasmahn.momentum.core.ui.theme.MoPropGreenDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropGrey
import de.nicolasmahn.momentum.core.ui.theme.MoPropInk
import de.nicolasmahn.momentum.core.ui.theme.MoPropLeaf
import de.nicolasmahn.momentum.core.ui.theme.MoPropMint
import de.nicolasmahn.momentum.core.ui.theme.MoPropOrange
import de.nicolasmahn.momentum.core.ui.theme.MoPropPink
import de.nicolasmahn.momentum.core.ui.theme.MoPropPinkDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropRed
import de.nicolasmahn.momentum.core.ui.theme.MoPropRedDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropSand
import de.nicolasmahn.momentum.core.ui.theme.MoPropSilver
import de.nicolasmahn.momentum.core.ui.theme.MoPropSky
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
import de.nicolasmahn.momentum.feature.mo.block
import de.nicolasmahn.momentum.feature.mo.chunk
import de.nicolasmahn.momentum.feature.mo.cycle
import de.nicolasmahn.momentum.feature.mo.ease
import de.nicolasmahn.momentum.feature.mo.limb
import de.nicolasmahn.momentum.feature.mo.mitten
import de.nicolasmahn.momentum.feature.mo.moIdle
import de.nicolasmahn.momentum.feature.mo.oval
import de.nicolasmahn.momentum.feature.mo.puff
import de.nicolasmahn.momentum.feature.mo.pulse
import de.nicolasmahn.momentum.feature.mo.shape
import de.nicolasmahn.momentum.feature.mo.twitch
import de.nicolasmahn.momentum.feature.mo.wave
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/*
 * More of a hungry Mo: he dreams of dinner, gnaws his cutlery, begs, floats after a smell, makes one
 * pea last, and cannot reach the fruit jar. Nothing here is about what was logged; it is cartoon food.
 */

private fun mix(from: Float, to: Float, f: Float): Float = from + (to - from) * f

private fun mix(from: Offset, to: Offset, f: Float): Offset = from + (to - from) * f

private fun mix(from: MoArm, to: MoArm, f: Float): MoArm = MoArm(mix(from.reach, to.reach, f), mix(from.bend, to.bend, f))

/** One smooth bump from 0 to 1 and back while [g] runs from [from] to [to], and 0 outside. */
private fun arc(g: Float, from: Float, to: Float): Float =
    if (g <= from || g >= to) 0f else sin((g - from) / (to - from) * PI.toFloat())

/** He chews air with his eyes shut while bites vanish from the vegetable pictured in his thought cloud. */
private object DreamDinner : MoAct("hungry_dream_dinner", MoOccasion.Hungry, intensity = MoIntensity.Mild) {
    private const val PERIOD = 6f
    private const val LAST_BITE = 0.55f
    private const val GONE = 0.58f
    private const val REFILL = 0.78f
    private const val SETTLED = 0.92f

    private val cloudColour = MoPropCream

    // His tip leans right, so the cloud is mostly on the left.
    private fun side(v: MoVariant) = if (v.chance(30, salt = 2)) 1f else -1f

    private fun cloud(v: MoVariant) = Offset(CENTRE + side(v) * 92f, 70f)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val side = side(v)
        val g = cycle(t, PERIOD)
        val dreaming = g < GONE || g >= SETTLED
        val chomp = pulse(t, 0.55f)
        // He chews from the settled moment right through the wrap, so neither mouth nor bounce pops
        // where the loop starts over; only the empty cloud and the refill stop him.
        val eating = 1f - ease((g - GONE + 0.03f) / 0.03f) + ease((g - SETTLED) / 0.03f)
        val idle = moIdle(t, energy = 0.7f)
        val eye = if (dreaming) MoEye(shape = MoEyeShape.Shut) else MoEye(gaze = Offset(side * 0.8f, -0.9f), lid = -0.3f)
        val onBelly = MoArm(Offset(-46f, 22f + 2f * chomp), bend = 0.4f)
        return idle.copy(
            leftEye = eye,
            rightEye = eye,
            brows = if (g >= GONE && g < REFILL) MoBrows(MoBrowKind.Worried, lift = 3f) else MoBrows(),
            mouth = when {
                dreaming -> MoMouth.Chew(open = 0.3f + 0.6f * chomp)
                g < REFILL -> MoMouth.Oh(5f)
                else -> MoMouth.TongueOut
            },
            leftArm = if (side > 0f) onBelly else MoArm.Down,
            rightArm = if (side > 0f) MoArm.Down else onBelly,
            armsInFront = true,
            squash = idle.squash + 0.012f * chomp * eating,
            tilt = side * 2f,
            blinks = false,
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val c = cloud(v)
        val g = cycle(t, PERIOD)
        // The cloud swells once as the picture puffs away, which is what empties it.
        val breathe = 1f + 0.03f * wave(t, 3f) + 0.1f * arc(g, GONE, GONE + 0.08f)
        puff(c, 50f * breathe, cloudColour)
        puff(c + Offset(0f, 16f), 44f * breathe, cloudColour)

        val shown = when {
            g < GONE -> 1f
            g < REFILL -> 0f
            else -> ease((g - REFILL) / 0.1f) * (1f + 0.18f * arc(g, REFILL + 0.04f, SETTLED))
        }
        if (shown <= 0f) return
        val bites = 3 + v.pick(2, salt = 3)
        val eaten = if (g < GONE) (0 until bites).count { g >= (it + 1) * LAST_BITE / bites } else 0
        // The vegetables are drawn about 45 wide; the cloud has room for a third more.
        withTransform({ scale(shown * 1.35f, shown * 1.35f, c) }) {
            val meal = v.pick(3, salt = 1)
            val bitesTaken = Path().apply {
                biteCentres[meal].take(eaten).forEach { addOval(Rect(c + it, 9f)) }
            }
            clipPath(bitesTaken, ClipOp.Difference) {
                when (meal) {
                    0 -> kale(c)
                    1 -> carrot(c)
                    else -> broccoli(c)
                }
            }
        }
    }

    // Bite centres follow the edge of each vegetable from the first mouthful to the last.
    private val biteCentres = listOf(
        listOf(Offset(14f, -26f), Offset(20f, -8f), Offset(8f, 8f), Offset(-6f, 18f)),
        listOf(Offset(-20f, 18f), Offset(-10f, 4f), Offset(2f, -6f), Offset(18f, -10f)),
        listOf(Offset(-20f, -14f), Offset(-6f, -26f), Offset(8f, -26f), Offset(21f, -12f)),
    )

    /** A curly leaf on its rib, the ruffles a darker green around a lighter face. */
    private fun DrawScope.kale(c: Offset) {
        val lobes = listOf(Offset(-10f, 10f) to 12f, Offset(-2f, 0f) to 12f, Offset(6f, -9f) to 11f, Offset(12f, -17f) to 8f)
        for ((at, r) in lobes) {
            for (k in 0 until 6) {
                val a = k * PI.toFloat() / 3f
                drawCircle(MoPropGreenDark, 4.5f, c + at + Offset(r * cos(a), r * sin(a)))
            }
        }
        for ((at, r) in lobes) drawCircle(MoPropGreenDark, r, c + at)
        for ((at, r) in lobes) drawCircle(MoPropGreen, r - 3f, c + at)
        limb(c + Offset(-22f, 22f), c + Offset(10f, -14f), MoPropMint, 3.5f)
    }

    /** A fat carrot lying aslant, its leafy top up at the right. */
    private fun DrawScope.carrot(c: Offset) {
        for (leaf in listOf(Offset(4f, -26f), Offset(14f, -24f), Offset(20f, -16f))) {
            limb(c + Offset(10f, -12f), c + leaf, MoPropGreen, 5f)
        }
        shape(MoPropOrange, c.x + 2f to c.y - 18f, c.x + 18f to c.y - 4f, c.x - 22f to c.y + 20f)
        drawCircle(MoPropOrange, 11.3f, c + Offset(10f, -11f))
        for (ridge in listOf(-4f, -12f)) limb(c + Offset(ridge, ridge * -0.35f + 2f), c + Offset(ridge + 5f, ridge * -0.35f + 6f), MoPropRedDark, 2f)
    }

    /** A broccoli floret: a pale stalk under three green puffs. */
    private fun DrawScope.broccoli(c: Offset) {
        shape(MoPropMint, c.x - 7f to c.y + 22f, c.x + 7f to c.y + 22f, c.x + 5f to c.y, c.x - 5f to c.y)
        for ((at, r) in listOf(Offset(-11f, -4f) to 11f, Offset(11f, -4f) to 11f, Offset(0f, -12f) to 13f)) {
            drawCircle(MoPropGreenDark, r, c + at)
            drawCircle(MoPropGreen, r - 3f, c + at + Offset(-1f, -1f))
        }
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val side = side(v)
        // The bubbles start beside his eye on whichever body he has, and always reach the fixed cloud.
        val start = (if (side < 0f) rig.leftEye else rig.rightEye) + Offset(side * 54f, -64f)
        val cloudBottom = cloud(v) + Offset(-side * 6f, 68f)
        val bubbles = if (v.chance(50, salt = 4)) listOf(0.1f to 5f, 0.45f to 8f, 0.8f to 11f) else listOf(0.2f to 6f, 0.7f to 10f)
        bubbles.forEachIndexed { index, (along, size) ->
            drawCircle(cloudColour, size, mix(start, cloudBottom, along) + Offset(0f, 2f * wave(t + index * 0.6f, 1.9f)))
        }
    }

    override val sparkles get() = false
}

/** He chews absent-mindedly on his spoon, pulls it out, finds the bite marks in it, and sighs. */
private object SpoonGnawer : MoAct("hungry_spoon_gnawer", MoOccasion.Hungry, intensity = MoIntensity.Mild) {
    private const val PERIOD = 5.4f
    private val chewingLever = Offset(-40f, -32f)
    private const val CHOMP = 0.45f
    private const val SIGH = 0.72f
    private const val BACK = 0.88f

    /** The heads are modelled small and drawn this much larger, so the bites still read at thumbnail size. */
    private const val HEAD = 1.5f

    private fun side(v: MoVariant) = if (v.chance(50, salt = 3)) 1f else -1f

    /** Where in the loop he stops chewing and looks: fewer or more chews before he checks. */
    private fun check(v: MoVariant) = v.between(0.45f, 0.6f, salt = 5)

    private fun out(g: Float, v: MoVariant) = ease((g - check(v)) / 0.08f) * (1f - ease((g - BACK) / 0.1f))

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val side = side(v)
        val g = cycle(t, PERIOD)
        val out = out(g, v)
        val chomp = pulse(t, CHOMP)
        val shape = MoBodies.of(body)
        val bite = shape.mouth + Offset(side * 31f, 5f)
        val shoulder = Offset(CENTRE + side * shape.shoulderHalf, shape.shoulderY)
        val chewingReach = Offset(side * (bite.x - shoulder.x), bite.y - shoulder.y) - chewingLever
        // Pull sideways below the eye before lifting the spoon to look at it.
        val spoonArm = MoArm(
            Offset(mix(chewingReach.x, 15f, ease(out / 0.55f)), mix(chewingReach.y, -12f, ease((out - 0.42f) / 0.58f))),
            bend = 0.65f,
        )
        val eye = if (out > 0.5f) {
            MoEye(gaze = Offset(side * 0.9f, -0.15f), lid = -0.45f)
        } else {
            MoEye(gaze = Offset(0.5f * sin(t * 0.6f), -0.2f), lidDrop = 0.4f)
        }
        return moIdle(t, energy = 0.7f).copy(
            leftEye = eye,
            rightEye = eye,
            brows = if (out > 0.5f) MoBrows(MoBrowKind.Worried) else MoBrows(),
            mouth = when {
                out <= 0.5f -> MoMouth.Chew(open = 0.25f + 0.55f * chomp * (1f - out))
                g < SIGH -> MoMouth.Oh(6f)
                else -> MoMouth.Flat(9f)
            },
            leftArm = if (side < 0f) spoonArm else MoArm.Down,
            rightArm = if (side < 0f) MoArm.Down else spoonArm,
            armsInFront = true,
            squash = 0.012f * chomp * (1f - out) - 0.035f * arc(g, SIGH, SIGH + 0.1f),
            tilt = side * 1.5f,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val side = side(v)
        val out = out(cycle(t, PERIOD), v)
        val hand = if (side < 0f) rig.leftHand else rig.rightHand
        val wag = 3f * PI.toFloat() / 180f * pulse(t, CHOMP) * (1f - out)
        val pitch = mix(atan2(chewingLever.y, chewingLever.x), -PI.toFloat() / 2f, out) + wag
        val head = hand + Offset(side * cos(pitch), sin(pitch)) * chewingLever.getDistance()

        val material = v.pick(3, salt = 4)
        val face = if (material == 1) MoPropWood else MoPropSilver
        val ledge = if (material == 1) MoPropWoodDark else MoPropSteel
        val handle = when (material) {
            1 -> MoPropWood
            2 -> MoPropTeal
            else -> MoPropSilver
        }
        val along = head - hand
        val angle = atan2(along.y, along.x) * 180f / PI.toFloat()
        limb(hand, head, handle, 8f)
        val outline = if (v.chance(25, salt = 2)) forkHead() else spoonHead(notches = 2 + v.pick(2, salt = 1))
        for ((drop, colour) in listOf(3f to ledge, 0f to face)) {
            withTransform({
                translate(head.x, head.y + drop)
                rotate(angle, Offset.Zero)
                scale(HEAD, HEAD, Offset.Zero)
            }) { drawPath(outline, colour) }
        }
        mitten(rig, hand, side)
    }

    // Both heads are drawn around the origin with the far end along +x. The notches are cut out of
    // the path, so whatever is behind the spoon shows through them like a real bite.

    private fun spoonHead(notches: Int): Path {
        val bowl = Path().apply { addOval(Rect(-14f, -10.5f, 14f, 10.5f)) }
        val bitten = Path().apply {
            listOf(Offset(12f, -7.5f), Offset(9f, 9.5f), Offset(16f, 1f)).take(notches).forEach { addOval(Rect(it, 5.6f)) }
        }
        return Path.combine(PathOperation.Difference, bowl, bitten)
    }

    private fun forkHead(): Path = Path().apply {
        addRoundRect(RoundRect(Rect(-9f, -9f, 3f, 9f), CornerRadius(4f)))
        // The last prong has been bitten short.
        listOf(-9f to 16f, -2.25f to 16f, 4.5f to 8f).forEach { (top, end) ->
            addRoundRect(RoundRect(Rect(0f, top, end, top + 4.5f), CornerRadius(2.2f)))
        }
    }
}

/** He holds his empty bowl out to you in both mittens, on tiptoe, with the biggest eyes he has. */
private object PleaseSomeMore : MoAct("hungry_please_some_more", MoOccasion.Hungry, intensity = MoIntensity.Medium) {
    private const val PERIOD = 4.4f

    // 1 at t = 0 with a plateau around it, 0 half a period later.
    private fun ask(t: Float) = ease(pulse(t + PERIOD / 2f, PERIOD) * 1.5f)

    private fun peek(t: Float) = ease((0.3f - ask(t)) / 0.3f)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val ask = ask(t)
        val peek = peek(t)
        val lean = if (v.chance(50, salt = 5)) 1f else -1f
        // The bowl's rim has to stay under his pout, and the Low body's mouth sits level with its shoulders.
        val shape = MoBodies.of(body)
        val reach = shape.mouth.y + 50f - shape.shoulderY
        val arm = MoArm(Offset(-50f, reach + 8f * (1f - ask)), bend = 0.3f)
        val rattles = if (v.chance(50, salt = 3)) 2 else 1
        val rattle = twitch(t, PERIOD, beat = 0.45f, offset = 0.04f) + if (rattles == 2) twitch(t, PERIOD, beat = 0.45f, offset = 0.2f) else 0f
        val eye = MoEye(gaze = Offset(0f, -0.6f * ask + 0.9f * peek), scale = 1f + 0.12f * ask, lid = -0.25f)
        return moIdle(t, energy = 0.8f).copy(
            leftEye = eye,
            rightEye = if (v.chance(30, salt = 4)) eye.copy(tear = cycle(t, PERIOD)) else eye,
            brows = MoBrows(MoBrowKind.Worried, lift = 4f * ask),
            mouth = if (peek > 0.5f) MoMouth.Oh(4f) else MoMouth.Pout,
            leftArm = arm,
            rightArm = arm,
            armsInFront = true,
            offset = Offset(0f, -6f * ask),
            squash = 0.025f * ask,
            flare = -6f * (1f - ask),
            shake = wave(t, 0.09f) * 1.3f * rattle,
            tilt = lean * 2f,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val c = (rig.leftHand + rig.rightHand) / 2f + Offset(0f, 2f)
        val (colour, ledge) = v.of(
            listOf(MoPropTeal to MoPropTealDark, MoPropSky to MoPropBlue, MoPropPink to MoPropPinkDark, MoPropCream to MoPropSand),
            salt = 1,
        )
        // Pushed toward you, the bowl grows: there is no depth here, so size is how near it is.
        val near = 1f + 0.14f * ask(t)
        withTransform({ scale(near, near, c) }) {
            chunk(c, width = 100f, height = 32f, colour = colour, ledge = ledge, corner = 16f)
            oval(c + Offset(0f, -13f), 48f, 10.5f, colour)
            oval(c + Offset(0f, -13f), 43f, 7.5f, MoPropInk)
            if (v.chance(40, salt = 2)) {
                val end = c + Offset(46f, -32f)
                limb(c + Offset(16f, -12f), end, MoPropSilver, 6f)
                drawCircle(MoPropSilver, 5f, end)
            }
        }
        // The mittens come near with the bowl, so they keep hold of its ends instead of sitting on its face.
        mitten(rig, mix(c, rig.leftHand, near), -1f)
        mitten(rig, mix(c, rig.rightHand, near), 1f)
    }
}

/** A ribbon of scent curls up under his face, and he floats along it off the floor, tip first. */
private object CarriedByASmell : MoAct("hungry_carried_by_a_smell", MoOccasion.Hungry, intensity = MoIntensity.Mild) {
    private const val SNIFF = 6.4f

    private fun from(v: MoVariant) = if (v.chance(50, salt = 1)) 1f else -1f

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val s = from(v)
        val bob = pulse(t, 3.2f)
        // The deep sniff sits in the middle of its period and the peek before it, so t = 0 is plain bliss.
        val sniff = ease(twitch(t, SNIFF, beat = 1.6f, offset = 0.5f))
        val peeking = v.chance(50, salt = 5) && twitch(t, SNIFF, beat = 0.8f, offset = 0.75f) > 0f
        val idle = moIdle(t, energy = 0.8f)
        val eye = if (peeking) MoEye(gaze = Offset(s * 0.9f, 0.3f), lidDrop = 0.3f) else MoEye(shape = MoEyeShape.Shut)
        val arm = MoArm(Offset(14f, 26f + 3f * bob), bend = 0.3f)
        return idle.copy(
            leftEye = eye,
            rightEye = eye,
            brows = MoBrows(MoBrowKind.Raised, lift = 3f + 4f * sniff),
            mouth = if (v.chance(35, salt = 4)) MoMouth.Drool else MoMouth.Smile(width = 13f, depth = 9f),
            leftArm = arm,
            rightArm = arm,
            legs = MoLegs(MoLeg(swing = 3f * wave(t, 1.6f)), MoLeg(swing = -3f * wave(t + 0.5f, 1.6f))),
            offset = Offset(s * 4f, -(v.between(8f, 14f, salt = 6) + 5f * bob + 6f * sniff)),
            squash = 0.02f + 0.04f * sniff,
            sway = idle.sway + s * (24f + 6f * sniff),
            tilt = s * 3f,
            blinks = false,
        )
    }

    override fun DrawScope.foreground(t: Float, v: MoVariant) {
        val colour = v.of(listOf(MoPropCream, MoPropMint, MoPropPink), salt = 2)
        if (v.chance(40, salt = 3)) {
            ribbon(t, from(v), colour, width = 7f, lower = 0f, lag = 0f)
            ribbon(t, from(v), colour, width = 7f, lower = 17f, lag = 0.9f)
        } else {
            ribbon(t, from(v), colour, width = 12f, lower = 0f, lag = 0f)
        }
    }

    // It stays below his mouth on both bodies, so his face and tip are never crossed.
    private fun DrawScope.ribbon(t: Float, s: Float, colour: Color, width: Float, lower: Float, lag: Float) {
        val last = 27
        val drift = (0..last).map { i ->
            val along = i / last.toFloat()
            // A travelling wave has no beginning or end, so it can run forever; it calms down toward the curl.
            val swell = 12f * sin(i * 0.55f - t * 2.4f + lag) * min(1f, (last - i) / 6f)
            Offset(mix(CENTRE + s * 158f, CENTRE - s * 4f, along), mix(336f, 372f, ease(along)) + lower * (1f - along) + swell)
        }
        // The end curls up toward his face like a beckoning finger, and beckons.
        val beckon = 0.5f + 0.05f * wave(t + lag, 1.6f)
        var at = drift.last()
        val curl = (1..9).map { j ->
            val heading = (if (s < 0f) 0f else PI.toFloat()) + s * beckon * j
            at += Offset(cos(heading), sin(heading)) * (mix(15f, 5f, j / 9f) * 0.55f)
            at
        }
        // It thins out to nothing at the far end, so it comes out of thin air and not from a cut edge.
        (drift + curl).zipWithNext().forEachIndexed { index, (from, to) ->
            limb(from, to, colour, width * (0.15f + 0.85f * ease(index / 7f)))
        }
    }
}

/** One pea on a big plate: he tips it from side to side and watches his whole dinner roll. */
private object OnePea : MoAct("hungry_one_pea", MoOccasion.Hungry, intensity = MoIntensity.Mild) {
    private const val LAG = 0.45f

    private fun period(v: MoVariant) = v.between(4.4f, 6f, salt = 3)

    // The morsel follows the tilt a moment late, as a rolling thing does.
    private fun roll(t: Float, v: MoVariant) = wave(t - LAG, period(v))

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val period = period(v)
        val lean = wave(t, period)
        val gaze = Offset(0.8f * roll(t, v), 0.9f)
        val arm = MoArm(Offset(-34f, 44f), bend = 0.3f)
        // His tongue comes out for a moment as the morsel stops against the rim on one side.
        val arrives = LAG + period / 4f - 0.25f
        val licking = twitch(t, period, beat = 0.5f, offset = 1f - arrives / period) > 0f
        return moIdle(t, energy = 0.7f).copy(
            leftEye = MoEye(gaze = gaze, lid = -0.2f),
            rightEye = MoEye(gaze = gaze, lid = -0.2f),
            brows = MoBrows(MoBrowKind.Worried, lift = 2f),
            mouth = if (licking) MoMouth.TongueOut else MoMouth.Flat(10f),
            leftArm = arm,
            rightArm = arm,
            armsInFront = true,
            tilt = 6f * lean,
            offset = Offset(3f * lean, 0f),
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val c = (rig.leftHand + rig.rightHand) / 2f + Offset(0f, -4f)
        val rim = v.of(listOf(MoPropTeal, MoPropPink, MoPropSky, MoPropYellow), salt = 2)
        oval(c + Offset(0f, 5f), 70f, 16f, MoPropGrey)
        oval(c, 70f, 15f, MoPropWhite)
        oval(c, 55f, 10.5f, rim)
        oval(c, 48f, 8.5f, MoPropWhite)
        mitten(rig, rig.leftHand, -1f)
        mitten(rig, rig.rightHand, 1f)

        val at = c + Offset(36f * roll(t, v), -7f)
        oval(at + Offset(0f, 8f), 9f, 2.8f, MoPropGrey)
        when (v.pick(3, salt = 1)) {
            0 -> {
                drawCircle(MoPropGreen, 8.5f, at)
                drawCircle(MoPropMint, 2.6f, at + Offset(-3f, -3.2f))
            }
            1 -> {
                limb(at + Offset(1f, -6f), at + Offset(6f, -16f), MoPropLeaf, 3f)
                drawCircle(MoPropRed, 8.5f, at)
                drawCircle(MoPropPink, 2.4f, at + Offset(-3f, -3.2f))
            }
            else -> {
                drawCircle(MoPropGoldDark, 7.5f, at + Offset(0f, 2.5f))
                drawCircle(MoPropGold, 7.5f, at)
            }
        }
    }
}

/** The fruit jar is on the high shelf: tiptoe, tongue out, two hops that fall short, and a breather. */
private object JarOnTheHighShelf : MoAct("hungry_jar_on_the_high_shelf", MoOccasion.Hungry, intensity = MoIntensity.Medium) {
    private const val PERIOD = 5.6f
    private const val SHELF_TOP = 180f

    private fun side(v: MoVariant) = if (v.chance(50, salt = 1)) 1f else -1f

    private fun hops(v: MoVariant): List<Pair<Float, Float>> =
        listOf(0.2f to 0.35f, 0.4f to 0.55f) + if (v.chance(35, salt = 4)) listOf(0.05f to 0.18f) else emptyList()

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val s = side(v)
        val g = cycle(t, PERIOD)
        val stretch = 1f - ease((g - 0.58f) / 0.06f) + ease((g - 0.86f) / 0.12f)
        val hop = hops(v).sumOf { (from, to) -> arc(g, from, to).toDouble() }.toFloat()
        val landing = hops(v).sumOf { (_, to) -> arc(g, to, to + 0.04f).toDouble() }.toFloat()
        val pant = pulse(t, 0.5f) * (1f - stretch)
        // Even at the top of a hop the mitten has to end a hand short of the shelf, on the Tall body too.
        val near = mix(MoArm.Down, MoArm.Up, stretch)
        val far = mix(MoArm.Down, MoArm(Offset(50f, 4f + 6f * wave(t, 0.5f)), bend = 0.1f), stretch)
        val eye = MoEye(gaze = Offset(s * 0.8f, -0.9f), lidDrop = 0.25f * (1f - stretch))
        val idle = moIdle(t)
        return idle.copy(
            leftEye = eye,
            rightEye = eye,
            brows = if (stretch > 0.5f) MoBrows(MoBrowKind.Flat, lift = 2f) else MoBrows(MoBrowKind.Worried),
            mouth = if (stretch > 0.5f) MoMouth.TongueOut else MoMouth.Oh(5f + 2f * pant),
            leftArm = if (s < 0f) near else far,
            rightArm = if (s < 0f) far else near,
            legs = MoLegs(MoLeg(lift = 8f * hop), MoLeg(lift = 8f * hop)),
            offset = Offset(0f, -8f * stretch - 18f * hop),
            squash = idle.squash + 0.05f * stretch + 0.03f * hop - 0.04f * landing + 0.015f * pant,
            tilt = s * 3f * stretch,
            sway = idle.sway + s * 10f * stretch,
            flare = 6f * hop,
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        // Short and far out, so the shelf ends beside his flame and never appears to pass through it.
        val x = CENTRE + side(v) * 126f
        for (bracket in listOf(x - 23f, x + 23f)) {
            shape(MoPropWoodDark, bracket - 7f to SHELF_TOP + 12f, bracket + 7f to SHELF_TOP + 12f, bracket to SHELF_TOP + 22f)
        }
        chunk(Offset(x, SHELF_TOP + 6f), width = 66f, height = 12f, colour = MoPropWood, ledge = MoPropWoodDark, corner = 5f)

        val filling = v.pick(3, salt = 3)
        val (lid, lidLedge) = v.of(listOf(MoPropRed to MoPropRedDark, MoPropTeal to MoPropTealDark, MoPropBlue to MoPropBlueDark), salt = 2)
        // The jar stands toward the outer end of the shelf, so the hopping flame never covers it.
        val jarX = x + side(v) * 4f
        withTransform({ scale(1.2f, 1.2f, Offset(jarX, SHELF_TOP)) }) { jar(jarX, filling, lid, lidLedge) }
    }

    private fun DrawScope.jar(x: Float, filling: Int, lid: Color, lidLedge: Color) {
        // The glass goes behind the fruit, so the fruit keeps its colour and still reads as inside.
        block(Offset(x, SHELF_TOP - 27f), 44f, 54f, MoPropGlass.copy(alpha = 0.5f), corner = 11f)
        listOf(Offset(-9f, -10f), Offset(9f, -11f), Offset(-1f, -23f), Offset(-10f, -35f), Offset(9f, -36f)).forEach { place ->
            val at = Offset(x, SHELF_TOP) + place
            when (filling) {
                0 -> {
                    drawCircle(MoPropRed, 8.5f, at)
                    limb(at + Offset(0f, -7f), at + Offset(1.5f, -11f), MoPropWoodDark, 2f)
                }
                1 -> {
                    drawCircle(MoPropOrange, 8.5f, at)
                    drawCircle(MoPropLeaf, 1.8f, at + Offset(0f, -6f))
                }
                else -> {
                    drawCircle(MoPropGreen, 8.5f, at)
                    limb(at + Offset(0f, -7f), at + Offset(1.5f, -11f), MoPropWoodDark, 2f)
                }
            }
        }
        limb(Offset(x - 14f, SHELF_TOP - 44f), Offset(x - 14f, SHELF_TOP - 30f), MoPropWhite.copy(alpha = 0.8f), 3.5f)
        chunk(Offset(x, SHELF_TOP - 56f), width = 38f, height = 10f, colour = lid, ledge = lidLedge, corner = 5f)
    }

    override val sparkles get() = false
}

internal val hungryActs2: List<MoAct> = listOf(
    DreamDinner,
    SpoonGnawer,
    PleaseSomeMore,
    CarriedByASmell,
    OnePea,
    JarOnTheHighShelf,
)
