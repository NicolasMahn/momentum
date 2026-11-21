package com.example.workouttracker.persistence

/**
 * A tracker state written out as version 25 stored it, the last version in which a measurement a
 * person made held its name, its look, its unit and its step and nothing else: an English
 * installation with nothing recorded but [measurementLogs], [weight]'s configuration and the
 * [made] measurements after it, each a JSON text as it was stored. Nothing here comes from the
 * current encoder, so it stays what that version wrote.
 */
internal fun frozenVersion25(measurementLogs: String = "[]", made: List<String> = emptyList(), weight: String = FrozenWeightOfVersion25): String =
    """{"schemaVersion":25,"measurementLogs":$measurementLogs,"sessions":[],"activityTypes":[],"activityEntries":[],""" +
        """"readingLogs":[],"substanceLogs":[],"substanceNoneDays":[],""" +
        """"substances":{"tracked":[],"own":[],"stayingAway":[{"ids":[]}],"checkIn":{"remind":false,"at":"sleep-15"},""" +
        """"rewards":null,"whenAway":"due","goal":"logging","exceptionsOnly":true},""" +
        """"eatingReflections":[],"checklists":[],"checklistDays":[],""" +
        """"measurements":[${(listOf(weight) + made).joinToString(",")}]}"""

/** Weight's default configuration as version 25 stored it. */
internal const val FrozenWeightOfVersion25 =
    """{"view":"measurement","id":"weight","label":"Weight","icon":"scale","hue":"sky","unit":"kg","decimals":1,""" +
        """"rewards":{"maxPasses":7,"earn":{"keptDaysInWeek":6}},"whenAway":"excused"}"""

/** A measurement a person made as version 25 stored it: a waist in centimetres, in steps of 0.1. */
internal const val FrozenWaistOfVersion25 =
    """{"view":"measurement","id":"msr_k2p9","label":"Waist","icon":"ruler","hue":"plum","unit":"cm","decimals":1}"""

/** One without a unit, in whole steps, as version 25 stored it. */
internal const val FrozenMoodOfVersion25 =
    """{"view":"measurement","id":"msr_m7xd","label":"Mood","icon":"mood","hue":"teal","unit":"","decimals":0}"""
