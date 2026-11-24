package de.nicolasmahn.momentum.feature.mo.acts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import de.nicolasmahn.momentum.core.ui.theme.MoCoin
import de.nicolasmahn.momentum.core.ui.theme.MoCoinLedge
import de.nicolasmahn.momentum.core.ui.theme.MoCoinShine
import de.nicolasmahn.momentum.core.ui.theme.MoPropBlue
import de.nicolasmahn.momentum.core.ui.theme.MoPropBlueDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropCharcoal
import de.nicolasmahn.momentum.core.ui.theme.MoPropCream
import de.nicolasmahn.momentum.core.ui.theme.MoPropGold
import de.nicolasmahn.momentum.core.ui.theme.MoPropInk
import de.nicolasmahn.momentum.core.ui.theme.MoPropLeaf
import de.nicolasmahn.momentum.core.ui.theme.MoPropMint
import de.nicolasmahn.momentum.core.ui.theme.MoPropOrange
import de.nicolasmahn.momentum.core.ui.theme.MoPropPink
import de.nicolasmahn.momentum.core.ui.theme.MoPropPinkDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropPurple
import de.nicolasmahn.momentum.core.ui.theme.MoPropPurpleDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropRed
import de.nicolasmahn.momentum.core.ui.theme.MoPropRedDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropShadow
import de.nicolasmahn.momentum.core.ui.theme.MoPropSky
import de.nicolasmahn.momentum.core.ui.theme.MoPropSteel
import de.nicolasmahn.momentum.core.ui.theme.MoPropTeal
import de.nicolasmahn.momentum.core.ui.theme.MoPropTealDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropWater
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
import de.nicolasmahn.momentum.feature.mo.grippingMitten
import de.nicolasmahn.momentum.feature.mo.block
import de.nicolasmahn.momentum.feature.mo.chunk
import de.nicolasmahn.momentum.feature.mo.curve
import de.nicolasmahn.momentum.feature.mo.cycle
import de.nicolasmahn.momentum.feature.mo.drop
import de.nicolasmahn.momentum.feature.mo.ease
import de.nicolasmahn.momentum.feature.mo.floorShadow
import de.nicolasmahn.momentum.feature.mo.limb
import de.nicolasmahn.momentum.feature.mo.moIdle
import de.nicolasmahn.momentum.feature.mo.oval
import de.nicolasmahn.momentum.feature.mo.polyline
import de.nicolasmahn.momentum.feature.mo.pulse
import de.nicolasmahn.momentum.feature.mo.shape
import de.nicolasmahn.momentum.feature.mo.twitch
import de.nicolasmahn.momentum.feature.mo.wave
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/*
 * More of the streak being in danger: small frights that show in his hands and his eyes before they
 * show anywhere else. Only the draught takes him over.
 */

private fun mix(from: Float, to: Float, amount: Float): Float = from + (to - from) * amount

private fun mix(from: Offset, to: Offset, amount: Float): Offset = from + (to - from) * amount

private fun mix(from: MoArm, to: MoArm, amount: Float): MoArm =
    MoArm(mix(from.reach, to.reach, amount), mix(from.bend, to.bend, amount))

/** His streak stands beside him as a swaying tower of coins, and he does not dare to steady it. */
private object CoinTower : MoAct("scared_the_coin_tower", MoOccasion.Scared, intensity = MoIntensity.Mild) {
    private const val PITCH = 12f

    // A full stack reaches his shoulder, which is what makes the tower look ready to go over.
    private fun coins(v: MoVariant) = 7 + v.pick(3, salt = 1)

    private fun side(v: MoVariant) = if (v.chance(50, salt = 2)) 1f else -1f

    // Positive leans toward him. Half a second in, so the first frame already shows it bending.
    private fun lean(t: Float) = wave(t + 0.5f, 3.4f)

