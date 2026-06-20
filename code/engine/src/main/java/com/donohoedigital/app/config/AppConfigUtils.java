/*
 * AppConfigUtils.java
 *
 * Created on March 8, 2003, 9:26 AM
 */

package com.donohoedigital.app.config;

import com.donohoedigital.config.ConfigManager;
import com.donohoedigital.config.ConfigUtils;

import java.io.File;

/**
 *
 * @author  donohoe
 */
public class AppConfigUtils
{
    public static final String SAVE_DIR = "save";
    public static final String BIN_DIR = "bin";
    public static final String CACHE_DIR = "cache";

    private static File saveDir = null;
    private static File binDir = null;
    private static File cacheDir = null;

    /**
     * Get the location for save files, creating the directory if not there.
     * the returned value is cached in the ConfigManager, so there is only
     * one per application (useful for locking)
     */
    public synchronized static File getSaveDir()
    {
        if (saveDir == null)
        {
            saveDir = getSaveDirLocation();
        }
        return saveDir;
    }

    /**
     * Get the location for the app's private bin dir, creating the directory if not there.
     * The returned value is cached.
     */
    public synchronized static File getBinDir()
    {
        if (binDir == null)
        {
            binDir = getBinDirLocation();
        }
        return binDir;
    }

    /**
     * Get the location for the app's cache dir, creating the directory if not there.
     * Intended for regenerable data (e.g. thumbnails). The returned value is cached.
     */
    public synchronized static File getCacheDir()
    {
        if (cacheDir == null)
        {
            cacheDir = getCacheDirLocation();
        }
        return cacheDir;
    }

    /**
     * Get the location for save files, create if it doesn't exist
     */
    private static File getSaveDirLocation()
    {
        File parentDir = ConfigManager.getUserHome();
        File saveDir = new File(parentDir, SAVE_DIR);
        ConfigUtils.verifyNewDirectory(saveDir);
        return saveDir;
    }

    /**
     * Get the location for the app's private bin dir, create if it doesn't exist
     */
    private static File getBinDirLocation()
    {
        File parentDir = ConfigManager.getUserHome();
        File binDir = new File(parentDir, BIN_DIR);
        ConfigUtils.verifyNewDirectory(binDir);
        return binDir;
    }

    /**
     * Get the location for the cache dir, create if it doesn't exist
     */
    private static File getCacheDirLocation()
    {
        File parentDir = ConfigManager.getUserHome();
        File cacheDir = new File(parentDir, CACHE_DIR);
        ConfigUtils.verifyNewDirectory(cacheDir);
        return cacheDir;
    }
}
