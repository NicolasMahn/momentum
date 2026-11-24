package de.nicolasmahn.momentum.domain

/** A stable namespace owned by one tracker module, independent of its translated name. */
@JvmInline
value class TrackerTypeId(val value: String) {
    init {
        require(TrackerTypePattern.matches(value)) { "A tracker type needs a stable lowercase namespace." }
    }
}

private val TrackerTypePattern = Regex("[a-z][a-z0-9_-]*")

/** The app can route a tracker without knowing the shape of its records or configuration. */
data class TrackerRef(val type: TrackerTypeId, val id: String) {
    init {
        require(id.isNotBlank()) { "A tracker needs an ID within its type." }
    }
}
