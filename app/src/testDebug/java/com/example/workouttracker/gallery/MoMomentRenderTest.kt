package com.example.workouttracker.gallery

import android.animation.ValueAnimator
import android.content.res.Configuration
import android.graphics.Bitmap
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.workouttracker.R
import com.example.workouttracker.app.HomeScreen
import com.example.workouttracker.core.ui.LocalTrackerIdentity
import com.example.workouttracker.core.ui.theme.WorkoutTrackerTheme
import com.example.workouttracker.domain.mo.MoEvent
import com.example.workouttracker.domain.mo.MoLevel
import com.example.workouttracker.domain.mo.MoLevelArrival
import com.example.workouttracker.domain.mo.MoState
import com.example.workouttracker.domain.mo.MoStreakLoss
import com.example.workouttracker.domain.mo.MoWeekProgress
import com.example.workouttracker.domain.mo.moMood
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import com.example.workouttracker.domain.trackerRef
import com.example.workouttracker.feature.mo.FreezeToken
import com.example.workouttracker.feature.mo.MoAtHisBest
import com.example.workouttracker.feature.mo.MoBackfill
import com.example.workouttracker.feature.mo.MoEventTakeover
import com.example.workouttracker.feature.mo.MoLevelTakeover
import com.example.workouttracker.feature.mo.MoLook
import com.example.workouttracker.feature.mo.MoLossTakeover
import com.example.workouttracker.feature.mo.PassToken
import com.example.workouttracker.feature.mo.RestToken
import com.example.workouttracker.feature.mo.drawSpentCoin
import com.example.workouttracker.feature.mo.drawStreakCoin
import com.example.workouttracker.feature.mo.drawWorldRestToken
import com.example.workouttracker.feature.mo.drawWorldToken
import com.example.workouttracker.feature.mo.renderStreakShareImage
import java.io.File
import java.time.LocalDate
import java.util.Locale
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The viewport is smaller than the test window so system chrome cannot crop the film. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w440dp-h1024dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MoMomentRenderTest {
    companion object {
        @JvmStatic
        @BeforeClass
        fun requested() = assumeTrue(System.getProperty("mo.acts.out") != null)
    }

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun moments() {
        val output = requireNotNull(System.getProperty("mo.acts.out"))
        val tall = System.getProperty("mo.acts.phone") == "tall"
        val width = if (tall) 412 else 360
        val height = if (tall) 892 else 640
        val language = System.getProperty("mo.acts.language", "en")
        val font = System.getProperty("mo.acts.font", "1.0").toFloat()
        val step = System.getProperty("mo.acts.step", "64").toInt()
        val dir = File(output, "${language}-${width}x${height}-${font}")
        val only = System.getProperty("mo.acts.only").orEmpty().split(',')
        var matched = false
        fun wanted(name: String) = only.any { name.startsWith(it) }
        val app = RuntimeEnvironment.getApplication()
        val config = Configuration(app.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
            fontScale = font
            screenWidthDp = width
            screenHeightDp = height
        }
        val context = app.createConfigurationContext(config)
        var screen by mutableStateOf<@Composable () -> Unit>({})
        compose.mainClock.autoAdvance = false
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            CompositionLocalProvider(
                LocalContext provides context, LocalConfiguration provides config, LocalDensity provides Density(context),
                LocalTrackerIdentity provides { ref -> demoTrackerIdentity(ref) },
            ) {
                WorkoutTrackerTheme {
                    Box(Modifier.size(width.dp, height.dp).background(MaterialTheme.colorScheme.background).testTag("frame")) {
                        key(screen) { screen() }
                    }
                }
            }
        }

        fun frame(name: String, file: String) {
            val bounds = compose.onNodeWithTag("frame").fetchSemanticsNode().boundsInRoot
            check(bounds.width == width.toFloat() && bounds.height == height.toFloat()) { "Clipped viewport: $bounds" }
            // PixelCopy waits for a window redraw. Draw the composed view at the held clock instead.
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            compose.runOnIdle {
                val canvas = android.graphics.Canvas(bitmap)
                canvas.translate(-bounds.left, -bounds.top)
                view.draw(canvas)
            }
            bitmap.save(File(dir, "$name/$file.png"))
            bitmap.recycle()
        }
        fun show(content: @Composable () -> Unit) {
            compose.runOnUiThread {
                screen = { content() }
                // Deliver the new scene before the first timestamp, including when motion is off.
                Snapshot.sendApplyNotifications()
            }
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeByFrame()
        }
        fun film(name: String, end: Int, content: @Composable () -> Unit) {
            if (!wanted(name)) return
            matched = true
            if (end > 0) {
                durationScale(1f)
                show(content)
                var time = 0
                while (true) {
                    frame(name, time.toString().padStart(4, '0'))
                    if (time >= end) break
                    val next = minOf(time + step, end)
                    compose.mainClock.advanceTimeBy((next - time).toLong())
                    time = next
                }
            }
            durationScale(0f)
            show(content)
            frame(name, "still")
        }

        val day = LocalDate.of(2026, 11, 18)
        val fixture = demoHistory(day.toString())
        val activity = DemoTask.Activity.view.trackerRef
        val mood = moMood(fixture, FeatureVisibilitySettings(), day).let {
            it.copy(state = MoState.Blazing, streak = it.streak.copy(days = 365, freezes = 1, freezeCapacity = 3),
                trackerWeekProgress = mapOf(activity to MoWeekProgress(3, 3)))
        }
        try {
            MoLevel.entries.forEach { level ->
                film("level_${level.name.lowercase()}", 1824) {
                    MoLevelTakeover(MoLevelArrival(level, day, day.minusDays(level.days - 1L)), level.days, {}, {})
                }
            }
            for (count in listOf(0, 1, 3)) {
                var opened: String? = null
                val backfills = DemoTask.entries.take(count).map { task -> MoBackfill(task.name, day.toString()) { opened = task.name } }
                film("loss_$count", 1632) { MoLossTakeover(MoStreakLoss(23, day), backfills, {}, {}, {}) }
                if (count > 0 && wanted("loss_$count")) {
                    compose.onNodeWithTag("mo_lost_backfill").performClick()
                    if (count > 1) {
                        compose.mainClock.advanceTimeBy(256)
                        val last = backfills.last()
                        compose.onNodeWithText(context.getString(R.string.mo_lost_backfill_view, last.label, last.dateText)).performClick()
                    }
                    check(opened == backfills.last().label) { "Backfill did not open the selected tracker" }
                }
            }
            val events = mapOf(
                "milestone" to MoEvent.StreakMilestone(50), "perfect_week" to MoEvent.PerfectWeek,
                "comeback" to MoEvent.Comeback, "week_goal" to MoEvent.WeekGoalReached(activity),
                "token" to MoEvent.TokenEarned(DemoTask.Weight.view.trackerRef), "freeze" to MoEvent.FreezeEarned,
            )
            events.forEach { (id, event) -> film("event_$id", 1824) { MoEventTakeover(event, mood, {}) } }
            // The scale he stands on and the book on its stand show whether the floor stays where
            // it is while he hops: the second turn of Reading is the act with the stand.
            val saves = listOf(Triple("hop", DemoTask.Reading, 0), Triple("page", DemoTask.Reading, 1), Triple("burst", DemoTask.Activity, 0), Triple("scale", DemoTask.Weight, 0))
            for ((id, task, turn) in saves) {
                film("home_$id", 2112) {
                    HomeScreen(
                        mood = mood, moIsAngry = false, moScene = null, moEvent = null, onMoEventDone = {},
                        celebrateMo = task.view.trackerRef, moSaveTurn = turn, onMoCelebrated = {}, featureVisibility = FeatureVisibilitySettings(),
                        onLogActivity = {}, activity = fixture.activity, onOpenSubstances = {},
                        onOpenStatistics = {}, onOpenSettings = {},
                    )
                }
            }
            film("rewards", 0) { RewardSizes() }
            for (days in listOf(1, 4) + MoLevel.entries.map { it.days }) {
                if (wanted("share_$days")) {
                    matched = true
                    renderStreakShareImage(context, if (days >= 7) MoAtHisBest else MoLook(MoState.Steady), days)
                        .save(File(dir, "share_$days.png"))
                }
            }
            for (state in MoState.entries) {
                val name = "share_${state.name.lowercase()}"
                if (wanted(name)) {
                    matched = true
                    renderStreakShareImage(context, MoLook.of(mood.copy(state = state)), days = 4)
                        .save(File(dir, "$name.png"))
                }
            }
            check(matched) { "No moment matched $only" }
        } finally {
            durationScale(1f)
        }
    }

    private fun durationScale(value: Float) {
        ValueAnimator::class.java.getMethod("setDurationScale", Float::class.javaPrimitiveType).invoke(null, value)
        check(ValueAnimator.areAnimatorsEnabled() == (value > 0f))
    }

    private fun Bitmap.save(file: File) {
        file.parentFile!!.mkdirs()
        file.outputStream().use { check(compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
}

@Composable
private fun RewardSizes() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (size in listOf(18, 26, 40, 72)) {
            val icon = Modifier.size(size.dp)
            Row {
                Canvas(icon) { drawStreakCoin() }
                FreezeToken(icon)
                Canvas(icon) { drawSpentCoin() }
                RestToken(icon)
                PassToken(demoTrackerIdentity(DemoTask.Weight), icon)
            }
        }
        Row {
            DemoTask.entries.forEach { PassToken(demoTrackerIdentity(it), Modifier.size(40.dp)) }
        }
        val colors = demoTrackerIdentity(DemoTask.Activity).colors
        for (size in listOf(24, 72)) {
            Row {
                Canvas(Modifier.size(size.dp)) { drawWorldToken(center, this.size.width * 0.4f, colors.accent, colors.ledge, colors.onAccent) }
                Canvas(Modifier.size(size.dp)) { drawWorldRestToken(center, this.size.width * 0.4f) }
                RestToken(Modifier.size(size.dp), empty = true)
                PassToken(demoTrackerIdentity(DemoTask.Activity), Modifier.size(size.dp), empty = true)
            }
        }
    }
}
