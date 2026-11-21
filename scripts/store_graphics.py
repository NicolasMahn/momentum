#!/usr/bin/env python3
"""The pictures Momentum's Google Play listing needs, drawn from the app's own parts.

Mo is the launcher icon, the colours are the app's, the type is the app's Nunito, so the store
cannot show a Momentum the app does not have. Sizes are Play's.

    scripts/store_graphics.py icon <directory>                 icon.png, 512 x 512
    scripts/store_graphics.py feature <directory>              feature-graphic.png, 1024 x 500
    scripts/store_graphics.py screenshots <raw> <directory>    <language>/NN-name.png, 1080 x 1920

`screenshots` frames full-screen shots of the debug build, taken with made-up data and never from
anyone's records. It expects `<raw>/<language>/NN-name.png` and puts each under the caption that
`CAPTIONS` holds for its name.

The pictures are drawn by Chrome without a window, which has to be installed. They are not kept
in the repository: draw them again when the app or a caption changes.
"""

import argparse
import html
import shutil
import struct
import subprocess
import sys
import tempfile
from pathlib import Path

import website

FONTS = website.RESOURCES / "font"
CHROME = ("google-chrome", "chromium", "chromium-browser")
# How much of a shot's height the status bar takes on the test emulator.
STATUS_BAR = 0.05

# What each screenshot says above the screen. Play allows no "best", "free" or "new" here, no
# call to action, and at most a fifth of the picture for words.
CAPTIONS = {
    "en": {
        "home": "Mo, a small flame, keeps you company",
        "checklist": "Checklists for your day",
        "weight": "One number a day, with a trend",
        "activity": "Workouts and routines",
        "reading": "Minutes toward a weekly goal",
        "new": "You choose what to track",
        "streak": "A streak that allows for a bad day",
    },
    "de": {
        "home": "Mo, eine kleine Flamme, ist mit dabei",
        "checklist": "Checklisten für deinen Tag",
        "weight": "Eine Zahl pro Tag, mit Trend",
        "activity": "Training und Routinen",
        "reading": "Minuten für ein Wochenziel",
        "new": "Du entscheidest, was du erfasst",
        "streak": "Ein Streak, der einen schlechten Tag verzeiht",
    },
}


def mo(view):
    """Mo alone, cut to `view` of the launcher icon's 108-unit square."""
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{view}">{website.mo_figure()}</svg>'


def sheet(body, style=""):
    colors = website.app_colors()
    faces = "".join(
        f'@font-face {{ font-family: Nunito; font-weight: {weight}; src: url("{(FONTS / file).as_uri()}"); }}'
        for weight, file in ((500, "nunito_medium.ttf"), (600, "nunito_semibold.ttf"), (800, "nunito_extrabold.ttf"))
    )
    return f"""<!doctype html>
<meta charset="utf-8">
<style>
{faces}
html, body {{ margin: 0; height: 100%; overflow: hidden; }}
body {{ background: {colors["mo_icon_background"]}; color: {colors["mo_icon_gloss"]}; font-family: Nunito, sans-serif; }}
{style}
</style>
{body}
"""


def draw(page, width, height, target):
    chrome = next((found for name in CHROME if (found := shutil.which(name))), None)
    if chrome is None:
        sys.exit(f"Drawing needs Chrome without a window; none of {', '.join(CHROME)} is installed.")
    target.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as directory:
        source = Path(directory) / "page.html"
        source.write_text(page, encoding="utf-8")
        subprocess.run(
            [
                chrome, "--headless=new", "--disable-gpu", "--hide-scrollbars",
                "--force-device-scale-factor=1", f"--user-data-dir={directory}/profile",
                f"--window-size={width},{height}", f"--screenshot={target}", source.as_uri(),
            ],
            check=True, capture_output=True,
        )
    print(target)


def icon(directory):
    # The launcher shows the middle 72 units and Mo fills them. Play rounds the corners itself,
    # so the store icon shows 88 units and leaves him air on every side.
    page = sheet(mo("10 10 88 88"), "svg { display: block; width: 512px; height: 512px; }")
    draw(page, 512, 512, directory / "icon.png")


def feature(directory):
    style = """
body { display: flex; align-items: center; justify-content: center; gap: 20px; }
svg { width: 280px; height: 280px; flex: none; }
p { margin: 0 28px 0 0; font-size: 110px; font-weight: 800; letter-spacing: -2px; }
"""
    draw(sheet(f'{mo("18 18 72 72")}<p>Momentum</p>', style), 1024, 500, directory / "feature-graphic.png")


def size(png):
    """A PNG's width and height, read from its header."""
    with png.open("rb") as file:
        header = file.read(24)
    return struct.unpack(">II", header[16:24])


def screenshots(raw, directory):
    style = """
body { display: flex; flex-direction: column; align-items: center; }
p { margin: 0; height: 330px; padding: 0 80px; box-sizing: border-box; display: flex; align-items: center;
    text-align: center; font-size: 66px; line-height: 1.15; font-weight: 800; text-wrap: balance; }
div { height: 1530px; overflow: hidden; border-radius: 44px;
    outline: 3px solid color-mix(in srgb, currentColor 22%, transparent); outline-offset: -1px; }
img { display: block; width: 100%; }
"""
    shots = sorted(raw.glob("*/*.png"))
    if not shots:
        sys.exit(f"No screenshots in {raw}: expected <language>/NN-name.png.")
    for shot in shots:
        language, name = shot.parent.name, shot.stem.split("-", 1)[-1]
        caption = CAPTIONS.get(language, {}).get(name)
        if caption is None:
            sys.exit(f"{shot} has no caption: add '{name}' for '{language}' to CAPTIONS.")
        width, height = size(shot)
        # The status bar shows the emulator's clock and icons, so the frame starts under it.
        hidden = round(height * STATUS_BAR)
        frame = f"aspect-ratio: {width} / {height - hidden}"
        picture = f'<img style="margin-top: -{hidden / width:.4%}" src="{shot.resolve().as_uri()}">'
        page = sheet(f'<p>{html.escape(caption)}</p><div style="{frame}">{picture}</div>', style)
        draw(page, 1080, 1920, directory / language / shot.name)


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    commands = parser.add_subparsers(dest="command", required=True)
    for name in ("icon", "feature"):
        commands.add_parser(name).add_argument("directory", type=Path)
    framed = commands.add_parser("screenshots")
    framed.add_argument("raw", type=Path)
    framed.add_argument("directory", type=Path)
    arguments = parser.parse_args()
    if arguments.command == "icon":
        icon(arguments.directory)
    elif arguments.command == "feature":
        feature(arguments.directory)
    else:
        screenshots(arguments.raw, arguments.directory)


if __name__ == "__main__":
    main()
