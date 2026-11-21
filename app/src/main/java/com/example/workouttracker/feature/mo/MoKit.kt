package com.example.workouttracker.feature.mo

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import com.example.workouttracker.core.ui.theme.MoColdInk
import com.example.workouttracker.core.ui.theme.MoEyeWhite
import com.example.workouttracker.core.ui.theme.MoFlameBelly
import com.example.workouttracker.core.ui.theme.MoFlameLedge
import com.example.workouttracker.core.ui.theme.MoFlameMiddle
import com.example.workouttracker.core.ui.theme.MoFlameOuter
import com.example.workouttracker.core.ui.theme.MoFoam
import com.example.workouttracker.core.ui.theme.MoGloss
import com.example.workouttracker.core.ui.theme.MoGoldenBelly
import com.example.workouttracker.core.ui.theme.MoGoldenLedge
import com.example.workouttracker.core.ui.theme.MoGoldenMiddle
import com.example.workouttracker.core.ui.theme.MoGoldenOuter
import com.example.workouttracker.core.ui.theme.MoInk
import com.example.workouttracker.core.ui.theme.MoPaleBelly
import com.example.workouttracker.core.ui.theme.MoPaleLedge
import com.example.workouttracker.core.ui.theme.MoPaleMiddle
import com.example.workouttracker.core.ui.theme.MoPaleOuter
import com.example.workouttracker.core.ui.theme.MoPropGold
import com.example.workouttracker.core.ui.theme.MoQueasyBelly
import com.example.workouttracker.core.ui.theme.MoQueasyLedge
import com.example.workouttracker.core.ui.theme.MoQueasyMiddle
import com.example.workouttracker.core.ui.theme.MoQueasyOuter
import com.example.workouttracker.core.ui.theme.MoRageBelly
import com.example.workouttracker.core.ui.theme.MoRageLedge
import com.example.workouttracker.core.ui.theme.MoRageMiddle
import com.example.workouttracker.core.ui.theme.MoRageOuter
import com.example.workouttracker.core.ui.theme.MoSlate
import com.example.workouttracker.core.ui.theme.MoSlateGloss
import com.example.workouttracker.core.ui.theme.MoSlateLedge
import com.example.workouttracker.core.ui.theme.MoSpark
import com.example.workouttracker.core.ui.theme.MoTear
import com.example.workouttracker.core.ui.theme.MoTongue
import com.example.workouttracker.domain.mo.MoNeglect
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * The kit every act is built from. An act never draws Mo himself: it describes a pose in the words
 * below, and the kit draws him from it on whichever body his state gives him. That is what keeps
 * two hundred acts looking like one character.
 *
 * Coordinates are his design space: 230 wide, centre x = 115 (CENTRE), feet on y = 425 (FEET), up is
 * smaller y. A Blazing Mo's view spans x from -45 to 275 and y from 0 to 431; keep everything an
 * act draws inside that.
 */

/** The bodies Mo can have. Blazing and Steady are Tall, Dimming is Low, Cold is the Droplet. */
internal enum class MoBodyKind { Tall, Low, Droplet, Husk }

/** Where a body keeps its parts, so one pose fits them all. */
internal class MoBody(
    val kind: MoBodyKind,
    val outer: Outline,
    val middle: Outline?,
    val belly: Outline?,
    val top: Float,
    val pivot: Float,
    val eyeY: Float,
    val leftEyeX: Float,
    val rightEyeX: Float,
    val eyeRadius: Float,
    val mouth: Offset,
    val shoulderY: Float,
    val shoulderHalf: Float,
    val legTop: Float,
    /** The lowest point of his body, which rests on the floor when he sits. */
    val bottom: Float,
    val leftLegX: Float,
    val rightLegX: Float,
    val leftFootX: Float,
    val rightFootX: Float,
    val glosses: List<Triple<Offset, Offset, Offset>>,
    val defaultPalette: MoPalette,
)

/** The colours of one Mo. Acts choose among [MoPalettes]; they never build their own. */
internal data class MoPalette(
    val outer: Color,
    val middle: Color,
    val belly: Color,
    val ledge: Color,
    val gloss: Color,
    val limb: Color,
    val feet: Color,
    val ink: Color,
)

internal object MoPalettes {
    val Warm = MoPalette(MoFlameOuter, MoFlameMiddle, MoFlameBelly, MoFlameLedge, MoGloss, MoFlameOuter, MoFlameOuter, MoInk)

    /** Dimming: the cold has reached his feet and the band at his base. */
    val Fading = Warm.copy(ledge = MoSlateLedge, feet = MoSlateLedge)

    /** Anger: his fire pushed from orange into red. */
    val Rage = MoPalette(MoRageOuter, MoRageMiddle, MoRageBelly, MoRageLedge, MoGloss, MoRageOuter, MoRageOuter, MoInk)

    /** Fear: washed out, as if the blood had left his face. */
    val Pale = MoPalette(MoPaleOuter, MoPaleMiddle, MoPaleBelly, MoPaleLedge, MoGloss, MoPaleOuter, MoPaleOuter, MoInk)

    /** Unwell or queasy: off-colour, with a greenish belly. */
    val Queasy = MoPalette(MoQueasyOuter, MoQueasyMiddle, MoQueasyBelly, MoQueasyLedge, MoGloss, MoQueasyOuter, MoQueasyOuter, MoInk)

