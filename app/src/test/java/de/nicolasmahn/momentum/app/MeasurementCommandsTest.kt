package de.nicolasmahn.momentum.app

import de.nicolasmahn.momentum.domain.measurement.MeasurementBank
import de.nicolasmahn.momentum.persistence.CurrentTrackerStore
import de.nicolasmahn.momentum.persistence.storedDocument
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.checklist.ChecklistRewards
import de.nicolasmahn.momentum.domain.checklist.Checkpoint
import de.nicolasmahn.momentum.domain.checklist.CheckpointTime
import de.nicolasmahn.momentum.domain.checklist.Dated
import de.nicolasmahn.momentum.domain.checklist.DefaultChecklistRewards
import de.nicolasmahn.momentum.domain.checklist.RewardPeriod
import de.nicolasmahn.momentum.domain.checklist.ViewHue
import de.nicolasmahn.momentum.domain.checklist.WhenAway
import de.nicolasmahn.momentum.domain.measurement.MeasurementConfiguration
import de.nicolasmahn.momentum.domain.measurement.MeasurementEdit
import de.nicolasmahn.momentum.domain.measurement.MeasurementLog
import de.nicolasmahn.momentum.domain.measurement.MeasurementStep
import de.nicolasmahn.momentum.domain.compatibility.WeightMeasurementId
import de.nicolasmahn.momentum.domain.measurement.asMeasurementLogs
import de.nicolasmahn.momentum.domain.measurement.recordsOf
import de.nicolasmahn.momentum.domain.measurement.starterMeasurement
import de.nicolasmahn.momentum.domain.measurement.testMadeMeasurement
import de.nicolasmahn.momentum.domain.measurement.testWeightMeasurement
import de.nicolasmahn.momentum.domain.compatibility.weightMeasurement
import de.nicolasmahn.momentum.domain.measurement.withWeight
import de.nicolasmahn.momentum.domain.measurement.MeasurementRecord
import de.nicolasmahn.momentum.feature.settings.ViewWrite
import de.nicolasmahn.momentum.feature.measurement.MeasurementDayChange
import de.nicolasmahn.momentum.domain.DayChangeRefusal
import de.nicolasmahn.momentum.domain.DayChangeResult
import de.nicolasmahn.momentum.domain.settings.FeatureVisibilitySettings
import de.nicolasmahn.momentum.domain.LoggedViewKind
import de.nicolasmahn.momentum.domain.LoggedViewRef
import de.nicolasmahn.momentum.domain.ref
import de.nicolasmahn.momentum.domain.withoutLeftoverEntries
import de.nicolasmahn.momentum.persistence.PreferencesFeatureVisibilityRepository
import de.nicolasmahn.momentum.persistence.PreferencesTrackerRepository
import de.nicolasmahn.momentum.persistence.TrackerRepository
import de.nicolasmahn.momentum.persistence.TrackerStringStore
import de.nicolasmahn.momentum.persistence.TrackerUpdateResult
import de.nicolasmahn.momentum.persistence.trackerRepository
import de.nicolasmahn.momentum.reminder.MeasurementReplyCoordinator
import de.nicolasmahn.momentum.reminder.MeasurementReplyResult
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A measurement's page saves its edits to the latest stored configuration, on the habit day the
 * save runs, and its screen one day's number against the records as they are stored. The setup
 * creates a measurement and its page deletes it, each against the store as it is.
 */
class MeasurementCommandsTest {
    private val today = LocalDate.parse("2026-10-14")
    private val edits = listOf(MeasurementEdit.Rename("Scale"), MeasurementEdit.SetBankRules(maxPasses = 3, everyKeptDays = 4), MeasurementEdit.SetWhenAway(WhenAway.Due))

    private class Fixture(state: AppState, day: LocalDate, updates: Boolean = true) {
        val repository = FakeTrackerRepository(state, updates)
        val handedOver = mutableListOf<AppState>()

        val commands = MeasurementCommands(repository, reconcile = { handedOver += it; true }, exactAlarmsAllowed = { true }, refresh = {}, stored = { handedOver += it }, hidden = { emptySet() }, forgetHidden = {}, today = { day })
    }

    @Test fun aSaveAppliesTheEditsOnTheDayItRunsAndHandsTheRecordsOver() {
        val records = listOf(MeasurementRecord("2026-10-13", 1L, 81.4))
        val fixture = Fixture(AppState(measurementLogs = records.asMeasurementLogs("weight")).withWeight(), today)

        assertEquals(ViewWrite.Stored, fixture.commands.save(WeightMeasurementId, edits))

        val saved = requireNotNull(fixture.repository.load().weightMeasurement)
        assertEquals("Scale", saved.label)
        assertEquals(Dated(listOf(Dated.Revision(7), Dated.Revision(3, today))), saved.standardRewards!!.maxPasses)
        assertEquals(Dated(listOf(Dated.Revision(WhenAway.Excused), Dated.Revision(WhenAway.Due, today))), saved.whenAway)
        assertEquals(records, fixture.repository.load().recordsOf("weight"))
        assertEquals(listOf(fixture.repository.load()), fixture.handedOver)
    }

    /** The page's baseline may be older than the store: the edits go over what is stored at that moment. */
    @Test fun theEditsGoOverTheConfigurationStoredAtThatMoment() {
        val storedMeanwhile = testWeightMeasurement().copy(hue = ViewHue.Plum)
        val fixture = Fixture(AppState().withWeight(storedMeanwhile), today.plusDays(1))

        assertEquals(ViewWrite.Stored, fixture.commands.save(WeightMeasurementId, edits))

        val saved = requireNotNull(fixture.repository.load().weightMeasurement)
        assertEquals(ViewHue.Plum, saved.hue)
        assertEquals("Scale", saved.label)
        // The week rule judged every day before the save ran, and none from it on.
        assertEquals(today, (saved.rewards as MeasurementBank.Standard).earlierWeeks!!.until)
    }

    @Test fun aSaveTheStoreRefusesStoresNothingAndSaysSo() {
        val fixture = Fixture(AppState().withWeight(), today, updates = false)
        assertEquals(ViewWrite.NotStored, fixture.commands.save(WeightMeasurementId, edits))
        assertEquals(testWeightMeasurement(), fixture.repository.load().weightMeasurement)
        assertEquals(emptyList<AppState>(), fixture.handedOver)
    }

