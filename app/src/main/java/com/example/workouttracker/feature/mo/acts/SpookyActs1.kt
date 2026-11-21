package com.example.workouttracker.feature.mo.acts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import com.example.workouttracker.core.ui.theme.MoFlameBelly
import com.example.workouttracker.core.ui.theme.MoPropBlue
import com.example.workouttracker.core.ui.theme.MoPropBrown
import com.example.workouttracker.core.ui.theme.MoPropCream
import com.example.workouttracker.core.ui.theme.MoPropGold
import com.example.workouttracker.core.ui.theme.MoPropGoldDark
import com.example.workouttracker.core.ui.theme.MoPropGreen
import com.example.workouttracker.core.ui.theme.MoPropGreenDark
import com.example.workouttracker.core.ui.theme.MoPropInk
import com.example.workouttracker.core.ui.theme.MoPropMint
import com.example.workouttracker.core.ui.theme.MoPropOrange
import com.example.workouttracker.core.ui.theme.MoPropPink
import com.example.workouttracker.core.ui.theme.MoPropPinkDark
import com.example.workouttracker.core.ui.theme.MoPropPurple
import com.example.workouttracker.core.ui.theme.MoPropPurpleDark
import com.example.workouttracker.core.ui.theme.MoPropRed
import com.example.workouttracker.core.ui.theme.MoPropSand
import com.example.workouttracker.core.ui.theme.MoPropShadow
import com.example.workouttracker.core.ui.theme.MoPropSilver
import com.example.workouttracker.core.ui.theme.MoPropSky
import com.example.workouttracker.core.ui.theme.MoPropTeal
import com.example.workouttracker.core.ui.theme.MoPropTealDark
import com.example.workouttracker.core.ui.theme.MoPropWhite
import com.example.workouttracker.core.ui.theme.MoPropWood
import com.example.workouttracker.core.ui.theme.MoPropWoodDark
import com.example.workouttracker.domain.mo.MoIntensity
import com.example.workouttracker.domain.mo.MoOccasion
import com.example.workouttracker.feature.mo.CENTRE
import com.example.workouttracker.feature.mo.FEET
import com.example.workouttracker.feature.mo.MoArm
import com.example.workouttracker.feature.mo.MoBodyKind
import com.example.workouttracker.feature.mo.MoBrowKind
import com.example.workouttracker.feature.mo.MoBrows
import com.example.workouttracker.feature.mo.MoEye
import com.example.workouttracker.feature.mo.MoLeg
import com.example.workouttracker.feature.mo.MoLegs
import com.example.workouttracker.feature.mo.MoMouth
import com.example.workouttracker.feature.mo.MoPose
import com.example.workouttracker.feature.mo.MoRig
import com.example.workouttracker.feature.mo.block
import com.example.workouttracker.feature.mo.curve
import com.example.workouttracker.feature.mo.cycle
import com.example.workouttracker.feature.mo.ease
import com.example.workouttracker.feature.mo.flameTongue
import com.example.workouttracker.feature.mo.floorShadow
import com.example.workouttracker.feature.mo.limb
import com.example.workouttracker.feature.mo.mitten
import com.example.workouttracker.feature.mo.moIdle
import com.example.workouttracker.feature.mo.oval
import com.example.workouttracker.feature.mo.pulse
import com.example.workouttracker.feature.mo.shape
import com.example.workouttracker.feature.mo.sparkle
import com.example.workouttracker.feature.mo.threadFromAbove
import com.example.workouttracker.feature.mo.twitch
import com.example.workouttracker.feature.mo.wave
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/*
 * The spooky night, played warm: the visitors are friendly, the pumpkin is a portrait, and the only
 * thing that burns is Mo.
 */

/** A point as the pair [shape] takes, for shapes built out of a moving frame. */
private fun Offset.pair(): Pair<Float, Float> = x to y

/** He carved a pumpkin with his own face on it and presents it: he is the candle inside. */
private object CarvedHimself : MoAct("spooky_carved_himself", MoOccasion.Spooky, intensity = MoIntensity.Mild) {
    private const val LOOK_SECONDS = 4.6f
    // High enough that the arm passes over the stem rather than through it.
    private val present = MoArm(Offset(46f, -42f), bend = 0.1f)

