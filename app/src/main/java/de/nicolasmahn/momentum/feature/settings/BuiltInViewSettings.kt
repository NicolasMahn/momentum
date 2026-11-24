package de.nicolasmahn.momentum.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import de.nicolasmahn.momentum.domain.ViewLook
import de.nicolasmahn.momentum.domain.reminder.ReminderSettings
import de.nicolasmahn.momentum.domain.settings.DashboardFeature
import java.time.LocalDate

/** The result of saving configuration and reminder settings, including a pending phone permission. */
internal enum class BuiltInViewSaveStep {
    /** Nothing is left in either draft and nothing failed: the page returns to the list. */
    Close,

    /** The configuration could not be stored. The Save stopped there, so the reminder was not attempted. */
    ConfigurationFailed,

    /** The Save waits, and the phone has to be asked for exact alarms. */
    AskExactAlarm,

    /** The Save waits, and the phone has to be asked for notifications. */
    AskNotifications,

    /** The phone did not give what the reminder needs, so the reminder was not stored and the Save is over. */
    ReminderRefused,

    /** Storing the reminder reported a failure, which keeps the page open with its error. */
    ReminderFailed,

    /** The Save is over and nothing failed, but a draft still holds something, so the page stays. */
    Stay,
}

/**
 * The one Save of Activity's page over its two stores: [configuration], the ordered edits of its
 * parts, and [reminder], what is wanted of the workout reminder, which lives in the reminder
 * settings and may need a permission before it can be stored.
 *
 * A store with nothing to store is skipped. The configuration goes first: if it cannot be stored
 * the Save stops with nothing else attempted, and once it is stored its draft is clean and stays
 * so whatever the reminder does next, refused, waiting for the phone or failed. One decision
 * closes the page wherever a Save finishes, at a press or at the phone's answer: nothing is left
 * in either draft and nothing failed.
 *
 * What is left is read from what is stored, never remembered, so Save and the question before
 * leaving follow exactly what a Save would still write: after the configuration was stored and
 * the reminder refused, only the reminder holds the page.
 */
internal class BuiltInViewSave<C, E, D : ConfigurationDraft<C, E>>(val configuration: D, val reminder: FeatureSettingsDraft) {
    /** Whether leaving would lose something, in either draft, over the reminder [settings] as they are stored. */
    fun hasUnsavedWork(settings: ReminderSettings): Boolean = configuration.hasUnsavedWork || reminder.hasUnsavedWork(settings)

    /** Saving needs a change in either draft, and no field of the configuration's that holds Save back. */
    fun canSave(settings: ReminderSettings): Boolean =
        (configuration.isDirty || reminder.hasUnsavedWork(settings)) && !configuration.heldBack

    /**
     * Save was pressed. The configuration's edits go through [storeConfiguration] first, then the
     * reminder's wanted values over [settings] through [storeReminder], unless switching it on
     * needs a permission the phone has not given: then the answer says which one to ask for.
     */
    fun save(
        settings: ReminderSettings,
        exactAlarmAllowed: Boolean,
        notificationsAllowed: Boolean,
        storeConfiguration: (List<E>) -> Boolean,
        storeReminder: (ReminderSettings) -> Boolean,
    ): BuiltInViewSaveStep {
        if (configuration.isDirty) {
            if (!storeConfiguration(configuration.edits)) return BuiltInViewSaveStep.ConfigurationFailed
            configuration.saved()
        }
        return finished(reminder.save(settings, exactAlarmAllowed, notificationsAllowed, storeReminder))
    }

    /** The phone's exact-alarm screen was left; null where no Save waited for it. See [FeatureSettingsDraft.exactAlarmAnswered]. */
    fun exactAlarmAnswered(
        granted: Boolean,
        notificationsAllowed: Boolean,
        settings: ReminderSettings,
        storeReminder: (ReminderSettings) -> Boolean,
    ): BuiltInViewSaveStep? = reminder.exactAlarmAnswered(granted, notificationsAllowed, settings, storeReminder)?.let(::finished)

    /** The phone answered the notification request; null where no Save waited for it. */
    fun notificationsAnswered(granted: Boolean, settings: ReminderSettings, storeReminder: (ReminderSettings) -> Boolean): BuiltInViewSaveStep? =
        reminder.notificationsAnswered(granted, settings, storeReminder)?.let(::finished)

