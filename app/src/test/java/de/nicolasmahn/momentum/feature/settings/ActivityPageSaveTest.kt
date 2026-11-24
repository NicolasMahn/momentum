package de.nicolasmahn.momentum.feature.settings

import androidx.compose.runtime.saveable.SaverScope
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.activity.ActivityConfiguration
import de.nicolasmahn.momentum.domain.activity.ActivityEdit
import de.nicolasmahn.momentum.domain.activity.ActivityViewId
import de.nicolasmahn.momentum.domain.activity.applyingActivityEdits
import de.nicolasmahn.momentum.domain.activity.testActivityConfiguration
import de.nicolasmahn.momentum.domain.activity.withActivity
import de.nicolasmahn.momentum.domain.checklist.Dated
import de.nicolasmahn.momentum.domain.checklist.ViewHue
import de.nicolasmahn.momentum.domain.checklist.WhenAway
import de.nicolasmahn.momentum.domain.reminder.ReminderSettings
import de.nicolasmahn.momentum.domain.reminder.ReminderTime
import de.nicolasmahn.momentum.domain.settings.DashboardFeature
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Activity's page keeps two drafts, the ordered edits to its configuration and what is wanted of
 * its reminder, which lives in the reminder settings, and one Save stores both: the configuration
 * first, then the reminder, which may wait for the phone. What is left to save or to lose is read
 * from what is stored.
 */
class ActivityPageSaveTest {
    private val today = LocalDate.parse("2026-10-14")

    /** The two stores as they are. A store that works puts what it is handed there, as the app's do, on the day it runs. */
    private var state = AppState().withActivity()
    private var reminders = ReminderSettings(fallbackAlarmTime = ReminderTime(7, 0))
    private var storeDay = today
    private val calls = mutableListOf<String>()
    private val activity: ActivityConfiguration get() = requireNotNull(state.activity)

    private val storeConfiguration: (List<ActivityEdit>) -> Boolean = { edits ->
        calls += "configuration"
        state = state.applyingActivityEdits(edits, storeDay)
        true
    }
    private val storeReminder: (ReminderSettings) -> Boolean = { updated ->
        calls += "reminder"
        reminders = updated
        true
    }

    private fun page() = ActivityPageSave(ActivitySettingsDraft(activity, today), FeatureSettingsDraft())

    /** The page as each composition sees it: its configuration draft over what is stored now. */
    private fun ActivityPageSave.shown(day: LocalDate = today) = apply { configuration.follow(activity, day) }

    private fun ActivityPageSave.save(
        exactAlarm: Boolean = true,
        notifications: Boolean = true,
        configurationStore: (List<ActivityEdit>) -> Boolean = storeConfiguration,
        reminderStore: (ReminderSettings) -> Boolean = storeReminder,
    ) = save(reminders, exactAlarm, notifications, configurationStore, reminderStore)

    private fun restored(page: ActivityPageSave) = ActivityPageSave(
        requireNotNull(ActivitySettingsDraft.Saver.restore(requireNotNull(with(ActivitySettingsDraft.Saver) { SaverScopeStub.save(page.configuration) }))),
        requireNotNull(FeatureSettingsDraft.Saver.restore(requireNotNull(with(FeatureSettingsDraft.Saver) { SaverScopeStub.save(page.reminder) }))),
    )

    /** A cleared name keeps the last one in the draft, holds Save back, and is work leaving would lose. */
    @Test
    fun aBlankNameHoldsSaveBack() {
        val page = page()
        page.configuration.edit(ActivityEdit.SetHue(ViewHue.Navy))
        assertTrue(page.canSave(reminders))

        page.configuration.named(ActivityViewId, null)
        assertFalse(page.canSave(reminders))
        assertTrue(page.hasUnsavedWork(reminders))
        assertEquals("Activity", page.configuration.edited.label)
        assertFalse(page.configuration.nameStored(ActivityViewId))

        // Also when the reminder is all that changed.
        val onlyReminder = page().apply { reminder.remind(true); configuration.named(ActivityViewId, null) }
        assertFalse(onlyReminder.canSave(reminders))

        page.configuration.named(ActivityViewId, "Scale")
        page.configuration.edit(ActivityEdit.Rename("Scale"))
        assertTrue(page.canSave(reminders))
        assertEquals(BuiltInViewSaveStep.Close, page.save())
        assertEquals("Scale", activity.label)
        assertTrue(page.shown().configuration.nameStored(ActivityViewId))
    }

