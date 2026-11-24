package de.nicolasmahn.momentum.core.locale

import androidx.core.os.LocaleListCompat
import java.io.File
import java.util.Properties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppLanguageTest {
    @Test
    fun noAppSpecificLanguageMeansFollowingThePhone() {
        assertEquals(AppLanguage.System, appLanguageOf(LocaleListCompat.getEmptyLocaleList()))
    }

    @Test
    fun aRegionalVariantPickedInThePhoneSettingsStillMatchesItsLanguage() {
        assertEquals(AppLanguage.German, appLanguageOf(LocaleListCompat.forLanguageTags("de-AT")))
        assertEquals(AppLanguage.English, appLanguageOf(LocaleListCompat.forLanguageTags("en-GB")))
    }

    @Test
    fun aLanguageMomentumDoesNotOfferReadsAsFollowingThePhone() {
        assertEquals(AppLanguage.System, appLanguageOf(LocaleListCompat.forLanguageTags("fr-FR")))
    }

    @Test
    fun eachChoiceRoundTripsThroughTheTagAndroidStores() {
        AppLanguage.entries.forEach { language ->
            val stored = language.tag?.let(LocaleListCompat::forLanguageTags) ?: LocaleListCompat.getEmptyLocaleList()
            assertEquals(language, appLanguageOf(stored))
        }
    }

    @Test
    fun eachLanguageNamesItselfAndFollowingThePhoneIsLeftToTheScreen() {
        assertEquals("English", AppLanguage.English.ownName)
        assertEquals("Deutsch", AppLanguage.German.ownName)
        assertNull(AppLanguage.System.ownName)
    }

    /**
     * Android lists the app's languages from its translated resources, so a translation without an
     * entry would be offered by the phone and shown here as "System language", and an entry without
     * a translation would offer a language the app cannot speak.
     */
    @Test
    fun settingsOffersExactlyTheLanguagesTheAppIsTranslatedInto() {
        val res = requireNotNull(
            generateSequence(File("").absoluteFile) { it.parentFile }
                .map { File(it, "src/main/res") }
                .firstOrNull(File::isDirectory),
        ) { "Run this test from the app module so it reads the real resources." }
        val default = File(res, "resources.properties").reader().use { reader ->
            Properties().apply { load(reader) }.getProperty("unqualifiedResLocale")
        }
        val translated = res.listFiles().orEmpty().mapNotNull { dir ->
            TranslatedValues.matchEntire(dir.name)?.destructured?.let { (language, region) ->
                if (region.isEmpty()) language else "$language-$region"
            }
        }

        assertEquals(
            (translated + default).toSet(),
            AppLanguage.entries.mapNotNull { it.tag }.toSet(),
        )
    }

    private companion object {
        /** A values folder qualified by language and, optionally, region, such as `values-pt-rBR`. */
        val TranslatedValues = Regex("""values-([a-z]{2,3})(?:-r([A-Z]{2}))?""")
    }
}
