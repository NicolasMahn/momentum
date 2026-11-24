package de.nicolasmahn.momentum.feature.mo.acts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import de.nicolasmahn.momentum.core.ui.theme.MoPropBlue
import de.nicolasmahn.momentum.core.ui.theme.MoPropBlueDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropPink
import de.nicolasmahn.momentum.core.ui.theme.MoPropPinkDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropShadow
import de.nicolasmahn.momentum.core.ui.theme.MoPropSteel
import de.nicolasmahn.momentum.core.ui.theme.MoPropTeal
import de.nicolasmahn.momentum.core.ui.theme.MoPropTealDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropYellow
import de.nicolasmahn.momentum.core.ui.theme.MoPropYellowDark
import de.nicolasmahn.momentum.core.ui.theme.MoStink
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
import de.nicolasmahn.momentum.feature.mo.MoMouth
import de.nicolasmahn.momentum.feature.mo.MoPose
import de.nicolasmahn.momentum.feature.mo.MoRig
import de.nicolasmahn.momentum.feature.mo.chunk
import de.nicolasmahn.momentum.feature.mo.curve
import de.nicolasmahn.momentum.feature.mo.cycle
import de.nicolasmahn.momentum.feature.mo.ease
import de.nicolasmahn.momentum.feature.mo.floorShadow
import de.nicolasmahn.momentum.feature.mo.limb
import de.nicolasmahn.momentum.feature.mo.moIdle
import de.nicolasmahn.momentum.feature.mo.oval
import de.nicolasmahn.momentum.feature.mo.puff
import de.nicolasmahn.momentum.feature.mo.wave
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

/*
 * A task with the teeth motif is neglected and it shows: his breath has turned green. These are the small and middling
 * ways he notices, hides it, or is let down by it. Every act runs on one master cycle cut into
 * phases, and each phase ends in the pose the next one starts from, so the loop has no seam.
 */

/** How far [x] has come from [from] to [to], held at 0 before and at 1 after. */
private fun span(x: Float, from: Float, to: Float): Float =
    if (to - from < 1e-4f) (if (x >= to) 1f else 0f) else ((x - from) / (to - from)).coerceIn(0f, 1f)

private fun mix(a: Float, b: Float, k: Float): Float = a + (b - a) * k

private fun mix(a: Offset, b: Offset, k: Float): Offset = Offset(mix(a.x, b.x, k), mix(a.y, b.y, k))

private fun mix(a: MoArm, b: MoArm, k: Float): MoArm = MoArm(mix(a.reach, b.reach, k), mix(a.bend, b.bend, k))

/** Half a sine over [x] from 0 to 1: up and back down once. */
private fun arc(x: Float): Float = sin(x.coerceIn(0f, 1f) * PI.toFloat())

/** Where an arm's hand ends up on [body], in the space `held` draws in. */
private fun handOf(body: MoBody, arm: MoArm, side: Float): Offset =
    Offset(CENTRE + side * (body.shoulderHalf + arm.reach.x), body.shoulderY + arm.reach.y)

/** A stink wisp: an S of two stacked strokes standing on [base]. [swing] is how far it bellies out. */
private fun DrawScope.wisp(base: Offset, height: Float, swing: Float, colour: Color, width: Float) {
    val middle = base + Offset(0f, -height / 2f)
    curve(base, base + Offset(swing, -height / 4f), middle, colour, width)
    curve(middle, middle + Offset(-swing, -height / 4f), base + Offset(0f, -height), colour, width)
}

/** He huffs into a cupped mitten, sniffs, and after a beat of nothing his own breath knocks him back. */
private object BreathCheck : MoAct("stinky_breath_check", MoOccasion.Stinky) {
    private const val HUFF = 0.12f
    private const val SNIFFED = 0.30f
    private const val SHAKE_OFF = 0.75f

    private fun phase(t: Float) = cycle(t, 5.6f, offset = 0.15f)

    private fun side(v: MoVariant) = if (v.chance(50, salt = 1)) 1f else -1f

    /** The end of the beat in which nothing happens yet. */
    private fun hit(v: MoVariant) = SNIFFED + v.between(0.10f, 0.16f, salt = 4)