    /**
     * A made measurement joins the streak, takes its bank's rules and its holiday rule through the
     * same save: over the configuration as it is stored at that moment, dated by the habit day
     * the save runs on, and with every record and every other measurement as it was.
     */
    @Test fun aSaveJoinsAMadeMeasurementOnTheDayItRunsOverWhatIsStoredAtThatMoment() {
        val waist = testMadeMeasurement()
        val records = listOf(MeasurementLog(waist.id, "2026-10-13", 5L, 91.5), MeasurementLog(WeightMeasurementId, "2026-10-13", 1L, 81.4))
        // Renamed meanwhile, behind the page's back, and saved a day after the page was opened.
        val storedMeanwhile = waist.copy(label = "Hips")
        val fixture = Fixture(AppState(measurementLogs = records).withWeight().let { it.copy(measurements = it.measurements + storedMeanwhile) }, today.plusDays(1))
        val streakEdits = listOf(MeasurementEdit.JoinRewards, MeasurementEdit.SetBankRules(maxPasses = 4, everyKeptDays = 5), MeasurementEdit.SetWhenAway(WhenAway.Excused))

        assertEquals(ViewWrite.Stored, fixture.commands.save(waist.id, streakEdits))

        val saved = fixture.repository.load().measurements.single { it.id == waist.id }
        assertEquals("Hips", saved.label)
        assertEquals(ChecklistRewards(maxPasses = 4, everyKeptDays = 5, periods = listOf(RewardPeriod(today.plusDays(1)))), saved.standardRewards)
        assertEquals(Dated(listOf(Dated.Revision(WhenAway.Due), Dated.Revision(WhenAway.Excused, today.plusDays(1)))), saved.whenAway)
        assertEquals(storedMeanwhile.checkpoint, saved.checkpoint)
        assertEquals(testWeightMeasurement(), fixture.repository.load().weightMeasurement)
        assertEquals(records, fixture.repository.load().measurementLogs)
        assertEquals(listOf(fixture.repository.load()), fixture.handedOver)

        // Leaving a week later closes the period at the day before that save, and the rule stays.
        val later = Fixture(fixture.repository.load(), today.plusDays(8))
        assertEquals(ViewWrite.Stored, later.commands.save(waist.id, listOf(MeasurementEdit.LeaveRewards)))
        val left = later.repository.load().measurements.single { it.id == waist.id }
        assertEquals(listOf(RewardPeriod(today.plusDays(1), today.plusDays(7))), left.standardRewards?.periods)
        assertEquals(saved.whenAway, left.whenAway)
        assertEquals(records, later.repository.load().measurementLogs)
    }

    /** What a made measurement's save stores is read back from the real store as it was saved: its bank with its period, its rule and its reminder. */
    @Test fun aJoinedMeasurementIsReadBackFromTheStoreAsItWasSaved() {
        val waist = testMadeMeasurement()
        val store = CurrentTrackerStore()
        val repository = trackerRepository(store)
        assertTrue(repository.save(repository.load().withWeight().let { it.copy(measurements = it.measurements + waist) }))
        val commands = MeasurementCommands(repository, reconcile = { true }, exactAlarmsAllowed = { true }, refresh = {}, stored = {}, hidden = { emptySet() }, forgetHidden = {}, today = { today })

        assertEquals(ViewWrite.Stored, commands.save(waist.id, listOf(MeasurementEdit.JoinRewards, MeasurementEdit.SetWhenAway(WhenAway.Excused))))

        val reloaded = trackerRepository(store).load().measurements.single { it.id == waist.id }
        assertEquals(DefaultChecklistRewards.copy(periods = listOf(RewardPeriod(today))), reloaded.standardRewards)
        assertEquals(Dated(listOf(Dated.Revision(WhenAway.Due), Dated.Revision(WhenAway.Excused, today))), reloaded.whenAway)
        assertEquals(waist.checkpoint, reloaded.checkpoint)
        val stored = storedDocument(store).getJSONArray("measurements").getJSONObject(1)
        assertEquals(setOf("view", "id", "label", "icon", "hue", "mo", "unit", "step", "valuePolicy", "trendDays", "checkpoints", "rewards", "whenAway"), stored.keys().asSequence().toSet())
        assertFalse(stored.getJSONObject("mo").getBoolean("enabled"))
        assertEquals("finite", stored.getString("valuePolicy"))
        assertEquals(waist.mo, reloaded.mo)
        assertEquals(waist.valuePolicy, reloaded.valuePolicy)
    }

    /**
     * A changed unit or step is stored with the configuration and converts nothing: through the
     * real store, the records it holds afterwards are written exactly as they were, a number with
     * more digits than the step and one of zero among them, and Weight's are not touched either.
     */
    @Test fun aChangedUnitOrStepRewritesNoStoredNumber() {
        val waist = testMadeMeasurement()
        val records = listOf(
            MeasurementLog(waist.id, "2026-10-13", 5L, 91.456),
            MeasurementLog(WeightMeasurementId, "2026-10-13", 1L, 81.4),
            MeasurementLog(waist.id, "2026-10-12", 2L, 0.0),
            MeasurementLog(waist.id, "2026-10-11", 3L, 87.45),
        )
        val store = CurrentTrackerStore()
        val repository = trackerRepository(store)
        assertTrue(repository.save(repository.load().withWeight().let { it.copy(measurements = it.measurements + waist, measurementLogs = records) }))
        fun storedRecords() = storedDocument(store).getJSONArray("measurementLogs").toString()
        val before = storedRecords()
        val commands = MeasurementCommands(repository, reconcile = { true }, exactAlarmsAllowed = { true }, refresh = {}, stored = {}, hidden = { emptySet() }, forgetHidden = {}, today = { today })

        listOf(
            listOf(MeasurementEdit.SetUnit("in")),
            listOf(MeasurementEdit.SetStep(MeasurementStep("1"))),
            listOf(MeasurementEdit.SetUnit(""), MeasurementEdit.SetStep(MeasurementStep("0.01"))),
        ).forEach { change ->
            assertEquals(ViewWrite.Stored, commands.save(waist.id, change))
            assertEquals(before, storedRecords())
            assertEquals(records, repository.load().measurementLogs)
            assertEquals(testWeightMeasurement(), repository.load().weightMeasurement)
        }
        assertEquals(waist.copy(unit = "", step = MeasurementStep("0.01")), repository.load().measurements.single { it.id == waist.id })
        // Weight's unit and step change the same way: its configuration says them, and no record is rewritten or converted.
        assertEquals(ViewWrite.Stored, commands.save(WeightMeasurementId, listOf(MeasurementEdit.SetUnit("lb"), MeasurementEdit.SetStep(MeasurementStep("0.5")))))
        assertEquals(testWeightMeasurement().copy(unit = "lb", step = MeasurementStep("0.5")), repository.load().weightMeasurement)
        assertEquals(before, storedRecords())
        assertEquals(records, repository.load().measurementLogs)
    }

