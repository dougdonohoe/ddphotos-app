package com.donohoedigital.ddphotos.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The {@code sync:} block: its validation (mirroring photogen's {@code albums_config.go}), its
 * round trip, and the sync-folder paths and names derived from it.
 */
public class AlbumsFileSyncTest {

    private static final String ID_A = "ef8acfb8-43fb-4c63-90c0-307b88b8f97a";
    private static final String ID_B = "9c16a3ab-74c9-4067-9335-c0092b9afb4c";

    @TempDir
    Path tmp;

    // ── load and validation ─────────────────────────────────────────────────

    @Test
    public void load_syncBlock() throws Exception {
        AlbumsFile af = AlbumsFile.load(writeYaml(settings("demo") + """
                albums:
                  - slug: theway
                    cover: IMG_1.jpg
                    sync:
                      provider: immich
                      album_id: %s
                      captions: false
                  - slug: antarctica
                    name: Antarctica Voyage
                    sync:
                      provider: immich
                      album_id: %s
                """.formatted(ID_A, ID_B)));

        AlbumEntry a = af.getAlbums().getFirst();
        assertTrue(a.isSynced());
        assertNull(a.getName(), "name is optional for a synced album");
        assertNull(a.getSource());
        assertEquals("immich", a.getSync().getProvider());
        assertEquals(ID_A, a.getSync().getAlbumId());
        assertEquals(Boolean.FALSE, a.getSync().getCaptions());
        assertFalse(a.getSync().isCaptionsEnabled());

        SyncEntry b = af.getAlbums().get(1).getSync();
        assertNull(b.getCaptions(), "absent stays absent");
        assertTrue(b.isCaptionsEnabled(), "absent means true");
    }

    @Test
    public void validate_localAlbumStillNeedsNameAndSource() throws Exception {
        assertLoadFails("albums:\n  - slug: a\n    source: /tmp/p\n", "name is required");
        assertLoadFails("albums:\n  - slug: a\n    name: A\n", "source is required");
    }

    @Test
    public void validate_syncExcludesSourceAndBase() throws Exception {
        assertLoadFails(album("    source: /tmp/p\n" + sync("immich", ID_A)),
                        "sync and source/base are mutually exclusive");
        assertLoadFails("bases:\n  b: /tmp\n" + album("    base: b\n" + sync("immich", ID_A)),
                        "sync and source/base are mutually exclusive");
    }

    @Test
    public void validate_provider() throws Exception {
        assertLoadFails(album("    sync:\n      album_id: " + ID_A + "\n"),
                        "sync.provider is required (one of: immich, mock)");
        assertLoadFails(album(sync("flickr", ID_A)), "sync.provider \"flickr\" is not a known provider");
    }

    @Test
    public void validate_albumId() throws Exception {
        assertLoadFails(album("    sync:\n      provider: immich\n"), "sync.album_id is required");
        assertLoadFails(album(sync("immich", "ef8acfb8-43fb")), "is not an Immich album UUID");
        // Uppercase is accepted, as photogen accepts it.
        AlbumsFile.load(writeYaml(album(sync("immich", ID_A.toUpperCase()))));
        // Only Immich ids are shape-checked.
        AlbumsFile.load(writeYaml(album(sync("mock", "antarctica")
                + "      mock:\n        assets: a.json\n        media_dir: m\n")));
    }

    @Test
    public void validate_mock() throws Exception {
        assertLoadFails(album(sync("immich", ID_A) + "      mock:\n        assets: a.json\n        media_dir: m\n"),
                        "sync.mock is only valid with provider \"mock\", not \"immich\"");
        assertLoadFails(album(sync("mock", "x") + "      mock:\n        media_dir: m\n"),
                        "sync.mock.assets is required");
        assertLoadFails(album(sync("mock", "x") + "      mock:\n        assets: a.json\n"),
                        "sync.mock.media_dir is required");
        assertLoadFails(album(sync("mock", "x") + "      mock:\n        assets: a\n        media_dir: m\n        fail: boom\n"),
                        "sync.mock.fail must be \"list\" or \"fetch\", got \"boom\"");
    }

    @Test
    public void validate_duplicateAlbumIdIgnoringCase() throws Exception {
        String yaml = "albums:\n"
                + "  - slug: one\n" + sync("immich", ID_A)
                + "  - slug: two\n" + sync("immich", ID_A.toUpperCase());
        assertLoadFails(yaml, "albums \"one\" and \"two\": both sync album_id");
    }

    // ── round trip ──────────────────────────────────────────────────────────

    @Test
    public void roundTrip_keepsMockBlockAndComments() throws Exception {
        String yaml = """
                albums:
                  - slug: antarctica
                    sync:
                      provider: mock   # the offline provider
                      album_id: antarctica
                      captions: true
                      mock:
                        assets: testdata/sync/antarctica.json
                        media_dir: sample/source/antarctica
                """;
        Path f = writeYaml(yaml);
        AlbumsFile af = AlbumsFile.load(f);
        af.getAlbums().getFirst().getSync().setCaptions(false);
        af.save(f);

        String out = Files.readString(f);
        assertTrue(out.contains("provider: mock # the offline provider"), out);
        assertTrue(out.contains("captions: false"), out);
        assertTrue(out.contains("assets: testdata/sync/antarctica.json"), out);
        assertTrue(out.contains("media_dir: sample/source/antarctica"), out);
        assertEquals(af.getAlbums().getFirst(), AlbumsFile.load(f).getAlbums().getFirst());
    }

    @Test
    public void save_providerChangedFromMock_dropsMockBlock() throws Exception {
        // What the album panel saves: a new SyncEntry, without the mock block, for the new provider.
        Path f = writeYaml(album(sync("mock", "antarctica")
                + "      mock:\n        assets: a.json\n        media_dir: m\n"));
        AlbumsFile af = AlbumsFile.load(f);
        af.getAlbums().getFirst().setSync(new SyncEntry("immich", ID_A, true));
        af.save(f);

        String out = Files.readString(f);
        assertFalse(out.contains("mock"), out);
        assertEquals(af.getAlbums().getFirst(), AlbumsFile.load(f).getAlbums().getFirst());
    }

    @Test
    public void save_newSyncedAlbum_writesSyncAfterNameAndNoSource() throws Exception {
        Path f = writeYaml(settings("demo") + "albums:\n  - slug: local\n    name: Local\n    source: /tmp/p\n");
        AlbumsFile af = AlbumsFile.load(f);
        AlbumEntry a = new AlbumEntry();
        a.setSlug("theway");
        a.setSync(new SyncEntry("immich", ID_A, true));
        af.getAlbums().add(a);
        af.save(f);

        String out = Files.readString(f);
        String block = out.substring(out.indexOf("- slug: theway"));
        assertFalse(block.contains("name:"), block);
        assertFalse(block.contains("source:"), block);
        assertTrue(block.indexOf("sync:") < block.indexOf("manual_sort_order:"), block);
        assertTrue(block.contains("album_id: " + ID_A), block);
        assertTrue(block.contains("captions: true"), block);
    }

    @Test
    public void save_switchLocalToSyncAndBack() throws Exception {
        Path f = writeYaml("bases:\n  b: /tmp\nalbums:\n  - slug: a\n    name: A\n    base: b\n    source: p\n");
        AlbumsFile af = AlbumsFile.load(f);
        AlbumEntry a = af.getAlbums().getFirst();

        a.setBase(null);
        a.setSource(null);
        a.setName(null);
        a.setSync(new SyncEntry("immich", ID_A, true));
        af.save(f);
        String synced = Files.readString(f);
        assertFalse(synced.contains("source:"), synced);
        assertFalse(synced.contains("    base:"), synced);
        assertFalse(synced.contains("name:"), synced);
        assertTrue(synced.contains("sync:"), synced);

        a.setSync(null);
        a.setName("A");
        a.setSource("/tmp/p");
        af.save(f);
        String local = Files.readString(f);
        assertFalse(local.contains("sync:"), local);
        assertTrue(local.contains("source: /tmp/p"), local);
    }

    // ── paths and names ─────────────────────────────────────────────────────

    @Test
    public void resolveSourcePath_syncedAlbumUsesSyncFolder() throws Exception {
        AlbumsFile af = AlbumsFile.load(writeYaml(settings("demo") + album(sync("immich", ID_A))));
        af.setSiteDir(tmp);
        AlbumEntry a = af.getAlbums().getFirst();

        Path expected = tmp.resolve("sync").resolve("demo").resolve("immich").resolve("a");
        assertEquals(expected, af.resolveSyncPath(a));
        assertEquals(expected, af.resolveSourcePath(a));
        assertEquals(expected.resolve("IMG.jpg"), af.resolveCoverPath(withCover(a, "IMG.jpg")));
        assertEquals(tmp.resolve("sync").resolve("demo"), af.resolveSyncSiteRoot("demo"));
    }

    @Test
    public void resolveSyncPath_nullWithoutSiteDirOrId() throws Exception {
        AlbumsFile noDir = AlbumsFile.load(writeYaml(settings("demo") + album(sync("immich", ID_A))));
        assertNull(noDir.resolveSyncPath(noDir.getAlbums().getFirst()));

        AlbumsFile noId = AlbumsFile.load(writeYaml(album(sync("immich", ID_A))));
        noId.setSiteDir(tmp);
        assertNull(noId.resolveSyncPath(noId.getAlbums().getFirst()));
    }

    @Test
    public void displayName_fallsBackToMetadataThenSlug() throws Exception {
        AlbumsFile af = AlbumsFile.load(writeYaml(settings("demo") + album(sync("immich", ID_A))));
        af.setSiteDir(tmp);
        AlbumEntry a = af.getAlbums().getFirst();

        assertEquals("a", af.displayName(a), "no metadata yet: the slug");
        assertNull(af.displayDescription(a));

        SyncMetadataFile.writeStub(af.resolveSyncPath(a), "immich", ID_A, "The Way", "Walking &amp; talking");
        assertEquals("The Way", af.displayName(a));
        assertEquals("Walking &amp; talking", af.displayDescription(a));

        a.setName("Camino");
        a.setDescription("Mine");
        assertEquals("Camino", af.displayName(a), "albums.yaml wins");
        assertEquals("Mine", af.displayDescription(a));
    }

    @Test
    public void loadSyncMetadata_rereadsWhenFileChanges() throws Exception {
        AlbumsFile af = AlbumsFile.load(writeYaml(settings("demo") + album(sync("immich", ID_A))));
        af.setSiteDir(tmp);
        AlbumEntry a = af.getAlbums().getFirst();
        Path dir = af.resolveSyncPath(a);

        SyncMetadataFile.writeStub(dir, "immich", ID_A, "First", "");
        assertEquals("First", af.displayName(a));
        // A different length guarantees a different stamp even on a coarse-mtime filesystem.
        SyncMetadataFile.writeStub(dir, "immich", ID_A, "Second name", "");
        assertEquals("Second name", af.displayName(a));
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static String settings(String id) {
        return "settings:\n  id: " + id + "\n";
    }

    private static String album(String body) {
        return "albums:\n  - slug: a\n" + body;
    }

    private static String sync(String provider, String albumId) {
        return "    sync:\n      provider: " + provider + "\n      album_id: " + albumId + "\n";
    }

    private static AlbumEntry withCover(AlbumEntry a, String cover) {
        AlbumEntry copy = new AlbumEntry(a);
        copy.setCover(cover);
        return copy;
    }

    private void assertLoadFails(String yaml, String expected) throws Exception {
        Path f = writeYaml(yaml);
        AlbumsFileException e = assertThrows(AlbumsFileException.class, () -> AlbumsFile.load(f));
        assertTrue(e.getMessage().contains(expected), e.getMessage());
    }

    private Path writeYaml(String content) throws Exception {
        Path f = Files.createTempFile(tmp, "albums", ".yaml");
        Files.writeString(f, content, StandardCharsets.UTF_8);
        return f;
    }
}