    /** A celebration: brighter and more golden than an ordinary day. */
    val Golden = MoPalette(MoGoldenOuter, MoGoldenMiddle, MoGoldenBelly, MoGoldenLedge, MoGloss, MoGoldenOuter, MoGoldenOuter, MoInk)

    /** Cold: all slate. Only the Droplet wears it. */
    val Slate = MoPalette(MoSlate, MoSlate, MoSlate, MoSlateLedge, MoSlateGloss, MoSlateLedge, MoSlateLedge, MoColdInk)
}

internal object MoBodies {
    val Tall = MoBody(
        MoBodyKind.Tall, OUTER_FLAME, MIDDLE_FLAME, BELLY_FLAME,
        top = 82f, pivot = 250f, eyeY = 284f, leftEyeX = 71f, rightEyeX = 155f, eyeRadius = 27f,
        mouth = Offset(113f, 322f), shoulderY = 330f, shoulderHalf = 92f,
        legTop = 378f, bottom = 394f, leftLegX = 76f, rightLegX = 126f, leftFootX = 85f, rightFootX = 147f,
        glosses = listOf(
            Triple(Offset(69f, 151f), Offset(78f, 126f), Offset(92f, 114f)),
            Triple(Offset(31f, 260f), Offset(38f, 236f), Offset(51f, 219f)),
        ),
        defaultPalette = MoPalettes.Warm,
    )
    val Low = MoBody(
        MoBodyKind.Low, LOW_OUTER, LOW_MIDDLE, LOW_BELLY,
        top = 150f, pivot = 262f, eyeY = 304f, leftEyeX = 74f, rightEyeX = 152f, eyeRadius = 25f,
        mouth = Offset(113f, 342f), shoulderY = 342f, shoulderHalf = 92f,
        legTop = 384f, bottom = 395f, leftLegX = 76f, rightLegX = 126f, leftFootX = 85f, rightFootX = 147f,
        glosses = listOf(Triple(Offset(40f, 262f), Offset(50f, 236f), Offset(66f, 218f))),
        defaultPalette = MoPalettes.Fading,
    )
    val Droplet = MoBody(
        MoBodyKind.Droplet, DROPLET, null, null,
        top = 157f, pivot = 270f, eyeY = 296f, leftEyeX = 78f, rightEyeX = 150f, eyeRadius = 25f,
        // As high under his eyes as a mouth goes: the core he holds is drawn over whatever reaches it.
        mouth = Offset(114f, 327f), shoulderY = 334f, shoulderHalf = 82f,
        legTop = 388f, bottom = 400f, leftLegX = 80f, rightLegX = 122f, leftFootX = 88f, rightFootX = 142f,
        glosses = listOf(Triple(Offset(58f, 270f), Offset(66f, 248f), Offset(80f, 232f))),
        defaultPalette = MoPalettes.Slate,
    )

    fun of(kind: MoBodyKind): MoBody = when (kind) {
        MoBodyKind.Tall -> Tall
        MoBodyKind.Low -> Low
        MoBodyKind.Droplet, MoBodyKind.Husk -> Droplet
    }

    /** The bodies that still burn, which is what most acts are written for. */
    val Burning = setOf(MoBodyKind.Tall, MoBodyKind.Low)
}

// --------------------------------------------------------------------------------------- the face

internal enum class MoEyeShape {
    /** His ordinary eye: white, a big dark iris, two highlights. Every field of [MoEye] applies. */
    Round,

    /** Squeezed shut in joy: an arc like a little hill. */
    Happy,

    /** Shut in sleep or contentment: an arc like a little bowl. */
    Shut,

    /** Knocked out: a cross. */
    Cross,

    /** Dizzy: a turning spiral. */
    Spiral,

    /** Delighted: a gold star where the iris was. */
    Star,

    /** Smitten: a heart where the iris was. */
    Heart,
}

/**
 * One eye. [gaze] runs from -1 to 1 on each axis, x positive to the viewer's right, y positive down.
 * [lid] slants the upper lid: positive drops its inner end into a glare, negative drops its outer
 * end into sadness. [lidDrop] lowers the whole lid, from 0 to 1, for sleepiness or suspicion.
 * [iris] is the iris size relative to the eye; about 0.5 looks startled, 0.3 terrified.
 */
internal data class MoEye(
    val shape: MoEyeShape = MoEyeShape.Round,
    val open: Float = 1f,
    val gaze: Offset = Offset.Zero,
    val iris: Float = 0.76f,
    val lid: Float = 0f,
    val lidDrop: Float = 0f,
    val scale: Float = 1f,
    /** A tear rolling down from this eye, as its progress from 0 to 1, or null for none. */
    val tear: Float? = null,
)

internal enum class MoBrowKind { None, Angry, Worried, Raised, Flat }

/** [lift] raises both brows in design units; [skew] raises the right one further, for a quizzical look. */
internal data class MoBrows(val kind: MoBrowKind = MoBrowKind.None, val lift: Float = 0f, val skew: Float = 0f)

/** Everything his mouth can do. Sizes are in design units around the body's mouth position. */
internal sealed interface MoMouth {
    data class Smile(val width: Float = 17f, val depth: Float = 15f) : MoMouth
    data class Grin(val open: Float = 1f, val tongue: Boolean = true) : MoMouth
    data class Frown(val width: Float = 11f) : MoMouth
    data class Flat(val width: Float = 14f) : MoMouth
    data class Oh(val size: Float = 9f) : MoMouth

