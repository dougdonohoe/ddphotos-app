package com.donohoedigital.ddphotos.runner;

import com.donohoedigital.ddphotos.config.Site;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RunRunner extends DdphotosRunner {

    /**
     * Vite's ready banner - the only URL {@code run} prints in this app:
     * {@code "  ➜  Local:   http://localhost:5173/"}. Its companion {@code Network:} line is
     * suppressed inside the container (do-run.sh exports {@code DDPHOTOS_HIDE_NETWORK_URL}), and
     * vite's "press h + enter" hint needs a TTY, which the GUI never gives it (--non-interactive).
     *
     * <p>The port is captured rather than hardcoded because {@code RUN_PORT} can be set in the
     * environment the app inherits. Requiring the {@code Local:} label and a {@code localhost} host
     * is what keeps a URL quoted in some error message from being launched.
     */
    static final Pattern LOCAL_URL =
            Pattern.compile("^\\s*(?:\\u279C\\s*)?Local:\\s+(https?://localhost:\\d+/?)$");

    /** The URL from vite's {@code Local:} line, or null if this is any other line. */
    static String localUrl(String line) {
        Matcher m = LOCAL_URL.matcher(line);
        return m.matches() ? m.group(1) : null;
    }

    @Override
    public String getSubCommand() {
        return "run";
    }

    @Override
    public boolean showsFailureFeedback() { return true; }

    @Override
    protected boolean usesNamedContainer() { return true; }

    @Override
    public String autoOpenUrl(String line) { return localUrl(line); }

    @Override
    protected List<FlagDef> subCommandFlagDefs(Site site) {
        return List.of();
    }
}