    /**
     * A draft opens on a day, and before it is saved a notification's reply stores a weight. On the
     * draft's own day the save is refused, so the reply's number stands and the screen is shown it;
     * on another day the save goes through and the reply's record is there beside it. Saved as the
     * whole state the screen was shown, either would have undone the reply.
     */
    @Test fun aNotificationsReplyStoredBeforeASaveIsNotUndone() {
        val yesterdays = MeasurementLog(WeightMeasurementId, "2026-10-13", 5L, 81.4)
        val days = DayFixture(listOf(yesterdays))
        val shown = days.repository.load()

        // The reply answers for today, the day the draft is open on with no record yet.
        assertEquals(MeasurementReplyResult.Saved(82.0), days.reply.submit("82", targetDateKey = "2026-10-14"))
        val replied = MeasurementLog(WeightMeasurementId, "2026-10-14", ReplyTime, 82.0)
        val writes = days.store.commits

        assertEquals(DayChangeResult.Refused(DayChangeRefusal.ChangedElsewhere), days.commands.changeDay(MeasurementDayChange.Save(WeightMeasurementId, "2026-10-14", opened = null, value = 83.0)))
        assertEquals(listOf(replied, yesterdays), days.repository.load().measurementLogs)
        assertEquals("A refused change writes nothing", writes, days.store.commits)
        // The screen is handed what is stored, so the reply's number is in sight.
        assertEquals(listOf(replied, yesterdays), days.handedOver.single().measurementLogs)

        // The reply answers for yesterday while today's draft is open: today is saved, and yesterday's reply stays.
        val otherDay = DayFixture(listOf(yesterdays))
        assertEquals(MeasurementReplyResult.Saved(80.9), otherDay.reply.submit("80,9 kg", targetDateKey = "2026-10-13"))
        val stored = otherDay.commands.changeDay(MeasurementDayChange.Save(WeightMeasurementId, "2026-10-14", opened = null, value = 83.0))
        val expected = listOf(MeasurementLog(WeightMeasurementId, "2026-10-14", SaveTime, 83.0), MeasurementLog(WeightMeasurementId, "2026-10-13", ReplyTime, 80.9))
        assertEquals(expected, otherDay.repository.load().measurementLogs)
        assertEquals(expected, (stored as DayChangeResult.Stored).after.measurementLogs)
        assertEquals(listOf(MeasurementLog(WeightMeasurementId, "2026-10-13", ReplyTime, 80.9)), stored.before.measurementLogs)
        assertEquals(listOf(stored.after), otherDay.handedOver)
        // What the screen was shown is what a whole-state save would have written back: yesterday without the reply.
        assertEquals(listOf(yesterdays), shown.measurementLogs)
    }

    /** The same for a removal: a day whose record a reply replaced is not removed, and a reply for another day survives a removal. */
    @Test fun aNotificationsReplyStoredBeforeARemovalIsNotUndone() {
        val opened = MeasurementLog(WeightMeasurementId, "2026-10-14", 5L, 80.0)
        val yesterdays = MeasurementLog(WeightMeasurementId, "2026-10-13", 4L, 81.4)
        val days = DayFixture(listOf(opened, yesterdays))

        assertEquals(MeasurementReplyResult.Saved(82.0), days.reply.submit("82", targetDateKey = "2026-10-14"))
        val replied = MeasurementLog(WeightMeasurementId, "2026-10-14", ReplyTime, 82.0)
        val writes = days.store.commits
        assertEquals(DayChangeResult.Refused(DayChangeRefusal.ChangedElsewhere), days.commands.changeDay(MeasurementDayChange.Remove(WeightMeasurementId, "2026-10-14", opened = MeasurementRecord("2026-10-14", 5L, 80.0))))
        assertEquals(listOf(replied, yesterdays), days.repository.load().measurementLogs)
        assertEquals(writes, days.store.commits)

        val otherDay = DayFixture(listOf(opened, yesterdays))
        assertEquals(MeasurementReplyResult.Saved(80.9), otherDay.reply.submit("80.9", targetDateKey = "2026-10-13"))
        assertTrue(otherDay.commands.changeDay(MeasurementDayChange.Remove(WeightMeasurementId, "2026-10-14", opened = MeasurementRecord("2026-10-14", 5L, 80.0))) is DayChangeResult.Stored)
        assertEquals(listOf(MeasurementLog(WeightMeasurementId, "2026-10-13", ReplyTime, 80.9)), otherDay.repository.load().measurementLogs)

        // A record that is the one the draft opened on is replaced and removed as asked.
        val untouched = DayFixture(listOf(opened, yesterdays))
        assertTrue(untouched.commands.changeDay(MeasurementDayChange.Save(WeightMeasurementId, "2026-10-14", opened = MeasurementRecord("2026-10-14", 5L, 80.0), value = 79.5)) is DayChangeResult.Stored)
        assertEquals(listOf(MeasurementLog(WeightMeasurementId, "2026-10-14", SaveTime, 79.5), yesterdays), untouched.repository.load().measurementLogs)
        assertTrue(untouched.commands.changeDay(MeasurementDayChange.Remove(WeightMeasurementId, "2026-10-14", opened = MeasurementRecord("2026-10-14", SaveTime, 79.5))) is DayChangeResult.Stored)
        assertEquals(listOf(yesterdays), untouched.repository.load().measurementLogs)
    }

    /**
     * The day's record is chosen as the screen chooses it among several: the latest time, the first
     * in the list winning a tie. A change is made when that one is the record the draft opened on,
     * and it then replaces or removes every record the measurement has of that day.
     */
    @Test fun theDaysRecordIsTheOneTheScreenChoosesAmongSeveral() {
        val several = listOf(
            MeasurementLog(WeightMeasurementId, "2026-10-14", 5L, 70.0),
            MeasurementLog(WeightMeasurementId, "2026-10-14", 9L, 82.0),
            MeasurementLog(WeightMeasurementId, "2026-10-14", 9L, 85.0),
        )
        listOf(MeasurementRecord("2026-10-14", 5L, 70.0), MeasurementRecord("2026-10-14", 9L, 85.0), null, MeasurementRecord("2026-10-14", 9L, 82.5), MeasurementRecord("2026-10-13", 9L, 82.0)).forEach { notTheDays ->
            val days = DayFixture(several)
            assertEquals("$notTheDays", DayChangeResult.Refused(DayChangeRefusal.ChangedElsewhere), days.commands.changeDay(MeasurementDayChange.Save(WeightMeasurementId, "2026-10-14", notTheDays, 83.0)))
            assertEquals("$notTheDays", DayChangeResult.Refused(DayChangeRefusal.ChangedElsewhere), days.commands.changeDay(MeasurementDayChange.Remove(WeightMeasurementId, "2026-10-14", notTheDays)))
            assertEquals(several, days.repository.load().measurementLogs)
        }
        val days = DayFixture(several)
        assertTrue(days.commands.changeDay(MeasurementDayChange.Save(WeightMeasurementId, "2026-10-14", MeasurementRecord("2026-10-14", 9L, 82.0), 83.0)) is DayChangeResult.Stored)
        assertEquals(listOf(MeasurementLog(WeightMeasurementId, "2026-10-14", SaveTime, 83.0)), days.repository.load().measurementLogs)
    }

