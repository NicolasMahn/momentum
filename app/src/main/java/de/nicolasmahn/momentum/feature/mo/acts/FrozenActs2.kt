package de.nicolasmahn.momentum.feature.mo.acts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import de.nicolasmahn.momentum.core.ui.theme.MoPropBrown
import de.nicolasmahn.momentum.core.ui.theme.MoPropCream
import de.nicolasmahn.momentum.core.ui.theme.MoPropInk
import de.nicolasmahn.momentum.core.ui.theme.MoPropPink
import de.nicolasmahn.momentum.core.ui.theme.MoPropPurple
import de.nicolasmahn.momentum.core.ui.theme.MoPropPurpleDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropRed
import de.nicolasmahn.momentum.core.ui.theme.MoPropRedDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropShadow
import de.nicolasmahn.momentum.core.ui.theme.MoPropSilver
import de.nicolasmahn.momentum.core.ui.theme.MoPropSnow
import de.nicolasmahn.momentum.core.ui.theme.MoPropTeal
import de.nicolasmahn.momentum.core.ui.theme.MoPropTealDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropWhite
import de.nicolasmahn.momentum.core.ui.theme.MoPropWood
import de.nicolasmahn.momentum.core.ui.theme.MoPropWoodDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropYellow
import de.nicolasmahn.momentum.core.ui.theme.MoPropYellowDark
import de.nicolasmahn.momentum.domain.mo.MoIntensity
import de.nicolasmahn.momentum.domain.mo.MoOccasion
import de.nicolasmahn.momentum.feature.mo.FEET
import de.nicolasmahn.momentum.feature.mo.MoBodyKind
import de.nicolasmahn.momentum.feature.mo.MoBrowKind
import de.nicolasmahn.momentum.feature.mo.MoBrows
import de.nicolasmahn.momentum.feature.mo.MoEye
import de.nicolasmahn.momentum.feature.mo.MoEyeShape
import de.nicolasmahn.momentum.feature.mo.MoMouth
import de.nicolasmahn.momentum.feature.mo.MoPose
import de.nicolasmahn.momentum.feature.mo.block
import de.nicolasmahn.momentum.feature.mo.chunk
import de.nicolasmahn.momentum.feature.mo.curve
import de.nicolasmahn.momentum.feature.mo.cycle
import de.nicolasmahn.momentum.feature.mo.flameTongue
import de.nicolasmahn.momentum.feature.mo.floorShadow
import de.nicolasmahn.momentum.feature.mo.limb
import de.nicolasmahn.momentum.feature.mo.oval
import de.nicolasmahn.momentum.feature.mo.puff
import de.nicolasmahn.momentum.feature.mo.pulse
import de.nicolasmahn.momentum.feature.mo.wave
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Frozen: someone left a mug of cocoa beside the block for when he is out, and he can only look. */
private object FrozenCocoaForLater : MoAct("frozen_cocoa_for_later", MoOccasion.Frozen, MoBodyKind.entries.toSet(), MoIntensity.Mild) {
    private fun side(v: MoVariant) = if (v.chance(50, salt = 2)) 1f else -1f

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose = when (body) {
        MoBodyKind.Droplet, MoBodyKind.Husk -> frozenRest(body).copy(
            leftEye = MoEye(gaze = Offset(side(v) * 0.9f, 0.8f), lid = -0.3f, tear = 0.35f),
            rightEye = MoEye(gaze = Offset(side(v) * 0.9f, 0.8f), lid = -0.3f, tear = 0.35f),
            brows = MoBrows(MoBrowKind.Worried),
            mouth = MoMouth.Smile(width = 8f, depth = 4f),
        )
        MoBodyKind.Tall, MoBodyKind.Low -> frozenRest(body).copy(
            leftEye = MoEye(shape = MoEyeShape.Heart),
            rightEye = MoEye(shape = MoEyeShape.Heart),
            brows = MoBrows(MoBrowKind.Raised, lift = 3f),
            mouth = MoMouth.Oh(size = 6f),
        )
    }

