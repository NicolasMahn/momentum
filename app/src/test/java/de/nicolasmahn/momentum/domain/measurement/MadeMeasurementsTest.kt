package de.nicolasmahn.momentum.domain.measurement

import de.nicolasmahn.momentum.domain.compatibility.madeMeasurements

import de.nicolasmahn.momentum.domain.compatibility.WeightMeasurementId

import de.nicolasmahn.momentum.domain.measurement.recordsOf

import de.nicolasmahn.momentum.domain.settings.FeatureVisibilitySettings
import de.nicolasmahn.momentum.domain.LoggedViewKind
import de.nicolasmahn.momentum.domain.LoggedViewRef
import de.nicolasmahn.momentum.domain.withoutLeftoverEntries
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.checklist.Checkpoint
import de.nicolasmahn.momentum.domain.checklist.CheckpointTime
import de.nicolasmahn.momentum.domain.checklist.Dated
import de.nicolasmahn.momentum.domain.checklist.ViewHue
import de.nicolasmahn.momentum.domain.checklist.ViewIcon
import de.nicolasmahn.momentum.domain.checklist.WhenAway
import de.nicolasmahn.momentum.domain.checklist.starterChecklist
import java.time.LocalTime
import java.time.LocalDate
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import de.nicolasmahn.momentum.domain.measurement.MeasurementConfiguration

/** A measurement a person makes: what it starts as, the ID it is given, and how it is added and deleted. */
class MadeMeasurementsTest {
    private val today = LocalDate.parse("2026-10-14")
    private val waist = testMadeMeasurement()
    private val mood = testMadeMeasurement(id = "msr_m7xd", label = "Mood", unit = "", step = "1")

    /** Outside the streak is the absence of a bank, and it starts still due on holiday with its reminder off: never Weight's values. */
    @Test fun aStarterIsTheRulerInStepsOfATenthWithoutAUnitAndOutsideTheStreak() {
        val starter = AppState().withWeight().starterMeasurement("Measurement")

        assertEquals("Measurement", starter.label)
        assertEquals(ViewIcon.Ruler, starter.icon)
        assertEquals("", starter.unit)
        assertEquals(MeasurementStep("0.1"), starter.step)
        assertNull(starter.rewards)
        assertEquals(Dated.always(WhenAway.Due), starter.whenAway)
        assertEquals(Checkpoint("day", CheckpointTime.Fixed(LocalTime.of(9, 0)), remind = false), starter.checkpoint)
        assertTrue(starter.id.matches(Regex("msr_[a-z2-9]{4}")))
    }

    /** The color is chosen as a checklist's starter chooses it, over what checklists and measurements wear together. */
    @Test fun aStarterWearsTheFirstStarterColorNoChecklistAndNoMeasurementWears() {
        val bare = AppState().withWeight()
        val first = bare.starterMeasurement("One").hue
        assertEquals(bare.starterChecklist("List", today).hue, first)
        assertTrue(first.isDeep)

        // A checklist wears the first and a measurement the second, so the third is next.
        val checklist = bare.starterChecklist("List", today)
        val withChecklist = bare.copy(checklists = listOf(checklist))
        val second = withChecklist.starterMeasurement("Two")
        assertFalse(second.hue == first)
        val third = withChecklist.addMeasurement(second).starterMeasurement("Three").hue
        assertFalse(third in setOf(first, second.hue))

        // With every color worn it takes the first, as a checklist's does.
        val everyHue = ViewHue.entries.mapIndexed { index, hue -> testMadeMeasurement(id = "msr_$index", label = "M$index").copy(hue = hue) }
        assertEquals(first, bare.copy(measurements = bare.measurements + everyHue).starterMeasurement("Next").hue)
    }

