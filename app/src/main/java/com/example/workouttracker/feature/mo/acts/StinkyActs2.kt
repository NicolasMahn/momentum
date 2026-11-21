package com.example.workouttracker.feature.mo.acts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import com.example.workouttracker.core.ui.theme.MoPropBlue
import com.example.workouttracker.core.ui.theme.MoPropBlueDark
import com.example.workouttracker.core.ui.theme.MoPropCharcoal
import com.example.workouttracker.core.ui.theme.MoPropGold
import com.example.workouttracker.core.ui.theme.MoPropGreen
import com.example.workouttracker.core.ui.theme.MoPropGreenDark
import com.example.workouttracker.core.ui.theme.MoPropGrey
import com.example.workouttracker.core.ui.theme.MoPropGreyDark
import com.example.workouttracker.core.ui.theme.MoPropMint
import com.example.workouttracker.core.ui.theme.MoPropOrange
import com.example.workouttracker.core.ui.theme.MoPropPink
import com.example.workouttracker.core.ui.theme.MoPropPinkDark
import com.example.workouttracker.core.ui.theme.MoPropPurple
import com.example.workouttracker.core.ui.theme.MoPropRed
import com.example.workouttracker.core.ui.theme.MoPropRedDark
import com.example.workouttracker.core.ui.theme.MoPropShadow
import com.example.workouttracker.core.ui.theme.MoPropSilver
import com.example.workouttracker.core.ui.theme.MoPropSky
import com.example.workouttracker.core.ui.theme.MoPropTeal
import com.example.workouttracker.core.ui.theme.MoPropTealDark
import com.example.workouttracker.core.ui.theme.MoPropWhite
import com.example.workouttracker.core.ui.theme.MoPropYellow
import com.example.workouttracker.core.ui.theme.MoStink
import com.example.workouttracker.domain.mo.MoIntensity
import com.example.workouttracker.domain.mo.MoOccasion
import com.example.workouttracker.feature.mo.CENTRE
import com.example.workouttracker.feature.mo.FEET
import com.example.workouttracker.feature.mo.MoArm
import com.example.workouttracker.feature.mo.MoBodies
import com.example.workouttracker.feature.mo.MoBody
import com.example.workouttracker.feature.mo.MoBodyKind
import com.example.workouttracker.feature.mo.MoBrowKind
import com.example.workouttracker.feature.mo.MoBrows
import com.example.workouttracker.feature.mo.MoEye
import com.example.workouttracker.feature.mo.MoEyeShape
import com.example.workouttracker.feature.mo.MoLeg
import com.example.workouttracker.feature.mo.MoLegs
import com.example.workouttracker.feature.mo.MoMouth
import com.example.workouttracker.feature.mo.MoPose
import com.example.workouttracker.feature.mo.MoRig
import com.example.workouttracker.feature.mo.block
import com.example.workouttracker.feature.mo.chunk
import com.example.workouttracker.feature.mo.curve
import com.example.workouttracker.feature.mo.cycle
import com.example.workouttracker.feature.mo.ease
import com.example.workouttracker.feature.mo.floorShadow
import com.example.workouttracker.feature.mo.limb
import com.example.workouttracker.feature.mo.moIdle
import com.example.workouttracker.feature.mo.shape
import com.example.workouttracker.feature.mo.oval
import com.example.workouttracker.feature.mo.puff
import com.example.workouttracker.feature.mo.pulse
import com.example.workouttracker.feature.mo.sparkle
import com.example.workouttracker.feature.mo.twitch
import com.example.workouttracker.feature.mo.wave
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/*
 * A task with the teeth motif is open, part two: what his breath does to the world around him. Flies faint, a skunk falls
 * in love, a flower backs off, and the things that would fix it stay just out of reach.
 */

/** 0 before [from], 1 after [to], eased in between: one step of a longer story told over a cycle. */
private fun ramp(x: Float, from: Float, to: Float): Float = ease((x - from) / max(to - from, 1e-4f))

/** 0 outside [from]..[to], rising to 1 in the middle of it. */
private fun bump(x: Float, from: Float, to: Float): Float {
    val middle = (from + to) / 2f
    return ramp(x, from, middle) * (1f - ramp(x, middle, to))
}

private fun mix(a: Float, b: Float, k: Float): Float = a + (b - a) * k

private fun mix(a: Offset, b: Offset, k: Float): Offset = a + (b - a) * k

private fun mix(a: MoArm, b: MoArm, k: Float): MoArm = MoArm(mix(a.reach, b.reach, k), mix(a.bend, b.bend, k))

