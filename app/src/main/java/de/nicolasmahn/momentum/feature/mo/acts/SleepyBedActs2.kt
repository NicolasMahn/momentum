package de.nicolasmahn.momentum.feature.mo.acts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import de.nicolasmahn.momentum.core.ui.theme.MoPropBlue
import de.nicolasmahn.momentum.core.ui.theme.MoPropBlueDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropBrown
import de.nicolasmahn.momentum.core.ui.theme.MoPropCharcoal
import de.nicolasmahn.momentum.core.ui.theme.MoPropCream
import de.nicolasmahn.momentum.core.ui.theme.MoPropGreen
import de.nicolasmahn.momentum.core.ui.theme.MoPropGrey
import de.nicolasmahn.momentum.core.ui.theme.MoPropGreyDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropInk
import de.nicolasmahn.momentum.core.ui.theme.MoPropMint
import de.nicolasmahn.momentum.core.ui.theme.MoPropMoon
import de.nicolasmahn.momentum.core.ui.theme.MoPropPink
import de.nicolasmahn.momentum.core.ui.theme.MoPropPinkDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropPurple
import de.nicolasmahn.momentum.core.ui.theme.MoPropPurpleDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropRed
import de.nicolasmahn.momentum.core.ui.theme.MoPropSand
import de.nicolasmahn.momentum.core.ui.theme.MoPropShadow
import de.nicolasmahn.momentum.core.ui.theme.MoPropSilver
import de.nicolasmahn.momentum.core.ui.theme.MoPropSky
import de.nicolasmahn.momentum.core.ui.theme.MoPropTeal
import de.nicolasmahn.momentum.core.ui.theme.MoPropTealDark
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
import de.nicolasmahn.momentum.feature.mo.MoPose
import de.nicolasmahn.momentum.feature.mo.MoRig
import de.nicolasmahn.momentum.feature.mo.block
import de.nicolasmahn.momentum.feature.mo.chunk
import de.nicolasmahn.momentum.feature.mo.curve
import de.nicolasmahn.momentum.feature.mo.cycle
import de.nicolasmahn.momentum.feature.mo.ease
import de.nicolasmahn.momentum.feature.mo.floorShadow
import de.nicolasmahn.momentum.feature.mo.grippingMitten
import de.nicolasmahn.momentum.feature.mo.limb
import de.nicolasmahn.momentum.feature.mo.mitten
import de.nicolasmahn.momentum.feature.mo.moIdle
import de.nicolasmahn.momentum.feature.mo.oval
import de.nicolasmahn.momentum.feature.mo.puff
import de.nicolasmahn.momentum.feature.mo.pulse
import de.nicolasmahn.momentum.feature.mo.shape
import de.nicolasmahn.momentum.feature.mo.sparkle
import de.nicolasmahn.momentum.feature.mo.twitch
import de.nicolasmahn.momentum.feature.mo.wave
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * Bedtime, second helping: one act asleep in the shared bed, and four on the way there, when the
 * last small things of the day get done half asleep.
 */

/** Eased progress of [p] through the window from [from] to [to]: 0 before it, 1 after it. */
private fun ramp(p: Float, from: Float, to: Float): Float = ease((p - from) / max(to - from, 0.001f))

private fun mix(a: Float, b: Float, k: Float): Float = a + (b - a) * k

private fun mix(a: Offset, b: Offset, k: Float): Offset = a + (b - a) * k

private fun mix(a: MoArm, b: MoArm, k: Float): MoArm = MoArm(mix(a.reach, b.reach, k), mix(a.bend, b.bend, k))

private fun MoVariant.side(salt: Int): Float = if (chance(50, salt)) 1f else -1f

private val shutEye = MoEye(shape = MoEyeShape.Shut)

/** Asleep in bed with a smile, while the cloud over him dreams tomorrow: a toothbrush, a book and a dumbbell, each in turn. */
private object SleepyDreamOfTomorrow : MoAct("sleepy_bed_dream_of_tomorrow", MoOccasion.Sleepy, intensity = MoIntensity.Strong) {
    private const val PERIOD = 10.5f
    private const val PICTURES = 3

