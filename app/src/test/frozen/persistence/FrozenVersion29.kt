package com.example.workouttracker.persistence

import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.activity.routine.LoggedSet
import com.example.workouttracker.domain.activity.routine.ExerciseLog
import com.example.workouttracker.domain.activity.routine.WorkoutSession
import com.example.workouttracker.domain.activity.ActivityEntry
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.activity.ListedActivity
import com.example.workouttracker.domain.activity.testActivityConfiguration
import com.example.workouttracker.domain.activity.withActivity
import com.example.workouttracker.domain.checklist.Checkpoint
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.checklist.MoMotif
import com.example.workouttracker.domain.checklist.RewardPeriod
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.measurement.withWeight
import com.example.workouttracker.domain.questions.withEating
import com.example.workouttracker.domain.time.DefaultTokenSteps
import com.example.workouttracker.domain.time.TimeLog
import com.example.workouttracker.domain.time.TimeLogOrder
import com.example.workouttracker.domain.time.TimeRewards
import com.example.workouttracker.domain.time.testMadeTime
import com.example.workouttracker.domain.time.testReadingTime
import com.example.workouttracker.domain.time.tokenStepsOfTokensPerWeek
import com.example.workouttracker.domain.time.withReading
import java.time.LocalDate
import java.time.LocalTime

/**
 * A tracker state written out as the first builds that stored version 29 wrote it, on 2026-10-13,
 * before a time view's bank held its tokens a week and a time view its flavor: taken from that
 * build's encoder and kept here byte for byte, so nothing in it comes from the current one. Those
 * builds showed Mo with his book for Reading alone and let every week earn two tokens at most.
 *
 * It holds an English installation with Weight's and Eating's default configurations, and three
 * time views: Reading, its goal raised to ninety minutes and its bank to four tokens on
 * 2026-10-13; Piano, in the streak since 2026-10-05 and not due on holiday from 2026-10-13, its
 * reminder on twenty minutes after waking; and Guitar, forty-five minutes a week, which never
 * counted and so has no bank. Each has a log, and Reading a mark without minutes. The two logs of
 * 2026-10-13 stand in the order the state was handed them in, the older first, where the app
 * holds the newer first; reading puts them in order, as it does for any payload.
 */
internal const val FrozenVersion29WithoutTokensAWeekOrAFlavor =
    """{"checklists":[],"measurementLogs":[],"sessions":[],"checklistDays":[],"schemaVersion":29,""" +
        """"substances":{"whenAway":"due","checkIn":{"at":"sleep-15","remind":false},"goal":"logging","own":[],"stayingAway":[{"ids":[]}],"tracked":[],"exceptionsOnly":true,"rewards":null},""" +
        """"substanceLogs":[],""" +
        """"questions":[{"view":"questions","whenAway":"due","icon":"food","about":"yesterday","hue":"orange","id":"eating","label":"Eating","rewards":{"maxPasses":3,"earn":{"everyKeptDays":7}}}],""" +
        """"activityTypes":[],"questionEntries":[],"timeLogs":[{"timestampMillis":1760335200000,"minutes":25,"dateKey":"2026-10-13","time":"reading"},""" +
        """{"timestampMillis":1760338800000,"minutes":40,"dateKey":"2026-10-13","time":"tim_k2p9"},""" +
        """{"timestampMillis":1760162400000,"dateKey":"2026-10-11","time":"reading"},""" +
        """{"timestampMillis":1760076000000,"minutes":15,"dateKey":"2026-10-10","time":"tim_m7xd"}],"activityEntries":[],"substanceNoneDays":[],""" +
        """"time":[{"view":"time","whenAway":"due","goal":[{"value":60},""" +
        """{"from":"2026-10-13","value":90}],"icon":"book","hue":"yellow","id":"reading","label":"Reading","rewards":{"maxPasses":[{"value":3},""" +
        """{"from":"2026-10-13","value":4}]}},{"view":"time","whenAway":[{"value":"due"},""" +
        """{"from":"2026-10-13","value":"excused"}],"goal":60,"icon":"music","hue":"navy","checkpoints":[{"at":"wake+20","id":"day","remind":true}],"id":"tim_k2p9","label":"Piano","rewards":{"maxPasses":3,"periods":[{"from":"2026-10-05"}]}},""" +
        """{"view":"time","whenAway":"due","goal":45,"icon":"music","hue":"navy","checkpoints":[{"at":"09:00","id":"day","remind":false}],"id":"tim_m7xd","label":"Guitar"}],""" +
        """"measurements":[{"view":"measurement","unit":"kg","whenAway":"excused","icon":"scale","hue":"sky","step":"0.1","id":"weight","label":"Weight","rewards":{"maxPasses":7,"earn":{"keptDaysInWeek":6}}}]}"""

