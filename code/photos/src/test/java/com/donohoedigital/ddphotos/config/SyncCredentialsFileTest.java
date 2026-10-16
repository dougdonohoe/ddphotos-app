package com.donohoedigital.ddphotos.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class SyncCredentialsFileTest {

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
        SyncCredentialsFile c = new SyncCredentialsFile(SyncCredentialsFile.IMMICH, f).load();
        assertTrue(c.existsOnDisk());
        assertEquals("oeW00smAhMkRo0GYb7zAAQWr8mUFNvYSxO7P0TIeg", c.getSecret());
        assertEquals("http://localhost:2283/api", c.getUrl());
        assertTrue(c.isComplete());
    }

    @Test
    public void albumsFile_picksUpAHandEdit() throws Exception {
        // The chooser's own error message sends the user off to fix the key in a text editor.
        write("IMMICH_INSTANCE_URL=http://localhost:2283\nIMMICH_API_KEY=old-key-0123456789\n");
        AlbumsFile af = new AlbumsFile();
        af.setConfigDir(tmp);
        assertEquals("old-key-0123456789", af.getSyncCredentialsFile(SyncCredentialsFile.IMMICH).getSecret());

        // A different length guarantees a different stamp even on a coarse-mtime filesystem.
        write("IMMICH_INSTANCE_URL=http://localhost:2283\nIMMICH_API_KEY=fixed-key-0123456789abcdef\n");
        assertEquals("fixed-key-0123456789abcdef", af.getSyncCredentialsFile(SyncCredentialsFile.IMMICH).getSecret());
    }

    @Test
    public void albumsFile_picksUpAFileCreatedLater() throws Exception {
        AlbumsFile af = new AlbumsFile();
        af.setConfigDir(tmp);
        assertFalse(af.getSyncCredentialsFile(SyncCredentialsFile.IMMICH).isComplete());

        write("IMMICH_INSTANCE_URL=http://localhost:2283\nIMMICH_API_KEY=new-key-0123456789\n");
        assertTrue(af.getSyncCredentialsFile(SyncCredentialsFile.IMMICH).isComplete());
    }

    @Test
    public void load_missingFile() {
        SyncCredentialsFile c = new SyncCredentialsFile(SyncCredentialsFile.IMMICH, tmp.resolve("immich.env")).load();
        assertFalse(c.existsOnDisk());
        assertNull(c.getSecret());
        assertFalse(c.isComplete());
    }

    @Test
    public void load_valueContainingEquals() throws Exception {
        SyncCredentialsFile c = new SyncCredentialsFile(SyncCredentialsFile.IMMICH, write("IMMICH_INSTANCE_URL=http://h/x?a=b\n")).load();
        assertEquals("http://h/x?a=b", c.getUrl());
    }

    @Test
    public void save_updatesInPlaceAndKeepsOtherLines() throws Exception {
        Path f = write("""
                # my Immich
                IMMICH_INSTANCE_URL=http://old:2283
                OTHER=keep
                """);
        SyncCredentialsFile c = new SyncCredentialsFile(SyncCredentialsFile.IMMICH, f).load();
        c.setUrl(" http://new:2283 ");
        c.setSecret("abc123");
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
        SyncCredentialsFile c = new SyncCredentialsFile(SyncCredentialsFile.IMMICH, f).load();
        c.setSecret("three");
        c.save();
        assertEquals("IMMICH_API_KEY=three\nIMMICH_INSTANCE_URL=http://h\n", Files.readString(f));
    }

    @Test
    public void save_createsOwnerOnlyFile() throws Exception {
        assumeTrue(tmp.getFileSystem().supportedFileAttributeViews().contains("posix"));
        Path f = tmp.resolve("config").resolve("immich.env");
        SyncCredentialsFile c = new SyncCredentialsFile(SyncCredentialsFile.IMMICH, f).load();
        c.setUrl("http://localhost:2283");
        c.setSecret("abc123");
        c.save();

        assertEquals("IMMICH_API_KEY=abc123\nIMMICH_INSTANCE_URL=http://localhost:2283\n", Files.readString(f));
        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(f)));
        assertEquals("abc123", new SyncCredentialsFile(SyncCredentialsFile.IMMICH, f).load().getSecret());
    }

    @Test
    public void save_keepsExistingPermissions() throws Exception {
        assumeTrue(tmp.getFileSystem().supportedFileAttributeViews().contains("posix"));
        Path f = write("IMMICH_API_KEY=a\n");
        Files.setPosixFilePermissions(f, PosixFilePermissions.fromString("rw-r-----"));
        SyncCredentialsFile c = new SyncCredentialsFile(SyncCredentialsFile.IMMICH, f).load();
        c.setSecret("b");
        c.save();
        assertEquals("rw-r-----", PosixFilePermissions.toString(Files.getPosixFilePermissions(f)));
    }

    @Test
    public void photoprism_roundTrip() throws Exception {
        Path f = tmp.resolve("photoprism.env");
        Files.writeString(f, "# PhotoPrism\nPHOTOPRISM_SITE_URL=http://localhost:2342\nIMMICH_API_KEY=ignored\n");
        SyncCredentialsFile c = new SyncCredentialsFile(SyncCredentialsFile.PHOTOPRISM, f).load();
        assertEquals("http://localhost:2342", c.getUrl());
        assertNull(c.getSecret(), "another provider's key is not this file's secret");
        c.setSecret("abcdef-ghijkl-mnopqr-stuvwx");
        c.save();

        assertEquals("""
                # PhotoPrism
                PHOTOPRISM_SITE_URL=http://localhost:2342
                IMMICH_API_KEY=ignored
                PHOTOPRISM_APP_PASSWORD=abcdef-ghijkl-mnopqr-stuvwx
                """, Files.readString(f));
    }

    @Test
    public void albumsFile_keepsEachProvidersFileApart() throws Exception {
        write("IMMICH_INSTANCE_URL=http://localhost:2283\nIMMICH_API_KEY=immich-key-0123456789\n");
        AlbumsFile af = new AlbumsFile();
        af.setConfigDir(tmp);
        assertTrue(af.getSyncCredentialsFile(SyncCredentialsFile.IMMICH).isComplete());
        SyncCredentialsFile pp = af.getSyncCredentialsFile(SyncCredentialsFile.PHOTOPRISM);
        assertEquals(tmp.resolve("photoprism.env"), pp.getPath());
        assertFalse(pp.isComplete());
    }

    private Path write(String content) throws Exception {
        Path f = tmp.resolve("immich.env");
        Files.writeString(f, content);
        return f;
    }
}
