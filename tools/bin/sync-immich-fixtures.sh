#!/bin/bash
#
# Copies the Immich API fixtures the app's tests use from the ddphotos repo, where
# cmd/immich-record records them from a real Immich server.  Re-run after re-recording there.
#
#   sync-immich-fixtures.sh [ddphotos-repo]      (default: $DDPHOTOS_REPO, else ~/work/ddphotos)
#
set -euo pipefail

SRC_REPO="${1:-${DDPHOTOS_REPO:-$HOME/work/ddphotos}}"
SRC="$SRC_REPO/pkg/photogen/testdata/immich"
DEST="$(cd "$(dirname "$0")/../.." && pwd)/code/photos/src/test/resources/testdata/immich"

if [ ! -d "$SRC" ]; then
    echo "No fixtures at $SRC - pass the ddphotos repo as the first argument." >&2
    exit 1
fi

mkdir -p "$DEST"
for f in album.json albums.json api-key-me.json; do
    cp "$SRC/$f" "$DEST/$f"
    echo "copied $f"
done
echo "Fixtures are in $DEST"
