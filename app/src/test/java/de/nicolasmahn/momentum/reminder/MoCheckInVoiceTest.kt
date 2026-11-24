package de.nicolasmahn.momentum.reminder

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import de.nicolasmahn.momentum.core.text.resolve
import de.nicolasmahn.momentum.domain.mo.MoDayMark
import de.nicolasmahn.momentum.domain.mo.MoMood
import de.nicolasmahn.momentum.domain.trackerRef
import de.nicolasmahn.momentum.domain.compatibility.MoNeed
import de.nicolasmahn.momentum.domain.mo.MoNeglect
import de.nicolasmahn.momentum.domain.mo.MoState
import de.nicolasmahn.momentum.domain.mo.MoStreak
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Checks his check-in on the real English words, the ones the notification shows. */
@RunWith(RobolectricTestRunner::class)
class MoCheckInVoiceTest {
    @Test
    fun openTasksAndAFrozenMoodStaySilentWithoutAnythingOverdue() {
        listOf(MoState.Steady, MoState.Cold, MoState.Husk).forEach { state ->
            val open = mood(state, missingToday = listOf(MoNeed.Care))
            assertNull(nudgeMessage(open, emptyList()))
            assertNull(nudgeMessage(open.copy(streak = open.streak.copy(frozenTrackersYesterday = listOf(MoNeed.Reading.view.trackerRef))), emptyList()))
        }
    }

    @Test
    fun aBurningMoFumesAboutTheClockAndExactlyWhatIsOverdue() {
        val late = listOf(MoNeed.Reading, MoNeed.Care)
        // Activity is still to come today, so he must not fume about it yet.
        val missing = late + MoNeed.Activity

        listOf(MoState.Blazing, MoState.Steady, MoState.Dimming).forEach { state ->
            assertEquals(
                state.name,
                Said(
                    title = "Mo is seeing red",
                    text = "Look at the clock: 18:00. Still not done: Reading, Care. " +
                        "Get going and I'll simmer down.",
                ),
                nudgeMessage(mood(state, missingToday = missing), late, at = "2026-09-16T18:00"),
            )
        }
    }

    @Test
    fun theFumingLinesRotateThroughADayAndAcrossMidnight() {
        val mood = mood(state = MoState.Steady, missingToday = listOf(MoNeed.Weight))
        fun at(nudge: String) = nudgeMessage(mood, mood.missingTrackersToday.mapNotNull { ref -> MoNeed.entries.firstOrNull { it.view.trackerRef == ref } }, at = nudge)

        assertEquals(
            listOf(
                Said(
                    "Mo is fuming",
                    "It's already 12:00 and you still haven't: Weight. I'm glowing red over here.",
                ),
                Said(
                    "Mo is boiling over",
                    "It's 15:00! Overdue: Weight. Off you go, and watch me cool down.",
                ),
                Said(
                    "Mo is seeing red",
                    "Look at the clock: 18:00. Still not done: Weight. Get going and I'll simmer down.",
                ),
                Said(
                    "Mo is heating up",
                    "21:00, and I'm still waiting for: Weight. Go now, before I boil over.",
                ),
            ),
            listOf("12:00", "15:00", "18:00", "21:00").map { at("2026-09-16T$it") },
        )
        assertEquals("Mo is boiling over", at("2026-09-17T12:00")?.title)
        assertEquals("Mo is seeing red", at("2026-09-18T12:00")?.title)
    }

    @Test
    fun aMoWithoutFireKeepsHisSadVoiceNarrowedToWhatIsOverdue() {
        val late = listOf(MoNeed.Care)
        val missing = late + MoNeed.Activity

        assertEquals(
            Said(
                title = "Mo is getting cold",
                text = "I've been cold for 2 days. One of these would warm me: Care.",
            ),
            nudgeMessage(mood(MoState.Cold, missingToday = missing, daysCold = 2), late),
        )
        assertEquals(
            Said(
                title = "Mo has gone out",
                text = "Mo has been out for 5 days. One record rekindles him: Care.",
            ),
            nudgeMessage(mood(MoState.Husk, missingToday = missing, daysCold = 5), late),
        )
        assertEquals(
            Said(
                title = "Mo is frozen",
                text = "A freeze saved your streak yesterday (Reading). Thaw me: Care.",
            ),
            nudgeMessage(
                mood(MoState.Steady, missingToday = missing, frozenYesterday = listOf(MoNeed.Reading)),
                late,
            ),
        )
        // In the ice a Husk is as silent as out of it: the app speaks about him.
        assertEquals(
            Said(
                title = "Mo is frozen",
                text = "A freeze saved your streak yesterday (Reading). One record thaws Mo: Care.",
            ),
            nudgeMessage(
                mood(MoState.Husk, missingToday = missing, daysCold = 5, frozenYesterday = listOf(MoNeed.Reading)),
                late,
            ),
        )
    }

