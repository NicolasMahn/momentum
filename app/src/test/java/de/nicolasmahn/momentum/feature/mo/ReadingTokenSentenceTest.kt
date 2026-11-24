package de.nicolasmahn.momentum.feature.mo

import de.nicolasmahn.momentum.domain.trackerRef
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.core.text.pluralText
import de.nicolasmahn.momentum.core.text.resolve
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.compatibility.ReadingTimeId
import de.nicolasmahn.momentum.domain.activity.withActivity
import de.nicolasmahn.momentum.domain.mo.MoEvent
import de.nicolasmahn.momentum.domain.compatibility.MoNeed
import de.nicolasmahn.momentum.domain.mo.moEvents
import de.nicolasmahn.momentum.domain.mo.moMood
import de.nicolasmahn.momentum.domain.mo.only
import de.nicolasmahn.momentum.domain.time.TimeEdit
import de.nicolasmahn.momentum.domain.time.TimeLog
import de.nicolasmahn.momentum.domain.time.applyingTimeEdits
import de.nicolasmahn.momentum.domain.time.withReading
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The sentence after a week of Reading-earned time names the minutes the week had to reach on the
 * Sunday that earned it, also when today's goal is already another. Its words stay generic to the
 * Time kind even when Reading gives Mo a book to draw.
 */
@RunWith(RobolectricTestRunner::class)
class ReadingTokenSentenceTest {
    private val monday = LocalDate.parse("2026-10-05")
    private fun on(day: Int) = monday.plusDays(day.toLong())
    private fun read(minutes: Int) = AppState(timeLogs = listOf(TimeLog(ReadingTimeId, on(1).toString(), 1L, minutes))).withReading().withActivity()
    private fun AppState.saved(day: Int, minutes: Int) = applyingTimeEdits(ReadingTimeId, listOf(TimeEdit.SetGoal(minutes)), on(day))

    private fun sentenceOn(day: Int, state: AppState) = moMood(state, only(MoNeed.Reading), on(day)).let { mood ->
        assertTrue(MoEvent.TokenEarned(MoNeed.Reading.view.trackerRef) in moEvents(mood))
        MoEvent.TokenEarned(MoNeed.Reading.view.trackerRef).words(mood).line
    }

    @Test fun theDefaultGoalUsesTheGenericTimeKindLine() {
        val line = sentenceOn(7, read(60))
        assertEquals(pluralText(R.plurals.mo_tracker_token_week_minutes, 60, 60), line)
        assertEquals("60 recorded minutes last week earned it. It covers one miss.", line.resolve(resources(Locale.ENGLISH)))
        assertEquals("60 erfasste Minuten letzte Woche haben sie verdient. Sie deckt ein Versäumnis ab.", line.resolve(resources(Locale.GERMAN)))
    }

    @Test fun orphanedLogsWithoutATimeConfigurationDoNotEarnAToken() {
        val ref = MoNeed.Reading.view.trackerRef
        val mood = moMood(read(60).copy(time = emptyList()), only(MoNeed.Reading), on(7))

        assertFalse(MoEvent.TokenEarned(ref) in moEvents(mood))
        assertFalse(ref in mood.tokenEarningBasisByTracker)
    }

    @Test fun itNamesTheGoalOfTheWeekThatEarnedItNotTodays() {
        // Forty-five minutes reach a goal of forty-five, and on the Monday the token is handed over ninety is saved.
        val earnedByFortyFive = read(45).saved(day = 0, minutes = 45)
        val line = sentenceOn(7, earnedByFortyFive.saved(day = 7, minutes = 90))
        assertEquals("45 recorded minutes last week earned it. It covers one miss.", line.resolve(resources(Locale.ENGLISH)))
        assertEquals("45 erfasste Minuten letzte Woche haben sie verdient. Sie deckt ein Versäumnis ab.", line.resolve(resources(Locale.GERMAN)))
        assertEquals(line, sentenceOn(7, earnedByFortyFive))
    }

    @Test fun oneMinuteIsOneMinute() {
        val line = sentenceOn(7, read(1).saved(day = 0, minutes = 1))
        assertEquals("1 recorded minute last week earned it. It covers one miss.", line.resolve(resources(Locale.ENGLISH)))
        assertEquals("1 erfasste Minute letzte Woche hat sie verdient. Sie deckt ein Versäumnis ab.", line.resolve(resources(Locale.GERMAN)))
    }

/** The Time kind's plural owns the same number in both locales. */
    @Test fun thePluralAtSixtyIsTheSameSentence() {
        val line = sentenceOn(7, read(60))
        listOf(Locale.ENGLISH, Locale.GERMAN).forEach { locale ->
            val resources = resources(locale)
            assertEquals(resources.getQuantityString(R.plurals.mo_tracker_token_week_minutes, 60, 60), line.resolve(resources))
        }
    }

    private fun resources(locale: Locale): Resources {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configuration = Configuration(context.resources.configuration).apply { setLocale(locale) }
        return context.createConfigurationContext(configuration).resources
    }
}
