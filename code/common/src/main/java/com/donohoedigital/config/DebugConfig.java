package com.donohoedigital.config;

import org.apache.logging.log4j.*;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: donohoe
 * Date: Apr 3, 2008
 * Time: 8:06:16 AM
 * To change this template use File | Settings | File Templates.
 */
public class DebugConfig
{
    private static Logger logger = LogManager.getLogger(DebugConfig.class);
    private static Boolean TESTING_ENABLED = null;
    private static final Map<String, Boolean> cache = new HashMap<String, Boolean>();

    /**
     * Return true if given debug testing property is on.
     */
    public static boolean TESTING(String s)
    {
        if (!isTestingOn()) return false;

        // take sync hit only if testing is on
        synchronized (cache)
        {
            Boolean on = cache.get(s);
            if (on == null)
            {
                on = getDebugProperty(s);
                cache.put(s, on);
            }
            return on;
        }
    }

    public static void TOGGLE(String s)
    {
        // take sync hit only if testing is on
        synchronized (cache)
        {
            Boolean on = cache.get(s);
            if (on == null)
            {
                on = getDebugProperty(s);
            }
            cache.put(s, !on);
        }
    }

    /**
     * Returns a debug property and debug-prints if it is turned on
     */
    private static Boolean getDebugProperty(String sName)
    {
        boolean b = PropertyConfig.getBooleanProperty(sName, false, false);
        if (b)
        {
            logger.debug("Debug setting " + sName + " is on.");
        }
        return b;
    }

    /**
     * Is "settings.debug.enabled" property on?
     */
    public static boolean isTestingOn()
    {
        if (TESTING_ENABLED == null)
        {
            if (!PropertyConfig.isInitialized())
            {
                logger.warn("Checking isTestingOn() before PropertyConfig initialized.");
                return false;
            }

            //noinspection NonThreadSafeLazyInitialization
            TESTING_ENABLED = PropertyConfig.getBooleanProperty("settings.debug.enabled", false, false);
            if (TESTING_ENABLED) logger.debug("Debug testing on.");
        }
        return TESTING_ENABLED;
    }
}