    /** The edits, a blank name and the day survive the process, and so does a Save waiting for the phone. */
    @Test
    fun bothDraftsSurviveProcessDeath() {
        val page = page()
        page.configuration.edit(ActivityEdit.Rename("Scale"))
        page.configuration.edit(ActivityEdit.SetBankSize(2))
        page.configuration.edit(ActivityEdit.SetWhenAway(WhenAway.Due))
        page.configuration.named(ActivityViewId, null)
        page.reminder.remind(true)

        val restored = restored(page)
        assertEquals(page.configuration.edits, restored.configuration.edits)
        assertEquals(page.configuration.stored, restored.configuration.stored)
        assertEquals(page.configuration.edited, restored.configuration.edited)
        assertEquals(setOf(ActivityViewId), restored.configuration.blankNames)
        assertEquals(today, restored.configuration.today)
        assertEquals(true, restored.reminder.enabled)
        assertTrue(restored.hasUnsavedWork(reminders))
        assertFalse(restored.canSave(reminders))
    }

    /** A store with nothing to store is skipped: only the reminder changed, so the configuration is not written. */
    @Test
    fun saveWithOnlyTheReminderChangedStoresTheReminderAlone() {
        val page = page()
        assertFalse(page.canSave(reminders))
        page.reminder.remind(true)
        assertTrue(page.canSave(reminders) && page.hasUnsavedWork(reminders))

        assertEquals(BuiltInViewSaveStep.Close, page.save())
        assertEquals(listOf("reminder"), calls)
        assertTrue(reminders.workout.enabled)
        assertEquals(testActivityConfiguration(), activity)
        assertFalse(page.shown().hasUnsavedWork(reminders))
    }

    @Test
    fun saveWithOnlyTheConfigurationChangedStoresTheConfigurationAlone() {
        val page = page()
        page.configuration.edit(ActivityEdit.Rename("Scale"))
        page.configuration.edit(ActivityEdit.SetBankSize(2))

        assertEquals(BuiltInViewSaveStep.Close, page.save(exactAlarm = false, notifications = false))
        assertEquals(listOf("configuration"), calls)
        assertEquals("Scale", activity.label)
        assertEquals(2, activity.rewards.maxPasses.on(today))
        assertEquals(ReminderSettings(fallbackAlarmTime = ReminderTime(7, 0)), reminders)
        assertFalse(page.shown().hasUnsavedWork(reminders))
        assertFalse(page.canSave(reminders))
    }

    /** With both changed the configuration is stored first, then the reminder, and the page closes once. */
    @Test
    fun saveWithBothChangedStoresTheConfigurationFirst() {
        val page = page()
        page.reminder.remind(true)
        page.configuration.edit(ActivityEdit.SetHue(ViewHue.Plum))

        assertEquals(BuiltInViewSaveStep.Close, page.save())
        assertEquals(listOf("configuration", "reminder"), calls)
        assertEquals(ViewHue.Plum, activity.hue)
        assertTrue(reminders.workout.enabled)
        assertFalse(page.shown().hasUnsavedWork(reminders))
    }

    /** A configuration that cannot be stored stops the Save: the reminder is not attempted, and everything is still there to save. */
    @Test
    fun aConfigurationThatCannotBeStoredStopsTheSaveBeforeTheReminder() {
        val page = page()
        page.reminder.remind(true)
        page.configuration.edit(ActivityEdit.Rename("Scale"))

        assertEquals(BuiltInViewSaveStep.ConfigurationFailed, page.save(configurationStore = { calls += "configuration"; false }))
        assertEquals(listOf("configuration"), calls)
        assertFalse(reminders.workout.enabled)
        assertNull(page.reminder.waitingFor)
        assertEquals("Activity", activity.label)
        assertTrue(page.shown().canSave(reminders))
        assertTrue(page.hasUnsavedWork(reminders))

        // Pressed again with the store working, both go through.
        assertEquals(BuiltInViewSaveStep.Close, page.save())
        assertEquals(listOf("configuration", "configuration", "reminder"), calls)
        assertEquals("Scale", activity.label)
    }

