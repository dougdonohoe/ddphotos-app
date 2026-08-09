package com.donohoedigital.ddphotos.config;

import com.donohoedigital.base.Utils;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Unit tests for the atomic replace.  The point of the class is what the file looks like when a
 * save goes wrong, so most of these check the failure paths.
 */
public class AtomicWriteTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void writesANewFile() throws IOException {
        Path target = tmp.getRoot().toPath().resolve("albums.yaml");
        AtomicWrite.writeString(target, "a: 1\n");
        assertEquals("a: 1\n", Files.readString(target, StandardCharsets.UTF_8));
    }

    @Test
    public void replacesAnExistingFile() throws IOException {
        Path target = tmp.newFile("albums.yaml").toPath();
        Files.writeString(target, "old\n");
        AtomicWrite.writeString(target, "new\n");
        assertEquals("new\n", Files.readString(target, StandardCharsets.UTF_8));
    }

    @Test
    public void leavesNoTempFileBehind() throws IOException {
        Path target = tmp.getRoot().toPath().resolve("albums.yaml");
        AtomicWrite.writeString(target, "a: 1\n");
        AtomicWrite.writeString(target, "a: 2\n");
        assertEquals("only the target should remain",
                Set.of("albums.yaml"), listNames(tmp.getRoot().toPath()));
    }

    @Test
    public void writesUtf8() throws IOException {
        Path target = tmp.getRoot().toPath().resolve("albums.yaml");
        AtomicWrite.writeString(target, "name: café → über\n");
        assertEquals("name: café → über\n", Files.readString(target, StandardCharsets.UTF_8));
    }

    @Test
    public void handlesARelativePath() throws IOException {
        // The callers hand us whatever path the site was configured with.
        Path target = tmp.getRoot().toPath().resolve("albums.yaml");
        Path relative = Path.of("").toAbsolutePath().relativize(target);
        AtomicWrite.writeString(relative, "a: 1\n");
        assertEquals("a: 1\n", Files.readString(target, StandardCharsets.UTF_8));
    }

    // ── the reason this class exists ────────────────────────────────────────

    @Test
    public void aFailedWriteLeavesTheOriginalIntact() throws IOException {
        Assume.assumeFalse("POSIX file permissions are not supported on Windows", Utils.ISWINDOWS);
        Path dir = tmp.newFolder("config").toPath();
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
        Path target = tmp.getRoot().toPath().resolve("gone/config/albums.yaml");
        NoSuchFileException e = assertThrows(NoSuchFileException.class,
                () -> AtomicWrite.writeString(target, "a: 1\n"));
        assertEquals(target.toString(), e.getFile());
    }

    @Test
    public void readOnlyTargetIsRefusedRatherThanReplaced() throws IOException {
        Assume.assumeFalse("POSIX file permissions are not supported on Windows", Utils.ISWINDOWS);
        Path target = tmp.newFile("albums.yaml").toPath();
        Files.writeString(target, "the original\n");
        Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("r--r--r--"));
        try {
            // A rename only needs write permission on the directory, so without the explicit
            // check this would succeed and quietly replace a file the user locked down.
            AccessDeniedException e = assertThrows(AccessDeniedException.class,
                    () -> AtomicWrite.writeString(target, "replacement\n"));
            assertEquals(target.toString(), e.getFile());
            assertEquals("the original\n", Files.readString(target, StandardCharsets.UTF_8));
            assertEquals(Set.of("albums.yaml"), listNames(tmp.getRoot().toPath()));
        } finally {
            Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("rw-r--r--"));
        }
    }

    // ── symlinks ────────────────────────────────────────────────────────────

    @Test
    public void writesThroughASymlinkRatherThanReplacingIt() throws IOException {
        Assume.assumeFalse("creating symlinks on Windows requires elevation or Developer Mode", Utils.ISWINDOWS);
        Path real = tmp.newFolder("shared").toPath().resolve("passwords.yaml");
        Files.writeString(real, "key: abc\n");
        Path link = tmp.newFolder("config").toPath().resolve("passwords.yaml");
        Files.createSymbolicLink(link, real);

        AtomicWrite.writeString(link, "key: xyz\n");

        assertTrue("the link must survive the save", Files.isSymbolicLink(link));
        assertEquals("key: xyz\n", Files.readString(real, StandardCharsets.UTF_8));
    }

    @Test
    public void followsASymlinkToAFileThatDoesNotExistYet() throws IOException {
        Assume.assumeFalse("creating symlinks on Windows requires elevation or Developer Mode", Utils.ISWINDOWS);
        Path real = tmp.newFolder("shared").toPath().resolve("passwords.yaml");
        Path link = tmp.newFolder("config").toPath().resolve("passwords.yaml");
        Files.createSymbolicLink(link, real);

        AtomicWrite.writeString(link, "key: xyz\n");

        assertTrue(Files.isSymbolicLink(link));
        assertEquals("key: xyz\n", Files.readString(real, StandardCharsets.UTF_8));
    }

    @Test
    public void followsARelativeSymlink() throws IOException {
        Assume.assumeFalse("creating symlinks on Windows requires elevation or Developer Mode", Utils.ISWINDOWS);
        Path dir = tmp.newFolder("config").toPath();
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
        Assume.assumeFalse("POSIX file permissions are not supported on Windows", Utils.ISWINDOWS);
        Path target = tmp.newFile("passwords.yaml").toPath();
        Files.writeString(target, "key: abc\n");
        Set<PosixFilePermission> locked = PosixFilePermissions.fromString("rw-------");
        Files.setPosixFilePermissions(target, locked);

        AtomicWrite.writeString(target, "key: xyz\n");

        assertEquals("a deliberately private passwords.yaml must not be widened by a save",
                locked, Files.getPosixFilePermissions(target));
        assertEquals("key: xyz\n", Files.readString(target, StandardCharsets.UTF_8));
    }

    @Test
    public void aNewFileIsReadableLikeAnyOtherNewFile() throws IOException {
        Assume.assumeFalse("POSIX file permissions are not supported on Windows", Utils.ISWINDOWS);
        Path target = tmp.getRoot().toPath().resolve("albums.yaml");
        AtomicWrite.writeString(target, "a: 1\n");

        Path reference = tmp.getRoot().toPath().resolve("reference.yaml");
        Files.writeString(reference, "a: 1\n");

        assertEquals("a new file should get the same permissions a plain write would give it",
                Files.getPosixFilePermissions(reference), Files.getPosixFilePermissions(target));
    }

    private static Set<String> listNames(Path dir) throws IOException {
        try (var entries = Files.list(dir)) {
            return entries.map(p -> p.getFileName().toString()).collect(java.util.stream.Collectors.toSet());
        }
    }
}
