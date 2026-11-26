package de.nicolasmahn.momentum.domain.mo

import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.SubstancesTrackerRef
import de.nicolasmahn.momentum.domain.TrackerRef
import de.nicolasmahn.momentum.domain.checklist.ChecklistRewards
import de.nicolasmahn.momentum.domain.checklist.Checkpoint
import de.nicolasmahn.momentum.domain.checklist.CheckpointTime
import de.nicolasmahn.momentum.domain.checklist.DayCheckpointId
import de.nicolasmahn.momentum.domain.checklist.RewardPeriod
import de.nicolasmahn.momentum.domain.measurement.MeasurementBank
import de.nicolasmahn.momentum.domain.measurement.MeasurementConfiguration
import de.nicolasmahn.momentum.domain.measurement.MeasurementLog
import de.nicolasmahn.momentum.domain.measurement.testMadeMeasurement
import de.nicolasmahn.momentum.domain.ref
import de.nicolasmahn.momentum.domain.reminder.ReminderSettings
import de.nicolasmahn.momentum.domain.settings.FeatureVisibilitySettings
import de.nicolasmahn.momentum.domain.substance.SubstanceEdit
import de.nicolasmahn.momentum.domain.substance.SubstanceGoal
import de.nicolasmahn.momentum.domain.substance.SubstanceKind
import de.nicolasmahn.momentum.domain.substance.SubstanceNoneDay
import de.nicolasmahn.momentum.domain.substance.SubstanceSettings
import de.nicolasmahn.momentum.domain.substance.applyEdit
import de.nicolasmahn.momentum.domain.trackerRef
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mo watches every view that counts toward the streak, on the days the reward walk judges it. A
 * Substances view counted toward the streak from 2026-11-11 with a bank of tokens while Mo
 * ignored it, because a stored flag no screen showed was off; a view a person made was the same.
 * The flag is off in every state here.
 */
class WatchedByStreakTest {
    private val monday = LocalDate.parse("2026-11-02")
    private fun on(day: Int) = monday.plusDays(day.toLong())
    private val visibility = FeatureVisibilitySettings()
    private val reminders = ReminderSettings()
    private val nine = LocalTime.of(9, 0)

    /** Alcohol tracked and a check-in at nine, as a person sets Substances up. */
    private val tracking = SubstanceSettings()
        .applyEdit(SubstanceEdit.Track(SubstanceKind.ALCOHOL.id, true), on(0))
        .applyEdit(SubstanceEdit.Remind(true), on(0))
        .applyEdit(SubstanceEdit.CheckInAt(CheckpointTime.Fixed(nine)), on(0))

    /** Logging counts toward the streak from Wednesday. */
    private val counting = tracking.applyEdit(SubstanceEdit.SetGoal(SubstanceGoal.Logging), on(2))

    /** "None" recorded for each of [days]; Substances is about yesterday, so the day after judges it. */
    private fun substances(settings: SubstanceSettings, vararg days: Int) =
        AppState(substances = settings, substanceNoneDays = days.map { SubstanceNoneDay(on(it).toString(), 1L) })

    /** A measurement a person made, reminded at nine, counting toward the streak from Wednesday. */
    private val waist = testMadeMeasurement().copy(
        checkpoint = Checkpoint(id = DayCheckpointId, at = CheckpointTime.Fixed(nine), remind = true),
        rewards = MeasurementBank.Standard(ChecklistRewards(3, 7, periods = listOf(RewardPeriod(on(2))))),
    )
    private val waistRef = waist.ref.trackerRef

    private fun measured(view: MeasurementConfiguration, vararg days: Int) =
        AppState(measurements = listOf(view), measurementLogs = days.map { MeasurementLog(view.id, on(it).toString(), 1L, 90.0) })

    private fun mood(state: AppState, day: Int) = moMood(state, visibility, on(day))

    private fun watchedDays(state: AppState, ref: TrackerRef, day: Int) = MoLedger.of(state, visibility, on(day)).history(ref).keys

    @Test fun substancesThatCountsTowardTheStreakIsAskedForAtItsCheckIn() {
        // Friday judges Thursday, which holds no record.
        val friday = mood(substances(counting, 0, 1, 2), day = 4)

        assertFalse(counting.mo.enabled)
        assertEquals(listOf(SubstancesTrackerRef), friday.missingTrackersToday)
        assertEquals(listOf(habitDayMinuteOf(nine)), friday.trackerDueMinutes(SubstancesTrackerRef, reminders))
        assertEquals(listOf(SubstancesTrackerRef), moAskedTrackers(friday, reminders, LocalTime.of(9, 20)).waited.keys.toList())
        assertEquals(emptyList<TrackerRef>(), moOverdueTrackers(friday, reminders, LocalTime.of(9, 59)))
        assertEquals(listOf(SubstancesTrackerRef), moOverdueTrackers(friday, reminders, LocalTime.of(10, 1)))
    }

