package com.example.workouttracker.persistence

import com.example.workouttracker.persistence.json.introducedInFormat

import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.activity.GymActivityId
import com.example.workouttracker.domain.activity.SeededActivityIds
import com.example.workouttracker.domain.activity.SeededActivityRules
import com.example.workouttracker.domain.compatibility.CareChecklistId
import com.example.workouttracker.domain.checklist.AboutDay
import com.example.workouttracker.domain.checklist.ChecklistConfiguration
import com.example.workouttracker.domain.checklist.ChecklistTarget
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.checklist.fromChecklistWideRule
import com.example.workouttracker.domain.measurement.DefaultMeasurementCheckpoint
import com.example.workouttracker.domain.measurement.MadeMeasurementWhenAway
import com.example.workouttracker.domain.measurement.MeasurementBank
import com.example.workouttracker.domain.measurement.MeasurementStep
import com.example.workouttracker.domain.compatibility.WeightMeasurementId
import com.example.workouttracker.domain.compatibility.defaultWeightCheckpoint
import com.example.workouttracker.domain.compatibility.weightMeasurement
import com.example.workouttracker.domain.compatibility.WeightRewards
import com.example.workouttracker.domain.compatibility.WeightWhenAway
import com.example.workouttracker.domain.compatibility.madeMeasurements
import com.example.workouttracker.domain.compatibility.EatingAmountQuestionId
import com.example.workouttracker.domain.compatibility.EatingQualityQuestionId
import com.example.workouttracker.domain.compatibility.EatingQuestionsId
import com.example.workouttracker.domain.compatibility.defaultEatingCheckpoint
import com.example.workouttracker.domain.compatibility.eatingQuestions
import com.example.workouttracker.domain.compatibility.EatingRewards
import com.example.workouttracker.domain.compatibility.EatingWhenAway
import com.example.workouttracker.domain.reminder.ReminderSettings
import com.example.workouttracker.domain.substance.SubstanceCheckIn
import com.example.workouttracker.domain.substance.SubstanceGoal
import com.example.workouttracker.domain.substance.SubstanceSettings
import com.example.workouttracker.domain.compatibility.SupplementsCheckpointId
import com.example.workouttracker.domain.compatibility.SupplementsChecklistId
import com.example.workouttracker.domain.compatibility.ReadingGoal
import com.example.workouttracker.domain.compatibility.ReadingRewards
import com.example.workouttracker.domain.compatibility.ReadingTimeId
import com.example.workouttracker.domain.compatibility.defaultReadingCheckpoint
import com.example.workouttracker.domain.compatibility.readingTime
import com.example.workouttracker.domain.compatibility.ReadingWhenAway
import com.example.workouttracker.persistence.json.AppStateJsonCodec
import com.example.workouttracker.persistence.json.legacySubstance
import com.example.workouttracker.persistence.json.encode
import com.example.workouttracker.persistence.json.objects
import org.json.JSONArray
import org.json.JSONObject