    /**
     * A measurement a person made is saved and removed the same way, and only its own day is
     * touched: Weight's records of the day, another made measurement's and those of a measurement
     * nothing knows, two alike among them, stay exactly as they were. Weight's save leaves theirs.
     */
    @Test fun aDayOfOneMeasurementIsChangedAndNothingElse() {
        val waist = testMadeMeasurement()
        val mood = testMadeMeasurement(id = "msr_m7xd", label = "Mood", unit = "", step = "1")
        val others = listOf(
            MeasurementLog(WeightMeasurementId, "2026-10-14", 9L, 82.0),
            MeasurementLog(mood.id, "2026-10-14", 9L, 7.0),
            MeasurementLog("msr_gone", "2026-10-14", 99L, 3.0),
            MeasurementLog("msr_gone", "2026-10-14", 99L, 3.0),
            MeasurementLog(mood.id, "2026-10-13", 2L, 6.0),
        )
        val days = DayFixture(others.take(2) + MeasurementLog(waist.id, "2026-10-14", 4L, 92.0) + others.drop(2) + MeasurementLog(waist.id, "2026-10-13", 3L, 91.5), listOf(waist, mood))
        fun othersNow() = days.repository.load().measurementLogs.filter { it.measurementId != waist.id }

        assertTrue(days.commands.changeDay(MeasurementDayChange.Save(waist.id, "2026-10-14", MeasurementRecord("2026-10-14", 4L, 92.0), 92.456)) is DayChangeResult.Stored)
        assertEquals(others, othersNow())
        assertEquals(listOf(MeasurementRecord("2026-10-14", SaveTime, 92.456), MeasurementRecord("2026-10-13", 3L, 91.5)), days.repository.load().recordsOf(waist.id))
        // A first record of a day, where the draft opened on none.
        assertTrue(days.commands.changeDay(MeasurementDayChange.Save(waist.id, "2026-10-12", opened = null, value = 91.0)) is DayChangeResult.Stored)
        assertEquals(others, othersNow())
        assertTrue(days.commands.changeDay(MeasurementDayChange.Remove(waist.id, "2026-10-14", MeasurementRecord("2026-10-14", SaveTime, 92.456))) is DayChangeResult.Stored)
        assertEquals(others, othersNow())
        assertEquals(listOf("2026-10-13", "2026-10-12"), days.repository.load().recordsOf(waist.id).map { it.dateKey })
        assertEquals(listOf(testWeightMeasurement(), waist, mood), days.repository.load().measurements)

        val waists = days.repository.load().recordsOf(waist.id)
        assertTrue(days.commands.changeDay(MeasurementDayChange.Save(WeightMeasurementId, "2026-10-14", MeasurementRecord("2026-10-14", 9L, 82.0), 81.5)) is DayChangeResult.Stored)
        assertEquals(waists, days.repository.load().recordsOf(waist.id))
        assertEquals(listOf(MeasurementRecord("2026-10-14", 9L, 7.0), MeasurementRecord("2026-10-13", 2L, 6.0)), days.repository.load().recordsOf(mood.id))
        assertEquals(listOf(MeasurementRecord("2026-10-14", SaveTime, 81.5)), days.repository.load().recordsOf("weight"))
    }

    /**
     * A measurement deleted while its screen was open: a save and a removal store nothing and say
     * so, whether its records went with it or are still there, and what is stored is byte for byte
     * what it was. Weight is refused the same way once it is deleted, a notification's reply for
     * it too: there is no measurement to save the number for, and nothing is written.
     */
    @Test fun aChangeForAMeasurementDeletedMeanwhileStoresNothingAndSaysSo() {
        val waist = testMadeMeasurement()
        val waists = MeasurementLog(waist.id, "2026-10-14", 4L, 92.0)
        val weights = MeasurementLog(WeightMeasurementId, "2026-10-14", 9L, 82.0)
        listOf(listOf(weights), listOf(waists, weights)).forEach { leftBehind ->
            val days = DayFixture(listOf(waists, weights), listOf(waist))
            assertTrue(days.repository.update { it.copy(measurements = it.measurements.filterNot { made -> made.id == waist.id }, measurementLogs = leftBehind) } is TrackerUpdateResult.Updated)
            val kept = days.store.everything.toMap()
            val writes = days.store.commits

            listOf(
                MeasurementDayChange.Save(waist.id, "2026-10-14", MeasurementRecord("2026-10-14", 4L, 92.0), 93.0),
                MeasurementDayChange.Save(waist.id, "2026-10-15", opened = null, value = 93.0),
                MeasurementDayChange.Remove(waist.id, "2026-10-14", MeasurementRecord("2026-10-14", 4L, 92.0)),
            ).forEach { change ->
                assertEquals(change.toString(), DayChangeResult.Refused(DayChangeRefusal.Deleted), days.commands.changeDay(change))
                assertEquals(change.toString(), kept, days.store.everything.toMap())
                assertEquals(change.toString(), writes, days.store.commits)
            }
            assertEquals(leftBehind, days.repository.load().measurementLogs)
            // Weight is still saved beside what is left.
            assertTrue(days.commands.changeDay(MeasurementDayChange.Save(WeightMeasurementId, "2026-10-14", MeasurementRecord("2026-10-14", 9L, 82.0), 81.5)) is DayChangeResult.Stored)
        }
        // A measurement that never was is refused the same way.
        val never = DayFixture(listOf(weights))
        assertEquals(DayChangeResult.Refused(DayChangeRefusal.Deleted), never.commands.changeDay(MeasurementDayChange.Save("msr_zzzz", "2026-10-14", opened = null, value = 1.0)))
        assertEquals(listOf(weights), never.repository.load().measurementLogs)

        // Weight deleted while its screen was open, or while its reminder waited for a reply.
        val withoutWeight = DayFixture(listOf(weights))
        assertEquals(MeasurementReplyResult.Saved(82.5), withoutWeight.reply.submit("82.5", targetDateKey = "2026-10-13"))
        assertTrue(withoutWeight.repository.update { it.copy(measurements = emptyList()) } is TrackerUpdateResult.Updated)
        val keptWithout = withoutWeight.store.everything.toMap()
        val writesWithout = withoutWeight.store.commits
        assertEquals(DayChangeResult.Refused(DayChangeRefusal.Deleted), withoutWeight.commands.changeDay(MeasurementDayChange.Save(WeightMeasurementId, "2026-10-14", MeasurementRecord("2026-10-14", 9L, 82.0), 81.5)))
        assertEquals(DayChangeResult.Refused(DayChangeRefusal.Deleted), withoutWeight.commands.changeDay(MeasurementDayChange.Remove(WeightMeasurementId, "2026-10-14", MeasurementRecord("2026-10-14", 9L, 82.0))))
        assertEquals(MeasurementReplyResult.Deleted, withoutWeight.reply.submit("81.5", targetDateKey = "2026-10-14"))
        assertEquals(keptWithout, withoutWeight.store.everything.toMap())
        assertEquals(writesWithout, withoutWeight.store.commits)
        assertEquals(listOf(weights, MeasurementLog(WeightMeasurementId, "2026-10-13", ReplyTime, 82.5)), withoutWeight.repository.load().measurementLogs)
    }

