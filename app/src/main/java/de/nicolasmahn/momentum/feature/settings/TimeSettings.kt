package de.nicolasmahn.momentum.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.domain.LoggedView
import de.nicolasmahn.momentum.domain.checklist.CheckpointTime
import de.nicolasmahn.momentum.domain.checklist.MoMotif
import de.nicolasmahn.momentum.domain.checklist.ViewHue
import de.nicolasmahn.momentum.domain.checklist.ViewIcon
import de.nicolasmahn.momentum.domain.checklist.WhenAway
import de.nicolasmahn.momentum.domain.holiday.HolidaySchedule
import de.nicolasmahn.momentum.domain.reminder.Bedtime
import de.nicolasmahn.momentum.domain.time.DefaultTokenSteps
import de.nicolasmahn.momentum.domain.time.FurtherStepPercent
import de.nicolasmahn.momentum.domain.time.StepTokens
import de.nicolasmahn.momentum.domain.time.TimeConfiguration
import de.nicolasmahn.momentum.domain.time.TimeEdit
import de.nicolasmahn.momentum.domain.time.TimerMotifs
import de.nicolasmahn.momentum.domain.time.TokenStepCount
import de.nicolasmahn.momentum.core.ui.ReservedMessage
import de.nicolasmahn.momentum.domain.time.TokenCover
import de.nicolasmahn.momentum.domain.time.TokenCoverMinutes
import de.nicolasmahn.momentum.domain.time.TokenCoverStep
import de.nicolasmahn.momentum.domain.time.TokenSteps
import de.nicolasmahn.momentum.domain.time.WeeklyGoalMinutes
import de.nicolasmahn.momentum.domain.time.applyEdit
import de.nicolasmahn.momentum.domain.time.applyingEdits
import de.nicolasmahn.momentum.domain.time.stepMinutes
import de.nicolasmahn.momentum.persistence.json.AppStateJsonCodec
import de.nicolasmahn.momentum.reminder.TestReminder
import java.time.LocalDate

/**
 * A time view's name, look, Mo's flavor, goal, reminder and streak part as a draft
 * ([LoggedViewDraft]). The goal is typed: a field holding
 * text that is no number of minutes a week can ask keeps the last goal that could be stored and
 * holds Save back ([goalRefused]).
 *
 * The steps a week earns by are listed, one row each, and a further step's percent is typed. The
 * draft keeps what each percent field holds itself ([stepTexts]), by the step's place in the
 * list, so a row shows its own step's text after the row above it was removed; text that is no
 * percent the step may have stays there, refused ([refusedSteps]), and holds Save back as a
 * refused goal does. A refused text is taken the moment its neighbors make room for it.
 */