    private fun cloudSide(v: MoVariant) = v.side(salt = 1)

    /** Where picture [index] is in its turn: 0 in the middle of it, further from 0 the longer until or since. */
    private fun turn(t: Float, index: Int): Float =
        ((cycle(t, PERIOD) - index.toFloat() / PICTURES + 0.5f).mod(1f) - 0.5f) * PICTURES

    /** How much of picture [index] shows: all of it in the middle of its turn, half where it crosses the next. */
    private fun showing(t: Float, index: Int): Float = 1f - ramp(abs(turn(t, index)), 0.32f, 0.68f)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val brush = showing(t, 0)
        val book = showing(t, 1)
        val lift = showing(t, 2)
        val bed = moInBed(t, body, tilt = cloudSide(v) * 3f)
        // The dumbbell is the one dream he joins in with: both mittens draw in for a lift, without leaving the blanket.
        fun curl(arm: MoArm) = arm.copy(reach = arm.reach + Offset(-9f * lift, 0f), bend = arm.bend + 0.25f * lift)
        return bed.copy(
            leftEye = shutEye,
            rightEye = shutEye,
            brows = if (book > 0.5f) MoBrows(MoBrowKind.Raised, lift = 2f) else MoBrows(),
            mouth = MoMouth.Smile(width = 9f + 5f * brush + 2f * lift, depth = 6f + 3f * brush + 2f * lift),
            leftArm = curl(bed.leftArm),
            rightArm = curl(bed.rightArm),
            blinks = false,
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val side = cloudSide(v)
        val moon = Offset(CENTRE - side * 118f, 46f)
        drawCircle(MoPropMoon, 20f, moon)
        drawCircle(MoPropGrey.copy(alpha = 0.35f), 5f, moon + Offset(-6f, -5f))
        sparkle(Offset(CENTRE - side * 62f, 28f), 5f, 1f + 0.3f * pulse(t + 0.7f, 3.1f), MoPropMoon)

        // As big as fits between the edge of the view and his tip, above the headboard.
        val cloud = Offset(CENTRE + side * 98f, 60f + 3f * wave(t, 4.3f))
        puff(cloud, 50f, MoPropSilver)
        val at = cloud + Offset(0f, 4f)
        for (index in 0 until PICTURES) {
            val showing = showing(t, index)
            if (showing <= 0.01f) continue
            val scale = 1.6f * (0.7f + 0.3f * showing)
            withTransform({ scale(scale, scale, at) }) {
                when (index) {
                    0 -> toothbrush(at + Offset(10f, 3f), 1f, v, salt = 2, angle = -12f, size = 0.60f, alpha = showing)
                    1 -> book(at, v, showing)
                    else -> dumbbell(at, showing)
                }
            }
        }
        bedBehind()
    }

    private fun DrawScope.book(at: Offset, v: MoVariant, alpha: Float) {
        val cover = v.of(listOf(MoPropRed, MoPropBlue, MoPropGreen), salt = 3).copy(alpha = alpha)
        block(at + Offset(0f, 1f), 40f, 28f, cover, corner = 3f)
        for (page in listOf(-9f, 9f)) block(at + Offset(page, -1f), 15f, 22f, MoPropCream.copy(alpha = alpha), corner = 2f)
    }

    private fun DrawScope.dumbbell(at: Offset, alpha: Float) {
        limb(at + Offset(-22f, 0f), at + Offset(22f, 0f), MoPropGrey.copy(alpha = alpha), 6f)
        for (plate in listOf(-16f, 16f)) {
            chunk(at + Offset(plate, 0f), 11f, 26f, MoPropCharcoal.copy(alpha = alpha), MoPropInk.copy(alpha = alpha), corner = 3f, drop = 3f)
        }
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val side = cloudSide(v)
        // moInBed lifts the low body, so a point of the world sits that much lower in his own frame.
        val lift = MoBodies.Tall.mouth.y - rig.body.mouth.y
        val from = Offset(CENTRE + side * 62f, rig.tip.y + 36f)
        val to = Offset(CENTRE + side * 96f, 112f - lift)
        listOf(0.15f to 5f, 0.5f to 8f, 0.85f to 11f).forEachIndexed { index, (along, radius) ->
            // Each bubble swells a beat after the one below it, so the dream seems to travel up.
            drawCircle(MoPropSilver, radius * (0.8f + 0.3f * pulse(t - index * 0.8f, 2.4f)), mix(from, to, along))
        }
    }

