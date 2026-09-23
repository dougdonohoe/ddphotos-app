package com.donohoedigital.ddphotos.sync;

import com.donohoedigital.ddphotos.PhotosConstants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.channels.UnresolvedAddressException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The calls the app makes to an Immich server (verified against Immich v3.2.2).  photogen does
 * the syncing; the app only checks credentials and lists albums to choose from, so this client
 * needs only {@code album.read} beyond a valid key.
 *
 * <ul>
 *   <li>{@code GET /api/api-keys/me}: accepted with any valid key, and returns the key's
 *       permissions, so one call checks reachability, the key, and what photogen needs.</li>
 *   <li>{@code GET /api/albums}: owned and shared albums, with {@code assetCount}.</li>
 * </ul>
 *
 * <p>Errors mirror photogen's wording ({@code provider_immich.go} {@code statusError}) and never
 * include the API key.  There are no retries: every call is made while the user waits.
 */
public class ImmichClient implements SyncClient {

    private static final Logger logger = LogManager.getLogger(ImmichClient.class);

    /** What photogen needs, as Immich names them (docs/CONFIGURATION.md). */
    public static final List<String> REQUIRED_PERMISSIONS = List.of("album.read", "asset.read", "asset.download");

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final String baseUrl_;
    private final String apiKey_;

    /**
     * @param url    the instance URL as the user wrote it; normalized here
     * @param apiKey the API key
     * @throws SyncException when the URL or the API key is not usable
     */
    public ImmichClient(String url, String apiKey) throws SyncException {
        String configuredUrl = url == null ? "" : url.strip();
        baseUrl_ = normalizeUrl(configuredUrl);
        apiKey_ = apiKey == null ? "" : apiKey.strip();
        // Checked here because the HTTP library rejects some characters (smart quotes pasted into
        // immich.env, say) with an unchecked exception.  Same rule as the credentials' dialog.
        if (!apiKey_.matches(PhotosConstants.REGEXP_IMMICH_API_KEY)) {
            throw new SyncException("The Immich API key is not valid: it should be only letters, digits, "
                                    + "'-' and '_'.  Check for quotes or spaces copied along with it.");
        }
    }

    /** The normalized instance URL, without {@code /api}. */
    public String getBaseUrl() { return baseUrl_; }

    @Override
    public ConnectionTest test() throws SyncException {
        Response r = get("/api/api-keys/me");
        if (r.status == 404) {
            // A server older than the api-keys/me route.  Listing albums still proves the key and
            // album.read; the other two permissions cannot be checked.
            listAlbums();
            return new ConnectionTest(null, List.of());
        }
        Map<?, ?> me = asObject(r.ok());
        List<String> granted = new ArrayList<>();
        for (Object p : Json.array(me, "permissions")) granted.add(String.valueOf(p));
        List<String> missing = new ArrayList<>();
        if (!granted.contains("all")) {
            for (String p : REQUIRED_PERMISSIONS) {
                if (!granted.contains(p)) missing.add(p);
            }
        }
        return new ConnectionTest(Json.string(me, "name"), missing);
    }

