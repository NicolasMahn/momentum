package de.nicolasmahn.momentum.feature.tracker

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.core.text.uiText
import de.nicolasmahn.momentum.core.ui.ViewIdentity
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.TrackerStatisticsIndex
import de.nicolasmahn.momentum.domain.TrackerRef
import de.nicolasmahn.momentum.domain.TrackerTypeId
import de.nicolasmahn.momentum.domain.checklist.ChecklistConfiguration
import de.nicolasmahn.momentum.domain.checklist.ChecklistEdit
import de.nicolasmahn.momentum.domain.checklist.ChecklistProgress
import de.nicolasmahn.momentum.domain.checklist.isInUse
import de.nicolasmahn.momentum.domain.holiday.HolidaySchedule
import de.nicolasmahn.momentum.domain.mo.MoMood
import de.nicolasmahn.momentum.domain.settings.FeatureVisibilitySettings
import de.nicolasmahn.momentum.feature.checklist.ChecklistScreen
import de.nicolasmahn.momentum.feature.checklist.presentation
import de.nicolasmahn.momentum.feature.checklist.vector
import de.nicolasmahn.momentum.domain.checklist.recordJudgedOn
import de.nicolasmahn.momentum.domain.checklist.ViewIcon
import de.nicolasmahn.momentum.domain.checklist.asSchedule
import de.nicolasmahn.momentum.feature.settings.ChecklistEditor
import de.nicolasmahn.momentum.feature.settings.ChecklistSetup
import de.nicolasmahn.momentum.feature.settings.ViewWrite
import de.nicolasmahn.momentum.reminder.sendTestReminder
import java.time.LocalDate

