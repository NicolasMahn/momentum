package de.nicolasmahn.momentum.persistence.json

import de.nicolasmahn.momentum.core.time.parseDateKey
import de.nicolasmahn.momentum.domain.checklist.AboutDay
import de.nicolasmahn.momentum.domain.checklist.ChecklistConfiguration
import de.nicolasmahn.momentum.domain.checklist.ChecklistDay
import de.nicolasmahn.momentum.domain.checklist.ChecklistItem
import de.nicolasmahn.momentum.domain.checklist.ChecklistRewards
import de.nicolasmahn.momentum.domain.checklist.ChecklistTarget
import de.nicolasmahn.momentum.domain.checklist.Checkpoint
import de.nicolasmahn.momentum.domain.checklist.Dated
import de.nicolasmahn.momentum.domain.checklist.RewardPeriod
import de.nicolasmahn.momentum.domain.checklist.ViewHue
import de.nicolasmahn.momentum.domain.checklist.ViewIcon
import de.nicolasmahn.momentum.domain.checklist.WhenAway
import de.nicolasmahn.momentum.domain.checklist.parseCheckpointTime
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import org.json.JSONArray
import org.json.JSONObject

/*
 * Configurations in the shape of `docs/configurable-views.md`. They decide how every day is judged,
 * so they are read strictly: a configuration that cannot be read exactly makes the whole state
 * unreadable instead of being dropped or replaced by a default.
 */

internal fun encodeChecklists(checklists: List<ChecklistConfiguration>) = JSONArray().apply {
    checklists.forEach { checklist ->
        put(
            JSONObject()
                .put("view", ChecklistView)
                .put("id", checklist.id)
                .put("label", checklist.label)
                .put("icon", checklist.icon.storageId)
                .put("hue", checklist.hue.storageId)
                .put("mo", checklist.mo.encode())
                .put("checkpoints", JSONArray().apply { checklist.checkpoints.forEach { put(it.encode()) } })
                .put("target", checklist.target.encode())
                .put("rewards", checklist.rewards?.encode() ?: JSONObject.NULL)
                .put("items", JSONArray().apply { checklist.items.forEach { put(it.encode()) } })
                // About today is the default, left unwritten as the contract's examples leave it.
                .putOptional("about", checklist.about.takeUnless { it == AboutToday }?.encode { it.storageId }),
        )
    }
}

internal fun JSONObject.decodeChecklists(): List<ChecklistConfiguration> =
    array("checklists").objects().map { it.decodeChecklist() }

internal fun encodeChecklistDays(days: List<ChecklistDay>) = JSONArray().apply {
    days.forEach { day ->
        put(
            JSONObject()
                .put("checklistId", day.checklistId)
                .put("dateKey", day.dateKey)
                .put("timestampMillis", day.timestampMillis)
                .put("ticks", JSONArray().apply { day.ticks.sorted().forEach(::put) })
                // Empty misses carry no facts.
                .putOptional("misses", day.misses.takeIf { it.isNotEmpty() }?.let { misses -> JSONArray().apply { misses.sorted().forEach(::put) } }),
        )
    }
}

internal fun JSONObject.decodeChecklistDays(): List<ChecklistDay> =
    array("checklistDays").objects().map { it.decodeChecklistDay() }

private const val ChecklistView = "checklist"

private fun JSONObject.decodeChecklistDay(): ChecklistDay {
    requireFields(required = setOf("checklistId", "dateKey", "timestampMillis", "ticks"), optional = setOf("misses"))
    return ChecklistDay(
        checklistId = text("checklistId"),
        dateKey = text("dateKey"),
        timestampMillis = optLongOrNull("timestampMillis") ?: error("timestampMillis must be a whole number"),
        ticks = marks("ticks"),
        misses = if (has("misses")) marks("misses") else emptySet(),
    )
}

private fun JSONObject.marks(name: String): Set<String> {
    val raw = array(name)
    val entries = (0 until raw.length()).map { raw.textAt(it) }
    require(entries.none(String::isBlank)) { "A mark must name an item" }
    return entries.toUniqueSet()
}

internal fun JSONObject.decodeChecklist(): ChecklistConfiguration {
    requireFields(
        required = setOf("view", "id", "label", "icon", "hue", "mo", "checkpoints", "target", "rewards", "items"),
        optional = setOf("about"),
    )
    require(text("view") == ChecklistView) { "Unsupported view kind" }
    return ChecklistConfiguration(
        id = text("id"),
        label = text("label"),
        icon = decodeIcon(),
        hue = ViewHue.fromStorageId(text("hue")) ?: error("Unknown hue"),
        mo = getJSONObject("mo").decodeMoPolicy(),
        checkpoints = array("checkpoints").objects().map { it.decodeCheckpoint() },
        target = getJSONObject("target").decodeTarget(),
        rewards = if (isNull("rewards")) null else getJSONObject("rewards").decodeRewards(),
        items = array("items").objects().map { it.decodeItem() },
        about = if (has("about")) decodeDated("about") { raw -> AboutDay.fromStorageId(raw as? String ?: error("about must be text")) ?: error("Unknown about") } else AboutToday,
    )
}

