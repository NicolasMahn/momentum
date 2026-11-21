package com.example.workouttracker.feature.about

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.workouttracker.core.locale.currentLocale
import java.util.Locale

/** A text the law asks the app to carry, by the name of its files in `assets/privacy`. */
internal enum class LegalText(val file: String) {
    PrivacyPolicy("privacy-policy"),

    /** Who offers the app and where they can be reached, which German law calls the Impressum. */
    LegalNotice("legal-notice"),
}

/**
 * A legal text as the app ships it, in German where the app speaks German and in English
 * everywhere else. `assets/privacy` holds the one copy of each: the public pages, among them the
 * one Google Play links to, are published from the same files, so the two cannot drift apart.
 */
internal fun Context.legalText(text: LegalText, locale: Locale): String {
    val language = if (locale.language == Locale.GERMAN.language) "de" else "en"
    return assets.open("privacy/${text.file}-$language.md").bufferedReader().use { it.readText() }
}

/** One piece of a legal text as the page lays it out. */
internal sealed interface LegalBlock {
    val text: AnnotatedString

    data class Heading(override val text: AnnotatedString) : LegalBlock
    data class Paragraph(override val text: AnnotatedString) : LegalBlock
    data class Bullet(override val text: AnnotatedString) : LegalBlock
}

/**
 * A legal text's Markdown as blocks. It reads only what these texts use: `##` headings, `-` lists,
 * paragraphs whose lines stay lines, `**bold**` and code marks. The `#` title is left out, since
 * the page's header already carries it.
 */
internal fun legalBlocks(markdown: String): List<LegalBlock> {
    val blocks = mutableListOf<LegalBlock>()
    val paragraph = mutableListOf<String>()

    fun closeParagraph() {
        if (paragraph.isNotEmpty()) blocks += LegalBlock.Paragraph(inline(paragraph.joinToString("\n")))
        paragraph.clear()
    }

    for (line in markdown.lines().map { it.trim() }) {
        when {
            line.isEmpty() -> closeParagraph()
            line.startsWith("# ") -> closeParagraph()
            line.startsWith("## ") -> {
                closeParagraph()
                blocks += LegalBlock.Heading(inline(line.removePrefix("## ")))
            }
            line.startsWith("- ") -> {
                closeParagraph()
                blocks += LegalBlock.Bullet(inline(line.removePrefix("- ")))
            }
            else -> paragraph += line
        }
    }
    closeParagraph()
    return blocks
}

/** A line with its `**bold**` parts set bold and its code marks dropped. */
private fun inline(text: String): AnnotatedString = buildAnnotatedString {
    text.replace("`", "").split("**").forEachIndexed { index, part ->
        if (index % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(part) } else append(part)
    }
}

/** One legal text in full. It holds nothing, so leaving it asks nothing. */
@Composable
internal fun LegalTextPage(text: LegalText) {
    val context = LocalContext.current
    val locale = currentLocale()
    val blocks = remember(context, locale, text) { legalBlocks(context.legalText(text, locale)) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // One text for each block, so TalkBack reads the text in steps and not as one node.
        blocks.forEachIndexed { index, block ->
            when (block) {
                is LegalBlock.Heading -> Text(
                    text = block.text,
                    modifier = Modifier.testTag("legal_heading_$index"),
                    style = MaterialTheme.typography.titleMedium,
                )
                is LegalBlock.Paragraph -> Text(
                    text = block.text,
                    modifier = Modifier.testTag("legal_paragraph_$index"),
                    style = MaterialTheme.typography.bodyMedium,
                )
                is LegalBlock.Bullet -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = BulletMark, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = block.text,
                        modifier = Modifier.weight(1f).testTag("legal_bullet_$index"),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

private const val BulletMark = "•"
