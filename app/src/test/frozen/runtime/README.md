These files preserve the runtime specification before the user-requested clean break on 2026-10-25. They are outside the Kotlin test source roots. Their original expectations are retained for historical reference, not regenerated against new code, and they provide no runtime compatibility implementation.

The complete MoMoodTest, MoLedgerTest and MoStreakTest suites assumed original task identities, six-day Reading/Eating/Weight schedules, or the former freeze rules. MoOverdueTest and MoObligationsComparisonTest assumed original-task deadlines, including Reading's special deadline and Activity's reminder override. MoOutcomeGoldenTest and SupplementOutcomeGoldenTest compared original-feature outcome snapshots. These contracts were explicitly retired.

The `.legacy-method.kt` files retain individual original-only assertions removed from otherwise active configuration suites: pre-reward identity free days, Eating's special on-time cutoff, and a comparison against the recovered six-task profile. The remaining tests in those suites still execute.

Active replacement coverage:

- GenericMoBehaviorTest exercises explicit free-day allocation/reset/backfill, first factual dates, visibility, sickness, heat relief, partial checkpoints, timestamp ordering, neglect, thriving, shared deadlines and the 04:00 boundary using arbitrary tracker references.
- GenericRuntimePolicyTest proves reserved names/IDs have no extra policy, factual values are independent of reward protection, observations retain recorded ownership, and absent/unrecorded trackers invent no records.
- IndependentMoPolicyTest proves all six kinds remain observed after leaving rewards, no-bank/facts-only ownership changes preserve history, measurement earning thresholds cannot alter Mo's daily target, holiday ribbon marks remain factual, and timer leave cannot rewrite its first partial week.
- TrackerObservationTest, TrackerEventsTest, TrackerRewardCatalogueTest, WeeklyRestOwnershipTest and the domain/reward suites retain current generic integration, event, bank, rest and freeze coverage.
- OnlyLogMissesRewardsTest remains active, including the care-assumption test proving that only factual records enter statistics. Other assumption, sick-day and look-back suites remain active too.

Gallery tests use explicitly configured demo_* references and stored editable questions. They no longer request canonical runtime identities or fixed built-in prompts.
