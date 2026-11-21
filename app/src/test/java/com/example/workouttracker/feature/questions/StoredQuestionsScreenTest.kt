package com.example.workouttracker.feature.questions

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import com.example.workouttracker.core.text.resolve
import com.example.workouttracker.core.ui.theme.WorkoutTrackerTheme
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.changingDay
import com.example.workouttracker.domain.questions.Answer
import com.example.workouttracker.domain.questions.Question
import com.example.workouttracker.domain.questions.testMadeQuestions
import com.example.workouttracker.persistence.PreferencesTrackerRepository
import com.example.workouttracker.persistence.RecordedTimeSessionsCodec
import com.example.workouttracker.persistence.backup.BackupArchive
import com.example.workouttracker.persistence.backup.BackupSnapshot
import com.example.workouttracker.persistence.backup.validateRestoreCompatibility
import com.example.workouttracker.persistence.json.AppStateJsonCodec
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class StoredQuestionsScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    @Config(qualifiers = "de")
    fun acceptedStoredPromptsAnswersAndHelpRenderVerbatimAndSaveTheirOwnIds() {
        val day = LocalDate.parse("2026-10-23")
        val questions = listOf(
            Question("focus", "Where did your attention go?", listOf(
                Answer("steady", "One thing at a time"), Answer("shifting", "I switched often"),
            ), help = "Include breaks and distractions. Choose what fits your own day."),
            Question("energy", "What energy did you have?", listOf(
                Answer("high", "Plenty"), Answer("low", "A little"),
            )),
        )
        val configured = testMadeQuestions(id = "reflection", label = "Daily reflection", questions = questions, madeOn = day.minusDays(1))
        val original = AppState(questions = listOf(configured))
        val values = mapOf(
            PreferencesTrackerRepository.STATE_V32_KEY to AppStateJsonCodec.encode(original),
            PreferencesTrackerRepository.RECORDED_TIME_SESSIONS_KEY to RecordedTimeSessionsCodec.encode(emptySet()),
        )
        val snapshot = BackupSnapshot(BackupArchive.StoreNames.associateWith { store ->
            if (store == PreferencesTrackerRepository.PREFERENCES_NAME) values else emptyMap()
        })
        val restored = BackupArchive.decode(BackupArchive.encode(snapshot))
        validateRestoreCompatibility(restored)
        val decoded = AppStateJsonCodec.decodeVersion32(restored.stores.getValue(PreferencesTrackerRepository.PREFERENCES_NAME).getValue(PreferencesTrackerRepository.STATE_V32_KEY))
        assertEquals(original, decoded)
        val view = decoded.questions.single()
        val resources = ApplicationProvider.getApplicationContext<Context>().resources
        val shown = view.presentedQuestions()
        assertEquals(questions.map { it.id }, shown.map { it.id })
        assertEquals(questions.map { it.text }, shown.map { it.text.resolve(resources) })
        assertEquals(questions.map { question -> question.answers.map { it.id to it.label } },
            shown.map { question -> question.answers.map { it.id to it.label.resolve(resources) } })

        var stored = AppStateJsonCodec.encode(decoded)
        var state by mutableStateOf(decoded)
        compose.setContent {
            WorkoutTrackerTheme {
                QuestionsScreen(
                    state = state,
                    view = view,
                    words = view.words(),
                    todayKey = day.toString(),
                    onDayChange = { change ->
                        state.changingDay(change, timestampMillis = 200L) { updated ->
                            stored = AppStateJsonCodec.encode(updated)
                            state = AppStateJsonCodec.decodeVersion32(stored)
                            true
                        }
                    },
                )
            }
        }
        questions.forEach { compose.onNodeWithText(it.text).assertExists() }
        compose.onNodeWithTag("questions_reflection_focus_0").assertTextEquals("One thing at a time")
        click("questions_reflection_focus_help")
        compose.onNodeWithText(questions.first().help!!).assertExists()
        click("questions_reflection_focus_help_close")
        click("questions_reflection_focus_0")
        click("questions_reflection_energy_1")
        click("questions_reflection_save")
        compose.runOnIdle {
            val saved = AppStateJsonCodec.decodeVersion32(stored)
            val entry = saved.questionEntries.single()
            assertEquals(view.id, entry.viewId)
            assertEquals(day.toString(), entry.dateKey)
            assertEquals(mapOf("focus" to "steady", "energy" to "low"), entry.answers)
            assertEquals(questions, saved.questions.single().questions)
        }
    }

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }
}
