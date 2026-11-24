package de.nicolasmahn.momentum.feature.about

import android.content.Context
import androidx.compose.ui.text.font.FontWeight
import androidx.test.core.app.ApplicationProvider
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The legal texts ship in both languages, and their page shows the words without the Markdown. */
@RunWith(RobolectricTestRunner::class)
class LegalTextsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun bothLanguagesShipAndEveryOtherLanguageReadsEnglish() {
        val english = context.legalText(LegalText.PrivacyPolicy, Locale.ENGLISH)
        val german = context.legalText(LegalText.PrivacyPolicy, Locale.GERMAN)

        assertTrue(english.startsWith("# Privacy policy"))
        assertTrue(german.startsWith("# Datenschutzerklärung"))
        assertEquals(german, context.legalText(LegalText.PrivacyPolicy, Locale("de", "AT")))
        assertEquals(english, context.legalText(LegalText.PrivacyPolicy, Locale.FRENCH))
        assertTrue(context.legalText(LegalText.LegalNotice, Locale.ENGLISH).startsWith("# Legal notice"))
        assertTrue(context.legalText(LegalText.LegalNotice, Locale.GERMAN).startsWith("# Impressum"))
    }

    @Test
    fun theTwoLanguagesHaveTheSameShape() {
        for (text in LegalText.entries) {
            val shapes = listOf(Locale.ENGLISH, Locale.GERMAN).map { locale ->
                legalBlocks(context.legalText(text, locale)).map { it::class }
            }

            assertEquals(text.name, shapes[0], shapes[1])
        }
    }

    @Test
    fun noMarkdownMarkIsShown() {
        for (text in LegalText.entries) {
            for (locale in listOf(Locale.ENGLISH, Locale.GERMAN)) {
                val blocks = legalBlocks(context.legalText(text, locale))

                assertTrue(blocks.isNotEmpty())
                blocks.forEach { block ->
                    val shown = block.text.text
                    assertTrue(shown, shown.isNotBlank() && "**" !in shown && "`" !in shown && !shown.startsWith("#") && !shown.startsWith("- "))
                }
            }
        }
        assertTrue(legalBlocks(context.legalText(LegalText.PrivacyPolicy, Locale.ENGLISH)).size > 20)
    }

    /** An address a letter can reach is name, street and town, each on its own line. */
    @Test
    fun theLegalNoticeNamesWhoOffersTheAppWithAnAddressAndTheContactAddressOfThePolicy() {
        for (locale in listOf(Locale.ENGLISH, Locale.GERMAN)) {
            val blocks = legalBlocks(context.legalText(LegalText.LegalNotice, locale)).map { it.text.text }
            val address = blocks.single { it.startsWith("Nicolas Mahn\n") }

            assertEquals(blocks.toString(), 4, address.lines().size)
            val mail = blocks.last().substringAfter(": ")
            assertTrue(mail, mail in context.legalText(LegalText.PrivacyPolicy, locale))
        }
    }

    @Test
    fun aParagraphKeepsItsBoldLeadAndItsLines() {
        val blocks = legalBlocks("# Title\n\n## Part\n\n**One.** Two `three`\nfour\n\n- five\n- six")

        assertEquals(listOf("Part", "One. Two three\nfour", "five", "six"), blocks.map { it.text.text })
        assertTrue(blocks[0] is LegalBlock.Heading && blocks[1] is LegalBlock.Paragraph && blocks[3] is LegalBlock.Bullet)
        val bold = blocks[1].text.spanStyles.single()
        assertEquals(FontWeight.Bold, bold.item.fontWeight)
        assertEquals("One.", blocks[1].text.text.substring(bold.start, bold.end))
    }
}