internal class TimeSettingsDraft(
    stored: TimeConfiguration,
    today: LocalDate,
    edits: List<TimeEdit> = emptyList(),
    blankNames: Set<String> = emptySet(),
    goalRefused: Boolean = false,
    stepTexts: Map<Int, String> = emptyMap(),
    refusedSteps: Set<Int> = emptySet(),
) : LoggedViewDraft<TimeConfiguration, TimeEdit>(stored, today, edits, blankNames) {
    /**
     * Whether the goal field holds text that is no goal ([WeeklyGoalMinutes]). The field keeps
     * what was typed and the draft the last goal that could be stored, and nothing is stored
     * while it does: what was typed is never swapped for that goal behind the person's back.
     */
    var goalRefused: Boolean by mutableStateOf(goalRefused)
        private set

    /** What each typed percent field holds, by the step's index, until it is saved. */
    var stepTexts: Map<Int, String> by mutableStateOf(stepTexts)
        private set

    /** The steps whose field holds text that is no percent they may have. */
    var refusedSteps: Set<Int> by mutableStateOf(refusedSteps)
        private set

    override val heldBack: Boolean get() = super.heldBack || goalRefused || stepsRefused

    override val hasUnsavedWork: Boolean get() = super.hasUnsavedWork || goalRefused || stepsRefused

    /** Whether a percent field holds text that is no percent its step may have, which holds Save and Create back. */
    val stepsRefused: Boolean get() = refusedSteps.isNotEmpty()

    /** Whether the goal is stored: the field holds one that can be, and no edit to it waits for Save. */
    val goalStored: Boolean get() = !goalRefused && edits.none { it is TimeEdit.SetGoal }

    override fun TimeConfiguration.applying(edit: TimeEdit, today: LocalDate): TimeConfiguration = applyEdit(edit, today)

    override fun TimeConfiguration.applyingAll(edits: List<TimeEdit>, today: LocalDate): TimeConfiguration = applyingEdits(edits, today)

    /** The one name is the view's own. */
    override fun TimeEdit.names(key: String): Boolean = this is TimeEdit.Rename && key == stored.id

    override fun TimeEdit.removes(): String? = null

    override fun rename(label: String): Boolean = edit(TimeEdit.Rename(label))

    override fun setIcon(icon: ViewIcon): Boolean = edit(TimeEdit.SetIcon(icon))

    override fun setHue(hue: ViewHue): Boolean = edit(TimeEdit.SetHue(hue))

    fun setMotif(motif: MoMotif?): Boolean = edit(TimeEdit.SetMotif(motif))

    override fun setRemind(remind: Boolean): Boolean = edit(TimeEdit.SetRemind(remind))

    override fun setReminderTime(at: CheckpointTime): Boolean = edit(TimeEdit.SetReminderTime(at))

    override fun setWhenAway(whenAway: WhenAway): Boolean = edit(TimeEdit.SetWhenAway(whenAway))

    fun setBankSize(maxPasses: Int): Boolean = edit(TimeEdit.SetBankSize(maxPasses))

    /** One token covers [minutes] of a week that falls short, from the week today is in. */
    fun setTokenCover(minutes: Int): Boolean = edit(TimeEdit.SetTokenCover(TokenCover.Minutes(minutes)))

    /**
     * Out of the streak, the rows are out of sight: what a percent field held goes with them, so
     * nothing hidden holds Save back, and back in the streak the rows show the last steps taken.
     */
    override fun takePart(takesPart: Boolean): Boolean {
        val taken = edit(if (takesPart) TimeEdit.JoinRewards else TimeEdit.LeaveRewards)
        if (taken && !takesPart) {
            stepTexts = emptyMap()
            refusedSteps = emptySet()
        }
        return taken
    }

    override fun saved() {
        super.saved()
        stepTexts = emptyMap()
    }

    /** The steps as the page shows them, those of the edited bank on today; a view without a bank has none. */
    val steps: TokenSteps? get() = edited.rewards?.tokenSteps?.on(today)

    /** What the percent field of the step at [index] shows: what was typed into it, or the step's percent. */
    fun stepText(index: Int): String = stepTexts[index] ?: requireSteps().steps[index].percent.toString()

    fun stepRefused(index: Int): Boolean = index in refusedSteps

    /** Whether one more step can be added: room after the last one, and no field holding refused text. */
    val canAddStep: Boolean get() = steps?.canGrow == true && !stepsRefused

    fun addStep(): Boolean = canAddStep && changeSteps { it.grown() }

    /** Removes the step at [index], never the first; the fields under it move up with their steps. */
    fun removeStep(index: Int): Boolean {
        if (!changeSteps { it.withoutStep(index) }) return false
        stepTexts = stepTexts.filterKeys { it != index }.mapKeys { (at, _) -> if (at > index) at - 1 else at }
        refusedSteps = refusedSteps.filter { it != index }.mapTo(LinkedHashSet()) { if (it > index) it - 1 else it }
        settleRefusedSteps()
        return true
    }

    fun setStepTokens(index: Int, tokens: Int): Boolean = changeSteps { it.withStep(index, it.steps[index].copy(tokens = tokens)) }

    /**
     * The percent field of the step at [index] was typed into. What was [typed], without the
     * space around it, becomes the step's percent where it is a whole number the step may have
     * beside its neighbors ([TokenSteps.percentsAllowedAt]); otherwise the field keeps it, refused.
     */
    fun typeStepPercent(index: Int, typed: String) {
        stepTexts = stepTexts + (index to typed)
        refusedSteps = if (takePercent(index, typed)) refusedSteps - index else refusedSteps + index
        settleRefusedSteps()
    }

    /** Whether [typed] is a percent the step at [index] may have now, taken into the draft where it is. */
    private fun takePercent(index: Int, typed: String): Boolean {
        val percent = typed.trim().takeIf { text -> text.isNotEmpty() && text.all(Char::isDigit) }?.toIntOrNull()
            ?.takeIf { it in requireSteps().percentsAllowedAt(index) } ?: return false
        return changeSteps { it.withStep(index, it.steps[index].copy(percent = percent)) }
    }

    /** The steps as [change] makes them, kept as one edit; a list the steps refuse is not taken, as the stored configuration would not take it. */
    private fun changeSteps(change: (TokenSteps) -> TokenSteps): Boolean =
        runCatching { change(requireSteps()) }.map { edit(TimeEdit.SetTokenSteps(it)) }.getOrDefault(false)

    /** A refused text its neighbors have since made room for is taken, and may make room for another. */
    private fun settleRefusedSteps() {
        var settled: Int?
        do {
            settled = refusedSteps.firstOrNull { index -> stepTexts[index]?.let { takePercent(index, it) } == true }
            settled?.let { refusedSteps = refusedSteps - it }
        } while (settled != null)
    }

    private fun requireSteps(): TokenSteps = requireNotNull(steps) { "Time view ${stored.id} has no bank." }

    /**
     * The goal field was typed into. What was [typed], without the space around it, becomes the
     * goal where it is a whole number of minutes a week can ask; otherwise the draft only knows
     * the field is refused. Only digits are a number here: a sign or a decimal separator is not.
     */
    fun typeGoal(typed: String) {
        val minutes = typed.trim().takeIf { text -> text.isNotEmpty() && text.all(Char::isDigit) }?.toIntOrNull()?.takeIf { it in WeeklyGoalMinutes }
        goalRefused = minutes == null
        if (minutes != null) edit(TimeEdit.SetGoal(minutes))
    }

    companion object {
        val Saver: Saver<TimeSettingsDraft, Any> = listSaver(
            save = {
                listOf(
                    AppStateJsonCodec.encodeTimeView(it.stored),
                    AppStateJsonCodec.encodeTimeEdits(it.edits),
                    ArrayList(it.blankNames),
                    it.today.toString(),
                    it.goalRefused,
                    ArrayList(it.stepTexts.keys),
                    ArrayList(it.stepTexts.values),
                    ArrayList(it.refusedSteps),
                )
            },
            restore = {
                TimeSettingsDraft(
                    stored = AppStateJsonCodec.decodeTimeView(it[0] as String),
                    edits = AppStateJsonCodec.decodeTimeEdits(it[1] as String),
                    blankNames = (it[2] as List<*>).mapTo(LinkedHashSet()) { key -> key as String },
                    today = LocalDate.parse(it[3] as String),
                    goalRefused = it[4] as Boolean,
                    stepTexts = (it[5] as List<*>).map { index -> index as Int }.zip((it[6] as List<*>).map { text -> text as String }).toMap(),
                    refusedSteps = (it[7] as List<*>).mapTo(LinkedHashSet()) { index -> index as Int },
                )
            },
        )
    }
}