    /** A full row of clenched teeth: effort, anger, or a forced smile. */
    data object Gritted : MoMouth

    /** A wide white beam of a smile. */
    data object Beam : MoMouth

    /** A queasy wobble of a line. */
    data object Wavy : MoMouth

    /** A frightened zigzag. */
    data object Zigzag : MoMouth
    data object TongueOut : MoMouth

    /** Open, with a drop of drool forming and falling. */
    data object Drool : MoMouth
    data class Chew(val open: Float = 0.5f) : MoMouth
    data class Yawn(val open: Float = 1f) : MoMouth

    /** Pursed to one side, as for whistling or blowing. */
    data object Whistle : MoMouth
    data object Pout : MoMouth
    data object Smirk : MoMouth

    /** No mouth drawn: something the act draws itself covers it. */
    data object Hidden : MoMouth
}

// --------------------------------------------------------------------------------------- the limbs

/**
 * One arm, as where its hand is relative to its shoulder: x positive reaches outward, away from the
 * body, negative reaches across it; y positive is down. [bend] bows the elbow outward (positive) or
 * inward. The same arm works for either side and on every body.
 *
 * [inFront] puts this one arm over his body or behind it, whatever [MoPose.armsInFront] says for
 * both: a hand on his cheek while the other hangs at his side. A hand that reaches across his body
 * behind it is simply gone, and whatever it holds floats.
 *
 * [warm] takes the cold off this hand: at 1 a Dimming Mo's mitten is drawn without its slate tip.
 * It is for a hand that lies on his face, where the dark tip reads as a hole in it, or as a pupil
 * on a shut eye. Raise it as the hand comes up, so the tip dwindles on the way.
 */
internal data class MoArm(val reach: Offset, val bend: Float = 0.22f, val inFront: Boolean? = null, val warm: Float = 0f) {
    companion object {
        val Down = MoArm(Offset(4f, 36f), bend = 0.1f)
        val Up = MoArm(Offset(30f, -62f))
        val Out = MoArm(Offset(52f, -4f), bend = 0.1f)
        val Hip = MoArm(Offset(-6f, 30f), bend = 0.75f)
        /**
         * A mitten on his cheek: beside his mouth and clear of his eye, on either burning body. The
         * droplet's eyes sit lower against his shoulders, so there this hand lies on an eye: a cold
         * act places its own.
         */
        val Cheek = MoArm(Offset(-30f, -6f), bend = 0.5f)
        val Front = MoArm(Offset(-52f, 34f), bend = 0.3f)
        val Flex = MoArm(Offset(34f, -52f), bend = 0.9f)
    }
}

/** One leg: [lift] raises the foot, [swing] moves it sideways (positive outward). */
internal data class MoLeg(val lift: Float = 0f, val swing: Float = 0f)

internal data class MoLegs(val left: MoLeg = MoLeg(), val right: MoLeg = MoLeg(), val sitting: Boolean = false)

/**
 * Everything about Mo at one moment. Start from [moIdle] so he keeps breathing, swaying and blinking,
 * and change only what the act is about.
 *
 * [sway] leans his flame tip sideways and [flare] shoots it upward, both in design units. [squash]
 * stretches him (positive) or squashes him (negative) from the feet. [tilt] leans the whole body in
 * degrees, [offset] moves it, and [shake] is a sideways jitter already multiplied by time.
 */
internal data class MoPose(
    val palette: MoPalette? = null,
    val leftEye: MoEye = MoEye(),
    val rightEye: MoEye = MoEye(),
    val brows: MoBrows = MoBrows(),
    val mouth: MoMouth = MoMouth.Smile(),
    val leftArm: MoArm = MoArm.Down,
    val rightArm: MoArm = MoArm.Down,
    val armsInFront: Boolean = false,
    val legs: MoLegs = MoLegs(),
    val sway: Float = 0f,
    val flare: Float = 0f,
    val squash: Float = 0f,
    val tilt: Float = 0f,
    val offset: Offset = Offset.Zero,
    val shake: Float = 0f,
    val blinks: Boolean = true,
) {
    /** Both eyes at once, which is what most expressions want. */
    fun eyes(eye: MoEye): MoPose = copy(leftEye = eye, rightEye = eye)

    fun arms(arm: MoArm): MoPose = copy(leftArm = arm, rightArm = arm)
}

/**
 * Mo alive and doing nothing in particular: the base of every act. [energy] scales how lively the
 * flame is, from about 0.4 for tired to 2 for excited. At t = 0 it is his resting pose.
 */
internal fun moIdle(t: Float, energy: Float = 1f): MoPose = MoPose(
    sway = (wave(t, 2.9f / energy) * 6f + wave(t, 0.83f) * 1.5f) * energy,
    squash = wave(t, 3.2f) * 0.014f,
)

/**
 * Where his parts ended up, for whatever the act puts in his hands or around him. Positions are in
 * the same space the act's [MoAct.held] draws in, so they follow his tilt, hop and shake.
 */
internal class MoRig(
    val body: MoBody,
    val palette: MoPalette,
    val leftHand: Offset,
    val rightHand: Offset,
    val leftEye: Offset,
    val rightEye: Offset,
    val mouth: Offset,
    val tip: Offset,
    val leftFoot: Offset,
    val rightFoot: Offset,
    /**
     * How far a hop or a leap has him off the floor right now. Everything drawn in `held` leaves
     * the floor with him. A thing that stands there and that his hand only touches, a book on its
     * stand, is drawn this much lower, and stays put while his hand comes away from it.
     *
     * That holds for a pose that neither tilts nor stretches him: `held` draws inside his tilt and
     * his squash, where straight down is no longer the way back to the floor.
     */
    val lift: Float = 0f,
)

