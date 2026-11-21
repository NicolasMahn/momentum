package com.example.workouttracker.domain.mo

import com.example.workouttracker.domain.compatibility.missed

import com.example.workouttracker.domain.compatibility.free

import com.example.workouttracker.domain.compatibility.kept

import com.example.workouttracker.domain.compatibility.history

import com.example.workouttracker.domain.compatibility.MoNeed

import com.example.workouttracker.domain.settings.showingOnly
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.settings.ViewRef
import com.example.workouttracker.domain.trackerRef
import com.example.workouttracker.domain.compatibility.LegacyCareAction
import com.example.workouttracker.domain.compatibility.CareChecklistId
import com.example.workouttracker.domain.care.CareDone
import com.example.workouttracker.domain.compatibility.LegacyCareSession
import com.example.workouttracker.domain.care.careState
import com.example.workouttracker.domain.checklist.ChecklistConfiguration
import com.example.workouttracker.domain.checklist.ChecklistDay
import com.example.workouttracker.domain.checklist.ChecklistEdit
import com.example.workouttracker.domain.checklist.ChecklistItem
import com.example.workouttracker.domain.checklist.ChecklistTarget
import com.example.workouttracker.domain.checklist.Checkpoint
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.checklist.applyEdit
import com.example.workouttracker.domain.checklist.checklist
import com.example.workouttracker.domain.checklist.joinRewards
import com.example.workouttracker.domain.holiday.DayRange
import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.feature.statistics.StatisticsRange
import com.example.workouttracker.feature.statistics.statisticsSummary
import com.example.workouttracker.domain.care.openCareItems
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.example.workouttracker.domain.checklist.progressOf

/**
 * Care's between-teeth cleaning once a week on any day, as the owner asked for it: everywhere a
 * checklist is judged reads the same week.
 */
class WeekItemRewardsTest {
    private val monday = LocalDate.parse("2026-10-12")
    private val onlyCare = showingOnly(MoNeed.Care)
    private val careRef = MoNeed.Care.view.trackerRef
    private fun checklistRef(id: String) = ViewRef.Checklist(id).trackerRef
    private fun day(offset: Int): LocalDate = monday.plusDays(offset.toLong())

    /** Care with the cleaning once a week from [monday], brushing and foam done every day of that week, the cleaning on [flossOn]. */
    private fun careWeek(flossOn: Int? = null): AppState {
        val daily = (0..6).flatMap { offset ->
            LegacyCareSession.entries.flatMap { session ->
                listOf(LegacyCareAction.TOOTHBRUSHING, LegacyCareAction.HAIR_FOAM).map { CareDone(day(offset).toString(), session, it, offset.toLong()) }
            }
        }
        val floss = listOfNotNull(flossOn?.let { CareDone(day(it).toString(), LegacyCareSession.EVENING, LegacyCareAction.CLEAN_BETWEEN_TEETH, 10L) })
        return careState(*(daily + floss).toTypedArray())
            .applyEdit(CareChecklistId, ChecklistEdit.SetItemTarget(LegacyCareAction.CLEAN_BETWEEN_TEETH.storageId, ChecklistTarget.PerWeek(times = 1)), monday)
    }

    @Test fun theRewardWalkAndTheLedgerAgreeOnAWeekItem() {
        listOf(null, 3).forEach { flossOn ->
            val state = careWeek(flossOn)
            val nextMonday = day(7)
            val ledger = MoLedger.of(state, onlyCare, nextMonday).history(careRef)
            val walk = habitStreak(state, onlyCare, nextMonday).history.associateBy { it.date }
            (0..6).forEach { offset ->
                val kept = ledger[day(offset)] == MoStatus.Kept
                assertEquals("floss on $flossOn, day $offset", kept, walk.getValue(day(offset)).mark == MoDayMark.Perfect)
            }
            // Without the cleaning the week's one gap falls on Sunday, and Care's pass covers it.
            val sunday = walk.getValue(day(6))
            assertEquals(if (flossOn == null) MoDayMark.Protected else MoDayMark.Perfect, sunday.mark)
            assertEquals(if (flossOn == null) setOf(careRef) else emptySet(), sunday.coveredTrackers)
        }
    }

