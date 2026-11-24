package de.nicolasmahn.momentum.feature.mo.acts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import de.nicolasmahn.momentum.core.ui.theme.MoPropBlue
import de.nicolasmahn.momentum.core.ui.theme.MoPropBlueDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropCream
import de.nicolasmahn.momentum.core.ui.theme.MoPropGold
import de.nicolasmahn.momentum.core.ui.theme.MoPropGoldDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropGreen
import de.nicolasmahn.momentum.core.ui.theme.MoPropGrey
import de.nicolasmahn.momentum.core.ui.theme.MoPropGreyDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropInk
import de.nicolasmahn.momentum.core.ui.theme.MoPropMint
import de.nicolasmahn.momentum.core.ui.theme.MoPropNavy
import de.nicolasmahn.momentum.core.ui.theme.MoPropPink
import de.nicolasmahn.momentum.core.ui.theme.MoPropPinkDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropPurple
import de.nicolasmahn.momentum.core.ui.theme.MoPropPurpleDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropRed
import de.nicolasmahn.momentum.core.ui.theme.MoPropRedDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropShadow
import de.nicolasmahn.momentum.core.ui.theme.MoPropSilver
import de.nicolasmahn.momentum.core.ui.theme.MoPropSky
import de.nicolasmahn.momentum.core.ui.theme.MoPropWhite
import de.nicolasmahn.momentum.core.ui.theme.MoPropYellow
import de.nicolasmahn.momentum.domain.mo.MoIntensity
import de.nicolasmahn.momentum.domain.mo.MoOccasion
import de.nicolasmahn.momentum.feature.mo.CENTRE
import de.nicolasmahn.momentum.feature.mo.FEET
import de.nicolasmahn.momentum.feature.mo.MoArm
import de.nicolasmahn.momentum.feature.mo.MoBodyKind
import de.nicolasmahn.momentum.feature.mo.MoBrowKind
import de.nicolasmahn.momentum.feature.mo.MoBrows
import de.nicolasmahn.momentum.feature.mo.MoEye
import de.nicolasmahn.momentum.feature.mo.MoEyeShape
import de.nicolasmahn.momentum.feature.mo.MoMouth
import de.nicolasmahn.momentum.feature.mo.MoPose
import de.nicolasmahn.momentum.feature.mo.MoRig
import de.nicolasmahn.momentum.feature.mo.curve
import de.nicolasmahn.momentum.feature.mo.cycle
import de.nicolasmahn.momentum.feature.mo.ease
import de.nicolasmahn.momentum.feature.mo.floorShadow
import de.nicolasmahn.momentum.feature.mo.limb
import de.nicolasmahn.momentum.feature.mo.moIdle
import de.nicolasmahn.momentum.feature.mo.oval
import de.nicolasmahn.momentum.feature.mo.pulse
import de.nicolasmahn.momentum.feature.mo.quadTo
import de.nicolasmahn.momentum.feature.mo.shape
import de.nicolasmahn.momentum.feature.mo.wave
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/*
 * A task with the book motif is open, third helping: a pet rock, a yo-yo he cannot work, a spider that has moved in,
 * and a reminder bow whose reason is long gone.
 */

private fun radians(degrees: Float) = degrees * PI.toFloat() / 180f

/** A task with the book motif is open: he walks a pet rock on a lead, and a gentle tug makes it do its one trick. */
private object WalkingTheRock : MoAct("dumb_walking_the_rock", MoOccasion.Dumb, intensity = MoIntensity.Mild) {
    private const val ROCK_OUT = 114f
    private const val COLLAR_UP = 31f
    private const val LEDGE = 6f

    /** The top of the collar at the middle of the stone, where the lead clips on. */
    private const val CLIP_UP = COLLAR_UP - 2f

    // Round, flat and lumpy, as corners relative to the spot it sits on; pebble() rounds them off.
    private val outlines = listOf(
        listOf(-38f to 0f, -42f to -24f, -28f to -46f, -4f to -52f, 22f to -47f, 40f to -26f, 40f to 0f),
        listOf(-40f to 0f, -44f to -18f, -28f to -35f, 4f to -40f, 30f to -32f, 43f to -14f, 41f to 0f),
        listOf(-38f to 0f, -42f to -22f, -32f to -38f, -15f to -33f, -5f to -52f, 17f to -54f, 25f to -37f, 37f to -33f, 42f to -13f, 40f to 0f),
    )

