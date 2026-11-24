#!/usr/bin/env python3
"""Generates Momentum's hue palette and neutral surfaces.

Every color is computed in OKLCH so all view identities share one formula: hues sit at equal
perceptual steps, each at the lightness where it is most vivid, and ledge, label, and container
are fixed offsets from the face. Edit the tables here and rerun; never edit the generated file.

    python3 scripts/generate_palette.py
"""

import math
from pathlib import Path

OUTPUT = (
    Path(__file__).resolve().parent.parent
    / "app/src/main/java/de/nicolasmahn/momentum/core/ui/theme/GeneratedPalette.kt"
)

# name, OKLCH hue, face lightness. Red is reserved for errors and destruction.
HUES = [
    ("Orange", 55, 0.76),
    ("Yellow", 95, 0.86),
    ("Green", 140, 0.80),
    ("Teal", 185, 0.78),
    ("Sky", 230, 0.74),
    ("Indigo", 270, 0.64),
    ("Purple", 305, 0.70),
    ("Pink", 345, 0.72),
    ("Red", 25, 0.66),
]
# Deep tones of the same families, so a view can wear a color no other view wears without squeezing
# the hues closer together. A deep face is dark enough to carry light ink; its lightness is the
# middle of the range where that ink reaches 4.5:1 and the face 3:1 on the dark background.
DEEP_HUES = [
    ("Rust", 50, 0.53),
    ("Olive", 100, 0.52),
    ("Forest", 145, 0.51),
    ("Pine", 190, 0.51),
    ("Navy", 245, 0.52),
    ("Violet", 285, 0.55),
    ("Plum", 320, 0.56),
    ("Berry", 355, 0.55),
]
MAX_CHROMA = 0.30
LEDGE_DROP = 0.15
# A deep face is already dark, so its ledge drops less: the ledge is also a Home tile's edge, and it
# keeps the edge as visible on the dark background as the darkest vivid ledges (Indigo, Red).
DEEP_LEDGE_DROP = 0.045

# A ramp is one hue family in steps: it either ranks values (the answers of a question) or tells
# apart the sub-kinds of one thing (the gym days) without ever leaving the family. The steps start
# above the lightness where the hue's own ink still reaches 4.5:1 on the fill, so every step carries
# the same text color and a step only ever means "brighter", never a different identity.
#
# Every hue a view can wear has one, by one formula: five steps, six hundredths of lightness apart,
# the first on the lowest even hundredth where the ink reaches 4.5:1. A deep hue's ramp is the
# bright end of its family too, under the same dark ink: its own face is too dark to rank five
# values below it.
RAMP_STEPS = 5
RAMP_STEP_HUNDREDTHS = 6
RAMP_INK = (0.24, 0.07)
RAMP_INK_CONTRAST = 4.5
# Ramps with steps of their own, named for what sets them apart from their hue's.
OWN_RAMPS = [
    # Gym and its days. Wide steps, because these are read as small calendar cells side by side.
    ("GreenWide", 140, [0.62, 0.72, 0.82, 0.92]),
]

# Gold means "done" in every view, so it is a fixed meaning rather than a view identity and stays
# out of HUES. Its lightness and hue are the OKLCH reading of the app's established gold, so the
# generated face matches the gold the app started with (#FBBF24) while the ledge and ink come from
# the same formula as every other raised surface.
REWARD = ("Reward", 84.4, 0.837)
REWARD_ROLES = ("face", "ledge", "ink")

# The chrome carries a whisper of blue so no view's hue is favored by its background.
NEUTRAL_HUE = 235
SURFACES = [
    ("DarkSurfaceLowest", 0.16, 0.018),
    ("DarkBackground", 0.20, 0.020),
    ("DarkSurfaceLow", 0.23, 0.022),
    ("DarkSurface", 0.26, 0.025),
    ("DarkSurfaceHigh", 0.30, 0.027),
    ("DarkSurfaceHighest", 0.34, 0.030),
    ("DarkSurfaceVariant", 0.38, 0.030),
    ("DarkSurfaceBright", 0.36, 0.030),
    ("DarkOutline", 0.55, 0.030),
    ("DarkOnSurface", 0.96, 0.010),
    ("DarkOnSurfaceVariant", 0.78, 0.020),
]