/**
 * A timer's settings share [MadeViewSettingsPage], with four parts: Look, its name, icon and color;
 * Goal, its minutes a week ([TimeGoalPart]); Reminders, its one reminder a day
 * ([LoggedViewReminder]); and Streak, whether it counts toward the streak, how many tokens its
 * bank holds, what a holiday means for it and the steps a week earns tokens by ([TimeStreak]).
 * All four change one draft, and Save stores its edits through [onSave].
 *
 * [reminderRow], [onSendTest], [bedtime] and [daysOff] are the Reminders part's, as on a made
 * measurement's page. It can be deleted, with every day's minutes, once the dialog naming it and
 * its [loggedDays] confirmed that. The page opens on Look; a host that shows another part first,
 * as the gallery does, says which through [opensOn].
 */
@Composable
internal fun MadeTimeSettingsPage(
    view: TimeConfiguration,
    today: LocalDate,
    loggedDays: Int,
    bedtime: Bedtime,
    daysOff: HolidaySchedule,
    onSave: (List<TimeEdit>) -> ViewWrite,
    reminderRow: @Composable (LoggedView, String, (Boolean) -> Unit) -> Unit,
    onSendTest: ((TestReminder.AtCheckpoint) -> Unit)?,
    onDelete: () -> Boolean,
    onClose: () -> Unit,
    leaveRequested: Boolean,
    onLeaveAnswered: (discard: Boolean) -> Unit,
    onUnsavedWorkChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    opensOn: ChecklistPage = LoggedViewParts.first(),
) {
    val draft = rememberSaveable(view.id, saver = TimeSettingsDraft.Saver) { TimeSettingsDraft(view, today) }
    draft.follow(view, today)

    MadeViewSettingsPage(
        view = view,
        draft = draft,
        testTag = "time_settings",
        pageKey = SettingsPage.Time(view.id).key,
        parts = LoggedViewParts,
        opensOn = opensOn,
        // The sentence names no kind: a time view's days are recorded days as a measurement's are.
        deletion = LoggedViewDeletion(loggedDays, R.plurals.measurement_settings_delete_message, R.string.time_settings_delete_failed, onDelete),
        onSave = onSave,
        onClose = onClose,
        leaveRequested = leaveRequested,
        onLeaveAnswered = onLeaveAnswered,
        onUnsavedWorkChange = onUnsavedWorkChange,
        modifier = modifier,
        message = message,
        partLabel = { TimeOwnPart.nameOf(it) },
        partTagName = TimeOwnPart::tagNameOf,
    ) { shown, edited, onPickTime, nameField ->
        MadeTimePart(
            part = shown,
            view = edited,
            draft = draft,
            today = today,
            bedtime = bedtime,
            daysOff = daysOff,
            testTag = "time_settings",
            reminderRow = reminderRow,
            onSendTest = onSendTest,
            onPickTime = onPickTime,
            nameField = nameField,
        )
    }
}

