package com.example.workouttracker.persistence

/**
 * A tracker state written out as version 28 stored it, the last version that held Reading's logs
 * under `readingLogs` and no configuration for it: an English installation with Weight's and
 * Eating's default configurations and nothing recorded but [readingLogs], a JSON array as it was
 * stored. Nothing here comes from the current encoder, so it stays what that version wrote.
 */
internal fun frozenVersion28(readingLogs: String = "[]"): String =
    """{"schemaVersion":28,"measurementLogs":[],"sessions":[],"activityTypes":[],"activityEntries":[],""" +
        """"readingLogs":$readingLogs,"substanceLogs":[],"substanceNoneDays":[],""" +
        """"substances":{"tracked":[],"own":[],"stayingAway":[{"ids":[]}],"checkIn":{"remind":false,"at":"sleep-15"},""" +
        """"rewards":null,"whenAway":"due","goal":"logging","exceptionsOnly":true},""" +
        """"checklists":[],"checklistDays":[],"measurements":[$FrozenWeightOfVersion28],""" +
        """"questions":[{"view":"questions","id":"eating","label":"Eating","icon":"food","hue":"orange","about":"yesterday",""" +
        """"rewards":{"maxPasses":3,"earn":{"everyKeptDays":7}},"whenAway":"due"}],"questionEntries":[]}"""

/** Weight's default configuration as version 28 stored it: kilograms, in steps of 0.1. */
internal const val FrozenWeightOfVersion28 =
    """{"view":"measurement","id":"weight","label":"Weight","icon":"scale","hue":"sky","unit":"kg","step":"0.1",""" +
        """"rewards":{"maxPasses":7,"earn":{"keptDaysInWeek":6}},"whenAway":"excused"}"""

/**
 * Reading's logs as version 28 could hold them: timed minutes, a whole day, an old one-tap mark
 * without minutes and one minute at the epoch, in the order it wrote them; and what it repaired
 * on every read, three logs for one date, of which the newest wins, a date that is none and
 * minutes a day cannot hold, which are left out.
 */
internal const val FrozenReadingLogsOfVersion28 =
    """[{"dateKey":"2026-10-12","timestampMillis":1760292000000,"durationMinutes":25},""" +
        """{"dateKey":"2026-10-11","timestampMillis":1760205600000,"durationMinutes":1440},""" +
        """{"dateKey":"2026-10-10","timestampMillis":1760119200000},""" +
        """{"dateKey":"2026-10-09","timestampMillis":100,"durationMinutes":10},""" +
        """{"dateKey":"2026-10-09","timestampMillis":200,"durationMinutes":40},""" +
        """{"dateKey":"2026-10-09","timestampMillis":150},""" +
        """{"dateKey":"2026-02-30","timestampMillis":5,"durationMinutes":5},""" +
        """{"dateKey":"2026-10-08","timestampMillis":7,"durationMinutes":0},""" +
        """{"dateKey":"2026-10-07","timestampMillis":0,"durationMinutes":1}]"""