    // Held out past his cheek: a mitten over his own flame is orange on orange and the puff on it
    // would read as a stain. Out here both stand against the background on the tall and the low body.
    // The reach is measured so that the puff still clears the edge of the view once he leans on it.
    private fun cupped(body: MoBody) = MoArm(Offset(34f, body.mouth.y - body.shoulderY + 5f), bend = 0.45f)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val p = phase(t)
        val s = side(v)
        val hit = hit(v)
        val recoil = ease(span(p, hit, hit + 0.05f)) * (1f - ease(span(p, SHAKE_OFF, 0.93f)))
        val checking = mix(cupped(MoBodies.of(body)), MoArm.Out, ease(span(p, hit, hit + 0.04f)) * (1f - ease(span(p, SHAKE_OFF + 0.03f, 0.97f))))
        val idle = moIdle(t, energy = 0.8f)
        val sniffs = arc(span(p, 0.15f, 0.21f)) + arc(span(p, 0.22f, 0.28f))

        val face = when {
            p < SNIFFED -> idle.eyes(MoEye(gaze = Offset(0.95f * s, 0.35f))).copy(
                brows = MoBrows(MoBrowKind.Raised, lift = 2f + 2f * sniffs),
                mouth = if (p < HUFF) MoMouth.Oh(size = 7f) else MoMouth.Flat(width = 8f),
            )
            p < hit -> idle.eyes(MoEye(iris = 0.5f)).copy(brows = MoBrows(MoBrowKind.Flat), mouth = MoMouth.Flat(width = 8f), blinks = false)
            p < SHAKE_OFF -> idle.eyes(if (v.chance(50, salt = 3)) MoEye(shape = MoEyeShape.Spiral) else MoEye(iris = 0.3f, scale = 1.05f)).copy(
                brows = MoBrows(MoBrowKind.Worried, lift = 5f),
                mouth = MoMouth.Wavy,
                blinks = false,
            )
            else -> idle.eyes(MoEye(gaze = Offset(0.9f * s, 0.25f), lidDrop = 0.3f * (1f - span(p, 0.94f, 1f)))).copy(
                brows = MoBrows(MoBrowKind.Flat),
                mouth = MoMouth.Pout,
            )
        }
        return face.copy(
            leftArm = if (s < 0f) checking else MoArm.Down,
            rightArm = if (s > 0f) checking else MoArm.Down,
            armsInFront = true,
            sway = idle.sway - 14f * s * recoil,
            // He is knocked flat for a moment and then holds a smaller slump.
            squash = idle.squash + 0.012f * sniffs - recoil * (0.02f + 0.03f * (1f - ease(span(p, hit + 0.04f, hit + 0.16f)))),
            tilt = mix(3f * s, -7f * s, recoil),
            offset = Offset(-8f * s * recoil, 0f),
            shake = if (p >= SHAKE_OFF) wave(t, 0.09f) * 2f * (1f - span(p, SHAKE_OFF, 1f)) else 0f,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val p = phase(t)
        val s = side(v)
        val hit = hit(v)
        val gone = span(p, hit, hit + 0.18f)
        val travel = ease(span(p, 0f, HUFF + 0.09f))
        val size = v.between(12f, 15f, salt = 2) * travel
        if (gone >= 1f || size < 0.5f) return
        val lip = rig.mouth + Offset(s * 9f, 2f)
        val palm = handOf(rig.body, cupped(rig.body), s) + Offset(0f, -20f - 12f * gone)
        val fade = 1f - ease(gone)
        for (i in 0..2) {
            val along = (travel - i * 0.28f).coerceAtLeast(0f)
            val at = mix(lip, palm, along) + Offset(0f, -5f * along * (1f - along))
            puff(at, size * (1f - 0.28f * i), MoStink.copy(alpha = 0.48f * fade * along))
        }
    }
}

/** Green wisps rise beside his face, and his own flame tip leans hard the other way to get clear. */
private object TipLeansAway : MoAct("stinky_tip_leans_away", MoOccasion.Stinky, intensity = MoIntensity.Mild) {
    // A quarter period ahead, so t = 0 is an extreme: wisps fully up, the tip as far away as it goes.
    private fun swing(t: Float) = wave(t + 1.75f, 7f)