    override fun DrawScope.foreground(t: Float, v: MoVariant) = bedBlanket(t, v)

    override val ownsTheFloor get() = true
    override val sparkles get() = false
}

/** Brushing his teeth half asleep: slow strokes under heavy lids, until a yawn takes the brush out; he blinks awake and brushes on. */
private object SleepyToothbrushLastThing : MoAct("sleepy_toothbrush_last_thing", MoOccasion.Sleepy) {
    private const val PERIOD = 7.5f

    private fun side(v: MoVariant) = v.side(salt = 1)

    private fun yawn(p: Float) = ramp(p, 0.42f, 0.50f) * (1f - ramp(p, 0.66f, 0.74f))

    private fun awake(p: Float) = ramp(p, 0.72f, 0.75f) * (1f - ramp(p, 0.86f, 1f))

    /** How far the brush is along its stroke: the yawn stops it, and the blink awake makes it brisker for a while. */
    private fun stroke(t: Float, p: Float) = 5f * wave(t, 1.3f) * (1f - yawn(p)) * (1f + 0.6f * awake(p))

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val s = side(v)
        val p = cycle(t, PERIOD)
        val yawn = yawn(p)
        val awake = awake(p)
        val shape = MoBodies.of(body)
        // The brush hand sits out on his cheek, under the eye and level with the mouth, on either body.
        val brushing = MoArm(Offset(-(shape.shoulderHalf - 52f) + stroke(t, p), shape.mouth.y + 13f - shape.shoulderY), bend = 0.55f)
        val dropped = MoArm(Offset(24f, 8f), bend = 0.25f)
        val stretch = MoArm(Offset(40f, -74f), bend = 0.25f)
        val lid = mix(0.72f + 0.06f * wave(t, 2.3f), 0.12f, awake)
        val eye = if (yawn > 0.55f) shutEye else MoEye(lidDrop = lid, gaze = Offset(s * 0.35f, 0.5f * (1f - awake)), iris = mix(0.76f, 0.62f, awake))
        val brushArm = mix(brushing, dropped, yawn).copy(warm = 1f - yawn)
        val freeArm = mix(MoArm.Down, stretch, yawn)
        val idle = moIdle(t, energy = 0.45f)
        return idle.copy(
            leftEye = eye,
            rightEye = eye,
            brows = if (awake > 0.5f) MoBrows(MoBrowKind.Raised, lift = 3f) else MoBrows(),
            // His teeth stay visible above the brush until he lowers it to yawn.
            mouth = if (yawn > 0.04f) MoMouth.Yawn(open = yawn) else MoMouth.Beam,
            leftArm = if (s < 0f) brushArm else freeArm,
            rightArm = if (s < 0f) freeArm else brushArm,
            armsInFront = true,
            flare = 14f * yawn,
            squash = idle.squash + 0.04f * yawn,
            tilt = s * (1.5f - 2.5f * yawn),
            blinks = yawn < 0.5f,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val s = side(v)
        val p = cycle(t, PERIOD)
        val yawn = yawn(p)
        val hand = if (s < 0f) rig.leftHand else rig.rightHand
        toothbrush(hand, s, v, salt = 2, angle = -3f - 48f * yawn)
        grippingMitten(rig, s, warm = 1f - yawn)
    }

    override val sparkles get() = false
}

/** Trudging off to bed, dragging a fat pillow along behind him by its edge: it bumps along, and he yawns. */
private object SleepyDraggingThePillow : MoAct("sleepy_dragging_the_pillow", MoOccasion.Sleepy) {
    private const val STEP = 1.6f
    private const val PERIOD = 4f * STEP

