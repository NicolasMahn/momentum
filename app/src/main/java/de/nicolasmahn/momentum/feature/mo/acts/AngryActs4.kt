package de.nicolasmahn.momentum.feature.mo.acts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import de.nicolasmahn.momentum.core.ui.theme.MoFlameBelly
import de.nicolasmahn.momentum.core.ui.theme.MoFlameMiddle
import de.nicolasmahn.momentum.core.ui.theme.MoInk
import de.nicolasmahn.momentum.core.ui.theme.MoPropBlue
import de.nicolasmahn.momentum.core.ui.theme.MoPropBlueDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropCharcoal
import de.nicolasmahn.momentum.core.ui.theme.MoPropGreyDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropSand
import de.nicolasmahn.momentum.core.ui.theme.MoPropShadow
import de.nicolasmahn.momentum.core.ui.theme.MoPropSky
import de.nicolasmahn.momentum.core.ui.theme.MoPropSteel
import de.nicolasmahn.momentum.core.ui.theme.MoPropTeal
import de.nicolasmahn.momentum.core.ui.theme.MoPropTealDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropWhite
import de.nicolasmahn.momentum.core.ui.theme.MoPropWood
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
import de.nicolasmahn.momentum.feature.mo.MoLeg
import de.nicolasmahn.momentum.feature.mo.MoLegs
import de.nicolasmahn.momentum.feature.mo.MoMouth
import de.nicolasmahn.momentum.feature.mo.MoPose
import de.nicolasmahn.momentum.feature.mo.MoRig
import de.nicolasmahn.momentum.feature.mo.angerMark
import de.nicolasmahn.momentum.feature.mo.block
import de.nicolasmahn.momentum.feature.mo.chunk
import de.nicolasmahn.momentum.feature.mo.cycle
import de.nicolasmahn.momentum.feature.mo.ease
import de.nicolasmahn.momentum.feature.mo.flameTongue
import de.nicolasmahn.momentum.feature.mo.limb
import de.nicolasmahn.momentum.feature.mo.moIdle
import de.nicolasmahn.momentum.feature.mo.oval
import de.nicolasmahn.momentum.feature.mo.pulse
import de.nicolasmahn.momentum.feature.mo.twitch
import de.nicolasmahn.momentum.feature.mo.wave
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/*
 * Angry, part four: two ways of making a scene about an overdue task that stop short of the red
 * tantrum, so both keep his own colours.
 */

/** Overdue and loud about it: a one-flame protest, banging a saucepan with a wooden spoon. */
private object PanAndSpoon : MoAct("angry_pan_and_spoon", MoOccasion.Angry) {
    private const val BEAT = 0.3f

    // Two silent beats, so the raised spoon hangs in the air long enough to be dreaded.
    private const val REST_BEATS = 2

    // How far into the bar t = 0 is: the blow has just landed, so the clang is out and he is shouting.
    private const val FIRST_FRAME = 0.06f
    private const val ATTACK = 0.04f
    private const val RINGING = 0.2f

    private const val SPOON = 52f

    // The spoon's angle from hanging straight down, swung toward the pan, when it lands.
    private const val STRIKE_DEGREES = 40f

    /** The pan's centre from the hand on its handle, x toward the spoon's side. */
    private val panFromHand = Offset(64f, 10f)

    /** Where the spoon's bowl lands, from the pan's centre: the far end of its rim. */
    private val rimFromPan = Offset(34f, -27f)

    /**
     * Where the bar is: how hard the last blow still rings, how far the spoon is up between two
     * blows, and how far it is raised over the rest.
     */
    private class Bar(val hit: Float, val lift: Float, val raise: Float)