    /** A store that cannot write is a failure, not a refusal: nothing is handed over and nothing changed. */
    @Test fun aDayTheStoreCannotWriteFailsAndIsNotHandedOver() {
        val fixture = Fixture(AppState(measurementLogs = listOf(MeasurementLog(WeightMeasurementId, "2026-10-13", 1L, 81.4))).withWeight(), today, updates = false)
        assertEquals(DayChangeResult.Failed, fixture.commands.changeDay(MeasurementDayChange.Save(WeightMeasurementId, "2026-10-14", opened = null, value = 83.0)))
        assertEquals(DayChangeResult.Failed, fixture.commands.changeDay(MeasurementDayChange.Remove(WeightMeasurementId, "2026-10-13", MeasurementRecord("2026-10-13", 1L, 81.4))))
        assertEquals(emptyList<AppState>(), fixture.handedOver)
        assertEquals(listOf(MeasurementRecord("2026-10-13", 1L, 81.4)), fixture.repository.load().recordsOf("weight"))
    }

    /**
     * Create stores the setup's measurement, its starter with the edits made to it, and nothing
     * else: every record and every other configuration is written as it was.
     */
    @Test fun createStoresTheConfigurationAndNothingElse() {
        val mood = testMadeMeasurement(id = "msr_m7xd", label = "Mood", unit = "", step = "1")
        val records = listOf(MeasurementLog(WeightMeasurementId, "2026-10-14", 9L, 82.0), MeasurementLog(mood.id, "2026-10-14", 9L, 7.0))
        val days = DayFixture(records, listOf(mood))
        val starter = days.repository.load().starterMeasurement("Measurement")
        val before = storedDocument(days.store)
        val otherKeys = days.store.everything.filterKeys { !it.startsWith("row:measurements/") }

        assertEquals(ViewWrite.Stored, days.commands.create(starter, listOf(MeasurementEdit.Rename("Waist"), MeasurementEdit.SetUnit("cm"), MeasurementEdit.SetStep(MeasurementStep("1")), MeasurementEdit.SetHue(ViewHue.Plum))))

        val created = starter.copy(label = "Waist", unit = "cm", step = MeasurementStep("1"), hue = ViewHue.Plum)
        assertEquals(listOf(testWeightMeasurement(), mood, created), days.repository.load().measurements)
        assertEquals(records, days.repository.load().measurementLogs)
        assertEquals(listOf(days.repository.load()), days.handedOver)
        val after = storedDocument(days.store)
        assertEquals(3, after.getJSONArray("measurements").length())
        // Nothing but the list of configurations differs in what is stored.
        before.remove("measurements")
        after.remove("measurements")
        assertEquals(before.toString(), after.toString())
        assertEquals(otherKeys, days.store.everything.filterKeys { !it.startsWith("row:measurements/") })
    }

    /**
     * The setup's ID was free when it opened. If a configuration made meanwhile, or a record of a
     * measurement nothing knows, names it by the time Create is pressed, nothing is stored: the
     * new measurement is never given another's numbers, and never a second ID.
     */
    @Test fun anIdNamedSinceTheSetupOpenedIsRefusedAndNothingIsWritten() {
        val orphan = MeasurementLog("msr_k2p9", "2026-10-13", 1L, 3.0)
        listOf<(AppState) -> AppState>(
            { it.copy(measurements = it.measurements + testMadeMeasurement(label = "Made meanwhile")) },
            { it.copy(measurementLogs = it.measurementLogs + orphan) },
        ).forEach { meanwhile ->
            val days = DayFixture(listOf(MeasurementLog(WeightMeasurementId, "2026-10-14", 9L, 82.0)))
            val starter = testMadeMeasurement()
            assertTrue(days.repository.update(meanwhile) is TrackerUpdateResult.Updated)
            val kept = days.store.everything.toMap()
            val writes = days.store.commits

            assertEquals(ViewWrite.NotStored, days.commands.create(starter, listOf(MeasurementEdit.Rename("Hips"))))

            assertEquals(kept, days.store.everything.toMap())
            assertEquals(writes, days.store.commits)
            assertEquals(emptyList<AppState>(), days.handedOver)
        }
    }

    /** A store that could not write leaves the setup's measurement to be created by the next press; a press after that creates no second one. */
    @Test fun aFailedCreateCanBeRetriedAndTwoPressesCreateOne() {
        val days = DayFixture(emptyList())
        val starter = testMadeMeasurement()
        val edits = listOf(MeasurementEdit.Rename("Hips"))

        days.store.writable = false
        assertEquals(ViewWrite.NotStored, days.commands.create(starter, edits))
        assertEquals(listOf(testWeightMeasurement()), days.repository.load().measurements)

        days.store.writable = true
        assertEquals(ViewWrite.Stored, days.commands.create(starter, edits))
        assertEquals(ViewWrite.NotStored, days.commands.create(starter, edits))
        assertEquals(listOf(testWeightMeasurement(), starter.copy(label = "Hips")), days.repository.load().measurements)
        assertEquals(1, days.handedOver.size)
    }

    /**
     * Deleting is one update of the store: the configuration and every record that names it, two
     * of one day and one after today among them, are gone together, and every other record and
     * configuration is as it was.
     */
    @Test fun deletingRemovesTheConfigurationAndEveryRecordOfItInOneUpdate() {
        val waist = testMadeMeasurement()
        val mood = testMadeMeasurement(id = "msr_m7xd", label = "Mood", unit = "", step = "1")
        val others = listOf(
            MeasurementLog(WeightMeasurementId, "2026-10-14", 9L, 82.0),
            MeasurementLog(mood.id, "2026-10-14", 9L, 7.0),
            MeasurementLog("msr_gone", "2026-10-14", 99L, 3.0),
            MeasurementLog(WeightMeasurementId, "2026-10-13", 8L, 81.4),
        )
        val waists = listOf(
            MeasurementLog(waist.id, "2026-12-24", 6L, 90.0),
            MeasurementLog(waist.id, "2026-10-14", 4L, 92.0),
            MeasurementLog(waist.id, "2026-10-14", 5L, 92.5),
            MeasurementLog(waist.id, "2026-10-12", 3L, 91.0),
        )
        val days = DayFixture(listOf(waists[0], others[0], waists[1], others[1], waists[2], others[2], others[3], waists[3]), listOf(waist, mood))
        val updates = days.store.commits
        days.repository.update { it }
        val writesOfOneUpdate = days.store.commits - updates
        val writes = days.store.commits

        assertTrue(days.commands.delete(waist.id))

        assertEquals(writesOfOneUpdate, days.store.commits - writes)
        assertEquals(listOf(testWeightMeasurement(), mood), days.repository.load().measurements)
        assertEquals(others, days.repository.load().measurementLogs)
        assertEquals(listOf(days.repository.load()), days.handedOver)
        // What is stored names the deleted measurement nowhere.
        assertFalse(waist.id in storedDocument(days.store).toString())

        // Its screen, still open, is told so; a second delete finds nothing to delete.
        assertEquals(DayChangeResult.Refused(DayChangeRefusal.Deleted), days.commands.changeDay(MeasurementDayChange.Save(waist.id, "2026-10-14", opened = null, value = 93.0)))
        assertFalse(days.commands.delete(waist.id))
    }

