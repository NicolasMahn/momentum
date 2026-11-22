package com.example.workouttracker.domain.checklist

import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.compatibility.CareChecklistId
import com.example.workouttracker.domain.compatibility.configuredChecklists
import com.example.workouttracker.domain.compatibility.CareLabels
import com.example.workouttracker.domain.compatibility.defaultCareChecklist
import com.example.workouttracker.domain.holiday.DayRange
import com.example.workouttracker.domain.holiday.HolidaySchedule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every edit starts on today's habit day: no earlier day is ever read differently. */
class ChecklistEditsTest {
    private val today = LocalDate.parse("2026-10-14")
    private val yesterday = today.minusDays(1)
    private val medication = ChecklistConfiguration(
        id = "chk_med",
        label = "Medication",
        icon = ViewIcon.Medication,
        hue = ViewHue.Sky,
        checkpoints = listOf(
            Checkpoint("morning", CheckpointTime.Fixed(LocalTime.of(8, 0)), label = "Morning", remind = true),
            Checkpoint("evening", CheckpointTime.Fixed(LocalTime.of(21, 0)), label = "Evening", remind = true),
        ),
        items = listOf(
            ChecklistItem("levo", "Levothyroxine", checkpoints = listOf("morning")),
            ChecklistItem("ibu", "Ibuprofen"),
            ChecklistItem("iron", "Iron", checkpoints = listOf("evening"), from = LocalDate.parse("2026-09-01")),
        ),
        rewards = ChecklistRewards(maxPasses = 3, everyKeptDays = 7),
    )
    private val state = AppState(checklists = listOf(medication))

    private fun AppState.edit(vararg edits: ChecklistEdit, on: LocalDate = today, id: String = medication.id): AppState =
        edits.fold(this) { state, edit -> state.applyEdit(id, edit, on) }

    private val AppState.medication get() = checklist("chk_med")!!

    private fun AppState.dueOn(date: LocalDate): Set<String> = medication.rulesOn(date).ticks

    /** The away rule of [itemId] on [day], read from the entry in use that day; null while it has none. */
    private fun ChecklistConfiguration.awayRuleOf(itemId: String, day: LocalDate): WhenAway? =
        items.singleOrNull { it.id == itemId && isWithin(it.from, it.until, day) }?.whenAway?.on(day)

    @Test fun anAddedItemIsDueFromTodayAndNeverBefore() {
        val added = state.edit(ChecklistEdit.AddItem("itm_d", "Vitamin D", checkpoints = listOf("morning")))
        assertEquals(state.dueOn(yesterday), added.dueOn(yesterday))
        assertEquals(state.dueOn(today) + "morning/itm_d", added.dueOn(today))
    }

    @Test fun aRemovedItemEndsYesterdayAndStaysForTheDaysItCounted() {
        val removed = state.edit(ChecklistEdit.RemoveItem("ibu"))
        assertEquals(state.dueOn(yesterday), removed.dueOn(yesterday))
        assertFalse(removed.dueOn(today).any { it.endsWith("/ibu") })
        assertEquals(yesterday, removed.medication.items.single { it.id == "ibu" }.until)
        assertFalse(removed.medication.liveItems(today).any { it.id == "ibu" })
    }

    @Test fun anItemAddedAndRemovedOnOneDayLeavesNothingBehind() {
        val back = state.edit(ChecklistEdit.AddItem("itm_d", "Vitamin D"), ChecklistEdit.RemoveItem("itm_d"))
        assertEquals(state, back)
    }

    @Test fun changingAnItemSplitsItsEntryAndUndoingItOnTheSameDayJoinsItAgain() {
        val saturdays = ChecklistTarget.Weekdays(setOf(DayOfWeek.SATURDAY))
        val changed = state.edit(ChecklistEdit.SetItemTarget("ibu", saturdays))
        assertEquals(state.dueOn(yesterday), changed.dueOn(yesterday))
        // Wednesday 2026-10-14 is no Saturday any more.
        assertFalse(changed.dueOn(today).any { it.endsWith("/ibu") })
        val entries = changed.medication.items.filter { it.id == "ibu" }
        assertEquals(listOf(yesterday to null, null to saturdays), entries.map { it.until to it.target })
        assertEquals(listOf(null, today), entries.map { it.from })
        // One item in the editor, two entries in the file.
        assertEquals(1, changed.medication.liveItems(today).count { it.id == "ibu" })

        assertEquals(state, changed.edit(ChecklistEdit.SetItemTarget("ibu", null)))
        val twice = changed.edit(ChecklistEdit.SetItemTarget("ibu", ChecklistTarget.Weekdays(setOf(DayOfWeek.MONDAY))))
        assertEquals(2, twice.medication.items.count { it.id == "ibu" })
    }