/** Which side the thing beside him is on: 1 for the viewer's right, -1 for the left. */
private fun side(v: MoVariant): Float = if (v.chance(50, salt = 1)) 1f else -1f

/** Puts [near] on the arm of side [s] and [far] on the other one. */
private fun MoPose.arms(s: Float, near: MoArm, far: MoArm): MoPose =
    if (s > 0f) copy(rightArm = near, leftArm = far) else copy(leftArm = near, rightArm = far)

private fun MoPose.legs(s: Float, near: MoLeg, far: MoLeg): MoPose =
    copy(legs = if (s > 0f) MoLegs(left = far, right = near) else MoLegs(left = near, right = far))

/** One green wisp of smell: a standing S that leans to and fro. */
private fun DrawScope.stinkWisp(base: Offset, height: Float, lean: Float, colour: Color, width: Float) {
    val middle = base + Offset(0f, -height / 2f)
    curve(base, base + Offset(-lean, -height * 0.25f), middle, colour, width)
    curve(middle, middle + Offset(lean, -height * 0.25f), base + Offset(0f, -height), colour, width)
}

private fun DrawScope.heart(centre: Offset, size: Float, colour: Color) {
    val heart = Path().apply {
        moveTo(centre.x, centre.y + size * 0.78f)
        cubicTo(centre.x - size * 1.3f, centre.y - size * 0.1f, centre.x - size * 0.55f, centre.y - size * 1.05f, centre.x, centre.y - size * 0.35f)
        cubicTo(centre.x + size * 0.55f, centre.y - size * 1.05f, centre.x + size * 1.3f, centre.y - size * 0.1f, centre.x, centre.y + size * 0.78f)
        close()
    }
    drawPath(heart, colour)
}

/** A fly crosses the haze by his mouth, faints, lies on its back by his foot, and wobbles off again. */
private object FlyThatFainted : MoAct("stinky_fly_that_fainted", MoOccasion.Stinky) {
    private const val ARRIVED = 0.30f
    private const val STALLED = 0.36f
    private const val DROPPED = 0.46f
    private const val FLIP = 0.06f

    /** Where a fly comes in from and leaves towards, far enough in that the big one stays in frame. */
    private const val EDGE = 126f

    private fun count(v: MoVariant) = 2 + v.pick(2, salt = 2)

    private fun progress(t: Float, v: MoVariant, index: Int): Float {
        val total = count(v)
        // A third fly needs a longer round, or they would queue up in the haze.
        return cycle(t, if (total == 2) 6f else 7.5f, offset = 0.55f - index.toFloat() / total)
    }

    /** When a fainted fly comes round, as a share of its round. */
    private fun awake(v: MoVariant) = DROPPED + v.between(0.20f, 0.30f, salt = 4)

    // Out beside his mouth at about its own height, clear of it and of the eye above: nothing he
    // breathes may hide his face, and any lower it sits on his chin and reads as a stain.
    private fun haze(body: MoBody, s: Float) = body.mouth + Offset(58f * s, 12f)