    private fun startSide(v: MoVariant) = if (v.chance(50, salt = 1)) 1f else -1f

    /** Where the tip is, from -1 to 1 times the start side: it snaps across and rests at both ends. */
    private fun lean(t: Float) = -(2f * ease(0.5f + swing(t) * 1.6f) - 1f)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val idle = moIdle(t, energy = 0.7f)
        val lean = lean(t) * startSide(v)
        val hurry = 1f - abs(lean(t))
        val gaze = if (v.chance(70, salt = 4)) Offset(lean * 0.55f, -0.9f) else Offset(-lean * 0.8f, -0.3f)
        return idle.eyes(MoEye(gaze = gaze, iris = 0.7f)).copy(
            brows = MoBrows(MoBrowKind.Worried, lift = 3f + 3f * hurry),
            mouth = MoMouth.Pout,
            sway = idle.sway * 0.3f + lean * v.between(30f, 40f, salt = 3),
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val swing = swing(t)
        val side = startSide(v) * if (swing >= 0f) 1f else -1f
        // They are gone while the swing passes zero, so they change sides without crossing his face.
        val there = ease(abs(swing) * 1.5f)
        val count = v.pick(2, salt = 2) + 2
        for (index in 0 until count) {
            val rise = cycle(t, 2.6f, offset = index.toFloat() / count)
            // Three wisps 15 apart end at x = -31 or 261, which keeps the outermost S inside his view.
            val base = Offset(CENTRE + side * (116f + 15f * index), rig.body.shoulderY - 20f - rise * 110f)
            val alpha = ease(rise * 5f) * (1f - rise * rise) * there
            wisp(base, 62f, side * (13f + 3f * wave(t + index, 1.3f)), MoStink.copy(alpha = alpha), 9f)
        }
    }
}

/** He smiles stiffly at you while patting down the green wisps that keep popping up over his shoulders. */
private object WhackAWisp : MoAct("stinky_whack_a_wisp", MoOccasion.Stinky, intensity = MoIntensity.Mild) {
    private const val WISP_X = 24f
    private const val WISP_BASE = 5f

    // A mitten is 12.5 in radius and the wisp's round cap 4.5 more: pressed this low, nothing is left.
    private const val MITTEN = 17f
    private val rest = Offset(WISP_X, -62f)

    // Low at his side, so only the mitten with a wisp under it is ever up: two raised hands read as a surrender.
    private val waiting = Offset(30f, 26f)
    private val pressed = Offset(WISP_X, WISP_BASE - MITTEN)

    private fun beat(t: Float, v: MoVariant) = cycle(t, v.between(2.2f, 2.8f, salt = 2), offset = 0.1f)

    private fun firstSide(v: MoVariant) = if (v.chance(50, salt = 1)) 1f else -1f

    /** How far into its own half of the beat [side] is, or past 1 while the other side has its turn. */
    private fun turn(k: Float, side: Float, v: MoVariant): Float = if (side == firstSide(v)) k * 2f else (k * 2f + 1f).mod(2f)

    private fun stubborn(side: Float, v: MoVariant) = side != firstSide(v) && v.chance(30, salt = 3)

    /** The pat, from 0 (mitten up) to 1 (pressed flat). A stubborn wisp springs back and needs a second one. */
    private fun press(u: Float, twice: Boolean): Float =
        if (twice) 0.55f * ease(span(u, 0.72f, 0.80f)) - 0.35f * ease(span(u, 0.80f, 0.87f)) + 0.8f * ease(span(u, 0.87f, 0.96f))
        else ease(span(u, 0.80f, 0.92f))

