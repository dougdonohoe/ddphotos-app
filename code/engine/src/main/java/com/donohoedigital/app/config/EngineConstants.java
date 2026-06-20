/*
 * EngineConstants.java
 *
 * Created on March 26, 2003, 2:24 PM
 */

package com.donohoedigital.app.config;

/**
 *
 * @author  donohoe
 */
public class EngineConstants
{
    // used when TESTING_CHANGE_STARTING_SIZE is on
    public static final int TESTING_CHANGE_SIZE_WIDTH = 800;
    public static final int TESTING_CHANGE_SIZE_HEIGHT = 600;

    // debug settings configured in common.properties file
    public static final String TESTING_PERFORMANCE = "settings.debug.performance";
    public static final String TESTING_CHANGE_STARTING_SIZE = "settings.debug.changesize";
    public static final String TESTING_NO_EXTERNAL = "settings.debug.no.external";

    // size/location
    public static final String PREF_X = "x";
    public static final String PREF_Y = "y";
    public static final String PREF_W = "w";
    public static final String PREF_H = "h";
}
