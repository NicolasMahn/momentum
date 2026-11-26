package de.nicolasmahn.momentum.feature.tracker

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import de.nicolasmahn.momentum.core.text.UiText
import de.nicolasmahn.momentum.core.ui.LeaveGuard
import de.nicolasmahn.momentum.core.ui.ViewIdentity
import de.nicolasmahn.momentum.domain.TrackerStatistics
import de.nicolasmahn.momentum.domain.reward.TrackerReward
import de.nicolasmahn.momentum.domain.mo.TrackerObservation
import de.nicolasmahn.momentum.domain.TrackerRef
import de.nicolasmahn.momentum.domain.TrackerTypeId
import de.nicolasmahn.momentum.domain.checklist.Checkpoint
import de.nicolasmahn.momentum.domain.holiday.HolidaySchedule
import de.nicolasmahn.momentum.domain.reminder.Bedtime
import de.nicolasmahn.momentum.feature.settings.ViewWrite
import java.time.LocalDate

/**
 * One app integration point per kind. Implementations keep their configuration, edits and record
 * commands typed internally; the shell only discovers views and renders their owned content.
 * Rebuild the module's views when their source state changes. No module is a persistence codec:
 * a new stored record shape still needs its own migration and backup validation.
 */
internal interface TrackerModule {
    val typeId: TrackerTypeId
    val views: List<TrackerView>
    val creation: TrackerCreation?

    /** Every way the kind can be started; most have the one [creation], a checklist also a schedule. */
    val creations: List<TrackerCreation> get() = listOfNotNull(creation)
    val rewards: List<TrackerReward> get() = emptyList()
    val observations: List<TrackerObservation> get() = emptyList()
    val chosenPasses: Set<TrackerRef> get() = emptySet()
}

internal interface TrackerView {
    val ref: TrackerRef
    val label: String
    val tagName: String
    val visible: Boolean
    val reminders: List<TrackerReminderCommand>
    val opensOnReminderDate: Boolean
    val startSession: (() -> Unit)? get() = null
    val onSetAside: ((Boolean) -> Unit)? get() = null
    /** Null uses the shared reward standing; a kind may supply more detailed factual progress. */
    val homeStatus: TrackerHomeStatus? get() = null
    val homeOrder: Int get() = 1000
    val settingsOrder: Int get() = homeOrder
    val bankOrder: Int get() = homeOrder
    val bankTagName: String get() = "streak_$tagName"
    val statistics: TrackerStatistics? get() = null
    val tokenProgressInMinutes: Boolean get() = false
    val usable: Boolean get() = true
    val unavailableNote: UiText? get() = null

    fun recordDateForJudgmentDay(day: LocalDate): LocalDate? = day
    fun setVisible(visible: Boolean): Boolean

    @Composable fun identity(): ViewIdentity
    @Composable fun Screen(host: TrackerScreenHost)
    @Composable fun Settings(host: TrackerSettingsHost)
}

internal interface TrackerCreation {
    val typeId: TrackerTypeId

    /** Tells apart two ways one kind is started, as a schedule is a checklist started with times of day. */
    val variant: String? get() = null
    val title: UiText
    val pageTitle: UiText
    val description: UiText
    val icon: ImageVector
    val testTag: String

    @Composable fun Setup(host: TrackerSettingsHost)
}

internal data class TrackerReminderRef(val view: TrackerRef, val id: String)

/** Optional capability: a kind may have no reminders, one, or several named reminders. */
internal class TrackerReminderCommand(
    val id: String,
    val enabled: Boolean,
    val enable: () -> ViewWrite,
)

internal data class TrackerScreenHost(
    val todayKey: String,
    val initialDateKey: String?,
    /** The tokens a screen with several checkpoints shows on them, or null where the header carries the view's tokens or it has none. */
    val checkpointTokens: CheckpointTokens? = null,
    /** How the screen is left while it holds unsaved work. */
    val leave: LeaveGuard = LeaveGuard.None,
    val onInitialDateConsumed: () -> Unit,
)

/**
 * A token for one checkpoint at a time, on a checklist with several: the tokens no choice holds
 * yet ([left]), which every checkpoint shows, the checkpoints one can be set aside for or taken
 * back from ([offered]), those that hold one ([setAside]), and the command. A choice holds for
 * today only and records nothing.
 */
internal class CheckpointTokens(
    val left: Int,
    val offered: Set<String>,
    val setAside: Set<String>,
    val onChange: (checkpointId: String, setAside: Boolean) -> Unit,
)

internal data class TrackerReminderRow(
    val target: TrackerReminderRef,
    val label: String,
    val checkpoint: Checkpoint,
    val testTag: String,
    val timingTestTag: String = "${testTag}_timing",
)

/** Shared page lifecycle and platform access; it never knows a kind's edit classes. */
internal class TrackerSettingsHost(
    val today: LocalDate,
    val bedtime: Bedtime,
    val daysOff: HolidaySchedule,
    val leaveRequested: Boolean,
    val onLeaveAnswered: (Boolean) -> Unit,
    val onUnsavedWorkChange: (Boolean) -> Unit,
    val onClose: () -> Unit,
    val message: String?,
    val modifier: Modifier,
    val reminderRow: @Composable (TrackerReminderRow, (Boolean) -> Unit) -> Unit,
    val sendTest: (() -> Boolean) -> Unit,
    private val saveWithAccess: (TrackerReminderRef, () -> ViewWrite, () -> ViewWrite) -> ViewWrite,
) {
    fun <E> save(
        target: TrackerReminderRef,
        reminderEnabled: Boolean,
        edits: List<E>,
        reminderChange: (E) -> Boolean?,
        write: (List<E>) -> ViewWrite,
    ): ViewWrite {
        val switchesOn = !reminderEnabled && edits.mapNotNull(reminderChange).lastOrNull() == true
        if (!switchesOn) return write(edits)
        val rest = edits.filter { reminderChange(it) == null }
        return saveWithAccess(target, { if (rest.isEmpty()) ViewWrite.Stored else write(rest) }, { write(edits) })
    }
}

/** A presentation result of the reward rules, independent of a tracker's record shape. */
internal sealed interface TrackerHomeStatus {
    data object None : TrackerHomeStatus
    data object Done : TrackerHomeStatus
    data object Kept : TrackerHomeStatus
    /** A free day covers today and nothing is asked of the task, as Activity on a rest day its week names. */
    data object Rest : TrackerHomeStatus
    /** A week's amount Mo was asked not to ask for today: settled for the day, though the week is still short. */
    data object Quiet : TrackerHomeStatus
    /** A token is set aside for today, so the task is settled without a record. */
    data object Covered : TrackerHomeStatus
    data class Open(val done: Int, val parts: Int, val optional: Boolean = false) : TrackerHomeStatus
}
