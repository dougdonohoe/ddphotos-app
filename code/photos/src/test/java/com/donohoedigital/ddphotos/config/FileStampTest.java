package com.donohoedigital.ddphotos.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class FileStampTest {

    @TempDir
    Path tmp;

    private Path write(String name, String content) throws Exception {
        Path p = tmp.resolve(name);
        Files.writeString(p, content, StandardCharsets.UTF_8);
        return p;
    }

    // ── absent / unusable paths ─────────────────────────────────────────────

    @Test
    public void of_nullPath_isMissing() {
        assertEquals(FileStamp.MISSING, FileStamp.of(null));
    }

    @Test
    public void of_absentFile_isMissing() {
        Path p = tmp.resolve("nope.yaml");
        assertEquals(FileStamp.MISSING, FileStamp.of(p));
    }

    @Test
    public void of_directory_isMissing() {
        // A directory is not a file we can hold in memory, so it reads as "nothing there".
        assertEquals(FileStamp.MISSING, FileStamp.of(tmp));
    }

    // ── detecting changes ───────────────────────────────────────────────────

    @Test
    public void of_sameFileUntouched_isEqual() throws Exception {
        Path p = write("a.yaml", "hello");
        assertEquals(FileStamp.of(p), FileStamp.of(p));
    }

    @Test
    public void of_sizeChanged_isDifferent() throws Exception {
        Path p = write("a.yaml", "hello");
        FileStamp before = FileStamp.of(p);

        // Rewrite at exactly the same timestamp, so only the length can give it away.
        FileTime when = Files.getLastModifiedTime(p);
        Files.writeString(p, "hello world", StandardCharsets.UTF_8);
        Files.setLastModifiedTime(p, when);

        assertNotEquals(before, FileStamp.of(p));
    }

    @Test
    public void of_mtimeChanged_isDifferent() throws Exception {
        Path p = write("a.yaml", "hello");
        FileStamp before = FileStamp.of(p);

        // Same length, different moment - the case size alone would miss.
        Files.writeString(p, "world", StandardCharsets.UTF_8);
        Files.setLastModifiedTime(p, FileTime.fromMillis(
                Files.getLastModifiedTime(p).toMillis() + 5_000));

        assertNotEquals(before, FileStamp.of(p));
    }

    @Test
    public void of_fileDeleted_isDifferent() throws Exception {
        Path p = write("a.yaml", "hello");
        FileStamp before = FileStamp.of(p);
        Files.delete(p);
        assertNotEquals(before, FileStamp.of(p));
        assertEquals(FileStamp.MISSING, FileStamp.of(p));
    }

    @Test
    public void of_fileCreated_isDifferent() throws Exception {
        Path p = tmp.resolve("later.yaml");
        FileStamp before = FileStamp.of(p);
        assertEquals(FileStamp.MISSING, before);

        Files.writeString(p, "now here", StandardCharsets.UTF_8);
        assertNotEquals(before, FileStamp.of(p));
    }
}
