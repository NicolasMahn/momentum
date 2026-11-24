package de.nicolasmahn.momentum.feature.settings

import androidx.compose.runtime.saveable.SaverScope
import de.nicolasmahn.momentum.domain.reminder.MoCheckInSettings
import de.nicolasmahn.momentum.domain.reminder.ReminderSettings
import de.nicolasmahn.momentum.domain.reminder.ReminderTime
import de.nicolasmahn.momentum.domain.reminder.WorkoutReminderSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Activity's page keeps what the person wants of the workout reminder. The stored settings wait
 * for Save, and for the permission a Save may need, and are never overwritten by an earlier copy.
 */
class FeatureSettingsDraftTest {
    /** The settings as stored. A store that works puts what it is handed there, as the app's does. */
    private var stored = ReminderSettings(fallbackAlarmTime = ReminderTime(7, 0))
    private val store: (ReminderSettings) -> Boolean = { updated ->
        stored = updated
        true
    }

    private fun FeatureSettingsDraft.save(
        exactAlarm: Boolean = true,
        notifications: Boolean = true,
        store: (ReminderSettings) -> Boolean = this@FeatureSettingsDraftTest.store,
    ) = save(stored, exactAlarm, notifications, store)

    @Test
    fun aSwitchedReminderIsStoredOnlyOnSave() {
        val draft = FeatureSettingsDraft()
        assertFalse(draft.hasUnsavedWork(stored))

        draft.remind(true)

        assertFalse(stored.workout.enabled)
        assertTrue(draft.enabledIn(stored))
        assertTrue(draft.hasUnsavedWork(stored))

        assertEquals(FeatureSaveStep.Stored, draft.save())
        assertTrue(stored.workout.enabled)
        assertFalse(draft.hasUnsavedWork(stored))
    }

    /** Unsaved work is read from what is stored, so a change taken back leaves nothing to save or to ask about. */
    @Test
    fun aChangeTakenBackLeavesNothingToSave() {
        val draft = FeatureSettingsDraft()
        draft.remind(true)
        draft.remind(false)
        assertFalse(draft.hasUnsavedWork(stored))
        assertEquals(FeatureSaveStep.Stored, draft.save(exactAlarm = false, notifications = false, store = { error("Nothing is left to store.") }))
        assertNull(draft.waitingFor)

        // Stored by another route, the wanted value is no work left either.
        draft.remind(true)
        stored = stored.copy(workout = stored.workout.copy(enabled = true))
        assertFalse(draft.hasUnsavedWork(stored))
    }

    @Test
    fun switchingOnAsksForExactAlarmsBeforeNotificationsAndSwitchingOffAsksForNothing() {
        val draft = FeatureSettingsDraft()
        draft.remind(true)

        assertEquals(FeatureSaveStep.AskExactAlarm, draft.save(exactAlarm = false, notifications = false))
        assertEquals(ReminderAccessRequirement.ExactAlarm, draft.waitingFor)
        assertFalse(stored.workout.enabled)

        assertEquals(FeatureSaveStep.AskNotifications, draft.exactAlarmAnswered(granted = true, notificationsAllowed = false, stored, store))
        assertEquals(ReminderAccessRequirement.Notifications, draft.waitingFor)
        assertFalse(stored.workout.enabled)

        assertEquals(FeatureSaveStep.Stored, draft.notificationsAnswered(granted = true, stored, store))
        assertNull(draft.waitingFor)
        assertTrue(stored.workout.enabled)

        draft.remind(false)
        assertEquals(FeatureSaveStep.Stored, draft.save(exactAlarm = false, notifications = false))
        assertFalse(stored.workout.enabled)
    }

    /** Refused, or not even openable: nothing is stored, the Save is over and the wanted value stays on the page. */
    @Test
    fun aRefusalLeavesThePageWithUnsavedWork() {
        val draft = FeatureSettingsDraft()
        draft.remind(true)

        assertEquals(FeatureSaveStep.AskExactAlarm, draft.save(exactAlarm = false))
        assertEquals(FeatureSaveStep.Refused, draft.exactAlarmAnswered(granted = false, notificationsAllowed = true, stored, store))
        assertNull(draft.waitingFor)
        assertFalse(stored.workout.enabled)
        assertTrue(draft.enabledIn(stored))
        assertTrue(draft.hasUnsavedWork(stored))

        assertEquals(FeatureSaveStep.AskNotifications, draft.save(notifications = false))
        assertEquals(FeatureSaveStep.Refused, draft.notificationsAnswered(granted = false, stored, store))
        assertFalse(stored.workout.enabled)
        assertTrue(draft.hasUnsavedWork(stored))

        assertEquals(FeatureSaveStep.AskExactAlarm, draft.save(exactAlarm = false))
        draft.notAsked()
        assertNull(draft.waitingFor)
        assertTrue(draft.hasUnsavedWork(stored))

        // A second try, with the permissions given, stores it.
        assertEquals(FeatureSaveStep.Stored, draft.save())
        assertTrue(stored.workout.enabled)
    }