    private fun bar(t: Float, v: MoVariant): Bar {
        val bangs = 3 + v.pick(2, salt = 3)
        val length = (bangs + REST_BEATS) * BEAT
        val at = cycle(t, length, offset = FIRST_FRAME / length) * length
        val lastBang = (bangs - 1) * BEAT
        val sinceBang = if (at < lastBang) at.mod(BEAT) else at - lastBang
        val hit = if (sinceBang < ATTACK) ease(sinceBang / ATTACK) else 1f - ease((sinceBang - ATTACK) / RINGING)
        if (at < lastBang) {
            // It rests on the rim for a moment, comes up slowly and goes down fast: a whack, not a wave.
            val up = ((sinceBang / BEAT - 0.15f) / 0.85f).coerceIn(0f, 1f)
            return Bar(hit, lift = sin(PI.toFloat() * up.pow(1.5f)), raise = 0f)
        }
        val through = sinceBang / (length - lastBang)
        val down = ((through - 0.82f) / 0.18f).coerceIn(0f, 1f)
        return Bar(hit, lift = 0f, raise = ease((through - 0.06f) / 0.36f) * (1f - down * down))
    }

    private fun panOnLeft(v: MoVariant) = v.chance(50, salt = 2)

    /** The pan sits under his mouth on either body, so the reach is measured from the mouth. */
    private fun panReach(shape: MoBody) = Offset(-36f, shape.mouth.y + 41f - shape.shoulderY)

    /** The spoon arm's reach that puts the bowl exactly on the rim, worked back from the pan. */
    private fun strikeReach(shape: MoBody): Offset {
        val strike = STRIKE_DEGREES * PI.toFloat() / 180f
        val pan = panReach(shape)
        // x runs from his centre toward the spoon's side.
        val bowlX = -(shape.shoulderHalf + pan.x) + panFromHand.x + rimFromPan.x
        val handX = bowlX + SPOON * sin(strike)
        return Offset(handX - shape.shoulderHalf, pan.y + panFromHand.y + rimFromPan.y - SPOON * cos(strike))
    }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val now = bar(t, v)
        val shape = MoBodies.of(body)
        val panArm = MoArm(panReach(shape) + Offset(0f, 3f * now.hit), bend = 0.3f)
        // The raise goes up more than out: halfway round the spoon points straight out, and must stay in view.
        val spoonArm = MoArm(strikeReach(shape) + Offset(0f, 8f * now.hit) + Offset(16f, -26f) * now.lift + Offset(14f, -52f) * now.raise, bend = 0.5f)
        val idle = moIdle(t, energy = 1.5f)
        // Over the rest he takes aim at the pan; otherwise the noise is meant for whoever is watching.
        val eye = MoEye(lid = 0.8f, iris = 0.6f, gaze = Offset(0f, 0.85f * now.raise))
        return idle.copy(
            leftEye = eye,
            rightEye = eye,
            brows = MoBrows(MoBrowKind.Angry),
            mouth = if (now.hit > 0.35f) MoMouth.Oh(7f + 8f * now.hit) else MoMouth.Gritted,
            leftArm = if (panOnLeft(v)) panArm else spoonArm,
            rightArm = if (panOnLeft(v)) spoonArm else panArm,
            armsInFront = true,
            flare = 6f + 9f * now.hit,
            // He hops with the spoon, lands with the blow, and breathes in under the raised spoon.
            offset = Offset(0f, -5f * now.lift),
            squash = idle.squash - 0.035f * now.hit + 0.03f * now.raise,
            shake = wave(t, 0.07f) * 1.3f * now.hit,
            blinks = false,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val now = bar(t, v)
        val panOnLeft = panOnLeft(v)
        val inward = if (panOnLeft) 1f else -1f
        val panHand = if (panOnLeft) rig.leftHand else rig.rightHand
        val spoonHand = if (panOnLeft) rig.rightHand else rig.leftHand
        val (colour, dark) = v.of(
            listOf(MoPropSteel to MoPropGreyDark, MoPropTeal to MoPropTealDark, MoPropBlue to MoPropBlueDark),
            salt = 1,
        )
        angerMark(Offset(CENTRE + 74f, rig.body.top + 96f), 0.75f + 0.1f * now.hit)

        val pan = panHand + Offset(inward * panFromHand.x, panFromHand.y)
        // The far end dips under the blow, turning about the hand that holds it.
        rotate(degrees = inward * 5f * now.hit, pivot = panHand) {
            limb(panHand - Offset(inward * 17f, 0f), pan + Offset(-inward * 34f, -panFromHand.y), MoPropCharcoal, 8f)
            chunk(pan, 68f, 34f, colour, dark, corner = 10f)
            block(pan + Offset(0f, -19f), 76f, 8f, dark, corner = 4f)
            limb(pan + Offset(-inward * 22f, -7f), pan + Offset(-inward * 22f, 5f), MoPropWhite.copy(alpha = 0.45f), 5f)
        }
        // A thumb over each handle, so both things are gripped and not stuck onto his mittens.
        drawCircle(rig.palette.feet, 8f, panHand + Offset(0f, 1f))

        // The clang rings off the pan's sides, clear of the handle and the floor, and fades as it travels.
        val clang = v.of(listOf(MoPropWhite, MoPropSky), salt = 4).copy(alpha = now.hit)
        val travel = 7f * (1f - now.hit)
        for ((side, degrees) in listOf(inward to -15f, inward to 12f, inward to 40f, -inward to 12f, -inward to 40f)) {
            val angle = degrees * PI.toFloat() / 180f
            val out = Offset(side * cos(angle), sin(angle))
            val from = pan + Offset(out.x * (46f + travel), 2f + out.y * (24f + travel))
            limb(from, from + out * 12f, clang, 4.5f)
        }

        // It trembles while he holds it up.
        val degrees = STRIKE_DEGREES - 25f * now.lift - 200f * now.raise + 3f * wave(t, 0.09f) * now.raise
        rotate(degrees = inward * degrees, pivot = spoonHand) {
            // Pale wood, because a brown spoon is lost against his orange.
            limb(spoonHand + Offset(0f, -13f), spoonHand + Offset(0f, SPOON - 8f), MoPropSand, 8f)
            oval(spoonHand + Offset(0f, SPOON), 11f, 14f, MoPropSand)
            oval(spoonHand + Offset(0f, SPOON + 0.5f), 7f, 9.5f, MoPropWood)
        }
        drawCircle(rig.palette.feet, 8f, spoonHand + Offset(0f, 1f))
    }

