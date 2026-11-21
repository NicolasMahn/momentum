package com.example.workouttracker.feature.mo.acts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import com.example.workouttracker.core.ui.theme.MoBubble
import com.example.workouttracker.core.ui.theme.MoFlameBelly
import com.example.workouttracker.core.ui.theme.MoFlameMiddle
import com.example.workouttracker.core.ui.theme.MoFlameOuter
import com.example.workouttracker.core.ui.theme.MoPropGold
import com.example.workouttracker.core.ui.theme.MoPropGrey
import com.example.workouttracker.core.ui.theme.MoPropGreyDark
import com.example.workouttracker.core.ui.theme.MoPropInk
import com.example.workouttracker.core.ui.theme.MoPropLeaf
import com.example.workouttracker.core.ui.theme.MoPropMoon
import com.example.workouttracker.core.ui.theme.MoPropPink
import com.example.workouttracker.core.ui.theme.MoPropPinkDark
import com.example.workouttracker.core.ui.theme.MoPropPurple
import com.example.workouttracker.core.ui.theme.MoPropPurpleDark
import com.example.workouttracker.core.ui.theme.MoPropRed
import com.example.workouttracker.core.ui.theme.MoPropRedDark
import com.example.workouttracker.core.ui.theme.MoPropSand
import com.example.workouttracker.core.ui.theme.MoPropShadow
import com.example.workouttracker.core.ui.theme.MoPropSky
import com.example.workouttracker.core.ui.theme.MoPropSnow
import com.example.workouttracker.core.ui.theme.MoPropTeal
import com.example.workouttracker.core.ui.theme.MoPropTealDark
import com.example.workouttracker.core.ui.theme.MoPropWater
import com.example.workouttracker.core.ui.theme.MoPropWood
import com.example.workouttracker.core.ui.theme.MoPropWoodDark
import com.example.workouttracker.core.ui.theme.MoPropYellow
import com.example.workouttracker.core.ui.theme.MoSmoke
import com.example.workouttracker.domain.mo.MoIntensity
import com.example.workouttracker.domain.mo.MoOccasion
import com.example.workouttracker.feature.mo.CENTRE
import com.example.workouttracker.feature.mo.MoArm
import com.example.workouttracker.feature.mo.MoBodies
import com.example.workouttracker.feature.mo.MoBodyKind
import com.example.workouttracker.feature.mo.MoBrowKind
import com.example.workouttracker.feature.mo.MoBrows
import com.example.workouttracker.feature.mo.MoEye
import com.example.workouttracker.feature.mo.MoEyeShape
import com.example.workouttracker.feature.mo.MoMouth
import com.example.workouttracker.feature.mo.MoPose
import com.example.workouttracker.feature.mo.MoRig
import com.example.workouttracker.feature.mo.block
import com.example.workouttracker.feature.mo.chunk
import com.example.workouttracker.feature.mo.cycle
import com.example.workouttracker.feature.mo.drop
import com.example.workouttracker.feature.mo.ease
import com.example.workouttracker.feature.mo.flameTongue
import com.example.workouttracker.feature.mo.floorShadow
import com.example.workouttracker.feature.mo.limb
import com.example.workouttracker.feature.mo.moIdle
import com.example.workouttracker.feature.mo.oval
import com.example.workouttracker.feature.mo.puff
import com.example.workouttracker.feature.mo.pulse
import com.example.workouttracker.feature.mo.shape
import com.example.workouttracker.feature.mo.smoke
import com.example.workouttracker.feature.mo.wave
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/*
 * Cold, part one. He is a small slate drop hugging the last of his fire; everything here is quiet,
 * slow and sad, and the only warmth in any picture is something he cannot have.
 */

private val droplet = setOf(MoBodyKind.Droplet)

/** Both mittens round the core, the pose he always comes back to. */
internal val coldHug = MoArm(Offset(-62f, 38f), bend = -0.55f)

/** Eyes up at the viewer, the plea of every Cold act. */
private val atViewer = Offset(0f, -0.7f)

/**
 * His shiver, as a share of the one in `cold_hugging_the_core`. The kit already shakes every Cold
 * Mo once by itself, so an amount of 0 shakes him the other way and holds him truly still: the
 * moment of rest these acts are about.
 */
