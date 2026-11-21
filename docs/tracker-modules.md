Tracker modules connect a tracker kind to the application. Each kind has a stable `TrackerTypeId`;
each instance has a `TrackerRef` containing that type and its own stable ID. Adding a kind does
not add a dispatch case to Home, Settings or tracker navigation. Names, icons and instance IDs
do not select another kind's layout, record rules or presentation.

`feature/tracker/TrackerModule.kt` defines the contracts. A module supplies current views and an
optional creation flow. Each view owns its screen, settings, identity, usability, visibility
command, named reminder commands and presentation metadata. Configuration, edits and record
commands remain typed inside the module. Checklist, measurement, questions and time use this
same integration path; Activity and Substances retain their application paths and contribute to
the same domain evaluation.

Home and Settings discover configured instances through `TrackerModules`. Modules own ordering
and usability. A checklist without active items supplies its unavailable note; the shell does
not infer this by inspecting records. Tags and navigation carry type plus instance ID. Typed
routes become references at their entry boundaries. Platform notification actions retain their
stable URI shapes and request codes; new commands use the generic tracker intent factory.

A module can independently contribute `TrackerReward`, `TrackerObservation` and factual
`TrackerStatistics` snapshots. References belong to the module's namespace and are unique there.
Kinds stored in `AppState` own a `TrackerRuntimeProvider` in their domain package. Providers share
one evaluation context and its indexes; the central assembly lists providers rather than
implementing their record rules. Keep snapshots stable until their source facts or policy change.

Every typed configuration explicitly declares `TrackerMoPolicy(enabled, motif)`. Mo watches
every view that counts toward the streak, on the days the reward walk judges it: `watchedBy` in
`MoLedger.kt` is the one rule, the same for every kind, and no runtime decides it. `enabled` is
the flag that enrolled a view before that rule; it is still stored and read, so a view with it on
stays watched outside the streak. Activity defaults to enabled; all other kinds default to
disabled. The motif controls presentation, and editing it preserves the flag. Neither a bank nor
Mo's watching creates factual records, and Statistics reads each view's own rule, not Mo's.
Measurement value policy, complete question prompts, weekly goals and banks are also explicit
configuration. Every questions tracker stores its actual question texts, answer labels and optional
help. All prompts are editable; their stored answer order defines the scales. There is no question
source or catalogue lookup, and an instance ID never selects different prompts or controls.

`ObservedTrackerCompletions` handles every completion celebration using the configured and
additional observations exposed by `MoEvaluation`. A newly kept day needs a new factual action.
For observations with `owedActionKeysOn`, that action must be newly recorded and previously owed;
timestamps alone cannot turn undoing a miss into a completion. Other observations require a
newer factual action timestamp. Initial observations, settings changes, protection and reservations
do not earn celebrations. Weekly-goal events additionally require `celebratesWeeklyGoal`.
Token events carry a tracker reference, its configured label and an optional earning basis
provided by the reward policy for that earning week.

`MoMood`, `MoStreak` and `MoDayRecord` use tracker-keyed progress, standings and protection.
Rest belongs to each participant: one weekly tracker resting does not rest another tracker.
Deleted-owner records remain facts without enrolling the absent tracker. Passes, freezes,
excused days and exception-only assumptions do not create records, measured values or minutes,
and do not increase factual completion counts or rates.

A reservable module supplies `chosenPasses` and its views' `onSetAside` commands. The shell binds
the shared control; the module owns persistence. Configured app trackers share
`habit_pass_choices_v2`, containing integer `version: 2`, an ISO `day` and
`trackers: [{ "type": "measurement", "id": "sensor" }]`. Type and instance ID together identify
a reservation. Extension modules retain their own store and may omit reservations entirely.

To add a kind:

1. Define typed domain models and application commands, including record ownership, validation
   and factual statistics. Keep reward outcomes separate from recorded facts.
2. Implement `TrackerModule`, its `TrackerView`s and optional `TrackerCreation`. Required methods
   and typed commands make missing integration explicit. Use string resources and the feature's
   shared presentation mapping.
3. Supply it through `MomentumApp.additionalTrackerModules` and replace snapshots when source
   state changes. A module may own storage independently of `AppState`.
4. Add reward, observation and statistics contributions as needed. Observations use the habit day
   they answer; questions about yesterday map the fact onto the due day that asks for it.
5. Verify the production storage connection and important behavior. A new persistent format needs
   an explicit current codec, versioned backup registration, restore validation and representative
   round trips. Plan external conversion for deployed data before changing its format.

`TrackerSettingsHost` supplies dates, page lifecycle and platform access. Modules hand over typed
save functions. `TrackerReminderFlow` and `TrackerVisibilityFlow` keep an operation's reference
while permission is requested, resolve its current module on return, consume it once and reject
deleted targets. Test notification commands expire at the habit-day boundary. Session starting
is an optional tracker command.

The application reads and writes only its current persistence formats. Tracker state is strict
schema31; unsupported fields, damaged records and obsolete store keys refuse access and writes.
There is no runtime historical decoder, migration fallback, inferred profile or old-name
normalization. Older data is converted outside the app, never read by it. Restore validation checks all registered stores and complete round trips.
See the exact shapes and store-key rules in [the current tracker contract](configurable-views.md).
`LoggedViewKind` describes the measurement, questions and time record families; an extension
module need not implement `LoggedView` or reuse their record shapes.

`TrackerModuleFlowTest`, `TrackerIntentTest`, `TrackerRoutingTest` and `feature/tracker` tests use
a counter kind absent from the production domain. The disposable debug gallery also hosts an
in-memory counter module. These exercise extension without adding a product tracker or store.