    @Test fun theChecklistsRhythmChangesTheItemsFollowingItFromTodayAndNoEarlierDay() {
        val weekly = ChecklistTarget.PerWeek(times = 2)
        val saturdays = ChecklistTarget.Weekdays(setOf(DayOfWeek.SATURDAY))
        val own = state.edit(ChecklistEdit.SetItemTarget("levo", saturdays), on = yesterday)
        val changed = own.edit(ChecklistEdit.SetTarget(weekly))
        assertEquals(own.dueOn(yesterday), changed.dueOn(yesterday))
        assertEquals(own.medication.rulesOn(yesterday).weekItems, changed.medication.rulesOn(yesterday).weekItems)
        // Ibuprofen and Iron follow the checklist; Levothyroxine keeps the Saturdays it chose.
        assertEquals(listOf("ibu", "iron"), changed.medication.rulesOn(today).weekItems.map { it.item.id })
        assertEquals(saturdays, changed.medication.liveItems(today).single { it.id == "levo" }.target)
        assertEquals(listOf(null, null), changed.medication.liveItems(today).filter { it.id != "levo" }.map { it.target })

        // Back to daily on the same day, every day reads as before the change.
        val back = changed.edit(ChecklistEdit.SetTarget(ChecklistTarget.PerDay))
        listOf(yesterday, today, today.plusDays(3)).forEach { day -> assertEquals(own.dueOn(day), back.dueOn(day)) }
        assertEquals(emptyList<WeekItemRules>(), back.medication.rulesOn(today).weekItems)
    }

    @Test fun anItemAskingWhatTheChecklistNowAsksFollowsItAgain() {
        val weekly = ChecklistTarget.PerWeek(times = 3)
        val changed = state.edit(ChecklistEdit.SetItemTarget("ibu", weekly), on = yesterday).edit(ChecklistEdit.SetTarget(weekly))
        assertEquals(null, changed.medication.liveItems(today).single { it.id == "ibu" }.target)
        assertEquals(state.dueOn(yesterday.minusDays(1)), changed.dueOn(yesterday.minusDays(1)))
    }

    @Test fun aWeekTargetAndItsAwayNumberAreDatedLikeAnyTarget() {
        val weekly = ChecklistTarget.PerWeek(times = 3)
        val changed = state.edit(ChecklistEdit.SetItemTarget("ibu", weekly), ChecklistEdit.SetItemAwayTimes("ibu", 1))
        assertEquals(state.dueOn(yesterday), changed.dueOn(yesterday))
        assertEquals(emptyList<WeekItemRules>(), changed.medication.rulesOn(yesterday).weekItems)
        assertFalse(changed.dueOn(today).any { it.endsWith("/ibu") })
        assertEquals(listOf("ibu"), changed.medication.rulesOn(today).weekItems.map { it.item.id })
        assertEquals(listOf(yesterday to null, null to weekly), changed.medication.items.filter { it.id == "ibu" }.map { it.until to it.target })
        assertEquals(listOf(null, 1), changed.medication.items.filter { it.id == "ibu" }.map { it.awayTimes })
        // Fewer times is for an item due more than once a week: a Saturday item cannot choose it; this one can, dated too.
        assertThrows(IllegalArgumentException::class.java) {
            state.edit(ChecklistEdit.SetItemTarget("ibu", ChecklistTarget.Weekdays(setOf(DayOfWeek.SATURDAY))), ChecklistEdit.SetItemWhenAway("ibu", WhenAway.LowerTarget))
        }
        val lowered = changed.edit(ChecklistEdit.SetItemWhenAway("ibu", WhenAway.LowerTarget)).medication
        assertEquals(WhenAway.Due to WhenAway.LowerTarget, lowered.awayRuleOf("ibu", yesterday) to lowered.awayRuleOf("ibu", today))
    }

