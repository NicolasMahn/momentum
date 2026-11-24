package de.nicolasmahn.momentum.feature.mo

import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.Icons
import de.nicolasmahn.momentum.core.ui.MomentumButtonVariant
import de.nicolasmahn.momentum.core.ui.MomentumButton
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.core.ui.glyph
import de.nicolasmahn.momentum.domain.holiday.DayOff
import de.nicolasmahn.momentum.feature.checklist.presentation
import de.nicolasmahn.momentum.domain.checklist.ChecklistConfiguration
import de.nicolasmahn.momentum.core.locale.currentLocale
import de.nicolasmahn.momentum.core.text.resolve
import de.nicolasmahn.momentum.core.time.HabitDayStart
import de.nicolasmahn.momentum.core.ui.LocalTrackerIdentity
import de.nicolasmahn.momentum.core.ui.LocalViewColors
import de.nicolasmahn.momentum.core.ui.MomentumCard
import de.nicolasmahn.momentum.core.ui.MomentumCardDefaults
import de.nicolasmahn.momentum.core.ui.MomentumProgressBar
import de.nicolasmahn.momentum.core.ui.ReservedMessage
import de.nicolasmahn.momentum.core.ui.ViewIdentity
import de.nicolasmahn.momentum.core.ui.ViewTheme
import de.nicolasmahn.momentum.core.ui.theme.MoCoin
import de.nicolasmahn.momentum.core.ui.theme.MoCoinLedge
import de.nicolasmahn.momentum.core.ui.theme.MoIce
import de.nicolasmahn.momentum.core.ui.theme.MoIceLedge
import de.nicolasmahn.momentum.core.ui.theme.MoSlate
import de.nicolasmahn.momentum.core.ui.theme.MoSlateLedge
import de.nicolasmahn.momentum.domain.mo.MoDayMark
import de.nicolasmahn.momentum.domain.mo.MoMood
import de.nicolasmahn.momentum.domain.TrackerRef
import de.nicolasmahn.momentum.domain.trackerRef
import de.nicolasmahn.momentum.domain.LoggedView
import de.nicolasmahn.momentum.domain.LoggedViewKind
import de.nicolasmahn.momentum.domain.ref
import de.nicolasmahn.momentum.feature.checklist.tagName
import de.nicolasmahn.momentum.domain.mo.MoStreak
import de.nicolasmahn.momentum.domain.mo.MoTokenProgress
import de.nicolasmahn.momentum.domain.mo.MoWeekDay
import java.time.DayOfWeek
import java.time.format.TextStyle

/**
 * What the score on Home means, opened by pressing it. It is a picture with captions, not a text:
 * the streak with the freezes that guard it, the week as seven marks, each task's tokens as the
 * objects they are with how far the next one is, and three rules of a few words.
 *
 * A token for a task still open today can be reserved by pressing it, which tells Mo that task is
 * covered today and he stops asking. It never records anything; whether the token is spent is
 * settled when the day closes, as it would have been anyway.
 */
@Composable
internal fun MoStreakScreen(
    mood: MoMood,
    modifier: Modifier = Modifier,
    banks: List<TrackerBankPresentation> = emptyList(),
    /** Shares the streak as a picture: only its days and level, never anything recorded. */
    onShare: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            StreakAndFreezes(mood.streak)
            ThisWeeksDays(mood)
            if (onShare != null) {
                MomentumButton(
                    text = stringResource(R.string.mo_streak_share),
                    onClick = onShare,
                    modifier = Modifier.fillMaxWidth(),
                    variant = MomentumButtonVariant.Secondary,
                    icon = Icons.Rounded.Share,
                    testTag = "streak_share",
                )
            }
        }
        TokenShelf(mood, banks)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Rule(R.string.mo_rule_done, R.string.mo_rule_done_effect) { drawStreakCoin() }
            Rule(R.string.mo_rule_covered, R.string.mo_rule_covered_effect) { drawAnyToken() }
            Rule(R.string.mo_rule_uncovered, R.string.mo_rule_uncovered_effect) { drawSpentCoin() }
        }
    }
}

