package com.example.workouttracker.feature.tracker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.workouttracker.R
import com.example.workouttracker.core.text.uiText
import com.example.workouttracker.core.ui.LocalDayCovers
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.TrackerStatisticsIndex
import com.example.workouttracker.domain.DayChangeResult
import com.example.workouttracker.domain.LoggedViewRef
import com.example.workouttracker.domain.TrackerRef
import com.example.workouttracker.domain.TrackerTypeId
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.questions.Question
import com.example.workouttracker.domain.questions.QuestionsConfiguration
import com.example.workouttracker.domain.questions.QuestionsEdit
import com.example.workouttracker.domain.questions.starterQuestion
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import com.example.workouttracker.feature.checklist.vector
import com.example.workouttracker.feature.questions.words
import com.example.workouttracker.feature.questions.QuestionsDayChange
import com.example.workouttracker.feature.questions.QuestionsScreen
import com.example.workouttracker.feature.settings.DefaultAnswerLabels
import com.example.workouttracker.feature.settings.MadeQuestionsSettingsPage
import com.example.workouttracker.feature.settings.NamedQuestionIds
import com.example.workouttracker.feature.settings.QuestionsSetup
import com.example.workouttracker.reminder.sendTestReminder

/** The questions kind owns its screens, setup, settings and typed store connections. */
internal class QuestionsTrackerModule(
    private val state: AppState,
    visibility: FeatureVisibilitySettings,
    onVisibilityChange: (LoggedViewRef, Boolean) -> Boolean,
    private val actions: LoggedTrackerActions<QuestionsConfiguration, QuestionsEdit>,
    private val newQuestions: (String, Question) -> QuestionsConfiguration,
    private val onDayChange: (QuestionsDayChange) -> DayChangeResult,
    private val answeredDays: (QuestionsConfiguration) -> Int,
    private val namedIds: (QuestionsConfiguration) -> NamedQuestionIds,
    private val homeStatuses: Map<TrackerRef, TrackerHomeStatus> = emptyMap(),
    private val statisticsIndex: TrackerStatisticsIndex = TrackerStatisticsIndex(state),
) : TrackerModule {
    override val typeId = TrackerTypeId("questions")

    override val views: List<TrackerView> = state.questions.map { view ->
        object : LoggedTrackerView<QuestionsConfiguration, QuestionsEdit>(
            typeId = typeId,
            configuration = view,
            visibility = visibility,
            onVisibilityChange = onVisibilityChange,
            actions = actions,
            homeStatus = homeStatuses[TrackerRef(typeId, view.id)] ?: TrackerHomeStatus.None,
            homeOrder = 300,
            reminderEdit = { QuestionsEdit.SetRemind(it) },
            reminderChange = { edit ->
                when (edit) {
                    is QuestionsEdit.SetRemind -> edit.remind
                    else -> null
                }
            },
        ) {
            override val statistics get() = statisticsIndex.facts(configuration)
            override fun recordDateForJudgmentDay(day: java.time.LocalDate) = configuration.recordJudgedOn(day)
            override val opensOnReminderDate = true

            @Composable
            override fun Screen(host: TrackerScreenHost) {
                QuestionsScreen(
                    state = state,
                    view = configuration,
                    todayKey = host.todayKey,
                    onDayChange = onDayChange,
                    initialDateKey = host.initialDateKey,
                    onInitialDateConsumed = host.onInitialDateConsumed,
                    words = remember(configuration) { configuration.words() },
                    covers = LocalDayCovers.current[ref].orEmpty(),
                    leave = host.leave,
                )
            }

            @Composable
            override fun Settings(host: TrackerSettingsHost) {
                MadeQuestionsSettingsPage(
                    view = configuration,
                    today = host.today,
                    answeredDays = answeredDays(configuration),
                    named = namedIds(configuration),
                    bedtime = host.bedtime,
                    daysOff = host.daysOff,
                    onSave = { save(host, it) },
                    reminderRow = { edited, tag, onChange -> host.LoggedReminderRow(typeId, edited, tag, onChange) },
                    onSendTest = sendTest(host),
                    onDelete = { delete(host) },
                    onClose = host.onClose,
                    leaveRequested = host.leaveRequested,
                    onLeaveAnswered = host.onLeaveAnswered,
                    onUnsavedWorkChange = host.onUnsavedWorkChange,
                    message = host.message,
                    modifier = host.modifier,
                )
            }
        }
    }

    override val creation: TrackerCreation = object : TrackerCreation {
        override val typeId = this@QuestionsTrackerModule.typeId
        override val title = uiText(R.string.questions_destination)
        override val pageTitle = uiText(R.string.questions_settings_new)
        override val description = uiText(R.string.settings_new_questions_description)
        override val icon = ViewIcon.Journal.vector
        override val testTag = "settings_questions_new"

        @Composable
        override fun Setup(host: TrackerSettingsHost) {
            val context = LocalContext.current
            QuestionsSetup(
                starter = {
                    newQuestions(
                        context.getString(R.string.questions_destination),
                        starterQuestion(
                            text = context.getString(R.string.questions_settings_question),
                            answerLabels = DefaultAnswerLabels.map(context::getString),
                        ),
                    )
                },
                today = host.today,
                bedtime = host.bedtime,
                daysOff = host.daysOff,
                onCreate = actions.create,
                onClose = host.onClose,
                reminderRow = { edited, tag, onChange -> host.LoggedReminderRow(typeId, edited, tag, onChange) },
                onSendTest = { test -> host.sendTest { context.sendTestReminder(test) } },
                leaveRequested = host.leaveRequested,
                onLeaveAnswered = host.onLeaveAnswered,
                onUnsavedWorkChange = host.onUnsavedWorkChange,
                message = host.message,
                modifier = host.modifier,
            )
        }
    }
}
