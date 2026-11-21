package com.example.workouttracker.persistence

/**
 * A tracker state written out as version 27 stored it, the last version in which a measurement
 * held the decimal places of its step and Weight's unit and step were fixed: an English
 * installation with Eating's default configuration and nothing recorded but [measurementLogs],
 * and [measurements] as they were stored, each a JSON text. Nothing here comes from the current
 * encoder, so it stays what that version wrote.
 */
internal fun frozenVersion27(measurements: List<String> = FrozenMeasurementsOfVersion27, measurementLogs: String = "[]"): String =
    """{"schemaVersion":27,"measurementLogs":$measurementLogs,"sessions":[],"activityTypes":[],"activityEntries":[],""" +
        """"readingLogs":[],"substanceLogs":[],"substanceNoneDays":[],""" +
        """"substances":{"tracked":[],"own":[],"stayingAway":[{"ids":[]}],"checkIn":{"remind":false,"at":"sleep-15"},""" +
        """"rewards":null,"whenAway":"due","goal":"logging","exceptionsOnly":true},""" +
        """"checklists":[],"checklistDays":[],"measurements":[${measurements.joinToString(",")}],""" +
        """"questions":[{"view":"questions","id":"eating","label":"Eating","icon":"food","hue":"orange","about":"yesterday",""" +
        """"rewards":{"maxPasses":3,"earn":{"everyKeptDays":7}},"whenAway":"due"}],"questionEntries":[]}"""

/** Weight's default configuration as versions 25 to 27 stored it: kilograms, and one decimal place for its step of 0.1. */
internal const val FrozenWeightOfVersion27 = FrozenWeightOfVersion25

/** A waist in centimetres in steps of 0.1 as version 27 stored it, its reminder off. */
internal const val FrozenWaistOfVersion27 =
    """{"view":"measurement","id":"msr_k2p9","label":"Waist","icon":"ruler","hue":"plum","unit":"cm","decimals":1,""" +
        """"checkpoints":[{"id":"day","at":"09:00","remind":false}]}"""

/** A mood without a unit in whole steps as version 27 stored it, reminding before sleep and in the streak since a day. */
internal const val FrozenMoodOfVersion27 =
    """{"view":"measurement","id":"msr_m7xd","label":"Mood","icon":"mood","hue":"teal","unit":"","decimals":0,""" +
        """"checkpoints":[{"id":"day","at":"sleep-30","remind":true}],""" +
        """"rewards":{"maxPasses":3,"earn":{"everyKeptDays":7},"periods":[{"from":"2026-10-06"}]},"whenAway":"excused"}"""

/** Body fat as a ratio in steps of 0.01 as version 27 stored it. */
internal const val FrozenBodyFatOfVersion27 =
    """{"view":"measurement","id":"msr_b4fq","label":"Body fat","icon":"ruler","hue":"olive","unit":"ratio","decimals":2,""" +
        """"checkpoints":[{"id":"day","at":"wake+15","remind":false}]}"""

/** Weight and a made measurement of each of the three numbers of decimal places a step could have. */
internal val FrozenMeasurementsOfVersion27 = listOf(FrozenWeightOfVersion27, FrozenWaistOfVersion27, FrozenMoodOfVersion27, FrozenBodyFatOfVersion27)