    /** The phone could not be asked at all, so the Save is over with the reminder not stored. */
    fun notAsked() = reminder.notAsked()

    private fun finished(reminderStep: FeatureSaveStep): BuiltInViewSaveStep = when (reminderStep) {
        // The reminder holds nothing more; the configuration may, if it was changed while the phone was asked.
        FeatureSaveStep.Stored -> if (configuration.hasUnsavedWork) BuiltInViewSaveStep.Stay else BuiltInViewSaveStep.Close
        FeatureSaveStep.Failed -> BuiltInViewSaveStep.ReminderFailed
        FeatureSaveStep.AskExactAlarm -> BuiltInViewSaveStep.AskExactAlarm
        FeatureSaveStep.AskNotifications -> BuiltInViewSaveStep.AskNotifications
        FeatureSaveStep.Refused -> BuiltInViewSaveStep.ReminderRefused
    }
}

/**
 * Keeps the configuration and reminder drafts across process death while their separate writes
 * and permission requests finish. [draft] reads the current [stored] baseline for [today].
 */
@Composable
internal fun <C : ViewLook, E, D : ConfigurationDraft<C, E>> rememberBuiltInViewSave(
    stored: C,
    today: LocalDate,
    reminder: FeatureSettingsDraft,
    saver: Saver<D, Any>,
    draft: (C, LocalDate) -> D,
): BuiltInViewSave<C, E, D> {
    val configuration = rememberSaveable(stored.id, saver = saver) { draft(stored, today) }
    configuration.follow(stored, today)
    return remember(configuration, reminder) { BuiltInViewSave(configuration, reminder) }
}

/**
 * Activity's settings on the page every view has ([DraftSettingsPage]), under the tags a plain
 * view's page has (`feature_settings…`) and its key. A kind brings its [parts] and the content of each ([part]), what a part still
 * waits for ([hint]) and the dialogs it
 * opens ([dialogs]), and the level a part with levels of its own shows ([scrollKey]).
 *
 * Changes stay on the page, in [save]'s two drafts, until Save, which Settings runs through
 * [onSave] because the reminder may need the phone to be asked. Until then the page has unsaved
 * work, read from the configuration and the reminder [settings] as they are stored, and asks
 * before it is left. The view is built in, so its Delete is disabled and the bar says to switch it
 * off instead; what went wrong in Settings ([message]) takes the bar's line before that.
 */
@Composable
internal fun <C : ViewLook, E> BuiltInViewSettingsPage(
    save: BuiltInViewSave<C, E, *>,
    settings: ReminderSettings,
    onSave: () -> Unit,
    parts: List<ChecklistPage>,
    leaveRequested: Boolean,
    onLeaveAnswered: (discard: Boolean) -> Unit,
    onUnsavedWorkChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    opensOn: ChecklistPage = parts.first(),
    hint: BarLine? = null,
    partLabel: @Composable (ChecklistPage) -> String = { stringResource(it.label) },
    partTagName: (ChecklistPage) -> String = { it.tagName },
    dialogs: @Composable () -> Unit = {},
    scrollKey: Any? = null,
    barButton: (shown: ChecklistPage) -> BarButton? = { null },
    /** Saving switches the view on, as in the first start, so Save is a change even with nothing edited. */
    switchesOn: Boolean = false,
    part: @Composable (shown: ChecklistPage, edited: C, nameField: @Composable () -> Unit) -> Unit,
) {
    DraftSettingsPage(
        draft = save.configuration,
        testTag = "feature_settings",
        pageKey = SettingsPage.Feature(DashboardFeature.Activity).key,
        parts = parts,
        opensOn = opensOn,
        unsavedWork = save.hasUnsavedWork(settings),
        canSave = save.canSave(settings) || (switchesOn && !save.configuration.heldBack),
        onSave = onSave,
        delete = ViewDelete.BuiltIn,
        error = message?.let { BarLine(it, "settings_reminder_error") },
        leaveRequested = leaveRequested,
        onLeaveAnswered = onLeaveAnswered,
        onUnsavedWorkChange = onUnsavedWorkChange,
        modifier = modifier,
        hint = hint,
        partLabel = partLabel,
        partTagName = partTagName,
        dialogs = { dialogs() },
        scrollKey = scrollKey,
        barButton = barButton,
        part = part,
    )
}
