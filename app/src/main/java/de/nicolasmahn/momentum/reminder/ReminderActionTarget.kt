package de.nicolasmahn.momentum.reminder

import de.nicolasmahn.momentum.core.time.isValidDateKey

internal const val ExtraReminderTargetDateKey = "reminder_target_date_key"

internal fun reminderTargetDateKey(storedDateKey: String?): String? =
    storedDateKey?.takeIf { dateKey ->
        isValidDateKey(dateKey)
    }