    private fun reach(k: Float, side: Float, v: MoVariant): Offset {
        val u = turn(k, side, v)
        if (u > 1f) return mix(pressed, waiting, ease(span(u - 1f, 0f, 0.2f)))
        // The wisp pops up under the waiting mitten and lifts it, always a little ahead of its own top.
        val lifted = mix(waiting, rest, ease(span(u, 0f, 0.24f)))
        // It winds up in a little arc first, then comes straight down on it.
        val over = span(u, 0.56f, 0.72f)
        val above = Offset(mix(lifted.x, WISP_X, ease(over)), lifted.y - 8f * arc(over))
        return mix(above, pressed, press(u, stubborn(side, v)))
    }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val k = beat(t, v)
        val u = (k * 2f).mod(1f)
        val newSide = if (k < 0.5f) firstSide(v) else -firstSide(v)
        val dart = ease(span(u, 0.04f, 0.09f)) * (1f - ease(span(u, 0.28f, 0.36f)))
        val idle = moIdle(t)
        return idle.eyes(MoEye(gaze = Offset(0.95f * newSide * dart, -0.5f * dart))).copy(
            brows = MoBrows(MoBrowKind.Raised, lift = 3f + 2f * dart),
            mouth = MoMouth.Smile(width = 21f, depth = 8f),
            // Elbows tucked in along his sides, which leaves the space under each mitten to the wisp.
            leftArm = MoArm(reach(k, -1f, v), bend = -0.3f),
            rightArm = MoArm(reach(k, 1f, v), bend = -0.3f),
            squash = idle.squash - 0.015f * arc(span(u, 0.84f, 1f)),
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val k = beat(t, v)
        val full = v.between(46f, 52f, salt = 4)
        for (side in listOf(-1f, 1f)) {
            val u = turn(k, side, v)
            if (u > 1f) continue
            val hand = if (side < 0f) rig.leftHand else rig.rightHand
            val base = Offset(CENTRE + side * (rig.body.shoulderHalf - 1f), rig.body.shoulderY + WISP_BASE)
            val grown = base.y - full * ease(span(u, 0f, 0.24f))
            // The green only shows in the space below the mitten, including while it rises.
            val top = max(grown, hand.y + MITTEN)
            val height = (base.y - top).coerceIn(0f, full)
            val middle = base + Offset(side * 13f, -height * 0.48f)
            val end = base + Offset(side * WISP_X, -height)
            val green = MoStink.copy(alpha = ease(span(u, 0f, 0.12f)) * (1f - ease(span(u, 0.92f, 1f))))
            curve(base, base + Offset(side * 25f, -height * 0.22f), middle, green, 8f)
            curve(middle, middle + Offset(-side * 8f, -height * 0.3f), end, green, 8f)
            val pat = press(u, stubborn(side, v))
            if (pat > 0f) oval(end + Offset(side * 5f, 0f), 13f * pat, 5f * pat, green)
        }
    }
}

/** He sighs, and the sigh is a green puff so heavy that it drops to the floor and crawls away. */
private object HeavySigh : MoAct("stinky_heavy_sigh", MoOccasion.Stinky, intensity = MoIntensity.Mild) {
    private const val OUT = 0.12f
    private const val LANDED = 0.40f
    private const val SETTLED = 0.48f

    /** The life of puff [index], from 0 to 1. Two share the loop, half a life apart. */
    private fun life(t: Float, v: MoVariant, index: Int) = cycle(t, v.between(4.4f, 5.4f, salt = 4), offset = 0.3f + 0.5f * index)

    private fun direction(v: MoVariant) = if (v.chance(50, salt = 1)) 1f else -1f

