package com.donohoedigital.ddphotos.config;

import java.util.regex.Pattern;

/**
 * Text handling for values that come from a sync provider, ported from ddphotos
 * {@code pkg/photogen/sync.go} {@code escapeSyncText}.
 *
 * <p>Upstream descriptions are plain text, while DD Photos renders album descriptions and
 * captions as HTML.  photogen escapes them on the way in and stores the escaped form in
 * {@code metadata.yaml}, so anything this app writes there must be escaped the same way.
 */
public final class SyncText {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    private SyncText() {}

    /**
     * Collapses whitespace runs (newlines and tabs included) to one space, trims, and escapes
     * {@code &}, {@code <} and {@code >}.  Double quotes are left alone, as photogen leaves them.
     * A null value is treated as empty.
     */
    public static String escape(String s) {
        if (s == null) return "";
        // Collapse before trimming: strip() does not treat NBSP as whitespace, but the pattern
        // does (as Go's strings.Fields does), so an edge run becomes one plain space first.
        String out = WHITESPACE.matcher(s).replaceAll(" ").strip();
        return out.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
