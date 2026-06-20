/*
 * Prefs.java
 *
 * Created on February 23, 2003, 2:04 PM
 */

package com.donohoedigital.config;


import com.donohoedigital.base.*;

import java.util.prefs.*;

/**
 * @author Doug Donohoe
 */
public class Prefs
{

    static
    {
        if (Utils.ISLINUX)
        {
            // need to change system root to user dir
            // because default is /etc
            String userRoot = System.getProperty("user.home");
            System.setProperty("java.util.prefs.systemRoot", userRoot + "/.java");
            // TODO: this is probably broken now
            System.setProperty("javax.xml.transform.TransformerFactory", "com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl");
        }
    }

    // store root node override
    private static String ROOT = null;
    public static final String NODE_OPTIONS = "options/";

    /**
     * Set root node name - overrides default of ConfigManager.getAppName();
     * only sets if ROOT is null.
     */
    public static void setRootNodeName(String s)
    {
        if (ROOT == null || s == null) ROOT = s;
    }

    /**
     * Get root node name for all prefs
     */
    private static String getRootNodeName()
    {
        if (ROOT != null) return ROOT;

        // probably not needed anymore since we don't run multiple
        // apps in an appserver.  commenting out to avoid dependency
        // on ConfigManager so we can use this in the installer easier
        //ApplicationError.assertNotNull(ConfigManager.getAppConfig(), "AppConfig is null");
        //return ConfigManager.getAppName();
        return "generic";
    }

    /**
     * Get root user prefs.  Users "com/donohoedigital/" + appname
     */
    public static Preferences getUserRootPrefs()
    {
        return Preferences.userRoot().node("com/donohoedigital/" + getRootNodeName());
    }

    /**
     * Clear all prefs
     */
    public static void clearAll()
    {
        try
        {
            clearNode(getUserRootPrefs());
        }
        catch (BackingStoreException bse)
        {
            // no worries
        }
    }

    private static void clearNode(Preferences node) throws BackingStoreException
    {
        for (String child : node.childrenNames())
        {
            clearNode(node.node(child));
        }
        node.clear();
    }

    /**
     * Get given node under user root prefs.  Nodes are delimited by /
     */
    public static Preferences getUserPrefs(String sNodeName)
    {
        return getUserRootPrefs().node(sNodeName);
    }
}
