/*
 * EnginePrefs.java
 *
 * Created on February 23, 2003, 3:15 PM
 */

package com.donohoedigital.app.engine;

import com.donohoedigital.config.Prefs;
import com.donohoedigital.config.PropertyConfig;
import com.donohoedigital.gui.DDOption;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 *
 * @author  Doug Donohoe
 */
public class EnginePrefs {

    // Used to track "don't show again" for various info dialogs
    private static final String NODE_DIALOG_PHASE = "dialog-phase";

    private final Preferences prefs_;

    /**
     * Wrapper around Preferences, adding getters for options, with support for
     * fetching default value from .properties file (option.[name].default).
     */
    public EnginePrefs(Preferences prefs) {
        this.prefs_ = prefs;
    }

    public int getInt(String key, int def) {
        return prefs_.getInt(key, def);
    }

    public void putInt(String key, int value) {
        prefs_.putInt(key, value);
    }

    public static Preferences getDialogPrefs() {
        return Prefs.getUserPrefs(EnginePrefs.NODE_DIALOG_PHASE);
    }

    public static void clearDialogPrefs() {
        Preferences prefs = getDialogPrefs();
        try
        {
            prefs.clear();
        }
        catch (BackingStoreException bse)
        {
            Logger logger = LogManager.getLogger(EnginePrefs.class);
            logger.warn("Unable to clear prefs for node: " + EnginePrefs.NODE_DIALOG_PHASE);
        }
    }
}
