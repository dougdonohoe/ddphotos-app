package com.donohoedigital.ddphotos.runner;

import com.donohoedigital.ddphotos.config.Site;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ServeRunner extends DdphotosRunner {

    /**
     * The one line {@code serve} prints before handing off to Apache (do-serve.sh):
     * {@code "  Serving my-photos at: http://localhost:8000"}. As with {@link RunRunner}, the port
     * is captured rather than hardcoded ({@code SERVE_PORT} can be set in the environment) and the
     * label plus a {@code localhost} host keep incidental URLs from matching.
     */
    static final Pattern SERVING_URL =
            Pattern.compile("^\\s*Serving\\s+.*\\sat:\\s+(https?://localhost:\\d+/?)$");

    /** The URL from the {@code Serving <site> at:} line, or null if this is any other line. */
    static String servingUrl(String line) {
        Matcher m = SERVING_URL.matcher(line);
        return m.matches() ? m.group(1) : null;
    }

    @Override
    public String getSubCommand() {
        return "serve";
    }

    @Override
    public boolean showsFailureFeedback() { return true; }

    @Override
    protected boolean usesNamedContainer() { return true; }

    @Override
    public String autoOpenUrl(String line) { return servingUrl(line); }

    /**
     * do-serve.sh echoes the URL and only then execs Apache. Docker has already published the host
     * port by that point, so a browser that arrives first gets a refused connection rather than a
     * retry - hence a moment's grace before opening.
     */
    @Override
    public long autoOpenDelayMs() { return 1000; }

    @Override
    protected List<FlagDef> subCommandFlagDefs(Site site) {
        return List.of();
    }
}
