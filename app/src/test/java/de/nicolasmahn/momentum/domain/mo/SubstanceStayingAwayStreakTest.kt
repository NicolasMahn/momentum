package de.nicolasmahn.momentum.domain.mo

import de.nicolasmahn.momentum.domain.compatibility.kept

import de.nicolasmahn.momentum.domain.compatibility.history

import de.nicolasmahn.momentum.domain.compatibility.needs

import de.nicolasmahn.momentum.domain.compatibility.mood

import de.nicolasmahn.momentum.domain.compatibility.MoNeed

import de.nicolasmahn.momentum.domain.settings.showingOnly
import de.nicolasmahn.momentum.app.featuresLoggedOn
import de.nicolasmahn.momentum.app.substanceProgressOn
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.SubstancesTrackerRef
import de.nicolasmahn.momentum.domain.DayProgress
import de.nicolasmahn.momentum.domain.checklist.WhenAway
import de.nicolasmahn.momentum.domain.holiday.DayRange
import de.nicolasmahn.momentum.domain.holiday.HolidaySchedule
import de.nicolasmahn.momentum.domain.measurement.asMeasurementLogs
import de.nicolasmahn.momentum.domain.settings.DashboardFeature
import de.nicolasmahn.momentum.domain.substance.SubstanceEdit
import de.nicolasmahn.momentum.domain.substance.SubstanceGoal
import de.nicolasmahn.momentum.domain.substance.SubstanceKind
import de.nicolasmahn.momentum.domain.substance.SubstanceLog
import de.nicolasmahn.momentum.domain.substance.SubstanceNoneDay
import de.nicolasmahn.momentum.domain.substance.SubstanceSettings
import de.nicolasmahn.momentum.domain.substance.applyEdit
import de.nicolasmahn.momentum.domain.measurement.MeasurementRecord
import de.nicolasmahn.momentum.domain.mo.compatibility.withInheritedTrackerConfigurations
import java.time.LocalDate
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Staying away in the streak: a use of a substance stayed away from on that day is one gap, as any
 * task's gap, covered by a token, then a freeze, then the streak restarts. Everything else a day
 * holds costs nothing, a day with nothing to stay away from asks nothing, and a dated change never
 * reaches an earlier day. Logging is Block 21's, proven unchanged by [SubstanceStreakTest].
 */
class SubstanceStayingAwayStreakTest {
    private val monday = LocalDate.parse("2026-09-28")
    private val friday = monday.plusDays(4)
    private fun visible(vararg needs: MoNeed, substances: Boolean = true) = showingOnly(*needs, features = if (substances) setOf(DashboardFeature.Substances) else emptySet())
    private val substancesOnly = visible()

    /** Staying away from cannabis and alcohol since Monday; tobacco is just tracked. */
    private val stayingAway = SubstanceSettings.Legacy
        .applyEdit(SubstanceEdit.SetGoal(SubstanceGoal.StayingAway), monday)
        .applyEdit(SubstanceEdit.StayAway(SubstanceKind.TOBACCO.id, false), monday)
    private val checkInRule = stayingAway.applyEdit(SubstanceEdit.SetExceptionsOnly(false), monday)

    // Substances is about yesterday: each helper takes the day that judges a record and dates the
    // record the day before, the day it is about.
    private fun use(day: LocalDate, substance: SubstanceKind, amount: Double? = null) =
        SubstanceLog("use-$day-${substance.id}", substance.id, day.minusDays(1).toString(), 1L, amount, amount?.let { substance.unit })

    private fun none(day: LocalDate) = SubstanceNoneDay(day.minusDays(1).toString(), 1L)