    private fun side(v: MoVariant) = if (v.chance(50, salt = 1)) -1f else 1f

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val s = side(v)
        val p = cycle(t, LOOK_SECONDS)
        val atPumpkin = ease((p - 0.35f) / 0.12f) - ease((p - 0.8f) / 0.12f)
        // Half the look period, so the proud little bounce can never drift out of step with it.
        val bounce = twitch(t, LOOK_SECONDS / 2f, beat = 0.45f, offset = 0.5f)
        val idle = moIdle(t)
        val eye = MoEye(gaze = Offset(s * 0.9f * atPumpkin, 0.55f * atPumpkin))
        return idle.copy(
            leftEye = eye,
            rightEye = eye,
            brows = MoBrows(MoBrowKind.Raised, lift = 2f),
            mouth = MoMouth.Grin(open = 0.5f),
            leftArm = if (s < 0f) present else MoArm.Hip,
            rightArm = if (s < 0f) MoArm.Hip else present,
            tilt = s * 2f,
            offset = Offset(0f, -4f * bounce),
            squash = idle.squash + 0.02f * bounce,
        )
    }

    override fun DrawScope.foreground(t: Float, v: MoVariant) {
        val s = side(v)
        // In front, so it overlaps the corner of his base instead of hiding behind it.
        val c = Offset(CENTRE + s * 106f, FEET - 48f)
        val (face, ledge) = v.of(
            listOf(MoPropOrange to MoPropWood, MoPropGreen to MoPropGreenDark, MoPropPurple to MoPropPurpleDark),
            salt = 2,
        )
        floorShadow(c.x, 54f, MoPropShadow)
        oval(c + Offset(0f, 6f), 50f, 42f, ledge)
        oval(c + Offset(-24f, 0f), 26f, 40f, face)
        oval(c + Offset(24f, 0f), 26f, 40f, face)
        oval(c, 32f, 42f, face)
        shape(
            v.of(listOf(MoPropGreenDark, MoPropBrown), salt = 4),
            c.x - 7f to c.y - 40f, c.x + 7f to c.y - 40f, c.x + 16f to c.y - 52f, c.x + 6f to c.y - 54f,
        )
        carved(c, MoFlameBelly, v)
        // The light breathes on moIdle's own sway period, so the pumpkin flickers with his flame. It
        // brightens rather than reddens: an orange flicker vanished on the orange pumpkin.
        carved(c, MoPropCream.copy(alpha = 0.5f * (0.5f + 0.5f * wave(t, 2.9f))), v)
    }

    /** The cut-out face, which is his: two big wide-set eyes and a small mouth. */
    private fun DrawScope.carved(c: Offset, colour: Color, v: MoVariant) {
        drawCircle(colour, 13f, c + Offset(-22f, -10f))
        drawCircle(colour, 13f, c + Offset(22f, -10f))
        when (v.pick(3, salt = 3)) {
            0 -> curve(c + Offset(-16f, 10f), c + Offset(0f, 28f), c + Offset(16f, 10f), colour, 7f)
            1 -> oval(c + Offset(0f, 18f), 15f, 9f, colour)
            // Small and low, or it reads as a third eye.
            else -> drawCircle(colour, 6.5f, c + Offset(0f, 21f))
        }
    }

    override val ownsTheFloor get() = true
}

/** A little bedsheet ghost has found the warmest thing in the room and holds its hands out to it. */
private object GhostWarmsItsHands : MoAct("spooky_ghost_warms_its_hands", MoOccasion.Spooky, intensity = MoIntensity.Mild) {
    private fun side(v: MoVariant) = if (v.chance(50, salt = 1)) -1f else 1f

    private fun driftSeconds(v: MoVariant) = v.between(5.6f, 7.2f, salt = 3)

