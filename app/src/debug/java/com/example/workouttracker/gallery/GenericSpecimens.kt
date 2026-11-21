package com.example.workouttracker.gallery

import com.example.workouttracker.domain.trackerRef

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import com.example.workouttracker.core.ui.LocalViewColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.workouttracker.app.AppDestination
import com.example.workouttracker.core.locale.currentLocale
import com.example.workouttracker.core.time.calendarWeekDateKeys
import com.example.workouttracker.core.time.calendarWeekdayShortLabel
import com.example.workouttracker.core.ui.CalendarWeekdayHeader
import com.example.workouttracker.core.ui.CompletionButton
import com.example.workouttracker.core.ui.CompletionButtonColors
import com.example.workouttracker.core.ui.DayCover
import com.example.workouttracker.core.ui.DayCoverMark
import com.example.workouttracker.core.ui.DayCoverSeal
import com.example.workouttracker.core.ui.sealInk
import com.example.workouttracker.core.ui.blankDayStyle
import com.example.workouttracker.core.ui.MomentumBlockingDialog
import com.example.workouttracker.core.ui.MomentumButton
import com.example.workouttracker.core.ui.MomentumButtonVariant
import com.example.workouttracker.core.ui.MomentumCard
import com.example.workouttracker.core.ui.MomentumCardDefaults
import com.example.workouttracker.core.ui.MomentumCheckbox
import com.example.workouttracker.core.ui.MomentumCheckboxLook
import com.example.workouttracker.core.ui.MomentumCheckboxRow
import com.example.workouttracker.core.ui.MomentumChoiceTile
import com.example.workouttracker.core.ui.MomentumChoiceTileRow
import com.example.workouttracker.core.ui.MomentumTileGrid
import com.example.workouttracker.core.ui.MomentumDateField
import com.example.workouttracker.core.ui.DiscardChangesDialog
import com.example.workouttracker.core.ui.MomentumDialog
import com.example.workouttracker.core.ui.MomentumDialogAction
import com.example.workouttracker.core.ui.MomentumDropdown
import com.example.workouttracker.core.ui.MomentumOpenTaskBadge
import com.example.workouttracker.core.ui.MomentumProgressBar
import com.example.workouttracker.core.ui.MomentumStepPopOut
import com.example.workouttracker.core.ui.MomentumPopOutGroup
import com.example.workouttracker.core.ui.StepEditButton
import com.example.workouttracker.core.ui.StepRefusal
import com.example.workouttracker.core.format.formatMetricNumber
import com.example.workouttracker.core.format.parseTypedNumber
import com.example.workouttracker.core.ui.MomentumRemoveButton
import com.example.workouttracker.core.ui.MomentumRewardBadge
import com.example.workouttracker.core.ui.MomentumTabs
import com.example.workouttracker.core.ui.MomentumTextField
import com.example.workouttracker.core.ui.RecentDayCalendar
import com.example.workouttracker.core.ui.RecentDayCalendarStyle
import com.example.workouttracker.core.ui.ReservedMessage
import com.example.workouttracker.core.ui.SelectedDayLine
import androidx.compose.foundation.layout.Spacer
import com.example.workouttracker.core.ui.MomentumButtonDefaults
import androidx.compose.foundation.layout.widthIn
import com.example.workouttracker.core.ui.MomentumActionBar
import com.example.workouttracker.core.ui.MomentumBarAction
import com.example.workouttracker.core.ui.ActionBarLine
import com.example.workouttracker.core.ui.ViewTheme
import com.example.workouttracker.core.ui.theme.MomentumHueRegistry
import com.example.workouttracker.core.ui.theme.MomentumRampRegistry
import com.example.workouttracker.core.ui.theme.MomentumRamps
import com.example.workouttracker.core.ui.theme.MomentumSurfaceRegistry
import kotlinx.coroutines.delay
import java.time.DayOfWeek