    private fun flyAt(f: Float, t: Float, body: MoBody, s: Float, awake: Float): Offset {
        val haze = haze(body, s)
        // Beside his foot, not on it.
        val floor = Offset(CENTRE + 72f * s, FEET - 6f)
        return when {
            f < ARRIVED -> {
                val k = f / ARRIVED
                mix(Offset(CENTRE + EDGE * s, body.mouth.y - 8f), haze, ease(k)) + Offset(0f, sin(k * 9f) * 7f * (1f - k))
            }
            f < STALLED -> haze + Offset(wave(t, 0.09f) * 1.5f * bump(f, ARRIVED, STALLED), 0f)
            f < DROPPED -> {
                val k = (f - STALLED) / (DROPPED - STALLED)
                Offset(mix(haze.x, floor.x, k), mix(haze.y, floor.y, k * k))
            }
            f < awake -> floor
            f < awake + FLIP -> floor + Offset(0f, -6f * sin((f - awake) / FLIP * PI.toFloat()))
            else -> {
                // A dizzy curl that starts on the floor spot, widens, and drifts up and out.
                val k = (f - awake - FLIP) / max(1f - awake - FLIP, 1e-4f)
                val radius = 4f + 14f * k
                val angle = k * 4f * PI.toFloat()
                mix(floor, Offset(CENTRE + (EDGE - 2f) * s, FEET - 80f), ease(k)) + Offset(s * sin(angle) * radius, (cos(angle) - 1f) * radius * 0.8f)
            }
        }
    }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val s = side(v)
        val awake = awake(v)
        val on = MoBodies.of(body)
        // His eyes belong to whichever fly is closest to fainting, and glide from one to the next.
        var look = Offset(0.8f * s, 0f) * 0.15f
        var weight = 0.15f
        var cover = 0f
        var alarm = 0f
        for (index in 0 until count(v)) {
            val f = progress(t, v, index)
            val at = flyAt(f, t, on, s, awake)
            val care = ramp(f, 0.08f, 0.2f) * (1f - ramp(f, awake + 0.02f, awake + 0.12f)) * (1f + 6f * bump(f, 0.25f, awake))
            look += Offset(((at.x - CENTRE) / 90f).coerceIn(-1f, 1f), ((at.y - on.eyeY) / 110f).coerceIn(-1f, 1f)) * care
            weight += care
            // The mitten goes up a beat too late: the fly is already on the floor.
            cover = max(cover, ramp(f, 0.60f, 0.67f) * (1f - ramp(f, 0.76f, 0.84f)))
            alarm = max(alarm, bump(f, STALLED, 0.54f))
        }
        val gaze = look / weight
        val toMouth = MoArm(Offset(-on.shoulderHalf + 3f, on.mouth.y - on.shoulderY + 3f), bend = 0.8f)
        return moIdle(t, energy = 0.8f).copy(
            leftEye = MoEye(gaze = gaze, iris = 0.66f),
            rightEye = MoEye(gaze = gaze, iris = 0.66f),
            brows = MoBrows(MoBrowKind.Worried, lift = 3f + 3f * alarm),
            mouth = MoMouth.Frown(9f),
            armsInFront = true,
        ).arms(s, near = MoArm.Down, far = mix(MoArm.Down, toMouth, cover).copy(warm = cover))
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val s = side(v)
        val awake = awake(v)
        val wing = v.of(listOf(MoPropSky, MoPropSilver, MoPropMint), salt = 3)
        // Nearly solid: a translucent green over his belly reads as a stain rather than as a cloud.
        val green = MoStink.copy(alpha = 0.85f + 0.1f * pulse(t, 2.3f))
        // A smaller puff just off the corner of his mouth ties the haze to the breath it came from.
        puff(rig.mouth + Offset(30f * s, 12f), 8f, green)
        puff(haze(rig.body, s), 19f, green)
        for (index in 0 until count(v)) {
            val f = progress(t, v, index)
            val at = flyAt(f, t, rig.body, s, awake)
            val alpha = ramp(f, 0f, 0.05f) * (1f - ramp(f, 0.95f, 1f))
            val onFloor = ramp(f, DROPPED - 0.04f, DROPPED) * (1f - ramp(f, awake + FLIP, awake + FLIP + 0.04f))
            val onBack = ramp(f, STALLED, DROPPED) * (1f - ramp(f, awake, awake + FLIP))
            val flying = (1f - ramp(f, ARRIVED - 0.01f, ARRIVED + 0.01f)) + ramp(f, awake + FLIP - 0.01f, awake + FLIP + 0.01f)
            floorShadow(at.x, 13f, MoPropShadow.copy(alpha = MoPropShadow.alpha * onFloor))
            fly(at, wing, flap = abs(wave(t, 0.07f)) * flying, onBack = onBack, alpha = alpha, wiggle = wave(t + index, 0.2f))
        }
    }

    // Twice the fly in CoreActs: this one is the whole story, and a fly at its usual size is a speck
    // at thumbnail size, where the fainted one has to be seen lying on its back with its legs up.
    private fun DrawScope.fly(centre: Offset, wing: Color, flap: Float, onBack: Float, alpha: Float, wiggle: Float) =
        withTransform({ scale(2.1f, 2.1f, centre + Offset(0f, 4.5f)) }) { plainFly(centre, wing, flap, onBack, alpha, wiggle) }

    private fun DrawScope.plainFly(at: Offset, wing: Color, flap: Float, onBack: Float, alpha: Float, wiggle: Float) {
        // Belly up, the wings lie flat under it and the legs are in the air.
        val wingY = mix(-5f - flap * 3f, 4f, onBack)
        oval(at + Offset(-5f, wingY), 6f, 3.5f, wing.copy(alpha = 0.85f * alpha))
        oval(at + Offset(5f, wingY), 6f, 3.5f, wing.copy(alpha = 0.85f * alpha))
        if (onBack > 0.5f) {
            for (leg in 0..3) {
                val x = -4.5f + 3f * leg
                val kick = wiggle * if (leg % 2 == 0) 1.8f else -1.8f
                limb(at + Offset(x, -2f), at + Offset(x + kick, -7.5f), MoPropGrey.copy(alpha = alpha), 2f)
            }
        }
        oval(at, 5.5f, 4.5f, MoPropGreyDark.copy(alpha = alpha))
    }

    // Blazing's sparkles are small bright things drifting through the air: exactly what the flies are.
    override val ownsTheFloor get() = true
    override val sparkles get() = false
}

