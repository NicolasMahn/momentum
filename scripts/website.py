#!/usr/bin/env python3
"""Momentum's website: a front page, the privacy policy and the legal notice.

The privacy policy and the legal notice exist once per language, in
`app/src/main/assets/privacy/`. The app shows those files under Settings, About Momentum, and the
pages here are built from them, so the two cannot say different things. The front page's words are
in `docs/store/website/`, and its picture of Mo is the app's own launcher icon.

    scripts/website.py build <directory>    write the site into a directory, to look at it
    scripts/website.py publish              build it and put it on the web server

`publish` makes the texts public. It refuses while one still holds a blank such as
`<postal code>`; `--unfinished` publishes the finished texts and leaves the others out.

The site is static files in `/opt/momentum-site/public` on the web server, served by a small
container (`deploy/website/docker-compose.yml`). The server's only Caddy belongs to the
AV_Stuttgart repository; its `deployment/Caddyfile` sends `momentum.nicolas-mahn.de` to that
container.
"""

import argparse
import html
import os
import posixpath
import re
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ElementTree
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / "app/src/main/res"
COMPOSE = ROOT / "deploy/website/docker-compose.yml"
CADDYFILE = ROOT / "deploy/website/Caddyfile"

SITE_URL = "https://momentum.nicolas-mahn.de/"
SERVER = os.environ.get("MOMENTUM_SITE_SERVER", "root@momentum.nicolas-mahn.de")
SERVER_KEY = Path(os.environ.get("MOMENTUM_SITE_KEY", Path.home() / ".ssh/av_stuttgart_hetzner"))
SERVER_DIRECTORY = "/opt/momentum-site"

# Each text: where its two files are, and where its page sits by language.
TEXTS = {
    "home": (ROOT / "docs/store/website", {"en": "", "de": "de/"}),
    "privacy-policy": (ROOT / "app/src/main/assets/privacy", {"en": "privacy/", "de": "de/datenschutz/"}),
    "legal-notice": (ROOT / "app/src/main/assets/privacy", {"en": "legal-notice/", "de": "de/impressum/"}),
}
LANGUAGES = {"en": "English", "de": "Deutsch"}

# The same blank `checkPrivacyPolicyIsFinal` in app/build.gradle.kts looks for.
BLANK = re.compile(r"<[a-z][a-z ]*>")
EMAIL = re.compile(r"[\w.+-]+@[\w-]+(\.[\w-]+)+")
ANDROID = "{http://schemas.android.com/apk/res/android}"


def app_colors():
    """The app's named colors, so the site wears them and not a copy of them."""
    colors = ElementTree.parse(RESOURCES / "values/colors.xml").getroot()
    return {color.get("name"): color.text.strip() for color in colors.iter("color")}


def mo_figure():
    """Mo as the app's launcher icon draws him: SVG markup in the icon's 108-unit square.

    It reads the part of Android's vector format the icon uses and stops at anything else, so a
    changed icon cannot come out wrong without anyone noticing.
    """
    colors = app_colors()
    clips = []

    def color(value):
        return colors[value.removeprefix("@color/")] if value.startswith("@color/") else value

    def attributes(node, allowed):
        given = {name.removeprefix(ANDROID): value for name, value in node.attrib.items()}
        unknown = set(given) - set(allowed)
        if unknown:
            sys.exit(f"The launcher icon uses something this script cannot draw: {sorted(unknown)}")
        return given

    def draw(node):
        kind = node.tag
        if kind == "path":
            a = attributes(node, ["pathData", "fillColor", "fillAlpha", "strokeColor", "strokeWidth", "strokeLineCap", "strokeAlpha"])
            shown = {
                "d": a["pathData"],
                "fill": color(a["fillColor"]) if "fillColor" in a else "none",
                "fill-opacity": a.get("fillAlpha"),
                "stroke": color(a["strokeColor"]) if "strokeColor" in a else None,
                "stroke-width": a.get("strokeWidth"),
                "stroke-linecap": a.get("strokeLineCap"),
                "stroke-opacity": a.get("strokeAlpha"),
            }
            return "<path " + " ".join(f'{name}="{value}"' for name, value in shown.items() if value is not None) + "/>"
        if kind == "group":
            a = attributes(node, ["scaleX", "scaleY", "translateX", "translateY"])
            shown = []
            if a:
                # Android scales a group first and moves it afterwards.
                shown.append(f'transform="translate({a.get("translateX", 0)} {a.get("translateY", 0)}) scale({a.get("scaleX", 1)} {a.get("scaleY", 1)})"')
            inside = []
            for child in node:
                if child.tag == "clip-path":
                    clips.append(f'<clipPath id="clip{len(clips)}"><path d="{attributes(child, ["pathData"])["pathData"]}"/></clipPath>')
                    shown.append(f'clip-path="url(#clip{len(clips) - 1})"')
                else:
                    inside.append(draw(child))
            return f"<g {' '.join(shown)}>" + "".join(inside) + "</g>"
        sys.exit(f"The launcher icon uses something this script cannot draw: <{kind}>")

    foreground = ElementTree.parse(RESOURCES / "drawable/ic_launcher_foreground.xml").getroot()
    drawn = "".join(draw(child) for child in foreground)
    return f"<defs>{''.join(clips)}</defs>{drawn}"


