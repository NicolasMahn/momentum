package de.nicolasmahn.momentum.domain.mo

import de.nicolasmahn.momentum.domain.compatibility.contentKey

import de.nicolasmahn.momentum.domain.compatibility.MoNeed

import de.nicolasmahn.momentum.domain.checklist.MoMotif
import org.junit.Assert.assertEquals
import org.junit.Test

class MoContentTest {
    @Test
    fun genericTimeMeasurementAndQuestionsStartWithANeutralFigure() {
        val neutral = MoFigureContent(
            occasion = MoOccasion.Idle,
            prop = MoProp.None,
            saveAct = MoSaveAct.Generic,
            thrivingLook = MoThrivingLook.None,
        )

        listOf(MoViewKind.Time, MoViewKind.Measurement, MoViewKind.Questions).forEach { kind ->
            assertEquals("$kind has no inherited prop by default", neutral, MoContentKey(kind).figure)
        }
    }

    @Test
    fun establishedProfilesChooseTheirMotifExplicitly() {
        assertEquals(MoContentKey(MoViewKind.Time, MoMotif.Book), MoNeed.Reading.contentKey(null))
        assertEquals(MoContentKey(MoViewKind.Measurement, MoMotif.Scale), MoNeed.Weight.contentKey(null))
        assertEquals(MoContentKey(MoViewKind.Questions, MoMotif.Food), MoNeed.Eating.contentKey(null))

        assertEquals(MoProp.Book, MoNeed.Reading.contentKey(null).figure.prop)
        assertEquals(MoProp.Scale, MoNeed.Weight.contentKey(null).figure.prop)
        assertEquals(MoProp.EmptyBowl, MoNeed.Eating.contentKey(null).figure.prop)
    }

    @Test
    fun theSameMotifAddsTheSameFigureToDifferentKinds() {
        listOf(MoMotif.Book, MoMotif.Scale, MoMotif.Food).forEach { motif ->
            val time = MoContentKey(MoViewKind.Time, motif).figure
            val checklist = MoContentKey(MoViewKind.Checklist, motif).figure

            assertEquals("$motif remains a figure choice, independent of tracker kind", time, checklist)
        }
    }
}
