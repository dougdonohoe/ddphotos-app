package com.donohoedigital.ddphotos.sync;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A local HTTP server replaying canned responses, so the sync clients can be tested without a
 * real Immich or PhotoPrism.  A route is a path, or a path and query when the response depends
 * on the query (a page of a listing, say).  Unrouted requests answer 404.
 */
final class FixtureServer implements AutoCloseable {

    private final HttpServer server_;
    /** path or path?query -> {status, body} or {status, body, content type}. */
    private final Map<String, Object[]> routes_ = new ConcurrentHashMap<>();
    /** path -> header name (lowercase) -> last value seen. */
    private final Map<String, Map<String, String>> headers_ = new ConcurrentHashMap<>();
    private final List<String> requests_ = new CopyOnWriteArrayList<>();

    FixtureServer() throws IOException {
        server_ = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server_.createContext("/", exchange -> {
            URI uri = exchange.getRequestURI();
            String path = uri.getPath();
            String withQuery = uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery();
            requests_.add(withQuery);
            Map<String, String> seen = headers_.computeIfAbsent(path, _ -> new ConcurrentHashMap<>());
            exchange.getRequestHeaders().forEach((name, values) ->
                    seen.put(name.toLowerCase(), values.getLast()));
            Object[] r = routes_.getOrDefault(withQuery,
                    routes_.getOrDefault(path, new Object[]{404, "{\"message\":\"Not Found\"}"}));
            byte[] body = ((String) r[1]).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", r.length > 2 ? (String) r[2] : "application/json");
            exchange.sendResponseHeaders((Integer) r[0], body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server_.start();
    }

    /** The server's base URL, such as {@code http://127.0.0.1:54321}. */
    String url() {
        return "http://127.0.0.1:" + server_.getAddress().getPort();
    }

    /** Answers {@code route} (a path, or path and query) with a JSON body. */
    void route(String route, int status, String body) {
        routes_.put(route, new Object[]{status, body});
    }

    /** Answers {@code route} with a body of the given content type. */
    void route(String route, int status, String body, String contentType) {
        routes_.put(route, new Object[]{status, body, contentType});
    }

    /** The last value of a request header sent to {@code path}; empty when it was not sent. */
    String header(String path, String name) {
        Map<String, String> seen = headers_.get(path);
        return seen == null ? "" : Objects.requireNonNullElse(seen.get(name.toLowerCase()), "");
    }

    /** Every request received, as path or path?query, in order. */
    List<String> requests() {
        return requests_;
    }

    @Override
    public void close() {
        server_.stop(0);
    }

    /** A recorded response from {@code testdata/<dir>/}, copied from ddphotos by {@code script}. */
    static String fixture(String dir, String name, String script) throws IOException {
        try (InputStream in = FixtureServer.class.getResourceAsStream("/testdata/" + dir + "/" + name)) {
            assertNotNull(in, "missing fixture " + name + " - run tools/bin/" + script);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    interface Call {
        void run() throws Exception;
    }

    /** Asserts that {@code call} throws a {@link SyncException} whose message contains {@code expected}. */
    static SyncException assertMessage(Call call, String expected) {
        SyncException e = assertThrows(SyncException.class, call::run);
        assertTrue(e.getMessage().contains(expected), e.getMessage());
        return e;
    }
}