/**
 * What [FrozenVersion29WithoutTokensAWeekOrAFlavor] must read as: everything it stored, and what
 * it left unsaid as those builds did it, written out here and not left to a default. Reading
 * wears Reading and the two views a person made Plain, and each bank earns by the two steps
 * every bank started with, a token at the goal and two at twice it.
 */
internal fun stateOfFrozenVersion29(): AppState {
    val day = LocalDate.parse("2026-10-13")
    val reading = testReadingTime().copy(
        motif = MoMotif.Book,
        goal = Dated(listOf(Dated.Revision(60), Dated.Revision(90, day))),
        rewards = TimeRewards(maxPasses = Dated(listOf(Dated.Revision(3), Dated.Revision(4, day))), periods = null, tokenSteps = Dated.always(DefaultTokenSteps)),
    )
    val piano = testMadeTime().copy(
        motif = null,
        rewards = TimeRewards(maxPasses = Dated.always(3), periods = listOf(RewardPeriod(day.minusDays(8))), tokenSteps = Dated.always(DefaultTokenSteps)),
        whenAway = Dated(listOf(Dated.Revision(WhenAway.Due), Dated.Revision(WhenAway.Excused, day))),
        checkpoint = Checkpoint("day", CheckpointTime.AfterWake(20), remind = true),
    )
    val guitar = testMadeTime(id = "tim_m7xd", label = "Guitar", goal = 45).copy(motif = null, rewards = null)
    val logs = listOf(
        TimeLog("reading", "2026-10-13", 1_760_335_200_000, 25),
        TimeLog(piano.id, "2026-10-13", 1_760_338_800_000, 40),
        TimeLog("reading", "2026-10-11", 1_760_162_400_000),
        TimeLog(guitar.id, "2026-10-10", 1_760_076_000_000, 15),
    )
    return AppState(timeLogs = logs.sortedWith(TimeLogOrder)).withWeight().withEating().withReading(reading).withActivity().let { it.copy(time = it.time + piano + guitar) }
}

/**
 * A tracker state written out as the builds of 2026-10-14 wrote it, which stored how many tokens
 * a week earns as a number, `tokensPerWeek`, 1 to 5 and dated, before the steps a week earns by
 * could be listed: taken from that build's encoder and kept here byte for byte, so nothing in it
 * comes from the current one. Those builds let a week earn a token for each full goal up to its
 * bank's number.
 *
 * It holds an English installation with Weight's and Eating's default configurations, and four
 * time views: Reading, its goal raised to ninety minutes and its bank to four tokens on
 * 2026-10-13 and its tokens a week to three on 2026-10-14; Piano, in the streak since 2026-10-05
 * earning one a week, not due on holiday from 2026-10-13, its reminder on twenty minutes after
 * waking; Guitar, forty-five minutes a week on Reading's flavor, which never counted and so has
 * no bank; and Harp, thirty minutes a week, which took part for the week of 2026-10-06 earning
 * five a week. Reading has a log and a mark without minutes, Piano and Harp a log each.
 */