    /** Home and Mo count the cleaning in the evening only on the day skipping it would cost. */
    @Test fun homeAndMoCountTheWeekItemOnlyOnceItsFreeDaysAreUsedUp() {
        val state = careWeek()
        val saturday = moMood(state, onlyCare, day(5))
        assertEquals(listOf(careRef), saturday.keptTrackersToday)
        assertEquals(emptyList<com.example.workouttracker.domain.TrackerRef>(), saturday.missingTrackersToday)
        val sunday = moMood(state, onlyCare, day(6))
        assertEquals(listOf(careRef), sunday.missingTrackersToday)
        assertEquals(listOf(com.example.workouttracker.domain.CheckpointPart("evening")), sunday.trackerStillOpen(careRef))
        // The evening reminder names it only then.
        assertEquals(emptyList<String>(), state.openCareItems(day(5).toString(), LegacyCareSession.EVENING, HolidaySchedule()).map { it.id })
        assertEquals(listOf(LegacyCareAction.CLEAN_BETWEEN_TEETH.storageId), state.openCareItems(day(6).toString(), LegacyCareSession.EVENING, HolidaySchedule()).map { it.id })
    }

    /**
     * Choosing fewer times while away for Care lowers only its week item; a missed daily item is a
     * miss for Mo as for the walk, even in a week without holidays.
     */
    @Test fun loweringTheTargetWhileAwayGivesCareNoFreeDays() {
        val lowered = careWeek(flossOn = 3)
            .applyEdit(CareChecklistId, ChecklistEdit.SetItemWhenAway(LegacyCareAction.CLEAN_BETWEEN_TEETH.storageId, WhenAway.LowerTarget), monday)
            .let { state -> state.copy(checklistDays = state.checklistDays.map { day -> if (day.dateKey == monday.toString()) day.copy(ticks = day.ticks - "morning/toothbrushing") else day }) }
        val nextMonday = day(7)
        val ledger = MoLedger.of(lowered, onlyCare, nextMonday).history(careRef)
        assertEquals(MoStatus.Missed, ledger[monday])
        assertEquals(List(6) { MoStatus.Kept }, (1..6).map { ledger[day(it)] })
        val walk = habitStreak(lowered, onlyCare, nextMonday).history.associateBy { it.date }
        assertEquals(MoDayMark.Protected, walk.getValue(monday).mark)
        assertEquals(List(6) { MoDayMark.Perfect }, (1..6).map { walk.getValue(day(it)).mark })
    }

    /**
     * Care's hair foam every day, three times a week on a holiday that fills the week: Monday to
     * Thursday are its free days and Friday to Sunday gaps, and the walk, the ledger, Mo, Home and
     * the evening reminder read the same standing.
     */
    @Test fun everyJudgeReadsADailyItemsHolidayAllowanceAlike() {
        val brushing = (0..6).flatMap { offset -> LegacyCareSession.entries.map { CareDone(day(offset).toString(), it, LegacyCareAction.TOOTHBRUSHING, offset.toLong()) } }
        val lowered = careState(*brushing.toTypedArray())
            .applyEdit(CareChecklistId, ChecklistEdit.SetItemAwayTimes(LegacyCareAction.HAIR_FOAM.storageId, 3), monday)
            .applyEdit(CareChecklistId, ChecklistEdit.SetItemWhenAway(LegacyCareAction.HAIR_FOAM.storageId, WhenAway.LowerTarget), monday)
        val away = HolidaySchedule(listOf(DayRange(monday, day(6))))
        val nextMonday = day(7)
        val ledger = MoLedger.of(lowered, onlyCare, nextMonday, away).history(careRef)
        assertEquals(List(4) { MoStatus.Kept } + List(3) { MoStatus.Missed }, (0..6).map { ledger[day(it)] })
        val walk = habitStreak(lowered, onlyCare, nextMonday, away).history.associateBy { it.date }
        assertEquals(List(4) { MoDayMark.Perfect }, (0..3).map { walk.getValue(day(it)).mark })
        assertTrue((4..6).none { walk.getValue(day(it)).mark == MoDayMark.Perfect })
        assertEquals(emptyList<com.example.workouttracker.domain.TrackerRef>(), moMood(lowered, onlyCare, day(3), away).missingTrackersToday)
        assertEquals(listOf(careRef), moMood(lowered, onlyCare, day(4), away).missingTrackersToday)
        assertTrue(lowered.progressOf(lowered.checklist(CareChecklistId)!!, day(3).toString(), away).isComplete)
        assertEquals(1, lowered.progressOf(lowered.checklist(CareChecklistId)!!, day(4).toString(), away).gaps)
        assertEquals(emptyList<String>(), lowered.openCareItems(day(3).toString(), LegacyCareSession.EVENING, away).map { it.id })
        assertEquals(listOf(LegacyCareAction.HAIR_FOAM.storageId), lowered.openCareItems(day(4).toString(), LegacyCareSession.EVENING, away).map { it.id })
        // The same week without the holiday: the foam is missed every day.
        assertEquals(List(7) { MoStatus.Missed }, (0..6).map { MoLedger.of(lowered, onlyCare, nextMonday).history(careRef)[day(it)] })
    }

