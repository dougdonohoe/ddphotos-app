package com.donohoedigital.ddphotos.sync;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;

import static com.donohoedigital.ddphotos.sync.FixtureServer.assertMessage;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ImmichClient} against a {@link FixtureServer} replaying responses recorded from a real
 * Immich server ({@code testdata/immich}, copied from ddphotos by
 * {@code tools/bin/sync-immich-fixtures.sh}).
 */
public class ImmichClientTest {

    private static final String KEY = "oeW00smAhMkRo0GYb7zAAQWr8mUFNvYSxO7P0TIeg";

    private FixtureServer server_;
    private String url_;

    @BeforeEach
    public void start() throws IOException {
        server_ = new FixtureServer();
        url_ = server_.url();
    }

    @AfterEach
    public void stop() {
        server_.close();
    }

    // ── URL ─────────────────────────────────────────────────────────────────

    @Test
    public void normalizeUrl() throws Exception {
        assertEquals("http://localhost:2283", ImmichClient.normalizeUrl("http://localhost:2283"));
        assertEquals("http://localhost:2283", ImmichClient.normalizeUrl(" http://localhost:2283/ "));
        assertEquals("http://localhost:2283", ImmichClient.normalizeUrl("http://localhost:2283/api"));
        assertEquals("http://localhost:2283", ImmichClient.normalizeUrl("http://localhost:2283/API/"));
        assertEquals("https://host/immich", ImmichClient.normalizeUrl("https://host/immich/api?x=1#f"));
        assertEquals("https://host/apiary", ImmichClient.normalizeUrl("https://host/apiary"));
        assertEquals("HTTPS://host".toLowerCase(), ImmichClient.normalizeUrl("HTTPS://host"));
    }

    @Test
    public void normalizeUrl_rejects() {
        assertMessage(() -> ImmichClient.normalizeUrl(""), "empty");
        assertMessage(() -> ImmichClient.normalizeUrl("localhost:2283"), "needs an http:// or https:// prefix");
        assertMessage(() -> ImmichClient.normalizeUrl("ftp://host"), "needs an http:// or https:// prefix");
        assertMessage(() -> ImmichClient.normalizeUrl("http:///nohost"), "has no host");
    }

    // ── test() ──────────────────────────────────────────────────────────────

    @Test
    public void test_recordedKeyHasEveryPermission() throws Exception {
        server_.route("/api/api-keys/me", 200, fixture("api-key-me.json"));
        ConnectionTest t = new ImmichClient(url_ + "/api", KEY).test();
        assertTrue(t.isComplete(), t.missingPermissions().toString());
        assertEquals(List.of(), t.uncheckedPermissions());
        assertEquals(KEY, server_.header("/api/api-keys/me", "x-api-key"), "the key goes in the x-api-key header");
        // Immich drops a connection that asks to upgrade to HTTP/2 (h2c).
        assertEquals("", server_.header("/api/api-keys/me", "Upgrade"));
    }

    @Test
    public void test_reportsMissingPermissions() throws Exception {
        server_.route("/api/api-keys/me", 200, "{\"name\":\"k\",\"permissions\":[\"album.read\"]}");
        ConnectionTest t = new ImmichClient(url_, KEY).test();
        assertEquals(List.of("asset.read", "asset.download"), t.missingPermissions());
    }

    @Test
    public void test_allPermissionCoversEverything() throws Exception {
        server_.route("/api/api-keys/me", 200, "{\"permissions\":[\"all\"]}");
        assertTrue(new ImmichClient(url_, KEY).test().isComplete());
    }

    @Test
    public void test_olderServerFallsBackToAlbums() throws Exception {
        // Listing albums proves the key and album.read, and nothing about the other two.
        server_.route("/api/albums", 200, fixture("albums.json"));
        ConnectionTest t = new ImmichClient(url_, KEY).test();
        assertEquals(List.of(), t.missingPermissions());
        assertEquals(List.of("asset.read", "asset.download"), t.uncheckedPermissions());
    }

    @Test
    public void test_badKey() {
        server_.route("/api/api-keys/me", 401,
                "{\"message\":\"Invalid API key\",\"error\":\"Unauthorized\",\"statusCode\":401}");
        SyncException e = assertThrows(SyncException.class, () -> new ImmichClient(url_, KEY).test());
        assertTrue(e.getMessage().contains("rejected the API key (401: Invalid API key)"), e.getMessage());
        assertFalse(e.getMessage().contains(KEY));
    }