/** A skunk at the edge thinks he smells wonderful and sends him hearts; he leans politely away. */
private object SmittenSkunk : MoAct("stinky_smitten_skunk", MoOccasion.Stinky) {
    /** The skunk's half step closer and back, which his lean answers. */
    private fun shuffle(t: Float) = pulse(t + 1f, 4f)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val s = side(v)
        val u = shuffle(t)
        val look = MoEye(gaze = Offset(0.95f * s, 0.3f), iris = 0.58f, scale = 1.05f)
        return moIdle(t, energy = 0.8f).copy(
            leftEye = look,
            rightEye = look,
            brows = MoBrows(MoBrowKind.Worried, lift = 3f),
            mouth = MoMouth.Wavy,
            tilt = -s * (3f + 4f * u),
            offset = Offset(-s * (9f + 5f * u), 0f),
        )
            .arms(s, near = MoArm(Offset(22f, -12f - 6f * u), bend = 0.6f), far = MoArm.Down)
            .legs(s, near = MoLeg(), far = MoLeg(swing = 4f * u))
    }

    private fun skunkX(t: Float, s: Float) = CENTRE + s * (126f - 8f * shuffle(t))

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val s = side(v)
        // A slate grey, not black: a charcoal animal disappears into the background at this size.
        val fur = MoPropGreyDark
        val x = skunkX(t, s)
        // Its feet only patter while it is actually moving.
        val moving = abs(wave(t + 1f, 4f))
        floorShadow(x, 28f, MoPropShadow)

        // The plume stands beside the body on the outer side, or the body would hide what makes it a skunk.
        val tailBase = Offset(x + 14f * s, FEET - 14f)
        withTransform({ rotate(5f * wave(t, v.between(0.7f, 1.2f, salt = 4)), tailBase) }) {
            oval(tailBase + Offset(0f, -42f), 14f, 42f, fur)
            oval(tailBase + Offset(0f, -46f), 6.5f, 33f, MoPropWhite)
        }
        for (foot in listOf(-1f, 1f)) {
            oval(Offset(x + foot * 9f - 3f * s, FEET - 4f - 3f * moving * max(foot * wave(t, 0.5f), 0f)), 9f, 5f, fur)
        }
        oval(Offset(x - 2f * s, FEET - 28f), 18f, 26f, fur)
        // The white stripe runs up his back, on the side the tail is on.
        oval(Offset(x + 7f * s, FEET - 38f), 6f, 17f, MoPropWhite)

        val head = Offset(x - 6f * s, FEET - 64f + 1.5f * wave(t, 1.6f))
        for (ear in listOf(-1f, 1f)) drawCircle(fur, 5.5f, head + Offset(ear * 11f, -13f))
        drawCircle(fur, 16f, head)
        oval(head + Offset(-s * 14f, 6f), 9f, 6.5f, fur)
        block(head + Offset(s * 2f, -10f), 8f, 17f, MoPropWhite, corner = 4f)
        for (eye in listOf(-1f, 1f)) {
            val at = head + Offset(eye * 6.5f - s * 5f, 1f)
            curve(at + Offset(-3.5f, 2f), at + Offset(0f, -4.5f), at + Offset(3.5f, 2f), MoPropCharcoal, 2.8f)
        }
        drawCircle(MoPropPink, 3.4f, head + Offset(-s * 21f, 5f))
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val s = side(v)
        val bases = listOf(Offset(CENTRE - s * 100f, rig.body.shoulderY - 44f), rig.tip + Offset(-s * 40f, 40f))
        bases.forEachIndexed { index, base ->
            val rise = cycle(t, 2.8f, offset = index * 0.5f)
            val fade = MoStink.copy(alpha = ramp(rise, 0f, 0.15f) * (1f - rise * rise))
            stinkWisp(base + Offset(0f, -60f * rise), 36f, 9f + 3f * wave(t + index, 1.4f), fade, 5.5f)
        }
    }

    override fun DrawScope.foreground(t: Float, v: MoVariant) {
        val s = side(v)
        val colour = v.of(listOf(MoPropPink, MoPropRed, MoPropPurple), salt = 2)
        val total = 2 + v.pick(3, salt = 3)
        val x = skunkX(t, s)
        for (index in 0 until total) {
            val rise = cycle(t, 3.2f, offset = index.toFloat() / total)
            // They drift outward as they rise, away from his fending mitten.
            val at = Offset(x + s * 10f * rise + sin(rise * 5f + index) * 6f, FEET - 98f - rise * 110f)
            heart(at, 9f + 5f * rise, colour.copy(alpha = ramp(rise, 0f, 0.1f) * (1f - rise * rise)))
        }
    }

    override val ownsTheFloor get() = true
    override val sparkles get() = false
}

