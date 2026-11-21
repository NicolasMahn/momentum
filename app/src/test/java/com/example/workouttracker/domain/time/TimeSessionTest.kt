package com.example.workouttracker.domain.time

import com.example.workouttracker.domain.compatibility.ReadingTimeId

import com.example.workouttracker.domain.AppState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class TimeSessionTest {
    private val zone = ZoneId.of("Europe/Berlin")

    private fun at(text: String): Long = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun aSessionSavesItsWholeMinutesOnTheDayItStarted() {
        val session = TimeSession.startingAt(ReadingTimeId, at("2026-10-03T21:10:00"), zone)

        val finished = finishTimeSession(AppState(), session, at("2026-10-03T21:33:59"))

        assertEquals(23, finished.savedMinutes)
        assertEquals(listOf(TimeLog(ReadingTimeId, "2026-10-03", at("2026-10-03T21:33:59"), 23)), finished.state.timeLogs.filter { it.viewId == ReadingTimeId })
    }

    @Test
    fun aLateNightSessionBelongsToTheEveningItBegan() {
        val session = TimeSession.startingAt(ReadingTimeId, at("2026-10-04T01:30:00"), zone)
        assertEquals("2026-10-03", session.dateKey)

        // Reading on past 04:00 does not move it to the next habit day.
        val finished = finishTimeSession(AppState(), session, at("2026-10-04T04:20:00"))
        assertEquals("2026-10-03", finished.state.timeLogs.filter { it.viewId == ReadingTimeId }.single().dateKey)
        assertEquals(170, finished.savedMinutes)
    }

    @Test
    fun underAMinuteSavesNothing() {
        val state = AppState(timeLogs = listOf(TimeLog(ReadingTimeId, "2026-10-02", 1L, 12)))
        val session = TimeSession.startingAt(ReadingTimeId, at("2026-10-03T21:00:00"), zone)

        val finished = finishTimeSession(state, session, at("2026-10-03T21:00:59"))

        assertEquals(0, finished.savedMinutes)
        assertSame(state, finished.state)
    }

    @Test
    fun aClockSetBackwardsReadsAsNothing() {
        val session = TimeSession.startingAt(ReadingTimeId, at("2026-10-03T21:00:00"), zone)
        assertEquals(0, session.minutesAt(at("2026-10-03T20:00:00")))
    }

    @Test
    fun minutesAddToTheDayAndAnOldTickStaysWithoutInventedMinutes() {
        val tick = TimeLog(ReadingTimeId, "2026-10-03", 5L)
        val session = TimeSession.startingAt(ReadingTimeId, at("2026-10-03T21:00:00"), zone)

        val finished = finishTimeSession(AppState(timeLogs = listOf(tick)), session, at("2026-10-03T21:10:00"))

        // The day now has the ten timed minutes and nothing more for the older tick.
        assertEquals(10, finished.state.timeLogs.filter { it.viewId == ReadingTimeId }.single().minutes)
    }

    @Test
    fun aDayNeverPassesTwentyFourHours() {
        val state = AppState(timeLogs = listOf(TimeLog(ReadingTimeId, "2026-10-03", 5L, 1430)))
        val session = TimeSession.startingAt(ReadingTimeId, at("2026-10-03T21:00:00"), zone)

        val finished = finishTimeSession(state, session, at("2026-10-03T22:00:00"))

        assertEquals(10, finished.savedMinutes)
        assertEquals(1440, finished.state.timeLogs.filter { it.viewId == ReadingTimeId }.single().minutes)
    }

    @Test
    fun aStoppedSessionKeepsItsEndSoARetryCannotAddTime() {
        val session = TimeSession.startingAt(ReadingTimeId, at("2026-10-03T21:00:00"), zone)
            .copy(endedAtMillis = at("2026-10-03T21:20:00"))

        val finished = finishTimeSession(AppState(), session, at("2026-10-03T23:00:00"))

        assertEquals(20, finished.savedMinutes)
        assertEquals(20, session.minutesAt(at("2026-10-04T09:00:00")))
    }

    /** A session adds to the day of the view it names and to no other: Reading's log of that day is as it was. */
    @Test
    fun aSessionOfAMadeViewAddsToItsOwnDayAndLeavesReadingsAlone() {
        val readings = TimeLog(ReadingTimeId, "2026-10-03", 5L, 40)
        val pianos = TimeLog("tim_k2p9", "2026-10-03", 6L, 15)
        val state = AppState(timeLogs = listOf(pianos, readings))
        val session = TimeSession.startingAt("tim_k2p9", at("2026-10-03T21:00:00"), zone)
        assertEquals("tim_k2p9", session.viewId)

        val finished = finishTimeSession(state, session, at("2026-10-03T21:25:30"))

        assertEquals(25, finished.savedMinutes)
        assertEquals(listOf(TimeLog("tim_k2p9", "2026-10-03", at("2026-10-03T21:25:30"), 40), readings), finished.state.timeLogs)
        assertEquals(listOf(TimeLog(ReadingTimeId, "2026-10-03", 5L, 40)), finished.state.timeLogs.filter { it.viewId == ReadingTimeId })
        assertEquals(setOf(session.startedAtMillis), finished.state.recordedTimeSessions)

        // Its own day is what fills: a full day of Reading leaves the made view's whole day open.
        val readingFull = AppState(timeLogs = listOf(TimeLog(ReadingTimeId, "2026-10-03", 5L, 1440)))
        assertEquals(25, finishTimeSession(readingFull, session, at("2026-10-03T21:25:30")).savedMinutes)
        val ownAlmostFull = AppState(timeLogs = listOf(TimeLog("tim_k2p9", "2026-10-03", 5L, 1430)))
        assertEquals(10, finishTimeSession(ownAlmostFull, session, at("2026-10-03T21:25:30")).savedMinutes)
    }

    /** One session runs at a time, so its start names it whichever view it was of: recorded once, it adds nothing again. */
    @Test
    fun aRecordedSessionOfAMadeViewAddsNothingWhenItComesBack() {
        val session = TimeSession.startingAt("tim_k2p9", at("2026-10-03T21:00:00"), zone).copy(endedAtMillis = at("2026-10-03T21:20:00"))
        val recorded = finishTimeSession(AppState(), session, at("2026-10-03T21:20:00"))

        val again = finishTimeSession(recorded.state, session, at("2026-10-03T23:00:00"))

        assertSame(recorded.state, again.state)
        assertEquals(20, again.savedMinutes)
        assertEquals(20, again.state.timeLogs.single().minutes)
    }

    @Test
    fun aRecordedSessionThatComesBackAddsNothingEvenAfterOtherReadingWasSaved() {
        val session = TimeSession.startingAt(ReadingTimeId, at("2026-10-03T21:00:00"), zone)
            .copy(endedAtMillis = at("2026-10-03T21:20:00"))
        val recorded = finishTimeSession(AppState(), session, at("2026-10-03T21:20:00"))
        // Five minutes entered by hand move the day's timestamp on.
        val withMore = recorded.state.copy(timeLogs = addTimeMinutes(recorded.state.timeLogs, ReadingTimeId, "2026-10-03", 5, at("2026-10-03T22:00:00")))

        // The process died before it forgot the session, so the same stopped session comes back.
        val again = finishTimeSession(withMore, session, at("2026-10-03T23:00:00"))

        assertSame(withMore, again.state)
        assertEquals(20, again.savedMinutes)
        assertEquals(25, again.state.timeLogs.filter { it.viewId == ReadingTimeId }.single().minutes)
    }
}
