package com.example.workouttracker.feature.weight

import com.example.workouttracker.domain.measurement.MeasurementValuePolicy

import com.example.workouttracker.feature.measurement.MeasurementScreenError
import com.example.workouttracker.feature.measurement.MeasurementWords
import com.example.workouttracker.feature.measurement.words
import android.content.Context
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import com.example.workouttracker.core.format.DateStyle
import com.example.workouttracker.core.format.relativeDateText
import com.example.workouttracker.core.text.UiText
import com.example.workouttracker.core.text.resolve
import com.example.workouttracker.domain.measurement.testMadeMeasurement
import com.example.workouttracker.domain.measurement.testWeightMeasurement
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The sentences of a measurement a person made, each as it reads in English and German: with a
 * unit, where a value is its number and its unit, and without one, where it is the number alone and
 * no sentence is left with a stray space or a dangling "in". The name and the unit appear exactly
 * as typed, and no word of Weight's is among them.
 *
 * On screen a unit is set apart by the two invisible marks written here as ⁨ and ⁩; what TalkBack
 * hears has neither.
 */
@RunWith(RobolectricTestRunner::class)
class MeasurementWordsTest {
    private val waist = MeasurementWords(name = "Waist", unit = "cm")
    private val mood = MeasurementWords(name = "Mood", unit = "")
    private val saturday = UiText.Date(LocalDate.of(2026, 10, 3), DateStyle.WeekdayDayMonth)
    private val yesterday = relativeDateText("2026-10-02", "2026-10-03")

    private fun resources(): Resources = ApplicationProvider.getApplicationContext<Context>().resources
    private fun UiText.read() = resolve(resources()).replace('⁨', '⁨').replace('⁩', '⁩')

    /** Every sentence [words] can say, in the order the screen says them. */
    private fun everySentence(words: MeasurementWords): List<String> = listOf(
        words.fieldLabel,
        words.save,
        words.saved,
        words.remove(saturday),
        words.previous(91.5, yesterday),
        words.previousWithChange(91.5, yesterday, 0.4),
        words.previousWithChange(91.5, yesterday, -1.25),
        words.previousWithChange(91.5, yesterday, 0.0),
        words.error(MeasurementScreenError.Invalid),
        words.error(MeasurementScreenError.ChangedBeforeSave),
        words.error(MeasurementScreenError.SaveFailed),
        words.error(MeasurementScreenError.ChangedBeforeRemoval),
        words.error(MeasurementScreenError.RemovalFailed),
        words.error(MeasurementScreenError.Deleted),
        words.drawn("91.5"),
        words.spoken(91.5),
        words.noneLogged,
        words.trend(91.25),
        words.chartEmpty(28),
        words.chartEmpty(1),
        words.chartLine(28, 91.5, smoothed = true),
        words.chartLine(1, 91.5, smoothed = true),
        words.chartLine(28, 91.5, smoothed = false),
        words.chartLine(1, 91.5, smoothed = false),
        words.chartLineBetween(28, 91.5, 90.0, 93.25, smoothed = true),
        words.chartLineBetween(1, 91.5, 90.0, 93.25, smoothed = true),
        words.chartLineBetween(28, 91.5, 90.0, 93.25, smoothed = false),
        words.chartLineBetween(1, 91.5, 90.0, 93.25, smoothed = false),
        words.selectedNone(saturday),
        words.selected(saturday, 91.5),
        words.selectedWithTrend(saturday, 91.5, 91.25),
    ).map { it.read() }

    @Test
    fun aMeasurementWithAUnitInEnglish() = assertEquals(
        listOf(
            "Waist in ⁨cm⁩",
            "Save",
            "Saved",
            "Remove the entry for Sat, Oct 3",
            "Previous: 91.5 ⁨cm⁩ (yesterday)",
            "Previous: 91.5 ⁨cm⁩ (yesterday) · +0.4 ⁨cm⁩",
            "Previous: 91.5 ⁨cm⁩ (yesterday) · -1.25 ⁨cm⁩",
            "Previous: 91.5 ⁨cm⁩ (yesterday) · ±0 ⁨cm⁩",
            "Enter a number.",
            "This entry changed elsewhere. Reopen Waist before saving.",
            "Couldn't save the entry. Try again.",
            "This entry changed elsewhere. Review it before deleting.",
            "Couldn't delete the entry. Try again.",
            "Waist was deleted. Nothing was changed.",
            "91.5",
            "91.5 cm",
            "Nothing logged",
            "Trend 91.25 ⁨cm⁩",
            "Chart of Waist with no known value in the last 28 days",
            "Chart of Waist with no known value in the last 1 day",
            "Trend of Waist for the last 28 days, ending at 91.5 cm",
            "Trend of Waist for the last 1 day, ending at 91.5 cm",
            "Waist for the last 28 days, ending at 91.5 cm",
            "Waist for the last 1 day, ending at 91.5 cm",
            "Trend of Waist for the last 28 days, ending at 91.5 cm, records between 90 and 93.25 cm",
            "Trend of Waist for the last 1 day, ending at 91.5 cm, records between 90 and 93.25 cm",
            "Waist for the last 28 days, ending at 91.5 cm, records between 90 and 93.25 cm",
            "Waist for the last 1 day, ending at 91.5 cm, records between 90 and 93.25 cm",
            "Selected Sat, Oct 3: nothing logged",
            "Selected Sat, Oct 3: 91.5 cm",
            "Selected Sat, Oct 3: 91.5 cm, trend 91.25 cm",
        ),
        everySentence(waist),
    )