    /** 1 when it is up against him, 0 at the far end of its drift. */
    private fun near(t: Float, v: MoVariant) = 1f - pulse(t, driftSeconds(v))

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val s = side(v)
        val near = near(t, v)
        // He watches it while it is close and lets his eyes go back to us while it drifts off.
        val glance = ease(near * 1.6f - 0.3f)
        val eye = MoEye(gaze = Offset(s * 0.85f * glance, 0.35f * glance))
        // The near arm is behind his guest all loop whatever it does, so the far one opens in welcome
        // and the near one tucks in high enough that no mitten shows under the hem on the Low body.
        val welcome = MoArm(Offset(24f, 14f), bend = 0.3f)
        val tucked = MoArm(Offset(-6f, 16f), bend = 0.75f)
        return moIdle(t).copy(
            leftEye = eye,
            rightEye = eye,
            mouth = MoMouth.Smile(width = 19f, depth = 16f),
            leftArm = if (s < 0f) tucked else welcome,
            rightArm = if (s < 0f) welcome else tucked,
            tilt = s * (1f + 1.5f * near),
        )
    }

    override fun DrawScope.foreground(t: Float, v: MoVariant) {
        val s = side(v)
        val near = near(t, v)
        val far = 1f - near
        // Ghosts are cold: it only shivers out at the far end, and in short bursts.
        val burst = twitch(t, 1.1f, beat = 0.6f)
        val (face, hem) = v.of(
            listOf(MoPropCream to MoPropSand, MoPropMint to MoPropTeal, MoPropSky to MoPropBlue),
            salt = 2,
        )
        // Beside his belly on both bodies, well off the floor so it plainly floats, and under the
        // eyes of both; the foreground has no rig, so this is the one fixed number here.
        val g = Offset(
            CENTRE + s * (108f + 8f * far) + wave(t, 0.07f) * 2.2f * far * burst,
            338f + 4f * wave(t, 2.1f),
        )
        floorShadow(g.x, 20f, MoPropShadow.copy(alpha = 0.16f))
        sheet(g + Offset(0f, 6f), hem)
        sheet(g, face)
        // Past the dome's edge, or they would read as a bump in the sheet and not as arms, but short
        // of his eyes on either body.
        oval(g + Offset(-s * 34f, -6f), 13f, 7.5f, face)
        oval(g + Offset(-s * 32f, 12f), 13f, 7.5f, face)

        // Its face turns a little toward him, the way anyone at a fire faces it.
        val eyes = g + Offset(-s * 5f, -12f)
        for (w in listOf(-1f, 1f)) {
            val e = eyes + Offset(w * 11f, 0f)
            if (near > 0.6f) {
                curve(e + Offset(-6f, 3f), e + Offset(0f, -6f), e + Offset(6f, 3f), MoPropInk, 3.5f)
            } else if (v.chance(50, salt = 4)) {
                oval(e, 5f, 7f, MoPropInk)
            } else {
                drawCircle(MoPropInk, 5f, e)
            }
        }
        if (near <= 0.6f) drawCircle(MoPropInk, 3.5f, g + Offset(-s * 5f, 8f))

        val chill = MoPropSky.copy(alpha = far * burst)
        for (m in listOf(g + Offset(s * 32f, -18f), g + Offset(s * 35f, 4f))) {
            curve(m + Offset(0f, -6f), m + Offset(s * 6f, 0f), m + Offset(0f, 6f), chill, 3f)
        }
    }

    /** The sheet: a rounded dome with three scallops along its hem. */
    private fun DrawScope.sheet(c: Offset, colour: Color) {
        block(c, 60f, 72f, colour, corner = 30f)
        for (dx in listOf(-20f, 0f, 20f)) drawCircle(colour, 11f, c + Offset(dx, 34f))
    }
}

/** A bat has picked his arm as the warmest perch in the room, and he does not dare move. */
private object BatAsleepOnHisArm : MoAct("spooky_bat_asleep_on_his_arm", MoOccasion.Spooky, intensity = MoIntensity.Mild) {
    private const val PERIOD = 5.2f

    private fun side(v: MoVariant) = if (v.chance(50, salt = 1)) -1f else 1f

    /** How far the arm has given way, 0 to 1: slowly down all cycle, then hauled back up. */
    private fun droop(t: Float): Float {
        val p = cycle(t, PERIOD)
        return if (p < 0.85f) ease(p / 0.85f) else 1f - ease((p - 0.85f) / 0.15f)
    }