/**
 * [state] in the shape tracker [version] stored it: before version 30 nothing stores Activity's
 * configuration, so its rules, look and plan can only be the seeded ones, its activities are rows
 * of types, one per name, and each entry names its type and holds its name; before version 29 nothing stores a time view,
 * so there is none a person made and Reading can only be the built-in rules, the logs are
 * Reading's alone under `readingLogs`, each with its minutes as `durationMinutes`, and no view
 * wears the book icon or the three beside it; before version 28 a measurement holds the
 * decimal places of its step, so it can only step by 1, 0.1 or 0.01, and Weight's unit and step
 * are fixed, kilograms and 0.1; before version 27 nothing stores a questions
 * view, so there is none a person made and Eating can only be the built-in rules, and the entries
 * are Eating's alone, its reflections under `eatingReflections`; before version 26 a measurement a person made
 * holds its name, its look, its unit and its step and nothing else, so it can only be one outside
 * the streak, still due on holiday, with its reminder off at 09:00; before version 25 the one measurement is
 * Weight, whose unit and step are the code's and stored nowhere; before version 24 a record names no
 * measurement, so there are only Weight's, a date and a number each under `weightLogs`; before
 * version 23 Weight's configuration is in
 * the code, so nothing stores it and it can only be the built-in rules, and no view wears the
 * scale icon; before version 22 no item logs only misses and
 * no day holds a miss, both of which the current version leaves out while unused; before version 21 Substances has no goal and
 * no exceptions-only rule, and its stance is one flat set; before version 20 no day says "none",
 * every tracked substance is one to stay away from, and Substances has no check-in and no place in
 * the streak; before version 19 the three substances are
 * fixed, so nothing says which are tracked, there are none of the person's own and no record names
 * another; before version 18 how many times a week on
 * holiday asks sits in the item's own week target, so only a week item with a target of its own
 * carries a count and no daily or some-days item asks fewer times; before version 17 no checklist
 * is about yesterday; before version 16 a checklist says once, in its
 * target, what an away day means, so its items must all read as that one rule; before version 15
 * no item is due a number of times a week and no item lowers its target while away; before
 * version 14 no checklist wears a borrowed motif; before version 13 no checklist wears a deep hue
 * or a new icon; before version 12 no setting is dated; before version 11 a checklist says nothing
 * about away days; before version 8 there are no configurations, and Supplements' days are
 * `supplementLogs` of taken IDs; before version 10 Care has no configuration, and its days are
 * `careCompletions`, one per tick at its day's time. That is how the encoders of those versions
 * wrote them. Versions 12 and 13 could also mark a checklist `"archived": true`; a test that needs
 * one adds the flag itself.
 */