/** A time view's second part holds its goal, not items. */
internal val TimeOwnPart = OwnPart(R.string.time_settings_goal_part, tagName = "goal")

/**
 * One part of a time view's page, the page's and the setup's alike, over [view] as [draft] is
 * changing it ([TimePart]), with the one reminder every logged view has: [reminderRow],
 * [onSendTest] and [onPickTime] are its Reminders part's ([LoggedViewReminder]). [nameField] is
 * Look's name, which the page and the setup keep differently. Every tag follows from [testTag].
 */
@Composable
internal fun MadeTimePart(
    part: ChecklistPage,
    view: TimeConfiguration,
    draft: TimeSettingsDraft,
    today: LocalDate,
    bedtime: Bedtime,
    daysOff: HolidaySchedule,
    testTag: String,
    reminderRow: @Composable (LoggedView, String, (Boolean) -> Unit) -> Unit,
    onSendTest: ((TestReminder.AtCheckpoint) -> Unit)?,
    onPickTime: () -> Unit,
    nameField: @Composable () -> Unit,
) {
    TimePart(part = part, view = view, draft = draft, today = today, testTag = testTag, nameField = nameField) {
        LoggedViewReminder(
            view = view,
            edits = draft,
            today = today,
            bedtime = bedtime,
            daysOff = daysOff,
            testTag = testTag,
            reminderRow = reminderRow,
            onSendTest = onSendTest,
            onPickTime = onPickTime,
        )
    }
}

