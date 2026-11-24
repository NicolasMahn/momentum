package de.nicolasmahn.momentum.feature.mo.acts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import de.nicolasmahn.momentum.core.ui.theme.MoPropBlueDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropBrown
import de.nicolasmahn.momentum.core.ui.theme.MoPropGlass
import de.nicolasmahn.momentum.core.ui.theme.MoPropInk
import de.nicolasmahn.momentum.core.ui.theme.MoPropOrange
import de.nicolasmahn.momentum.core.ui.theme.MoPropPink
import de.nicolasmahn.momentum.core.ui.theme.MoPropPinkDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropPurple
import de.nicolasmahn.momentum.core.ui.theme.MoPropPurpleDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropRedDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropSand
import de.nicolasmahn.momentum.core.ui.theme.MoPropShadow
import de.nicolasmahn.momentum.core.ui.theme.MoPropSilver
import de.nicolasmahn.momentum.core.ui.theme.MoPropSky
import de.nicolasmahn.momentum.core.ui.theme.MoPropSteel
import de.nicolasmahn.momentum.core.ui.theme.MoPropTeal
import de.nicolasmahn.momentum.core.ui.theme.MoPropTealDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropWater
import de.nicolasmahn.momentum.core.ui.theme.MoPropWhite
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
import de.nicolasmahn.momentum.feature.mo.MoPalettes
import de.nicolasmahn.momentum.feature.mo.MoPose
import de.nicolasmahn.momentum.feature.mo.MoRig
import de.nicolasmahn.momentum.feature.mo.grippingMitten
import de.nicolasmahn.momentum.feature.mo.block
import de.nicolasmahn.momentum.feature.mo.chunk
import de.nicolasmahn.momentum.feature.mo.cycle
import de.nicolasmahn.momentum.feature.mo.drop
import de.nicolasmahn.momentum.feature.mo.ease
import de.nicolasmahn.momentum.feature.mo.floorShadow
import de.nicolasmahn.momentum.feature.mo.limb
import de.nicolasmahn.momentum.feature.mo.moIdle
import de.nicolasmahn.momentum.feature.mo.oval
import de.nicolasmahn.momentum.feature.mo.puff
import de.nicolasmahn.momentum.feature.mo.pulse
import de.nicolasmahn.momentum.feature.mo.shape
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
 * More of a sick day: small games and small defeats. He is poorly, never in danger, and the
 * medicine around him is a prop like any other.
 */

private fun mix(from: Float, to: Float, amount: Float): Float = from + (to - from) * amount

private fun mix(from: Offset, to: Offset, amount: Float): Offset = from + (to - from) * amount

private fun mix(from: MoArm, to: MoArm, amount: Float): MoArm =
    MoArm(mix(from.reach, to.reach, amount), mix(from.bend, to.bend, amount))

/** Rises from 0 to 1 over [start]..[end]; the ends may not coincide, so the division is guarded. */
private fun ramp(x: Float, start: Float, end: Float): Float = ease((x - start) / max(end - start, 0.0001f))

/** Sickly: he keeps a tissue in the air with little puffs, and his tired eyes ride up and down with it. */
private object TissueInTheAir : MoAct("sickly_tissue_in_the_air", MoOccasion.Sickly, intensity = MoIntensity.Mild) {
    private fun second(v: MoVariant) = v.chance(25, salt = 4)

    private fun flight(t: Float, behind: Float = 0f) = cycle(t, 3f, offset = 0.45f + behind)

    /** Shot up in the first fifth of the flight, then the long float back down to the mitten. */
    private fun rise(u: Float): Float =
        if (u < 0.22f) sin(u / 0.22f * PI.toFloat() / 2f) else 1f - ease((u - 0.22f) / 0.78f)

    /** The puff sits astride the landing, so the tissue never quite touches his mitten. */
    private fun blow(u: Float): Float {
        val away = min(u, 1f - u)
        return if (away < 0.06f) cos(away / 0.06f * PI.toFloat() / 2f) else 0f
    }

