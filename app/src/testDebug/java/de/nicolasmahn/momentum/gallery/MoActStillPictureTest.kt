package de.nicolasmahn.momentum.gallery

import de.nicolasmahn.momentum.feature.mo.acts.MoActBook
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * The widget and the notifications show an act's first moment as a picture cut at his box. Whatever
 * an act draws outside the box in that moment is sliced off there, so no act may.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MoActStillPictureTest {
    @Test
    fun everyActsFirstMomentStaysInsideHisBox() {
        val stage = MoActStage()
        val cutOff = MoActBook.flatMap { act ->
            // Steady exposes scenery above the Blazing crop, even on the same tall body.
            act.states().flatMap { state ->
                VARIANTS.mapNotNull { seed ->
                    val beyond = stage.beyondTheBox(stage.shoot(MoActMoment(state, seed, 0f).look(act), 0f)).max()
                    "${act.id}, $state, variant $seed: $beyond".takeIf { beyond >= STILL_PICTURE_SLACK }
                }
            }
        }

        assertEquals("drawn outside his box in the first moment, by this many box units", emptyList<String>(), cutOff)
    }

    /**
     * The same holds for what no act draws: his classic looks, and the things he has neglected,
     * which lie beside him in every picture, whatever he is up to.
     */
    @Test
    fun hisClassicLooksStayInsideHisBox() {
        val stage = MoActStage()
        val cutOff = MoClassicLooks.flatMap { set ->
            set.looks.mapNotNull { (label, look) ->
                val beyond = stage.beyondTheBox(stage.shoot(look, 0f)).max()
                "${set.name}, $label: $beyond".takeIf { beyond >= STILL_PICTURE_SLACK }
            }
        }

        assertEquals("drawn outside his box in the first moment, by this many box units", emptyList<String>(), cutOff)
    }

    private companion object {
        val VARIANTS = listOf(1, 2, 3, 7)
    }
}