    /**
     * A sick day takes one off what the away days ask, and a tick beyond that keeps nothing: the
     * Monstera three times a week on a holiday that fills the week asks two once Wednesday is sick,
     * so a third tick is a record that neither grows the streak nor earns.
     */
    @Test fun aTickBeyondASickWeeksAllowanceKeepsNothingAndEarnsNothing() {
        val sickWednesday = HolidaySchedule(listOf(DayRange(monday, day(6)))).withSickDays(DayRange(day(2), day(2)))
        val two = habitStreak(watered(0, 1), nothingElse, day(7), sickWednesday)
        val three = habitStreak(watered(0, 1, 3), nothingElse, day(7), sickWednesday)
        assertEquals(
            listOf(MoDayMark.Perfect, MoDayMark.Perfect, MoDayMark.Sick) + List(4) { MoDayMark.NothingDue },
            three.history.filter { it.date < day(7) }.map { it.mark },
        )
        assertEquals(two.history.filter { it.date < day(7) }.map { it.mark }, three.history.filter { it.date < day(7) }.map { it.mark })
        assertEquals(two.days to two.standings[checklistRef(plants.id)]?.passes, three.days to three.standings[checklistRef(plants.id)]?.passes)
        assertEquals(2, three.days)
        // The records stay: three days watered, as the screen shows them.
        assertEquals(3 to 2, watered(0, 1, 3).progressOf(plants, day(6).toString(), sickWednesday).weekItems.single().let { it.done to it.asked })
    }

    /** A later edit reaches no earlier day in the walk either: Monday kept stays kept, and as of Wednesday the streak is the same. */
    @Test fun aLaterEditLeavesEarlierDaysKeptInTheWalk() {
        val away = HolidaySchedule(listOf(DayRange(monday, day(6))))
        val mondayWatered = watered(0)
        val dueFromWednesday = mondayWatered.applyEdit(plants.id, ChecklistEdit.SetItemWhenAway("monstera", WhenAway.Due), day(2))
        val onceFromWednesday = mondayWatered.applyEdit(plants.id, ChecklistEdit.SetItemAwayTimes("monstera", 1), day(2))
        listOf(mondayWatered, dueFromWednesday, onceFromWednesday).forEach { state ->
            val walk = habitStreak(state, nothingElse, day(7), away).history.associateBy { it.date }
            assertEquals(MoDayMark.Perfect to MoDayMark.NothingDue, walk.getValue(monday).mark to walk.getValue(day(1)).mark)
            assertEquals(1, habitStreak(state, nothingElse, day(2), away).days)
        }
        // From Wednesday the item is due every day again, or asks nothing more this week.
        assertEquals(List(5) { MoDayMark.Protected }.first(), habitStreak(dueFromWednesday, nothingElse, day(7), away).history.single { it.date == day(2) }.mark)
        assertEquals(List(5) { MoDayMark.NothingDue }, habitStreak(onceFromWednesday, nothingElse, day(7), away).history.filter { it.date in day(2)..day(6) }.map { it.mark })
    }

