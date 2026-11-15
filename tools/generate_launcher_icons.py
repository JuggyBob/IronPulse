#!/usr/bin/env python3
"""
Generate IronPulse launcher icon variants from the existing foreground artwork.

The source is the same `ic_app_icon_fg.jpg` used by the current adaptive icon. The script removes
its dark square background, creates transparent adaptive-icon foregrounds, monochrome masks,
preview images and legacy density PNGs. Run from the repository root:

    python tools/generate_launcher_icons.py
"""
from __future__ import annotations

import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageOps

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app" / "src" / "main" / "res"
SOURCE = RES / "drawable" / "ic_app_icon_fg.jpg"
NODPI = RES / "drawable-nodpi"
NODPI.mkdir(parents=True, exist_ok=True)

# (key, accent dark, accent light, preview background, legacy background)
VARIANTS = {
    "default": ("#123B16", "#8CFF5A", "#0D1117", "#101A13"),
    "cyan": ("#06313D", "#5BE9FF", "#0A1E28", "#071A22"),
    "amber": ("#3A2408", "#FFC857", "#241607", "#201507"),
    "violet": ("#2B1740", "#C08BFF", "#160D22", "#160D20"),
    "ocean": ("#082A46", "#5BC8FF", "#071A28", "#081B2A"),
    "crimson": ("#3A0B12", "#FF6B7A", "#240A0E", "#2A0A10"),
    "rose": ("#3A1028", "#FF9BCB", "#240D1A", "#2A0C1B"),
    "arctic": ("#26313B", "#EAF7FF", "#141C22", "#182128"),
}

# Adaptive icon backgrounds. The original three stay byte-for-byte compatible with the resources
# that shipped in 4.8.1; the four new variants use the same generator.
BACKGROUNDS = {
    "cyan": ("#061923", "#0C3340"),
    "amber": ("#1F1405", "#3A2408"),
    "violet": ("#130A1D", "#2B1740"),
    "ocean": ("#061A2A", "#0B3550"),
    "crimson": ("#21060A", "#3A0D15"),
    "rose": ("#210812", "#3A1028"),
    "arctic": ("#10161B", "#26333D"),
}


