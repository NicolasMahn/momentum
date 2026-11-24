package de.nicolasmahn.momentum.feature.mo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import de.nicolasmahn.momentum.core.text.UiText
import de.nicolasmahn.momentum.core.text.resolve
import de.nicolasmahn.momentum.domain.mo.MoDayMark
import de.nicolasmahn.momentum.domain.mo.MoMood
import de.nicolasmahn.momentum.domain.compatibility.MoNeed
import de.nicolasmahn.momentum.domain.mo.MoState
import de.nicolasmahn.momentum.domain.mo.MoStreak
import de.nicolasmahn.momentum.domain.TrackerRef
import de.nicolasmahn.momentum.domain.trackerRef
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** His words as the screen, the widget and his notifications show them. */
@RunWith(RobolectricTestRunner::class)
class MoSpeechTest {
    @Test
    fun aFreezeSpentYesterdayIsNamedAndTheWayOutFollows() {
        val frozen = mood(
            MoState.Steady,
            missing = listOf(MoNeed.Care, MoNeed.Reading).map { it.view.trackerRef },
            frozenTrackersYesterday = listOf(MoNeed.Weight, MoNeed.Eating).map { it.view.trackerRef },
        )

        assertEquals("A freeze saved your streak yesterday (Weight, Eating). Thaw me: Care, Reading.", frozen.line().resolved())
        assertEquals(
            "A freeze saved your streak yesterday (Weight, Eating). Log anything to thaw me.",
            frozen.copy(missingTrackersToday = emptyList(), dueToday = 2).line().resolved(),
        )
    }

    @Test
    fun aHuskIsSpokenAboutAndASingleDayReadsAsADay() {
        assertEquals(
            "Mo has been out for a day. One record rekindles him: Care.",
            mood(MoState.Husk, missing = listOf(MoNeed.Care.view.trackerRef), daysCold = 1).line().resolved(),
        )
        assertEquals(
            "I've been cold for 4 days. One of these would warm me: Care.",
            mood(MoState.Cold, missing = listOf(MoNeed.Care.view.trackerRef), daysCold = 4).line().resolved(),
        )
    }

    @Test
    fun theReadingNudgeCountsMinutesAsAQuantity() {
        val reading = MoNeed.Reading.view.trackerRef
        val done = mood(MoState.Dimming, dueToday = 1).copy(trackerWeeklyNudges = mapOf(reading to 25))

        assertEquals(
            "Thank you. I can feel it getting warmer. Reading: 1 minute still to go this week.",
            done.copy(trackerWeeklyNudges = mapOf(reading to 1)).line().resolved(),
        )
        assertEquals(
            "Thank you. I can feel it getting warmer. Reading: 25 minutes still to go this week.",
            done.copy(trackerWeeklyNudges = mapOf(reading to 25)).line().resolved(),
        )
    }

    @Test
    fun theStreakSummaryOnlyNamesFreezesOnceThereAreSome() {
        assertEquals("1 day streak", streak(days = 1, freezes = 0).summary().resolved())
        assertEquals("12 day streak · 1 freeze", streak(days = 12, freezes = 1).summary().resolved())
        assertEquals("12 day streak · 2 freezes", streak(days = 12, freezes = 2).summary().resolved())
    }

    @Test
    @Config(qualifiers = "de")
    fun theWidgetSpeaksGerman() {
        assertEquals("Mo ist erloschen", MoState.Husk.description().resolved())
        assertEquals("Streak: 1 Tag", streak(days = 1, freezes = 0).summary().resolved())
        assertEquals("Streak: 12 Tage · 2 Freezes", streak(days = 12, freezes = 2).summary().resolved())
    }

    private fun UiText.resolved(): String = resolve(ApplicationProvider.getApplicationContext<Context>().resources)

    private fun streak(days: Int, freezes: Int, frozenTrackersYesterday: List<TrackerRef> = emptyList()) = MoStreak(
        days = days,
        freezes = freezes,
        freezeCapacity = 3,
        week = listOf(MoDayMark.Open) + List(6) { MoDayMark.Upcoming },
        weekCanEarnFreeze = true,
        frozenTrackersYesterday = frozenTrackersYesterday,
    )

    private fun mood(
        state: MoState,
        missing: List<TrackerRef> = emptyList(),
        dueToday: Int = missing.size,
        frozenTrackersYesterday: List<TrackerRef> = emptyList(),
        daysCold: Int = 0,
    ) = MoMood(
        state = state,
        dueToday = dueToday,
        daysCold = daysCold,
        streak = streak(days = 0, freezes = 0, frozenTrackersYesterday = frozenTrackersYesterday),
        trackerLabels = MoNeed.entries.associate { it.view.trackerRef to it.name },
        missingTrackersToday = missing,
    )
}
