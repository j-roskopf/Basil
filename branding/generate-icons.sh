#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/branding/basil.png"
FG_SRC="$ROOT/branding/basil_foreground.png"

if [[ ! -f "$SRC" ]]; then
  echo "Missing $SRC" >&2
  exit 1
fi

# Prefer the Python generator (safe-zone padding + adaptive foregrounds).
if command -v python3 >/dev/null 2>&1; then
  python3 "$ROOT/branding/generate_icons.py"
  exit 0
fi

mkdir -p "$ROOT/branding/generated/android" "$ROOT/branding/generated/ios" "$ROOT/branding/generated/web"

# Fallback: sips resize only (no adaptive safe-zone padding).
for size in 48:mdpi 72:hdpi 96:xhdpi 144:xxhdpi 192:xxxhdpi; do
  IFS=: read -r px folder <<<"$size"
  out="$ROOT/androidApp/src/main/res/mipmap-$folder"
  mkdir -p "$out"
  sips -z "$px" "$px" "$SRC" --out "$out/ic_launcher.png" >/dev/null
  cp "$out/ic_launcher.png" "$out/ic_launcher_round.png"
  if [[ -f "$FG_SRC" ]]; then
    case "$folder" in
      mdpi) fgp=108 ;;
      hdpi) fgp=162 ;;
      xhdpi) fgp=216 ;;
      xxhdpi) fgp=324 ;;
      xxxhdpi) fgp=432 ;;
    esac
    sips -z "$fgp" "$fgp" "$FG_SRC" --out "$out/ic_launcher_foreground.png" >/dev/null
  fi
done

sips -z 192 192 "$SRC" --out "$ROOT/composeApp/src/wasmJsMain/resources/icons/icon-192.png" >/dev/null
sips -z 512 512 "$SRC" --out "$ROOT/composeApp/src/wasmJsMain/resources/icons/icon-512.png" >/dev/null
cp "$ROOT/composeApp/src/wasmJsMain/resources/icons/icon-192.png" "$ROOT/branding/generated/web/favicon-192.png"
sips -z 32 32 "$SRC" --out "$ROOT/composeApp/src/wasmJsMain/resources/favicon.png" >/dev/null
sips -z 1024 1024 "$SRC" --out "$ROOT/branding/generated/ios/AppIcon-1024.png" >/dev/null
mkdir -p "$ROOT/composeApp/src/desktopMain/resources"
sips -z 256 256 "$SRC" --out "$ROOT/composeApp/src/desktopMain/resources/icon.png" >/dev/null

echo "Generated platform icons from branding/basil.png"