private val AboutToday = Dated.always(AboutDay.Today)

internal fun JSONObject.decodeIcon(): ViewIcon = ViewIcon.fromStorageId(text("icon")) ?: error("Unknown icon")

internal fun Checkpoint.encode() = JSONObject()
    .put("id", id)
    .putOptional("label", label)
    .put("at", at.toString())
    .put("remind", remind)
    .putOptional("from", from?.toString())
    .putOptional("until", until?.toString())

internal fun JSONObject.decodeCheckpoint(): Checkpoint {
    requireFields(required = setOf("id", "at", "remind"), optional = setOf("label", "from", "until"))
    return Checkpoint(
        id = text("id"),
        at = parseCheckpointTime(text("at")) ?: error("Invalid checkpoint time"),
        label = if (has("label")) text("label") else null,
        remind = get("remind") as? Boolean ?: error("remind must be a Boolean"),
        from = date("from"),
        until = date("until"),
    )
}

internal fun ChecklistTarget.encode(): JSONObject = when (this) {
    ChecklistTarget.PerDay -> JSONObject().put("per", "day")
    // Stored in week order, whatever order they were chosen in.
    is ChecklistTarget.Weekdays -> JSONObject().put("weekdays", days.encodeWeekdays())
    is ChecklistTarget.PerWeek -> JSONObject().put("per", "week").put("times", times)
}

private fun JSONObject.decodeWhenAway(): Dated<WhenAway> = if (has("whenAway")) {
    decodeDated("whenAway") { raw -> WhenAway.fromStorageId(raw as? String ?: error("whenAway must be text")) ?: error("Unknown whenAway") }
} else Dated.always(WhenAway.Due)

internal fun JSONObject.decodeTarget(): ChecklistTarget = if (has("weekdays")) {
    requireFields(required = setOf("weekdays"))
    ChecklistTarget.Weekdays(array("weekdays").decodeWeekdays())
} else if (has("per") && get("per") == "week") {
    requireFields(required = setOf("per", "times"))
    ChecklistTarget.PerWeek(times = wholeNumber(get("times")))
} else {
    requireFields(required = setOf("per"))
    require(text("per") == "day") { "Unsupported target" }
    ChecklistTarget.PerDay
}

private val WeekdayIds: Map<DayOfWeek, String> = mapOf(
    DayOfWeek.MONDAY to "MON",
    DayOfWeek.TUESDAY to "TUE",
    DayOfWeek.WEDNESDAY to "WED",
    DayOfWeek.THURSDAY to "THU",
    DayOfWeek.FRIDAY to "FRI",
    DayOfWeek.SATURDAY to "SAT",
    DayOfWeek.SUNDAY to "SUN",
)

/** Days of the week as every store writes them, `MON` to `SUN` in the week's order; a weekday target's and Activity's week alike. */
internal fun Collection<DayOfWeek>.encodeWeekdays(): JSONArray = JSONArray().apply { sorted().forEach { put(it.encodeWeekday()) } }

/** The days [encodeWeekdays] wrote, each known and named once. */
internal fun JSONArray.decodeWeekdays(): Set<DayOfWeek> {
    val ids = (0 until length()).map { get(it) as? String ?: error("A weekday must be text") }
    return ids.map { decodeWeekday(it) }.toUniqueSet()
}

/** One day of the week as every store writes it, `MON` to `SUN`. */
internal fun DayOfWeek.encodeWeekday(): String = WeekdayIds.getValue(this)

/** The day [encodeWeekday] wrote, or a refusal for any other text. */
internal fun decodeWeekday(id: String): DayOfWeek = WeekdayIds.entries.firstOrNull { it.value == id }?.key ?: error("Unknown weekday")

internal fun ChecklistRewards.encode() = JSONObject()
    .put("maxPasses", maxPasses.encode { it })
    .put("earn", JSONObject().put("everyKeptDays", everyKeptDays.encode { it }))
    .putOptional("periods", periods?.encode())