/** His toothbrush lies just out of reach: he stretches, pats the floor short of it, and nearly tips. */
private object BrushOutOfReach : MoAct("stinky_brush_out_of_reach", MoOccasion.Stinky) {
    private const val PERIOD = 5f

    private fun progress(t: Float) = cycle(t, PERIOD, offset = 0.1f)

    /** How far he leans: 1 is the full stretch, more is nearly tipping, below 0 the spring back. */
    private fun lean(p: Float): Float =
        1f + 0.3f * ramp(p, 0.60f, 0.66f) - 1.65f * ramp(p, 0.70f, 0.78f) + 0.35f * ramp(p, 0.78f, 0.84f) + ramp(p, 0.90f, 1f)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val s = side(v)
        val p = progress(t)
        val lean = lean(p)
        val stretch = lean.coerceIn(0f, 1f)
        val wobble = ramp(p, 0.58f, 0.63f) * (1f - ramp(p, 0.70f, 0.73f))
        // The pats are counted from the start of the stretch, so the loop closes on them too.
        val since = p * PERIOD
        var pat = 0f
        for (index in 0 until 2 + v.pick(2, salt = 3)) {
            val into = (since - 0.5f - index * 0.55f) / 0.22f
            if (into in 0f..1f) pat = sin(into * PI.toFloat())
        }
        // The Low body's shoulders sit lower, so the floor is nearer. The elbow bows well outward,
        // or the arm lies along his own silhouette and the stretch is not seen at all.
        val floor = if (body == MoBodyKind.Tall) 70f else 62f
        val reaching = MoArm(Offset(-3f, floor - 7f * pat), bend = 0.55f)
        val balancing = MoArm(Offset(23f, -13f - 14f * wobble * wave(t, 0.25f)), bend = 0.2f)
        val breath = bump(p, 0.82f, 0.94f)
        val startled = p in 0.70f..0.93f
        val look = MoEye(gaze = Offset(0.95f * s, 0.85f), iris = if (startled) 0.58f else 0.7f)
        return moIdle(t, energy = 0.8f).copy(
            leftEye = look,
            rightEye = look,
            brows = MoBrows(MoBrowKind.Worried, lift = 2f + 3f * wobble),
            mouth = if (startled) MoMouth.Oh(7f) else MoMouth.TongueOut,
            tilt = s * (8f * lean + 1.2f * wave(t, 0.5f) * wobble),
            offset = Offset(s * (3f * lean - 17f), 0f),
            squash = 0.03f * breath - 0.02f * bump(p, 0.74f, 0.82f),
        )
            .arms(s, near = mix(MoArm.Down, reaching, stretch), far = mix(MoArm.Down, balancing, stretch))
            .legs(s, near = MoLeg(lift = 4f * stretch), far = MoLeg(lift = 14f * stretch, swing = 8f * stretch))
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val s = side(v)
        val x = CENTRE + s * 138f
        val rim = Offset(x, FEET - 30f)
        floorShadow(x, 20f, MoPropShadow)
        oval(rim, 17f, 5f, MoPropGreyDark)
        toothbrush(rim + Offset(-s * 3f, -8f), s, v, salt = 2, angle = 80f)
        shape(MoPropSilver, x - 17f to rim.y, x + 17f to rim.y, x + 12f to FEET - 2f, x - 12f to FEET - 2f)
        oval(Offset(x, FEET - 2f), 12f, 3f, MoPropGrey)
        limb(rim + Offset(-15f, 1f), rim + Offset(15f, 1f), MoPropWhite, 4f)
    }

    override val ownsTheFloor get() = true
}

/** He leans in to smell a potted flower, and it leans away by exactly as much. */
private object FlowerLeansAway : MoAct("stinky_flower_leans_away", MoOccasion.Stinky, intensity = MoIntensity.Mild) {
    private fun progress(t: Float) = cycle(t, 6f, offset = 0.2f)

    private fun lean(p: Float) = ramp(p, 0f, 0.28f) * (1f - ramp(p, 0.50f, 0.70f))