    /** Weight is deleted like any other measurement: its configuration and every record of its own go in one update, and its screen is told so. */
    @Test fun weightIsDeletedWithItsRecordsLikeAnyOtherMeasurement() {
        val waist = testMadeMeasurement()
        val records = listOf(MeasurementLog(waist.id, "2026-10-14", 4L, 92.0), MeasurementLog(WeightMeasurementId, "2026-10-14", 9L, 82.0), MeasurementLog(WeightMeasurementId, "2026-10-13", 8L, 82.4))
        val days = DayFixture(records, listOf(waist))

        assertTrue(days.commands.delete(WeightMeasurementId))

        assertEquals(listOf(waist), days.repository.load().measurements)
        assertEquals(listOf(records[0]), days.repository.load().measurementLogs)
        assertEquals(listOf(days.repository.load()), days.handedOver)
        assertEquals(DayChangeResult.Refused(DayChangeRefusal.Deleted), days.commands.changeDay(MeasurementDayChange.Save(WeightMeasurementId, "2026-10-14", opened = null, value = 81.0)))
        assertFalse(days.commands.delete(WeightMeasurementId))
    }

    /** A delete the store cannot write leaves the configuration and every record as they were. */
    @Test fun aDeleteThatFailsLeavesEverythingAsItWas() {
        val waist = testMadeMeasurement()
        val records = listOf(MeasurementLog(waist.id, "2026-10-14", 4L, 92.0), MeasurementLog(WeightMeasurementId, "2026-10-14", 9L, 82.0))
        val days = DayFixture(records, listOf(waist))
        val kept = days.store.everything.toMap()

        days.store.writable = false
        assertFalse(days.commands.delete(waist.id))
        days.store.writable = true

        assertEquals(kept, days.store.everything.toMap())
        assertEquals(emptyList<AppState>(), days.handedOver)
        assertEquals(listOf(testWeightMeasurement(), waist), days.repository.load().measurements)
        assertEquals(records, days.repository.load().measurementLogs)
    }

    /**
     * A write stands in three cases: its alarms were set, the phone does not allow exact alarms,
     * or it asked for no new alarm. It is so for a Save and for a Create.
     */
    @Test fun aWriteStandsWithItsAlarmSetWithoutAccessOrWithNoNewAlarmAsked() {
        val waist = testMadeMeasurement()
        val switchOn = listOf(MeasurementEdit.SetRemind(true))

        val alarmsSet = SettleFixture(listOf(waist))
        assertEquals(ViewWrite.Stored, alarmsSet.commands.save(waist.id, switchOn))
        assertEquals(true, alarmsSet.made(waist.id)?.checkpoint?.remind)
        assertEquals(listOf(alarmsSet.repository.load()), alarmsSet.reconciled)

        val withoutAccess = SettleFixture(listOf(waist), alarmsSet = false, exactAllowed = false)
        assertEquals(ViewWrite.Stored, withoutAccess.commands.save(waist.id, switchOn))
        assertEquals(true, withoutAccess.made(waist.id)?.checkpoint?.remind)
        assertEquals(ViewWrite.Stored, withoutAccess.commands.create(testMadeMeasurement(id = "msr_new1"), switchOn))
        assertEquals(true, withoutAccess.made("msr_new1")?.checkpoint?.remind)

        // No new alarm: a name, the streak, the reminder switched off, or the time it already has.
        val reminding = waist.copy(checkpoint = waist.checkpoint.copy(remind = true))
        val noNewAlarm = SettleFixture(listOf(reminding), alarmsSet = false)
        listOf(
            listOf(MeasurementEdit.Rename("Hips"), MeasurementEdit.JoinRewards),
            listOf(MeasurementEdit.SetReminderTime(CheckpointTime.Fixed(LocalTime.of(9, 0)))),
            listOf(MeasurementEdit.SetRemind(false)),
            listOf(MeasurementEdit.SetReminderTime(CheckpointTime.AfterWake(15))),
        ).forEach { edits -> assertEquals(edits.toString(), ViewWrite.Stored, noNewAlarm.commands.save(waist.id, edits)) }
        assertEquals(Checkpoint("day", CheckpointTime.AfterWake(15), remind = false), noNewAlarm.made(waist.id)?.checkpoint)
        assertEquals("Hips", noNewAlarm.made(waist.id)?.label)
        assertEquals(ViewWrite.Stored, noNewAlarm.commands.create(testMadeMeasurement(id = "msr_new1"), listOf(MeasurementEdit.Rename("Mood"))))
        assertEquals(0, noNewAlarm.refreshed)
    }

    /**
     * With access granted and the alarm not set, a Save that asked for one is taken back: what is
     * stored is byte for byte what it was, every record and every other configuration with it,
     * and the alarms are set again for that.
     */
    @Test fun aSaveWhoseNewAlarmCannotBeSetIsTakenBackWithEverythingAsItWas() {
        val waist = testMadeMeasurement()
        val reminding = testMadeMeasurement(id = "msr_m7xd", label = "Mood", unit = "", step = "1").let { it.copy(checkpoint = it.checkpoint.copy(remind = true)) }
        val records = listOf(MeasurementLog(waist.id, "2026-10-13", 5L, 91.5), MeasurementLog(WeightMeasurementId, "2026-10-13", 1L, 81.4))
        val fixture = SettleFixture(listOf(waist, reminding), records, alarmsSet = false)
        val kept = fixture.store.everything.toMap()

        // Newly reminding, with everything else the page changed; and an existing reminder moved.
        assertEquals(ViewWrite.NotStored, fixture.commands.save(waist.id, listOf(MeasurementEdit.Rename("Hips"), MeasurementEdit.JoinRewards, MeasurementEdit.SetRemind(true))))
        assertEquals(ViewWrite.NotStored, fixture.commands.save(reminding.id, listOf(MeasurementEdit.SetReminderTime(CheckpointTime.Fixed(LocalTime.of(21, 30))))))

        assertEquals(kept, fixture.store.everything.toMap())
        assertEquals(listOf(testWeightMeasurement(), waist, reminding), fixture.repository.load().measurements)
        assertEquals(records, fixture.repository.load().measurementLogs)
        // Each save was shown with its change, then without it.
        assertEquals(listOf("Hips", "Waist", "Waist", "Waist"), fixture.reconciled.map { state -> state.measurements.single { it.id == waist.id }.label })
        assertEquals(listOf("09:00", "09:00", "21:30", "09:00"), fixture.reconciled.map { state -> state.measurements.single { it.id == reminding.id }.checkpoint?.at.toString() })
        assertEquals(0, fixture.refreshed)
    }

