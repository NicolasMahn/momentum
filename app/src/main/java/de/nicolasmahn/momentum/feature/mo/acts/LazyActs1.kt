package de.nicolasmahn.momentum.feature.mo.acts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import de.nicolasmahn.momentum.feature.mo.curve
import androidx.compose.ui.graphics.drawscope.DrawScope
import de.nicolasmahn.momentum.core.ui.theme.MoPropBlue
import de.nicolasmahn.momentum.core.ui.theme.MoPropBlueDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropCharcoal
import de.nicolasmahn.momentum.core.ui.theme.MoPropCream
import de.nicolasmahn.momentum.core.ui.theme.MoPropGreen
import de.nicolasmahn.momentum.core.ui.theme.MoPropGreenDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropGreyDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropInk
import de.nicolasmahn.momentum.core.ui.theme.MoPropMint
import de.nicolasmahn.momentum.core.ui.theme.MoPropPink
import de.nicolasmahn.momentum.core.ui.theme.MoPropPinkDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropPurple
import de.nicolasmahn.momentum.core.ui.theme.MoPropPurpleDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropRed
import de.nicolasmahn.momentum.core.ui.theme.MoPropRedDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropShadow
import de.nicolasmahn.momentum.core.ui.theme.MoPropSky
import de.nicolasmahn.momentum.core.ui.theme.MoPropSteel
import de.nicolasmahn.momentum.core.ui.theme.MoPropTeal
import de.nicolasmahn.momentum.core.ui.theme.MoPropTealDark
import de.nicolasmahn.momentum.core.ui.theme.MoPropWater
import de.nicolasmahn.momentum.core.ui.theme.MoPropWhite
import de.nicolasmahn.momentum.core.ui.theme.MoPropYellow
import de.nicolasmahn.momentum.core.ui.theme.MoPropYellowDark
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
import de.nicolasmahn.momentum.feature.mo.block
import de.nicolasmahn.momentum.feature.mo.chunk
import de.nicolasmahn.momentum.feature.mo.cycle
import de.nicolasmahn.momentum.feature.mo.drop
import de.nicolasmahn.momentum.feature.mo.ease
import de.nicolasmahn.momentum.feature.mo.floorShadow
import de.nicolasmahn.momentum.feature.mo.limb
import de.nicolasmahn.momentum.feature.mo.moIdle
import de.nicolasmahn.momentum.feature.mo.oval
import de.nicolasmahn.momentum.feature.mo.polyline
import de.nicolasmahn.momentum.feature.mo.puff
import de.nicolasmahn.momentum.feature.mo.pulse
import de.nicolasmahn.momentum.feature.mo.wave
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/*
 * Activity is open and he would rather not. Every act here is a small comedy of effort avoided, timed
 * in seconds along one loop: track() reads a value off a list of keyframes, and reachTo() keeps a
 * mitten on a thing that stands still while the rest of him leans, stretches and trembles.
 */

/** The value at [x] along eased keyframes of (position, value); flat before the first and after the last. */
private fun track(x: Float, vararg keys: Pair<Float, Float>): Float {
    if (x <= keys.first().first) return keys.first().second
    for (index in 1 until keys.size) {
        val (to, toValue) = keys[index]
        if (x <= to) {
            val (from, fromValue) = keys[index - 1]
            val span = to - from
            return if (span <= 0f) toValue else fromValue + (toValue - fromValue) * ease((x - from) / span)
        }
    }
    return keys.last().second
}

private fun mix(from: Float, to: Float, amount: Float): Float = from + (to - from) * amount

private fun mix(from: Offset, to: Offset, amount: Float): Offset = from + (to - from) * amount

private fun mix(from: MoArm, to: MoArm, amount: Float): MoArm =
    MoArm(mix(from.reach, to.reach, amount), mix(from.bend, to.bend, amount))

/**
 * The reach that puts the hand of the arm on [side] at the screen point [target]: the kit moves him
 * by [offset], leans him by [tilt] and stretches him by [squash] about his feet, so the point is
 * taken back through all three before the shoulder is subtracted.
 */
