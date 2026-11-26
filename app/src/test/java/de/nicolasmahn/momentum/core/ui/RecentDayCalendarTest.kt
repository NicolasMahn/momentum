package de.nicolasmahn.momentum.core.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import de.nicolasmahn.momentum.core.text.resolve
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class RecentDayCalendarTest {
    @Test
    fun selectedStateTakesPriorityOverTheBaselineOutline() {
        assertEquals(
            RecentDayCalendarEmphasis.SELECTED,
            recentDayCalendarEmphasis(isSelected = true),
        )
    }

    @Test
    fun everyUnselectedDayUsesTheBaselineOutline() {
        assertEquals(
            RecentDayCalendarEmphasis.BASELINE,
            recentDayCalendarEmphasis(isSelected = false),
        )
    }

    @Test
    fun todayDescriptionRetainsFeatureStatus() {
        assertEquals(
            "Today, Thu, Sep 17: No activity. Select date.",
            todayDescription(status = "No activity", action = "Select date"),
        )
    }

    @Test
    @Config(qualifiers = "de")
    fun germanTodayDescriptionUsesGermanDate() {
        assertEquals(
            "Heute, Do., 17. Sept.: Keine Aktivität. Datum auswählen.",
            todayDescription(status = "Keine Aktivität", action = "Datum auswählen"),
        )
    }

    private fun todayDescription(status: String, action: String): String =
        recentDayCalendarDescription(
            dateKey = "2026-09-17",
            isToday = true,
            statusDescription = status,
            actionDescription = action,
        ).resolve(ApplicationProvider.getApplicationContext<Context>().resources)
}