    private fun blow(t: Float, v: MoVariant): Float =
        if (second(v)) max(blow(flight(t)), blow(flight(t, behind = 0.5f))) else blow(flight(t))

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val idle = moIdle(t, energy = 0.7f)
        val blow = blow(t, v)
        val watch = MoEye(lidDrop = 0.35f, gaze = Offset(0.9f, 0.4f - 1.3f * rise(flight(t))))
        return idle.copy(
            leftEye = watch,
            rightEye = watch,
            mouth = if (blow > 0f) MoMouth.Whistle else MoMouth.Flat(10f),
            leftArm = MoArm.Hip,
            // An open mitten held out clear of his silhouette, for the tissue to land on.
            rightArm = MoArm(Offset(32f, -16f + 3f * blow), bend = 0.25f),
            sway = idle.sway - 12f * blow,
            squash = idle.squash - 0.02f * blow,
            tilt = wave(t, 3f),
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val (colour, dark) = v.of(listOf(MoPropTeal to MoPropTealDark, MoPropPurple to MoPropPurpleDark, MoPropSky to MoPropBlueDark), salt = 2)
        floorShadow(247f, 27f, MoPropShadow)
        chunk(Offset(247f, FEET - 16f), 50f, 32f, colour, dark)
        oval(Offset(247f, FEET - 32f), 15f, 3.5f, MoPropInk)
        shape(MoPropWhite, 238f to FEET - 33f, 240f to FEET - 50f, 246f to FEET - 43f, 253f to FEET - 54f, 256f to FEET - 33f)
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val colour = v.of(listOf(MoPropWhite, MoPropPink, MoPropSky), salt = 1)
        val apex = v.between(120f, 160f, salt = 3)
        if (second(v)) tissue(flight(t, behind = 0.5f), rig.rightHand + Offset(-12f, 4f), apex * 0.7f, colour)
        tissue(flight(t), rig.rightHand, apex, colour)
        val blow = blow(t, v)
        // The breath leaves his face and travels towards the tissue, where white still shows.
        puff(rig.mouth + Offset(52f + 22f * blow, -2f), 7f, MoPropWhite.copy(alpha = 0.7f * blow))
    }

    private fun DrawScope.tissue(u: Float, hand: Offset, apex: Float, colour: Color) {
        // It only rocks like a leaf while it floats: not on the way up, and not as it lands.
        val floating = ease((u - 0.22f) / 0.15f) * ease((1f - u) / 0.12f)
        val rock = sin(u * 4f * PI.toFloat()) * floating
        val at = hand + Offset(2f + rock * 5f, -26f - apex * rise(u))
        val angle = rock * 0.5f
        // Blown thin on the way up, spread wide on the way down.
        val wide = 0.75f + 0.25f * ease((u - 0.19f) / 0.06f) * ease((1f - u) / 0.06f)
        val curl = 3.5f * rock
        val outline = listOf(
            Offset(-19f, -12f - curl), Offset(0f, -16f), Offset(19f, -12f + curl), Offset(17f, 11f + curl),
            Offset(5f, 14f), Offset(-6f, 10f), Offset(-18f, 13f - curl),
        ).map { at + Offset(it.x * wide * cos(angle) - it.y * sin(angle), it.x * wide * sin(angle) + it.y * cos(angle)) }
        shape(colour, *outline.map { it.x to it.y }.toTypedArray())
    }

    override val ownsTheFloor get() = true
    override val sparkles get() = false
}

/** Sickly: woozy, he tips over very slowly, saves himself with windmilling arms, and starts to tip the other way. */
private object SlowTopple : MoAct("sickly_slow_topple", MoOccasion.Sickly, intensity = MoIntensity.Strong) {
    private val leaning = MoArm(Offset(28f, 2f), bend = 0.2f)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val idle = moIdle(t, energy = 0.5f)
        val h = cycle(t, 7f, offset = 0.2f)
        val first = if (v.chance(50, salt = 1)) 1f else -1f
        val side = if (h < 0.5f) first else -first
        val q = (h * 2f).mod(1f)
        val amp = v.between(15f, 17f, salt = 2)
        val turns = 1 + v.pick(2, salt = 3)