/**
 * Draws Mo in [pose] on [body] and hands the result to [held], inside his own transform. A neglected
 * mouth always shows through, whatever the act wanted it to do, unless the act hides the mouth.
 */
internal fun DrawScope.drawPosed(
    body: MoBody,
    pose: MoPose,
    t: Float,
    teeth: MoNeglect? = null,
    lift: Float = 0f,
    held: DrawScope.(MoRig) -> Unit = {},
) {
    val palette = pose.palette ?: body.defaultPalette
    val flame: (Offset) -> Offset = { point ->
        val height = ((body.pivot - point.y) / (body.pivot - body.top)).coerceIn(0f, 1.2f)
        Offset(point.x + pose.sway * height * height, point.y - pose.flare * height * height)
    }
    val feet = Offset(CENTRE, FEET)

    // Sitting, he comes down until his body rests on the floor, and his feet stick out in front of it.
    val seat = if (pose.legs.sitting) FEET - SEAT_GAP - body.bottom else 0f

    withTransform({
        translate(pose.offset.x + pose.shake, pose.offset.y + seat)
        rotate(pose.tilt, feet)
        scale(1f - pose.squash / 2f, 1f + pose.squash, feet)
    }) {
        val leftHand = hand(body, pose.leftArm, side = -1f)
        val rightHand = hand(body, pose.rightArm, side = 1f)
        val arms = listOf(pose.leftArm to -1f, pose.rightArm to 1f)
        val (front, behind) = arms.partition { (arm, _) -> arm.inFront ?: pose.armsInFront }
        behind.forEach { (arm, side) -> arm(body, arm, side, palette) }
        val leftFoot = if (pose.legs.sitting) sittingFoot(body, pose.legs.left, -1f, seat) else leg(body, pose.legs.left, -1f, palette)
        val rightFoot = if (pose.legs.sitting) sittingFoot(body, pose.legs.right, 1f, seat) else leg(body, pose.legs.right, 1f, palette)

        body(body.outer.toPath(flame), palette.outer, palette.ledge, lift = 11f)
        body.middle?.let { drawPath(it.toPath(flame), palette.middle) }
        // The belly holds the face, so it follows the flame only half as far.
        body.belly?.let { belly ->
            drawPath(belly.toPath { point -> flame(point).let { Offset((it.x + point.x) / 2f, (it.y + point.y) / 2f) } }, palette.belly)
        }
        body.glosses.forEachIndexed { index, (from, control, to) ->
            curve(from, control, to, palette.gloss.copy(alpha = if (index == 0) 1f else 0.85f), 8.5f, if (index == 0) flame else { p -> p })
        }
        if (pose.legs.sitting) {
            oval(leftFoot, 21f, 13f, palette.feet)
            oval(rightFoot, 21f, 13f, palette.feet)
        }

        val blink = if (pose.blinks) blink(t) else 1f
        val left = Offset(body.leftEyeX, body.eyeY)
        val right = Offset(body.rightEyeX, body.eyeY)
        eye(left, pose.leftEye, side = -1f, body.eyeRadius, palette.ink, blink)
        eye(right, pose.rightEye, side = 1f, body.eyeRadius, palette.ink, blink)
        brows(body, pose.brows, palette.ink)
        when {
            pose.mouth == MoMouth.Hidden -> Unit
            teeth != null -> when (body.kind) {
                MoBodyKind.Tall -> teeth(body.mouth + Offset(0f, 4f), teeth, t, width = 56f)
                MoBodyKind.Low -> teeth(body.mouth + Offset(0f, 4f), teeth, t, width = 44f)
                // Between his eyes and his core there is room for a small mouth only.
                MoBodyKind.Droplet, MoBodyKind.Husk -> teeth(body.mouth - Offset(0f, 1f), teeth, t, width = 36f)
            }
            else -> mouth(body.mouth, pose.mouth, palette.ink, t)
        }

        if (body.kind == MoBodyKind.Droplet) {
            // His fire is now a core he has to hold. It never goes out while he is Cold.
            val glow = 1f + wave(t, 2.4f) * 0.07f
            drawCircle(MoFlameOuter, 25f * glow, Offset(115f, 366f))
            drawCircle(MoSpark, 17f * glow, Offset(115f, 366f))
            drawCircle(MoFlameBelly, 8f * glow, Offset(115f, 368f))
        }
        front.forEach { (arm, side) -> arm(body, arm, side, palette) }

        held(
            MoRig(
                body = body,
                palette = palette,
                leftHand = leftHand,
                rightHand = rightHand,
                leftEye = left,
                rightEye = right,
                mouth = body.mouth,
                tip = flame(Offset(128f, body.top + 12f)),
                leftFoot = leftFoot,
                rightFoot = rightFoot,
                lift = lift,
            ),
        )
    }
}

// How far above the floor his body rests when he sits: the ledge at its base still shows.
private const val SEAT_GAP = 3f

private fun shoulder(body: MoBody, side: Float) = Offset(CENTRE + side * body.shoulderHalf, body.shoulderY)

