package com.example.workouttracker.persistence

/**
 * A tracker state written out as version 23 stored it, the last version whose weight records were a
 * date and a number under `weightLogs`: an English installation with Weight's default configuration
 * and nothing recorded but [weightLogs] and [readingLogs], each a JSON array as it was stored.
 * Nothing here comes from the current encoder, so it stays what that version wrote.
 */
internal fun frozenVersion23(weightLogs: String, readingLogs: String = "[]"): String =
    """{"schemaVersion":23,"weightLogs":$weightLogs,"sessions":[],"activityTypes":[],"activityEntries":[],""" +
        """"readingLogs":$readingLogs,"substanceLogs":[],"substanceNoneDays":[],""" +
        """"substances":{"tracked":[],"own":[],"stayingAway":[{"ids":[]}],"checkIn":{"at":"sleep-15","remind":false},""" +
        """"rewards":null,"whenAway":"due","goal":"logging","exceptionsOnly":true},""" +
        """"eatingReflections":[],"checklists":[],"checklistDays":[],""" +
        """"measurements":[{"view":"measurement","id":"weight","label":"Weight","icon":"scale","hue":"sky",""" +
        """"rewards":{"maxPasses":7,"earn":{"keptDaysInWeek":6}},"whenAway":"excused"}]}"""