    @Test
    fun theClockFollowsTheDevice() {
        assertEquals("18:00", moClock(LocalTime.of(18, 0), is24Hour = true, Locale.ENGLISH))
        assertEquals("09:05", moClock(LocalTime.of(9, 5), is24Hour = true, Locale.ENGLISH))
        assertEquals("6 PM", moClock(LocalTime.of(18, 0), is24Hour = false, Locale.ENGLISH))
        assertEquals("12 PM", moClock(LocalTime.of(12, 0), is24Hour = false, Locale.ENGLISH))
        assertEquals("7:30 AM", moClock(LocalTime.of(7, 30), is24Hour = false, Locale.ENGLISH))

        val mood = mood(state = MoState.Steady, missingToday = listOf(MoNeed.Reading))
        assertEquals(
            "It's already 12 PM and you still haven't: Reading. I'm glowing red over here.",
            nudgeMessage(mood, mood.missingTrackersToday.mapNotNull { ref -> MoNeed.entries.firstOrNull { it.view.trackerRef == ref } }, at = "2026-09-16T12:00", is24Hour = false)?.text,
        )
    }

    @Test
    @Config(qualifiers = "de")
    fun missingTrackerLabelsUseTheReferenceWithoutInferringAnOriginalName() {
        listOf(MoState.Dimming, MoState.Cold).forEach { state ->
            val archived = mood(state, missingToday = listOf(MoNeed.Activity)).copy(trackerLabels = emptyMap())
            val text = requireNotNull(nudgeMessage(archived, listOf(MoNeed.Activity))).text
            assertTrue(text, text.contains("activity"))
            assertFalse(text, text.contains("Aktivität"))
        }
    }

    private fun nudgeMessage(
        mood: MoMood,
        overdue: List<MoNeed>,
        at: String = "2026-09-16T15:00",
        is24Hour: Boolean = true,
    ): Said? = moNudgeMessage(mood, overdue.map { it.view.trackerRef }, LocalDateTime.parse(at), is24Hour)?.let {
        val resources = ApplicationProvider.getApplicationContext<Context>().resources
        Said(it.title.resolve(resources), it.text.resolve(resources))
    }

    private data class Said(val title: String, val text: String)

    private fun mood(
        state: MoState,
        missingToday: List<MoNeed> = emptyList(),
        keptToday: List<MoNeed> = emptyList(),
        latestToday: MoNeed? = null,
        dueToday: Int = missingToday.size + keptToday.size,
        neglect: Map<MoNeed, MoNeglect> = emptyMap(),
        frozenYesterday: List<MoNeed> = emptyList(),
        daysCold: Int = 0,
    ) = MoMood(
        state = state,
        missingTrackersToday = missingToday.map { it.view.trackerRef },
        trackerLabels = MoNeed.entries.associate { it.view.trackerRef to it.name },
        keptTrackersToday = keptToday.map { it.view.trackerRef },
        latestTrackerToday = latestToday?.view?.trackerRef,
        dueToday = dueToday,
        daysCold = daysCold,
        streak = MoStreak(
            days = 0,
            freezes = 0,
            freezeCapacity = 3,
            week = listOf(MoDayMark.Open) + List(6) { MoDayMark.Upcoming },
            weekCanEarnFreeze = true,
            frozenTrackersYesterday = frozenYesterday.map { it.view.trackerRef },
        ),
        trackerNeglect = neglect.mapKeys { it.key.view.trackerRef },
    )
}
