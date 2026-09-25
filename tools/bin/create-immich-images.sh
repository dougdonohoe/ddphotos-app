#!/usr/bin/env bash
#
# create-immich-images.sh
#
# Build the screenshots used in images/immich/README.md from the full-size
# originals in images/immich/orig/.
#
# Each original is scaled by SCALE, then given the same thin gray border and
# soft drop shadow that the app adds to its own screenshots (see
# GuiUtils.printToImage and GuiUtils.withDropShadow). The results are written
# to images/immich/ under the same file names.
#
# Requires ImageMagick 7 (the `magick` command).

set -euo pipefail

# ── Configuration ───────────────────────────────────────────────────────────

# Resize factor applied to every original, as a percentage.
SCALE=35%

# Border and shadow tuning, in pixels of the scaled image. These match the
# constants in GuiUtils.java.
BORDER_COLOR="#B0B0B0"
SHADOW_MARGIN=24
SHADOW_BLUR=10
SHADOW_OFFSET_X=0
SHADOW_OFFSET_Y=6
SHADOW_ALPHA=90         # 0-255

# ── Paths ───────────────────────────────────────────────────────────────────

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
OUT_DIR="$REPO_ROOT/images/immich"
ORIG_DIR="$OUT_DIR/orig"

# ── Checks ──────────────────────────────────────────────────────────────────

if ! command -v magick >/dev/null 2>&1; then
    echo "error: ImageMagick 7 (the 'magick' command) is required but was not found." >&2
    exit 1
fi

shopt -s nullglob
originals=("$ORIG_DIR"/*.png)
if (( ${#originals[@]} == 0 )); then
    echo "error: no .png files found in $ORIG_DIR" >&2
    exit 1
fi

# ── Build ───────────────────────────────────────────────────────────────────

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

SHADOW_COLOR="rgba(0,0,0,$(awk "BEGIN { printf \"%.4f\", $SHADOW_ALPHA / 255 }"))"

for src in "${originals[@]}"; do
    name="$(basename "$src")"
    content="$TMP_DIR/$name"

    # Scale, then draw the border over the outermost pixels (as the Java code
    # does) rather than adding to the image size.
    magick "$src" -resize "$SCALE" -shave 1x1 -bordercolor "$BORDER_COLOR" -border 1 "$content"

    read -r cw ch < <(magick identify -format "%w %h\n" "$content")
    w=$(( cw + SHADOW_MARGIN * 2 ))
    h=$(( ch + SHADOW_MARGIN * 2 ))
    x1=$(( SHADOW_MARGIN + SHADOW_OFFSET_X ))
    y1=$(( SHADOW_MARGIN + SHADOW_OFFSET_Y ))

    # White canvas, then a translucent black rectangle box-blurred into a
    # shadow, then the bordered shot on top.
    magick -size "${w}x${h}" xc:white \
        \( -size "${w}x${h}" xc:none -fill "$SHADOW_COLOR" \
           -draw "rectangle $x1,$y1 $(( x1 + cw - 1 )),$(( y1 + ch - 1 ))" \
           -define convolve:scale='!' -morphology Convolve "Square:$SHADOW_BLUR" \) \
        -composite \
        "$content" -geometry "+$SHADOW_MARGIN+$SHADOW_MARGIN" -composite \
        -strip "$OUT_DIR/$name"

    echo "$name: ${w}x${h}"
done
