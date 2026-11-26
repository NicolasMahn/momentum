package de.nicolasmahn.momentum.feature.mo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import de.nicolasmahn.momentum.TestSupplementsChecklist
import de.nicolasmahn.momentum.TestWeightMeasurement
import de.nicolasmahn.momentum.core.ui.theme.WorkoutTrackerTheme
import de.nicolasmahn.momentum.careState
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.testfixtures.CareChecklistId
import de.nicolasmahn.momentum.domain.checklist.ChecklistConfiguration
import de.nicolasmahn.momentum.domain.checklist.ChecklistItem
import de.nicolasmahn.momentum.domain.checklist.Checkpoint
import de.nicolasmahn.momentum.domain.checklist.CheckpointTime
import de.nicolasmahn.momentum.domain.checklist.ViewHue
import de.nicolasmahn.momentum.domain.checklist.ViewIcon
import de.nicolasmahn.momentum.domain.checklist.joinRewards
import de.nicolasmahn.momentum.domain.checklist.leaveRewards
import de.nicolasmahn.momentum.domain.measurement.DefaultMeasurementCheckpoint
import de.nicolasmahn.momentum.domain.measurement.MadeMeasurementWhenAway
import de.nicolasmahn.momentum.domain.measurement.MeasurementConfiguration
import de.nicolasmahn.momentum.domain.measurement.MeasurementEdit
import de.nicolasmahn.momentum.domain.measurement.MeasurementStep
import de.nicolasmahn.momentum.domain.measurement.applyingMeasurementEdits
import de.nicolasmahn.momentum.domain.mo.moMood
import de.nicolasmahn.momentum.domain.settings.FeatureVisibilitySettings
import de.nicolasmahn.momentum.domain.settings.ViewRef
import de.nicolasmahn.momentum.domain.loggedViews
import de.nicolasmahn.momentum.domain.ref
import de.nicolasmahn.momentum.domain.trackerRef
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import org.junit.Rule
import org.junit.Test

class MoStreakScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val today = LocalDate.parse("2026-10-14")
    private val medication = ChecklistConfiguration(
        id = "chk_med",
        label = "Medication",
        icon = ViewIcon.Medication,
        hue = ViewHue.Sky,
        checkpoints = listOf(Checkpoint("morning", CheckpointTime.Fixed(LocalTime.of(8, 0)), remind = false)),
        items = listOf(ChecklistItem("levo", "Levothyroxine")),
    )
    private val plants = medication.copy(id = "chk_plants", label = "Plants", icon = ViewIcon.Plant, hue = ViewHue.Forest)

    /** A checklist outside the streak has no row, as no other view outside it has one. */
    @Test
    fun onlyChecklistsInTheStreakHaveABank() {
        val state = joinRewards(AppState(checklists = listOf(medication, plants)), medication.id, today.minusDays(7))
        composeRule.setContent {
            WorkoutTrackerTheme {
                val mood = moMood(state, FeatureVisibilitySettings(), today)
                MoStreakScreen(mood = mood, banks = testBanks(state, mood))
            }
        }

        composeRule.onNodeWithTag("streak_checklist_chk_med").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("streak_checklist_chk_plants").assertDoesNotExist()
    }

    /** A measurement a person made has its row while it counts toward the streak, and none outside it or once it left. */
    @Test
    fun onlyMeasurementsInTheStreakHaveABank() {
        fun made(id: String, label: String) = MeasurementConfiguration(
            id = id,
            label = label,
            icon = ViewIcon.Ruler,
            hue = ViewHue.Plum,
            unit = "cm",
            step = MeasurementStep("0.1"),
            rewards = null,
            whenAway = MadeMeasurementWhenAway,
            checkpoint = DefaultMeasurementCheckpoint,
        )
        val state = AppState(measurements = listOf(TestWeightMeasurement, made("msr_wa1s", "Waist"), made("msr_h1ps", "Hips"), made("msr_ch3s", "Chest")))
            .applyingMeasurementEdits("msr_wa1s", listOf(MeasurementEdit.JoinRewards), today.minusDays(7))
            .applyingMeasurementEdits("msr_ch3s", listOf(MeasurementEdit.JoinRewards), today.minusDays(7))
            .applyingMeasurementEdits("msr_ch3s", listOf(MeasurementEdit.LeaveRewards), today)
        composeRule.setContent {
            WorkoutTrackerTheme {
                val mood = moMood(state, FeatureVisibilitySettings(), today)
                MoStreakScreen(mood = mood, banks = testBanks(state, mood))
            }
        }

        composeRule.onNodeWithTag("streak_measurement_msr_wa1s").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("streak_measurement_msr_h1ps").assertDoesNotExist()
        composeRule.onNodeWithTag("streak_measurement_msr_ch3s").assertDoesNotExist()
    }

    /** Care taken out of the streak through its page has no bank either, so no empty bank reads as full. */
    @Test
    fun aTaskWhoseChecklistLeftTheStreakHasNoBank() {
        val left = leaveRewards(careState().let { it.copy(checklists = it.checklists + TestSupplementsChecklist) }, CareChecklistId, today)
        composeRule.setContent {
            WorkoutTrackerTheme {
                val mood = moMood(left, FeatureVisibilitySettings(), today)
                MoStreakScreen(mood = mood, banks = testBanks(left, mood))
            }
        }

        composeRule.onNodeWithTag("streak_checklist_supplements").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("streak_checklist_care").assertDoesNotExist()
    }
}

/** Supplies candidates; the screen itself decides which trackers have a standing today. */
private fun testBanks(state: AppState, mood: de.nicolasmahn.momentum.domain.mo.MoMood): List<TrackerBankPresentation> {
    val refs = (listOfNotNull(de.nicolasmahn.momentum.domain.ActivityTrackerRef.takeIf { state.activity != null }) +
        state.checklists.map { ViewRef.Checklist(it.id).trackerRef } +
        state.loggedViews.map { it.ref.trackerRef }).distinct()
    return refs.map { ref ->
        val tag = "streak_${ref.type.value}_${ref.id}"
        TrackerBankPresentation(ref = ref, label = mood.trackerLabels[ref] ?: ref.id, testTag = tag)
    }
}
