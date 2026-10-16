#!/bin/bash
#
# Copies the PhotoPrism API fixtures the app's tests use from the ddphotos repo, where
# cmd/photoprism-record records them from a real PhotoPrism instance.  Re-run after re-recording there.
#
#   sync-photoprism-fixtures.sh [ddphotos-repo]      (default: $DDPHOTOS_REPO, else ~/work/ddphotos)
#
set -euo pipefail

SRC_REPO="${1:-${DDPHOTOS_REPO:-$HOME/work/ddphotos}}"
SRC="$SRC_REPO/pkg/photogen/testdata/photoprism"
DEST="$(cd "$(dirname "$0")/../.." && pwd)/code/photos/src/test/resources/testdata/photoprism"

if [ ! -d "$SRC" ]; then
    echo "No fixtures at $SRC - pass the ddphotos repo as the first argument." >&2
    exit 1
fi

mkdir -p "$DEST"
for f in albums.json session.json; do
    cp "$SRC/$f" "$DEST/$f"
    echo "copied $f"
done
echo "Fixtures are in $DEST"
