package de.nicolasmahn.momentum.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.core.format.formatMetricNumber
import de.nicolasmahn.momentum.core.format.typedDecimal
import de.nicolasmahn.momentum.core.locale.currentLocale
import de.nicolasmahn.momentum.core.ui.MomentumChoiceTile
import de.nicolasmahn.momentum.core.ui.MomentumChoiceTileRow
import de.nicolasmahn.momentum.domain.LoggedView
import de.nicolasmahn.momentum.domain.checklist.CheckpointTime
import de.nicolasmahn.momentum.domain.checklist.ViewHue
import de.nicolasmahn.momentum.domain.checklist.ViewIcon
import de.nicolasmahn.momentum.domain.checklist.WhenAway
import de.nicolasmahn.momentum.domain.holiday.HolidaySchedule
import de.nicolasmahn.momentum.domain.measurement.MaximumMeasurementStep
import de.nicolasmahn.momentum.domain.measurement.MaximumMeasurementUnitLength
import de.nicolasmahn.momentum.domain.measurement.MeasurementConfiguration
import de.nicolasmahn.momentum.domain.measurement.MeasurementEdit
import de.nicolasmahn.momentum.domain.measurement.MeasurementStep
import de.nicolasmahn.momentum.domain.measurement.MeasurementStepDecimals
import de.nicolasmahn.momentum.domain.measurement.applyEdit
import de.nicolasmahn.momentum.domain.measurement.applyingEdits
import de.nicolasmahn.momentum.domain.measurement.isValidMeasurementUnit
import de.nicolasmahn.momentum.domain.reminder.Bedtime
import de.nicolasmahn.momentum.persistence.json.AppStateJsonCodec
import de.nicolasmahn.momentum.reminder.TestReminder
import java.time.LocalDate

/**
 * The measurement draft keeps its last valid unit and step while a field holds invalid text.
 * [unitRefused] and [stepRefused] prevent Save from storing that text.
 */
internal class MeasurementDraft(
    stored: MeasurementConfiguration,
    today: LocalDate,
    edits: List<MeasurementEdit> = emptyList(),
    blankNames: Set<String> = emptySet(),
    unitRefused: Boolean = false,
    stepRefused: Boolean = false,
) : LoggedViewDraft<MeasurementConfiguration, MeasurementEdit>(stored, today, edits, blankNames), DailyViewEdits {
    /**
     * Whether the unit field holds text that cannot be a unit ([isValidMeasurementUnit]). The
     * field keeps what was typed and the draft the last unit that could be stored, and nothing is
     * stored while it does: what was typed is never swapped for that unit behind the person's
     * back.
     */
    var unitRefused: Boolean by mutableStateOf(unitRefused)
        private set

    /** Whether the step field holds text that is no step ([MeasurementStep.parse]), kept and held back as a refused unit is. */
    var stepRefused: Boolean by mutableStateOf(stepRefused)
        private set

    override val heldBack: Boolean get() = super.heldBack || unitRefused || stepRefused

    override val hasUnsavedWork: Boolean get() = super.hasUnsavedWork || unitRefused || stepRefused

    /** Whether the unit is stored: the field holds one that can be, and no edit to it waits for Save. */
    val unitStored: Boolean get() = !unitRefused && edits.none { it is MeasurementEdit.SetUnit }

    /** Whether the step is stored, as [unitStored] says of the unit. */
    val stepStored: Boolean get() = !stepRefused && edits.none { it is MeasurementEdit.SetStep }

    override fun MeasurementConfiguration.applying(edit: MeasurementEdit, today: LocalDate): MeasurementConfiguration = applyEdit(edit, today)

    override fun MeasurementConfiguration.applyingAll(edits: List<MeasurementEdit>, today: LocalDate): MeasurementConfiguration = applyingEdits(edits, today)

    /** The one name is the measurement's own. */
    override fun MeasurementEdit.names(key: String): Boolean = this is MeasurementEdit.Rename && key == stored.id

    override fun MeasurementEdit.removes(): String? = null

    override fun rename(label: String): Boolean = edit(MeasurementEdit.Rename(label))

    override fun setIcon(icon: ViewIcon): Boolean = edit(MeasurementEdit.SetIcon(icon))

    override fun setHue(hue: ViewHue): Boolean = edit(MeasurementEdit.SetHue(hue))

    override fun setRemind(remind: Boolean): Boolean = edit(MeasurementEdit.SetRemind(remind))

    override fun setReminderTime(at: CheckpointTime): Boolean = edit(MeasurementEdit.SetReminderTime(at))

    override fun takePart(takesPart: Boolean): Boolean = edit(if (takesPart) MeasurementEdit.JoinRewards else MeasurementEdit.LeaveRewards)

    override fun setBankRules(maxPasses: Int, everyKeptDays: Int): Boolean = edit(MeasurementEdit.SetBankRules(maxPasses, everyKeptDays))

    override fun setWhenAway(whenAway: WhenAway): Boolean = edit(MeasurementEdit.SetWhenAway(whenAway))

    /**
     * The unit field was typed into. [typed] without the spaces around it becomes the unit where
     * it can be one; otherwise the draft only knows the field is refused.
     *
     * Only the spaces of the space bar are left out. A line break, a tab or any other character
     * the rule refuses is put to the rule where it stands, at the edge too: cut off there, what is
     * left would be stored as a unit the person never typed.
     */
    fun typeUnit(typed: String) {
        val unit = typed.trim(' ')
        unitRefused = !isValidMeasurementUnit(unit)
        if (!unitRefused) edit(MeasurementEdit.SetUnit(unit))
    }

    /**
     * The step field was typed into. What was [typed] becomes the step where it is a decimal the
     * rule allows, written with either decimal separator; otherwise the draft only knows the field
     * is refused.
     */
    fun typeStep(typed: String) {
        val step = MeasurementStep.parse(typedDecimal(typed))
        stepRefused = step == null
        if (step != null) edit(MeasurementEdit.SetStep(step))
    }

    companion object {
        val Saver: Saver<MeasurementDraft, Any> = listSaver(
            save = {
                listOf(
                    AppStateJsonCodec.encodeMeasurement(it.stored),
                    AppStateJsonCodec.encodeMeasurementEdits(it.edits),
                    ArrayList(it.blankNames),
                    it.today.toString(),
                    it.unitRefused,
                    it.stepRefused,
                )
            },
            restore = {
                MeasurementDraft(
                    stored = AppStateJsonCodec.decodeMeasurement(it[0] as String),
                    edits = AppStateJsonCodec.decodeMeasurementEdits(it[1] as String),
                    blankNames = (it[2] as List<*>).mapTo(LinkedHashSet()) { key -> key as String },
                    today = LocalDate.parse(it[3] as String),
                    unitRefused = it[4] as Boolean,
                    stepRefused = it[5] as Boolean,
                )
            },
        )
    }
}

