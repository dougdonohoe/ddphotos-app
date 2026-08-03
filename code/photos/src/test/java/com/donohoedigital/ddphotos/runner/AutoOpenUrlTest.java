package com.donohoedigital.ddphotos.runner;

import com.donohoedigital.ddphotos.PhotosUtils;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Unit tests for the preview-URL matchers behind {@link CommandRunner#autoOpenUrl}.  No Swing,
 * config or process dependency - exercises the static matchers directly, the way
 * {@link PrerequisiteCheckTest} does.
 *
 * <p>The fixtures are the real lines {@code run} and {@code serve} print, as the console sees them:
 * one line at a time, already put through {@link PhotosUtils#stripAnsi} and stripped of its
 * newline.  The negative cases matter more than the positive ones - a loose match here launches a
 * browser at whatever URL happened to scroll by.
 */
public class AutoOpenUrlTest {

    private static final String ESC = Character.toString(27);

    // ── run: vite's "Local:" banner ──────────────────────────────────────────

    /** The line as printed off a TTY: no color, but vite's arrow and column padding remain. */
    @Test
    public void viteLocalLine() {
        assertEquals("http://localhost:5173/",
                     RunRunner.localUrl("  ➜  Local:   http://localhost:5173/"));
    }

    /**
     * The same line when vite does emit color (picocolors green arrow, bold label, cyan URL with a
     * bold port inside it).  The console strips the escapes before we ever see the line, so this
     * asserts the pair works together rather than that the pattern handles ANSI itself.
     */
    @Test
    public void viteLocalLineColorized() {
        String colored = "  " + ESC + "[32m➜" + ESC + "[39m  " + ESC + "[1mLocal" + ESC + "[22m:   "
                         + ESC + "[36mhttp://localhost:" + ESC + "[1m5173" + ESC + "[22m/" + ESC + "[39m";
        assertEquals("http://localhost:5173/", RunRunner.localUrl(PhotosUtils.stripAnsi(colored)));
    }

    /** RUN_PORT is an env var the app can inherit, so the port is read off the line, not assumed. */
    @Test
    public void viteLocalLineNonDefaultPort() {
        assertEquals("http://localhost:3000/",
                     RunRunner.localUrl("  ➜  Local:   http://localhost:3000/"));
    }

    /** ddphotos' own progress line names the same port but has no scheme - not a URL, no match. */
    @Test
    public void runStartingLineIsNotAUrl() {
        assertNull(RunRunner.localUrl("  Starting dev server for my-photos at localhost:5173..."));
    }

    /**
     * do-run.sh suppresses this line inside the container because the container IP is unreachable
     * from the host.  If that ever regresses, opening it would send the user to a dead address.
     */
    @Test
    public void viteNetworkLineIgnored() {
        assertNull(RunRunner.localUrl("  ➜  Network: http://172.17.0.2:5173/"));
    }

    // ── serve: do-serve.sh's "Serving <site> at:" line ───────────────────────

    @Test
    public void serveLine() {
        assertEquals("http://localhost:8000",
                     ServeRunner.servingUrl("  Serving my-photos at: http://localhost:8000"));
    }

    @Test
    public void serveLineNonDefaultPort() {
        assertEquals("http://localhost:9000",
                     ServeRunner.servingUrl("  Serving my-photos at: http://localhost:9000"));
    }

    /** Site ids are slugs, but the matcher should not care what the id looks like. */
    @Test
    public void serveLineOddSiteId() {
        assertEquals("http://localhost:8000",
                     ServeRunner.servingUrl("  Serving my_photos-2026 at: http://localhost:8000"));
    }

    // ── neither runner reacts to the other's line, or to incidental URLs ──────

    @Test
    public void runnersDoNotMatchEachOther() {
        assertNull(RunRunner.localUrl("  Serving my-photos at: http://localhost:8000"));
        assertNull(ServeRunner.servingUrl("  ➜  Local:   http://localhost:5173/"));
    }

    /** A published-site URL from a deploy or export must never be opened by these runners. */
    @Test
    public void deployUrlIgnored() {
        assertNull(RunRunner.localUrl("Deploy done to https://photos.example.com"));
        assertNull(ServeRunner.servingUrl("Deploy done to https://photos.example.com"));
    }

    /** A localhost URL quoted mid-sentence - the anchors are what reject it. */
    @Test
    public void urlInsideAnErrorMessageIgnored() {
        String line = "Error: failed to load http://localhost:5173/albums/uganda/index.json";
        assertNull(RunRunner.localUrl(line));
        assertNull(ServeRunner.servingUrl(line));
    }

    /** The mount banner every command prints, plus plain empty output. */
    @Test
    public void ordinaryOutputIgnored() {
        assertNull(RunRunner.localUrl("DD Photos (using /usr/local/bin/docker) is mounting:"));
        assertNull(RunRunner.localUrl(""));
        assertNull(ServeRunner.servingUrl(""));
    }
}
