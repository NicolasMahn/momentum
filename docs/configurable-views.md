# Current tracker contract

Schema32, 2026-10-25. This document describes the format the app reads and writes now.
[Tracker modules](tracker-modules.md) describes how a tracker kind joins the application.

A tracker is identified by `TrackerRef(type: TrackerTypeId, id: String)`. The type selects its
record model and capabilities; the ID identifies one instance within that type. Labels, IDs,
icons and units do not select hidden rules. A view called Weight, Eating or Reading is an ordinary
instance of its configured kind. A new installation seeds Activity alone, in the current app language.

Configuration explicitly declares numerical value policy, complete question prompts, reward banks,
checkpoints, dated targets and Mo participation. Every typed configuration, including Substances,
has `mo: { "enabled": false, "motif": null }`. Activity defaults to enabled; the other kinds
default to disabled. Mo watches every view that counts toward the streak, on the days the reward
walk judges it (`watchedBy` in `MoLedger.kt`); `enabled` is the flag that decided it before, still
stored and read, so a view with it on stays watched outside the streak too. Nothing sets it and no
screen shows it. `motif` is presentation, independent of enrollment and rewards. Its values
are `null`, `pills`, `teeth`, `food`, `running`, `reading` and `scale`; `MoMotif.Book` stores
`reading`. There is no separate configuration-level `motif` field.

Recorded facts and reward outcomes are separate. A pass, freeze, excused day or exception-only
assumption may protect a goal; it never creates a tick, answer, measurement, use, none-day or
minute. Statistics count what was completed by each view's own rule, the rule Mo and the export
read; a protected day counts as not completed. A timer is judged by the week: a week's bar is its
recorded minutes against what the week asks, and its number is the share of finished weeks that
reached the goal, or this week's progress until one has finished. Measurement direction and question-answer quality do
not earn rewards. A timer completion mark without minutes records completion without inventing
a duration. Habit dates run from 04:00 local time to the next 04:00; stored date keys stay ISO.

## Storage and strictness

The tracker state lives in the SQLite database `momentum.db`, one row per stored object:

```text
entity(kind, id, body, position, changed, deleted)   PRIMARY KEY (kind, id)
meta(key, value)                                       trackerFormat = "rows-1", importedDocument, device, clock,
                                                       syncCursor, syncPushed, syncHeld
```

A row's `kind` is the root field of the tracker document below that holds it (`measurements`,
`measurementLogs`, …), `activity` and `substances` are one row each with the ID `singleton`, and
`timerSessions` holds one row per recorded session start. A row's `body` is exactly the object the
document holds, so the document below remains the definition of what is valid: a read puts the rows
back together into it and decodes it with `decodeVersion32`. A row's ID is given once and never
changes: an object's natural key (its `id`, a checklist
day's `checklistId/dateKey`, a timer day's `time/dateKey`, a weigh-in's `measurement/dateKey`, a
none day's `dateKey`) when no row of its collection holds that ID yet, otherwise a random one; any
ID reads. `position` is the order within a collection, local to the device. A deleted row is a
tombstone with an empty body. `changed` is a hybrid logical clock reading, `millis-counter-device`
with 15 and 5 digits, which a sync compares per row; a new installation's defaults are stamped
`000000000000000-00000-device`, before every change. The device ID is the installation's own: the
database's `device` must match `no_backup/momentum.db.device`, or a new one is made and the sync
marks are forgotten. A restore sets `syncHeld`.

The `workout_tracker` preferences store accepts only these entries:

| Key | Payload |
| --- | --- |
| `holiday_settings_v1` | Current holiday/sick-day settings, payload version 2. |
| `habit_rules_started_v1` | ISO habit-rule start date. |
| `habit_pass_choices_v2` | Version 2, ISO `day`, and `trackers: [{ "type": "measurement", "id": "sensor" }]`. |
| `state_v32`, `reading_sessions_recorded_v1` | The tracker document and its session ledger from before the rows. The first read moves them into the rows and removes them. |

Rows exist exactly while they hold the state. Without rows, the document and its ledger either
both exist, and are upgraded, or are both absent; when both are absent, an empty store or a store
containing only valid current sibling settings can seed Activity. A document beside rows is
removed only when its fingerprint is `importedDocument`; any other is refused. Unknown or obsolete
keys, a missing half of the pair, damaged rows or an unknown layout refuse in-app access and
writes. Existing bytes remain untouched. The app never interprets an old store as a new
installation.