private fun reachTo(target: Offset, side: Float, body: MoBody, tilt: Float, squash: Float, offset: Offset): Offset {
    val d = target - offset - Offset(CENTRE, FEET)
    val angle = tilt * PI.toFloat() / 180f
    val x = (d.x * cos(angle) + d.y * sin(angle)) / (1f - squash / 2f).coerceAtLeast(0.2f)
    val y = (-d.x * sin(angle) + d.y * cos(angle)) / (1f + squash).coerceAtLeast(0.2f)
    val shoulder = Offset(CENTRE + side * body.shoulderHalf, body.shoulderY)
    return Offset((CENTRE + x - shoulder.x) * side, FEET + y - shoulder.y)
}

/** His own tip has wilted over like a fringe: he eyes it, puffs it back upright, and it wilts again. */
private object BlowingTipBackUp : MoAct("lazy_blowing_tip_back_up", MoOccasion.Lazy, intensity = MoIntensity.Mild) {
    private fun twoPuffs(v: MoVariant) = v.chance(50, salt = 1)

    private fun period(v: MoVariant) = 6.2f + v.between(0.8f, 1.6f, salt = 2)

    // The loop starts with the tip already flopped and his eyes on it, which is the whole joke in one still.
    private fun seconds(t: Float, v: MoVariant): Float = period(v).let { cycle(t, it, offset = 4.5f / it) * it }

    private fun springAt(v: MoVariant) = if (twoPuffs(v)) 5.8f else 5.4f

    /** When each breath leaves him, and how big it is. Two weak ones or one good one. */
    private fun puffs(v: MoVariant): List<Pair<Float, Float>> =
        if (twoPuffs(v)) listOf(4.95f to 0.75f, 5.45f to 0.9f) else listOf(5.0f to 1f)

    private fun droop(sec: Float, v: MoVariant): Float =
        if (twoPuffs(v)) {
            track(sec, 0f to 0f, 4.2f to 1f, 5.3f to 1f, 5.45f to 0.45f, 5.78f to 0.75f, 5.8f to 0.75f, 5.98f to 0f)
        } else {
            track(sec, 0f to 0f, 4.2f to 1f, 5.4f to 1f, 5.58f to 0f)
        }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val sec = seconds(t, v)
        val d = droop(sec, v)
        val sprung = sec - springAt(v)
        val wobble = if (sprung in 0f..0.8f) -12f * sin(sprung / 0.24f * 2f * PI.toFloat()) * (1f - ease(sprung / 0.8f)) else 0f
        val watching = track(sec, 3f to 0f, 3.7f to 1f, springAt(v) + 0.3f to 1f, springAt(v) + 0.8f to 0f)
        val blowing = sec >= puffs(v).first().first - 0.2f && sec < springAt(v) + 0.1f
        val pleased = sec >= springAt(v) + 0.1f || sec < 0.9f
        val eye = MoEye(
            lidDrop = track(sec, 0.6f to 0.3f, 1.8f to 0.45f, 3f to 0.45f, 3.7f to 0.3f),
            gaze = Offset((0.15f + 0.4f * d) * watching, -0.9f * watching),
        )
        val idle = moIdle(t, energy = 0.45f)
        return idle.copy(
            leftEye = eye,
            rightEye = eye,
            brows = if (v.chance(50, salt = 3) && watching > 0.5f && sprung < 0f) MoBrows(MoBrowKind.Raised, skew = 6f) else MoBrows(),
            mouth = when {
                blowing -> MoMouth.Whistle
                pleased -> MoMouth.Smile(width = 13f, depth = 9f)
                else -> MoMouth.Flat(12f)
            },
            // Far over and pulled down: anything less reads as his ordinary lean to the right. The low
            // body's tip is already a hook, which the same push would draw out into a spike.
            sway = idle.sway * (1f - d) + (if (body == MoBodyKind.Low) 66f else 100f) * d + wobble,
            flare = -36f * d,
            tilt = 1.5f * d,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val sec = seconds(t, v)
        val colour = v.of(listOf(MoPropWhite, MoPropCream, MoPropSky), salt = 4)
        // The breath starts beside his face above the brow line, so it never crosses an eye or the mouth.
        val from = rig.rightEye + Offset(34f, -44f)
        for ((start, strength) in puffs(v)) {
            val fly = (sec - start) / 0.42f
            if (fly !in 0f..1f) continue
            val at = mix(from, rig.tip + Offset(10f, 16f), ease(fly))
            // It forms beside his face rather than appearing there whole.
            puff(at, (13f + 9f * fly) * strength, colour.copy(alpha = 0.9f * ease(fly / 0.15f) * (1f - fly * fly)))
        }
    }
}