        // The save overshoots to -2, which is the +2 the lean to the other side starts from.
        val lean = when {
            q < 0.62f -> mix(2f, amp, ease(q / 0.62f))
            q < 0.68f -> amp + wave(t, 0.09f) * 0.5f
            else -> mix(amp, -2f, ramp(q, 0.68f, 0.96f))
        }
        val far = abs(lean) / amp
        val saving = q >= 0.62f
        val relief = ramp(q, 0.92f, 1f)

        val windmill = ramp(q, 0.62f, 0.66f) * (1f - ramp(q, 0.86f, 0.95f))
        val angle = 2f * PI.toFloat() * turns * (q - 0.62f) / 0.3f
        fun arm(armSide: Float): MoArm {
            val resting = mix(MoArm.Down, leaning, max(lean * side * armSide, 0f) / amp)
            val turn = angle + if (armSide > 0f) 0f else PI.toFloat()
            // Low and close to the shoulder: a high mitten has the most leverage under the tilt, and
            // at the far side of a full lean it would be carried out of the frame.
            val whirl = MoArm(Offset(20f + 12f * cos(turn), -12f + 30f * sin(turn)), bend = 0.3f)
            return mix(resting, whirl, windmill)
        }

        val drowsy = MoEye(lidDrop = 0.2f + 0.5f * far, gaze = Offset(side * 0.5f, 0.3f))
        val eye = when {
            !saving -> drowsy
            q < 0.70f && v.chance(35, salt = 4) -> MoEye(shape = MoEyeShape.Spiral, scale = 1.06f)
            else -> MoEye(
                iris = mix(0.5f, 0.76f, relief),
                scale = mix(1.06f, 1f, relief),
                lidDrop = mix(0f, 0.2f + 0.5f * 2f / amp, relief),
                gaze = mix(Offset(side * 0.3f, -0.3f), Offset(-side * 0.5f, 0.3f), relief),
            )
        }
        // The foot on the high side leaves the floor while he hangs there.
        val foot = MoLeg(lift = 14f * windmill, swing = 6f * windmill)
        return idle.copy(
            palette = MoPalettes.Queasy,
            leftEye = eye,
            rightEye = eye,
            brows = if (saving && q < 0.92f) MoBrows(MoBrowKind.Raised, lift = 6f) else MoBrows(MoBrowKind.Worried),
            mouth = when {
                !saving -> MoMouth.Wavy
                q < 0.92f -> MoMouth.Zigzag
                else -> MoMouth.Oh(7f)
            },
            leftArm = arm(-1f),
            rightArm = arm(1f),
            armsInFront = true,
            legs = if (side > 0f) MoLegs(left = foot) else MoLegs(right = foot),
            tilt = side * lean,
            // His flame hangs the other way like a counterweight, but not so far that it hides the lean.
            sway = idle.sway - side * lean * 2.6f,
            // His feet shuffle under him, which also keeps the leaning mitten inside the frame.
            offset = Offset(-side * lean * 0.5f, -8f * far),
            shake = if (q in 0.62f..0.68f) wave(t, 0.06f) * 1.5f else 0f,
            blinks = false,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val h = cycle(t, 7f, offset = 0.2f)
        val first = if (v.chance(50, salt = 1)) 1f else -1f
        val side = if (h < 0.5f) first else -first
        val q = (h * 2f).mod(1f)
        if (q > 0.9f) {
            val k = (q - 0.9f) / 0.1f
            // It swells out of him and thins away, so it is never simply there for a frame.
            puff(rig.mouth + Offset(-side * (14f + 32f * k), 4f), 7f, MoPropWhite.copy(alpha = 0.7f * sin(PI.toFloat() * k)))
        }
    }

