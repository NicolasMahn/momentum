package de.nicolasmahn.momentum.feature.logactivity.gym

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.core.text.UiText
import de.nicolasmahn.momentum.core.text.joinedBy
import de.nicolasmahn.momentum.core.text.pluralText
import de.nicolasmahn.momentum.core.text.resolve
import de.nicolasmahn.momentum.core.text.uiText
import de.nicolasmahn.momentum.core.ui.MomentumCard
import de.nicolasmahn.momentum.core.ui.MomentumCardDefaults
import de.nicolasmahn.momentum.core.ui.platformAnimationsEnabled
import de.nicolasmahn.momentum.domain.activity.ActivityConfiguration
import de.nicolasmahn.momentum.domain.activity.ListedActivity
import de.nicolasmahn.momentum.domain.activity.routine.RoutineDay
import de.nicolasmahn.momentum.feature.logactivity.ActivityMark
import de.nicolasmahn.momentum.feature.logactivity.lookOf
import java.util.Locale

/**
 * The days of [activity]'s routine still offered, one card each, in the routine's order.
 * [nextDayId] is the day next in turn, which its card marks; the person still picks.
 *
 * [onPlanSelected] also gets how far down the picker the tapped card starts, so the workout can
 * unfold out of it ([unfoldingFrom]).
 */
@Composable
internal fun GymPlanPicker(
    configuration: ActivityConfiguration,
    activity: ListedActivity,
    onPlanSelected: (dayId: String, cardTop: Float) -> Unit,
    modifier: Modifier = Modifier,
    selectedDay: String? = null,
    nextDayId: String? = null,
) {
    val cardTops = remember { mutableMapOf<String, Float>() }
    Column(
        modifier = modifier
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        activity.routine?.retainedDays.orEmpty().forEach { day ->
            GymPlanCard(
                activity = activity,
                day = day,
                configuration = configuration,
                selected = day.id == selectedDay,
                next = day.id == nextDayId,
                onClick = { onPlanSelected(day.id, cardTops[day.id] ?: 0f) },
                modifier = Modifier.onGloballyPositioned { cardTops[day.id] = it.positionInParent().y },
            )
        }
    }
}

private const val UnfoldMillis = 220

/** Ends a touch past full size and settles back, so the unfold lands instead of stopping. */
private val UnfoldEasing = CubicBezierEasing(0.34f, 1.4f, 0.64f, 1f)

/**
 * The content keyed by [key] grows out of a point [fromY] pixels below its own top: it fades in,
 * slides up and widens to full size, with a small overshoot in the same time. Drawn, never laid out, so nothing around it moves; with
 * system animations off it is simply there.
 */
@Composable
internal fun Modifier.unfoldingFrom(key: Any?, fromY: Float, animate: Boolean = true): Modifier {
    val motion = animate && platformAnimationsEnabled()
    val progress = remember(key) { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(key) {
        progress.animateTo(1f, tween(UnfoldMillis, easing = UnfoldEasing))
    }
    return graphicsLayer {
        val shown = progress.value
        val scale = 0.92f + 0.08f * shown
        // Never quite invisible, so the switch shows no empty frame between the card and this.
        alpha = (0.3f + 0.7f * shown).coerceAtMost(1f)
        translationY = (1f - shown) * fromY
        scaleX = scale
        scaleY = scale
        transformOrigin = TransformOrigin(0.5f, 0f)
    }
}

@Composable
private fun GymPlanCard(
    activity: ListedActivity,
    day: RoutineDay,
    configuration: ActivityConfiguration,
    selected: Boolean,
    next: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val look = configuration.lookOf(ActivityMark.RoutineDay(activity.id, day.id))
    val exercises = day.retainedExercises
    val remainingExercises = exercises.size - 3
    val previewText = buildList {
        exercises.take(3).forEach { add(UiText.Verbatim(it.label)) }
        if (remainingExercises > 0) add(uiText(R.string.activity_gym_plan_more, remainingExercises))
    }.joinedBy(" · ").resolve()

    MomentumCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        selected = selected,
        // One day at a time, in the day's own color.
        selectedColors = MomentumCardDefaults.selectedColors(accent = look.containerColor),
        role = Role.RadioButton,
        testTag = "gym_plan_${day.id.lowercase(Locale.ROOT)}",
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = RoundedCornerShape(14.dp),
                color = look.containerColor,
                contentColor = look.contentColor,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    // The badge is the day's place in the routine as a letter, A, B, C and on.
                    Text(
                        text = badgeLetter(activity.routine?.placeOf(day.id) ?: 0),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = day.label,
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    // The day next in turn says so beside its name, in its own color.
                    if (next) {
                        GymInfoPill(
                            text = stringResource(R.string.activity_gym_plan_next),
                            containerColor = look.containerColor,
                            contentColor = look.contentColor,
                            modifier = Modifier.testTag("gym_plan_${day.id.lowercase(Locale.ROOT)}_next"),
                        )
                    }
                }
                Text(
                    text = listOf(
                        pluralText(R.plurals.activity_gym_plan_exercises, exercises.size),
                        pluralText(R.plurals.activity_gym_plan_target_sets, day.totalSets),
                    ).joinedBy(" · ").resolve(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = previewText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A, B, C and on, which is what the gym's days were called before the routine was a person's. */
private fun badgeLetter(place: Int): String = ('A' + place % 26).toString()