/**
 * A measurement's settings share [MadeViewSettingsPage], with four parts: Look, its name, icon
 * and color; Unit, its unit and its step ([MeasurementUnitPart]); Reminders, its one reminder a
 * day ([LoggedViewReminder]); and Streak, whether it counts toward the streak with the bank it
 * has, and what a holiday means for it ([MeasurementStreak]). All four change one
 * draft, and Save stores its edits through [onSave].
 *
 * The reminder's switch belongs to Settings with the permissions it may need, so it comes in as
 * [reminderRow], and so does sending the reminder now as a test, through [onSendTest], which is
 * null while the measurement is hidden and no reminder of it can come. [bedtime] is the night a
 * time after waking or before sleep is read against, and [daysOff] the days on which a
 * measurement not due while away has nothing to say. The page opens on Look; a host that shows
 * another part first, as the gallery does, says which through [opensOn].
 *
 * It can be deleted, with every record of it, once the dialog naming it and its [recordedDays]
 * confirmed that: [onDelete] answers whether it happened, and the page is closed by whoever opened
 * it. What went wrong, here or in Settings ([message]), is on the bar.
 */
@Composable
internal fun MadeMeasurementSettingsPage(
    measurement: MeasurementConfiguration,
    today: LocalDate,
    recordedDays: Int,
    bedtime: Bedtime,
    daysOff: HolidaySchedule,
    onSave: (List<MeasurementEdit>) -> ViewWrite,
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
    val draft = rememberSaveable(measurement.id, saver = MeasurementDraft.Saver) { MeasurementDraft(measurement, today) }
    draft.follow(measurement, today)

    MadeViewSettingsPage(
        view = measurement,
        draft = draft,
        testTag = "measurement_settings",
        pageKey = SettingsPage.Measurement(measurement.id).key,
        parts = LoggedViewParts,
        opensOn = opensOn,
        deletion = LoggedViewDeletion(recordedDays, R.plurals.measurement_settings_delete_message, R.string.measurement_settings_delete_failed, onDelete),
        onSave = onSave,
        onClose = onClose,
        leaveRequested = leaveRequested,
        onLeaveAnswered = onLeaveAnswered,
        onUnsavedWorkChange = onUnsavedWorkChange,
        modifier = modifier,
        message = message,
        partLabel = { MeasurementOwnPart.nameOf(it) },
        partTagName = MeasurementOwnPart::tagNameOf,
    ) { shown, edited, onPickTime, nameField ->
        MadeMeasurementPart(
            part = shown,
            measurement = edited,
            draft = draft,
            today = today,
            bedtime = bedtime,
            daysOff = daysOff,
            testTag = "measurement_settings",
            reminderRow = reminderRow,
            onSendTest = onSendTest,
            onPickTime = onPickTime,
            nameField = nameField,
        )
    }
}

/** A measurement's second part holds its unit and its step, not items. */
internal val MeasurementOwnPart = OwnPart(R.string.measurement_settings_unit_part, tagName = "unit")

/**
 * One part of a made measurement's page, the page's and the setup's alike, over [measurement] as
 * [draft] is changing it. [nameField] is Look's name, which the page and the setup keep
 * differently; [reminderRow], [onSendTest] and [onPickTime] are the Reminders part's
 * ([LoggedViewReminder]). Every tag follows from [testTag].
 */
