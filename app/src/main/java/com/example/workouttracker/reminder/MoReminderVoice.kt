package com.example.workouttracker.reminder

import com.example.workouttracker.R
import com.example.workouttracker.core.text.UiText
import com.example.workouttracker.core.text.joinedBy
import com.example.workouttracker.core.text.uiText
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.activity.activityRules
import com.example.workouttracker.domain.activity.nextRoutineDay
import com.example.workouttracker.domain.mo.MoMood
import com.example.workouttracker.domain.ActivityTrackerRef
import com.example.workouttracker.domain.mo.MoContentKey
import com.example.workouttracker.domain.mo.MoViewKind
import com.example.workouttracker.feature.mo.MoContentCatalogue
import com.example.workouttracker.feature.mo.moTone
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Every reminder in Mo's voice, using the view kind and motif catalogue (the words live in
 * strings_reminders.xml). See docs/mo.md: he is hurt, never angry, he never asks about a logged
 * value, and as a Husk he no longer speaks, so the text speaks about him instead.
 */
internal object MoReminderVoice {
    /**
     * The same day always yields the same words, so re-posting a notification never rewrites it,
     * and a new day rotates to the next variant.
     */
    fun speak(
        subject: MoReminderSubject.Activity,
        mood: MoMood?,
        day: LocalDate,
    ): MoReminderMessage {
        val tone = mood?.state.moTone()
        val content = MoContentCatalogue[MoContentKey(MoViewKind.Activity)]
        val label = UiText.Verbatim(subject.label)
        val facts = listOfNotNull(subject.planned)
        val copy = content.reminder(tone, detailed = facts.isNotEmpty())
        val line = copy.line(day, label, facts)
        val ache = mood?.trackerNeglect?.get(ActivityTrackerRef)?.let { content.reminderAche(it, tone, label) }
        return MoReminderMessage(
            title = label,
            text = listOfNotNull(ache, line, subject.nextDay?.let { uiText(R.string.reminder_activity_next_day, it) }).joinedBy(" "),
        )
    }
}

/** Resolved by the notification at the moment it posts, so it speaks the app's current language. */
internal data class MoReminderMessage(
    val title: UiText,
    val text: UiText,
)

/** What a reminder is about, carrying the facts its text must keep. */
internal object MoReminderSubject {
    data class Activity(
        val activityId: String?,
        val planned: UiText?,
        val nextDay: UiText?,
        val label: String,
    )
}

/**
 * Activity's reminder for [day]: the activity the week's current revision plans on it, while
 * Activity still offers that activity, with the routine day next in turn by the routine's one
 * resolver where it has a routine; nothing is named on a day planned as any activity. The
 * notification and the test in Settings both come here, so they cannot drift apart.
 */
internal fun activityReminderSubject(state: AppState, day: DayOfWeek): MoReminderSubject.Activity {
    val rules = state.activityRules
    val planned = rules.week.current.plannedOn(day)?.let(rules::activity)?.takeUnless { it.removed }
        ?: return MoReminderSubject.Activity(activityId = null, planned = null, nextDay = null, label = rules.label)
    return MoReminderSubject.Activity(
        label = rules.label,
        activityId = planned.id,
        planned = UiText.Verbatim(planned.label),
        nextDay = planned.nextRoutineDay(state.sessions)?.let { UiText.Verbatim(it.label) },
    )
}
