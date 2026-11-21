#!/usr/bin/env python3
"""Look at Mo on the PC, without a phone or an emulator.

An act is drawing code, and code that compiles can still draw nonsense, so Mo is judged by his
pictures. These are drawn by the app's own code, run in a unit test with real graphics
(`MoActRenderTest`, `MoActAuditTest` in `app/src/testDebug`), so they are what the phone draws.

    scripts/mo_acts.py moments level_ loss_       film the real screens, including their stills
    scripts/mo_acts.py sheets                     one review sheet per act, all of them
    scripts/mo_acts.py sheets angry_ idle_nap     only acts whose id starts like that
    scripts/mo_acts.py variants idle_             the same act on two dozen different days
    scripts/mo_acts.py film idle_nap --from 2 --to 3 --frames 12
                                                  one stretch of an act, frame by frame
    scripts/mo_acts.py film idle_nap --close face --frames 4
                                                  the same, with his face filling each cell
    scripts/mo_acts.py classic                    his looks without an act: plain, angry, frozen,
                                                  thriving and neglected
    scripts/mo_acts.py film doing_brushing --from 0 --to 0.4 --frames 8 --close face
    scripts/mo_acts.py kit                        every mouth, eye and arm of the kit, on every body
    scripts/mo_acts.py audit                      measure every act: what leaves his box, what jumps
    scripts/mo_acts.py poses                      read every act's poses: hands hidden behind him,
                                                  hands on his face

A sheet shows eight moments (0 to 8.1 s): the top row in the act's brightest state in one variant,
the bottom row in its dimmest state in another. A film has one row per state the act plays in,
unless `--state` names one. Every cell is his 320 x 432 box and is cut off at its edge, as the
widget's picture is.

An act chooses things by the day: a shape, a colour, how many, which side. A sheet shows two such
variants, numbers 1 and 7. `variants` shows numbers 1 to 24 at half size, the first twelve early in
the act and the rest late, each with its number, which `film --seed` then shows large. An act with
three shapes has one that no sheet shows, so look here before calling an act reviewed.

`--close face` fills each cell with his eyes and mouth, and `--close body` with him from the
shoulders down, both several times larger than a sheet shows them. A mouth, a tongue, a hand on a
prop or an arm against his body can only be judged that close. Four frames of a close-up are as
wide as a sheet.

`audit` takes about ten minutes for all acts and writes `audit.tsv`, one line per finding, worst
first: `still` (the first moment, which the widget shows, reaches past his box), `overflow` (any
moment does), `pop` (something jumps from one instant to the next), `face` (the only large pose change
is facial), `quick` (fast, but motion) and `floor` (drawn where his neglected things lie). It saves a
picture of each `still`, `overflow` and `pop` beside it. A finding is a place to look, not a verdict.

`poses` takes a minute and writes `poses.tsv`, one line per act, body and kind, longest first:
`hidden` (his arms are drawn behind him and a hand is over his body, so it is gone), `on-eye` and
`on-mouth` (his arms are in front and one lies on his face), `flip` (his arms jump between behind and
in front while over his body) and `long` (an arm longer than he can reach).

Pictures land in `app/build/mo-acts/<command>/` unless `--out` names another directory.
"""

import argparse
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

TESTS = {
    "moments": "MoMomentRenderTest",
    "sheets": "MoActRenderTest.sheets",
    "film": "MoActRenderTest.film",
    "variants": "MoActRenderTest.variants",
    "classic": "MoActRenderTest.classic",
    "kit": "MoActRenderTest.kit",
    "audit": "MoActAuditTest",
    "poses": "MoActPoseAuditTest",
}


def run(command, out, properties):
    out.mkdir(parents=True, exist_ok=True)
    properties = {"out": out.resolve(), **properties}
    gradle = [
        str(ROOT / "gradlew"), "-q", ":app:testDebugUnitTest", "--rerun",
        "--tests", f"*{TESTS[command]}",
        *(f"-Pmo.acts.{name}={value}" for name, value in properties.items() if value),
    ]
    if subprocess.run(gradle, cwd=ROOT).returncode != 0:
        sys.exit(f"rendering failed; the test report is in {ROOT / 'app/build/reports/tests'}")
    print(out.resolve())


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    commands = parser.add_subparsers(dest="command", required=True)

    def command(name, help):
        added = commands.add_parser(name, help=help)
        added.add_argument("--out", type=Path, help="where the pictures go")
        return added

    def close_up(added):
        added.add_argument("--close", choices=["face", "body"], help="fill each cell with that part of him")

    def stretch(added, end, frames):
        added.add_argument("--from", dest="start", type=float, default=None if end is None else 0.0, help="first moment, in seconds")
        added.add_argument("--to", dest="end", type=float, default=end, help="last moment, in seconds")
        added.add_argument("--frames", type=int, default=frames)

    moments = command("moments", "film takeovers, Home celebrations, reward icons and share pictures")
    moments.add_argument("ids", nargs="*", help="prefixes: level_, loss_, event_, home_, rewards, share_")
    moments.add_argument("--language", choices=["en", "de"], default="en")
    moments.add_argument("--phone", choices=["narrow", "tall"], default="narrow",
                         help="available screen in dp: 360 x 640 or 412 x 892")
    moments.add_argument("--font-scale", choices=["1.0", "1.3"], default="1.0")
    moments.add_argument("--step-ms", type=int, default=64, help="time between frames, in multiples of 16 ms")

    sheets = command("sheets", "one review sheet per act")
    sheets.add_argument("ids", nargs="*", help="id prefixes; all acts without one")
    close_up(sheets)

    variants = command("variants", "two dozen variants of each act on one sheet")
    variants.add_argument("ids", nargs="*", help="id prefixes; all acts without one")
    close_up(variants)

    film = command("film", "one stretch of an act, frame by frame")
    film.add_argument("ids", nargs="+", help="id prefixes")
    stretch(film, end=8.0, frames=8)
    film.add_argument("--seed", type=int, default=1, help="the variant; sheets show 1 above and 7 below")
    film.add_argument("--state", choices=["Blazing", "Steady", "Dimming", "Cold", "Husk"])
    close_up(film)

    classic = command("classic", "his looks without an act")
    classic.add_argument("ids", nargs="*", help="name prefixes, such as plain or teeth; all sets without one")
    stretch(classic, end=None, frames=8)
    close_up(classic)

    kit = command("kit", "every mouth, eye and arm of the kit, on every body")
    kit.add_argument("ids", nargs="*", help="name prefixes, such as mouths_tall or arms; all sheets without one")

    audit = command("audit", "measure every act: what leaves his box, what jumps")
    audit.add_argument("ids", nargs="*", help="id prefixes; all acts without one")

    poses = command("poses", "read every act's poses: hands hidden behind him, hands on his face")
    poses.add_argument("ids", nargs="*", help="id prefixes; all acts without one")

    args = parser.parse_args()
    properties = {"only": ",".join(args.ids), "close": getattr(args, "close", None)}
    if args.command == "moments":
        if args.step_ms < 16 or args.step_ms % 16:
            parser.error("--step-ms must be a positive multiple of 16")
        properties.update(language=args.language, phone=args.phone, font=args.font_scale, step=args.step_ms)
    if args.command == "film":
        properties["film"] = ",".join(str(part) for part in [args.start, args.end, args.frames, args.seed, args.state] if part is not None)
    if args.command == "classic" and args.end is not None:
        properties["film"] = f"{args.start or 0.0},{args.end},{args.frames}"
    run(args.command, args.out or ROOT / "app/build/mo-acts" / args.command, properties)


if __name__ == "__main__":
    main()
