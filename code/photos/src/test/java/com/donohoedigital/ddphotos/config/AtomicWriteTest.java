package com.donohoedigital.ddphotos.config;

import com.donohoedigital.base.Utils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Unit tests for the atomic replace.  The point of the class is what the file looks like when a
 * save goes wrong, so most of these check the failure paths.
 */
public class AtomicWriteTest {

    @TempDir
    Path tmp;

    @Test
    public void writesANewFile() throws IOException {
        Path target = tmp.resolve("albums.yaml");
        AtomicWrite.writeString(target, "a: 1\n");
        assertEquals("a: 1\n", Files.readString(target, StandardCharsets.UTF_8));
    }

    @Test
    public void replacesAnExistingFile() throws IOException {
        Path target = Files.createFile(tmp.resolve("albums.yaml"));
        Files.writeString(target, "old\n");
        AtomicWrite.writeString(target, "new\n");
        assertEquals("new\n", Files.readString(target, StandardCharsets.UTF_8));
    }

    @Test
    public void leavesNoTempFileBehind() throws IOException {
        Path target = tmp.resolve("albums.yaml");
        AtomicWrite.writeString(target, "a: 1\n");
        AtomicWrite.writeString(target, "a: 2\n");
        assertEquals(Set.of("albums.yaml"), listNames(tmp), "only the target should remain");
    }

    @Test
    public void writesUtf8() throws IOException {
        Path target = tmp.resolve("albums.yaml");
        AtomicWrite.writeString(target, "name: café → über\n");
        assertEquals("name: café → über\n", Files.readString(target, StandardCharsets.UTF_8));
    }

    @Test
    public void handlesARelativePath() throws IOException {
        // The callers hand us whatever path the site was configured with.
        Path target = tmp.resolve("albums.yaml");
        Path relative = Path.of("").toAbsolutePath().relativize(target);
        AtomicWrite.writeString(relative, "a: 1\n");
        assertEquals("a: 1\n", Files.readString(target, StandardCharsets.UTF_8));
    }

    // ── copying one file over another ───────────────────────────────────────

    @Test
    public void copyReplacesAnExistingFile() throws IOException {
        Path source = Files.createFile(tmp.resolve("source"));
        Files.writeString(source, "the new script\n");
        Path target = Files.createFile(tmp.resolve("ddphotos"));
        Files.writeString(target, "the old script\n");

        AtomicWrite.copy(source, target);

        assertEquals("the new script\n", Files.readString(target, StandardCharsets.UTF_8));
        assertEquals("the new script\n", Files.readString(source, StandardCharsets.UTF_8), "the source is left alone");
    }

    @Test
    public void copyKeepsTheTargetsPermissionsRatherThanTheSources() throws IOException {
        assumeFalse(Utils.ISWINDOWS, "POSIX file permissions are not supported on Windows");
        Path source = Files.createFile(tmp.resolve("source"));
        Files.writeString(source, "the new script\n");
        Files.setPosixFilePermissions(source, PosixFilePermissions.fromString("rw-------"));
        Path target = Files.createFile(tmp.resolve("ddphotos"));
        Files.writeString(target, "the old script\n");
        Set<PosixFilePermission> executable = PosixFilePermissions.fromString("rwxr-xr-x");
        Files.setPosixFilePermissions(target, executable);

        AtomicWrite.copy(source, target);

        assertEquals(executable, Files.getPosixFilePermissions(target),
                "an executable script must still be executable after a refresh");
    }

    @Test
    public void copyLeavesNoTempFileBehind() throws IOException {
        Path dir = Files.createDirectory(tmp.resolve("site"));
        Path source = Files.createFile(tmp.resolve("source"));
        Files.writeString(source, "a\n");
        Path target = dir.resolve("ddphotos");
        Files.writeString(target, "b\n");

        AtomicWrite.copy(source, target);

        assertEquals(Set.of("ddphotos"), listNames(dir));
    }

    @Test
    public void copyToAMissingDirectoryReportsTheTargetPath() throws IOException {
        Path source = Files.createFile(tmp.resolve("source"));
        Files.writeString(source, "a\n");
        Path target = tmp.resolve("gone/ddphotos");

        NoSuchFileException e = assertThrows(NoSuchFileException.class,
                () -> AtomicWrite.copy(source, target));
        assertEquals(target.toString(), e.getFile());
    }

    @Test
    public void copyFromAMissingSourceLeavesTheTargetIntact() throws IOException {
        Path source = tmp.resolve("never-installed");
        Path dir = Files.createDirectory(tmp.resolve("site"));
        Path target = dir.resolve("ddphotos");
        Files.writeString(target, "the old script\n");

        assertThrows(NoSuchFileException.class, () -> AtomicWrite.copy(source, target));
        assertEquals("the old script\n", Files.readString(target, StandardCharsets.UTF_8));
        assertEquals(Set.of("ddphotos"), listNames(dir));
    }

    // ── the reason this class exists ────────────────────────────────────────