private fun hand(body: MoBody, arm: MoArm, side: Float): Offset =
    shoulder(body, side) + Offset(side * arm.reach.x, arm.reach.y)

/**
 * Where an arm's elbow is. It bows away from his body for a positive bend, which is what shows on
 * an arm that hangs or is raised. On an arm that runs level that bow falls along the arm itself and
 * shows nothing, so there the elbow hangs below it instead: his shoulders are as high as his mouth,
 * and a hand at his mouth with no elbow under it is a straight rod across his face.
 */
internal fun elbow(body: MoBody, arm: MoArm, side: Float): Offset {
    val from = shoulder(body, side)
    val to = hand(body, arm, side)
    val length = (to - from).getDistance()
    // From 0 for an arm that hangs or is raised to 1 for one that points straight sideways, and
    // close to 0 for everything in between, so only an arm that really runs level is changed by it.
    val level = if (length > 0f) ((to.x - from.x) / length).let { it * it * it * it } else 0f
    val hang = ELBOW_SAG + (ELBOW_HANG - ELBOW_SAG) * level
    return (from + to) / 2f + Offset(side * arm.bend * length * 0.5f, abs(arm.bend) * length * hang)
}

// How far below the middle of his arm the elbow is, as a share of the arm's length at full bend:
// a slight sag on any arm, and a real hang on a level one.
private const val ELBOW_SAG = 0.12f
private const val ELBOW_HANG = 0.5f

private fun DrawScope.arm(body: MoBody, arm: MoArm, side: Float, palette: MoPalette) {
    val from = shoulder(body, side)
    val to = hand(body, arm, side)
    val elbow = elbow(body, arm, side)
    limb(from, elbow, palette.limb, 21f)
    limb(elbow, to, palette.limb, 20f)
    mitten(palette, to, side, arm.warm)
}

/**
 * His mitten at [at], with the cold tip a Dimming Mo's has, less what [warm] takes off it.
 * [side] is -1 for his left hand, 1 for his right.
 */
internal fun DrawScope.mitten(palette: MoPalette, at: Offset, side: Float, warm: Float = 0f) {
    drawCircle(palette.limb, 12.5f, at)
    val tip = 10.5f * (1f - warm.coerceIn(0f, 1f))
    if (palette.feet != palette.limb && tip > 0.5f) drawCircle(palette.feet, tip, at + Offset(side, 3f))
}

/**
 * His mitten once more, over something an act has put in it. The kit draws his hand before the act
 * draws what he holds, so the thing would lie on his hand; with the mitten drawn again on top, it
 * sits in his grip. [at] is where that hand is, from the rig.
 */
internal fun DrawScope.mitten(rig: MoRig, at: Offset, side: Float, warm: Float = 0f) = mitten(rig.palette, at, side, warm)

/** The same for a hand that is where the rig says it is. */
internal fun DrawScope.grippingMitten(rig: MoRig, side: Float, warm: Float = 0f) =
    mitten(rig, if (side < 0f) rig.leftHand else rig.rightHand, side, warm)

/** Where a foot lies while he sits: on the floor, a little apart, however far his body came down. */
private fun sittingFoot(body: MoBody, leg: MoLeg, side: Float, seat: Float): Offset {
    val footX = (if (side < 0f) body.leftFootX else body.rightFootX) + side * leg.swing
    return Offset(footX + side * 6f, 414f - seat - leg.lift)
}

private fun DrawScope.leg(body: MoBody, leg: MoLeg, side: Float, palette: MoPalette): Offset {
    val legX = if (side < 0f) body.leftLegX else body.rightLegX
    val footX = (if (side < 0f) body.leftFootX else body.rightFootX) + side * leg.swing
    val foot = Offset(footX, 413f - leg.lift)
    withTransform({ translate(side * leg.swing * 0.5f, -leg.lift) }) {
        drawRoundRect(palette.feet, Offset(legX, body.legTop), Size(30f, 414f - body.legTop), CornerRadius(8f))
    }
    oval(foot, 19f, 12f, palette.feet)
    return foot
}

private fun DrawScope.eye(centre: Offset, eye: MoEye, side: Float, bodyRadius: Float, ink: Color, blink: Float) {
    val r = bodyRadius * eye.scale
    when (eye.shape) {
        MoEyeShape.Happy -> curve(centre + Offset(-r * 0.72f, r * 0.25f), centre + Offset(0f, -r * 0.95f), centre + Offset(r * 0.72f, r * 0.25f), ink, 6.5f)
        MoEyeShape.Shut -> curve(centre + Offset(-r * 0.72f, -r * 0.1f), centre + Offset(0f, r * 0.8f), centre + Offset(r * 0.72f, -r * 0.1f), ink, 6f)
        MoEyeShape.Cross -> {
            limb(centre + Offset(-r * 0.55f, -r * 0.55f), centre + Offset(r * 0.55f, r * 0.55f), ink, 6.5f)
            limb(centre + Offset(r * 0.55f, -r * 0.55f), centre + Offset(-r * 0.55f, r * 0.55f), ink, 6.5f)
        }
        else -> roundEye(centre, eye, side, r, ink, blink)
    }
    eye.tear?.let { tear(centre + Offset(side * r * 0.75f, r * 0.6f), it.coerceIn(0f, 1f), side) }
}