    override fun DrawScope.atIce(t: Float, v: MoVariant, ice: Rect) {
        val s = side(v)
        val wall = ice.wall(s)
        val (colour, ledge) = v.of(
            listOf(MoPropRed to MoPropRedDark, MoPropTeal to MoPropTealDark, MoPropPurple to MoPropPurpleDark, MoPropYellow to MoPropYellowDark),
            salt = 1,
        )
        // Blazing leaves only 37 units between the widest block and the edge of the view, so the mug
        // is tall rather than wide, and the handle takes the rest of the strip.
        val mug = Offset(wall + s * 12f, FEET - 22f)
        val surface = Offset(mug.x, FEET - 44f)

        floorShadow(mug.x, 20f, MoPropShadow)
        // The handle goes on first so the mug covers its ends; it always faces away from him.
        for ((handleColour, drop) in listOf(ledge to 3f, colour to 0f)) {
            curve(Offset(mug.x + s * 11f, FEET - 37f + drop), Offset(mug.x + s * 32f, FEET - 27f + drop), Offset(mug.x + s * 11f, FEET - 16f + drop), handleColour, 6f)
        }
        chunk(mug, width = 26f, height = 40f, colour = colour, ledge = ledge, corner = 7f)
        oval(surface, 12f, 4f, MoPropCream)
        oval(surface, 10.5f, 3f, MoPropBrown)

        val marshmallows = 1 + v.pick(3, salt = 3)
        for (index in 0 until marshmallows) {
            val bob = wave(t + index * 0.7f, 2.4f) * 1.2f
            block(surface + Offset((index - (marshmallows - 1) / 2f) * 8f, -3f + bob), 7f, 6f, MoPropWhite, corner = 3f)
        }
        if (v.chance(30, salt = 5)) straw(surface, s)

        // Steam, not fire: white puffs that lean a little toward him and fade out before the ice.
        val puffs = 2 + v.pick(2, salt = 4)
        for (index in 0 until puffs) {
            val rise = cycle(t, 3.6f, offset = index.toFloat() / puffs)
            val at = Offset(mug.x - s * rise * 6f + sin(rise * 5f + index) * 4f, surface.y - 8f - rise * 60f)
            val alpha = 0.85f * min(1f, rise * 6f) * (1f - rise * rise)
            puff(at, 5f + rise * 5f, MoPropWhite.copy(alpha = alpha))
        }
    }

    private fun DrawScope.straw(surface: Offset, s: Float) {
        val from = surface + Offset(s * 3f, 0f)
        val to = from + Offset(s * 8f, -21f)
        limb(from, to, MoPropWhite, 4f)
        // Two bands across the straw, drawn along its normal; the straw's length is fixed, so this is safe.
        val along = (to - from) / 22.5f
        val across = Offset(-along.y, along.x) * 3f
        for (fraction in listOf(0.4f, 0.7f)) {
            val at = from + (to - from) * fraction
            limb(at - across, at + across, MoPropRed, 4f)
        }
    }

    override val ownsTheFloor get() = true
    override val sparkles get() = false
}

/** Frozen: somebody built a little snowman of him beside the block, scarf and all, and he is not sure it is a compliment. */
private object FrozenSnowTwin : MoAct("frozen_the_snow_twin", MoOccasion.Frozen, MoBodyKind.entries.toSet(), MoIntensity.Mild) {
    private fun side(v: MoVariant) = if (v.chance(50, salt = 2)) 1f else -1f

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose = frozenRest(body).copy(
        leftEye = MoEye(gaze = Offset(side(v), 0.4f), iris = 0.7f),
        rightEye = MoEye(gaze = Offset(side(v), 0.4f), iris = 0.7f),
        brows = MoBrows(MoBrowKind.Raised, lift = 1f, skew = 7f),
        mouth = MoMouth.Flat(width = 10f),
    )