private fun shiver(t: Float, amount: Float): Float = wave(t, 0.11f) * 1.6f * (2f * amount - 1f)

/** The thin wisp off his flopped tip that every Cold act keeps. */
private fun DrawScope.tipSmoke(t: Float, rig: MoRig) = smoke(rig.tip + Offset(34f, 8f), cycle(t, 3.8f))

/** Where [p] is between [from] and [to], from 0 to 1, clamped. */
private fun along(p: Float, from: Float, to: Float): Float = ((p - from) / max(to - from, 1e-4f)).coerceIn(0f, 1f)

/** The same, eased. */
private fun span(p: Float, from: Float, to: Float): Float = ease(along(p, from, to))

/** Up and down again once while [p] crosses [from]..[to]. */
private fun bump(p: Float, from: Float, to: Float): Float = sin(along(p, from, to) * PI.toFloat())

private fun mix(from: Float, to: Float, amount: Float): Float = from + (to - from) * amount

private fun mix(from: Offset, to: Offset, amount: Float): Offset = from + (to - from) * amount

private fun mix(from: MoArm, to: MoArm, amount: Float): MoArm = MoArm(mix(from.reach, to.reach, amount), mix(from.bend, to.bend, amount))

/** He warms a mitten on the core and presses it under one eye; for a breath the shiver stops. */
private object WarmMittenOnTheCheek : MoAct("cold_warm_mitten_on_the_cheek", MoOccasion.Cold, droplet, MoIntensity.Mild) {
    // Under the eye, not on it: the Cheek preset would cover the eye on this small body.
    private val cheek = MoArm(Offset(-25f, 2f), bend = 0.7f)

    /** Two halves of one 9.6 s loop, a hand each; the swap falls exactly where the half wraps. */
    private fun half(t: Float) = cycle(t, 9.6f, offset = 0.25f)

    private fun u(t: Float) = (half(t) * 2f).mod(1f)

    private fun side(t: Float, v: MoVariant): Float {
        val first = if (v.chance(50, salt = 1)) -1f else 1f
        return if (half(t) < 0.5f) first else -first
    }

    private fun relief(u: Float) = span(u, 0.42f, 0.48f) * (1f - span(u, 0.62f, 0.70f))

    private fun onCheek(u: Float) = span(u, 0.30f, 0.42f) - span(u, 0.80f, 1f)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val u = u(t)
        val side = side(t, v)
        val relief = relief(u)
        val rested = relief > 0.5f
        val active = mix(coldHug, cheek, onCheek(u))
        val farTearEscapes = v.chance(30, salt = 4)
        fun eye(tear: Float, underMitten: Boolean) = MoEye(
            shape = if (rested) MoEyeShape.Shut else MoEyeShape.Round,
            gaze = atViewer,
            lid = -0.3f,
            // A tear runs from the eye's lower outer edge, straight into a mitten pressed there.
            tear = tear.takeUnless { rested && (underMitten || !farTearEscapes) },
        )
        return moIdle(t, energy = 0f).copy(
            leftEye = eye(cycle(t, 3.4f), underMitten = side < 0f),
            rightEye = eye(cycle(t, 3.4f, offset = 0.5f), underMitten = side > 0f),
            brows = MoBrows(MoBrowKind.Worried, lift = 3f * relief),
            mouth = if (rested) MoMouth.Flat(width = 8f) else MoMouth.Frown(),
            leftArm = if (side < 0f) active else coldHug,
            rightArm = if (side > 0f) active else coldHug,
            armsInFront = true,
            tilt = side * v.between(2f, 5f, salt = 3) * relief,
            squash = -0.02f * relief,
            shake = shiver(t, 1f - relief),
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val u = u(t)
        val hand = if (side(t, v) < 0f) rig.leftHand else rig.rightHand
        // Warmth soaks in while the mitten sits on the core and drains away again on his cheek.
        val warmth = span(u, 0.05f, 0.30f) * (1f - span(u, 0.50f, 0.68f))
        val warm = v.of(listOf(MoFlameMiddle, MoPropYellow), salt = 2)
        // Heat rises from the core across the pad of the mitten.
        val radius = 12.5f
        if (warmth > 0f) {
            clipRect(left = hand.x - radius, top = hand.y + radius * (1f - 2f * warmth),
                right = hand.x + radius, bottom = hand.y + radius) {
                drawCircle(warm, radius, hand)
            }
        }
        tipSmoke(t, rig)
    }
}