def icon_svg():
    """The launcher icon as an SVG: Mo on the app's background, with rounded corners."""
    # A launcher shows the middle 72 of the icon's 108 units; the rest is room for its mask.
    return (
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="18 18 72 72" role="img" aria-label="Mo">'
        f'<rect x="18" y="18" width="72" height="72" rx="16" fill="{app_colors()["mo_icon_background"]}"/>'
        f"{mo_figure()}</svg>"
    )


def style():
    colors = app_colors()
    return f"""
:root {{ color-scheme: dark; }}
body {{ margin: 0; background: {colors["mo_icon_background"]}; color: {colors["mo_icon_gloss"]}; font: 1.05rem/1.6 system-ui, sans-serif; }}
main {{ max-width: 40rem; margin: 0 auto; padding: 1.5rem 1.25rem 4rem; }}
nav {{ display: flex; flex-wrap: wrap; gap: 0.25rem 1.25rem; font-size: 0.9rem; }}
h1 {{ font-size: 1.7rem; line-height: 1.25; margin: 2rem 0 1rem; }}
h2 {{ font-size: 1.15rem; margin: 2rem 0 0.5rem; }}
a {{ color: {colors["mo_icon_middle"]}; }}
code {{ font: inherit; }}
ul {{ padding-left: 1.25rem; }}
li {{ margin: 0.35rem 0; }}
.mo {{ display: block; width: 9rem; margin: 3rem auto 0; }}
.mo + h1 {{ text-align: center; font-size: 2.4rem; margin: 1rem 0 0.25rem; }}
.mo + h1 + p {{ text-align: center; font-size: 1.2rem; margin: 0 0 2.5rem; }}
""".strip()


def source(text, language):
    directory, _ = TEXTS[text]
    return (directory / f"{text}-{language}.md").read_text(encoding="utf-8")


def blanks(markdown):
    return [match.group() for match in BLANK.finditer(markdown)]


def inline(text):
    """One line as HTML: `**bold**`, code marks, and a mail address a reader can tap."""
    parts = html.escape(text, quote=False).split("**")
    text = "".join(f"<strong>{part}</strong>" if index % 2 else part for index, part in enumerate(parts))
    text = re.sub(r"`([^`]*)`", r"<code>\1</code>", text)
    return EMAIL.sub(lambda match: f'<a href="mailto:{match.group()}">{match.group()}</a>', text)


def body(markdown):
    """A text's blocks as HTML, and its title.

    It reads the part of Markdown the texts use, the same part the app reads in
    `feature/about/LegalTexts.kt`: a `#` title, `##` headings, `- ` bullets, and paragraphs whose
    lines stay lines.
    """
    title = ""
    blocks = []
    paragraph = []
    bullets = []

    def close():
        if paragraph:
            blocks.append("<p>" + "<br>\n".join(inline(line) for line in paragraph) + "</p>")
        if bullets:
            blocks.append("<ul>\n" + "\n".join(f"<li>{inline(item)}</li>" for item in bullets) + "\n</ul>")
        paragraph.clear()
        bullets.clear()

    for line in (line.strip() for line in markdown.splitlines()):
        if not line:
            close()
        elif line.startswith("- "):
            if paragraph:
                close()
            bullets.append(line[2:])
        elif line.startswith("## "):
            close()
            blocks.append(f"<h2>{inline(line[3:])}</h2>")
        elif line.startswith("# "):
            close()
            title = line[2:]
        else:
            if bullets:
                close()
            paragraph.append(line)
    close()
    return title, "\n".join(blocks)


