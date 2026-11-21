package com.example.workouttracker.persistence

/**
 * A tracker state written out as version 26 stored it, the last version that held Eating's
 * reflections under `eatingReflections` and no configuration for it: an English installation with
 * Weight's default configuration and nothing recorded but [eatingReflections], a JSON array as it
 * was stored. Nothing here comes from the current encoder, so it stays what that version wrote.
 */
internal fun frozenVersion26(eatingReflections: String = "[]"): String =
    """{"schemaVersion":26,"measurementLogs":[],"sessions":[],"activityTypes":[],"activityEntries":[],""" +
        """"readingLogs":[],"substanceLogs":[],"substanceNoneDays":[],""" +
        """"substances":{"tracked":[],"own":[],"stayingAway":[{"ids":[]}],"checkIn":{"remind":false,"at":"sleep-15"},""" +
        """"rewards":null,"whenAway":"due","goal":"logging","exceptionsOnly":true},""" +
        """"eatingReflections":$eatingReflections,"checklists":[],"checklistDays":[],""" +
        """"measurements":[$FrozenWeightOfVersion25]}"""

/**
 * Reflections as version 26 stored them: one with both answers and a note, one with its quality
 * alone, one with its amount alone, one with neither, and one with nothing but a note.
 */
internal const val FrozenReflectionsOfVersion26 =
    """[{"id":"both","dateKey":"2026-09-15","timestampMillis":1757950400000,"quality":"very_good","amount":"a_little_more","note":"Dinner with friends"},""" +
        """{"id":"quality","dateKey":"2026-09-14","timestampMillis":1757860400000,"quality":"poor"},""" +
        """{"id":"amount","dateKey":"2026-09-13","timestampMillis":1757774000000,"amount":"much_less"},""" +
        """{"id":"none","dateKey":"2026-09-12","timestampMillis":1757687600000},""" +
        """{"id":"note","dateKey":"2026-09-11","timestampMillis":0,"note":"Travelling"}]"""