    /** The heave that gets it level again, a half sine that starts and ends at nothing. */
    private fun hoist(t: Float): Float {
        val p = cycle(t, PERIOD)
        return if (p > 0.85f) sin((p - 0.85f) / 0.15f * PI.toFloat()) else 0f
    }

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val s = side(v)
        val p = cycle(t, PERIOD)
        val sag = v.between(8f, 13f, salt = 4) * droop(t)
        val heave = hoist(t)
        // A glance at us in the middle of the cycle: look what has happened to me.
        val toViewer = ease((p - 0.3f) / 0.08f) - ease((p - 0.5f) / 0.08f)
        // Held out high, which is the only way a whole bat fits between his hand and the floor.
        val perch = MoArm(Offset(44f, -40f + sag + wave(t, 0.09f) * 1.5f * droop(t)), bend = 0.05f)
        val eye = MoEye(
            gaze = Offset(s * 0.9f * (1f - toViewer), 0.3f * (1f - toViewer)),
            open = 1f - 0.85f * heave,
        )
        return moIdle(t, energy = 0.6f).copy(
            leftEye = eye,
            rightEye = eye,
            brows = MoBrows(MoBrowKind.Worried, lift = 2f + 3f * heave),
            mouth = if (heave > 0.3f) MoMouth.Flat(width = 10f) else MoMouth.Smile(width = 10f, depth = 8f),
            leftArm = if (s < 0f) perch else MoArm.Down,
            rightArm = if (s < 0f) MoArm.Down else perch,
            tilt = -s * 1.5f,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val s = side(v)
        val (colour, dark) = v.of(
            listOf(MoPropPurple to MoPropPurpleDark, MoPropPink to MoPropPinkDark, MoPropTeal to MoPropTealDark),
            salt = 2,
        )
        val shoulder = Offset(CENTRE + s * rig.body.shoulderHalf, rig.body.shoulderY)
        val hand = if (s < 0f) rig.leftHand else rig.rightHand
        val wing = if (v.chance(50, salt = 5)) 1f else -1f
        // One bat only: the arm is not long enough for two to hang side by side without overlapping.
        val anchor = shoulder + (hand - shoulder) * 0.62f
        bat(anchor, (hand - shoulder) * 0.12f, 0.15f * wave(t, PERIOD / 2f), 1.1f, colour, dark, twitch(t, PERIOD, beat = 1.4f, offset = 0.55f), wing)
    }

    /** One bat asleep upside down at [anchor], [angle] radians off vertical, at [scale]. */
    private fun DrawScope.bat(
        anchor: Offset,
        footSpacing: Offset,
        angle: Float,
        scale: Float,
        colour: Color,
        dark: Color,
        stretch: Float,
        wing: Float,
    ) {
        // Down the hanging bat and across it, so everything about it swings as one piece.
        fun at(down: Float, across: Float): Offset {
            val d = down * scale
            val a = across * scale
            return anchor + Offset(sin(angle) * d + cos(angle) * a, cos(angle) * d - sin(angle) * a)
        }
        for (w in listOf(-1f, 1f)) {
            val foot = anchor + footSpacing * w
            val across = if (foot.x < anchor.x) -5f else 5f
            limb(foot, at(15f, across), dark, 4f * scale)
            limb(foot, foot + Offset(-2f, 3f), dark, 4f * scale)
        }
        // Two scalloped wings, because a bat wrapped in a cloak is only a blob at thumbnail size.
        for (w in listOf(-1f, 1f)) {
            val spread = if (w * wing > 0f) 1f + 0.2f * stretch else 1f
            shape(
                dark,
                at(12f, w * 6f * spread).pair(),
                at(25f, w * 19f * spread).pair(),
                at(37f, w * 14f * spread).pair(),
                at(47f, w * 20f * spread).pair(),
                at(54f, w * 6f * spread).pair(),
            )
        }
        oval(at(34f, 0f), 12f * scale, 21f * scale, colour)
        drawCircle(colour, 11f * scale, at(58f, 0f))
        for (w in listOf(-1f, 1f)) {
            // The ears point down: everything about him is upside down, and that is the joke.
            shape(
                colour,
                at(64f, w * 11f).pair(),
                at(64f, w * 3f).pair(),
                at(80f, w * 9f).pair(),
            )
            curve(at(58f, w * 8f), at(54f, w * 5f), at(58f, w * 2f), MoPropInk, 2f * scale)
        }
    }
}

/** A spider drops in beside his face on a thread, and he jumps every single time. */
private object SpiderDropsIn : MoAct("spooky_spider_drops_in", MoOccasion.Spooky) {
    private const val PERIOD = 6f