The app has one tracker-state decoder, `decodeVersion32`, and one current writer. Backups from
before schema 32 must be converted outside the app, then validated as current data before
restoration; the application contains no historical tracker decoders or migration chain.

## How stored data grows

Schema 32 is the baseline, frozen in `app/src/test/resources/formats/schema-32.json`, which the
current code must read, save and restore. Adding to stored data never needs a schema bump or a
conversion. Everything schema 32 stored stays required, so a damaged file is still refused. A field
or collection added after it is declared in `AddedAfterBaseline` (`StoredGrowth.kt`) and read as
one of three kinds:

| Kind | Absent means |
| --- | --- |
| Setting | Its fallback: what the app did before the setting existed, a fixed value that never changes. Written on every save. |
| Optional fact | Not recorded. Never a value such as 0. |
| Collection | No records yet. |

A store added after schema 32 may be missing from a backup; restoring that backup leaves the store
empty. The rows (`momentum_rows`) are the first such store: a backup holds each live row as
`kind/id` with `{position, body}`, and the layout under `~format`. Each addition is declared with its place and what its absence means, and a restore may add
nothing else. A collection or store added later gets a frozen sample of its own once it exists,
since schema 32's sample cannot hold it. Removing, renaming or reinterpreting stored data is a
breaking change and needs a plan of its own.

An older build does not understand what a newer one added. The tracker state then refuses to open
and stays untouched; reminder and visibility settings fall back to their defaults and may be
overwritten. Rolling back an update therefore means restoring a backup the older build can read.

Decoding is otherwise strict: no unknown fields, type coercion, trimming, skipped records, merged
duplicates or replacement defaults. Writing validates the complete state, decodes the result and requires
full domain equality before committing. Session bookkeeping is written losslessly in the same
commit. Backup validation independently checks the decoded and re-encoded current payload and
all registered stores. A successful local save is not uninstall-safe until the latest revision
has a verified copy outside app-private storage; older backup generations remain available.

## Wire notation

The shapes below list every field. `?` means omitted when absent, not JSON `null`. The only
nullable fields are root `activity`, `mo.motif`, checklist `rewards` and Substances `rewards`.
Arrays preserve order unless their domain value is a set. Dates are canonical ISO dates;
timestamps are nonnegative integer epoch milliseconds. Numbers are finite JSON numbers, never
numeric strings. Domain limits and references are validated as well as the structural shapes.

`Dated<T>` is plain `T` while unchanged. Once changed, it is
`[{ "value": T }, { "from": "2026-10-25", "value": T }]`: the first revision has no start,
later starts increase strictly, and each revision changes the value. One revision stays plain.

| Shared shape | Fields |
| --- | --- |
| Look | `label: String`, `icon: ViewIcon.storageId`, `hue: ViewHue.storageId`. |
| Mo | `enabled: Boolean`, `motif: String \| null`, using the storage IDs above. |
| Checkpoint | `id`, `at`, `remind`, `label?`, `from?`, `until?`. Time is `HH:mm`, `wake+N` or `sleep-N`. |
| Target | `{per:"day"}`, `{weekdays:["MON",…,"SUN"]}` or `{per:"week",times:Int}`. |
| Period | `{from: Date, until?: Date}`, inclusive and in domain order. |
| Daily bank | `{maxPasses:Dated<Int>, earn:{everyKeptDays:Dated<Int>}, periods?:[Period]}`. |

A bank without `periods` participates over its whole history; `periods: []` participates on no
date. Nonempty periods declare participation dates. `whenAway` is `due`, `excused` or, where a
kind supports a count to lower, `lowerTarget`. `about` is `today` or `yesterday`. IDs remain stable;
user text is stored verbatim after the domain's input validation.

The root requires exactly:

```text
{
  schemaVersion: 32,
  activity: Activity | null,
  sessions: [WorkoutSession], activityEntries: [ActivityEntry],
  checklists: [Checklist], checklistDays: [ChecklistDay],
  measurements: [Measurement], measurementLogs: [MeasurementLog],
  questions: [Questions], questionEntries: [QuestionEntry],
  time: [Time], timeLogs: [TimeLog],
  substances: SubstanceSettings, substanceLogs: [SubstanceLog],
  substanceNoneDays: [SubstanceNoneDay]
}
```

## Checklists

