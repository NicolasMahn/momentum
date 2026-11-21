package com.example.workouttracker.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.workouttracker.persistence.backup.BackupError
import com.example.workouttracker.persistence.backup.BackupStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BackupWarningTest {
    @get:Rule val composeRule = createComposeRule()
    private val verified = BackupStatus(true, "2026-09-17T12:00:00Z", false, null)

    @Test fun ordinaryPendingBackupDoesNotMoveScreenThenDelayedWarningClearsWhenVerified() {
        composeRule.mainClock.autoAdvance = false
        val status = mutableStateOf(verified)
        composeRule.setContent {
            MaterialTheme {
                Column {
                    BackupWarning(status.value) {}
                    Box(Modifier.size(32.dp).testTag("screen_content"))
                }
            }
        }
        val originalBounds = composeRule.onNodeWithTag("screen_content").fetchSemanticsNode().boundsInRoot
        composeRule.runOnIdle {
            // A future wall-clock origin gives the coroutine the full, deterministic 30-second delay.
            status.value = verified.copy(pending = true, pendingSinceMillis = System.currentTimeMillis() + 60_000)
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag("backup_warning").assertDoesNotExist()
        assertEquals(originalBounds, composeRule.onNodeWithTag("screen_content").fetchSemanticsNode().boundsInRoot)
        composeRule.mainClock.advanceTimeBy(29_000)
        composeRule.onNodeWithTag("backup_warning").assertDoesNotExist()
        composeRule.mainClock.advanceTimeBy(1_100)
        composeRule.onNodeWithText("Backup is taking longer than usual. Open Settings to check.").assertIsDisplayed()
        composeRule.runOnIdle { status.value = verified }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag("backup_warning").assertDoesNotExist()
        assertEquals(originalBounds, composeRule.onNodeWithTag("screen_content").fetchSemanticsNode().boundsInRoot)
    }

    @Test fun failureAppearsImmediatelyAndOpensSettings() {
        composeRule.mainClock.autoAdvance = false
        var openedSettings = false
        composeRule.setContent {
            MaterialTheme {
                BackupWarning(verified.copy(pending = true, error = BackupError.WriteFailed, pendingSinceMillis = System.currentTimeMillis())) {
                    openedSettings = true
                }
            }
        }
        composeRule.onNodeWithText("Backup failed. Open Settings to retry.").assertIsDisplayed()
        composeRule.onNodeWithTag("backup_warning").performClick()
        composeRule.runOnIdle { assertTrue(openedSettings) }
    }

    @Test fun alreadyDelayedPendingCopyAppearsImmediatelyDespiteHistoricalVerification() {
        composeRule.setContent {
            MaterialTheme {
                BackupWarning(verified.copy(pending = true, pendingSinceMillis = System.currentTimeMillis() - 31_000)) {}
            }
        }
        composeRule.onNodeWithText("Backup is taking longer than usual. Open Settings to check.").assertIsDisplayed()
    }

    @Test fun setupPromptOpensSettingsWithoutWaiting() {
        var openedSettings = false
        composeRule.setContent {
            MaterialTheme {
                BackupWarning(BackupStatus(false, null, true, null)) { openedSettings = true }
            }
        }
        composeRule.onNodeWithText("Set up backups to protect records against uninstall.").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertTrue(openedSettings) }
    }
}