    @Test fun aLoggedDayIsKeptAndNothingIsAskedOfIt() {
        val friday = mood(substances(counting, 0, 1, 2, 3), day = 4)

        assertEquals(listOf(SubstancesTrackerRef), friday.keptTrackersToday)
        assertEquals(emptyList<TrackerRef>(), friday.missingTrackersToday)
        assertEquals(emptyList<Int>(), friday.trackerDueMinutes(SubstancesTrackerRef, reminders))
    }

    @Test fun substancesIsWatchedOnlyOnTheDaysItCountsTowardTheStreak() {
        // Nothing was logged before it joined on Wednesday: those days are none of Mo's.
        assertEquals(setOf(on(2), on(3), on(4)), watchedDays(substances(counting), SubstancesTrackerRef, day = 4))
        // Never joined, it is not watched at all, however long nothing was logged.
        val outside = mood(substances(tracking, 0), day = 4)
        assertFalse(SubstancesTrackerRef in outside.trackerOrder)
        assertEquals(emptyList<TrackerRef>(), outside.missingTrackersToday)
        assertEquals(emptyList<Int>(), outside.trackerDueMinutes(SubstancesTrackerRef, reminders))
    }

    @Test fun substancesStopsBeingWatchedWhenItLeavesTheStreak() {
        val left = counting.applyEdit(SubstanceEdit.SetGoal(null), on(4))
        val friday = mood(substances(left, 0, 1, 2), day = 4)

        assertFalse(SubstancesTrackerRef in friday.trackerOrder)
        assertEquals(emptyList<TrackerRef>(), friday.missingTrackersToday)
        assertEquals(emptyMap<TrackerRef, List<Int>>(), moAskedTrackers(friday, reminders, LocalTime.of(23, 0)).waited)
        assertEquals(emptySet<LocalDate>(), watchedDays(substances(left, 0, 1, 2), SubstancesTrackerRef, day = 4))
    }

    @Test fun aMadeViewThatCountsTowardTheStreakIsAskedForAtItsReminder() {
        val friday = mood(measured(waist, 0, 1, 2, 3), day = 4)

        assertFalse(waist.mo.enabled)
        assertEquals(listOf(waistRef), friday.missingTrackersToday)
        assertEquals(listOf(habitDayMinuteOf(nine)), friday.trackerDueMinutes(waistRef, reminders))
        assertEquals(listOf(waistRef), moOverdueTrackers(friday, reminders, LocalTime.of(10, 1)))
        // Counted from Wednesday, although it was measured from Monday on.
        assertEquals(setOf(on(2), on(3), on(4)), watchedDays(measured(waist, 0, 1, 2, 3), waistRef, day = 4))
        assertEquals(listOf(waistRef), mood(measured(waist, 0, 1, 2, 3, 4), day = 4).keptTrackersToday)
    }

    @Test fun aMadeViewStopsBeingWatchedWhenItLeavesTheStreak() {
        val left = waist.copy(rewards = MeasurementBank.Standard(ChecklistRewards(3, 7, periods = listOf(RewardPeriod(on(2), until = on(3))))))
        val friday = mood(measured(left, 0, 1, 2, 3), day = 4)

        assertFalse(waistRef in friday.trackerOrder)
        assertEquals(emptyList<TrackerRef>(), friday.missingTrackersToday)
        assertEquals(emptyList<Int>(), friday.trackerDueMinutes(waistRef, reminders))
    }

    /** A view with the flavor Plain has nothing to act out: Mo asks for it and warms by it as he is. */
    @Test fun aPlainViewIsAskedForWithNothingToActOut() {
        val friday = mood(measured(waist, 0, 1, 2, 3), day = 4)
        val figure = friday.contentKey(waistRef).figure

        assertEquals(null, waist.mo.motif)
        assertEquals(MoOccasion.Idle, figure.occasion)
        assertEquals(MoProp.None, figure.prop)
        assertEquals(MoSaveAct.Generic, figure.saveAct)
        assertEquals(MoThrivingLook.None, figure.thrivingLook)
    }

    /**
     * The reward walk never read the flag, and it does not read who Mo watches: with the flag on
     * or off the same records give the same marks, tokens and streak, on every day of two weeks.
     */
    @Test fun whoMoWatchesChangesNoMarkTokenOrStreak() {
        val flagged = TrackerMoPolicy(enabled = true)
        val records = intArrayOf(0, 1, 2, 4, 5, 8, 9, 10)
        val off = substances(counting, *records).let { state ->
            state.copy(measurements = listOf(waist), measurementLogs = records.map { MeasurementLog(waist.id, on(it).toString(), 1L, 90.0) })
        }
        val onFlag = off.copy(substances = counting.copy(mo = flagged), measurements = listOf(waist.copy(mo = flagged)))

        (2..13).forEach { day ->
            val expected = habitStreak(onFlag, visibility, on(day))
            val actual = habitStreak(off, visibility, on(day))
            assertEquals("day $day", expected, actual)
            assertEquals("day $day", expected, mood(off, day).streak)
        }
        // The walk had something to decide: a gap a token covered, and both banks on show.
        val last = habitStreak(off, visibility, on(13))
        assertTrue(last.history.any { it.mark == MoDayMark.Protected })
        assertEquals(setOf(SubstancesTrackerRef, waistRef), last.standings.keys)
    }
}
