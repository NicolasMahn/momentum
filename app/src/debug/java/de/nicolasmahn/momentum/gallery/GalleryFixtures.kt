package de.nicolasmahn.momentum.gallery

import de.nicolasmahn.momentum.domain.measurement.recordsOf

import de.nicolasmahn.momentum.domain.measurement.MeasurementRecord
import de.nicolasmahn.momentum.core.time.currentDateKey
import de.nicolasmahn.momentum.core.time.parseDateKey
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.mo.MoDayMark
import de.nicolasmahn.momentum.domain.mo.MoDayRecord
import de.nicolasmahn.momentum.domain.ActivityTrackerRef
import de.nicolasmahn.momentum.domain.TrackerRef
import de.nicolasmahn.momentum.domain.TrackerTypeId
import de.nicolasmahn.momentum.domain.trackerRef
import de.nicolasmahn.momentum.domain.mo.MoStreak
import de.nicolasmahn.momentum.domain.mo.moMood
import de.nicolasmahn.momentum.domain.settings.FeatureVisibilitySettings
import de.nicolasmahn.momentum.feature.questions.questionsHistoryWindow
import de.nicolasmahn.momentum.feature.logactivity.activityHistoryGrid
import de.nicolasmahn.momentum.feature.logactivity.activityTimelineForDate
import de.nicolasmahn.momentum.feature.time.timeHistoryWindow
import de.nicolasmahn.momentum.feature.substances.substanceHistoryWindow
import de.nicolasmahn.momentum.feature.checklist.checklistHistory
import de.nicolasmahn.momentum.domain.holiday.HolidaySchedule
import de.nicolasmahn.momentum.feature.measurement.MeasurementHistoryDayCount
import de.nicolasmahn.momentum.feature.measurement.MeasurementHistoryWindow
import de.nicolasmahn.momentum.feature.measurement.measurementHistoryWindow

/**
 * The specimens read the same invented history the "Fill app with demo data" action writes
 * ([demoHistory]), anchored on today so calendars show the same "today" marker the real screens do.
 * Every window below is built by the app's own builder, so a specimen shows the shape the real
 * screen would get.
 */
internal val FixtureTodayKey: String = currentDateKey()

private val FixtureClock = DemoDayClock(FixtureTodayKey)

internal fun fixtureDateKey(daysAgo: Int): String = FixtureClock.dateKey(daysAgo)

private fun fixtureMillis(daysAgo: Int): Long = FixtureClock.millis(daysAgo, hour = 9)

internal val fixtureAppState: AppState by lazy {
    demoHistory(FixtureTodayKey)
}

/** Every feature taken up ten days ago, for the blocks that must not count the time before. */
internal val fixtureRecentStartState: AppState by lazy {
    demoHistory(FixtureTodayKey, dayCount = 10)
}

/** The day the Activity timeline specimen shows: prefer a gym session and a logged activity together. */
internal val FixtureTimelineDateKey: String by lazy {
    fixtureAppState.sessions
        .map { it.dateKey }
        .firstOrNull { dateKey -> fixtureAppState.activityEntries.any { it.dateKey == dateKey } }
        ?: fixtureAppState.sessions.firstOrNull()?.dateKey
        ?: FixtureTodayKey
}

internal val fixtureActivityGrid by lazy {
    activityHistoryGrid(fixtureAppState, FixtureTodayKey)
}
internal val fixtureActivityTimeline by lazy {
    activityTimelineForDate(fixtureAppState, FixtureTimelineDateKey)
}
internal val fixtureEatingWindow by lazy {
    questionsHistoryWindow(fixtureAppState.questionEntries.filter { it.viewId == DemoEatingId }, FixtureTodayKey)
}
internal val fixtureReadingWindow by lazy {
    timeHistoryWindow(fixtureAppState.timeLogs.filter { it.viewId == DemoReadingId }, FixtureTodayKey)
}
internal val fixtureSubstanceWindow by lazy {
    substanceHistoryWindow(fixtureAppState.substanceLogs, FixtureTodayKey, fixtureAppState.substances)
}
internal val fixtureSupplementHistory by lazy {
    checklistHistory(fixtureAppState, DemoSupplementsChecklist, FixtureTodayKey, HolidaySchedule())
}
internal val fixtureWeightWindow by lazy {
    measurementHistoryWindow(fixtureAppState.recordsOf("demo_weight"), FixtureTodayKey)
}

/**
 * The shapes the weight chart has to survive, not just the comfortable one: an empty month, a
 * single point with no line to draw, two points, a gap wide enough to dominate the window, and a
 * reading every day.
 */
