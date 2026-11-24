package de.nicolasmahn.momentum.feature.mo.acts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import de.nicolasmahn.momentum.core.ui.theme.MoHusk
import de.nicolasmahn.momentum.core.ui.theme.MoHuskCrack
import de.nicolasmahn.momentum.core.ui.theme.MoHuskLedge
import de.nicolasmahn.momentum.core.ui.theme.MoPropBlue
import de.nicolasmahn.momentum.core.ui.theme.MoPropBlueDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropCharcoal
import de.nicolasmahn.momentum.core.ui.theme.MoPropCream
import de.nicolasmahn.momentum.core.ui.theme.MoPropGlass
import de.nicolasmahn.momentum.core.ui.theme.MoPropGold
import de.nicolasmahn.momentum.core.ui.theme.MoPropGreen
import de.nicolasmahn.momentum.core.ui.theme.MoPropGreenDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropGrey
import de.nicolasmahn.momentum.core.ui.theme.MoPropGreyDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropLeaf
import de.nicolasmahn.momentum.core.ui.theme.MoPropMint
import de.nicolasmahn.momentum.core.ui.theme.MoPropNight
import de.nicolasmahn.momentum.core.ui.theme.MoPropPink
import de.nicolasmahn.momentum.core.ui.theme.MoPropPurple
import de.nicolasmahn.momentum.core.ui.theme.MoPropPurpleDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropSand
import de.nicolasmahn.momentum.core.ui.theme.MoPropShadow
import de.nicolasmahn.momentum.core.ui.theme.MoPropSteel
import de.nicolasmahn.momentum.core.ui.theme.MoPropTeal
import de.nicolasmahn.momentum.core.ui.theme.MoPropTealDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropWater
import de.nicolasmahn.momentum.core.ui.theme.MoPropWhite
import de.nicolasmahn.momentum.core.ui.theme.MoPropWood
import de.nicolasmahn.momentum.core.ui.theme.MoPropWoodDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropYellowDark
import de.nicolasmahn.momentum.core.ui.theme.MoSpark
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
import de.nicolasmahn.momentum.feature.mo.flameTongue
import de.nicolasmahn.momentum.feature.mo.floorShadow
import de.nicolasmahn.momentum.feature.mo.limb
import de.nicolasmahn.momentum.feature.mo.moIdle
import de.nicolasmahn.momentum.feature.mo.oval
import de.nicolasmahn.momentum.feature.mo.polyline
import de.nicolasmahn.momentum.feature.mo.puff
import de.nicolasmahn.momentum.feature.mo.pulse
import de.nicolasmahn.momentum.feature.mo.quadTo
import de.nicolasmahn.momentum.feature.mo.twitch
import de.nicolasmahn.momentum.feature.mo.wave
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.tan

/*
 * Scared, part one: no freeze is left and the day is running out. Most of these are small frights
 * he could keep up for a while; only the shape behind him takes him over and turns him pale.
 */

/** How far [c] is through the stretch from [from] to [to], from 0 to 1. The stretches are constants, never empty. */
private fun span(c: Float, from: Float, to: Float): Float = ((c - from) / (to - from)).coerceIn(0f, 1f)

private fun mix(a: Float, b: Float, f: Float): Float = a + (b - a) * f

private fun mix(a: MoArm, b: MoArm, f: Float): MoArm =
    MoArm(Offset(mix(a.reach.x, b.reach.x, f), mix(a.reach.y, b.reach.y, f)), mix(a.bend, b.bend, f))

/** One leaf: an oval laid along [degrees], so a seedling can splay out sideways instead of standing up. */
private fun DrawScope.leafBlade(at: Offset, degrees: Float, colour: Color) {
    withTransform({ rotate(degrees, at) }) { oval(at, 25f, 8f, colour) }
}

/** The reach that puts a mitten on the eye of its own side, on whichever body he has. */
private fun overTheEye(b: MoBody, left: Boolean): Offset {
    val eyeFromCentre = if (left) CENTRE - b.leftEyeX else b.rightEyeX - CENTRE
    return Offset(-(b.shoulderHalf - eyeFromCentre), b.eyeY - b.shoulderY)
}

