package de.nicolasmahn.momentum.app

import de.nicolasmahn.momentum.domain.questions.eatingEntry
import de.nicolasmahn.momentum.domain.compatibility.ReadingTimeId
import de.nicolasmahn.momentum.domain.time.TimeLog
import de.nicolasmahn.momentum.domain.compatibility.MoNeed
import de.nicolasmahn.momentum.core.time.localDateKeys
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.DayProgress
import de.nicolasmahn.momentum.domain.activity.ActivityEntry
import de.nicolasmahn.momentum.domain.compatibility.LegacyCareAction
import de.nicolasmahn.momentum.domain.care.CareDone
import de.nicolasmahn.momentum.domain.care.careState
import de.nicolasmahn.momentum.domain.care.dueCareActions
import de.nicolasmahn.momentum.domain.care.withCare
import de.nicolasmahn.momentum.domain.compatibility.LegacyCareSession
import de.nicolasmahn.momentum.domain.measurement.asMeasurementLogs
import de.nicolasmahn.momentum.domain.substance.SubstanceKind
import de.nicolasmahn.momentum.domain.substance.SubstanceLog
import de.nicolasmahn.momentum.domain.checklist.applyEdit
import de.nicolasmahn.momentum.domain.checklist.DayRecord
import de.nicolasmahn.momentum.domain.checklist.setDayRecord
import de.nicolasmahn.momentum.domain.checklist.tickOf
import de.nicolasmahn.momentum.domain.supplement.AllSupplementIds
import de.nicolasmahn.momentum.domain.compatibility.StandardSupplementIds
import de.nicolasmahn.momentum.domain.supplement.supplementDay
import de.nicolasmahn.momentum.domain.supplement.supplementsState
import de.nicolasmahn.momentum.domain.supplement.testSupplementsChecklist
import de.nicolasmahn.momentum.domain.supplement.withSupplements
import de.nicolasmahn.momentum.domain.measurement.MeasurementRecord
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZonedDateTime
import de.nicolasmahn.momentum.domain.holiday.HolidaySchedule

class HomeTodayStatusTest {
    /** A checklist that asks nothing today shows nothing to do: no parts, and not done either. */
    @Test
    fun aFeatureWhoseChecklistAsksNothingTodayIsNeitherOpenNorDone() {
        // A Wednesday. A checklist keeps an item, so what stays is due on Saturdays or in January only.
        val today = java.time.LocalDate.parse("2026-10-14")
        val saturdaysOnly = listOf("toothbrushing", "hair_foam").fold(careState()) { state, item ->
            state.applyEdit(de.nicolasmahn.momentum.domain.compatibility.CareChecklistId, de.nicolasmahn.momentum.domain.checklist.ChecklistEdit.RemoveItem(item), today)
        }
        val outOfSeason = listOf(StandardSupplementIds.MULTIVITAMIN, StandardSupplementIds.VITAMIN_D).fold(supplementsState()) { state, id ->
            state.applyEdit(de.nicolasmahn.momentum.domain.compatibility.SupplementsChecklistId, de.nicolasmahn.momentum.domain.checklist.ChecklistEdit.RemoveItem(id), today)
        }.applyEdit(
            de.nicolasmahn.momentum.domain.compatibility.SupplementsChecklistId,
            de.nicolasmahn.momentum.domain.checklist.ChecklistEdit.SetItemSeason(StandardSupplementIds.CREATINE, setOf(java.time.Month.JANUARY)),
            today,
        )
        listOf(saturdaysOnly to MoNeed.Care, outOfSeason to MoNeed.Supplements).forEach { (state, feature) ->
            assertEquals(null, featureProgressOn(state, today.toString(), HolidaySchedule())[feature])
            assertEquals(false, feature in featuresLoggedOn(state, today.toString(), HolidaySchedule()))
        }
    }

