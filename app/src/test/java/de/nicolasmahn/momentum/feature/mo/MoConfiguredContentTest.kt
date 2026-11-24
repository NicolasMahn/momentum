package de.nicolasmahn.momentum.feature.mo

import de.nicolasmahn.momentum.domain.compatibility.contentKey

import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.core.text.UiText
import de.nicolasmahn.momentum.core.text.uiText
import de.nicolasmahn.momentum.domain.care.testCareChecklist
import de.nicolasmahn.momentum.domain.checklist.ChecklistConfiguration
import de.nicolasmahn.momentum.domain.checklist.MoMotif
import de.nicolasmahn.momentum.domain.mo.MoContentKey
import de.nicolasmahn.momentum.domain.mo.MoEvent
import de.nicolasmahn.momentum.domain.mo.MoFeeling
import de.nicolasmahn.momentum.domain.mo.MoIntensity
import de.nicolasmahn.momentum.domain.mo.MoMood
import de.nicolasmahn.momentum.domain.compatibility.MoNeed
import de.nicolasmahn.momentum.domain.mo.MoNeglect
import de.nicolasmahn.momentum.domain.mo.MoOccasion
import de.nicolasmahn.momentum.domain.mo.MoProp
import de.nicolasmahn.momentum.domain.mo.MoSaveAct
import de.nicolasmahn.momentum.domain.mo.MoState
import de.nicolasmahn.momentum.domain.mo.TrackerAsked
import de.nicolasmahn.momentum.domain.mo.MoViewKind
import de.nicolasmahn.momentum.domain.mo.contentKey
import de.nicolasmahn.momentum.domain.mo.figure
import de.nicolasmahn.momentum.domain.mo.moFeeling
import de.nicolasmahn.momentum.domain.mo.moOccasion
import de.nicolasmahn.momentum.domain.reminder.Bedtime
import de.nicolasmahn.momentum.domain.supplement.testSupplementsChecklist
import de.nicolasmahn.momentum.domain.trackerRef
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The flavor saved on Supplements' or Care's page is the one Mo shows and speaks, everywhere he
 * does: a task's content is decided from its configuration in one place, never from a fixed table.
 */
class MoConfiguredContentTest {
    private val today = LocalDate.parse("2026-10-14")
    private val noon = LocalDateTime.of(today, LocalTime.NOON)
    private val bedtime = Bedtime(LocalTime.of(7, 0), fixedBedtime = null)
    private val supplementsRef = MoNeed.Supplements.view.trackerRef
    private val supplementsWaiting = TrackerAsked(waited = mapOf(supplementsRef to listOf(20)))
    /** Supplements still open and mildly neglected, configured as [supplements] says. */
    private fun moodWith(supplements: ChecklistConfiguration?): MoMood {
        val base = contentComparisonMoods(MoState.Steady, MoNeed.Supplements, MoNeglect.Mild).first()
        val ref = MoNeed.Supplements.view.trackerRef
        val content = MoNeed.Supplements.contentKey(supplements)
        return base.copy(
            trackerLabels = if (supplements == null) base.trackerLabels - ref else base.trackerLabels + (ref to supplements.label),
            trackerContent = base.trackerContent + (ref to content),
            trackerCheckpoints = if (supplements == null) base.trackerCheckpoints - ref else base.trackerCheckpoints + (ref to supplements.checkpoints),
        )
    }

    private fun drawnOver100Days(mood: MoMood): Set<MoOccasion> =
        (0 until 100).map { moOccasion(mood, supplementsWaiting, noon.plusDays(it.toLong()), bedtime) }.toSet()

    @Test fun aSavedFlavorReachesMoWhileItsRewardStillFollowsTheChecklistKind() {
        val configuredLabel = UiText.Verbatim("Supplements after lunch")
        val mood = moodWith(testSupplementsChecklist().copy(mo = testSupplementsChecklist().mo.copy(motif = MoMotif.Scale), label = configuredLabel.value))
        val label = mood.label(MoNeed.Supplements.view.trackerRef)
        val weight = MoContentCatalogue[MoNeed.Weight.contentKey(null)]
        val genericChecklist = MoContentCatalogue[MoContentKey(MoViewKind.Checklist)]
        assertEquals(configuredLabel, label)
        assertEquals(MoContentKey(MoViewKind.Checklist, MoMotif.Scale), mood.contentKey(MoNeed.Supplements.view.trackerRef))

        // What lies on the floor, what he does after a save, what he acts out while it waits.
        assertEquals(listOf(MoProp.Scale), MoLook.of(mood).props.map { it.prop })
        assertEquals(MoSaveAct.Weighing, MoLook.of(mood, doing = MoNeed.Supplements.view.trackerRef).doing)
        val drawn = drawnOver100Days(mood)
        assertTrue("$drawn", MoOccasion.Unweighed in drawn && MoOccasion.Sickly !in drawn)
        val unweighedDay = (0 until 100).map { noon.plusDays(it.toLong()) }.first { moOccasion(mood, supplementsWaiting, it, bedtime) == MoOccasion.Unweighed }
        assertEquals(MoFeeling(MoOccasion.Unweighed, MoIntensity.Medium), moFeeling(mood, supplementsWaiting, unweighedDay, bedtime))

        // The line on Home and the takeover use the configured ache. The token still belongs to
        // this checklist's kind, even when its figure wears a Scale motif.
        assertEquals(weight.presentationAche(MoNeglect.Mild, label), (mood.line() as UiText.Joined).parts.first())
        val tokenEvent = MoEvent.TokenEarned(MoNeed.Supplements.view.trackerRef).words(mood)
        assertEquals(uiText(R.string.mo_event_token, configuredLabel), tokenEvent.headline)
        val tokenLine = tokenEvent.line
        assertEquals(genericChecklist.tokenEarnedLine(label, loggedDays = 6, keptDays = 7, weekMinutes = 60, activeDays = 5), tokenLine)
        assertNotEquals("the checklist motif must not borrow Weight's token rule", weight.tokenEarnedLine(label, loggedDays = 6, keptDays = 7, weekMinutes = 60, activeDays = 5), tokenLine)
        assertEquals(
            genericChecklist.weekGoalLine(label),
            MoEvent.WeekGoalReached(MoNeed.Supplements.view.trackerRef).words(mood).line,
        )
    }