    private fun side(v: MoVariant) = if (v.chance(50, salt = 1)) -1f else 1f

    /** The jump: full at the drop, which is both the end and the start of the cycle. */
    private fun startle(p: Float): Float = when {
        p < 0.18f -> 1f - ease(p / 0.18f)
        p > 0.9f -> ease((p - 0.9f) / 0.1f)
        else -> 0f
    }

    /** How high the spider has climbed, 0 at eye level and 1 up near the ceiling. */
    private fun up(p: Float) = ease((p - 0.45f) / 0.15f) - ease((p - 0.85f) / 0.15f)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val s = side(v)
        val p = cycle(t, PERIOD)
        val jump = startle(p)
        val eye = MoEye(
            gaze = Offset(s * 0.95f, -0.8f * up(p)),
            iris = 0.76f - 0.26f * jump,
            scale = 1f + 0.06f * jump,
        )
        return moIdle(t, energy = 0.8f).copy(
            leftEye = eye,
            rightEye = eye,
            brows = MoBrows(MoBrowKind.Worried, lift = 3f + 4f * jump, skew = 3f),
            mouth = if (jump > 0.4f) MoMouth.Oh(size = 10f) else MoMouth.Wavy,
            leftArm = if (s < 0f) shield(jump) else across(jump),
            rightArm = if (s < 0f) across(jump) else shield(jump),
            tilt = -s * (3f + 3f * jump),
            offset = Offset(-s * (4f + 6f * jump), -10f * jump),
            shake = wave(t, 0.07f) * 1.2f * jump,
            flare = 8f * jump,
            blinks = false,
        )
    }

    /** The near mitten, up between him and it. */
    private fun shield(jump: Float) = MoArm(Offset(12f, -12f - 8f * jump), bend = 0.4f, inFront = true)

    private fun across(jump: Float) = MoArm(Offset(18f, 10f - 20f * jump), bend = 0.3f)

    override fun DrawScope.foreground(t: Float, v: MoVariant) {
        val s = side(v)
        val p = cycle(t, PERIOD)
        val climbed = up(p)
        val (colour, dark) = v.of(
            listOf(MoPropPurple to MoPropPurpleDark, MoPropTeal to MoPropTealDark, MoPropPink to MoPropPinkDark),
            salt = 2,
        )
        val anchorX = CENTRE + s * 124f
        // It only swings while it dangles; up at the top the thread is short and steady.
        val x = anchorX + 5f * wave(t, 1.9f) * (1f - climbed)
        val low = 288f - v.between(0f, 16f, salt = 3)
        val y = low - (low - 168f) * climbed + if (p > 0.9f) 6f * sin(startle(p) * PI.toFloat()) else 0f
        threadFromAbove(Offset(x, y - 12f), MoPropSilver, 2.5f, anchorX)

        val hello = if (v.chance(60, salt = 4)) -8f * pulse(t, 0.5f) * (1f - climbed) else 0f
        for (i in 0..3) {
            for (w in listOf(-1f, 1f)) {
                val knee = Offset(x + w * (17f + i), y - 14f + i * 6f)
                // The front leg on his side waves at him, which is what makes it friendly.
                val toe = Offset(x + w * (26f - i), y + 3f + i * 6f + if (i == 0 && w * s < 0f) hello else 0f)
                limb(Offset(x, y), knee, dark, 3.5f)
                limb(knee, toe, dark, 3.5f)
            }
        }
        oval(Offset(x, y), 16f, 13f, colour)
        for (w in listOf(-1f, 1f)) {
            val e = Offset(x + w * 6f, y - 3f)
            drawCircle(MoPropWhite, 4f, e)
            drawCircle(MoPropInk, 1.8f, e + Offset(-s * 1.5f, 0f))
        }
    }
}

/**
 * His first ride on a broom: a hand's width off the floor, gripping the stick, grinning through it.
 */
