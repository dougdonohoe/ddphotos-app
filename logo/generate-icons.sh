#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
OUT="$SCRIPT_DIR/icons"

if ! command -v inkscape &>/dev/null; then
  echo "inkscape not found — install with: brew install inkscape"
  exit 1
fi

if ! command -v magick &>/dev/null; then
  echo "magick not found — install with: brew install imagemagick"
  exit 1
fi

svg_to_png() {
  local svg="$1" width="$2" out="$3"
  inkscape --export-type=png --export-width="$width" --export-filename="$out" "$svg" 2>/dev/null
}

mkdir -p "$OUT"

generate() {
  local svg="$1"       # e.g. ddphotos-icon.svg
  local name="$2"      # e.g. ddphotos-icon
  local shortname="$3" # e.g. icon/logo
  local dir="$OUT/$name"
  mkdir -p "$dir"

  echo "Generating PNGs for $svg ..."
  for size in 16 20 32 48 64 128 180 192 256 512 1024; do
    local tmp="$dir/${shortname}_${size}x${size}_tmp.png"
    # Scale by width, preserving aspect ratio
    svg_to_png "$SCRIPT_DIR/$svg" "$size" "$tmp"
    # Pad height to make a square with transparent background
    magick "$tmp" -gravity center -background none -extent "${size}x${size}" "$dir/${shortname}_${size}x${size}.png"
    rm "$tmp"
  done

  cp "$dir/${shortname}_32x32.png"    "$dir/${shortname}_16x16@2x.png"
  cp "$dir/${shortname}_64x64.png"    "$dir/${shortname}_32x32@2x.png"
  cp "$dir/${shortname}_256x256.png"  "$dir/${shortname}_128x128@2x.png"
  cp "$dir/${shortname}_512x512.png"  "$dir/${shortname}_256x256@2x.png"
  cp "$dir/${shortname}_1024x1024.png" "$dir/${shortname}_512x512@2x.png"

  echo "Assembling $name.icns ..."
  local iconset="$dir/${name}.iconset"
  rm -rf "$iconset"
  mkdir -p "$iconset"
  # iconutil requires the files inside the .iconset to be named exactly
  # icon_<size>.png — the "icon_" prefix is mandated by Apple's tooling and
  # cannot be customized. (PNG sources keep the $shortname prefix.)
  cp "$dir/${shortname}_16x16.png"      "$iconset/icon_16x16.png"
  cp "$dir/${shortname}_16x16@2x.png"   "$iconset/icon_16x16@2x.png"
  cp "$dir/${shortname}_32x32.png"      "$iconset/icon_32x32.png"
  cp "$dir/${shortname}_32x32@2x.png"   "$iconset/icon_32x32@2x.png"
  cp "$dir/${shortname}_128x128.png"    "$iconset/icon_128x128.png"
  cp "$dir/${shortname}_128x128@2x.png" "$iconset/icon_128x128@2x.png"
  cp "$dir/${shortname}_256x256.png"    "$iconset/icon_256x256.png"
  cp "$dir/${shortname}_256x256@2x.png" "$iconset/icon_256x256@2x.png"
  cp "$dir/${shortname}_512x512.png"    "$iconset/icon_512x512.png"
  cp "$dir/${shortname}_512x512@2x.png" "$iconset/icon_512x512@2x.png"
  iconutil -c icns "$iconset" -o "$dir/${name}.icns"
  rm -rf "$iconset"
  echo "  -> $dir/${name}.icns"

  echo "Assembling $name.ico ..."
  magick "$dir/${shortname}_16x16.png" "$dir/${shortname}_32x32.png" "$dir/${shortname}_48x48.png" "$dir/${shortname}_256x256.png" "$dir/${name}.ico"
  echo "  -> $dir/${name}.ico"
}

generate "ddphotos-icon.svg" "ddphotos-icon" "icon"
generate "ddphotos-logo.svg" "ddphotos-logo" "logo"

echo "Done. Output in $OUT"
