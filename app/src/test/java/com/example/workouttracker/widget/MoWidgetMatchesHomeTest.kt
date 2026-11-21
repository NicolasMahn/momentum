package com.example.workouttracker.widget

import com.example.workouttracker.core.time.habitDate
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.checklist.ChecklistConfiguration
import com.example.workouttracker.domain.checklist.ChecklistDay
import com.example.workouttracker.domain.checklist.ChecklistItem
import com.example.workouttracker.domain.checklist.Checkpoint
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.checklist.joinRewards
import com.example.workouttracker.domain.checklist.tickOf
import com.example.workouttracker.domain.mo.MoEvaluation
import com.example.workouttracker.domain.mo.MoMood
import com.example.workouttracker.domain.mo.TrackerMoPolicy
import com.example.workouttracker.domain.mo.moMoodWithStoredChoices
import com.example.workouttracker.domain.reminder.Bedtime
import com.example.workouttracker.domain.reminder.ReminderSettings
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import com.example.workouttracker.domain.visibilityInUse
import com.example.workouttracker.feature.mo.moPicture
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Home reads the visibility switches with a checklist that has nothing in use hidden, the widget
 * reads them as stored. Mo must come out the same from either: while he did not, a checklist whose
 * items had all ended still counted its unticked days on the widget, which showed a dimming Mo and
 * a shorter streak beside a blazing Home.
 */
class MoWidgetMatchesHomeTest {
    private val today = LocalDate.parse("2026-10-14")
    private val stored = FeatureVisibilitySettings()
    private val reminders = ReminderSettings()
    private val bedtime = Bedtime(wake = LocalTime.of(7, 0), fixedBedtime = null)

    private val plants = checklist("chk_plants", ChecklistItem("itm_water", "Water the plants"))
    private val ended = listOf("chk_floss", "chk_stretch").map { checklist(it, ChecklistItem("itm_old", "Old habit", until = today.minusDays(1))) }
    private val state = (listOf(plants) + ended).fold(
        AppState(
            checklists = listOf(plants) + ended,
            checklistDays = (1L..14L).flatMap { back ->
                val day = today.minusDays(back)
                // The ended ones were ticked on some of the days they were in use and not on the others.
                listOf(ticked(plants, "itm_water", day)) + ended.filter { back % 4 == 0L }.map { ticked(it, "itm_old", day) }
            },
        ),
    ) { joined, checklist -> joinRewards(joined, checklist.id, today.minusDays(14)) }

    /** Around a day part's start, the 09:00 checkpoint turning overdue, and the 04:00 day boundary. */
    @Test
    fun theWidgetShowsTheMoHomeShowsAtTheSameMoment() {
        listOf(
            "2026-10-14T08:30", "2026-10-14T10:59", "2026-10-14T11:00", "2026-10-14T14:30",
            "2026-10-15T03:59", "2026-10-15T04:00", "2026-10-15T10:15",
        ).map(LocalDateTime::parse).forEach { at ->
            val day = habitDate(at)
            // As MomentumApp builds it, from the switches in use.
            val homeMood = MoEvaluation(state, state.visibilityInUse(stored, day), day).moodWithTrackerChoices(emptySet())
            // As moWidgetViews builds it, from the switches as stored.
            val widgetMood = moMoodWithStoredChoices(state, stored, day)
            assertEquals("mood at $at", homeMood, widgetMood)

            val home = picture(homeMood, day, at)
            val widget = picture(widgetMood, day, at)
            assertEquals("look at $at", home.look.copy(scene = null), widget.look.copy(scene = null))
            assertEquals("scene at $at", home.scene?.act?.id, widget.scene?.act?.id)
        }
    }

    /** What Home hands down as a save's turn moves on with the habit day and with nothing else. */
    @Test
    fun aSavesTurnIsTheHabitDay() {
        val mood = moMoodWithStoredChoices(state, stored, today)
        fun turn(at: String) = LocalDateTime.parse(at).let { picture(mood, habitDate(it), it).saveTurn }

        assertEquals(turn("2026-10-14T08:30"), turn("2026-10-14T23:10"))
        assertEquals("past midnight is still the same habit day", turn("2026-10-14T08:30"), turn("2026-10-15T03:59"))
        assertEquals(turn("2026-10-14T08:30") + 1, turn("2026-10-15T04:00"))
    }

    private fun picture(mood: MoMood, day: LocalDate, at: LocalDateTime) =
        moPicture(mood, reminders, LocalDateTime.of(day, at.toLocalTime()), bedtime)

    private fun checklist(id: String, item: ChecklistItem) = ChecklistConfiguration(
        id = id,
        label = id,
        icon = ViewIcon.Plant,
        hue = ViewHue.Forest,
        checkpoints = listOf(Checkpoint("cpt_1", CheckpointTime.Fixed(LocalTime.of(9, 0)), remind = true)),
        items = listOf(item),
        mo = TrackerMoPolicy(enabled = true),
    )

    private fun ticked(checklist: ChecklistConfiguration, itemId: String, day: LocalDate) =
        ChecklistDay(checklist.id, day.toString(), timestampMillis = 0, ticks = setOf(tickOf(checklist.checkpoints.single().id, itemId)))
}
