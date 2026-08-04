#!/usr/bin/perl
#
# Tests for the release-notes and README logic in buildall.pl.
#
# buildall.pl can't be 'require'd, since loading it runs a build.  So we slice the
# subroutine definitions out of the source text and eval just those, which lets us call
# them directly with our own globals.
#
# NOTE: this depends on the "##### Sub routines" banner in buildall.pl staying put, and on
# the subs being driven by globals ($DEVDIR, $INSTALLERDIR, $VERSION_FILE, ...).  If either
# changes, fix this script.
#
# Not covered here: checkNotBehind(), confirm() and pullOriginalDir(), which talk to the
# network and to stdin and call exit() directly - use 'buildall -full -github-dryrun' for
# those.  pullOriginalDir()'s decision logic is covered via pullPlan().
#
# Everything that needs a local build - or 'gh', which releaseNotes() uses for the
# changelog link - sits behind $haveBuild, so a bare checkout (CI, or just a tree that has
# never been built) skips those and still runs the rest offline.
#
# Usage:  test-buildall.pl
#

use File::Temp qw(tempdir);

$SCRIPTDIR = `cd \`dirname $0\` && pwd`;
chop $SCRIPTDIR;
$DEVDIR = `cd $SCRIPTDIR && git rev-parse --show-toplevel`;
chop $DEVDIR;

$BUILDALL = "$SCRIPTDIR/buildall.pl";
$WHATSNEW = "$DEVDIR/code/photos/src/main/resources/config/ddphotos/help/whatsnew.html";
$INSTALLERDIR = "$DEVDIR/installer/builds";