/** Both mittens over his eyes; one slides down to the cheek so that eye can peek, then the other. */
private object ScaredOneEyeAtATime : MoAct("scared_one_eye_at_a_time", MoOccasion.Scared) {
    private const val LOOP = 4.8f

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val b = MoBodies.of(body)
        val c = cycle(t, LOOP, offset = 0.1f)
        val leftPeeks = (c < 0.5f) == v.chance(50, salt = 1)
        val turn = (c * 2f).mod(1f)
        val peek = ease(turn / 0.1f) * (1f - ease((turn - v.between(0.5f, 0.68f, salt = 2)) / 0.1f))

        fun arm(left: Boolean): MoArm {
            val cover = overTheEye(b, left)
            return if (left == leftPeeks) {
                MoArm(cover + Offset(4f, 40f) * peek, bend = 0.55f, warm = 1f)
            } else {
                MoArm(cover + Offset(0f, wave(t, 0.1f) * 1.5f), bend = 0.55f, warm = 1f)
            }
        }
        val gaze = if (v.chance(40, salt = 3)) Offset(0f, -0.9f) else Offset((sin(t * 5f) * 3f).coerceIn(-1f, 1f) * 0.75f, -0.2f)
        val peeking = if (peek > 0.4f) {
            MoEye(open = ease((peek - 0.4f) / 0.4f), iris = 0.3f, scale = 1.12f, gaze = gaze)
        } else {
            MoEye(shape = MoEyeShape.Shut)
        }
        val knock = wave(t, 0.17f) * 2.5f
        val leg = MoLeg(swing = -7f + knock)
        return moIdle(t, energy = 0.6f).copy(
            leftEye = if (leftPeeks) peeking else MoEye(shape = MoEyeShape.Shut),
            rightEye = if (leftPeeks) MoEye(shape = MoEyeShape.Shut) else peeking,
            brows = MoBrows(MoBrowKind.Worried, lift = 4f + 3f * peek),
            mouth = MoMouth.Zigzag,
            leftArm = arm(left = true),
            rightArm = arm(left = false),
            armsInFront = true,
            legs = MoLegs(leg, leg),
            // His tip stands on end each time a mitten comes down; the offset lines it up with the peeks.
            flare = 20f * twitch(t, LOOP / 2f, beat = 0.5f, offset = 0.2f),
            shake = wave(t, 0.07f),
            squash = -0.02f,
            blinks = false,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        for (index in 0..v.pick(2, salt = 4)) {
            val side = if (index == 0) -1f else 1f
            val eye = if (index == 0) rig.leftEye else rig.rightEye
            val fly = cycle(t, 1.2f, offset = 0.18f + index * 0.45f)
            drop(eye + Offset(side * (38f + 14f * fly), -30f + 26f * fly), 5.5f * ease(fly / 0.12f), MoPropWater.copy(alpha = 1f - fly))
        }
    }
}

/** He holds an hourglass on its side so the last sand cannot run, and it seesaws in his shaking mittens. */
private object HoldingTimeLevel : MoAct("scared_holding_time_level", MoOccasion.Scared) {
    private const val HALF = 54f