internal const val FrozenVersion29WithTokensAWeek =
    """{"checklists":[],"measurementLogs":[],"sessions":[],"checklistDays":[],"schemaVersion":29,""" +
    """"substances":{"whenAway":"due","checkIn":{"at":"sleep-15","remind":false},"goal":"logging","own":[],"stayingAway":[{"ids":[]}],"tracked":[],"exceptionsOnly":true,"rewards":null},""" +
    """"substanceLogs":[],""" +
    """"questions":[{"view":"questions","whenAway":"due","icon":"food","about":"yesterday","hue":"orange","id":"eating","label":"Eating","rewards":{"maxPasses":3,"earn":{"everyKeptDays":7}}}],""" +
    """"activityTypes":[],""" +
    """"questionEntries":[],""" +
    """"timeLogs":[{"timestampMillis":1760425200000,"minutes":20,"dateKey":"2026-10-14","time":"tim_k2p9"},""" +
    """{"timestampMillis":1760421600000,"minutes":35,"dateKey":"2026-10-14","time":"reading"},""" +
    """{"timestampMillis":1760162400000,"dateKey":"2026-10-11","time":"reading"},""" +
    """{"timestampMillis":1760076000000,"minutes":15,"dateKey":"2026-10-10","time":"tim_h4rp"}],""" +
    """"activityEntries":[],"substanceNoneDays":[],""" +
    """"time":[{"view":"time","whenAway":"due","goal":[{"value":60},""" +
    """{"from":"2026-10-13","value":90}],"icon":"book","hue":"yellow","id":"reading","label":"Reading","motif":"reading","rewards":{"maxPasses":[{"value":3},""" +
    """{"from":"2026-10-13","value":4}],"tokensPerWeek":[{"value":2},{"from":"2026-10-14","value":3}]}},""" +
    """{"view":"time","whenAway":[{"value":"due"},""" +
    """{"from":"2026-10-13","value":"excused"}],"goal":60,"icon":"music","hue":"navy","checkpoints":[{"at":"wake+20","id":"day","remind":true}],"id":"tim_k2p9","label":"Piano","motif":"plain","rewards":{"maxPasses":3,"tokensPerWeek":1,"periods":[{"from":"2026-10-05"}]}},""" +
    """{"view":"time","whenAway":"due","goal":45,"icon":"music","hue":"navy","checkpoints":[{"at":"09:00","id":"day","remind":false}],"id":"tim_m7xd","label":"Guitar","motif":"reading"},""" +
    """{"view":"time","whenAway":"due","goal":30,"icon":"music","hue":"navy","checkpoints":[{"at":"21:30","id":"day","remind":false}],"id":"tim_h4rp","label":"Harp","motif":"plain","rewards":{"maxPasses":2,"tokensPerWeek":5,"periods":[{"from":"2026-10-06","until":"2026-10-12"}]}}],""" +
    """"measurements":[{"view":"measurement","unit":"kg","whenAway":"excused","icon":"scale","hue":"sky","step":"0.1","id":"weight","label":"Weight","rewards":{"maxPasses":7,"earn":{"keptDaysInWeek":6}}}]}"""

/**
 * What [FrozenVersion29WithTokensAWeek] must read as: everything it stored, and each number of
 * tokens a week as the steps of each whole goal up to it, revision by revision, which earn
 * exactly what the number earned.
 */
internal fun stateOfFrozenVersion29WithTokensAWeek(): AppState {
    val day = LocalDate.parse("2026-10-13")
    val reading = testReadingTime().copy(
        motif = MoMotif.Book,
        goal = Dated(listOf(Dated.Revision(60), Dated.Revision(90, day))),
        rewards = TimeRewards(
            maxPasses = Dated(listOf(Dated.Revision(3), Dated.Revision(4, day))),
            periods = null,
            tokenSteps = Dated(listOf(Dated.Revision(tokenStepsOfTokensPerWeek(2)), Dated.Revision(tokenStepsOfTokensPerWeek(3), day.plusDays(1)))),
        ),
    )
    val piano = testMadeTime().copy(
        motif = null,
        rewards = TimeRewards(maxPasses = Dated.always(3), periods = listOf(RewardPeriod(day.minusDays(8))), tokenSteps = Dated.always(tokenStepsOfTokensPerWeek(1))),
        whenAway = Dated(listOf(Dated.Revision(WhenAway.Due), Dated.Revision(WhenAway.Excused, day))),
        checkpoint = Checkpoint("day", CheckpointTime.AfterWake(20), remind = true),
    )
    val guitar = testMadeTime(id = "tim_m7xd", label = "Guitar", goal = 45).copy(motif = MoMotif.Book, rewards = null)
    val harp = testMadeTime(id = "tim_h4rp", label = "Harp", goal = 30).copy(
        motif = null,
        rewards = TimeRewards(maxPasses = Dated.always(2), periods = listOf(RewardPeriod(day.minusDays(7), day.minusDays(1))), tokenSteps = Dated.always(tokenStepsOfTokensPerWeek(5))),
        checkpoint = Checkpoint("day", CheckpointTime.Fixed(LocalTime.of(21, 30)), remind = false),
    )
    val logs = listOf(
        TimeLog(piano.id, "2026-10-14", 1_760_425_200_000, 20),
        TimeLog("reading", "2026-10-14", 1_760_421_600_000, 35),
        TimeLog("reading", "2026-10-11", 1_760_162_400_000),
        TimeLog(harp.id, "2026-10-10", 1_760_076_000_000, 15),
    )
    return AppState(timeLogs = logs.sortedWith(TimeLogOrder)).withWeight().withEating().withReading(reading).withActivity().let { it.copy(time = it.time + piano + guitar + harp) }
}