    /**
     * The reminder needs the phone, and the phone refuses. The configuration was stored before
     * the question and stays stored; its draft is clean, so what is left to save, and what
     * leaving would lose, is the reminder alone.
     */
    @Test
    fun theConfigurationStaysStoredWhenTheReminderIsRefused() {
        val page = page()
        page.reminder.remind(true)
        page.configuration.edit(ActivityEdit.SetBankSize(2))

        assertEquals(BuiltInViewSaveStep.AskExactAlarm, page.save(exactAlarm = false, notifications = false))
        assertEquals(listOf("configuration"), calls)
        assertEquals(2, activity.rewards.maxPasses.on(today))
        assertFalse(page.shown().configuration.hasUnsavedWork)
        // While the phone is asked, the reminder is still to be stored, so leaving would lose it.
        assertTrue(page.hasUnsavedWork(reminders))

        assertEquals(BuiltInViewSaveStep.ReminderRefused, page.exactAlarmAnswered(granted = false, notificationsAllowed = false, reminders, storeReminder))
        assertEquals(listOf("configuration"), calls)
        assertEquals(2, activity.rewards.maxPasses.on(today))
        assertFalse(reminders.workout.enabled)
        assertFalse(page.shown().configuration.hasUnsavedWork)
        assertTrue(page.reminder.hasUnsavedWork(reminders))
        assertTrue(page.hasUnsavedWork(reminders) && page.canSave(reminders))

        // The reminder switched back off, nothing is left: the page has nothing to save or to ask about.
        page.reminder.remind(false)
        assertFalse(page.hasUnsavedWork(reminders))
        assertFalse(page.canSave(reminders))
        // And a Save pressed again once the phone allows it stores the reminder alone.
        page.reminder.remind(true)
        assertEquals(BuiltInViewSaveStep.Close, page.save())
        assertEquals(listOf("configuration", "reminder"), calls)
    }

    /** A Save that waits for the phone finishes where the phone answers, and one decision closes the page there too. */
    @Test
    fun aSaveWaitingForAPermissionFinishesAtThePhonesAnswer() {
        val page = page()
        page.reminder.remind(true)
        page.configuration.edit(ActivityEdit.SetWhenAway(WhenAway.Due))

        assertEquals(BuiltInViewSaveStep.AskExactAlarm, page.save(exactAlarm = false, notifications = false))
        assertEquals(BuiltInViewSaveStep.AskNotifications, page.exactAlarmAnswered(granted = true, notificationsAllowed = false, reminders, storeReminder))
        assertEquals(ReminderAccessRequirement.Notifications, page.reminder.waitingFor)
        assertFalse(reminders.workout.enabled)
        assertTrue(page.shown().hasUnsavedWork(reminders))
        // No Save waits for exact alarms any more, so that answer is nobody's.
        assertNull(page.exactAlarmAnswered(granted = true, notificationsAllowed = true, reminders, storeReminder))

        assertEquals(BuiltInViewSaveStep.Close, page.notificationsAnswered(granted = true, reminders, storeReminder))
        assertEquals(listOf("configuration", "reminder"), calls)
        assertTrue(reminders.workout.enabled)
        assertEquals(WhenAway.Due, activity.whenAway.on(today))
        assertFalse(page.shown().hasUnsavedWork(reminders))

        // Notifications refused instead: the reminder is not stored, the configuration is.
        val refused = page().apply { reminder.remind(false) }
        reminders = reminders.copy(workout = reminders.workout.copy(enabled = false))
        refused.reminder.remind(true)
        refused.configuration.edit(ActivityEdit.SetHue(ViewHue.Navy))
        assertEquals(BuiltInViewSaveStep.AskNotifications, refused.save(exactAlarm = true, notifications = false))
        assertEquals(BuiltInViewSaveStep.ReminderRefused, refused.notificationsAnswered(granted = false, reminders, storeReminder))
        assertEquals(ViewHue.Navy, activity.hue)
        assertFalse(reminders.workout.enabled)
        assertTrue(refused.shown().hasUnsavedWork(reminders))
        assertFalse(refused.configuration.hasUnsavedWork)
    }

    /**
     * A reminder store that reports a failure keeps the page open with its error. Whether the
     * values were stored all the same is read from the settings: not stored, they are still to
     * save; stored, nothing is left to save or to ask about, and the page stays for its error.
     */
    @Test
    fun aReminderThatFailsKeepsThePageOpenWhateverWasStored() {
        val page = page()
        page.reminder.remind(true)
        page.configuration.edit(ActivityEdit.Rename("Scale"))

        assertEquals(BuiltInViewSaveStep.ReminderFailed, page.save(reminderStore = { calls += "reminder"; false }))
        assertEquals(listOf("configuration", "reminder"), calls)
        assertEquals("Scale", activity.label)
        assertFalse(page.shown().configuration.hasUnsavedWork)
        assertTrue(page.hasUnsavedWork(reminders) && page.canSave(reminders))

        // Reported failed although the values went in, as when their alarm could not be set and taken back.
        val storedAllTheSame: (ReminderSettings) -> Boolean = { updated ->
            reminders = updated
            false
        }
        assertEquals(BuiltInViewSaveStep.ReminderFailed, page.save(reminderStore = storedAllTheSame))
        assertTrue(reminders.workout.enabled)
        assertFalse(page.shown().hasUnsavedWork(reminders))
        assertFalse(page.canSave(reminders))
    }