/** Checklist rules stay here; the app discovers it in the same way as every other tracker kind. */
internal class ChecklistTrackerModule(
    private val state: AppState,
    private val visibility: FeatureVisibilitySettings,
    private val today: LocalDate,
    private val daysOff: HolidaySchedule,
    private val mood: MoMood?,
    private val onVisibilityChange: (String, Boolean) -> Boolean,
    private val onStateChange: (String, AppState) -> Boolean,
    private val onSave: (String, List<ChecklistEdit>) -> Boolean,
    private val onCreate: (ChecklistConfiguration, List<ChecklistEdit>) -> Boolean,
    private val onDelete: (String) -> Boolean,
    private val starter: (String) -> ChecklistConfiguration,
    private val progress: (ChecklistConfiguration) -> ChecklistProgress,
    private val recordedDays: (ChecklistConfiguration) -> Int,
    private val recordedDates: (ChecklistConfiguration) -> Set<LocalDate>,
    private val homeStatuses: Map<TrackerRef, TrackerHomeStatus> = emptyMap(),
    private val statisticsIndex: TrackerStatisticsIndex = TrackerStatisticsIndex(state),
) : TrackerModule {
    override val typeId = TrackerTypeId("checklist")
    override val views: List<TrackerView> = state.checklists.map { configuration ->
        object : TrackerView {
            override val ref = TrackerRef(typeId, configuration.id)
            override val label = configuration.label
            override val statistics get() = statisticsIndex.facts(configuration)
            override val tagName = "${typeId.value}_${configuration.id}"
            override val usable = configuration.isInUse(today)
            override val unavailableNote = uiText(R.string.checklist_settings_no_items).takeUnless { usable }
            override val visible = visibility.isChecklistVisible(configuration.id)
            override val homeOrder = 100
            override val homeStatus = homeStatuses[ref] ?: TrackerHomeStatus.None
            override fun recordDateForJudgmentDay(day: java.time.LocalDate) = configuration.recordJudgedOn(day)
            override val opensOnReminderDate = true
            override val reminders = configuration.checkpoints.map { checkpoint ->
                TrackerReminderCommand(checkpoint.id, checkpoint.remind) {
                    if (onSave(configuration.id, listOf(ChecklistEdit.SetCheckpointRemind(checkpoint.id, true)))) ViewWrite.Stored else ViewWrite.NotStored
                }
            }
            override fun setVisible(visible: Boolean): Boolean = onVisibilityChange(configuration.id, visible)
            @Composable override fun identity(): ViewIdentity = configuration.presentation()
            @Composable override fun Screen(host: TrackerScreenHost) {
                ChecklistScreen(
                    state = state,
                    checklist = configuration,
                    onStateChange = { onStateChange(configuration.id, it) },
                    todayKey = host.todayKey,
                    daysOff = daysOff,
                    initialDateKey = host.initialDateKey,
                    onInitialDateConsumed = host.onInitialDateConsumed,
                    checkpointTokens = host.checkpointTokens,
                    leave = host.leave,
                )
            }
            @Composable override fun Settings(host: TrackerSettingsHost) {
                val context = LocalContext.current
                ChecklistEditor(
                    checklist = configuration,
                    today = host.today,
                    bedtime = host.bedtime,
                    mood = mood,
                    progress = progress,
                    recordedDays = recordedDays(configuration),
                    recordedDates = recordedDates(configuration),
                    onSave = { onSave(configuration.id, it) },
                    reminderRow = { edited, checkpoint, onChange ->
                        host.reminderRow(TrackerReminderRow(TrackerReminderRef(ref, checkpoint.id), edited.label, checkpoint, "checklist_editor_checkpoint_${checkpoint.id}_reminder", "checklist_editor_checkpoint_${checkpoint.id}_timing"), onChange)
                    },
                    onSendTest = if (visible) { test -> host.sendTest { context.sendTestReminder(test.opening(usable)) } } else null,
                    onDelete = { onDelete(configuration.id).also { if (it) host.onClose() } },
                    onClose = host.onClose,
                    leaveRequested = host.leaveRequested,
                    onLeaveAnswered = host.onLeaveAnswered,
                    onUnsavedWorkChange = host.onUnsavedWorkChange,
                    modifier = host.modifier,
                    message = host.message,
                )
            }
        }
    }
    override val creation: TrackerCreation = checklistCreation(schedule = false)

    // One kind underneath: a schedule is a checklist that starts with times of day to tick at.
    override val creations: List<TrackerCreation> = listOf(creation, checklistCreation(schedule = true))

    private fun checklistCreation(schedule: Boolean): TrackerCreation = object : TrackerCreation {
        override val typeId = this@ChecklistTrackerModule.typeId
        override val variant = ScheduleVariant.takeIf { schedule }
        override val title = uiText(if (schedule) R.string.checklist_schedule_destination else R.string.checklist_destination)
        override val pageTitle = uiText(if (schedule) R.string.checklist_schedule_settings_new else R.string.checklist_settings_new)
        override val description = uiText(if (schedule) R.string.settings_new_schedule_description else R.string.settings_new_checklist_description)
        override val icon = (if (schedule) ViewIcon.Schedule else ViewIcon.Checklist).vector
        override val testTag = if (schedule) "settings_schedule_new" else "settings_checklist_new"
        @Composable override fun Setup(host: TrackerSettingsHost) {
            val context = LocalContext.current
            ChecklistSetup(
                starter = {
                    if (schedule) starter(context.getString(R.string.checklist_schedule_new_label)).asSchedule()
                    else starter(context.getString(R.string.checklist_new_label))
                },
                today = host.today,
                bedtime = host.bedtime,
                mood = mood,
                progress = progress,
                onCreate = onCreate,
                onClose = host.onClose,
                reminderRow = { edited, checkpoint, onChange ->
                    host.reminderRow(TrackerReminderRow(TrackerReminderRef(TrackerRef(typeId, edited.id), checkpoint.id), edited.label, checkpoint, "checklist_editor_checkpoint_${checkpoint.id}_reminder", "checklist_editor_checkpoint_${checkpoint.id}_timing"), onChange)
                },
                onSendTest = { test -> host.sendTest { context.sendTestReminder(test) } },
                leaveRequested = host.leaveRequested,
                onLeaveAnswered = host.onLeaveAnswered,
                onUnsavedWorkChange = host.onUnsavedWorkChange,
                modifier = host.modifier,
                message = host.message,
            )
        }
    }
}

private const val ScheduleVariant = "schedule"