/** One snowflake wobbles down toward his fire; he hunches over it and the flake melts on a mitten. */
private object OneSnowflake : MoAct("cold_one_snowflake", MoOccasion.Cold, droplet) {
    private val tight = MoArm(Offset(-68f, 34f), bend = -0.7f)

    /** Two 6 s flakes in one 12 s loop, one from each side; the side swaps where a flake wraps. */
    private fun half(t: Float) = cycle(t, 12f, offset = 0.2f)

    private fun f(t: Float) = (half(t) * 2f).mod(1f)

    private fun side(t: Float, v: MoVariant): Float {
        val first = if (v.chance(50, salt = 1)) -1f else 1f
        return if (half(t) < 0.5f) first else -first
    }

    /** Where it lands: the top of the mitten on its side, once he has tightened the hug. */
    private fun landing(side: Float) = Offset(CENTRE + side * 14f, 358f)

    private fun flake(f: Float, side: Float, v: MoVariant): Offset {
        val q = ease(f / 0.72f)
        val q2 = q * q
        // The eighth power keeps it wide of his eyes until it is below them, then it curls in.
        val curl = q2 * q2 * q2 * q2
        val wobble = v.between(6f, 14f, salt = 4)
        return Offset(
            CENTRE + side * (105f - 91f * curl) + sin(f * 9f) * wobble * (1f - q),
            20f + 338f * q,
        )
    }

    private fun hunch(f: Float) = span(f, 0.5f, 0.7f) * (1f - span(f, 0.82f, 0.97f))

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val f = f(t)
        val side = side(t, v)
        val q = ease(f / 0.72f)
        val hunch = hunch(f)
        val at = if (f < 0.72f) flake(f, side, v) else landing(side)
        // On the viewer at both ends of the flake, so the eyes never snap where one flake hands over to the next.
        val look = max(span(f, 0.78f, 0.90f), 1f - span(f, 0f, 0.08f))
        fun eye(eyeAt: Offset, tear: Float): MoEye {
            val toFlake = (at - eyeAt) / 80f
            val onFlake = Offset(toFlake.x.coerceIn(-1f, 1f), toFlake.y.coerceIn(-1f, 1f))
            return MoEye(gaze = mix(onFlake, atViewer, look), iris = mix(0.76f, 0.55f, q * (1f - look)), lid = -0.2f, tear = tear)
        }
        val breath = bump(f, 0.79f, 0.92f)
        val shape = MoBodies.of(body)
        return moIdle(t, energy = 0f).copy(
            leftEye = eye(Offset(shape.leftEyeX, shape.eyeY), cycle(t, 3.4f)),
            rightEye = eye(Offset(shape.rightEyeX, shape.eyeY), cycle(t, 3.4f, offset = 0.5f)),
            brows = MoBrows(MoBrowKind.Worried, lift = 6f * q * (1f - look)),
            mouth = if (breath > 0.1f) MoMouth.Oh(size = 3f + 2.5f * breath) else MoMouth.Frown(),
            leftArm = mix(coldHug, tight, hunch),
            rightArm = mix(coldHug, tight, hunch),
            armsInFront = true,
            squash = -0.04f * hunch,
            tilt = -side * 2f * hunch,
            shake = shiver(t, 1f - 0.6f * hunch),
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val f = f(t)
        val side = side(t, v)
        if (f in 0.74f..0.92f) {
            val p = along(f, 0.74f, 0.92f)
            val hand = if (side < 0f) rig.leftHand else rig.rightHand
            drop(hand + Offset(side * 2f, -12f + 26f * p), 4f, MoPropWater.copy(alpha = 1f - p * p))
        }
        tipSmoke(t, rig)
    }

    override fun DrawScope.foreground(t: Float, v: MoVariant, picture: Rect) {
        val f = f(t)
        val side = side(t, v)
        val colour = v.of(listOf(MoPropSky, MoPropSnow, MoPropMoon), salt = 2)
        val radius = 16f * v.between(0.85f, 1.2f, salt = 3)
        if (f < 0.78f) {
            // It joins the part of the fall he follows with his eyes while it is still high overhead.
            val approach = 1f - span(f, 0f, 0.36f)
            val at = flake(f, side, v) + Offset(0f, (picture.top - radius - 20f) * approach)
            snowflake(at, radius * (1f - along(f, 0.72f, 0.78f)), colour)
        }

    }

