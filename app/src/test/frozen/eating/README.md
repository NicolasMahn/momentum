These English and German transcripts record the retired, built-in Eating screen. They are
historical evidence, preserved without regeneration. Their runner and script are kept as
noncompiled fixtures in `retired-runner`.

The current app stores each question, answer label and help text in its tracker configuration.
It has no built-in Eating wording, translated prompt catalogue or nutrition-only help dialog.
Consequently these snapshots are not the contract of the generic questions screen.

Current behavior is covered by `QuestionsScreenTest` (editing, dates, saved drafts, failures and
record isolation) and `StoredQuestionsScreenTest` (accepted archive data rendered verbatim under
another UI language, generic help, save and re-decode with exact answer IDs). No current baseline
was generated from these historical snapshots.
