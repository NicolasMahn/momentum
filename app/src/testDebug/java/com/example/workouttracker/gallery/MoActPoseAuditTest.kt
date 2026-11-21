package com.example.workouttracker.gallery

import android.graphics.Region
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidPath
import com.example.workouttracker.feature.mo.CENTRE
import com.example.workouttracker.feature.mo.MoArm
import com.example.workouttracker.feature.mo.MoBodies
import com.example.workouttracker.feature.mo.MoBody
import com.example.workouttracker.feature.mo.MoBodyKind
import com.example.workouttracker.feature.mo.MoEyeShape
import com.example.workouttracker.feature.mo.MoMouth
import com.example.workouttracker.feature.mo.MoPose
import com.example.workouttracker.feature.mo.acts.MoAct
import com.example.workouttracker.feature.mo.acts.MoActBook
import com.example.workouttracker.feature.mo.acts.MoVariant
import com.example.workouttracker.feature.mo.elbow
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Reads every act's poses for what goes wrong between his arms and the rest of him, and writes it
 * to `poses.tsv`: one line per act, body and kind, at the worst moment found, with how long it
 * lasts. It needs no pictures, so it reads every thirtieth of a second of every act in a minute.
 * Like [MoActAuditTest] it is a tool that `scripts/mo_acts.py` runs: a finding is a place to look.
 *
 * - `hidden`: his arms are drawn behind his body and a hand is over it. The hand is gone, and
 *   whatever the act puts in it floats on his belly. The size is the share of the mitten hidden.
 * - `on-eye`: his arms are in front and an arm or a hand lies on an open eye.
 * - `on-mouth`: his arms are in front and a hand lies on his mouth.
 * - `flip`: his arms change between behind and in front while one of them is over his body, so
 *   it jumps through him.
 * - `long`: an arm is longer than he can reach. The size is its length in design units.
 * - `under-core`: on the droplet his mouth reaches down into the core he holds, which is drawn over
 *   it. The size is how far, in design units.
 * - `mouth-eye`: a corner of an open mouth is closer than a hair to an eye, or in it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MoActPoseAuditTest {
    @Test
    fun poses() {
        val out = System.getProperty("mo.acts.out")?.let(::File)
        assumeTrue(out != null)
        val wanted = System.getProperty("mo.acts.only").orEmpty().split(',').filter { it.isNotBlank() }
        val acts = MoActBook.filter { act -> wanted.isEmpty() || wanted.any { act.id.startsWith(it) } }

        val findings = acts.flatMap { act -> (act.bodies - MoBodyKind.Husk).flatMap { body -> read(act, body) } }
        out!!.mkdirs()
        File(out, "poses.tsv").writeText(
            "kind\tact\tbody\tseed\tseconds\tsize\tlasts\tdetail\n" +
                findings.sortedWith(compareBy<Finding> { it.kind }.thenByDescending { it.lasts }).joinToString("") { it.line() },
        )
    }

    private fun read(act: MoAct, kind: MoBodyKind): List<Finding> {
        val body = MoBodies.of(kind)
        val worst = mutableMapOf<String, Finding>()
        val lasting = mutableMapOf<String, Float>()
        // Two hands on two eyes in one instant are one instant of it.
        val counted = mutableSetOf<Triple<String, Int, Int>>()
        var frame = 0
        fun found(what: String, seed: Int, seconds: Float, size: Float, detail: String) {
            if (counted.add(Triple(what, seed, frame))) lasting.merge(what, STEP / SEEDS.size, Float::plus)
            if (size > (worst[what]?.size ?: 0f)) worst[what] = Finding(what, act, kind, seed, seconds, size, detail)
        }
        for (seed in SEEDS) {
            val variant = MoVariant(seed)
            var before: List<Boolean>? = null
            for (step in 0..(SECONDS / STEP).toInt()) {
                frame = step
                val seconds = frame * STEP
                val pose = act.pose(seconds, variant, kind)
                val shape = BodyShape(body, pose)
                val overHim = mutableListOf<Float>()
                for ((name, arm, side) in listOf(Triple("left", pose.leftArm, -1f), Triple("right", pose.rightArm, 1f))) {
                    val shoulder = Offset(CENTRE + side * body.shoulderHalf, body.shoulderY)
                    val hand = shoulder + Offset(side * arm.reach.x, arm.reach.y)
                    val length = arm.reach.getDistance()
                    val elbow = elbow(body, arm, side)
                    val hidden = shape.share(hand, MITTEN)
                    overHim += hidden
                    if (length > REACH) found("long", seed, seconds, length, "$name arm ${arm.describe()}")
                    if (!(arm.inFront ?: pose.armsInFront)) {
                        if (hidden >= HIDDEN) found("hidden", seed, seconds, hidden, "$name hand ${arm.describe()}")
                        continue
                    }
                    for ((eye, x) in listOf(pose.leftEye to body.leftEyeX, pose.rightEye to body.rightEyeX)) {
                        if (eye.shape in SHUT_EYES || eye.open < 0.3f) continue
                        val centre = Offset(x, body.eyeY)
                        val radius = body.eyeRadius * eye.scale
                        val over = maxOf(radius + MITTEN - (hand - centre).getDistance(), radius + ARM - distance(centre, elbow, hand))
                        if (over > TOUCH) found("on-eye", seed, seconds, over, "$name arm ${arm.describe()}")
                    }
                    if (pose.mouth != MoMouth.Hidden) {
                        val over = MITTEN + MOUTH - (hand - (body.mouth + Offset(0f, 4f))).getDistance()
                        if (over > TOUCH) found("on-mouth", seed, seconds, over, "$name hand ${arm.describe()}, mouth ${pose.mouth}")
                    }
                }
                val mouth = extent(pose.mouth)
                if (mouth != null) {
                    val under = body.mouth.y + mouth.bottom - (CORE_Y - CORE)
                    if (kind == MoBodyKind.Droplet && under > 1f) found("under-core", seed, seconds, under, "${pose.mouth}")
                    for ((eye, x) in listOf(pose.leftEye to body.leftEyeX, pose.rightEye to body.rightEyeX)) {
                        val corner = Offset(body.mouth.x + (if (x < CENTRE) -1f else 1f) * mouth.half, body.mouth.y + mouth.top)
                        val gap = (corner - Offset(x, body.eyeY)).getDistance() - body.eyeRadius * eye.scale
                        if (gap < HAIR) found("mouth-eye", seed, seconds, HAIR - gap, "${pose.mouth}")
                    }
                }
                val inFront = listOf(pose.leftArm, pose.rightArm).map { it.inFront ?: pose.armsInFront }
                // The arm that changed sides is the one that has to be over him for it to show.
                before?.forEachIndexed { arm, was ->
                    if (was != inFront[arm] && overHim[arm] > 0.15f) {
                        found("flip", seed, seconds, overHim[arm], "${if (arm == 0) "left" else "right"} arm to the ${if (inFront[arm]) "front" else "back"}")
                    }
                }
                before = inFront
            }
        }
        return worst.values.map { it.copy(lasts = lasting.getValue(it.kind)) }
    }

    /** His body's outline in one pose, to ask which points it covers. */
    private class BodyShape(body: MoBody, pose: MoPose) {
        private val region = Region().apply {
            val flame: (Offset) -> Offset = { point ->
                val height = ((body.pivot - point.y) / (body.pivot - body.top)).coerceIn(0f, 1.2f)
                Offset(point.x + pose.sway * height * height, point.y - pose.flare * height * height)
            }
            setPath(body.outer.toPath(flame).asAndroidPath(), Region(-400, -400, 800, 800))
        }

        /** How much of a disc lies over the body, from 0 to 1. */
        fun share(centre: Offset, radius: Float): Float =
            DISC.count { (centre + it * radius).let { point -> region.contains(point.x.roundToInt(), point.y.roundToInt()) } }.toFloat() / DISC.size
    }

    private data class Finding(
        val kind: String,
        val act: MoAct,
        val body: MoBodyKind,
        val seed: Int,
        val seconds: Float,
        val size: Float,
        val detail: String,
        val lasts: Float = 0f,
    ) {
        fun line() = String.format(Locale.ROOT, "%s\t%s\t%s\t%d\t%.2f\t%.2f\t%.2f\t%s\n", kind, act.id, body, seed, seconds, size, lasts, detail)
    }

    /** How far a mouth reaches from where the body keeps it: up, down and to each side. */
    private class Extent(val top: Float, val bottom: Float, val half: Float)

    private fun extent(mouth: MoMouth): Extent? = when (mouth) {
        is MoMouth.Smile -> Extent(-7.75f, maxOf(mouth.depth / 2f - 2.25f, -2.25f), mouth.width + 2.75f)
        is MoMouth.Grin -> (8f + 26f * mouth.open.coerceIn(0f, 1f)).let { Extent(-0.3f * it, 0.7f * it, 21f) }
        is MoMouth.Frown -> Extent(-0.25f, 11.75f, mouth.width + 2.75f)
        is MoMouth.Flat -> Extent(-0.75f, 4.75f, mouth.width + 2.75f)
        is MoMouth.Oh -> Extent(4f - mouth.size, 4f + mouth.size, mouth.size * 0.78f)
        MoMouth.Gritted -> Extent(-6f, 18f, 27f)
        MoMouth.Beam -> Extent(-10.2f, 23.8f, 25f)
        MoMouth.Wavy -> Extent(-3.5f, 9.5f, 22.5f)
        MoMouth.Zigzag -> Extent(-2.25f, 9.25f, 20.25f)
        MoMouth.TongueOut -> Extent(-7.75f, 18f, 19.75f)
        // The drop falls on behind whatever is below; the mouth itself is what counts.
        MoMouth.Drool -> Extent(-3f, 11f, 9f)
        is MoMouth.Chew -> (4f + 9f * mouth.open.coerceIn(0f, 1f)).let { Extent(2f - it, 2f + it, 15f) }
        is MoMouth.Yawn -> mouth.open.coerceIn(0f, 1f).let { Extent(-16f * it, 16f + 16f * it, 13f + 5f * it) }
        MoMouth.Whistle -> Extent(-3.75f, 11.75f, 16.75f)
        MoMouth.Pout -> Extent(-0.25f, 19f, 12.75f)
        MoMouth.Smirk -> Extent(-8.75f, 9f, 19.75f)
        MoMouth.Hidden -> null
    }

    private fun MoArm.describe() = String.format(Locale.ROOT, "reach %.0f, %.0f bend %.2f", reach.x, reach.y, bend)

    private fun distance(point: Offset, from: Offset, to: Offset): Float {
        val along = to - from
        val t = (((point - from).x * along.x + (point - from).y * along.y) / (along.getDistanceSquared().coerceAtLeast(0.01f))).coerceIn(0f, 1f)
        return (point - (from + along * t)).getDistance()
    }

    private companion object {
        val SEEDS = listOf(1, 2, 3, 7)
        const val SECONDS = 12f
        const val STEP = 1f / 30f

        // Half the width of his arm, the radius of his mitten and about half his mouth, in design units.
        const val ARM = 10f
        const val MITTEN = 12.5f
        const val MOUTH = 8f

        // A hand half behind him at the hip still reads as a hand; more than that is a hand that is gone.
        const val HIDDEN = 0.6f

        // How far an arm must lie on an eye or a mouth before it counts as covering it.
        const val TOUCH = 4f

        // Where the droplet holds his core, and its radius at its brightest.
        const val CORE_Y = 366f
        const val CORE = 26.75f

        // Less than this between a mouth's corner and an eye reads as the two touching.
        const val HAIR = 2f

        // His longest arm of the kit, straight up, is 69 long.
        const val REACH = 100f
        val SHUT_EYES = setOf(MoEyeShape.Happy, MoEyeShape.Shut, MoEyeShape.Cross)

        // Points spread evenly over a unit disc.
        val DISC: List<Offset> = (1..4).flatMap { ring -> (0 until ring * 6).map { step -> (step * 2f * Math.PI.toFloat() / (ring * 6)).let { Offset(cos(it), sin(it)) * (ring / 4f) } } } + Offset.Zero
    }
}
