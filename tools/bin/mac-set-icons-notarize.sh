#!/usr/bin/env bash
#
# Script to set dmg icon and volume icon on Install4j generated .dmg
# since we can't do it through the tool.
#

set -e

# Verify we have a version
VERSION=$1
if [[ -z "$VERSION" ]]; then
  echo "mac-set-icons-notarize.sh [version]"
  exit 1
fi

SCRIPTDIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPTDIR"
DDHOME="$(git rev-parse --show-toplevel)"

SRC="$DDHOME/installer/builds/ddphotos${VERSION}.dmg"
BAK="/tmp/ddphotos${VERSION}.bak.dmg"
DST_RW="$DDHOME/installer/builds/ddphotos${VERSION}_rw.dmg"
DST_ALT="$DDHOME/installer/builds/ddphotos${VERSION}_alt.dmg"
DST_MNT="/Volumes/dd_photos_dst"

if [[ ! -f "$SRC" ]]; then
  echo "$SRC not found."
  exit 1
fi

# make read/write copy of installer
rm -f "$DST_RW"
hdiutil convert "$SRC" -format UDRW -o "$DST_RW"

# Add 20000 to the size
input=$(hdiutil resize "$DST_RW" | tail -1)
size=$(echo "$input" | awk '{print $2}')
newsize=$((size + 50000))
echo "Increasing size from $size to $newsize..."
hdiutil resize -sectors $newsize "$DST_RW"

# Mount rw, unmounting if still mounted
if [[ -d "$DST_MNT" ]]; then
  hdiutil detach "$DST_MNT"
fi
hdiutil attach "$DST_RW" -mountpoint "$DST_MNT"

# Copy icon and set finder data
cp -p "${DDHOME}/logo/icons/ddphotos-logo/ddphotos-logo.icns" "${DST_MNT}/.VolumeIcon.icns"
SetFile -c icnC "${DST_MNT}/.VolumeIcon.icns"
SetFile -a C "${DST_MNT}"

# Output directory structure
echo
echo "Volume contents:"
ls -la "$DST_MNT"
echo

# Unmount
hdiutil detach "$DST_MNT"

# convert back to ro and remove rw
rm -f "$DST_ALT"
hdiutil convert "$DST_RW" -format UDBZ -o "$DST_ALT"
rm -rf "$DST_RW"

# attach icon to new dmg  (this apparently only works on local mac; doesn't stick after download,
# but keeping around since I like it locally and it was a pain to figure out)
TMP_ICN=/tmp/icons_copy.icns
TMP_RSRC=/tmp/icons_copy.rsrc
cp "${DDHOME}/logo/icons/ddphotos-logo/ddphotos-logo.icns" "$TMP_ICN"
sips -i "$TMP_ICN"
DeRez -only icns "$TMP_ICN" > "$TMP_RSRC"
Rez -append "$TMP_RSRC" -o "$DST_ALT"
SetFile -a C "$DST_ALT"

# Copy new one back over original, backing up original to /tmp
mv -v "$SRC" "$BAK"
mv -v "$DST_ALT" "$SRC"

# Sign and notarize new one
~/work/donohoe/installer/mac-sign-notarize.sh "$SRC"