    private fun topY(v: MoVariant) = FEET - 8f - (coins(v) - 1) * PITCH

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val s = side(v)
        val lean = lean(t)
        val toward = max(lean, 0f)
        val b = MoBodies.of(body)
        // The mitten hovers clear above the top coin, wherever that is on this body, and lifts further
        // as the tower comes at him. The elbow bows inward, so no part of the arm crosses the coin
        // that is doing the sliding.
        val hover = MoArm(
            Offset(24f - 5f * toward + 3f * wave(t, 0.9f), topY(v) - 27f - 8f * toward - b.shoulderY),
            bend = -0.4f,
        )
        val cheek = MoArm(Offset(-24f, -16f), bend = 0.5f, warm = 1f)
        val eye = MoEye(iris = 0.55f, gaze = Offset(s, ((topY(v) - b.eyeY) / 110f).coerceIn(-0.2f, 0.8f) - 0.1f * lean))
        val idle = moIdle(t, energy = 0.7f)
        return idle.copy(
            leftEye = eye,
            rightEye = eye,
            brows = MoBrows(MoBrowKind.Worried, lift = 3f),
            // He holds his breath while it hangs at the end of a sway.
            mouth = if (abs(lean) > 0.85f) MoMouth.Flat(8f) else MoMouth.Oh(size = 5f),
            leftArm = if (s < 0f) hover else cheek,
            rightArm = if (s < 0f) cheek else hover,
            armsInFront = true,
            tilt = s * 3f,
            // He stands well over, both to make room for the tower and because he wants no part of it.
            offset = Offset(-s * 38f, 0f),
            squash = idle.squash - 0.125f * max(0f, abs(lean) - 0.8f),
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val s = side(v)
        val x0 = CENTRE + s * 108f
        val total = coins(v)
        val lean = lean(t)
        val slide = v.between(16f, 22f, salt = 3)
        val shine = v.chance(50, salt = 4)
        floorShadow(x0, 28f, MoPropShadow)
        for (index in 0 until total) {
            val height = index / (total - 1f)
            val shudder = if (index == total - 1) wave(t, 0.13f) * 1.4f * abs(lean) else 0f
            val at = Offset(x0 - s * slide * lean * height * height + shudder, FEET - 8f - index * PITCH)
            chunk(at, width = 48f, height = 8.5f, colour = MoCoin, ledge = MoCoinLedge, corner = 5f, drop = 3.5f)
            if (shine && index % 2 == 0) block(at + Offset(-11f, -4f), 13f, 3f, MoCoinShine, corner = 1.5f)
        }
    }

    override val ownsTheFloor get() = true
}

/** His heart thumps right out of his chest, twice and a rest, and he holds on to it. */
private object PoundingHeart : MoAct("scared_pounding_heart", MoOccasion.Scared) {
    private fun rate(v: MoVariant) = v.between(0.95f, 1.3f, salt = 3)

    private fun side(v: MoVariant) = if (v.chance(50, salt = 2)) 1f else -1f

    // The offset puts t = 0 in the middle of the first thump; the second, smaller one follows 0.22 of a beat later.
    private fun beat(t: Float, v: MoVariant): Float =
        max(twitch(t, rate(v), beat = 0.16f, offset = 0.07f), 0.8f * twitch(t, rate(v), beat = 0.16f, offset = 0.07f - 0.22f))

