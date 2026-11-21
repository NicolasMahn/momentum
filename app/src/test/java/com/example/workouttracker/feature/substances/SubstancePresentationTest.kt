package com.example.workouttracker.feature.substances

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.example.workouttracker.core.ui.theme.MomentumHues
import com.example.workouttracker.domain.substance.OwnSubstance
import com.example.workouttracker.domain.substance.SubstanceAmountUnit
import com.example.workouttracker.domain.substance.SubstanceKind
import com.example.workouttracker.domain.substance.SubstanceLog
import com.example.workouttracker.domain.substance.SubstanceSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubstancePresentationTest {
    private val everything = SubstanceSettings(tracked = SubstanceKind.entries.mapTo(LinkedHashSet()) { it.id })

    @Test
    fun noSubstanceReadsAsAnErrorOrAsTheFeatureItselfAndTheThreeKeepTheirHues() {
        SubstanceKind.entries.forEach { kind ->
            val hue = everything.hue(kind)
            // Red is reserved for errors and destruction, Pink is the Substances view itself.
            assertNotEquals(kind.name, MomentumHues.Red, hue)
            assertNotEquals(kind.name, MomentumHues.Pink, hue)
        }
        assertEquals(MomentumHues.Green, everything.hue(SubstanceKind.CANNABIS))
        assertEquals(MomentumHues.Yellow, everything.hue(SubstanceKind.ALCOHOL))
        assertEquals(MomentumHues.Sky, everything.hue(SubstanceKind.TOBACCO))
    }

    /** Fifteen hues for sixteen substances: the ones likely tracked together must still differ. */
    @Test
    fun substancesLikelyTrackedTogetherAreToldApart() {
        listOf(
            listOf(SubstanceKind.TOBACCO, SubstanceKind.VAPING, SubstanceKind.NICOTINE_POUCHES, SubstanceKind.CANNABIS),
            listOf(SubstanceKind.COFFEE, SubstanceKind.ENERGY_DRINKS, SubstanceKind.TOBACCO, SubstanceKind.ALCOHOL),
            listOf(SubstanceKind.ALCOHOL, SubstanceKind.COCAINE, SubstanceKind.MDMA, SubstanceKind.KETAMINE, SubstanceKind.AMPHETAMINES, SubstanceKind.GHB, SubstanceKind.CANNABIS),
            listOf(SubstanceKind.ALCOHOL, SubstanceKind.OPIOIDS, SubstanceKind.BENZODIAZEPINES, SubstanceKind.TOBACCO),
            listOf(SubstanceKind.COCAINE, SubstanceKind.CRACK, SubstanceKind.ALCOHOL, SubstanceKind.TOBACCO),
            listOf(SubstanceKind.CANNABIS, SubstanceKind.PSYCHEDELICS, SubstanceKind.MDMA, SubstanceKind.KETAMINE),
        ).forEach { group ->
            val hues = group.map { everything.hue(it) }
            assertEquals("$group must wear different hues", hues.size, hues.toSet().size)
        }
    }

    @Test
    fun everyAccentHasReadableSelectedContent() {
        val own = OwnSubstance("sub_k2p9", "Kratom", SubstanceAmountUnit.GRAMS)
        val settings = SubstanceSettings(tracked = SubstanceKind.entries.map { it.id }.toSet() + own.id, own = listOf(own))
        settings.all.forEach { substance ->
            val colors = settings.presentation(substance).colors
            assertTrue(
                "${substance.id} selected content needs accessible contrast",
                contrastRatio(colors.accent, colors.onAccent) >= 4.5f,
            )
        }
    }

    /** An own substance wears a hue no tracked substance uses while one is left, and keeps it as long as the choice stands. */
    @Test
    fun anOwnSubstanceTakesAHueNoTrackedSubstanceWears() {
        val first = OwnSubstance("sub_a", "Kratom", SubstanceAmountUnit.GRAMS)
        val second = OwnSubstance("sub_b", "Kava", SubstanceAmountUnit.CUPS)
        val tracked = listOf(SubstanceKind.ALCOHOL, SubstanceKind.TOBACCO, SubstanceKind.CANNABIS, SubstanceKind.MDMA, SubstanceKind.AMPHETAMINES)
        val settings = SubstanceSettings(tracked = tracked.map { it.id }.toSet() + first.id + second.id, own = listOf(first, second))

        val taken = tracked.map { settings.hue(it) }.toSet()
        val firstHue = settings.hue(first)
        val secondHue = settings.hue(second)
        assertTrue(firstHue !in taken && secondHue !in taken)
        assertNotEquals(firstHue, secondHue)
        assertNotEquals(MomentumHues.Red, firstHue)
        assertNotEquals(MomentumHues.Pink, firstHue)
        // The same choice always deals the same hues.
        assertEquals(firstHue, SubstanceSettings(settings.tracked, settings.own).hue(first))

        // With every hue worn, an own substance still gets one, and two own substances still differ.
        val crowded = SubstanceSettings(tracked = SubstanceKind.entries.map { it.id }.toSet() + first.id + second.id, own = listOf(first, second))
        assertNotEquals(crowded.hue(first), crowded.hue(second))
    }

    private fun contrastRatio(first: Color, second: Color): Float {
        val lighter = maxOf(first.luminance(), second.luminance())
        val darker = minOf(first.luminance(), second.luminance())
        return (lighter + 0.05f) / (darker + 0.05f)
    }
}
