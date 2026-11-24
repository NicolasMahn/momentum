package de.nicolasmahn.momentum.feature.tracker

import de.nicolasmahn.momentum.domain.TrackerRef
import de.nicolasmahn.momentum.domain.TrackerTypeId

/** Assembly fails early rather than letting a second module silently shadow a route. */
internal class TrackerModules(modules: List<TrackerModule>) {
    private val byType: Map<TrackerTypeId, TrackerModule>
    val views: List<TrackerView>
    val creations: List<TrackerCreation>
    private val byRef: Map<TrackerRef, TrackerView>

    init {
        require(modules.distinctBy { it.typeId }.size == modules.size) { "Tracker type IDs must be unique." }
        modules.forEach { module ->
            require(module.views.all { it.ref.type == module.typeId }) { "A module owns only its namespace." }
            require(module.creations.all { it.typeId == module.typeId }) { "A setup belongs to its module." }
            require(module.creations.distinctBy { it.variant }.size == module.creations.size) { "A module's setups are told apart by their variant." }
            require(module.chosenPasses.all { it.type == module.typeId }) { "A module reserves only trackers in its namespace." }
            require(module.rewards.all { it.ref.type == module.typeId }) { "A module owns only rewards in its namespace." }
            require(module.observations.all { it.ref.type == module.typeId }) { "A module owns only observations in its namespace." }
            require(module.rewards.distinctBy { it.ref }.size == module.rewards.size) { "Reward targets must be unique within a module." }
            require(module.observations.distinctBy { it.ref }.size == module.observations.size) { "Observation targets must be unique within a module." }
            module.views.forEach { view ->
                require(view.reminders.distinctBy { it.id }.size == view.reminders.size) { "Reminder IDs must be unique within a tracker." }
            }
        }
        byType = modules.associateBy { it.typeId }
        views = modules.flatMap { it.views }
        require(views.distinctBy { it.ref }.size == views.size) { "Tracker IDs must be unique within a type." }
        byRef = views.associateBy { it.ref }
        creations = modules.flatMap { it.creations }
    }

    fun view(ref: TrackerRef): TrackerView? = byRef[ref]
    fun creation(type: TrackerTypeId, variant: String? = null): TrackerCreation? = byType[type]?.creations?.firstOrNull { it.variant == variant }
    fun reminder(ref: TrackerReminderRef): TrackerReminderCommand? = view(ref.view)?.reminders?.firstOrNull { it.id == ref.id }
}