/**
 * One part of a time view's page over [view] as [draft] is changing it: Look, Goal
 * ([TimeGoalPart]) and Streak ([TimeStreak]), and [reminders], the Reminders part its host brings
 * ([MadeTimePart]). Look ends with Mo's flavor, the control a checklist has, offering Plain and
 * the motifs Mo has something for while a timer runs. Every tag follows from [testTag].
 */
@Composable
internal fun TimePart(
    part: ChecklistPage,
    view: TimeConfiguration,
    draft: TimeSettingsDraft,
    today: LocalDate,
    testTag: String,
    nameField: @Composable () -> Unit,
    reminders: @Composable () -> Unit,
) {
    when (part) {
        ChecklistPage.Look -> LoggedViewLook(
            view = view,
            edits = draft,
            testTag = testTag,
            icons = TimeIcons,
            end = { MoFlavorPicker(selected = view.motif, motifs = TimerMotifs, testTag = testTag) { draft.setMotif(it) } },
        ) { nameField() }

        ChecklistPage.Items -> TimeGoalPart(view = view, draft = draft, today = today, testTag = testTag)

        ChecklistPage.Reminders -> reminders()

        ChecklistPage.Streak -> TimeStreak(view = view, draft = draft, today = today, testTag = testTag)
    }
}

/**
 * What a time view asks of a week, on its page and in its setup alike, over [view] as [draft] is
 * changing it: "Minutes a week", typed as a whole number, and under it a line saying what
 * reaching the goal earns and, with more than one step, the most a week can earn, by the goal and
 * the steps as the draft has them; a view with no bank yet is told of the steps it would start
 * with. Text that is no such number stays in the field, which says why in its label's place. Tags
 * follow from [testTag]: the field is `<testTag>_goal` and the line `<testTag>_goal_note`.
 */
@Composable
internal fun TimeGoalPart(view: TimeConfiguration, draft: TimeSettingsDraft, today: LocalDate, testTag: String) {
    val goal = view.goal.on(today)
    val steps = view.rewards?.tokenSteps?.on(today) ?: DefaultTokenSteps
    val atTheGoal = steps.steps.first().tokens
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TypedPartField(
            text = goal.toString(),
            key = "${view.id}/goal",
            label = stringResource(R.string.time_settings_goal),
            error = if (draft.goalRefused) stringResource(R.string.time_settings_goal_invalid, WeeklyGoalMinutes.first, WeeklyGoalMinutes.last) else null,
            settled = draft.goalStored,
            testTag = "${testTag}_goal",
            onType = draft::typeGoal,
            keyboardType = KeyboardType.Number,
        )
        EditorNote(
            text = listOfNotNull(
                pluralStringResource(R.plurals.time_settings_goal_note, goal, goal, pluralStringResource(R.plurals.settings_tokens, atTheGoal, atTheGoal)),
                if (steps.steps.size > 1) pluralStringResource(R.plurals.time_settings_goal_note_more, steps.total, steps.total) else null,
            ).joinToString(" "),
            testTag = "${testTag}_goal_note",
        )
    }
}

/**
 * A timer's reward settings, shared by its page and setup, over [view] as [draft] changes it
 * ([LoggedViewStreak]): whether it counts, how many
 * tokens its bank holds while it does, then the steps a week earns tokens by while it counts
 * ([TimeTokenStepRows]), and last what a holiday means for it. It has no kept days to earn by: a time
 * view earns by its goal, which its Goal part says. Tags follow from [testTag]: the switch is
 * `<testTag>_rewards`, the bank's size `<testTag>_max_passes` and the steps `<testTag>_step_<number>`.
 */