def oklch_to_linear_srgb(lightness, chroma, hue):
    a = chroma * math.cos(math.radians(hue))
    b = chroma * math.sin(math.radians(hue))
    l = (lightness + 0.3963377774 * a + 0.2158037573 * b) ** 3
    m = (lightness - 0.1055613458 * a - 0.0638541728 * b) ** 3
    s = (lightness - 0.0894841775 * a - 1.2914855480 * b) ** 3
    return (
        4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
        -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
        -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s,
    )


def in_gamut_chroma(lightness, chroma, hue):
    """Largest chroma at or below the request that sRGB can show."""
    while chroma > 0:
        if all(-0.0005 <= c <= 1.0005 for c in oklch_to_linear_srgb(lightness, chroma, hue)):
            return chroma
        chroma -= 0.004
    return 0.0


def srgb(lightness, chroma, hue):
    chroma = in_gamut_chroma(lightness, chroma, hue)

    def encode(c):
        c = min(max(c, 0.0), 1.0)
        return 12.92 * c if c <= 0.0031308 else 1.055 * c ** (1 / 2.4) - 0.055

    return tuple(round(encode(c) * 255) for c in oklch_to_linear_srgb(lightness, chroma, hue))


def argb(lightness, chroma, hue):
    r, g, b = srgb(lightness, chroma, hue)
    return f"Color(0xFF{r:02X}{g:02X}{b:02X})"


def contrast(first, second):
    """The WCAG contrast ratio of two sRGB colors."""

    def luminance(color):
        r, g, b = (c / 255 for c in color)
        linear = [c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4 for c in (r, g, b)]
        return 0.2126 * linear[0] + 0.7152 * linear[1] + 0.0722 * linear[2]

    lighter, darker = sorted((luminance(first), luminance(second)), reverse=True)
    return (lighter + 0.05) / (darker + 0.05)


def hue_roles(hue, face_lightness, deep=False):
    face_chroma = in_gamut_chroma(face_lightness, MAX_CHROMA, hue)
    return [
        ("face", argb(face_lightness, MAX_CHROMA, hue)),
        ("ledge", argb(face_lightness - (DEEP_LEDGE_DROP if deep else LEDGE_DROP), face_chroma * 0.95, hue)),
        # A deep face carries light ink, and its labels sit a step below a vivid hue's.
        ("ink", argb(0.97, 0.02, hue) if deep else argb(0.24, 0.07, hue)),
        ("label", argb(0.74, 0.13, hue) if deep else argb(max(face_lightness, 0.80), 0.14, hue)),
        # A deep face is an icon on its container on Home, so its container sinks to keep 3:1.
        ("container", argb(0.22, 0.08, hue) if deep else argb(0.30, 0.07, hue)),
    ]


ROLE_NAMES = [role for role, _ in hue_roles(0, 0.7)]


def hue_block(name, hue, face_lightness, deep=False):
    body = "\n".join(f"        {role} = {value}," for role, value in hue_roles(hue, face_lightness, deep))
    return f"    val {name} = MomentumHue(\n{body}\n    )"


def ramp_lightnesses(hue):
    """The steps of a hue's own ramp, counted in hundredths so each is the value a table would hold."""
    ink = srgb(*RAMP_INK, hue)
    first = next(
        hundredths
        for hundredths in range(0, 100, 2)
        if contrast(srgb(hundredths / 100, MAX_CHROMA, hue), ink) >= RAMP_INK_CONTRAST
    )
    return [(first + RAMP_STEP_HUNDREDTHS * step) / 100 for step in range(RAMP_STEPS)]


def ramp_step(hue, lightness, ink):
    return (
        "        MomentumRampStep(\n"
        f"            fill = {argb(lightness, MAX_CHROMA, hue)},\n"
        f"            ink = {ink},\n"
        "        ),"
    )


