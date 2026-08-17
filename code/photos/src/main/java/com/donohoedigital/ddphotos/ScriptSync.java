package com.donohoedigital.ddphotos;

import com.donohoedigital.app.config.AppConfigUtils;
import com.donohoedigital.ddphotos.config.AtomicWrite;
import com.donohoedigital.ddphotos.config.FileErrors;
import com.donohoedigital.ddphotos.config.Site;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Keeps each site's own copy of the {@code ddphotos} wrapper script in step with the app's.
 *
 * <p>{@code ddphotos init} drops a copy of the script in the site directory so the user can run
 * {@code ./ddphotos <command>} from there.  The app runs its own copy from
 * {@link PhotosUtils#scriptPath()} instead, and that is the only one {@code ddphotos upgrade}
 * (the Upgrade tab) rewrites - the image replaces the script it finds at its
 * {@code /ddphotos-script-dir} mount, which is the app's bin directory.  So the site's copy is
 * left behind on whatever version was current when the site was created, still pinned to that
 * release's image tag.  Running it then quietly uses the old image, and the container warns that
 * the local script no longer matches.  So the Upgrade tab runs this at the end of every run of
 * {@code ddphotos upgrade}, whether or not there turned out to be an upgrade to do.
 *
 * <p>The script carries no version of its own, so - like the image's own check - this compares
 * byte for byte and replaces the whole file when it differs.
 */
public final class ScriptSync {

    private static final Logger logger = LogManager.getLogger(ScriptSync.class);

    /** The bash wrapper, and the Windows launcher that sits beside it on a Git Bash install. */
    private static final String SCRIPT = "ddphotos";
    private static final String LAUNCHER = "ddphotos.cmd";

    private ScriptSync() {}

    /**
     * Refreshes the script in every site directory that already has one.  Best effort: a site on
     * an unplugged drive or in a read-only directory is logged and skipped, never allowed to fail
     * the run this is called from (see {@code UpgradeRunner.afterRun}).
     */
    public static void syncSites(List<Site> sites) {
        syncSites(AppConfigUtils.getBinDir().toPath(), sites);
    }

    /** Takes the app's bin directory rather than asking for it, so tests can point elsewhere. */
    static void syncSites(Path binDir, List<Site> sites) {
        for (Site site : sites) {
            String dirPath = site.getDirPath();
            if (dirPath == null || dirPath.isBlank()) continue;

            Path siteDir = Path.of(dirPath);
            if (!Files.isDirectory(siteDir)) continue;

            syncOne(binDir.resolve(SCRIPT), siteDir.resolve(SCRIPT));
            syncOne(binDir.resolve(LAUNCHER), siteDir.resolve(LAUNCHER));
        }
    }

    /**
     * Replaces {@code target} with {@code source} when the two differ, and reports whether it did.
     *
     * <p>A target that isn't there already is left alone rather than created.  A site can be added
     * by pointing the Add Site dialog at a directory that was never initialized from the app, and
     * {@code ddphotos.cmd} only exists where the user ran {@code ddphotos init} from Git Bash
     * themselves - neither should gain a file it never had.  This is what the image does with the
     * launcher on an upgrade.
     */
    static boolean syncOne(Path source, Path target) {
        try {
            // No app copy to sync from: the user has yet to install one, and start() sends them
            // to the wizard for exactly that.
            if (!Files.isRegularFile(source)) return false;
            if (!Files.isRegularFile(target)) return false;
            if (Files.mismatch(source, target) < 0) return false;

            AtomicWrite.copy(source, target);
            makeExecutable(target);
            logger.info("Updated stale '{}' from {}", target, source);
            return true;
        } catch (IOException e) {
            // getMessage() on the NIO exceptions is just the path again - see FileErrors.
            logger.warn("Could not update '{}' from {}: {}", target, source, FileErrors.reason(e));
            return false;
        }
    }

    /**
     * Makes sure the replacement can still be run.  {@link AtomicWrite} carries the old file's
     * permissions over and {@code ddphotos init} marks the script executable, so this only matters
     * for a copy that somehow lost the bit; the container does the same {@code chmod +x} after its
     * own copy.  Only the owner bit is added - the file is the user's to run, and widening it to
     * the group or the world isn't ours to decide.  A no-op where POSIX permissions don't apply.
     */
    private static void makeExecutable(Path target) {
        try {
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(target);
            if (perms.contains(PosixFilePermission.OWNER_EXECUTE)) return;
            perms = new HashSet<>(perms);
            perms.add(PosixFilePermission.OWNER_EXECUTE);
            Files.setPosixFilePermissions(target, perms);
        } catch (IOException | UnsupportedOperationException e) {
            // Not a POSIX filesystem (Windows) - there is no execute bit to set.
        }
    }
}