    override val sparkles get() = false
}

/** Sickly: he holds a fizzing vitamin up beside his cheek and watches bubble after bubble climb and pop. */
private object VitaminFizz : MoAct("sickly_vitamin_fizz", MoOccasion.Sickly, intensity = MoIntensity.Mild) {
    private fun side(v: MoVariant) = if (v.chance(50, salt = 1)) 1f else -1f

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val idle = moIdle(t, energy = 0.7f)
        val s = side(v)
        val pop = twitch(t, 2.4f, beat = 0.2f, offset = 0.08f)
        // The gaze climbs with a bubble and snaps back for the next, like an eye reading lines.
        // The glass is held above his eyes, so the whole climb is an upward look.
        val watch = MoEye(lidDrop = 0.4f, gaze = Offset(s * 0.9f, -0.1f - 0.8f * cycle(t, 2.4f)), open = 1f - 0.8f * pop)
        val holding = MoArm(Offset(23f, -40f), bend = 0.6f)
        return idle.copy(
            leftEye = watch,
            rightEye = watch,
            brows = MoBrows(),
            mouth = MoMouth.Flat(10f),
            leftArm = if (s < 0f) holding else MoArm.Down,
            rightArm = if (s > 0f) holding else MoArm.Down,
            flare = 6f * pop,
            tilt = s * 2f,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val s = side(v)
        val hand = if (s > 0f) rig.rightHand else rig.leftHand
        val g = hand + Offset(s * 12f, -18f)
        val tablet = v.of(listOf(MoPropOrange, MoPropYellow, MoPropPink), salt = 2)
        val bubbles = 4 + v.pick(3, salt = 3)

        if (v.chance(40, salt = 4)) {
            limb(g + Offset(-s * 4f, 20f), g + Offset(s * 6f, -46f), MoPropPink, 4f)
            limb(g + Offset(s * 6f, -46f), g + Offset(s * 18f, -51f), MoPropPink, 4f)
        }
        block(g, 42f, 70f, MoPropGlass, corner = 8f)
        block(g + Offset(0f, 9f), 36f, 50f, MoPropWater.copy(alpha = 0.55f), corner = 6f)
        oval(g + Offset(wave(t, 0.13f) * 1.5f, 27f), 13f, 6f, tablet)
        for (index in 0 until bubbles) {
            val r = cycle(t, 2.4f, offset = index.toFloat() / bubbles)
            val at = g + Offset(sin(index * 2.1f + r * 5f) * 9f, 24f - 38f * r)
            if (r < 0.92f) {
                drawCircle(MoPropWhite.copy(alpha = 0.9f), 3f + 1.5f * r, at)
            } else {
                val burst = (r - 0.92f) / 0.08f
                for (dot in -1..1) {
                    drawCircle(MoPropWhite.copy(alpha = 0.9f * (1f - burst)), 1.5f, at + Offset(dot * (3f + 5f * burst), -4f - burst * (8f - abs(dot) * 3f)))
                }
            }
        }
        limb(g + Offset(-15f, -26f), g + Offset(-15f, 4f), MoPropWhite.copy(alpha = 0.7f), 4f)
        grippingMitten(rig, s)
    }

    override val sparkles get() = false
}

/** Sickly: a snail carries his medicine past, and he is too slow today to catch even that. */
private object SnailCourier : MoAct("sickly_snail_courier", MoOccasion.Sickly, intensity = MoIntensity.Mild) {
    private const val STRETCHES = 7

    /** Drawn a third larger than its numbers, which still keeps the parcel under his mouth on the low body. */
    private const val SIZE = 1.3f

    /** From its middle to the end of its head, as drawn. */
    private const val HALF_LENGTH = 48f

    private fun direction(v: MoVariant) = if (v.chance(50, salt = 1)) 1f else -1f