/** Everything no single view owns: shared components, app icons, Material controls, the palette. */
internal val genericSpecimens: List<GallerySpecimen> = homeRowSpecimens + listOf(
    GallerySpecimen("MomentumButton, enabled") { ButtonVariantsSpecimen(enabled = true) },
    GallerySpecimen("MomentumButton, disabled") { ButtonVariantsSpecimen(enabled = false) },
    GallerySpecimen("MomentumButton with icon") {
        MomentumButton(text = "Remind me", onClick = {}, icon = Icons.Rounded.Alarm)
    },
    GallerySpecimen("MomentumCard") { CardsSpecimen() },
    GallerySpecimen("MomentumOpenTaskBadge") {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MomentumOpenTaskBadge(outline = MaterialTheme.colorScheme.primary)
            MomentumOpenTaskBadge(outline = MaterialTheme.colorScheme.primary, optional = true)
            MomentumOpenTaskBadge(outline = MaterialTheme.colorScheme.primary, done = 1, parts = 2)
            MomentumOpenTaskBadge(outline = MaterialTheme.colorScheme.primary, done = 2, parts = 3)
            MomentumRewardBadge()
        }
    },
    GallerySpecimen("DayCoverMark: free day, token, rest day, freeze") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DayCover.entries.forEach { cover ->
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small),
                ) { DayCoverMark(cover, MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    },
    GallerySpecimen("DayCoverSeal: token, rest day, freeze, on a blank day") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DayCover.entries.mapNotNull { cover -> cover.sealInk?.let { cover to it } }.forEach { (cover, ink) ->
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small),
                    contentAlignment = Alignment.Center,
                ) {
                    DayCoverSeal(cover)
                    Text("15", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = ink)
                }
            }
        }
    },
    GallerySpecimen("Choice cards") { ChoiceCardsSpecimen() },
    GallerySpecimen("MomentumChoiceTile") { ChoiceTilesSpecimen() },
    GallerySpecimen("MomentumTabs") { TabsSpecimen() },
    GallerySpecimen("MomentumDropdown") { DropdownSpecimen() },
    GallerySpecimen("MomentumCheckbox states") { CheckboxStatesSpecimen() },
    GallerySpecimen("MomentumCheckboxRow rhythm") { CheckboxRhythmSpecimen() },
    GallerySpecimen("ReservedMessage") { ReservedMessageSpecimen() },
    GallerySpecimen("SelectedDayLine") { SelectedDayLineSpecimen() },
    GallerySpecimen("MomentumActionBar") { ActionBarSpecimen() },
    GallerySpecimen("MomentumDateField and MomentumTextField") { DateFieldSpecimen() },
    GallerySpecimen("MomentumRemoveButton") { RemoveButtonSpecimen() },
    GallerySpecimen("MomentumStepPopOut") { StepPopOutSpecimen() },
    GallerySpecimen("MomentumProgressBar") { ProgressBarSpecimen() },
    GallerySpecimen("MomentumDialog") { DialogSpecimen() },
    GallerySpecimen("MomentumRewardBadge") { RewardBadgeSpecimen() },
    GallerySpecimen("CompletionButton states") { CompletionStatesSpecimen() },
    GallerySpecimen("MomentaryCompletionButton, Restrained") {
        MomentarySavingSpecimen(
            colors = neutralCompletionColors(),
            icon = Icons.Rounded.Alarm,
            actionLabel = "Mark as done",
            completedLabel = "Done",
            testTag = "gallery_momentary",
        )
    },
    GallerySpecimen("RecentDayCalendar, today mid-week") {
        RecentDayCalendarSpecimen(FixtureMidWeekTodayKey)
    },
    GallerySpecimen("RecentDayCalendar, today on Monday") {
        RecentDayCalendarSpecimen(FixtureMondayTodayKey)
    },
    GallerySpecimen("CalendarWeekdayHeader") { CalendarWeekdayHeader() },
    GallerySpecimen("All views") { AllViewsSpecimen() },
    GallerySpecimen("Material controls") { MaterialControlsSpecimen() },
    GallerySpecimen("Palette hues") { HuePaletteSpecimen() },
    GallerySpecimen("Ordinal ramps") { RampSpecimen() },
    GallerySpecimen("Neutral surfaces") { SurfaceSpecimen() },
    GallerySpecimen("Typography") { TypographySpecimen() },
)

@Composable
private fun ButtonVariantsSpecimen(enabled: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        MomentumButtonVariant.entries.forEach { variant ->
            MomentumButton(
                text = variant.name,
                onClick = {},
                variant = variant,
                enabled = enabled,
            )
        }
    }
}

/**
 * The generic states use neutral Material roles; the celebration is judged in the Reading section,
 * where the real feature colors live.
 */
@Composable
private fun CompletionStatesSpecimen() {
    val colors = neutralCompletionColors()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CompletionButton(
            actionLabel = "Mark as done",
            completedLabel = "Done today",
            completed = false,
            onClick = {},
            icon = Icons.Rounded.Alarm,
            colors = colors,
            testTag = "gallery_completion_idle",
        )
        CompletionButton(
            actionLabel = "Mark as done",
            completedLabel = "Done today",
            completed = false,
            onClick = {},
            icon = Icons.Rounded.Alarm,
            colors = colors,
            testTag = "gallery_completion_pending",
            pending = true,
        )
        CompletionButton(
            actionLabel = "Mark as done",
            completedLabel = "Done today",
            completed = true,
            onClick = {},
            icon = Icons.Rounded.Alarm,
            colors = colors,
            testTag = "gallery_completion_completed",
        )
        CompletionButton(
            actionLabel = "Mark as done",
            completedLabel = "Done today",
            completed = false,
            onClick = {},
            icon = Icons.Rounded.Alarm,
            colors = colors,
            testTag = "gallery_completion_disabled",
            enabled = false,
        )
    }
}