def link(here, there):
    """The way from one page's folder to another's."""
    return posixpath.relpath("/" + there, "/" + here).rstrip("/") + "/"


def page(text, language, texts):
    """One page. It links to the other pages among [texts], the ones being built."""
    title, blocks = body(source(text, language))
    here = TEXTS[text][1][language]
    # The other texts in this language, then this text in the other languages.
    others = [
        (link(here, TEXTS[other][1][language]), language, body(source(other, language))[0])
        for other in texts
        if other != text
    ]
    others += [(link(here, path), code, LANGUAGES[code]) for code, path in TEXTS[text][1].items() if code != language]
    links = "\n".join(f'<a href="{href}" lang="{code}" hreflang="{code}">{html.escape(name)}</a>' for href, code, name in others)
    mo = f'<div class="mo">{icon_svg()}</div>\n' if text == "home" else ""
    return f"""<!doctype html>
<html lang="{language}">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{html.escape(title)}</title>
<link rel="icon" href="{link(here, "")}icon.svg">
<style>
{style()}
</style>
</head>
<body>
<main>
<nav>
{links}
</nav>
{mo}<h1>{inline(title)}</h1>
{blocks}
</main>
</body>
</html>
"""


def unfinished():
    """The texts that still hold a blank, with what is open in each."""
    open_ = {}
    for text, (_, paths) in TEXTS.items():
        found = [f"{text}-{language}.md: {blank}" for language in paths for blank in blanks(source(text, language))]
        if found:
            open_[text] = found
    return open_


def build(directory, texts=TEXTS):
    directory = Path(directory)
    for text in texts:
        for language, path in TEXTS[text][1].items():
            target = directory / path / "index.html"
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(page(text, language, texts), encoding="utf-8")
    (directory / "icon.svg").write_text(icon_svg(), encoding="utf-8")


def run(*command):
    subprocess.run(command, check=True)


def publish(allow_unfinished):
    open_ = unfinished()
    if open_ and not allow_unfinished:
        sys.exit("The texts are not final:\n" + "\n".join(f"  {line}" for lines in open_.values() for line in lines))

    # A first connection learns the server's key; a changed key stops the deploy.
    options = ["-i", str(SERVER_KEY), "-o", "BatchMode=yes", "-o", "StrictHostKeyChecking=accept-new"]
    ssh = " ".join(["ssh", *options])
    with tempfile.TemporaryDirectory() as work:
        build(work, [text for text in TEXTS if text not in open_])
        run("ssh", *options, SERVER, f"mkdir -p {SERVER_DIRECTORY}/public")
        run("rsync", "--archive", "--delete", "--chmod=D755,F644", "--rsh", ssh, f"{work}/", f"{SERVER}:{SERVER_DIRECTORY}/public/")
    run("rsync", "--chmod=F644", "--rsh", ssh, str(COMPOSE), f"{SERVER}:{SERVER_DIRECTORY}/docker-compose.yml")
    run("rsync", "--chmod=F644", "--rsh", ssh, str(CADDYFILE), f"{SERVER}:{SERVER_DIRECTORY}/Caddyfile")
    run("ssh", *options, SERVER, f"cd {SERVER_DIRECTORY} && docker compose up --detach --quiet-pull --force-recreate")

    print(f"Published. {SITE_URL}")
    for lines in open_.values():
        for line in lines:
            print(f"Left out: {line}")


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("build", help="write the site into a directory").add_argument("directory")
    commands.add_parser("publish", help="put the site on the web server").add_argument(
        "--unfinished", action="store_true", help="publish the finished texts and leave out those with a blank"
    )
    arguments = parser.parse_args()
    if arguments.command == "build":
        build(arguments.directory)
    else:
        publish(arguments.unfinished)


if __name__ == "__main__":
    main()