    // His chest: below his mouth, above where his legs start, which puts it on the belly of either body.
    private fun chestY(b: MoBody) = b.mouth.y + (b.legTop - b.mouth.y) * 0.4f

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val b = MoBodies.of(body)
        val beat = beat(t, v)
        // The mitten lies against the heart, and is knocked outward by every thump.
        val clutch = MoArm(Offset(54f + 6f * beat - b.shoulderHalf, chestY(b) + 6f - b.shoulderY), bend = 0.45f)
        val loose = MoArm(MoArm.Down.reach + Offset(wave(t, 0.12f), 0f), bend = 0.1f)
        val eye = MoEye(iris = 0.42f, scale = 1.05f + 0.03f * beat)
        val idle = moIdle(t, energy = 0.6f)
        return idle.copy(
            leftEye = eye,
            rightEye = eye,
            brows = MoBrows(MoBrowKind.Worried, lift = 4f + 2f * beat),
            // The gulp, in the middle of the rest.
            mouth = if (cycle(t, rate(v), offset = 0.07f) in 0.6f..0.78f) MoMouth.Flat(7f) else MoMouth.Oh(size = 6f),
            leftArm = if (side(v) < 0f) clutch else loose,
            rightArm = if (side(v) < 0f) loose else clutch,
            armsInFront = true,
            legs = MoLegs(MoLeg(swing = -4f), MoLeg(swing = -4f)),
            squash = idle.squash - 0.045f * beat,
        )
    }

    private fun DrawScope.heart(at: Offset, z: Float, colour: Color) {
        drawCircle(colour, 0.52f * z, at + Offset(-0.48f * z, -0.3f * z))
        drawCircle(colour, 0.52f * z, at + Offset(0.48f * z, -0.3f * z))
        shape(colour, at.x - 0.9f * z to at.y - 0.06f * z, at.x + 0.9f * z to at.y - 0.06f * z, at.x to at.y + 1.05f * z)
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val (colour, ledge) = v.of(listOf(MoPropPink to MoPropPinkDark, MoPropRed to MoPropRedDark), salt = 1)
        val s = side(v)
        val beat = beat(t, v)
        val z = 17f * (1f + 0.4f * beat)
        val at = Offset(CENTRE + s * 34f, chestY(rig.body))
        heart(at + Offset(0f, 5f), z, ledge)
        heart(at, z, colour)
        grippingMitten(rig, s)
        if (v.chance(70, salt = 4) && beat > 0.3f) {
            // The mitten has one side of the heart, so the thump rings out on the other.
            for (ring in 0..1) {
                val out = z + 12f + ring * 11f
                val reach = 8f + ring * 4f
                curve(
                    at + Offset(-s * out, 4f - reach), at + Offset(-s * (out + 6f), 4f), at + Offset(-s * out, 4f + reach),
                    MoPropCream.copy(alpha = beat * (1f - ring * 0.35f)), 4.5f,
                )
            }
        }
    }
}

/** A cuckoo clock in the corner: the bird shoots out, he leaps out of his skin, and then he eyes the door. */
private object TheCuckoo : MoAct("scared_the_cuckoo", MoOccasion.Scared) {
    private const val PERIOD = 4f

    // A second pop, so soon that he is barely back on the floor.
    private const val SECOND = 0.26f

    private fun side(v: MoVariant) = if (v.chance(50, salt = 3)) 1f else -1f

    private fun twice(v: MoVariant) = v.chance(30, salt = 4)

    // The offset puts t = 0 at the top of the leap, with the bird fully out.
    private fun at(t: Float) = cycle(t, PERIOD, offset = 0.09f)

    private fun out(c: Float, v: MoVariant): Float {
        fun pop(start: Float) = ease((c - start) / 0.04f) * (1f - ease((c - start - 0.13f) / 0.06f))
        return if (twice(v)) max(pop(0f), pop(SECOND)) else pop(0f)
    }

    // He leaves the floor a moment after the door opens: it takes that long to reach him.
    private fun hop(c: Float, v: MoVariant): Float {
        fun leap(start: Float) = if (c - start in 0.02f..0.22f) sin((c - start - 0.02f) / 0.2f * PI.toFloat()) else 0f
        return if (twice(v)) max(leap(0f), 0.55f * leap(SECOND)) else leap(0f)
    }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val s = side(v)
        val c = at(t)
        val late = if (twice(v)) SECOND else 0f
        val hop = hop(c, v)
        fun flung(start: Float) = ease((c - start - 0.02f) / 0.05f) * (1f - ease((c - start - 0.2f) / 0.14f))
        val up = if (twice(v)) max(flung(0f), 0.7f * flung(SECOND)) else flung(0f)
        // Back on the floor he trembles with his eyes on the door, and lets go of it before the next pop.
        val tense = ease((c - 0.2f - late) / 0.08f) * (1f - ease((c - 0.72f) / 0.14f))
        val landing = (c - 0.22f - late).let { if (it in 0f..0.08f) sin(it / 0.08f * PI.toFloat()) else 0f }