    @Override
    public List<SyncAlbumInfo> listAlbums() throws SyncException {
        Object body = get("/api/albums").ok();
        if (!(body instanceof List<?> list)) throw notImmich(null);
        List<SyncAlbumInfo> albums = new ArrayList<>(list.size());
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> a)) continue;
            String id = Json.string(a, "id");
            if (id == null) continue;
            albums.add(new SyncAlbumInfo(id, Json.string(a, "albumName"), Json.string(a, "description"),
                                         Json.integer(a, "assetCount", -1), owner(a)));
        }
        return albums;
    }

    /** The owner's name: Immich v3 has no {@code owner} field, but lists the owner in {@code albumUsers}. */
    private static String owner(Map<?, ?> album) {
        for (Object o : Json.array(album, "albumUsers")) {
            if (o instanceof Map<?, ?> u && "owner".equals(Json.string(u, "role"))) {
                Map<?, ?> user = Json.object(u, "user");
                if (user != null) return Json.string(user, "name");
            }
        }
        Map<?, ?> owner = Json.object(album, "owner");  // Immich v1/v2
        return owner != null ? Json.string(owner, "name") : null;
    }

    // ── URL ─────────────────────────────────────────────────────────────────

    /**
     * Port of photogen's {@code normalizeImmichURL}, minus the Docker rewrite (the app runs on the
     * host, where {@code localhost} means what it says).  Requires http or https and a host, drops
     * a trailing {@code /} and a final {@code /api} segment, and keeps any other path prefix, so an
     * instance behind a reverse proxy at {@code https://host/immich} works.
     */
    public static String normalizeUrl(String raw) throws SyncException {
        String s = raw == null ? "" : raw.strip();
        if (s.isEmpty()) throw new SyncException("The Immich URL is empty.");
        URI u;
        try {
            u = new URI(s);
        } catch (URISyntaxException e) {
            throw new SyncException("\"" + s + "\" is not a valid URL.");
        }
        String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new SyncException("\"" + s + "\" needs an http:// or https:// prefix.");
        }
        if (u.getHost() == null || u.getHost().isEmpty()) {
            throw new SyncException("\"" + s + "\" has no host.");
        }
        String path = u.getPath() == null ? "" : u.getPath();
        if (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        int slash = path.lastIndexOf('/');
        if (path.substring(slash + 1).equalsIgnoreCase("api")) path = path.substring(0, Math.max(slash, 0));
        try {
            // Query and fragment are dropped, as photogen drops them.
            return new URI(scheme, u.getUserInfo(), u.getHost(), u.getPort(), path, null, null).toString();
        } catch (URISyntaxException e) {
            throw new SyncException("\"" + s + "\" is not a valid URL.");
        }
    }

    // ── HTTP ────────────────────────────────────────────────────────────────

    private record Response(ImmichClient client, String path, int status, String contentType, String body) {
        /** The parsed body of a 2xx response; anything else becomes the matching error. */
        Object ok() throws SyncException {
            if (status < 200 || status >= 300) throw client.statusError(path, status, body);
            try {
                return Json.parse(body);
            } catch (IllegalArgumentException e) {
                // JSON that does not parse came from an API, so blaming the URL would mislead;
                // anything else is some other web server.
                if (contentType.startsWith("application/json")) throw client.unreadable(path, e);
                throw client.notImmich(e);
            }
        }
    }

    private Response get(String path) throws SyncException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl_ + path))
                .GET()
                .header("x-api-key", apiKey_)
                .header("Accept", "application/json")
                .header("User-Agent", PhotosConstants.APP_DISPLAY_NAME + "/" + PhotosConstants.VERSION)
                .timeout(REQUEST_TIMEOUT)
                .build();
        try (HttpClient client = HttpClient.newBuilder()
                // The default (HTTP/2) sends an h2c upgrade on http:// URLs, and Immich drops the
                // connection on it ("header parser received no bytes").
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(CONNECT_TIMEOUT)
                .build()) {
            HttpResponse<String> r = client.send(request, HttpResponse.BodyHandlers.ofString());
            logger.info("immich GET {} -> {}", path, r.statusCode());
            String contentType = r.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
            return new Response(this, path, r.statusCode(), contentType, r.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SyncException("Interrupted while contacting Immich.", e);
        } catch (HttpConnectTimeoutException e) {
            throw unreachable("the connection timed out", e);
        } catch (HttpTimeoutException e) {
            throw unreachable("it did not answer within " + REQUEST_TIMEOUT.toSeconds() + " seconds", e);
        } catch (ConnectException e) {
            throw unreachable(e.getCause() instanceof UnresolvedAddressException
                              ? "the host name did not resolve" : "the connection was refused", e);
        } catch (IOException e) {
            throw unreachable(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName(), e);
        }
    }

    private SyncException unreachable(String reason, Exception cause) {
        logger.info("immich unreachable at {}: {}", baseUrl_, cause.toString());
        return new SyncException("Could not reach Immich at " + baseUrl_ + ": " + reason + ".", cause);
    }

    private SyncException notImmich(Exception cause) {
        return new SyncException("The server at " + baseUrl_ + " answered, but not like Immich. "
                + "Check the URL.", cause);
    }

    private SyncException unreadable(String path, Exception cause) {
        logger.info("immich GET {}: unreadable response: {}", path, cause.toString());
        return new SyncException("Immich answered GET " + path + ", but its response could not be read.", cause);
    }

    /** photogen's {@code statusError}, reworded for a dialog. */
    SyncException statusError(String path, int status, String body) {
        String detail = detail(body);
        String suffix = detail.isEmpty() ? "" : ": " + detail;
        return switch (status) {
            case 401 -> new SyncException("Immich rejected the API key (401" + suffix + "). Check the API key.");
            case 403 -> new SyncException("The Immich API key lacks a required permission (403" + suffix
                    + "). It needs " + String.join(", ", REQUIRED_PERMISSIONS) + ".");
            default -> new SyncException("Immich GET " + path + " failed (" + status + suffix + ").");
        };
    }

    /** Immich's {@code message}, plus any {@code errors[]} detail; empty when the body has none. */
    private static String detail(String body) {
        try {
            if (!(Json.parse(body) instanceof Map<?, ?> m)) return "";
            StringBuilder sb = new StringBuilder();
            String message = Json.string(m, "message");
            if (message != null) sb.append(message);
            for (Object o : Json.array(m, "errors")) {
                if (o instanceof Map<?, ?> err && Json.string(err, "message") != null) {
                    sb.append(sb.isEmpty() ? "" : "; ").append(Json.string(err, "message"));
                }
            }
            return sb.toString();
        } catch (RuntimeException e) {
            return "";
        }
    }

    private Map<?, ?> asObject(Object body) throws SyncException {
        if (body instanceof Map<?, ?> m) return m;
        throw notImmich(null);
    }
}
