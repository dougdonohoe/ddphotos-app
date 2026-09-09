package com.donohoedigital.ddphotos;

import com.donohoedigital.base.Utils;
import com.donohoedigital.ddphotos.config.Site;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Unit tests for the site-script refresh.  Exercises {@link ScriptSync#syncOne} directly against
 * on-disk fixtures - no Swing, no site config, and nothing that reads the real bin directory.
 */
public class ScriptSyncTest {

    private static final String NEW_SCRIPT = "#!/usr/bin/env bash\nIMAGE=\"ddphotos:v1.2.0\"\n";
    private static final String OLD_SCRIPT = "#!/usr/bin/env bash\nIMAGE=\"ddphotos:v1.1.0\"\n";

    @TempDir
    Path tmp;

    private Path source;    // stands in for ~/.config/ddphotos/bin/ddphotos
    private Path siteDir;   // stands in for a site directory

    @BeforeEach
    public void setUp() throws IOException {
        source = Files.createDirectory(tmp.resolve("bin")).resolve("ddphotos");
        Files.writeString(source, NEW_SCRIPT);
        siteDir = Files.createDirectory(tmp.resolve("my-photos"));
    }

    @Test
    public void replacesAStaleScript() throws IOException {
        Path target = siteDir.resolve("ddphotos");
        Files.writeString(target, OLD_SCRIPT);

        assertTrue(ScriptSync.syncOne(source, target));
        assertEquals(NEW_SCRIPT, Files.readString(target, StandardCharsets.UTF_8));
    }

    @Test
    public void leavesAMatchingScriptAlone() throws IOException {
        Path target = siteDir.resolve("ddphotos");
        Files.writeString(target, NEW_SCRIPT);

        assertFalse(ScriptSync.syncOne(source, target), "an identical script is not worth copying, or logging");
        assertEquals(NEW_SCRIPT, Files.readString(target, StandardCharsets.UTF_8));
    }

    @Test
    public void doesNotCreateAScriptThatWasNeverThere() {
        // A site can be added by pointing at a directory that was never initialized from the app.
        Path target = siteDir.resolve("ddphotos");

        assertFalse(ScriptSync.syncOne(source, target));
        assertFalse(Files.exists(target));
    }

    @Test
    public void doesNothingWithoutAnAppCopyToSyncFrom() throws IOException {
        Path missing = tmp.resolve("bin/never-installed");
        Path target = siteDir.resolve("ddphotos");
        Files.writeString(target, OLD_SCRIPT);

        assertFalse(ScriptSync.syncOne(missing, target));
        assertEquals(OLD_SCRIPT, Files.readString(target, StandardCharsets.UTF_8),
                "the site's copy is better than nothing");
    }

    @Test
    public void doesNotMistakeADirectoryForAScript() throws IOException {
        Path target = siteDir.resolve("ddphotos");
        Files.createDirectory(target);

        assertFalse(ScriptSync.syncOne(source, target));
        assertTrue(Files.isDirectory(target));
    }

    // ── the replacement still has to be runnable ────────────────────────────

    @Test
    public void keepsTheScriptExecutable() throws IOException {
        assumeFalse(Utils.ISWINDOWS, "POSIX file permissions are not supported on Windows");
        Path target = siteDir.resolve("ddphotos");
        Files.writeString(target, OLD_SCRIPT);
        Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("rwxr-xr-x"));

        assertTrue(ScriptSync.syncOne(source, target));

        assertEquals(PosixFilePermissions.fromString("rwxr-xr-x"), Files.getPosixFilePermissions(target),
                "'ddphotos init' chmod +x'd it and the refresh must not undo that");
    }

    @Test
    public void restoresAnExecuteBitThatWentMissing() throws IOException {
        assumeFalse(Utils.ISWINDOWS, "POSIX file permissions are not supported on Windows");
        Path target = siteDir.resolve("ddphotos");
        Files.writeString(target, OLD_SCRIPT);
        Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("rw-r--r--"));

        assertTrue(ScriptSync.syncOne(source, target));

        assertTrue(Files.getPosixFilePermissions(target).contains(PosixFilePermission.OWNER_EXECUTE),
                "the point of the file is that './ddphotos' runs it");
    }

    // ── failures are survivable ─────────────────────────────────────────────

    @Test
    public void aReadOnlySiteDirectoryIsSkippedRatherThanThrown() throws IOException {
        assumeFalse(Utils.ISWINDOWS, "POSIX file permissions are not supported on Windows");
        assumeFalse("root".equals(System.getProperty("user.name")), "root ignores directory permissions");
        Path target = siteDir.resolve("ddphotos");
        Files.writeString(target, OLD_SCRIPT);
        Files.setPosixFilePermissions(siteDir, PosixFilePermissions.fromString("r-xr-xr-x"));
        try {
            // Startup must not fail because a site lives somewhere we can't write.
            assertFalse(ScriptSync.syncOne(source, target));
            assertEquals(OLD_SCRIPT, Files.readString(target, StandardCharsets.UTF_8));
        } finally {
            Files.setPosixFilePermissions(siteDir, PosixFilePermissions.fromString("rwxr-xr-x"));
        }
    }

    @Test
    public void leavesNoTempFileBehind() throws IOException {
        Path target = siteDir.resolve("ddphotos");
        Files.writeString(target, OLD_SCRIPT);

        assertTrue(ScriptSync.syncOne(source, target));

        try (var entries = Files.list(siteDir)) {
            assertEquals(1, entries.count(), "only the script should remain");
        }
    }

    // ── walking the configured sites ────────────────────────────────────────

    @Test
    public void refreshesEverySiteThatHasAScript() throws IOException {
        Path other = Files.createDirectory(tmp.resolve("other-photos"));
        Files.writeString(siteDir.resolve("ddphotos"), OLD_SCRIPT);
        Files.writeString(other.resolve("ddphotos"), OLD_SCRIPT);

        ScriptSync.syncSites(source.getParent(), List.of(site(siteDir), site(other)));

        assertEquals(NEW_SCRIPT, Files.readString(siteDir.resolve("ddphotos"), StandardCharsets.UTF_8));
        assertEquals(NEW_SCRIPT, Files.readString(other.resolve("ddphotos"), StandardCharsets.UTF_8));
    }

    @Test
    public void refreshesTheWindowsLauncherOnlyWhereOneExists() throws IOException {
        Path launcherSource = source.getParent().resolve("ddphotos.cmd");
        Files.writeString(launcherSource, "@echo new\n");
        Files.writeString(siteDir.resolve("ddphotos.cmd"), "@echo old\n");
        Path other = Files.createDirectory(tmp.resolve("mac-photos"));

        ScriptSync.syncSites(source.getParent(), List.of(site(siteDir), site(other)));

        assertEquals("@echo new\n",
                Files.readString(siteDir.resolve("ddphotos.cmd"), StandardCharsets.UTF_8));
        assertFalse(Files.exists(other.resolve("ddphotos.cmd")), "a Mac/Linux site must not acquire a .cmd launcher");
    }

    @Test
    public void skipsSitesWithNoUsableDirectory() {
        // A site whose drive is unplugged, or one saved before dir_path was filled in.
        Path gone = tmp.resolve("unplugged");
        ScriptSync.syncSites(source.getParent(),
                List.of(site(gone), new Site("No Dir", null, null), new Site("Blank", "  ", null)));
    }

    private static Site site(Path dir) {
        return new Site(dir.getFileName().toString(), dir.toString(), null);
    }
}
