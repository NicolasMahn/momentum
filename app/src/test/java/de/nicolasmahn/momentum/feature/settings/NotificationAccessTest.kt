package de.nicolasmahn.momentum.feature.settings

import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationAccessTest {
    @Test
    fun grantedNotificationsAskForNothing() {
        assertEquals(
            NotificationAccessAction.None,
            notificationAccessAction(sdkInt = 34, notificationsAllowed = true),
        )
        assertEquals(
            NotificationAccessAction.None,
            notificationAccessAction(sdkInt = 30, notificationsAllowed = true),
        )
    }

    @Test
    fun theFirstAskOnAndroid13GoesThroughTheRuntimeDialog() {
        assertEquals(
            NotificationAccessAction.RequestPermission,
            notificationAccessAction(
                sdkInt = Build.VERSION_CODES.TIRAMISU,
                notificationsAllowed = false,
            ),
        )
    }

    @Test
    fun beforeAndroid13OnlyTheSystemScreenCanSwitchNotificationsBackOn() {
        assertEquals(
            NotificationAccessAction.OpenSystemSettings,
            notificationAccessAction(
                sdkInt = Build.VERSION_CODES.S_V2,
                notificationsAllowed = false,
            ),
        )
    }

    @Test
    fun aDeclinedDialogLeavesTheRowAloneSoTheButtonDoesNotNag() {
        assertEquals(
            NotificationAccessAction.None,
            notificationAccessFollowUp(granted = false, canAskAgain = true),
        )
    }

    /** The case the owner hit: pressing Allow appeared to do nothing at all. */
    @Test
    fun aRequestTheSystemWillNotShowFallsThroughToSystemSettings() {
        assertEquals(
            NotificationAccessAction.OpenSystemSettings,
            notificationAccessFollowUp(granted = false, canAskAgain = false),
        )
    }

    @Test
    fun aGrantEndsTheFlowWithoutOpeningAnything() {
        assertEquals(
            NotificationAccessAction.None,
            notificationAccessFollowUp(granted = true, canAskAgain = false),
        )
    }
}
