package de.nicolasmahn.momentum.domain.mo

import de.nicolasmahn.momentum.domain.checklist.MoMotif

/**
 * A view's flavor for Mo ([motif]), and the flag that enrolled it with him before one rule decided
 * which views he watches: every view that counts toward the streak
 * ([de.nicolasmahn.momentum.domain.mo.watchedBy]). [enabled] is still stored and read, so a
 * view that has it on, the ones Momentum began with, stays watched outside the streak too. Nothing
 * sets it for a view a person makes, and no screen shows it.
 */
data class TrackerMoPolicy(
    val enabled: Boolean = false,
    val motif: MoMotif? = null,
)