    /** A view's own neglect belongs to the view, not to a checklist that borrows its look. */
    @Test fun aBorrowedLookTakesItsDegreeOnlyFromTheTaskAskedFor() {
        val mood = moodWith(testSupplementsChecklist().copy(mo = testSupplementsChecklist().mo.copy(motif = MoMotif.Scale)))
            .let { configured ->
                configured.copy(
                    trackerNeglect = mapOf(MoNeed.Weight.view.trackerRef to MoNeglect.Severe),
                )
            }
        val unweighedDay = (0 until 100).map { noon.plusDays(it.toLong()) }.first { moOccasion(mood, supplementsWaiting, it, bedtime) == MoOccasion.Unweighed }
        assertEquals(MoFeeling(MoOccasion.Unweighed, MoIntensity.Mild), moFeeling(mood, supplementsWaiting, unweighedDay, bedtime))
    }

    /** With nothing to act out, an overdue Plain checklist leaves him only his anger. */
    @Test fun aPlainChecklistOverdueNeverLeavesHimIdling() {
        val mood = moodWith(testSupplementsChecklist().copy(mo = testSupplementsChecklist().mo.copy(motif = null)))
        val overdue = TrackerAsked(waited = mapOf(supplementsRef to listOf(196)), overdueBy = mapOf(supplementsRef to 196))
        val drawn = (0 until 100).map { moOccasion(mood, overdue, noon.plusDays(it.toLong()), bedtime) }.toSet()
        assertEquals(setOf(MoOccasion.Angry), drawn)
    }

    @Test fun plainIsTheGenericChecklistContent() {
        val mood = moodWith(testSupplementsChecklist().copy(mo = testSupplementsChecklist().mo.copy(motif = null)))
        val label = mood.label(MoNeed.Supplements.view.trackerRef)
        val generic = MoContentCatalogue[MoContentKey(MoViewKind.Checklist)]
        assertEquals(MoContentKey(MoViewKind.Checklist, motif = null), mood.contentKey(MoNeed.Supplements.view.trackerRef))
        assertEquals(emptyList<MoProp>(), MoLook.of(mood).props.map { it.prop })
        assertEquals(MoSaveAct.Generic, MoLook.of(mood, doing = MoNeed.Supplements.view.trackerRef).doing)
        // Nothing to act out: he idles, or the season has its say, but he is never sickly over it.
        val drawn = drawnOver100Days(mood)
        assertTrue("$drawn", MoOccasion.Idle in drawn && MoOccasion.Sickly !in drawn)
        assertEquals(generic.tokenEarnedLine(label, loggedDays = 6, keptDays = 7, weekMinutes = 60, activeDays = 5), MoEvent.TokenEarned(MoNeed.Supplements.view.trackerRef).words(mood).line)
    }

    /** The stored motifs are Pills and Teeth, and so is Mo without a loaded configuration, so nothing he has shown so far changes. */
    @Test fun theStoredMotifsAndAMissingConfigurationKeepTheBuiltInContent() {
        val pills = MoContentKey(MoViewKind.Checklist, MoMotif.Pills)
        assertEquals(pills, moodWith(testSupplementsChecklist()).contentKey(MoNeed.Supplements.view.trackerRef))
        assertEquals(pills, moodWith(null).contentKey(MoNeed.Supplements.view.trackerRef))
        assertEquals(MoOccasion.Sickly, moodWith(null).contentKey(MoNeed.Supplements.view.trackerRef).figure.occasion)
        val teeth = MoContentKey(MoViewKind.Checklist, MoMotif.Teeth)
        assertEquals(teeth, MoNeed.Care.contentKey(testCareChecklist()))
        assertEquals(teeth, MoNeed.Care.contentKey(null))
        assertEquals(MoContentKey(MoViewKind.Time, MoMotif.Book), MoNeed.Reading.contentKey(null))
        assertEquals(MoContentKey(MoViewKind.Measurement, MoMotif.Scale), MoNeed.Weight.contentKey(null))
        assertEquals(MoContentKey(MoViewKind.Questions, MoMotif.Food), MoNeed.Eating.contentKey(null))
    }
}
