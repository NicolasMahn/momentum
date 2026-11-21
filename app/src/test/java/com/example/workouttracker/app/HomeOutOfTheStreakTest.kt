package com.example.workouttracker.app

import android.content.Context
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.workouttracker.R
import com.example.workouttracker.core.time.currentHabitDate
import com.example.workouttracker.core.ui.theme.WorkoutTrackerTheme
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.ActivityTrackerRef
import com.example.workouttracker.domain.mo.habitStreak
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import com.example.workouttracker.domain.activity.ActivityEdit
import com.example.workouttracker.domain.activity.applyingActivityEdits
import com.example.workouttracker.domain.activity.withActivity
import com.example.workouttracker.domain.measurement.withWeight
import com.example.workouttracker.domain.compatibility.MoNeed
import com.example.workouttracker.domain.mo.keeping
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
