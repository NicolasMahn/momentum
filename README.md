# Momentum

Track the habits you choose. Mo, a small flame, helps you keep at it.

![Mo in his five states: blazing, steady, dimming, cold and husk](docs/pet/mo-reference.png)

Momentum is a habit tracker for Android. It is for the things you decide to keep up: training, a daily measurement, a checklist, a few questions about your day, minutes of reading, or staying away from alcohol or nicotine. Mo burns brightly when you keep your routines and cools down when you let them slide. He never goes out for good.

Momentum has no account and no ads, and it uses the internet only to send feedback you write yourself. The developer never receives what you record. The [privacy policy](https://momentum.nicolas-mahn.de/privacy/) says exactly when a copy leaves the app.

It is an early alpha, built by one person with a lot of help from AI agents. Stored data can still change shape between versions.

## Try it

Momentum is in a closed test on Google Play. To join:

1. Sign in to Google and join the [tester group](https://groups.google.com/g/momentum-with-mo-testers).
2. Opt in on the [test page](https://play.google.com/apps/testing/de.nicolasmahn.momentum), then install the app from Google Play.

Merges to `main` are built and sent to Google Play for the testers.

## Build it

You need Git, JDK 21 and the Android SDK. Nothing else: no keys, no accounts. Build from a clone, because the version code counts the commits.

```sh
./gradlew :app:assembleDebug        # the app, as the package com.example.workouttracker.test
./gradlew :app:testDebugUnitTest    # the unit tests
./gradlew :app:assembleRelease      # the shrunken store build, unsigned without the upload key
```

There are three builds, and they install side by side:

| Build | Package | For |
|---|---|---|
| `debug` | `com.example.workouttracker.test` | Development and tests. Its data is disposable. |
| `personal` | `com.example.workouttracker` | A real installation built from source. Tests never touch it. |
| `release` | `de.nicolasmahn.momentum` | The Google Play build, shrunk with R8. |

Connected tests run only on an emulator, or on a phone you list in `~/.config/momentum/test_phones.txt` (one adb serial per line, then a name). They can erase the app's data, so never list a phone you use.

## Find your way

- [`AGENTS.md`](AGENTS.md) holds the project's rules. They are written for the AI agents that do most of the work, and they are the fastest way for a person to learn how the code is meant to be.
- [`docs/glossary.md`](docs/glossary.md) fixes the words Momentum uses for its own ideas.
- [`docs/configurable-views.md`](docs/configurable-views.md) and [`docs/tracker-modules.md`](docs/tracker-modules.md) describe how a tracker is configured and stored.
- [`docs/data-durability.md`](docs/data-durability.md) describes backups and what they protect against.
- [`docs/mo.md`](docs/mo.md) and [`docs/mo-acts.md`](docs/mo-acts.md) describe Mo and how his animations are made.

## Contribute

Issues and pull requests are welcome. A pull request needs the `unit-tests` check to be green. Please open an issue before a large change: the app has firm opinions, most of them written down in `AGENTS.md`. A contribution takes the license of the files it changes.

Questions: momentum@nicolas-mahn.de

## License

Momentum's own code is under the [MIT License](LICENSE).

Mo is not. Everything in `feature/mo`, the launcher icon and the pictures in `docs/pet/` are under [CC BY-NC 4.0](LICENSES/CC-BY-NC-4.0.txt): he may be used, changed and shared under that license's terms for anything that is not commercial. Because the app contains Mo, a commercial version needs permission or its own character.

[`NOTICE`](NOTICE) has the exact paths, and what to change if you publish a version of your own.
