package de.nicolasmahn.momentum.reminder

import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.core.text.UiText
import de.nicolasmahn.momentum.core.text.joinedWithAnd
import de.nicolasmahn.momentum.core.text.uiText
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.checklist.AboutDay
import de.nicolasmahn.momentum.domain.checklist.ChecklistConfiguration
import de.nicolasmahn.momentum.domain.checklist.ChecklistDay
import de.nicolasmahn.momentum.domain.checklist.ChecklistEdit
import de.nicolasmahn.momentum.domain.checklist.ChecklistRewards
import de.nicolasmahn.momentum.domain.checklist.RewardPeriod
import de.nicolasmahn.momentum.domain.checklist.applyEdit
import de.nicolasmahn.momentum.domain.checklist.ChecklistItem
import de.nicolasmahn.momentum.domain.checklist.Checkpoint
import de.nicolasmahn.momentum.domain.checklist.CheckpointTime
import de.nicolasmahn.momentum.domain.checklist.Dated
import de.nicolasmahn.momentum.domain.checklist.ViewHue
import de.nicolasmahn.momentum.domain.checklist.ViewIcon
import de.nicolasmahn.momentum.domain.checklist.WhenAway
import de.nicolasmahn.momentum.domain.checklist.on
import de.nicolasmahn.momentum.domain.reminder.Bedtime
import de.nicolasmahn.momentum.domain.reminder.ReminderSettings
import de.nicolasmahn.momentum.domain.reminder.ReminderTime
import de.nicolasmahn.momentum.domain.settings.FeatureVisibilitySettings
import de.nicolasmahn.momentum.domain.supplement.supplementsState
import de.nicolasmahn.momentum.persistence.TrackerRepository
import de.nicolasmahn.momentum.persistence.TrackerUpdateResult
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChecklistRemindersTest {
    private val medication = ChecklistConfiguration(
        id = "chk_7f3a",
        label = "Medication",
        icon = ViewIcon.Checklist,
        hue = ViewHue.Sky,
        checkpoints = listOf(Checkpoint("morning", CheckpointTime.AfterWake(30), remind = true)),
        items = listOf(ChecklistItem("itm_levo", "Levothyroxine"), ChecklistItem("itm_ibu", "Ibuprofen 400")),
    )
    private val today = "2026-10-04"

    /** Every shown checkpoint that reminds has a slot, Supplements' among them under the key a checklist's has. */
    @Test fun onlyVisibleCheckpointsThatRemindGetASlotSupplementsToo() {
        val withSupplements = supplementsState(remind = true)
        val state = withSupplements.copy(checklists = withSupplements.checklists + medication)
        val supplements = ReminderSlot(ReminderViewKind.Checklist, "supplements", "midday", CheckpointTime.Fixed(LocalTime.of(12, 30)))

        assertEquals(listOf(supplements, ReminderSlot(ReminderViewKind.Checklist, "chk_7f3a", "morning", CheckpointTime.AfterWake(30))), checklistReminderSlots(state, FeatureVisibilitySettings(), LocalDate.parse(today)))
        assertEquals("supplements/midday", supplements.key)
        assertEquals(listOf(supplements), checklistReminderSlots(state, FeatureVisibilitySettings().hidingChecklist("chk_7f3a"), LocalDate.parse(today)))
        val silent = state.copy(checklists = supplementsState().checklists + medication.copy(checkpoints = medication.checkpoints.map { it.copy(remind = false) }))
        assertEquals(emptyList<ReminderSlot>(), checklistReminderSlots(silent, FeatureVisibilitySettings(), LocalDate.parse(today)))
    }

    /** Alarms and notifications are told apart by the slot key itself, which no two slots share. */
    @Test fun eachSlotHasItsOwnKey() {
        assertEquals("chk_7f3a/morning", ReminderSlot(ReminderViewKind.Checklist, "chk_7f3a", "morning", CheckpointTime.AfterWake(30)).key)
        // Keys whose text hashes alike are still different keys.
        assertEquals("Aa".hashCode(), "BB".hashCode())
        assertNotEquals(slotKey("Aa", "morning"), slotKey("BB", "morning"))
    }

    /** A checkpoint never runs past its wake day, as its time is read on that day. */
    @Test fun anOffsetPastMidnightRemindsAtTheWakeDaysLastMinute() {
        val berlin = ZoneId.of("Europe/Berlin")
        val now = ZonedDateTime.of(2026, 10, 4, 6, 0, 0, 0, berlin)
        val late = CheckpointTime.AfterWake(20 * 60)
        fun at(alarm: Long?) = nextCheckpointReminderInstant(now, late, ReminderTime(8, 0), fixedBedtime = null, alarm).atZone(berlin).toLocalDateTime().toString()

        assertEquals("2026-10-04T23:59", at(null))
        assertEquals("2026-10-04T23:59", at(ZonedDateTime.of(2026, 10, 4, 9, 0, 0, 0, berlin).toInstant().toEpochMilli()))
        assertEquals(LocalTime.MAX, late.on(Bedtime(LocalTime.of(8, 0), fixedBedtime = null)))

        // An alarm at 23:59:45 clamps its reminder to 23:59, which has passed at 23:59:30.
        val lateNow = ZonedDateTime.of(2026, 10, 4, 23, 59, 30, 0, berlin)
        val lateAlarm = ZonedDateTime.of(2026, 10, 4, 23, 59, 45, 0, berlin).toInstant().toEpochMilli()
        val next = nextCheckpointReminderInstant(lateNow, CheckpointTime.AfterWake(60), ReminderTime(8, 0), fixedBedtime = null, lateAlarm)
        assertTrue(next.isAfter(lateNow.toInstant()))
        assertEquals("2026-10-05T09:00", next.atZone(berlin).toLocalDateTime().toString())
        assertEquals(LocalTime.of(8, 0), CheckpointTime.AfterWake(0).on(Bedtime(LocalTime.of(8, 0), fixedBedtime = null)))
    }

    /** Each form fires by the rules the app's own reminders follow, the phone's alarm first. */
    @Test fun eachFormOfTimeFiresWhenTheSameWordsDoElsewhere() {
        val berlin = ZoneId.of("Europe/Berlin")
        val now = ZonedDateTime.of(2026, 10, 4, 6, 0, 0, 0, berlin)
        val fallback = ReminderTime(7, 0)
        val alarmAtNine = ZonedDateTime.of(2026, 10, 4, 9, 0, 0, 0, berlin).toInstant().toEpochMilli()
        fun at(time: CheckpointTime, alarm: Long?, bedtime: ReminderTime? = null) =
            nextCheckpointReminderInstant(now, time, fallback, bedtime, alarm).atZone(berlin).toLocalDateTime().toString()

        assertEquals("2026-10-04T12:30", at(CheckpointTime.Fixed(LocalTime.of(12, 30)), alarmAtNine))
        assertEquals("2026-10-04T07:30", at(CheckpointTime.AfterWake(30), null))
        assertEquals("2026-10-04T09:30", at(CheckpointTime.AfterWake(30), alarmAtNine))
        assertEquals("2026-10-04T22:45", at(CheckpointTime.BeforeSleep(15), null))
        assertEquals("2026-10-04T21:45", at(CheckpointTime.BeforeSleep(15), alarmAtNine, bedtime = ReminderTime(22, 0)))
        // This morning's alarm ended last night, so tonight follows the fallback.
        assertEquals("2026-10-04T22:45", at(CheckpointTime.BeforeSleep(15), alarmAtNine))
    }

    @Test fun reconcilingCancelsTheAlarmsOfARemovedChecklistAndSetsTheRest() {
        val fixture = Fixture(AppState(checklists = listOf(medication)))
        assertTrue(fixture.coordinator.reconcile())
        assertEquals(listOf("chk_7f3a/morning"), fixture.gateway.scheduled.map { it.first.key })
        assertEquals(ReminderTime(6, 45), fixture.gateway.fallbacks.single())
        assertEquals(ReminderTime(23, 30), fixture.gateway.bedtimes.single())

        fixture.state = AppState()
        assertTrue(fixture.coordinator.reconcile())
        assertEquals(listOf("chk_7f3a/morning"), fixture.gateway.cancelled)
        assertEquals(emptySet<String>(), fixture.runtime.keys)
        assertEquals(emptyMap<String, String>(), fixture.runtime.shown)
    }

    /**
     * A slot rung tonight is not rung again tonight by a later reconcile: an evening with two
     * candidate times, the phone's alarm and the fallback, is reminded once. Ported from Care's
     * own scheduler, whose remembered day every slot keeps now.
     */
    @Test fun aRescheduleAfterTonightsReminderNeverRemindsAgainTonight() {
        val fixture = Fixture(AppState(checklists = listOf(medication)))
        val tomorrow = LocalDate.parse(today).plusDays(1)

        fixture.coordinator.onReminderDue("chk_7f3a", "morning")
        fixture.coordinator.reconcile()

        assertEquals(List(2) { tomorrow }, fixture.gateway.scheduled.map { it.second })
    }

    /** One slot whose alarm cannot be set leaves the others set, and the answer says not all were. */
    @Test fun everySlotIsAttemptedWhenOneCannotBeSet() {
        val plants = medication.copy(id = "chk_plants", label = "Plants")
        val fixture = Fixture(AppState(checklists = listOf(medication, plants)))
        fixture.gateway.failing += "chk_7f3a/morning"

        assertFalse(fixture.coordinator.reconcile())
        assertEquals(listOf("chk_plants/morning"), fixture.gateway.scheduled.map { it.first.key })
        assertEquals(setOf("chk_7f3a/morning", "chk_plants/morning"), fixture.runtime.keys)
    }

    @Test fun aReminderSpeaksOnlyWhileSomethingDueIsOpenAndAlwaysSetsTomorrow() {
        val fixture = Fixture(AppState(checklists = listOf(medication)))
        assertTrue(fixture.coordinator.onReminderDue("chk_7f3a", "morning"))
        assertEquals(listOf("Levothyroxine", "Ibuprofen 400"), fixture.notified.single())
        assertEquals("chk_7f3a/morning", fixture.keys.single())
        assertEquals(mapOf("chk_7f3a/morning" to today), fixture.runtime.shown)
        assertEquals(LocalDate.parse(today).plusDays(1), fixture.gateway.scheduled.last().second)

        // A tick under another checkpoint's name does not count here.
        fixture.state = fixture.state.copy(checklistDays = listOf(ChecklistDay("chk_7f3a", today, 1L, setOf("morning/itm_levo", "evening/itm_ibu"))))
        fixture.coordinator.onReminderDue("chk_7f3a", "morning")
        assertEquals(listOf("Ibuprofen 400"), fixture.notified.last())

        fixture.state = fixture.state.copy(checklistDays = listOf(ChecklistDay("chk_7f3a", today, 2L, setOf("morning/itm_levo", "morning/itm_ibu"))))
        fixture.coordinator.onReminderDue("chk_7f3a", "morning")
        assertEquals(2, fixture.notified.size)
    }

    /** A token set aside for a checkpoint stops its asking: the alarm says nothing today, and is still set for tomorrow. */
    @Test fun aCheckpointWithATokenSetAsideStaysQuiet() {
        val ref = de.nicolasmahn.momentum.domain.TrackerRef(de.nicolasmahn.momentum.domain.TrackerTypeId("checklist"), "chk_7f3a")
        val setAside = Fixture(AppState(checklists = listOf(medication)), quietedByChoice = QuietedByChoice { asked, checkpoint -> asked == ref && checkpoint == "morning" })
        assertTrue(setAside.coordinator.onReminderDue("chk_7f3a", "morning"))
        assertTrue(setAside.notified.isEmpty())
        assertEquals(LocalDate.parse(today).plusDays(1), setAside.gateway.scheduled.last().second)

        // A token for another checkpoint, or for another checklist, quiets nothing here.
        val elsewhere = Fixture(AppState(checklists = listOf(medication)), quietedByChoice = QuietedByChoice { asked, checkpoint -> asked != ref || checkpoint != "morning" })
        elsewhere.coordinator.onReminderDue("chk_7f3a", "morning")
        assertEquals(listOf("Levothyroxine", "Ibuprofen 400"), elsewhere.notified.single())
    }

    /** Away, an excused item owes nothing: a checklist with every item excused stays quiet, and a mixed one names only the item still due. */
    @Test fun anExcusedItemIsQuietOnAnAwayDay() {
        val away = de.nicolasmahn.momentum.domain.holiday.HolidaySchedule(
            listOf(de.nicolasmahn.momentum.domain.holiday.DayRange(LocalDate.parse(today), LocalDate.parse(today))),
        )
        val excused = Fixture(AppState(checklists = listOf(medication.excused())), awayDays = away)
        excused.coordinator.onReminderDue("chk_7f3a", "morning")
        assertEquals(0, excused.notified.size)
        assertEquals(LocalDate.parse(today).plusDays(1), excused.gateway.scheduled.last().second)
        val due = Fixture(AppState(checklists = listOf(medication)), awayDays = away)
        due.coordinator.onReminderDue("chk_7f3a", "morning")
        assertEquals(1, due.notified.size)
        val mixed = Fixture(AppState(checklists = listOf(medication.copy(items = listOf(medication.items[0].copy(whenAway = Dated.always(WhenAway.Excused)), medication.items[1])))), awayDays = away)
        mixed.coordinator.onReminderDue("chk_7f3a", "morning")
        assertEquals(listOf(listOf("Ibuprofen 400")), mixed.notified)
    }

    /** Sick wins over away: a day that is both asks nothing of the streak, but its reminders still come. */
    @Test fun anExcusedItemStillRemindsOnADayThatIsSickAsWellAsAway() {
        val sickAndAway = de.nicolasmahn.momentum.domain.holiday.HolidaySchedule(
            listOf(de.nicolasmahn.momentum.domain.holiday.DayRange(LocalDate.parse(today), LocalDate.parse(today))),
        ).withSickDays(de.nicolasmahn.momentum.domain.holiday.DayRange(LocalDate.parse(today), LocalDate.parse(today)))
        val excused = Fixture(AppState(checklists = listOf(medication.excused())), awayDays = sickAndAway)
        excused.coordinator.onReminderDue("chk_7f3a", "morning")
        assertEquals(listOf(listOf("Levothyroxine", "Ibuprofen 400")), excused.notified)
    }

    /** On an away day an item that asks fewer times is named only on a day skipping it would cost, as a week item is. */
    @Test fun aLoweredItemRemindsOnAnAwayDayOnlyOnceItsFreeDaysAreUsedUp() {
        // Sunday 2026-10-04 ends a week spent away; three times a week on holiday leaves Monday to Thursday free.
        val week = de.nicolasmahn.momentum.domain.holiday.HolidaySchedule(
            listOf(de.nicolasmahn.momentum.domain.holiday.DayRange(LocalDate.parse("2026-09-28"), LocalDate.parse(today))),
        )
        val lowered = medication.copy(items = listOf(medication.items[0], medication.items[1].copy(awayTimes = 3, whenAway = Dated.always(WhenAway.LowerTarget))))
        val thursday = Fixture(AppState(checklists = listOf(lowered)), awayDays = week, todayKey = "2026-10-01")
        thursday.coordinator.onReminderDue("chk_7f3a", "morning")
        assertEquals(listOf(listOf("Levothyroxine")), thursday.notified)
        val sunday = Fixture(AppState(checklists = listOf(lowered)), awayDays = week)
        sunday.coordinator.onReminderDue("chk_7f3a", "morning")
        assertEquals(listOf(listOf("Levothyroxine", "Ibuprofen 400")), sunday.notified)
    }

    private fun ChecklistConfiguration.excused() = copy(items = items.map { it.copy(whenAway = Dated.always(WhenAway.Excused)) })

    /** The alarm of a checklist's checkpoint rang. */
    private fun ChecklistReminderCoordinator.onReminderDue(checklistId: String, checkpointId: String) = onReminderDue(ReminderViewKind.Checklist, checklistId, checkpointId)

    /**
     * A notification answers to the day it asked about, after 04:00 too: Monday evening's about
     * Sunday is answered on Tuesday by Sunday's record, never by Monday's, which answers Tuesday
     * morning's.
     */
    @Test fun aNotificationIsAnsweredByTheRecordItAskedAboutEvenAfterTheDayTurned() {
        val evening = Checkpoint("evening", CheckpointTime.Fixed(LocalTime.of(21, 0)), remind = true)
        val lastNight = medication.copy(checkpoints = medication.checkpoints + evening, about = Dated.always(AboutDay.Yesterday))
        val state = AppState(checklists = listOf(lastNight))
        fun complete(dateKey: String, at: Long) = ChecklistDay(lastNight.id, dateKey, at, lastNight.checkpoints.flatMap { checkpoint -> lastNight.items.map { "${checkpoint.id}/${it.id}" } }.toSet())

        val monday = Fixture(state, todayKey = "2026-10-12")
        monday.coordinator.onReminderDue("chk_7f3a", "evening")
        assertEquals(mapOf("chk_7f3a/evening" to "2026-10-11"), monday.runtime.shown)

        val tuesday = Fixture(state, todayKey = "2026-10-13", runtime = monday.runtime)
        tuesday.coordinator.onReminderDue("chk_7f3a", "morning")
        assertEquals(mapOf("chk_7f3a/evening" to "2026-10-11", "chk_7f3a/morning" to "2026-10-12"), tuesday.runtime.shown)

        val mondayDone = state.copy(checklistDays = listOf(complete("2026-10-12", 1L)))
        tuesday.coordinator.onRecordsSaved(state, mondayDone, lastNight.id)
        assertEquals(listOf("chk_7f3a/morning"), tuesday.gateway.dismissed)
        assertEquals(mapOf("chk_7f3a/evening" to "2026-10-11"), tuesday.runtime.shown)

        val sundayDone = mondayDone.copy(checklistDays = mondayDone.checklistDays + complete("2026-10-11", 2L))
        tuesday.coordinator.onRecordsSaved(mondayDone, sundayDone, lastNight.id)
        assertEquals(listOf("chk_7f3a/morning", "chk_7f3a/evening"), tuesday.gateway.dismissed)
        assertEquals(emptyMap<String, String>(), tuesday.runtime.shown)
        // Another checklist's save answers nothing here.
        tuesday.coordinator.onReminderDue("chk_7f3a", "morning")
        tuesday.coordinator.onRecordsSaved(state, mondayDone, "chk_other")
        assertEquals(2, tuesday.gateway.dismissed.size)
    }

    /**
     * A checklist about yesterday is reminded of yesterday's record: what is open there is named
     * as yesterday's, whatever today's own record holds, and the notifier is told the due day.
     */
    @Test fun aChecklistAboutYesterdayIsRemindedOfYesterdaysRecord() {
        val dueDay = LocalDate.parse(today)
        val yesterday = dueDay.minusDays(1).toString()
        val lastNight = medication.copy(about = Dated.always(AboutDay.Yesterday))
        val levoOnly = ChecklistDay(lastNight.id, yesterday, 1L, setOf("morning/itm_levo"))
        val todayComplete = ChecklistDay(lastNight.id, today, 2L, setOf("morning/itm_levo", "morning/itm_ibu"))
        val fixture = Fixture(AppState(checklists = listOf(lastNight), checklistDays = listOf(levoOnly, todayComplete)))
        fixture.coordinator.onReminderDue("chk_7f3a", "morning")
        assertEquals(listOf(listOf("Ibuprofen 400")), fixture.notified)
        assertEquals(listOf(dueDay), fixture.dueDays)
        val ibuprofen = listOf(UiText.Verbatim("Ibuprofen 400")).joinedWithAnd()
        assertEquals(
            uiText(R.string.checklist_reminder_open_yesterday, ibuprofen),
            checklistReminderMessage(lastNight, lastNight.checkpoints.single(), lastNight.items.filter { it.id == "itm_ibu" }, lastNight.reminderDaysOn(dueDay)).text,
        )
        assertEquals(
            uiText(R.string.checklist_reminder_open, ibuprofen),
            checklistReminderMessage(medication, medication.checkpoints.single(), medication.items.filter { it.id == "itm_ibu" }, medication.reminderDaysOn(dueDay)).text,
        )

        // Yesterday complete, nothing is owed, whatever today holds.
        fixture.state = AppState(checklists = listOf(lastNight), checklistDays = listOf(todayComplete.copy(dateKey = yesterday)))
        fixture.coordinator.onReminderDue("chk_7f3a", "morning")
        assertEquals(1, fixture.notified.size)

        // On its first day it asks about a day it did not exist on, which had nothing due.
        val created = Fixture(AppState(checklists = listOf(lastNight.copy(items = lastNight.items.map { it.copy(from = dueDay) }))))
        created.coordinator.onReminderDue("chk_7f3a", "morning")
        assertEquals(0, created.notified.size)
    }

    @Test fun aHiddenOrRemovedChecklistNeverSpeaks() {
        val hidden = Fixture(AppState(checklists = listOf(medication)), FeatureVisibilitySettings().hidingChecklist("chk_7f3a"))
        hidden.coordinator.onReminderDue("chk_7f3a", "morning")
        val removed = Fixture(AppState())
        removed.coordinator.onReminderDue("chk_7f3a", "morning")
        assertEquals(0, hidden.notified.size + removed.notified.size)
    }

    /** A week item is named only on a day skipping it would cost; on its free days it is offered without a nudge. */
    @Test fun aWeekItemIsNamedOnlyOnceItsFreeDaysAreUsedUp() {
        // 2026-10-04 is a Sunday, the last day of a week that walked on no day yet.
        val weekly = medication.copy(items = medication.items + ChecklistItem("itm_walk", "Walk", target = de.nicolasmahn.momentum.domain.checklist.ChecklistTarget.PerWeek(times = 1)))
        val pillsTaken = setOf("morning/itm_levo", "morning/itm_ibu")
        val sunday = Fixture(AppState(checklists = listOf(weekly), checklistDays = listOf(ChecklistDay(weekly.id, today, 1L, pillsTaken))))
        sunday.coordinator.onReminderDue("chk_7f3a", "morning")
        assertEquals(listOf("Walk"), sunday.notified.single())
        // Walked on Tuesday, nothing is owed on Sunday.
        sunday.state = sunday.state.copy(checklistDays = sunday.state.checklistDays + ChecklistDay(weekly.id, "2026-09-29", 2L, setOf("morning/itm_walk")))
        sunday.coordinator.onReminderDue("chk_7f3a", "morning")
        assertEquals(1, sunday.notified.size)
        // On Saturday a free day is left, so the morning stays quiet once the pills are taken.
        val saturday = Fixture(AppState(checklists = listOf(weekly), checklistDays = listOf(ChecklistDay(weekly.id, "2026-10-03", 1L, pillsTaken))), todayKey = "2026-10-03")
        saturday.coordinator.onReminderDue("chk_7f3a", "morning")
        assertEquals(0, saturday.notified.size)
    }

    /** A miss is settled, never owed: a checkpoint whose due items all log only misses never reminds. */
    @Test fun aCheckpointWhoseItemsAllLogOnlyMissesNeverReminds() {
        val fixture = Fixture(AppState(checklists = listOf(loggingOnlyMisses("itm_levo", "itm_ibu"))))
        assertTrue(fixture.coordinator.onReminderDue("chk_7f3a", "morning"))
        assertEquals(emptyList<List<String>>(), fixture.notified)
        assertEquals(emptyMap<String, String>(), fixture.runtime.shown)
    }

    /**
     * A notification on show names only what is still owed: after a save it says the rest again,
     * and after an item is switched to only log misses it leaves the words, quietly both times; once
     * nothing is owed it goes.
     */
    @Test fun aPostedNotificationIsSaidAgainWithWhatIsStillOwed() {
        val fixture = Fixture(AppState(checklists = listOf(medication)))
        fixture.coordinator.onReminderDue("chk_7f3a", "morning")
        val levoTaken = AppState(checklists = listOf(medication), checklistDays = listOf(ChecklistDay(medication.id, today, 1L, setOf("morning/itm_levo"))))
        fixture.coordinator.onRecordsSaved(fixture.state, levoTaken, medication.id)
        assertEquals(listOf("Ibuprofen 400"), fixture.notified.last())

        fixture.state = AppState(checklists = listOf(loggingOnlyMisses("itm_ibu")))
        fixture.coordinator.refreshPosted()
        assertEquals(listOf("Levothyroxine"), fixture.notified.last())
        assertEquals(listOf(false, true, true), fixture.quiet)
        assertEquals(mapOf("chk_7f3a/morning" to today), fixture.runtime.shown)

        fixture.state = AppState(checklists = listOf(loggingOnlyMisses("itm_ibu", "itm_levo")))
        fixture.coordinator.refreshPosted()
        assertEquals(3, fixture.notified.size)
        assertEquals(listOf("chk_7f3a/morning"), fixture.gateway.dismissed)
        assertEquals(emptyMap<String, String>(), fixture.runtime.shown)
    }

    /**
     * A notification keeps the days it asked about. Tuesday's asks about Monday on a checklist about
     * yesterday in the streak; switched to about today that Tuesday, it is still said again about
     * Monday, as yesterday's, and still opens Monday rather than a day the new setting would name.
     */
    @Test fun aNotificationSaidAgainKeepsTheRecordItAskedAbout() {
        val tuesday = LocalDate.parse("2026-10-13")
        val monday = tuesday.minusDays(1)
        val lastNight = medication.copy(
            about = Dated.always(AboutDay.Yesterday),
            rewards = ChecklistRewards(maxPasses = 3, everyKeptDays = 7, periods = listOf(RewardPeriod(LocalDate.parse("2026-10-01")))),
        )
        val fixture = Fixture(AppState(checklists = listOf(lastNight)), todayKey = tuesday.toString())
        fixture.coordinator.onReminderDue("chk_7f3a", "morning")
        assertEquals(ReminderDays(tuesday, monday), fixture.asked.single())

        val aboutToday = lastNight.applyEdit(ChecklistEdit.SetAbout(AboutDay.Today), tuesday)
        assertEquals(Dated(listOf(Dated.Revision(AboutDay.Yesterday), Dated.Revision(AboutDay.Today, tuesday))), aboutToday.about)
        fixture.state = AppState(checklists = listOf(aboutToday))
        fixture.coordinator.refreshPosted()
        assertEquals(listOf(ReminderDays(tuesday, monday), ReminderDays(tuesday, monday)), fixture.asked)
        assertTrue(fixture.asked.last().aboutYesterday)
        assertEquals(mapOf("chk_7f3a/morning" to monday.toString()), fixture.runtime.shown)
    }

    /** A notification swiped away or tapped is forgotten: neither a settings change nor a save brings it back. */
    @Test fun aNotificationNoLongerOnShowIsNeverBroughtBack() {
        val fixture = Fixture(AppState(checklists = listOf(medication)))
        fixture.coordinator.onReminderDue("chk_7f3a", "morning")
        fixture.gateway.swiped += "chk_7f3a/morning"

        fixture.state = AppState(checklists = listOf(loggingOnlyMisses("itm_ibu")))
        fixture.coordinator.refreshPosted()
        val levoTaken = AppState(checklists = listOf(medication), checklistDays = listOf(ChecklistDay(medication.id, today, 1L, setOf("morning/itm_levo"))))
        fixture.coordinator.onRecordsSaved(AppState(checklists = listOf(medication)), levoTaken, medication.id)
        assertEquals(1, fixture.notified.size)
        assertEquals(emptyMap<String, String>(), fixture.runtime.shown)
    }

    /** The medication with [ids] logging only misses. */
    private fun loggingOnlyMisses(vararg ids: String) =
        medication.copy(items = medication.items.map { if (it.id in ids) it.copy(exceptionsOnly = Dated.always(true)) else it })

    private inner class Fixture(
        var state: AppState,
        visibility: FeatureVisibilitySettings = FeatureVisibilitySettings(),
        awayDays: de.nicolasmahn.momentum.domain.holiday.HolidaySchedule = de.nicolasmahn.momentum.domain.holiday.HolidaySchedule(),
        todayKey: String = today,
        /** Shared with another day's fixture, as the phone's is from one day to the next. */
        val runtime: FakeRuntime = FakeRuntime(),
        quietedByChoice: QuietedByChoice = QuietedByChoice { _, _ -> false },
    ) {
        val gateway = FakeGateway(runtime)
        val notified = mutableListOf<List<String>>()
        val keys = mutableListOf<String>()
        val dueDays = mutableListOf<LocalDate>()
        /** The days each notification asked about. */
        val asked = mutableListOf<ReminderDays>()
        /** Whether each notification was said again quietly rather than rung. */
        val quiet = mutableListOf<Boolean>()
        val coordinator = ChecklistReminderCoordinator(
            trackerRepository = object : TrackerRepository {
                override fun load() = state
                override fun save(state: AppState) = error("read only")
                override fun update(transform: (AppState) -> AppState): TrackerUpdateResult = error("read only")
            },
            featureVisibility = FeatureVisibilitySource { visibility },
            gateway = gateway,
            runtime = runtime,
            notifier = { _, _, open, key, days, quietly -> notified += open.map { it.label }; keys += key; dueDays += days.dueDay; asked += days; quiet += quietly; true },
            loggedNotifier = { _, _, _, _ -> error("No measurement reminds here.") },
            reminderSettings = { ReminderSettings(fallbackAlarmTime = ReminderTime(6, 45), fixedBedtime = ReminderTime(23, 30)) },
            todayKey = { todayKey },
            awayDays = { awayDays },
            quietedByChoice = quietedByChoice,
        )
    }

    /** Every notification posted is on show until it is [swiped] away. */
    private class FakeGateway(private val runtime: FakeRuntime) : ChecklistReminderGateway {
        val swiped = mutableSetOf<String>()
        /** The keys whose alarm cannot be set, as one whose time cannot be found. */
        val failing = mutableSetOf<String>()
        val scheduled = mutableListOf<Pair<ReminderSlot, LocalDate?>>()
        val fallbacks = mutableSetOf<ReminderTime>()
        val bedtimes = mutableSetOf<ReminderTime?>()
        val cancelled = mutableListOf<String>()
        val dismissed = mutableListOf<String>()
        override fun canScheduleExact() = true
        override fun shown() = runtime.posted().keys - swiped
        override fun schedule(slot: ReminderSlot, earliestDate: LocalDate?, fallbackAlarmTime: ReminderTime, fixedBedtime: ReminderTime?): Boolean {
            if (slot.key in failing) return false
            scheduled += slot to earliestDate
            fallbacks += fallbackAlarmTime
            bedtimes += fixedBedtime
            return true
        }
        override fun cancel(key: String) {
            cancelled += key
        }
        override fun dismiss(key: String) {
            dismissed += key
        }
        override fun keepUnsaved(key: String, why: UiText) = false
    }

    private class FakeRuntime : ChecklistReminderRuntime {
        var keys = emptySet<String>()
        private val days = mutableMapOf<String, ReminderDays>()
        private val bounds = mutableMapOf<String, LocalDate>()
        override fun earliest() = bounds.toMap()
        override fun earliest(key: String, day: LocalDate) {
            bounds[key] = day
        }
        /** Each notification posted, to the day whose record it asked about. */
        val shown: Map<String, String> get() = days.mapValues { (_, asked) -> asked.recordDay.toString() }
        override fun scheduled() = keys
        override fun record(keys: Set<String>) {
            this.keys = keys
        }
        override fun posted() = days.toMap()
        override fun posted(key: String, days: ReminderDays) {
            this.days[key] = days
        }
        override fun dismissed(key: String) {
            days.remove(key)
        }
    }
}