    /** A closed path that cuts every corner of [corners], so any polygon becomes a smooth stone. */
    private fun pebble(corners: List<Offset>): Path = Path().apply {
        var from = (corners.last() + corners.first()) / 2f
        moveTo(from.x, from.y)
        corners.forEachIndexed { index, corner ->
            val next = (corner + corners[(index + 1) % corners.size]) / 2f
            quadTo(this, from, corner, next)
            from = next
        }
        close()
    }

    private fun side(v: MoVariant) = if (v.chance(50, salt = 1)) 1f else -1f

    /** Seconds into the loop. It starts a quarter second in, so the first frame is mid-tug. */
    private fun seconds(t: Float, v: MoVariant): Float {
        val period = v.between(4.0f, 5.4f, salt = 5)
        return cycle(t, period, offset = 0.25f / period) * period
    }

    private fun tug(s: Float) = if (s < 0.5f) sin(s / 0.5f * PI.toFloat()) else 0f

    /** The rock tips toward him a beat after the tug, rocks back, and settles. */
    private fun rock(s: Float): Float {
        val u = (s - 0.15f) / 1.1f
        return if (u in 0f..1f) sin(u * 3f * PI.toFloat()) * (1f - u) * (1f - u) else 0f
    }

    private fun proud(s: Float) = ease((s - 0.45f) / 0.2f) * (1f - ease((s - 1.9f) / 0.4f))

    private fun squash(t: Float, v: MoVariant) = moIdle(t, energy = 0.8f).squash + 0.02f * proud(seconds(t, v))

    /** Where the lead clips onto the collar, in the world, following the rock as it tips. */
    private fun clip(t: Float, v: MoVariant): Offset {
        val lean = radians(-side(v) * 8f * rock(seconds(t, v)))
        return Offset(CENTRE + side(v) * ROCK_OUT + CLIP_UP * sin(lean), FEET - CLIP_UP * cos(lean))
    }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val s = seconds(t, v)
        // Reaching this far out clears his flame, so the mitten the lead hangs from is actually seen.
        val lead = MoArm(Offset(28f, -36f - 12f * tug(s)), bend = 0.5f)
        val smitten = proud(s) >= 0.5f
        val eye = if (smitten) MoEye(shape = MoEyeShape.Heart) else MoEye(gaze = Offset(side(v) * 0.9f, 0.7f), lidDrop = 0.15f)
        // Tilt, offset and shake stay 0: the lead's far end is tied to a rock that does not move with him.
        return moIdle(t, energy = 0.8f).copy(
            leftEye = eye,
            rightEye = eye,
            mouth = if (smitten) MoMouth.Beam else MoMouth.Smile(),
            leftArm = if (side(v) < 0f) lead else MoArm.Down,
            rightArm = if (side(v) > 0f) lead else MoArm.Down,
            squash = squash(t, v),
            blinks = !smitten,
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val x = CENTRE + side(v) * ROCK_OUT
        val collar = v.of(listOf(MoPropRed, MoPropSky, MoPropPink, MoPropGreen), salt = 2)
        val outline = outlines[v.pick(outlines.size, salt = 3)]
        floorShadow(x, 44f, MoPropShadow)
        withTransform({ rotate(-side(v) * 8f * rock(seconds(t, v)), Offset(x, FEET)) }) {
            drawPath(pebble(outline.map { (dx, dy) -> Offset(x + dx, FEET + dy) }), MoPropGreyDark)
            val face = pebble(outline.map { (dx, dy) -> Offset(x + dx, FEET + dy - LEDGE) })
            drawPath(face, MoPropGrey)
            // Clipped by the stone, the collar fits whichever outline the day picked.
            clipPath(face) {
                curve(Offset(x - 60f, FEET - COLLAR_UP - 3f), Offset(x, FEET - COLLAR_UP + 8f), Offset(x + 60f, FEET - COLLAR_UP - 3f), collar, 9f)
                curve(Offset(x - 26f, FEET - COLLAR_UP - 9f), Offset(x - 22f, FEET - COLLAR_UP - 17f), Offset(x - 13f, FEET - COLLAR_UP - 19f), MoPropSilver, 5f)
                // Two pits on the side away from him and from the tag: they are what says stone.
                oval(Offset(x + side(v) * 20f, FEET - 19f), 4f, 3f, MoPropGreyDark)
                oval(Offset(x + side(v) * 30f, FEET - 13f), 2.5f, 2f, MoPropGreyDark)
            }
            if (v.chance(40, salt = 4)) {
                val tag = Offset(x - side(v) * 12f, FEET - COLLAR_UP + 13f)
                drawCircle(MoPropGoldDark, 5.5f, tag + Offset(0f, 1.5f))
                drawCircle(MoPropGold, 5.5f, tag)
            }
        }
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val colour = v.of(listOf(MoPropRed, MoPropSky, MoPropPink, MoPropGreen), salt = 2)
        val hand = if (side(v) > 0f) rig.rightHand else rig.leftHand
        // He breathes from his feet; undoing that here keeps the far end of the lead on the rock.
        val breath = squash(t, v)
        val world = clip(t, v)
        val end = Offset(CENTRE + (world.x - CENTRE) / (1f - breath / 2f), FEET + (world.y - FEET) / (1f + breath))
        val slack = 1f - tug(seconds(t, v))
        val sag = Offset(side(v) * (20f + 3f * wave(t, 1.7f)) * slack, (16f + 3f * wave(t + 0.4f, 1.7f)) * slack)
        curve(hand, (hand + end) / 2f + sag, end, colour, 4.5f)
        // Small enough to sit inside the mitten, which is what makes the lead read as held.
        drawCircle(colour, 6f, hand, style = Stroke(3f))
        drawCircle(MoPropSilver, 4.5f, end)
    }

