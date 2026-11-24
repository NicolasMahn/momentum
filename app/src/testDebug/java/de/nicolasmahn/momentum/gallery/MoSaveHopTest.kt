package de.nicolasmahn.momentum.gallery

import androidx.compose.ui.geometry.Rect
import de.nicolasmahn.momentum.domain.mo.MoState
import de.nicolasmahn.momentum.feature.mo.CENTRE
import de.nicolasmahn.momentum.feature.mo.FEET
import de.nicolasmahn.momentum.feature.mo.acts.MoActBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * On Home a save is met with a hop, and while he acts the save out only he leaves the floor. What
 * he stands on or beside is drawn by the act and has to stay where it is: a scale that came up
 * under his feet, or a book that left its stand with his hand, was drawn in the wrong layer.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MoSaveHopTest {
    @Test
    fun whatStandsOnTheFloorStaysThereWhenHeHops() {
        val stage = MoActStage()
        // The part of each picture that holds the thing on the floor and nothing of him.
        val standing = mapOf(
            // The ends of the scale, to either side of his feet.
            "doing_weighing_stand_on_the_scale" to listOf(Rect(CENTRE - 76f, FEET - 22f, CENTRE - 54f, FEET + 4f), Rect(CENTRE + 54f, FEET - 22f, CENTRE + 76f, FEET + 4f)),
            // The stand under the book and the lower half of its far page.
            "doing_reading_turn_the_page" to listOf(Rect(CENTRE + 90f, FEET - 45f, CENTRE + 146f, FEET)),
        )
        for ((id, parts) in standing) for (state in listOf(MoState.Blazing, MoState.Dimming)) {
            val act = MoActBook.single { it.id == id }
            val look = MoActMoment(state, seed = 1, seconds = 0f).look(act)
            val onTheFloor = stage.shoot(look, 0f).copyOf()
            val inTheAir = stage.shoot(look, 0f, lift = HOP).copyOf()

            assertTrue("$id, $state: he did not leave the floor at all", stage.changedBetween(onTheFloor, inTheAir) > 0)
            parts.forEach { part ->
                val (changed, all) = stage.changedWithin(onTheFloor, inTheAir, state, part)
                assertEquals("$id, $state: of $all pixels of what stands on the floor, this many moved with him", 0, changed)
            }
        }
    }

    private companion object {
        // About the height of his hop on Home, in box units.
        const val HOP = 20f
    }
}