/** He heaves at a dumbbell with everything he has: he gets longer, and the dumbbell stays where it is. */
private object DumbbellStaysDown : MoAct("lazy_dumbbell_stays_down", MoOccasion.Lazy) {
    private const val PERIOD = 6.5f

    private fun side(v: MoVariant) = if (v.chance(50, salt = 1)) -1f else 1f

    // He stands off to the far side, which is what leaves room for a dumbbell worth failing at.
    private const val APART = 18f

    private fun bar(v: MoVariant) = Offset(CENTRE + side(v) * 108f, FEET - 30f)

    // The loop starts deep in the heave, the frame that explains itself.
    private fun seconds(t: Float) = cycle(t, PERIOD, offset = 2.6f / PERIOD) * PERIOD

    private fun heave(sec: Float) = track(sec, 1f to 0f, 1.9f to 0.8f, 3.6f to 1f, 3.85f to 0f)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val s = side(v)
        val sec = seconds(t)
        val h = heave(sec)
        // Let go, he bounces: squashed first, then a couple of ever smaller wobbles.
        val since = sec - 3.8f
        val rebound = if (since in 0f..1f) sin(since / 0.4f * 2f * PI.toFloat()) * (1f - ease(since)) else 0f
        val idle = moIdle(t, energy = 0.7f)
        val squash = idle.squash - 0.07f - v.between(0.08f, 0.14f, salt = 5) * h - 0.06f * rebound
        val tilt = s * (3f + 4f * h)
        val shake = wave(t, 0.07f) * 1.4f * h
        val grip = MoArm(reachTo(bar(v) + Offset(0f, -4f), s, MoBodies.of(body), tilt, squash, Offset(shake - s * APART, 0f)), bend = 0.45f, inFront = true)
        // A clenched fist held close: standing this far over, a flung-out arm would leave the picture.
        val counterweight = mix(MoArm.Hip, MoArm(Offset(-24f, 13f), bend = 0.8f), h).copy(inFront = true)
        val heaving = sec in 1f..3.7f
        val panting = sec > 3.7f && sec < 5.3f
        val eye = when {
            heaving -> MoEye(lidDrop = 0.2f, gaze = Offset(-s * 0.7f, -0.4f))
            panting -> MoEye(lidDrop = 0.55f, gaze = Offset(0f, 0.3f))
            else -> MoEye(lidDrop = 0.35f)
        }
        return idle.copy(
            leftEye = eye,
            rightEye = eye,
            brows = when {
                heaving -> MoBrows(MoBrowKind.Flat, lift = 2f)
                panting -> MoBrows()
                else -> MoBrows(MoBrowKind.Worried)
            },
            mouth = when {
                heaving -> MoMouth.Gritted
                panting -> MoMouth.Oh(size = 6f + 3f * pulse(t, 0.5f))
                else -> MoMouth.Wavy
            },
            leftArm = if (s < 0f) grip else counterweight,
            rightArm = if (s < 0f) counterweight else grip,
            squash = squash,
            tilt = tilt,
            offset = Offset(-s * APART, 0f),
            shake = shake,
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val bar = bar(v)
        val (plate, ledge) = v.of(
            listOf(MoPropRed to MoPropRedDark, MoPropBlue to MoPropBlueDark, MoPropPurple to MoPropPurpleDark, MoPropGreen to MoPropGreenDark),
            salt = 2,
        )
        floorShadow(bar.x, 48f, MoPropShadow)
        block(bar, 96f, 11f, MoPropSteel, corner = 5.5f)
        for (end in listOf(-1f, 1f)) {
            if (v.chance(50, salt = 3)) chunk(bar + Offset(end * 41f, 0f), 12f, 38f, plate, ledge, corner = 5f)
            chunk(bar + Offset(end * 26f, 0f), 18f, 55f, plate, ledge, corner = 7f)
        }
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        if (!v.chance(40, salt = 4)) return
        val s = side(v)
        val fly = (seconds(t) - 3f) / 0.7f
        if (fly !in 0f..1f) return
        // It stays close to his brow: he stands far over and leans further, so a long arc would leave the picture.
        val brow = (if (s < 0f) rig.rightEye else rig.leftEye) + Offset(-s * 40f, -46f)
        drop(brow + Offset(-s * 14f * fly, -26f * fly + 22f * fly * fly), 6f, MoPropWater.copy(alpha = 1f - fly))
    }