    private fun watching(q: Float, d: Float) = Offset(0.9f * d * ease(span(q, OUT, OUT + 0.12f)), 0.3f + 0.7f * ease(span(q, OUT, LANDED)))

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val young = minOf(life(t, v, 0), life(t, v, 1))
        val sigh = arc(span(young, 0f, 0.2f))
        val breathingOut = young < OUT
        // His eyes close for the sigh, and under the closed lids they move on to the new puff.
        val gaze = watching(if (young >= 0.06f) young else young + 0.5f, direction(v))
        val idle = moIdle(t, energy = 0.6f)
        return idle.eyes(MoEye(gaze = gaze, lid = -0.3f, open = 1f - 0.92f * arc(span(young, 0f, 0.13f)))).copy(
            brows = MoBrows(MoBrowKind.Worried, lift = 2f + 3f * sigh),
            mouth = if (breathingOut) MoMouth.Oh(size = 3f + 5f * arc(span(young, 0f, OUT))) else MoMouth.Frown(width = 10f),
            squash = idle.squash - 0.03f * sigh,
            flare = -8f * sigh,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val full = v.between(21f, 25f, salt = 2)
        val d = direction(v)
        val bounces = v.chance(40, salt = 3)
        for (index in 0..1) {
            val q = life(t, v, index)
            val creep = span(q, SETTLED, 1f)
            val size = mix(3f, full, ease(span(q, 0f, OUT))) * mix(1f, 0.5f, creep)
            val flat = ease(span(q, LANDED - 0.015f, LANDED + 0.01f)) * mix(1f, 0.45f, ease(span(q, LANDED + 0.01f, SETTLED)))
            val wide = 1f + 0.4f * flat
            val low = 1f - 0.45f * flat
            val fall = span(q, OUT, LANDED).let { it * it }
            val hop = if (bounces) 8f * arc(span(q, LANDED + 0.01f, SETTLED)) else 0f
            // It slides out past his cheek before it drops, so the fall is against the background
            // and not a green smear over his belly, and it lands beside his feet where it shows.
            val out = ease(span(q, OUT, OUT + 0.09f))
            val at = Offset(
                mix(rig.mouth.x, CENTRE + d * 94f, out) + d * 40f * ease(creep),
                mix(rig.mouth.y + 24f, FEET - 2f - size * 0.8f * low, fall) - hop,
            )
            val alpha = 1f - creep * creep
            floorShadow(at.x, 24f * ease(span(q, 0.3f, 0.42f)) * size / full * wide, MoPropShadow.copy(alpha = MoPropShadow.alpha * alpha))
            val colour = MoStink.copy(alpha = alpha)
            oval(at + Offset(-size * 0.55f * wide, size * 0.12f * low), size * 0.62f * wide, size * 0.62f * low, colour)
            oval(at, size * 0.8f * wide, size * 0.8f * low, colour)
            oval(at + Offset(size * 0.6f * wide, size * 0.16f * low), size * 0.58f * wide, size * 0.58f * low, colour)
        }
    }

    override val ownsTheFloor get() = true
}

/** He dares a smile, a green wisp slips out of it, and his mittens clamp it shut again. */
private object HidingHisSmile : MoAct("stinky_hiding_his_smile", MoOccasion.Stinky) {
    private const val LOWERING = 0.12f
    private const val REALISING = 0.06f
    private const val CLAP = 0.10f

    private fun phase(t: Float) = cycle(t, 6f)

    private fun daring(v: MoVariant) = v.between(0.26f, 0.36f, salt = 4)

    /** How long he stays covered: what the other phases leave of the loop. */
    private fun covered(v: MoVariant) = 1f - LOWERING - REALISING - CLAP - daring(v)

    // One mitten leads the other to his mouth on some days.
    private fun cover(body: MoBody, side: Float) =
        MoArm(Offset((body.mouth.x - CENTRE) * side - body.shoulderHalf + 9f, body.mouth.y - body.shoulderY + 2f), bend = 0.83f)

    /** 1 while the mittens are up at his mouth, 0 while they hang. */
    private fun up(p: Float, v: MoVariant): Float =
        if (p > 1f - CLAP) ease(span(p, 1f - CLAP, 1f - CLAP + 0.06f)) else 1f - ease(span(p, covered(v), covered(v) + LOWERING))

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val p = phase(t)
        val covered = covered(v)
        val tryFrom = covered + LOWERING
        val tryTo = tryFrom + daring(v)
        val up = up(p, v)
        val shape = MoBodies.of(body)
        val leftUp = if (v.chance(75, salt = 1)) up else up * up
        val dart = ease(span(p, covered * 0.45f, covered * 0.5f)) * (1f - ease(span(p, covered * 0.72f, covered * 0.8f)))
        val side = if (v.chance(50, salt = 2)) 1f else -1f
        val trying = span(p, tryFrom, tryTo)
        val caught = p >= tryTo
        val idle = moIdle(t)

