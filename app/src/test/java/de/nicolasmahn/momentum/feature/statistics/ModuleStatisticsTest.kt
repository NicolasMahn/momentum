package de.nicolasmahn.momentum.feature.statistics

import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.TrackerRef
import de.nicolasmahn.momentum.domain.TrackerTypeId
import de.nicolasmahn.momentum.domain.TrackerStatistics
import de.nicolasmahn.momentum.domain.TrackerStatisticsContribution
import de.nicolasmahn.momentum.feature.tracker.TestTrackerModule
import de.nicolasmahn.momentum.feature.tracker.TestTrackerView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModuleStatisticsTest {
    @Test fun unknownCounterKindContributesRecordedDaysWithoutACentralKindCase() {
        val type = TrackerTypeId("counter")
        val ref = TrackerRef(type, "glasses")
        val module = TestTrackerModule(typeId = type, views = listOf(TestTrackerView(
            ref = ref,
            statistics = TrackerStatistics(setOf("2026-10-12", "2026-10-14", "2026-10-16")),
        )))
        val contributions = module.views.mapNotNull { view ->
            view.statistics?.let { TrackerStatisticsContribution(view.ref, view.label, it) }
        }
        val summary = statisticsSummary(AppState(), "2026-10-14", StatisticsRange.FourWeeks, statisticsContributions = contributions)
        val counter = summary.consistency.single()
        assertEquals(ref, counter.ref)
        assertEquals("Water counter", counter.label)
        // Today's week is not over: it shows how far it is, and is the number until a week is over.
        val week = counter.weeks.last()
        assertEquals(2 to 3, week.kept to week.counted)
        assertEquals(2f / 7, week.fill, 0f)
        assertEquals(0, counter.counted)
        assertEquals(ConsistencyUnit.Days, counter.unit)
        assertEquals(2.0 / 7, counter.rate!!, 1e-6)
    }

    @Test fun modulesThatDoNotProvideStatisticsDoNotAcquireInferredFacts() {
        assertNull(TestTrackerView(TrackerRef(TrackerTypeId("counter"), "empty")).statistics)
        val summary = statisticsSummary(AppState(), "2026-10-14", StatisticsRange.FourWeeks)
        assertEquals(emptyList<FeatureConsistency>(), summary.consistency)
    }
}
