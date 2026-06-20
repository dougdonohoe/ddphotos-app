/*
 * BaseCommandLineApp.java
 *
 * Created on September 27, 2003, 5:08 PM
 */

package com.donohoedigital.config;

import com.donohoedigital.base.*;

public abstract class BaseCommandLineApp
{
    protected String sAppName_ = null;
    protected String sConfigDir_ = null;
    protected TypedHashMap htOptions_;
            
    public BaseCommandLineApp(String sAppName, String[] args)
    {
        LoggingConfig loggingConfig = new LoggingConfig(sAppName, ApplicationType.COMMAND_LINE);
        loggingConfig.init();

        CommandLine.setUsage(getClass().getName() + " [options]");
        setupApplicationCommandLineOptions();
        
        init(sAppName, args);
    }
    
    /**
     * Can be overridden for application specific options
     */
    protected void setupApplicationCommandLineOptions()
    {
    }

    /**
     * Get command line options
     */
    public TypedHashMap getCommandLineOptions()
    {
        return htOptions_;
    }

    /**
     * Main init function to be called by subclass applications
     * from main()
     */
    protected void init(String sAppName, String[] args)
    {
        sAppName_ = sAppName;

        // get command line options
        CommandLine.parseArgs(args);
        htOptions_ = CommandLine.getOptions();

        // init config files
        new ConfigManager(sAppName, ApplicationType.COMMAND_LINE);
    }
}