    override val sparkles get() = false
}

/** Impatient: little flames off his own temper stomp in a ring round his feet, and he drills them, fists on his hips. */
private object HisTemperMarches : MoAct("angry_his_temper_marches", MoOccasion.Angry, intensity = MoIntensity.Mild) {
    private const val RING_HALF_WIDTH = 130f
    private const val RING_DEPTH = 14f
    private const val RING_Y = FEET - 15f

    // Where the first marcher starts on the ring: just left of front and centre, under his eyes.
    private val start = 100f * PI.toFloat() / 180f

    private fun count(v: MoVariant) = 3 + v.pick(2, salt = 1)

    private fun direction(v: MoVariant) = if (v.chance(50, salt = 2)) 1f else -1f

    private fun hasRunt(v: MoVariant) = v.chance(60, salt = 3)

    private fun step(v: MoVariant) = if (v.chance(50, salt = 4)) 0.5f else 0.4f

    /** Where marcher [index] is on the ring; the sine of it is how near the viewer he is. */
    private fun angle(t: Float, v: MoVariant, index: Int): Float {
        // The runt cannot quite keep its place in the file.
        val lag = if (index == 0 && hasRunt(v)) -0.04f else 0f
        // Twelve steps to a lap, so his stamping and the ring come round together.
        val lap = cycle(t, 12f * step(v), offset = index.toFloat() / count(v) + lag)
        return start + direction(v) * lap * 2f * PI.toFloat()
    }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val tempo = step(v)
        val march = wave(t, 2f * tempo)
        // His glare drops to each marcher as it passes his toes, follows it across, and comes back up.
        var inspection = Offset.Zero
        for (index in 0 until count(v)) {
            val at = angle(t, v, index)
            inspection += Offset((cos(at) * 1.6f).coerceIn(-0.8f, 0.8f), 0.9f) * ease((sin(at) - 0.86f) / 0.1f)
        }
        val eye = MoEye(lid = 0.7f, iris = 0.62f, gaze = inspection)
        // He barks the time on every left foot.
        val barking = cycle(t, 2f * tempo, offset = 0.5f) * 2f * tempo < 0.18f
        val idle = moIdle(t, energy = 1.2f)
        return idle.copy(
            leftEye = eye,
            rightEye = eye,
            brows = MoBrows(MoBrowKind.Angry),
            mouth = if (barking) MoMouth.Oh(size = 9f) else MoMouth.Frown(width = 13f),
            leftArm = MoArm.Hip,
            rightArm = MoArm.Hip,
            armsInFront = true,
            legs = MoLegs(MoLeg(lift = 7f * max(march, 0f)), MoLeg(lift = 7f * max(-march, 0f))),
            offset = Offset(0f, -2f * abs(march)),
            squash = idle.squash - 0.02f * twitch(t, tempo, beat = 0.14f),
            flare = 3f + 3f * pulse(t, tempo),
        )
    }

    private fun DrawScope.marchers(t: Float, v: MoVariant, near: Boolean) {
        val tempo = step(v)
        for (index in 0 until count(v)) {
            val at = angle(t, v, index)
            val depth = sin(at)
            // They change layers at the ends of the ring, which lie outside his silhouette, so nothing pops.
            if ((depth >= 0f) != near) continue
            val runt = index == 0 && hasRunt(v)
            val size = (if (runt) 19f else 26f) * (0.92f + 0.08f * depth)
            // Nobody is quite in step, and the runt takes two hops to everyone else's one.
            val stride = wave(t + index * 0.17f, if (runt) tempo else 2f * tempo)
            val floor = Offset(CENTRE + cos(at) * RING_HALF_WIDTH, RING_Y + depth * RING_DEPTH)
            val base = floor - Offset(0f, 6f * abs(stride))
            // Positive when he is heading to the viewer's right.
            val heading = -depth * direction(v)

            oval(floor + Offset(0f, 2f), size * (0.8f - 0.15f * abs(stride)), 3.5f, MoPropShadow)
            for (side in listOf(-1f, 1f)) {
                oval(base + Offset(side * size * 0.38f, -3.5f * max(side * stride, 0f)), size * 0.32f, size * 0.2f, MoFlameMiddle)
            }
            // The tip trails behind, as a flame's does when it moves.
            rotate(degrees = -9f * heading + 3f * wave(t + index, 0.45f), pivot = base) {
                flameTongue(base, size, MoFlameMiddle)
                flameTongue(base - Offset(0f, 1.5f), size * 0.68f, MoFlameBelly)
                val face = base + Offset(heading * size * 0.14f, -size * 0.7f)
                for (side in listOf(-1f, 1f)) {
                    val eye = face + Offset(side * size * 0.27f, 0f)
                    drawCircle(MoInk, size * 0.15f, eye)
                    limb(eye + Offset(side * size * 0.2f, -size * 0.42f), eye + Offset(-side * size * 0.16f, -size * 0.22f), MoInk, size * 0.16f)
                }
            }
        }
    }

    override fun DrawScope.world(t: Float, v: MoVariant) = marchers(t, v, near = false)

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        angerMark(Offset(CENTRE + 74f, rig.body.top + 96f), 0.65f)
    }

    override fun DrawScope.foreground(t: Float, v: MoVariant) = marchers(t, v, near = true)

    override val ownsTheFloor get() = true
    override val sparkles get() = false
}

internal val angryActs4: List<MoAct> = listOf(
    PanAndSpoon,
    HisTemperMarches,
)