    /** A daily item asks fewer times on holiday with a count of its own, dated like a target change and undone the same day without a trace. */
    @Test fun aDailyItemAsksFewerTimesOnHolidayWithACount() {
        val lowered = state.edit(ChecklistEdit.SetItemAwayTimes("levo", 3), ChecklistEdit.SetItemWhenAway("levo", WhenAway.LowerTarget))
        assertEquals(state.dueOn(yesterday), lowered.dueOn(yesterday))
        val entries = lowered.medication.items.filter { it.id == "levo" }
        assertEquals(listOf(yesterday to null, null to 3), entries.map { it.until to it.awayTimes })
        assertEquals(listOf(Dated.always(WhenAway.Due), Dated.always(WhenAway.LowerTarget)), entries.map { it.whenAway })
        assertEquals(state, lowered.edit(ChecklistEdit.SetItemWhenAway("levo", WhenAway.Due), ChecklistEdit.SetItemAwayTimes("levo", null)))
        // No fewer than usual is refused.
        assertThrows(IllegalArgumentException::class.java) { state.edit(ChecklistEdit.SetItemAwayTimes("levo", 7)) }
    }

    /** Fewer times means nothing for an item due once a week or less, so one that becomes so goes back to due in the same dated edit, keeping no count. */
    @Test fun loweringGoesBackToDueWhenTheItemBecomesDueOnceAWeekOrLess() {
        val lowered = state.edit(ChecklistEdit.SetItemTarget("ibu", ChecklistTarget.PerWeek(times = 3)), ChecklistEdit.SetItemAwayTimes("ibu", 1), on = yesterday)
            .edit(ChecklistEdit.SetItemWhenAway("ibu", WhenAway.LowerTarget), on = yesterday)
        assertEquals(WhenAway.LowerTarget, lowered.medication.awayRuleOf("ibu", today))
        val saturdays = lowered.edit(ChecklistEdit.SetItemTarget("ibu", ChecklistTarget.Weekdays(setOf(DayOfWeek.SATURDAY))))
        assertEquals(WhenAway.LowerTarget to WhenAway.Due, saturdays.medication.awayRuleOf("ibu", yesterday) to saturdays.medication.awayRuleOf("ibu", today))
        assertEquals(null, saturdays.medication.liveItems(today).single { it.id == "ibu" }.awayTimes)
        assertEquals(1, saturdays.medication.items.single { it.id == "ibu" && it.until == yesterday }.awayTimes)
        // Every day is more than once a week, so going daily keeps the lowering and its count.
        val daily = lowered.edit(ChecklistEdit.SetItemTarget("ibu", null)).medication.liveItems(today).single { it.id == "ibu" }
        assertEquals(WhenAway.LowerTarget to 1, daily.whenAway.current to daily.awayTimes)
        // Each item's rule is its own: another week item keeps its choice.
        val two = lowered.edit(
            ChecklistEdit.SetItemTarget("iron", ChecklistTarget.PerWeek(times = 2)),
            ChecklistEdit.SetItemWhenAway("iron", WhenAway.LowerTarget),
            ChecklistEdit.SetItemTarget("ibu", ChecklistTarget.PerWeek(times = 1)),
        )
        assertEquals(WhenAway.LowerTarget to WhenAway.Due, two.medication.awayRuleOf("iron", today) to two.medication.awayRuleOf("ibu", today))
        // A removed item keeps the rule its days were judged by.
        assertEquals(WhenAway.LowerTarget, lowered.edit(ChecklistEdit.RemoveItem("ibu")).medication.awayRuleOf("ibu", yesterday))
    }