    override val ownsTheFloor get() = true
}

/** Slumped on the floor, he pats about for a remote that lies a hand's width too far, and pleads with us instead of getting up. */
private object RemoteOutOfReach : MoAct("lazy_remote_out_of_reach", MoOccasion.Lazy) {
    private const val PERIOD = 8f

    // How far his body is lowered onto the floor.
    private const val SLUMP = 14f

    // Where the kit puts the middle of a foot that stands on the floor.
    private const val FOOT_Y = 413f

    private fun side(v: MoVariant) = if (v.chance(50, salt = 1)) -1f else 1f

    private fun remote(v: MoVariant) = Offset(CENTRE + side(v) * 129f, FEET - 13f)

    private fun seconds(t: Float) = cycle(t, PERIOD, offset = 0.3f) * PERIOD

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val s = side(v)
        val sec = seconds(t)
        val shape = MoBodies.of(body)
        val lean = track(sec, 0f to 0f, 1.6f to 1f, 4.96f to 1f, 6f to 0f)
        val pats = 2 + v.pick(2, salt = 2)
        val patting = (sec - 1.76f) / 1.6f
        val lift = if (patting in 0f..1f) 12f * abs(sin(pats * PI.toFloat() * patting)) else 0f
        val idle = moIdle(t, energy = 0.5f)
        // One sigh while he sits there beaten, before the next try.
        val squash = idle.squash - 0.04f - 0.025f * track(sec, 6.4f to 0f, 7f to 1f, 7.7f to 0f)
        val tilt = s * 10f * lean
        // He sits off to the far side, so the reach toward the remote has room to be a reach, and low
        // enough for his base to rest on the floor: feet hovering under him would read as standing.
        val offset = Offset(-s * 48f, SLUMP)
        // Half a mitten and a bit: always short of the remote, by a different amount each day.
        val short = 29f + 12.5f + v.between(8f, 16f, salt = 4)
        val floor = reachTo(remote(v) + Offset(-s * short, -1f - lift), s, shape, tilt, squash, offset)
        val reaching = mix(MoArm.Down, MoArm(floor, bend = 0.05f), lean)
        val hanging = MoArm(Offset(2f, 40f), bend = 0.1f)
        val pleading = sec > 3.5f && sec < 5.1f
        val trying = sec <= 3.5f
        val eye = when {
            pleading -> MoEye(lid = -0.4f, gaze = Offset(0f, -0.4f))
            trying -> MoEye(lidDrop = 0.3f, gaze = Offset(s * 0.9f, 0.8f))
            else -> MoEye(lidDrop = 0.5f, gaze = Offset(s * 0.8f, 0.7f))
        }
        return idle.copy(
            leftEye = eye,
            rightEye = eye,
            brows = when {
                pleading -> MoBrows(MoBrowKind.Worried, lift = 3f)
                trying -> MoBrows(MoBrowKind.Flat)
                else -> MoBrows()
            },
            mouth = when {
                pleading -> MoMouth.Pout
                trying -> MoMouth.TongueOut
                else -> MoMouth.Frown(9f)
            },
            leftArm = if (s < 0f) reaching else hanging,
            rightArm = if (s < 0f) hanging else reaching,
            legs = MoLegs(planted(-1f, shape, tilt, squash), planted(1f, shape, tilt, squash)),
            squash = squash,
            tilt = tilt,
            offset = offset,
        )
    }

    /**
     * A foot splayed out to [side] that stays flat on the floor: the slump and the lean would otherwise
     * push the foot he leans over through the floor and swing the other one into the air.
     */
    private fun planted(side: Float, body: MoBody, tilt: Float, squash: Float): MoLeg {
        val swing = 34f
        val out = (if (side < 0f) body.leftFootX else body.rightFootX) + side * swing - CENTRE
        val angle = tilt * PI.toFloat() / 180f
        val above = (FEET - FOOT_Y + SLUMP + out * (1f - squash / 2f) * sin(angle)) / cos(angle) / (1f + squash)
        return MoLeg(lift = above - (FEET - FOOT_Y), swing = swing)
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val s = side(v)
        val remote = remote(v)
        floorShadow(remote.x, 30f, MoPropShadow)
        chunk(remote, 58f, 22f, MoPropGreyDark, MoPropCharcoal, corner = 9f, drop = 4f)
        val buttons = listOf(MoPropGreen, MoPropYellow, MoPropSky, MoPropPink).filterIndexed { index, _ -> index != v.pick(4, salt = 3) }
        drawCircle(MoPropRed, 5.5f, remote + Offset(s * 19f, -2f))
        buttons.forEachIndexed { index, colour -> drawCircle(colour, 4f, remote + Offset(s * (6f - index * 11f), -2f)) }
    }

    override val ownsTheFloor get() = true
}