    /** The flower bends with him but only dares to straighten a moment after he has. */
    private fun bend(p: Float) = ramp(p, 0f, 0.28f) * (1f - ramp(p, 0.60f, 0.80f))

    // Bent away it drops further than it slides, which is what makes the recoil read as a recoil at
    // thumbnail size rather than as a flower standing still.
    private fun head(x: Float, s: Float, bend: Float) = Offset(x + s * 28f * bend, FEET - 116f + 32f * bend)

    // Far enough in that the petals stay in the box at the deepest bend.
    private fun potX(s: Float) = CENTRE + s * 110f

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val s = side(v)
        val p = progress(t)
        val lean = lean(p)
        val base = moIdle(t, energy = 0.8f).copy(
            tilt = s * 9f * lean,
            offset = Offset(s * 6f * lean, 0f),
        ).arms(s, near = MoArm(Offset(-2f, 34f), bend = 0.1f), far = MoArm.Down)
        return when {
            p < 0.35f -> base.eyes(MoEye(shape = MoEyeShape.Happy)).copy(
                brows = MoBrows(MoBrowKind.Raised, lift = 2f),
                mouth = MoMouth.Smile(12f, 9f),
            )
            p < 0.70f -> base.eyes(MoEye(gaze = Offset(0.9f * s, 0.2f), lid = -0.4f)).copy(
                brows = MoBrows(MoBrowKind.Worried),
                mouth = MoMouth.Frown(9f),
            )
            else -> base.eyes(MoEye(gaze = Offset(0.8f * s, 0.4f))).copy(
                brows = MoBrows(MoBrowKind.Raised, lift = 1f),
                mouth = if (p < 0.88f) MoMouth.Flat(9f) else MoMouth.Smile(10f, 6f),
            )
        }
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val s = side(v)
        val bend = bend(progress(t))
        val petal = petal(v)
        val (pot, potLedge) = v.of(listOf(MoPropTeal to MoPropTealDark, MoPropRed to MoPropRedDark, MoPropBlue to MoPropBlueDark), salt = 3)
        val x = potX(s)
        val head = head(x, s, bend)
        floorShadow(x, 24f, MoPropShadow)
        curve(Offset(x, FEET - 30f), Offset(x + s * 14f * bend, FEET - 78f), head, MoPropGreenDark, 6f)
        // Halfway up the bent stem, so the leaves ride along with it.
        val middle = Offset(x + s * 14f * bend, FEET - 73f + 8f * bend)
        oval(middle + Offset(9f, -2f), 10f, 5f, MoPropGreen)
        oval(middle + Offset(-9f, 4f), 10f, 5f, MoPropGreen)
        chunk(Offset(x, FEET - 16f), width = 36f, height = 28f, colour = pot, ledge = potLedge, corner = 8f)

        val total = 5 + v.pick(2, salt = 5)
        for (index in 0 until total) {
            val angle = index * 2f * PI.toFloat() / total - PI.toFloat() / 2f
            // The petals on the lower half hang when it droops.
            val droop = if (sin(angle) > 0f) 9f * bend else 0f
            drawCircle(petal, 6.5f, head + Offset(cos(angle) * 12f, sin(angle) * 12f + droop))
        }
        drawCircle(if (petal == MoPropYellow) MoPropOrange else MoPropYellow, 7.5f, head)
    }

    override fun DrawScope.foreground(t: Float, v: MoVariant) {
        if (!v.chance(25, salt = 4)) return
        val s = side(v)
        val p = progress(t)
        if (p < 0.35f || p > 0.95f) return
        // One petal lets go at the moment he opens his eyes, and sways down to the floor.
        val fall = ramp(p, 0.35f, 0.75f)
        val from = head(potX(s), s, 1f) + Offset(0f, 12f)
        val at = Offset(from.x + sin(fall * 7f) * 8f * (1f - fall), mix(from.y, FEET - 5f, fall))
        drawCircle(petal(v).copy(alpha = ramp(p, 0.35f, 0.38f) * (1f - ramp(p, 0.80f, 0.95f))), 5f, at)
    }

    private fun petal(v: MoVariant) = v.of(listOf(MoPropPink, MoPropYellow, MoPropSky, MoPropPurple), salt = 2)

    override val ownsTheFloor get() = true
}

/** A small green cloud hangs beside his flame; he sidesteps away from it, and it drifts after him. */
private object CloudFollowsHim : MoAct("stinky_cloud_follows_him", MoOccasion.Stinky, intensity = MoIntensity.Mild) {
    private const val STEP = 26f

    /** Where the cloud rests beside him, as a distance from his middle. */
    private const val BESIDE = 102f