    private fun swing(t: Float, v: MoVariant): Float = (t / 3.6f + 0.2f) * 2f * PI.toFloat() + if (v.chance(50, salt = 3)) PI.toFloat() else 0f

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val b = MoBodies.of(body)
        val rock = sin(swing(t, v))
        val dip = abs(rock)
        val tremble = wave(t, 0.09f) * 1.2f
        // He holds it low, down in front of his base: on the low body there is no room for it
        // between a gritted mouth and the floor.
        val hands = 394f - b.shoulderY
        val look = MoEye(iris = 0.55f - 0.2f * dip, gaze = Offset(-0.8f * (rock * 2f).coerceIn(-1f, 1f), 0.9f))
        return moIdle(t, energy = 0.7f).copy(
            leftEye = look,
            rightEye = look,
            brows = MoBrows(MoBrowKind.Worried, lift = 3f * dip),
            mouth = when {
                dip > 0.6f -> MoMouth.Gritted
                dip < 0.15f -> MoMouth.Oh(size = 5f)
                else -> MoMouth.Wavy
            },
            // His palms straddle the two end plates while the glass rocks between them.
            leftArm = MoArm(Offset(-33f, hands + 7f * rock + tremble), bend = 0.35f),
            rightArm = MoArm(Offset(-33f, hands - 7f * rock - tremble), bend = 0.35f),
            armsInFront = true,
            legs = MoLegs(MoLeg(swing = 3f), MoLeg(swing = 3f)),
            tilt = -1.5f * rock,
            squash = -0.02f * dip,
        )
    }

    private fun bulb(side: Float) = Path().apply {
        moveTo(side * (HALF - 4f), -20f)
        quadTo(this, Offset(side * (HALF - 4f), -20f), Offset(side * 13f, -19f), Offset(side * 3f, -3f))
        lineTo(side * 3f, 3f)
        quadTo(this, Offset(side * 3f, 3f), Offset(side * 13f, 19f), Offset(side * (HALF - 4f), 20f))
        close()
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val (cap, capDark) = v.of(listOf(MoPropWood to MoPropWoodDark, MoPropTeal to MoPropTealDark, MoPropPurple to MoPropPurpleDark), salt = 1)
        val sand = v.of(listOf(MoPropGold, MoPropYellowDark, MoPropPink), salt = 2)
        val across = rig.rightHand - rig.leftHand
        val mid = (rig.leftHand + rig.rightHand) / 2f
        val angle = atan2(across.y, across.x)
        // Sand lies level with the world, so inside the turned glass its surface slopes the other way.
        val slope = -tan(angle.coerceIn(-0.4f, 0.4f))
        // What has run into the left bulb is the integral of the tilt, so it ends every loop where it began.
        val inLeft = 0.5f - 0.3f * cos(swing(t, v))

        withTransform({
            translate(mid.x, mid.y)
            rotate(angle * 180f / PI.toFloat(), pivot = Offset.Zero)
        }) {
            for (side in listOf(-1f, 1f)) {
                val glass = bulb(side)
                drawPath(glass, MoPropGlass.copy(alpha = 0.9f))
                val level = 18f - 32f * (if (side < 0f) inLeft else 1f - inLeft)
                clipPath(glass) {
                    drawPath(
                        Path().apply {
                            moveTo(0f, level + slope * (-side * 26f))
                            lineTo(side * 52f, level + slope * (side * 26f))
                            lineTo(side * 52f, 30f)
                            lineTo(0f, 30f)
                            close()
                        },
                        sand,
                    )
                }
            }
            // A few grains slip through the neck toward whichever end hangs low.
            val flow = ease((abs(angle) - 0.05f) / 0.05f)
            val grains = 2 + v.pick(3, salt = 4)
            for (index in 0 until grains) {
                val run = cycle(t, 0.4f, offset = index.toFloat() / grains)
                drawCircle(sand.copy(alpha = flow * (1f - run * 0.5f)), 2.4f, Offset(sign(angle) * (-6f + 18f * run), 12f * run * run))
            }
            curve(Offset(17f, -7f), Offset(25f, -13f), Offset(38f, -13f), MoPropWhite.copy(alpha = 0.7f), 3.5f)
            // The frame, drawn over the glass: two posts and two end plates standing taller than the
            // bulbs. Without them a sideways hourglass is simply a bow tie.
            for (rail in listOf(-19f, 19f)) limb(Offset(-HALF, rail), Offset(HALF, rail), capDark, 5f)
            for (side in listOf(-1f, 1f)) chunk(Offset(side * HALF, 0f), width = 13f, height = 50f, cap, capDark, corner = 4f, drop = 4f)
        }
        grippingMitten(rig, -1f)
        grippingMitten(rig, 1f)
    }
}

/** His own Husk looms over one shoulder as a huge shape, and ducks behind him whenever he dares to look. */
private object TheShapeBehindHim : MoAct("scared_the_shape_behind_him", MoOccasion.Scared, intensity = MoIntensity.Strong) {
    private const val LOOP = 6f

    private fun side(v: MoVariant) = if (v.chance(50, salt = 1)) 1f else -1f