```text
Checklist = {
  view:"checklist", id, label, icon, hue, mo:Mo,
  checkpoints:[Checkpoint], target:Target, rewards:DailyBank|null, items:[Item],
  about?:Dated<AboutDay>
}
Item = {
  id, label, months?:[1..12], from?:Date, until?:Date,
  checkpoints?:[checkpoint-ID], target?:Target, awayTimes?:Int,
  whenAway?:Dated<WhenAway>, exceptionsOnly?:Dated<Boolean>
}
ChecklistDay = {checklistId, dateKey, timestampMillis, ticks:[String], misses?:[String]}
```

`about` is omitted exactly for always today. An item's `whenAway` is omitted exactly for always
due, and `exceptionsOnly` exactly for always false. A mark is a nonblank `checkpointId/itemId`;
ticks and misses are sets, disjoint, and at least one exists. Empty misses are omitted. A
checklist has one record per date. Unknown or deleted owners and mark IDs remain factual records.

## Measurements

```text
Measurement = {
  view:"measurement", id, label, icon, hue, mo:Mo, unit, step,
  valuePolicy:"finite"|"positive", trendDays?:Int, checkpoints:[Checkpoint], whenAway:Dated<WhenAway>,
  rewards?:MeasurementBank
}
MeasurementBank = DailyBank & {earlierWeeks?:EarlierWeeks} | WeekBank
WeekBank = {maxPasses:Dated<Int>, earn:{keptDaysInWeek:Dated<Int>}, periods?:[Period]}
EarlierWeeks = {keptDaysInWeek:Dated<Int>, until:Date}
MeasurementLog = {measurement, dateKey, timestampMillis, value:Number}
```

`step` is canonical positive decimal text, such as `"0.1"` or `"0.5"`, with at most two decimal
places and at most 1000. A JSON number, `decimals` or another spelling such as `"0.50"` is refused.
`valuePolicy` controls entry and estimation; it does not alter recorded numbers. `trendDays`,
from 2 to 90 and 21 for a new measurement, is how many days the chart's trend line follows: an
exponential moving average with daily smoothing `2 / (trendDays + 1)`, seeded from the whole
history. It is presentation only and judges nothing. It is a setting added later: absent, it is 21. Either bank can
belong to any ID; its exact `earn` field declares its kind. A week bank, a token for a
Monday-to-Sunday week with that many logged days, is the older shape, which Weight was first
stored with: nothing makes one any more, and it is never converted on its own. Saving its two
numbers on its page makes it a daily bank from that day, a token for every so many kept days
wherever in a week they fall, and keeps the week rule as `earlierWeeks` with `until`, the last
day that rule judged, the day before the save. Every day up to `until` is judged by
`earlierWeeks` and the bank's size of that day, so no mark, no token earned or spent and no
streak changes; the kept days of the week in progress count toward the first token, one short
of it at most. `earlierWeeks` is a fact added later: absent, the bank never earned by the week. Signed finite measurements, duplicate
facts, multiple facts on a day and records of deleted configurations are preserved.

## Questions

```text
Questions = {
  view:"questions", id, label, icon, hue, mo:Mo, questions:[Question],
  about:Dated<AboutDay>, whenAway:Dated<WhenAway>, checkpoints:[Checkpoint],
  rewards?:DailyBank, madeOn?:Date
}
Question = {id, text, answers:[{id,label}], help?:String}
QuestionEntry = {id, questions, dateKey, timestampMillis, answers:{question-ID:answer-ID}, note?:String}
```

Every configuration stores its complete one-to-five questions, each with two-to-five answers,
and every prompt is editable. The screen uses those stored texts, labels and optional help;
there is no question source, catalogue key, fixed prompt provider or `source` wire field. Answer
scales derive from the stored answer order. Neither label nor instance ID changes presentation
or editability. There is one entry per view and date; empty answers are valid. Entries retain
removed prompt/answer IDs and deleted-owner records as facts. `madeOn`, when present, limits which
due days ask for a record; it never moves a fact.

## Time

```text
Time = {
  view:"time", id, label, icon, hue, mo:Mo, goal:Dated<Int>,
  whenAway:Dated<WhenAway>, checkpoints:[Checkpoint], rewards?:TimeBank
}
TimeBank = {
  maxPasses:Dated<Int>, tokenSteps:Dated<{steps:[{percent:Int,tokens:Int}]}>,
  tokenCover?:Dated<"quarterOfGoal"|Int>, periods?:[Period]
}
TimeLog = {time, dateKey, timestampMillis, minutes?:Int}
```