    /** Where the dragging hand hangs on every body, so the world can lay the pillow's edge in it. */
    private const val HAND_Y = 370f

    // The arm hangs almost straight: any further out and the pillow beside it would leave the view.
    private const val HAND_OUT = 4f

    private fun dir(v: MoVariant) = v.side(salt = 1)

    // Shifted so the still frame already has a foot in the air.
    private fun step(t: Float) = wave(t + 0.4f, STEP)

    private fun yawn(p: Float) = ramp(p, 0.55f, 0.63f) * (1f - ramp(p, 0.76f, 0.84f))

    /** The trudge slows while he yawns, and so does everything that rides on it. */
    private fun bob(t: Float, p: Float) = -2f * abs(step(t)) * (1f - 0.7f * yawn(p))

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val dir = dir(v)
        val p = cycle(t, PERIOD)
        val yawn = yawn(p)
        val step = step(t) * (1f - 0.7f * yawn)
        val shape = MoBodies.of(body)
        val eye = if (yawn > 0.55f) shutEye else MoEye(lidDrop = 0.66f + 0.08f * wave(t, 2.7f), gaze = Offset(dir * 0.3f, 0.55f))
        val dragging = MoArm(Offset(HAND_OUT, HAND_Y - shape.shoulderY), bend = 0.1f)
        val hanging = MoArm(Offset(2f + 3f * step, 30f), bend = 0.15f)
        // A mitten comes up beside the yawn, politely missing it.
        val toMouth = MoArm(Offset(-(shape.shoulderHalf - 34f), shape.mouth.y + 10f - shape.shoulderY), bend = 0.5f)
        val leading = mix(hanging, toMouth, yawn).copy(warm = yawn)
        val idle = moIdle(t, energy = 0.4f)
        return idle.copy(
            leftEye = eye,
            rightEye = eye,
            mouth = if (yawn > 0.04f) MoMouth.Yawn(open = yawn) else MoMouth.Flat(9f),
            leftArm = if (dir > 0f) dragging else leading,
            rightArm = if (dir > 0f) leading else dragging,
            armsInFront = true,
            legs = MoLegs(MoLeg(lift = 7f * max(step, 0f)), MoLeg(lift = 7f * max(-step, 0f))),
            // The tip trails behind the walk and hangs; only the yawn lifts it.
            sway = idle.sway - dir * 12f,
            flare = -16f + 10f * yawn,
            squash = idle.squash - 0.015f + 0.04f * yawn,
            // No tilt: the pillow is drawn in the world, where it has to meet a mitten it cannot see.
            offset = Offset(0f, bob(t, p)),
            blinks = yawn < 0.5f,
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val out = -dir(v)
        val hand = Offset(CENTRE + out * (MoBodies.Tall.shoulderHalf + HAND_OUT), HAND_Y)
        val bump = twitch(t + 0.4f, STEP, beat = 0.45f)
        val (cloth, clothDark) = v.of(
            listOf(MoPropCream to MoPropSand, MoPropSky to MoPropBlueDark, MoPropPink to MoPropPinkDark, MoPropMint to MoPropTealDark),
            salt = 2,
        )
        // The pillow lies on the floor behind him: its inner end is under his base, where his body
        // hides it, and the rest trails out beside him with his mitten gripping its top edge. The
        // outer end lifts a little with every step, which is the bump.
        val heel = Offset(hand.x - out * 66f, FEET - 3f)
        floorShadow(hand.x + out * 4f, 44f, MoPropShadow)
        withTransform({
            translate(heel.x, heel.y)
            scale(out, 1f, Offset.Zero)
            rotate(-(4f + 4f * bump), Offset.Zero)
        }) {
            chunk(Offset(50f, -22f), 100f, 44f, cloth, clothDark, corner = 18f, drop = 5f)
            when (v.pick(3, salt = 3)) {
                0 -> block(Offset(72f, -24f), 10f, 30f, clothDark.copy(alpha = 0.55f), corner = 5f)
                1 -> for (dot in listOf(Offset(24f, -32f), Offset(50f, -16f), Offset(78f, -32f))) drawCircle(clothDark.copy(alpha = 0.55f), 4.5f, dot)
                else -> Unit
            }
        }
    }

