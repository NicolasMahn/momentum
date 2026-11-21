package com.example.workouttracker.feature.mo

import com.example.workouttracker.domain.mo.canQuiet
import com.example.workouttracker.domain.time.TimeLog
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.compatibility.MoNeed
import com.example.workouttracker.domain.mo.moMood
import com.example.workouttracker.domain.mo.only
import com.example.workouttracker.domain.compatibility.ReadingTimeId
import com.example.workouttracker.domain.trackerRef
import com.example.workouttracker.domain.time.TimeEdit
import com.example.workouttracker.domain.time.applyingTimeEdits
import com.example.workouttracker.domain.time.withReading
import com.example.workouttracker.domain.activity.withActivity
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tokens are set aside for Reading a gap at a time, while its week is short: one for each started
 * quarter of the week's goal still missing, as many as the bank holds. With the goal Reading
 * always had a gap is fifteen minutes; with another goal it follows.
 */
class ReadingTokenReserveTest {
    private val monday = LocalDate.parse("2026-10-05")
    private fun on(day: Int) = monday.plusDays(day.toLong())
    private val onlyReading = only(MoNeed.Reading)
    private val reading = MoNeed.Reading.view.trackerRef
    private fun read(vararg days: Pair<Int, Int>) =
        AppState(timeLogs = days.map { (day, minutes) -> TimeLog(ReadingTimeId, on(day).toString(), day.toLong(), minutes) }).withReading().withActivity()
    private fun AppState.goal(minutes: Int) = applyingTimeEdits(ReadingTimeId, listOf(TimeEdit.SetGoal(minutes)), on(0))
    private fun sunday(state: AppState, day: Int, reserved: Boolean = false) =
        moMood(state, onlyReading, on(day), chosenTrackers = if (reserved) setOf(reading) else emptySet())

    /** A bank of three covers 45 minutes, three gaps, and not a minute more; a bank of one covers fifteen. */
    @Test fun withSixtyMinutesATokenCoversFifteenAndAFullBankThreeGaps() {
        // Two hours in the first week fill the bank to three.
        val thirtyShort = sunday(read(1 to 120, 8 to 30), day = 13)
        assertEquals(3, thirtyShort.streak.standings.getValue(reading).passes)
        assertEquals(30, thirtyShort.trackerWeeklyRemainders[reading])
        assertTrue(thirtyShort.canSetAside(reading))
        assertTrue(sunday(read(1 to 120, 8 to 15), day = 13).canSetAside(reading))
        assertFalse(sunday(read(1 to 120, 8 to 14), day = 13).canSetAside(reading))

        val reserved = sunday(read(1 to 120, 8 to 30), day = 13, reserved = true)
        assertEquals(2, reserved.tokensSetAside(reading))
        assertEquals(1, reserved.tokensLeft(reading))
        assertEquals(1, sunday(read(1 to 120, 8 to 46), day = 13, reserved = true).tokensSetAside(reading))
        assertEquals(2, sunday(read(1 to 120, 8 to 44), day = 13, reserved = true).tokensSetAside(reading))
        assertEquals(3, sunday(read(1 to 120, 8 to 15), day = 13, reserved = true).tokensSetAside(reading))

        // The one token every bank starts with covers one gap.
        assertTrue(sunday(read(1 to 45), day = 6).canSetAside(reading))
        assertFalse(sunday(read(1 to 44), day = 6).canSetAside(reading))
        // Before Sunday the week can still be made up: no token is set aside, and the day can be quieted.
        assertFalse(sunday(read(1 to 45), day = 5).canSetAside(reading))
        assertTrue(sunday(read(1 to 45), day = 5).canQuiet(reading))
    }

    /** With two hours a week a gap is thirty minutes: two tokens cover an hour missing, three of them an hour and a half. */
    @Test fun aGapIsAQuarterOfTheGoalTheWeekHas() {
        val hourShort = sunday(read(1 to 240, 8 to 60).goal(120), day = 13)
        assertEquals(3, hourShort.streak.standings.getValue(reading).passes)
        assertEquals(60, hourShort.trackerWeeklyRemainders[reading])
        assertTrue(hourShort.canSetAside(reading))
        assertTrue(sunday(read(1 to 240, 8 to 30).goal(120), day = 13).canSetAside(reading))
        assertFalse(sunday(read(1 to 240, 8 to 29).goal(120), day = 13).canSetAside(reading))
        assertEquals(2, sunday(read(1 to 240, 8 to 60).goal(120), day = 13, reserved = true).tokensSetAside(reading))
        assertEquals(1, sunday(read(1 to 240, 8 to 90).goal(120), day = 13, reserved = true).tokensSetAside(reading))
        // A 90-minute goal rounds its quarter-gap up to 23 minutes: 46 missing needs two tokens,
        // another minute three, which the bank holds, and 70 missing four, which it does not.
        assertEquals(2, sunday(read(1 to 180, 8 to 44).goal(90), day = 13).trackerTokenRequirements[reading])
        assertTrue(sunday(read(1 to 180, 8 to 44).goal(90), day = 13).canSetAside(reading))
        assertEquals(3, sunday(read(1 to 180, 8 to 43).goal(90), day = 13).trackerTokenRequirements[reading])
        assertTrue(sunday(read(1 to 180, 8 to 43).goal(90), day = 13).canSetAside(reading))
        assertFalse(sunday(read(1 to 180, 8 to 20).goal(90), day = 13).canSetAside(reading))
    }
}