    @Test
    fun onlyFeaturesWithARecordForThatExactDayAreMarked() {
        val state = AppState(
            measurementLogs = listOf(MeasurementRecord(Today, 1L, 88.4)).asMeasurementLogs("weight"),
            activityEntries = listOf(
                ActivityEntry(
                    id = "yesterday-walk",
                    activityId = "walk",
                    name = "Walk",
                    dateKey = Yesterday,
                    timestampMillis = 1L,
                ),
            ),
        ).withSupplements(supplementDay(Today, 1L, AllSupplementIds))

        assertEquals(
            setOf(MoNeed.Weight, MoNeed.Supplements),
            featuresLoggedOn(state, Today, HolidaySchedule()),
        )
        assertEquals(setOf(MoNeed.Activity), featuresLoggedOn(state, Yesterday, HolidaySchedule()))
    }

    @Test
    fun aPartialOrUnflatteringRecordStillCountsAsLogged() {
        val state = AppState(
            questionEntries = listOf(
                eatingEntry("eating", Yesterday, 1L, quality = "poor"),
            ),
        ).withSupplements(supplementDay(Today, 1L, setOf(StandardSupplementIds.CREATINE)))
            .withCare(CareDone(Today, LegacyCareSession.MORNING, LegacyCareAction.TOOTHBRUSHING, 1L))

        assertEquals(
            setOf(
                MoNeed.Supplements,
                MoNeed.Eating,
            ),
            featuresLoggedOn(state, Today, HolidaySchedule()),
        )
    }

    @Test
    fun aFeatureWhoseDayHasPartsIsOnlyCompleteWhenEveryPartIsDone() {
        fun session(session: LegacyCareSession, dateKey: String = Today) = dueCareActions(dateKey, session, 1L).toList()
        val brushedOnly = CareDone(Today, LegacyCareSession.MORNING, LegacyCareAction.TOOTHBRUSHING, 1L)
        fun care(done: List<CareDone>) =
            featureProgressOn(careState(*done.toTypedArray()), Today, HolidaySchedule()).getValue(MoNeed.Care)

        assertEquals(DayProgress(done = 0, parts = 2), care(session(LegacyCareSession.EVENING, dateKey = "2026-09-15")))
        // A session counts once its whole routine is done, the same rule that keeps the streak.
        assertEquals(DayProgress(done = 0, parts = 2), care(listOf(brushedOnly)))
        assertEquals(DayProgress(done = 1, parts = 2), care(session(LegacyCareSession.MORNING)))
        assertEquals(true, care(session(LegacyCareSession.MORNING) + session(LegacyCareSession.EVENING)).isComplete)
    }

    @Test
    fun aFeatureWithOneRecordADayIsEitherOpenOrDoneAndWhatIsNeverLoggedHasNoProgress() {
        val read = AppState(timeLogs = listOf(TimeLog(ReadingTimeId, dateKey = Today, timestampMillis = 1L)))

        assertEquals(DayProgress(done = 1, parts = 1), featureProgressOn(read, Today, HolidaySchedule())[MoNeed.Reading])
        assertEquals(DayProgress(done = 0, parts = 1), featureProgressOn(read, Today, HolidaySchedule())[MoNeed.Weight])
    }

    @Test
    fun careBadgeRequiresBothCompleteSessions() {
        val complete = LegacyCareSession.entries.flatMap { session -> dueCareActions(Today, session, 1L).toList() }
        assertEquals(setOf(MoNeed.Care), featuresLoggedOn(careState(*complete.toTypedArray()), Today, HolidaySchedule()))
        complete.forEach { missing ->
            assertEquals(emptySet<MoNeed>(), featuresLoggedOn(careState(*(complete - missing).toTypedArray()), Today, HolidaySchedule()))
        }
    }

    @Test
    fun aSubstanceUseNeverEarnsTheBadge() {
        val state = AppState(
            substanceLogs = listOf(SubstanceLog("drink", SubstanceKind.ALCOHOL.id, Today, 1L)),
        )

        assertEquals(emptySet<MoNeed>(), featuresLoggedOn(state, Today, HolidaySchedule()))
    }