    override fun DrawScope.foreground(t: Float, v: MoVariant) {
        val dir = dir(v)
        // The ground sliding past is what says he is walking rather than standing.
        for (index in 0..5) {
            // Each dash fades in at one edge of the view and out at the other, so none ever pops.
            val along = cycle(t, 2f * STEP, offset = index / 6f)
            val x = if (dir > 0f) 240f - 280f * along else -40f + 280f * along
            val alpha = 0.8f * ramp(along, 0f, 0.12f) * (1f - ramp(along, 0.88f, 1f))
            limb(Offset(x, 428f), Offset(x + 30f, 428f), MoPropGreyDark.copy(alpha = alpha), 5.5f)
        }
    }

    override val ownsTheFloor get() = true
}

/** A blanket round his shoulders like a cape, held shut under his chin: he shuffles, rests, and hums to himself. */
private object SleepyBlanketCape : MoAct("sleepy_blanket_cape", MoOccasion.Sleepy, intensity = MoIntensity.Mild) {
    private const val STEP = 1.5f
    private const val PERIOD = 9f

    /** The hem, high enough that his feet show under it while he shuffles. */
    private const val HEM = FEET - 18f

    // 0.1 into the loop he is mid-shuffle, which is the picture that explains it.
    private fun progress(t: Float) = cycle(t, PERIOD, offset = 0.1f)

    /** 1 while he shuffles, 0 while he stands still and hums in the middle of the loop. */
    private fun shuffling(p: Float) = 1f - ramp(p, 0.42f, 0.50f) * (1f - ramp(p, 0.68f, 0.76f))

    private fun humSide(v: MoVariant) = v.side(salt = 3)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val p = progress(t)
        val shuffling = shuffling(p)
        val rest = 1f - shuffling
        val step = wave(t, STEP) * shuffling
        val shape = MoBodies.of(body)
        // Both mittens meet under his chin and draw the cape a little closer while he rests.
        val hold = MoArm(Offset(-(shape.shoulderHalf - 17f) + 3f * rest, shape.mouth.y + 22f - shape.shoulderY), bend = 0.4f)
        val eye = MoEye(lidDrop = 0.55f + 0.25f * rest + 0.05f * wave(t, 3.1f), gaze = Offset(0f, 0.5f))
        val idle = moIdle(t, energy = 0.45f)
        return idle.copy(
            leftEye = eye,
            rightEye = eye,
            mouth = MoMouth.Smile(width = 10f + 3f * rest, depth = 7f + 2f * rest),
            leftArm = hold,
            rightArm = hold,
            armsInFront = true,
            legs = MoLegs(MoLeg(lift = 3f * max(step, 0f), swing = 2f * max(step, 0f)), MoLeg(lift = 3f * max(-step, 0f), swing = 2f * max(-step, 0f))),
            offset = Offset(3f * wave(t, 2f * STEP) * shuffling, -1.5f * abs(step)),
            tilt = 2f * wave(t, 2f * STEP) * shuffling,
            squash = idle.squash + 0.02f * rest,
            flare = -6f * rest,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val p = progress(t)
        val rest = 1f - shuffling(p)
        val (cloth, clothDark) = v.of(
            listOf(MoPropBlue to MoPropBlueDark, MoPropPurple to MoPropPurpleDark, MoPropTeal to MoPropTealDark, MoPropPink to MoPropPinkDark),
            salt = 1,
        )
        val top = rig.body.shoulderY - 6f
        val collar = rig.body.mouth.y + 18f
        // The hem trails the steps by a beat.
        val swing = 5f * wave(t + 0.25f, STEP) * shuffling(p)
        val squeeze = 4f * rest
        fun outline(dy: Float): Array<Pair<Float, Float>> = arrayOf(
            CENTRE - 108f to top + dy,
            CENTRE - 30f + squeeze to collar + dy,
            CENTRE + 30f - squeeze to collar + dy,
            CENTRE + 108f to top + dy,
            CENTRE + 122f + swing to HEM + dy,
            CENTRE + 81f + swing to HEM - 7f + dy,
            CENTRE + 41f + swing to HEM + dy,
            CENTRE + swing to HEM - 7f + dy,
            CENTRE - 41f + swing to HEM + dy,
            CENTRE - 81f + swing to HEM - 7f + dy,
            CENTRE - 122f + swing to HEM + dy,
        )
        shape(clothDark, *outline(5f))
        shape(cloth, *outline(0f))
        when (v.pick(3, salt = 2)) {
            0 -> {
                val middle = (collar + HEM) / 2f
                for (row in listOf(-1f, 1f)) for (column in -2..2) {
                    drawCircle(MoPropCream, 5f, Offset(CENTRE + column * 40f + row * 10f + swing * 0.5f, middle + row * 14f))
                }
            }
            1 -> for (column in listOf(-64f, 64f)) block(Offset(CENTRE + column + swing * 0.5f, (collar + HEM) / 2f + 4f), 10f, HEM - collar - 24f, clothDark.copy(alpha = 0.5f), corner = 5f)
            else -> Unit
        }
        mitten(rig, rig.leftHand, -1f)
        mitten(rig, rig.rightHand, 1f)

        // A hummed note floats up beside his cheek while he stands still, clear of the eye.
        val side = humSide(v)
        val rise = (p - 0.47f) / 0.24f
        if (rise in 0f..1f) {
            val colour = v.of(listOf(MoPropMoon, MoPropYellow, MoPropSky), salt = 4)
            val fade = colour.copy(alpha = ramp(rise, 0f, 0.15f) * (1f - ramp(rise, 0.7f, 1f)))
            val head = rig.mouth + Offset(side * (84f + 40f * rise), -8f - 70f * rise + 4f * sin(rise * 9f))
            oval(head, 7f, 5.5f, fade)
            limb(head + Offset(6f, 0f), head + Offset(6f, -22f), fade, 3.5f)
            limb(head + Offset(6f, -22f), head + Offset(15f, -17f), fade, 3.5f)
        }
    }