@Composable
internal fun MadeMeasurementPart(
    part: ChecklistPage,
    measurement: MeasurementConfiguration,
    draft: MeasurementDraft,
    today: LocalDate,
    bedtime: Bedtime,
    daysOff: HolidaySchedule,
    testTag: String,
    reminderRow: @Composable (LoggedView, String, (Boolean) -> Unit) -> Unit,
    onSendTest: ((TestReminder.AtCheckpoint) -> Unit)?,
    onPickTime: () -> Unit,
    nameField: @Composable () -> Unit,
) {
    when (part) {
        ChecklistPage.Look -> LoggedViewLook(view = measurement, edits = draft, testTag = testTag, icons = MeasurementIcons) { nameField() }

        ChecklistPage.Items -> MeasurementUnitPart(measurement = measurement, draft = draft, testTag = testTag)

        ChecklistPage.Reminders -> LoggedViewReminder(
            view = measurement,
            edits = draft,
            today = today,
            bedtime = bedtime,
            daysOff = daysOff,
            testTag = testTag,
            reminderRow = reminderRow,
            onSendTest = onSendTest,
            onPickTime = onPickTime,
        )

        ChecklistPage.Streak -> MeasurementStreak(measurement = measurement, draft = draft, today = today, testTag = testTag)
    }
}

/**
 * A measurement's Streak part ([DailyViewStreak]): the switch and the two steppers every daily
 * view has. A bank still earning by the week shows its two numbers in them, with a line under
 * them that says its days count within a week until a change is saved, tagged
 * `<testTag>_earn_note`: changing either number is what makes it earn by kept days from that day.
 */
@Composable
internal fun MeasurementStreak(measurement: MeasurementConfiguration, draft: MeasurementDraft, today: LocalDate, testTag: String) {
    val week = measurement.weekRewards
    DailyViewStreak(
        view = measurement,
        edits = draft,
        today = today,
        testTag = testTag,
        rewards = week?.acrossWeeks() ?: measurement.standardRewards,
        earnNote = if (week != null) stringResource(R.string.measurement_settings_still_by_week) else null,
    )
}

/**
 * The configured unit, adjustment step and trend line, shared by measurement settings and setup,
 * over [measurement] as [draft] is changing it. Under the two fields a line says what changing the
 * unit does not do: no number already recorded is converted. Tags follow from [testTag]: the unit is `<testTag>_unit`, the
 * step `<testTag>_step` and the line `<testTag>_unit_note`.
 */
@Composable
internal fun MeasurementUnitPart(measurement: MeasurementConfiguration, draft: MeasurementDraft, testTag: String) {
    val locale = currentLocale()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TypedPartField(
            text = measurement.unit,
            key = measurement.id,
            label = stringResource(R.string.measurement_settings_unit),
            error = if (draft.unitRefused) stringResource(R.string.measurement_settings_unit_invalid, MaximumMeasurementUnitLength) else null,
            settled = draft.unitStored,
            testTag = "${testTag}_unit",
            onType = draft::typeUnit,
        )
        TypedPartField(
            text = formatMetricNumber(measurement.step.value, locale),
            key = "${measurement.id}/step",
            label = stringResource(R.string.measurement_settings_step),
            error = if (draft.stepRefused) stringResource(R.string.measurement_settings_step_invalid, MaximumMeasurementStep, MeasurementStepDecimals) else null,
            settled = draft.stepStored,
            testTag = "${testTag}_step",
            keyboardType = KeyboardType.Decimal,
            onType = draft::typeStep,
        )
        EditorNote(stringResource(R.string.measurement_settings_unit_note), testTag = "${testTag}_unit_note")
        MeasurementTrendChoice(trendDays = measurement.trendDays, testTag = testTag) { draft.edit(MeasurementEdit.SetTrendDays(it)) }
    }
}

/** The spans offered: one to four weeks. A stored span outside them shows with no tile chosen. */
private val TrendDayChoices = listOf(7, 14, 21, 28)

/**
 * How many days the chart's trend line follows, with a line under the tiles that says it in a
 * sentence. Tags follow from [testTag]: each tile is `<testTag>_trend_<days>`, the line
 * `<testTag>_trend_note`.
 */
@Composable
private fun MeasurementTrendChoice(trendDays: Int, testTag: String, onChoose: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.measurement_settings_trend),
            style = MaterialTheme.typography.titleSmall,
        )
        MomentumChoiceTileRow {
            TrendDayChoices.forEach { days ->
                MomentumChoiceTile(
                    label = pluralStringResource(R.plurals.measurement_settings_trend_days, days, days),
                    selected = days == trendDays,
                    onClick = { if (days != trendDays) onChoose(days) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    testTag = "${testTag}_trend_$days",
                )
            }
        }
        EditorNote(pluralStringResource(R.plurals.measurement_settings_trend_note, trendDays, trendDays), testTag = "${testTag}_trend_note")
    }
}