internal fun legacyTrackerJson(state: AppState, version: Int): JSONObject {
    require(version in 1..29)
    require(state.activity == null || state.activity.isSeededButForItsActivities()) { "Activity's rules, look and routine arrived in version 30" }
    require((state.checklists.map { it.icon } + state.measurements.map { it.icon } + state.questions.map { it.icon } + state.time.map { it.icon }).none { it.introducedInFormat == 30 }) { "The sixteen icons of Activity and its sports arrived in version 30" }
    require(state.weightMeasurement?.let { it.checkpoint == defaultWeightCheckpoint(remind = false) && it.weekRewards?.periods == null } != false) { "Weight's checkpoint and its periods arrived in version 30" }
    require(state.eatingQuestions?.let { it.checkpoint == defaultEatingCheckpoint(remind = false) && it.rewards?.periods == null } != false) { "Eating's checkpoint and its periods arrived in version 30" }
    require(state.readingTime?.let { it.checkpoint == defaultReadingCheckpoint(remind = false) && it.rewards?.periods == null } != false) { "Reading's checkpoint and its periods arrived in version 30" }
    require(state.activity?.rewards?.periods == null) { "Activity's periods arrived in version 30" }
    val json = JSONObject(AppStateJsonCodec.encode(state)).withTypesOfBefore30().withoutBuiltInCheckpointsOfBefore30()
    if (version == 29) return json.put("schemaVersion", 29)
    require(state.time.all { it.id == ReadingTimeId && it.goal == ReadingGoal && it.rewards == ReadingRewards && it.whenAway == ReadingWhenAway }) {
        "Time views, and a changed goal, bank or away rule for Reading, arrived in version 29"
    }
    require((state.checklists.map { it.icon } + state.measurements.map { it.icon } + state.questions.map { it.icon }).none { it.introducedInFormat == 29 }) { "The book icon and the three beside it arrived in version 29" }
    json.withReadingLogsOfBefore29()
    if (version == 28) return json.put("schemaVersion", 28)
    require(state.measurements.all { it.step in StepsOfBefore28 && (it.id != WeightMeasurementId || (it.unit == "kg" && it.step == MeasurementStep("0.1"))) }) {
        "Another step than 1, 0.1 and 0.01, and another unit or step for Weight, arrived in version 28"
    }
    json.withDecimalsOfBefore28()
    if (version == 27) return json.put("schemaVersion", 27)
    require(state.questions.all { it.id == EatingQuestionsId && it.rewards == EatingRewards && it.whenAway == EatingWhenAway }) {
        "Questions views, and a changed bank or away rule for Eating, arrived in version 27"
    }
    json.withReflectionsOfBefore27()
    if (version == 26) return json.put("schemaVersion", 26)
    require(state.madeMeasurements.all { it.rewards == null && it.whenAway == MadeMeasurementWhenAway && it.checkpoint == DefaultMeasurementCheckpoint }) {
        "A made measurement's reminder, bank and away rule arrived in version 26"
    }
    json.withoutCheckpointsOfBefore26()
    if (version == 25) return json.put("schemaVersion", 25)
    json.withWeightAloneOfBefore25()
    if (version == 24) return json.put("schemaVersion", 24)
    json.withWeightLogsOfBefore24()
    if (version == 23) return json.put("schemaVersion", 23)
    require(state.measurements.all { it.rewards == MeasurementBank.Week(WeightRewards) && it.whenAway == WeightWhenAway } && state.checklists.none { it.icon.introducedInFormat == 23 }) {
        "A changed bank or away rule for Weight and the scale icon arrived in version 23"
    }
    json.remove("measurements")
    if (version == 22) return json.put("schemaVersion", 22)
    require(state.checklists.none { checklist -> checklist.items.any { it.exceptionsOnly != Dated.always(false) } } && state.checklistDays.none { it.misses.isNotEmpty() }) {
        "Only logging misses arrived in version 22"
    }
    if (version == 21) return json.put("schemaVersion", 21)
    require(
        state.substances.goal == Dated.always(SubstanceGoal.Logging) &&
            state.substances.exceptionsOnly == Dated.always(true) &&
            !state.substances.stayingAway.isDated,
    ) { "The goal, exceptions only and a dated stance arrived in version 21" }
    json.getJSONObject("substances").apply {
        remove("goal")
        remove("exceptionsOnly")
        put("stayingAway", getJSONArray("stayingAway").getJSONObject(0).getJSONArray("ids"))
    }
    if (version == 20) return json.put("schemaVersion", 20)
    require(
        state.substanceNoneDays.isEmpty() &&
            state.substances.stayingAway.current == state.substances.tracked &&
            state.substances.checkIn == SubstanceCheckIn() &&
            state.substances.rewards == null &&
            state.substances.whenAway == Dated.always(WhenAway.Due),
    ) { "\"None today\", the staying-away choice, the check-in and the streak arrived in version 20" }
    json.remove("substanceNoneDays")
    json.getJSONObject("substances").apply { listOf("stayingAway", "checkIn", "rewards", "whenAway").forEach { remove(it) } }
    if (version == 19) return json.put("schemaVersion", 19)
    require(
        state.substances.own.isEmpty() &&
            state.substances.tracked.all { legacySubstance(it) != null } &&
            state.substanceLogs.all { legacySubstance(it.substanceId) != null },
    ) { "Other substances arrived in version 19" }
    json.remove("substances")
    if (version == 18) return json.put("schemaVersion", 18)
    state.checklists.forEachIndexed { index, checklist ->
        val items = json.getJSONArray("checklists").getJSONObject(index).getJSONArray("items")
        checklist.items.forEachIndexed { itemIndex, item ->
            val stored = items.getJSONObject(itemIndex)
            stored.remove("awayTimes")
            val count = item.awayTimes ?: return@forEachIndexed
            require(item.target is ChecklistTarget.PerWeek) { "A count on an item without a week target of its own arrived in version 18" }
            stored.getJSONObject("target").put("awayTimes", count)
        }
        require(checklist.items.none { item -> (item.target ?: checklist.target) !is ChecklistTarget.PerWeek && item.whenAway.revisions.any { it.value == WhenAway.LowerTarget } }) {
            "Fewer times for a daily or some-days item arrived in version 18"
        }
    }
    if (version == 17) return json.put("schemaVersion", 17)
    require(state.checklists.all { it.about == Dated.always(AboutDay.Today) }) { "A checklist about yesterday arrived in version 17" }
    if (version == 16) return json.put("schemaVersion", 16)
    state.checklists.forEachIndexed { index, checklist ->
        val rule = requireNotNull(checklist.checklistWideAwayRule()) { "Before version 16 every item of a checklist read as one away rule" }
        val stored = json.getJSONArray("checklists").getJSONObject(index)
        stored.getJSONObject("target").put("whenAway", rule.encoded())
        stored.getJSONArray("items").let { items -> (0 until items.length()).forEach { items.getJSONObject(it).remove("whenAway") } }
    }
    if (version == 15) return json.put("schemaVersion", 15)
    require(state.checklists.none { checklist -> (checklist.items.map { it.target } + checklist.target).any { it is ChecklistTarget.PerWeek } }) {
        "Week targets arrived in version 15"
    }
    require(state.checklists.none { checklist -> checklist.items.any { item -> item.whenAway.revisions.any { it.value == WhenAway.LowerTarget } } }) {
        "Lowered targets arrived in version 15"
    }
    if (version == 14) return json.put("schemaVersion", 14)
    require(state.checklists.none { it.motif?.introducedInFormat == 14 }) { "Food, Running, Reading and Scale arrived in version 14" }
    if (version == 13) return json.put("schemaVersion", 13)
    require(state.checklists.none { it.hue.isDeep || it.icon.introducedInFormat == 13 }) { "Deep hues and three icons arrived in version 13" }
    if (version == 12) return json.put("schemaVersion", 12)
    require(state.checklists.none { checklist -> checklist.items.any { it.whenAway.isDated } || checklist.rewards?.maxPasses?.isDated == true || checklist.rewards?.everyKeptDays?.isDated == true }) {
        "Dated settings arrived in version 12"
    }
    if (version == 11) return json.put("schemaVersion", 11)
    // What an away day means arrived in version 11.
    json.getJSONArray("checklists").let { checklists -> (0 until checklists.length()).forEach { checklists.getJSONObject(it).getJSONObject("target").remove("whenAway") } }
    if (version == 10) return json.put("schemaVersion", 10)
    val allDays = json.remove("checklistDays") as JSONArray
    val days = (0 until allDays.length()).map(allDays::getJSONObject)
    fun JSONObject.ticks() = getJSONArray("ticks").let { ticks -> (0 until ticks.length()).map { ticks.getString(it) } }
    json.put("careCompletions", JSONArray().apply {
        days.filter { it.getString("checklistId") == CareChecklistId }.forEach { day ->
            day.ticks().forEach { tick ->
                put(
                    JSONObject()
                        .put("id", "${day.getString("dateKey")}-$tick")
                        .put("action", tick.substringAfter('/'))
                        .put("session", tick.substringBefore('/'))
                        .put("dateKey", day.getString("dateKey"))
                        .put("timestampMillis", day.getLong("timestampMillis")),
                )
            }
        }
    })
    val checklists = json.remove("checklists") as JSONArray
    if (version >= 8) {
        json.put("checklistDays", JSONArray(days.filter { it.getString("checklistId") != CareChecklistId }))
        json.put("checklists", JSONArray((0 until checklists.length()).map(checklists::getJSONObject).filter { it.getString("id") != CareChecklistId }))
        json.put("schemaVersion", version)
        return json
    }
    json.put("supplementLogs", JSONArray().apply {
        days.filter { it.getString("checklistId") == SupplementsChecklistId }.forEach { day ->
            put(
                JSONObject()
                    .put("dateKey", day.getString("dateKey"))
                    .put("timestampMillis", day.getLong("timestampMillis"))
                    .put("takenSupplementIds", JSONArray(day.ticks().map { it.removePrefix("$SupplementsCheckpointId/") }.sorted())),
            )
        }
    })
    val introduced = mapOf("activityTypes" to 2, "activityEntries" to 2, "supplementLogs" to 3, "readingLogs" to 4, "substanceLogs" to 5, "careCompletions" to 6, "eatingReflections" to 7)
    introduced.filterValues { it > version }.keys.forEach(json::remove)
    if (version == 1) json.remove("schemaVersion") else json.put("schemaVersion", version)
    return json
}

