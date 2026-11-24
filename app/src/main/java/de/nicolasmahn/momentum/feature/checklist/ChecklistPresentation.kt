package de.nicolasmahn.momentum.feature.checklist

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.WatchLater
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.rounded.DownhillSkiing
import androidx.compose.material.icons.rounded.Kayaking
import androidx.compose.material.icons.rounded.Rowing
import androidx.compose.material.icons.rounded.Skateboarding
import androidx.compose.material.icons.rounded.SportsBasketball
import androidx.compose.material.icons.rounded.SportsGolf
import androidx.compose.material.icons.rounded.SportsMartialArts
import androidx.compose.material.icons.rounded.SportsTennis
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Hiking
import androidx.compose.material.icons.rounded.LocalDrink
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.Mood
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Pool
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Shower
import androidx.compose.material.icons.rounded.SmokingRooms
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import de.nicolasmahn.momentum.core.ui.ViewIdentity
import de.nicolasmahn.momentum.core.ui.theme.MomentumHue
import de.nicolasmahn.momentum.core.ui.theme.MomentumHues
import de.nicolasmahn.momentum.core.ui.theme.MomentumRampStep
import de.nicolasmahn.momentum.core.ui.theme.MomentumRamps
import de.nicolasmahn.momentum.core.ui.viewColors
import de.nicolasmahn.momentum.domain.checklist.ChecklistConfiguration
import de.nicolasmahn.momentum.domain.checklist.ViewHue
import de.nicolasmahn.momentum.domain.checklist.ViewIcon
import de.nicolasmahn.momentum.feature.logactivity.BoulderingIcon

/** A checklist wears the icon and hue its configuration names; nothing else decides its look. */
@Composable
internal fun ChecklistConfiguration.presentation(): ViewIdentity = viewIdentity(icon, hue)

/** The identity a configured view of any kind wears: the icon and the hue its configuration names. */
@Composable
internal fun viewIdentity(icon: ViewIcon, hue: ViewHue): ViewIdentity =
    ViewIdentity(icon = icon.vector, colors = hue.momentumHue.viewColors())

internal val ViewIcon.vector: ImageVector
    get() = when (this) {
        ViewIcon.Medication -> Icons.Rounded.Medication
        ViewIcon.Checklist -> Icons.Rounded.Checklist
        ViewIcon.Schedule -> Icons.Rounded.WatchLater
        ViewIcon.Toothbrush -> ToothbrushIcon
        ViewIcon.Water -> Icons.Rounded.WaterDrop
        ViewIcon.Shower -> Icons.Rounded.Shower
        ViewIcon.Spa -> Icons.Rounded.Spa
        ViewIcon.Meditation -> Icons.Rounded.SelfImprovement
        ViewIcon.Sleep -> Icons.Rounded.Bedtime
        ViewIcon.Food -> Icons.Rounded.Restaurant
        ViewIcon.Walk -> Icons.AutoMirrored.Rounded.DirectionsWalk
        ViewIcon.Heart -> Icons.Rounded.Favorite
        ViewIcon.Mind -> Icons.Rounded.Psychology
        ViewIcon.Study -> Icons.Rounded.School
        ViewIcon.Music -> Icons.Rounded.MusicNote
        ViewIcon.Plant -> Icons.Rounded.LocalFlorist
        ViewIcon.Pets -> Icons.Rounded.Pets
        ViewIcon.Cleaning -> Icons.Rounded.CleaningServices
        ViewIcon.Journal -> Icons.Rounded.EditNote
        ViewIcon.Savings -> Icons.Rounded.Savings
        ViewIcon.Sun -> Icons.Rounded.WbSunny
        ViewIcon.Star -> Icons.Rounded.Star
        ViewIcon.Coffee -> Icons.Rounded.LocalCafe
        ViewIcon.Calendar -> Icons.Rounded.CalendarMonth
        ViewIcon.Scale -> Icons.Rounded.MonitorWeight
        ViewIcon.Ruler -> Icons.Rounded.Straighten
        ViewIcon.Pulse -> Icons.Rounded.MonitorHeart
        ViewIcon.Mood -> Icons.Rounded.Mood
        ViewIcon.Book -> Icons.AutoMirrored.Rounded.MenuBook
        ViewIcon.Timer -> Icons.Rounded.Timer
        ViewIcon.Language -> Icons.Rounded.Translate
        ViewIcon.Art -> Icons.Rounded.Palette
        ViewIcon.Dumbbell -> Icons.Rounded.FitnessCenter
        ViewIcon.Bouldering -> BoulderingIcon
        ViewIcon.Bicycle -> Icons.AutoMirrored.Rounded.DirectionsBike
        ViewIcon.Running -> Icons.AutoMirrored.Rounded.DirectionsRun
        ViewIcon.Hiking -> Icons.Rounded.Hiking
        ViewIcon.Bolt -> Icons.Rounded.Bolt
        ViewIcon.Swimming -> Icons.Rounded.Pool
        ViewIcon.Ball -> Icons.Rounded.SportsSoccer
        ViewIcon.Tennis -> Icons.Rounded.SportsTennis
        ViewIcon.Basketball -> Icons.Rounded.SportsBasketball
        ViewIcon.Rowing -> Icons.Rounded.Rowing
        ViewIcon.Skiing -> Icons.Rounded.DownhillSkiing
        ViewIcon.MartialArts -> Icons.Rounded.SportsMartialArts
        ViewIcon.Golf -> Icons.Rounded.SportsGolf
        ViewIcon.Skateboarding -> Icons.Rounded.Skateboarding
        ViewIcon.Kayaking -> Icons.Rounded.Kayaking
        ViewIcon.Science -> Icons.Rounded.Science
        ViewIcon.Drink -> Icons.Rounded.LocalDrink
        ViewIcon.Smoking -> Icons.Rounded.SmokingRooms
        ViewIcon.Sweets -> Icons.Rounded.Cookie
    }