    // Slow for a courier, quick for him: short enough that the crossing is visibly under way.
    private fun round(t: Float) = cycle(t, 13f, offset = 0.165f)

    /** How far through its stretches the snail is: it crosses in the first 70 percent of a round. */
    private fun stretch(u: Float): Float = (u / 0.7f).coerceIn(0f, 1f) * STRETCHES

    /** Where the snail is, relative to his centre and along its way: each stretch ends in a little rest. */
    private fun along(u: Float): Float {
        val at = stretch(u)
        val done = min(floor(at), STRETCHES.toFloat())
        return -200f + 400f * (done + ease((at - done) / 0.6f)) / STRETCHES
    }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val idle = moIdle(t, energy = 0.6f)
        val d = direction(v)
        val u = round(t)
        // Once it is gone he sighs at the viewer, then looks to the side it comes round from.
        val sigh = ramp(u, 0.76f, 0.79f) * (1f - ramp(u, 0.86f, 0.89f))
        val following = (along(u) / 160f).coerceIn(-1f, 1f)
        val lookX = d * 0.9f * mix(following, -1f, ramp(u, 0.88f, 0.98f))
        val eye = MoEye(lidDrop = 0.35f - 0.05f * sigh, gaze = mix(Offset(lookX, 0.9f), Offset(0f, -0.2f), sigh))
        // He starts after it once it is past him and arrives over empty floor.
        val reach = ramp(u, 0.49f, 0.64f) * (1f - ramp(u, 0.68f, 0.77f))
        val after = mix(MoArm.Down, MoArm(Offset(40f, 50f), bend = 0.1f), reach)
        return idle.copy(
            leftEye = eye,
            rightEye = eye,
            brows = MoBrows(MoBrowKind.Worried),
            mouth = if (sigh > 0.5f) MoMouth.Oh(5f) else MoMouth.Flat(10f),
            leftArm = if (d < 0f) after else MoArm.Down,
            rightArm = if (d > 0f) after else MoArm.Down,
            squash = idle.squash - 0.04f * reach - 0.02f * sigh,
            tilt = d * 5f * reach,
            shake = wave(t, 1.3f) * 1.2f,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val u = round(t)
        if (u > 0.77f && u < 0.86f) {
            val k = (u - 0.77f) / 0.09f
            // It leaves his lips and thins away, so it is never simply there for a frame.
            puff(rig.mouth + Offset(direction(v) * (10f + 26f * k), 8f + 8f * k), 6f, MoPropWhite.copy(alpha = 0.6f * sin(PI.toFloat() * k)))
        }
    }

