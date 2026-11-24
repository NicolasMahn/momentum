package de.nicolasmahn.momentum.app

import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.TrackerRef
import de.nicolasmahn.momentum.domain.TrackerTypeId
import de.nicolasmahn.momentum.domain.mo.MoEvaluation
import de.nicolasmahn.momentum.domain.reminder.ReminderSettings
import de.nicolasmahn.momentum.domain.reminder.ReminderTime
import de.nicolasmahn.momentum.domain.reminder.WorkoutReminderSettings
import de.nicolasmahn.momentum.domain.settings.FeatureVisibilitySettings
import de.nicolasmahn.momentum.persistence.json.AppStateJsonCodec
import de.nicolasmahn.momentum.persistence.rows.StoredRow
import de.nicolasmahn.momentum.persistence.rows.TrackerRows
import java.time.LocalDate

/**
 * An everyday set of views, as stored: a daily plan at waking and before sleep, one at 12:30, a weigh-in
 * at waking, a questionnaire about yesterday a little after it, a weekly timer at 18:00, Activity
 * with Thursday free, and Substances outside the streak. What Home and Mo make of a day is tested
 * against these, so a rule is tried on the shapes it was written for.
 */
internal object EverydayViews {
    private val views = listOf(
        "checklists" to """{"view":"checklist","id":"supplements","label":"Supplements","icon":"medication","hue":"purple","mo":{"enabled":true,"motif":"pills"},"checkpoints":[{"id":"midday","at":"12:30","remind":true}],"target":{"per":"day"},"rewards":{"maxPasses":3,"earn":{"everyKeptDays":7}},"items":[{"id":"creatine","label":"Creatine"}]}""",
        "checklists" to """{"view":"checklist","id":"care","label":"Care","icon":"toothbrush","hue":"teal","mo":{"enabled":true,"motif":"teeth"},"checkpoints":[{"id":"morning","label":"Morning","at":"wake+5","remind":true},{"id":"evening","label":"Evening","at":"sleep-15","remind":true}],"target":{"per":"day"},"rewards":{"maxPasses":1,"earn":{"everyKeptDays":14}},"items":[{"id":"toothbrushing","label":"Brush teeth"}]}""",
        "measurements" to """{"view":"measurement","id":"weight","label":"Weight","icon":"scale","hue":"sky","mo":{"enabled":true,"motif":"scale"},"unit":"kg","step":"0.1","valuePolicy":"positive","trendDays":21,"checkpoints":[{"id":"day","at":"wake+5","remind":true}],"rewards":{"maxPasses":7,"earn":{"keptDaysInWeek":6}},"whenAway":"excused"}""",
        "questions" to """{"view":"questions","id":"eating","label":"Eating","icon":"food","hue":"orange","mo":{"enabled":true,"motif":"food"},"questions":[{"id":"quality","text":"Quality?","answers":[{"id":"poor","label":"Poor"},{"id":"good","label":"Good"}]}],"about":"yesterday","rewards":{"maxPasses":3,"earn":{"everyKeptDays":7}},"whenAway":"due","checkpoints":[{"id":"day","at":"wake+15","remind":true}]}""",
        "time" to """{"view":"time","id":"reading","label":"Reading","icon":"book","hue":"yellow","mo":{"enabled":true,"motif":"reading"},"goal":60,"rewards":{"maxPasses":3,"tokenSteps":{"steps":[{"percent":100,"tokens":1}]},"tokenCover":"quarterOfGoal"},"whenAway":"due","checkpoints":[{"id":"day","at":"18:00","remind":true}]}""",
        "activity" to """{"view":"activity","id":"activity","label":"Activity","icon":"hiking","hue":"green","mo":{"enabled":true,"motif":"running"},"week":{"days":[{"day":"MON","activity":"gym"},{"day":"TUE","activity":"gym"},{"day":"WED","activity":"gym"},{"day":"THU"},{"day":"FRI","activity":"gym"}]},"whenAway":"lowerTarget","awayDays":2,"rewards":{"maxPasses":3,"tokenSteps":{"steps":[{"daysMore":0,"tokens":1}]}},"activities":[{"id":"gym","label":"Gym","icon":"dumbbell"}]}""",
        "substances" to """{"mo":{"enabled":false,"motif":null},"tracked":["alcohol"],"own":[],"stayingAway":[{"ids":[]}],"checkIn":{"remind":false,"at":"18:00"},"rewards":null,"whenAway":"due","goal":"logging","exceptionsOnly":true}""",
    )
    val thursday: LocalDate = LocalDate.parse("2026-11-12")
    val reminders = ReminderSettings(workout = WorkoutReminderSettings(enabled = true, fixedTime = ReminderTime(18, 0)))
    val care = TrackerRef(TrackerTypeId("checklist"), "care")
    val supplements = TrackerRef(TrackerTypeId("checklist"), "supplements")
    val weight = TrackerRef(TrackerTypeId("measurement"), "weight")
    val eating = TrackerRef(TrackerTypeId("questions"), "eating")
    val reading = TrackerRef(TrackerTypeId("time"), "reading")

    /** Every view kept on the nine days before the day before [today], so Eating, about yesterday, is still open and each takes part today. */
    private fun history(today: LocalDate) = (2L..10L).map { today.minusDays(it).toString() }.flatMap { day ->
        listOf(
            "checklistDays" to """{"checklistId":"supplements","dateKey":"$day","timestampMillis":1,"ticks":["midday/creatine"]}""",
            "checklistDays" to """{"checklistId":"care","dateKey":"$day","timestampMillis":1,"ticks":["morning/toothbrushing","evening/toothbrushing"]}""",
            "measurementLogs" to """{"measurement":"weight","dateKey":"$day","timestampMillis":1,"value":80.0}""",
            "questionEntries" to """{"id":"q$day","questions":"eating","dateKey":"$day","timestampMillis":1,"answers":{"quality":"good"}}""",
            "activityEntries" to """{"id":"a$day","activity":"gym","name":"Gym","dateKey":"$day","timestampMillis":1}""",
            "timeLogs" to """{"time":"reading","dateKey":"$day","timestampMillis":1,"minutes":1}""",
        )
    }

    /**
     * The views with their kept history and [records] on top, each a row's kind and body. The
     * history ends two days before [today], which is [thursday] unless a test runs on the clock.
     */
    fun state(vararg records: Pair<String, String>, today: LocalDate = thursday): AppState {
        val rows = (views + history(today) + records).mapIndexed { index, (kind, body) ->
            StoredRow(kind, if (kind == "activity" || kind == "substances") TrackerRows.Singleton else "$kind-$index", body, index.toLong())
        }
        return AppStateJsonCodec.decodeVersion32(TrackerRows.assemble(rows).first)
    }

    fun evaluation(state: AppState, day: LocalDate): MoEvaluation =
        MoEvaluation(state, FeatureVisibilitySettings(), day, rewardStart = LocalDate.parse("2026-10-01"))
}