private fun DrawScope.roundEye(centre: Offset, eye: MoEye, side: Float, r: Float, ink: Color, blink: Float) {
    val glare = max(eye.lid, 0f)
    val sorrow = max(-eye.lid, 0f)
    val innerX = centre.x - side * (r + 5f)
    val outerX = centre.x + side * (r + 5f)
    val innerY = centre.y - r * (1.25f - 1.13f * glare)
    val outerY = centre.y - r * (1.25f - 1.13f * sorrow)
    val lids = Path().apply {
        moveTo(outerX, outerY)
        lineTo(innerX, innerY)
        lineTo(innerX, centre.y + r + 6f)
        lineTo(outerX, centre.y + r + 6f)
        close()
    }
    val openness = max(eye.open * blink, 0.08f)
    val lidHeight = 1.44f * eye.lidDrop.coerceIn(0f, 1f) - 1f
    // An eye with its lid raised keeps the whole antialiased edge of its blink.
    val lidTop = if (eye.lidDrop > 0f) centre.y + r * openness * lidHeight else centre.y - r
    clipPath(lids) {
        clipRect(left = centre.x - r - 6f, top = lidTop, right = centre.x + r + 6f, bottom = centre.y + r + 6f) {
            withTransform({ scale(1f, openness, centre) }) {
                drawCircle(MoEyeWhite, r, centre)
                val pupil = centre + Offset(eye.gaze.x, eye.gaze.y) * (r * 0.24f)
                when (eye.shape) {
                    MoEyeShape.Spiral -> spiral(centre, r * 0.78f, ink)
                    MoEyeShape.Star -> sparkle(centre, r * 0.92f, 1f, MoPropGold)
                    MoEyeShape.Heart -> heart(centre + Offset(0f, r * 0.05f), r * 0.8f, MoTongue)
                    else -> {
                        drawCircle(ink, r * eye.iris, pupil)
                        drawCircle(MoEyeWhite, r * eye.iris * 0.32f, pupil + Offset(-r * eye.iris * 0.34f, -r * eye.iris * 0.43f))
                        drawCircle(MoEyeWhite, r * eye.iris * 0.18f, pupil + Offset(r * eye.iris * 0.43f, r * eye.iris * 0.43f))
                    }
                }
            }
        }
        // The edge follows the chord of the blinking eye, including where a slanted lid hides it.
        // The edge comes in as the lid comes down. On a lid that is barely lowered it would be a
        // short dash across the top of a wide eye, which reads as a second little brow.
        val lidEdge = 3.5f * ease((eye.lidDrop - 0.1f) / 0.15f)
        if (lidEdge > 0f) {
            val halfWidth = r * sqrt(1f - lidHeight * lidHeight)
            limb(Offset(centre.x - halfWidth, lidTop), Offset(centre.x + halfWidth, lidTop), ink, lidEdge)
        }
    }
}