    override fun DrawScope.foreground(t: Float, v: MoVariant) {
        val u = round(t)
        if (u >= 0.7f) return
        val d = direction(v)
        val x = CENTRE + d * along(u)
        val at = stretch(u)
        val moving = ((at - floor(at)) / 0.6f).coerceIn(0f, 1f)
        // The head goes first and the tail is pulled after, which is how a snail gets anywhere.
        val head = x + d * (22f + 5f * sin(moving * PI.toFloat()))
        val (shell, spiral) = v.of(listOf(MoPropPurple to MoPropPurpleDark, MoPropTeal to MoPropTealDark, MoPropPink to MoPropPinkDark, MoPropOrange to MoPropRedDark), salt = 2)
        val cap = v.of(listOf(MoPropWhite, MoPropYellow, MoPropPink), salt = 3)
        val back = Offset(x - d * 4f, 404f)

        // A smaller Mo leaves air between his frame and the canvas, where a clipped snail would look
        // sliced. So it fades in and out just inside the frame, and the clip only catches the rest.
        // It stays solid while it is whole, and only dissolves once its head is past the edge: a
        // half-faded snail in open floor reads as a dark smudge, not as one leaving.
        val inside = min(x + 45f, 275f - x) - HALF_LENGTH * SIZE
        val fade = ramp(inside, -34f, -10f)
        fun Color.faded() = copy(alpha = alpha * fade)
        clipRect(left = -45f, top = 0f, right = 275f, bottom = 431f) { withTransform({ scale(SIZE, SIZE, Offset(x, FEET)) }) {
            floorShadow(x, 30f, MoPropShadow.faded())
            limb(Offset(x - d * 30f, 422f), Offset(x - d * 60f, 422f), MoPropSilver.copy(alpha = 0.35f).faded(), 3f)
            limb(Offset(x - d * 66f, 422f), Offset(x - d * 94f, 422f), MoPropSilver.copy(alpha = 0.15f).faded(), 3f)
            limb(Offset(x - d * 24f, 418f), Offset(head, 418f), MoPropSand.faded(), 14f)
            drawCircle(MoPropSand.faded(), 8f, Offset(head + d * 2f, 412f))
            for (stalk in 0..1) {
                val from = Offset(head + d * (stalk * 5f - 1f), 408f)
                val to = Offset(from.x + d * 2f + wave(t + stalk * 0.2f, 0.6f) * 2f, 395f)
                limb(from, to, MoPropSand.faded(), 3f)
                drawCircle(MoPropSand.faded(), 3f, to)
                drawCircle(MoPropInk.faded(), 1.2f, to + Offset(d * 0.8f, 0f))
            }

            // The parcel goes on first, so the shell hides the foot of it and the straps end on the shell.
            if (v.chance(25, salt = 4)) {
                val capsule = back + Offset(0f, -21f)
                block(capsule, 28f, 13f, MoPropWhite.faded(), corner = 6.5f)
                block(capsule + Offset(-d * 7f, 0f), 14f, 13f, cap.faded(), corner = 6.5f)
                drawRect(cap.faded(), capsule + Offset(if (d > 0f) -7f else 0f, -6.5f), Size(7f, 13f))
            } else {
                chunk(back + Offset(0f, -23f), 16f, 16f, MoPropSky.faded(), MoPropBlueDark.faded(), corner = 5f, drop = 3f)
                block(back + Offset(0f, -33.5f), 12f, 5f, cap.faded(), corner = 2.5f)
            }
            drawCircle(shell.faded(), 16f, back)
            drawCircle(spiral.faded(), 9f, back, style = Stroke(3f))
            drawCircle(spiral.faded(), 3f, back)
            for (strap in listOf(-1f, 1f)) {
                limb(back + Offset(strap * 8f, -28f), back + Offset(strap * 13f, -8f), MoPropBrown.faded(), 3f)
            }
        } }
    }

    override val ownsTheFloor get() = true
}

/** Sickly: too hot, with his tongue out and a mitten fanning; one hopeful beat; then too cold, hugging himself. */
private object TooHotTooCold : MoAct("sickly_too_hot_too_cold", MoOccasion.Sickly) {
    private val hug = MoArm(Offset(-58f, 30f), bend = -0.5f)

    /** Where he is between the fever (1) and the chills (-1); clipped, so each spell holds for a while. */
    private fun fever(t: Float, v: MoVariant): Float {
        val period = v.between(7f, 9f, salt = 3)
        val start = if (v.chance(50, salt = 1)) 0.25f else 0.75f
        return (1.6f * sin((cycle(t, period, offset = start)) * 2f * PI.toFloat())).coerceIn(-1f, 1f)
    }

    private fun fanSide(v: MoVariant) = if (v.chance(50, salt = 2)) 1f else -1f

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val idle = moIdle(t, energy = 0.8f)
        val k = fever(t, v)
        val hot = max(k, 0f)
        val cold = max(-k, 0f)
        val right = 1f - min(abs(k) / 0.3f, 1f)
        val f = fanSide(v)