/** Whether this configuration is the seeded one but for the activities a person's records added, which types can stand for. */
private fun com.example.workouttracker.domain.activity.ActivityConfiguration.isSeededButForItsActivities(): Boolean =
    copy(activities = SeededActivityRules.activities) == SeededActivityRules && activities.take(SeededActivityRules.activities.size) == SeededActivityRules.activities

/**
 * [this], a payload of the current version, with Activity as every version before 30 stored it:
 * no configuration, a type for each activity that is not a seeded one, archived where it is
 * removed, each entry naming its activity's ID as its type and holding its name, and each
 * session, the gym's, naming no activity.
 */
private fun JSONObject.withTypesOfBefore30(): JSONObject = apply {
    val configuration = remove("activity") as? JSONObject
    val entries = optJSONArray("activityEntries")?.let { stored -> (0 until stored.length()).mapNotNull(stored::optJSONObject) }.orEmpty()
    entries.forEach { entry ->
        entry.put("activityTypeId", entry.remove("activity"))
        entry.put("activityName", entry.remove("name"))
    }
    optJSONArray("sessions")?.let { stored -> (0 until stored.length()).mapNotNull(stored::optJSONObject) }.orEmpty().forEach { session ->
        require(session.remove("activity") == GymActivityId) { "Every session before version 30 was the gym's" }
    }
    val activities = configuration?.getJSONArray("activities")?.let { stored -> (0 until stored.length()).map(stored::getJSONObject) }.orEmpty()
    put(
        "activityTypes",
        JSONArray(
            activities.filterNot { it.getString("id") in SeededActivityIds }.mapIndexed { index, activity ->
                JSONObject()
                    .put("id", activity.getString("id"))
                    .put("name", activity.getString("label"))
                    .put("createdAtMillis", entries.filter { it.get("activityTypeId") == activity.getString("id") }.minOfOrNull { it.getLong("timestampMillis") } ?: index.toLong())
                    .apply { if (activity.optBoolean("removed", false)) put("archivedAtMillis", 0) }
            },
        ),
    )
}

