package de.nicolasmahn.momentum.feature.mo

import de.nicolasmahn.momentum.core.text.UiText
import de.nicolasmahn.momentum.domain.mo.MoViewKind

/** Keeps frozen multi-kind copy fixtures explicit while production accepts one policy amount. */
internal fun MoContent.tokenEarnedLine(
    label: UiText,
    loggedDays: Int,
    keptDays: Int,
    weekMinutes: Int,
    activeDays: Int,
): UiText = tokenEarnedLine(label, when (key.kind) {
    MoViewKind.Measurement -> loggedDays
    MoViewKind.Questions -> keptDays
    MoViewKind.Time -> weekMinutes
    MoViewKind.Activity -> activeDays
    MoViewKind.Checklist -> 0
})