    override val ownsTheFloor get() = true
}

/** A task with the book motif is open: he cannot make the yo-yo come back up, and watching it swing has put him under. */
private object YoYoSpell : MoAct("dumb_yo_yo_spell", MoOccasion.Dumb, intensity = MoIntensity.Strong) {
    private const val SWING = 2.4f

    private fun side(v: MoVariant) = if (v.chance(50, salt = 1)) 1f else -1f

    // His lean trails the yo-yo and his tip trails his lean, so the swing seems to pull him along.
    private fun lean(t: Float) = 3.5f * wave(t - 0.3f, SWING)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        // Reaching this far out clears his flame, so the yo-yo hangs from a mitten and not from his side.
        val holding = MoArm(Offset(28f, -48f), bend = 0.4f)
        return moIdle(t, energy = 0.5f).copy(
            leftEye = MoEye(shape = MoEyeShape.Spiral),
            rightEye = MoEye(shape = MoEyeShape.Spiral),
            mouth = if (v.chance(65, salt = 4)) MoMouth.Drool else MoMouth.Oh(6f),
            leftArm = if (side(v) < 0f) holding else MoArm.Down,
            rightArm = if (side(v) > 0f) holding else MoArm.Down,
            sway = 7f * wave(t - 0.6f, SWING),
            tilt = lean(t),
            blinks = false,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val (rim, hub) = v.of(
            listOf(MoPropRed to MoPropYellow, MoPropBlue to MoPropSky, MoPropPurple to MoPropPink, MoPropGreen to MoPropMint),
            salt = 2,
        )
        val hand = if (side(v) > 0f) rig.rightHand else rig.leftHand
        val length = v.between(66f, 78f, salt = 3)
        // This is drawn leaning with him, so his lean comes off the angle and the string hangs true.
        // Swing, string and rim are sized together: at full swing on the longest string, with his
        // lean added, the rim stops about five units inside the edge of the view on either side.
        val angle = radians(12f * wave(t, SWING) - lean(t))
        val along = Offset(sin(angle), cos(angle))
        val disc = hand + along * length
        limb(hand, disc - along * 18f, MoPropCream, 3.5f)
        // Small enough to sit inside the mitten, so the string reads as looped round his hand.
        drawCircle(MoPropCream, 5f, hand, style = Stroke(2.5f))
        drawCircle(rim, 19f, disc)
        drawCircle(hub, 11.5f, disc)
        drawCircle(MoPropWhite, 4.5f, disc)
    }
}

