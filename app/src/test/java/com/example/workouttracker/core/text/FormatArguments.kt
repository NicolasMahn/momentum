package com.example.workouttracker.core.text

/**
 * The arguments a format string reads, by index, each with the conversions it reads it as. A bare
 * `%s` reads the next ordinary index and `%<s` the one before it, as `java.util.Formatter` counts,
 * so `%s und %s` and `%1$s und %2$s` read the same two arguments.
 */
internal fun formatArguments(text: String): Map<Int, Set<Char>> {
    val arguments = mutableMapOf<Int, MutableSet<Char>>()
    var ordinary = 0
    var previous = 0
    FormatSpecifier.findAll(text).forEach { match ->
        val (index, conversion) = match.destructured
        val type = conversion.lowercase().first()
        if (type == '%' || type == 'n') return@forEach
        previous = when (index) {
            "" -> ++ordinary
            "<" -> previous
            else -> index.removeSuffix("$").toInt()
        }
        arguments.getOrPut(previous) { mutableSetOf() } += type
    }
    return arguments
}

/**
 * Why [translated] cannot be formatted with the arguments its caller gives [english], or null when
 * it can. It reads no argument English does not, and none as another type. It may leave one out
 * only in a plural's `one` form, and only the quantity, which that form already says is 1: the
 * one integer English reads. Where English reads several, nothing says which is the quantity, so
 * every one stays.
 */
internal fun argumentMismatch(english: String, translated: String, isOneForm: Boolean = false): String? {
    val expected = formatArguments(english)
    val actual = formatArguments(translated)
    actual.forEach { (index, types) ->
        val allowed = expected[index] ?: return "reads argument $index, which English never supplies"
        if (!allowed.containsAll(types)) return "reads argument $index as ${types.sorted()}, English as ${allowed.sorted()}"
    }
    val missing = expected.keys - actual.keys
    val quantity = expected.filterValues { 'd' in it }.keys.singleOrNull()
    return when {
        missing.isEmpty() -> null
        isOneForm && missing == setOf(quantity) -> null
        else -> "leaves out ${missing.sorted().joinToString { "argument $it" }}"
    }
}

/** `%[index$ or <][flags][width][.precision]conversion`; a date conversion is `t` and one more letter. */
private val FormatSpecifier = Regex("""%(\d+\$|<)?[-#+ 0,(]*\d*(?:\.\d+)?([tT]?[a-zA-Z%])""")
