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
    public static final int TESTING_CHANGE_SIZE_WIDTH = 1500;
    public static final int TESTING_CHANGE_SIZE_HEIGHT = 992;

    // debug settings configured in common.properties file
    public static final String TESTING_PERFORMANCE = "settings.debug.performance";
    public static final String TESTING_CHANGE_STARTING_SIZE = "settings.debug.changesize";
    public static final String TESTING_NO_EXTERNAL = "settings.debug.no.external";

    // size/location
    public static final String PREF_X = "x";
    public static final String PREF_Y = "y";
    public static final String PREF_W = "w";
    public static final String PREF_H = "h";

    // per-invocation window identity overrides (passed via processPhase params); when present they
    // override a window phase's static window name / title, so multiple instances (e.g. one editor
    // per album) get distinct titles and their own remembered size/position.
    public static final String PARAM_WINDOW_NAME  = "window-name";
    public static final String PARAM_WINDOW_TITLE = "window-title-override";

    // style of the bottom hover-help strip (see LogoWindowPanel).  Named by the app rather than
    // the engine, since the styles themselves are app config; absent means no help strip.
    public static final String PARAM_HELP_STYLE = "help-style";
}
