package com.example.workouttracker.feature.mo

import com.example.workouttracker.domain.compatibility.withWeightWeekRule
import com.example.workouttracker.domain.trackerRef
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import com.example.workouttracker.R
import com.example.workouttracker.core.text.pluralText
import com.example.workouttracker.core.text.resolve
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.compatibility.WeightMeasurementId
import com.example.workouttracker.domain.measurement.MeasurementEdit
import com.example.workouttracker.domain.measurement.MeasurementRecord
import com.example.workouttracker.domain.measurement.applyingMeasurementEdits
import com.example.workouttracker.domain.measurement.asMeasurementLogs
import com.example.workouttracker.domain.measurement.withWeight
import com.example.workouttracker.domain.mo.MoEvent
import com.example.workouttracker.domain.compatibility.MoNeed
import com.example.workouttracker.domain.mo.moEvents
import com.example.workouttracker.domain.mo.moMood
import com.example.workouttracker.domain.mo.only
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The sentence after a week of Weight earned a token names the rule that applied: the number of
 * logged days the week was closed with on its Sunday, also when today's number is already another.
 */
@RunWith(RobolectricTestRunner::class)
class WeightTokenSentenceTest {
    private val monday = LocalDate.parse("2026-10-05")
    private fun on(day: Int) = monday.plusDays(day.toLong())
    private fun weighed(days: IntRange) = AppState(measurementLogs = days.map { MeasurementRecord(on(it).toString(), it.toLong(), 80.0) }.asMeasurementLogs("weight")).withWeight()
    private fun AppState.saved(day: Int, days: Int) =
        withWeightWeekRule(on(day), maxPasses = 7, keptDaysInWeek = days)

    private fun sentenceOn(day: Int, state: AppState) = moMood(state, only(MoNeed.Weight), on(day)).let { mood ->
        assertTrue(MoEvent.TokenEarned(MoNeed.Weight.view.trackerRef) in moEvents(mood))
        MoEvent.TokenEarned(MoNeed.Weight.view.trackerRef).words(mood).line
    }

    @Test fun itNamesTheNumberOfTheClosingSundayNotTodays() {
        // Four days from Wednesday, four weigh-ins, and on the Monday the token is handed over two days are saved.
        val closedWithFour = weighed(0..3).saved(day = 2, days = 4)
        val line = sentenceOn(7, closedWithFour.saved(day = 7, days = 2))
        assertEquals(pluralText(R.plurals.mo_tracker_token_logged_days, 4, 4), line)
        assertEquals("4 recorded days last week earned it. It covers one miss.", line.resolve(resources(Locale.ENGLISH)))
        assertEquals("4 erfasste Tage letzte Woche haben sie verdient. Sie deckt ein Versäumnis ab.", line.resolve(resources(Locale.GERMAN)))
        assertEquals(line, sentenceOn(7, closedWithFour))
    }

    @Test fun untilASettingChangesItSaysSixAsItAlwaysDid() {
        val line = sentenceOn(7, weighed(0..5))
        assertEquals(pluralText(R.plurals.mo_tracker_token_logged_days, 6, 6), line)
        assertEquals("6 recorded days last week earned it. It covers one miss.", line.resolve(resources(Locale.ENGLISH)))
        assertEquals("6 erfasste Tage letzte Woche haben sie verdient. Sie deckt ein Versäumnis ab.", line.resolve(resources(Locale.GERMAN)))
    }

    /**
     * Once Weight earns by kept days the sentence names those days and no week. The Monday and
     * Tuesday logged under the week rule count toward the first token, so the Wednesday the change
     * is saved on is the third kept day.
     */
    @Test fun onceItEarnsByKeptDaysTheSentenceNamesNoWeek() {
        val byKeptDays = weighed(0..2).applyingMeasurementEdits(WeightMeasurementId, listOf(MeasurementEdit.SetBankRules(maxPasses = 7, everyKeptDays = 3)), on(2))
        val line = sentenceOn(3, byKeptDays)
        assertEquals(pluralText(R.plurals.mo_tracker_token_kept_days, 3, 3), line)
        assertEquals("3 completed days earned it. It covers one miss.", line.resolve(resources(Locale.ENGLISH)))
        assertEquals("3 abgeschlossene Tage haben sie verdient. Sie deckt ein Versäumnis ab.", line.resolve(resources(Locale.GERMAN)))
        // The Monday after, the week that closed under the new rule earned nothing by its Sunday.
        assertFalse(MoEvent.TokenEarned(MoNeed.Weight.view.trackerRef) in moEvents(moMood(byKeptDays, only(MoNeed.Weight), on(7))))
    }

    @Test fun orphanedRecordsWithoutAMeasurementConfigurationDoNotEarnAToken() {
        val ref = MoNeed.Weight.view.trackerRef
        val mood = moMood(weighed(0..5).copy(measurements = emptyList()), only(MoNeed.Weight), on(7))

        assertFalse(MoEvent.TokenEarned(ref) in moEvents(mood))
        assertFalse(ref in mood.tokenEarningBasisByTracker)
    }

    @Test fun oneDayIsOneWeighIn() {
        val line = sentenceOn(7, weighed(0..0).saved(day = 0, days = 1))
        assertEquals(pluralText(R.plurals.mo_tracker_token_logged_days, 1, 1), line)
        assertEquals("1 recorded day last week earned it. It covers one miss.", line.resolve(resources(Locale.ENGLISH)))
    }

    private fun resources(locale: Locale): Resources {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configuration = Configuration(context.resources.configuration).apply { setLocale(locale) }
        return context.createConfigurationContext(configuration).resources
    }
}
