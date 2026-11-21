package com.example.workouttracker.app

internal object TrackerStateRefreshSignal {
    private val listeners = mutableSetOf<() -> Unit>()

    @Synchronized
    fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    @Synchronized
    fun removeListener(listener: () -> Unit) {
        listeners -= listener
    }

    fun notifyChanged() {
        val snapshot = synchronized(this) { listeners.toList() }
        snapshot.forEach { it() }
    }
}
