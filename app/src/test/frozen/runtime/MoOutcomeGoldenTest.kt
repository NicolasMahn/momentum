package com.example.workouttracker.domain.mo

import com.example.workouttracker.domain.compatibility.history

import com.example.workouttracker.domain.compatibility.mood

import com.example.workouttracker.domain.compatibility.freeDays

import com.example.workouttracker.domain.compatibility.moFreeDays

import com.example.workouttracker.domain.compatibility.MoNeed

import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.holiday.DayRange
import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import java.io.File
import java.security.MessageDigest
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.TimeZone
import org.junit.After
import com.example.workouttracker.domain.mo.compatibility.InheritedMoEvaluation
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import com.example.workouttracker.domain.supplement.withSupplements
import com.example.workouttracker.domain.care.withCare
import com.example.workouttracker.domain.measurement.withWeight
import com.example.workouttracker.domain.questions.withEating
import com.example.workouttracker.domain.time.withReading
import com.example.workouttracker.domain.activity.withActivity

/**
 * Pins every observable outcome of Mo's evaluation (mood, streak, tokens, history, free days) over
 * generated histories, so a change made for speed can be shown to change nothing.
 *
 * The expected file holds one digest per scenario and day. When a rule changes on purpose, rerun
 * with `MO_GOLDEN_WRITE=1` to rewrite it, and review the rule change through the focused tests; the
 * full renderings of a failing run are written to `build/mo-golden/actual.txt` for diffing.
 */
class MoOutcomeGoldenTest {
    private lateinit var zone: TimeZone

    // Eating cutoffs are judged in the default zone, and the histories are written in UTC.
    @Before fun pinZone() {
        zone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After fun restoreZone() = TimeZone.setDefault(zone)

    @Test fun everyScenarioMatchesTheRecordedOutcomes() {
        val actual = scenarios.flatMap { scenario ->
            evaluatedDays.map { today -> "${scenario.name} $today" to scenario.render(today) }
        }
        val expectedFile = File("src/test/resources/mo/outcomes.sha256")
        val digests = actual.joinToString("\n", postfix = "\n") { (key, rendering) -> "$key ${sha256(rendering)}" }
        if (System.getenv("MO_GOLDEN_WRITE") == "1") {
            expectedFile.parentFile.mkdirs()
            expectedFile.writeText(digests)
        }
        File("build/mo-golden").apply { mkdirs() }.resolve("actual.txt")
            .writeText(actual.joinToString("\n") { (key, rendering) -> "$key $rendering" })
        val expected = expectedFile.readLines().filter { it.isNotBlank() }
        val mismatched = expected.zip(digests.lines()).filter { (want, got) -> want != got }.map { it.first.substringBeforeLast(' ') }
        assertEquals("Outcomes changed for: $mismatched", expected, digests.lines().filter { it.isNotBlank() })
    }

    private class Scenario(
        val name: String,
        val state: AppState,
        val visibility: FeatureVisibilitySettings = FeatureVisibilitySettings(),
        val holidays: HolidaySchedule = HolidaySchedule(),
        val rewardStart: LocalDate = DefaultRewardStart,
        val passes: (LocalDate) -> Set<MoNeed> = { emptySet() },
    ) {
        fun render(today: LocalDate): String {
            val mood = InheritedMoEvaluation(state, visibility, today, holidays, rewardStart, passes(today)).mood
            val freeDays = moFreeDays(state, visibility, today, holidays, rewardStart)
            return "${mood.outcome()} $freeDays"
        }
    }

    private companion object {
        val first: LocalDate = LocalDate.parse("2026-03-04")
        val last: LocalDate = LocalDate.parse("2026-10-25")

        /** Six weeks around the reward-rule start, the vitamin D start and the weekly care start. */
        val evaluatedDays = generateSequence(LocalDate.parse("2026-09-14")) { it.plusDays(1) }
            .takeWhile { it <= last }.toList()

        val holidays = HolidaySchedule(
            listOf(
                // Thursday to Wednesday, across a week boundary.
                DayRange(LocalDate.parse("2026-09-17"), LocalDate.parse("2026-09-23")),
                // A whole Monday-to-Sunday week.
                DayRange(LocalDate.parse("2026-10-05"), LocalDate.parse("2026-10-11")),
                DayRange(LocalDate.parse("2026-10-21"), LocalDate.parse("2026-10-21")),
            ),
        )

        fun passesOnSomeDays(day: LocalDate): Set<MoNeed> = when {
            day.dayOfMonth % 3 == 0 -> setOf(MoNeed.Weight, MoNeed.Care, MoNeed.Reading)
            day.dayOfWeek == DayOfWeek.SUNDAY -> MoNeed.entries.toSet()
            else -> emptySet()
        }

        val scenarios = listOf(
            Scenario("steady", generatedHistory(1, first, last, adherence = 0.92)),
            Scenario("patchy", generatedHistory(2, first, last, adherence = 0.6), holidays = holidays, passes = ::passesOnSomeDays),
            Scenario("holidays", generatedHistory(3, first, last, adherence = 0.85), holidays = holidays, passes = ::passesOnSomeDays),
            Scenario("someDisabled", generatedHistory(4, first, last, adherence = 0.75), visibility = only(MoNeed.Weight, MoNeed.Care, MoNeed.Eating), holidays = holidays),
            Scenario("readingAndActivityOnly", generatedHistory(5, first, last, adherence = 0.7), visibility = only(MoNeed.Reading, MoNeed.Activity)),
            Scenario("noneVisible", generatedHistory(6, first, last, adherence = 0.8), visibility = only()),
            Scenario("earlyRewardStart", generatedHistory(7, first, last, adherence = 0.8), rewardStart = LocalDate.parse("2026-08-05"), holidays = holidays),
            Scenario("legacyRulesThroughout", generatedHistory(8, first, last, adherence = 0.8), rewardStart = LocalDate.parse("2026-12-07")),
            Scenario("sparse", generatedHistory(9, LocalDate.parse("2026-09-03"), last, adherence = 0.3), passes = ::passesOnSomeDays),
            // The six configurations and no record: what a state seeded from an older version holds before anything is logged.
            Scenario("empty", AppState().withSupplements().withCare().withWeight().withEating().withReading().withActivity()),
        )

        fun sha256(text: String): String =
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
