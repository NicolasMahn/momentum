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
import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import com.example.workouttracker.domain.time.TimeConfiguration
import com.example.workouttracker.domain.time.TimeEdit
import com.example.workouttracker.feature.checklist.vector
import com.example.workouttracker.feature.settings.MadeTimeSettingsPage
import com.example.workouttracker.feature.settings.TimeSetup
import com.example.workouttracker.feature.time.TimeDayChange
import com.example.workouttracker.feature.time.TimeScreen
import com.example.workouttracker.feature.time.TimeSessions
import com.example.workouttracker.feature.time.words
import com.example.workouttracker.reminder.sendTestReminder

/** The time kind owns its screens, setup, settings and typed store connections. */
internal class TimeTrackerModule(
    private val state: AppState,
    visibility: FeatureVisibilitySettings,
    onVisibilityChange: (LoggedViewRef, Boolean) -> Boolean,
    private val actions: LoggedTrackerActions<TimeConfiguration, TimeEdit>,
    private val newTime: (String) -> TimeConfiguration,
    private val onDayChange: (TimeDayChange) -> DayChangeResult,
    private val loggedDays: (TimeConfiguration) -> Int,
    private val daysOff: HolidaySchedule,
    private val sessions: () -> TimeSessions,
    private val homeStatuses: Map<TrackerRef, TrackerHomeStatus> = emptyMap(),
    private val statisticsIndex: TrackerStatisticsIndex = TrackerStatisticsIndex(state),
) : TrackerModule {
    override val typeId = TrackerTypeId("time")

    override val views: List<TrackerView> = state.time.map { view ->
        object : LoggedTrackerView<TimeConfiguration, TimeEdit>(
            typeId = typeId,
            configuration = view,
            visibility = visibility,
            onVisibilityChange = onVisibilityChange,
            actions = actions,
            homeStatus = homeStatuses[TrackerRef(typeId, view.id)] ?: TrackerHomeStatus.None,
            homeOrder = 400,
            reminderEdit = { TimeEdit.SetRemind(it) },
            reminderChange = { edit ->
                when (edit) {
                    is TimeEdit.SetRemind -> edit.remind
                    else -> null
                }
            },
        ) {
            override val tokenProgressInMinutes = true
            override val statistics get() = statisticsIndex.facts(configuration)
            override val opensOnReminderDate = false
            override val startSession: () -> Unit = { sessions().requestStart(configuration.id) }

            @Composable
            override fun Screen(host: TrackerScreenHost) {
                val timeSessions = sessions()
                TimeScreen(
                    state = state,
                    view = configuration,
                    todayKey = host.todayKey,
                    onDayChange = onDayChange,
                    words = remember(configuration) { configuration.words() },
                    daysOff = daysOff,
                    onStartSession = { timeSessions.requestStart(configuration.id) },
                    sessionNote = timeSessions.note?.takeIf { it.viewId == configuration.id && it.dateKey == host.todayKey },
                    covers = LocalDayCovers.current[ref].orEmpty(),
                    leave = host.leave,
                )
            }

            @Composable
            override fun Settings(host: TrackerSettingsHost) {
                MadeTimeSettingsPage(
                    view = configuration,
                    today = host.today,
                    loggedDays = loggedDays(configuration),
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
        override val typeId = this@TimeTrackerModule.typeId
        override val title = uiText(R.string.time_destination)
        override val pageTitle = uiText(R.string.time_settings_new)
        override val description = uiText(R.string.settings_new_time_description)
        override val icon = ViewIcon.Timer.vector
        override val testTag = "settings_time_new"

        @Composable
        override fun Setup(host: TrackerSettingsHost) {
            val context = LocalContext.current
            TimeSetup(
                starter = { newTime(context.getString(R.string.time_destination)) },
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
