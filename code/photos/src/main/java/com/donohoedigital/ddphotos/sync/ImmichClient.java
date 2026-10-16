package com.donohoedigital.ddphotos.sync;

import com.donohoedigital.ddphotos.PhotosConstants;

import java.net.http.HttpRequest;
import java.util.ArrayList;
import java.util.List;
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
 * include the API key.
 */
public class ImmichClient extends HttpSyncClient {

    private static final String DISPLAY_NAME = "Immich";
    /** photogen strips only a final {@code /api}. */
    private static final List<String> API_SUFFIXES = List.of("api");

    /** What photogen needs, as Immich names them (docs/CONFIGURATION.md). */
    public static final List<String> REQUIRED_PERMISSIONS = List.of("album.read", "asset.read", "asset.download");

    private final String apiKey_;

    /**
     * @param url    the instance URL as the user wrote it; normalized here
     * @param apiKey the API key
     * @throws SyncException when the URL or the API key is not usable
     */
    public ImmichClient(String url, String apiKey) throws SyncException {
        super(DISPLAY_NAME, url, API_SUFFIXES);
        apiKey_ = apiKey == null ? "" : apiKey.strip();
        // Checked here because the HTTP library rejects some characters (smart quotes pasted into
        // immich.env, say) with an unchecked exception.  Same rule as the credentials' dialog.
        if (!apiKey_.matches(PhotosConstants.REGEXP_IMMICH_API_KEY)) {
            throw new SyncException("The Immich API key is not valid: it should be only letters, digits, "
                                    + "'-' and '_'.  Check for quotes or spaces copied along with it.");
        }
    }

    @Override
    public ConnectionTest test() throws SyncException {
        Response r = get("/api/api-keys/me");
        if (r.status() == 404) {
            // A server older than the api-keys/me route.  Listing albums still proves the key and
            // album.read; the other permissions cannot be checked.
            listAlbums();
            List<String> unchecked = REQUIRED_PERMISSIONS.stream().filter(p -> !p.equals("album.read")).toList();
            return new ConnectionTest(List.of(), unchecked);
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
        return new ConnectionTest(missing, List.of());
    }

    @Override
    public List<SyncAlbumInfo> listAlbums() throws SyncException {
        Object body = get("/api/albums").ok();
        if (!(body instanceof List<?> list)) throw notProvider(null);
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

    /** Normalizes an Immich URL: see {@link HttpSyncClient#normalizeUrl(String, String, List)}. */
    public static String normalizeUrl(String raw) throws SyncException {
        return normalizeUrl(raw, DISPLAY_NAME, API_SUFFIXES);
    }

    // ── HTTP ────────────────────────────────────────────────────────────────

    @Override
    protected void authorize(HttpRequest.Builder request) {
        request.header("x-api-key", apiKey_);
    }

    /** photogen's {@code statusError}, reworded for a dialog. */
    @Override
    protected SyncException statusError(String path, int status, String body) {
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
}