        val gaze = mix(Offset(0f, -0.3f), mix(Offset(0f, -0.5f), Offset(0.9f * side, 0.1f), dart), up)
        val mouth = when {
            up > 0.96f -> MoMouth.Hidden
            up > 0.6f -> MoMouth.Flat(width = 9f)
            p < tryFrom + 0.04f -> MoMouth.Flat(width = 10f)
            trying < 0.8f -> ease(span(trying, 0.12f, 0.8f)).let { MoMouth.Smile(width = mix(12f, 20f, it), depth = mix(6f, 13f, it)) }
            else -> MoMouth.Grin(open = 0.45f, tongue = false)
        }
        return idle.eyes(MoEye(gaze = gaze, iris = if (caught) 0.5f else 0.62f)).copy(
            brows = when {
                caught -> MoBrows(MoBrowKind.Worried, lift = 6f)
                p < tryFrom -> MoBrows(MoBrowKind.Worried, lift = mix(1f, 4f, up))
                else -> MoBrows(MoBrowKind.Raised, lift = 2f)
            },
            mouth = mouth,
            leftArm = mix(MoArm.Down, cover(shape, -1f), leftUp).copy(warm = leftUp),
            rightArm = mix(MoArm.Down, cover(shape, 1f), up).copy(warm = up),
            armsInFront = true,
            squash = idle.squash - 0.02f * up - 0.04f * arc(span(p, 1f - CLAP, 1f - CLAP + 0.08f)),
            blinks = !caught,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val p = phase(t)
        val covered = covered(v)
        // The leak starts as the mittens arrive and dries up before they come down.
        val leaking = if (p > 0.5f) ease(span(p, 0.95f, 1f)) else 1f - ease(span(p, covered - 0.08f, covered - 0.01f))
        val count = v.pick(2, salt = 3) + 1
        // Up the gap between his eyes: the one channel across his face that nothing of him occupies,
        // and wide enough for a stroke heavy enough to read as green on his yellow belly.
        for (index in 0 until count) {
            // Offset so the first wisp is already halfway up at t = 0, the frame the widget shows:
            // the leak is what makes this act Stinky, and a single one starting at nothing is none.
            val rise = cycle(t, 2f, offset = 0.3f + index * 0.5f)
            val drift = if (count == 1) 0f else (index - 0.5f) * 11f
            val alpha = ease(rise * 5f) * (1f - rise * rise) * leaking
            wisp(rig.mouth + Offset(drift, -16f - rise * 40f), 34f, 7f, MoStink.copy(alpha = alpha), 8.5f)
        }

        val slipFrom = covered + LOWERING + daring(v) * 0.8f
        val slip = span(p, slipFrom, 0.98f)
        if (slip > 0f && slip < 1f) {
            val alpha = ease(slip * 6f) * (1f - slip * slip)
            wisp(rig.mouth + Offset(3f, -8f - slip * 46f), 36f, 7f, MoStink.copy(alpha = alpha), 8.5f)
        }
    }
}

/** He has clipped a clothes peg where a nose would be; it slides off, he catches it and flips it back into place. */
private object PegWithNoNose : MoAct("stinky_peg_with_no_nose", MoOccasion.Stinky, setOf(MoBodyKind.Tall)) {
    private const val SLID = 0.55f
    private const val CAUGHT = 0.68f
    private const val LOOKED = 0.82f
    private const val CLIPPED = 0.90f
    private const val LETTING_GO = 0.95f
    private const val SLIDE = 18f

    private val waiting = MoArm(Offset(20f, -30f), bend = 0.6f)

    private fun phase(t: Float, v: MoVariant) = cycle(t, v.between(4.6f, 6f, salt = 3), offset = 0.08f)

    private fun catcher(v: MoVariant) = if (v.chance(50, salt = 2)) 1f else -1f

    // A short upward flick sends the peg back across the gap.
    private fun flick(p: Float) = ease(span(p, LOOKED - 0.04f, LOOKED)) * (1f - ease(span(p, CLIPPED, LETTING_GO)))

