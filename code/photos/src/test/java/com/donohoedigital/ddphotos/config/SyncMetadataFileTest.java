package com.donohoedigital.ddphotos.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class SyncMetadataFileTest {

    @TempDir
    Path tmp;

    /** A real file as photogen writes it, trimmed to two photos. */
    private static final String PHOTOGEN_WRITTEN = """
            # Written by photogen sync. Do not edit.
            provider: immich
            album_id: ef8acfb8-43fb-4c63-90c0-307b88b8f97a
            synced_at: 2026-09-21T21:20:23Z
            name: The Way
            description: "Fish &amp; chips"
            photos:
                - asset_id: efab74ee-8369-4396-9ec7-94292ecac38e
                  file: 2024-The-Way-3.jpg
                  original_file_name: 2024-The-Way-3.jpg
                  size: 5535328
                  checksum: 6lbXjOrcKhH1iFG18HiVf+eyzhU=
                  updated_at: 2026-09-20T15:22:11.579Z
                  caption: ""
                - asset_id: e4179164-31af-4662-aa9e-022fd0782a9a
                  file: IMG_9728.heic
                  original_file_name: IMG_9728.heic
                  size: 2369718
                  checksum: 7ZaLpOOoSykfpm2I40sjZ09dNDg=
                  updated_at: 2026-09-20T15:22:18.01Z
                  caption: ""
            """;

    @Test
    public void load_photogenWrittenFile() throws Exception {
        Files.writeString(tmp.resolve(SyncMetadataFile.FILE_NAME), PHOTOGEN_WRITTEN);
        SyncMetadataFile m = new SyncMetadataFile(tmp).load();

        assertTrue(m.existsOnDisk());
        assertEquals("immich", m.getProvider());
        assertEquals("ef8acfb8-43fb-4c63-90c0-307b88b8f97a", m.getAlbumId());
        assertEquals("The Way", m.getName());
        assertEquals("Fish &amp; chips", m.getDescription(), "stored escaped, returned as stored");
        assertTrue(m.hasSynced());
    }

    @Test
    public void load_missingFileIsEmpty() {
        SyncMetadataFile m = new SyncMetadataFile(tmp.resolve("never-synced")).load();
        assertFalse(m.existsOnDisk());
        assertNull(m.getName());
        assertNull(m.getDescription());
        assertFalse(m.hasSynced());
    }

    @Test
    public void load_emptyDescriptionIsNull() throws Exception {
        Files.writeString(tmp.resolve(SyncMetadataFile.FILE_NAME), "name: X\ndescription: \"\"\n");
        assertNull(new SyncMetadataFile(tmp).load().getDescription());
    }

    @Test
    public void load_brokenFileIsTreatedAsEmpty() throws Exception {
        Files.writeString(tmp.resolve(SyncMetadataFile.FILE_NAME), "name: [unclosed\n");
        SyncMetadataFile m = new SyncMetadataFile(tmp).load();
        assertNull(m.getName());
        assertFalse(m.hasSynced());
    }

    @Test
    public void hasSynced_goZeroTimeIsNotSynced() throws Exception {
        Files.writeString(tmp.resolve(SyncMetadataFile.FILE_NAME), "synced_at: 0001-01-01T00:00:00Z\n");
        assertFalse(new SyncMetadataFile(tmp).load().hasSynced());
    }

    @Test
    public void writeStub_createsFoldersAndWritesOnlyPhotogenKeys() throws Exception {
        Path dir = tmp.resolve("sync/demo/immich/theway");
        SyncMetadataFile.writeStub(dir, "immich", "ef8acfb8-43fb-4c63-90c0-307b88b8f97a",
                                   "The Way: Part 1", SyncText.escape("Tom & Jerry\n<3"));

        Path file = dir.resolve(SyncMetadataFile.FILE_NAME);
        String text = Files.readString(file);
        assertTrue(text.startsWith(SyncMetadataFile.HEADER), text);

        // photogen reads the file with KnownFields(true): any other key would fail its run.
        Object parsed = new org.snakeyaml.engine.v2.api.Load(
                org.snakeyaml.engine.v2.api.LoadSettings.builder().build()).loadFromString(text);
        assertEquals(Map.of("provider", "immich",
                            "album_id", "ef8acfb8-43fb-4c63-90c0-307b88b8f97a",
                            "name", "The Way: Part 1",
                            "description", "Tom &amp; Jerry &lt;3"), parsed);

        SyncMetadataFile m = new SyncMetadataFile(dir).load();
        assertEquals("The Way: Part 1", m.getName());
        assertFalse(m.hasSynced(), "a stub is not a sync");
    }

    @Test
    public void writeStub_nullsBecomeEmptyStrings() throws Exception {
        SyncMetadataFile.writeStub(tmp, "immich", "id", null, null);
        String text = Files.readString(tmp.resolve(SyncMetadataFile.FILE_NAME));
        assertTrue(text.contains("name: ''") || text.contains("name: \"\""), text);
    }

    @Test
    public void isChangedOnDisk_afterRewrite() throws Exception {
        SyncMetadataFile.writeStub(tmp, "immich", "id", "A", "");
        SyncMetadataFile m = new SyncMetadataFile(tmp).load();
        assertFalse(m.isChangedOnDisk());
        SyncMetadataFile.writeStub(tmp, "immich", "id", "A longer name", "");
        assertTrue(m.isChangedOnDisk());
    }
}