/** A task with the book motif is open for so long that a spider lives in his tip, and he goes cross-eyed watching it bob. */
private object LodgerInHisTip : MoAct("dumb_lodger_in_his_tip", MoOccasion.Dumb, intensity = MoIntensity.Strong) {
    private fun tempo(v: MoVariant) = v.between(2.6f, 3.6f, salt = 3)

    private fun lingers(v: MoVariant) = v.chance(30, salt = 6)

    /** How far down its thread the spider is, from 0 to 1. Some days it lingers at the bottom. */
    private fun bob(t: Float, v: MoVariant): Float {
        if (!lingers(v)) return pulse(t + 0.8f, tempo(v))
        val p = cycle(t + 0.8f, tempo(v))
        return ease(p / 0.36f) * (1f - ease((p - 0.62f) / 0.38f))
    }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose = moIdle(t, energy = 0.5f).copy(
        // Each eye follows a little late, and not equally late, so they are never quite in step.
        leftEye = MoEye(gaze = Offset(0.95f, -0.9f + 1.3f * bob(t - 0.15f, v)), iris = 0.6f),
        rightEye = MoEye(gaze = Offset(-0.95f, -0.9f + 1.3f * bob(t - 0.4f, v)), iris = 0.6f),
        brows = MoBrows(MoBrowKind.Raised, lift = 4f),
        mouth = MoMouth.Oh(7f),
        tilt = 1.5f * wave(t, tempo(v)),
    )

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        web(t, v, rig)

        val colour = v.of(listOf(MoPropInk, MoPropPurpleDark, MoPropNavy), salt = 1)
        val down = bob(t, v)
        val depth = v.between(22f, 32f, salt = 2)
        val sp = Offset((rig.leftEye.x + rig.rightEye.x) / 2f + (rig.tip.x - 128f) * 0.3f, rig.leftEye.y - 34f + depth * down)
        limb(rig.tip, sp + Offset(0f, -7f), MoPropSilver, 2f)

        // The legs draw in while it climbs and spread while it lets itself down.
        val curl = ((bob(t - 0.1f, v) - down) * 10f).coerceIn(-1f, 1f)
        val p = cycle(t + 0.8f, tempo(v))
        val hello = if (lingers(v)) ease((p - 0.4f) / 0.05f) * (1f - ease((p - 0.56f) / 0.05f)) else 0f
        for (legSide in listOf(-1f, 1f)) {
            for (index in 0..3) {
                val waving = if (legSide > 0f && index == 0) hello else 0f
                val out = radians(-48f + index * 30f - 40f * waving - 14f * waving * wave(t, 0.3f))
                val knee = sp + Offset(legSide * cos(out) * 14f, sin(out) * 12f - 2f)
                val foot = knee + Offset(legSide * (5f - 3f * curl), (8f - 5f * curl) * (1f - waving) - 7f * waving)
                limb(sp, knee, colour, 2.6f)
                limb(knee, foot, colour, 2.6f)
            }
        }
        oval(sp, 10f, 9f, colour)
        drawCircle(MoPropWhite, 2.6f, sp + Offset(-3.8f, -1f))
        drawCircle(MoPropWhite, 2.6f, sp + Offset(3.8f, -1f))
    }

    /**
     * A small web spun across the crook beside his tip, between the two flame walls that make it.
     * The points are read off the outlines, and the low body only has a crook under its curl.
     */
    private fun DrawScope.web(t: Float, v: MoVariant, rig: MoRig) {
        if (!v.chance(60, salt = 4)) return
        val (crook, wall, tongue) = when {
            rig.body.kind != MoBodyKind.Tall -> Triple(Offset(148f, 180f), Offset(165f, 171f), Offset(170f, 212f))
            v.chance(50, salt = 5) -> Triple(Offset(161f, 170f), Offset(141f, 138f), Offset(182f, 149f))
            else -> Triple(Offset(56f, 186f), Offset(70f, 140f), Offset(40f, 162f))
        }
        // The web rides on the flame, so it leans with it exactly as the outline under it does.
        val sway = moIdle(t, energy = 0.5f).sway
        val reach = (rig.body.pivot - rig.body.top).coerceAtLeast(1f)
        val onFlame: (Offset) -> Offset = { point ->
            val height = ((rig.body.pivot - point.y) / reach).coerceIn(0f, 1.2f)
            Offset(point.x + sway * height * height, point.y)
        }
        val silk = MoPropSilver.copy(alpha = 0.9f)
        val ends = (0..3).map { index ->
            val across = wall + (tongue - wall) * (index / 3f)
            // The outer thread sags toward the crook between the two walls it hangs from.
            if (index in 1..2) across + (crook - across) * 0.14f else across
        }
        ends.forEach { limb(onFlame(crook), onFlame(it), silk, 1.6f) }
        for (ring in floatArrayOf(0.5f, 1f)) {
            ends.zipWithNext { from, to ->
                val a = crook + (from - crook) * ring
                val b = crook + (to - crook) * ring
                curve(a, crook + ((a + b) / 2f - crook) * 0.82f, b, silk, 1.6f, onFlame)
            }
        }
    }
}

