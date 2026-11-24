package de.nicolasmahn.momentum.app

import android.content.Context
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.core.time.currentHabitDate
import de.nicolasmahn.momentum.core.ui.theme.WorkoutTrackerTheme
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.ActivityTrackerRef
import de.nicolasmahn.momentum.domain.mo.habitStreak
import de.nicolasmahn.momentum.domain.settings.FeatureVisibilitySettings
import de.nicolasmahn.momentum.domain.activity.ActivityEdit
import de.nicolasmahn.momentum.domain.activity.applyingActivityEdits
import de.nicolasmahn.momentum.domain.activity.withActivity
import de.nicolasmahn.momentum.domain.measurement.withWeight
import de.nicolasmahn.momentum.domain.compatibility.MoNeed
import de.nicolasmahn.momentum.domain.mo.keeping
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Recorded completion stays visible when reward participation ends. */
@RunWith(RobolectricTestRunner::class)
class HomeOutOfTheStreakTest {
    @get:Rule
    val compose = createComposeRule()

    private val done = ApplicationProvider.getApplicationContext<Context>().getString(R.string.app_home_task_done)

    @Test
    fun recordedCompletionStaysVisibleAfterLeavingRewards() {
        val today = currentHabitDate()
        val kept = AppState().withActivity().withWeight()
            .keeping(MoNeed.Activity, today.minusDays(1)).keeping(MoNeed.Activity, today)
            .keeping(MoNeed.Weight, today.minusDays(1)).keeping(MoNeed.Weight, today)
        val left = kept.applyingActivityEdits(listOf(ActivityEdit.LeaveRewards), today)
        compose.setContent { WorkoutTrackerTheme { MomentumApp(state = left, onStateChange = { true }, onActivitySave = ActivitySaveNotExpected) } }

        compose.onNode(hasContentDescription(done) and hasAnyAncestor(hasTestTag("home_measurement_weight")), useUnmergedTree = true).assertExists()
        compose.onNode(hasContentDescription(done) and hasAnyAncestor(hasTestTag("home_log_activity")), useUnmergedTree = true).assertExists()
        assertFalse(ActivityTrackerRef in habitStreak(left, FeatureVisibilitySettings(), today).standings)
        assertEquals(kept.activityEntries, left.activityEntries)
        assertEquals(kept.measurementLogs, left.measurementLogs)
    }
}
