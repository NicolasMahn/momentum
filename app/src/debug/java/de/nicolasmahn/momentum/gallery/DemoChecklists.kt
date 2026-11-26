package de.nicolasmahn.momentum.gallery

import de.nicolasmahn.momentum.domain.checklist.ChecklistConfiguration
import de.nicolasmahn.momentum.domain.checklist.ChecklistDay
import de.nicolasmahn.momentum.domain.checklist.ChecklistItem
import de.nicolasmahn.momentum.domain.checklist.ChecklistTarget
import de.nicolasmahn.momentum.domain.checklist.Checkpoint
import de.nicolasmahn.momentum.domain.checklist.Dated
import de.nicolasmahn.momentum.domain.checklist.CheckpointTime
import de.nicolasmahn.momentum.domain.checklist.ViewHue
import de.nicolasmahn.momentum.domain.checklist.ViewIcon
import de.nicolasmahn.momentum.domain.checklist.ticks
import de.nicolasmahn.momentum.domain.checklist.rulesOn
import java.time.LocalDate
import java.time.Month

/**
 * A second checklist for the debug build only: morning medication half an hour after the alarm,
 * one item seasonal, and no rewards or Mo. It proves a checklist that is not Supplements runs
 * beside it and changes nothing there.
 */
internal val DemoSecondChecklist = ChecklistConfiguration(
    id = "chk_demo",
    label = "Medication",
    icon = ViewIcon.Checklist,
    hue = ViewHue.Sky,
    checkpoints = listOf(Checkpoint("morning", CheckpointTime.AfterWake(30), remind = true)),
    items = listOf(
        ChecklistItem("itm_levo", "Levothyroxine"),
        ChecklistItem("itm_iron", "Iron", months = setOf(Month.NOVEMBER, Month.DECEMBER, Month.JANUARY, Month.FEBRUARY)),
    ),
)

/** Most days kept in full, some only partly, a few forgotten. */
internal fun demoSecondChecklistDays(todayKey: String, dayCount: Int): List<ChecklistDay> {
    val clock = DemoDayClock(todayKey)
    val random = kotlin.random.Random(seed = 77)
    return ((dayCount - 1) downTo 1).mapNotNull { daysAgo ->
        val dateKey = clock.dateKey(daysAgo)
        val due = DemoSecondChecklist.rulesOn(LocalDate.parse(dateKey)).ticks
        val ticks = when (random.nextInt(6)) {
            0 -> emptySet()
            1 -> due.take(1).toSet()
            else -> due
        }
        ticks.takeIf { it.isNotEmpty() }?.let { ChecklistDay(DemoSecondChecklist.id, dateKey, clock.millis(daysAgo, 8), it) }
    }
}

/**
 * Care with the between-teeth cleaning due once a week on any day, which the owner asked for: the
 * tick is offered every day, and only one day of the week counts. Written without a start date so
 * the whole demo history reads it that way.
 */
internal val DemoWeeklyCareChecklist = DemoCareChecklist.copy(
    items = DemoCareChecklist.items.map { item ->
        if (item.id == DemoCareAction.CLEAN_BETWEEN_TEETH.storageId) item.copy(from = null, target = ChecklistTarget.PerWeek(times = 1)) else item
    },
)

/**
 * Care with brushing and hair foam so established that only a miss is recorded, written without a
 * start date like the weekly cleaning above: a day nothing was recorded on is kept by assumption,
 * while the cleaning still asks for its tick.
 */
internal val DemoOnlyMissesCareChecklist = DemoCareChecklist.copy(
    items = DemoCareChecklist.items.map { item ->
        if (item.id in setOf(DemoCareAction.TOOTHBRUSHING.storageId, DemoCareAction.HAIR_FOAM.storageId)) item.copy(exceptionsOnly = Dated.always(true)) else item
    },
)
