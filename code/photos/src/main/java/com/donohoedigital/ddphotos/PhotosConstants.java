package com.donohoedigital.ddphotos;

import com.donohoedigital.base.Version;
import com.donohoedigital.gui.DDOption;

import java.util.prefs.Preferences;

public class PhotosConstants {
    public static final String APP_NAME = "ddphotos";
    public static final String APP_DISPLAY_NAME = "DD Photos"; // should match installer app name
    public static final String PREFS_NODE_APP = "ddphotos-app";

    /**
     * Version history, most recent first. The current version is always the first entry -
     * to ship a new release, just add a line at the top (no commenting/uncommenting needed).
     */
    public static final Version VERSION = latest(
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
