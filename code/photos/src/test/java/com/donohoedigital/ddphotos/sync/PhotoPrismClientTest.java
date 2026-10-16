package com.donohoedigital.ddphotos.sync;

import com.donohoedigital.ddphotos.config.SyncEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;

import static com.donohoedigital.ddphotos.sync.FixtureServer.assertMessage;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link PhotoPrismClient} against a {@link FixtureServer} replaying responses recorded from a
 * real PhotoPrism instance ({@code testdata/photoprism}, copied from ddphotos by
 * {@code tools/bin/sync-photoprism-fixtures.sh}).
 */
public class PhotoPrismClientTest {

    private static final String PASSWORD = "Abc123-dEf456-ghI789-jkL012";
    private static final String SESSION = "/api/v1/session";
    private static final String ALBUMS = "/api/v1/albums";

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

    /** photogen's {@code TestNormalizePhotoPrismURL} cases. */
    @Test
    public void normalizeUrl() throws Exception {
        assertEquals("http://localhost:2342", PhotoPrismClient.normalizeUrl("http://localhost:2342"));
        assertEquals("http://localhost:2342", PhotoPrismClient.normalizeUrl("http://localhost:2342/"));
        assertEquals("http://localhost:2342", PhotoPrismClient.normalizeUrl("http://localhost:2342/api/v1"));
        assertEquals("http://localhost:2342", PhotoPrismClient.normalizeUrl("http://localhost:2342/api/v1/"));
        assertEquals("http://localhost:2342", PhotoPrismClient.normalizeUrl("http://localhost:2342/api"));
        assertEquals("http://localhost:2342", PhotoPrismClient.normalizeUrl("http://localhost:2342/library"));
        assertEquals("https://example.com/photoprism", PhotoPrismClient.normalizeUrl("https://example.com/photoprism"));
        assertEquals("https://example.com/photoprism",
                     PhotoPrismClient.normalizeUrl("https://example.com/photoprism/api/v1"));
        // Only a whole trailing segment is a suffix.
        assertEquals("https://example.com/myapi", PhotoPrismClient.normalizeUrl("https://example.com/myapi"));
        assertEquals("https://example.com/mylibrary", PhotoPrismClient.normalizeUrl("https://example.com/mylibrary"));
    }

    @Test
    public void normalizeUrl_rejects() {
        assertMessage(() -> PhotoPrismClient.normalizeUrl(""), "The PhotoPrism URL is empty");
        assertMessage(() -> PhotoPrismClient.normalizeUrl("localhost:2342"), "needs an http:// or https:// prefix");
    }

    // ── test() ──────────────────────────────────────────────────────────────

    @Test
    public void test_recordedSessionHasFullAccess() throws Exception {
        server_.route(SESSION, 200, fixture("session.json"));
        ConnectionTest t = new PhotoPrismClient(url_ + "/library", PASSWORD).test();
        assertTrue(t.isComplete(), t.missingPermissions().toString());
        assertEquals("Bearer " + PASSWORD, server_.header(SESSION, "Authorization"),
                     "the app password is sent as a bearer token");
        assertEquals("", server_.header(SESSION, "Upgrade"));
    }

    @Test
    public void test_restrictedScopeLacksFullAccess() throws Exception {
        server_.route(SESSION, 200, "{\"scope\":\"webdav\",\"provider\":\"application\"}");
        ConnectionTest t = new PhotoPrismClient(url_, PASSWORD).test();
        assertEquals(List.of(PhotoPrismClient.FULL_ACCESS), t.missingPermissions());
    }

    @Test
    public void test_badPassword() {
        server_.route(SESSION, 401, "{\"error\":\"Invalid credentials\",\"code\":401}");
        SyncException e = assertMessage(() -> new PhotoPrismClient(url_, PASSWORD).test(),
                                        "rejected the app password (401: Invalid credentials)");
        assertFalse(e.getMessage().contains(PASSWORD));
    }