    @Test
    @Config(qualifiers = "de")
    fun aMeasurementWithAUnitInGerman() = assertEquals(
        listOf(
            "Waist in ⁨cm⁩",
            "Speichern",
            "Gespeichert",
            "Eintrag für Sa., 3. Okt. entfernen",
            "Zuvor: 91,5 ⁨cm⁩ (gestern)",
            "Zuvor: 91,5 ⁨cm⁩ (gestern) · +0,4 ⁨cm⁩",
            "Zuvor: 91,5 ⁨cm⁩ (gestern) · -1,25 ⁨cm⁩",
            "Zuvor: 91,5 ⁨cm⁩ (gestern) · ±0 ⁨cm⁩",
            "Gib eine Zahl ein.",
            "Dieser Eintrag wurde woanders geändert. Öffne Waist neu, bevor du speicherst.",
            "Der Eintrag konnte nicht gespeichert werden. Versuch es noch einmal.",
            "Dieser Eintrag wurde woanders geändert. Sieh ihn dir an, bevor du ihn löschst.",
            "Der Eintrag konnte nicht gelöscht werden. Versuch es noch einmal.",
            "Waist wurde gelöscht. Es wurde nichts geändert.",
            "91.5",
            "91,5 cm",
            "Nichts erfasst",
            "Trend 91,25 ⁨cm⁩",
            "Diagramm für Waist ohne bekannten Wert in den letzten 28 Tagen",
            "Diagramm für Waist ohne bekannten Wert am letzten Tag",
            "Trend für Waist in den letzten 28 Tagen, zuletzt 91,5 cm",
            "Trend für Waist am letzten Tag, zuletzt 91,5 cm",
            "Waist in den letzten 28 Tagen, zuletzt 91,5 cm",
            "Waist am letzten Tag, zuletzt 91,5 cm",
            "Trend für Waist in den letzten 28 Tagen, zuletzt 91,5 cm, Einträge zwischen 90 und 93,25 cm",
            "Trend für Waist am letzten Tag, zuletzt 91,5 cm, Einträge zwischen 90 und 93,25 cm",
            "Waist in den letzten 28 Tagen, zuletzt 91,5 cm, Einträge zwischen 90 und 93,25 cm",
            "Waist am letzten Tag, zuletzt 91,5 cm, Einträge zwischen 90 und 93,25 cm",
            "Ausgewählt Sa., 3. Okt.: nichts erfasst",
            "Ausgewählt Sa., 3. Okt.: 91,5 cm",
            "Ausgewählt Sa., 3. Okt.: 91,5 cm, Trend 91,25 cm",
        ),
        everySentence(waist),
    )

    @Test
    fun aMeasurementWithoutAUnitInEnglish() = assertEquals(
        listOf(
            "Mood",
            "Save",
            "Saved",
            "Remove the entry for Sat, Oct 3",
            "Previous: 91.5 (yesterday)",
            "Previous: 91.5 (yesterday) · +0.4",
            "Previous: 91.5 (yesterday) · -1.25",
            "Previous: 91.5 (yesterday) · ±0",
            "Enter a number.",
            "This entry changed elsewhere. Reopen Mood before saving.",
            "Couldn't save the entry. Try again.",
            "This entry changed elsewhere. Review it before deleting.",
            "Couldn't delete the entry. Try again.",
            "Mood was deleted. Nothing was changed.",
            "91.5",
            "91.5",
            "Nothing logged",
            "Trend 91.25",
            "Chart of Mood with no known value in the last 28 days",
            "Chart of Mood with no known value in the last 1 day",
            "Trend of Mood for the last 28 days, ending at 91.5",
            "Trend of Mood for the last 1 day, ending at 91.5",
            "Mood for the last 28 days, ending at 91.5",
            "Mood for the last 1 day, ending at 91.5",
            "Trend of Mood for the last 28 days, ending at 91.5, records between 90 and 93.25",
            "Trend of Mood for the last 1 day, ending at 91.5, records between 90 and 93.25",
            "Mood for the last 28 days, ending at 91.5, records between 90 and 93.25",
            "Mood for the last 1 day, ending at 91.5, records between 90 and 93.25",
            "Selected Sat, Oct 3: nothing logged",
            "Selected Sat, Oct 3: 91.5",
            "Selected Sat, Oct 3: 91.5, trend 91.25",
        ),
        everySentence(mood),
    )