/**
 * [this], a payload of the current version, with Reading's logs as every version before 29 stored
 * them: under `readingLogs`, each with its date, its time and its minutes as `durationMinutes`, in
 * their order, and no time view. A list that is none is left for the older version to refuse.
 */
private fun JSONObject.withReadingLogsOfBefore29(): JSONObject = apply {
    remove("time")
    val logs = (remove("timeLogs") as? JSONArray ?: return@apply).let { stored -> (0 until stored.length()).map(stored::getJSONObject) }
    require(logs.all { it.getString("time") == ReadingTimeId }) { "Logs of another time view arrived in version 29" }
    put(
        "readingLogs",
        JSONArray(
            logs.map { log ->
                JSONObject().put("dateKey", log.get("dateKey")).put("timestampMillis", log.get("timestampMillis")).apply { if (log.has("minutes")) put("durationMinutes", log.get("minutes")) }
            },
        ),
    )
}

/** The steps a measurement could have before version 28, by the decimal places that stood for each. */
private val StepsOfBefore28 = listOf(MeasurementStep("1"), MeasurementStep("0.1"), MeasurementStep("0.01"))

/**
 * [this], a payload of the current version, with each measurement's step as versions 25 to 27
 * stored it: `decimals`, the decimal places of a step of 1, 0.1 or 0.01. A step that is none of
 * the three, a field that is no text, or a list that is none is left for the older version to
 * refuse.
 */
