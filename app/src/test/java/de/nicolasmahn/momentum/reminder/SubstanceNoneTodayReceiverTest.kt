package de.nicolasmahn.momentum.reminder

import de.nicolasmahn.momentum.domain.measurement.withWeight
import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.app.AppDestination
import de.nicolasmahn.momentum.app.AppNavigationRequest
import de.nicolasmahn.momentum.app.requestedAppNavigation
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.substance.SubstanceCheckIn
import de.nicolasmahn.momentum.domain.substance.SubstanceKind
import de.nicolasmahn.momentum.domain.substance.SubstanceLog
import de.nicolasmahn.momentum.domain.substance.SubstanceNoneDay
import de.nicolasmahn.momentum.domain.substance.SubstanceSettings
import de.nicolasmahn.momentum.persistence.PreferencesTrackerRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import de.nicolasmahn.momentum.domain.questions.withEating
import de.nicolasmahn.momentum.domain.time.withReading
import de.nicolasmahn.momentum.domain.activity.withActivity

/**
 * "None today" from the notification records none for the day it asked about and never over a
 * use; meeting one, it leaves the records alone and says so on the notification, whose tap opens
 * the day. It starts no screen itself: a receiver may not, and the tap is the way that works.
 */
@RunWith(RobolectricTestRunner::class)
class SubstanceNoneTodayReceiverTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dateKey = "2026-10-06"
    private val remind = SubstanceSettings.Legacy.copy(checkIn = SubstanceCheckIn(remind = true))

    @Before fun allowNotifications() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test fun noneTodayRecordsNoneForAnUnloggedDayAndTakesTheCheckInDown() {
        PreferencesTrackerRepository(context).save(AppState(substances = remind).withWeight().withEating().withReading().withActivity())
        assertTrue(SubstanceCheckInNotification(context).show(dateKey))

        SubstanceNoneTodayReceiver().onReceive(context, noneToday(dateKey))

        assertEquals(listOf(dateKey), PreferencesTrackerRepository(context).load().substanceNoneDays.map(SubstanceNoneDay::dateKey))
        assertNull(notifications().getNotification(SubstanceCheckInNotification.NotificationId))
        assertNull(shadowOf(context as Application).nextStartedActivity)
    }

    /** The check-in is titled with the view's stored name, as typed, and with the app's own word while it has none. */
    @Test fun theCheckInIsTitledWithTheViewsStoredName() {
        PreferencesTrackerRepository(context).save(AppState(substances = remind).withWeight().withEating().withReading().withActivity())
        assertTrue(SubstanceCheckInNotification(context).show(dateKey))
        assertEquals(context.getString(R.string.feature_substances), requireNotNull(notifications().getNotification(SubstanceCheckInNotification.NotificationId)).title())

        PreferencesTrackerRepository(context).save(AppState(substances = remind.copy(label = "Evenings")).withWeight().withEating().withReading().withActivity())
        assertTrue(SubstanceCheckInNotification(context).show(dateKey))
        assertEquals("Evenings", requireNotNull(notifications().getNotification(SubstanceCheckInNotification.NotificationId)).title())
    }

    @Test fun noneTodayOverAUseLeavesTheRecordsAloneAndSaysSoWhereATapOpensTheDay() {
        val use = SubstanceLog("c1", SubstanceKind.CANNABIS.id, dateKey, 1L, 0.5, SubstanceKind.CANNABIS.unit)
        PreferencesTrackerRepository(context).save(AppState(substances = remind, substanceLogs = listOf(use)).withWeight().withEating().withReading().withActivity())
        assertTrue(SubstanceCheckInNotification(context).show(dateKey))

        SubstanceNoneTodayReceiver().onReceive(context, noneToday(dateKey))

        val stored = PreferencesTrackerRepository(context).load()
        assertEquals(listOf(use), stored.substanceLogs)
        assertEquals(emptyList<SubstanceNoneDay>(), stored.substanceNoneDays)
        val posted = requireNotNull(notifications().getNotification(SubstanceCheckInNotification.NotificationId))
        assertEquals(context.getString(R.string.substance_check_in_holds_record), posted.text())
        assertEquals(0, posted.actions.orEmpty().size)
        assertEquals(
            AppNavigationRequest(AppDestination.Substances, substanceDateKey = dateKey),
            shadowOf(posted.contentIntent).savedIntent.requestedAppNavigation(),
        )
        assertNull(shadowOf(context as Application).nextStartedActivity)
    }

    private fun noneToday(dateKey: String) = Intent(context, SubstanceNoneTodayReceiver::class.java).apply {
        action = SubstanceCheckInNotification.ActionNoneToday
        putExtra(ExtraReminderTargetDateKey, dateKey)
    }

    private fun notifications() = shadowOf(context.getSystemService(NotificationManager::class.java))

    private fun Notification.text() = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

    private fun Notification.title() = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
}
