#!/usr/bin/env python3
"""Generate Basil launcher icons with adaptive-icon safe-zone padding."""

from __future__ import annotations

import shutil
import subprocess
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
LEAF_SRC = ROOT / "branding" / "basil_leaf_source.png"
MASTER_OUT = ROOT / "branding" / "basil.png"
FG_OUT = ROOT / "branding" / "basil_foreground.png"
BG = (242, 245, 238, 255)


def load_leaf() -> Image.Image:
    if not LEAF_SRC.exists():
        raise SystemExit(f"Missing {LEAF_SRC}")
    img = Image.open(LEAF_SRC).convert("RGBA")
    pixels = []
    for r, g, b, a in img.getdata():
        if r > 245 and g > 245 and b > 245:
            pixels.append((r, g, b, 0))
        else:
            pixels.append((r, g, b, a))
    img.putdata(pixels)
    return img


def fit_on_canvas(
    leaf: Image.Image,
    size: int,
    scale: float,
    background: tuple[int, int, int, int] | None,
) -> Image.Image:
    canvas = Image.new("RGBA", (size, size), background if background is not None else (0, 0, 0, 0))
    box = int(size * scale)
    fitted = leaf.copy()
    fitted.thumbnail((box, box), Image.Resampling.LANCZOS)
    ox = (size - fitted.width) // 2
    oy = (size - fitted.height) // 2
    canvas.paste(fitted, (ox, oy), fitted)
    return canvas


def resized_rgb(master: Image.Image, size: int) -> Image.Image:
    return master.resize((size, size), Image.Resampling.LANCZOS).convert("RGB")


def write_desktop_packaging_icons(master: Image.Image, icons_dir: Path) -> None:
    """Icons for Compose Desktop jpackage (DMG / MSI / Deb)."""
    icons_dir.mkdir(parents=True, exist_ok=True)
    resized_rgb(master, 512).save(icons_dir / "icon.png")

    ico_sizes = [16, 32, 48, 64, 128, 256]
    ico_images = [resized_rgb(master, size) for size in ico_sizes]
    ico_images[0].save(
        icons_dir / "icon.ico",
        format="ICO",
        sizes=[(s, s) for s in ico_sizes],
        append_images=ico_images[1:],
    )

    if sys.platform != "darwin":
        print("Skipping icon.icns (requires macOS iconutil)")
        return

    iconset = icons_dir / "icon.iconset"
    if iconset.exists():
        shutil.rmtree(iconset)
    iconset.mkdir()

    iconset_entries = [
        ("icon_16x16.png", 16),
        ("icon_16x16@2x.png", 32),
        ("icon_32x32.png", 32),
        ("icon_32x32@2x.png", 64),
        ("icon_128x128.png", 128),
        ("icon_128x128@2x.png", 256),
        ("icon_256x256.png", 256),
        ("icon_256x256@2x.png", 512),
        ("icon_512x512.png", 512),
        ("icon_512x512@2x.png", 1024),
    ]
    for filename, px in iconset_entries:
        resized_rgb(master, px).save(iconset / filename)

    subprocess.run(
        ["iconutil", "-c", "icns", str(iconset), "-o", str(icons_dir / "icon.icns")],
        check=True,
    )
    shutil.rmtree(iconset)


def main() -> None:
    leaf = load_leaf()
    # Legacy / round icons: padded on cream so the leaf is not edge-cropped.
    master = fit_on_canvas(leaf, 1024, 0.58, BG)
    # Adaptive foreground: transparent, content inside the ~66% safe zone.
    foreground = fit_on_canvas(leaf, 1024, 0.56, None)

    master.convert("RGB").save(MASTER_OUT)
    foreground.save(FG_OUT)

    densities = {
        "mdpi": (48, 108),
        "hdpi": (72, 162),
        "xhdpi": (96, 216),
        "xxhdpi": (144, 324),
        "xxxhdpi": (192, 432),
    }
    for folder, (px, fgp) in densities.items():
        out = ROOT / f"androidApp/src/main/res/mipmap-{folder}"
        out.mkdir(parents=True, exist_ok=True)
        master.resize((px, px), Image.Resampling.LANCZOS).convert("RGB").save(out / "ic_launcher.png")
        master.resize((px, px), Image.Resampling.LANCZOS).convert("RGB").save(out / "ic_launcher_round.png")
        foreground.resize((fgp, fgp), Image.Resampling.LANCZOS).save(out / "ic_launcher_foreground.png")

    web = ROOT / "composeApp/src/wasmJsMain/resources/icons"
    web.mkdir(parents=True, exist_ok=True)
    resized_rgb(master, 192).save(web / "icon-192.png")
    resized_rgb(master, 512).save(web / "icon-512.png")
    resized_rgb(master, 32).save(ROOT / "composeApp/src/wasmJsMain/resources/favicon.png")
    desktop = ROOT / "composeApp/src/desktopMain/resources"
    desktop.mkdir(parents=True, exist_ok=True)
    resized_rgb(master, 256).save(desktop / "icon.png")
    write_desktop_packaging_icons(master, desktop / "icons")
    ios = ROOT / "branding/generated/ios"
    ios.mkdir(parents=True, exist_ok=True)
    ios_icon = ios / "AppIcon-1024.png"
    master.resize((1024, 1024), Image.Resampling.LANCZOS).convert("RGB").save(ios_icon)
    appiconset = ROOT / "iosApp/iosApp/Assets.xcassets/AppIcon.appiconset"
    appiconset.mkdir(parents=True, exist_ok=True)
    master.resize((1024, 1024), Image.Resampling.LANCZOS).convert("RGB").save(appiconset / "AppIcon.png")
    (appiconset / "Contents.json").write_text(
        """{
  "images" : [
    {
      "filename" : "AppIcon.png",
      "idiom" : "universal",
      "platform" : "ios",
      "size" : "1024x1024"
    }
  ],
  "info" : {
    "author" : "xcode",
    "version" : 1
  }
}
""",
        encoding="utf-8",
    )
    flatpak_icons = ROOT / "composeApp/flatpak/icons"
    for size in (128, 256, 512):
        icon_dir = flatpak_icons / str(size)
        icon_dir.mkdir(parents=True, exist_ok=True)
        master.resize((size, size), Image.Resampling.LANCZOS).convert("RGB").save(
            icon_dir / "com.joetr.basil.png",
        )

    webgen = ROOT / "branding/generated/web"
    webgen.mkdir(parents=True, exist_ok=True)
    master.resize((192, 192), Image.Resampling.LANCZOS).convert("RGB").save(webgen / "favicon-192.png")
    print("Generated platform icons from branding/basil_leaf_source.png (safe-zone padded)")


if __name__ == "__main__":
    main()
