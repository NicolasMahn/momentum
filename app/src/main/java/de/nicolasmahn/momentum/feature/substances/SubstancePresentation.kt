package de.nicolasmahn.momentum.feature.substances

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.Healing
import androidx.compose.material.icons.rounded.Interests
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.LocalDrink
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.SmokingRooms
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.VapingRooms
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.core.text.UiText
import de.nicolasmahn.momentum.core.text.uiText
import de.nicolasmahn.momentum.core.ui.ViewIdentity
import de.nicolasmahn.momentum.core.ui.theme.MomentumHue
import de.nicolasmahn.momentum.core.ui.theme.MomentumHues
import de.nicolasmahn.momentum.core.ui.viewColors
import de.nicolasmahn.momentum.domain.substance.DefaultSubstancesHue
import de.nicolasmahn.momentum.domain.substance.DefaultSubstancesIcon
import de.nicolasmahn.momentum.domain.substance.OwnSubstance
import de.nicolasmahn.momentum.domain.substance.Substance
import de.nicolasmahn.momentum.domain.substance.SubstanceAmountUnit
import de.nicolasmahn.momentum.domain.substance.SubstanceKind
import de.nicolasmahn.momentum.domain.substance.SubstanceSettings
import de.nicolasmahn.momentum.feature.checklist.viewIdentity

/**
 * The one mapping from Substances' stored look to what is shown: Home, the header, the streak
 * view, Settings, Statistics, the check-in and Mo all name and draw the view through these.
 */

/** The app's own word for the view, which it is called by until a person names it. */
internal val SubstancesOwnWord: UiText = uiText(R.string.feature_substances)

/** What the view is called: the name a person gave it, as typed, or the app's own word in its language. */
internal fun SubstanceSettings.viewLabel(): UiText = label?.let(UiText::Verbatim) ?: SubstancesOwnWord

/** The icon and the color the view wears, as stored. */
@Composable
internal fun SubstanceSettings.viewIdentity(): ViewIdentity = viewIdentity(icon, hue)

/** The look Substances wore while it was fixed, for a place with no stored settings at hand, as the gallery's table of destinations. */
@Composable
internal fun substanceFeaturePresentation(): ViewIdentity = viewIdentity(DefaultSubstancesIcon, DefaultSubstancesHue)

/** What a substance is called: the catalogue's word, or the person's own as typed. */
internal fun Substance.label(): UiText = when (this) {
    is SubstanceKind -> uiText(labelRes)
    is OwnSubstance -> UiText.Verbatim(label)
}

@get:StringRes
private val SubstanceKind.labelRes: Int
    get() = when (this) {
        SubstanceKind.ALCOHOL -> R.string.substance_alcohol
        SubstanceKind.TOBACCO -> R.string.substance_tobacco
        SubstanceKind.VAPING -> R.string.substance_vaping
        SubstanceKind.NICOTINE_POUCHES -> R.string.substance_nicotine_pouches
        SubstanceKind.CANNABIS -> R.string.substance_cannabis
        SubstanceKind.COFFEE -> R.string.substance_coffee
        SubstanceKind.ENERGY_DRINKS -> R.string.substance_energy_drinks
        SubstanceKind.COCAINE -> R.string.substance_cocaine
        SubstanceKind.CRACK -> R.string.substance_crack
        SubstanceKind.AMPHETAMINES -> R.string.substance_amphetamines
        SubstanceKind.MDMA -> R.string.substance_mdma
        SubstanceKind.KETAMINE -> R.string.substance_ketamine
        SubstanceKind.PSYCHEDELICS -> R.string.substance_psychedelics
        SubstanceKind.OPIOIDS -> R.string.substance_opioids
        SubstanceKind.BENZODIAZEPINES -> R.string.substance_benzodiazepines
        SubstanceKind.GHB -> R.string.substance_ghb
    }

/** The amount field's label, such as "Grams (optional)". */
@get:StringRes
internal val SubstanceAmountUnit.amountLabelRes: Int
    get() = when (this) {
        SubstanceAmountUnit.GRAMS -> R.string.substance_amount_grams
        SubstanceAmountUnit.DRINKS -> R.string.substance_amount_drinks
        SubstanceAmountUnit.USES -> R.string.substance_amount_uses
        SubstanceAmountUnit.POUCHES -> R.string.substance_amount_pouches
        SubstanceAmountUnit.CUPS -> R.string.substance_amount_cups
        SubstanceAmountUnit.CANS -> R.string.substance_amount_cans
        SubstanceAmountUnit.PILLS -> R.string.substance_amount_pills
    }

/** The suffix after the number in the amount field, such as "g". */
@get:StringRes
internal val SubstanceAmountUnit.suffixRes: Int
    get() = when (this) {
        SubstanceAmountUnit.GRAMS -> R.string.substance_unit_grams
        SubstanceAmountUnit.DRINKS -> R.string.substance_unit_drinks
        SubstanceAmountUnit.USES -> R.string.substance_unit_uses
        SubstanceAmountUnit.POUCHES -> R.string.substance_unit_pouches
        SubstanceAmountUnit.CUPS -> R.string.substance_unit_cups
        SubstanceAmountUnit.CANS -> R.string.substance_unit_cans
        SubstanceAmountUnit.PILLS -> R.string.substance_unit_pills
    }