        val arm = mix(
            mix(MoArm(Offset(-14f, 30f), bend = 0.5f), MoArm(Offset(-26f, 24f), bend = 0.5f), tense),
            MoArm(Offset(40f, -50f), bend = 0.2f),
            up,
        )
        val eye = MoEye(
            iris = mix(mix(0.6f, 0.45f, tense), 0.3f, up),
            scale = 1f + 0.12f * up,
            gaze = mix(mix(Offset(s * 0.5f, -0.4f), Offset(s * 0.9f, -0.8f), tense), Offset(s * 0.4f, -0.3f), up),
        )
        val idle = moIdle(t, energy = 0.7f)
        return idle.copy(
            leftEye = eye,
            rightEye = eye,
            brows = if (up > 0.5f) MoBrows(MoBrowKind.Raised, lift = 7f) else MoBrows(MoBrowKind.Worried, lift = 2f + 2f * tense),
            mouth = when {
                up > 0.5f -> MoMouth.Oh(size = 10f)
                tense > 0.5f -> MoMouth.Zigzag
                else -> MoMouth.Wavy
            },
            leftArm = arm,
            rightArm = arm,
            armsInFront = true,
            legs = MoLegs(MoLeg(lift = 8f * hop, swing = 10f * hop), MoLeg(lift = 8f * hop, swing = 10f * hop)),
            offset = Offset(-s * 6f * hop, -26f * hop),
            flare = 32f * hop,
            squash = idle.squash + 0.05f * hop - 0.06f * landing,
            shake = wave(t, 0.06f) * 1.6f * tense * (1f - ease((c - 0.3f - late) / 0.4f)),
            blinks = false,
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val s = side(v)
        val k = Offset(CENTRE + s * 118f, 72f)
        val out = out(at(t), v)

        // The pendulum's period divides the clock's, so the whole picture repeats exactly.
        val swing = wave(t, 1f) * 0.31f
        val bob = k + Offset(sin(swing) * 36f, 28f + cos(swing) * 36f)
        limb(k + Offset(0f, 28f), bob, MoPropWoodDark, 3.5f)
        drawCircle(MoPropGold, 7f, bob)

        chunk(k, width = 56f, height = 62f, colour = MoPropWood, ledge = MoPropWoodDark, corner = 7f)
        val roof = v.of(listOf(MoPropRed, MoPropTeal, MoPropPurple), salt = 2)
        shape(roof, k.x - 37f to k.y - 32f, k.x + 37f to k.y - 32f, k.x to k.y - 60f)
        val door = k + Offset(0f, -16f)
        block(door, 20f, 17f, if (out > 0.02f) MoPropInk else MoPropWoodDark, corner = 5f)
        val face = k + Offset(0f, 12f)
        drawCircle(MoPropCream, 14f, face)
        // On the hour, without a single mark on the face. A long hand straight up and a short one out
        // to the side of the pin: two hands that share no hemisphere can never read as a tick.
        limb(face, face + Offset(0f, -11.5f), MoPropInk, 3f)
        limb(face, face + Offset(8.5f, 5f), MoPropInk, 3.5f)
        drawCircle(MoPropInk, 2.5f, face)

        if (out > 0.02f) {
            val along = Offset(-s * 0.98f, 0.2f)
            val across = Offset(along.y, -along.x)
            val length = 44f * out
            val head = door + along * (length + 12f)
            val bird = v.of(listOf(MoPropYellow, MoPropSky, MoPropPink, MoPropMint), salt = 1)
            polyline(MoPropSteel, 4f, (0..8).map { door + along * (length * it / 8f) + across * (if (it % 2 == 0) 0f else if (it % 4 == 1) 5f else -5f) })
            val tip = head + along * 26f
            val jaw = head + along * 11f
            shape(MoPropOrange, jaw.x + across.x * 6f to jaw.y + across.y * 6f, jaw.x - across.x * 6f to jaw.y - across.y * 6f, tip.x to tip.y)
            drawCircle(bird, 13f, head)
            // A wing thrown back over the spring, which is what makes the blob read as a bird.
            oval(head - along * 11f, 9f, 6f, bird)
            drawCircle(MoPropInk, 2.8f, head + along * 4f + Offset(0f, -4f))
        }
    }