@Composable
private fun neutralCompletionColors(): CompletionButtonColors {
    val scheme = MaterialTheme.colorScheme
    return CompletionButtonColors(
        actionContainer = scheme.primary,
        actionContent = scheme.onPrimary,
        completedContainer = scheme.secondaryContainer,
        completedContent = scheme.onSecondaryContainer,
        disabledContainer = scheme.surfaceContainerHighest,
        disabledContent = scheme.onSurfaceVariant,
        depth = scheme.onPrimaryContainer,
        burstPrimary = scheme.primary,
        burstSecondary = scheme.secondary,
        burstTertiary = scheme.tertiary,
    )
}

/** Both card forms side by side: the flat static group and the one that sinks when pressed. */
@Composable
private fun CardsSpecimen() {
    var presses by remember { mutableIntStateOf(0) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MomentumCard(modifier = Modifier.fillMaxWidth()) {
            CardBody("Static: flat, because a ledge means pressable")
        }
        MomentumCard(
            onClick = { presses++ },
            modifier = Modifier.fillMaxWidth(),
            testTag = "gallery_card_clickable",
        ) {
            CardBody("Clickable, pressed $presses times")
        }
        MomentumCard(onClick = {}, modifier = Modifier.fillMaxWidth(), enabled = false) {
            CardBody("Clickable, disabled")
        }
    }
}

/**
 * The clickable card as a choice. The single-choice group shows that only one card can wear the
 * accent; the multi-choice group shows a card carrying its item's own color instead of the view's.
 */
@Composable
private fun ChoiceCardsSpecimen() {
    val options = listOf("Morning", "Afternoon", "Evening")
    var chosen by remember { mutableStateOf(options.first()) }
    val tags = MomentumHueRegistry.take(3)
    val picked = remember { mutableStateListOf(tags.first().first) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        options.forEach { option ->
            MomentumCard(
                onClick = { chosen = option },
                modifier = Modifier.fillMaxWidth(),
                selected = option == chosen,
                role = Role.RadioButton,
                testTag = "gallery_choice_$option",
            ) {
                CardBody(option)
            }
        }
        tags.forEach { (name, hue) ->
            MomentumCard(
                onClick = { if (name in picked) picked.remove(name) else picked.add(name) },
                modifier = Modifier.fillMaxWidth(),
                selected = name in picked,
                selectedColors = MomentumCardDefaults.selectedColors(hue.face),
                role = Role.Checkbox,
                testTag = "gallery_multi_choice_$name",
            ) {
                CardBody(name)
            }
        }
    }
}

/**
 * Every state the box can be in, so fill, outline, ledge and disabled can be judged side by side.
 * The second row holds the looks of an item that logs only misses: an assumed check, the mark
 * alone and quieter than a recorded one, and the cross it swings to once it is marked missed.
 */
@Composable
private fun CheckboxStatesSpecimen() {
    var checked by remember { mutableStateOf(false) }
    Column {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MomentumCheckbox(
                checked = checked,
                onCheckedChange = { checked = it },
                testTag = "gallery_checkbox",
            )
            MomentumCheckbox(checked = true, onCheckedChange = {})
            MomentumCheckbox(checked = false, onCheckedChange = {}, enabled = false)
            MomentumCheckbox(checked = true, onCheckedChange = {}, enabled = false)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            var assumed by remember { mutableStateOf(true) }
            MomentumCheckbox(
                look = if (assumed) MomentumCheckboxLook.Assumed else MomentumCheckboxLook.Empty,
                onCheckedChange = { assumed = it },
                testTag = "gallery_checkbox_assumed",
            )
            var kept by remember { mutableStateOf(false) }
            MomentumCheckbox(
                look = if (kept) MomentumCheckboxLook.Assumed else MomentumCheckboxLook.Crossed,
                onCheckedChange = { kept = it },
                testTag = "gallery_checkbox_crossed",
            )
            MomentumCheckbox(look = MomentumCheckboxLook.Crossed, onCheckedChange = {}, enabled = false)
        }
    }
}

/**
 * Five labelled rows in a view theme. Checking them one after another is the only way to feel
 * whether the motion still reads as crisp on the tenth tap rather than on the first. A sixth
 * swings between an assumed check and a cross, which must stay as quiet however often it swings.
 */