internal val ViewHue.momentumHue: MomentumHue
    get() = when (this) {
        ViewHue.Orange -> MomentumHues.Orange
        ViewHue.Yellow -> MomentumHues.Yellow
        ViewHue.Green -> MomentumHues.Green
        ViewHue.Teal -> MomentumHues.Teal
        ViewHue.Sky -> MomentumHues.Sky
        ViewHue.Indigo -> MomentumHues.Indigo
        ViewHue.Purple -> MomentumHues.Purple
        ViewHue.Pink -> MomentumHues.Pink
        ViewHue.Red -> MomentumHues.Red
        ViewHue.Rust -> MomentumHues.Rust
        ViewHue.Olive -> MomentumHues.Olive
        ViewHue.Forest -> MomentumHues.Forest
        ViewHue.Pine -> MomentumHues.Pine
        ViewHue.Navy -> MomentumHues.Navy
        ViewHue.Violet -> MomentumHues.Violet
        ViewHue.Plum -> MomentumHues.Plum
        ViewHue.Berry -> MomentumHues.Berry
    }

/**
 * The five steps a view's hue ranks values in, darkest first: the answers of a questions view's
 * first question wear them, and its calendar paints a day with the step of its answer.
 */
internal val ViewHue.ramp: List<MomentumRampStep>
    get() = when (this) {
        ViewHue.Orange -> MomentumRamps.Orange
        ViewHue.Yellow -> MomentumRamps.Yellow
        ViewHue.Green -> MomentumRamps.Green
        ViewHue.Teal -> MomentumRamps.Teal
        ViewHue.Sky -> MomentumRamps.Sky
        ViewHue.Indigo -> MomentumRamps.Indigo
        ViewHue.Purple -> MomentumRamps.Purple
        ViewHue.Pink -> MomentumRamps.Pink
        ViewHue.Red -> MomentumRamps.Red
        ViewHue.Rust -> MomentumRamps.Rust
        ViewHue.Olive -> MomentumRamps.Olive
        ViewHue.Forest -> MomentumRamps.Forest
        ViewHue.Pine -> MomentumRamps.Pine
        ViewHue.Navy -> MomentumRamps.Navy
        ViewHue.Violet -> MomentumRamps.Violet
        ViewHue.Plum -> MomentumRamps.Plum
        ViewHue.Berry -> MomentumRamps.Berry
    }

internal val ChecklistConfiguration.testTagPrefix: String
    get() = "checklist_$id"