/**
 * A tracker state written out as version 29 stored Activity, before its configuration was stored:
 * types created behind the scenes for each name, and entries naming their type and holding their
 * name; taken from that version's encoder and kept here byte for byte, so nothing in it comes from
 * the current one.
 *
 * It holds an English installation with Weight's, Eating's and Reading's default configurations;
 * entries of the three tiles, Cycling's without the type ID every entry had; two custom names,
 * Yoga in two spellings of case under one type, the earlier entry spelled "yOGA", and "Trail run",
 * which never had a type; a type Pilates created but never used, and a type Swimming archived
 * and never used; and sessions of A, B and C, one with two sets of an exercise and an exercise
 * without a set, one with a duration. The entries stand in the order the state was handed them.
 */
internal const val FrozenVersion29WithActivities =
    """{"schemaVersion":29,"measurementLogs":[],""" +
        """"sessions":[{"id":"2026-10-05-A","day":"A","dateKey":"2026-10-05","timestampMillis":1759680000000,"exerciseLogs":[{"exerciseId":"bench_press","exerciseName":"Bench Press","sets":[{"weightKg":22.5,"reps":10},{"weightKg":25,"reps":8}]},{"exerciseId":"back_squat","exerciseName":"Back Squat","sets":[]}]},""" +
        """{"id":"2026-10-07-B","day":"B","dateKey":"2026-10-07","timestampMillis":1759852800000,"durationMinutes":55,"exerciseLogs":[{"exerciseId":"pull_ups","exerciseName":"Pull Ups","sets":[{"weightKg":-15,"reps":8}]}]},""" +
        """{"id":"2026-10-09-C","day":"C","dateKey":"2026-10-09","timestampMillis":1760025600000,"exerciseLogs":[{"exerciseId":"face_pulls","exerciseName":"Face Pulls","sets":[{"weightKg":17.5,"reps":15.5}]}]}],""" +
        """"activityTypes":[{"id":"57d26204-4371-433f-8430-314c90fa106e","name":"Bouldering","createdAtMillis":1759600000000},""" +
        """{"id":"type-yoga","name":"Yoga","createdAtMillis":1759700000000},""" +
        """{"id":"type-pilates","name":"Pilates","createdAtMillis":1759900000000},""" +
        """{"id":"type-swim","name":"Swimming","createdAtMillis":1759800000000,"archivedAtMillis":1759850000000},""" +
        """{"id":"f93d61d7-f3f9-48d5-86bc-580be047c49a","name":"Jogging","createdAtMillis":1759650000000}],""" +
        """"activityEntries":[{"id":"e-yoga-2","activityTypeId":"type-yoga","activityName":"Yoga","dateKey":"2026-10-08","timestampMillis":1759950000000,"durationMinutes":45},""" +
        """{"id":"e-trail","activityTypeId":"type-gone","activityName":" Trail run ","dateKey":"2026-10-10","timestampMillis":1760100000000,"notes":"Muddy"},""" +
        """{"id":"e-boulder","activityTypeId":"57d26204-4371-433f-8430-314c90fa106e","activityName":"Bouldering","dateKey":"2026-10-06","timestampMillis":1759760000000,"durationMinutes":90},""" +
        """{"id":"e-cycle","activityName":"Cycling","dateKey":"2026-10-04","timestampMillis":1759590000000},""" +
        """{"id":"e-yoga-1","activityTypeId":"type-yoga","activityName":"yOGA","dateKey":"2026-10-03","timestampMillis":1759500000000},""" +
        """{"id":"e-jog","activityTypeId":"f93d61d7-f3f9-48d5-86bc-580be047c49a","activityName":"jogging","dateKey":"2026-10-02","timestampMillis":1759400000000,"durationMinutes":30,"notes":"Easy"}],""" +
        """"substanceLogs":[],"substanceNoneDays":[],""" +
        """"substances":{"tracked":[],"own":[],"stayingAway":[{"ids":[]}],"checkIn":{"remind":false,"at":"sleep-15"},"rewards":null,"whenAway":"due","goal":"logging","exceptionsOnly":true},""" +
        """"checklists":[],"checklistDays":[],"measurements":[$FrozenWeightOfVersion28],""" +
        """"questions":[{"view":"questions","id":"eating","label":"Eating","icon":"food","hue":"orange","about":"yesterday","rewards":{"maxPasses":3,"earn":{"everyKeptDays":7}},"whenAway":"due"}],"questionEntries":[],""" +
        """"time":[{"view":"time","id":"reading","label":"Reading","icon":"book","hue":"yellow","motif":"reading","goal":60,"rewards":{"maxPasses":3,"tokenSteps":{"steps":[{"percent":100,"tokens":1},{"percent":200,"tokens":1}]}},"whenAway":"due"}],"timeLogs":[]}"""

