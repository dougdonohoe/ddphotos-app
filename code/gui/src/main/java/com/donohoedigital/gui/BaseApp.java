package com.donohoedigital.gui;

import com.donohoedigital.base.CommandLine;
import com.donohoedigital.base.TypedHashMap;
import com.donohoedigital.base.Utils;
import com.donohoedigital.config.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.util.Locale;

public abstract class BaseApp
{
    private final Logger logger;

    static Thread mainThread_ = null;
    protected static BaseApp app_ = null;

    protected String sLocale_ = null;
    protected BaseFrame frame_;
    protected TypedHashMap htOptions_;
    protected String sAppName;

    private boolean bReady_ = false;
    private String sMajorVersion;
    private final String[] args;

    public BaseApp(ApplicationType appType, String sAppName, String sMajorVersion, String[] args)
    {
        // initialize logging before anything else
        LoggingConfig loggingConfig = new LoggingConfig(sAppName, appType);
        loggingConfig.init();
        logger = LogManager.getLogger();

        mainThread_ = Thread.currentThread();
        app_ = this;

        // Mac specific setup
        if (Utils.ISMAC)
        {
            setupMac();
        }

        this.sAppName = sAppName;
        this.sMajorVersion = sMajorVersion;
        this.args = args;
    }

    private void setupMac() {
        // Set handlers for macOS-specific actions
        if (Desktop.isDesktopSupported()) {
            Desktop desktop = Desktop.getDesktop();

            desktop.setAboutHandler(_ -> {
                if (!app_.isReady()) return;
                app_.showAbout();
            });

            desktop.setOpenFileHandler(e -> {
                if (!e.getFiles().isEmpty()) {
                    CommandLine.setMacFileArg(e.getFiles().getFirst().getAbsolutePath());
                }
            });

            desktop.setPreferencesHandler(_ -> {
                if (!app_.isReady()) return;
                app_.showPrefs();
            });

            desktop.setQuitHandler((_, response) -> {
                if (app_.isReady()) {
                    app_.quit();
                    response.cancelQuit();  // Indicates that app handles quit
                } else {
                    response.performQuit(); // Forces quit if app isn't ready
                }
            });
        }
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
     * Sets up application-specific command line options.
     */
    private void setupStandardCommandLineOptions()
    {
        CommandLine.setUsage(getClass().getName() + " [options]");

        CommandLine.addStringOption("module", null);
        CommandLine.setDescription("module", "Extra Module (runtime)", "module");
        CommandLine.addStringOption("locale", null);
        CommandLine.setDescription("locale", "Locale", "locale");
    }

    /**
     * Main init function after construction
     */
    public void init()
    {
        // setup command line options
        setupStandardCommandLineOptions();
        setupApplicationCommandLineOptions();

        // get command line options
        CommandLine.parseArgs(args);
        htOptions_ = CommandLine.getOptions();
        sLocale_ = htOptions_.getString("locale");
        String sExtraModule = htOptions_.getString("module");

        // set locale
        Locale.setDefault(PropertyConfig.getLocale(sLocale_));

        // set version string items
        if (sMajorVersion == null) sMajorVersion = "";
        Prefs.setRootNodeName(sAppName + sMajorVersion); // keep in sync with FileCleanup

        // pre-init
        preConfigManagerInit();

        // init config files
        ApplicationType type = ApplicationType.CLIENT;
        new ConfigManager(sAppName, type, sExtraModule, sLocale_, true);

        // create base frame
        frame_ = createMainWindow();
        frame_.setTitle(sAppName);
    }

    /**
     * stuff to do pre-config manager init
     */
    protected void preConfigManagerInit()
    {
    }

    /**
     * Create main app window
     */
    protected BaseFrame createMainWindow()
    {
        return new BaseFrame();
    }

    /**
     * Display main window and set ready flag when done
     */
    protected void displayMainWindow()
    {
        frame_.display();

        // invoke later to set ready flag (used for Mac integration)
        SwingUtilities.invokeLater(
                () -> bReady_ = true
        );
    }

    /**
     * Return if ready for processing
     */
    public boolean isReady()
    {
        return bReady_;
    }

    /**
     * Get locale
     */
    public String getLocale()
    {
        return sLocale_;
    }

    /**
     * Subclass must implement - called from window closing
     * event.  If true return, frame is closed and app exits
     */
    public abstract boolean okayToClose();

    /**
     * Called in some OS when Preferences menu item selected.
     * Should be overridden to do something.
     */
    public void showPrefs()
    {
    }

    /**
     * Called in some OS when About menu item selected.
     * Should be overridden to do something.
     */
    public void showAbout()
    {
    }

    /**
     * Called in some OS when Help menu item selected
     */
    public void showHelp()
    {
    }

    /**
     * Called when the Support menu item is selected.
     * Should be overridden to do something.
     */
    public void showSupport()
    {
    }

    /**
     * quit app - if okayToClose
     */
    public void quit()
    {
        if (!okayToClose()) return;

        exit(0);
    }

    /**
     * Use this to exit application cleanly
     */
    public void exit(int nCode)
    {
        if (frame_ != null)
        {
            try
            {
                frame_.cleanup();
            }
            catch (Throwable t)
            {
                logger.debug("Error trying to cleanup: {}", Utils.formatExceptionText(t));
            }

            exitAfterWindowClosed(nCode);
        }
        else
        {
            _exit(nCode);
        }
    }

    /**
     * Handle final cleanup
     */
    private void _exit(int nCode)
    {
        System.exit(nCode);
    }

    /**
     * deferred exit
     */
    private void exitAfterWindowClosed(final int nCode)
    {
        SwingUtilities.invokeLater(
                () -> _exit(nCode)
        );
    }

    /**
     * Return current base app
     */
    public static BaseApp getBaseApp()
    {
        return app_;
    }
}