    private fun DrawScope.snowflake(at: Offset, radius: Float, colour: Color) {
        if (radius <= 0f) return
        for (arm in 0 until 6) {
            rotate(arm * 60f, at) {
                limb(at, at + Offset(0f, -radius), colour, radius * 0.16f)
                val fork = at + Offset(0f, -radius * 0.48f)
                for (side in listOf(-1f, 1f)) {
                    limb(fork, fork + Offset(side * radius * 0.32f, -radius * 0.24f), colour, radius * 0.13f)
                }
            }
        }
    }
}

/** A tickle builds into a tiny sneeze that blows his wisp away; he checks the core in a fright. */
private object SneezeScare : MoAct("cold_sneeze_scare", MoOccasion.Cold, droplet) {
    private const val SNEEZE_O = 7f

    // Beside the core rather than above it, so the open mouth stays clear of the mittens.
    private val lifted = MoArm(Offset(-44f, 26f), bend = -0.4f)
    private val clamp = MoArm(Offset(-64f, 36f), bend = -0.6f)

    private fun period(v: MoVariant) = v.between(6.4f, 8f, salt = 1)

    // The offset opens on the top of the build, so the first frame is not the plain hug.
    private fun s(t: Float, v: MoVariant) = cycle(t, period(v), offset = 0.74f)

    private fun dir(v: MoVariant) = if (v.chance(50, salt = 2)) -1f else 1f

    private fun sneeze(s: Float, v: MoVariant): Float =
        bump(s, 0.80f, 0.86f) + if (v.chance(30, salt = 4)) 0.5f * bump(s, 0.88f, 0.92f) else 0f

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val s = s(t, v)
        val build = span(s, 0.55f, 0.80f)
        val stretch = build * (1f - span(s, 0.80f, 0.84f))
        val k = sneeze(s, v)
        val liftedBy = build * (1f - span(s, 0.86f, 0.89f))
        val clampedBy = span(s, 0.86f, 0.89f) * (1f - span(s, 0.94f, 1f))
        val arm = MoArm(
            coldHug.reach + (lifted.reach - coldHug.reach) * liftedBy + (clamp.reach - coldHug.reach) * clampedBy,
            coldHug.bend + (lifted.bend - coldHug.bend) * liftedBy + (clamp.bend - coldHug.bend) * clampedBy,
        )
        val calm = span(s, 0.93f, 0.98f)
        val up = build * (1f - calm)
        val eye = when {
            k > 0.05f -> MoEye(shape = MoEyeShape.Shut)
            s < 0.55f -> MoEye(gaze = atViewer)
            s < 0.80f -> MoEye(gaze = mix(atViewer, Offset(0f, -0.9f), build), open = 1f - 0.55f * build, lid = -0.2f * build)
            s < 0.93f -> MoEye(iris = 0.5f, scale = 1.05f, gaze = Offset(0f, 0.9f))
            else -> MoEye(iris = mix(0.5f, 0.76f, calm), scale = mix(1.05f, 1f, calm), gaze = mix(Offset(0f, 0.9f), atViewer, calm))
        }
        val afterSneeze = (s + 0.07f).mod(1f)
        return moIdle(t, energy = 0f).copy(
            leftEye = eye.copy(tear = along(afterSneeze, 0f, 0.40f)),
            rightEye = eye.copy(tear = along(afterSneeze, 0.21f, 0.62f)),
            brows = if (up > 0.5f) MoBrows(MoBrowKind.Raised, lift = 8f * up) else MoBrows(MoBrowKind.Worried, lift = 8f * up),
            mouth = when {
                s < 0.55f || s >= 0.93f -> MoMouth.Frown()
                // The Droplet's mouth sits right over the core, so even a big O stays modest:
                // any larger and it would join the top of the core at its brightest.
                s < 0.86f -> MoMouth.Oh(size = (4f + 5f * stretch + 3f * k).coerceAtMost(SNEEZE_O))
                else -> MoMouth.Oh(size = (5f + 4f * k).coerceAtMost(SNEEZE_O))
            },
            leftArm = arm,
            rightArm = arm,
            armsInFront = true,
            sway = dir(v) * 22f * k,
            flare = 6f * stretch,
            squash = 0.05f * stretch - 0.10f * k - 0.03f * bump(s, 0.93f, 0.97f),
            shake = shiver(t, 1f - 0.7f * stretch) + wave(t, 0.05f) * 2.5f * k,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val s = s(t, v)
        val dir = dir(v)
        val base = rig.tip + Offset(34f, 8f)
        if (s < 0.80f) {
            val wisp = span(s, 0f, 0.03f) * (1f - span(s, 0.77f, 0.80f))
            smoke(base, cycle(t, period(v) / 2f, offset = 0.48f), strength = wisp)
        } else {
            val r = along(s, 0.80f, 1f)
            puff(base + Offset(dir * 30f * r, -12f - 50f * r), (12f + 6f * r) * ease(r / 0.1f), MoSmoke.copy(alpha = 1f - r * r))
            val flecks = 3 + v.pick(2, salt = 3)
            for (index in 0 until flecks) {
                // Each fleck has its own speed and arc, so they never fall in a row.
                val fall = along(s, 0.80f, 0.90f + 0.04f * index)
                val at = rig.tip + Offset(dir * (8f + (30f + 14f * index) * fall), -6f - (14f + 12f * index) * sin(fall * PI.toFloat()) + 40f * fall * fall)
                oval(at, 3f, 2.2f, MoPropGreyDark.copy(alpha = ease(fall / 0.1f) * (1f - fall)))
            }
        }
    }
}

