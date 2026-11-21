package com.example.workouttracker.feature.mo

import com.example.workouttracker.domain.trackerRef
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import com.example.workouttracker.R
import com.example.workouttracker.core.text.pluralText
import com.example.workouttracker.core.text.resolve
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.compatibility.EatingQuestionsId
import com.example.workouttracker.domain.mo.MoEvent
import com.example.workouttracker.domain.compatibility.MoNeed
import com.example.workouttracker.domain.mo.moEvents
import com.example.workouttracker.domain.mo.moMood
import com.example.workouttracker.domain.mo.only
import com.example.workouttracker.domain.mo.reflectionAbout
import com.example.workouttracker.domain.questions.QuestionsEdit
import com.example.workouttracker.domain.questions.applyingQuestionsEdits
import com.example.workouttracker.domain.questions.withEating
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The sentence after kept days of Eating earned a token names the number of days the rule took on
 * the day it was earned, also when today's number is already another. Its words stay generic to
 * the Questions kind even when Eating gives Mo a bowl to draw.
 */
@RunWith(RobolectricTestRunner::class)
class EatingTokenSentenceTest {
    private val monday = LocalDate.parse("2026-10-05")
    private fun on(day: Int) = monday.plusDays(day.toLong())
    private fun kept(days: IntRange) = AppState(questionEntries = days.map { reflectionAbout(on(it - 1)) }).withEating()
    private fun AppState.saved(day: Int, days: Int) =
        applyingQuestionsEdits(EatingQuestionsId, listOf(QuestionsEdit.SetRewardRules(maxPasses = 3, everyKeptDays = days)), on(day))

    private fun sentenceOn(day: Int, state: AppState) = moMood(state, only(MoNeed.Eating), on(day)).let { mood ->
        assertTrue(MoEvent.TokenEarned(MoNeed.Eating.view.trackerRef) in moEvents(mood))
        MoEvent.TokenEarned(MoNeed.Eating.view.trackerRef).words(mood).line
    }

    @Test fun theDefaultRuleUsesTheGenericQuestionsKindLine() {
        val line = sentenceOn(7, kept(0..6))
        assertEquals(pluralText(R.plurals.mo_tracker_token_kept_days, 7, 7), line)
        assertEquals("7 completed days earned it. It covers one miss.", line.resolve(resources(Locale.ENGLISH)))
        assertEquals("7 abgeschlossene Tage haben sie verdient. Sie deckt ein Versäumnis ab.", line.resolve(resources(Locale.GERMAN)))
    }

    @Test fun orphanedEntriesWithoutAQuestionsConfigurationDoNotEarnAToken() {
        val ref = MoNeed.Eating.view.trackerRef
        val mood = moMood(kept(0..6).copy(questions = emptyList()), only(MoNeed.Eating), on(7))

        assertFalse(MoEvent.TokenEarned(ref) in moEvents(mood))
        assertFalse(ref in mood.tokenEarningBasisByTracker)
    }

    @Test fun itNamesTheNumberOfTheDayThatEarnedItNotTodays() {
        // Three kept days earn it on Wednesday, and on the Thursday the token is handed over five days are saved.
        val earnedByThree = kept(0..2).saved(day = 0, days = 3)
        val line = sentenceOn(3, earnedByThree.saved(day = 3, days = 5))
        assertEquals("3 completed days earned it. It covers one miss.", line.resolve(resources(Locale.ENGLISH)))
        assertEquals("3 abgeschlossene Tage haben sie verdient. Sie deckt ein Versäumnis ab.", line.resolve(resources(Locale.GERMAN)))
        assertEquals(line, sentenceOn(3, earnedByThree))
    }

    @Test fun oneDayIsOneDay() {
        val line = sentenceOn(1, kept(0..0).saved(day = 0, days = 1))
        assertEquals("1 completed day earned it. It covers one miss.", line.resolve(resources(Locale.ENGLISH)))
        assertEquals("1 abgeschlossener Tag hat sie verdient. Sie deckt ein Versäumnis ab.", line.resolve(resources(Locale.GERMAN)))
    }

    /** The earned token uses the Questions plural for the kept-day count in both languages. */
    @Test fun thePluralAtSevenIsTheSameSentence() {
        val line = sentenceOn(7, kept(0..6))
        listOf(Locale.ENGLISH, Locale.GERMAN).forEach { locale ->
            val resources = resources(locale)
            assertEquals(resources.getQuantityString(R.plurals.mo_tracker_token_kept_days, 7, 7), line.resolve(resources))
        }
    }

    private fun resources(locale: Locale): Resources {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configuration = Configuration(context.resources.configuration).apply { setLocale(locale) }
        return context.createConfigurationContext(configuration).resources
    }
}
