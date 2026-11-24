package de.nicolasmahn.momentum.feature.mo

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.core.text.UiText
import de.nicolasmahn.momentum.core.text.pluralText
import de.nicolasmahn.momentum.core.text.uiText
import de.nicolasmahn.momentum.domain.checklist.MoMotif
import de.nicolasmahn.momentum.domain.mo.MoContentKey
import de.nicolasmahn.momentum.domain.mo.MoFigureContent
import de.nicolasmahn.momentum.domain.mo.MoNeglect
import de.nicolasmahn.momentum.domain.mo.MoState
import de.nicolasmahn.momentum.domain.mo.MoViewKind
import de.nicolasmahn.momentum.domain.mo.figure
import de.nicolasmahn.momentum.domain.activity.DefaultActivityDaysAWeek
import java.time.LocalDate

internal enum class MoTone { Warm, Faint, Out }

/** A missing mood still sounds like Mo; failed evaluation must not make a reminder cold. */
internal fun MoState?.moTone(): MoTone = when (this) {
    null, MoState.Blazing, MoState.Steady -> MoTone.Warm
    MoState.Dimming, MoState.Cold -> MoTone.Faint
    MoState.Husk -> MoTone.Out
}

internal data class MoTones<T>(val warm: T, val faint: T, val out: T) {
    operator fun get(tone: MoTone): T = when (tone) {
        MoTone.Warm -> warm
        MoTone.Faint -> faint
        MoTone.Out -> out
    }
}

/** Labels and reminder facts are separate: a user's label must never need grammatical inflection. */
internal enum class MoCopyArguments {
    None, Details, Label;

    fun of(label: UiText, details: List<UiText>): List<UiText> = when (this) {
        None -> emptyList()
        Details -> details
        Label -> listOf(label)
    }
}

internal data class MoReminderCopy(
    @param:StringRes val title: Int,
    val lines: List<Int>,
    val titleArguments: MoCopyArguments = MoCopyArguments.None,
    val lineArguments: MoCopyArguments = MoCopyArguments.None,
) {
    fun title(label: UiText, details: List<UiText>): UiText =
        uiText(title, *titleArguments.of(label, details).toTypedArray())

    /** Re-posting on the same day keeps its words, and the next day rotates to the next variant. */
    fun line(day: LocalDate, label: UiText, details: List<UiText>): UiText = uiText(
        lines[Math.floorMod(day.toEpochDay(), lines.size.toLong()).toInt()],
        *lineArguments.of(label, details).toTypedArray(),
    )
}

internal data class MoLineCopy(@param:StringRes val id: Int, val labelled: Boolean = false) {
    fun text(label: UiText): UiText = if (labelled) uiText(id, label) else uiText(id)
}

internal data class MoAcheCopy(val mild: MoLineCopy, val severe: MoLineCopy = mild) {
    fun text(neglect: MoNeglect, label: UiText): UiText =
        (if (neglect == MoNeglect.Mild) mild else severe).text(label)
}

/** Reward copy names the amount supplied by its earning policy, in the declared unit. */
internal sealed interface MoTokenCopy {
    data class Fixed(val line: MoLineCopy) : MoTokenCopy

    /** A measurement's days: [inWeek] for a bank a week's logged days fill, [acrossWeeks] for one every so many kept days fill. */
    data class ByLoggedDays(@param:PluralsRes val inWeek: Int, @param:PluralsRes val acrossWeeks: Int) : MoTokenCopy

    data class ByNumber(val names: MoTokenNumber, @param:PluralsRes val id: Int) : MoTokenCopy

    /**
     * A number that the code fixed before it became a setting, the one [names]. With [usual] the
     * line is [usualLine], the sentence it always was; any other number is named through [id].
     */
    data class OnceFixed(val names: MoTokenNumber, val usual: Int, val usualLine: MoLineCopy, @param:PluralsRes val id: Int) : MoTokenCopy

