package de.nicolasmahn.momentum.feature.mo

import de.nicolasmahn.momentum.domain.compatibility.contentKey

import de.nicolasmahn.momentum.core.text.UiText
import de.nicolasmahn.momentum.domain.mo.MoEvent
import de.nicolasmahn.momentum.domain.mo.MoMood
import de.nicolasmahn.momentum.domain.compatibility.MoNeed
import de.nicolasmahn.momentum.domain.mo.MoNeglect
import de.nicolasmahn.momentum.domain.mo.MoProp
import de.nicolasmahn.momentum.domain.mo.MoState
import de.nicolasmahn.momentum.domain.mo.MoThrivingLook
import de.nicolasmahn.momentum.domain.mo.contentKey
import de.nicolasmahn.momentum.domain.mo.figure
import de.nicolasmahn.momentum.domain.TrackerRef
import de.nicolasmahn.momentum.domain.trackerRef
import org.junit.Assert.assertEquals
import org.junit.Test

class MoContentPreservationTest {
    @Test
    fun eachTaskKeepsEveryDrawingChoiceAndThePresentationAche() {
        MoNeed.entries.forEach { need ->
            val key = need.contentKey(storedChecklists[need])
            val content = MoContentCatalogue[key]
            assertEquals("$need domain drawing choices", need.frozenFigureContent(), key.figure)
            assertEquals("$need catalogue drawing choices", need.frozenFigureContent(), content.figure)
            MoNeglect.entries.forEach { neglect ->
                val label = storedChecklists[need]?.let { UiText.Verbatim(it.label) } ?: UiText.Verbatim(need.name)
                assertEquals(
                    "$need $neglect presentation ache",
                    need.frozenPresentationAche(),
                    content.presentationAche(neglect, label),
                )
            }
        }
    }

    @Test
    fun everyExistingPresentationKeepsItsResourceIdsAndArguments() {
        MoNeed.entries.forEach { need ->
            MoState.entries.forEach { state ->
                (listOf(null) + MoNeglect.entries).forEach { neglect ->
                    contentComparisonMoods(state, need, neglect).forEachIndexed { scenario, mood ->
                        assertEquals("$need $state $neglect scenario $scenario", mood.frozenLine(), mood.line())
                    }
                }
            }
        }
        MoState.entries.forEach { state ->
            val base = contentComparisonMoods(state, MoNeed.Care, null).first()
            val neglected = MoNeed.entries.associateWith { MoNeglect.entries[it.ordinal % MoNeglect.entries.size] }
            listOf(neglected, neglected.entries.reversed().associate { it.toPair() }).forEach { order ->
                val mood = base.copy(
                    missingTrackersToday = MoNeed.entries.map { it.view.trackerRef },
                    dueToday = MoNeed.entries.size,
                    trackerNeglect = order.mapKeys { (inherited, _) -> inherited.view.trackerRef },
                )
                assertEquals("mixed aches in $state with order ${order.keys}", mood.frozenLine(), mood.line())
            }
        }
    }

    @Test
    fun everyMoodKeepsItsVisiblePropsTeethSaveActAndThrivingLook() {
        MoNeed.entries.forEach { need ->
            MoState.entries.forEach { state ->
                (listOf(null) + MoNeglect.entries).forEach { neglect ->
                    contentComparisonMoods(state, need, neglect).forEach { mood ->
                        assertDrawingChoices(mood)
                    }
                }
            }
        }
        MoState.entries.forEach { state ->
            val base = contentComparisonMoods(state, MoNeed.Care, null).first()
            val neglected = MoNeed.entries.associateWith { MoNeglect.entries[it.ordinal % MoNeglect.entries.size] }
            listOf(neglected, neglected.entries.reversed().associate { it.toPair() }).forEach { order ->
                assertDrawingChoices(
                    base.copy(
                        missingTrackersToday = MoNeed.entries.map { it.view.trackerRef },
                        trackerNeglect = order.mapKeys { (inherited, _) -> inherited.view.trackerRef },
                        thrivingTrackers = MoNeed.entries.mapTo(LinkedHashSet()) { it.view.trackerRef },
                    ),
                )
                assertDrawingChoices(
                    base.copy(
                        missingTrackersToday = listOf(MoNeed.Activity.view.trackerRef),
                        freeTrackersToday = listOf(MoNeed.Reading.view.trackerRef),
                        keptTrackersToday = listOf(MoNeed.Care, MoNeed.Supplements, MoNeed.Weight, MoNeed.Eating)
                            .map { it.view.trackerRef },
                        trackerNeglect = order.mapKeys { (inherited, _) -> inherited.view.trackerRef },
                    ),
                )
            }
        }
    }