private object LearnersBroom : MoAct(
    "spooky_learners_broom",
    MoOccasion.Spooky,
    bodies = setOf(MoBodyKind.Tall),
) {
    private fun bristleSide(v: MoVariant) = if (v.chance(50, salt = 1)) -1f else 1f

    private fun hover(v: MoVariant) = v.between(24f, 36f, salt = 3)

    private fun dip(t: Float, v: MoVariant) =
        twitch(t, v.of(listOf(3.9f, 4.8f, 5.7f), salt = 4), beat = 0.7f, offset = 0.5f)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val b = bristleSide(v)
        val hover = hover(v)
        val dip = dip(t, v)
        // The lurch drops him most of the way to the floor, and never onto it.
        val height = hover + 4f * wave(t, 1.7f) - (hover - 7f) * dip
        val lookDown = twitch(t, 2.4f, beat = 0.5f, offset = 0.3f)
        // Wide enough that both mittens clear his silhouette and read as a grip on the stick.
        val grip = MoArm(Offset(12f, 48f), bend = 0.3f)
        val eye = MoEye(gaze = Offset(0f, 0.85f * lookDown), iris = 0.76f - 0.26f * dip, scale = 1f + 0.05f * dip)
        return moIdle(t, energy = 1.3f).copy(
            leftEye = eye,
            rightEye = eye,
            brows = MoBrows(MoBrowKind.Raised, lift = 2f + 5f * dip),
            mouth = if (dip > 0.35f) MoMouth.Gritted else MoMouth.Grin(open = 0.8f),
            leftArm = grip,
            rightArm = grip,
            armsInFront = true,
            legs = MoLegs(MoLeg(swing = 5f * wave(t, 0.9f)), MoLeg(swing = 5f * wave(t + 0.45f, 0.9f))),
            tilt = 3.5f * wave(t, 1.3f) * (1f - dip) + b * 5f * dip,
            offset = Offset(3f * wave(t, 2.6f), -height),
            flare = 6f + 10f * dip,
            shake = wave(t, 0.08f) * 1.5f * dip,
        )
    }

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val b = bristleSide(v)
        // The stick runs under him through both mittens, so it rises and lurches with him.
        val y = rig.leftHand.y
        val (bristle, bristleDark) = v.of(
            listOf(MoPropSand to MoPropWood, MoPropGold to MoPropGoldDark, MoPropPink to MoPropPinkDark),
            salt = 2,
        )
        limb(Offset(CENTRE - b * 146f, y + 3f), Offset(CENTRE + b * 118f, y + 3f), MoPropWoodDark, 11f)
        limb(Offset(CENTRE - b * 146f, y), Offset(CENTRE + b * 118f, y), MoPropWood, 11f)
        shape(
            bristleDark,
            CENTRE + b * 114f to y - 4f, CENTRE + b * 144f to y - 20f,
            CENTRE + b * 148f to y + 28f, CENTRE + b * 114f to y + 14f,
        )
        shape(
            bristle,
            CENTRE + b * 114f to y - 9f, CENTRE + b * 144f to y - 25f,
            CENTRE + b * 148f to y + 23f, CENTRE + b * 114f to y + 9f,
        )
        limb(Offset(CENTRE + b * 120f, y - 6f), Offset(CENTRE + b * 142f, y - 13f), bristleDark, 3f)
        limb(Offset(CENTRE + b * 120f, y + 5f), Offset(CENTRE + b * 142f, y + 12f), bristleDark, 3f)
        block(Offset(CENTRE + b * 112f, y), 12f, 26f, v.of(listOf(MoPropPurple, MoPropTeal, MoPropRed), salt = 6), corner = 4f)
        // His mittens go back on over the stick, so he is holding it and not hiding behind it.
        mitten(rig, rig.leftHand, -1f)
        mitten(rig, rig.rightHand, 1f)
        if (v.chance(35, salt = 5)) {
            for (i in 0..2) {
                val fall = cycle(t, 1.6f, offset = i / 3f)
                sparkle(Offset(CENTRE + b * (118f + 22f * fall), y + 14f + fall * 22f), 9f * (1f - fall), 1f, MoPropGold)
            }
        }
    }

    override val ownsTheFloor get() = true
}

/** His little cousins have come to visit: cool wisps circling his feet, one perched on his mitten. */
private object WispCousins : MoAct("spooky_wisp_cousins", MoOccasion.Spooky, intensity = MoIntensity.Mild) {
    private const val PERIOD = 5.2f

    /** Low around his base, below the mouth of both bodies, so the ring never crosses his face. */
    private const val RING_Y = 392f

    private fun count(v: MoVariant) = 2 + v.pick(2, salt = 2)