    @Test
    public void aFailedWriteLeavesTheOriginalIntact() throws IOException {
        assumeFalse(Utils.ISWINDOWS, "POSIX file permissions are not supported on Windows");
        Path dir = Files.createDirectory(tmp.resolve("config"));
        Path target = dir.resolve("albums.yaml");
        Files.writeString(target, "the original\n");

        // Read-only directory: the temp file can't be created, so the write fails after the
        // point where Files.writeString() would already have truncated the target.
        Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("r-xr-xr-x"));
        try {
            assertThrows(IOException.class, () -> AtomicWrite.writeString(target, "replacement\n"));
            assertEquals("the original\n", Files.readString(target, StandardCharsets.UTF_8));
        } finally {
            Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwxr-xr-x"));
        }
    }

    // ── errors are reported against the target, not the temp file ───────────

    @Test
    public void missingDirectoryReportsTheTargetPath() {
        Path target = tmp.resolve("gone/config/albums.yaml");
        NoSuchFileException e = assertThrows(NoSuchFileException.class,
                () -> AtomicWrite.writeString(target, "a: 1\n"));
        assertEquals(target.toString(), e.getFile());
    }

    @Test
    public void readOnlyTargetIsRefusedRatherThanReplaced() throws IOException {
        assumeFalse(Utils.ISWINDOWS, "POSIX file permissions are not supported on Windows");
        Path target = Files.createFile(tmp.resolve("albums.yaml"));
        Files.writeString(target, "the original\n");
        Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("r--r--r--"));
        try {
            // A rename only needs write permission on the directory, so without the explicit
            // check this would succeed and quietly replace a file the user locked down.
            AccessDeniedException e = assertThrows(AccessDeniedException.class,
                    () -> AtomicWrite.writeString(target, "replacement\n"));
            assertEquals(target.toString(), e.getFile());
            assertEquals("the original\n", Files.readString(target, StandardCharsets.UTF_8));
            assertEquals(Set.of("albums.yaml"), listNames(tmp));
        } finally {
            Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("rw-r--r--"));
        }
    }

    // ── symlinks ────────────────────────────────────────────────────────────

    @Test
    public void writesThroughASymlinkRatherThanReplacingIt() throws IOException {
        assumeFalse(Utils.ISWINDOWS, "creating symlinks on Windows requires elevation or Developer Mode");
        Path real = Files.createDirectory(tmp.resolve("shared")).resolve("passwords.yaml");
        Files.writeString(real, "key: abc\n");
        Path link = Files.createDirectory(tmp.resolve("config")).resolve("passwords.yaml");
        Files.createSymbolicLink(link, real);

        AtomicWrite.writeString(link, "key: xyz\n");

        assertTrue(Files.isSymbolicLink(link), "the link must survive the save");
        assertEquals("key: xyz\n", Files.readString(real, StandardCharsets.UTF_8));
    }

    @Test
    public void followsASymlinkToAFileThatDoesNotExistYet() throws IOException {
        assumeFalse(Utils.ISWINDOWS, "creating symlinks on Windows requires elevation or Developer Mode");
        Path real = Files.createDirectory(tmp.resolve("shared")).resolve("passwords.yaml");
        Path link = Files.createDirectory(tmp.resolve("config")).resolve("passwords.yaml");
        Files.createSymbolicLink(link, real);

        AtomicWrite.writeString(link, "key: xyz\n");

        assertTrue(Files.isSymbolicLink(link));
        assertEquals("key: xyz\n", Files.readString(real, StandardCharsets.UTF_8));
    }

    @Test
    public void followsARelativeSymlink() throws IOException {
        assumeFalse(Utils.ISWINDOWS, "creating symlinks on Windows requires elevation or Developer Mode");
        Path dir = Files.createDirectory(tmp.resolve("config"));
        Path real = dir.resolve("passwords-real.yaml");
        Files.writeString(real, "key: abc\n");
        Path link = dir.resolve("passwords.yaml");
        Files.createSymbolicLink(link, Path.of("passwords-real.yaml"));

        AtomicWrite.writeString(link, "key: xyz\n");

        assertTrue(Files.isSymbolicLink(link));
        assertEquals("key: xyz\n", Files.readString(real, StandardCharsets.UTF_8));
    }

    // ── permissions ─────────────────────────────────────────────────────────

    @Test
    public void preservesTheTargetsPermissions() throws IOException {
        assumeFalse(Utils.ISWINDOWS, "POSIX file permissions are not supported on Windows");
        Path target = Files.createFile(tmp.resolve("passwords.yaml"));
        Files.writeString(target, "key: abc\n");
        Set<PosixFilePermission> locked = PosixFilePermissions.fromString("rw-------");
        Files.setPosixFilePermissions(target, locked);

        AtomicWrite.writeString(target, "key: xyz\n");

        assertEquals(locked, Files.getPosixFilePermissions(target),
                "a deliberately private passwords.yaml must not be widened by a save");
        assertEquals("key: xyz\n", Files.readString(target, StandardCharsets.UTF_8));
    }

    @Test
    public void aNewFileIsReadableLikeAnyOtherNewFile() throws IOException {
        assumeFalse(Utils.ISWINDOWS, "POSIX file permissions are not supported on Windows");
        Path target = tmp.resolve("albums.yaml");
        AtomicWrite.writeString(target, "a: 1\n");

        Path reference = tmp.resolve("reference.yaml");
        Files.writeString(reference, "a: 1\n");

        assertEquals(Files.getPosixFilePermissions(reference), Files.getPosixFilePermissions(target),
                "a new file should get the same permissions a plain write would give it");
    }

    private static Set<String> listNames(Path dir) throws IOException {
        try (var entries = Files.list(dir)) {
            return entries.map(p -> p.getFileName().toString()).collect(java.util.stream.Collectors.toSet());
        }
    }
}
