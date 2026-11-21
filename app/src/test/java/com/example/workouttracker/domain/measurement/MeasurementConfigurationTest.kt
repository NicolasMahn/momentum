package com.example.workouttracker.domain.measurement

import com.example.workouttracker.domain.compatibility.defaultWeightCheckpoint

import com.example.workouttracker.domain.compatibility.WeightWhenAway

import com.example.workouttracker.domain.compatibility.WeightRewards


import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.checklist.Checkpoint
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.checklist.DefaultChecklistRewards
import com.example.workouttracker.domain.checklist.RewardPeriod
import com.example.workouttracker.domain.checklist.WhenAway
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasurementConfigurationTest {
    private val weight = testWeightMeasurement()
    private val waist = testMadeMeasurement()
    private val day = LocalDate.parse("2026-10-13")

    /** The bank a made view starts with, joined on [day]. */
    private val joined = DefaultChecklistRewards.copy(periods = listOf(RewardPeriod(day)))

    /** A unit is nothing at all, or what was typed: any printable text, spaces inside it included. */
    @Test fun aUnitIsNothingOrTextKeptAsTyped() {
        listOf("", "cm", "kg", "%", "bpm", "°C", "km / h", "kcal/day", "m²", "ml·kg⁻¹", "$", "#", "x", "1", "h:mm", "Std.", "כ״ס", "كغ", "公斤", "🍎").forEach { unit ->
            assertTrue(unit, isValidMeasurementUnit(unit))
            assertEquals(unit, waist.copy(unit = unit).unit)
        }
    }

    /** At most twelve Unicode code points, counted as characters a person sees typed, not as the UTF-16 units that store them. */
    @Test fun aUnitHasAtMostTwelveCodePoints() {
        assertTrue(isValidMeasurementUnit("twelve chars"))
        assertFalse(isValidMeasurementUnit("thirteen long"))
        val apples = "🍎".repeat(12)
        assertEquals(24, apples.length)
        assertTrue(isValidMeasurementUnit(apples))
        assertFalse(isValidMeasurementUnit(apples + "🍎"))
        assertFalse(isValidMeasurementUnit("x".repeat(13)))
        assertEquals(12, MaximumMeasurementUnitLength)
    }

    /** Stored text has no space around it; a unit of nothing but spaces is not the empty unit. */
    @Test fun aUnitHasNoSpaceAroundIt() {
        listOf(" cm", "cm ", " cm ", " ", "   ", " cm", "cm ", "cm　").forEach { unit -> assertFalse("'$unit'", isValidMeasurementUnit(unit)) }
        assertTrue(isValidMeasurementUnit("a b"))
        assertTrue(isValidMeasurementUnit("a  b"))
        assertTrue(isValidMeasurementUnit("a b"))
    }

    /** A line break, a control character and a character that only steers the reading direction are refused, wherever they stand. */
    @Test fun aUnitHoldsNoLineBreakNoControlCharacterAndNoDirectionControl() {
        val lineBreaks = listOf("\n", "\r", "\r\n", "\u000B", "\u000C", "\u0085", " ", " ")
        val controls = listOf("\u0000", "\t", "\u0007", "\u001B", "\u007F", "\u009F")
        val directionControls = listOf("؜", "‎", "‏", "‪", "‫", "‬", "‭", "‮", "⁦", "⁧", "⁨", "⁩")
        (lineBreaks + controls + directionControls).forEach { character ->
            val code = character.map { "U+%04X".format(it.code) }.joinToString(" ")
            assertFalse(code, isValidMeasurementUnit("c${character}m"))
            assertFalse(code, isValidMeasurementUnit(character))
            assertFalse(code, isValidMeasurementUnit("cm$character"))
            assertFalse(code, isValidMeasurementUnit("${character}cm"))
        }
        // Letters written right to left are text, and the characters that join or part letters and emoji stay: only the controls go.
        listOf("ק״ג", "كغ", "a‍b", "a‌b", "👨‍👩‍👧").forEach { unit -> assertTrue(unit, isValidMeasurementUnit(unit)) }
    }

    /** The configuration asks the same rule, so no measurement can hold a unit a draft would refuse. */
    @Test fun aConfigurationRefusesAUnitTheRuleRefuses() {
        listOf(" cm", "cm ", "c\nm", "c\tm", "‏cm", "thirteen long").forEach { unit ->
            assertFalse(unit, isValidMeasurementUnit(unit))
            assertThrows(unit, IllegalArgumentException::class.java) { waist.copy(unit = unit) }
        }
    }

    /** The step is what the − and + buttons add: any decimal above 0 and up to 1000 that is written with at most two decimal places. */
    @Test fun aStepIsADecimalAboveZeroAndUpToAThousandWithAtMostTwoPlaces() {
        mapOf("0.5" to 0.5, "0.05" to 0.05, "2.5" to 2.5, "1" to 1.0, "0.1" to 0.1, "0.01" to 0.01, "0.07" to 0.07, "12.34" to 12.34, "999.99" to 999.99, "1000" to 1000.0).forEach { (written, value) ->
            val step = MeasurementStep(written)
            assertEquals(written, step, MeasurementStep.parse(written))
            assertEquals(written, value, step.value, 0.0)
            assertEquals(written, written, step.written)
            assertEquals(written, step, waist.copy(step = step).step)
        }
        listOf(
            "0", "0.0", "0.00", "000", "-1", "-0.5", "+1", "1000.01", "1001", "100000000000000000000", "0.001", "0.125", "0.500", "1.000",
            "", " ", ".", "1 ", " 1", "1,5", "1.2.3", "1e2", "1E-1", "0x1", "NaN", "Infinity", "١", "0.5 kg", "abc",
        ).forEach { written ->
            assertNull(written, MeasurementStep.parse(written))
            assertThrows(written, IllegalArgumentException::class.java) { MeasurementStep(written) }
        }
    }

    /** One step is one value however it was written, and it is stored the one way: without a zero that says nothing. */
    @Test fun aStepIsOneValueHoweverItWasWritten() {
        mapOf("0.50" to "0.5", "1.0" to "1", "1.00" to "1", "007" to "7", "00.10" to "0.1", ".5" to "0.5", "5." to "5", "1000.00" to "1000", "0010" to "10").forEach { (written, stored) ->
            assertEquals(written, MeasurementStep(stored), MeasurementStep(written))
            assertEquals(written, stored, MeasurementStep(written).written)
            assertEquals(written, MeasurementStep(stored).hashCode(), MeasurementStep(written).hashCode())
        }
        assertFalse(MeasurementStep("0.5") == MeasurementStep("0.05"))
        assertEquals("2.5", MeasurementStep("2.50").toString())
    }

    /**
     * Weight starts in kilograms and steps of 0.1 and counts toward the streak with the bank a
     * week fills. Its unit and its step are its own to change, as a made measurement's are; its
     * bank, its away rule and its reminder stay Weight's.
     */
    @Test fun weightStartsInKilogramsAndStepsOfATenthAndKeepsItsBankAndItsAwayRule() {
        assertEquals("kg", weight.unit)
        assertEquals(MeasurementStep("0.1"), weight.step)
        assertEquals(DefaultMeasurementStep, weight.step)
        listOf("lb", "st", "", "KG", "Pfund").forEach { unit -> assertEquals(unit, unit, weight.copy(unit = unit).unit) }
        listOf("1", "0.5", "0.05", "2.5", "0.01").forEach { step -> assertEquals(step, MeasurementStep(step), weight.copy(step = MeasurementStep(step)).step) }
        assertThrows(IllegalArgumentException::class.java) { weight.copy(unit = "thirteen long") }
        assertEquals(MeasurementBank.Week(WeightRewards), weight.rewards)
        assertEquals(WeightRewards, weight.weekRewards)
        assertNull(weight.standardRewards)
        assertEquals(WeightWhenAway, weight.whenAway)
        assertEquals(defaultWeightCheckpoint(remind = false), weight.checkpoint)
        assertEquals(CheckpointTime.AfterWake(5), weight.checkpoint.at)
        assertNull(weight.weekRewards?.periods)
        listOf<() -> MeasurementConfiguration>(
            { weight.copy(whenAway = Dated.always(WhenAway.LowerTarget)) },
            { weight.copy(checkpoint = DefaultMeasurementCheckpoint.copy(id = "morning")) },
        ).forEach { build -> assertThrows(IllegalArgumentException::class.java) { build() } }
        // Its week bank may hold the periods it took part in, and its checkpoint may be switched and moved like any.
        assertEquals(listOf(RewardPeriod(day)), weight.copy(rewards = MeasurementBank.Week(WeightRewards.copy(periods = listOf(RewardPeriod(day))))).weekRewards?.periods)
        assertEquals(true, weight.copy(checkpoint = DefaultMeasurementCheckpoint.copy(remind = true)).checkpoint.remind)
    }

    @Test fun changingAConfiguredUnitNeverConvertsRecordedFacts() {
        val records = listOf(
            MeasurementLog(weight.id, "2026-10-13", 1L, 81.7),
            MeasurementLog(waist.id, "2026-10-13", 2L, 92.0),
        )
        val state = AppState(measurements = listOf(weight, waist), measurementLogs = records)
        listOf(weight, waist).forEach { view ->
            val updated = state.applyingMeasurementEdits(view.id, listOf(MeasurementEdit.SetUnit("lb")), day)
            assertEquals(records, updated.measurementLogs)
            assertEquals("lb", updated.measurements.single { it.id == view.id }.unit)
            assertEquals(state.measurements.single { it.id != view.id }, updated.measurements.single { it.id != view.id })
        }
    }

    /**
     * A measurement a person made starts outside the streak, still due on holiday, its reminder
     * off at 09:00. It may hold the bank a made checklist has, always with the periods it took
     * part in, an empty list once it left with no day kept; it may say an away day is not due; and
     * its reminder may be on, at any of a checkpoint's times.
     */
    @Test fun aMadeMeasurementHasTheStandardBankWithItsPeriodsOrNoneAndItsOneCheckpoint() {
        assertNull(waist.rewards)
        assertNull(waist.standardRewards)
        assertEquals(Dated.always(WhenAway.Due), waist.whenAway)
        assertEquals(Checkpoint("day", CheckpointTime.Fixed(LocalTime.of(9, 0)), remind = false), waist.checkpoint)

        val counting = waist.copy(rewards = MeasurementBank.Standard(joined), whenAway = Dated.always(WhenAway.Excused))
        assertEquals(joined, counting.standardRewards)
        assertNull(counting.weekRewards)
        assertEquals(emptyList<RewardPeriod>(), waist.copy(rewards = MeasurementBank.Standard(joined.copy(periods = emptyList()))).standardRewards?.periods)
        listOf(CheckpointTime.Fixed(LocalTime.of(21, 30)), CheckpointTime.AfterWake(5), CheckpointTime.BeforeSleep(15)).forEach { at ->
            assertEquals(at, waist.copy(checkpoint = Checkpoint("day", at, remind = true)).checkpoint?.at)
        }
    }

    /** A measurement has no count a week to lower and has exactly the checkpoint `day`, without a name or dates. */
    @Test fun aMeasurementRefusesAnInvalidAwayRuleOrCheckpointShape() {
        val checkpoint = DefaultMeasurementCheckpoint
        mapOf<String, () -> MeasurementConfiguration>(
            "a lowered target" to { waist.copy(whenAway = Dated.always(WhenAway.LowerTarget)) },
            "a lowered target from a day" to { waist.copy(whenAway = Dated(listOf(Dated.Revision(WhenAway.Due), Dated.Revision(WhenAway.LowerTarget, day)))) },
            "another checkpoint ID" to { waist.copy(checkpoint = checkpoint.copy(id = "cpt_k2p9")) },
            "a name on the checkpoint" to { waist.copy(checkpoint = checkpoint.copy(label = "Morning")) },
            "a start on the checkpoint" to { waist.copy(checkpoint = checkpoint.copy(from = day)) },
            "an end on the checkpoint" to { waist.copy(checkpoint = checkpoint.copy(until = day)) },
        ).forEach { (what, build) -> assertThrows(what, IllegalArgumentException::class.java) { build() } }
        // Names and appearance do not change the kind's structural rules.
        assertEquals("Weight", waist.copy(label = "Weight", icon = weight.icon, hue = weight.hue, unit = "kg").label)
    }

    @Test fun measurementIdentityDoesNotChooseItsRewardBank() {
        val weekly = MeasurementBank.Week(MeasurementRewards(maxPasses = 3, keptDaysInWeek = 5))
        val standard = MeasurementBank.Standard(DefaultChecklistRewards)
        val configured = waist.copy(rewards = weekly)
        val sameConfigurationWithDifferentIds = configured.copy(id = weight.id)

        assertEquals(weekly, configured.rewards)
        assertEquals(weekly, sameConfigurationWithDifferentIds.rewards)
        assertNull(configured.weekRewards?.periods)
        assertNull(sameConfigurationWithDifferentIds.weekRewards?.periods)
        assertEquals(standard, waist.copy(rewards = standard).rewards)
        assertEquals(standard, weight.copy(id = waist.id, rewards = standard).rewards)

        val noBank = weight.copy(id = waist.id, rewards = null)
        assertNull(noBank.rewards)
    }

    @Test fun valuePolicyIsExplicitAndIndependentOfIdentityOrUnit() {
        val generic = testMadeMeasurement(id = "weight", unit = "kg")
        assertEquals(MeasurementValuePolicy.Finite, generic.valuePolicy)
        listOf(-12.5, -0.0, 0.0, 12.5, -Double.MAX_VALUE, Double.MAX_VALUE).forEach { value ->
            assertTrue(isValidMeasurementValue(value))
            assertTrue(generic.acceptsValue(value))
        }
        val positive = testWeightMeasurement().copy(id = "another-id", label = "Other", unit = "")
        assertEquals(MeasurementValuePolicy.Positive, positive.valuePolicy)
        listOf(-1.0, 0.0).forEach { assertFalse(positive.acceptsValue(it)) }
        assertTrue(positive.acceptsValue(0.01))
        listOf(Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY).forEach { value ->
            assertFalse(isValidMeasurementValue(value))
            assertFalse(generic.acceptsValue(value))
            assertFalse(positive.acceptsValue(value))
        }
    }

    /** A stored state holds every measurement under an ID of its own, Weight among them or deleted. */
    @Test fun storableMeasurementsHoldNoIdTwice() {
        val mood = testMadeMeasurement(id = "msr_m7xd", label = "Mood", unit = "", step = "1")
        listOf(emptyList(), listOf(waist), listOf(waist, mood), listOf(weight), listOf(weight, waist), listOf(waist, weight, mood), listOf(mood, waist, weight)).forEach { requireStorableMeasurements(it) }
        listOf(
            listOf(weight, weight),
            listOf(weight, waist, weight.copy(label = "Evening weight")),
            listOf(weight, waist, waist),
            listOf(weight, waist, mood.copy(id = waist.id)),
        ).forEach { measurements -> assertThrows(measurements.map { it.id }.toString(), IllegalArgumentException::class.java) { requireStorableMeasurements(measurements) } }
    }
}