    /** Storing can report a failure after the values went in, as when an alarm could not follow: the page follows what is stored, not the report. */
    @Test
    fun aFailureAfterStoringLeavesThePageCleanAndOneWithoutStoringLeavesItUnsaved() {
        val draft = FeatureSettingsDraft()
        draft.remind(true)

        assertEquals(FeatureSaveStep.Failed, draft.save(store = { false }))
        assertFalse(stored.workout.enabled)
        assertTrue(draft.hasUnsavedWork(stored))

        val storedThenFailed: (ReminderSettings) -> Boolean = { updated ->
            stored = updated
            false
        }
        assertEquals(FeatureSaveStep.Failed, draft.save(store = storedThenFailed))
        assertTrue(stored.workout.enabled)
        assertFalse(draft.hasUnsavedWork(stored))
    }

    @Test
    fun aChangeStoredElsewhereBeforeSaveIsNotOverwritten() {
        val draft = FeatureSettingsDraft()
        draft.remind(true)

        val elsewhere = stored.copy(fallbackAlarmTime = ReminderTime(6, 30), moCheckIn = stored.moCheckIn.copy(enabled = false))
        stored = elsewhere

        assertEquals(FeatureSaveStep.Stored, draft.save())
        assertEquals(elsewhere.copy(workout = elsewhere.workout.copy(enabled = true)), stored)
    }

    /** The waiting Save holds no settings, so what is stored while the permission screen is open is what the wanted values go on. */
    @Test
    fun aChangeStoredDuringThePermissionWaitIsNotOverwritten() {
        val draft = FeatureSettingsDraft()
        draft.remind(true)
        assertEquals(FeatureSaveStep.AskExactAlarm, draft.save(exactAlarm = false, notifications = false))

        val duringExactAlarm = stored.copy(fixedBedtime = ReminderTime(23, 15))
        stored = duringExactAlarm
        assertEquals(FeatureSaveStep.AskNotifications, draft.exactAlarmAnswered(granted = true, notificationsAllowed = false, stored, store))
        assertEquals(duringExactAlarm, stored)

        val duringNotifications = stored.copy(moCheckIn = MoCheckInSettings(enabled = false), fallbackAlarmTime = ReminderTime(6, 30))
        stored = duringNotifications
        assertEquals(FeatureSaveStep.Stored, draft.notificationsAnswered(granted = true, stored, store))
        assertEquals(duringNotifications.copy(workout = duringNotifications.workout.copy(enabled = true)), stored)
    }

    /** Only the time was picked, so the switch is not the page's to say: it is the stored one at the moment of the save. */
    @Test
    fun aFieldOfThePageThatWasNotTouchedFollowsWhatIsStored() {
        val draft = FeatureSettingsDraft()
        draft.at(ReminderTime(6, 15))
        assertEquals(ReminderTime(6, 15), draft.timeIn(stored))
        assertFalse(draft.enabledIn(stored))
        assertTrue(draft.hasUnsavedWork(stored))

        stored = stored.copy(workout = stored.workout.copy(enabled = true))
        assertTrue(draft.enabledIn(stored))

        assertEquals(FeatureSaveStep.Stored, draft.save())
        assertEquals(WorkoutReminderSettings(enabled = true, fixedTime = ReminderTime(6, 15)), stored.workout)
    }

    /** Allow and a waiting test bring the same answers back; with no Save waiting they store nothing and leave the page as it is. */
    @Test
    fun anAnswerNoSaveWaitedForStoresNothing() {
        val draft = FeatureSettingsDraft()
        draft.remind(true)

        assertNull(draft.notificationsAnswered(granted = true, stored, store))
        assertNull(draft.exactAlarmAnswered(granted = true, notificationsAllowed = true, stored, store))

        assertFalse(stored.workout.enabled)
        assertTrue(draft.hasUnsavedWork(stored))
    }

    /** The permission screen can outlive the process: the wanted values and the waiting Save come back, and the Save finishes over what is stored by then. */
    @Test
    fun theWantedValuesAndAWaitingSaveSurviveBeingSavedAndRestored() {
        val draft = FeatureSettingsDraft()
        draft.remind(true)
        draft.at(ReminderTime(6, 15))
        assertEquals(FeatureSaveStep.AskExactAlarm, draft.save(exactAlarm = false))

        val restored = restored(draft)

        assertEquals(true, restored.enabled)
        assertEquals(ReminderTime(6, 15), restored.time)
        assertEquals(ReminderAccessRequirement.ExactAlarm, restored.waitingFor)

        val meanwhile = stored.copy(fixedBedtime = ReminderTime(23, 0))
        stored = meanwhile
        assertEquals(FeatureSaveStep.Stored, restored.exactAlarmAnswered(granted = true, notificationsAllowed = true, stored, store))
        assertEquals(meanwhile.copy(workout = WorkoutReminderSettings(enabled = true, fixedTime = ReminderTime(6, 15))), stored)

        // A switch set to off is a wanted value too, and an untouched page comes back untouched.
        val off = restored(FeatureSettingsDraft().apply { remind(false) })
        assertEquals(false, off.enabled)
        val untouched = restored(FeatureSettingsDraft())
        assertNull(untouched.enabled)
        assertNull(untouched.time)
        assertNull(untouched.waitingFor)
    }

    private fun restored(draft: FeatureSettingsDraft): FeatureSettingsDraft {
        val saved = with(FeatureSettingsDraft.Saver) { SaverScope { true }.save(draft) }
        return requireNotNull(FeatureSettingsDraft.Saver.restore(requireNotNull(saved)))
    }
}