    /** How far up the shape is: up while he looks front, gone within half a second of his look, back by the seam. */
    private fun loom(c: Float): Float = (1f - ease(span(c, 0.55f, 0.62f))) + ease(span(c, 0.78f, 1f))

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val c = cycle(t, LOOP)
        val loom = loom(c)
        val quick = v.chance(35, salt = 4)
        val look = if (quick) {
            ease(span(c, 0.52f, 0.55f)) * (1f - ease(span(c, 0.72f, 0.75f)))
        } else {
            ease(span(c, 0.45f, 0.55f)) * (1f - ease(span(c, 0.72f, 0.8f)))
        }
        val eye = MoEye(iris = 0.3f, scale = 1.05f, gaze = Offset(side(v) * look, -0.5f * look))
        val huddled = MoArm(Offset(-48f, 22f), bend = 0.5f)
        return moIdle(t, energy = 0.5f).copy(
            palette = MoPalettes.Pale,
            leftEye = eye,
            rightEye = eye,
            brows = MoBrows(MoBrowKind.Worried, lift = 5f),
            mouth = if (c in 0.64f..0.78f) MoMouth.Oh(size = 6f) else MoMouth.Zigzag,
            leftArm = huddled,
            rightArm = huddled,
            armsInFront = true,
            legs = MoLegs(MoLeg(swing = -8f), MoLeg(swing = -8f)),
            flare = -8f * loom,
            squash = -0.03f,
            shake = wave(t, 0.06f) * 1.4f * loom,
            blinks = false,
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val s = side(v)
        val c = cycle(t, LOOP)
        val loom = loom(c)
        val alpha = ease(loom / 0.25f)
        if (alpha < 0.01f) return
        // It swells a little the longer he does not look. While it is down the swelling can reset unseen.
        val creep = if (c < 0.7f) ease(c / 0.55f) else 0f
        val k = (0.5f + 0.5f * loom) * v.between(0.92f, 1.04f, salt = 3) * (1f + 0.06f * creep)
        // Behind his base when it is down, a whole body out and as tall as him when it is up.
        val cx = CENTRE + s * 86f * (0.3f + 0.7f * loom)
        val shell = MoHusk.copy(alpha = alpha)

        withTransform({ scale(k, k, Offset(cx, FEET)) }) {
            oval(Offset(cx, 348f), 58f, 80f, shell)
            oval(Offset(cx - s * 12f, 212f), 52f, 108f, shell)
            // The tip of a burnt-out flame: collapsed flat, and here it hangs right over his head.
            limb(Offset(cx - s * 16f, 130f), Offset(cx - s * 92f, 84f), shell, 52f)
            block(Offset(cx, FEET - 9f), 120f, 22f, MoHuskLedge.copy(alpha = alpha), 10f)
            // Four points and no symmetry: a crack of three drew a tidy chevron, which at a glance
            // is a mark on the page rather than a split down a burnt-out body.
            val cracks = listOf(
                listOf(Offset(cx + s * 26f, 240f), Offset(cx + s * 38f, 253f), Offset(cx + s * 27f, 265f), Offset(cx + s * 35f, 281f)),
                listOf(Offset(cx + s * 12f, 158f), Offset(cx + s * 23f, 170f), Offset(cx + s * 13f, 183f), Offset(cx + s * 19f, 197f)),
                listOf(Offset(cx + s * 36f, 306f), Offset(cx + s * 45f, 317f), Offset(cx + s * 37f, 330f)),
            )
            cracks.take(2 + v.pick(2, salt = 2)).forEach { polyline(MoHuskCrack.copy(alpha = alpha), 4f, it) }
            // The hollow where his fire was, out on the far side, where Mo never stands in front of it.
            val hollow = Offset(cx + s * 34f, 356f)
            flameTongue(hollow, 21f, MoPropNight.copy(alpha = alpha))
            drawCircle(MoSpark.copy(alpha = alpha), 4f + 1.4f * pulse(t, 2.4f), hollow + Offset(0f, -16f))
        }
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val s = side(v)
        val brow = if (s > 0f) rig.leftEye else rig.rightEye
        for (index in 0..1) {
            val fly = cycle(t, 1.1f, offset = index * 0.5f)
            drop(brow + Offset(-s * (36f + 16f * fly), -28f + 24f * fly), 4.5f, MoPropWater.copy(alpha = 1f - fly))
        }
    }

    override val sparkles get() = false

    // The shape rises from the floor beside him, through where his neglected things would lie.
    override val ownsTheFloor get() = true
}