    /**
     * Under exceptions only, what a day holds beyond a staying-away use never matters: generated
     * histories where the other days hold a use of tobacco, "none" or nothing at all, in any mix,
     * give identical streaks, tokens, Home badges and Mo moods on every day, sick and away days
     * included.
     */
    @Test fun underExceptionsOnlyAJustTrackedUseNoneAndAnEmptyDayAreJudgedIdentically() {
        val random = Random(22)
        val days = (0 until 42).map { monday.plusDays(it.toLong()) }
        repeat(40) {
            val slips = days.filter { random.nextInt(5) == 0 }
            val marked = days.filter { random.nextBoolean() }
            val weights = days.filter { random.nextInt(3) > 0 }.map { MeasurementRecord(it.toString(), 1L, 80.0) }
            val away = DayRange(days[random.nextInt(20)], days[20 + random.nextInt(8)])
            val sick = DayRange(days[28 + random.nextInt(6)], days[34 + random.nextInt(6)])
            val daysOff = HolidaySchedule(listOf(away)).withSickDays(sick)
            val visibility = visible(MoNeed.Weight)
            val settings = if (random.nextBoolean()) stayingAway else stayingAway.applyEdit(SubstanceEdit.SetWhenAway(WhenAway.Excused), monday)
            val empty = AppState(
                substances = settings,
                measurementLogs = weights.asMeasurementLogs("weight"),
                substanceLogs = slips.map { use(it, if (random.nextBoolean()) SubstanceKind.CANNABIS else SubstanceKind.ALCOHOL, if (random.nextBoolean()) random.nextDouble(0.5, 9.0) else null) },
            ).withInheritedTrackerConfigurations()
            val tobacco = empty.copy(substanceLogs = empty.substanceLogs + marked.map { use(it, SubstanceKind.TOBACCO, random.nextDouble(1.0, 20.0)) })
            val nones = empty.copy(substanceNoneDays = marked.filterNot { it in slips }.map(::none))
            val mixed = empty.copy(
                substanceLogs = empty.substanceLogs + marked.filterIndexed { index, _ -> index % 2 == 0 }.map { use(it, SubstanceKind.TOBACCO) },
                substanceNoneDays = marked.filterIndexed { index, day -> index % 2 == 1 && day !in slips }.map(::none),
            )
            days.forEach { today ->
                val expected = habitStreak(empty, visibility, today, daysOff)
                listOf(tobacco, nones, mixed).forEach { other ->
                    val streak = habitStreak(other, visibility, today, daysOff)
                    assertEquals("streak on $today", expected, streak)
                    assertEquals("mood on $today", moMood(empty, visibility, today, daysOff).outcome(), moMood(other, visibility, today, daysOff).outcome())
                    assertEquals("badge on $today", substanceProgressOn(expected), substanceProgressOn(streak))
                    assertEquals("home on $today", featuresLoggedOn(empty, today.toString(), daysOff), featuresLoggedOn(other, today.toString(), daysOff))
                }
            }
        }
    }

    /** A staying-away use is one gap, however many uses or substances the day holds; a use of a substance only tracked costs nothing. */
    @Test fun aStayingAwayUseIsOneGapHoweverManyThereAre() {
        val tuesday = monday.plusDays(1)
        val oneUse = AppState(substances = stayingAway, substanceLogs = listOf(use(tuesday, SubstanceKind.CANNABIS)))
        val manyUses = oneUse.copy(substanceLogs = listOf(use(tuesday, SubstanceKind.CANNABIS, 9.0), use(tuesday, SubstanceKind.ALCOHOL, 12.0), use(tuesday, SubstanceKind.TOBACCO, 20.0)))
        val tobaccoOnly = oneUse.copy(substanceLogs = listOf(use(tuesday, SubstanceKind.TOBACCO, 20.0)))

        val one = habitStreak(oneUse, substancesOnly, friday)
        assertEquals(one, habitStreak(manyUses, substancesOnly, friday))
        // Tuesday spent the starting token; today is kept from the start of the day.
        assertEquals(listOf(MoDayMark.Perfect, MoDayMark.Protected, MoDayMark.Perfect, MoDayMark.Perfect, MoDayMark.Perfect, MoDayMark.Upcoming, MoDayMark.Upcoming), one.week)
        assertEquals(4, one.days)
        assertEquals(MoStreakStanding(passes = 0, passCapacity = 3, nextToken = MoTokenProgress(4, 7), openToday = false, keptToday = true), one.standings[SubstancesTrackerRef])
        assertEquals(DayProgress(done = 1, parts = 1), substanceProgressOn(one))

        val clean = habitStreak(tobaccoOnly, substancesOnly, friday)
        assertEquals(List(5) { MoDayMark.Perfect } + MoDayMark.Upcoming + MoDayMark.Upcoming, clean.week)
        assertEquals(1, requireNotNull(clean.standings[SubstancesTrackerRef]).passes)

        // Today: a use makes it a gap, and removing the use brings it back.
        val slipToday = habitStreak(tobaccoOnly.copy(substanceLogs = tobaccoOnly.substanceLogs + use(friday, SubstanceKind.ALCOHOL)), substancesOnly, friday)
        assertEquals(MoDayMark.Open, slipToday.week[4])
        assertEquals(MoStreakStanding(passes = 1, passCapacity = 3, nextToken = MoTokenProgress(4, 7), openToday = true, keptToday = false), slipToday.standings[SubstancesTrackerRef])
        assertEquals(DayProgress(done = 0, parts = 1), substanceProgressOn(slipToday))
    }