def ramp_block(name, hue, lightnesses):
    ink = argb(*RAMP_INK, hue)
    steps = "\n".join(ramp_step(hue, lightness, ink) for lightness in lightnesses)
    return f"    val {name} = listOf(\n{steps}\n    )"


def reward_block(name, hue, face_lightness):
    roles = dict(hue_roles(hue, face_lightness))
    body = "\n".join(f"    {role} = {roles[role]}," for role in REWARD_ROLES)
    return f"val Momentum{name} = MomentumRewardShades(\n{body}\n)"


def registry(kotlin_type, name, entries):
    body = "\n".join(f'    "{key}" to {value},' for key, value in entries)
    return f"val {name}: List<Pair<String, {kotlin_type}>> = listOf(\n{body}\n)"


def main():
    surface_names = [name for name, _, _ in SURFACES]
    hues = "\n\n".join([hue_block(*hue) for hue in HUES] + [hue_block(*hue, deep=True) for hue in DEEP_HUES])
    all_ramps = [(name, hue, ramp_lightnesses(hue)) for name, hue, _ in HUES + DEEP_HUES] + OWN_RAMPS
    ramps = "\n\n".join(ramp_block(*ramp) for ramp in all_ramps)
    reward = reward_block(*REWARD)
    shades = "\n".join(f'        "{role}" to {role},' for role in ROLE_NAMES)
    surfaces = "\n".join(
        f"val Momentum{name} = {argb(lightness, chroma, NEUTRAL_HUE)}"
        for name, lightness, chroma in SURFACES
    )
    registries = "\n\n".join(
        [
            registry(
                "MomentumHue",
                "MomentumHueRegistry",
                [(name, f"MomentumHues.{name}") for name, _, _ in HUES + DEEP_HUES],
            ),
            registry(
                "List<MomentumRampStep>",
                "MomentumRampRegistry",
                [(name, f"MomentumRamps.{name}") for name, _, _ in all_ramps],
            ),
            registry(
                "Color",
                "MomentumSurfaceRegistry",
                [(name, f"Momentum{name}") for name in surface_names],
            ),
        ]
    )
    OUTPUT.write_text(
        f"""// Generated by scripts/generate_palette.py. Change the script and rerun it; do not edit this file.
package de.nicolasmahn.momentum.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * One hue of the palette. Every hue offers the same shades so each view identity carries the
 * same visual weight.
 */
data class MomentumHue(
    /** Vivid mid-tone for button faces and icons. */
    val face: Color,
    /** Solid darker shade that shows under a raised face. */
    val ledge: Color,
    /** Text and icons on [face]: near-black of the hue, or near-white on a deep tone. */
    val ink: Color,
    /** Text of the hue on the background, where [face] itself would lack contrast. */
    val label: Color,
    /** Soft tinted ground of the hue, such as a Home tile's icon well. */
    val container: Color,
) {{
    /** Every shade with its role name, so a specimen can show a whole hue without listing roles. */
    val shades: List<Pair<String, Color>> get() = listOf(
{shades}
    )
}}

object MomentumHues {{
{hues}
}}

/**
 * One step of an ordinal scale. A scale ranks values inside a single hue, so a step means "more"
 * and never "better" or "worse"; view identities live in [MomentumHues] instead.
 */
data class MomentumRampStep(
    val fill: Color,
    /** Readable on every step of the ramp, so the scale reads only as brightness. */
    val ink: Color,
)

object MomentumRamps {{
{ramps}
}}

/**
 * The shades of the one reward color. A reward is not a view identity: it means "done" in every
 * view, so it offers only what a raised gold coin needs and never a container or a label.
 */
data class MomentumRewardShades(
    val face: Color,
    /** Solid darker gold that shows under the raised face. */
    val ledge: Color,
    /** Near-black gold for a mark drawn on [face]. */
    val ink: Color,
)

{reward}

{surfaces}

// Registries let a tool walk the whole palette. Without them every consumer would keep its own
// hand-written list of names, which is exactly what this generator exists to prevent.
{registries}
"""
    )
    print(f"Wrote {OUTPUT}")


if __name__ == "__main__":
    main()
