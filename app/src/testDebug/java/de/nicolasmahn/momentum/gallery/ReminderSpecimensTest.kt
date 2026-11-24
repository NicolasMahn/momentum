package de.nicolasmahn.momentum.gallery

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import de.nicolasmahn.momentum.app.AppDestination
import de.nicolasmahn.momentum.core.ui.theme.WorkoutTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** The gallery's reminders specimen posts the three real notifications, each with its action. */
@RunWith(RobolectricTestRunner::class)
class ReminderSpecimensTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before fun allowNotifications() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test
    fun eachButtonPostsItsReminderWithItsAction() {
        val specimen = viewSpecimens(AppDestination.Settings).single { it.name == "Reminder notifications, posted with their actions" }
        compose.setContent { WorkoutTrackerTheme { specimen.content() } }
        val shade = context.getSystemService(NotificationManager::class.java)

        val actions = (0..2).map { index ->
            val before = shade.activeNotifications.map { it.tag }.toSet()
            compose.onNodeWithTag("gallery_reminder_$index").performClick()
            compose.waitForIdle()
            shade.activeNotifications.single { it.tag !in before }.notification.actions.map { it.title.toString() }
        }

        assertEquals(listOf(listOf("Log it"), listOf("Start the timer"), listOf("Mark all done")), actions)
        compose.onNodeWithTag("gallery_reminder_refused").assertDoesNotExist()
    }
}
