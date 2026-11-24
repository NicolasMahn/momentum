package de.nicolasmahn.momentum.gallery

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import de.nicolasmahn.momentum.domain.mo.MoNeglect
import de.nicolasmahn.momentum.domain.mo.MoOccasion
import de.nicolasmahn.momentum.domain.mo.MoProp
import de.nicolasmahn.momentum.domain.mo.MoState
import de.nicolasmahn.momentum.feature.mo.MoArm
import de.nicolasmahn.momentum.feature.mo.MoBodyKind
import de.nicolasmahn.momentum.feature.mo.MoEye
import de.nicolasmahn.momentum.feature.mo.MoLook
import de.nicolasmahn.momentum.feature.mo.MoNeglectedDrawing
import de.nicolasmahn.momentum.feature.mo.MoPose
import de.nicolasmahn.momentum.feature.mo.acts.MoAct
import de.nicolasmahn.momentum.feature.mo.acts.MoActBook
import de.nicolasmahn.momentum.feature.mo.acts.MoScene
import de.nicolasmahn.momentum.feature.mo.acts.MoVariant
import de.nicolasmahn.momentum.feature.mo.acts.bodyKind
import de.nicolasmahn.momentum.feature.mo.moIdle
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Measures every act for what a still picture cannot show, and writes what it finds to `audit.tsv`.
 * Like [MoActRenderTest] it is a tool that `scripts/mo_acts.py` runs, not a check: a finding is a
 * place to look, and plenty of them are an act doing exactly what it means to.
 *
 * - `overflow`: the act draws outside Mo's box, by that many box units on each side. In the app
 *   that lands on whatever is beside him. `overflow/` holds the picture, with a line round his box.
 * - `still`: the same for the first moment alone, which is the picture the widget and the
 *   notifications show, cut at his box.
 * - `pop`: between two frames a thirtieth of a second apart, far more of the picture changed than
 *   in the frames before and after, and it changed all at once however finely time is cut. That
 *   is a jump: a seam in a loop, a prop that appears from nothing, a limb that is suddenly
 *   somewhere else. `pops/` holds the two frames, between the two before and the two after.
 * - `face`: a jump where the only large change found in his pose is facial. Props can change too.
 * - `quick`: as much changed, but spread over the thirtieth of a second: fast motion, which the
 *   finer samples resolve. Worth a look when it is meant to be gentle.
 * - `floor`: the act draws where the things he has neglected lie, without owning the floor.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MoActAuditTest {
    @Test
    fun audit() {
        val out = System.getProperty("mo.acts.out")?.let(::File)
        assumeTrue(out != null)
        val wanted = System.getProperty("mo.acts.only").orEmpty().split(',').filter { it.isNotBlank() }
        val acts = MoActBook.filter { act -> wanted.isEmpty() || wanted.any { act.id.startsWith(it) } }

        val findings = acts.flatMap { act ->
            act.states().flatMap { state -> still(act, state) + overflow(act, state) + pops(act, state) + floor(act, state) }
        }
        out!!.mkdirs()
        findings.filter { it.kind == "pop" }.forEach { pop ->
            val around = (-3..2).map { MoActMoment(pop.state, pop.seed, pop.seconds + it.toFloat() / FRAMES_PER_SECOND) }
            save(moActSheet(pop.act, listOf(around), scale = 0.75f), File(out, "pops/${pop.name()}.png"))
        }
        findings.filter { it.kind == "overflow" || it.kind == "still" }.forEach { beyond ->
            stage.shoot(beyond.act.look(beyond.state, beyond.seed), beyond.seconds)
            save(stage.pictureWithItsBox(), File(out, "${beyond.kind}/${beyond.name()}.png"))
        }
        File(out, "audit.tsv").writeText(
            "kind\tact\tstate\tseed\tseconds\tsize\tdetail\n" +
                findings.sortedWith(compareBy<Finding> { it.kind }.thenByDescending { it.size }).joinToString("") { it.line() },
        )
    }

    private val stage = MoActStage()

    private fun still(act: MoAct, state: MoState): List<Finding> = beyondTheBox("still", act, state, STILL_SEEDS, listOf(0f), STILL_PICTURE_SLACK)

    private fun overflow(act: MoAct, state: MoState): List<Finding> =
        beyondTheBox("overflow", act, state, OVERFLOW_SEEDS, OVERFLOW_MOMENTS, OVERFLOW_WORTH_A_LOOK)

    /** The moment and variant in which the act reaches furthest past his box, if that is worth a look. */
    private fun beyondTheBox(kind: String, act: MoAct, state: MoState, seeds: List<Int>, moments: List<Float>, worthALook: Float): List<Finding> {
        val (seed, seconds, beyond) = seeds.flatMap { seed -> moments.map { seed to it } }
            .map { (seed, seconds) -> Triple(seed, seconds, stage.shoot(act.look(state, seed), seconds).let(stage::beyondTheBox)) }
            .maxBy { (_, _, beyond) -> beyond.max() }
        if (beyond.max() < worthALook) return emptyList()
        val sides = listOf("left", "top", "right", "bottom").zip(beyond).filter { it.second >= worthALook }
        return listOf(Finding(kind, act, state, seed, seconds, beyond.max(), sides.joinToString(" ") { "${it.first} ${it.second.format()}" }))
    }

    private fun pops(act: MoAct, state: MoState): List<Finding> {
        val seed = if (state == act.states().first()) 1 else 7
        val look = act.look(state, seed)
        val frames = (POP_SECONDS * FRAMES_PER_SECOND).toInt()
        val before = stage.shoot(look, 0f).copyOf()
        val changed = IntArray(frames) { index ->
            val now = stage.shoot(look, (index + 1f) / FRAMES_PER_SECOND)
            stage.changedBetween(before, now).also { now.copyInto(before) }
        }
        return (1 until frames - 1)
            .filter { changed[it] >= POP_AREA && changed[it] >= POP_RATIO * max(max(changed[it - 1], changed[it + 1]), POP_AREA / 8) }
            .map { index ->
                val (from, to) = index.toFloat() / FRAMES_PER_SECOND to (index + 1f) / FRAMES_PER_SECOND
                val body = state.bodyKind()
                // In the ice he holds the pose of his first moment, and the Husk has none.
                val posed = body != MoBodyKind.Husk && !look.frozen
                // Describe the pose change at the peak. Neighbouring motion can otherwise make a
                // change of expression look like a jump of his whole body.
                val instant = instantOfChange(look, from, to)
                val moved = if (posed) poseChange(act.pose(instant.from, MoVariant(seed), body), act.pose(instant.to, MoVariant(seed), body)) else emptyList()
                val kind = when {
                    instant.share < AT_ONCE -> "quick"
                    moved.isNotEmpty() && moved.all { it.startsWith(FACE) } -> "face"
                    else -> "pop"
                }
                Finding(kind, act, state, seed, to, changed[index] * 100f / stage.boxArea, moved.joinToString("; ").ifEmpty { "around him" })
            }
            .groupBy { it.kind }
            .flatMap { (_, ofAKind) -> ofAKind.sortedByDescending { it.size }.take(POPS_PER_ACT) }
    }

    /** The sampled interval with the most changed pixels and its share of the frame-to-frame count. */
    private class Instant(val from: Float, val to: Float, val share: Float)

    /**
     * Check every subframe. The half with the most movement overall can still have a smaller
     * peak than a single jump in the other half.
     */
    private fun instantOfChange(look: MoLook, from: Float, to: Float): Instant {
        val previous = stage.shoot(look, from).copyOf()
        val whole = stage.changedBetween(previous, stage.shoot(look, to)).coerceAtLeast(1)
        var most = 0
        var start = from
        var end = from
        var lastTime = from
        for (step in 1..SUBFRAMES) {
            val time = from + (to - from) * step / SUBFRAMES
            val current = stage.shoot(look, time)
            val changed = stage.changedBetween(previous, current)
            if (changed > most) {
                most = changed
                start = lastTime
                end = time
            }
            current.copyInto(previous)
            lastTime = time
        }
        return Instant(start, end, most.toFloat() / whole)
    }

    private fun floor(act: MoAct, state: MoState): List<Finding> {
        if (act.ownsTheFloor || act.occasion == MoOccasion.Frozen) return emptyList()
        val neglected = listOf(MoNeglectedDrawing(MoProp.Book, MoNeglect.Severe, 0), MoNeglectedDrawing(MoProp.Weights, MoNeglect.Severe, 1))
        val nothing = MoLook(state, scene = MoScene(StandingStill, MoVariant(1)))
        val bare = stage.shoot(nothing, 0f).copyOf()
        val littered = stage.shoot(nothing.copy(neglect = neglected), 0f).copyOf()
        val lying = bare.indices.filter { stage.differ(bare[it], littered[it]) }
        if (lying.isEmpty()) return emptyList()
        val (seconds, covered) = FLOOR_MOMENTS.map { seconds ->
            val frame = stage.shoot(act.look(state, 1), seconds)
            seconds to lying.count { stage.differ(frame[it], bare[it]) } * 100f / lying.size
        }.maxBy { it.second }
        return if (covered < FLOOR_WORTH_A_LOOK) emptyList() else listOf(Finding("floor", act, state, 1, seconds, covered, "of where his neglected things lie"))
    }

    /** What moved too far between two poses a frame apart to be motion, in words. */
    private fun poseChange(from: MoPose, to: MoPose): List<String> = buildList {
        fun jump(name: String, a: Float, b: Float, limit: Float) {
            if (abs(a - b) > limit) add("$name ${a.format()}→${b.format()}")
        }
        fun jump(name: String, a: Offset, b: Offset, limit: Float) {
            if ((a - b).getDistance() > limit) add("$name (${a.x.format()}, ${a.y.format()})→(${b.x.format()}, ${b.y.format()})")
        }
        fun arm(name: String, a: MoArm, b: MoArm) {
            jump("$name.reach", a.reach, b.reach, 9f)
            jump("$name.bend", a.bend, b.bend, 0.3f)
        }
        fun eye(name: String, a: MoEye, b: MoEye) {
            if (a.shape != b.shape) add("$name ${a.shape}→${b.shape}")
            jump("$name.open", a.open, b.open, 0.34f)
            jump("$name.gaze", a.gaze, b.gaze, 0.5f)
            jump("$name.iris", a.iris, b.iris, 0.12f)
            jump("$name.lid", a.lid, b.lid, 0.3f)
            jump("$name.lidDrop", a.lidDrop, b.lidDrop, 0.3f)
            jump("$name.scale", a.scale, b.scale, 0.12f)
            if ((a.tear == null) != (b.tear == null)) add("$name.tear ${a.tear}→${b.tear}")
        }
        eye("${FACE}leftEye", from.leftEye, to.leftEye)
        eye("${FACE}rightEye", from.rightEye, to.rightEye)
        if (from.brows.kind != to.brows.kind) add("${FACE}brows ${from.brows.kind}→${to.brows.kind}")
        jump("${FACE}brows.lift", from.brows.lift, to.brows.lift, 4f)
        jump("${FACE}brows.skew", from.brows.skew, to.brows.skew, 4f)
        if (from.mouth::class != to.mouth::class) add("${FACE}mouth ${from.mouth::class.simpleName}→${to.mouth::class.simpleName}")
        if (from.palette != to.palette) add("palette")
        arm("leftArm", from.leftArm, to.leftArm)
        arm("rightArm", from.rightArm, to.rightArm)
        if (from.armsInFront != to.armsInFront) add("armsInFront")
        if (from.legs.sitting != to.legs.sitting) add("sitting")
        jump("leftLeg.lift", from.legs.left.lift, to.legs.left.lift, 7f)
        jump("leftLeg.swing", from.legs.left.swing, to.legs.left.swing, 7f)
        jump("rightLeg.lift", from.legs.right.lift, to.legs.right.lift, 7f)
        jump("rightLeg.swing", from.legs.right.swing, to.legs.right.swing, 7f)
        jump("sway", from.sway, to.sway, 7f)
        jump("flare", from.flare, to.flare, 7f)
        jump("squash", from.squash, to.squash, 0.035f)
        jump("tilt", from.tilt, to.tilt, 3.5f)
        jump("offset", from.offset, to.offset, 8f)
        jump("shake", from.shake, to.shake, 6f)
    }

    private fun MoAct.look(state: MoState, seed: Int): MoLook = MoActMoment(state, seed, 0f).look(this)

    private fun save(picture: Bitmap, file: File) {
        file.parentFile.mkdirs()
        file.outputStream().use { picture.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private class Finding(val kind: String, val act: MoAct, val state: MoState, val seed: Int, val seconds: Float, val size: Float, val detail: String) {
        fun line() = "$kind\t${act.id}\t$state\t$seed\t${seconds.format()}\t${size.format()}\t$detail\n"

        fun name() = "${act.id}-$state-$seed-${seconds.format()}"
    }

    private object StandingStill : MoAct("idle_standing_still", MoOccasion.Idle, bodies = MoBodyKind.entries.toSet()) {
        override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose = moIdle(t)
    }

    private companion object {
        const val FRAMES_PER_SECOND = 30

        // Long enough for the slowest loops to come round at least once.
        const val POP_SECONDS = 24f

        // A jump counts from about a third of a mitten changing at once.
        const val POP_AREA = 36
        const val POP_RATIO = 3.5f
        const val POPS_PER_ACT = 6
        const val SUBFRAMES = 32
        const val AT_ONCE = 0.5f
        const val FACE = "face: "

        val OVERFLOW_SEEDS = listOf(1, 2, 3, 4, 5, 7)

        // More variants than MoActStillPictureTest has time for on every build.
        val STILL_SEEDS = (1..12).toList()
        val OVERFLOW_MOMENTS = List(48) { it * 0.25f }

        // Less than this is the soft edge of a floor shadow, which nobody sees cut off.
        const val OVERFLOW_WORTH_A_LOOK = 6f

        val FLOOR_MOMENTS = listOf(0f, 1.3f, 2.9f, 4.4f, 6.8f)
        const val FLOOR_WORTH_A_LOOK = 20f
    }
}

private fun Float.format(): String = String.format(Locale.ROOT, "%.2f", this).trimEnd('0').trimEnd('.')
