package com.example.workouttracker.core.text

import android.content.Context
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import com.example.workouttracker.R
import com.example.workouttracker.core.format.DateStyle
import com.example.workouttracker.core.format.relativeDateText
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The words a model decides read correctly in each language once they reach the screen. */
@RunWith(RobolectricTestRunner::class)
class UiTextTest {
    @Test
    fun placeholdersTakeResolvedTextNumbersAndDates() {
        val text = uiText(
            R.string.measurement_chart_selected_with_trend,
            UiText.Date(LocalDate.of(2026, 10, 3), DateStyle.WeekdayDayMonth),
            UiText.Number(82.5),
            UiText.Number(82.25),
        )

        assertEquals("Selected Sat, Oct 3: 82.5, trend 82.25", text.resolve(resources()))
    }

    @Test
    @Config(qualifiers = "de")
    fun germanFormatsTheSameValuesItsOwnWay() {
        val text = uiText(
            R.string.measurement_chart_selected_with_trend,
            UiText.Date(LocalDate.of(2026, 10, 3), DateStyle.WeekdayDayMonth),
            UiText.Number(82.5),
            UiText.Number(82.25),
        )

        assertEquals("Ausgewählt Sa., 3. Okt.: 82,5, Trend 82,25", text.resolve(resources()))
    }

    @Test
    fun pluralsChooseTheirFormByCount() {
        assertEquals("yesterday", relativeDateText("2026-10-02", "2026-10-03").resolve(resources()))
        assertEquals("2 days ago", relativeDateText("2026-10-01", "2026-10-03").resolve(resources()))
        assertEquals("Sat, Sep 26", relativeDateText("2026-09-26", "2026-10-03").resolve(resources()))
    }

    @Test
    @Config(qualifiers = "de")
    fun germanPluralsAndDates() {
        assertEquals("gestern", relativeDateText("2026-10-02", "2026-10-03").resolve(resources()))
        assertEquals("vor 2 Tagen", relativeDateText("2026-10-01", "2026-10-03").resolve(resources()))
        assertEquals("Sa., 26. Sept.", relativeDateText("2026-09-26", "2026-10-03").resolve(resources()))
    }

    @Test
    fun userTextIsShownExactlyAsEntered() {
        val note = "  Pasta, 2× – mit Käse %s  "
        assertEquals(note, UiText.Verbatim(note).resolve(resources()))
    }

    @Test
    fun joinedTextKeepsEachPartWhole() {
        val parts = listOf(uiText(R.string.feature_weight), UiText.Verbatim("bowling"))
        assertEquals("Weight, bowling", parts.joinedBy(", ").resolve(resources()))
    }

    @Test
    fun aListReadsTheWayItsLanguageJoinsOne() {
        val tasks = listOf(UiText.Verbatim("a"), UiText.Verbatim("b"), UiText.Verbatim("c"))
        assertEquals("a", tasks.take(1).joinedWithAnd().resolve(resources()))
        assertEquals("a and b", tasks.take(2).joinedWithAnd().resolve(resources()))
        assertEquals("a, b and c", tasks.joinedWithAnd().resolve(resources()))
    }

    @Test
    @Config(qualifiers = "de")
    fun germanJoinsAListWithItsOwnAnd() {
        val tasks = listOf(UiText.Verbatim("a"), UiText.Verbatim("b"), UiText.Verbatim("c"))
        assertEquals("a, b und c", tasks.joinedWithAnd().resolve(resources()))
    }

    private fun resources(): Resources = ApplicationProvider.getApplicationContext<Context>().resources
}