/** A thought puff floats above him with a small bright fire in it; he gazes up at it, still. */
private object DreamingOfFire : MoAct("cold_dreaming_of_fire", MoOccasion.Cold, droplet, MoIntensity.Mild) {
    private const val PUFF_X = 24f

    private fun d(t: Float) = cycle(t, 8f, offset = 0.3f)

    /** The shiver that runs through him and thins the dream, once a loop. */
    private fun fade(d: Float) = span(d, 0.70f, 0.78f) * (1f - span(d, 0.88f, 0.98f))

    private fun height(t: Float, v: MoVariant) = v.between(104f, 128f, salt = 3) + 3f * wave(t, 5f)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val d = d(t)
        val fade = fade(d)
        val eye = MoEye(gaze = mix(Offset(-0.8f, -0.9f), atViewer, fade), lid = -0.45f, lidDrop = 0.15f * (1f - fade))
        return moIdle(t, energy = 0f).copy(
            leftEye = eye.copy(tear = if (d in 0.72f..0.98f) along(d, 0.72f, 0.98f) else null),
            rightEye = eye,
            brows = MoBrows(MoBrowKind.Worried, lift = 2f),
            mouth = MoMouth.Frown(width = 7f + 4f * fade),
            leftArm = coldHug,
            rightArm = coldHug,
            armsInFront = true,
            squash = -0.03f * pulse(t, 4f),
            tilt = -2f,
            shake = shiver(t, fade),
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val d = d(t)
        val fade = fade(d)
        val scale = 1f - 0.45f * fade
        val h = height(t, v)
        // Opaque, or the three circles of the puff would show through one another.
        val cloud = v.of(listOf(MoBubble.copy(alpha = 1f), MoPropMoon, MoPropSnow), salt = 2)
        drawCircle(cloud, 5.5f * scale, Offset(62f, h + 88f))
        drawCircle(cloud, 9f * scale, Offset(44f, h + 64f))
        puff(Offset(PUFF_X, h), 46f * scale, cloud)
        val dream = scale * (1f - 0.5f * fade)
        when (v.pick(3, salt = 1)) {
            0 -> flameTongue(Offset(PUFF_X + wave(t, 0.47f) * 2f, h + 22f * dream), 16f * dream * (1f + 0.1f * wave(t, 0.31f)), MoFlameOuter, MoFlameBelly)
            1 -> sun(Offset(PUFF_X, h), dream, t)
            else -> mug(Offset(PUFF_X, h), dream, t)
        }
    }