@Composable
private fun CheckboxRhythmSpecimen() {
    val identity = demoTrackerIdentity(DemoTask.Supplements.view.trackerRef)
    val labels = listOf("Vitamin D", "Creatine", "Magnesium", "Omega 3", "Zinc")
    val taken = remember { mutableStateListOf<String>() }

    ViewTheme(identity.colors) {
        Column {
            labels.forEach { label ->
                val isTaken = label in taken
                MomentumCheckboxRow(
                    checked = isTaken,
                    onCheckedChange = { if (isTaken) taken.remove(label) else taken.add(label) },
                    testTag = "gallery_checkbox_row_$label",
                ) {
                    Text(text = label, style = MaterialTheme.typography.titleMedium)
                }
            }
            // An item that logs only misses swings between its assumed check and the cross.
            var missed by remember { mutableStateOf(false) }
            MomentumCheckboxRow(
                look = if (missed) MomentumCheckboxLook.Crossed else MomentumCheckboxLook.Assumed,
                onCheckedChange = { missed = !it },
                stateDescription = if (missed) "Missed" else "Assumed done",
                testTag = "gallery_checkbox_row_only_misses",
            ) {
                Text(text = "Brushing, only misses", style = MaterialTheme.typography.titleMedium)
            }
            MomentumCheckboxRow(checked = true, onCheckedChange = {}, enabled = false) {
                Text(text = "Disabled", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/**
 * The tile forms: a plain row of choices, one where each option carries its own color, one where
 * the option's color is an ordinal ramp step shown as a swatch even when unpicked, and a week of
 * shortened labels that TalkBack says in full. The long label proves the row keeps every tile as
 * tall as the tallest.
 */
@Composable
private fun ChoiceTilesSpecimen() {
    val options = listOf("Much less", "About the same", "More")
    var chosen by remember { mutableStateOf<String?>(null) }
    val kinds = MomentumHueRegistry.take(3)
    val picked = remember { mutableStateListOf(kinds.first().first) }
    val ramp = MomentumRamps.Orange.take(3)
    var step by remember { mutableStateOf<Int?>(null) }
    val days = remember { mutableStateListOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY) }
    val locale = currentLocale()
    val gridOptions = listOf("Benzodiazepines", "Coffee", "Nicotine pouches", "Tea", "Water")
    var gridChoice by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // A grid keeps a short last row's tiles the width of the others, and a label wider than
        // its tile wraps once, hyphenated, instead of being cut.
        MomentumTileGrid(gridOptions, columns = 3) { option, tileModifier ->
            MomentumChoiceTile(
                label = option,
                selected = option == gridChoice,
                onClick = { gridChoice = option.takeIf { it != gridChoice } },
                modifier = tileModifier,
                icon = Icons.Rounded.Alarm,
                testTag = "gallery_grid_$option",
            )
        }
        MomentumChoiceTileRow {
            options.forEach { option ->
                MomentumChoiceTile(
                    label = option,
                    selected = option == chosen,
                    onClick = { chosen = option.takeIf { it != chosen } },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    testTag = "gallery_tile_$option",
                )
            }
        }
        MomentumChoiceTileRow {
            kinds.forEach { (name, hue) ->
                MomentumChoiceTile(
                    label = name,
                    selected = name in picked,
                    onClick = { if (name in picked) picked.remove(name) else picked.add(name) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    icon = Icons.Rounded.Alarm,
                    accent = hue.face,
                    role = Role.Checkbox,
                    testTag = "gallery_tile_accent_$name",
                )
            }
        }
        MomentumChoiceTileRow {
            ramp.forEachIndexed { index, rampStep ->
                MomentumChoiceTile(
                    label = "Step ${index + 1}",
                    selected = index == step,
                    onClick = { step = index.takeIf { it != step } },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    accent = rampStep.fill,
                    swatch = rampStep.fill,
                    testTag = "gallery_tile_swatch_$index",
                )
            }
        }
        MomentumChoiceTileRow {
            DayOfWeek.entries.forEach { day ->
                MomentumChoiceTile(
                    label = stringResource(calendarWeekdayShortLabel(day)),
                    selected = day in days,
                    onClick = { if (day in days) days.remove(day) else days.add(day) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    role = Role.Checkbox,
                    testTag = "gallery_tile_day_${day.value}",
                    spoken = day.getDisplayName(java.time.format.TextStyle.FULL, locale),
                )
            }
        }
    }
}

/**
 * The neutral chrome, the same tabs wearing a view's identity, the checklist editor's German
 * labels, one long enough to hyphenate onto a second line, and the setup's numbered steps with the
 * ones ahead not yet pressable. Every tab must grow with the tallest and the underline must slide
 * without moving the text below.
 */
@Composable
private fun TabsSpecimen() {
    val identity = viewIdentities(AppDestination.Checklist).first()
    val short = listOf("Look", "Items", "Reminders", "Streak")
    val long = listOf("Aussehen", "Punkte", "Erinnerungen", "Streak")
    val steps = short.mapIndexed { index, name -> "${index + 1} $name" }
    var neutralTab by remember { mutableStateOf(short.first()) }
    var viewTab by remember { mutableStateOf(short[1]) }
    var longTab by remember { mutableStateOf(long.last()) }
    var step by remember { mutableStateOf(steps[1]) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        MomentumTabs(
            tabs = short,
            selected = neutralTab,
            onSelect = { neutralTab = it },
            label = { it },
            testTag = { "gallery_tab_$it" },
        )
        ViewTheme(identity.colors) {
            MomentumTabs(tabs = short, selected = viewTab, onSelect = { viewTab = it }, label = { it })
        }
        MomentumTabs(tabs = long, selected = longTab, onSelect = { longTab = it }, label = { it })
        Text(
            text = "Showing $longTab",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ViewTheme(identity.colors) {
            MomentumTabs(
                tabs = steps,
                selected = step,
                onSelect = { step = it },
                label = { it },
                enabled = { steps.indexOf(it) <= steps.indexOf(step) },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MomentumButton(
                text = "Back",
                onClick = { step = steps[steps.indexOf(step) - 1] },
                variant = MomentumButtonVariant.Secondary,
                enabled = step != steps.first(),
            )
            MomentumButton(
                text = "Next",
                onClick = { step = steps[steps.indexOf(step) + 1] },
                enabled = step != steps.last(),
            )
        }
    }
}

/** Closed it shows label and value; open, its menu marks the current choice in the view's color. */
@Composable
private fun DropdownSpecimen() {
    val identity = demoTrackerIdentity(DemoTask.Reading.view.trackerRef)
    val languages = listOf("System language", "English", "Deutsch")
    val goals = listOf("30 min a week", "60 min a week", "90 min a week", "120 min a week")
    var language by remember { mutableStateOf(languages.first()) }
    var goal by remember { mutableStateOf(goals[1]) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MomentumDropdown(
            label = "Language",
            options = languages,
            selected = language,
            onSelect = { language = it },
            optionLabel = { it },
            modifier = Modifier.fillMaxWidth(),
            testTag = "gallery_dropdown",
            optionTestTag = { "gallery_dropdown_$it" },
        )
        ViewTheme(identity.colors) {
            MomentumDropdown(
                label = "Weekly goal",
                options = goals,
                selected = goal,
                onSelect = { goal = it },
                optionLabel = { it },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Toggling the messages must move nothing: the button below them is the thing to watch. The
 * second slot may show a sentence that wraps, and names it, so it holds that height from the start.
 */
@Composable
private fun ReservedMessageSpecimen() {
    var speaking by remember { mutableStateOf(false) }
    val wrapping = "Not sent while the reminder is off, which is a sentence long enough to take a second line at a large font size."
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ReservedMessage(
            text = "Couldn't save. Try again.".takeIf { speaking },
            testTag = "gallery_reserved_message",
        )
        ReservedMessage(
            text = wrapping.takeIf { speaking },
            testTag = "gallery_reserved_message_wrapping",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            reserving = listOf(wrapping),
        )
        MomentumButton(
            text = if (speaking) "Hide message" else "Show message",
            onClick = { speaking = !speaking },
            variant = MomentumButtonVariant.Secondary,
        )
    }
}

/**
 * The line names a day only while it is not the one the screen opens on, and takes no room while
 * it says nothing: on the opening day the button below sits where the line was, with no gap above
 * it, and moves down by the line when another day is picked.
 */
@Composable
private fun SelectedDayLineSpecimen() {
    var onAnotherDay by remember { mutableStateOf(true) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SelectedDayLine(
            selectedDateKey = if (onAnotherDay) "2026-09-14" else FixtureMidWeekTodayKey,
            openingDateKey = FixtureMidWeekTodayKey,
            testTag = "gallery_selected_day",
        )
        MomentumButton(
            text = if (onAnotherDay) "Select the opening day" else "Select another day",
            onClick = { onAnotherDay = !onAnotherDay },
            variant = MomentumButtonVariant.Secondary,
        )
    }
}

/**
 * A page's pinned bar over content that runs on under it. Next fills the bar and looks not ready
 * until the step is complete; pressing it then says why, over the content, and the bar never moves.
 */
@Composable
private fun ActionBarSpecimen() {
    var ready by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Content the bar sits on", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(48.dp))
        MomentumActionBar(
            waitingFor = ActionBarLine("Add at least one item to continue", "gallery_action_bar_waiting"),
            leading = {
                MomentumButton(
                    text = "Delete",
                    onClick = {},
                    modifier = Modifier.fillMaxHeight(),
                    variant = MomentumButtonVariant.Secondary,
                    colors = MomentumButtonDefaults.destructiveColors(MomentumButtonVariant.Secondary),
                )
            },
        ) {
            MomentumBarAction(text = "Next", onClick = {}, ready = ready, testTag = "gallery_action_bar_next")
        }
        MomentumButton(
            text = if (ready) "Undo" else "Complete the step",
            onClick = { ready = !ready },
            variant = MomentumButtonVariant.Secondary,
        )
    }
}

/** Empty, part-way, full and past the goal; the button shows that a change animates in place. */
@Composable
private fun ProgressBarSpecimen() {
    val identity = demoTrackerIdentity(DemoTask.Reading.view.trackerRef)
    var minutes by remember { mutableIntStateOf(15) }
    ViewTheme(identity.colors) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(0, minutes, 60, 75).forEach { value ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MomentumProgressBar(
                        progress = value / 60f,
                        description = "$value / 60 min this week",
                        modifier = Modifier.weight(1f),
                    )
                    Text("$value / 60 min", style = MaterialTheme.typography.titleMedium)
                }
            }
            MomentumButton(
                text = "Add 15 min",
                onClick = { minutes = (minutes + 15) % 75 },
                variant = MomentumButtonVariant.Secondary,
            )
        }
    }
}

/** The error lives in the label, so toggling validity must move nothing under the field. */
@Composable
private fun DateFieldSpecimen() {
    var text by remember { mutableStateOf("2026-09-19") }
    var amount by remember { mutableStateOf("0.5") }
    var invalid by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MomentumDateField(
            value = text,
            onValueChange = { text = it },
            isError = invalid,
            modifier = Modifier.fillMaxWidth(),
            testTag = "gallery_date_field",
        )
        // The date field is this field with a fixed label and message; both must hold still.
        MomentumTextField(
            value = amount,
            onValueChange = { amount = it },
            label = "Amount (optional)",
            error = "Enter a positive amount or leave it blank".takeIf { invalid },
            modifier = Modifier.fillMaxWidth(),
            suffix = "g",
            testTag = "gallery_text_field",
        )
        // A narrow number against its sign, as a token step's percent of the goal: the number is
        // set at the end, so "200 %" reads as one value.
        MomentumTextField(
            value = "200",
            onValueChange = {},
            label = "Of the goal",
            error = null,
            modifier = Modifier.width(102.dp),
            suffix = "%",
            textAlign = TextAlign.End,
            testTag = "gallery_text_field_number",
        )
        // A field that only shows what cannot be changed here, as a built-in question's text: it
        // wraps, since nothing can scroll its line.
        MomentumTextField(
            value = "How would you rate the overall quality of what you ate?",
            onValueChange = {},
            label = "Question",
            error = null,
            modifier = Modifier.fillMaxWidth(),
            enabled = false,
            testTag = "gallery_text_field_disabled",
        )
        MomentumButton(
            text = if (invalid) "Make valid" else "Make invalid",
            onClick = { invalid = !invalid },
            variant = MomentumButtonVariant.Secondary,
            testTag = "gallery_date_field_toggle",
        )
    }
}

/**
 * The row as a screen uses it: a main action taking the width and the square trash can at its end.
 * Toggling must grey the square out without moving a pixel, and both faces and both ledges must
 * line up. The count proves that only the second tap removes.
 */
@Composable
private fun RemoveButtonSpecimen() {
    var removals by remember { mutableIntStateOf(0) }
    var hasSomethingToRemove by remember { mutableStateOf(true) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            MomentumButton(
                text = if (hasSomethingToRemove) "Saved day" else "Empty day",
                onClick = { hasSomethingToRemove = !hasSomethingToRemove },
                modifier = Modifier.weight(1f),
            )
            MomentumRemoveButton(
                description = "Remove specimen day",
                onRemove = { removals++ },
                testTag = "gallery_remove_secondary",
                enabled = hasSomethingToRemove,
            )
        }
        MomentumRemoveButton(
            description = "Remove specimen record",
            onRemove = { removals++ },
            testTag = "gallery_remove_quiet",
            variant = MomentumButtonVariant.Quiet,
        )
        Text(
            text = "Removed $removals",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The real pop-out over a step kept in memory. Opening it must move nothing under the row; a step
 * of 0 or above 20 is refused in the field's label, and "fail" says the store refused it.
 */
@Composable
private fun StepPopOutSpecimen() {
    var step by remember { mutableStateOf(2.5) }
    var open by remember { mutableStateOf(false) }
    var weight by remember { mutableStateOf(60.0) }
    val locale = currentLocale()
    MomentumPopOutGroup {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MomentumStepPopOut(
                open = open,
                onClose = { open = false },
                step = formatMetricNumber(step, locale),
                unit = "kg",
                label = "Step of the − and + buttons",
                invalidMessage = "0.25 to 20 kg",
                onSave = { typed ->
                    when {
                        typed.trim() == "fail" -> StepRefusal.NotStored
                        else -> parseTypedNumber(typed)?.takeIf { it in 0.25..20.0 }?.let { step = it; null } ?: StepRefusal.Invalid
                    }
                },
                testTag = "gallery_step",
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MomentumButton(text = "−${formatMetricNumber(step, locale)} kg", onClick = { weight -= step }, modifier = Modifier.weight(1f), variant = MomentumButtonVariant.Secondary)
                    MomentumButton(text = "+${formatMetricNumber(step, locale)} kg", onClick = { weight += step }, modifier = Modifier.weight(1f), variant = MomentumButtonVariant.Secondary)
                    StepEditButton(onClick = { open = true }, testTag = "gallery_step")
                }
            }
            Text(
                text = "${formatMetricNumber(weight, locale)} kg",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A dialog can only be judged open, so each kind gets a button that opens the real one: the plain
 * guard over unsaved work, the destructive confirm, and a help dialog with scrollable body content.
 */
@Composable
private fun DialogSpecimen() {
    var open by remember { mutableStateOf<DialogSpecimenKind?>(null) }
    var lastAnswer by remember { mutableStateOf("Nothing answered yet") }

    fun answer(text: String) {
        lastAnswer = text
        open = null
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DialogSpecimenKind.entries.forEach { kind ->
            MomentumButton(
                text = kind.label,
                onClick = { open = kind },
                modifier = Modifier.fillMaxWidth(),
                variant = MomentumButtonVariant.Secondary,
            )
        }
        Text(
            text = lastAnswer,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    when (open) {
        null -> Unit

        // The real question every day screen asks before Back or another day drops its changes.
        DialogSpecimenKind.Confirm -> DiscardChangesDialog(
            testTag = "gallery_discard",
            onDiscard = { answer("Discarded") },
            onKeep = { answer("Kept editing") },
        )

        DialogSpecimenKind.Destructive -> MomentumDialog(
            onDismissRequest = { answer("Dismissed by tapping outside") },
            title = "Replace all records and settings?",
            message = "Restoring replaces every current record and app setting with the backup " +
                "contents. The app will reload after restoration.",
            confirm = MomentumDialogAction(
                text = "Replace and restore",
                onClick = { answer("Restored") },
                destructive = true,
            ),
            dismiss = MomentumDialogAction("Cancel", { answer("Cancelled") }),
        )

        DialogSpecimenKind.RichContent -> MomentumDialog(
            onDismissRequest = { answer("Dismissed by tapping outside") },
            title = "What the levels mean",
            confirm = MomentumDialogAction("Close", { answer("Closed") }),
            content = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    repeat(8) { index ->
                        Text(
                            text = "Level ${index + 1} — a line of guidance long enough that the " +
                                "body has to scroll inside the dialog rather than grow past it.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    MomentumButton(
                        text = "Open the guidance",
                        onClick = { answer("Followed a link") },
                        variant = MomentumButtonVariant.Quiet,
                    )
                }
            },
        )

        DialogSpecimenKind.NeedsChoice -> {
            var chosen by remember { mutableStateOf(false) }
            MomentumDialog(
                onDismissRequest = { answer("Dismissed by tapping outside") },
                title = "Add a holiday",
                confirm = MomentumDialogAction("Add", { answer("Added") }, enabled = chosen),
                dismiss = MomentumDialogAction("Cancel", { answer("Cancelled") }),
                content = {
                    MomentumCheckboxRow(checked = chosen, onCheckedChange = { chosen = it }) {
                        Text("A day is chosen")
                    }
                },
            )
        }

        DialogSpecimenKind.Blocking -> {
            // It has no way out by design, so the specimen ends the "work" on its own.
            LaunchedEffect(Unit) {
                delay(BlockingSpecimenMillis)
                answer("The blocking work finished")
            }
            MomentumBlockingDialog(message = "Restoring your records…")
        }
    }
}

private const val BlockingSpecimenMillis = 2_000L

private enum class DialogSpecimenKind(val label: String) {
    Confirm("Discard changes"),
    Destructive("Destructive confirm"),
    RichContent("Rich content"),
    NeedsChoice("Confirm waits for a choice"),
    Blocking("Blocking, no actions"),
}

/** The coin is judged the way it is worn: alone, and hanging over the corner it decorates. */
@Composable
private fun RewardBadgeSpecimen() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MomentumRewardBadge()
        Box {
            MomentumCard(modifier = Modifier.width(104.dp).height(76.dp)) {
                CardBody("Logged")
            }
            MomentumRewardBadge(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 12.dp, y = (-12).dp),
            )
        }
    }
}

@Composable
private fun CardBody(text: String) {
    Text(text = text, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun RecentDayCalendarSpecimen(todayKey: String) {
    var selectedDateKey by remember { mutableStateOf(todayKey) }
    val colors = LocalViewColors.current
    val days = calendarWeekDateKeys(todayKey)
    // A few recorded days and one of each cover, so a seal is seen beside a filled day.
    val recorded = days.filterIndexed { index, _ -> index % 3 == 0 }.toSet()
    val covers = listOf(DayCover.Token, DayCover.Rest, DayCover.Freeze, DayCover.Free)
        .zip(days.filterIndexed { index, _ -> index % 3 == 1 })
        .associate { (cover, key) -> key to cover }
    RecentDayCalendar(
        days = days,
        dateKey = { it },
        selectedDateKey = selectedDateKey,
        todayKey = todayKey,
        onDayClick = { selectedDateKey = it },
        dayStyle = { key ->
            if (key in recorded) RecentDayCalendarStyle(colors.accent, colors.onAccent) else blankDayStyle()
        },
        statusDescription = { key -> if (key in recorded) "recorded" else "no entry" },
        actionDescription = { "Select date" },
        testTagPrefix = "gallery_calendar_$todayKey",
        cover = { key -> covers[key] },
    )
}

/**
 * Every view identity on one page, each wearing a clickable card of its own theme: icon, name and
 * Primary button. Equal visual weight across these rows is what makes the views feel like one app,
 * and the card's ledge shows how much of the identity a neutral surface picks up.
 */
@Composable
private fun AllViewsSpecimen() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppDestination.entries.forEach { destination ->
            AllViewsRow(viewIdentities(destination).first())
        }
    }
}

@Composable
private fun AllViewsRow(identity: GalleryIdentity) {
    if (identity.isOwned) {
        ViewTheme(identity.colors) { AllViewsCard(identity) }
    } else {
        AllViewsCard(identity)
    }
}

@Composable
private fun AllViewsCard(identity: GalleryIdentity) {
    MomentumCard(onClick = {}, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = identity.icon,
                contentDescription = null,
                tint = identity.colors.accent,
            )
            Text(
                text = identity.name,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.width(96.dp),
            )
            Box(Modifier.weight(1f)) {
                MomentumButton(text = "Primary", onClick = {})
            }
        }
    }
}

/** The Material controls the app uses as they come, with no Momentum wrapper around them. */
@Composable
private fun MaterialControlsSpecimen() {
    var switched by remember { mutableStateOf(true) }
    var chipSelected by remember { mutableStateOf(true) }
    var text by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Switch(checked = switched, onCheckedChange = { switched = it })
            Switch(checked = false, onCheckedChange = {}, enabled = false)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = chipSelected,
                onClick = { chipSelected = !chipSelected },
                label = { Text("Selected") },
            )
            FilterChip(selected = false, onClick = {}, label = { Text("Unselected") })
        }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Label") },
            placeholder = { Text("Placeholder") },
            singleLine = true,
        )
    }
}

/**
 * The whole generated palette, walked from the registries the generator emits. Nothing here is
 * hand-listed, so a hue added to `scripts/generate_palette.py` shows up on the next run.
 */
@Composable
private fun HuePaletteSpecimen() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        MomentumHueRegistry.forEach { (name, hue) ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = name, style = MaterialTheme.typography.titleSmall)
                SwatchGrid(
                    swatches = hue.shades.map { (role, color) -> GalleryColorRole(role, color) },
                    columns = 4,
                )
            }
        }
    }
}

