package com.donohoedigital.ddphotos.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class ImmichCredentialsFileTest {

    @TempDir
    Path tmp;

    @Test
    public void load_parsesLikePhotogen() throws Exception {
        Path f = write("""
                # config/immich.env
                IMMICH_API_KEY = "oeW00smAhMkRo0GYb7zAAQWr8mUFNvYSxO7P0TIeg"
                  IMMICH_INSTANCE_URL='http://localhost:2283/api'
                not an assignment
                """);
        ImmichCredentialsFile c = new ImmichCredentialsFile(f).load();
        assertTrue(c.existsOnDisk());
        assertEquals("oeW00smAhMkRo0GYb7zAAQWr8mUFNvYSxO7P0TIeg", c.getApiKey());
        assertEquals("http://localhost:2283/api", c.getUrl());
        assertTrue(c.isComplete());
    }

    @Test
    public void load_missingFile() {
        ImmichCredentialsFile c = new ImmichCredentialsFile(tmp.resolve("immich.env")).load();
        assertFalse(c.existsOnDisk());
        assertNull(c.getApiKey());
        assertFalse(c.isComplete());
    }

    @Test
    public void load_valueContainingEquals() throws Exception {
        ImmichCredentialsFile c = new ImmichCredentialsFile(write("IMMICH_INSTANCE_URL=http://h/x?a=b\n")).load();
        assertEquals("http://h/x?a=b", c.getUrl());
    }

    @Test
    public void save_updatesInPlaceAndKeepsOtherLines() throws Exception {
        Path f = write("""
                # my Immich
                IMMICH_INSTANCE_URL=http://old:2283
                OTHER=keep
                """);
        ImmichCredentialsFile c = new ImmichCredentialsFile(f).load();
        c.setUrl(" http://new:2283 ");
        c.setApiKey("abc123");
        c.save();

        assertEquals("""
                # my Immich
                IMMICH_INSTANCE_URL=http://new:2283
                OTHER=keep
                IMMICH_API_KEY=abc123
                """, Files.readString(f));
        assertFalse(c.isChangedOnDisk(), "our own save is not an external change");
    }

    @Test
    public void save_dropsDuplicateKeyLines() throws Exception {
        Path f = write("IMMICH_API_KEY=one\nIMMICH_API_KEY=two\nIMMICH_INSTANCE_URL=http://h\n");
        ImmichCredentialsFile c = new ImmichCredentialsFile(f).load();
        c.setApiKey("three");
        c.save();
        assertEquals("IMMICH_API_KEY=three\nIMMICH_INSTANCE_URL=http://h\n", Files.readString(f));
    }

    @Test
    public void save_createsOwnerOnlyFile() throws Exception {
        assumeTrue(tmp.getFileSystem().supportedFileAttributeViews().contains("posix"));
        Path f = tmp.resolve("config").resolve("immich.env");
        ImmichCredentialsFile c = new ImmichCredentialsFile(f).load();
        c.setUrl("http://localhost:2283");
        c.setApiKey("abc123");
        c.save();

        assertEquals("IMMICH_API_KEY=abc123\nIMMICH_INSTANCE_URL=http://localhost:2283\n", Files.readString(f));
        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(f)));
        assertEquals("abc123", new ImmichCredentialsFile(f).load().getApiKey());
    }

    @Test
    public void save_keepsExistingPermissions() throws Exception {
        assumeTrue(tmp.getFileSystem().supportedFileAttributeViews().contains("posix"));
        Path f = write("IMMICH_API_KEY=a\n");
        Files.setPosixFilePermissions(f, PosixFilePermissions.fromString("rw-r-----"));
        ImmichCredentialsFile c = new ImmichCredentialsFile(f).load();
        c.setApiKey("b");
        c.save();
        assertEquals("rw-r-----", PosixFilePermissions.toString(Files.getPosixFilePermissions(f)));
    }

    private Path write(String content) throws Exception {
        Path f = tmp.resolve("immich.env");
        Files.writeString(f, content);
        return f;
    }
}