    private fun DrawScope.sun(at: Offset, size: Float, t: Float) {
        val turn = cycle(t, 18f) * 2f * PI.toFloat()
        for (index in 0..5) {
            val angle = turn + index * PI.toFloat() / 3f
            val ray = Offset(cos(angle), sin(angle))
            limb(at + ray * (19f * size), at + ray * (27f * size), MoPropGold, 4.5f * size)
        }
        drawCircle(MoPropYellow, 15f * size, at)
    }

    private fun DrawScope.mug(at: Offset, size: Float, t: Float) {
        limb(at + Offset(14f * size, 0f), at + Offset(22f * size, 0f), MoPropRedDark, 8f * size)
        chunk(at + Offset(0f, 3f * size), 28f * size, 24f * size, MoPropRed, MoPropRedDark, corner = 6f * size)
        for (index in 0..1) {
            val rise = cycle(t, 1.6f, offset = index * 0.5f)
            puff(at + Offset((index - 0.5f) * 9f * size, (-16f - 16f * rise) * size), (3.5f + 2f * rise) * size, MoSmoke.copy(alpha = 1f - rise))
        }
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) = tipSmoke(t, rig)
}

/** A campfire is laid beside him and nothing burns in it; he holds a mitten out over it anyway. */
private object UnlitCampfire : MoAct("cold_unlit_campfire", MoOccasion.Cold, droplet) {
    private fun side(v: MoVariant) = if (v.chance(50, salt = 1)) -1f else 1f

    /** The fire's centre: far enough out that the stone ring stays inside the box on either side. */
    private fun fireX(v: MoVariant) = CENTRE + side(v) * 118f

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val side = side(v)
        val g = cycle(t, 6.4f)
        val hold = v.between(0.30f, 0.45f, salt = 5)
        val onLogs = span(g, hold, hold + 0.15f) * (1f - span(g, 0.85f, 1f))
        val eye = MoEye(gaze = mix(atViewer, Offset(side * 0.9f, 0.6f), onLogs), lid = -0.3f)
        val reach = MoArm(Offset(36f + wave(t, 0.13f) * 1.5f, 6f + wave(t, 0.17f)), bend = 0.2f)
        return moIdle(t, energy = 0f).copy(
            leftEye = if (side < 0f) eye.copy(tear = cycle(t, 3.2f)) else eye,
            rightEye = if (side > 0f) eye.copy(tear = cycle(t, 3.2f)) else eye,
            brows = MoBrows(MoBrowKind.Worried, lift = 4f * (1f - onLogs)),
            mouth = MoMouth.Frown(),
            leftArm = if (side < 0f) reach else coldHug,
            rightArm = if (side > 0f) reach else coldHug,
            armsInFront = true,
            tilt = side * 4f,
            offset = Offset(side * 4f, 0f),
            shake = shiver(t, 1f),
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val cx = fireX(v)
        floorShadow(cx, 38f, MoPropShadow)
        val logs = listOf(Triple(-28f, -6f, 372f), Triple(28f, 7f, 369f), Triple(2f, 0f, 364f), Triple(-12f, 13f, 378f))
        for ((foot, apex, apexY) in logs.take(3 + v.pick(2, salt = 2))) {
            val from = Offset(cx + foot, 420f)
            val to = Offset(cx + apex, apexY)
            limb(from + Offset(0f, 3f), to + Offset(0f, 3f), MoPropWoodDark, 13f)
            limb(from, to, MoPropWood, 13f)
            drawCircle(MoPropSand, 5.5f, to)
        }
        if (v.chance(40, salt = 4)) {
            // Never in a fire colour: an orange fleck among the logs would read as an ember.
            val leaf = Offset(cx + 6f, 386f)
            shape(MoPropLeaf, leaf.x to leaf.y - 6f, leaf.x + 6f to leaf.y, leaf.x to leaf.y + 4f, leaf.x - 7f to leaf.y)
        }
        for ((dx, dy) in listOf(-8f to 418f, 10f to 419f, 1f to 416f)) oval(Offset(cx + dx, dy), 3f, 2f, MoPropGreyDark)
        val stones = 5 + v.pick(2, salt = 3)
        for (index in 0 until stones) {
            val x = cx - 32f + 64f * index / (stones - 1)
            oval(Offset(x, 420f), 8f, 5.5f, MoPropGreyDark)
            oval(Offset(x, 418f), 8f, 5f, MoPropGrey)
        }
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) = tipSmoke(t, rig)

