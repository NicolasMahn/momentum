package de.nicolasmahn.momentum.gallery

import android.graphics.Bitmap
import de.nicolasmahn.momentum.domain.mo.MoState
import de.nicolasmahn.momentum.feature.mo.acts.MoAct
import de.nicolasmahn.momentum.feature.mo.acts.MoActBook
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Renders acts to pictures on the PC, with the same drawing code the phone runs. It is a tool, not
 * a check: it only runs when `scripts/mo_acts.py` names a directory to write to, and that script is
 * how it is meant to be used.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MoActRenderTest {
    private val out = System.getProperty("mo.acts.out")?.let(::File)
    private val only = System.getProperty("mo.acts.only").orEmpty().split(',').filter { it.isNotBlank() }
    private val acts: List<MoAct> get() = MoActBook.filter { wanted(it.id) }
    private val close = System.getProperty("mo.acts.close")?.let { name -> MoCloseUp.entries.first { it.name.equals(name, ignoreCase = true) } } ?: MoCloseUp.Whole

    // Eight whole cells of 240 px make a sheet 1920 px wide, which is as much as a reviewer can
    // take in. A close-up is asked for to see more, so it keeps its full size.
    private val scale = if (close == MoCloseUp.Whole) 0.75f else 1f

    private fun wanted(name: String) = only.isEmpty() || only.any { name.startsWith(it) }

    /** One review sheet per act: eight moments, brightest state above, dimmest below. */
    @Test
    fun sheets() {
        assumeTrue(out != null)
        acts.forEach { act -> save(moActSheet(act, act.reviewRows(), scale, close), "${act.id}.png") }
        File(out, "index.txt").writeText(acts.joinToString("\n") { "${it.id}\t${it.occasion}\t${it.intensity}\t${it.bodies.joinToString(",")}" })
    }

    /** One sheet per act with two dozen of its variants, at half size: the shapes, counts and sides a day can choose. */
    @Test
    fun variants() {
        assumeTrue(out != null)
        acts.forEach { act -> save(moSheet(act.variantRows(), 0.5f, close), "${act.id}.png") }
    }

    /**
     * A strip of evenly spaced frames, for looking at one stretch of an act closely. Without a
     * state every state the act plays in gets a row.
     */
    @Test
    fun film() {
        assumeTrue(out != null)
        val film = Film.of(System.getProperty("mo.acts.film")!!)
        // Only the states an act plays in: a film of any other would show a picture nobody ever gets.
        val (playing, not) = acts.partition { act -> film.state == null || film.state in act.states() }
        // Said out loud, or the old picture of an earlier request would pass for this one's.
        assertTrue("${not.map { it.id }} do not play on ${film.state}: there is nothing to film", playing.isNotEmpty())
        not.forEach { File(out, "${it.id}.png").delete() }
        playing.forEach { act ->
            val states = act.states().filter { film.state == null || it == film.state }
            save(moActSheet(act, states.map { state -> film.moments.map { MoActMoment(state, film.seed, it) } }, scale, close), "${act.id}.png")
        }
    }

    /** His classic drawings, which no act draws: one sheet per set of looks, one row per look. */
    @Test
    fun classic() {
        assumeTrue(out != null)
        val moments = System.getProperty("mo.acts.film")?.let { Film.of(it).moments } ?: ReviewMoments
        MoClassicLooks.filter { wanted(it.name) }.forEach { set ->
            val rows = set.looks.map { (label, look) -> moments.mapIndexed { index, seconds -> MoShot(look, seconds, label.takeIf { index == 0 }) } }
            save(moSheet(rows, scale, close), "${set.name}.png")
        }
    }

    /** The kit's parts, one to a cell: every mouth, eye and arm on every body an act can pose. */
    @Test
    fun kit() {
        assumeTrue(out != null)
        MoKitSheets.filter { wanted(it.name) }.forEach { save(moSheet(it.rows, close = it.close), "${it.name}.png") }
    }

    /** `from,to,frames`, optionally followed by a seed and a state. */
    private class Film(val moments: List<Float>, val seed: Int, val state: MoState?) {
        companion object {
            fun of(property: String): Film {
                val parts = property.split(',')
                val (from, to) = parts[0].toFloat() to parts[1].toFloat()
                val frames = parts[2].toInt()
                return Film(
                    moments = List(frames) { from + (to - from) * it / (frames - 1).coerceAtLeast(1) },
                    seed = parts.getOrNull(3)?.toInt() ?: 1,
                    state = parts.getOrNull(4)?.let(MoState::valueOf),
                )
            }
        }
    }

    private fun save(sheet: Bitmap, name: String) {
        out!!.mkdirs()
        File(out, name).outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
