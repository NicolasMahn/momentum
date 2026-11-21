package com.example.workouttracker.feature.checklist

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.example.workouttracker.R
import com.example.workouttracker.core.ui.theme.WorkoutTrackerTheme
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.checklist.ChecklistConfiguration
import com.example.workouttracker.domain.checklist.ChecklistItem
import com.example.workouttracker.domain.checklist.Checkpoint
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.holiday.HolidaySchedule
import java.time.LocalTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Some of a checklist's items ticked, the button names what it does: it saves them. */
@RunWith(RobolectricTestRunner::class)
class ChecklistSaveLabelTest {
    @get:Rule
    val compose = createComposeRule()

    private val supplements = ChecklistConfiguration(
        id = "chk_supplements",
        label = "Supplements",
        icon = ViewIcon.Journal,
        hue = ViewHue.Plum,
        checkpoints = listOf(Checkpoint("cpt_morning", CheckpointTime.Fixed(LocalTime.of(8, 0)), remind = true)),
        items = listOf(ChecklistItem("itm_multi", "Multivitamin"), ChecklistItem("itm_creatine", "Creatine"), ChecklistItem("itm_d", "Vitamin D")),
    )
    private val tag = "checklist_chk_supplements"

    @Test
    fun twoOfThreeTickedOffersToSave() {
        compose.setContent {
            WorkoutTrackerTheme {
                ChecklistScreen(
                    state = AppState(checklists = listOf(supplements)),
                    checklist = supplements,
                    onStateChange = { true },
                    todayKey = "2026-10-13",
                    daysOff = HolidaySchedule(),
                )
            }
        }
        listOf("itm_multi", "itm_d").forEach { item ->
            compose.onNodeWithTag("${tag}_status_$item").performSemanticsAction(SemanticsActions.OnClick)
        }
        compose.waitForIdle()

        val save = ApplicationProvider.getApplicationContext<Context>().getString(R.string.checklist_save)
        compose.onNodeWithTag("${tag}_save").assertTextEquals(save)
    }
}