    override fun DrawScope.atIce(t: Float, v: MoVariant, ice: Rect) {
        val s = side(v)
        val wall = ice.wall(s)
        val (colourA, colourB) = v.of(listOf(MoPropRed to MoPropCream, MoPropTeal to MoPropYellow, MoPropPink to MoPropPurple), salt = 1)
        val armsUp = v.chance(50, salt = 3)
        val eyes = if (v.chance(30, salt = 5)) colourA else MoPropInk
        // The wind comes up eased, holds a moment and dies away; squaring the pulse leaves half the loop nearly still.
        val gustPeriod = v.between(3.6f, 5f, salt = 4)
        val gust = pulse(t, gustPeriod).let { it * it }
        val shiver = wave(t, 0.12f) * 2f * gust

        // Blazing leaves only 37 units between the widest block and the edge of the view: the twin
        // fills them, with its twigs angled so neither reaches under the ice.
        val base = Offset(wall + s * 17f, FEET - 15f)
        val chest = Offset(base.x, FEET - 40f)
        floorShadow(base.x, 17f, MoPropShadow)
        for (dir in listOf(-1f, 1f)) {
            val from = Offset(chest.x + dir * 8f, FEET - 28f)
            val to = Offset(chest.x + dir * 14f, (if (armsUp) FEET - 39f else FEET - 21f) + shiver)
            limb(from, to, MoPropWood, 3f)
            limb(to, to + Offset(dir * 2.5f, if (armsUp) -3f else 2f), MoPropWoodDark, 3f)
        }
        drawCircle(MoPropSilver, 15f, base + Offset(0f, 4f))
        drawCircle(MoPropSnow, 15f, base)
        drawCircle(MoPropSnow, 11f, chest)
        // His tip leans to the viewer's right whichever side the twin stands on, because his does. A
        // knob rounds the tongue's point, so it is blunt like his and not a hat; anything on the
        // chest's shoulders read as ears at thumbnail size, so the twin has none.
        val tipBase = Offset(chest.x + 2f, FEET - 49f)
        rotate(18f, tipBase) {
            flameTongue(tipBase, 7f, MoPropSnow)
            drawCircle(MoPropSnow, 4f, tipBase + Offset(0f, -14.5f))
        }

        drawCircle(eyes, 3f, chest + Offset(-5f, -3f))
        drawCircle(eyes, 3f, chest + Offset(5f, -3f))
        curve(chest + Offset(-3f, 2f), chest + Offset(0f, 5f), chest + Offset(3f, 2f), MoPropInk, 2.5f)

        scarf(Offset(base.x, FEET - 30f), s, colourA, colourB, gust, t)
    }

    private fun DrawScope.scarf(at: Offset, s: Float, colourA: Color, colourB: Color, gust: Float, t: Float) {
        block(at, 26f, 7f, colourA, corner = 3.5f)
        block(at + Offset(-6f, 0f), 5f, 7f, colourB, corner = 1f)
        block(at + Offset(5f, 0f), 5f, 7f, colourB, corner = 1f)
        val knot = at + Offset(s * 6f, 0f)
        // The tails hang nearly straight down at rest and stream outward in a gust, with a flap on top.
        // At full stretch they stop short of the edge of the view on Blazing.
        val angle = (80f - 48f * gust + wave(t, 0.18f) * 8f * gust) * PI.toFloat() / 180f
        val trailing = angle + 12f * PI.toFloat() / 180f
        limb(knot, knot + Offset(s * cos(angle) * 9f, sin(angle) * 9f), colourA, 6f)
        limb(knot, knot + Offset(s * cos(trailing) * 7f, sin(trailing) * 7f), colourB, 6f)
        drawCircle(colourA, 4f, knot)
    }

    override val ownsTheFloor get() = true
    override val sparkles get() = false
}

internal val frozenActs2: List<MoAct> = listOf(
    FrozenCocoaForLater,
    FrozenSnowTwin,
)