/** The unit on its own, such as "Grams", where one is chosen for an own substance. */
@get:StringRes
internal val SubstanceAmountUnit.nameRes: Int
    get() = when (this) {
        SubstanceAmountUnit.GRAMS -> R.string.substance_unit_name_grams
        SubstanceAmountUnit.DRINKS -> R.string.substance_unit_name_drinks
        SubstanceAmountUnit.USES -> R.string.substance_unit_name_uses
        SubstanceAmountUnit.POUCHES -> R.string.substance_unit_name_pouches
        SubstanceAmountUnit.CUPS -> R.string.substance_unit_name_cups
        SubstanceAmountUnit.CANS -> R.string.substance_unit_name_cans
        SubstanceAmountUnit.PILLS -> R.string.substance_unit_name_pills
    }

/** An own substance has no picture of its own, so it wears one neutral mark whatever it is. */
internal val Substance.icon: ImageVector
    get() = when (this) {
        is SubstanceKind -> icon
        is OwnSubstance -> Icons.Rounded.Interests
    }

private val SubstanceKind.icon: ImageVector
    get() = when (this) {
        SubstanceKind.ALCOHOL -> Icons.Rounded.LocalDrink
        SubstanceKind.TOBACCO -> Icons.Rounded.SmokingRooms
        SubstanceKind.VAPING -> Icons.Rounded.VapingRooms
        SubstanceKind.NICOTINE_POUCHES -> Icons.Rounded.Inventory2
        SubstanceKind.CANNABIS -> Icons.Rounded.Spa
        SubstanceKind.COFFEE -> Icons.Rounded.Coffee
        SubstanceKind.ENERGY_DRINKS -> Icons.Rounded.Bolt
        SubstanceKind.COCAINE -> Icons.Rounded.Grain
        SubstanceKind.CRACK -> Icons.Rounded.Diamond
        SubstanceKind.AMPHETAMINES -> Icons.Rounded.Speed
        SubstanceKind.MDMA -> Icons.Rounded.Favorite
        SubstanceKind.KETAMINE -> Icons.Rounded.Cloud
        SubstanceKind.PSYCHEDELICS -> Icons.Rounded.AutoAwesome
        SubstanceKind.OPIOIDS -> Icons.Rounded.Healing
        SubstanceKind.BENZODIAZEPINES -> Icons.Rounded.Medication
        SubstanceKind.GHB -> Icons.Rounded.Colorize
    }

/**
 * A substance is an item inside the view, not a view of its own, so each takes a hue that is
 * clearly not Pink, the view's color unless a person chose another, and never Red: Red means
 * error or destruction everywhere else. That leaves fifteen hues for sixteen substances. The ones most likely tracked together differ in family,
 * a vivid hue never beside its own deep tone: the nicotine three, coffee and energy drinks,
 * cocaine and crack, opioids and benzodiazepines, and the party cluster around alcohol. The one
 * shared hue falls to the pair least likely to meet, nicotine pouches and crack.
 */
private val SubstanceKind.hue: MomentumHue
    get() = when (this) {
        SubstanceKind.ALCOHOL -> MomentumHues.Yellow
        SubstanceKind.TOBACCO -> MomentumHues.Sky
        SubstanceKind.VAPING -> MomentumHues.Purple
        SubstanceKind.NICOTINE_POUCHES -> MomentumHues.Orange
        SubstanceKind.CANNABIS -> MomentumHues.Green
        SubstanceKind.COFFEE -> MomentumHues.Rust
        SubstanceKind.ENERGY_DRINKS -> MomentumHues.Violet
        SubstanceKind.COCAINE -> MomentumHues.Berry
        SubstanceKind.CRACK -> MomentumHues.Orange
        SubstanceKind.AMPHETAMINES -> MomentumHues.Teal
        SubstanceKind.MDMA -> MomentumHues.Plum
        SubstanceKind.KETAMINE -> MomentumHues.Indigo
        SubstanceKind.PSYCHEDELICS -> MomentumHues.Navy
        SubstanceKind.OPIOIDS -> MomentumHues.Forest
        SubstanceKind.BENZODIAZEPINES -> MomentumHues.Olive
        SubstanceKind.GHB -> MomentumHues.Pine
    }

/**
 * The hue a substance wears here. An own substance takes a hue no tracked catalogue substance
 * uses, where one is left, dealt in a fixed order so it keeps its hue while the choice stands;
 * once every hue is taken they are dealt round again.
 */
internal fun SubstanceSettings.hue(substance: Substance): MomentumHue = when (substance) {
    is SubstanceKind -> substance.hue
    is OwnSubstance -> ownHues().getValue(substance.id)
}

private fun SubstanceSettings.ownHues(): Map<String, MomentumHue> {
    val taken = trackedSubstances.filterIsInstance<SubstanceKind>().mapTo(HashSet()) { it.hue }
    val free = OwnHueOrder.filterNot { it in taken }.toMutableList()
    return own.mapIndexed { index, substance ->
        substance.id to (free.firstOrNull()?.also(free::remove) ?: OwnHueOrder[index % OwnHueOrder.size])
    }.toMap()
}

/** Deep and vivid hues alternate, so two own substances in a row are told apart at a glance. */
private val OwnHueOrder: List<MomentumHue> = listOf(
    MomentumHues.Plum,
    MomentumHues.Teal,
    MomentumHues.Berry,
    MomentumHues.Indigo,
    MomentumHues.Violet,
    MomentumHues.Orange,
    MomentumHues.Pine,
    MomentumHues.Purple,
    MomentumHues.Navy,
    MomentumHues.Yellow,
    MomentumHues.Rust,
    MomentumHues.Green,
    MomentumHues.Olive,
    MomentumHues.Sky,
    MomentumHues.Forest,
)

internal fun SubstanceSettings.presentation(substance: Substance): ViewIdentity = ViewIdentity(
    icon = substance.icon,
    colors = hue(substance).viewColors(),
)