@Composable
internal fun TimeStreak(view: TimeConfiguration, draft: TimeSettingsDraft, today: LocalDate, testTag: String) {
    val bank = view.rewards
    LoggedViewStreak(view = view, edits = draft, today = today, testTag = testTag) {
        StreakSwitch(takesPart = bank?.takesPartOn(today) == true, testTag = testTag, onTakePart = { draft.takePart(it) }) {
            if (bank != null) BankSizeStepper(bank.maxPasses.on(today), testTag, draft::setBankSize)
        }
        if (bank != null && bank.takesPartOn(today)) {
            TimeTokenStepRows(steps = bank.tokenSteps.on(today), goal = view.goal.on(today), draft = draft, key = view.id, testTag = testTag)
            TokenCoverStepper(cover = bank.tokenCover.on(today), goal = view.goal.on(today), testTag = testTag, onChange = draft::setTokenCover)
        }
    }
}

/**
 * How many minutes of a week that falls short one token covers, in steps of [TokenCoverStep], with
 * a line that says what that means for a week. A cover that has never been set is a
 * quarter of the goal, which a second line says; its room stays once the cover is set, so nothing
 * below moves. Tags follow from [testTag]: the stepper is `<testTag>_token_cover`, the line
 * `<testTag>_token_cover_note` and the second `<testTag>_token_cover_quarter`.
 */
@Composable
private fun TokenCoverStepper(cover: TokenCover, goal: Int, testTag: String, onChange: (Int) -> Boolean) {
    val minutes = cover.minutesOf(goal)
    val quarter = stringResource(R.string.time_settings_token_cover_quarter)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        NumberStepper(
            label = stringResource(R.string.time_settings_token_cover),
            value = minutes,
            // Not tied to the goal: a goal changed on the page could otherwise strand the cover, and
            // a token covering more than the goal only means one token covers the whole week.
            range = TokenCoverMinutes,
            valueText = stringResource(R.string.time_settings_step_minutes, minutes),
            testTag = "${testTag}_token_cover",
            onChange = onChange,
            step = TokenCoverStep,
        )
        EditorNote(
            text = stringResource(R.string.time_settings_token_cover_note, minutes),
            testTag = "${testTag}_token_cover_note",
        )
        ReservedMessage(
            text = quarter.takeIf { cover == TokenCover.QuarterOfGoal },
            testTag = "${testTag}_token_cover_quarter",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            reserving = listOf(quarter),
            announced = false,
        )
    }
}

/**
 * The steps a time view's week earns tokens by ([TokenStepRows]), read from the goal up: "At the
 * goal", then each further step's percent of the goal, typed in a narrow field that says so;
 * beside it what that comes to in minutes by the goal as [draft] has it today. A typed percent the
 * step may not have stays in its field, which says the range it may have in its label's place.
 * The middle column keeps room for the widest minutes the goal allows, ten times it, so typing a
 * percent never moves a row or the focus, and the minutes never wrap.
 *
 * Tags are the shared rows', with the minutes `<testTag>_step_<number>_minutes` and a step's
 * field `…_percent`.
 */
@Composable
private fun TimeTokenStepRows(steps: TokenSteps, goal: Int, draft: TimeSettingsDraft, key: String, testTag: String) {
    TokenStepRows(
        rows = steps.steps.mapIndexed { index, step ->
            TokenStepRow(
                lead = { modifier -> TimeStepLead(index, allowed = steps, draft = draft, key = key, testTag = "${testTag}_step_${index + 1}", modifier = modifier) },
                middle = stringResource(R.string.time_settings_step_minutes, stepMinutes(step.percent, goal)),
                tokens = step.tokens,
                tokensLabel = if (index == 0) stringResource(R.string.time_settings_step_tokens_at_goal) else stringResource(R.string.settings_step_tokens_at, index + 1),
                onTokens = { draft.setStepTokens(index, it) },
                onRemove = if (index == 0) null else ({ draft.removeStep(index) }),
                removeKey = step,
            )
        },
        tokens = StepTokens,
        leadWidth = stepLeadWidth(),
        widestMiddle = stringResource(R.string.time_settings_step_minutes, stepMinutes(FurtherStepPercent.last, goal)),
        total = stepsTotal(stepMinutes(steps.last.percent, goal), steps.total),
        // The widest minutes and the most tokens make the longest sentence in any language.
        widestTotal = stepsTotal(stepMinutes(FurtherStepPercent.last, WeeklyGoalMinutes.last), TokenStepCount.last * StepTokens.last),
        canAdd = draft.canAddStep,
        onAdd = { draft.addStep() },
        testTag = testTag,
        middleTag = "minutes",
    )
}