    // The cape trails on the floor on both sides of him, over where his neglected things would lie.
    override val ownsTheFloor get() = true
}

/** The last warm mug: he sips, and his lids sink lower after every sip, until he nearly nods into it. */
private object SleepyWarmMug : MoAct("sleepy_warm_mug", MoOccasion.Sleepy, intensity = MoIntensity.Mild) {
    private const val PERIOD = 9.5f

    private val sips = listOf(0.12f, 0.40f, 0.68f)

    /** Where the mug rides: low on his belly, a clear span of face above it, clear of the floor below. */
    private fun mugY(body: MoBodyKind) = if (body == MoBodyKind.Low) 376f else 374f

    private fun mugScale(body: MoBodyKind) = if (body == MoBodyKind.Low) 0.82f else 1f

    /** How far the mug travels to reach his lips, which sit lower on the low body. */
    private fun sipLift(body: MoBodyKind) = if (body == MoBodyKind.Low) 20f else 38f

    private fun handleSide(v: MoVariant) = v.side(salt = 2)

    private fun sip(p: Float) = sips.maxOf { c -> ramp(p, c - 0.07f, c - 0.02f) * (1f - ramp(p, c + 0.03f, c + 0.09f)) }

    /** After the last sip he sinks towards the mug, until a small start brings him back. */
    private fun drowse(p: Float) = ramp(p, 0.78f, 0.88f) * (1f - ramp(p, 0.90f, 0.915f))

    private fun start(p: Float) = ramp(p, 0.90f, 0.92f) * (1f - ramp(p, 0.96f, 1f))