    @Test
    fun everyExistingTakeoverKeepsItsHeadlineLineAndArguments() {
        MoNeed.entries.forEach { need ->
            MoState.entries.forEach { state ->
                (listOf(null) + MoNeglect.entries).forEach { neglect ->
                    contentComparisonMoods(state, need, neglect).forEachIndexed { scenario, mood ->
                        val events = listOf(
                            MoEvent.StreakMilestone(1),
                            MoEvent.StreakMilestone(14),
                            MoEvent.PerfectWeek,
                            MoEvent.Comeback,
                            MoEvent.WeekGoalReached(need.view.trackerRef),
                            MoEvent.TokenEarned(need.view.trackerRef),
                            MoEvent.FreezeEarned,
                        )
                        events.forEach { event ->
                            val expected = event.frozenWords(mood)
                            val actual = event.words(mood)
                            assertEquals("$event $state $neglect scenario $scenario headline", expected.headline, actual.headline)
                            assertEquals("$event $state $neglect scenario $scenario line", expected.line, actual.line)
                        }
                    }
                }
            }
        }
    }

    private fun assertDrawingChoices(mood: MoMood) {
        (listOf(null) + MoNeed.entries).forEach { doing ->
            listOf(false, true).forEach { angry ->
                // These are the old MoLook.of, drawProps and mouth selections, expressed in the
                // closed vocabulary rather than sharing the new catalogue or its adapter.
                val visible = mood.trackerNeglect
                    .filterKeys { it == MoNeed.Care.view.trackerRef || it in mood.missingTrackersToday || it in mood.freeTrackersToday }
                    .mapNotNull { (ref, level) -> ref.inheritedNeed()?.let { it to level } }
                    .toMap()
                val expected = MoLook(
                    state = mood.state,
                    neglect = visible.map { (need, level) -> MoNeglectedDrawing(need.frozenFigureContent().prop, level, need.ordinal) },
                    thriving = mood.thrivingTrackers.mapNotNullTo(mutableSetOf()) { it.inheritedNeed()?.frozenFigureContent()?.thrivingLook } - MoThrivingLook.None,
                    doing = doing?.frozenFigureContent()?.saveAct,
                    frozen = mood.streak.frozenTrackersYesterday.isNotEmpty() && mood.latestTrackerToday == null,
                    angry = angry && mood.state >= MoState.Dimming,
                )
                val actual = MoLook.of(mood, doing = doing?.view?.trackerRef, angry = angry)
                assertEquals("look for ${mood.state}, $visible, doing $doing, angry $angry", expected, actual)
                val props = visible.entries
                    .filter { it.key != MoNeed.Care }
                    .sortedWith(compareByDescending<Map.Entry<MoNeed, MoNeglect>> { it.value }.thenBy { it.key })
                    .take(2)
                    .map { (need, level) -> MoNeglectedDrawing(need.frozenFigureContent().prop, level, need.ordinal) }
                assertEquals("props for $visible", props, actual.props)
                assertEquals("teeth for $visible", visible[MoNeed.Care], actual.teethNeglect)
                assertEquals("Care teeth remain separate from floor props", false, actual.props.any { it.prop == MoProp.LostTeeth })
            }
        }
    }

    private fun TrackerRef.inheritedNeed(): MoNeed? =
        MoNeed.entries.firstOrNull { it.view.trackerRef == this }
}