    /** With the check-in rule an empty day is a gap too: "none" or a tobacco use keeps the day, a staying-away use still does not, and a gap costs a token, then a freeze. */
    @Test fun withTheCheckInRuleAnEmptyDayIsAGap() {
        val state = AppState(
            substances = checkInRule,
            substanceLogs = listOf(use(monday, SubstanceKind.TOBACCO), use(monday.plusDays(2), SubstanceKind.CANNABIS)),
            substanceNoneDays = listOf(none(monday.plusDays(1))),
        )

        val streak = habitStreak(state, substancesOnly, friday)
        assertEquals(listOf(MoDayMark.Perfect, MoDayMark.Perfect, MoDayMark.Protected, MoDayMark.Frozen, MoDayMark.Open, MoDayMark.Upcoming, MoDayMark.Upcoming), streak.week)
        assertEquals(0, streak.freezes)
        assertEquals(MoStreakStanding(passes = 0, passCapacity = 3, nextToken = MoTokenProgress(2, 7), openToday = true, keptToday = false), streak.standings[SubstancesTrackerRef])
        assertEquals(DayProgress(done = 0, parts = 1), substanceProgressOn(streak))
        // One more empty day, and the streak restarts.
        assertEquals(0, habitStreak(state, substancesOnly, friday.plusDays(1)).days)

        // Under exceptions only the same records keep Thursday and today.
        val assumed = habitStreak(state.copy(substances = stayingAway), substancesOnly, friday)
        assertEquals(listOf(MoDayMark.Perfect, MoDayMark.Perfect, MoDayMark.Protected, MoDayMark.Perfect, MoDayMark.Perfect, MoDayMark.Upcoming, MoDayMark.Upcoming), assumed.week)
    }

    /**
     * A day on which the stance names no substance takes no part: it is neither kept nor a gap,
     * earns nothing and is never Perfect, also while Substances is the only participant, and its
     * bank is out of sight. Beside Weight the days are Weight's alone, exactly as with Substances
     * off. Once a substance is chosen, the days from then on are judged and the earlier use is not.
     */
    @Test fun aDayWithNothingStayedAwayFromTakesNoPart() {
        val nothing = SubstanceSettings.Legacy.tracked.fold(SubstanceSettings.Legacy.applyEdit(SubstanceEdit.SetGoal(SubstanceGoal.StayingAway), monday)) { settings, id ->
            settings.applyEdit(SubstanceEdit.StayAway(id, false), monday)
        }
        val state = AppState(substances = nothing, substanceLogs = listOf(use(monday, SubstanceKind.CANNABIS)), substanceNoneDays = listOf(none(monday.plusDays(1))))

        val alone = habitStreak(state, substancesOnly, friday)
        assertEquals(List(5) { MoDayMark.NothingDue } + MoDayMark.Upcoming + MoDayMark.Upcoming, alone.week)
        assertEquals(0, alone.days)
        assertNull(alone.standings[SubstancesTrackerRef])
        assertNull(substanceProgressOn(alone))
        assertTrue(alone.history.none { it.mark == MoDayMark.Perfect })

        val weights = (0L..4L).map { MeasurementRecord(monday.plusDays(it).toString(), 1L, 80.0) }
        val both = visible(MoNeed.Weight)
        assertEquals(habitStreak(state.copy(measurementLogs = weights.asMeasurementLogs("weight"), substances = SubstanceSettings.Legacy).withInheritedTrackerConfigurations(), both, friday), habitStreak(state.copy(measurementLogs = weights.asMeasurementLogs("weight")).withInheritedTrackerConfigurations(), both, friday))

        val wednesday = monday.plusDays(2)
        val chosen = habitStreak(state.copy(substances = nothing.applyEdit(SubstanceEdit.StayAway(SubstanceKind.CANNABIS.id, true), wednesday)), substancesOnly, friday)
        // The stance chosen from Wednesday is about Wednesday on, which Thursday judges first.
        assertEquals(listOf(MoDayMark.NothingDue, MoDayMark.NothingDue, MoDayMark.NothingDue, MoDayMark.Perfect, MoDayMark.Perfect, MoDayMark.Upcoming, MoDayMark.Upcoming), chosen.week)
        assertEquals(MoStreakStanding(passes = 1, passCapacity = 3, nextToken = MoTokenProgress(2, 7), openToday = false, keptToday = true), chosen.standings[SubstancesTrackerRef])
    }