    /** How heavy his lids are: a step heavier after each sip, and light again once the start has shaken him. */
    private fun lid(p: Float): Float {
        val sunk = sips.fold(0f) { sum, c -> sum + ramp(p, c + 0.04f, c + 0.12f) }
        return mix(0.3f + 0.2f * sunk, 0.3f, ramp(p, 0.90f, 0.92f))
    }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val p = cycle(t, PERIOD)
        val sip = sip(p)
        val drowse = drowse(p)
        val start = start(p)
        val side = handleSide(v)
        val eye = if (sip > 0.5f) shutEye else MoEye(lidDrop = mix(lid(p), 0.08f, start), gaze = Offset(0f, 0.6f * (1f - start)), iris = mix(0.76f, 0.6f, start))
        val shape = MoBodies.of(body)
        val hold = MoArm(Offset(30f * mugScale(body) - shape.shoulderHalf, mugY(body) + 2f - shape.shoulderY - sipLift(body) * sip), bend = 0.3f)
        val idle = moIdle(t, energy = 0.45f)
        return idle.copy(
            leftEye = eye,
            rightEye = eye,
            brows = if (start > 0.5f) MoBrows(MoBrowKind.Raised, lift = 3f) else MoBrows(),
            mouth = when {
                start > 0.5f -> MoMouth.Oh(4f)
                sip > 0.9f -> MoMouth.Hidden
                else -> MoMouth.Smile(mix(11f, 4f, sip), mix(7f, 1f, sip))
            },
            leftArm = hold,
            rightArm = hold,
            armsInFront = true,
            tilt = 2.5f * wave(t, 3.4f) * (1f - sip) * (1f - drowse) + side * 7f * drowse,
            flare = -6f * drowse + 6f * start,
            squash = idle.squash - 0.02f * drowse + 0.02f * start,
            offset = Offset(0f, -2f * drowse - 3f * start),
            blinks = sip < 0.5f && start < 0.5f,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val p = cycle(t, PERIOD)
        val sip = sip(p)
        // Never his own yellow: a mug the colour of his belly would vanish into it.
        val (mug, mugDark) = v.of(
            listOf(MoPropTeal to MoPropTealDark, MoPropPink to MoPropPinkDark, MoPropBlue to MoPropBlueDark, MoPropPurple to MoPropPurpleDark),
            salt = 1,
        )
        val handleSide = handleSide(v)
        val steamSide = -handleSide
        val c = (rig.leftHand + rig.rightHand) / 2f + Offset(0f, -2f)
        val scale = mugScale(rig.body.kind)
        val width = 66f * scale
        val height = 50f * scale

        // The handle bows out past the mitten on its side, so his hand reads as being through it.
        curve(
            c + Offset(handleSide * width * 0.46f, -14f * scale),
            c + Offset(handleSide * width * 1.3f, 2f * scale),
            c + Offset(handleSide * width * 0.46f, 18f * scale),
            mug,
            10f * scale,
        )
        chunk(c, width, height, mug, mugDark, corner = 16f * scale, drop = 5f)
        when (v.pick(3, salt = 3)) {
            0 -> for (dot in listOf(-16f, 0f, 16f)) drawCircle(MoPropCream, 4f * scale, c + Offset(dot * scale, 5f * scale))
            1 -> block(c + Offset(0f, 6f * scale), width, 8f * scale, MoPropCream, corner = 0f)
            else -> Unit
        }
        // A pale ring of rim around the dark drink, so the opening never reads as a second mouth.
        val rim = c + Offset(0f, -height * 0.4f)
        oval(rim, width * 0.44f, 7f * scale, MoPropCream)
        oval(rim, width * 0.44f - 5f * scale, 4.5f * scale, MoPropBrown)

        grippingMitten(rig, -1f)
        grippingMitten(rig, 1f)

        for (wisp in 0..1) {
            val rise = cycle(t, 2.6f, offset = 0.5f * wisp + 0.07f)
            val at = rim + Offset(steamSide * (width * 0.32f + 98f * ease(rise * 2.8f)), -3f - 130f * rise * rise)
            val size = (12f + 9f * rise) * min(1f, rise * 13f) * (1f - ramp(rise, 0.62f, 1f))
            puff(at, size * (1f - sip), MoPropWhite)
        }
    }

    override val sparkles get() = false
}

internal val sleepyBedActs2: List<MoAct> = listOf(
    SleepyDreamOfTomorrow,
    SleepyToothbrushLastThing,
    SleepyDraggingThePillow,
    SleepyBlanketCape,
    SleepyWarmMug,
)
