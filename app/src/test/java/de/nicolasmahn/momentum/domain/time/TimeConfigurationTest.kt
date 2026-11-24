package de.nicolasmahn.momentum.domain.time

import de.nicolasmahn.momentum.domain.time.TokenCover
import de.nicolasmahn.momentum.domain.compatibility.startingTimeMotif

import de.nicolasmahn.momentum.domain.compatibility.defaultReadingCheckpoint

import de.nicolasmahn.momentum.domain.checklist.Checkpoint
import de.nicolasmahn.momentum.domain.checklist.CheckpointTime
import de.nicolasmahn.momentum.domain.checklist.Dated
import de.nicolasmahn.momentum.domain.checklist.MoMotif
import de.nicolasmahn.momentum.domain.checklist.RewardPeriod
import de.nicolasmahn.momentum.domain.checklist.ViewHue
import de.nicolasmahn.momentum.domain.checklist.ViewIcon
import de.nicolasmahn.momentum.domain.checklist.WhenAway
import de.nicolasmahn.momentum.domain.holiday.DayRange
import de.nicolasmahn.momentum.domain.holiday.HolidaySchedule
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeConfigurationTest {
    private val reading = testReadingTime()
    private val piano = testMadeTime()
    private val monday = LocalDate.parse("2026-10-05")
    private fun on(day: Int) = monday.plusDays(day.toLong())
    private val sunday = on(6)

    /** The bank a made view starts with, joined on [monday]. */
    private val joined = TimeRewards(maxPasses = 3, periods = listOf(RewardPeriod(monday)))

    private fun refused(build: () -> Any) {
        assertThrows(IllegalArgumentException::class.java) { build() }
    }

    private fun away(first: Int, last: Int = first) = HolidaySchedule().withHoliday(DayRange(on(first), on(last)))
    private fun HolidaySchedule.sick(first: Int, last: Int = first) = withSickDays(DayRange(on(first), on(last)))
    private fun TimeConfiguration.notDueAway(from: LocalDate? = null) =
        copy(whenAway = if (from == null) Dated.always(WhenAway.Excused) else Dated(listOf(Dated.Revision(WhenAway.Due), Dated.Revision(WhenAway.Excused, from))))

    /** Reading is seeded with what the code held: the book in yellow, Mo with his book, sixty minutes a week, three tokens, a token at the goal and two at twice it, still due, in the streak over its whole history. */
    @Test fun readingIsSeededWithTheConstantsItHad() {
        assertEquals("reading", reading.id)
        assertEquals("Reading", reading.label)
        assertEquals(ViewIcon.Book, reading.icon)
        assertEquals(ViewHue.Yellow, reading.hue)
        assertEquals(MoMotif.Book, reading.motif)
        assertEquals(Dated.always(60), reading.goal)
        assertEquals(Dated.always(3), reading.rewards!!.maxPasses)
        assertEquals(Dated.always(DefaultTokenSteps), reading.rewards!!.tokenSteps)
        assertNull(reading.rewards!!.periods)
        assertEquals(Dated.always(WhenAway.Due), reading.whenAway)
        assertEquals(defaultReadingCheckpoint(remind = false), reading.checkpoint)
        assertEquals(CheckpointTime.Fixed(LocalTime.of(18, 0)), reading.checkpoint.at)
        assertEquals(60, DefaultWeeklyGoalMinutes)
        assertEquals(3, DefaultTimeBankSize)
        assertEquals(TokenSteps(listOf(TokenStep(100, 1), TokenStep(200, 1))), DefaultTokenSteps)
    }

    /**
     * Mo's flavor is Plain or a motif Mo has something for while a timer runs, which today is
     * Reading alone, for Reading as for a view a person made. Reading starts on Reading and a
     * made view on Plain, and either can wear the other's.
     */
    @Test fun aTimeViewWearsPlainOrAMotifMoHasSomethingForWhileATimerRuns() {
        assertEquals(listOf(MoMotif.Book), TimerMotifs)
        assertEquals(MoMotif.Book, startingTimeMotif("reading"))
        assertNull(startingTimeMotif(piano.id))
        assertNull(piano.motif)
        assertEquals(MoMotif.Book, piano.copy(mo = piano.mo.copy(motif = MoMotif.Book)).motif)
        assertNull(reading.copy(mo = reading.mo.copy(motif = null)).motif)
        (MoMotif.entries - TimerMotifs.toSet()).forEach { motif ->
            refused { piano.copy(mo = piano.mo.copy(motif = motif)) }
            refused { reading.copy(mo = reading.mo.copy(motif = motif)) }
        }
    }

    private fun steps(vararg steps: Pair<Int, Int>) = TokenSteps(steps.map { (percent, tokens) -> TokenStep(percent, tokens) })

    /**
     * The steps are one to five: the first at the goal, each further one at 101 to 1000 % and
     * above the one before, each adding one to five tokens of its own, which need not rise.
     * Anything else cannot be built, in a list or in a bank's revision, so no wrong list is ever
     * stored. The most a week can earn is what every step adds, together.
     */
    @Test fun theStepsRefuseWhatTheyCannotBe() {
        assertEquals(1..5, TokenStepCount)
        assertEquals(100, GoalStepPercent)
        assertEquals(101..1000, FurtherStepPercent)
        assertEquals(1..5, StepTokens)
        assertEquals(Dated.always(DefaultTokenSteps), joined.tokenSteps)
        listOf(
            steps(100 to 1),
            steps(100 to 5),
            steps(100 to 1, 101 to 2),
            steps(100 to 1, 1000 to 2),
            steps(100 to 1, 150 to 2, 200 to 3, 400 to 4, 1000 to 5),
            steps(100 to 4, 1000 to 5),
            steps(100 to 1, 200 to 1),
            steps(100 to 5, 200 to 1, 300 to 5),
        ).forEach { good -> assertEquals(good, TimeRewards(maxPasses = 3, tokenSteps = good).tokenSteps.current) }
        assertEquals(listOf(1, 5, 3, 15, 9, 2, 11), listOf(steps(100 to 1), steps(100 to 5), steps(100 to 1, 101 to 2), steps(100 to 1, 150 to 2, 200 to 3, 400 to 4, 1000 to 5), steps(100 to 4, 1000 to 5), DefaultTokenSteps, steps(100 to 5, 200 to 1, 300 to 5)).map { it.total })
        assertEquals(25, steps(100 to 5, 200 to 5, 300 to 5, 400 to 5, 500 to 5).total)
        assertEquals(listOf(5, 6, 11), steps(100 to 5, 200 to 1, 300 to 5).runningTotals)
        assertEquals(listOf(1, 2), DefaultTokenSteps.runningTotals)
        listOf(
            "no step" to emptyList(),
            "the first step not at the goal" to listOf(101 to 1),
            "the first step below the goal" to listOf(50 to 1),
            "a further step at the goal" to listOf(100 to 1, 100 to 2),
            "a further step beyond ten times the goal" to listOf(100 to 1, 1001 to 2),
            "a step not above the one before" to listOf(100 to 1, 300 to 2, 200 to 3),
            "no token" to listOf(100 to 0),
            "six tokens" to listOf(100 to 6),
            "six tokens on a further step" to listOf(100 to 1, 200 to 6),
            "six steps" to listOf(100 to 1, 200 to 2, 300 to 3, 400 to 4, 500 to 5, 600 to 5),
        ).forEach { (what, wrong) ->
            assertThrows(what, IllegalArgumentException::class.java) { TokenSteps(wrong.map { (percent, tokens) -> TokenStep(percent, tokens) }) }
        }
        // A bank earns by one to five tokens a week as it used to: a step of one token at each whole goal up to the number.
        assertEquals(steps(100 to 1), tokenStepsOfTokensPerWeek(1))
        assertEquals(DefaultTokenSteps, tokenStepsOfTokensPerWeek(2))
        assertEquals(steps(100 to 1, 200 to 1, 300 to 1, 400 to 1, 500 to 1), tokenStepsOfTokensPerWeek(5))
        assertEquals(listOf(1, 2, 3, 4, 5), tokenStepsOfTokensPerWeek(5).runningTotals)
        listOf(0, 6, -2, Int.MAX_VALUE, Int.MIN_VALUE).forEach { number -> refused { tokenStepsOfTokensPerWeek(number) } }
    }

    /**
     * What a step may be beside its neighbors: its percent above the one before and below the one
     * after, its tokens its own, and one more step after the last while there is room above it in
     * count and percent, starting a hundred points above it and adding one token.
     */
    @Test fun aStepIsBoundByItsNeighborsAndOneMoreIsAddedAfterTheLast() {
        val three = steps(100 to 1, 200 to 2, 400 to 4)
        assertEquals(listOf(100..100, 101..399, 201..1000), (0..2).map(three::percentsAllowedAt))
        assertEquals(steps(100 to 1, 250 to 2, 400 to 4), three.withStep(1, TokenStep(250, 2)))
        assertEquals(steps(100 to 1, 200 to 3, 400 to 4), three.withStep(1, TokenStep(200, 3)))
        assertEquals(steps(100 to 1, 200 to 5, 400 to 4), three.withStep(1, TokenStep(200, 5)))
        assertEquals(steps(100 to 5, 200 to 2, 400 to 4), three.withStep(0, TokenStep(100, 5)))
        refused { three.withStep(1, TokenStep(400, 2)) }
        refused { three.withStep(1, TokenStep(200, 6)) }
        refused { three.withStep(0, TokenStep(150, 1)) }
        assertEquals(steps(100 to 1, 400 to 4), three.withoutStep(1))
        assertEquals(steps(100 to 1, 200 to 2), three.withoutStep(2))
        refused { three.withoutStep(0) }

        assertTrue(three.canGrow)
        assertEquals(steps(100 to 1, 200 to 2, 400 to 4, 500 to 1), three.grown())
        assertEquals(steps(100 to 1, 950 to 2, 1000 to 1), steps(100 to 1, 950 to 2).grown())
        assertTrue("five tokens are no bar", steps(100 to 5).canGrow)
        assertFalse("ten times the goal already", steps(100 to 1, 1000 to 2).canGrow)
        assertFalse("five steps already", steps(100 to 1, 200 to 2, 300 to 3, 400 to 4, 500 to 5).canGrow)
        refused { steps(100 to 1, 1000 to 2).grown() }
    }

    /** A step's minutes are its share of the goal, rounded up to a whole minute. */
    @Test fun aStepsMinutesAreItsShareOfTheGoalRoundedUp() {
        assertEquals(60, stepMinutes(100, 60))
        assertEquals(120, stepMinutes(200, 60))
        assertEquals(68, stepMinutes(150, 45))
        assertEquals(61, stepMinutes(101, 60))
        assertEquals(20, stepMinutes(333, 6))
        assertEquals(2, stepMinutes(101, 1))
        assertEquals(100_800, stepMinutes(1000, 10080))
    }

    /** A week earns by the steps its Sunday has, as it is judged by its Sunday's goal: a list saved in the middle of a week is that week's, and the week before keeps its own. */
    @Test fun aWeekEarnsByTheStepsOfItsSunday() {
        val one = steps(100 to 1)
        fun loweredOn(day: LocalDate) = joined.copy(tokenSteps = Dated(listOf(Dated.Revision(DefaultTokenSteps), Dated.Revision(one, day))))
        assertEquals(one, loweredOn(on(2)).stepsOfWeek(monday))
        assertEquals(DefaultTokenSteps, loweredOn(on(2)).stepsOfWeek(on(-7)))
        assertEquals(one, loweredOn(on(2)).stepsOfWeek(on(7)))
        assertEquals(one, loweredOn(sunday).stepsOfWeek(monday))
        assertEquals(DefaultTokenSteps, loweredOn(on(7)).stepsOfWeek(monday))
        assertThrows(IllegalArgumentException::class.java) { joined.stepsOfWeek(on(1)) }
    }

    /** A goal is whole minutes a week, from one to every minute a week has, in every revision. */
    @Test fun aGoalIsOneTo10080MinutesAWeek() {
        listOf(1, 60, 10080).forEach { goal -> assertEquals(goal, piano.copy(goal = Dated.always(goal)).goal.current) }
        listOf(0, -60, 10081).forEach { goal ->
            refused { piano.copy(goal = Dated.always(goal)) }
            refused { reading.copy(goal = Dated(listOf(Dated.Revision(60), Dated.Revision(goal, monday)))) }
        }
    }

    /** A gap is a quarter of the goal, rounded up to a whole minute: Reading's fifteen of sixty. */
    @Test fun aGapIsAQuarterOfTheGoalRoundedUp() {
        assertEquals(15, TokenCover.QuarterOfGoal.minutesOf(60))
        assertEquals(1, TokenCover.QuarterOfGoal.minutesOf(1))
        assertEquals(1, TokenCover.QuarterOfGoal.minutesOf(4))
        assertEquals(2, TokenCover.QuarterOfGoal.minutesOf(5))
        assertEquals(16, TokenCover.QuarterOfGoal.minutesOf(61))
        assertEquals(23, TokenCover.QuarterOfGoal.minutesOf(90))
        assertEquals(2520, TokenCover.QuarterOfGoal.minutesOf(10080))
    }

    /** A time view can change its reward bank and its one checkpoint through the same generic configuration. */
    @Test fun aTimeViewCanHaveOrChangeItsBankAndCheckpoint() {
        assertNull(reading.copy(rewards = null).rewards)
        refused { reading.copy(checkpoint = piano.checkpoint.copy(id = "morning")) }
        assertEquals(joined.periods, reading.copy(rewards = joined).rewards!!.periods)
        assertEquals(emptyList<RewardPeriod>(), reading.copy(rewards = TimeRewards(maxPasses = 3, periods = emptyList())).rewards!!.periods)
        assertNull(piano.copy(rewards = TimeRewards(maxPasses = 3)).rewards!!.periods)
        assertEquals(piano.checkpoint, reading.copy(checkpoint = piano.checkpoint).checkpoint)
    }

    /** The time view ID has a generic shape; optional reward periods do not depend on the ID. */
    @Test fun aTimeViewUsesGenericIdsAndOptionalBankPeriods() {
        assertEquals(joined, piano.copy(rewards = joined).rewards)
        assertEquals(emptyList<RewardPeriod>(), piano.copy(rewards = joined.copy(periods = emptyList())).rewards!!.periods)
        listOf("tim_k2p", "tim_k2p9x", "qst_k2p9", "piano", "Reading", "custom-time").forEach { id ->
            assertEquals(id, piano.copy(id = id).id)
        }
        val sameReadingWithDifferentId = reading.copy(id = piano.id)
        assertEquals(reading.goal, sameReadingWithDifferentId.goal)
        assertEquals(reading.rewards, sameReadingWithDifferentId.rewards)
        assertEquals(reading.motif, sameReadingWithDifferentId.motif)
        val checkpoint = piano.checkpoint!!
        refused { piano.copy(checkpoint = checkpoint.copy(id = "morning")) }
        refused { piano.copy(checkpoint = checkpoint.copy(label = "Evening")) }
        refused { piano.copy(checkpoint = checkpoint.copy(from = monday)) }
        refused { piano.copy(checkpoint = checkpoint.copy(until = monday)) }
        assertEquals(CheckpointTime.BeforeSleep(30), piano.copy(checkpoint = Checkpoint("day", CheckpointTime.BeforeSleep(30), remind = true)).checkpoint!!.at)
        assertEquals(LocalTime.of(9, 0), (checkpoint.at as CheckpointTime.Fixed).time)
    }

    /** Neither kind has a count a week to lower, a blank name or a blank ID, and a bank holds a pass. */
    @Test fun everyTimeViewRefusesALoweredTargetABlankNameAndAnEmptyBank() {
        listOf(reading, piano).forEach { view ->
            refused { view.copy(whenAway = Dated.always(WhenAway.LowerTarget)) }
            refused { view.copy(whenAway = Dated(listOf(Dated.Revision(WhenAway.Due), Dated.Revision(WhenAway.LowerTarget, monday)))) }
            refused { view.copy(label = " ") }
            refused { view.copy(label = " Reading") }
            refused { view.copy(id = "") }
            refused { view.copy(id = "two words") }
        }
        refused { TimeRewards(maxPasses = 0) }
        refused { TimeRewards(Dated(listOf(Dated.Revision(3), Dated.Revision(0, monday)))) }
        refused { TimeRewards(maxPasses = 3, periods = listOf(RewardPeriod(monday), RewardPeriod(on(3)))) }
        refused { TimeRewards(maxPasses = 3, periods = listOf(RewardPeriod(on(3), on(4)), RewardPeriod(monday, on(1)))) }
    }

    /** A bank takes part on the days of its periods, and Reading's on every day. */
    @Test fun aBankTakesPartInItsPeriods() {
        assertTrue(reading.rewards!!.takesPartOn(LocalDate.parse("2020-01-01")))
        val twice = TimeRewards(maxPasses = 3, periods = listOf(RewardPeriod(monday, on(2)), RewardPeriod(on(5))))
        assertEquals(listOf(true, true, true, false, false, true, true), (0..6).map { twice.takesPartOn(on(it)) })
        assertFalse(twice.takesPartOn(on(-1)))
        assertFalse(TimeRewards(maxPasses = 3, periods = emptyList()).takesPartOn(monday))
    }

    /** A week is judged by the goal its Sunday has: a goal saved in the middle of a week is that week's, and the week before keeps its own. */
    @Test fun aWeekHasTheGoalOfItsSunday() {
        val raisedOnWednesday = reading.copy(goal = Dated(listOf(Dated.Revision(60), Dated.Revision(90, on(2)))))
        assertEquals(90, raisedOnWednesday.goalOfWeek(monday))
        assertEquals(60, raisedOnWednesday.goalOfWeek(on(-7)))
        assertEquals(90, raisedOnWednesday.goalOfWeek(on(7)))
        val raisedOnSunday = reading.copy(goal = Dated(listOf(Dated.Revision(60), Dated.Revision(90, sunday))))
        assertEquals(90, raisedOnSunday.goalOfWeek(monday))
        val raisedOnTheNextMonday = reading.copy(goal = Dated(listOf(Dated.Revision(60), Dated.Revision(90, on(7)))))
        assertEquals(60, raisedOnTheNextMonday.goalOfWeek(monday))
        assertThrows(IllegalArgumentException::class.java) { reading.goalOfWeek(on(1)) }
    }

    /** A sick day is none of the week's days: a seventh less each, rounded up, as the week always asked. */
    @Test fun aWeekAsksASeventhLessForEachSickDay() {
        val none = HolidaySchedule()
        assertEquals(60, reading.weekAsks(monday, none))
        assertEquals(52, reading.weekAsks(monday, none.sick(0)))
        assertEquals(35, reading.weekAsks(monday, none.sick(0, 2)))
        assertEquals(9, reading.weekAsks(monday, none.sick(0, 5)))
        assertEquals(0, reading.weekAsks(monday, none.sick(0, 6)))
        // A sick day of another week asks nothing less of this one.
        assertEquals(60, reading.weekAsks(monday, none.sick(-1).sick(7)))
    }

    /** Still due, an away day changes nothing. Not due, it is none of the week's days, as a sick day is. */
    @Test fun anAwayDayNotDueLowersTheWeekByASeventh() {
        assertEquals(60, reading.weekAsks(monday, away(0, 6)))
        val notDue = reading.notDueAway()
        assertEquals(60, notDue.weekAsks(monday, HolidaySchedule()))
        assertEquals(52, notDue.weekAsks(monday, away(3)))
        assertEquals(35, notDue.weekAsks(monday, away(1, 3)))
        assertEquals(0, notDue.weekAsks(monday, away(0, 6)))
        assertEquals(60, notDue.weekAsks(monday, away(-3, -1)))
        assertEquals(26, testMadeTime(goal = 45).notDueAway().weekAsks(monday, away(0, 2)))
    }

    /** A day that is sick and away counts once, and the amount is rounded up once at the end. */
    @Test fun aDayThatIsSickAndAwayCountsOnceAndTheAmountIsRoundedUpOnce() {
        val notDue = reading.notDueAway()
        // Tuesday to Thursday away, Thursday and Friday sick: four days off, not five.
        assertEquals(26, notDue.weekAsks(monday, away(1, 3).sick(3, 4)))
        // One sick day and one away day: 60 · 5 / 7 rounded up once is 43; a seventh off twice, each rounded up, would be 45.
        assertEquals(43, notDue.weekAsks(monday, away(1).sick(4)))
        // Still due, only the sick days count.
        assertEquals(43, reading.weekAsks(monday, away(0, 6).sick(3, 4)))
    }

    /** Each away day is read by its own rule: one before the rule changed is still due. */
    @Test fun eachAwayDayIsReadByTheRuleOfThatDay() {
        val notDueFromThursday = reading.notDueAway(from = on(3))
        // Away all week: Monday to Wednesday were still due, Thursday to Sunday are not.
        assertEquals(26, notDueFromThursday.weekAsks(monday, away(0, 6)))
        assertEquals(60, notDueFromThursday.weekAsks(on(-7), away(-7, -1)))
        assertEquals(0, notDueFromThursday.weekAsks(on(7), away(7, 13)))
        assertFalse(notDueFromThursday.isNotDueAwayOn(on(2), away(0, 6)))
        assertTrue(notDueFromThursday.isNotDueAwayOn(on(3), away(0, 6)))
        assertFalse(notDueFromThursday.isNotDueAwayOn(on(3), HolidaySchedule()))
    }

    /** A week's days off lower the goal its Sunday has. */
    @Test fun daysOffLowerTheGoalOfTheWeeksSunday() {
        val raised = reading.copy(goal = Dated(listOf(Dated.Revision(60), Dated.Revision(90, on(4))))).notDueAway()
        assertEquals(65, raised.weekAsks(monday, away(0).sick(1)))
        assertEquals(43, raised.weekAsks(on(-7), HolidaySchedule().withHoliday(DayRange(on(-7), on(-7))).withSickDays(DayRange(on(-6), on(-6)))))
    }

    /** A sick Sunday leaves its week unjudged, and so does an away Sunday the view is not due on. */
    @Test fun aWeekIsNotJudgedOnASickSundayNorOnAnAwayOneNotDue() {
        assertTrue(reading.judgesWeekOn(sunday, HolidaySchedule()))
        assertFalse(reading.judgesWeekOn(sunday, HolidaySchedule().sick(6)))
        assertTrue(reading.judgesWeekOn(sunday, away(6)))
        assertFalse(reading.notDueAway().judgesWeekOn(sunday, away(6)))
        assertTrue(reading.notDueAway().judgesWeekOn(sunday, away(0, 5)))
        assertTrue(reading.notDueAway(from = on(7)).judgesWeekOn(sunday, away(6)))
    }

    /** A state keeps unique view IDs and preserves a valid log whose view is not configured. */
    @Test fun aStateHoldsEveryViewUnderItsOwnId() {
        requireStorableTime(listOf(reading), emptyList())
        requireStorableTime(listOf(reading, piano, piano.copy(id = "tim_aaaa")), emptyList())
        requireStorableTime(emptyList(), emptyList())
        requireStorableTime(listOf(piano), emptyList())
        refused { requireStorableTime(listOf(reading, reading.copy(label = "Books")), emptyList()) }
        refused { requireStorableTime(listOf(reading, piano, piano.copy(label = "Guitar")), emptyList()) }
        val orphanMark = TimeLog("orphan-time", "2026-10-05", 1L)
        requireStorableTime(listOf(reading), listOf(orphanMark))
    }
}