/** He jogs on the spot in extreme slow motion, and a snail glides past his feet and leaves him behind. */
private object OvertakenByASnail : MoAct("lazy_overtaken_by_a_snail", MoOccasion.Lazy, intensity = MoIntensity.Mild) {
    private const val FROM = -12f
    private const val TO = 232f

    // The follower trails this far behind, so a pair needs a longer road for both to cross.
    private const val TRAIL = 56f

    private fun direction(v: MoVariant) = if (v.chance(50, salt = 2)) -1f else 1f

    private fun pair(v: MoVariant) = v.chance(15, salt = 4)

    // A multiple of his four-second stride, so his feet and the snail repeat together.
    private fun crossing(v: MoVariant) = v.of(listOf(12f, 16f, 20f), salt = 3)

    private fun road(v: MoVariant) = TO - FROM + if (pair(v)) TRAIL else 0f

    /** How far along its road the leading snail is, starting beside his near foot. */
    private fun travelled(t: Float, v: MoVariant) = cycle(t, crossing(v), offset = (44f - FROM) / road(v)) * road(v)

    /** A position along the road, as the x it is drawn at. */
    private fun at(along: Float, v: MoVariant) = if (direction(v) > 0f) FROM + along else 2f * CENTRE - FROM - along

    private fun visible(along: Float, ramp: Float = 25f) =
        ((along / ramp).coerceIn(0f, 1f) * ((TO - FROM - along) / ramp).coerceIn(0f, 1f))

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val along = travelled(t, v)
        val x = at(along, v)
        val level = (1f - abs(x - CENTRE) / 40f).coerceIn(0f, 1f)
        // His eyes let go of it as it fades at either edge, so they never jump when it comes round again.
        val following = ease(visible(along, ramp = 60f))
        val passed = (along - (CENTRE - FROM + 40f)) / road(v) * crossing(v)
        // The sag is gone again by the time the snail comes round, so the loop has no seam.
        val untilWrap = (road(v) - along) / road(v) * crossing(v)
        val sag = ease(passed / 1.5f) * ease(untilWrap / 2f)
        val eye = MoEye(
            lidDrop = 0.5f - 0.25f * level,
            gaze = mix(Offset(0f, 0.25f), Offset(((x - CENTRE) / 150f).coerceIn(-1f, 1f), 0.9f), following),
        )
        val idle = moIdle(t, energy = 0.5f)
        return idle.copy(
            leftEye = eye,
            rightEye = eye,
            brows = if (level > 0.3f) MoBrows(MoBrowKind.Raised, lift = 5f * level) else MoBrows(MoBrowKind.Flat),
            mouth = if (level > 0.7f) MoMouth.Oh(5f) else MoMouth.Flat(13f),
            leftArm = MoArm(Offset(8f, 22f - 7f * wave(t, 4f)), bend = 0.6f),
            rightArm = MoArm(Offset(8f, 22f + 7f * wave(t, 4f)), bend = 0.6f),
            legs = MoLegs(MoLeg(lift = 16f * pulse(t, 4f)), MoLeg(lift = 16f * pulse(t + 2f, 4f))),
            offset = Offset(0f, -2f * pulse(t, 2f)),
            sway = idle.sway + direction(v) * 5f * level,
            squash = idle.squash - 0.03f * sag,
        )
    }

    override fun DrawScope.foreground(t: Float, v: MoVariant) {
        val (shell, dark) = v.of(
            listOf(MoPropTeal to MoPropTealDark, MoPropPink to MoPropPinkDark, MoPropYellow to MoPropYellowDark, MoPropPurple to MoPropPurpleDark),
            salt = 1,
        )
        val along = travelled(t, v)
        if (pair(v)) snail(at(along - TRAIL, v), direction(v), 0.8f, visible(along - TRAIL), t + 0.5f, shell, dark)
        snail(at(along, v), direction(v), 1.4f, visible(along), t, shell, dark)
    }

    private fun DrawScope.snail(x: Float, dir: Float, scale: Float, alpha: Float, t: Float, shell: Color, dark: Color) {
        if (alpha <= 0f) return
        // A touch below his feet, so it plainly passes in front of them.
        val floor = FEET + 3f
        val skin = MoPropCream.copy(alpha = alpha)
        val stretch = pulse(t, 1.2f)
        floorShadow(x, 22f * scale, MoPropShadow.copy(alpha = MoPropShadow.alpha * alpha))
        val front = Offset(x + dir * (15f + 2f * stretch) * scale, floor - 8f * scale)
        for (stalk in listOf(0f, 1f)) {
            val tip = front + Offset(dir * (3f + stalk * 6f) * scale, (-12f + stalk * 2f) * scale + wave(t + stalk * 0.4f, 1.7f) * scale)
            limb(front, tip, skin, 3.5f * scale)
            drawCircle(skin, 3.5f * scale, tip)
            drawCircle(MoPropInk.copy(alpha = alpha), 1.6f * scale, tip + Offset(dir * 0.8f * scale, 0f))
        }
        oval(Offset(x, floor - 6f * scale), (21f + 2f * stretch) * scale, 6f * scale, skin)
        val centre = Offset(x - dir * 4f * scale, floor - 19f * scale)
        drawCircle(dark.copy(alpha = alpha), 13f * scale, centre + Offset(0f, 3f * scale))
        drawCircle(shell.copy(alpha = alpha), 13f * scale, centre)
        val spiral = (0..18).map { step ->
            val turn = step / 18f
            val angle = turn * 3f * PI.toFloat()
            centre + Offset(-dir * cos(angle), sin(angle)) * ((1.5f + 7.5f * turn) * scale)
        }
        polyline(dark.copy(alpha = alpha), 3f * scale, spiral)
    }

    // The snail has the whole floor for its road, so nothing else may lie on it.
    override val ownsTheFloor get() = true
}

