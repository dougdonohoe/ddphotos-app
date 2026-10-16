package com.donohoedigital.ddphotos.sync;

import com.donohoedigital.ddphotos.PhotosConstants;

import java.net.http.HttpRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The calls the app makes to a PhotoPrism instance (verified against PhotoPrism 261007-65faaae5d).
 * photogen does the syncing; the app only checks credentials and lists albums to choose from.
 *
 * <ul>
 *   <li>{@code GET /api/v1/session}: the app password's own session, whose {@code scope} is
 *       {@code *} when the password has Full Access, which is what photogen needs.</li>
 *   <li>{@code GET /api/v1/albums?type=album}: the albums the password can see, paged with
 *       {@code count} and {@code offset}.</li>
 * </ul>
 *
 * <p>The app password is sent as a bearer token.  Errors mirror photogen's wording
 * ({@code provider_photoprism.go} {@code photoprismStatusError}) and never include it.
 */
public class PhotoPrismClient extends HttpSyncClient {

    private static final String DISPLAY_NAME = "PhotoPrism";
    /** The API lives under {@code /api/v1}, and the browser shows {@code /library}. */
    private static final List<String> API_SUFFIXES = List.of("api/v1", "api", "library");

    /** The session scope of an app password with Full Access. */
    static final String SCOPE_ALL = "*";
    /** What photogen needs, as PhotoPrism's Apps and Devices dialog names it. */
    public static final String FULL_ACCESS = "Full Access";

    private static final int PAGE_SIZE = 500;

    private final String password_;
    private final int pageSize_;

    /**
     * @param url      the site URL as the user wrote it; normalized here
     * @param password the app password
     * @throws SyncException when the URL or the password is not usable
     */
    public PhotoPrismClient(String url, String password) throws SyncException {
        this(url, password, PAGE_SIZE);
    }

    PhotoPrismClient(String url, String password, int pageSize) throws SyncException {
        super(DISPLAY_NAME, url, API_SUFFIXES);
        password_ = password == null ? "" : password.strip();
        pageSize_ = pageSize;
        // Checked here because the HTTP library rejects some characters (smart quotes pasted into
        // photoprism.env, say) with an unchecked exception.  Same rule as the credentials' dialog.
        if (!password_.matches(PhotosConstants.REGEXP_PHOTOPRISM_APP_PASSWORD)) {
            throw new SyncException("The PhotoPrism app password is not valid: it should be only letters, digits, "
                                    + "'-' and '_'.  Check for quotes or spaces copied along with it.");
        }
    }

    /** Normalizes a PhotoPrism URL: see {@link HttpSyncClient#normalizeUrl(String, String, List)}. */
    public static String normalizeUrl(String raw) throws SyncException {
        return normalizeUrl(raw, DISPLAY_NAME, API_SUFFIXES);
    }

    @Override
    public ConnectionTest test() throws SyncException {
        Map<?, ?> session = asObject(get("/api/v1/session").ok());
        String scope = Json.string(session, "scope");
        if (scope == null) throw notProvider(null);
        return new ConnectionTest(SCOPE_ALL.equals(scope.strip()) ? List.of() : List.of(FULL_ACCESS), List.of());
    }

    /**
     * Albums in name order.  Pages are requested until one comes back short; unlike photo
     * search, the album listing counts albums, so a page's length is enough to go on.
     */
    @Override
    public List<SyncAlbumInfo> listAlbums() throws SyncException {
        List<SyncAlbumInfo> albums = new ArrayList<>();
        for (int offset = 0; ; offset += pageSize_) {
            Object body = get("/api/v1/albums?type=album&order=name&count=" + pageSize_ + "&offset=" + offset).ok();
            if (!(body instanceof List<?> page)) throw notProvider(null);
            for (Object o : page) {
                if (!(o instanceof Map<?, ?> a)) continue;
                String id = Json.string(a, "UID");
                if (id == null) continue;
                albums.add(new SyncAlbumInfo(id, Json.string(a, "Title"), Json.string(a, "Description"),
                                             Json.integer(a, "PhotoCount", -1), null));
            }
            if (page.size() < pageSize_) return albums;
        }
    }

    // ── HTTP ────────────────────────────────────────────────────────────────

    @Override
    protected void authorize(HttpRequest.Builder request) {
        request.header("Authorization", "Bearer " + password_);
    }

    /** photogen's {@code photoprismStatusError}, reworded for a dialog. */
    @Override
    protected SyncException statusError(String path, int status, String body) {
        String detail = detail(body);
        String suffix = detail.isEmpty() ? "" : ": " + detail;
        return switch (status) {
            case 401 -> new SyncException("PhotoPrism rejected the app password (401" + suffix
                    + "). Check the app password.");
            case 403 -> new SyncException("The PhotoPrism app password lacks a required permission (403" + suffix
                    + "). Create it with " + FULL_ACCESS + ".");
            default -> new SyncException("PhotoPrism GET " + path + " failed (" + status + suffix + ").");
        };
    }

    /** PhotoPrism's {@code error}; empty when the body has none. */
    private static String detail(String body) {
        try {
            if (!(Json.parse(body) instanceof Map<?, ?> m)) return "";
            String error = Json.string(m, "error");
            return error != null ? error : "";
        } catch (RuntimeException e) {
            return "";
        }
    }
}