    private val nothingElse = showingOnly()

    /** The Monstera every day, three times a week on holiday, with a pass for every kept day so a day kept beyond the allowance would show up in the bank. */
    private val plants = ChecklistConfiguration(
        id = "chk_plants",
        label = "Plants",
        icon = ViewIcon.Plant,
        hue = ViewHue.Forest,
        checkpoints = listOf(Checkpoint("morning", CheckpointTime.Fixed(LocalTime.of(8, 0)), remind = false)),
        items = listOf(ChecklistItem("monstera", "Water Monstera", awayTimes = 3, whenAway = Dated.always(WhenAway.LowerTarget))),
        rewards = com.example.workouttracker.domain.checklist.ChecklistRewards(maxPasses = 9, everyKeptDays = 1, periods = listOf(com.example.workouttracker.domain.checklist.RewardPeriod(monday))),
    )

    private fun watered(vararg offsets: Int) = AppState(checklists = listOf(plants), checklistDays = offsets.map { ChecklistDay(plants.id, day(it).toString(), it.toLong(), setOf("morning/monstera")) })

    /** A sick day keeps the week's free day for the others, so a sick Saturday leaves Sunday free. */
    @Test fun aSickDayLeavesTheWeeksFreeDayForTheOtherDays() {
        val state = careWeek()
        val sick = HolidaySchedule().withSickDays(DayRange(day(5), day(5)))
        val nextMonday = day(7)
        assertEquals(MoDayMark.Perfect, habitStreak(state, onlyCare, nextMonday, sick).history.single { it.date == day(6) }.mark)
        assertEquals(MoStatus.Kept, MoLedger.of(state, onlyCare, nextMonday, sick).history(careRef)[day(6)])
        assertEquals(emptyList<com.example.workouttracker.domain.TrackerRef>(), moMood(state, onlyCare, day(6), sick).missingTrackersToday)
    }

    /** Protection is a game outcome: the records and every statistic stay as recorded, whatever covered the gap. */
    @Test fun protectionNeverChangesARecordedFactOrAStatistic() {
        val state = careWeek()
        val nextMonday = day(7)
        val protectedWeek = habitStreak(state, onlyCare, nextMonday)
        val sickSaturday = habitStreak(state, onlyCare, nextMonday, HolidaySchedule().withSickDays(DayRange(day(5), day(5))))
        assertTrue(protectedWeek.history != sickSaturday.history)
        // The covered Sunday recorded no cleaning.
        assertTrue(state.checklistDays.none { day -> day.ticks.any { it.endsWith(LegacyCareAction.CLEAN_BETWEEN_TEETH.storageId) } })
        StatisticsRange.entries.forEach { range ->
            val plain = statisticsSummary(state, nextMonday.toString(), range, onlyCare, null)
            listOf(protectedWeek, sickSaturday).forEach { streak ->
                val other = statisticsSummary(state, nextMonday.toString(), range, onlyCare, streak.history)
                assertEquals(plain.consistency, other.consistency)
            }
        }
    }