def transparent_logo() -> Image.Image:
    """The original logo with its dark square background removed and safely centred."""
    image = Image.open(SOURCE).convert("RGB")
    pixels = image.load()
    alpha = Image.new("L", image.size, 0)
    alpha_pixels = alpha.load()
    for y in range(image.height):
        for x in range(image.width):
            r, g, b = pixels[x, y]
            brightest = max(r, g, b)
            darkest = min(r, g, b)
            saturation = (brightest - darkest) / brightest if brightest else 0.0
            # The source JPG has a dark, slightly teal square background (value <= 49).
            # A pure brightness ramp above that removes it completely; the saturation term
            # keeps darker, highly coloured glow pixels that are part of the logo itself.
            brightness_alpha = (brightest - 52.0) / 58.0
            saturation_alpha = (saturation - 0.52) / 0.28
            a = int(max(0.0, min(1.0, max(brightness_alpha, saturation_alpha))) * 255)
            alpha_pixels[x, y] = a

    rgba = image.convert("RGBA")
    rgba.putalpha(alpha)
    bbox = alpha.getbbox()
    if bbox:
        rgba = rgba.crop(bbox)
    # Fit the logo into the adaptive foreground safe area and add transparent breathing room.
    target = 330
    scale = min(target / rgba.width, target / rgba.height)
    rgba = rgba.resize(
        (max(1, int(rgba.width * scale)), max(1, int(rgba.height * scale))),
        Image.LANCZOS,
    )
    canvas = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
    canvas.alpha_composite(rgba, ((512 - rgba.width) // 2, (512 - rgba.height) // 2))
    return canvas


def duotone(logo: Image.Image, dark: str, light: str) -> Image.Image:
    gray = ImageOps.grayscale(logo.convert("RGB"))
    tinted = ImageOps.colorize(gray, black=dark, white=light).convert("RGBA")
    tinted.putalpha(logo.getchannel("A"))
    return tinted


def rounded_background(size: int, color: str, radius: int) -> Image.Image:
    image = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw.rounded_rectangle((0, 0, size - 1, size - 1), radius=radius, fill=color)
    return image


def circular_background(size: int, color: str) -> Image.Image:
    image = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw.ellipse((0, 0, size - 1, size - 1), fill=color)
    return image


def legacy_icon(logo: Image.Image, color: str, round_icon: bool, size: int) -> Image.Image:
    canvas = (
        circular_background(size, color)
        if round_icon
        else rounded_background(size, color, radius=int(size * 0.22))
    )
    logo_size = int(size * 0.68)
    scaled = logo.resize((logo_size, logo_size), Image.LANCZOS)
    canvas.alpha_composite(
        scaled,
        ((size - logo_size) // 2, (size - logo_size) // 2),
    )
    return canvas


def adaptive_resources(key: str) -> None:
    """Write the adaptive background and alias resources for one non-default variant."""
    start, end = BACKGROUNDS[key]
    drawable = RES / "drawable"
    adaptive = RES / "mipmap-anydpi-v26"
    drawable.mkdir(parents=True, exist_ok=True)
    adaptive.mkdir(parents=True, exist_ok=True)

    # The source artwork is a wide dumbbell. Drawn as the whole 108dp foreground it lands
    # outside the 66dp adaptive-icon safe zone and launchers with a circular/squircle mask cut
    # the plates off. The shipped default icon has always wrapped its artwork in a 66dp item;
    # a 60dp item makes the generated logo the same visual width as the default one
    # (38.7dp versus 38.3dp) and keeps it well inside every mask.
    (drawable / f"ic_launcher_foreground_{key}.xml").write_text(
        f'''<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item
        android:width="60dp"
        android:height="60dp"
        android:drawable="@drawable/ic_app_icon_fg_{key}"
        android:gravity="center" />
</layer-list>
''',
        encoding="utf-8",
    )

    (drawable / "ic_launcher_monochrome.xml").write_text(
        '''<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item
        android:width="60dp"
        android:height="60dp"
        android:drawable="@drawable/ic_app_icon_mono"
        android:gravity="center" />
</layer-list>
''',
        encoding="utf-8",
    )

    (drawable / f"ic_launcher_background_{key}.xml").write_text(
        f'''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:angle="315"
                android:startColor="{start}"
                android:endColor="{end}"
                android:type="linear" />
        </aapt:attr>
    </path>
</vector>
''',
        encoding="utf-8",
    )

    adaptive_body = f'''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background_{key}" />
    <foreground android:drawable="@drawable/ic_launcher_foreground_{key}" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
'''
    (adaptive / f"ic_launcher_{key}.xml").write_text(adaptive_body, encoding="utf-8")
    (adaptive / f"ic_launcher_{key}_round.xml").write_text(adaptive_body, encoding="utf-8")


def main() -> None:
    source_logo = transparent_logo()
    source_logo.save(NODPI / "ic_app_icon_fg_default.png")
    mono = Image.new("RGBA", source_logo.size, (255, 255, 255, 0))
    mono.putalpha(source_logo.getchannel("A"))
    mono.save(NODPI / "ic_app_icon_mono.png")

    densities = {
        "mdpi": 48,
        "hdpi": 72,
        "xhdpi": 96,
        "xxhdpi": 144,
        "xxxhdpi": 192,
    }

    for key, (dark, light, preview_bg, legacy_bg) in VARIANTS.items():
        logo = source_logo if key == "default" else duotone(source_logo, dark, light)
        logo.save(NODPI / f"ic_app_icon_fg_{key}.png")

        # Large preview used by Settings -> Appearance -> App Icon.
        preview = rounded_background(512, preview_bg, radius=110)
        preview.alpha_composite(logo)
        preview.save(NODPI / f"ic_app_icon_preview_{key}.png")

        if key == "default":
            # The existing mipmap resources remain the default launcher icon.
            continue

        adaptive_resources(key)

        for density, size in densities.items():
            folder = RES / f"mipmap-{density}"
            folder.mkdir(parents=True, exist_ok=True)
            legacy_icon(logo, legacy_bg, round_icon=False, size=size).save(
                folder / f"ic_launcher_{key}.png"
            )
            legacy_icon(logo, legacy_bg, round_icon=True, size=size).save(
                folder / f"ic_launcher_{key}_round.png"
            )

    print("launcher icon variants generated")


if __name__ == "__main__":
    main()
