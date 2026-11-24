package de.nicolasmahn.momentum.feature.mo

import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.core.text.UiText
import de.nicolasmahn.momentum.core.text.pluralText
import de.nicolasmahn.momentum.core.text.uiText
import de.nicolasmahn.momentum.domain.TrackerRef
import de.nicolasmahn.momentum.domain.TrackerTypeId
import de.nicolasmahn.momentum.domain.mo.MoContentKey
import de.nicolasmahn.momentum.domain.mo.MoDayMark
import de.nicolasmahn.momentum.domain.mo.MoEvent
import de.nicolasmahn.momentum.domain.mo.MoMood
import de.nicolasmahn.momentum.domain.mo.MoState
import de.nicolasmahn.momentum.domain.mo.MoStreak
import de.nicolasmahn.momentum.domain.mo.MoViewKind
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackerEventWordsTest {
    @Test
    fun anUnlistedTimerUsesItsClosedEarningBasisAndOwnLabel() {
        val ref = ref("time", "practice")
        val label = UiText.Verbatim("Instrument practice")
        val mood = mood(
            ref = ref,
            label = label.value,
            kind = MoViewKind.Time,
            basis = 60,
        )

        val words = MoEvent.TokenEarned(ref).words(mood)
        assertEquals(uiText(R.string.mo_event_token, label), words.headline)
        assertEquals(pluralText(R.plurals.mo_tracker_token_week_minutes, 60, 60), words.line)
    }

    @Test
    fun everyNativeEarningKindUsesItsUnitWithoutAnInheritedTrackerId() {
        val cases = listOf(
            Triple("measurement", MoViewKind.Measurement, pluralText(R.plurals.mo_tracker_token_logged_days, 6, 6)),
            Triple("questions", MoViewKind.Questions, pluralText(R.plurals.mo_tracker_token_kept_days, 6, 6)),
            Triple("time", MoViewKind.Time, pluralText(R.plurals.mo_tracker_token_week_minutes, 6, 6)),
            Triple("activity", MoViewKind.Activity, pluralText(R.plurals.mo_event_token_line_activity_days, 4, 4)),
        )
        cases.forEach { (type, kind, expected) ->
            val ref = ref(type, "configured")
            val mood = mood(ref = ref, label = "My $type", kind = kind, basis = if (kind == MoViewKind.Activity) 4 else 6)

            assertEquals("$kind from a non-inherited ref", expected, MoEvent.TokenEarned(ref).words(mood).line)
        }
    }

    @Test
    fun anUnknownEarningBasisFallsBackToNeutralConfiguredLabelCopy() {
        val ref = ref("time", "untimed")
        val label = UiText.Verbatim("Quiet practice")
        val mood = mood(ref = ref, label = label.value, kind = MoViewKind.Time, basis = null)

        val words = MoEvent.TokenEarned(ref).words(mood)
        assertEquals(uiText(R.string.mo_event_token, label), words.headline)
        assertEquals(uiText(R.string.mo_tracker_token_earned, label), words.line)
    }

    private fun mood(ref: TrackerRef, label: String, kind: MoViewKind, basis: Int?): MoMood = MoMood(
        state = MoState.Steady,
        dueToday = 0,
        daysCold = 0,
        streak = MoStreak(
            days = 0,
            freezes = 0,
            freezeCapacity = 3,
            week = List(7) { MoDayMark.Upcoming },
            weekCanEarnFreeze = false,
        ),
        trackerLabels = mapOf(ref to label),
        trackerContent = mapOf(ref to MoContentKey(kind)),
        tokenEarningBasisByTracker = basis?.let { mapOf(ref to it) }.orEmpty(),
    )

    private fun ref(type: String, id: String) = TrackerRef(TrackerTypeId(type), id)
}
