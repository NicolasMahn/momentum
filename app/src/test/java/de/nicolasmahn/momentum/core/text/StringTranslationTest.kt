package de.nicolasmahn.momentum.core.text

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.w3c.dom.Element

/**
 * Every language Momentum speaks is complete: each English string has a translation that reads the
 * same arguments and carries the plural forms its language needs. A missing string shows English
 * in the middle of German; an argument the caller does not give crashes the moment it is shown, and
 * one left out drops a value.
 *
 * Each failure lists every offending name, grouped by file, so one run says all that is left to do.
 */
class StringTranslationTest {
    private val english = load("values")
    private val translations = resourceDirectory().listFiles()!!
        .filter { it.isDirectory && it.name.matches(Regex("""values-[a-z]{2}(-r[A-Z]{2})?""")) }
        .sortedBy { it.name }
        .associate { it.name to load(it.name) }

    @Test
    fun thereIsAtLeastOneTranslation() {
        assertTrue(translations.isNotEmpty())
    }

    @Test
    fun everyEnglishStringIsTranslated() = assertNoOffenders("has no translation for") { strings ->
        // A <string> where English has <plurals> is a different resource, so the plural is still missing.
        english.values
            .filter { it.translatable && strings[it.name]?.isPlural != it.isPlural }
            .map { it to it.name }
    }

    @Test
    fun everyTranslatedStringExistsInEnglish() = assertNoOffenders("translates strings English does not have") { strings ->
        strings.values.filter { it.name !in english }.map { it to it.name }
    }

    @Test
    fun untranslatableStringsAreNeverTranslated() = assertNoOffenders("translates fixed strings") { strings ->
        strings.values.filter { english[it.name]?.translatable == false }.map { it to it.name }
    }

    /** Each form is checked against English's `other` form, whose arguments are what callers pass; English's own `one` too. */
    @Test
    fun everyFormReadsTheArgumentsEnglishIsGiven() =
        assertNoOffenders("disagrees with English arguments", mapOf("values" to english) + translations) { strings ->
            strings.values.flatMap { entry ->
                val expected = english[entry.name]?.forms?.get("other") ?: return@flatMap emptyList()
                entry.forms.mapNotNull { (quantity, text) ->
                    argumentMismatch(expected, text, isOneForm = entry.isPlural && quantity == "one")
                        ?.let { problem -> entry to "${entry.name}[$quantity] $problem" }
                }
            }
        }

    @Test
    fun pluralsCarryTheFormsTheirLanguageNeeds() =
        assertNoOffenders("needs plural forms one and other", mapOf("values" to english) + translations) { strings ->
            strings.values
                .filter { it.isPlural && !it.forms.keys.containsAll(listOf("one", "other")) }
                .map { it to "${it.name} has ${it.forms.keys}" }
        }

    /** One string resource; [file] is where it lives, so a failure can say where to look. */
    private class Entry(
        val file: String,
        val name: String,
        val forms: Map<String, String>,
        val isPlural: Boolean,
        val translatable: Boolean,
    )

    /** Fails with every offender of every folder, each paired with what to say about it. */
    private fun assertNoOffenders(
        problem: String,
        folders: Map<String, Map<String, Entry>> = translations,
        offenders: (Map<String, Entry>) -> List<Pair<Entry, String>>,
    ) {
        val report = folders.mapNotNull { (folder, strings) ->
            val byFile = offenders(strings).groupBy({ (entry, _) -> entry.file }, { (_, detail) -> detail })
            if (byFile.isEmpty()) return@mapNotNull null
            byFile.toSortedMap().entries.joinToString(
                separator = "\n",
                prefix = "$folder $problem (${byFile.values.sumOf { it.size }}):\n",
            ) { (file, details) -> "  $file: ${details.joinToString()}" }
        }
        if (report.isNotEmpty()) fail(report.joinToString("\n"))
    }

    private fun load(folder: String): Map<String, Entry> {
        val files = File(resourceDirectory(), folder).listFiles { file ->
            file.name.startsWith("strings") && file.extension == "xml"
        }.orEmpty().sortedBy { it.name }
        return files.flatMap { file ->
            val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
            root.children().mapNotNull { element ->
                val name = element.getAttribute("name")
                val translatable = element.getAttribute("translatable") != "false"
                when (element.tagName) {
                    "string" -> Entry(file.name, name, mapOf("other" to element.textContent), false, translatable)
                    "plurals" -> Entry(
                        file = file.name,
                        name = name,
                        forms = element.children().associate { it.getAttribute("quantity") to it.textContent },
                        isPlural = true,
                        translatable = translatable,
                    )
                    else -> null
                }
            }
        }.associateBy { it.name }
    }

    private fun Element.children(): List<Element> =
        (0 until childNodes.length).map(childNodes::item).filterIsInstance<Element>()

    private fun resourceDirectory(): File {
        val module = generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "src/main/res").isDirectory }
        return File(module, "src/main/res")
    }
}
