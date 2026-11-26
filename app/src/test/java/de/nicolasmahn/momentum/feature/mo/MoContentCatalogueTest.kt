package de.nicolasmahn.momentum.feature.mo

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.core.text.UiText
import de.nicolasmahn.momentum.core.text.pluralText
import de.nicolasmahn.momentum.core.text.resolve
import de.nicolasmahn.momentum.core.text.uiText
import de.nicolasmahn.momentum.domain.checklist.MoMotif
import de.nicolasmahn.momentum.domain.mo.MoContentKey
import de.nicolasmahn.momentum.domain.mo.MoNeglect
import de.nicolasmahn.momentum.domain.mo.MoProp
import de.nicolasmahn.momentum.domain.mo.MoSaveAct
import de.nicolasmahn.momentum.domain.mo.MoThrivingLook
import de.nicolasmahn.momentum.domain.mo.MoViewKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class MoContentCatalogueTest {
    @Test
    fun everyKindHasLocalizedAchesAndRewardLinesInEveryTone() {
        val english = resources(Locale.ENGLISH)
        val german = resources(Locale.GERMAN)

        MoViewKind.entries.forEach { kind ->
            val content = MoContentCatalogue[MoContentKey(kind)]
            MoTone.entries.forEach { tone ->
                val texts = buildList {
                    content.reminderAche(MoNeglect.Mild, tone, label)?.let(::add)
                    add(content.presentationAche(MoNeglect.Mild, label))
                    add(content.tokenEarnedLine(label, loggedDays = 6, keptDays = 7, weekMinutes = 60, activeDays = 5))
                    add(content.weekGoalLine(label))
                }
                texts.forEach { text ->
                    assertTrue("$kind $tone has an empty moment: $text", text.resolve(english).isNotBlank())
                    assertTrue("$kind $tone has an empty German moment: $text", text.resolve(german).isNotBlank())
                }
            }
        }
    }

    @Test
    fun activityKeepsDetailedAndPlainReminderVoices() {
        val content = MoContentCatalogue[MoContentKey(MoViewKind.Activity)]
        val english = resources(Locale.ENGLISH)
        val german = resources(Locale.GERMAN)
        MoTone.entries.forEach { tone ->
            listOf(content.reminder(tone), content.reminder(tone, detailed = false)).forEach { reminder ->
                assertTrue("$tone needs rotating reminder lines", reminder.lines.isNotEmpty())
                val texts = listOf(reminder.title(label, details)) + reminder.lines.indices.map { index ->
                    reminder.line(day.plusDays(index.toLong()), label, details)
                }
                texts.forEach { text ->
                    assertTrue(text.resolve(english).isNotBlank())
                    assertTrue(text.resolve(german).isNotBlank())
                }
            }
        }
    }

    @Test
    fun genericChecklistKeepsItsLabelInLocalizedAcheAndRewardCopy() {
        val content = MoContentCatalogue[MoContentKey(MoViewKind.Checklist)]
        assertEquals(MoProp.None, content.figure.prop)
        assertEquals(MoSaveAct.Generic, content.figure.saveAct)
        assertEquals(MoThrivingLook.None, content.figure.thrivingLook)
        val english = resources(Locale.ENGLISH)
        val german = resources(Locale.GERMAN)
        val texts = listOf(
            content.presentationAche(MoNeglect.Mild, label),
            content.tokenEarnedLine(label, loggedDays = 6, keptDays = 7, weekMinutes = 60, activeDays = 5),
            content.weekGoalLine(label),
        )

        texts.forEach { text ->
            val en = text.resolve(english)
            val de = text.resolve(german)
            assertContainsOnce(en, label.value)
            assertContainsOnce(de, label.value)
            assertNotEquals(en, de)
        }
    }

    @Test
    fun motifsKeepTheirFigureAndAcheWithoutTakingTheKindsTokenRule() {
        MoViewKind.entries.forEach { kind ->
            val generic = MoContentCatalogue[MoContentKey(kind)]
            MoMotif.entries.forEach { motif ->
                val flavored = MoContentCatalogue[MoContentKey(kind, motif)]
                assertEquals("$kind with $motif token rule", token(generic), token(flavored))
                assertEquals("$kind with $motif weekly goal", generic.weekGoalLine(label), flavored.weekGoalLine(label))
            }
        }

        val timeWithReading = MoContentCatalogue[MoContentKey(MoViewKind.Time, MoMotif.Book)]
        val checklistWithReading = MoContentCatalogue[MoContentKey(MoViewKind.Checklist, MoMotif.Book)]
        assertEquals(timeWithReading.figure, checklistWithReading.figure)
        assertEquals(MoProp.Book, timeWithReading.figure.prop)
        assertEquals(uiText(R.string.mo_ache_reading), timeWithReading.presentationAche(MoNeglect.Mild, label))
        assertEquals(pluralText(R.plurals.mo_tracker_token_week_minutes, 60, 60), token(timeWithReading))
        assertEquals(token(MoContentCatalogue[MoContentKey(MoViewKind.Checklist)]), token(checklistWithReading))
        assertNotEquals(token(timeWithReading), token(checklistWithReading))

        val timeWithScale = MoContentCatalogue[MoContentKey(MoViewKind.Time, MoMotif.Scale)]
        val checklistWithScale = MoContentCatalogue[MoContentKey(MoViewKind.Checklist, MoMotif.Scale)]
        assertEquals(timeWithScale.figure, checklistWithScale.figure)
        assertEquals(MoProp.Scale, timeWithScale.figure.prop)

        val timeLine = token(timeWithScale)
        assertEquals(pluralText(R.plurals.mo_tracker_token_week_minutes, 60, 60), timeLine)
        assertEquals("60 recorded minutes last week earned it. It covers one miss.", timeLine.resolve(resources(Locale.ENGLISH)))
        assertEquals("60 erfasste Minuten letzte Woche haben sie verdient. Sie deckt ein Versäumnis ab.", timeLine.resolve(resources(Locale.GERMAN)))

        val checklistLine = token(checklistWithReading)
        assertEquals(uiText(R.string.mo_tracker_token_earned, label), checklistLine)
        listOf(Locale.ENGLISH, Locale.GERMAN).forEach { locale ->
            val words = checklistLine.resolve(resources(locale))
            assertFalse("a checklist motif does not make a checklist a time tracker: $words", words.contains("minute", ignoreCase = true) || words.contains("Minute", ignoreCase = true))
        }
    }

    private fun token(content: MoContent) = content.tokenEarnedLine(
        label,
        loggedDays = 6,
        keptDays = 7,
        weekMinutes = 60,
        activeDays = 5,
    )

    private fun assertContainsOnce(text: String, value: String) {
        assertTrue("expected '$value' in: $text", text.contains(value))
        assertEquals("the exact label should occur once: $text", 1, text.windowed(value.length).count { it == value })
    }

    private fun resources(locale: Locale): Resources {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configuration = Configuration(context.resources.configuration).apply { setLocale(locale) }
        return context.createConfigurationContext(configuration).resources
    }

    private val day = LocalDate.parse("2026-10-05")
    private val label = UiText.Verbatim("Die Tasche / 薬 💊 25% \$name")
    private val details: List<UiText> = listOf(UiText.Verbatim("open items"))
}
