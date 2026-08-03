#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")" && pwd)"
repo_root="$(cd "${root}/../.." && pwd)"

source_icon="${repo_root}/branding/basil.png"
icon_512="${repo_root}/composeApp/src/wasmJsMain/resources/icons/icon-512.png"
icon_256="${repo_root}/composeApp/src/desktopMain/resources/icon.png"

if [[ ! -f "${source_icon}" ]]; then
  if command -v python3 >/dev/null 2>&1 && [[ -f "${repo_root}/branding/generate_icons.py" ]]; then
    python3 "${repo_root}/branding/generate_icons.py"
  else
    echo "Missing branding icon: ${source_icon}" >&2
    exit 1
  fi
fi

if [[ ! -f "${icon_512}" || ! -f "${icon_256}" ]]; then
  "${repo_root}/branding/generate-icons.sh"
fi

resize_icon() {
  local input="$1"
  local output="$2"
  local size="$3"

  if command -v magick >/dev/null 2>&1; then
    magick "${input}" -resize "${size}x${size}" "${output}"
  elif command -v convert >/dev/null 2>&1; then
    convert "${input}" -resize "${size}x${size}" "${output}"
  elif command -v sips >/dev/null 2>&1; then
    sips -z "${size}" "${size}" "${input}" --out "${output}" >/dev/null
  else
    echo "Install ImageMagick or run on macOS with sips to generate Flatpak icons." >&2
    exit 1
  fi
}

mkdir -p "${root}/icons/128" "${root}/icons/256" "${root}/icons/512"
cp "${icon_512}" "${root}/icons/512/com.joetr.basil.png"
cp "${icon_256}" "${root}/icons/256/com.joetr.basil.png"
resize_icon "${source_icon}" "${root}/icons/128/com.joetr.basil.png" 128
