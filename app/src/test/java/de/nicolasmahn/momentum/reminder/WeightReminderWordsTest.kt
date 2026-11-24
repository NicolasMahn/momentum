package de.nicolasmahn.momentum.reminder

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.core.text.resolve
import de.nicolasmahn.momentum.domain.mo.MoDayMark
import de.nicolasmahn.momentum.domain.mo.MoMood
import de.nicolasmahn.momentum.domain.mo.MoState
import de.nicolasmahn.momentum.domain.mo.MoStreak
import de.nicolasmahn.momentum.domain.measurement.MeasurementConfiguration
import de.nicolasmahn.momentum.domain.compatibility.WeightMeasurementId
import de.nicolasmahn.momentum.domain.measurement.testWeightMeasurement
import de.nicolasmahn.momentum.persistence.PreferencesTrackerRepository
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * What Weight's reminder asks for in its reply, what its reply is called and what it says of a
 * reply it cannot read follow the unit Weight is stored with when it is posted. In kilograms they
 * are the sentences they always were, with "Add weight"; with any other unit they are those of a
 * measurement, "Log it" among them, and say kilograms nowhere. The notification is posted as the
 * shared coordinator posts it.
 */
@RunWith(RobolectricTestRunner::class)
class WeightReminderWordsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val days = ReminderDays(LocalDate.parse("2026-10-14"), LocalDate.parse("2026-10-14"))
    private val key = slotKey(ReminderViewKind.Measurement, WeightMeasurementId, "day")

    @Before fun allowNotifications() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun show(weight: MeasurementConfiguration) = assertTrue(ChecklistReminderNotification(context).show(weight, key, days, quietly = false))

    private fun unsaved(weight: MeasurementConfiguration, unreadable: Boolean) = assertTrue(ChecklistReminderNotification(context).unsaved(weight, key, days, unreadable))

    private fun posted(): Notification = requireNotNull(shadowOf(context.getSystemService(NotificationManager::class.java)).getNotification(key, ChecklistReminderRequestCode))

    /** What the reply field of the notification on show asks for. */
    private fun prompt(): String = posted().actions.single().remoteInputs.single().label.toString()

    private fun reply(): String = posted().actions.single().title.toString()

    private fun text(): String = posted().extras.getCharSequence(Notification.EXTRA_TEXT).toString()

    private fun title(): String = posted().extras.getCharSequence(Notification.EXTRA_TITLE).toString()

    private fun assertNoKilograms(vararg said: String) = said.forEach { sentence ->
        listOf("kg", "kilogram").forEach { word -> assertFalse("\"$sentence\" says \"$word\"", sentence.lowercase().contains(word)) }
    }

    @Test fun inKilogramsTheReplyAsksAndRefusesAsItAlwaysDid() {
        val weight = testWeightMeasurement()
        show(weight)
        assertEquals("Weight in ⁨kg⁩", prompt())
        assertEquals(context.getString(R.string.reminder_action_log), reply())

        unsaved(weight, unreadable = true)
        assertEquals("Enter a number above zero.", text())
        assertEquals("Weight in ⁨kg⁩", prompt())
        unsaved(weight, unreadable = false)
        assertEquals(context.getString(R.string.measurement_error_save_failed), text())
    }

    @Test
    @Config(qualifiers = "de")
    fun inKilogramsTheReplyAsksAndRefusesAsItAlwaysDidInGerman() {
        val weight = testWeightMeasurement()
        show(weight)
        assertEquals("Weight in ⁨kg⁩", prompt())
        assertEquals("Eintragen", reply())
        unsaved(weight, unreadable = true)
        assertEquals("Gib eine Zahl über null ein.", text())
    }

    @Test fun inPoundsTheReplyAsksForItsUnitAndNothingSaysKilograms() {
        val weight = testWeightMeasurement().copy(unit = "lb")
        show(weight)
        assertEquals("Weight in ⁨lb⁩", prompt())
        assertEquals(context.getString(R.string.reminder_action_log), reply())
        assertNoKilograms(prompt(), text(), title(), reply())

        unsaved(weight, unreadable = true)
        assertEquals("Enter a number above zero.", text())
        assertEquals("Weight in ⁨lb⁩", prompt())
        assertNoKilograms(prompt(), text(), title())

        // What a failed save says names no unit, in any unit.
        unsaved(weight, unreadable = false)
        assertEquals(context.getString(R.string.measurement_error_save_failed), text())
        assertNoKilograms(prompt(), text())
    }

    @Test
    @Config(qualifiers = "de")
    fun inPoundsTheReplyAsksForItsUnitAndNothingSaysKilogramsInGerman() {
        val weight = testWeightMeasurement().copy(label = "Gewicht", unit = "lb")
        show(weight)
        assertEquals("Gewicht in ⁨lb⁩", prompt())
        assertEquals("Eintragen", reply())
        unsaved(weight, unreadable = true)
        assertEquals("Gib eine Zahl über null ein.", text())
        assertNoKilograms(prompt(), text())
    }

    /** Without a unit the reply asks for Weight by its name alone. */
    @Test fun withoutAUnitTheReplyAsksForWeightByItsName() {
        show(testWeightMeasurement().copy(label = "Morning weight", unit = ""))
        assertEquals("Morning weight", prompt())
    }

    /** A notification said again after another unit is saved asks in the new one. */
    @Test fun aReminderSaidAgainAsksInTheNewUnit() {
        show(testWeightMeasurement())
        assertEquals("Weight in ⁨kg⁩", prompt())
        assertTrue(ChecklistReminderNotification(context).show(testWeightMeasurement().copy(unit = "lb"), key, days, quietly = true))
        assertEquals("Weight in ⁨lb⁩", prompt())
    }

    /**
     * A store damaged as a preference of another type is, whose state's key holds a number so
     * access is refused, ends a reply in a failed save and leaves the damaged value untouched.
     */
    @Test fun aDamagedStoreEndsAReplyInAFailedSave() {
        context.getSharedPreferences(PreferencesTrackerRepository.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit().putInt(PreferencesTrackerRepository.STATE_V32_KEY, 7).commit()
        assertNull(PreferencesTrackerRepository(context).loadIfReadable())
        assertEquals(MeasurementReplyResult.SaveFailed, MeasurementReplyCoordinator(WeightMeasurementId, PreferencesTrackerRepository(context)).submit("82", days.recordDay.toString()))
        assertEquals(7, context.getSharedPreferences(PreferencesTrackerRepository.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getInt(PreferencesTrackerRepository.STATE_V32_KEY, 0))
    }

}
