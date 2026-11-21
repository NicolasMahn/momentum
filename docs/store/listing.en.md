# Store listing, English (en-GB)

Limits from Play Console Help: app name 30, short description 80, full description 4000 characters ([Create and set up your app](https://support.google.com/googleplay/android-developer/answer/9859152)). The counts below are of the text inside each block, counted by script on 2026-11-15.

The wording follows the [Metadata policy](https://support.google.com/googleplay/android-developer/answer/9898842): no emoji, no capitals for emphasis, no "best", "#1", "free" or calls to action in the name or the short description.

## App name

Chosen by the owner on 2026-11-15, 16 of 30 characters. He does not want "Habit Tracker" in the title: the app is one, but that is not what it should be called.

```text
Momentum with Mo
```

Not taken, 23 of 30 characters. It would be found by people who search for "habit tracker"; the short description has to carry that word now.

```text
Momentum: Habit Tracker
```

Alternative, 8 of 30 characters:

```text
Momentum
```

Several other apps on Google Play are called Momentum, so the plain name is harder to find.

## Short description

Alternative A, 69 of 80 characters. It says what the app is for and who Mo is:

```text
Track the habits you choose. Mo, a small flame, helps you keep at it.
```

Alternative B, 77 of 80 characters. It leads with privacy:

```text
A habit tracker with no account and no internet access. Mo keeps you company.
```

I recommend A. It is the app's own promise, the one the welcome screen makes. B tells a stranger nothing about what they will do with the app.

## Full description

3068 of 4000 characters:

```text
Momentum is a habit tracker for the things you decide to keep up: training, a daily measurement, a checklist, a few questions about your day, minutes of reading, or staying away from alcohol or nicotine.

You choose what matters to you. Mo helps you keep at it.

Mo

Mo is a small flame who lives in the app and in a widget for your home screen. He burns brightly when you keep your routines and cools down when you let them slide. He never goes out for good: one finished task warms him again. He nudges you only while something is overdue. He may sulk about an open task, but never about you, your weight or your answers.

Your first day

Open the app and pick what you want to track. Activity and Substances are built in, and you can switch either off. With "Track something new" you add your own:

- Checklist: items you tick once a day, such as brushing your teeth, stretching or taking your vitamins.
- Schedule: items you tick at several times of the day, such as morning and evening.
- Measurement: one number a day with your own unit, such as your weight. A chart shows your records and a trend line.
- Questions: your own multiple-choice questions, each answered with one tap.
- Timer: minutes toward a weekly goal, such as reading, meditating or practising an instrument.

Give each one a name and a look, set a reminder if you want one, and decide whether it counts toward your streak. Then log your first entry.

What else it does

- Activity: log workouts and sports. An activity can have a routine with days, exercises, sets, reps and weights.
- Substances: choose what you track, log a use or "None today", and decide what you want to stay away from. Amounts are optional. Rewards never depend on them.
- Reminders at the times you set.
- Do Not Disturb while a timer runs, if you allow it.
- Streak: kept days in a row, with levels from 7 to 365 days.
- Tokens and Freezes: a Token covers one missed task, a Freeze covers a whole missed day. A covered day keeps your streak, and it still shows as missed in your records. Rewards never change what you recorded.
- Holidays and sick days.
- Statistics, and an export of your whole history as a text file that you can share with an app you choose.
- A day runs from 04:00 to 04:00, so a late evening still counts.
- English and German. Dark design.

Your data

- No account, no ads, no analytics, no in-app purchases.
- The app has no internet permission. It cannot send your records to anyone, including its developer.
- Your records are stored on your phone. You can let Momentum write readable backup files into a folder you choose, and restore from them.
- If Android's own backup is switched on, Android includes Momentum's data in the backup to your Google account, as it does for other apps.

Good to know

Momentum is made by one person. It records what you do and shows you patterns. It is not a medical device and does not diagnose, treat, cure, or prevent any medical condition. For medical advice, including about weight, medication or substance use, please talk to a healthcare professional.
```

## What each claim rests on

- Kinds to track and their examples: `app/src/main/res/values/strings_settings.xml` (`settings_new_*_description`, `settings_activity_description`, `settings_substances_description`) and `docs/glossary.md`. The checklist examples differ from the app's hint on purpose: the hint names medication, and the listing should not read like a medication manager.
- The promise: `settings_first_start_welcome` and `docs/welcome-and-positioning.md`.
- Mo: `docs/mo.md` ("He never dies, and one completed action always warms him", "Guilt, not contempt"); nudges "only while something is overdue": `settings_timing_mo`; widget: `mo_widget_description`.
- Tokens, Freezes, levels, protected days: `docs/glossary.md`, game words.
- Do Not Disturb: `time_focus_message` in `strings_time.xml`.
- Export: `app/StatisticsExportShare.kt`. Backups: `settings_backup_explanation`.
- No internet, no ads, no analytics, no purchases: the merged release manifest and `gradle/libs.versions.toml`.
- The disclaimer is the wording the [Health Content and Services policy](https://support.google.com/googleplay/android-developer/answer/16679511) asks health apps to put in their description.