        // It flaps beside his face, clear of his silhouette and never over an eye, and only as hard as he is hot.
        val fan = MoArm(Offset(26f + wave(t, 0.22f) * 12f * hot, -46f + wave(t, 0.44f) * 4f * hot), bend = 0.6f)
        fun arm(side: Float): MoArm = when {
            k < 0f -> mix(MoArm.Down, hug, ease(cold))
            side == f -> mix(MoArm.Down, fan, ease(hot))
            else -> MoArm.Down
        }
        val eye = MoEye(lidDrop = 0.5f * hot, iris = mix(0.76f, 0.6f, cold), lid = -0.2f * cold)
        val knock = wave(t, 0.16f) * 3f * cold
        val leg = MoLeg(swing = -5f * cold + knock)
        return idle.copy(
            palette = MoPalettes.Queasy,
            leftEye = eye,
            rightEye = eye,
            brows = when {
                right > 0f -> MoBrows(MoBrowKind.Raised, lift = 3f * right)
                else -> MoBrows(MoBrowKind.Worried, lift = 3f * cold)
            },
            mouth = when {
                k > 0.3f -> MoMouth.TongueOut
                k < -0.3f -> MoMouth.Gritted
                else -> MoMouth.Smile(width = 12f, depth = 10f)
            },
            leftArm = arm(-1f),
            rightArm = arm(1f),
            // Always in front: the hug needs it, and switching sides of the body mid-loop would show.
            armsInFront = true,
            legs = MoLegs(leg, leg),
            flare = 22f * hot - 8f * cold,
            squash = idle.squash + 0.05f * hot - 0.06f * cold,
            shake = wave(t, 0.06f) * 2f * cold,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val k = fever(t, v)
        val hot = max(k, 0f)
        val cold = max(-k, 0f)
        // Sweat rolls down the cheek the mitten is not fanning.
        val f = fanSide(v)
        val cheek = if (f > 0f) rig.leftEye else rig.rightEye
        val drops = if (v.chance(40, salt = 4)) 2 else 1
        for (index in 0 until drops) {
            val fall = cycle(t, 1.5f, offset = index * 0.5f)
            // Just outside his silhouette, beside the cheek, where water blue shows up.
            val at = cheek + Offset(-f * (64f + index * 12f), -44f - index * 22f + 58f * fall)
            drop(at, 7f, MoPropWater.copy(alpha = hot * ramp(fall, 0f, 0.12f) * (1f - fall * fall)))
        }
        // The little draught his mitten makes, on its way to his face.
        val hand = if (f > 0f) rig.rightHand else rig.leftHand
        val flap = wave(t, 0.22f)
        for (row in 0..1) {
            val from = hand + Offset(-f * (20f + 4f * flap), -12f + row * 14f)
            limb(from, from + Offset(-f * 12f, 0f), MoPropWhite.copy(alpha = 0.85f * hot * (0.5f + 0.5f * flap)), 4f)
        }
        for (side in listOf(-1f, 1f)) {
            for (row in 0..1) {
                val at = rig.mouth + Offset(side * (112f + row * 6f + wave(t, 0.2f) * 3f), 6f - row * 30f)
                limb(at, at + Offset(side * 7f, -7f), MoPropSky.copy(alpha = cold), 4f)
                limb(at + Offset(side * 7f, -7f), at + Offset(side * 14f, 0f), MoPropSky.copy(alpha = cold), 4f)
            }
        }
    }

    override val sparkles get() = false
}

/** Sickly: a spoonful of syrup creeps toward a mouth clamped shut, and every time he loses his nerve. */
private object TheSpoonful : MoAct("sickly_the_spoonful", MoOccasion.Sickly) {
    /** From the mitten to the bowl. A spoon is one rigid thing: only the hand brings it closer. */
    private const val HANDLE = 76f

    private fun side(v: MoVariant) = if (v.chance(50, salt = 1)) 1f else -1f

    private fun turn(t: Float) = cycle(t, 6.5f, offset = 0.30f)

