package com.donohoedigital.ddphotos;

import com.donohoedigital.base.Version;
import com.donohoedigital.gui.DDOption;

import java.util.prefs.Preferences;

public class PhotosConstants {
    public static final String APP_NAME = "ddphotos";
    public static final String APP_DISPLAY_NAME = "DD Photos"; // should match installer app name
    public static final String PREFS_NODE_APP = "ddphotos-app";

    /** Prefs key (under {@link #PREFS_NODE_APP}) holding the version {@link UpdateCheck} last told the user about. */
    public static final String PREFS_KEY_UPDATE_NOTIFIED = "update.notified.version";

    // -------------------------------------------------------------------------
    // Text field validation patterns
    //
    // DDTextField.setRegExp() matches the whole trimmed value, so these need no
    // anchors. Keep them here so the same kind of field validates the same way
    // in every dialog and panel.
    // -------------------------------------------------------------------------

    /** Any non-empty text: the field is required. */
    public static final String REGEXP_REQUIRED = ".+";

    /** Any text, empty included: the field is optional. */
    public static final String REGEXP_OPTIONAL = ".*";

    /**
     * An album slug: starts with a letter or digit, then letters, digits, underscores
     * and hyphens. A slug is used unescaped in URLs, so spaces and dots are not allowed.
     * Matches photogen's slugPattern (albums_config.go).
     */
    public static final String REGEXP_SLUG = "[a-zA-Z0-9][a-zA-Z0-9_-]*";

    /**
     * A site id (settings.id): lower case only, and no underscores. Stricter than a slug
     * because photogen rejects anything else at build time (validSiteID in config.go).
     */
    public static final String REGEXP_SITE_ID = "[a-z0-9][a-z0-9-]*";

    /** A base name (a key in albums.yaml): the same rule as a site id. */
    public static final String REGEXP_BASE_NAME = REGEXP_SITE_ID;

    /** An optional web address: empty, or an http(s) URL with no spaces in it. */
    public static final String REGEXP_URL_OPTIONAL = "(https?://\\S+)?";

    /** A required web address: an http(s) URL with no spaces in it. */
    public static final String REGEXP_URL = "https?://\\S+";

    /**
     * An Immich API key.  Immich makes one from 32 random bytes in base64 with the non-word
     * characters removed, so about 43 letters and digits; the range is loose because keys from
     * older Immich versions were not checked.
     */
    public static final String REGEXP_IMMICH_API_KEY = "[A-Za-z0-9_-]{16,128}";

    /** A single folder name: no path separators and no whitespace. */
    public static final String REGEXP_FOLDER_NAME = "[^/\\\\\\s]+";

    // -------------------------------------------------------------------------
    // Text field length limits
    //
    // How much a field accepts, alongside the pattern above that validates it.
    // A limit used by only one field stays at that call site.
    // -------------------------------------------------------------------------

    /** An album slug, a site id or a base name. */
    public static final int MAX_SLUG_LENGTH = 64;

    /** A one-line value: a name, a URL, a password, a hint. */
    public static final int MAX_TEXT_LENGTH = 200;

    /** A file or folder path. */
    public static final int MAX_PATH_LENGTH = 500;

    /** A multi-line description. */
    public static final int MAX_DESCRIPTION_LENGTH = 500;

    // Gap between horizontal GUI items
    public static final int HORIZONTAL_GAP = 25;

    /**
     * Version history, most recent first. The current version is always the first entry -
     * to ship a new release, just add a line at the top (no commenting/uncommenting needed).
     */
    public static final Version VERSION = latest(
            new Version(1, 0, 12), // 1.0.12 (TBD)
            new Version(1, 0, 11), // 1.0.11 (9/18/2026)
            new Version(1, 0, 10), // 1.0.10 (9/13/2026)
            new Version(1, 0, 9), // 1.0.9 (9/11/2026)
            new Version(1, 0, 8), // 1.0.8 (8/28/2026)
            new Version(1, 0, 7), // 1.0.7 (8/26/2026)
            new Version(1, 0, 6), // 1.0.6 (8/25/2026)
            new Version(1, 0, 5), // 1.0.5 (8/19/2026)
            new Version(1, 0, 4), // 1.0.4 (8/17/2026)
            new Version(1, 0, 3), // 1.0.3 (8/12/2026)
            new Version(1, 0, 2), // 1.0.2 (8/7/2026)
            new Version(1, 0, 1), // 1.0.1 (8/4/2026)
            new Version(1, 0, 0), // 1.0.0 (8/3/2026)
            new Version(Version.TYPE_BETA, 1, 0, 8, 0), // 1.0.0b8 (Beta 8 7/31/2026)
            new Version(Version.TYPE_BETA, 1, 0, 7, 0), // 1.0.0b7 (Beta 7 7/29/2026)
            new Version(Version.TYPE_BETA, 1, 0, 6, 0), // 1.0.0b6 (Beta 6 6/21/2026)
            new Version(Version.TYPE_BETA, 1, 0, 5, 0), // 1.0.0b5 (Beta 5 6/20/2026)
            new Version(Version.TYPE_BETA, 1, 0, 4, 0), // 1.0.0b4 (Beta 4 6/19/2026)
            new Version(Version.TYPE_BETA, 1, 0, 3, 0), // 1.0.0b3 (Beta 3 6/14/2026)
            new Version(Version.TYPE_BETA, 1, 0, 2, 0), // 1.0.0b2 (Beta 2 6/14/2026)
            new Version(Version.TYPE_BETA, 1, 0, 1, 0)  // 1.0.0b1 (Beta 1 6/14/2026)
    );

    /** Returns the current (most recent) version, i.e. the first in the history. */
    private static Version latest(Version... history) {
        return history[0];
    }

    public static Preferences getAppPreferences() {
        return DDOption.getOptionPrefs(PREFS_NODE_APP);
    }
}
