package de.nicolasmahn.momentum.feature.settings

/**
 * What a Save of a logged view's configuration, or a Create of a new one, came to, said as it is
 * stored: a measurement's, a questions view's and a time view's alike.
 */
internal enum class ViewWrite {
    /** Stored, and it stands: with its alarm set, with the access to set one missing, or with no new alarm asked for. */
    Stored,

    /** Nothing new is stored: the store refused it, or its alarm could not be set and it was taken back. */
    NotStored,

    /** Stored without the alarm it asked for: the alarm could not be set, and the write could not be taken back. */
    StoredWithoutAlarm,

    /**
     * Stored but for the switch that turns its reminder on, which waits for the phone to give
     * what a reminder needs ([de.nicolasmahn.momentum.feature.tracker.TrackerReminderFlow]). Only a page's Save comes to this.
     */
    ReminderWaits,
}