private fun JSONObject.withDecimalsOfBefore28(): JSONObject = apply {
    val measurements = optJSONArray("measurements") ?: return@apply
    (0 until measurements.length()).mapNotNull(measurements::optJSONObject).forEach { measurement ->
        val decimals = StepsOfBefore28.indexOfFirst { it.written == measurement.opt("step") }
        if (decimals >= 0) {
            measurement.remove("step")
            measurement.put("decimals", decimals)
        }
    }
}

/**
 * [this], a payload of the current version, with Eating's entries as every version before 27
 * stored them: its reflections under `eatingReflections`, each with its ID, its date, its time,
 * its two answers as `quality` and `amount` and its note, in their order, and no questions view.
 * A list that is none is left for the older version to refuse.
 */
private fun JSONObject.withReflectionsOfBefore27(): JSONObject = apply {
    remove("questions")
    val entries = (remove("questionEntries") as? JSONArray ?: return@apply).let { stored -> (0 until stored.length()).map(stored::getJSONObject) }
    require(entries.all { it.getString("questions") == EatingQuestionsId }) { "Entries of another questions view arrived in version 27" }
    put(
        "eatingReflections",
        JSONArray(
            entries.map { entry ->
                val answers = entry.getJSONObject("answers")
                JSONObject().put("id", entry.get("id")).put("dateKey", entry.get("dateKey")).put("timestampMillis", entry.get("timestampMillis")).apply {
                    listOf(EatingQualityQuestionId, EatingAmountQuestionId).forEach { question -> if (answers.has(question)) put(question, answers.get(question)) }
                    if (entry.has("note")) put("note", entry.get("note"))
                }
            },
        ),
    )
}

/**
 * [this], a payload of the current version, with its measurement records as every version before
 * 24 stored them: Weight's alone under `weightLogs`, each with its date, its time and the number
 * as `weightKg`, in their order.
 */
private fun JSONObject.withWeightLogsOfBefore24(): JSONObject = apply {
    val records = (remove("measurementLogs") as JSONArray).let { logs -> (0 until logs.length()).map(logs::getJSONObject) }
    require(records.all { it.getString("measurement") == WeightMeasurementId }) { "Records of another measurement arrived in version 24" }
    put(
        "weightLogs",
        JSONArray(records.map { JSONObject().put("dateKey", it.get("dateKey")).put("timestampMillis", it.get("timestampMillis")).put("weightKg", it.get("value")) }),
    )
}

/**
 * [this], a payload of the current version, with its measurements as versions 23 and 24 stored
 * them: Weight's configuration alone, without the unit and the step that were the code's.
 */
private fun JSONObject.withWeightAloneOfBefore25(): JSONObject = apply {
    val measurements = getJSONArray("measurements").let { stored -> (0 until stored.length()).map(stored::getJSONObject) }
    require(measurements.all { it.getString("id") == WeightMeasurementId }) { "Measurements a person made arrived in version 25" }
    measurements.forEach { weight ->
        weight.remove("unit")
        weight.remove("decimals")
    }
}

/**
 * [this], a payload of the current version, with its made measurements as version 25 stored them:
 * without the checkpoint each holds since version 26. Nothing else is touched, so a bank or an away
 * rule one holds, or a list that is none, is still there for version 25 to refuse.
 */
private fun JSONObject.withoutCheckpointsOfBefore26(): JSONObject = apply {
    optJSONArray("measurements")?.let { stored -> (0 until stored.length()).forEach { stored.optJSONObject(it)?.remove("checkpoints") } }
}

/**
 * Versions 29 and older held Weight, Eating and Reading without a checkpoint, which the migration
 * builds from the reminder settings: the checkpoint the migration builds with its switch off is
 * taken off each of them here. Any other checkpoint on them is left for those versions to refuse.
 */