    /** A count the new usual count no longer allows is clamped to one below it. */
    @Test fun aCountTheNewDueRuleNoLongerAllowsIsClamped() {
        val lowered = state.edit(ChecklistEdit.SetItemAwayTimes("levo", 5), ChecklistEdit.SetItemWhenAway("levo", WhenAway.LowerTarget), on = yesterday)
        val threeDays = lowered.edit(ChecklistEdit.SetItemTarget("levo", ChecklistTarget.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))))
        assertEquals(2 to WhenAway.LowerTarget, threeDays.medication.liveItems(today).single { it.id == "levo" }.let { it.awayTimes to it.whenAway.current })
        assertEquals(5, threeDays.medication.items.single { it.id == "levo" && it.until == yesterday }.awayTimes)
        val twice = lowered.edit(ChecklistEdit.SetItemTarget("levo", ChecklistTarget.PerWeek(times = 2)))
        assertEquals(1, twice.medication.liveItems(today).single { it.id == "levo" }.awayTimes)
    }

    /**
     * An item made weekly today and lowered today can go back the same day: its target, its count
     * and its away rule change in one step, so no save fails on the way, and nothing is left behind.
     */
    @Test fun loweringGoesBackToDueOnTheDayItWasChosen() {
        val lowered = state.edit(ChecklistEdit.SetItemTarget("ibu", ChecklistTarget.PerWeek(times = 3)), ChecklistEdit.SetItemAwayTimes("ibu", 1), ChecklistEdit.SetItemWhenAway("ibu", WhenAway.LowerTarget))
        assertEquals(WhenAway.LowerTarget, lowered.medication.awayRuleOf("ibu", today))
        assertEquals(state, lowered.edit(ChecklistEdit.SetItemTarget("ibu", ChecklistTarget.PerWeek(times = 1)), ChecklistEdit.SetItemTarget("ibu", null)))
        // Iron is due in the evening only, so removing the evening ends it, lowered as it was.
        val eveningWeekly = state.edit(ChecklistEdit.SetItemTarget("iron", ChecklistTarget.PerWeek(times = 2)), ChecklistEdit.SetItemWhenAway("iron", WhenAway.LowerTarget))
        val withoutEvening = eveningWeekly.edit(ChecklistEdit.RemoveCheckpoint("evening"))
        assertEquals(yesterday, withoutEvening.medication.items.single { it.id == "iron" }.until)
        assertFalse(withoutEvening.medication.liveItems(today).any { it.id == "iron" })
    }

    @Test fun aSeasonAndCheckpointsAreDatedLikeATarget() {
        val winter = setOf(Month.NOVEMBER, Month.DECEMBER)
        val changed = state.edit(ChecklistEdit.SetItemSeason("ibu", winter), ChecklistEdit.SetItemCheckpoints("levo", listOf("evening")))
        assertEquals(state.dueOn(yesterday), changed.dueOn(yesterday))
        assertEquals(setOf("evening/levo", "evening/iron"), changed.dueOn(today))
    }

    @Test fun aRenameIsNotDatedAndShowsOnOldDays() {
        val changed = state.edit(ChecklistEdit.SetItemTarget("ibu", ChecklistTarget.Weekdays(setOf(DayOfWeek.MONDAY))), ChecklistEdit.RenameItem("ibu", "Ibuprofen 400"))
        assertEquals(listOf("Ibuprofen 400", "Ibuprofen 400"), changed.medication.items.filter { it.id == "ibu" }.map { it.label })
        assertEquals(
            "Ibuprofen 400",
            changed.medication.rulesOn(yesterday).checkpoints.flatMap { it.items }.first { it.id == "ibu" }.label,
        )
    }

    @Test fun anAddedCheckpointCountsFromToday() {
        val added = state.edit(ChecklistEdit.AddCheckpoint("cpt_noon", "Noon", CheckpointTime.AfterWake(240), remind = false))
        assertEquals(state.dueOn(yesterday), added.dueOn(yesterday))
        assertTrue("cpt_noon/ibu" in added.dueOn(today))
        assertEquals(today, added.medication.checkpoints.last().from)
    }

    @Test fun aCheckpointIsEmptyOnlyWhileNoItemFromTodayOnIsDueThere() {
        val noon = ChecklistEdit.AddCheckpoint("cpt_noon", "Noon", CheckpointTime.AfterWake(240), remind = false)
        val withoutEverywhere = state.edit(ChecklistEdit.RemoveItem("ibu"))
        // An item due at every checkpoint fills a new one too.
        assertEquals(emptyList<Checkpoint>(), state.edit(noon).medication.emptyCheckpoints(today))
        assertEquals(listOf("cpt_noon"), withoutEverywhere.edit(noon).medication.emptyCheckpoints(today).map { it.id })
        val filled = withoutEverywhere.edit(noon, ChecklistEdit.AddItem("itm_tea", "Tea", checkpoints = listOf("cpt_noon")))
        assertEquals(emptyList<Checkpoint>(), filled.medication.emptyCheckpoints(today))
    }

    @Test fun removingACheckpointEndsWhatWasDueOnlyThereAndKeepsTheRest() {
        val removed = state.edit(ChecklistEdit.RemoveCheckpoint("evening"))
        assertEquals(state.dueOn(yesterday), removed.dueOn(yesterday))
        assertEquals(setOf("morning/levo", "morning/ibu"), removed.dueOn(today))
        val evening = removed.medication.checkpoints.single { it.id == "evening" }
        assertEquals(yesterday, evening.until)
        assertFalse("A removed checkpoint never reminds.", evening.remind)
        assertEquals(yesterday, removed.medication.items.single { it.id == "iron" }.until)
        assertEquals(listOf("morning"), removed.medication.liveCheckpoints(today).map { it.id })
    }

    @Test fun anItemDueAtSeveralCheckpointsOnlyLosesTheRemovedOne() {
        val both = state.edit(ChecklistEdit.SetItemCheckpoints("levo", listOf("morning", "evening")), on = yesterday)
        val removed = both.edit(ChecklistEdit.RemoveCheckpoint("evening"))
        assertEquals(listOf("morning"), removed.medication.liveItems(today).single { it.id == "levo" }.checkpoints)
        assertEquals(both.dueOn(yesterday), removed.dueOn(yesterday))
    }

    @Test(expected = IllegalArgumentException::class)
    fun theLastCheckpointCannotBeRemoved() {
        state.edit(ChecklistEdit.RemoveCheckpoint("evening"), ChecklistEdit.RemoveCheckpoint("morning"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun anItemCanOnlyBeDueAtACheckpointInUse() {
        state.edit(ChecklistEdit.RemoveCheckpoint("evening"), ChecklistEdit.AddItem("itm_x", "Zinc", checkpoints = listOf("evening")))
    }

    @Test fun timesRemindersAndTheLookAreNotDated() {
        val changed = state.edit(
            ChecklistEdit.SetCheckpointTime("morning", CheckpointTime.AfterWake(10)),
            ChecklistEdit.SetCheckpointRemind("evening", false),
            ChecklistEdit.RenameCheckpoint("morning", "After waking"),
            ChecklistEdit.Rename("Pills"),
            ChecklistEdit.SetIcon(ViewIcon.Heart),
            ChecklistEdit.SetHue(ViewHue.Pink),
            ChecklistEdit.SetMotif(MoMotif.Pills),
        )
        val checklist = changed.medication
        assertEquals(CheckpointTime.AfterWake(10), checklist.checkpoints.first().at)
        assertEquals(null to null, checklist.checkpoints.first().from to checklist.checkpoints.first().until)
        assertEquals(listOf("Pills", "After waking"), listOf(checklist.label, checklist.checkpoints.first().label))
        assertEquals(Triple(ViewIcon.Heart, ViewHue.Pink, MoMotif.Pills), Triple(checklist.icon, checklist.hue, checklist.motif))
        assertEquals(state.dueOn(yesterday), changed.dueOn(yesterday))
    }

    @Test fun whatAnAwayDayMeansChangesFromTodayForEachItemOnItsOwn() {
        val excused = state.edit(ChecklistEdit.SetItemWhenAway("levo", WhenAway.Excused)).medication
        assertEquals(WhenAway.Due, excused.awayRuleOf("levo", yesterday))
        assertEquals(WhenAway.Excused, excused.awayRuleOf("levo", today))
        assertEquals(WhenAway.Due, excused.awayRuleOf("ibu", today))
        // The rule is dated inside the item, so its entry is not split.
        assertEquals(1, excused.items.count { it.id == "levo" })
        // Changing it back on the same day leaves no revision.
        assertEquals(state, state.edit(ChecklistEdit.SetItemWhenAway("levo", WhenAway.Excused), ChecklistEdit.SetItemWhenAway("levo", WhenAway.Due)))
    }

    /** No earlier day was judged by them, so a checklist that joined rewards today changes its rules plainly, and an item that only started today its away rule. */
    @Test fun settingsNoEarlierDayReadNeedNoRevision() {
        val outside = AppState(checklists = listOf(medication.copy(rewards = null)))
        val addedToday = state.edit(ChecklistEdit.AddItem("itm_d", "Vitamin D"), ChecklistEdit.SetItemWhenAway("itm_d", WhenAway.Excused))
        assertEquals(Dated.always(WhenAway.Excused), addedToday.medication.liveItems(today).single { it.id == "itm_d" }.whenAway)
        val joinedToday = outside.edit(ChecklistEdit.JoinRewards, ChecklistEdit.SetRewardRules(maxPasses = 2, everyKeptDays = 5)).medication.rewards!!
        assertEquals(Dated.always(2) to Dated.always(5), joinedToday.maxPasses to joinedToday.everyKeptDays)
    }

    /** The calendar reads an item's away rule on every day its entry covers, in or out of rewards, so an earlier away day keeps the rule it had. */
    @Test fun anAwayRuleIsDatedOutsideRewardsToo() {
        val outside = AppState(checklists = listOf(medication.copy(rewards = null)))
        val excused = outside.edit(ChecklistEdit.SetItemWhenAway("levo", WhenAway.Excused)).medication
        assertEquals(WhenAway.Due to WhenAway.Excused, excused.awayRuleOf("levo", yesterday) to excused.awayRuleOf("levo", today))
        // Yesterday, away with the levothyroxine missing, stays the open day it was.
        val away = HolidaySchedule(listOf(DayRange(yesterday, yesterday)))
        val ticks = tickRecords { if (it == yesterday) setOf("morning/ibu", "evening/ibu", "evening/iron") else emptySet() }
        assertEquals(listOf("levo"), excused.progressOn(yesterday, ticks, away).checkpoints.flatMap { checkpoint -> checkpoint.open.map { it.id } })
    }

    /** A lowering an item once had is history its entries keep; the one that stops being a week item reads as due from the day it did. */
    @Test fun anEarlierLoweringNeverStopsAnItemFromBecomingDaily() {
        val twoDaysAgo = today.minusDays(2)
        val weekly = state.edit(ChecklistEdit.SetItemTarget("ibu", ChecklistTarget.PerWeek(times = 3)), ChecklistEdit.SetItemAwayTimes("ibu", 1), ChecklistEdit.SetItemWhenAway("ibu", WhenAway.LowerTarget), on = twoDaysAgo)
        val dueAgain = weekly.edit(ChecklistEdit.SetItemWhenAway("ibu", WhenAway.Due), on = yesterday)
        val daily = dueAgain.edit(ChecklistEdit.SetItemTarget("ibu", null))
        assertEquals(
            listOf(WhenAway.LowerTarget, WhenAway.Due, WhenAway.Due),
            listOf(twoDaysAgo, yesterday, today).map { daily.medication.awayRuleOf("ibu", it) },
        )
        assertEquals(WhenAway.Due, daily.medication.liveItems(today).single { it.id == "ibu" }.whenAway.current)
    }

    @Test fun rewardRulesChangeFromToday() {
        val rewards = state.edit(ChecklistEdit.SetRewardRules(maxPasses = 5, everyKeptDays = 10)).medication.rewards!!
        assertEquals(3 to 7, rewards.maxPasses.on(yesterday) to rewards.everyKeptDays.on(yesterday))
        assertEquals(5 to 10, rewards.maxPasses.on(today) to rewards.everyKeptDays.on(today))
    }

    @Test fun deletingAChecklistRemovesItsConfigurationAndOnlyItsRecords() {
        val care = defaultCareChecklist(CareLabels("Care", "Morning", "Evening", "Brush teeth", "Hair foam", "Clean between teeth"), remindMorning = true, remindEvening = true)
        val caresDay = ChecklistDay(CareChecklistId, yesterday.toString(), 3L, setOf("morning/toothbrushing"))
        val days = listOf(
            ChecklistDay("chk_med", yesterday.toString(), 1L, setOf("morning/levo")),
            ChecklistDay("chk_med", today.minusDays(9).toString(), 2L, setOf("evening/iron")),
            caresDay,
        )
        val deleted = AppState(checklists = listOf(medication, care), checklistDays = days).deleteChecklist("chk_med")
        assertEquals(listOf(care), deleted.checklists)
        assertEquals(listOf(caresDay), deleted.checklistDays)
        assertEquals(emptyList<ChecklistConfiguration>(), deleted.configuredChecklists)
    }

    /** Supplements and Care are deleted like any other checklist, with every day recorded for them. */
    @Test fun aFeaturesChecklistIsDeletedWithItsDays() {
        val care = defaultCareChecklist(CareLabels("Care", "Morning", "Evening", "Brush teeth", "Hair foam", "Clean between teeth"), remindMorning = true, remindEvening = true)
        val caresDay = ChecklistDay(CareChecklistId, yesterday.toString(), 3L, setOf("morning/toothbrushing"))
        val medicationsDay = ChecklistDay("chk_med", yesterday.toString(), 1L, setOf("morning/levo"))
        val deleted = AppState(checklists = listOf(medication, care), checklistDays = listOf(medicationsDay, caresDay)).deleteChecklist(CareChecklistId)
        assertEquals(listOf(medication), deleted.checklists)
        assertEquals(listOf(medicationsDay), deleted.checklistDays)
    }

    /** A checklist always has something to tick, so the last item in use stays. */
    @Test fun theLastItemCannotBeRemoved() {
        val oneLeft = state.edit(ChecklistEdit.RemoveItem("levo"), ChecklistEdit.RemoveItem("ibu"))
        assertEquals(listOf("iron"), oneLeft.medication.liveItems(today).map { it.id })
        assertThrows(IllegalArgumentException::class.java) { oneLeft.edit(ChecklistEdit.RemoveItem("iron")) }
    }

    /** A checkpoint every item is checked only at would leave nothing to tick, so it stays too. */
    @Test fun aCheckpointHoldingEveryItemCannotBeRemoved() {
        val allMorning = state.edit(ChecklistEdit.SetItemCheckpoints("ibu", listOf("morning")), ChecklistEdit.RemoveItem("iron"))
        assertFalse(allMorning.medication.canRemoveCheckpoint("morning", today))
        assertTrue(allMorning.medication.canRemoveCheckpoint("evening", today))
        assertThrows(IllegalArgumentException::class.java) { allMorning.edit(ChecklistEdit.RemoveCheckpoint("morning")) }
        assertEquals(listOf("morning"), allMorning.edit(ChecklistEdit.RemoveCheckpoint("evening")).medication.liveCheckpoints(today).map { it.id })
        // An item due everywhere keeps the other checkpoint in use, so the first one may go.
        assertTrue(state.medication.canRemoveCheckpoint("morning", today))
    }

    /**
     * Whatever is changed today, every earlier day keeps the rules it had: a sweep of edits over
     * Care's configuration, read on every day of the season before.
     */
    @Test fun noEditReachesAnEarlierDay() {
        val care = defaultCareChecklist(CareLabels("Care", "Morning", "Evening", "Brush teeth", "Hair foam", "Clean between teeth"), remindMorning = true, remindEvening = true)
        val before = AppState(checklists = listOf(care))
        val edits = listOf(
            ChecklistEdit.AddItem("itm_floss", "Floss", checkpoints = listOf("evening")),
            ChecklistEdit.RemoveItem("hair_foam"),
            ChecklistEdit.SetItemTarget("toothbrushing", ChecklistTarget.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))),
            ChecklistEdit.SetItemSeason("clean_between_teeth", setOf(Month.JANUARY)),
            ChecklistEdit.SetItemCheckpoints("toothbrushing", listOf("morning")),
            ChecklistEdit.AddCheckpoint("cpt_noon", "Noon", CheckpointTime.Fixed(LocalTime.NOON), remind = true),
            ChecklistEdit.RemoveCheckpoint("morning"),
            ChecklistEdit.SetItemWhenAway("clean_between_teeth", WhenAway.Excused),
            ChecklistEdit.SetRewardRules(maxPasses = 2, everyKeptDays = 3),
        )
        val after = edits.fold(before) { state, edit -> state.applyEdit(CareChecklistId, edit, today) }
        val careAfter = after.checklist(CareChecklistId)!!
        assertNotEquals(care.rulesOn(today), careAfter.rulesOn(today))
        generateSequence(today.minusDays(1)) { it.minusDays(1) }.take(120).forEach { day ->
            assertEquals("Due on $day", care.rulesOn(day).ticks, careAfter.rulesOn(day).ticks)
            assertEquals("Recordable on $day", care.recordableOn(day).ticks, careAfter.recordableOn(day).ticks)
            care.items.forEach { item -> assertEquals("Away rule of ${item.id} on $day", care.awayRuleOf(item.id, day), careAfter.awayRuleOf(item.id, day)) }
            assertEquals(care.rewards!!.maxPasses.on(day), careAfter.rewards!!.maxPasses.on(day))
        }
    }

    /** A new checklist starts today, so changing its first checkpoint on that day leaves nothing behind. */
    @Test fun aNewChecklistsFirstDayLeavesNoTrace() {
        val starter = AppState().starterChecklist("Checklist", today, kotlin.random.Random(3))
        val started = AppState().addChecklist(starter)
        val replaced = started.applyEdit(starter.id, ChecklistEdit.AddCheckpoint("cpt_b", "Evening", CheckpointTime.Fixed(LocalTime.of(20, 0)), remind = false), today)
            .applyEdit(starter.id, ChecklistEdit.RemoveCheckpoint(starter.checkpoints.single().id), today)
        assertEquals(listOf("cpt_b"), replaced.checklist(starter.id)!!.checkpoints.map { it.id })
        assertEquals(ViewHue.Rust, starter.hue)
        assertEquals(ViewHue.Olive, started.starterChecklist("Checklist", today, kotlin.random.Random(4)).hue)
    }

    @Test fun freshIdsAreNewAndHaveNoSeparator() {
        val taken = setOf("itm_aaaa")
        val id = freshId("itm", taken, kotlin.random.Random(1))
        assertFalse(id in taken)
        assertTrue(id.matches(Regex("itm_[a-z2-9]{4}")))
    }

    /** A page applies the same edits to its draft, dated the same, so saving the draft stores what editing in place would have. */
    @Test fun aDraftTakesEveryEditAsTheStoredConfigurationDoes() {
        val edits = listOf(
            ChecklistEdit.AddItem("itm_d", "Vitamin D", checkpoints = listOf("morning")),
            ChecklistEdit.SetItemTarget("ibu", ChecklistTarget.Weekdays(setOf(DayOfWeek.MONDAY))),
            ChecklistEdit.SetItemWhenAway("ibu", WhenAway.Excused),
            ChecklistEdit.LeaveRewards,
            ChecklistEdit.JoinRewards,
            ChecklistEdit.SetRewardRules(5, 10),
            ChecklistEdit.RenameCheckpoint("evening", "Night"),
        )
        val draft = edits.fold(medication) { draft, edit -> draft.applyEdit(edit, today, state.checklistRecordedDates(medication.id)) }
        val stored = state.edit(*edits.toTypedArray()).medication
        assertEquals(stored, draft)
        assertEquals(yesterday, draft.items.first { it.id == "ibu" }.until)
        assertEquals(today, draft.items.last { it.id == "ibu" }.from)
    }

    /** A checklist in rewards over its whole history began at its first record, which a draft is told. */
    @Test fun leavingRewardsOnADraftClosesThePeriodThatBeganAtTheFirstRecord() {
        val first = LocalDate.parse("2026-10-01")
        val withDays = state.copy(checklistDays = listOf(ChecklistDay(medication.id, first.toString(), 1L, setOf("morning/levo"))))
        val stored = withDays.applyEdit(medication.id, ChecklistEdit.LeaveRewards, today).medication
        assertEquals(listOf(RewardPeriod(first, yesterday)), stored.rewards?.periods)
        assertEquals(stored, medication.applyEdit(ChecklistEdit.LeaveRewards, today, recordedDates = setOf(first)))
        assertEquals(emptyList<RewardPeriod>(), medication.applyEdit(ChecklistEdit.LeaveRewards, today).rewards?.periods)
    }

    @Test fun savingADraftReplacesTheChecklistWithItsIdAndNoOther() {
        val other = medication.copy(id = "chk_other", label = "Other")
        val both = AppState(checklists = listOf(medication, other))
        val draft = medication.applyEdit(ChecklistEdit.Rename("Pills"), today)
        assertEquals(listOf(draft, other), both.saveChecklist(draft).checklists)
        assertThrows(IllegalArgumentException::class.java) { both.saveChecklist(draft.copy(id = "chk_new")) }
    }
}