internal val fixtureWeightEdgeCases: List<Pair<String, MeasurementHistoryWindow>> by lazy {
    listOf(
        "No measurements" to fixtureWeightWindowOf(emptyList()),
        "One measurement" to fixtureWeightWindowOf(listOf(3)),
        "Two measurements" to fixtureWeightWindowOf(listOf(0, 21)),
        "A long gap" to fixtureWeightWindowOf(listOf(0, 1, 24, 25, 26)),
        "A dense month" to fixtureWeightWindowOf((0 until MeasurementHistoryDayCount).toList()),
    )
}

private fun fixtureWeightWindowOf(daysAgo: List<Int>): MeasurementHistoryWindow =
    measurementHistoryWindow(
        logs = daysAgo.mapIndexed { index, day ->
            MeasurementRecord(
                dateKey = fixtureDateKey(day),
                timestampMillis = fixtureMillis(day),
                // A visible drift, so a chart that flattens or inverts the scale shows it.
                value = 82.0 + index % 5 * 0.4,
            )
        },
        todayKey = FixtureTodayKey,
    )

/**
 * The generic calendar specimens carry no feature data, and their "today" is a fixed weekday so
 * the not-yet cells look the same whenever the gallery is opened.
 */
internal const val FixtureMidWeekTodayKey = "2026-09-16"
internal const val FixtureMondayTodayKey = "2026-09-14"

/** The streak Home walks for the demo history, which is what Statistics receives in the app. */
internal val fixtureStreak: MoStreak by lazy {
    moMood(
        fixtureAppState,
        FeatureVisibilitySettings(disabledFeatures = emptySet()),
        requireNotNull(parseDateKey(FixtureTodayKey)),
    ).streak
}

/**
 * Four weeks that hold every kind of streak day the calendar draws, since a real history rarely
 * shows a double token, a restart and a day only the demo waist's token protected at
 * once. They run from the Monday three weeks
 * before [FixtureMidWeekTodayKey] to that Wednesday, which is still open.
 */
internal val fixtureEveryStreakDay: MoStreak by lazy {
    val kept = FixtureStreakDay(MoDayMark.Perfect)
    val days = listOf(
        FixtureStreakDay(MoDayMark.NothingDue), FixtureStreakDay(MoDayMark.NothingDue), kept,
        FixtureStreakDay(MoDayMark.Perfect, rested = setOf(ActivityTrackerRef)), kept, FixtureStreakDay(MoDayMark.Missed), kept,
        kept, FixtureStreakDay(MoDayMark.Protected, setOf(DemoTask.Weight.view.trackerRef)), FixtureStreakDay(MoDayMark.Perfect, rested = setOf(ActivityTrackerRef)),
        kept, FixtureStreakDay(MoDayMark.Protected, setOf(DemoTask.Reading.view.trackerRef, DemoTask.Care.view.trackerRef, DemoTask.Eating.view.trackerRef)), kept,
        FixtureStreakDay(MoDayMark.Frozen, setOf(DemoTask.Supplements.view.trackerRef)),
        FixtureStreakDay(MoDayMark.Protected, covered = setOf(TrackerRef(TrackerTypeId("measurement"), DemoWaist.id))), kept, FixtureStreakDay(MoDayMark.Protected, setOf(DemoTask.Activity.view.trackerRef)), kept,
        FixtureStreakDay(MoDayMark.Perfect, rested = setOf(ActivityTrackerRef)), FixtureStreakDay(MoDayMark.Protected, setOf(DemoTask.Care.view.trackerRef, DemoTask.Weight.view.trackerRef)), kept,
        kept, kept, FixtureStreakDay(MoDayMark.Open),
    )
    val today = requireNotNull(parseDateKey(FixtureMidWeekTodayKey))
    val firstDay = today.minusDays(days.lastIndex.toLong())
    val history = days.mapIndexed { index, day ->
        MoDayRecord(firstDay.plusDays(index.toLong()), day.mark, coveredTrackers = day.covered, restedTrackers = day.rested)
    }
    MoStreak(
        days = 6,
        freezes = 1,
        freezeCapacity = 3,
        week = history.takeLast(3).map { it.mark } + List(4) { MoDayMark.Upcoming },
        weekCanEarnFreeze = false,
        history = history,
    )
}

private data class FixtureStreakDay(
    val mark: MoDayMark,
    val covered: Set<TrackerRef> = emptySet(),
    val rested: Set<TrackerRef> = emptySet(),
)
