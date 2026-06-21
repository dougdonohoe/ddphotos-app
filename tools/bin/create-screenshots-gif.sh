#!/usr/bin/env bash
#
# create-screenshots-gif.sh
#
# Build an animated GIF from the app screenshots in images/screenshots/.
#
# The frames are listed explicitly below (FRAMES) so the play order is whatever
# you set here - not alphabetical. Each frame shows for DELAY_MS. The GIF loops
# LOOP_COUNT times and then halts; it rests on STOP_ON (shown for END_HOLD_MS),
# which is appended as the final frame so the animation ends on a deliberate shot.
#
# Requires ImageMagick 7 (the `magick` command).

set -euo pipefail

# ── Configuration ───────────────────────────────────────────────────────────

# Per-frame display time, in milliseconds.
DELAY_MS=1000

# How long the final resting frame (STOP_ON) is held, in milliseconds.
END_HOLD_MS=2000

# Number of times the animation plays before stopping (0 = loop forever).
LOOP_COUNT=2

# Frame the GIF should come to rest on. Must be one of FRAMES (or any file in the
# screenshots dir). Leave empty to just rest on the last FRAMES entry.
STOP_ON="config.png"

# Frames in the desired play order (filenames within the screenshots dir).
FRAMES=(
    wizard-welcome.png
    wizard-docker.png
    wizard-script.png
    wizard-choice.png
    wizard-init.png
    config.png
    photogen.png
    run.png
    build.png
    serve.png
    deploy.png
    export.png
    wrangler.png
    surge.png
    upgrade.png
)

# ── Paths ───────────────────────────────────────────────────────────────────

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
SHOTS_DIR="$REPO_ROOT/images/screenshots"
OUTPUT="${1:-$REPO_ROOT/images/screenshots.gif}"

# ── Checks ──────────────────────────────────────────────────────────────────

if ! command -v magick >/dev/null 2>&1; then
    echo "error: ImageMagick 7 (the 'magick' command) is required but was not found." >&2
    echo "       Install with: brew install imagemagick" >&2
    exit 1
fi

if [ ${#FRAMES[@]} -eq 0 ]; then
    echo "error: no frames listed in FRAMES." >&2
    exit 1
fi

# ImageMagick delays are in centiseconds (1/100s); round from milliseconds.
delay_cs=$(( (DELAY_MS + 5) / 10 ))
end_cs=$(( (END_HOLD_MS + 5) / 10 ))

# ── Build the frame argument list ───────────────────────────────────────────

# Collect the ordered list of frame paths (FRAMES, plus STOP_ON as the resting frame).
paths=()
delays=()
for frame in "${FRAMES[@]}"; do
    path="$SHOTS_DIR/$frame"
    if [ ! -f "$path" ]; then
        echo "error: frame not found: $path" >&2
        exit 1
    fi
    paths+=( "$path" )
    delays+=( "$delay_cs" )
done

# Append the resting frame so the GIF stops on it (held for END_HOLD_MS).
if [ -n "$STOP_ON" ]; then
    stop_path="$SHOTS_DIR/$STOP_ON"
    if [ ! -f "$stop_path" ]; then
        echo "error: STOP_ON frame not found: $stop_path" >&2
        exit 1
    fi
    paths+=( "$stop_path" )
    delays+=( "$end_cs" )
fi

# Screenshots differ in size; find the largest so every frame can be matted onto a
# common canvas. Mixed-size frames would otherwise let earlier frames show through
# around smaller ones once inter-frame optimization kicks in.
max_w=0
max_h=0
while read -r w h; do
    [ "$w" -gt "$max_w" ] && max_w=$w
    [ "$h" -gt "$max_h" ] && max_h=$h
done < <(magick identify -format "%w %h\n" "${paths[@]}")

args=()
for i in "${!paths[@]}"; do
    args+=( -delay "${delays[$i]}" "${paths[$i]}" )
done

# ── Render ──────────────────────────────────────────────────────────────────

# -extent centers each frame on a uniform white canvas (matching the screenshots'
# own background).
#
# The UI is mostly smooth grays, which GIF's 256-color palette would otherwise
# dither into visible speckle. A single global palette built from every frame
# (-colors 256 before splitting into layers) plus -dither None gives flat, clean
# grays and avoids per-frame palettes drifting between frames. -layers optimize
# then shrinks the file via inter-frame diffs.
magick -loop "$LOOP_COUNT" "${args[@]}" \
    -background white -gravity center -extent "${max_w}x${max_h}" \
    -dither None -colors 256 \
    -layers optimize "$OUTPUT"

echo "Wrote $OUTPUT"
echo "  frames:   ${#FRAMES[@]} (+1 resting on ${STOP_ON:-<last>})"
echo "  delay:    ${DELAY_MS}ms per frame, ${END_HOLD_MS}ms on final"
echo "  loops:    $([ "$LOOP_COUNT" -eq 0 ] && echo forever || echo "$LOOP_COUNT")"