#
# load @PLATFORMS and the subs out of buildall.pl
#
$src = slurpFile($BUILDALL);
($platforms) = $src =~ /(\@PLATFORMS = .*?;)/s   or die "No \@PLATFORMS in $BUILDALL";
($subs)      = $src =~ /(##### Sub routines.*)/s or die "No sub routines banner in $BUILDALL";
eval "$platforms\n$subs\n1" || die "Couldn't load subs from $BUILDALL: $@";

# globals the subs expect
$GITREPO = "ddphotos-app";
$PRODUCT = "ddphotos";
$indent = "    ";

#
# the version under development is the newest whatsnew.html entry
#
$html = slurpFile($WHATSNEW);
@versions = ($html =~ /Version\s+(\S+)\s+-\s+[^<]+<\/span>/g);
die "No versions found in $WHATSNEW" if (!@versions);
$VERSION = $versions[0];
$VERSION_FILE = $VERSION =~ s/\./_/gr;

print("Testing $BUILDALL\n");
print("  repo:     $DEVDIR\n");
print("  version:  $VERSION ($VERSION_FILE)\n");
print("  builds:   $INSTALLERDIR\n\n");

# Some checks need a local build of *this* version, which there often isn't: -dev builds
# are occasional and CI never has one.  md5sums.txt is the last thing an installer build
# writes, so it is the sentinel - matched the same way releaseNotes() does, since a bare
# "1_0_0" also appears in a left-over "1_0_0b7" build.
$md5file = "$INSTALLERDIR/md5sums.txt";
$haveBuild = (-f $md5file) && slurpFile($md5file) =~ /_\Q$VERSION_FILE\E\./;
$noBuild = "no local $VERSION build in $INSTALLERDIR - run 'buildall -dev' first";

#####
##### Tests
#####

# --- naming helpers
ok("installerName is platform qualified", installerName("mac", "dmg") eq "ddphotos_mac_${VERSION_FILE}.dmg",
   "got '" . installerName("mac", "dmg") . "'");
@names = split / /, installerNames();
ok("installerNames covers all 3 platforms", scalar(@names) == 3, "got '" . installerNames() . "'");
ok("releaseNotesName", releaseNotesName() eq "release_notes_${VERSION_FILE}.md",
   "got '" . releaseNotesName() . "'");

# --- releaseNotes
if (!$haveBuild)
{
	skip("releaseNotes", $noBuild);
}
else
{
	$notes = eval { releaseNotes($VERSION) };
	ok("releaseNotes runs", defined $notes, $@);
	ok("releaseNotes has version header", $notes =~ /^## Version \Q$VERSION\E - \S/);
	ok("releaseNotes has bullets", scalar(() = $notes =~ /^- /gm) > 0);
	ok("releaseNotes has no leftover html", $notes !~ /<[a-z\/]/);
	ok("releaseNotes ends with md5 block", $notes =~ /\nMD5 \(\Q$PRODUCT\E_\w+_\Q$VERSION_FILE\E\.\w+\) = /);

	# compare against the published release, if there is one
	$published = `gh release view $VERSION --json body --jq .body 2>/dev/null | tr -d '\\r'`;
	if (!$published)
	{
		skip("releaseNotes matches published notes", "$VERSION is not released yet");
	}
	else
	{
		# md5s differ between a dev build and what was published, so compare the rest
		($mine)  = $notes     =~ /^(.*?)\n\nMD5 /s;
		($their) = $published =~ /^(.*?)\n\nMD5 /s;
		ok("releaseNotes matches published notes", $mine eq $their,
		   "generated:\n$mine\n\npublished:\n$their");
	}
}

# --- releaseNotes error handling
ok("releaseNotes dies on unknown version",
   !eval { releaseNotes("9.9.9z9"); 1 } && $@ =~ /No 'Version/, $@);

if ($haveBuild && @versions > 1)
{
	# an older version has a whatsnew entry but won't be in this build's md5sums.txt.
	# Also guards the substring trap: "1_0_0" must not be satisfied by a "1_0_0b7" build.
	$old = $versions[1];
	$saved = $VERSION_FILE;
	$VERSION_FILE = $old =~ s/\./_/gr;
	ok("releaseNotes dies on stale md5sums", !eval { releaseNotes($old); 1 } && $@ =~ /stale/, $@);
	$VERSION_FILE = $saved;
}

# --- checkInstallers
if (!$haveBuild)
{
	skip("checkInstallers", $noBuild);
}
else
{
	ok("checkInstallers passes for built version", eval { checkInstallers(); 1 }, $@);
}
{
	$saved = $VERSION_FILE;
	$VERSION_FILE = "9_9_9z9";
	ok("checkInstallers dies when missing", !eval { checkInstallers(); 1 } && $@ =~ /not found/, $@);
	$VERSION_FILE = $saved;
}

# --- updateReadme (against a throwaway copy of the real README)
$tmp = tempdir(CLEANUP => 1);
$realReadme = slurpFile("$DEVDIR/README.md");

{
	local $DEVDIR = $tmp;
	local $VERSION_FILE = "9_9_9z9";

	writeFile("$tmp/README.md", $realReadme);
	eval { updateReadme("9.9.9z9", 0) };
	$updated = slurpFile("$tmp/README.md");

	ok("updateReadme rewrites the version", $updated =~ /Download the latest release, \*\*9\.9\.9z9\*\*:/, $@);
	ok("updateReadme links all 3 platforms",
	   scalar(() = $updated =~ /releases\/download\/9\.9\.9z9\/ddphotos_\w+_9_9_9z9\.\w+\)/g) == 3);
	ok("updateReadme keeps the markers", $updated =~ /installers:begin/ && $updated =~ /installers:end/);
	ok("updateReadme leaves the rest alone",
	   linesOutsideMarkers($updated) eq linesOutsideMarkers($realReadme));

	# dryrun must not write
	writeFile("$tmp/README.md", $realReadme);
	eval { updateReadme("9.9.9z9", 1) };
	ok("updateReadme dryrun writes nothing", slurpFile("$tmp/README.md") eq $realReadme, $@);

	# missing markers must be caught
	writeFile("$tmp/README.md", "no markers here\n");
	ok("updateReadme dies without markers",
	   !eval { updateReadme("9.9.9z9", 1); 1 } && $@ =~ /Could not find the installers/, $@);
}

# --- pullPlan (the -github post-release pull back into the dev tree)
{
	# not a repo at all
	local $ORIGDIR = tempdir(CLEANUP => 1);
	($ok, $detail) = pullPlan();
	ok("pullPlan skips a non-repo", !$ok && $detail =~ /not a git repository/, $detail);
}

{
	# run from the build clone, so there is nothing to pull back
	local $ORIGDIR = $DEVDIR;
	($ok, $detail) = pullPlan();
	ok("pullPlan skips the build clone", !$ok && $detail =~ /build clone/, $detail);
}

{
	# a throwaway repo standing in for the dev tree, with one commit so HEAD resolves
	$repo = tempdir(CLEANUP => 1);
	$git = "git -C $repo -c user.email=t\@t -c user.name=t";
	`git init -q -b notmain $repo 2>&1`;
	`$git commit -q --allow-empty -m init 2>&1`;
	mkdir("$repo/sub");

	# ORIGDIR is a subdir, to prove the plan reports the work tree root
	local $ORIGDIR = "$repo/sub";

	($ok, $detail) = pullPlan();
	ok("pullPlan skips a non-main branch", !$ok && $detail =~ /is on 'notmain', not main/, $detail);

	`$git branch -m notmain main 2>&1`;
	($ok, $detail) = pullPlan();
	ok("pullPlan pulls a dev tree on main", $ok, $detail);
	ok("pullPlan reports the work tree root", $detail eq gitTopLevel($repo), $detail);
}

#####
##### Results
#####
print("\n$PASSED passed");
print(", $SKIPPED skipped") if ($SKIPPED);
print(", $FAILED FAILED") if ($FAILED);
print("\n\n");
exit($FAILED ? 1 : 0);

#####
##### Helpers
#####

# record a passing or failing test
sub ok
{
	my($name, $pass, $detail) = @_;

	if ($pass)
	{
		$PASSED++;
		print("  ok    $name\n");
	}
	else
	{
		$FAILED++;
		print("  FAIL  $name\n");
		$detail =~ s/^/          /gm if ($detail);
		print("$detail\n") if ($detail);
	}
}

# record a test we can't run here
sub skip
{
	my($name, $why) = @_;

	$SKIPPED++;
	print("  skip  $name ($why)\n");
}

# everything except the generated installer block, to prove we only touched that
sub linesOutsideMarkers
{
	my($text) = @_;

	$text =~ s/<!-- installers:begin.*?installers:end -->//s;

	return $text;
}

# slurp a file (buildall.pl's own slurp() needs its globals, and we use this before
# those are set up)
sub slurpFile
{
	my($file) = @_;

	open (IN, "$file") || die "Couldn't open $file";
	local $/;
	my $contents = <IN>;
	close(IN);

	return $contents;
}