    override val ownsTheFloor get() = true
}

/** A snail with a lit window in its shell crawls past his feet, and his eyes follow the warm light. */
private object SnailWithALitWindow : MoAct("cold_snail_with_a_lit_window", MoOccasion.Cold, droplet, MoIntensity.Mild) {
    private fun c(t: Float) = cycle(t, 24f, offset = 0.33f)

    private fun dir(v: MoVariant) = if (v.chance(50, salt = 1)) 1f else -1f

    private fun watch(c: Float) = span(c, 0.10f, 0.125f) * (1f - span(c, 0.72f, 0.745f))

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val c = c(t)
        val watch = watch(c)
        val across = dir(v) * (2f * c - 1f)
        val eye = MoEye(gaze = mix(atViewer, Offset(across, 0.9f), watch), lid = -0.3f)
        return moIdle(t, energy = 0f).copy(
            leftEye = eye.copy(tear = cycle(t, 3.4f)),
            rightEye = eye.copy(tear = cycle(t, 3.4f, offset = 0.5f)),
            brows = MoBrows(MoBrowKind.Worried, lift = 3f * watch),
            mouth = MoMouth.Frown(),
            leftArm = coldHug,
            rightArm = coldHug,
            armsInFront = true,
            tilt = across * 2f * watch,
            shake = shiver(t, 1f),
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) = tipSmoke(t, rig)

    override fun DrawScope.foreground(t: Float, v: MoVariant, picture: Rect) =
        clipRect(picture.left, picture.top, picture.right, picture.bottom) {
            val c = c(t)
            val dir = dir(v)
            val along = if (dir > 0f) c else 1f - c
            val x = mix(picture.left - 42f, picture.right + 42f, along)
            val (shell, shellDark) = v.of(
                listOf(MoPropPurple to MoPropPurpleDark, MoPropTeal to MoPropTealDark, MoPropPink to MoPropPinkDark, MoPropRed to MoPropRedDark),
                salt = 2,
            )
            floorShadow(x, 30f, MoPropShadow)
            // The head reaches out first and the tail gathers after it, which is how a snail moves.
            val head = x + dir * (22f + 5f * pulse(t, 1.6f))
            val tail = x - dir * (26f - 5f * pulse(t - 0.4f, 1.6f))
            limb(Offset(tail, 419f), Offset(head, 419f), MoPropSand, 11f)
            drawCircle(MoPropSand, 7f, Offset(head, 414f))
            val nod = wave(t, 1.6f) * 2f
            for ((dx, tipX) in listOf(-1f to 2f, 3f to 7f)) {
                val root = Offset(head + dir * dx, 410f)
                val tip = Offset(head + dir * tipX + nod, 400f)
                limb(root, tip, MoPropSand, 3f)
                drawCircle(MoPropInk, 2.6f, tip)
            }
            val shellAt = Offset(x - dir * 4f, 402f)
            drawCircle(shellDark, 17f, shellAt + Offset(0f, 3f))
            drawCircle(shell, 17f, shellAt)
            if (v.chance(35, salt = 3)) {
                block(shellAt + Offset(-5.5f, 0f), 7f, 8f, MoPropYellow, corner = 2f)
                block(shellAt + Offset(5.5f, 0f), 7f, 8f, MoPropYellow, corner = 2f)
            } else {
                block(shellAt, 10f, 10f, MoPropYellow, corner = 3f)
            }
            val chimney = Offset(x - dir * 12f, 388f)
            block(chimney, 6f, 9f, shellDark, corner = 2f)
            if (v.chance(75, salt = 4)) {
                for (index in 0..1) {
                    val rise = cycle(t, 1.6f, offset = index * 0.5f)
                    puff(chimney + Offset(-dir * 3f * rise, -7f - 18f * rise), 4f + 3f * rise, MoSmoke.copy(alpha = 0.7f * (1f - rise)))
                }
            }
        }

    override val ownsTheFloor get() = true
}

internal val coldActs1: List<MoAct> = listOf(
    WarmMittenOnTheCheek,
    OneSnowflake,
    SneezeScare,
    DreamingOfFire,
    UnlitCampfire,
    SnailWithALitWindow,
)
