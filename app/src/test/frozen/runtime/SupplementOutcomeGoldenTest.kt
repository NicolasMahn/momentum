package com.example.workouttracker.domain.supplement

import com.example.workouttracker.domain.compatibility.StandardSupplementIds
import com.example.workouttracker.domain.compatibility.supplementsChecklist
import com.example.workouttracker.domain.compatibility.SupplementsCheckpointId
import com.example.workouttracker.domain.compatibility.SupplementsChecklistId
import com.example.workouttracker.domain.compatibility.WeightMeasurementId

import com.example.workouttracker.domain.settings.hidingTasks
import com.example.workouttracker.domain.settings.showingOnly
import com.example.workouttracker.core.text.UiText
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.CheckpointPart
import com.example.workouttracker.domain.TrackerRef
import com.example.workouttracker.domain.TrackerTypeId
import com.example.workouttracker.domain.trackerRef
import com.example.workouttracker.domain.checklist.ChecklistConfiguration
import com.example.workouttracker.domain.checklist.ChecklistItem
import com.example.workouttracker.domain.checklist.Checkpoint
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.checklist.isCompleteFor
import com.example.workouttracker.domain.checklist.rulesOn
import com.example.workouttracker.domain.mo.outcome
import com.example.workouttracker.domain.mo.outcomeText
import com.example.workouttracker.domain.mo.DefaultRewardStart
import com.example.workouttracker.domain.mo.MoDayMark
import com.example.workouttracker.domain.mo.MoDayRecord
import com.example.workouttracker.domain.mo.MoLedger
import com.example.workouttracker.domain.mo.MoMood
import com.example.workouttracker.domain.compatibility.MoNeed
import com.example.workouttracker.domain.mo.MoState
import com.example.workouttracker.domain.mo.MoStreak
import com.example.workouttracker.domain.mo.generatedHistory
import com.example.workouttracker.domain.compatibility.moAsked
import com.example.workouttracker.domain.compatibility.moOverdue
import com.example.workouttracker.domain.mo.compatibility.InheritedDayReport
import com.example.workouttracker.domain.mo.compatibility.InheritedMoEvaluation
import com.example.workouttracker.domain.reminder.MoCheckInSettings
import com.example.workouttracker.domain.reminder.ReminderSettings
import com.example.workouttracker.domain.reminder.ReminderTime
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import com.example.workouttracker.feature.statistics.StatisticsRange
import com.example.workouttracker.feature.statistics.StatisticsSummary
import com.example.workouttracker.feature.statistics.statisticsSummary
import com.example.workouttracker.persistence.TrackerRepository
import com.example.workouttracker.persistence.TrackerUpdateResult
import com.example.workouttracker.domain.checklist.progressOf
import com.example.workouttracker.reminder.ChecklistReminderCoordinator
import com.example.workouttracker.reminder.ChecklistReminderGateway
import com.example.workouttracker.reminder.ChecklistReminderRuntime
import com.example.workouttracker.reminder.FeatureVisibilitySource
import com.example.workouttracker.reminder.ReminderDays
import com.example.workouttracker.reminder.ReminderSlot
import com.example.workouttracker.reminder.ReminderViewKind
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalTime
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.example.workouttracker.domain.holiday.HolidaySchedule

/**
 * Pins every Supplements outcome the shared checklist takes over: what is due and complete on each
 * day, whether the reminder still speaks, when Mo expects the pills, and the streak, tokens and
 * statistics over histories that cross the Vitamin D season. It was recorded on the code before the
 * checklist existed, so a pass proves the migration changed none of them.
 *
 * Rewrite it only for a deliberate rule change, with `SUPPLEMENTS_GOLDEN_WRITE=1`; the renderings of
 * a run are written to `build/supplements-golden/actual.txt` for diffing.
 */
class SupplementOutcomeGoldenTest {
    private lateinit var zone: TimeZone