private fun JSONObject.withoutBuiltInCheckpointsOfBefore30(): JSONObject = apply {
    listOf(
        Triple("measurements", WeightMeasurementId, defaultWeightCheckpoint(remind = false)),
        Triple("questions", EatingQuestionsId, defaultEatingCheckpoint(remind = false)),
        Triple("time", ReadingTimeId, defaultReadingCheckpoint(remind = false)),
    ).forEach { (field, id, checkpoint) ->
        val view = optJSONArray(field)?.objects()?.firstOrNull { it.opt("id") == id } ?: return@forEach
        if (view.opt("checkpoints")?.toString() == JSONArray().put(checkpoint.encode()).toString()) view.remove("checkpoints")
    }
}

/**
 * [this] as every version before 30 reads it back: Activity's configuration in the English words
 * the migration gives it, before the repository seeds the app language's.
 */
internal fun AppState.readBefore30(): AppState = copy(activity = activity?.let { SeededActivityRules.copy(activities = it.activities) })

/** [this] as every version before 29 reads it back: without Reading's configuration, which the repository then seeds. */
internal fun AppState.readBefore29(): AppState = readBefore30().copy(time = emptyList())

/** [this] as every version before 27 reads it back: without Eating's configuration either. */
internal fun AppState.readBefore27(): AppState = readBefore29().copy(questions = emptyList())

/** [this] as every version before 23 reads it back: without Weight's configuration either. */
internal fun AppState.readBefore23(): AppState = readBefore27().copy(measurements = emptyList())

/** [this] as every version before 19 reads it back: tracking the three everyone had, and none of its own. */
internal fun AppState.readBefore19(): AppState = readBefore20().copy(substances = SubstanceSettings.Legacy)

/** [this] as version 19 reads it back: every tracked substance stayed away from, no "none", no check-in and no streak. */
internal fun AppState.readBefore20(): AppState = readBefore23().copy(
    substances = SubstanceSettings(tracked = substances.tracked, own = substances.own, stayingAway = Dated.always(substances.tracked)),
    substanceNoneDays = emptyList(),
)

/** [this] as version 20 reads it back: the logging goal, exceptions only, and the stance as it is today, one revision from the beginning. */
internal fun AppState.readBefore21(): AppState = readBefore23().copy(
    substances = substances.copy(
        goal = Dated.always(SubstanceGoal.Logging),
        exceptionsOnly = Dated.always(true),
        stayingAway = Dated.always(substances.stayingAway.current),
    ),
)

/**
 * [this] in the current shape, claimed as [version]: how a test hands a newer field to an older
 * decoder to see it refused, or an older-but-compatible one accepted. Which substances are tracked
 * arrived in version 19 and would be refused first, so below 19 it is left out; what versions 20
 * and 21 added to it is left out below each the same way, the stance going back to its flat set,
 * and Weight's configuration below 23; below 24 the records are Weight's under `weightLogs`,
 * below 25 its configuration holds no unit and no step, below 26 a made measurement holds no
 * checkpoint, below 27 there is no questions view and the entries are Eating's reflections,
 * below 28 a step of 1, 0.1 or 0.01 is its decimal places, and below 29 there is no time view
 * and the logs are Reading's under `readingLogs`.
 */
internal fun AppState.encodedAsVersion(version: Int): JSONObject =
    JSONObject(AppStateJsonCodec.encode(this)).put("schemaVersion", version).apply {
        if (version < 30) withTypesOfBefore30().withoutBuiltInCheckpointsOfBefore30()
        if (version < 29) withReadingLogsOfBefore29()
        if (version < 28) withDecimalsOfBefore28()
        if (version < 27) withReflectionsOfBefore27()
        if (version < 26) withoutCheckpointsOfBefore26()
        if (version < 25) withWeightAloneOfBefore25()
        if (version < 24) withWeightLogsOfBefore24()
        if (version < 23) remove("measurements")
        if (version < 21) {
            optJSONObject("substances")?.apply {
                remove("goal")
                remove("exceptionsOnly")
                put("stayingAway", getJSONArray("stayingAway").getJSONObject(0).getJSONArray("ids"))
            }
        }
        if (version < 20) {
            remove("substanceNoneDays")
            optJSONObject("substances")?.apply { listOf("stayingAway", "checkIn", "rewards", "whenAway").forEach { remove(it) } }
        }
        if (version < 19) remove("substances")
    }

