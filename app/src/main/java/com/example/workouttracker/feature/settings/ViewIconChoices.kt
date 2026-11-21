package com.example.workouttracker.feature.settings

import com.example.workouttracker.domain.checklist.ViewIcon

/*
 * The order each kind's icon picker offers the catalogue in: the icons that suit the kind first,
 * two rows of them, which is all the picker shows before More, then the rest in catalogue order.
 */

/** A checklist's things to do once a day. */
internal val ChecklistIcons = suitedFirst(
    ViewIcon.Checklist, ViewIcon.Medication, ViewIcon.Toothbrush, ViewIcon.Water,
    ViewIcon.Shower, ViewIcon.Spa, ViewIcon.Plant, ViewIcon.Cleaning,
)

/** A schedule's times of the day. */
internal val ScheduleIcons = suitedFirst(
    ViewIcon.Schedule, ViewIcon.Medication, ViewIcon.Sun, ViewIcon.Sleep,
    ViewIcon.Toothbrush, ViewIcon.Food, ViewIcon.Coffee, ViewIcon.Calendar,
)

/** What a measurement measures. */
internal val MeasurementIcons = suitedFirst(
    ViewIcon.Ruler, ViewIcon.Scale, ViewIcon.Pulse, ViewIcon.Heart,
    ViewIcon.Water, ViewIcon.Sleep, ViewIcon.Mood, ViewIcon.Savings,
)

/** What questions ask about. */
internal val QuestionsIcons = suitedFirst(
    ViewIcon.Journal, ViewIcon.Mood, ViewIcon.Mind, ViewIcon.Heart,
    ViewIcon.Sleep, ViewIcon.Food, ViewIcon.Star, ViewIcon.Sun,
)

/** What a timer times. */
internal val TimeIcons = suitedFirst(
    ViewIcon.Timer, ViewIcon.Book, ViewIcon.Meditation, ViewIcon.Music,
    ViewIcon.Study, ViewIcon.Language, ViewIcon.Art, ViewIcon.Journal,
)

/**
 * Activity's own icon and each activity's, sport first: hardly anyone gives an activity a
 * checklist's icon. Then meditation, then the rest.
 */
internal val ActivityIcons = suitedFirst(
    ViewIcon.Dumbbell, ViewIcon.Bouldering, ViewIcon.Bicycle, ViewIcon.Running, ViewIcon.Walk, ViewIcon.Hiking, ViewIcon.Swimming, ViewIcon.Ball,
    ViewIcon.Tennis, ViewIcon.Basketball, ViewIcon.Rowing, ViewIcon.Skiing, ViewIcon.MartialArts, ViewIcon.Golf, ViewIcon.Skateboarding, ViewIcon.Kayaking,
    ViewIcon.Meditation,
)

/** What a person tracks or stays away from: the view's own flask, then what the commonest single substances look like. */
internal val SubstanceIcons = suitedFirst(
    ViewIcon.Science, ViewIcon.Drink, ViewIcon.Smoking, ViewIcon.Coffee,
    ViewIcon.Sweets, ViewIcon.Medication, ViewIcon.Bolt, ViewIcon.Spa,
)

private fun suitedFirst(vararg suited: ViewIcon): List<ViewIcon> = suited.toList() + (ViewIcon.entries - suited.toSet())