    @Test
    @Config(qualifiers = "de")
    fun aMeasurementWithoutAUnitInGerman() = assertEquals(
        listOf(
            "Mood",
            "Speichern",
            "Gespeichert",
            "Eintrag für Sa., 3. Okt. entfernen",
            "Zuvor: 91,5 (gestern)",
            "Zuvor: 91,5 (gestern) · +0,4",
            "Zuvor: 91,5 (gestern) · -1,25",
            "Zuvor: 91,5 (gestern) · ±0",
            "Gib eine Zahl ein.",
            "Dieser Eintrag wurde woanders geändert. Öffne Mood neu, bevor du speicherst.",
            "Der Eintrag konnte nicht gespeichert werden. Versuch es noch einmal.",
            "Dieser Eintrag wurde woanders geändert. Sieh ihn dir an, bevor du ihn löschst.",
            "Der Eintrag konnte nicht gelöscht werden. Versuch es noch einmal.",
            "Mood wurde gelöscht. Es wurde nichts geändert.",
            "91.5",
            "91,5",
            "Nichts erfasst",
            "Trend 91,25",
            "Diagramm für Mood ohne bekannten Wert in den letzten 28 Tagen",
            "Diagramm für Mood ohne bekannten Wert am letzten Tag",
            "Trend für Mood in den letzten 28 Tagen, zuletzt 91,5",
            "Trend für Mood am letzten Tag, zuletzt 91,5",
            "Mood in den letzten 28 Tagen, zuletzt 91,5",
            "Mood am letzten Tag, zuletzt 91,5",
            "Trend für Mood in den letzten 28 Tagen, zuletzt 91,5, Einträge zwischen 90 und 93,25",
            "Trend für Mood am letzten Tag, zuletzt 91,5, Einträge zwischen 90 und 93,25",
            "Mood in den letzten 28 Tagen, zuletzt 91,5, Einträge zwischen 90 und 93,25",
            "Mood am letzten Tag, zuletzt 91,5, Einträge zwischen 90 und 93,25",
            "Ausgewählt Sa., 3. Okt.: nichts erfasst",
            "Ausgewählt Sa., 3. Okt.: 91,5",
            "Ausgewählt Sa., 3. Okt.: 91,5, Trend 91,25",
        ),
        everySentence(mood),
    )

    /**
     * What a person typed is shown as typed, whatever it says: a name and a unit that look like
     * placeholders, a unit written right to left, one with spaces inside it, and a name that is a
     * word of Weight's.
     */
    @Test
    fun theNameAndTheUnitAreShownExactlyAsTyped() {
        val odd = MeasurementWords(name = "100 % %s", unit = "%1\$d %")
        assertEquals("100 % %s in ⁨%1\$d %⁩", odd.fieldLabel.read())
        assertEquals("Previous: 5 ⁨%1\$d %⁩ (yesterday) · +1 ⁨%1\$d %⁩", odd.previousWithChange(5.0, yesterday, 1.0).read())
        assertEquals("This entry changed elsewhere. Reopen 100 % %s before saving.", odd.error(MeasurementScreenError.ChangedBeforeSave).read())
        assertEquals("Trend of 100 % %s for the last 28 days, ending at 5 %1\$d %, records between 4 and 6 %1\$d %", odd.chartLineBetween(28, 5.0, 4.0, 6.0, smoothed = true).read())

        val hebrew = MeasurementWords(name = "משקל", unit = "ק״ג")
        assertEquals("משקל in ⁨ק״ג⁩", hebrew.fieldLabel.read())
        assertEquals("Previous: 82 ⁨ק״ג⁩ (yesterday) · +0.4 ⁨ק״ג⁩", hebrew.previousWithChange(82.0, yesterday, 0.4).read())
        // Spoken, the unit is what was typed and nothing else.
        assertEquals("82 ק״ג", hebrew.spoken(82.0).resolve(resources()))
        assertFalse(hebrew.selectedWithTrend(saturday, 82.0, 81.5).resolve(resources()).any { it == '⁨' || it == '⁩' })

        val spaced = MeasurementWords(name = "Speed", unit = "km / h")
        assertEquals("Trend 12.5 ⁨km / h⁩", spaced.trend(12.5).read())
        assertEquals("km / h", spaced.unitBesideTheNumber)

        val weight = MeasurementWords(name = "Weight", unit = "kg", valuePolicy = MeasurementValuePolicy.Positive)
        assertEquals("Weight in ⁨kg⁩", weight.fieldLabel.read())
        assertEquals("Selected Sat, Oct 3: 82 kg", weight.selected(saturday, 82.0).read())
    }

