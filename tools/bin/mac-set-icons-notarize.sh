#!/usr/bin/env bash
#
# Script to set dmg icon and volume icon on Install4j generated .dmg
# since we can't do it through the tool.
#

set -e

# Verify we have a dmg to work on
DMG=$1
if [[ -z "$DMG" ]]; then
  echo "mac-set-icons-notarize.sh [dmg-file-name]"
  exit 1
fi
BASE="${DMG%.dmg}"

SCRIPTDIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPTDIR"
DDHOME="$(git rev-parse --show-toplevel)"

SRC="$DDHOME/installer/builds/$DMG"
BAK="/tmp/${BASE}.bak.dmg"
DST_RW="$DDHOME/installer/builds/${BASE}_rw.dmg"
DST_ALT="$DDHOME/installer/builds/${BASE}_alt.dmg"
DST_MNT="/Volumes/dd_photos_dst"

if [[ ! -f "$SRC" ]]; then
  echo "$SRC not found."
  exit 1
fi

# scratch space for this run, cleaned up however we exit
SCRATCH="$(mktemp -d)"
trap 'rm -rf "$SCRATCH"' EXIT

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
TMP_ICN="$SCRATCH/icons_copy.icns"
TMP_RSRC="$SCRATCH/icons_copy.rsrc"
cp "${DDHOME}/logo/icons/ddphotos-logo/ddphotos-logo.icns" "$TMP_ICN"
sips -i "$TMP_ICN"
DeRez -only icns "$TMP_ICN" > "$TMP_RSRC"
Rez -append "$TMP_RSRC" -o "$DST_ALT"
SetFile -a C "$DST_ALT"

# Copy new one back over original, backing up original to /tmp.  The backup is
# deleted once notarization succeeds; if anything below fails it is left in place
# so the install4j dmg can be recovered.
mv -v "$SRC" "$BAK"
mv -v "$DST_ALT" "$SRC"

# Sign and notarize new one
~/work/donohoe/installer/mac-sign-notarize.sh "$SRC"

# notarized dmg is good, so the backup has no further use
rm -vf "$BAK"