/**
 * What [FrozenVersion29WithActivities] must read as, written out and not left to the migration:
 * the seeded configuration in English words, Gym first with the routine the plan was, with, after
 * the seeded four, Yoga under its type's ID and spelled as its earliest entry spelled it, Trail
 * run under an ID made from its name, then the types never used in the order they were created,
 * Swimming removed; each entry under the activity of its name with its name trimmed; each session
 * the Gym activity's as it was, its day its letter's.
 */
internal fun stateOfFrozenVersion29WithActivities(): AppState {
    val seeded = testActivityConfiguration()
    val configuration = seeded.copy(
        activities = seeded.activities + listOf(
            ListedActivity("type-yoga", "yOGA", com.example.workouttracker.domain.checklist.ViewIcon.Bolt),
            ListedActivity("v29-trail-run", "Trail run", com.example.workouttracker.domain.checklist.ViewIcon.Bolt),
            ListedActivity("type-swim", "Swimming", com.example.workouttracker.domain.checklist.ViewIcon.Bolt, removed = true),
            ListedActivity("type-pilates", "Pilates", com.example.workouttracker.domain.checklist.ViewIcon.Bolt),
        ),
    )
    val entries = listOf(
        ActivityEntry("e-trail", "v29-trail-run", "Trail run", "2026-10-10", 1_760_100_000_000, notes = "Muddy"),
        ActivityEntry("e-yoga-2", "type-yoga", "Yoga", "2026-10-08", 1_759_950_000_000, 45),
        ActivityEntry("e-boulder", "bouldering", "Bouldering", "2026-10-06", 1_759_760_000_000, 90),
        ActivityEntry("e-cycle", "cycling", "Cycling", "2026-10-04", 1_759_590_000_000),
        ActivityEntry("e-yoga-1", "type-yoga", "yOGA", "2026-10-03", 1_759_500_000_000),
        ActivityEntry("e-jog", "jogging", "jogging", "2026-10-02", 1_759_400_000_000, 30, "Easy"),
    )
    val sessions = listOf(
        WorkoutSession(
            "2026-10-09-C", "gym", "C", "2026-10-09", 1_760_025_600_000,
            listOf(ExerciseLog("face_pulls", "Face Pulls", listOf(LoggedSet(17.5, 15.5)))),
        ),
        WorkoutSession(
            "2026-10-07-B", "gym", "B", "2026-10-07", 1_759_852_800_000,
            listOf(ExerciseLog("pull_ups", "Pull Ups", listOf(LoggedSet(-15.0, 8.0)))),
            durationMinutes = 55,
        ),
        WorkoutSession(
            "2026-10-05-A", "gym", "A", "2026-10-05", 1_759_680_000_000,
            listOf(
                ExerciseLog("bench_press", "Bench Press", listOf(LoggedSet(22.5, 10.0), LoggedSet(25.0, 8.0))),
                ExerciseLog("back_squat", "Back Squat", emptyList()),
            ),
        ),
    )
    return AppState(sessions = sessions, activityEntries = entries, activity = configuration).withWeight().withEating().withReading()
}