/** A small raincloud creeps toward his flame; he leans away, then blows it back to the edge. */
private object BlowingTheCloudBack : MoAct("scared_blowing_the_cloud_back", MoOccasion.Scared, intensity = MoIntensity.Mild) {
    private const val LOOP = 5f
    private const val SIZE = 34f

    private fun at(t: Float) = cycle(t, LOOP, offset = 0.35f)

    /** How close the cloud is: three seconds of creeping, held while he breathes in, blown away fast. */
    private fun near(c: Float) = ease(c / 0.6f) * (1f - ease(span(c, 0.71f, 0.83f)))

    private fun blow(c: Float) = ease(span(c, 0.7f, 0.73f)) * (1f - ease(span(c, 0.82f, 0.9f)))

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val c = at(t)
        val near = near(c)
        val blow = blow(c)
        val inhale = ease(span(c, 0.6f, 0.68f)) * (1f - ease(span(c, 0.7f, 0.74f)))
        val eye = MoEye(iris = 0.62f - 0.24f * near, gaze = Offset(0.9f, -0.8f))
        val idle = moIdle(t, energy = 0.8f)
        return idle.copy(
            leftEye = eye,
            rightEye = eye,
            brows = MoBrows(MoBrowKind.Worried, lift = 4f),
            mouth = when {
                c < 0.6f -> if (near > 0.3f) MoMouth.Wavy else MoMouth.Flat(10f)
                c < 0.7f -> MoMouth.Oh(size = 8f)
                c < 0.85f -> MoMouth.Whistle
                else -> MoMouth.Flat(10f)
            },
            // A mitten goes up between him and the cloud as a shield. Reaching only as far as his
            // own outline it read as a wart on his side, so it holds it out at arm's length.
            rightArm = mix(MoArm.Down, MoArm(Offset(40f, -42f), bend = 0.3f), near),
            armsInFront = true,
            legs = MoLegs(left = MoLeg(lift = 5f * near, swing = 6f * near)),
            tilt = -7f * near * (1f - 0.7f * inhale) * (1f - blow) + 3f * blow,
            sway = idle.sway - 12f * near + 10f * blow,
            squash = idle.squash + 0.06f * inhale + 0.02f * blow,
            offset = Offset(-8f * near, 0f),
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val (light, dark) = v.of(listOf(MoPropGrey to MoPropGreyDark, MoPropSteel to MoPropCharcoal, MoPropGreyDark to MoPropCharcoal), salt = 1)
        val c = at(t)
        val near = near(c)
        val wobble = sin(t * 14f) * 4f * ease(span(c, 0.7f, 0.74f)) * (1f - ease(span(c, 0.82f, 0.92f)))
        // Leave room at the right for the wobble. The cloud creeps far enough
        // that the scoot back reads as a retreat, and still never close enough to rain on him.
        val cloud = Offset(228f - v.between(30f, 40f, salt = 3) * near + wobble, 118f)

        val drops = 3 + v.pick(2, salt = 2)
        for (index in 0 until drops) {
            val fall = cycle(t, 0.9f, offset = index * 0.37f)
            // The closer it is, the more the rain slants away from him, so it lands beside him and
            // not on him. The spread is what keeps the far drop and its splash inside x = 275.
            val x = cloud.x + 12f + index * 16f / (drops - 1)
            val slant = 22f * near
            drop(Offset(x + slant * fall, 144f + fall * (FEET - 161f)), 7f, MoPropWater.copy(alpha = 1f - fall * fall * fall * fall))
            if (fall < 0.35f) {
                val splash = fall / 0.35f
                oval(Offset(x + slant, FEET - 1f), 4f + 6f * splash, 2f + 2f * splash, MoPropWater.copy(alpha = 1f - splash))
            }
        }
        // The little one trails up and behind, clear of the big one: stacked on top it turns the cloud into a sack.
        if (v.chance(25, salt = 4)) puff(cloud + Offset(24f, -32f), 10f, light)
        puff(cloud + Offset(0f, 7f), SIZE * 0.93f, dark)
        puff(cloud, SIZE, light)
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val gust = span(at(t), 0.7f, 0.88f)
        if (gust <= 0f || gust >= 1f) return
        // One stream that leaves his mouth and widens on the way to the cloud. Specks starting a
        // body-width away from him were not a breath at all: this is drawn over him, which is where
        // a breath of his own passes, and each puff fades in and out so the window has no edge.
        for (index in 0..3) {
            val p = ((gust - index * 0.1f) / 0.6f).coerceIn(0f, 1f)
            if (p <= 0f || p >= 1f) continue
            val at = rig.mouth + Offset(18f + 99f * ease((p / 0.65f).coerceAtMost(1f)), -2f - 172f * p * p * p)
            puff(at, 9f + 17f * p, MoPropWhite.copy(alpha = 0.9f * sin(p * PI.toFloat())))
        }
    }