/** The streak as its coin and number, and beside it the freezes that keep it through a bad day. */
@Composable
private fun StreakAndFreezes(streak: MoStreak) {
    val freezeNote = if (streak.freezes >= streak.freezeCapacity) {
        stringResource(R.string.mo_full)
    } else {
        pluralStringResource(R.plurals.mo_freeze_next_in, streak.routineDaysUntilFreeze, streak.routineDaysUntilFreeze)
    }
    val spoken = listOf(
        pluralStringResource(R.plurals.mo_streak_days, streak.days, streak.days),
        pluralStringResource(
            R.plurals.mo_freezes_of_with_note,
            streak.freezeCapacity,
            streak.freezes,
            streak.freezeCapacity,
            freezeNote,
        ),
    ).joinToString(". ")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = spoken
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(56.dp)) { drawStreakCoin() }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).clearAndSetSemantics {}) {
            Text(text = "${streak.days}", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
            Text(
                text = pluralStringResource(R.plurals.mo_days_in_a_row, streak.days),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(Modifier.clearAndSetSemantics {}, horizontalAlignment = Alignment.End) {
            TokenSlots(streak.freezeCapacity) { slot -> FreezeToken(Modifier.size(32.dp), empty = slot >= streak.freezes) }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${pluralStringResource(R.plurals.mo_freezes, streak.freezes, streak.freezes)} · $freezeNote",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Whether today will close covered by tokens: a token is set aside for something open today, and
 * every view still open has one or a rest day left, which the close deals it.
 */
internal val MoMood.todayIsSetAside: Boolean
    get() {
        val open = streak.standings.filterValues { it.openToday }.keys
        return missingTrackersToday.isEmpty() && open.any { it in chosenTrackersToday } &&
            open.all { it in chosenTrackersToday || (streak.freeDaysLeftByTracker[it] ?: 0) > 0 }
    }

/** The week as seven days under their initials. Today already shows a token once it is [todayIsSetAside]. */
@Composable
private fun ThisWeeksDays(mood: MoMood) {
    val covered = mood.todayIsSetAside
    val streak = mood.streak.copy(
        week = mood.streak.week.map { if (covered && it == MoDayMark.Open) MoDayMark.Protected else it },
    )
    val locale = currentLocale()
    val days = streak.week.mapIndexed { index, mark ->
        stringResource(R.string.mo_week_day, WEEK[index].getDisplayName(TextStyle.FULL, locale), stringResource(mark.spoken))
    }
    val spoken = stringResource(R.string.mo_this_week, days.joinToString())
    val sickGlyph = rememberVectorPainter(DayOff.Sick.glyph)
    val sickTint = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = spoken },
    ) {
        streak.week.forEachIndexed { index, mark ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Canvas(Modifier.size(32.dp)) {
                    if (mark == MoDayMark.Sick) drawSickMark(sickGlyph, sickTint) else drawDayMark(mark)
                }
                Text(
                    text = WEEK[index].getDisplayName(TextStyle.NARROW, locale),
                    modifier = Modifier.clearAndSetSemantics {},
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (mark == MoDayMark.Open) FontWeight.Black else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The streak's week runs Monday to Sunday, like every calendar in the app. */
private val WEEK = DayOfWeek.entries

@get:StringRes
private val MoDayMark.spoken: Int
    get() = when (this) {
        MoDayMark.Perfect -> R.string.mo_day_perfect
        MoDayMark.Frozen -> R.string.mo_day_frozen
        MoDayMark.Protected -> R.string.mo_day_protected
        MoDayMark.Missed -> R.string.mo_day_missed
        MoDayMark.Open -> R.string.mo_day_open
        MoDayMark.Upcoming -> R.string.mo_day_upcoming
        MoDayMark.NothingDue -> R.string.mo_day_nothing_due
        MoDayMark.Sick -> R.string.mo_day_sick
    }

/** A coin for a complete day, ice or a token for a covered one, a cold coin for a lost one, a ring for what is open or to come. */
private fun DrawScope.drawDayMark(mark: MoDayMark) {
    val radius = size.minDimension * 0.42f
    val centre = Offset(size.width / 2f, size.height / 2f)
    val drop = Offset(0f, radius * 0.18f)
    when (mark) {
        MoDayMark.Perfect -> {
            drawCircle(MoCoinLedge, radius, centre + drop)
            drawCircle(MoCoin, radius, centre - drop / 2f)
        }
        MoDayMark.Frozen -> {
            drawCircle(MoIceLedge, radius, centre + drop)
            drawCircle(MoIce, radius, centre - drop / 2f)
        }
        MoDayMark.Protected -> drawAnyToken()
        MoDayMark.Missed -> {
            drawCircle(MoSlateLedge, radius, centre + drop)
            drawCircle(MoSlate, radius, centre - drop / 2f)
        }
        MoDayMark.Open -> drawCircle(MoCoin, radius * 0.86f, centre, style = Stroke(width = radius * 0.3f))
        MoDayMark.Upcoming -> drawCircle(MoSlateLedge, radius * 0.86f, centre, style = Stroke(width = radius * 0.28f))
        MoDayMark.NothingDue -> drawCircle(MoSlateLedge, radius * 0.22f, centre)
        // Drawn by [drawSickMark], which needs the glyph's painter.
        MoDayMark.Sick -> Unit
    }
}

/** A sick day wears the same mark as in every calendar, where nothing else would be. */
private fun DrawScope.drawSickMark(glyph: VectorPainter, tint: Color, share: Float = 0.8f) {
    val side = size.minDimension * share
    translate(left = (size.width - side) / 2f, top = (size.height - side) / 2f) {
        with(glyph) { draw(Size(side, side), colorFilter = ColorFilter.tint(tint)) }
    }
}

/** Any token at all, in the gold of the streak: what covered a day, whichever habit it was for. */
private fun DrawScope.drawAnyToken() {
    val unit = size.minDimension / 100f
    val centre = Offset(size.width / 2f, size.height / 2f - 4f * unit)
    drawWorldToken(centre, 40f * unit, MoCoin, MoCoinLedge, MoCoinLedge)
}

/**
 * One bank per task, each in its task's colours: how far the next token is on the left, the tokens
 * held on the right. A timer fills a bar with minutes; a weekly goal shows its week as squares under
 * the days, the way the week above does; the others show one square per day the next token takes.
 */
internal data class TrackerBankPresentation(
    val ref: TrackerRef,
    val label: String,
    val testTag: String,
    val inMinutes: Boolean = false,
    val reservation: TrackerBankReservation? = null,
    val restDays: Int? = null,
)

/**
 * What a bank's row can be pressed for: to set its tokens aside, [count] of them, for today or
 * [thisWeek], or, with [rest], to say today is a rest day, which holds no token.
 */
internal data class TrackerBankReservation(
    val selected: Boolean,
    val count: Int,
    val allowed: Boolean,
    val rest: Boolean = false,
    val thisWeek: Boolean = false,
    val onChange: (Boolean) -> Unit,
)

/**
 * What the row of [ref]'s bank can be pressed for, with [command] the way to say it, or null
 * where nothing is chosen and nothing can be. A day a rest day covers needs no token, so its row
 * is pressed to rest instead.
 */
internal fun MoMood.bankReservation(ref: TrackerRef, command: ((Boolean) -> Unit)?): TrackerBankReservation? {
    val reserved = ref in chosenTrackersToday
    val rest = !reserved && canChooseRest(ref)
    if (command == null && !reserved && !rest) return null
    return TrackerBankReservation(
        selected = reserved || ref in restChosenTrackers,
        count = tokensSetAside(ref),
        allowed = command != null && (rest || canSetAside(ref)),
        rest = rest,
        thisWeek = ref in weekJudgedTrackers,
    ) { command?.invoke(it) }
}

@Composable
private fun TokenShelf(mood: MoMood, banks: List<TrackerBankPresentation>) {
    val streak = mood.streak
    val shown = banks.filter { it.ref in streak.standings }
    if (shown.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        shown.forEach { bank ->
            val standing = streak.standings.getValue(bank.ref)
            val reservation = bank.reservation
            val chosen = reservation?.selected == true
            val resting = chosen && reservation?.rest == true
            val reserved = chosen && !resting
            val held = (standing.passes - (reservation?.count ?: 0)).coerceAtLeast(0)
            val full = standing.passes >= standing.passCapacity
            val progress = standing.nextToken
            val format = when {
                bank.inMinutes -> R.plurals.mo_progress_minutes_this_week
                progress?.week?.isNotEmpty() == true -> R.plurals.mo_progress_days_this_week
                else -> R.plurals.mo_progress_days
            }
            val toNext = progress?.let { pluralStringResource(format, it.goal, it.done, it.goal) }
            val note = when {
                reserved -> stringResource(R.string.mo_reserved)
                resting -> stringResource(R.string.mo_resting)
                full -> stringResource(R.string.mo_full)
                else -> null
            }
            val spoken = listOfNotNull(
                pluralStringResource(R.plurals.mo_bank_tokens, held, bank.label, held),
                if (reserved) stringResource(if (reservation?.thisWeek == true) R.string.mo_reserved_count_week else R.string.mo_reserved_count, reservation?.count ?: 0) else note,
                toNext?.let { stringResource(R.string.mo_next_token, it) },
                bank.restDays?.let { pluralStringResource(R.plurals.mo_rest_days_left, it, it) },
            ).joinToString(". ")
            val identity = LocalTrackerIdentity.current(bank.ref)
            ViewTheme(identity.colors) {
                BankRow(
                    token = { modifier, empty -> PassToken(identity, modifier, empty) },
                    held = held,
                    note = note,
                    spoken = spoken,
                    pressable = reservation?.allowed == true,
                    selected = chosen,
                    state = when {
                        reservation?.rest == true -> stringResource(if (resting) R.string.mo_resting_today else R.string.mo_can_rest_today)
                        else -> stringResource(reservationState(chosen, reservation?.thisWeek == true))
                    },
                    onPress = { reservation?.onChange?.invoke(!chosen) },
                    testTag = bank.testTag,
                ) {
                    when {
                        progress == null -> Unit
                        bank.inMinutes -> MinutesToNextToken(progress, toNext.orEmpty())
                        progress.week.isNotEmpty() -> WeekSquares(
                            tracker = bank.ref,
                            week = progress.week.map {
                                when {
                                    it != MoWeekDay.Open -> it
                                    reserved -> MoWeekDay.Covered
                                    resting -> MoWeekDay.Rested
                                    else -> it
                                }
                            },
                            restLeft = bank.restDays ?: 0,
                        )
                        else -> {
                            DaySquares(progress.done, progress.goal)
                            SquareCaption(toNext.orEmpty())
                        }
                    }
                }
            }
        }
        ReservedMessage(
            text = when {
                shown.any { it.reservation?.run { selected && !rest } == true } -> stringResource(R.string.mo_reserved_spent_at, HabitDayStart.toString())
                shown.any { it.reservation?.run { allowed && !rest } == true } -> stringResource(R.string.mo_reserve_hint)
                shown.any { it.reservation?.run { allowed && rest && !selected } == true } -> stringResource(R.string.mo_rest_hint)
                else -> null
            },
            testTag = "streak_token_hint",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A timer's weekly minutes toward the next token, as a bar over [caption]. */
@Composable
private fun MinutesToNextToken(progress: MoTokenProgress, caption: String) {
    MomentumProgressBar(progress = progress.done / progress.goal.toFloat(), description = caption)
    SquareCaption(caption)
}

/**
 * One bank: [progress] toward the next token on the left, the tokens held on the right as a count
 * beside one token, with a word under it when the bank is full or set aside today.
 *
 * Every bank is a tray, so the six line up as one set. Only one whose tokens can be set aside,
 * or whose rest day can be taken today, is raised, because a ledge means it can be pressed; a
 * pressed one stays selected until pressed again. A flat one sits a ledge higher, so the rows keep one rhythm.
 */
@Composable
private fun BankRow(
    token: @Composable (Modifier, Boolean) -> Unit,
    /** The tokens held, or null for a view that keeps no bank. */
    held: Int?,
    note: String?,
    spoken: String,
    pressable: Boolean = false,
    selected: Boolean = false,
    /** What a pressable bank says of itself, as its switch's state. */
    state: String? = null,
    onPress: () -> Unit = {},
    testTag: String? = null,
    progress: @Composable () -> Unit,
) {
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp).clearAndSetSemantics {},
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) { progress() }
            Column(Modifier.padding(start = 16.dp).width(TokenColumnWidth), horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (held != null) {
                        Text(
                            text = "$held×",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = LocalViewColors.current.label,
                        )
                    }
                    token(Modifier.size(SlotSize), held == null || held == 0)
                }
                if (note != null) {
                    Text(text = note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (pressable) {
        MomentumCard(
            onClick = onPress,
            modifier = Modifier.fillMaxWidth().semantics {
                contentDescription = spoken
                state?.let { stateDescription = it }
            },
            selected = selected,
            role = Role.Switch,
            testTag = testTag,
        ) { content() }
    } else {
        MomentumCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = MomentumCardDefaults.LedgeHeight)
                .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
                .semantics(mergeDescendants = true) { contentDescription = spoken },
        ) { content() }
    }
}

/**
 * This week as seven squares over the days' initials, filled where the task was done, holding a
 * token where one covered the day, and outlined today. Activity's rest days are grey: one already taken sits on the day it covered, and the
 * [restLeft] still to come fill the end of the week, where a rest day usually falls.
 */
@Composable
private fun WeekSquares(tracker: TrackerRef, week: List<MoWeekDay>, restLeft: Int) {
    val locale = currentLocale()
    val ahead = week.indices.filter { week[it] == MoWeekDay.Open || week[it] == MoWeekDay.Upcoming }
    val plannedRest = ahead.takeLast(restLeft).toSet()
    Row(Modifier.fillMaxWidth()) {
        week.forEachIndexed { index, day ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                val restHere = index in plannedRest
                DaySquare(if (restHere) MoWeekDay.Rested else day, today = day == MoWeekDay.Open && !restHere, tracker = tracker)
                Text(
                    text = WEEK[index].getDisplayName(TextStyle.NARROW, locale),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** One square per day the next token takes, filled for each day already counted. */
@Composable
private fun DaySquares(done: Int, goal: Int) {
    Row(Modifier.fillMaxWidth()) {
        repeat(goal) { index ->
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                DaySquare(if (index < done) MoWeekDay.Kept else MoWeekDay.Upcoming, size = if (goal > WEEK.size) 14.dp else SquareSize)
            }
        }
    }
}

@Composable
private fun DaySquare(day: MoWeekDay, today: Boolean = false, size: Dp = SquareSize, tracker: TrackerRef? = null) {
    val done = LocalViewColors.current.accent
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val rest = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = RestSquareAlpha)
    val colors = LocalViewColors.current
    val patch = tracker?.let { LocalTrackerIdentity.current(it) }
    val stamp = patch?.let { rememberVectorPainter(it.icon) }
    val sickGlyph = rememberVectorPainter(DayOff.Sick.glyph)
    Canvas(Modifier.size(size)) {
        val corner = CornerRadius(this.size.minDimension * 0.22f)
        drawRoundRect(color = if (day == MoWeekDay.Kept) done else track, cornerRadius = corner)
        // A rest day is greyed out with the rest token's moon, so it reads as rest and not as a gap;
        // a sick day the same way with the mark every calendar gives it.
        if (day == MoWeekDay.Rested) drawPath(crescent(center, this.size.minDimension * 0.3f), rest)
        if (day == MoWeekDay.Sick) drawSickMark(sickGlyph, rest, share = 0.7f)
        // A covered day wears the task's token like a patch over the hole: bigger than the square,
        // drawn past its bounds so the row never moves for it.
        if (day == MoWeekDay.Covered && patch != null) {
            scale(PATCH_SCALE) { drawPassToken(patch.colors, stamp) }
        }
        if (today && day != MoWeekDay.Kept) {
            val width = this.size.minDimension * 0.14f
            drawRoundRect(
                color = done,
                topLeft = Offset(width / 2f, width / 2f),
                size = Size(this.size.width - width, this.size.height - width),
                cornerRadius = corner,
                style = Stroke(width),
            )
        }
    }
}

@Composable
private fun TokenSlots(slots: Int, slot: @Composable (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) { repeat(slots) { slot(it) } }
}

private fun crescent(centre: Offset, radius: Float): Path = Path().apply {
    val moon = Path().apply { addOval(Rect(centre, radius)) }
    val bite = Path().apply { addOval(Rect(centre + Offset(radius * 0.45f, -radius * 0.35f), radius * 0.85f)) }
    op(moon, bite, PathOperation.Difference)
}

@Composable
private fun SquareCaption(text: String) {
    Text(text = text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Rule(@StringRes cause: Int, @StringRes effect: Int, icon: DrawScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(26.dp)) { icon() }
        Spacer(Modifier.width(10.dp))
        Text(text = stringResource(cause), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(text = stringResource(effect), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
    }
}

private val SlotSize: Dp = 28.dp
private val SquareSize: Dp = 22.dp
private val TokenColumnWidth: Dp = 64.dp
private const val RestSquareAlpha = 0.55f
private const val PATCH_SCALE = 1.45f
