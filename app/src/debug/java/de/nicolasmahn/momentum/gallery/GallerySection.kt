package de.nicolasmahn.momentum.gallery

import androidx.compose.runtime.Composable
import de.nicolasmahn.momentum.app.AppDestination

internal data class GallerySpecimen(
    val name: String,
    val content: @Composable () -> Unit,
)

/**
 * The gallery has one generic section plus one section per app view. The view list is derived from
 * [AppDestination] so a newly added view cannot be forgotten here.
 */
internal sealed interface GallerySection {
    val id: String
    val title: String

    data object Generic : GallerySection {
        override val id: String = "generic"
        override val title: String = "Generic"
    }

    data class View(val destination: AppDestination) : GallerySection {
        override val id: String get() = destination.name
        override val title: String get() = destination.name
    }
}

internal val gallerySections: List<GallerySection> =
    listOf(GallerySection.Generic) + AppDestination.entries.map(GallerySection::View)

internal fun gallerySection(id: String): GallerySection? =
    gallerySections.firstOrNull { it.id == id }

internal fun gallerySpecimens(section: GallerySection): List<GallerySpecimen> = when (section) {
    GallerySection.Generic -> genericSpecimens
    is GallerySection.View -> viewSpecimens(section.destination)
}
