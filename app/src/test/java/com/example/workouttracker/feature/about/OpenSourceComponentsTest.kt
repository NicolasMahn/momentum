package com.example.workouttracker.feature.about

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The list of open-source software the app ships, read as the app reads it. A component without
 * the text of its license would be shipped without the one thing its license asks for.
 */
@RunWith(RobolectricTestRunner::class)
class OpenSourceComponentsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val components = context.openSourceComponents()

    @Test
    fun everyComponentNamesWhereItComesFromAndALicenseWhoseTextIsStored() {
        assertTrue(components.isNotEmpty())
        components.forEach { component ->
            assertTrue(component.toString(), component.name.isNotBlank())
            assertTrue(component.toString(), component.source.startsWith("https://"))
            assertTrue(component.toString(), component.license.name.isNotBlank())
            assertTrue(component.toString(), component.notice?.isNotBlank() != false)
            // A real license, not a stub: the shortest of them is over a thousand characters.
            assertTrue(component.toString(), context.licenseText(component.license).trim().length > 1000)
        }
        // A component's page is found by its name.
        assertEquals(components.size, components.map { it.name }.toSet().size)
    }

    /** Each text is stored once and for a reason: no text without a component, no license without a text. */
    @Test
    fun theStoredTextsAreExactlyTheLicensesInUse() {
        val stored = context.assets.list("licenses").orEmpty().filter { it.endsWith(".txt") }.toSet()

        assertEquals(components.map { "${it.license.id}.txt" }.toSet(), stored)
    }

    /** What the page shows of a license is the license: the same words in the same order. */
    @Test
    fun aLicenseShownAsParagraphsKeepsEveryWord() {
        fun words(text: String) = text.split(Regex("\\s+")).filter { it.isNotEmpty() }

        components.map { it.license }.distinct().forEach { license ->
            val text = context.licenseText(license)
            assertEquals(license.id, words(text), words(licenseParagraphs(text).joinToString("\n")))
        }
    }

    @Test
    fun aLineBrokenBecauseItWasFullIsJoinedAndEveryOtherBreakIsKept() {
        val text = """
                                 A License
                           Version 2.0, January 2004

            Redistribution and use in source and binary forms, with or without
            modification, are permitted provided that the following conditions are met:

            * Redistributions of source code must retain the above copyright notice,
              this list of conditions and the following disclaimer.

            ---------------------------------------------------------------------------
            PREAMBLE
        """.trimIndent()

        assertEquals(
            listOf(
                "A License",
                "Version 2.0, January 2004",
                "Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:",
                "* Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.",
                "---------------------------------------------------------------------------",
                "PREAMBLE",
            ),
            licenseParagraphs(text),
        )
    }
}