    fun text(label: UiText, amount: Int, acrossWeeks: Boolean): UiText = when (this) {
        is Fixed -> line.text(label)
        is ByLoggedDays -> pluralText(if (acrossWeeks) this.acrossWeeks else inWeek, amount, amount)
        is ByNumber -> pluralText(id, amount, amount)
        is OnceFixed -> if (amount == usual) usualLine.text(label) else pluralText(id, amount, amount)
    }
}

/** The unit named by a quantity-bearing reward line. */
internal enum class MoTokenNumber { KeptDays, WeekMinutes, ActiveDays }

internal data class MoContentWords(
    val reminders: MoTones<MoReminderCopy>? = null,
    val reminderAche: MoAcheCopy,
    val presentationAche: MoAcheCopy,
    val tokenEarned: MoTokenCopy,
    val weekGoal: MoLineCopy = MoLineCopy(R.string.mo_event_week_goal_line, labelled = true),
    /** The lines for a reminder with nothing to name, where [reminders] name something: Activity's while the gym is off. */
    val plainReminders: MoTones<MoReminderCopy>? = null,
)

/** The complete moment contract; the domain owns drawing choices without depending on resources. */
internal class MoContent(val key: MoContentKey, private val words: MoContentWords) {
    val figure: MoFigureContent get() = key.figure

    /** Activity owns voiced reminder copy; configured trackers use their kind's controls. */
    fun reminder(tone: MoTone, detailed: Boolean = true): MoReminderCopy =
        requireNotNull(if (!detailed) words.plainReminders ?: words.reminders else words.reminders)[tone]

    /** The Husk has no visible symptoms and no voice, so even a severe ache stays silent. */
    fun reminderAche(neglect: MoNeglect, tone: MoTone, label: UiText): UiText? =
        words.reminderAche.text(neglect, label).takeUnless { tone == MoTone.Out }

    fun presentationAche(neglect: MoNeglect, label: UiText): UiText = words.presentationAche.text(neglect, label)

    /**
     * The earning policy supplies the amount, and whether it counted kept days [acrossWeeks] where
     * the kind can earn either way; visual motifs cannot select another rule.
     */
    fun tokenEarnedLine(label: UiText, amount: Int, acrossWeeks: Boolean = false): UiText = words.tokenEarned.text(label, amount, acrossWeeks)

    fun weekGoalLine(label: UiText): UiText = words.weekGoal.text(label)
}

/** A motif changes the figure and its ache, never the rule a token was earned by. */
internal object MoContentCatalogue {
    operator fun get(key: MoContentKey): MoContent {
        val generic = kinds.getValue(key.kind)
        val ache = when (key.motif) {
            MoMotif.Food -> MoAcheCopy(MoLineCopy(R.string.mo_ache_eating))
            MoMotif.Running -> MoAcheCopy(MoLineCopy(R.string.mo_ache_activity))
            MoMotif.Book -> MoAcheCopy(MoLineCopy(R.string.mo_ache_reading))
            MoMotif.Scale -> MoAcheCopy(MoLineCopy(R.string.mo_ache_weight))
            MoMotif.Pills -> MoAcheCopy(MoLineCopy(R.string.mo_ache_supplements))
            MoMotif.Teeth -> MoAcheCopy(MoLineCopy(R.string.mo_ache_care))
            null -> generic.presentationAche
        }
        return MoContent(key, generic.copy(presentationAche = ache))
    }

    private val neutralAche = MoAcheCopy(MoLineCopy(R.string.mo_tracker_ache, labelled = true))
    private val genericToken = MoTokenCopy.Fixed(MoLineCopy(R.string.mo_tracker_token_earned, labelled = true))
    private val genericWeek = MoLineCopy(R.string.mo_event_week_goal_line_checklist, labelled = true)