/** His running shoes wait beside him, one tapping its toe. He looks; it stops, hopeful. He looks away and whistles. */
private object TheWaitingShoes : MoAct("lazy_the_waiting_shoes", MoOccasion.Lazy, intensity = MoIntensity.Mild) {
    private const val PERIOD = 7f

    /** A little under the size he wears them, which is what the floor beside a blazing Mo has room for. */
    private const val PAIR_SIZE = 0.9f

    private fun side(v: MoVariant) = if (v.chance(50, salt = 1)) -1f else 1f

    private fun looking(t: Float): Float {
        val sec = cycle(t, PERIOD) * PERIOD
        return track(sec, 3.15f to 0f, 3.7f to 1f, 5.6f to 1f, 6.3f to 0f)
    }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val s = side(v)
        val look = looking(t)
        val eye = MoEye(lidDrop = 0.35f, gaze = mix(Offset(-s * 0.85f, -0.45f), Offset(s * 0.9f, 0.85f), look))
        val rocking = MoLeg(lift = 3f * pulse(t, 1.4f) * (1f - look))
        return moIdle(t, energy = 0.6f).copy(
            leftEye = eye,
            rightEye = eye,
            brows = if (look < 0.5f) MoBrows(MoBrowKind.Raised, lift = 3f) else MoBrows(MoBrowKind.Worried, lift = 2f),
            mouth = when {
                look < 0.3f -> MoMouth.Whistle
                look > 0.5f -> MoMouth.Wavy
                else -> MoMouth.Flat(10f)
            },
            // Both hands tucked behind him, as if the shoes might be handed to him otherwise.
            leftArm = MoArm.Hip,
            rightArm = MoArm.Hip,
            legs = if (s > 0f) MoLegs(left = rocking) else MoLegs(right = rocking),
            tilt = mix(-s * 2.5f, s * 1.5f, look),
            // He stands well clear of them, which also leaves the shoes room to be seen.
            offset = Offset(-s * 44f, 0f),
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val s = side(v)
        val look = looking(t)
        val (upper, trim) = v.of(
            listOf(MoPropBlue to MoPropBlueDark, MoPropGreen to MoPropGreenDark, MoPropPink to MoPropPinkDark, MoPropPurple to MoPropPurpleDark),
            salt = 2,
        )
        val trainers = MoTrainer(upper, trim, laces = v.of(listOf(MoPropYellow, MoPropWhite, MoPropMint), salt = 6))
        val tapper = v.pick(2, salt = 3)
        val beat = v.between(0.35f, 0.6f, salt = 4)
        // While he looks it holds its toe up and still, waiting; the moment he looks away it taps again.
        val tap = 9f * abs(sin(t * PI.toFloat() / beat)) * (1f - look) + 6f * ease((look - 0.4f) / 0.4f)
        floorShadow(CENTRE + s * 108f, 47f, MoPropShadow)
        // A pair as it is left on the floor: toes towards him, the far shoe half behind the near one
        // and a little further back. Side by side at his size they would not fit beside him.
        for (index in 0..1) {
            val far = index == 0
            val at = Offset(CENTRE + s * (if (far) 96f else 126f), FEET - TRAINER_FLOOR * PAIR_SIZE - if (far) 4f else 0f)
            trainer(at, toe = -s, trainers, size = PAIR_SIZE, toeUp = if (index == tapper) tap * 1.5f else 0f, behind = far)
        }
    }