/** A task with the book motif is open: he taps his chin over the reminder bow on his mitten, then asks us what it was for. */
private object TheBowHeTied : MoAct("dumb_the_bow_he_tied", MoOccasion.Dumb, intensity = MoIntensity.Mild) {
    private fun side(v: MoVariant) = if (v.chance(50, salt = 2)) 1f else -1f

    private fun asking(t: Float, v: MoVariant): Float {
        val p = cycle(t, 5.6f, offset = 0.04f)
        return ease((p - 0.6f) / 0.06f) * (1f - ease((p - v.between(0.82f, 0.94f, salt = 4)) / 0.06f))
    }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val p = cycle(t, 5.6f, offset = 0.04f)
        val ask = asking(t, v)
        val taps = 2 + v.pick(3, salt = 3)
        val tap = if (p < 0.6f) abs(sin(p / 0.6f * taps * PI.toFloat())) else 0f
        val bowArm = MoArm(Offset(30f, -56f - 5f * ask), bend = 0.6f)
        // On the belly flame beside his mouth, where an arm in his own colour still shows.
        val chinArm = MoArm(Offset(-56f + 3f * tap, 13f - 7f * tap), bend = 0.6f)
        val eye = MoEye(
            gaze = Offset(side(v) * 0.9f, -0.25f) * (1f - ask),
            scale = 1f + 0.06f * ask,
            iris = 0.7f + 0.1f * ask,
        )
        return moIdle(t, energy = 0.8f).copy(
            leftEye = eye,
            rightEye = eye,
            brows = MoBrows(MoBrowKind.Worried, lift = 5f * ask, skew = 6f * (1f - ask)),
            mouth = if (ask < 0.5f) MoMouth.Pout else MoMouth.Oh(5f),
            leftArm = if (side(v) < 0f) bowArm else chinArm,
            rightArm = if (side(v) > 0f) bowArm else chinArm,
            armsInFront = true,
            tilt = side(v) * 3f * (1f - ask),
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val (ribbon, knot) = v.of(
            listOf(MoPropRed to MoPropRedDark, MoPropPink to MoPropPinkDark, MoPropBlue to MoPropBlueDark, MoPropPurple to MoPropPurpleDark),
            salt = 1,
        )
        val hand = if (side(v) > 0f) rig.rightHand else rig.leftHand
        // On the wrist, just under the mitten: below his tip and beside his face, hiding neither.
        val k = hand + Offset(side(v) * 1f, 16f)
        limb(k, k + Offset(-8f, 19f), ribbon, 6f)
        limb(k, k + Offset(8f, 19f), ribbon, 6f)
        for (loop in listOf(-1f, 1f)) {
            shape(ribbon, k.x to k.y, k.x + loop * 20f to k.y - 11f, k.x + loop * 22f to k.y + 8f)
        }
        drawCircle(knot, 6f, k)
    }
}

internal val dumbActs3: List<MoAct> = listOf(
    WalkingTheRock,
    YoYoSpell,
    LodgerInHisTip,
    TheBowHeTied,
)
