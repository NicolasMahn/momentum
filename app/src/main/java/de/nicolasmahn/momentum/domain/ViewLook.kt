package de.nicolasmahn.momentum.domain

import de.nicolasmahn.momentum.domain.checklist.ViewHue
import de.nicolasmahn.momentum.domain.checklist.ViewIcon

/**
 * What a view is called and how it looks, under the ID it is stored by: a tracker configuration's stable ID. A settings page sets them on its Look part and
 * wears them in its frame, so one frame and one Save serve every such view, whatever else its
 * configuration holds.
 */
interface ViewLook {
    val id: String
    val label: String
    val icon: ViewIcon
    val hue: ViewHue
}