    override val sparkles get() = false
}

/** He crouches behind a knee-high seedling as if it hid him, and keeps hauling down the tip that gives him away. */
private object BehindASeedling : MoAct("scared_behind_a_seedling", MoOccasion.Scared, intensity = MoIntensity.Mild) {
    private const val RIM_Y = FEET - 51f

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val c = cycle(t, v.between(3.6f, 5.2f, salt = 4), offset = 0.2f)
        val pop = ease(span(c, 0.6f, 0.65f)) * (1f - ease(span(c, 0.72f, 0.84f)))
        val wince = ease(span(c, 0.72f, 0.75f)) * (1f - ease(span(c, 0.84f, 0.92f)))
        val alarmed = c in 0.6f..0.72f
        // The eyes travel to the tip and back, so neither end of the alarm is a jump.
        val alarm = ease(span(c, 0.6f, 0.63f)) * (1f - ease(span(c, 0.72f, 0.76f)))
        val first = if (v.chance(50, salt = 3)) 1f else -1f
        val eye = MoEye(
            iris = mix(0.5f, 0.32f, alarm),
            scale = mix(1f, 1.08f, alarm),
            open = 1f - 0.5f * wince,
            gaze = Offset(
                mix(first * sin(t * 1.4f + 1.2f) * 0.9f * (1f - wince), 0.2f, alarm),
                mix(0.1f - 0.8f * wince, -1f, alarm),
            ),
        )
        // The low body is squashed less, so his mouth stays above the leaves on both.
        val crouch = if (body == MoBodyKind.Tall) -0.09f else -0.03f
        val gripping = MoArm(Offset(-47f, RIM_Y - MoBodies.of(body).shoulderY), bend = 0.4f)
        return moIdle(t, energy = 0.5f).copy(
            leftEye = eye,
            rightEye = eye,
            brows = if (alarmed) MoBrows(MoBrowKind.Raised, lift = 6f) else MoBrows(MoBrowKind.Worried, lift = 3f),
            mouth = when {
                alarmed -> MoMouth.Oh(size = 8f)
                // The low body's teeth would reach into the leaves.
                wince > 0.5f -> if (body == MoBodyKind.Tall) MoMouth.Gritted else MoMouth.Wavy
                else -> MoMouth.Zigzag
            },
            leftArm = gripping,
            rightArm = gripping,
            armsInFront = true,
            legs = MoLegs(MoLeg(swing = -6f), MoLeg(swing = -6f)),
            flare = -10f + 40f * pop,
            squash = crouch - 0.025f * wince,
            shake = wave(t, 0.07f) * 0.8f,
            blinks = !alarmed,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val (pot, potDark) = v.of(listOf(MoPropTeal to MoPropTealDark, MoPropBlue to MoPropBlueDark, MoPropCream to MoPropSand), salt = 1)
        val leaf = v.of(listOf(MoPropLeaf, MoPropGreen, MoPropMint), salt = 2)
        val nod = wave(t, 2.3f) * 3f
        chunk(Offset(CENTRE, (FEET + RIM_Y) / 2f - 2f), 78f, FEET - RIM_Y - 5f, pot, potDark, corner = 9f)
        block(Offset(CENTRE, RIM_Y), 94f, 12f, pot, 5f)
        limb(Offset(CENTRE, RIM_Y - 2f), Offset(CENTRE + 1f, RIM_Y - 13f), MoPropGreenDark, 7f)
        leafBlade(Offset(CENTRE - 25f, RIM_Y - 10f), 20f + nod, leaf)
        leafBlade(Offset(CENTRE + 27f, RIM_Y - 13f), -20f - nod, leaf)
        grippingMitten(rig, -1f)
        grippingMitten(rig, 1f)
    }

    override val ownsTheFloor get() = true
}