/** When a step is reached: the goal's own words, or a further step's percent of the goal, typed. */
@Composable
private fun TimeStepLead(index: Int, allowed: TokenSteps, draft: TimeSettingsDraft, key: String, testTag: String, modifier: Modifier) {
    if (index == 0) {
        Text(text = stringResource(R.string.time_settings_step_goal), modifier = modifier, style = MaterialTheme.typography.bodyLarge)
        return
    }
    val percents = allowed.percentsAllowedAt(index)
    TypedPartField(
        text = draft.stepText(index),
        key = "$key/step/${index + 1}",
        label = stringResource(R.string.time_settings_step_of_goal),
        error = when {
            !draft.stepRefused(index) -> null
            // Between neighbors that leave room for one percent alone, that percent is the whole range.
            percents.first == percents.last -> stringResource(R.string.time_settings_step_percent_only, percents.first)
            else -> stringResource(R.string.time_settings_step_percent_invalid, percents.first, percents.last)
        },
        // The draft keeps what was typed, so the field only ever mirrors it.
        settled = true,
        testTag = "${testTag}_percent",
        onType = { draft.typeStepPercent(index, it) },
        keyboardType = KeyboardType.Number,
        modifier = modifier,
        suffix = stringResource(R.string.time_settings_step_percent_sign),
        textAlign = TextAlign.End,
    )
}

/** What a week of [minutes] or more earns in all, [tokens]. */
@Composable
private fun stepsTotal(minutes: Int, tokens: Int): String =
    pluralStringResource(R.plurals.time_settings_steps_total, minutes, minutes, pluralStringResource(R.plurals.settings_tokens, tokens, tokens))

/**
 * How wide the first column is: "At the goal", or the percent field holding "1000 %" and, in its
 * label line, "Of the goal" or the widest range it can say, whichever is widest in the language
 * on show, with a little room ([StepLeadRoom]). Measured, so the field fits its content and no
 * more, and the goal's row lines up with the others.
 */
@Composable
internal fun stepLeadWidth(): Dp {
    val typography = MaterialTheme.typography
    val fieldPadding = OutlinedTextFieldDefaults.contentPadding()
    val sign = stringResource(R.string.time_settings_step_percent_sign)
    // No range a step can say is written wider than the highest percent twice, so that bounds them all.
    val widestRange = stringResource(R.string.time_settings_step_percent_invalid, FurtherStepPercent.last, FurtherStepPercent.last)
    val inField = maxOf(
        widestTextWidth(listOf(FurtherStepPercent.last.toString()), typography.bodyLarge) + widestTextWidth(listOf(sign), typography.bodyLarge) + StepLeadRoom,
        // The floating label and the error are set in the small type, as Material sets them.
        widestTextWidth(listOf(stringResource(R.string.time_settings_step_of_goal), widestRange), typography.bodySmall),
    )
    val field = inField + fieldPadding.calculateLeftPadding(LayoutDirection.Ltr) + fieldPadding.calculateRightPadding(LayoutDirection.Ltr) + StepLeadRoom
    return maxOf(field, widestTextWidth(listOf(stringResource(R.string.time_settings_step_goal)), typography.bodyLarge))
}

/** The little room the percent field keeps after its content, "1000 %" or its label. */
private val StepLeadRoom = 4.dp