    // Blazing's sparkles would sit on the clock and its pendulum.
    override val sparkles get() = false
}

/** A gust nearly blows him out: tip flat and fluttering, arms up as a shield, one eye squeezed shut. */
private object Draught : MoAct("scared_draught", MoOccasion.Scared, intensity = MoIntensity.Strong) {
    // +1 blows to the right. From the right it goes against the natural lean of his tip, which is the worse one.
    private fun wind(v: MoVariant) = if (v.chance(60, salt = 1)) -1f else 1f

    // Half a period in, so t = 0 is the peak of a gust.
    private fun gust(t: Float, v: MoVariant): Float {
        val period = v.between(3.2f, 4.2f, salt = 4)
        return ease(pulse(t + period / 2f, period))
    }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val d = wind(v)
        val g = gust(t, v)
        val b = MoBodies.of(body)
        val shield = MoArm(Offset(28f, -50f - 8f * g), bend = 0.6f)
        // The other arm comes across his chest, below the mouth on either body.
        val across = MoArm(Offset(-60f, b.mouth.y + 30f - b.shoulderY), bend = 0.4f)
        val windward = if (g > 0.5f) MoEye(shape = MoEyeShape.Shut) else MoEye(iris = 0.35f, lidDrop = 0.2f, gaze = Offset(-d * 0.6f, -0.1f))
        val leeward = MoEye(iris = 0.3f, open = 0.6f + 0.4f * (1f - g), lidDrop = 0.35f * g, gaze = Offset(-d * 0.8f, -0.2f))
        val idle = moIdle(t, energy = 0.4f)
        return idle.copy(
            palette = MoPalettes.Pale,
            leftEye = if (d > 0f) windward else leeward,
            rightEye = if (d > 0f) leeward else windward,
            brows = MoBrows(MoBrowKind.Worried, lift = 2f),
            mouth = if (g > 0.35f) MoMouth.Gritted else MoMouth.Wavy,
            leftArm = if (d > 0f) shield else across,
            rightArm = if (d > 0f) across else shield,
            armsInFront = true,
            legs = MoLegs(MoLeg(swing = 9f), MoLeg(swing = 9f)),
            // Blown flat and fluttering, never gone; in the lull it springs halfway back.
            sway = idle.sway * 0.3f + d * (if (d < 0f) 35f + 55f * g else 20f + 40f * g) + wave(t, 0.08f) * 5f * g,
            flare = -6f - 16f * g,
            tilt = -d * (2f + 6f * g),
            squash = idle.squash - 0.03f * g,
            shake = wave(t, 0.07f) * 1.2f * g,
            blinks = false,
        )
    }

    override fun DrawScope.foreground(t: Float, v: MoVariant) {
        val d = wind(v)
        val g = gust(t, v)
        if (g < 0.02f) return
        fun across(x: Float, y: Float) = Offset(if (d > 0f) x else 2f * CENTRE - x, y)
        // Heights that miss his eyes and his mouth on both bodies.
        val heights = listOf(216f, 104f, 392f, 160f)
        for (index in 0 until 3 + v.pick(2, salt = 2)) {
            val travel = cycle(t, 1.5f, offset = 0.2f + index * 0.27f)
            val fade = sin(travel * PI.toFloat())
            // Short of white: a gust passes over him, and at full strength it would read as a mark
            // drawn on his flame instead.
            val colour = MoPropWhite.copy(alpha = (0.75f * g * fade).coerceIn(0f, 1f))
            val tail = -40f + 200f * travel
            val y = heights[index] + 6f * sin(travel * 5f + index)
            // A bowed streak that ends in a curl, which is how a gust is drawn everywhere.
            val bow = (0..5).map { across(tail + 92f * it / 5f, y + sin(it / 5f * PI.toFloat()) * 7f * d) }
            val curl = (1..9).map {
                val angle = it / 9f * 1.7f * PI.toFloat()
                across(tail + 92f + sin(angle) * 12f, y - 12f + cos(angle) * 12f)
            }
            polyline(colour, 7f, bow + curl)
        }
        if (v.chance(30, salt = 3)) {
            // A blade and a stalk tumbling end over end along the floor, well clear of his face.
            val travel = cycle(t, 1.5f, offset = 0.55f)
            val turn = cycle(t, 1.1f) * 2f * PI.toFloat()
            val along = Offset(cos(turn), sin(turn) * d)
            val leaf = across(-20f + 240f * travel, 402f - 14f * sin(travel * PI.toFloat()))
            val green = MoPropLeaf.copy(alpha = (g * sin(travel * PI.toFloat())).coerceIn(0f, 1f))
            limb(leaf - along * 9f, leaf + along * 9f, green, 16f)
            limb(leaf + along * 16f, leaf + along * 23f, green, 3.5f)
        }
    }

    override val sparkles get() = false
}

