package de.nicolasmahn.momentum.gallery

import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import de.nicolasmahn.momentum.app.AppDestination
import de.nicolasmahn.momentum.app.destination
import de.nicolasmahn.momentum.app.viewIdentity
import de.nicolasmahn.momentum.core.ui.ViewColors
import de.nicolasmahn.momentum.core.ui.neutralViewColors
import de.nicolasmahn.momentum.feature.checklist.presentation
import de.nicolasmahn.momentum.core.text.resolve
import de.nicolasmahn.momentum.domain.substance.SubstanceKind
import de.nicolasmahn.momentum.domain.substance.SubstanceSettings
import de.nicolasmahn.momentum.feature.substances.label
import de.nicolasmahn.momentum.feature.substances.presentation

internal data class GalleryColorRole(val name: String, val color: Color)

internal data class GalleryIdentity(
    val name: String,
    val icon: ImageVector,
    val colors: ViewColors,
    /** Set only when the view owns no mapping, explaining where these colors really come from. */
    val fallback: String? = null,
)

/** True identities tint their whole section; a fallback must not pretend to. */
internal val GalleryIdentity.isOwned: Boolean get() = fallback == null

internal fun ViewColors.roleSwatches(): List<GalleryColorRole> = listOf(
    GalleryColorRole("accent", accent),
    GalleryColorRole("onAccent", onAccent),
    GalleryColorRole("container", container),
    GalleryColorRole("onContainer", onContainer),
    GalleryColorRole("label", label),
    GalleryColorRole("ledge", ledge),
)

/**
 * The identities a view owns, read from the app's own destination table, so the gallery can never
 * show an identity the app does not wear. Substances has one per catalogue substance on top of the
 * feature identity; every other view has exactly one.
 */
@Composable
internal fun viewIdentities(destination: AppDestination): List<GalleryIdentity> {
    val identity = destination.viewIdentity()
    return when (destination) {
        // Home owns no presentation mapping. It renders with the neutral chrome every control
        // wears outside a view, so that is what the gallery shows, marked as not owned.
        AppDestination.Home -> listOf(
            GalleryIdentity(
                name = "Home",
                icon = Icons.Rounded.Home,
                colors = neutralViewColors,
                fallback = "No presentation mapping. The neutral chrome outside every view.",
            ),
        )

        // A checklist wears its configuration's icon and hue, so the gallery shows the demo ones.
        AppDestination.Checklist, AppDestination.SettingsPage -> listOf(DemoSupplementsChecklist, DemoSecondChecklist).map { checklist ->
            val presentation = checklist.presentation()
            GalleryIdentity(checklist.label, presentation.icon, presentation.colors)
        }

        // A measurement, a questions view and a time view a person made wear their configuration's too.
        AppDestination.Measurement, AppDestination.Questions, AppDestination.Time -> listOf(DemoWaist, DemoMood, DemoSleepQuestions, DemoPiano).filter { it.kind.destination == destination }.map { view ->
            val presentation = view.presentation()
            GalleryIdentity(view.label, presentation.icon, presentation.colors)
        }

        AppDestination.Tracker -> counterTrackerIdentity().let { counter ->
            listOf(GalleryIdentity("Counter", counter.icon, counter.colors))
        }

        AppDestination.Substances -> listOf(
            GalleryIdentity(stringResource(destination.label), identity.icon, identity.colors),
        ) + SubstanceKind.entries.map { kind ->
            val presentation = SubstanceSettings.Legacy.presentation(kind)
            GalleryIdentity(kind.label().resolve(), presentation.icon, presentation.colors)
        }

        else -> listOf(GalleryIdentity(stringResource(destination.label), identity.icon, identity.colors))
    }
}