    @Test
    public void test_notImmich() {
        server_.route("/api/api-keys/me", 200, "<html>hello</html>\n  : : [", "text/html");
        SyncException e = assertThrows(SyncException.class, () -> new ImmichClient(url_, KEY).test());
        assertTrue(e.getMessage().contains("not like Immich"), e.getMessage());
    }

    @Test
    public void test_connectionRefused() throws Exception {
        int port;
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        }
        SyncException e = assertThrows(SyncException.class,
                () -> new ImmichClient("http://127.0.0.1:" + port, KEY).test());
        assertTrue(e.getMessage().startsWith("Could not reach Immich at http://127.0.0.1:" + port), e.getMessage());
        assertFalse(e.getMessage().contains(KEY));
    }

    // ── listAlbums() ────────────────────────────────────────────────────────

    @Test
    public void listAlbums_recorded() throws Exception {
        server_.route("/api/albums", 200, fixture("albums.json"));
        List<SyncAlbumInfo> albums = new ImmichClient(url_, KEY).listAlbums();

        assertFalse(albums.isEmpty());
        for (SyncAlbumInfo a : albums) {
            assertTrue(com.donohoedigital.ddphotos.config.SyncEntry.isImmichAlbumId(a.id()), a.id());
            assertNotNull(a.name());
            assertTrue(a.assetCount() >= 0);
            assertNotNull(a.owner(), "the owner comes from albumUsers");
        }
    }

    @Test
    public void listAlbums_forbidden() {
        server_.route("/api/albums", 403,
                "{\"message\":\"Missing required permission: album.read\",\"statusCode\":403}");
        SyncException e = assertThrows(SyncException.class, () -> new ImmichClient(url_, KEY).listAlbums());
        assertTrue(e.getMessage().contains("lacks a required permission (403: Missing required permission: album.read)"),
                   e.getMessage());
    }

    @Test
    public void listAlbums_ownerFromAlbumUsers() throws Exception {
        server_.route("/api/albums", 200, """
                [{"id":"ef8acfb8-43fb-4c63-90c0-307b88b8f97a","albumName":"A","description":"d","assetCount":3,
                  "albumUsers":[{"role":"editor","user":{"name":"Ed"}},{"role":"owner","user":{"name":"Olive"}}]}]
                """);
        SyncAlbumInfo a = new ImmichClient(url_, KEY).listAlbums().getFirst();
        assertEquals(new SyncAlbumInfo("ef8acfb8-43fb-4c63-90c0-307b88b8f97a", "A", "d", 3, "Olive"), a);
    }

    @Test
    public void badlyFormedKey_isASyncException() {
        // A key pasted into immich.env with smart quotes: the HTTP library throws
        // IllegalArgumentException for it, which the dialogs' background threads never caught.
        assertMessage(() -> new ImmichClient(url_, "\u201c" + KEY + "\u201d").listAlbums(), "API key");
        assertMessage(() -> new ImmichClient(url_, "abc def ghi jkl mno").test(), "API key");
        assertTrue(server_.requests().isEmpty(), "nothing is sent with a key that cannot be valid");
    }

    @Test
    public void listAlbums_largeLibrary() throws Exception {
        // Over snakeyaml-engine's default 3,145,728 code point limit, which a library of a few
        // thousand albums reaches.
        String description = "x".repeat(10_000);
        StringBuilder json = new StringBuilder("[");
        int count = 400;
        for (int i = 0; i < count; i++) {
            if (i > 0) json.append(',');
            json.append("{\"id\":\"%08d-43fb-4c63-90c0-307b88b8f97a\",\"albumName\":\"A%d\",\"description\":\"%s\"}"
                                .formatted(i, i, description));
        }
        json.append(']');
        assertTrue(json.length() > 3_145_728);
        server_.route("/api/albums", 200, json.toString());

        assertEquals(count, new ImmichClient(url_, KEY).listAlbums().size());
    }

    @Test
    public void listAlbums_unreadableJson_isNotReportedAsNotImmich() {
        server_.route("/api/albums", 200, "[{\"id\": ");
        SyncException e = assertThrows(SyncException.class, () -> new ImmichClient(url_, KEY).listAlbums());
        assertTrue(e.getMessage().contains("could not be read"), e.getMessage());
        assertFalse(e.getMessage().contains("not like Immich"), e.getMessage());
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static String fixture(String name) throws IOException {
        return FixtureServer.fixture("immich", name, "sync-immich-fixtures.sh");
    }
}