    /** A made measurement's large number is the number alone, with its unit beside it as text of its own; without a unit there is nothing beside it. */
    @Test
    fun theUnitStandsBesideTheLargeNumberAsTextOfItsOwn() {
        assertEquals("91.5", waist.drawn("91.5").read())
        assertEquals("cm", waist.unitBesideTheNumber)
        assertNull(mood.unitBesideTheNumber)
        val weight = MeasurementWords(name = "Weight", unit = "kg", valuePolicy = MeasurementValuePolicy.Positive)
        assertEquals("91.5", weight.drawn("91.5").read())
        assertEquals("kg", weight.unitBesideTheNumber)
    }

    /** Apart from its name and unit, a measurement's generic wording says none of Weight's words. */
    @Test
    fun noEnglishSentenceOfAMadeMeasurementSaysAWordOfWeights() = assertNoWordOfWeights()

    @Test
    @Config(qualifiers = "de")
    fun noGermanSentenceOfAMadeMeasurementSaysAWordOfWeights() = assertNoWordOfWeights()

    private fun assertNoWordOfWeights() {
        val weightsWords = listOf("weight", "weigh", "kilogram", "kg", "scale", "gewicht", "wiegen", "gewogen", "waage")
        (everySentence(waist) + everySentence(mood)).forEach { sentence ->
            weightsWords.forEach { word -> assertFalse("\"$sentence\" says \"$word\"", sentence.lowercase().contains(word)) }
        }
    }

    /** Every measurement uses one wording model with the name and unit from its configuration. */
    @Test
    fun aMeasurementUsesItsConfiguredNameAndUnit() {
        assertEquals(MeasurementWords(name = "Weight", unit = "kg", valuePolicy = MeasurementValuePolicy.Positive), testWeightMeasurement().words())
        assertEquals(MeasurementWords(name = "Morning weight", unit = "kg", valuePolicy = MeasurementValuePolicy.Positive), testWeightMeasurement().copy(label = "Morning weight").words())
        assertEquals(MeasurementWords(name = "Waist", unit = "cm"), testMadeMeasurement().words())
        assertEquals(MeasurementWords(name = "Hips", unit = ""), testMadeMeasurement(label = "Hips", unit = "").words())
        assertEquals("Save", testWeightMeasurement().words().save.read())
        assertEquals("Weight in ⁨kg⁩", testWeightMeasurement().words().fieldLabel.read())
    }

    /** Any measurement reads a number alone or with its configured unit, in either decimal style. */
    @Test
    fun aMeasurementReadsItsNumberAndOptionalConfiguredUnit() {
        val weight = testWeightMeasurement().words()
        listOf("83.5" to 83.5, "83,5" to 83.5, " 83.5 " to 83.5, "7" to 7.0, "0.01" to 0.01, "87.456" to 87.456, "-3" to -3.0, "0" to 0.0).forEach { (typed, value) ->
            assertEquals(typed, value, waist.parse(typed)!!, 0.0)
            assertEquals(typed, value, mood.parse(typed)!!, 0.0)
            assertEquals(typed, value, weight.parse(typed)!!, 0.0)
        }
        listOf("83.5 cm", "83.5CM", "83,5 Cm ").forEach { typed ->
            assertEquals(typed, 83.5, waist.parse(typed)!!, 0.0)
            assertNull(typed, mood.parse(typed))
            assertNull(typed, weight.parse(typed))
        }
        listOf("83.5 KG", "83.5kg", "83,5 Kg", "83.5 kG ").forEach { typed ->
            assertEquals(typed, 83.5, weight.parse(typed)!!, 0.0)
            assertEquals(typed, 83.5, MeasurementWords(name = "Muscle", unit = "kg").parse(typed)!!, 0.0)
            assertNull(typed, waist.parse(typed))
            assertNull(typed, mood.parse(typed))
        }
        listOf("", " ", "abc", "83.5 lb", "NaN", "Infinity", "1/2", "8 3").forEach { typed ->
            assertNull(typed, waist.parse(typed))
            assertNull(typed, weight.parse(typed))
        }
        listOf("83.5 cm", "83.5cm", "83,5 CM").forEach { typed ->
            assertEquals(typed, 83.5, waist.parse(typed)!!, 0.0)
            assertNull(typed, weight.parse(typed))
            assertNull(typed, mood.parse(typed))
        }
    }
}
