---
name: emulator-explore
description: Use Momentum on the test emulator the way a person would, look at every screen in English and in German, at a large font and on a narrow phone, and report what looks or behaves wrong with screenshots. Use after building something a person will see, before a release, to rehearse an update, or when asked to check a screen. Not for running the instrumented suite, which is `scripts/emulator.py suite`.
---

# Explore Momentum on the emulator

The instrumented suite checks what its tests assert. Nobody looks at the screen while it runs, so
a calendar whose marks cover its numbers passes all of it. This skill is the looking: you use
the app like a person, screenshot what you see, judge it, and write down everything that is off,
whether or not it belongs to the task.

## Rules

- Reach the emulator only through `scripts/emulator.py` (`--help` lists every command). It
  refuses a device that is not an emulator and touches only the disposable debug package
  `com.example.workouttracker.test`. Never call `adb` yourself: a phone may be attached.
- `hold <run>` takes the shared emulator for your run; Mo's renders, the suite and other agents
  wait until you `release`. While you hold it, every command is
  `python3 scripts/emulator.py --run <run> <command>`; the examples below leave that prefix out.
  Release it when you are done, also when you stop early.
- Find, don't fix. Change no code and no repository file. Your report is the output; whoever
  asked decides what is fixed now and what becomes an issue.
- Report everything you see, in scope or not. "Not part of the task" is a column in the report,
  never a reason to leave something out. If you are unsure, report it and say so.
- The emulator holds test data only. Add records, views or settings freely to see a screen
  filled; `restore` takes them away again.

## Before you start

1. Read what you are checking: the plan or diff you were given, `docs/glossary.md` for the app's
   words, and the screen rules in `AGENTS.md` (ledges, shifting, dialogs, calendars). Those rules
   are most of what "looks wrong" means here.
2. Take the emulator and keep what is on it:

       python3 scripts/emulator.py start
       python3 scripts/emulator.py hold <run>
       save <run>-start

   `save` keeps the data, the installed build, and the notification and precise-alarm permissions. To check another
   build, `install <worktree>/app/build/outputs/apk/debug/app-debug.apk` (build it with
   `./gradlew :app:assembleDebug` there); `install` takes debug builds only.

## The states to start from

- **As found:** the emulator's long-lived test install with many views. Its records may be
  sparse in recent weeks; add what a screen needs to show itself filled. The app reads only its
  current stored format. If it says "Momentum could not read your records", the data on the
  emulator predates the build you check: say so in the report and start from a new install.
- **New install:** `fresh` clears the app the way a new installation finds it, without the
  notification permission. Use it for first start and empty states.
- **Updated:** after `fresh`, `install --downgrade <old.apk>`, use the old app, then
  `install <new.apk>` over it. `install` stops the app before installing, as
  `scripts/deploy_personal.py` does on a real phone, so this is a personal build's update path. Record
  `alarms`, `notifications`, `prefs` and `crashes` before and right after the install, before
  you `launch`: launching starts the app's own repairs and clears the crash log. The new shared
  reminders all carry one alarm tag. Match them by time against `prefs checklist_reminder_runtime`,
  and report two at the same time as unverified: nothing on the emulator tells them apart.
  Across a change of stored format the app must refuse the old data, never convert or replace it:
  conversion happens outside the app. There, check that it
  says it cannot read the records and that `prefs` still shows the old data unchanged.

## The variants

Momentum is dark only and has no light scheme, so there is no theme to switch. Every screen in
scope is seen in two variants:

1. **Baseline:** `look --reset`: the phone's English, normal font, 411 dp.
2. **Worst case:** `look --locale de-DE --font 1.3 --width 320`. German is the longer language,
   and 320 dp is the narrowest width the app is checked at.

Language, font and width may restart the screen, so walk the app once for each variant. When the
worst case shows a problem, isolate it with one variant at a time, so the report says whether
German, the font or the width causes it. `look` with no options prints the current variant.

## Use it like a person

For each screen in scope:

- Do what it is for: record, change, remove, set up a view, turn a reminder on.
- Then try what a person might: an empty value, zero, a very long name, a comma as decimal
  separator, the same action twice, leaving with `key back` before saving.
- Leave and come back, and `launch` again: is what you did still there, and shown the same?
- Read the words: do they say what happens, in both languages?

Move around with `screen`, which lists each element with its label, its center and `[tap]`
where it can be tapped, and `tap <label>` (add `--scroll` for elements further down, `--contains`
for part of a label). `scroll down` shows what is further down. `type <text> --into <label>`
fills a field. After typing, check `screen` before pressing `key back`: if no keyboard is open,
back leaves the page and drops what you typed. Home's tiles open the views; a view's settings are its row in Settings.

`screen` costs no image. Take a `shot` where you judge how something looks, and read the PNG.
Name shots `<screen>-<variant>`, for example `weight-calendar-worst`.

To see notifications, use a view's "Send a test" in its Settings page, then `notifications`
(titles, texts, actions) and `shade open` with a `shot`. Run `crashes` after anything odd.

## What counts as wrong

Judge each worst-case shot side by side with its baseline, part by part, not on its own. A
screen can be readable everywhere and still be wrong.

- **Weight changes between variants.** What stands out in the baseline must stand out in the
  worst case, and what is quiet must stay quiet. Empty days, borders and backgrounds that turn
  into heavy blocks are wrong; so are marks that fade away.
- Anything light: a white flash at launch, a light dialog or sheet, dark icons in the status or
  navigation bar. The app has one look, the dark one, whatever the phone is set to.
- Text or an icon too faint to read against its ground.
- Text cut off, wrapped badly, overlapping or running under something.
- Something that looks pressable but isn't (a raised, outlined surface), or is pressable but
  doesn't look it.
- Content that jumps when a message appears or a value changes.
- App text in English on a German screen: report it as a translation gap, kind odd, since German
  may fall back to English by design. A raw key, a placeholder or a cut sentence is wrong. Names
  a person typed stay as typed; they are no finding.
- A number, date or name that doesn't match what was entered, or a record that didn't survive
  leaving or restarting.
- A dialog where the rules say none belongs, or a step that can't be undone without warning.
- A crash, a frozen screen, a notification without its action.
- Anything else that looks odd. Say why.

## The report

Write `report.md` in the run folder (`hold` prints it) as you go, so nothing is lost if the run
stops; use the shell (`cat >> report.md <<'EOF'`) if your file tools may not write there. One entry per finding:

- **What is wrong**, in one sentence.
- **Where:** the screen, and the variants it shows in.
- **How to get there:** the steps from launch.
- **Screenshots:** paths in the run folder.
- **Kind:** broken (doesn't work), wrong (works, but shows or does the wrong thing), or odd
  (looks off).
- **In scope:** yes if the change you were checking caused or touches it, otherwise no.
- **Sure or unsure.**

End the report with what you covered (screens × variants × states) and what you did not.

## When you are done

    look --reset
    restore <run>-start
    release

`restore` puts the build, the data and both permissions back and checks the data byte for byte.
It leaves the app stopped, so it has no alarms until it is next launched.
Say so if it reports a difference.

Your final message starts with the in-scope findings, then a list headed "Seen, not fixed" with
the out-of-scope ones, then the coverage gaps, then the report's path. Every finding gets a line,
with nothing left out because it seemed small.

## Cost

Every screenshot you read costs context. Keep a run to about 40: one view's screens and pages in
three variants, or a few areas in two. For a full sweep, split the app into areas and give each
its own run.