    /** Positive while he crosses away from the cloud, negative on the way back; he rests beyond +-0.55. */
    private fun stride(t: Float) = wave(t + 0.55f, 6f)

    /** Where he stands, as steps away from the cloud's side: he rests at each end and crosses in a second. */
    private fun away(t: Float) = STEP * (2f * ease(0.5f + 0.9f * stride(t)) - 1f)

    private fun lagOf(v: MoVariant) = v.between(0.7f, 1.1f, salt = 2)

    /**
     * Where a thing that follows him sits in the frame: beside the spot he stood on [lag] seconds
     * ago. Written in frame coordinates, so however far he steps it stays inside the view box.
     */
    private fun trailing(t: Float, lag: Float, rest: Float) =
        (rest - away(t - lag)).coerceIn(rest - STEP, rest + STEP)

    /** How far the cloud is from him now. Larger than [BESIDE] means it is still catching up. */
    private fun apart(t: Float, v: MoVariant) = trailing(t, lagOf(v), BESIDE) + away(t)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val s = side(v)
        val moving = 1f - ramp(abs(stride(t)), 0.45f, 0.6f)
        val hops = v.chance(50, salt = 4)
        val hopeful = apart(t, v) > BESIDE + 3f
        // Just before he sets off again he checks where he could go.
        val planning = cycle(t, 6f, offset = 0.55f / 6f) in 0.82f..0.91f
        val look = when {
            planning -> MoEye(gaze = Offset(-0.8f * s, 0.1f))
            hopeful -> MoEye(gaze = Offset(0.6f * s, -0.8f))
            else -> MoEye(gaze = Offset(0.8f * s, -0.7f), lidDrop = 0.25f)
        }
        val arm = if (hopeful) MoArm.Down else MoArm(MoArm.Down.reach + Offset(0f, 4f), MoArm.Down.bend)
        return moIdle(t).copy(
            leftEye = look,
            rightEye = look,
            brows = if (hopeful || planning) MoBrows(MoBrowKind.Raised, lift = 3f) else MoBrows(MoBrowKind.Flat),
            mouth = if (hopeful) MoMouth.Smile(10f, 6f) else MoMouth.Flat(10f),
            leftArm = arm,
            rightArm = arm,
            legs = if (hops) MoLegs() else MoLegs(
                MoLeg(lift = 7f * moving * twitch(t, 0.4f, beat = 0.2f)),
                MoLeg(lift = 7f * moving * twitch(t, 0.4f, beat = 0.2f, offset = 0.5f)),
            ),
            offset = Offset(-s * away(t), if (hops) -6f * moving * abs(wave(t, 0.4f)) else 0f),
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val s = side(v)
        val size = v.between(19f, 25f, salt = 5)
        // He is drawn shifted by his sidestep, so it is taken back out: the cloud keeps its own place.
        val apart = apart(t, v)
        // Beside a shoulder tongue on either body, near his face, never as high as his tip.
        val height = rig.body.top + if (rig.body.kind == MoBodyKind.Tall) 116f else 96f
        val centre = Offset(CENTRE + s * max(apart, BESIDE - 8f), height + 3f * wave(t, 2.1f))
        val bumped = ((BESIDE - 8f - apart) / 6f).coerceIn(0f, 1f)
        if (v.chance(35, salt = 3)) {
            val tail = trailing(t, 2f * lagOf(v), BESIDE + 14f) + away(t)
            puff(Offset(CENTRE + s * tail, centre.y + 20f), size * 0.4f, MoStink)
        }
        // The wisp rises off its top: hanging under it, it turned the cloud into a little tree.
        val rise = cycle(t, 2.6f)
        val fade = MoStink.copy(alpha = 0.8f * ramp(rise, 0f, 0.2f) * (1f - rise * rise))
        stinkWisp(centre + Offset(-s * 4f, -size * 0.9f - 10f * rise), 18f, 5f + 2f * wave(t, 1.7f), fade, 4f)
        withTransform({ scale(1f - 0.1f * bumped, 1f + 0.06f * bumped, centre) }) { puff(centre, size, MoStink) }
    }
}

/** A wind-up set of gleaming teeth chatters beside him, and he looks at it with plain longing. */
private object WindUpTeeth : MoAct("stinky_wind_up_teeth", MoOccasion.Stinky, intensity = MoIntensity.Mild) {
    private const val PERIOD = 3.6f

    private fun progress(t: Float) = cycle(t, PERIOD, offset = 0.1f)

    private fun burst(v: MoVariant) = v.between(0.45f, 0.65f, salt = 4)

