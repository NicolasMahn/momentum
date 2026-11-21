package com.example.workouttracker.app

import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.TrackerRef
import com.example.workouttracker.domain.TrackerTypeId
import com.example.workouttracker.domain.checklist.AboutDay
import com.example.workouttracker.domain.checklist.ChecklistConfiguration
import com.example.workouttracker.domain.checklist.ChecklistDay
import com.example.workouttracker.domain.checklist.ChecklistItem
import com.example.workouttracker.domain.checklist.Checkpoint
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.holiday.HolidaySchedule
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

/** A checklist's Home bar draws the record its row's mark reads: yesterday's for a checklist about yesterday. */
class HomeChecklistAboutDayTest {
    private val friday = LocalDate.parse("2026-11-13")
    private val saturday = friday.plusDays(1)
    private val evening = Checkpoint("cpt_evening", CheckpointTime.Fixed(LocalTime.of(21, 0)), label = "Evening", remind = false)

    private fun checklist(about: AboutDay) = ChecklistConfiguration(
        id = "chk_list", label = "List", icon = ViewIcon.Checklist, hue = ViewHue.Sky,
        checkpoints = listOf(evening), items = List(3) { ChecklistItem("itm_$it", "Item $it") }, about = Dated.always(about),
    )

    private fun day(date: LocalDate, vararg ticks: String) = ChecklistDay("chk_list", date.toString(), 10L, ticks.toSet())

    private fun bar(checklist: ChecklistConfiguration, vararg days: ChecklistDay): List<Float> =
        (homeDayPictures(AppState(checklists = listOf(checklist), checklistDays = days.toList()), saturday, HolidaySchedule())
            .getValue(TrackerRef(TrackerTypeId("checklist"), checklist.id)) as HomeDayPicture.Segments).fills

    @Test
    fun aboutYesterdayDrawsYesterdaysRecordAndAboutTodayTodays() {
        val days = arrayOf(day(friday, "cpt_evening/itm_0", "cpt_evening/itm_1"), day(saturday, "cpt_evening/itm_2"))
        assertEquals(listOf(1f, 1f, 0f), bar(checklist(AboutDay.Yesterday), *days))
        assertEquals(listOf(0f, 0f, 1f), bar(checklist(AboutDay.Today), *days))
    }
}
