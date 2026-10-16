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
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What every provider's client shares: URL normalization, one blocking JSON GET, and turning
 * network failures into messages for a dialog.  Subclasses add the provider's credentials to
 * each request and word its HTTP errors.  Port of photogen's {@code provider_http.go}, minus
 * retries (every call is made while the user waits) and the Docker rewrite (the app runs on
 * the host, where {@code localhost} means what it says).
 *
 * <p>No message may include the secret.
 */
public abstract class HttpSyncClient implements SyncClient {

    private static final Logger logger = LogManager.getLogger(HttpSyncClient.class);

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final String displayName_;
    private final String baseUrl_;

    /**
     * @param displayName the provider's name, for messages
     * @param url         the instance URL as the user wrote it; normalized here
     * @param apiSuffixes trailing path segments to drop from the URL, tried in order
     * @throws SyncException when the URL is not usable
     */
    protected HttpSyncClient(String displayName, String url, List<String> apiSuffixes) throws SyncException {
        displayName_ = displayName;
        baseUrl_ = normalizeUrl(url, displayName, apiSuffixes);
    }

    /** The normalized instance URL, without any API suffix. */
    public String getBaseUrl() { return baseUrl_; }

    /** The provider's name, for messages. */
    protected String displayName() { return displayName_; }

    /** Adds the credentials to a request. */
    protected abstract void authorize(HttpRequest.Builder request);

    /** The error for a non-2xx answer to {@code GET path}. */
    protected abstract SyncException statusError(String path, int status, String body);

    // ── URL ─────────────────────────────────────────────────────────────────

    /**
     * Port of photogen's {@code normalizeSyncURL}.  Requires http or https and a host, drops a
     * trailing {@code /} and the first of {@code apiSuffixes} that ends the path as whole
     * segments, and keeps any other path prefix, so an instance behind a reverse proxy at
     * {@code https://host/photos} works.
     */
    public static String normalizeUrl(String raw, String displayName, List<String> apiSuffixes) throws SyncException {
        String s = raw == null ? "" : raw.strip();
        if (s.isEmpty()) throw new SyncException("The " + displayName + " URL is empty.");
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
        for (String suffix : apiSuffixes) {
            String tail = "/" + suffix;
            if (path.length() >= tail.length()
                    && path.regionMatches(true, path.length() - tail.length(), tail, 0, tail.length())) {
                path = path.substring(0, path.length() - tail.length());
                break;
            }
        }
        try {
            // Query and fragment are dropped, as photogen drops them.
            return new URI(scheme, u.getUserInfo(), u.getHost(), u.getPort(), path, null, null).toString();
        } catch (URISyntaxException e) {
            throw new SyncException("\"" + s + "\" is not a valid URL.");
        }
    }

    // ── HTTP ────────────────────────────────────────────────────────────────

    /** One answer to a GET. */
    protected record Response(HttpSyncClient client, String path, int status, String contentType, String body) {
        /** The parsed body of a 2xx response; anything else becomes the matching error. */
        public Object ok() throws SyncException {
            if (status < 200 || status >= 300) throw client.statusError(path, status, body);
            try {
                return Json.parse(body);
            } catch (IllegalArgumentException e) {
                // JSON that does not parse came from an API, so blaming the URL would mislead;
                // anything else is some other web server.
                if (contentType.startsWith("application/json")) throw client.unreadable(path, e);
                throw client.notProvider(e);
            }
        }
    }

    /** GETs {@code path} (which may carry a query) relative to the base URL. */
    protected Response get(String path) throws SyncException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl_ + path))
                .GET()
                .header("Accept", "application/json")
                .header("User-Agent", PhotosConstants.APP_DISPLAY_NAME + "/" + PhotosConstants.VERSION)
                .timeout(REQUEST_TIMEOUT);
        authorize(builder);
        try (HttpClient client = HttpClient.newBuilder()
                // The default (HTTP/2) sends an h2c upgrade on http:// URLs, and Immich drops the
                // connection on it ("header parser received no bytes").
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(CONNECT_TIMEOUT)
                .build()) {
            HttpResponse<String> r = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            logger.info("{} GET {} -> {}", displayName_, path, r.statusCode());
            String contentType = r.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
            return new Response(this, path, r.statusCode(), contentType, r.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SyncException("Interrupted while contacting " + displayName_ + ".", e);
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
        logger.info("{} unreachable at {}: {}", displayName_, baseUrl_, cause.toString());
        return new SyncException("Could not reach " + displayName_ + " at " + baseUrl_ + ": " + reason + ".", cause);
    }

    /** The server answered, but not as this provider would. */
    protected SyncException notProvider(Exception cause) {
        return new SyncException("The server at " + baseUrl_ + " answered, but not like " + displayName_ + ". "
                + "Check the URL.", cause);
    }

    /** {@code body} as a JSON object, or the not-this-provider error. */
    protected Map<?, ?> asObject(Object body) throws SyncException {
        if (body instanceof Map<?, ?> m) return m;
        throw notProvider(null);
    }

    private SyncException unreadable(String path, Exception cause) {
        logger.info("{} GET {}: unreadable response: {}", displayName_, path, cause.toString());
        return new SyncException(displayName_ + " answered GET " + path + ", but its response could not be read.", cause);
    }
}
