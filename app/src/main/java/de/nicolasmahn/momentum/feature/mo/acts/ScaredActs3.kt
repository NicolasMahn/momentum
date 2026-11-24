package de.nicolasmahn.momentum.feature.mo.acts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import de.nicolasmahn.momentum.core.ui.theme.MoFlameBelly
import de.nicolasmahn.momentum.core.ui.theme.MoFlameOuter
import de.nicolasmahn.momentum.core.ui.theme.MoIce
import de.nicolasmahn.momentum.core.ui.theme.MoIceLedge
import de.nicolasmahn.momentum.core.ui.theme.MoIceShine
import de.nicolasmahn.momentum.core.ui.theme.MoPropBlue
import de.nicolasmahn.momentum.core.ui.theme.MoPropCream
import de.nicolasmahn.momentum.core.ui.theme.MoPropGold
import de.nicolasmahn.momentum.core.ui.theme.MoPropGoldDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropInk
import de.nicolasmahn.momentum.core.ui.theme.MoPropMint
import de.nicolasmahn.momentum.core.ui.theme.MoPropPink
import de.nicolasmahn.momentum.core.ui.theme.MoPropPinkDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropSand
import de.nicolasmahn.momentum.core.ui.theme.MoPropSky
import de.nicolasmahn.momentum.core.ui.theme.MoPropTeal
import de.nicolasmahn.momentum.core.ui.theme.MoPropWater
import de.nicolasmahn.momentum.domain.mo.MoIntensity
import de.nicolasmahn.momentum.domain.mo.MoOccasion
import de.nicolasmahn.momentum.feature.mo.CENTRE
import de.nicolasmahn.momentum.feature.mo.FEET
import de.nicolasmahn.momentum.feature.mo.MoArm
import de.nicolasmahn.momentum.feature.mo.MoBodyKind
import de.nicolasmahn.momentum.feature.mo.MoBrowKind
import de.nicolasmahn.momentum.feature.mo.MoBrows
import de.nicolasmahn.momentum.feature.mo.MoEye
import de.nicolasmahn.momentum.feature.mo.MoLeg
import de.nicolasmahn.momentum.feature.mo.MoLegs
import de.nicolasmahn.momentum.feature.mo.MoMouth
import de.nicolasmahn.momentum.feature.mo.MoPose
import de.nicolasmahn.momentum.feature.mo.MoRig
import de.nicolasmahn.momentum.feature.mo.block
import de.nicolasmahn.momentum.feature.mo.chunk
import de.nicolasmahn.momentum.feature.mo.cycle
import de.nicolasmahn.momentum.feature.mo.flameTongue
import de.nicolasmahn.momentum.feature.mo.limb
import de.nicolasmahn.momentum.feature.mo.moIdle
import de.nicolasmahn.momentum.feature.mo.oval
import de.nicolasmahn.momentum.feature.mo.pulse
import de.nicolasmahn.momentum.feature.mo.sparkle
import de.nicolasmahn.momentum.feature.mo.twitch
import de.nicolasmahn.momentum.feature.mo.wave

/*
 * Scared, part three: small fears about what little he has left. A candle almost out, and the last
 * chip of a freeze melting on the floor.
 */

/** No freeze left: he holds up a candle stub whose tiny flame keeps guttering, and holds his breath at every dip. */
private object TheCandleStub : MoAct("scared_the_candle_stub", MoOccasion.Scared, intensity = MoIntensity.Mild) {
    private val waxes = listOf(MoPropCream to MoPropSand, MoPropPink to MoPropPinkDark, MoPropMint to MoPropTeal, MoPropSky to MoPropBlue)

    /** -1 when the viewer's left mitten holds the candle out, 1 when the right one does. */
    private fun side(v: MoVariant) = if (v.chance(50, salt = 2)) -1f else 1f

    private fun period(v: MoVariant) = v.between(2.7f, 3.8f, salt = 4)

    private fun dip(t: Float, v: MoVariant) = twitch(t, period(v), beat = 1.1f)