    /**
     * A Create taken back leaves no configuration, no record and no alarm: the store is byte for
     * byte what it was and the alarms are set for it. The setup keeps its ID, and trying again
     * creates the measurement once.
     */
    @Test fun aCreateWhoseAlarmCannotBeSetLeavesNothingAndCanBeTriedAgainUnderItsId() {
        val mood = testMadeMeasurement(id = "msr_m7xd", label = "Mood", unit = "", step = "1")
        val records = listOf(MeasurementLog(mood.id, "2026-10-13", 5L, 7.0), MeasurementLog(WeightMeasurementId, "2026-10-13", 1L, 81.4))
        val fixture = SettleFixture(listOf(mood), records, alarmsSet = false)
        val kept = fixture.store.everything.toMap()
        val starter = fixture.repository.load().starterMeasurement("Measurement")
        val edits = listOf(MeasurementEdit.Rename("Waist"), MeasurementEdit.SetUnit("cm"), MeasurementEdit.SetRemind(true), MeasurementEdit.JoinRewards)

        assertEquals(ViewWrite.NotStored, fixture.commands.create(starter, edits))

        assertEquals(kept, fixture.store.everything.toMap())
        assertEquals(listOf(testWeightMeasurement(), mood), fixture.repository.load().measurements)
        assertEquals(records, fixture.repository.load().measurementLogs)
        // The alarms were set last for the records without it, which cancels the one it asked for.
        assertEquals(listOf(listOf(WeightMeasurementId, mood.id, starter.id), listOf(WeightMeasurementId, mood.id)), fixture.reconciled.map { state -> state.measurements.map { it.id } })

        fixture.alarmsSet = true
        assertEquals(ViewWrite.Stored, fixture.commands.create(starter, edits))
        val created = requireNotNull(fixture.made(starter.id))
        assertEquals("Waist", created.label)
        assertEquals(true, created.checkpoint?.remind)
        assertEquals(listOf(RewardPeriod(today)), created.standardRewards?.periods)
        assertEquals(listOf(WeightMeasurementId, mood.id, starter.id), fixture.repository.load().measurements.map { it.id })
        assertEquals(records, fixture.repository.load().measurementLogs)
    }

    /**
     * Where the store cannot write the take-back, the write is still there, and the answer says
     * so: it is stored, without its alarm. It never says nothing was stored, and the screen is
     * shown what the store holds.
     */
    @Test fun aTakeBackTheStoreCannotWriteSaysWhatIsActuallyStored() {
        val waist = testMadeMeasurement()
        val saving = SettleFixture(listOf(waist), alarmsSet = false)
        saving.afterAlarms = { saving.store.writable = false }
        assertEquals(ViewWrite.StoredWithoutAlarm, saving.commands.save(waist.id, listOf(MeasurementEdit.Rename("Hips"), MeasurementEdit.SetRemind(true))))
        assertEquals(waist.copy(label = "Hips", checkpoint = waist.checkpoint.copy(remind = true)), saving.made(waist.id))
        assertEquals(1, saving.refreshed)
        assertEquals(1, saving.reconciled.size)

        val creating = SettleFixture(emptyList(), alarmsSet = false)
        creating.afterAlarms = { creating.store.writable = false }
        val starter = testMadeMeasurement()
        assertEquals(ViewWrite.StoredWithoutAlarm, creating.commands.create(starter, listOf(MeasurementEdit.SetRemind(true))))
        assertEquals(starter.copy(checkpoint = starter.checkpoint.copy(remind = true)), creating.made(starter.id))
        assertEquals(1, creating.refreshed)
        // Pressed again it stores nothing more: the setup, which knows it created, does not ask again.
        creating.store.writable = true
        assertEquals(ViewWrite.NotStored, creating.commands.create(starter, listOf(MeasurementEdit.SetRemind(true))))
        assertEquals(listOf(WeightMeasurementId, starter.id), creating.repository.load().measurements.map { it.id })
    }

    /**
     * Where the records changed between the write and its take-back, taking back no longer applies
     * cleanly: a Save's change stays beside what was stored meanwhile, and so does a creation
     * whose configuration is no longer the one created. Each says it is stored without its alarm,
     * and the alarms are set for what is stored. A record stored meanwhile is no obstacle to
     * taking a creation back, and stays.
     */
    @Test fun aTakeBackThatNoLongerAppliesCleanlySaysWhatIsActuallyStored() {
        val waist = testMadeMeasurement()
        val weighed = MeasurementLog(WeightMeasurementId, "2026-10-14", 9L, 82.0)

        val saving = SettleFixture(listOf(waist), alarmsSet = false)
        saving.afterAlarms = { saving.afterAlarms = {}; saving.repository.update { it.copy(measurementLogs = it.measurementLogs + weighed) } }
        assertEquals(ViewWrite.StoredWithoutAlarm, saving.commands.save(waist.id, listOf(MeasurementEdit.SetRemind(true))))
        assertEquals(true, saving.made(waist.id)?.checkpoint?.remind)
        assertEquals(listOf(weighed), saving.repository.load().measurementLogs)
        assertEquals(saving.repository.load(), saving.reconciled.last())

        val renamed = SettleFixture(emptyList(), alarmsSet = false)
        renamed.afterAlarms = {
            renamed.afterAlarms = {}
            renamed.repository.update { state -> state.copy(measurements = state.measurements.map { if (it.id == waist.id) it.copy(label = "Hips") else it }) }
        }
        assertEquals(ViewWrite.StoredWithoutAlarm, renamed.commands.create(waist, listOf(MeasurementEdit.SetRemind(true))))
        assertEquals("Hips", renamed.made(waist.id)?.label)

        val recorded = SettleFixture(emptyList(), alarmsSet = false)
        recorded.afterAlarms = { recorded.afterAlarms = {}; recorded.repository.update { it.copy(measurementLogs = it.measurementLogs + weighed) } }
        assertEquals(ViewWrite.NotStored, recorded.commands.create(waist, listOf(MeasurementEdit.SetRemind(true))))
        assertEquals(listOf(testWeightMeasurement()), recorded.repository.load().measurements)
        assertEquals(listOf(weighed), recorded.repository.load().measurementLogs)
    }

    /** Deleting hands the records over to have their alarms set, which cancels the deleted measurement's own. */
    @Test fun deletingSetsTheAlarmsForWhatIsLeft() {
        val waist = testMadeMeasurement().let { it.copy(checkpoint = it.checkpoint.copy(remind = true)) }
        val fixture = SettleFixture(listOf(waist), alarmsSet = false)
        assertTrue(fixture.commands.delete(waist.id))
        assertEquals(listOf(listOf(WeightMeasurementId)), fixture.reconciled.map { state -> state.measurements.map { it.id } })
    }