    override val ownsTheFloor get() = true
}

/** Sunk deep in a beanbag, he tries to heave himself out, and the bag takes him back. He does not mind. */
private object TheBeanbagWins : MoAct("lazy_the_beanbag_wins", MoOccasion.Lazy) {
    private const val PERIOD = 7f

    private fun seconds(t: Float) = cycle(t, PERIOD) * PERIOD

    private fun twoHeaves(v: MoVariant) = v.chance(40, salt = 3)

    /** When the bag takes him back, and when he has settled again. */
    private fun release(v: MoVariant) = if (twoHeaves(v)) 4.7f else 4f

    private fun settled(v: MoVariant) = if (twoHeaves(v)) 5.8f else 5.4f

    private fun sink(sec: Float, v: MoVariant): Float =
        if (twoHeaves(v)) {
            track(sec, 2f to 1f, 2.8f to 0.5f, 3.1f to 0.5f, 3.6f to 0.85f, 4.4f to 0.35f, 4.7f to 0.35f, 5.8f to 1f)
        } else {
            track(sec, 2f to 1f, 3.4f to 0.15f, 4f to 0.15f, 5.4f to 1f)
        }

    private fun colours(v: MoVariant) = v.of(
        listOf(MoPropTeal to MoPropTealDark, MoPropPurple to MoPropPurpleDark, MoPropRed to MoPropRedDark, MoPropPink to MoPropPinkDark),
        salt = 1,
    )

