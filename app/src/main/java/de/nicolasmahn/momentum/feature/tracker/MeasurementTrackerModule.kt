package de.nicolasmahn.momentum.feature.tracker

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.core.text.uiText
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.TrackerStatisticsIndex
import de.nicolasmahn.momentum.domain.DayChangeResult
import de.nicolasmahn.momentum.domain.LoggedViewRef
import de.nicolasmahn.momentum.domain.TrackerRef
import de.nicolasmahn.momentum.domain.TrackerTypeId
import de.nicolasmahn.momentum.domain.checklist.ViewIcon
import de.nicolasmahn.momentum.domain.measurement.MeasurementConfiguration
import de.nicolasmahn.momentum.domain.measurement.MeasurementEdit
import de.nicolasmahn.momentum.domain.settings.FeatureVisibilitySettings
import de.nicolasmahn.momentum.feature.checklist.vector
import de.nicolasmahn.momentum.feature.settings.MadeMeasurementSettingsPage
import de.nicolasmahn.momentum.feature.settings.MeasurementSetup
import de.nicolasmahn.momentum.feature.settings.ViewWrite
import de.nicolasmahn.momentum.feature.measurement.MeasurementDayChange
import de.nicolasmahn.momentum.feature.measurement.MeasurementScreen
import de.nicolasmahn.momentum.reminder.sendTestReminder

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