    /** He notices a moment after the flame has started to go. */
    private fun fright(t: Float, v: MoVariant) = twitch(t, period(v), beat = 1f, offset = -0.16f / period(v))

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val dip = dip(t, v)
        val fright = fright(t, v)
        val s = side(v)
        // He holds it out at arm's length, clear of his own face, and lifts it a little when it gutters.
        val holding = MoArm(Offset(40f, 14f - 3f * fright), bend = 0.25f)
        // The free mitten comes up beside his mouth: the candle is too far off to shelter, so what he
        // does instead is hold his breath. Across the belly, where it shows against the yellow.
        val free = MoArm(Offset(-56f, 12f - 6f * fright), bend = 0.45f, warm = 1f)
        val eye = MoEye(iris = 0.6f - 0.14f * fright, scale = 1f + 0.07f * fright, gaze = Offset(s * 0.85f, -0.15f))
        return moIdle(t, energy = 0.6f).copy(
            leftEye = eye,
            rightEye = eye,
            brows = MoBrows(MoBrowKind.Worried, lift = 2f + 4f * fright),
            mouth = MoMouth.Oh(size = 6f + 4f * fright),
            leftArm = if (s < 0f) holding else free,
            rightArm = if (s < 0f) free else holding,
            armsInFront = true,
            flare = -9f * dip,
            squash = -0.012f * dip,
            tilt = s * 2f * fright,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val s = side(v)
        val hand = if (s < 0f) rig.leftHand else rig.rightHand
        val (wax, waxLedge) = v.of(waxes, salt = 1)
        // The candle stands on the mitten rather than over it, so the hand still shows under the saucer.
        val saucer = hand + Offset(0f, -11f)
        chunk(saucer, width = 44f, height = 10f, MoPropGold, MoPropGoldDark, corner = 5f, drop = 5f)
        val stub = saucer + Offset(0f, -17f)
        chunk(stub, width = 30f, height = 24f, wax, waxLedge, corner = 9f, drop = 4f)
        block(stub + Offset(-s * 9f, 3f), 8f, 16f, wax, corner = 4f)
        val wickTop = stub + Offset(0f, -21f)
        limb(stub + Offset(0f, -12f), wickTop, MoPropInk, 3f)
        val size = 11f * (1f - v.between(0.6f, 0.8f, salt = 3) * dip(t, v)) * (1f + 0.06f * wave(t, 0.09f))
        // His own colours, so the little flame is plainly the same fire as his, only nearly out.
        flameTongue(wickTop + Offset(2f * wave(t, 0.5f), 1f), size, MoFlameOuter, MoFlameBelly)
    }
}

/** No freeze left: the last chip of one melts in a puddle beside him, and he lifts his foot away from every ripple. */
private object TheLastOfTheIce : MoAct("scared_the_last_of_the_ice", MoOccasion.Scared, intensity = MoIntensity.Mild) {
    private fun side(v: MoVariant) = if (v.chance(50, salt = 1)) 1f else -1f

    private fun period(v: MoVariant) = v.between(2.8f, 3.8f, salt = 4)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val s = side(v)
        // The foot goes up as the ripple arrives at his side of the puddle, not when it starts.
        val lift = twitch(t, period(v), beat = 1.3f, offset = -0.4f)
        val eye = MoEye(iris = 0.6f - 0.08f * lift, gaze = Offset(s * 0.9f, 0.9f), lid = -0.25f)
        val nearLeg = MoLeg(lift = 20f * lift, swing = -10f * lift)
        val nearArm = MoArm(Offset(-34f, 4f - 10f * lift), bend = 0.5f, warm = 1f)
        val farArm = MoArm(Offset(-40f, 20f), bend = 0.4f)
        return moIdle(t, energy = 0.6f).copy(
            leftEye = eye,
            rightEye = eye,
            brows = MoBrows(MoBrowKind.Worried, lift = 2f + 2f * lift),
            mouth = MoMouth.Wavy,
            leftArm = if (s < 0f) nearArm else farArm,
            rightArm = if (s < 0f) farArm else nearArm,
            armsInFront = true,
            legs = if (s < 0f) MoLegs(left = nearLeg) else MoLegs(right = nearLeg),
            tilt = -s * 2f - s * 3.5f * lift,
            // He gives the water a little more room as the ripple arrives.
            offset = Offset(-s * (30f + 8f * lift), 0f),
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val size = v.between(50f, 58f, salt = 2)
        // Its outer edge stays put just inside the frame, whatever its size.
        val q = Offset(CENTRE + side(v) * (157f - size), FEET - 6f)
        oval(q, size, size * 0.18f, MoPropWater)

        // A ring faked with two ovals. It leaves the chip's waterline, where the water below hides it,
        // widens to the rim and has faded out where the cycle wraps: no seam, and nothing grows past
        // the puddle. The fade is late, so the ring is still plain to see when it reaches his side.
        val r = cycle(t, period(v), offset = 0.12f)
        val ring = 30f + (size - 34f) * r
        oval(q, ring, ring * 0.18f, MoIceShine.copy(alpha = 0.9f * (1f - r * r * r)))
        oval(q, ring - 13f, (ring - 13f) * 0.13f, MoPropWater)

        val chip = q + Offset(0f, -16f + 1.5f * wave(t, 2.1f))
        withTransform({ rotate(-12f, chip) }) {
            chunk(chip, width = 42f, height = 32f, MoIce, MoIceLedge, corner = 8f, drop = 6f)
            limb(chip + Offset(-12f, -5f), chip + Offset(-4f, -13f), MoIceShine, 4f)
        }
        // Water over its foot, so it sits in the puddle and not on it.
        oval(q + Offset(0f, 3f), 30f, 5.5f, MoPropWater)

        val glints = v.pick(2, salt = 3) + 1
        // The first glint is at its brightest at t = 0, so the still frame always has one.
        sparkle(chip + Offset(-20f, -24f), 9f, 1f, MoIceShine.copy(alpha = pulse(t + 0.8f, 1.6f)))
        if (glints > 1) sparkle(chip + Offset(25f, -12f), 7f, 1f, MoIceShine.copy(alpha = pulse(t, 1.6f)))
    }

    override val ownsTheFloor get() = true
}

internal val scaredActs3: List<MoAct> = listOf(
    TheCandleStub,
    TheLastOfTheIce,
)