    /** How hard the spring is driving it: up at once, and running down at the end of the burst. */
    private fun drive(b: Float, burst: Float) = ramp(b, 0f, 0.05f) * (1f - ramp(b, burst - 0.12f, burst))

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val s = side(v)
        val b = progress(t)
        val burst = burst(v)
        val watching = drive(b, burst)
        val look = MoEye(gaze = mix(Offset(0.5f * s, 0.6f), Offset(0.9f * s, 0.9f), watching), lid = -0.3f)
        val raised = ramp(b, 0.18f, 0.30f) * (1f - ramp(b, burst - 0.04f, burst + 0.10f))
        return moIdle(t, energy = 0.7f).copy(
            leftEye = look,
            rightEye = look,
            brows = MoBrows(MoBrowKind.Worried, lift = 2f),
            mouth = MoMouth.Frown(9f),
            armsInFront = true,
            tilt = s * 2f,
        ).arms(s, near = MoArm.Down, far = mix(MoArm.Down, MoArm.Cheek, raised).copy(warm = raised))
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val s = side(v)
        val b = progress(t)
        val burst = burst(v)
        val drive = drive(b, burst)
        val (gum, gumLedge) = v.of(listOf(MoPropPink to MoPropPinkDark, MoPropRed to MoPropRedDark), salt = 2)
        val key = v.of(listOf(MoPropGold, MoPropSilver, MoPropTeal), salt = 3)

        // Shifted so that the first frame catches it in the air with its jaws apart.
        val hop = abs(sin(t / 0.32f * 2f * PI.toFloat() + 1f)) * drive
        val open = 10f * abs(cos(t / 0.16f * 2f * PI.toFloat() + 0.3f)) * drive
        val x = CENTRE + s * (137f - 6f * bump(b, 0f, burst))
        val lift = 8f * hop
        floorShadow(x, 22f - 4f * hop, MoPropShadow)

        for (foot in listOf(-1f, 1f)) oval(Offset(x + foot * 11f, FEET - 3f - lift), 7f, 4f, MoPropYellow)
        val lowerTop = FEET - 26f - lift
        val upperBottom = lowerTop - 5f - open
        // The key stands on the upper jaw, because nothing fits between the toy and the edge.
        val stem = Offset(x + s * 8f, upperBottom - 23f)
        limb(stem, stem + Offset(0f, -8f), key, 3f)
        val turn = max(7f * abs(cos(t * 3f)), 1.5f)
        drawOval(key, Offset(stem.x - turn, stem.y - 26f), Size(turn * 2f, 14f), style = Stroke(width = 3f))

        // The dark inside of its mouth, so the two jaws part instead of coming apart.
        block(Offset(x, (upperBottom + lowerTop) / 2f), 30f, lowerTop - upperBottom + 6f, MoPropCharcoal, corner = 3f)
        chunk(Offset(x, upperBottom - 15f), width = 38f, height = 10f, colour = gum, ledge = gumLedge, corner = 5f, drop = 4f)
        chunk(Offset(x, lowerTop + 15f), width = 38f, height = 10f, colour = gum, ledge = gumLedge, corner = 5f, drop = 4f)
        // The two white bands meet when it shuts: more tooth than gum, or it reads as a striped tin.
        block(Offset(x, upperBottom - 5f), 32f, 10f, MoPropWhite, corner = 2f)
        block(Offset(x, lowerTop + 5f), 32f, 10f, MoPropWhite, corner = 2f)
        for (gapIndex in -2..2) {
            val gapX = x + gapIndex * 6.4f
            limb(Offset(gapX, upperBottom - 8f), Offset(gapX, upperBottom - 2f), MoPropGrey, 1.2f)
            limb(Offset(gapX, lowerTop + 2f), Offset(gapX, lowerTop + 8f), MoPropGrey, 1.2f)
        }

        val glint = bump(b, 0f, burst / 3f + 0.06f)
        // Half off the near corner of the upper teeth: white on the white band cannot be seen at all,
        // so the star has to hang over the dark beside them. Its size is the glint, so it never pops.
        if (glint > 0.01f && (v.chance(50, salt = 5) || cycle(t, PERIOD * 2f, offset = 0.05f) < 0.5f)) {
            sparkle(Offset(x - s * 19f, upperBottom - 4f), 10f, glint, MoPropWhite)
        }
    }

    override val ownsTheFloor get() = true
    override val sparkles get() = false
}

internal val stinkyActs2: List<MoAct> = listOf(
    FlyThatFainted,
    SmittenSkunk,
    BrushOutOfReach,
    FlowerLeansAway,
    CloudFollowsHim,
    WindUpTeeth,
)
