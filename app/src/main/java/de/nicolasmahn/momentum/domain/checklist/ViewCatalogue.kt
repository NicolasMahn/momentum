package de.nicolasmahn.momentum.domain.checklist

/*
 * The closed catalogues a configuration chooses from. A stored configuration names an entry by its
 * storage ID, which never changes; what an entry looks like belongs to the presentation layer.
 */

/** Visual symbols available to tracker configurations. */
enum class ViewIcon(val storageId: String) {
    Checklist("checklist"),
    Schedule("schedule"),
    Medication("medication"),
    Toothbrush("toothbrush"),
    Water("water"),
    Shower("shower"),
    Spa("spa"),
    Meditation("meditation"),
    Sleep("sleep"),
    Food("food"),
    Walk("walk"),
    Heart("heart"),
    Mind("mind"),
    Study("study"),
    Music("music"),
    Plant("plant"),
    Pets("pets"),
    Cleaning("cleaning"),
    Journal("journal"),
    Savings("savings"),
    Sun("sun"),
    Star("star"),
    Coffee("coffee"),
    Calendar("calendar"),
    Scale("scale"),
    Ruler("ruler"),
    Pulse("pulse"),
    Mood("mood"),
    Book("book"),
    Timer("timer"),
    Language("language"),
    Art("art"),
    Dumbbell("dumbbell"),
    Bouldering("bouldering"),
    Bicycle("bicycle"),
    Running("running"),
    Hiking("hiking"),
    Bolt("bolt"),
    Swimming("swimming"),
    Ball("ball"),
    Tennis("tennis"),
    Basketball("basketball"),
    Rowing("rowing"),
    Skiing("skiing"),
    MartialArts("martial_arts"),
    Golf("golf"),
    Skateboarding("skateboarding"),
    Kayaking("kayaking"),
    Science("science"),
    Drink("drink"),
    Smoking("smoking"),
    Sweets("sweets"),
    ;

    companion object {
        fun fromStorageId(storageId: String): ViewIcon? = entries.firstOrNull { it.storageId == storageId }
    }
}

/**
 * One hue is enough: the palette generator derives every shade a view needs from it.
 */
enum class ViewHue(val storageId: String, val isDeep: Boolean = false) {
    Orange("orange"),
    Yellow("yellow"),
    Green("green"),
    Teal("teal"),
    Sky("sky"),
    Indigo("indigo"),
    Purple("purple"),
    Pink("pink"),
    Red("red"),
    Rust("rust", isDeep = true),
    Olive("olive", isDeep = true),
    Forest("forest", isDeep = true),
    Pine("pine", isDeep = true),
    Navy("navy", isDeep = true),
    Violet("violet", isDeep = true),
    Plum("plum", isDeep = true),
    Berry("berry", isDeep = true),
    ;

    companion object {
        fun fromStorageId(storageId: String): ViewHue? = entries.firstOrNull { it.storageId == storageId }
    }
}

/**
 * What an away day means for an item of a checklist, or for a view without items (the contract's
 * `whenAway`): still due, not due at all, or a smaller week target. Pills travel, so an item stays
 * due unless it says otherwise.
 */
enum class WhenAway(val storageId: String) {
    Due("due"),
    Excused("excused"),
    LowerTarget("lowerTarget"),
    ;

    companion object {
        fun fromStorageId(storageId: String): WhenAway? = entries.firstOrNull { it.storageId == storageId }
    }
}

/**
 * Which record day a due day asks about: that day or the previous day.
 */
enum class AboutDay(val storageId: String) {
    Today("today"),
    Yesterday("yesterday"),
    ;

    companion object {
        fun fromStorageId(storageId: String): AboutDay? = entries.firstOrNull { it.storageId == storageId }
    }
}

/**
 * A visual motif for Mo. It changes presentation, never a tracker's rules.
 */
enum class MoMotif(val storageId: String) {
    Pills("pills"),
    Teeth("teeth"),
    Food("food"),
    Running("running"),
    Book("reading"),
    Scale("scale"),
    ;

    companion object {
        fun fromStorageId(storageId: String): MoMotif? = entries.firstOrNull { it.storageId == storageId }
    }
}
