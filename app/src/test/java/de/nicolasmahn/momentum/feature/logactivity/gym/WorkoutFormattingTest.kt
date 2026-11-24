package de.nicolasmahn.momentum.feature.logactivity.gym

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import de.nicolasmahn.momentum.core.text.UiText
import de.nicolasmahn.momentum.core.text.resolve
import de.nicolasmahn.momentum.domain.activity.routine.LoggedSet
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class WorkoutFormattingTest {
    @Test
    fun formatSetLoad_readsAssistanceAsBodyweightMinusTheBand() {
        assertEquals("BW −3 kg", formatSetLoad(-3.0))
        assertEquals("BW −19 kg", formatSetLoad(-19.0))
        assertEquals("BW −2.5 kg", formatSetLoad(-2.5))
    }

    @Test
    fun formatSetLoad_saysBodyweightWhenNothingIsLoaded() {
        assertEquals("BW", formatSetLoad(0.0))
        // Rounding must not turn a hair under zero into an assist of nothing.
        assertEquals("BW", formatSetLoad(-0.001))
        assertEquals("BW", formatSetLoad(0.001))
    }

    @Test
    fun formatSetLoad_dropsTrailingZeros() {
        assertEquals("22.5 kg", formatSetLoad(22.5))
        assertEquals("60 kg", formatSetLoad(60.0))
        assertEquals("17.25 kg", formatSetLoad(17.25))
    }

    @Test
    @Config(qualifiers = "de")
    fun formatSetLoad_writesTheLoadInTheLanguagesDecimalStyle() {
        assertEquals("22,5 kg", formatSetLoad(22.5))
        assertEquals("7,5", formatSetReps(7.5))
    }

    @Test
    fun formatSetReps_countsWithoutAUnit() {
        assertEquals("8", formatSetReps(8.0))
        assertEquals("7.5", formatSetReps(7.5))
    }

    @Test
    fun formatExerciseSets_saysOneLoadOnceAndThenTheReps() {
        assertEquals(
            "25 kg × 10 · 9 · 9",
            formatExerciseSets(
                listOf(LoggedSet(25.0, 10.0), LoggedSet(25.0, 9.0), LoggedSet(25.0, 9.0)),
            ),
        )
    }

    @Test
    fun formatExerciseSets_keepsTheBodyweightWording() {
        assertEquals(
            "BW −3 kg × 6 · 10 · 10",
            formatExerciseSets(
                listOf(LoggedSet(-3.0, 6.0), LoggedSet(-3.0, 10.0), LoggedSet(-3.0, 10.0)),
            ),
        )
        assertEquals(
            "BW × 12 · 10",
            formatExerciseSets(listOf(LoggedSet(0.0, 12.0), LoggedSet(0.0, 10.0))),
        )
    }

    @Test
    fun formatExerciseSets_spellsEverySetOutWhenTheLoadMoves() {
        assertEquals(
            "20 kg × 12 · 22.5 kg × 10 · 25 kg × 8",
            formatExerciseSets(
                listOf(LoggedSet(20.0, 12.0), LoggedSet(22.5, 10.0), LoggedSet(25.0, 8.0)),
            ),
        )
    }

    @Test
    fun formatExerciseSets_groupsLoadsExactlyWhenTheyReadTheSame() {
        // 22.125 is shown as 22.12, so it is not the same load as 22.13 even though both round to
        // 2213 hundredths.
        assertEquals(
            "22.12 kg × 10 · 22.13 kg × 9",
            formatExerciseSets(listOf(LoggedSet(22.125, 10.0), LoggedSet(22.13, 9.0))),
        )
        assertEquals(
            "22.5 kg × 10 · 9",
            formatExerciseSets(listOf(LoggedSet(22.5, 10.0), LoggedSet(22.501, 9.0))),
        )
    }

    @Test
    fun formatExerciseSets_leavesASingleSetAsItIs() {
        assertEquals("25 kg × 10", formatExerciseSets(listOf(LoggedSet(25.0, 10.0))))
        assertEquals("", formatExerciseSets(emptyList()))
    }

    /** Where no set recorded a weight, the average names its reps as reps, one or many as shown. */
    @Test
    fun formatAverageWorkoutSummary_namesRepsWithoutAWeight() {
        assertEquals("25 kg × 10", formatAverageWorkoutSummary(ExerciseWorkoutSummary(25.0, 10.0, setCount = 3, fromHistory = true)))
        assertEquals("6 reps", formatAverageWorkoutSummary(ExerciseWorkoutSummary(null, 6.0, setCount = 3, fromHistory = true)))
        assertEquals("1 rep", formatAverageWorkoutSummary(ExerciseWorkoutSummary(null, 1.0, setCount = 1, fromHistory = true)))
        assertEquals("1.5 reps", formatAverageWorkoutSummary(ExerciseWorkoutSummary(null, 1.5, setCount = 2, fromHistory = true)))
    }

    private fun assertEquals(expected: String, actual: UiText) =
        assertEquals(expected, actual.resolve(ApplicationProvider.getApplicationContext<Context>().resources))
}