    private fun patchSide(v: MoVariant) = if (v.chance(50, salt = 2)) -1f else 1f

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val sec = seconds(t)
        val s = sink(sec, v)
        // Any further out and the mitten leaves the picture when the bag stretches him at his deepest.
        val draped = MoArm(Offset(46f, 8f), bend = 0.35f)
        val pushing = MoArm(Offset(42f, 34f), bend = 0.1f)
        // One arm goes first, as it does when nobody has planned the getting up.
        fun push(lead: Float) = ((1f - sink(sec + lead, v)) / 0.6f).coerceIn(0f, 1f)
        val first = mix(draped, pushing, push(0.4f))
        val second = mix(draped, pushing, push(0f))
        val leftFirst = v.chance(50, salt = 4)
        val heaving = sec > 2f && sec < release(v)
        val breathingOut = sec >= release(v) && sec < settled(v)
        // Feet tucked up, so they stay swallowed by the front of the bag however deep he sits.
        val foot = MoLeg(lift = 20f * s + 8f)
        val idle = moIdle(t, energy = 0.5f)
        return idle.copy(
            leftEye = MoEye(lidDrop = if (heaving) 0.1f else 0.6f, gaze = Offset(0f, if (heaving) -0.3f else 0.2f)),
            rightEye = MoEye(lidDrop = if (heaving) 0.1f else 0.6f, gaze = Offset(0f, if (heaving) -0.3f else 0.2f)),
            brows = if (heaving) MoBrows(MoBrowKind.Raised, lift = 5f) else MoBrows(),
            mouth = when {
                heaving -> MoMouth.Gritted
                breathingOut -> MoMouth.Oh(6f)
                else -> MoMouth.Smile(width = 12f, depth = 8f)
            },
            leftArm = if (leftFirst) first else second,
            rightArm = if (leftFirst) second else first,
            legs = MoLegs(foot, foot),
            offset = Offset(0f, (if (body == MoBodyKind.Low) 9f else 17f) * s),
            squash = idle.squash - 0.06f * s + 0.03f * (1f - s),
            shake = if (heaving) wave(t, 0.08f) * 1.2f * (1f - s) else 0f,
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) {
        val (bag, dark) = colours(v)
        val s = patchSide(v)
        val sag = 7f * sink(seconds(t), v)
        floorShadow(CENTRE, 150f, MoPropShadow)
        withTransform({
            translate(CENTRE, FEET)
            scale(s, 1f, Offset.Zero)
        }) {
            val sack = Path().apply {
                moveTo(112f, -174f + sag)
                cubicTo(136f, -152f, 118f, -113f, 144f, -70f)
                cubicTo(169f, -18f, 141f, 0f, 108f, 0f)
                lineTo(-110f, 0f)
                cubicTo(-157f, 0f, -158f, -38f, -140f, -70f)
                cubicTo(-110f, -124f, -62f, -67f, 23f, -110f)
                quadraticTo(92f, -132f, 112f, -174f + sag)
                close()
            }
            drawPath(sack, bag)
            curve(Offset(112f, -168f + sag), Offset(96f, -110f), Offset(128f, -72f), dark, 3f)
        }
        val patch = v.of(listOf(MoPropYellow, MoPropCream, MoPropMint, MoPropRed), salt = 6)
        drawCircle(patch, 13f, Offset(CENTRE + patchSide(v) * 120f, FEET - 104f))
    }

    override fun DrawScope.foreground(t: Float, v: MoVariant) {
        val (bag, dark) = colours(v)
        val sec = seconds(t)
        val sag = 5f * sink(sec, v)
        withTransform({
            translate(CENTRE, FEET)
            scale(patchSide(v), 1f, Offset.Zero)
        }) {
            val lap = Path().apply {
                moveTo(-145f, -48f)
                cubicTo(-105f, -60f, -76f, -20f + sag, -27f, -27f + sag)
                cubicTo(24f, -20f + sag, 80f, -32f, 138f, -56f)
                quadraticTo(155f, 2f, 102f, 2f)
                lineTo(-100f, 2f)
                quadraticTo(-158f, 0f, -145f, -48f)
                close()
            }
            drawPath(lap, bag)
            curve(Offset(-136f, -41f), Offset(-100f, -37f), Offset(-84f, -4f), dark, 3f)
        }
        if (!v.chance(50, salt = 5)) return
        // A few beads squeezed out of a seam as he lands, on the side without the patch.
        val seam = Offset(CENTRE - patchSide(v) * 104f, FEET - 44f)
        for (index in 0..3) {
            val fly = (sec - release(v) - 0.5f - index * 0.12f) / 0.9f
            if (fly !in 0f..1f) continue
            val out = -patchSide(v) * (8f + index * 5f) * fly
            drawCircle(MoPropWhite.copy(alpha = 1f - fly * fly), 2.5f, seam + Offset(out, -34f * fly + 40f * fly * fly))
        }
    }

    override val ownsTheFloor get() = true
    override val sparkles get() = false
}

internal val lazyActs1: List<MoAct> = listOf(
    BlowingTipBackUp,
    DumbbellStaysDown,
    RemoteOutOfReach,
    OvertakenByASnail,
    TheWaitingShoes,
    TheBeanbagWins,
)
