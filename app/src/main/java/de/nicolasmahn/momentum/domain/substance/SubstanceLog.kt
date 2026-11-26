package de.nicolasmahn.momentum.domain.substance

/**
 * One substance a record can name: an entry of the catalogue, or one the person added. Its [id] is
 * what a record stores and never changes; its [unit] is what an optional amount is counted in.
 */
sealed interface Substance {
    val id: String
    val unit: SubstanceAmountUnit
}

/**
 * The catalogue, in the order the screen and Settings offer it. Cannabis, alcohol and tobacco keep
 * the IDs and units they had while they were the only three, so no record moves.
 */
enum class SubstanceKind(override val id: String, override val unit: SubstanceAmountUnit) : Substance {
    ALCOHOL("alcohol", SubstanceAmountUnit.DRINKS),
    TOBACCO("tobacco", SubstanceAmountUnit.USES),
    VAPING("vaping", SubstanceAmountUnit.USES),
    NICOTINE_POUCHES("nicotine_pouches", SubstanceAmountUnit.POUCHES),
    CANNABIS("cannabis", SubstanceAmountUnit.GRAMS),
    COFFEE("coffee", SubstanceAmountUnit.CUPS),
    ENERGY_DRINKS("energy_drinks", SubstanceAmountUnit.CANS),
    COCAINE("cocaine", SubstanceAmountUnit.GRAMS),
    CRACK("crack", SubstanceAmountUnit.USES),
    AMPHETAMINES("amphetamines", SubstanceAmountUnit.USES),
    MDMA("mdma", SubstanceAmountUnit.USES),
    KETAMINE("ketamine", SubstanceAmountUnit.USES),
    PSYCHEDELICS("psychedelics", SubstanceAmountUnit.USES),
    OPIOIDS("opioids", SubstanceAmountUnit.USES),
    BENZODIAZEPINES("benzodiazepines", SubstanceAmountUnit.PILLS),
    GHB("ghb", SubstanceAmountUnit.USES),
    ;

    companion object {
        fun fromId(id: String): SubstanceKind? = entries.firstOrNull { it.id == id }

        /**
         * What Settings offers to track: what most people mean by substances. The rest stay in the
         * catalogue so their records keep their names, and are offered only where one is already
         * tracked or recorded; anyone else adds such a substance as their own.
         */
        val Offered: List<SubstanceKind> = listOf(ALCOHOL, TOBACCO, VAPING, NICOTINE_POUCHES, CANNABIS, COFFEE)

        /** The three every installation tracked before a person could choose: all tracker state 18 and older knows. */
        val Legacy: List<SubstanceKind> = listOf(CANNABIS, ALCOHOL, TOBACCO)
    }
}

/**
 * A substance the person named. Its [label] is shown as typed, its amount is counted in a unit of
 * the catalogue's, and its [id] (`sub_k2p9`) is never shown.
 */
data class OwnSubstance(
    override val id: String,
    val label: String,
    override val unit: SubstanceAmountUnit,
) : Substance {
    init {
        // An ID is an identity: one with a space in it would be a different, unknown one when read back.
        require(id.isNotBlank() && id.none { it.isWhitespace() }) { "An own substance needs an ID without spaces." }
        require(label.isNotBlank()) { "An own substance needs a name." }
    }
}

enum class SubstanceAmountUnit(val storageId: String) {
    GRAMS("grams"),
    DRINKS("drinks"),
    USES("uses"),
    POUCHES("pouches"),
    CUPS("cups"),
    CANS("cans"),
    PILLS("pills"),
    ;

    companion object {
        fun fromStorageId(storageId: String): SubstanceAmountUnit? =
            entries.firstOrNull { it.storageId == storageId }
    }
}

/** A use of one substance on one day, named by the substance's ID. */
data class SubstanceLog(
    val id: String,
    val substanceId: String,
    val dateKey: String,
    val timestampMillis: Long,
    val amount: Double? = null,
    val amountUnit: SubstanceAmountUnit? = null,
)

/**
 * "None" for a day: the person looked back on [dateKey] once it was over and had nothing. A fact
 * like a use, and the one record a day without uses can hold; a day holds uses or this, never
 * both. It says only that the day was logged, which is the one thing anything outside the
 * Substances screen may read.
 */
data class SubstanceNoneDay(
    val dateKey: String,
    val timestampMillis: Long,
)

/**
 * Whether [dateKey] was logged: a use of any substance, or none. The check-in and the streak read
 * the day through this one answer and never what was logged, so a use and "none" count the same.
 */
fun isSubstancesLoggedFor(logs: List<SubstanceLog>, noneDays: List<SubstanceNoneDay>, dateKey: String): Boolean =
    logs.any { it.dateKey == dateKey } || noneDays.any { it.dateKey == dateKey }

/**
 * Whether [dateKey] holds a use of any substance in [among]: the one more fact a staying-away goal
 * reads, beside whether the day was logged. Which substance and how much are never read, so a day
 * is one gap however many uses it holds.
 */
fun isSubstanceUsedOn(logs: List<SubstanceLog>, dateKey: String, among: Set<String>): Boolean =
    logs.any { it.dateKey == dateKey && it.substanceId in among }
