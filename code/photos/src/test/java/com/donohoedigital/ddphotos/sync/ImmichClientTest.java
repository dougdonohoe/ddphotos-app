package com.donohoedigital.ddphotos.sync;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ImmichClient} against a local HTTP server replaying responses recorded from a real
 * Immich server ({@code testdata/immich}, copied from ddphotos by
 * {@code tools/bin/sync-immich-fixtures.sh}).
 */
public class ImmichClientTest {

    private static final String KEY = "oeW00smAhMkRo0GYb7zAAQWr8mUFNvYSxO7P0TIeg";

    private HttpServer server_;
    private String url_;
    /** path -> {status, body} or {status, body, content type}; absent paths answer 404. */
    private final Map<String, Object[]> routes_ = new ConcurrentHashMap<>();
    private final Map<String, String> seenKeys_ = new ConcurrentHashMap<>();
    private final Map<String, String> seenUpgrades_ = new ConcurrentHashMap<>();

    @BeforeEach
    public void start() throws IOException {
        server_ = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server_.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            seenKeys_.put(path, Objects.requireNonNullElse(exchange.getRequestHeaders().getFirst("x-api-key"), ""));
            String upgrade = exchange.getRequestHeaders().getFirst("Upgrade");
            if (upgrade != null) seenUpgrades_.put(path, upgrade);
            Object[] r = routes_.getOrDefault(path, new Object[]{404, "{\"message\":\"Not Found\"}"});
            byte[] body = ((String) r[1]).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", r.length > 2 ? (String) r[2] : "application/json");
            exchange.sendResponseHeaders((Integer) r[0], body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server_.start();
        url_ = "http://127.0.0.1:" + server_.getAddress().getPort();
    }

    @AfterEach
    public void stop() {
        server_.stop(0);
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
        routes_.put("/api/api-keys/me", new Object[]{200, fixture("api-key-me.json")});
        ConnectionTest t = new ImmichClient(url_ + "/api", KEY).test();
        assertTrue(t.isComplete(), t.missingPermissions().toString());
        assertEquals(List.of(), t.uncheckedPermissions());
        assertEquals(KEY, seenKeys_.get("/api/api-keys/me"), "the key goes in the x-api-key header");
        // Immich drops a connection that asks to upgrade to HTTP/2 (h2c).
        assertEquals(Map.of(), seenUpgrades_);
    }

    @Test
    public void test_reportsMissingPermissions() throws Exception {
        routes_.put("/api/api-keys/me", new Object[]{200, "{\"name\":\"k\",\"permissions\":[\"album.read\"]}"});
        ConnectionTest t = new ImmichClient(url_, KEY).test();
        assertEquals(List.of("asset.read", "asset.download"), t.missingPermissions());
    }

    @Test
    public void test_allPermissionCoversEverything() throws Exception {
        routes_.put("/api/api-keys/me", new Object[]{200, "{\"permissions\":[\"all\"]}"});
        assertTrue(new ImmichClient(url_, KEY).test().isComplete());
    }

    @Test
    public void test_olderServerFallsBackToAlbums() throws Exception {
        // Listing albums proves the key and album.read, and nothing about the other two.
        routes_.put("/api/albums", new Object[]{200, fixture("albums.json")});
        ConnectionTest t = new ImmichClient(url_, KEY).test();
        assertEquals(List.of(), t.missingPermissions());
        assertEquals(List.of("asset.read", "asset.download"), t.uncheckedPermissions());
    }

    @Test
    public void test_badKey() {
        routes_.put("/api/api-keys/me", new Object[]{401,
                "{\"message\":\"Invalid API key\",\"error\":\"Unauthorized\",\"statusCode\":401}"});
        SyncException e = assertThrows(SyncException.class, () -> new ImmichClient(url_, KEY).test());
        assertTrue(e.getMessage().contains("rejected the API key (401: Invalid API key)"), e.getMessage());
        assertFalse(e.getMessage().contains(KEY));
    }

    @Test
    public void test_notImmich() {
        routes_.put("/api/api-keys/me", new Object[]{200, "<html>hello</html>\n  : : [", "text/html"});
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
        routes_.put("/api/albums", new Object[]{200, fixture("albums.json")});
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
        routes_.put("/api/albums", new Object[]{403,
                "{\"message\":\"Missing required permission: album.read\",\"statusCode\":403}"});
        SyncException e = assertThrows(SyncException.class, () -> new ImmichClient(url_, KEY).listAlbums());
        assertTrue(e.getMessage().contains("lacks a required permission (403: Missing required permission: album.read)"),
                   e.getMessage());
    }

    @Test
    public void listAlbums_ownerFromAlbumUsers() throws Exception {
        routes_.put("/api/albums", new Object[]{200, """
                [{"id":"ef8acfb8-43fb-4c63-90c0-307b88b8f97a","albumName":"A","description":"d","assetCount":3,
                  "albumUsers":[{"role":"editor","user":{"name":"Ed"}},{"role":"owner","user":{"name":"Olive"}}]}]
                """});
        SyncAlbumInfo a = new ImmichClient(url_, KEY).listAlbums().getFirst();
        assertEquals(new SyncAlbumInfo("ef8acfb8-43fb-4c63-90c0-307b88b8f97a", "A", "d", 3, "Olive"), a);
    }

    @Test
    public void badlyFormedKey_isASyncException() {
        // A key pasted into immich.env with smart quotes: the HTTP library throws
        // IllegalArgumentException for it, which the dialogs' background threads never caught.
        assertMessage(() -> new ImmichClient(url_, "\u201c" + KEY + "\u201d").listAlbums(), "API key");
        assertMessage(() -> new ImmichClient(url_, "abc def ghi jkl mno").test(), "API key");
        assertTrue(seenKeys_.isEmpty(), "nothing is sent with a key that cannot be valid");
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
        routes_.put("/api/albums", new Object[]{200, json.toString()});

        assertEquals(count, new ImmichClient(url_, KEY).listAlbums().size());
    }

    @Test
    public void listAlbums_unreadableJson_isNotReportedAsNotImmich() {
        routes_.put("/api/albums", new Object[]{200, "[{\"id\": "});
        SyncException e = assertThrows(SyncException.class, () -> new ImmichClient(url_, KEY).listAlbums());
        assertTrue(e.getMessage().contains("could not be read"), e.getMessage());
        assertFalse(e.getMessage().contains("not like Immich"), e.getMessage());
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static String fixture(String name) throws IOException {
        try (InputStream in = ImmichClientTest.class.getResourceAsStream("/testdata/immich/" + name)) {
            assertNotNull(in, "missing fixture " + name + " - run tools/bin/sync-immich-fixtures.sh");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private interface Call {
        void run() throws Exception;
    }

    private static void assertMessage(Call call, String expected) {
        SyncException e = assertThrows(SyncException.class, call::run);
        assertTrue(e.getMessage().contains(expected), e.getMessage());
    }
}