private fun DrawScope.spiral(centre: Offset, radius: Float, ink: Color) {
    val points = (0..40).map { step ->
        val turn = step / 40f
        val angle = turn * 5f * PI.toFloat()
        centre + Offset(cos(angle), sin(angle)) * (radius * turn)
    }
    polyline(ink, 4f, points)
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

private fun DrawScope.brows(body: MoBody, brows: MoBrows, ink: Color) {
    if (brows.kind == MoBrowKind.None) return
    val r = body.eyeRadius
    for ((x, side) in listOf(body.leftEyeX to -1f, body.rightEyeX to 1f)) {
        val lift = brows.lift + if (side > 0f) brows.skew else 0f
        val y = body.eyeY - r - 9f - lift
        val inner = Offset(x - side * (r * 0.75f), y)
        val outer = Offset(x + side * (r * 0.75f), y)
        when (brows.kind) {
            MoBrowKind.Angry -> limb(outer + Offset(0f, -9f), inner + Offset(0f, 9f), ink, 8.5f)
            MoBrowKind.Worried -> curve(outer + Offset(0f, 8f), Offset(x, y - 7f), inner + Offset(0f, -7f), ink, 5.5f)
            MoBrowKind.Raised -> curve(outer + Offset(0f, 2f), Offset(x, y - 13f), inner + Offset(0f, 2f), ink, 5.5f)
            MoBrowKind.Flat -> limb(outer, inner, ink, 6.5f)
            MoBrowKind.None -> Unit
        }
    }
}

private fun DrawScope.mouth(m: Offset, mouth: MoMouth, ink: Color, t: Float) {
    when (mouth) {
        is MoMouth.Smile -> curve(m + Offset(-mouth.width, -5f), m + Offset(0f, mouth.depth - 5f), m + Offset(mouth.width, -5f), ink, 5.5f)
        is MoMouth.Grin -> openMouth(m, width = 42f, height = 8f + 26f * mouth.open.coerceIn(0f, 1f), ink, tongue = mouth.tongue)
        is MoMouth.Frown -> curve(m + Offset(-mouth.width, 9f), m + Offset(0f, -4f), m + Offset(mouth.width, 9f), ink, 5.5f)
        is MoMouth.Flat -> limb(m + Offset(-mouth.width, 2f), m + Offset(mouth.width, 2f), ink, 5.5f)
        is MoMouth.Oh -> oval(m + Offset(0f, 4f), mouth.size * 0.78f, mouth.size, ink)
        MoMouth.Gritted -> grittedTeeth(m + Offset(0f, 6f))
        MoMouth.Beam -> toothyMouth(mouthShape(m, width = 50f, height = 34f), teethDownTo = m.y - 34f * ABOVE_THE_MOUTH + 15f, ink)
        MoMouth.Wavy -> polyline(ink, 5f, (0..8).map { m + Offset(-20f + it * 5f, 3f + sin(it * 1.57f) * 4f) })
        MoMouth.Zigzag -> polyline(ink, 4.5f, (0..6).map { m + Offset(-18f + it * 6f, if (it % 2 == 0) 0f else 7f) })
        MoMouth.TongueOut -> {
            val (from, control, to) = Triple(m + Offset(-17f, -5f), m + Offset(0f, 10f), m + Offset(17f, -5f))
            // The tongue comes out of the smile: it is cut off along the lip, and the lip is drawn
            // over the cut, so it hangs from his mouth instead of lying beside it.
            val belowTheLip = Path().apply {
                moveTo(from.x, from.y)
                quadTo(this, from, control, to)
                lineTo(to.x, m.y + 40f)
                lineTo(from.x, m.y + 40f)
                close()
            }
            clipPath(belowTheLip) {
                drawRoundRect(MoTongue, m + Offset(-3f, -8f), Size(17f, 26f), CornerRadius(8.5f))
                limb(m + Offset(5.5f, 2f), m + Offset(5.5f, 10f), ink.copy(alpha = 0.3f), 2.5f)
            }
            curve(from, control, to, ink, 5.5f)
        }
        MoMouth.Drool -> {
            oval(m + Offset(0f, 4f), 9f, 7f, ink)
            val fall = phase(t, 2.2f)
            // The drop stays its own colour until it is nearly gone: half see-through over his belly it is olive.
            val gone = ease((fall - 0.8f) / 0.2f)
            oval(m + Offset(8f, 10f + fall * 22f), 3.5f * (1f - 0.5f * gone), (4.5f + fall * 3f) * (1f - 0.5f * gone), MoTear.copy(alpha = 1f - gone))
        }
        is MoMouth.Chew -> {
            oval(m + Offset(0f, 2f), 15f, 4f + 9f * mouth.open.coerceIn(0f, 1f), ink)
        }
        is MoMouth.Yawn -> {
            val open = mouth.open.coerceIn(0f, 1f)
            val centre = m + Offset(0f, 8f)
            val rx = 13f + 5f * open
            val ry = 8f + 16f * open
            oval(centre, rx, ry, ink)
            // The tongue lies on the floor of his mouth, however little of it is open.
            val inside = Path().apply { addOval(Rect(centre.x - rx, centre.y - ry, centre.x + rx, centre.y + ry)) }
            clipPath(inside) { oval(centre + Offset(0f, ry * 0.86f), rx * 0.66f, ry * 0.46f, MoTongue) }
        }
        MoMouth.Whistle -> drawCircle(ink, 5.5f, m + Offset(9f, 4f), style = Stroke(width = 4.5f))
        MoMouth.Pout -> {
            curve(m + Offset(-10f, 7f), m + Offset(0f, -3f), m + Offset(10f, 7f), ink, 5.5f)
            curve(m + Offset(-6f, 13f), m + Offset(0f, 17f), m + Offset(6f, 13f), ink, 4f)
        }
        MoMouth.Smirk -> curve(m + Offset(-13f, 3f), m + Offset(6f, 10f), m + Offset(17f, -6f), ink, 5.5f)
        MoMouth.Hidden -> Unit
    }
}

/**
 * An open mouth with a white row of teeth across its top, down to [teethDownTo]. The ink is drawn
 * only below the teeth: under them it shows past the edge of the white as a dark, broken hairline
 * along his lip.
 */
internal fun DrawScope.toothyMouth(shape: Path, teethDownTo: Float, ink: Color) {
    val bounds = shape.getBounds()
    fun band(top: Float, bottom: Float) = Path.combine(
        PathOperation.Intersect,
        shape,
        Path().apply { addRect(Rect(bounds.left - 2f, top, bounds.right + 2f, bottom)) },
    )
    // The ink reaches a little way up under the teeth, so no ground shows where the two meet.
    drawPath(band(teethDownTo - 1f, bounds.bottom + 2f), ink)
    drawPath(band(bounds.top - 2f, teethDownTo), MoFoam)
}

private fun mouthShape(m: Offset, width: Float, height: Float): Path = Path().apply {
    val left = m.x - width / 2f
    val top = m.y - height * ABOVE_THE_MOUTH
    moveTo(left, top)
    cubicTo(left + width * 0.33f, top - 3f, left + width * 0.67f, top - 3f, left + width, top)
    cubicTo(left + width * 0.96f, top + height * 0.7f, left + width * 0.8f, top + height, m.x, top + height)
    cubicTo(left + width * 0.2f, top + height, left + width * 0.04f, top + height * 0.7f, left, top)
    close()
}

private fun DrawScope.openMouth(m: Offset, width: Float, height: Float, ink: Color, tongue: Boolean) {
    val shape = mouthShape(m, width, height)
    drawPath(shape, ink)
    // A mouth that is barely open has no room for a tongue: a sliver of it reads as a painted lip.
    val grown = ease((height - 11f) / 8f)
    if (tongue && grown > 0f) {
        clipPath(shape) { oval(Offset(m.x, m.y + height * (0.95f - ABOVE_THE_MOUTH)), width * 0.36f, height * 0.32f * grown, MoTongue) }
    }
}

/**
 * How much of an open mouth lies above the place the body keeps its mouth. It opens mostly
 * downward, as a jaw drops: his eyes sit close above it, and wider than the gap between them.
 */
private const val ABOVE_THE_MOUTH = 0.3f

// ------------------------------------------------------------------------------- things acts draw

/** A rounded rectangle given by its centre, which is how props are usually thought of. */
internal fun DrawScope.block(centre: Offset, width: Float, height: Float, colour: Color, corner: Float = 6f) =
    drawRoundRect(colour, Offset(centre.x - width / 2f, centre.y - height / 2f), Size(width, height), CornerRadius(corner))

/**
 * A chunky object in Momentum's style: a face in [colour] sitting on a darker [ledge] band, like a
 * button. Use it for anything solid he holds or stands next to.
 */
internal fun DrawScope.chunk(centre: Offset, width: Float, height: Float, colour: Color, ledge: Color, corner: Float = 8f, drop: Float = 5f) {
    block(centre + Offset(0f, drop / 2f), width, height, ledge, corner)
    block(centre - Offset(0f, drop / 2f), width, height, colour, corner)
}

/** A closed shape from points, for anything that is not a box or a circle. */
internal fun DrawScope.shape(colour: Color, vararg points: Pair<Float, Float>) {
    val path = Path().apply {
        moveTo(points[0].first, points[0].second)
        points.drop(1).forEach { lineTo(it.first, it.second) }
        close()
    }
    val solid = colour.copy(alpha = 1f)
    fading(colour.alpha, path.getBounds().inflate(3f)) {
        drawPath(path, solid)
        // The same colour stroked round rounds every corner, as everything in his world is rounded.
        drawPath(path, solid, style = Stroke(width = 5f, join = StrokeJoin.Round, cap = StrokeCap.Round))
    }
}

/**
 * Draws [block] as one thing at [alpha], within [bounds]. Parts that overlap then fade together.
 * Each drawn at its own alpha, they show through one another: a thing built of several shapes
 * shows its joints as it fades, and a shape with a stroke round it turns into a frame.
 */
internal inline fun DrawScope.fading(alpha: Float, bounds: Rect, block: DrawScope.() -> Unit) {
    when {
        alpha <= 0f -> Unit
        alpha >= 1f -> block()
        else -> {
            val canvas = drawContext.canvas
            canvas.saveLayer(bounds, Paint().also { it.alpha = alpha })
            block()
            canvas.restore()
        }
    }
}

/** A little flame tongue pointing up, for sparks, candles, and anything else that burns. */
internal fun DrawScope.flameTongue(base: Offset, size: Float, colour: Color, core: Color? = null) {
    fun tongue(at: Offset, s: Float) = Path().apply {
        moveTo(at.x, at.y - s * 2.6f)
        cubicTo(at.x + s, at.y - s * 1.2f, at.x + s * 0.8f, at.y, at.x, at.y)
        cubicTo(at.x - s * 0.8f, at.y, at.x - s, at.y - s * 1.2f, at.x, at.y - s * 2.6f)
        close()
    }
    drawPath(tongue(base, size), colour)
    core?.let { drawPath(tongue(base, size * 0.5f), it) }
}

/** A puff of cloud, steam, dust or thought, as three overlapping circles. */
internal fun DrawScope.puff(centre: Offset, size: Float, colour: Color) {
    drawCircle(colour, size * 0.62f, centre + Offset(-size * 0.55f, size * 0.12f))
    drawCircle(colour, size * 0.8f, centre)
    drawCircle(colour, size * 0.58f, centre + Offset(size * 0.6f, size * 0.16f))
}

/** A drop of sweat, water or rain, falling point-up. */
internal fun DrawScope.drop(top: Offset, size: Float, colour: Color) {
    val path = Path().apply {
        moveTo(top.x, top.y)
        cubicTo(top.x + size, top.y + size * 1.4f, top.x + size * 0.9f, top.y + size * 2.4f, top.x, top.y + size * 2.4f)
        cubicTo(top.x - size * 0.9f, top.y + size * 2.4f, top.x - size, top.y + size * 1.4f, top.x, top.y)
        close()
    }
    drawPath(path, colour)
}

/** The soft shadow that says something stands on the floor. */
internal fun DrawScope.floorShadow(centreX: Float, halfWidth: Float, colour: Color) =
    oval(Offset(centreX, FEET), halfWidth, 5f, colour)

/** A number from 0 to 1 that eases in and out, for anything that should not move like a machine. */
internal fun ease(x: Float): Float {
    val c = x.coerceIn(0f, 1f)
    return c * c * (3f - 2f * c)
}

/** 0 to 1 and back to 0 over [period] seconds, eased: a breath, a bounce, a glow. */
internal fun pulse(t: Float, period: Float): Float = 0.5f - 0.5f * cos(t / period * 2f * PI.toFloat())

/** How far into a repeating [period] the time [t] is, from 0 to 1, starting [offset] of the way in. */
internal fun cycle(t: Float, period: Float, offset: Float = 0f): Float = (t / period + offset).mod(1f)

/** True for a short [beat] at the start of every [period]: a twitch, a glance, a hiccup. */
internal fun twitch(t: Float, period: Float, beat: Float = 0.25f, offset: Float = 0f): Float {
    val at = cycle(t, period, offset) * period
    return if (at < beat) sin(at / beat * PI.toFloat()) else 0f
}