    /** High in the gap between his eyes, so that even slid down it ends well above his mouth. */
    private fun home(rig: MoRig) = (rig.leftEye + rig.rightEye) / 2f + Offset(0f, -12f)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val p = phase(t, v)
        val c = catcher(v)
        // His eyes and brows ride back up with the peg while he clips it on, so the loop closes on
        // the look it opened with instead of snapping back from the bottom of the slide.
        val slide = span(p, 0f, SLID).let { it * it } * (1f - ease(span(p, LOOKED, CLIPPED)))
        val crossed = if (p < SLID) 1f else ease(span(p, LOOKED + 0.03f, CLIPPED))
        val aside = Offset(0.9f * c, 0.15f)
        val dip = MoArm(waiting.reach + Offset(0f, 6f * arc(span(p, CAUGHT, CAUGHT + 0.07f))), waiting.bend)
        val arm = dip.copy(reach = dip.reach + Offset(0f, -18f * flick(p)))
        val idle = moIdle(t, energy = 0.8f)

        fun eye(side: Float) = MoEye(
            gaze = mix(aside, Offset(-0.9f * side, -0.2f + 0.7f * slide), crossed),
            iris = if (p >= SLID && p < CAUGHT) 0.5f else 0.6f,
            lidDrop = 0.25f * ease(span(p, CAUGHT, CAUGHT + 0.03f)) * (1f - ease(span(p, LOOKED, LOOKED + 0.04f))),
        )
        return idle.copy(
            leftEye = eye(-1f),
            rightEye = eye(1f),
            brows = if (p >= CAUGHT && p < LOOKED) MoBrows(MoBrowKind.Flat) else MoBrows(MoBrowKind.Raised, lift = 4f - 3f * slide),
            mouth = when {
                p < SLID -> MoMouth.Smile(width = 12f, depth = 8f)
                p < CAUGHT -> MoMouth.Oh(size = 6f)
                p < CLIPPED -> MoMouth.Pout
                else -> MoMouth.Smile(width = 12f, depth = 8f)
            },
            leftArm = if (c < 0f) arm else MoArm.Down,
            rightArm = if (c > 0f) arm else MoArm.Down,
            armsInFront = true,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val p = phase(t, v)
        val c = catcher(v)
        val home = home(rig)
        val slid = home + Offset(0f, SLIDE)
        // Lying on its side on the mitten: the mitten's radius plus half the peg's width.
        val onMitten = handOf(rig.body, waiting, c) + Offset(0f, -22f)
        val colours = v.of(listOf(MoPropYellow to MoPropYellowDark, MoPropPink to MoPropPinkDark, MoPropTeal to MoPropTealDark, MoPropBlue to MoPropBlueDark), salt = 1)

        val (at, turn) = when {
            p < SLID -> {
                val slide = span(p, 0f, SLID).let { it * it }
                (home + Offset(0f, SLIDE * slide)) to 2f * wave(t, 0.9f) * (0.3f + slide)
            }
            p < CAUGHT -> {
                val fly = span(p, SLID, CAUGHT)
                // One and a quarter turns, so it lands lying down.
                (mix(slid, onMitten, fly) + Offset(0f, -46f * arc(fly))) to c * 450f * fly
            }
            else -> {
                val settle = span(p, CAUGHT, CAUGHT + 0.07f)
                val hop = if (v.chance(35, salt = 4)) 10f * arc(span(p, CAUGHT + 0.02f, CAUGHT + 0.10f)) else 0f
                val back = ease(span(p, LOOKED, CLIPPED))
                val launch = onMitten + Offset(0f, 6f * arc(settle) - hop - 18f * flick(p))
                mix(launch, home, back) - Offset(0f, 64f * arc(back)) to c * 90f * (1f - back)
            }
        }
        withTransform({ rotate(turn, at) }) {
            // One body with the jaws parting at the bottom and the wire across its waist. Drawn as
            // two separate bars, at the size he wears it, it stops being a peg and becomes a mark
            // on his face; the solid chunk with a slit is the peg everyone draws.
            chunk(at, 22f, 34f, colours.first, colours.second, corner = 5f, drop = 4f)
            limb(at + Offset(0f, 15f), at + Offset(0f, 3f), colours.second, 3.5f)
            oval(at + Offset(0f, 1f), 13f, 3.5f, MoPropSteel)
        }
    }
}

internal val stinkyActs1: List<MoAct> = listOf(
    BreathCheck,
    TipLeansAway,
    WhackAWisp,
    HeavySigh,
    HidingHisSmile,
    PegWithNoNose,
)
