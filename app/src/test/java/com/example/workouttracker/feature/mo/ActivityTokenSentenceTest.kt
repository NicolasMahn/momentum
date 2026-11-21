package com.example.workouttracker.feature.mo

import com.example.workouttracker.domain.trackerRef
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import com.example.workouttracker.R
import com.example.workouttracker.core.text.resolve
import com.example.workouttracker.core.text.uiText
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.activity.ActivityEdit
import com.example.workouttracker.domain.activity.ActivityEntry
import com.example.workouttracker.domain.activity.WeekPlan
import com.example.workouttracker.domain.activity.applyingActivityEdits
import com.example.workouttracker.domain.activity.withActivity
import com.example.workouttracker.domain.mo.MoEvent
import com.example.workouttracker.domain.compatibility.MoNeed
import com.example.workouttracker.domain.mo.moEvents
import com.example.workouttracker.domain.mo.moMood
import com.example.workouttracker.domain.mo.only
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The sentence after a week of Activity earned a token names the days a week the week had to
 * reach on the Sunday that earned it, in both languages and in the singular too. With five, the
 * days it took before they became a setting, it is the sentence it always was.
 */
@RunWith(RobolectricTestRunner::class)
class ActivityTokenSentenceTest {
    private val monday = LocalDate.parse("2026-10-05")
    private fun on(day: Int) = monday.plusDays(day.toLong())
    private fun active(vararg days: Int) = AppState(
        activityEntries = days.map { ActivityEntry("e$it", "bouldering", "Bouldering", on(it).toString(), it.toLong()) },
    ).withActivity()

    private fun sentenceOn(day: Int, state: AppState) = moMood(state, only(MoNeed.Activity), on(day)).let { mood ->
        assertTrue(MoEvent.TokenEarned(MoNeed.Activity.view.trackerRef) in moEvents(mood))
        MoEvent.TokenEarned(MoNeed.Activity.view.trackerRef).words(mood).line
    }

    @Test fun untilASettingChangesItIsWordForWordTheSentenceItWas() {
        val line = sentenceOn(7, active(0, 1, 2, 3, 4))
        assertEquals(uiText(R.string.mo_event_token_line_activity), line)
        assertEquals("5 active days last week earned it. It covers one miss.", line.resolve(resources(Locale.ENGLISH)))
        assertEquals("Verdient mit 5 aktiven Tagen letzte Woche. Es deckt einen verpassten Tag ab.", line.resolve(resources(Locale.GERMAN)))
    }

    @Test fun oneDayIsOneDayInBothLanguages() {
        val oneDayAWeek = active(2).applyingActivityEdits(listOf(ActivityEdit.SetWeek(WeekPlan.of(DayOfWeek.MONDAY))), on(0))
        val line = sentenceOn(7, oneDayAWeek)
        assertEquals("1 active day last week earned it. It covers one miss.", line.resolve(resources(Locale.ENGLISH)))
        assertEquals("Verdient mit 1 aktivem Tag letzte Woche. Es deckt einen verpassten Tag ab.", line.resolve(resources(Locale.GERMAN)))
    }

    /** Five said through the plural reads as the fixed sentence does, so the two can never drift apart unseen. */
    @Test fun thePluralAtFiveIsTheSameSentence() {
        listOf(Locale.ENGLISH, Locale.GERMAN).forEach { locale ->
            val resources = resources(locale)
            assertEquals(resources.getString(R.string.mo_event_token_line_activity), resources.getQuantityString(R.plurals.mo_event_token_line_activity_days, 5, 5))
        }
    }

    private fun resources(locale: Locale): Resources {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configuration = Configuration(context.resources.configuration).apply { setLocale(locale) }
        return context.createConfigurationContext(configuration).resources
    }
}
