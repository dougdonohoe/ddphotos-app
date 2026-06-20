package com.donohoedigital.config;

import com.donohoedigital.base.*;
import static com.donohoedigital.config.ApplicationType.*;

import java.io.*;

public class ConfigManager
{
    private static ConfigManager configMgr = null;
    private static String appName = null;

    // config things we load/store
    private final String locale;
    private final String extraModule;
    private final String[] modules;
    private final RuntimeDirectory runtimeDir;

    /**
     * Load config files for given appname of specified type
     */
    public ConfigManager(String sAppName, ApplicationType type)
    {
        this(sAppName, type, null, null, true);
    }

    /**
     * Load config files using extra module and locale
     */
    public ConfigManager(String sAppName, ApplicationType type, String sExtraModule, String sLocale, boolean allowOverrides)
    {
        ApplicationError.warnNotNull(configMgr, "ConfigManager already initialized");
        configMgr = this;
        appName = sAppName;
        locale = sLocale;
        extraModule = sExtraModule;
        runtimeDir = new DefaultRuntimeDirectory();

        // set root prefs node
        Prefs.setRootNodeName(sAppName);

        // modules to load
        modules = sExtraModule == null ? new String[]{"common", sAppName} :
                  new String[]{"common", sAppName, sExtraModule};

        // Load properties (needs to be available for data elements)
        new PropertyConfig(sAppName, modules, type, locale, allowOverrides);

        // these items only used on client
        if (type == CLIENT)
        {
            // Load help info
            new HelpConfig(modules, locale);

            // gui stuff (images/styles)
            loadGuiConfig();
        }
    }

    /**
     * load display-related config files (images and styles)
     */
    public void loadGuiConfig()
    {
        // Load images
        new ImageConfig(modules);

        // Load colors
        new StylesConfig(modules);
    }

    /**
     * Get configuration directory for application
     */
    public String getLocale()
    {
        return locale;
    }

    /**
     * Get extra module
     */
    public String getExtraModule()
    {
        return extraModule;
    }

    /**
     * Get the app name used to create this config
     */
    public static String getAppName()
    {
        return appName;
    }

    /**
     * Get config manager currently applicable
     */
    public static ConfigManager getConfigManager()
    {
        return configMgr;
    }

    /**
     * Get config manager currently applicable
     */
    public static File getUserHome()
    {
        return configMgr.runtimeDir.getClientHome(appName);
    }
}
