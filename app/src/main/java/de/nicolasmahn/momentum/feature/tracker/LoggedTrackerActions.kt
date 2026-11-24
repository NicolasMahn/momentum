package de.nicolasmahn.momentum.feature.tracker

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import de.nicolasmahn.momentum.core.ui.ViewIdentity
import de.nicolasmahn.momentum.domain.LoggedView
import de.nicolasmahn.momentum.domain.LoggedViewRef
import de.nicolasmahn.momentum.domain.TrackerRef
import de.nicolasmahn.momentum.domain.TrackerTypeId
import de.nicolasmahn.momentum.domain.ref
import de.nicolasmahn.momentum.domain.settings.FeatureVisibilitySettings
import de.nicolasmahn.momentum.domain.shows
import de.nicolasmahn.momentum.feature.checklist.presentation
import de.nicolasmahn.momentum.feature.settings.ViewWrite
import de.nicolasmahn.momentum.reminder.TestReminder
import de.nicolasmahn.momentum.reminder.sendTestReminder

/** Required store commands stay typed at the module boundary, including preview assemblies. */
internal class LoggedTrackerActions<C : LoggedView, E>(
    val save: (String, List<E>) -> ViewWrite,
    val create: (C, List<E>) -> ViewWrite,
    val delete: (String) -> Boolean,
)

/** Common page behavior, with each module retaining its own configuration and edit types. */
internal abstract class LoggedTrackerView<C : LoggedView, E>(
    typeId: TrackerTypeId,
    protected val configuration: C,
    visibility: FeatureVisibilitySettings,
    private val onVisibilityChange: (LoggedViewRef, Boolean) -> Boolean,
    protected val actions: LoggedTrackerActions<C, E>,
    private val reminderEdit: (Boolean) -> E,
    private val reminderChange: (E) -> Boolean?,
    final override val homeStatus: TrackerHomeStatus = TrackerHomeStatus.None,
    final override val homeOrder: Int = 1000,
) : TrackerView {
    final override val ref = TrackerRef(typeId, configuration.id)
    final override val label = configuration.label
    final override val tagName = "${typeId.value}_${configuration.id}"
    final override val visible = visibility.shows(configuration.ref)
    final override val reminders = listOf(
        TrackerReminderCommand(configuration.checkpoint.id, configuration.checkpoint.remind) {
            actions.save(configuration.id, listOf(reminderEdit(true)))
        },
    )

    final override fun setVisible(visible: Boolean): Boolean = onVisibilityChange(configuration.ref, visible)

    @Composable
    final override fun identity(): ViewIdentity = configuration.presentation()

    protected fun save(host: TrackerSettingsHost, edits: List<E>): ViewWrite = host.save(
        target = TrackerReminderRef(ref, configuration.checkpoint.id),
        reminderEnabled = configuration.checkpoint.remind,
        edits = edits,
        reminderChange = reminderChange,
        write = { actions.save(configuration.id, it) },
    )

    protected fun delete(host: TrackerSettingsHost): Boolean = actions.delete(configuration.id).also { deleted ->
        if (deleted) host.onClose()
    }

    @Composable
    protected fun sendTest(host: TrackerSettingsHost): ((TestReminder.AtCheckpoint) -> Unit)? {
        val context = LocalContext.current
        return if (visible) { test -> host.sendTest { context.sendTestReminder(test.opening(true)) } } else null
    }
}

/** Drafts keep the stored view's ID, so their reminder rows route back to the same command. */
@Composable
internal fun TrackerSettingsHost.LoggedReminderRow(
    typeId: TrackerTypeId,
    view: LoggedView,
    tag: String,
    onChange: (Boolean) -> Unit,
) = reminderRow(
    TrackerReminderRow(
        target = TrackerReminderRef(TrackerRef(typeId, view.id), view.checkpoint.id),
        label = view.label,
        checkpoint = view.checkpoint,
        testTag = tag,
    ),
    onChange,
)