    @Test
    public void test_notPhotoPrism() {
        server_.route(SESSION, 200, "<html>hello</html>\n  : : [", "text/html");
        assertMessage(() -> new PhotoPrismClient(url_, PASSWORD).test(), "not like PhotoPrism");
    }

    @Test
    public void test_jsonWithoutScopeIsNotPhotoPrism() {
        // Immich answers unknown routes with JSON too.
        server_.route(SESSION, 200, "{\"message\":\"hello\"}");
        assertMessage(() -> new PhotoPrismClient(url_, PASSWORD).test(), "not like PhotoPrism");
    }

    @Test
    public void test_connectionRefused() throws Exception {
        int port;
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        }
        SyncException e = assertMessage(() -> new PhotoPrismClient("http://127.0.0.1:" + port, PASSWORD).test(),
                                        "Could not reach PhotoPrism at http://127.0.0.1:" + port);
        assertFalse(e.getMessage().contains(PASSWORD));
    }

    // ── listAlbums() ────────────────────────────────────────────────────────

    @Test
    public void listAlbums_recorded() throws Exception {
        server_.route(ALBUMS, 200, fixture("albums.json"));
        List<SyncAlbumInfo> albums = new PhotoPrismClient(url_, PASSWORD).listAlbums();

        assertEquals(3, albums.size());
        for (SyncAlbumInfo a : albums) {
            assertTrue(SyncEntry.isPhotoPrismAlbumId(a.id()), a.id());
            assertNotNull(a.name());
            assertTrue(a.assetCount() >= 0);
            assertNull(a.owner(), "PhotoPrism albums have no owner name");
        }
        assertTrue(albums.contains(new SyncAlbumInfo("atmyuuj99diyoq1s", "The Way",
                                                     "530 mile walk across Northern Spain", 23, null)));
        assertEquals(List.of("/api/v1/albums?type=album&order=name&count=500&offset=0"), server_.requests());
    }

    @Test
    public void listAlbums_pages() throws Exception {
        server_.route(ALBUMS + "?type=album&order=name&count=2&offset=0", 200,
                      "[{\"UID\":\"a000000000000001\",\"Title\":\"One\"},{\"UID\":\"a000000000000002\",\"Title\":\"Two\"}]");
        server_.route(ALBUMS + "?type=album&order=name&count=2&offset=2", 200,
                      "[{\"UID\":\"a000000000000003\",\"Title\":\"Three\"}]");
        List<SyncAlbumInfo> albums = new PhotoPrismClient(url_, PASSWORD, 2).listAlbums();
        assertEquals(List.of("One", "Two", "Three"), albums.stream().map(SyncAlbumInfo::name).toList());
    }

    @Test
    public void listAlbums_fullLastPageAsksOnce() throws Exception {
        server_.route(ALBUMS + "?type=album&order=name&count=1&offset=0", 200, "[{\"UID\":\"a000000000000001\"}]");
        server_.route(ALBUMS + "?type=album&order=name&count=1&offset=1", 200, "[]");
        assertEquals(1, new PhotoPrismClient(url_, PASSWORD, 1).listAlbums().size());
        assertEquals(2, server_.requests().size());
    }

    @Test
    public void listAlbums_forbidden() {
        server_.route(ALBUMS, 403, "{\"error\":\"Permission denied\",\"code\":403}");
        assertMessage(() -> new PhotoPrismClient(url_, PASSWORD).listAlbums(),
                      "lacks a required permission (403: Permission denied). Create it with Full Access.");
    }

    @Test
    public void badlyFormedPassword_isASyncException() {
        assertMessage(() -> new PhotoPrismClient(url_, "“" + PASSWORD + "”").listAlbums(), "app password");
        assertMessage(() -> new PhotoPrismClient(url_, "abc def ghi jkl mno").test(), "app password");
        assertTrue(server_.requests().isEmpty(), "nothing is sent with a password that cannot be valid");
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static String fixture(String name) throws IOException {
        return FixtureServer.fixture("photoprism", name, "sync-photoprism-fixtures.sh");
    }
}