@Composable
private fun RampSpecimen() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        MomentumRampRegistry.forEach { (name, steps) ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = name, style = MaterialTheme.typography.titleSmall)
                // Each step carries the ramp's single ink, since that is what makes it read as one scale.
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    steps.forEachIndexed { index, step ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(step.fill),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "${index + 1}",
                                color = step.ink,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SurfaceSpecimen() = SwatchGrid(
    swatches = MomentumSurfaceRegistry.map { (name, color) -> GalleryColorRole(name, color) },
)

/**
 * Nunito is judged on weight and roundness, not on style names, so every step shows real words and
 * digits. The two display sizes above 36sp are left out: nothing in the app uses them and they
 * would only reflow the page.
 */
@Composable
private fun TypographySpecimen() {
    val typography = MaterialTheme.typography
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(
            "displaySmall" to typography.displaySmall,
            "headlineLarge" to typography.headlineLarge,
            "headlineMedium" to typography.headlineMedium,
            "headlineSmall" to typography.headlineSmall,
            "titleLarge" to typography.titleLarge,
            "titleMedium" to typography.titleMedium,
            "titleSmall" to typography.titleSmall,
            "bodyLarge" to typography.bodyLarge,
            "bodyMedium" to typography.bodyMedium,
            "bodySmall" to typography.bodySmall,
            "labelLarge" to typography.labelLarge,
            "labelMedium" to typography.labelMedium,
            "labelSmall" to typography.labelSmall,
        ).forEach { (name, style) -> TypeSample(name, style) }
    }
}

@Composable
private fun TypeSample(name: String, style: TextStyle) {
    Column {
        Text(text = "Momentum 0123", style = style)
        Text(
            text = "$name · ${style.fontSize.value.toInt()}sp · ${style.fontWeight?.weight}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