/**
 * [raw], a payload of the current version, claimed as [version] and otherwise as it is, but for
 * the time views and their logs, which are Reading's logs below 29,
 * a step of 1, 0.1 or 0.01, which is its decimal places below 28, the questions views and their
 * entries, below 26 a made measurement's checkpoint, below 25 Weight's unit and step, below 24
 * the records naming their measurement and, below 23, Weight's configuration: an older version
 * would refuse those before the field a test is about.
 */
internal fun claimedAs(raw: String, version: Int): JSONObject =
    JSONObject(raw).put("schemaVersion", version).apply {
        if (version < 30) withTypesOfBefore30().withoutBuiltInCheckpointsOfBefore30()
        if (version < 29) withReadingLogsOfBefore29()
        if (version < 28) withDecimalsOfBefore28()
        if (version < 27) withReflectionsOfBefore27()
        if (version < 26) withoutCheckpointsOfBefore26()
        if (version < 25) withWeightAloneOfBefore25()
        if (version < 24) withWeightLogsOfBefore24()
        if (version < 23) remove("measurements")
    }

/**
 * The one rule a checklist of version 15 or older said for all its items, or null when its items
 * cannot be read as one: a lowered rule reached only the week items, so a week item's rule is the
 * one to try first, and every item must read as what that rule meant for its target.
 */
private fun ChecklistConfiguration.checklistWideAwayRule(): Dated<WhenAway>? {
    val candidate = items.firstOrNull { (it.target ?: target) is ChecklistTarget.PerWeek }?.whenAway
        ?: items.firstOrNull()?.whenAway
        ?: return Dated.always(WhenAway.Due)
    return candidate.takeIf { rule -> items.all { item -> item.whenAway == rule.map { it.fromChecklistWideRule(item.target ?: target) } } }
}

/** As every version since 12 writes a dated setting: plain while it never changed, its revisions once it did. */
private fun Dated<WhenAway>.encoded(): Any = if (!isDated) {
    current.storageId
} else {
    JSONArray().apply {
        revisions.forEach { revision ->
            put(JSONObject().put("value", revision.value.storageId).apply { revision.from?.let { put("from", it.toString()) } })
        }
    }
}

/**
 * [settings] as reminder [version] 2 to 12 stored them, with Weight's, Eating's and Reading's
 * switches ([builtIns]) beside them: the Supplements and Care switches ([checkpoints]) from their
 * historical settings before they moved into checklist checkpoints, and the fixed bedtime only
 * in version 12.
 */
internal fun legacyReminderJson(
    settings: ReminderSettings,
    version: Int,
    builtIns: LegacyBuiltInReminders = LegacyBuiltInReminders(),
    checkpoints: LegacyCheckpointSwitches = LegacyCheckpointSwitches(),
): JSONObject {
    require(version in 2..12)
    require(version == 12 || settings.fixedBedtime == null) { "A fixed bedtime arrived in version 12" }
    fun switch(enabled: Boolean) = JSONObject().put("enabled", enabled)
    val json = JSONObject(ReminderSettingsCodec.encode(settings))
        .put("version", version)
        .put("weight", switch(builtIns.weight))
        .put("reading", switch(builtIns.reading))
        .put("eating", switch(builtIns.eating))
        .put("supplements", switch(checkpoints.supplements))
        .put("care", JSONObject().put("morning", switch(checkpoints.morning)).put("evening", switch(checkpoints.evening)))
    if (version < 12) json.remove("fixedBedtime")
    val introduced = mapOf("supplements" to 3, "reading" to 4, "fallbackAlarm" to 5, "care" to 6, "eating" to 7, "moCheckIn" to 9)
    introduced.filterValues { it > version }.keys.forEach(json::remove)
    if (version >= 10) json.remove("supplements")
    if (version >= 11) json.remove("care")
    return json
}