/** He holds a little blanket to his chin and watches the room, and at a start yanks it up to his eyes. */
private object ComfortBlanket : MoAct("scared_comfort_blanket", MoOccasion.Scared) {
    private fun period(v: MoVariant) = v.between(4.4f, 5.8f, salt = 4)

    // Yanked up fast, lowered slowly.
    private fun up(t: Float, v: MoVariant): Float {
        val c = cycle(t, period(v))
        return ease((c - 0.62f) / 0.04f) * (1f - ease((c - 0.8f) / 0.2f))
    }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val b = MoBodies.of(body)
        val up = up(t, v)
        val first = if (v.chance(50, salt = 3)) 1f else -1f
        // Under his chin at rest; level with his mouth when he hides, which puts the hem just under his eyes.
        val hand = MoArm(Offset(-(b.shoulderHalf - 48f), b.mouth.y + 22f - 19f * up - b.shoulderY), bend = 0.55f)
        val eye = MoEye(iris = 0.45f - 0.15f * up, scale = 1.05f, gaze = Offset(first * sin(t * 1.3f + 0.9f) * 0.9f * (1f - up), -0.1f))
        val start = twitch(t, period(v), beat = 0.25f, offset = 0.38f)
        val idle = moIdle(t, energy = 0.5f)
        return idle.copy(
            leftEye = eye,
            rightEye = eye,
            brows = MoBrows(MoBrowKind.Worried, lift = 4f + 3f * up),
            mouth = MoMouth.Wavy,
            leftArm = hand,
            rightArm = hand,
            armsInFront = true,
            legs = MoLegs(MoLeg(swing = -6f), MoLeg(swing = -6f)),
            flare = 14f * start,
            squash = idle.squash + 0.03f * start,
            shake = wave(t, 0.07f) * (0.5f + 1.2f * up),
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val (colour, dark) = v.of(
            listOf(MoPropSteel to MoPropCharcoal, MoPropBlue to MoPropBlueDark, MoPropPurple to MoPropPurpleDark, MoPropTeal to MoPropTealDark),
            salt = 1,
        )
        val up = up(t, v)
        val l = rig.leftHand
        val r = rig.rightHand
        val top = l.y - 12f
        // As long as the body allows: the Low one has little room between his chin and the floor.
        val length = (FEET - 25f - (rig.mouth.y + 22f)).coerceAtMost(58f)
        val bottom = l.y + length
        val swing = wave(t, 1.7f) * 2f
        val sag = 5f * (1f - up)
        val middle = (l.x + r.x) / 2f

        // Wider at the hem than at his hands, so it hangs like cloth and not like a bucket.
        shape(colour, l.x - 16f to top, middle to top + sag, r.x + 16f to top, r.x + 26f + swing to bottom, l.x - 26f + swing to bottom)
        val hem = r.x - l.x + 52f
        for (index in 0..4) drawCircle(colour, hem / 10f, Offset(l.x - 26f + swing + hem * (index + 0.5f) / 5f, bottom))

        val pattern = MoPropCream.copy(alpha = 0.8f)
        if (v.chance(50, salt = 2)) {
            for (row in 0..1) for (index in 0..2) {
                val y = mix(top + 24f, bottom - 4f, row.toFloat())
                drawCircle(pattern, 5.5f, Offset(middle + (index - 1) * 36f + (row - 0.5f) * 18f + swing * row, y))
            }
        } else {
            for (row in 0..1) {
                val y = mix(top + 26f, bottom - 6f, row.toFloat())
                limb(Offset(l.x - 12f + swing * row, y), Offset(r.x + 12f + swing * row, y), pattern, 7f)
            }
        }
        // The fold along the top, in the darker partner.
        polyline(dark, 8f, listOf(Offset(l.x - 15f, top + 2f), Offset(middle, top + sag + 2f), Offset(r.x + 15f, top + 2f)))

        grippingMitten(rig, -1f)
        grippingMitten(rig, 1f)
    }
}