    private val kinds: Map<MoViewKind, MoContentWords> by lazy {
        val generic = MoContentWords(
            reminderAche = neutralAche,
            presentationAche = neutralAche,
            tokenEarned = genericToken,
            weekGoal = genericWeek,
        )
        mapOf(
            MoViewKind.Checklist to generic,
            MoViewKind.Measurement to generic.copy(tokenEarned = MoTokenCopy.ByLoggedDays(
                inWeek = R.plurals.mo_tracker_token_logged_days, acrossWeeks = R.plurals.mo_tracker_token_kept_days,
            )),
            MoViewKind.Questions to generic.copy(tokenEarned = MoTokenCopy.ByNumber(
                MoTokenNumber.KeptDays, R.plurals.mo_tracker_token_kept_days,
            )),
            MoViewKind.Time to generic.copy(tokenEarned = MoTokenCopy.ByNumber(
                MoTokenNumber.WeekMinutes, R.plurals.mo_tracker_token_week_minutes,
            )),
            MoViewKind.Activity to generic.copy(
                reminders = activityReminders,
                plainReminders = activityPlainReminders,
                reminderAche = MoAcheCopy(MoLineCopy(R.string.reminder_ache_activity_mild), MoLineCopy(R.string.reminder_ache_activity_severe)),
                presentationAche = MoAcheCopy(MoLineCopy(R.string.mo_ache_activity)),
                tokenEarned = MoTokenCopy.OnceFixed(MoTokenNumber.ActiveDays, DefaultActivityDaysAWeek,
                    MoLineCopy(R.string.mo_event_token_line_activity), R.plurals.mo_event_token_line_activity_days),
            ),
        )
    }
}

private val activityReminders = MoTones(
    warm = MoReminderCopy(
        title = R.string.reminder_activity_title_warm,
        lines = listOf(
            R.string.reminder_activity_warm_1,
            R.string.reminder_activity_warm_2,
            R.string.reminder_activity_warm_3,
            R.string.reminder_activity_warm_4,
        ),
        titleArguments = MoCopyArguments.Details,
        lineArguments = MoCopyArguments.Details,
    ),
    faint = MoReminderCopy(
        title = R.string.reminder_activity_title_faint,
        lines = listOf(
            R.string.reminder_activity_faint_1,
            R.string.reminder_activity_faint_2,
            R.string.reminder_activity_faint_3,
            R.string.reminder_activity_faint_4,
        ),
        titleArguments = MoCopyArguments.Details,
        lineArguments = MoCopyArguments.Details,
    ),
    out = MoReminderCopy(
        title = R.string.reminder_activity_title_out,
        lines = listOf(
            R.string.reminder_activity_out_1,
            R.string.reminder_activity_out_2,
            R.string.reminder_activity_out_3,
            R.string.reminder_activity_out_4,
        ),
        titleArguments = MoCopyArguments.Details,
        lineArguments = MoCopyArguments.Details,
    ),
)

/** Activity's reminder with no gym day to name, while the gym is off: the same voice, nothing planned. */
private val activityPlainReminders = MoTones(
    warm = MoReminderCopy(
        title = R.string.reminder_activity_plain_title_warm,
        lines = listOf(
            R.string.reminder_activity_plain_warm_1,
            R.string.reminder_activity_plain_warm_2,
            R.string.reminder_activity_plain_warm_3,
            R.string.reminder_activity_plain_warm_4,
        ),
    ),
    faint = MoReminderCopy(
        title = R.string.reminder_activity_plain_title_faint,
        lines = listOf(
            R.string.reminder_activity_plain_faint_1,
            R.string.reminder_activity_plain_faint_2,
            R.string.reminder_activity_plain_faint_3,
            R.string.reminder_activity_plain_faint_4,
        ),
    ),
    out = MoReminderCopy(
        title = R.string.reminder_activity_plain_title_out,
        lines = listOf(
            R.string.reminder_activity_plain_out_1,
            R.string.reminder_activity_plain_out_2,
            R.string.reminder_activity_plain_out_3,
            R.string.reminder_activity_plain_out_4,
        ),
    ),
)