    @Test
    fun eatingIsLoggedByTheReflectionOnTheDayThatJustEnded() {
        val reflectedOnYesterday = AppState(
            questionEntries = listOf(
                eatingEntry("eating", Yesterday, 1L, quality = "good"),
            ),
        )
        val reflectedOnToday = AppState(
            questionEntries = listOf(
                eatingEntry("eating", Today, 1L, quality = "good"),
            ),
        )

        assertEquals(setOf(MoNeed.Eating), featuresLoggedOn(reflectedOnYesterday, Today, HolidaySchedule()))
        assertEquals(emptySet<MoNeed>(), featuresLoggedOn(reflectedOnToday, Today, HolidaySchedule()))
    }

    @Test
    fun anEmptySupplementRecordAndStatisticsAreNeverLogged() {
        val ticked = setDayRecord(supplementsState(), testSupplementsChecklist(), Today, DayRecord(setOf(tickOf("midday", StandardSupplementIds.CREATINE))), 1L)
        val state = setDayRecord(ticked, testSupplementsChecklist(), Today, DayRecord(), 2L)

        assertEquals(emptySet<MoNeed>(), featuresLoggedOn(state, Today, HolidaySchedule()))
    }

    @Test
    fun badgesFollowTheDayAtFourRatherThanTheDayHomeOpenedOn() {
        val state = AppState(
            measurementLogs = listOf(MeasurementRecord(Yesterday, 1L, 88.4)).asMeasurementLogs("weight"),
            timeLogs = listOf(TimeLog(ReadingTimeId, Yesterday, 1L)),
        )
        var now = ZonedDateTime.parse("${Today}T03:59:30+02:00[Europe/Berlin]")

        val badges = runBlocking {
            localDateKeys(now = { now }, recheckMillis = 5L)
                .onEach { now = ZonedDateTime.parse("${Today}T04:00:00+02:00[Europe/Berlin]") }
                .map { dateKey -> featuresLoggedOn(state, dateKey, HolidaySchedule()) }
                .take(2)
                .toList()
        }

        assertEquals(
            listOf(
                setOf(MoNeed.Weight, MoNeed.Reading),
                emptySet<MoNeed>(),
            ),
            badges,
        )
    }

    /** Care's cleaning once a week: the evening part is done without it until the week's last day. */
    @Test
    fun aWeekItemCountsOnHomeOnlyOnADayItWouldBeAGap() {
        val monday = java.time.LocalDate.parse("2026-10-12")
        val daily = (0..6).flatMap { offset ->
            LegacyCareSession.entries.flatMap { session ->
                listOf(LegacyCareAction.TOOTHBRUSHING, LegacyCareAction.HAIR_FOAM).map { CareDone(monday.plusDays(offset.toLong()).toString(), session, it, 1L) }
            }
        }
        val state = careState(*daily.toTypedArray()).applyEdit(
            de.nicolasmahn.momentum.domain.compatibility.CareChecklistId,
            de.nicolasmahn.momentum.domain.checklist.ChecklistEdit.SetItemTarget(LegacyCareAction.CLEAN_BETWEEN_TEETH.storageId, de.nicolasmahn.momentum.domain.checklist.ChecklistTarget.PerWeek(times = 1)),
            monday,
        )
        val saturday = monday.plusDays(5).toString()
        val sunday = monday.plusDays(6).toString()
        assertEquals(DayProgress(done = 2, parts = 2), featureProgressOn(state, saturday, HolidaySchedule())[MoNeed.Care])
        assertEquals(true, MoNeed.Care in featuresLoggedOn(state, saturday, HolidaySchedule()))
        assertEquals(DayProgress(done = 1, parts = 2), featureProgressOn(state, sunday, HolidaySchedule())[MoNeed.Care])
        assertEquals(false, MoNeed.Care in featuresLoggedOn(state, sunday, HolidaySchedule()))
        // A sick Saturday keeps the free day for Sunday.
        val sick = HolidaySchedule().withSickDays(de.nicolasmahn.momentum.domain.holiday.DayRange(monday.plusDays(5), monday.plusDays(5)))
        assertEquals(DayProgress(done = 2, parts = 2), featureProgressOn(state, sunday, sick)[MoNeed.Care])
    }

    private companion object {
        const val Today = "2026-09-19"
        const val Yesterday = "2026-09-18"
    }
}