    // Eating cutoffs are judged in the default zone, and the histories are written in UTC.
    @Before fun pinZone() {
        zone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After fun restoreZone() = TimeZone.setDefault(zone)

    @Test fun everySupplementsOutcomeMatchesTheRecordedOne() {
        val actual = days(LocalDate.parse("2025-01-01"), LocalDate.parse("2027-12-31")).flatMap { day ->
            listOf("rules $day" to rules(day), "reminder $day" to reminder(day))
        } + moDueTimes() + histories()
        val expectedFile = File("src/test/resources/supplements/outcomes.sha256")
        val digests = actual.map { (key, rendering) -> "$key ${sha256(rendering)}" }
        if (System.getenv("SUPPLEMENTS_GOLDEN_WRITE") == "1") {
            expectedFile.parentFile.mkdirs()
            expectedFile.writeText(digests.joinToString("\n", postfix = "\n"))
        }
        File("build/supplements-golden").apply { mkdirs() }.resolve("actual.txt")
            .writeText(actual.joinToString("\n") { (key, rendering) -> "$key $rendering" })
        val expected = expectedFile.readLines().filter { it.isNotBlank() }
        val mismatched = expected.zip(digests).filter { (want, got) -> want != got }.map { it.first.substringBeforeLast(' ') }
        assertEquals("Outcomes changed for: ${mismatched.take(20)}", expected, digests)
    }

    /**
     * What is due, and whether each tick set completes the day, in one record or split over two. An
     * empty set is no record at all, as a stored day never has one without ticks.
     */
    private fun rules(day: LocalDate): String {
        val key = day.toString()
        val due = supplements.rulesOn(day).checkpoints.flatMap { checkpoint -> checkpoint.items.map { it.id } }
        return tickSets.joinToString(" ", prefix = "${due.sorted()} ") { ticks ->
            val single = records(key, 1L, ticks)
            val split = ticks.partition { it.length % 2 == 0 }.toList().flatMap { records(key, 2L, it.toSet()) }
            val completedNow = completedBy(supplementsState(), supplementsState(*single.toTypedArray()), key)
            "${supplements.isCompleteFor(single, key, HolidaySchedule()).bit}${supplements.isCompleteFor(split, key, HolidaySchedule()).bit}${completedNow.bit}"
        }
    }

    /**
     * Whether a save of [after] over [before] left nothing owed on [dateKey] where something was,
     * which once took Supplements' own notification down: it is answered as any checklist's now,
     * and this keeps what the recorded outcomes were made with.
     */
    private fun completedBy(before: AppState, after: AppState, dateKey: String): Boolean {
        val checklist = after.supplementsChecklist ?: return false
        return before.progressOf(checklist, dateKey, HolidaySchedule()).isOwing && !after.progressOf(checklist, dateKey, HolidaySchedule()).isOwing
    }

    /** Whether the reminder notifies, for every tick set, with the switch on, off and the view hidden. */
    private fun reminder(day: LocalDate, change: (AppState) -> AppState = { it }): String {
        val key = day.toString()
        fun shows(ticks: Set<String>, enabled: Boolean, hidden: Boolean): Boolean {
            var shown = false
            ChecklistReminderCoordinator(
                trackerRepository = FixedState(change(supplementsState(*records(key, 1L, ticks).toTypedArray(), remind = enabled))),
                featureVisibility = FeatureVisibilitySource {
                    if (hidden) FeatureVisibilitySettings(disabledFeatures = emptySet()).hidingTasks(MoNeed.Supplements) else FeatureVisibilitySettings()
                },
                gateway = AcceptingGateway,
                runtime = ForgettingRuntime,
                notifier = { _, _, _, _, _, _ -> shown = true; true },
                loggedNotifier = { _, _, _, _ -> true },
                reminderSettings = { ReminderSettings() },
                todayKey = { key },
            ).onReminderDue(ReminderViewKind.Checklist, SupplementsChecklistId, SupplementsCheckpointId)
            return shown
        }
        return tickSets.joinToString("") { ticks ->
            "${shows(ticks, true, false).bit}${shows(ticks, false, false).bit}${shows(ticks, true, true).bit}"
        }
    }

    /** When Mo acts out, fumes about and nudges over open pills, every minute of the day. */
    private fun moDueTimes(): List<Pair<String, String>> =
        listOf(true, false).flatMap { enabled ->
            listOf(ReminderTime(20, 0), ReminderTime(12, 0), ReminderTime(3, 30), ReminderTime(23, 59)).map { checkIn ->
                val reminders = ReminderSettings(
                    moCheckIn = MoCheckInSettings(fixedTime = checkIn),
                )
                val missing = openMood(remind = enabled)
                val rendering = (0 until 24 * 60).joinToString(";") { minuteOfDay ->
                    val now = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
                    "${moAsked(missing, reminders, now)}${moOverdue(missing, reminders, now)}"
                }
                "mo $enabled ${checkIn.hour}:${checkIn.minute}" to rendering
            }
        }

    /**
     * A second checklist with its own records, reminders and a hidden twin changes nothing about
     * Supplements, and removing its configuration again leaves Supplements as it was.
     */
    @Test fun aSecondChecklistChangesNoSupplementsOutcome() {
        val second = ChecklistConfiguration(
            id = "chk_second",
            label = "Medication",
            icon = ViewIcon.Checklist,
            hue = ViewHue.Sky,
            checkpoints = listOf(Checkpoint("midday", CheckpointTime.AfterWake(30), remind = true)),
            // Same item IDs and checkpoint ID as Supplements, so nothing can be told apart by them alone.
            items = listOf(ChecklistItem(StandardSupplementIds.CREATINE, "Creatine"), ChecklistItem("itm_iron", "Iron")),
        )
        fun withSecond(state: AppState) = state.copy(
            checklists = state.checklists + second,
            checklistDays = state.checklistDays + state.checklistDays.map { it.copy(checklistId = second.id, ticks = setOf("midday/creatine", "midday/itm_iron")) },
        )
        fun removed(state: AppState) = withSecond(state).let { it.copy(checklists = it.checklists - second) }
        val sample = generatedHistory(21, LocalDate.parse("2026-07-01"), LocalDate.parse("2027-04-20"), adherence = 0.93)
        val secondRef = TrackerRef(TrackerTypeId("checklist"), second.id)
        val secondRow = statisticsSummary(withSecond(sample), "2026-09-25", StatisticsRange.FourWeeks)
            .consistency.single { it.ref == secondRef }
        assertTrue("the added checklist keeps its own statistics", secondRow.kept > 0)
        // A new checklist should publish its own statistics, while leaving Supplements' recorded
        // history unchanged. The golden below still pins every tracker that existed when it was made.
        val plain = histories(statistics = HistoryStatistics.SupplementsOnly)
        assertEquals(plain, histories(::withSecond, HistoryStatistics.SupplementsOnly))
        assertEquals(plain, histories(::removed, HistoryStatistics.SupplementsOnly))
        days(LocalDate.parse("2026-09-25"), LocalDate.parse("2026-10-05")).forEach { day ->
            assertEquals(reminder(day), reminder(day, ::withSecond))
        }
    }

    /** The streak, tokens, mood, Supplements' ledger and statistics on days around both season edges. */
    private fun histories(
        change: (AppState) -> AppState = { it },
        statistics: HistoryStatistics = HistoryStatistics.All,
    ): List<Pair<String, String>> {
        val first = LocalDate.parse("2026-07-01")
        val last = LocalDate.parse("2027-04-20")
        val scenarios = listOf(
            "steady" to generatedHistory(21, first, last, adherence = 0.93),
            "patchy" to generatedHistory(22, first, last, adherence = 0.6),
            "sparse" to generatedHistory(23, first, last, adherence = 0.3),
        )
        val evaluated = days(LocalDate.parse("2026-09-24"), LocalDate.parse("2026-10-09")) +
            days(LocalDate.parse("2027-03-26"), LocalDate.parse("2027-04-08"))
        val visibilities = listOf("all" to FeatureVisibilitySettings(), "supplementsOnly" to onlySupplements)
        return scenarios.flatMap { (name, original) ->
            val state = change(original)
            visibilities.flatMap { (shown, visibility) ->
                evaluated.map { today ->
                    val evaluation = InheritedMoEvaluation(state, visibility, today, rewardStart = LocalDate.parse("2027-12-01"))
                    val mood = InheritedMoEvaluation(state, visibility, today, rewardStart = DefaultRewardStart).mood
                    val legacy = evaluation.streak
                    val ledger = MoLedger.of(state, visibility, today).history(MoNeed.Supplements.view.trackerRef)
                    val statisticsText = StatisticsRange.entries.map { range ->
                        val summary = statisticsSummary(state, today.toString(), range, visibility, inheritedHistory(legacy.history))
                        val historicalRows = when (statistics) {
                            HistoryStatistics.All -> summary
                            HistoryStatistics.SupplementsOnly -> summary.copy(
                                consistency = summary.consistency.filter { it.ref == supplementsStatisticsRef },
                            )
                        }
                        historicalStatistics(historicalRows)
                    }
                    "history $name $shown $today" to
                        "${mood.outcome()} ${outcomeText(legacy)} $ledger ${outcomeText(statisticsText)}"
                }
            }
        }
    }

    /** The recorded digest predates tracker-keyed statistics and pins only Mo's inherited trackers. */
    private fun historicalStatistics(summary: StatisticsSummary): String {
        val rowsByRef = summary.consistency.associateBy { it.ref }
        val consistency = historicalFeatureOrder.mapNotNull { need ->
            rowsByRef[need.view.trackerRef]?.let { "FeatureConsistency(feature=$need, weeks=${it.weeks})" }
        }
        val weight = summary.measurements
            .singleOrNull { it.configuration.id == WeightMeasurementId }
            ?.let { "WeightSummary(window=${outcomeText(it.window)}, latestKg=${it.latestValue}, changeKg=${it.change})" }
        val activity = summary.activity?.let {
            "ActivitySummary(days=${outcomeText(it.days)}, " +
                "consistency=FeatureConsistency(feature=${MoNeed.Activity}, weeks=${it.consistency.weeks}), " +
                "kinds=${outcomeText(it.kinds)})"
        }
        val streak = summary.streak?.let { calendar ->
            val days = calendar.days.map { day ->
                val tokens = day.tokens.mapNotNull { ref -> MoNeed.entries.firstOrNull { it.view.trackerRef == ref } }
                "StreakCalendarDay(dateKey=${day.dateKey}, mark=${day.mark}, tokens=$tokens, rested=${day.rested}, restart=${day.restart})"
            }
            "StreakCalendar(days=$days)"
        }
        return "StatisticsSummary(range=${summary.range}, startDateKey=${summary.startDateKey}, " +
            "consistency=$consistency, weight=$weight, activity=$activity, streak=$streak)"
    }

    private object AcceptingGateway : ChecklistReminderGateway {
        override fun canScheduleExact() = true
        override fun shown() = emptySet<String>()
        override fun schedule(slot: ReminderSlot, earliestDate: LocalDate?, fallbackAlarmTime: ReminderTime, fixedBedtime: ReminderTime?) = true
        override fun cancel(key: String) = Unit
        override fun dismiss(key: String) = Unit
        override fun keepUnsaved(key: String, why: UiText) = false
    }

    private object ForgettingRuntime : ChecklistReminderRuntime {
        override fun scheduled() = emptySet<String>()
        override fun record(keys: Set<String>) = Unit
        override fun posted() = emptyMap<String, ReminderDays>()
        override fun posted(key: String, days: ReminderDays) = Unit
        override fun dismissed(key: String) = Unit
        override fun earliest() = emptyMap<String, LocalDate>()
        override fun earliest(key: String, day: LocalDate) = Unit
    }

    private class FixedState(private val state: AppState) : TrackerRepository {
        override fun load(): AppState = state
        override fun save(state: AppState): Boolean = error("read only")
        override fun update(transform: (AppState) -> AppState): TrackerUpdateResult = error("read only")
    }

    private companion object {
        val supplementsStatisticsRef = TrackerRef(TrackerTypeId("checklist"), SupplementsChecklistId)
        val historicalFeatureOrder = listOf(MoNeed.Weight, MoNeed.Supplements, MoNeed.Reading, MoNeed.Care, MoNeed.Eating)

        /** Every subset of the three items and one ID no configuration knows. */
        val tickSets: List<Set<String>> = listOf(
            StandardSupplementIds.MULTIVITAMIN, StandardSupplementIds.CREATINE, StandardSupplementIds.VITAMIN_D, "omega_3",
        ).let { ids -> (0 until 16).map { mask -> ids.filterIndexed { i, _ -> mask shr i and 1 == 1 }.toSet() } }

        val onlySupplements = showingOnly(MoNeed.Supplements)

        val supplements = testSupplementsChecklist()

        val Boolean.bit: Char get() = if (this) '1' else '0'

        fun records(dateKey: String, timestampMillis: Long, takenIds: Set<String>) =
            if (takenIds.isEmpty()) emptyList() else listOf(supplementDay(dateKey, timestampMillis, takenIds))

        fun days(first: LocalDate, last: LocalDate): List<LocalDate> =
            generateSequence(first) { it.plusDays(1) }.takeWhile { it <= last }.toList()

        fun openMood(remind: Boolean): MoMood {
            val ref = MoNeed.Supplements.view.trackerRef
            val checkpoints = testSupplementsChecklist(remind).checkpoints
            return MoMood(
                state = MoState.Steady,
                dueToday = 1,
                daysCold = 0,
                streak = MoStreak(
                    days = 0,
                    freezes = 0,
                    freezeCapacity = 3,
                    week = listOf(MoDayMark.Open) + List(6) { MoDayMark.Upcoming },
                    weekCanEarnFreeze = true,
                ),
                missingTrackersToday = listOf(ref),
                trackerOpenParts = mapOf(ref to checkpoints.map { CheckpointPart(it.id) }),
                trackerCheckpoints = mapOf(ref to checkpoints),
            )
        }

        fun inheritedHistory(history: List<InheritedDayReport>) = history.map { row ->
            MoDayRecord(
                date = row.date,
                mark = row.mark,
                coveredTrackers = row.covered.mapTo(linkedSetOf()) { it.view.trackerRef },
                restedTrackers = if (row.rested) setOf(MoNeed.Activity.view.trackerRef) else emptySet(),
            )
        }

        fun sha256(text: String): String =
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private enum class HistoryStatistics { All, SupplementsOnly }
}