    /** How close the spoon has come: up in small brave steps, a trembling hold, and back down. */
    private fun approach(p: Float, v: MoVariant): Float {
        val steps = 2 + v.pick(2, salt = 3)
        val x = (p / 0.55f).coerceIn(0f, 1f) * steps
        val done = min(floor(x), steps.toFloat())
        val up = (done + ease((x - done) * 2f)) / steps
        return up * (1f - ramp(p, 0.65f, 0.78f))
    }

    private fun tremble(p: Float) = if (p in 0.55f..0.65f) 1f else 0f

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val idle = moIdle(t, energy = 0.7f)
        val s = side(v)
        val p = turn(t)
        val a = approach(p, v)
        val sheepish = ramp(p, 0.78f, 0.82f) * (1f - ramp(p, 0.94f, 1f))
        // The spoon is held at the height of his mouth on either body, which keeps it under the eye.
        // The mitten stays outside his silhouette, where it does not vanish into his own colour.
        val mouthHeight = MoBodies.of(body).let { it.mouth.y + 3f - it.shoulderY }
        val spoonArm = MoArm(Offset(44f - 25f * a, mouthHeight), bend = 0.7f)
        val bracing = mix(MoArm.Down, MoArm(Offset(36f, 4f), bend = 0.1f), a)
        val eyeing = MoEye(iris = mix(0.55f, 0.76f, sheepish), gaze = mix(Offset(-s * 0.3f, 0.9f), Offset(0f, -0.2f), sheepish))
        val squeezed = MoEye(open = 0.15f + 0.85f * (1f - a), gaze = mix(Offset(s * 0.3f, 0.5f), Offset(0f, -0.2f), sheepish))
        return idle.copy(
            palette = MoPalettes.Queasy,
            leftEye = if (s < 0f) eyeing else squeezed,
            rightEye = if (s > 0f) eyeing else squeezed,
            brows = if (sheepish > 0.5f) MoBrows(MoBrowKind.Raised, lift = 4f) else MoBrows(MoBrowKind.Worried, lift = 2f, skew = 4f),
            mouth = if (sheepish > 0.5f) MoMouth.Wavy else MoMouth.Pout,
            leftArm = if (s < 0f) spoonArm else bracing,
            rightArm = if (s > 0f) spoonArm else bracing,
            armsInFront = true,
            // All of him leans away from it, the tip most of all.
            tilt = -s * (3f + 5f * a) * (1f - sheepish),
            sway = idle.sway - s * (8f + 16f * a) * (1f - sheepish),
            squash = idle.squash - 0.02f * a,
            shake = wave(t, 0.06f) * 1.4f * tremble(p),
            blinks = a <= 0f,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val s = side(v)
        val hand = if (s > 0f) rig.rightHand else rig.leftHand
        // The long handle crosses from the mitten to his lips, and stops a pout's width short of them.
        val bowl = hand + Offset(-s * HANDLE, 0f)
        val syrup = v.of(listOf(MoPropPink, MoPropTeal, MoPropOrange, MoPropPurple), salt = 2)
        val wobble = wave(t, 0.3f) * (1f + 2f * tremble(turn(t)))
        // Steel under silver: plain silver is lost against his pale belly.
        limb(hand + Offset(s * 15f, 0f), bowl, MoPropSteel, 7f)
        if (v.chance(40, salt = 4)) drop(bowl + Offset(0f, 7f), 3.5f + pulse(t, 1.8f) * 1.5f, syrup)
        oval(bowl + Offset(0f, 2f), 18f, 10f, MoPropSteel)
        oval(bowl, 18f, 9f, MoPropSilver)
        oval(bowl + Offset(0f, -4f), 14f + wobble, 7.5f, syrup)
        drawCircle(MoPropWhite.copy(alpha = 0.7f), 2.5f, bowl + Offset(-5f, -7f))
        grippingMitten(rig, s)
    }

    override val sparkles get() = false
}

internal val sicklyActs2: List<MoAct> = listOf(
    TissueInTheAir,
    SlowTopple,
    VitaminFizz,
    SnailCourier,
    TooHotTooCold,
    TheSpoonful,
)