/** Mittens on his hips and a wide white smile for courage, which keeps giving way to knocking knees. */
private object BraveFace : MoAct("scared_brave_face", MoOccasion.Scared, intensity = MoIntensity.Mild) {
    private fun period(v: MoVariant) = v.between(4.8f, 6.4f, salt = 1)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val c = cycle(t, period(v))
        val slip = ease((c - 0.6f) / 0.08f) * (1f - ease((c - 0.84f) / 0.05f))
        val lead = if (v.chance(50, salt = 4)) 1f else -1f
        val knock = wave(t, 0.16f) * 3f * slip * lead
        // He catches himself and pulls the pose back on a little too hard.
        val pulledTogether = twitch(t, period(v), beat = 0.3f, offset = 0.13f)
        // The bend is what makes the pose read: it throws the elbow clear of his silhouette.
        val arm = mix(MoArm(Offset(-16f, 30f), bend = 1.35f), MoArm(Offset(6f, 38f), bend = 0.25f), slip)
        val eye = MoEye(iris = 0.62f - 0.2f * slip, gaze = if (v.chance(40, salt = 3)) Offset(0.6f, -0.2f) else Offset(0.15f, -0.1f))
        val idle = moIdle(t, energy = 0.8f)
        val leg = MoLeg(swing = -6f * slip + knock)
        return idle.copy(
            leftEye = eye,
            rightEye = eye,
            // The brows stay worried under the smile: they are what gives him away.
            brows = MoBrows(MoBrowKind.Worried, lift = 2f + 3f * slip),
            mouth = if (slip < 0.4f) MoMouth.Beam else MoMouth.Zigzag,
            leftArm = arm,
            rightArm = arm,
            armsInFront = true,
            legs = MoLegs(leg, leg),
            squash = idle.squash + 0.025f * (1f - slip) - 0.03f * slip + 0.04f * pulledTogether,
            shake = wave(t, 0.07f) * 0.8f * slip,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val side = if (v.chance(50, salt = 2)) 1f else -1f
        // Two drops to every smile, so the drop never jumps where the loop closes.
        val fall = cycle(t, period(v) / 2f, offset = 0.3f)
        val eye = if (side > 0f) rig.rightEye else rig.leftEye
        drop(eye + Offset(side * 34f, -18f + 46f * fall), 8.5f, MoPropWater.copy(alpha = if (fall < 0.75f) ease(fall / 0.08f) else (1f - fall) * 4f))
    }
}

internal val scaredActs2: List<MoAct> = listOf(
    CoinTower,
    PoundingHeart,
    TheCuckoo,
    Draught,
    ComfortBlanket,
    BraveFace,
)