A timer has one log per date. Present minutes are 1 through 1440. Omitted minutes are a recorded
completion mark without a measured duration, valid for every timer ID. Goals and reward steps
remain explicit; missing steps and `tokensPerWeek` are refused. Timers ask about their own due
day and have no `about` field. Measurement, Questions and Time each have exactly one checkpoint,
ID `day`, with no label or dates of its own.

`tokenCover` is how much of a week that falls short one token covers: `"quarterOfGoal"`, a quarter
of the week's goal rounded up to a whole minute, or a whole number of minutes from 1 to 10080.
Every started amount still missing on Sunday uses one token, as many as the bank holds; a bank
that cannot cover all of them spends none. A week is covered by what its Sunday has, so a change never re-judges an earlier week. It is a setting added later:
absent, it is `"quarterOfGoal"`, what every token covered before it could be set.

## Activity

```text
Activity = {
  view:"activity", id:"activity", label, icon, hue, mo:Mo,
  week:Dated<{days:[{day:weekday-ID,activity?:activity-ID}]}>,
  whenAway:Dated<WhenAway>, awayDays:Dated<Int>, rewards:ActivityBank,
  activities:[ListedActivity], logsDuration?:Boolean
}
ActivityBank = {
  maxPasses:Dated<Int>, tokenSteps:Dated<{steps:[{daysMore:Int,tokens:Int}]}>, periods?:[Period]
}
ListedActivity = {id, label, icon, removed?:true, routine?:{days:[RoutineDay]}}
RoutineDay = {id, label, removed?:true, exercises:[RoutineExercise]}
RoutineExercise = {
  id, label, removed?:true, sets:Int, reps:{from:Int,to:Int},
  start:{reps:Int,weightKg?:Number}, stepKg?:Number
}
ActivityEntry = {id, activity, name, dateKey, timestampMillis, durationMinutes?:Int, notes?:String}
WorkoutSession = {
  id, activity, day, dateKey, timestampMillis, durationMinutes?:Int,
  exerciseLogs:[{exerciseId,exerciseName,sets:[{reps:Number,weightKg?:Number}]}]
}
```

Weekdays are unique and ordered Monday through Sunday. Planned activities resolve against the
configuration. A routine exercise either has both `start.weightKg` and `stepKg`, or neither.
`removed` is written only as true. Optional durations are positive. Records retain their stable
activity, routine-day and exercise references, recorded names and set values even when the
configuration no longer lists them. Missing set weight never becomes an estimated weight.

`logsDuration` is whether logging an activity asks how long it took. It decides what the screen
offers and judges nothing; a duration an entry or a session holds stays whatever it says. It is a
setting added later: absent, it is `false`. That is deliberately not what the app did before it
existed, when the field always stood on the screen: the owner, the only user, asked for the field
to be gone and switches it on himself.

## Substances

```text
SubstanceSettings = {
  mo:Mo, tracked:[substance-ID], own:[{id,label,unit:amount-unit-ID}],
  stayingAway:[{ids:[substance-ID],from?:Date}], checkIn:{remind:Boolean,at:checkpoint-time},
  rewards:DailyBank|null, whenAway:Dated<WhenAway>,
  goal:Dated<"logging"|"stayingAway">, exceptionsOnly:Dated<Boolean>,
  label?:String, icon:ViewIcon.storageId, hue:ViewHue.storageId
}
SubstanceLog = {id, kind:substance-ID, dateKey, timestampMillis, amount?:Number, amountUnit?:String}
SubstanceNoneDay = {dateKey, timestampMillis}
```

The stance always stores a revision array, including an unchanged stance; its first revision
has no `from`. Substance IDs resolve through the current catalogue or stored own entries.
Amount units are `grams`, `drinks`, `uses`, `pouches`, `cups`, `cans` and `pills`. An amount and its
unit are both present or both absent; amounts are positive and the unit matches the substance.
Use IDs and none-day dates are unique. A date holds uses or a none-day, never both.

The look was added after schema 32 (`AddedAfterBaseline`). `label` is the name a person gave the
view and is written only once they did: without it the view is called by the app's own word, in
the app's language. `icon` and `hue` are written on every save; settings stored without them wear
`science` and `pink`, the look Substances had while it was fixed. Its `mo.motif` is any motif but
`pills`.

Logging and staying-away goals have explicit dated policies. Exception-only staying-away may
keep an unrecorded day for rewards. It does not assert no use or create a factual none-day.