    /**
     * An ID is taken if anything names it: a configuration, or a stored record of a measurement no
     * configuration knows, whose numbers a new measurement under that ID would be handed.
     */
    @Test fun aStartersIdIsNeverOneAConfigurationOrARecordNames() {
        val state = AppState(
            measurements = listOf(testWeightMeasurement(), testMadeMeasurement(id = "msr_aaaa")),
            measurementLogs = listOf(MeasurementLog("msr_bbbb", "2026-10-13", 1L, 3.0)),
        )
        // Draws "aaaa", then "bbbb", then "cccc".
        assertEquals("msr_cccc", state.starterMeasurement("Next", random = Repeating(0, 1, 2)).id)
        assertEquals(setOf(WeightMeasurementId, "msr_aaaa", "msr_bbbb"), state.namedMeasurementIds)
    }

    /**
     * A hidden entry names an ID too. A delete whose second write failed can leave one behind, and
     * a measurement made under it would start out hidden: the starter never draws it, and adding
     * under it is refused. The entry itself hides nothing and is dropped by whoever saves the
     * visibility settings with the configurations at hand.
     */
    @Test fun anIdAHiddenEntryNamesIsTakenAndALeftoverEntryIsDropped() {
        val state = AppState(measurements = listOf(testWeightMeasurement(), testMadeMeasurement(id = "msr_aaaa")))
        val visibility = FeatureVisibilitySettings(hiddenChecklists = setOf("msr_bbbb", "chk_gone"), hiddenMeasurements = setOf("msr_aaaa", "msr_bbbb"))

        // Draws "aaaa", then "bbbb", then "cccc".
        assertEquals("msr_cccc", state.starterMeasurement("Next", hidden = visibility.namedIds(LoggedViewKind.Measurement), random = Repeating(0, 1, 2)).id)
        assertEquals("msr_bbbb", state.starterMeasurement("Next", random = Repeating(0, 1, 2)).id)
        assertThrows(IllegalArgumentException::class.java) { state.addMeasurement(testMadeMeasurement(id = "msr_bbbb"), hidden = visibility.namedIds(LoggedViewKind.Measurement)) }
        assertEquals(3, state.addMeasurement(testMadeMeasurement(id = "msr_cccc"), hidden = visibility.namedIds(LoggedViewKind.Measurement)).measurements.size)

        // The entry of the measurement that exists stays; the leftovers go, a checklist's of an ID no checklist has among them.
        assertEquals(FeatureVisibilitySettings(hiddenMeasurements = setOf("msr_aaaa")), state.withoutLeftoverEntries(visibility))
        assertEquals(FeatureVisibilitySettings(), state.withoutLeftoverEntries(FeatureVisibilitySettings()))
        // Weight is a measurement with its configuration, so its entry is no leftover; without its configuration it is one like any other.
        val weightHidden = FeatureVisibilitySettings(hiddenMeasurements = setOf(WeightMeasurementId))
        assertEquals(weightHidden, state.withoutLeftoverEntries(weightHidden))
        assertEquals(FeatureVisibilitySettings(), state.copy(measurements = state.madeMeasurements).withoutLeftoverEntries(weightHidden))
    }

    @Test fun addingRefusesAnIdAConfigurationOrARecordNames() {
        val state = AppState(measurementLogs = listOf(MeasurementLog("msr_gone", "2026-10-13", 1L, 3.0))).withWeight()

        val added = state.addMeasurement(waist)
        assertEquals(listOf(testWeightMeasurement(), waist), added.measurements)
        assertEquals(state.copy(measurements = added.measurements), added)

        assertThrows(IllegalArgumentException::class.java) { added.addMeasurement(waist) }
        assertThrows(IllegalArgumentException::class.java) { added.addMeasurement(waist.copy(label = "Another")) }
        assertThrows(IllegalArgumentException::class.java) { state.addMeasurement(testMadeMeasurement(id = "msr_gone")) }
    }

