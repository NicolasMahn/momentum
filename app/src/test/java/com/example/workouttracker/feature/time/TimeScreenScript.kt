package com.example.workouttracker.feature.time

import com.example.workouttracker.domain.compatibility.ReadingTimeId
import com.example.workouttracker.domain.time.TimeLog
import com.example.workouttracker.domain.holiday.DayRange
import com.example.workouttracker.domain.holiday.HolidaySchedule
import java.time.LocalDate

/** The day every scene of the script is shown on, a Thursday. */
internal const val TimeScriptToday = "2026-09-17"

/**
 * Logs of every kind: minutes today, minutes earlier this week, an old one-tap mark without
 * minutes, a day almost full, and minutes in an earlier week.
 */
internal val TimeScriptRecords = listOf(
    TimeLog(ReadingTimeId, "2026-09-17", 500, 20),
    TimeLog(ReadingTimeId, "2026-09-15", 400, 25),
    TimeLog(ReadingTimeId, "2026-09-14", 300),
    TimeLog(ReadingTimeId, "2026-09-10", 200, 1430),
    TimeLog(ReadingTimeId, "2026-09-01", 100, 45),
)

/** Three sick days in the week of [TimeScriptToday], so it asks 35 of its 60 minutes. */
internal val TimeScriptSickDays = HolidaySchedule().withSickDays(DayRange(LocalDate.parse("2026-09-14"), LocalDate.parse("2026-09-16")))

/** The week before [TimeScriptToday], sick throughout, so it asks nothing. */
internal val TimeScriptSickWeek = HolidaySchedule().withSickDays(DayRange(LocalDate.parse("2026-09-07"), LocalDate.parse("2026-09-13")))

/** What the last session left behind, as its owner hands it to the screen. */
enum class TimeScriptNote { LeftAppSaved, LeftAppNothing, NothingSaved }

/**
 * What a script of the screen needs from whoever shows it: each step is one a person takes, by the
 * tags the screen has always had.
 */
internal interface TimeScreenStage {
    /**
     * Shows the screen anew over [records], on [TimeScriptToday]; [refuse] makes every save and
     * removal fail, and [covered] has the streak cover two days of the week, one by a token and
     * one by a freeze.
     */
    fun open(
        records: List<TimeLog>,
        refuse: Boolean = false,
        daysOff: HolidaySchedule = HolidaySchedule(),
        note: TimeScriptNote? = null,
        covered: Boolean = false,
    )

    /** Shows the running session at [minutes], as after a save that failed where [saveFailed]. */
    fun openSession(minutes: Int, saveFailed: Boolean = false)

    /** Shows the question about Do Not Disturb a first session asks. */
    fun openFocusQuestion()

    /** Picks a day as a person does: by its cell in the calendar. */
    fun select(dateKey: String)
    fun click(tag: String)
    fun type(tag: String, text: String)

    /** Fails unless a node of each of [tags] is on show: a tag that names nothing said is in no transcript. */
    fun see(vararg tags: String)

    /** Lets the moment after a save pass, in which the row goes back to the timer. */
    fun waitAMoment()

    /** Writes down everything on show under [title]. */
    fun say(title: String)
}

/**
 * Every state of the configured timer and its session that a person can reach.
 */
internal fun TimeScreenStage.playTimeScreen() {
    open(emptyList())
    say("nothing recorded, today")
    click("time_reading_start")
    say("Start pressed")
    click("time_reading_type_minutes")
    say("typing today's minutes")
    click("time_reading_log_today")
    say("Add with nothing typed")
    type("time_reading_minutes_input", "0")
    click("time_reading_log_today")
    say("Add with no minutes")
    type("time_reading_minutes_input", "12a345")
    say("more digits than the field takes")
    type("time_reading_minutes_input", "15")
    click("time_reading_log_today")
    say("after adding today's minutes")
    waitAMoment()
    say("back at the timer by itself")
    click("time_reading_type_minutes")
    type("time_reading_minutes_input", "7")
    click("time_reading_back_to_timer")
    say("back at the timer by its button")
    click("time_reading_type_minutes")
    say("typing again starts empty")

    open(TimeScriptRecords)
    say("records, today")
    click("time_reading_remove_day")
    say("today's removal armed")
    click("time_reading_remove_day")
    say("today removed")
    select("2026-09-15")
    say("a day with minutes")
    type("time_reading_minutes_input", "10")
    click("time_reading_log_today")
    say("minutes added to a day that has some")
    select("2026-09-14")
    say("a day with an old mark")
    type("time_reading_minutes_input", "30")
    click("time_reading_log_today")
    say("minutes added to an old mark")
    click("time_reading_remove_day")
    click("time_reading_remove_day")
    say("the day removed")
    select("2026-09-10")
    say("a day almost full, in the week before")
    type("time_reading_minutes_input", "11")
    click("time_reading_log_today")
    say("more minutes than the day has left")
    type("time_reading_minutes_input", "1441")
    click("time_reading_log_today")
    say("more minutes than a day has")
    type("time_reading_minutes_input", "10")
    click("time_reading_log_today")
    say("the day filled")
    select("2026-09-01")
    say("a day in an earlier week")
    select("2026-09-16")
    say("an empty day")
    select(TimeScriptToday)
    say("back on today")

    open(TimeScriptRecords, refuse = true)
    click("time_reading_type_minutes")
    type("time_reading_minutes_input", "5")
    click("time_reading_log_today")
    say("a save that fails")
    type("time_reading_minutes_input", "6")
    say("typing again takes the error away")
    click("time_reading_back_to_timer")
    click("time_reading_remove_day")
    click("time_reading_remove_day")
    say("a removal that fails")
    select("2026-09-15")
    say("another day takes the error away")

    open(TimeScriptRecords, daysOff = TimeScriptSickDays)
    say("a week with three sick days")
    open(TimeScriptRecords, daysOff = TimeScriptSickWeek)
    select("2026-09-10")
    say("a week sick throughout")

    open(TimeScriptRecords, covered = true)
    say("days the streak covered")

    TimeScriptNote.entries.forEach { note ->
        open(TimeScriptRecords, note = note)
        say("the note of the last session: $note")
    }
    open(TimeScriptRecords, refuse = true, note = TimeScriptNote.NothingSaved)
    click("time_reading_remove_day")
    click("time_reading_remove_day")
    say("an error in the note's place")

    openSession(minutes = 0)
    see("time_reading_session", "time_reading_session_done")
    say("a session just started")
    openSession(minutes = 1)
    say("a session of one minute")
    openSession(minutes = 23)
    say("a session of several minutes")
    openSession(minutes = 23, saveFailed = true)
    say("a session whose save failed")
    openFocusQuestion()
    see("time_reading_focus_dialog", "time_reading_focus_allow")
    say("the question about Do Not Disturb")
    click("time_reading_focus_without")
    say("the question answered without Do Not Disturb")
}