/** It is all too much: one mitten up, eyes rolled up, and he keels over stiff as a board until a foot saves him. */
private object TheSwoon : MoAct("scared_the_swoon", MoOccasion.Scared) {
    private fun side(v: MoVariant) = if (v.chance(50, salt = 1)) 1f else -1f

    private fun at(t: Float, v: MoVariant) = cycle(t, v.between(4.6f, 6f, salt = 3), offset = 0.42f)

    /** The foot shoots out at the last moment and stays out while he gets his balance back. */
    private fun catches(c: Float) = ease(span(c, 0.58f, 0.62f)) * (1f - ease(span(c, 0.74f, 0.88f)))

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val b = MoBodies.of(body)
        val s = side(v)
        val c = at(t, v)
        val over = span(c, 0.12f, 0.5f)
        val snap = ease(span(c, 0.58f, 0.64f))
        val settle = ease(span(c, 0.66f, 0.78f))
        val flung = catches(c)
        // He goes over slowly, hangs at full lean for a moment, and rebounds a little past upright.
        val tilt = s * v.between(17f, 22f, salt = 2) *
            (over * over * (1f - snap) - 0.16f * snap * (1f - settle))

        // The mitten of the arm on the far side from the fall goes up beside his temple and well
        // clear of his outline: an arm reaching across his face would cover an eye whichever way it
        // bends, and a mitten laid on his flame is one orange on another.
        val headSide = -s
        val toHead = ease(span(c, 0f, 0.12f)) * (1f - snap)
        val out = MoArm(Offset(46f, -8f), bend = 0.1f)
        val raised = MoArm(Offset(30f, b.eyeY - 14f - b.shoulderY), bend = 0.35f)
        val headArm = mix(mix(MoArm.Down, raised, toHead), out, flung)
        val otherArm = mix(MoArm.Down, out, flung)

        val eye = if (c < 0.56f) {
            val swoon = ease(span(c, 0.02f, 0.14f))
            MoEye(gaze = Offset(0f, -swoon), iris = mix(0.5f, 0.6f, swoon), lidDrop = 0.5f * swoon)
        } else {
            val calm = ease(span(c, 0.8f, 0.95f))
            MoEye(iris = 0.32f + 0.18f * calm, scale = 1.12f - 0.12f * calm)
        }
        val foot = MoLeg(swing = 20f * flung, lift = 5f * flung)
        val idle = moIdle(t, energy = 0.6f)
        return idle.copy(
            leftEye = eye,
            rightEye = eye,
            brows = MoBrows(MoBrowKind.Worried, lift = 5f),
            mouth = when {
                c < 0.06f -> MoMouth.Flat(12f)
                c < 0.56f -> MoMouth.Wavy
                c < 0.74f -> MoMouth.Oh(size = 9f)
                else -> MoMouth.Zigzag
            },
            leftArm = if (headSide < 0f) headArm else otherArm,
            rightArm = if (headSide < 0f) otherArm else headArm,
            armsInFront = true,
            legs = if (s > 0f) MoLegs(right = foot) else MoLegs(left = foot),
            tilt = tilt,
            // He tips over the edge of his base, so lift him by the little the low side would sink.
            offset = Offset(-0.7f * tilt, -22f * abs(sin(tilt * PI.toFloat() / 180f))),
            flare = 14f * flung,
            squash = idle.squash - 0.05f * flung,
            shake = wave(t, 0.07f) * ease(span(c, 0.63f, 0.7f)) * (1f - ease(span(c, 0.85f, 0.97f))),
            blinks = false,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val s = side(v)
        val c = at(t, v)
        if (c !in 0.58f..0.8f) return
        val fly = span(c, 0.58f, 0.8f)
        // Flung off his brow on the side he did not fall to, out past his face and away.
        val brow = if (s > 0f) rig.leftEye else rig.rightEye
        drop(brow + Offset(-s * (52f + 40f * fly), -40f - 24f * fly + 60f * fly * fly), 5.5f, MoPropWater.copy(alpha = 1f - fly))
    }
}

internal val scaredActs1: List<MoAct> = listOf(
    ScaredOneEyeAtATime,
    HoldingTimeLevel,
    TheShapeBehindHim,
    BlowingTheCloudBack,
    BehindASeedling,
    TheSwoon,
)