    /**
     * Changing the goal, the stance, untracking a substance, the check-in rule or leaving never
     * re-judges an earlier day: with generated records, every day before the change keeps the mark
     * it had, whatever the change.
     */
    @Test fun aDatedChangeNeverReJudgesAnEarlierDay() {
        val change = monday.plusDays(14)
        val days = (0 until 28).map { monday.plusDays(it.toLong()) }
        val random = Random(5)
        val logs = days.filter { random.nextInt(3) == 0 }.map { use(it, listOf(SubstanceKind.CANNABIS, SubstanceKind.ALCOHOL, SubstanceKind.TOBACCO, SubstanceKind.COFFEE).random(random)) }
        val nones = days.filter { day -> random.nextInt(4) == 0 && logs.none { it.dateKey == day.toString() } }.map(::none)
        val before = AppState(substances = stayingAway, substanceLogs = logs, substanceNoneDays = nones)
        val settled = habitStreak(before, substancesOnly, change).history.filter { it.date < change }
        assertTrue(settled.any { it.mark == MoDayMark.Protected || it.mark == MoDayMark.Frozen || it.mark == MoDayMark.Missed })

        listOf(
            stayingAway.applyEdit(SubstanceEdit.SetGoal(SubstanceGoal.Logging), change),
            stayingAway.applyEdit(SubstanceEdit.StayAway(SubstanceKind.CANNABIS.id, false), change),
            stayingAway.applyEdit(SubstanceEdit.StayAway(SubstanceKind.TOBACCO.id, true), change),
            stayingAway.applyEdit(SubstanceEdit.Track(SubstanceKind.ALCOHOL.id, false), change),
            stayingAway.applyEdit(SubstanceEdit.SetExceptionsOnly(false), change),
            stayingAway.applyEdit(SubstanceEdit.SetGoal(null), change),
            stayingAway.applyEdit(SubstanceEdit.StayAway(SubstanceKind.CANNABIS.id, false), change).applyEdit(SubstanceEdit.StayAway(SubstanceKind.ALCOHOL.id, false), change),
        ).forEach { changed ->
            val after = habitStreak(before.copy(substances = changed), substancesOnly, change.plusDays(7))
            assertEquals("$changed", settled, after.history.filter { it.date < change })
        }
    }

    /** The tokens set on the page are the ones the walk uses: the bank's size and how many kept days earn one, from the day they are set. */
    @Test fun theTokensSetOnThePageAreTheOnesTheWalkUses() {
        val bigger = stayingAway.applyEdit(SubstanceEdit.SetRewardRules(5, 2), monday)

        val streak = habitStreak(AppState(substances = bigger), substancesOnly, friday)
        // Four kept days by Thursday earned two tokens beside the starting one; today counts toward the next.
        assertEquals(MoStreakStanding(passes = 3, passCapacity = 5, nextToken = MoTokenProgress(1, 2), openToday = false, keptToday = true), streak.standings[SubstancesTrackerRef])

        // A smaller bank from Wednesday clamps from that day on; the earlier days keep their rule.
        val smaller = bigger.applyEdit(SubstanceEdit.SetRewardRules(2, 2), monday.plusDays(2))
        val clamped = habitStreak(AppState(substances = smaller), substancesOnly, friday)
        assertEquals(2 to 2, requireNotNull(clamped.standings[SubstancesTrackerRef]).let { it.passes to it.passCapacity })
    }
}