    /** One step removes the configuration and every record that names it; everything else stays as it was, in its order. */
    @Test fun deletingRemovesTheConfigurationAndEveryRecordOfItAndNothingElse() {
        val others = listOf(
            MeasurementLog(WeightMeasurementId, "2026-10-14", 9L, 82.0),
            MeasurementLog(mood.id, "2026-10-14", 9L, 7.0),
            MeasurementLog("msr_gone", "2026-10-14", 99L, 3.0),
            MeasurementLog(WeightMeasurementId, "2026-10-13", 8L, 81.4),
        )
        val waists = listOf(
            // Two alike, a second of the same day, a day after today and a value the screen would not take.
            MeasurementLog(waist.id, "2026-10-14", 4L, 92.0),
            MeasurementLog(waist.id, "2026-10-14", 4L, 92.0),
            MeasurementLog(waist.id, "2026-10-14", 5L, 92.5),
            MeasurementLog(waist.id, "2026-12-24", 6L, 90.0),
            MeasurementLog(waist.id, "2026-10-12", 3L, 0.0),
        )
        val state = AppState(
            measurements = listOf(testWeightMeasurement(), waist, mood),
            measurementLogs = listOf(others[0], waists[0], waists[1], others[1], waists[2], others[2], waists[3], others[3], waists[4]),
        )

        val deleted = state.deleteMeasurement(waist.id)

        assertEquals(listOf(testWeightMeasurement(), mood), deleted.measurements)
        assertEquals(others, deleted.measurementLogs)
        assertEquals(state.copy(measurements = deleted.measurements, measurementLogs = others), deleted)
        assertEquals(state.recordsOf("weight"), deleted.recordsOf("weight"))
    }

    /** Weight is deleted like any other, with every record of its own; a measurement nothing knows has no configuration to delete. */
    @Test fun weightIsDeletedWithItsRecordsAndAMeasurementNothingKnowsCannotBe() {
        val gone = MeasurementLog("msr_gone", "2026-10-13", 1L, 3.0)
        val state = AppState(measurementLogs = listOf(MeasurementLog(WeightMeasurementId, "2026-10-13", 2L, 80.0), gone)).withWeight()
        val withoutWeight = state.deleteMeasurement(WeightMeasurementId)
        assertEquals(emptyList<MeasurementConfiguration>(), withoutWeight.measurements)
        assertEquals(listOf(gone), withoutWeight.measurementLogs)
        assertThrows(IllegalArgumentException::class.java) { withoutWeight.deleteMeasurement(WeightMeasurementId) }
        assertThrows(IllegalArgumentException::class.java) { state.deleteMeasurement("msr_gone") }
        assertThrows(IllegalArgumentException::class.java) { state.deleteMeasurement(waist.id) }
    }

    /** The question before a delete names days: several records of one day are one day. */
    @Test fun theRecordedDaysOfAMeasurementAreDaysNotRecords() {
        val state = AppState(
            measurements = listOf(testWeightMeasurement(), waist, mood),
            measurementLogs = listOf(
                MeasurementLog(waist.id, "2026-10-14", 4L, 92.0),
                MeasurementLog(waist.id, "2026-10-14", 4L, 92.0),
                MeasurementLog(waist.id, "2026-10-14", 5L, 92.5),
                MeasurementLog(WeightMeasurementId, "2026-10-13", 8L, 81.4),
                MeasurementLog(waist.id, "2026-10-12", 3L, 91.0),
                MeasurementLog(waist.id, "2026-12-24", 6L, 90.0),
            ),
        )
        assertEquals(3, state.recordedDaysOf(waist.id))
        assertEquals(1, state.recordedDaysOf(WeightMeasurementId))
        assertEquals(0, state.recordedDaysOf(mood.id))
    }

    /** Draws each of [letters] four times in turn, so the IDs it makes are `aaaa`, `bbbb` and so on by index into the alphabet. */
    private class Repeating(private vararg val letters: Int) : Random() {
        private var drawn = 0
        override fun nextBits(bitCount: Int): Int = error("An ID is drawn a character at a time.")
        override fun nextInt(until: Int): Int = letters[(drawn++ / 4) % letters.size]
    }
}