    /**
     * The process died while the phone was asked, after the configuration was stored. The restored
     * page reads what is stored: nothing of the configuration is offered to save or to discard,
     * and the phone's answer finishes the reminder and closes the page.
     */
    @Test
    fun aRestoredPageAfterTheConfigurationWasStoredHoldsOnlyTheReminder() {
        val page = page()
        page.reminder.remind(true)
        page.configuration.edit(ActivityEdit.Rename("Scale"))
        page.configuration.edit(ActivityEdit.SetBankSize(2))
        // Kept as it was before the press, edits and all, then the Save stores the configuration and waits.
        val keptBeforeThePress = restored(page)
        assertEquals(BuiltInViewSaveStep.AskNotifications, page.save(exactAlarm = true, notifications = false))
        val keptWhileWaiting = restored(page)

        listOf(keptBeforeThePress, keptWhileWaiting).forEach { back ->
            back.shown()
            assertFalse(back.configuration.isDirty)
            assertFalse(back.configuration.hasUnsavedWork)
            assertEquals("Scale", back.configuration.edited.label)
            assertEquals(activity, back.configuration.edited)
            assertTrue(back.reminder.hasUnsavedWork(reminders))
        }
        // Only the one kept while waiting still has a Save to finish.
        assertNull(keptBeforeThePress.notificationsAnswered(granted = true, reminders, storeReminder))
        assertEquals(BuiltInViewSaveStep.Close, keptWhileWaiting.notificationsAnswered(granted = true, reminders, storeReminder))
        assertEquals(listOf("configuration", "reminder"), calls)
        assertTrue(reminders.workout.enabled)
        assertFalse(keptWhileWaiting.shown().hasUnsavedWork(reminders))
        // A second press on the page kept before stores nothing twice.
        assertEquals(BuiltInViewSaveStep.Close, keptBeforeThePress.shown().save())
        assertEquals(listOf("configuration", "reminder"), calls)
    }

    /**
     * Edits made before 04:00 and saved after are dated by the day they are saved: the page shows
     * them over the new day, and the store applies them on it. A second Save that day changes
     * nothing more.
     */
    @Test
    fun aSaveAfterFourOClockDatesTheChangesByTheNewDay() {
        val page = page()
        page.configuration.edit(ActivityEdit.SetBankSize(2))
        page.configuration.edit(ActivityEdit.SetWhenAway(WhenAway.Due))
        assertEquals(listOf(null, today), page.configuration.edited.whenAway.revisions.map { it.from })

        val tomorrow = today.plusDays(1)
        storeDay = tomorrow
        page.shown(tomorrow)
        assertEquals(listOf(null, tomorrow), page.configuration.edited.whenAway.revisions.map { it.from })
        assertEquals(BuiltInViewSaveStep.Close, page.save())
        assertEquals(Dated(listOf(Dated.Revision(3), Dated.Revision(2, tomorrow))), activity.rewards.maxPasses)
        assertEquals(Dated(listOf(Dated.Revision(WhenAway.LowerTarget), Dated.Revision(WhenAway.Due, tomorrow))), activity.whenAway)
        assertFalse(page.shown(tomorrow).hasUnsavedWork(reminders))

        // Saved on one day and changed again on the next: yesterday's revision stays, today's is added.
        val later = tomorrow.plusDays(1)
        storeDay = later
        page.shown(later)
        page.configuration.edit(ActivityEdit.SetBankSize(5))
        assertEquals(BuiltInViewSaveStep.Close, page.save())
        assertEquals(Dated(listOf(Dated.Revision(3), Dated.Revision(2, tomorrow), Dated.Revision(5, later))), activity.rewards.maxPasses)
        assertEquals(listOf("configuration", "configuration"), calls)
        assertEquals(BuiltInViewSaveStep.Close, page.shown(later).save(configurationStore = { error("Nothing is left to store.") }))
    }

    /** The phone's answer closes the page only when the configuration draft holds nothing either. */
    @Test
    fun aNameClearedWhileThePhoneWasAskedKeepsThePageOpen() {
        val page = page()
        page.reminder.remind(true)
        assertEquals(BuiltInViewSaveStep.AskNotifications, page.save(exactAlarm = true, notifications = false))
        page.configuration.named(ActivityViewId, null)

        assertEquals(BuiltInViewSaveStep.Stay, page.notificationsAnswered(granted = true, reminders, storeReminder))
        assertTrue(reminders.workout.enabled)
        assertTrue(page.hasUnsavedWork(reminders))
        assertFalse(page.canSave(reminders))
    }

    private object SaverScopeStub : SaverScope {
        override fun canBeSaved(value: Any): Boolean = true
    }
}