    /**
     * Every tick is recorded, but the streak and the bank read only the days the week asked for: a
     * once-a-week item ticked all seven days keeps one, and a week away that asks nothing keeps none.
     */
    @Test fun ticksBeyondTheWeeksTargetAdvanceNothingAndEarnNothing() {
        val nothingElse = showingOnly()
        val flossing = ChecklistConfiguration(
            id = "chk_floss",
            label = "Floss",
            icon = ViewIcon.Toothbrush,
            hue = ViewHue.Teal,
            checkpoints = listOf(Checkpoint("evening", CheckpointTime.Fixed(LocalTime.of(21, 0)), remind = false)),
            items = listOf(ChecklistItem("floss", "Floss", target = ChecklistTarget.PerWeek(times = 1))),
            // A pass for every kept day, so a day kept beyond the target would show up in the bank.
            rewards = com.example.workouttracker.domain.checklist.ChecklistRewards(maxPasses = 9, everyKeptDays = 1, periods = listOf(com.example.workouttracker.domain.checklist.RewardPeriod(monday))),
        )
        val everyDay = AppState(
            checklists = listOf(flossing),
            checklistDays = (0..6).map { ChecklistDay(flossing.id, day(it).toString(), it.toLong(), setOf("evening/floss")) },
        )
        val streak = habitStreak(everyDay, nothingElse, day(7))
        assertEquals(listOf(MoDayMark.Perfect) + List(6) { MoDayMark.NothingDue }, streak.history.filter { it.date < day(7) }.map { it.mark })
        assertEquals(1, streak.days)
        assertEquals(2, streak.standings[checklistRef(flossing.id)]?.passes)
        // The records are untouched: seven days ticked, as the screen shows them.
        assertEquals(7, everyDay.checklistDays.size)
        assertEquals(7, everyDay.progressOf(flossing, day(6).toString(), HolidaySchedule()).weekItems.single().done)

        // Away all week with nothing asked, a voluntary tick keeps no day either.
        val practice = flossing.copy(
            id = "chk_practice",
            items = listOf(ChecklistItem("violin", "Violin", target = ChecklistTarget.PerWeek(times = 5), awayTimes = 0, whenAway = Dated.always(WhenAway.LowerTarget))),
        )
        val away = HolidaySchedule(listOf(DayRange(monday, day(6))))
        val oneTick = AppState(checklists = listOf(practice), checklistDays = listOf(ChecklistDay(practice.id, monday.toString(), 1L, setOf("evening/violin"))))
        val awayStreak = habitStreak(oneTick, nothingElse, day(7), away)
        assertEquals(List(7) { MoDayMark.NothingDue }, awayStreak.history.filter { it.date < day(7) }.map { it.mark })
        assertEquals(0, awayStreak.days)
        assertEquals(1, awayStreak.standings[checklistRef(practice.id)]?.passes)
    }

    /** A checklist a person made with a week item: its free days are not judged, and its gaps are its own. */
    @Test fun aJoinedChecklistsWeekItemIsJudgedByTheWalk() {
        val practice = ChecklistConfiguration(
            id = "chk_practice",
            label = "Practice",
            icon = ViewIcon.Music,
            hue = ViewHue.Indigo,
            checkpoints = listOf(Checkpoint("evening", CheckpointTime.Fixed(LocalTime.of(21, 0)), remind = false)),
            items = listOf(ChecklistItem("violin", "Violin", target = ChecklistTarget.PerWeek(times = 5), awayTimes = 2)),
        )
        val ticked = AppState(
            checklists = listOf(practice),
            checklistDays = (0..1).map { ChecklistDay(practice.id, day(it).toString(), it.toLong(), setOf("evening/violin")) },
        )
        val joined = joinRewards(ticked, practice.id, monday)
        val nothingElse = showingOnly()
        val marks = habitStreak(joined, nothingElse, day(7)).history.filter { it.date < day(7) }.map { it.mark }
        assertEquals(
            listOf(MoDayMark.Perfect, MoDayMark.Perfect, MoDayMark.NothingDue, MoDayMark.NothingDue, MoDayMark.Protected, MoDayMark.Frozen, MoDayMark.Missed),
            marks,
        )
        // Away all week, two days are enough: the week asks nothing more.
        val away = HolidaySchedule(listOf(DayRange(monday, day(6))))
        val lowered = joined.copy(
            checklists = listOf(practice.copy(items = practice.items.map { it.copy(whenAway = Dated.always(WhenAway.LowerTarget)) }, rewards = joined.checklists.single().rewards)),
        )
        assertEquals(
            listOf(MoDayMark.Perfect, MoDayMark.Perfect) + List(5) { MoDayMark.NothingDue },
            habitStreak(lowered, nothingElse, day(7), away).history.filter { it.date < day(7) }.map { it.mark },
        )
    }
}