/** A bank earning by kept days. [optional] names what a kind's bank holds beside it, which that kind reads itself. */
internal fun JSONObject.decodeRewards(optional: Set<String> = emptySet()): ChecklistRewards {
    requireFields(required = setOf("maxPasses", "earn"), optional = setOf("periods") + optional)
    val earn = getJSONObject("earn").apply { requireFields(required = setOf("everyKeptDays")) }
    return ChecklistRewards(
        maxPasses = decodeDated("maxPasses", ::wholeNumber),
        everyKeptDays = earn.decodeDated("everyKeptDays", ::wholeNumber),
        periods = decodePeriods(),
    )
}

/** The periods a bank takes part in, whichever bank it is: each `{ "from": "2026-10-13", "until": "2026-10-20" }`, open while it has no end. */
internal fun List<RewardPeriod>.encode() = JSONArray().apply {
    forEach { put(JSONObject().put("from", it.from.toString()).putOptional("until", it.until?.toString())) }
}

/** The `periods` of a bank, or null where it holds none and takes part over its whole history. */
internal fun JSONObject.decodePeriods(): List<RewardPeriod>? = if (has("periods")) {
    array("periods").objects().map { period ->
        period.requireFields(required = setOf("from"), optional = setOf("until"))
        RewardPeriod(from = period.date("from") ?: error("A period needs a start"), until = period.date("until"))
    }
} else {
    null
}

/**
 * A setting that never changed is written plainly; one that changed
 * is its revisions, each `{ "value": ..., "from": "2026-10-05" }`, the first without a start.
 */
internal fun <T> Dated<T>.encode(write: (T) -> Any): Any = if (!isDated) {
    write(current)
} else {
    JSONArray().apply { revisions.forEach { put(JSONObject().put("value", write(it.value)).putOptional("from", it.from?.toString())) } }
}

internal fun <T> JSONObject.decodeDated(name: String, read: (Any) -> T): Dated<T> {
    val raw = get(name) as? JSONArray ?: return Dated.always(read(get(name)))
    return Dated(
        raw.objects().map { revision ->
            revision.requireFields(required = setOf("value"), optional = setOf("from"))
            Dated.Revision(read(revision.get("value")), revision.date("from"))
        },
    ).also { require(it.isDated) { "A setting that never changed is written plainly" } }
}

internal fun wholeNumber(raw: Any): Int = raw as? Int ?: error("Expected a whole number")

private fun ChecklistItem.encode() = JSONObject()
    .put("id", id)
    .put("label", label)
    .putOptional("months", months?.let { season -> JSONArray().apply { season.forEach { put(it.value) } } })
    .putOptional("from", from?.toString())
    .putOptional("until", until?.toString())
    .putOptional("checkpoints", checkpoints?.let { JSONArray(it) })
    .putOptional("target", target?.encode())
    .putOptional("awayTimes", awayTimes)
    // Due as ever is the default, left unwritten as the contract's examples leave it.
    .putOptional("whenAway", whenAway.takeUnless { it == DueAsEver }?.encode { it.storageId })
    // False is the default.
    .putOptional("exceptionsOnly", exceptionsOnly.takeUnless { it == NeverExceptionsOnly }?.encode { it })

private val DueAsEver = Dated.always(WhenAway.Due)

private val NeverExceptionsOnly = Dated.always(false)

private fun JSONObject.decodeItem(): ChecklistItem {
    requireFields(required = setOf("id", "label"), optional = setOf("months", "from", "until", "checkpoints", "target", "whenAway", "awayTimes", "exceptionsOnly"))
    return ChecklistItem(
        id = text("id"),
        label = text("label"),
        months = if (has("months")) {
            val numbers = array("months").let { raw -> (0 until raw.length()).map { wholeNumber(raw.get(it)) } }
            numbers.map { require(it in 1..12) { "Invalid month" }; Month.of(it) }.toUniqueSet()
        } else null,
        from = date("from"),
        until = date("until"),
        checkpoints = if (has("checkpoints")) array("checkpoints").let { raw -> (0 until raw.length()).map { raw.textAt(it) } } else null,
        target = if (has("target")) getJSONObject("target").decodeTarget() else null,
        awayTimes = if (has("awayTimes")) wholeNumber(get("awayTimes")) else null,
        whenAway = decodeWhenAway(),
        exceptionsOnly = if (has("exceptionsOnly")) decodeDated("exceptionsOnly") { raw -> raw as? Boolean ?: error("exceptionsOnly must be a Boolean") } else NeverExceptionsOnly,
    )
}

internal fun JSONObject.date(name: String): LocalDate? =
    if (has(name)) parseDateKey(text(name)) ?: error("$name must be a date") else null

/** Kept in stored order; a repeated entry would be lost on the way back, so it is refused. */
private fun <T> List<T>.toUniqueSet(): Set<T> = LinkedHashSet(this).also { require(it.size == size) { "Repeated entry" } }