    private fun turning(v: MoVariant) = if (v.chance(50, salt = 3)) 1f else -1f

    private fun host(v: MoVariant) = if (v.chance(50, salt = 4)) -1f else 1f

    // Cool fire, and never plain blue: a small blue tongue with a pale core is read as a raindrop.
    private fun colours(v: MoVariant) = v.of(
        listOf(MoPropTeal to MoPropMint, MoPropPurple to MoPropCream, MoPropGreen to MoPropMint),
        salt = 1,
    )

    private fun angle(t: Float, i: Int, v: MoVariant) =
        turning(v) * 2f * PI.toFloat() * cycle(t, PERIOD, offset = i.toFloat() / count(v) + 0.2f)

    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose {
        val h = host(v)
        val a = angle(t, 0, v)
        // His eyes sweep with the wisp crossing in front and settle while it is behind him. The
        // quarter guards the division and keeps the sweep from snapping as it passes the edges.
        val follow = 0.85f * cos(a) * sin(a) / (abs(sin(a)) + 0.25f)
        val lookUp = twitch(t, PERIOD, beat = 1f, offset = 0.5f)
        val eye = MoEye(
            gaze = Offset(
                follow * (1f - lookUp) + h * 0.9f * lookUp,
                0.55f * (1f - lookUp) - 0.45f * lookUp,
            ),
        )
        val offered = MoArm(Offset(34f, -40f), bend = 0.5f)
        return moIdle(t, energy = 1.1f).copy(
            leftEye = eye,
            rightEye = eye,
            mouth = MoMouth.Grin(open = 0.6f),
            leftArm = if (h < 0f) offered else MoArm.Down,
            rightArm = if (h < 0f) MoArm.Down else offered,
            tilt = 1.5f * wave(t, 2.6f),
        )
    }

    override fun DrawScope.world(t: Float, v: MoVariant) = ring(t, v, inFront = false)

    override fun DrawScope.foreground(t: Float, v: MoVariant) = ring(t, v, inFront = true)

    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) {
        val (colour, core) = colours(v)
        val hand = if (host(v) < 0f) rig.leftHand else rig.rightHand
        wisp(hand + Offset(0f, -22f - 4f * pulse(t, 1.6f)), 16f, 12f + 4f * wave(t, 0.7f), colour, core)
    }

    /**
     * Half the ring: the far wisps go behind him and the near ones in front. They hand over at the
     * two ends of the circle, well outside his silhouette, so nothing ever pops into view.
     */
    private fun DrawScope.ring(t: Float, v: MoVariant, inFront: Boolean) {
        val (colour, core) = colours(v)
        for (i in 0 until count(v)) {
            val a = angle(t, i, v)
            if ((sin(a) >= 0f) != inFront) continue
            val base = Offset(CENTRE + cos(a) * 132f, RING_Y + sin(a) * 16f)
            // The near ones come bigger, which is what turns a flat circle into a ring around him.
            val size = (19f + 3f * sin(a)) * (1f + 0.08f * wave(t + i, 0.31f))
            wisp(base, size, 14f + 4f * wave(t + i, 0.7f), colour, core)
        }
    }

    /** One will-o'-the-wisp: a small flame with a face, leaning right like his own tip. */
    private fun DrawScope.wisp(base: Offset, size: Float, lean: Float, colour: Color, core: Color) {
        withTransform({ rotate(lean, base) }) {
            flameTongue(base, size, colour, core)
            // The face is what keeps him a cousin and not a falling drop, so it is big and central.
            drawCircle(MoPropInk, size * 0.24f, base + Offset(-size * 0.34f, -size * 0.92f))
            drawCircle(MoPropInk, size * 0.24f, base + Offset(size * 0.34f, -size * 0.92f))
            curve(
                base + Offset(-size * 0.22f, -size * 0.52f),
                base + Offset(0f, -size * 0.36f),
                base + Offset(size * 0.22f, -size * 0.52f),
                MoPropInk,
                size * 0.09f,
            )
        }
    }

    override val sparkles get() = false
}

internal val spookyActs1: List<MoAct> = listOf(
    CarvedHimself,
    GhostWarmsItsHands,
    BatAsleepOnHisArm,
    SpiderDropsIn,
    LearnersBroom,
    WispCousins,
)
