package com.example.workouttracker.feature.tracker

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.example.workouttracker.R
import com.example.workouttracker.core.text.uiText
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.TrackerStatisticsIndex
import com.example.workouttracker.domain.DayChangeResult
import com.example.workouttracker.domain.LoggedViewRef
import com.example.workouttracker.domain.TrackerRef
import com.example.workouttracker.domain.TrackerTypeId
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.measurement.MeasurementConfiguration
import com.example.workouttracker.domain.measurement.MeasurementEdit
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import com.example.workouttracker.feature.checklist.vector
import com.example.workouttracker.feature.settings.MadeMeasurementSettingsPage
import com.example.workouttracker.feature.settings.MeasurementSetup
import com.example.workouttracker.feature.settings.ViewWrite
import com.example.workouttracker.feature.measurement.MeasurementDayChange
import com.example.workouttracker.feature.measurement.MeasurementScreen
import com.example.workouttracker.reminder.sendTestReminder

/** The measurement kind owns its screens, setup, settings and typed store connections. */
internal class MeasurementTrackerModule(
    private val state: AppState,
    visibility: FeatureVisibilitySettings,
    onVisibilityChange: (LoggedViewRef, Boolean) -> Boolean,
    private val actions: LoggedTrackerActions<MeasurementConfiguration, MeasurementEdit>,
    private val newMeasurement: (String) -> MeasurementConfiguration,
    private val onDayChange: (MeasurementDayChange) -> DayChangeResult,
    private val recordedDays: (MeasurementConfiguration) -> Int,
    private val homeStatuses: Map<TrackerRef, TrackerHomeStatus> = emptyMap(),
    private val statisticsIndex: TrackerStatisticsIndex = TrackerStatisticsIndex(state),
) : TrackerModule {
    override val typeId = TrackerTypeId("measurement")

    override val views: List<TrackerView> = state.measurements.map { view ->
        object : LoggedTrackerView<MeasurementConfiguration, MeasurementEdit>(
            typeId = typeId,
            configuration = view,
            visibility = visibility,
            onVisibilityChange = onVisibilityChange,
            actions = actions,
            homeStatus = homeStatuses[TrackerRef(typeId, view.id)] ?: TrackerHomeStatus.None,
            homeOrder = 200,
            reminderEdit = { MeasurementEdit.SetRemind(it) },
            reminderChange = { edit ->
                when (edit) {
                    is MeasurementEdit.SetRemind -> edit.remind
                    else -> null
                }
            },
        ) {
            override val statistics get() = statisticsIndex.facts(configuration)
            override val opensOnReminderDate = true

            @Composable
            override fun Screen(host: TrackerScreenHost) {
                MeasurementScreen(
                    state = state,
                    measurement = configuration,
                    todayKey = host.todayKey,
                    onDayChange = onDayChange,
                    onStepChange = { step -> actions.save(configuration.id, listOf(MeasurementEdit.SetStep(step))) != ViewWrite.NotStored },
                    initialDateKey = host.initialDateKey,
                    onInitialDateConsumed = host.onInitialDateConsumed,
                    leave = host.leave,
                )
            }

            @Composable
            override fun Settings(host: TrackerSettingsHost) {
                MadeMeasurementSettingsPage(
                    measurement = configuration,
                    today = host.today,
                    recordedDays = recordedDays(configuration),
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
        override val typeId = this@MeasurementTrackerModule.typeId
        override val title = uiText(R.string.measurement_destination)
        override val pageTitle = uiText(R.string.measurement_settings_new)
        override val description = uiText(R.string.settings_new_measurement_description)
        override val icon = ViewIcon.Ruler.vector
        override val testTag = "settings_measurement_new"

        @Composable
        override fun Setup(host: TrackerSettingsHost) {
            val context = LocalContext.current
            MeasurementSetup(
                starter = { newMeasurement(context.getString(R.string.measurement_new_label)) },
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
