#!/usr/bin/env bash
set -euo pipefail

# The logo/ dir is the source of truth for all logos/icons. This script copies
# the generated icons (see generate-icons.sh) out to the places they are used.
#
# Run generate-icons.sh first if the SVGs changed.

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ICON_SRC="$SCRIPT_DIR/icons/ddphotos-icon"
LOGO_SRC="$SCRIPT_DIR/icons/ddphotos-logo"

cp_v() {
  echo "  $1 -> $2"
  cp "$1" "$2"
}

# --- ddphotos website favicons ----------------------------------------------
WEB="$HOME/work/ddphotos/web/static"
echo "Updating website favicons in $WEB ..."
cp_v "$ICON_SRC/ddphotos-icon.ico" "$WEB/favicon.ico"
cp_v "$ICON_SRC/icon_192x192.png"  "$WEB/favicon-192.png"
cp_v "$ICON_SRC/icon_32x32.png"    "$WEB/favicon-32.png"
cp_v "$ICON_SRC/icon_512x512.png"  "$WEB/favicon-512.png"
cp_v "$ICON_SRC/icon_180x180.png"  "$WEB/apple-touch-icon.png"

# --- ddphotos sample site logo ----------------------------------------------
# Shown in the hero (site_title_html in sample/config/albums.yaml). 512px covers
# its ~150px display size at 3x.
SAMPLE_STATIC="$HOME/work/ddphotos/sample/config/static"
echo "Updating sample site logo in $SAMPLE_STATIC ..."
cp_v "$LOGO_SRC/logo_512x512.png" "$SAMPLE_STATIC/ddphotos-logo.png"

# --- admin app resources ----------------------------------------------------
# Refresh every icon_*/logo_*.png already present in the app's images dir from
# the source of truth (matched by filename, by size).
IMAGES="$SCRIPT_DIR/../code/photos/src/main/resources/config/ddphotos/images"
echo "Updating app icons in $IMAGES ..."
for dest in "$IMAGES"/icon_*.png "$IMAGES"/logo_*.png; do
  [ -e "$dest" ] || continue
  name="$(basename "$dest")"
  case "$name" in
    icon_*) src="$ICON_SRC/$name" ;;
    logo_*) src="$LOGO_SRC/$name" ;;
  esac
  if [ -e "$src" ]; then
    cp_v "$src" "$dest"
  else
    echo "  WARNING: no source for $name ($src) - skipped" >&2
  fi
done

echo "Done."