    /**
     * Create reads the hidden entries as they are stored at that moment. An ID one of them names
     * is taken: the creation is refused and nothing is written, so no measurement starts out
     * hidden under an entry a deleted one left behind.
     */
    @Test fun aCreateUnderAnIdAHiddenEntryNamesIsRefusedAndWritesNothing() {
        val fixture = SettleFixture(emptyList())
        val starter = testMadeMeasurement()
        // Hidden after the setup opened, as far as the setup knows.
        assertTrue(fixture.visibility.save(FeatureVisibilitySettings(hiddenChecklists = setOf("msr_free"), hiddenMeasurements = setOf(starter.id))))
        val kept = fixture.store.everything.toMap()
        val writes = fixture.store.commits

        assertEquals(ViewWrite.NotStored, fixture.commands.create(starter, listOf(MeasurementEdit.Rename("Hips"))))

        assertEquals(kept, fixture.store.everything.toMap())
        assertEquals(writes, fixture.store.commits)
        assertEquals(emptyList<AppState>(), fixture.reconciled)
        // An ID only a hidden checklist names is free for a measurement.
        assertEquals(ViewWrite.Stored, fixture.commands.create(testMadeMeasurement(id = "msr_free"), emptyList()))
    }

    /**
     * Deleting drops the measurement's hidden entry in a second write, the visibility settings
     * being another file. Where that write fails the measurement is deleted all the same. The
     * entry left behind hides nothing, its ID is given to no new measurement, and it is dropped
     * by the next save of the visibility settings that has the configurations at hand.
     */
    @Test fun deletingDropsTheHiddenEntryAndAFailedSecondWriteLeavesOneThatHidesNothing() {
        val waist = testMadeMeasurement()
        val mood = testMadeMeasurement(id = "msr_m7xd", label = "Mood", unit = "", step = "1")
        val bothHidden = FeatureVisibilitySettings(hiddenChecklists = setOf(waist.id), hiddenMeasurements = setOf(waist.id, mood.id))

        val fixture = SettleFixture(listOf(waist, mood))
        assertTrue(fixture.visibility.save(bothHidden))
        assertTrue(fixture.commands.delete(waist.id))
        assertEquals(listOf(waist.id), fixture.forgotten)
        // Its own entry is gone; the other measurement's and the checklist's of that ID are as they were.
        assertEquals(bothHidden.forgetting(waist.ref), fixture.visibility.load())

        val failing = SettleFixture(listOf(waist, mood))
        assertTrue(failing.visibility.save(bothHidden))
        failing.visibilityStore.writable = false
        assertTrue(failing.commands.delete(waist.id))
        assertEquals(listOf(testWeightMeasurement(), mood), failing.repository.load().measurements)
        assertEquals(bothHidden, failing.visibility.load())
        failing.visibilityStore.writable = true

        // The entry left behind is never inherited: Create under that ID is refused.
        assertEquals(ViewWrite.NotStored, failing.commands.create(waist, emptyList()))
        assertEquals(listOf(testWeightMeasurement(), mood), failing.repository.load().measurements)
        // It hides nothing: a measurement made under another ID is shown.
        val hips = testMadeMeasurement(id = "msr_h1ps", label = "Hips")
        assertEquals(ViewWrite.Stored, failing.commands.create(hips, emptyList()))
        assertTrue(failing.visibility.load().shows(hips.ref))

        // The next save of the visibility settings with the configurations at hand drops it.
        val shown = failing.repository.load().withoutLeftoverEntries(failing.visibility.load().forgetting(mood.ref))
        assertEquals(FeatureVisibilitySettings(), shown)
        assertTrue(failing.visibility.save(shown))
        assertEquals(7, JSONObject(failing.visibilityStore.values.getValue(PreferencesFeatureVisibilityRepository.SETTINGS_KEY)).getInt("version"))
        // And a delete the store refuses drops no entry.
        failing.store.writable = false
        assertFalse(failing.commands.delete(mood.id))
        assertEquals(listOf(waist.id), failing.forgotten)
    }

    /** The real store with the measurements a person made beside Weight, and the alarms answering as the test says. */
    private inner class SettleFixture(
        made: List<MeasurementConfiguration>,
        records: List<MeasurementLog> = emptyList(),
        var alarmsSet: Boolean = true,
        val exactAllowed: Boolean = true,
    ) {
        val store = CurrentTrackerStore()
        val repository = trackerRepository(store)
        val reconciled = mutableListOf<AppState>()
        var refreshed = 0

        /** The visibility settings in their own store, as on the phone, and the entries a delete asked to drop. */
        val visibilityStore = CurrentTrackerStore()
        val visibility = PreferencesFeatureVisibilityRepository(visibilityStore)
        val forgotten = mutableListOf<String>()

        /** What happens while the alarms are being set, before the answer is known. */
        var afterAlarms: () -> Unit = {}
        val commands = MeasurementCommands(
            repository = repository,
            reconcile = { state ->
                reconciled += state
                afterAlarms()
                alarmsSet
            },
            exactAlarmsAllowed = { exactAllowed },
            refresh = { refreshed++ },
            stored = { error("A configuration is shown by setting its alarms.") },
            hidden = { visibility.load().namedIds(LoggedViewKind.Measurement) },
            forgetHidden = { measurementId ->
                forgotten += measurementId
                visibility.save(visibility.load().forgetting(LoggedViewRef(LoggedViewKind.Measurement, measurementId)))
            },
            today = { today },
        )

        init {
            // A new installation tracks nothing: Weight is seeded here, as the tests stand on it.
            assertTrue(repository.save(repository.load().withWeight().let { it.copy(measurements = it.measurements + made, measurementLogs = records) }))
        }

        fun made(measurementId: String): MeasurementConfiguration? = repository.load().measurements.firstOrNull { it.id == measurementId }
    }

    /** The real store with Weight's records as given, the measurements a person made beside Weight, the commands over it and the reply's writer. */
    private class DayFixture(records: List<MeasurementLog>, made: List<MeasurementConfiguration> = emptyList()) {
        val store = CurrentTrackerStore()
        val repository = trackerRepository(store)
        val handedOver = mutableListOf<AppState>()
        val commands = MeasurementCommands(repository, reconcile = { handedOver += it; true }, exactAlarmsAllowed = { true }, refresh = {}, stored = { handedOver += it }, hidden = { emptySet() }, forgetHidden = {}, now = { SaveTime })
        val reply = MeasurementReplyCoordinator(WeightMeasurementId, repository, timestampMillis = { ReplyTime })

        init {
            // A new installation tracks nothing: Weight is seeded here, as the tests stand on it.
            assertTrue(repository.save(repository.load().withWeight().let { it.copy(measurements = it.measurements + made, measurementLogs = records) }))
        }
    }


    private companion object {
        const val SaveTime = 2_000L
        const val ReplyTime = 1_000L
    }

    private class FakeTrackerRepository(private var state: AppState, private val updates: Boolean) : TrackerRepository {
        override fun load(): AppState = state

        override fun save(state: AppState): Boolean = error("A configuration is saved by updating the latest records.")

        override fun update(transform: (AppState) -> AppState): TrackerUpdateResult {
            if (!updates) return TrackerUpdateResult.SaveFailed
            state = transform(state)
            return TrackerUpdateResult.Updated(state)
        }
    }
}
