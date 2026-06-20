/*
 * AppEngine.java
 *
 * Created on October 27, 2002, 2:46 PM
 */

package com.donohoedigital.app.engine;

import com.donohoedigital.app.config.AppdefConfig;
import com.donohoedigital.base.ApplicationError;
import com.donohoedigital.base.CommandLine;
import com.donohoedigital.base.Utils;
import com.donohoedigital.base.Version;
import com.donohoedigital.config.ApplicationType;
import com.donohoedigital.config.MatchingResources;
import com.donohoedigital.config.PropertyConfig;
import com.donohoedigital.gui.BaseApp;
import com.donohoedigital.gui.BaseFrame;
import com.donohoedigital.gui.DDOption;
import com.donohoedigital.gui.DDWindow;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Doug Donohoe
 */
public abstract class AppEngine extends BaseApp {
    private final Logger logger;

    // private stuff - does not change once created
    private static AppEngine engine_ = null;
    private AppdefConfig appdef_;
    private final String sMainModule_;

    // subclass access
    protected SplashScreen splashscreen_;
    private boolean tooSmall_;

    // other private stuff
    private EnginePrefs prefNode_;
    private final String sPrefNode_;

    // variable based on current state/app
    private AppContext defaultContext_;

    /**
     * Create AppEngine from config file
     */
    public AppEngine(String sConfigName, String sMainModule, String sMajorVersion, String[] args)
            throws ApplicationError {
        // init then create logger
        super(ApplicationType.CLIENT, sConfigName, sMajorVersion, args);
        logger = LogManager.getLogger(AppEngine.class);

        // remember who we are
        engine_ = this;

        // define the node for app preferences
        sPrefNode_ = sConfigName + "-prefs";
        sMainModule_ = sMainModule;
    }

    public String name() {
        return super.sAppName;
    }

    /**
     * primary initialization
     */
    @Override
    public void init() {
        super.init();

        // route otherwise-uncaught EDT exceptions through our handler so they
        // are logged and surfaced to the user, instead of AWT silently dumping
        // them to stderr (and so the app stays alive)
        Toolkit.getDefaultToolkit().getSystemEventQueue().push(new AppEventQueue());

        // set theme
        setLookAndFeel();

        // set version
        Version v = getVersion();
        v.setLocale(getLocale());

        // set desktop pane - used so windows can reorder
        JDesktopPane desktop = new JDesktopPane();
        MyDesktopUI ui = new MyDesktopUI();
        desktop.setUI(ui);
        frame_.setLayeredPane(desktop);

        // change splash screen UI to show details available after config files loaded
        if (splashscreen_ != null) {
            splashscreen_.changeUI(this, checkSize());
        }
    }

    protected void setLookAndFeel() {
    }

    /**
     * Can be overridden for application specific options
     */
    @Override
    protected void setupApplicationCommandLineOptions() {
        // used for shifting starting position for testing
        CommandLine.addIntegerOption("x", -1);
        CommandLine.setDescription("x", "x coor", "#");
        CommandLine.addIntegerOption("y", -1);
        CommandLine.setDescription("y", "y coor", "#");
        CommandLine.addFlagOption("reset");
        CommandLine.setDescription("reset", "reset window sizes/positions");
    }

    /**
     * stuff to do pre-config manager init
     */
    @Override
    protected void preConfigManagerInit() {
        URL file = new MatchingResources("classpath*:config/" + sAppName + "/images/" + getSplashBackgroundFile()).getSingleRequiredResourceURL();
        URL icon = new MatchingResources("classpath*:config/" + sAppName + "/images/" + getSplashIconFile()).getSingleRequiredResourceURL();
        splashscreen_ = new SplashScreen(file, icon, getSplashTitle());
        splashscreen_.setVisible(true);
    }

    /**
     * Get version
     */
    public abstract Version getVersion();

    /**
     * initial splash file name
     */
    protected String getSplashBackgroundFile() {
        return "splash.png";
    }

    /**
     * initial splash icon file name
     */
    protected String getSplashIconFile() {
        return "icon.gif";
    }

    /**
     * initial splash title
     */
    protected String getSplashTitle() {
        return "Donohoe Digital Presents...";
    }

    /**
     * UI for handling keyboard actions
     */
    private static class MyDesktopUI extends javax.swing.plaf.basic.BasicDesktopPaneUI {
        @Override
        public void uninstallKeyboardActions() {
            super.uninstallKeyboardActions();
        }
    }

    /**
     * Get Node used for prefs
     */
    public EnginePrefs getEnginePrefs() {
        if (prefNode_ == null) prefNode_ = new EnginePrefs(DDOption.getOptionPrefs(sPrefNode_));
        return prefNode_;
    }

    // sizes
    protected static final int DESIRED_MIN_WIDTH = 800;
    protected static final int DESIRED_MIN_HEIGHT = 600;

    // min size
    private static final int MIN_WIDTH = 768;
    private static final int MIN_HEIGHT = 600;

    /**
     * See if display is min size - 800x600 (allow less to accommodate tablet PC 768x1024).
     * Return non-null message if size is too small.
     */
    private String checkSize() {
        DisplayMode mode = frame_.getDisplayMode();
        if ((mode.getWidth() < MIN_WIDTH || mode.getHeight() < MIN_HEIGHT) && splashscreen_ != null) {
            tooSmall_ = true;
            return PropertyConfig.getMessage("msg.wrongsize", mode.getWidth(), mode.getHeight());
        }
        return null;
    }

    /**
     * Get starting size (defaults to 800x600)
     */
    protected Dimension getStartingSize() {
        return new Dimension(DESIRED_MIN_WIDTH, DESIRED_MIN_HEIGHT);
    }

    /**
     * Create main app window
     */
    @Override
    protected BaseFrame createMainWindow() {
        defaultContext_ = createAppContext("main", DESIRED_MIN_WIDTH, DESIRED_MIN_HEIGHT, true);
        return defaultContext_.getFrame();
    }

    /**
     * Create a context - here for overriding
     */
    protected AppContext createAppContext(String sName, int nDesiredMinWidth, int nDesiredMinHeight,
                                          boolean bQuitOnClose) {
        return new AppContext(this, sName, nDesiredMinWidth, nDesiredMinHeight, bQuitOnClose);
    }

    /**
     * Create a context - here for overriding
     */
    protected AppContext createInternalAppContext(AppContext context,
                                                  String sName, int nDesiredMinWidth, int nDesiredMinHeight) {
        return new AppContext(context, sName, nDesiredMinWidth, nDesiredMinHeight);
    }

    /**
     * Get default context
     */
    public AppContext getDefaultContext() {
        return defaultContext_;
    }

    /**
     * Event queue that catches exceptions escaping normal event dispatch -
     * i.e., those not already handled by {@link AppContext#processPhase} - so
     * we can log them and tell the user, rather than letting AWT dump them to
     * stderr.  Dispatch continues afterward, so the app stays alive.
     */
    private class AppEventQueue extends EventQueue {
        @Override
        protected void dispatchEvent(AWTEvent event) {
            try {
                super.dispatchEvent(event);
            } catch (Throwable e) {
                if (!handleDispatchException(event, e)) {
                    handleUncaughtException(e);
                }
            }
        }
    }

    /**
     * Hook for subclasses to swallow specific, known-benign dispatch
     * exceptions before they reach the generic error handler.  Return true
     * if the exception was handled and should be ignored.
     */
    protected boolean handleDispatchException(AWTEvent event, Throwable e) {
        return false;
    }

    /**
     * Log an uncaught EDT exception and show the standard error dialog.
     */
    private void handleUncaughtException(Throwable e) {
        logger.error(e.toString());
        logger.error(Utils.formatExceptionText(e));

        AppContext context = getDefaultContext();
        if (context != null) {
            // Defer to a fresh event cycle: we're inside the catch of the faulting dispatch,
            // so the modal dialog's nested event loop would start before that event finishes
            // unwinding, leaving keyboard focus unsettled (OK button looks focused but Enter/
            // Space don't reach it).  invokeLater lets the faulting event fully unwind first.
            SwingUtilities.invokeLater(() -> {
                try {
                    EngineUtils.displayErrorDialog(context, PropertyConfig.getMessage("msg.error.unexpected", e.toString()));
                } catch (Throwable t) {
                    logger.warn("AppEngine - Exception caught showing error dialog");
                    logger.warn(Utils.formatExceptionText(t));
                }
            });
        }
    }

    /**
     * Call to init main window
     */
    protected void initMainWindow() {
        // load config files
        appdef_ = new AppdefConfig(sMainModule_);

        // show main window now if not to small
        if (!tooSmall_) showMainWindow();
    }

    /**
     * Call when ready to show main window.  Removes the
     * splash screen if visible
     */
    public void showMainWindow() {
        // if splash screen visible, remove it
        if (splashscreen_ != null) {
            splashscreen_.setVisible(false);
            splashscreen_.dispose();
            splashscreen_ = null;
        }

        // init main window
        defaultContext_.getFrame().init(null, true, getStartingSize(), PropertyConfig.getRequiredStringProperty("msg.application.name"), true);

        // need to do after init so title is set
        contextInited(defaultContext_);

        // start the engine with the first phase
        initialStart();

        // display the frame
        displayMainWindow();
    }

    /**
     * Very first start phase - calls start() by default, but
     * can be overridden
     */
    protected void initialStart() {
        defaultContext_.processPhase(appdef_.getStartPhaseName(), null, true);
    }

    /**
     * Called from window closing - calls Exit phase and returns
     * false (meaning that the caller won't close the app - instead,
     * Exit does that)
     */
    @Override
    public boolean okayToClose() {
        // give registered listeners a chance to confirm with the user
        // and/or clean up (e.g., stop a running process) before closing
        for (CloseListener listener : closeListeners_) {
            if (!listener.okayToClose()) {
                return false;
            }
        }

        // this prompts users
        defaultContext_.processPhase("Exit"); // TODO: active context?
        return false;
    }

    /**
     * Implemented by UI components that may need to confirm with the
     * user and/or perform cleanup before the application closes
     * (e.g., a panel with a running process it should stop).
     */
    public interface CloseListener {
        /**
         * Return true if it is okay to close. Implementations should
         * perform whatever checks and user confirmation they need, and
         * any necessary cleanup (e.g., stopping a running process),
         * themselves.
         */
        boolean okayToClose();
    }

    private final List<CloseListener> closeListeners_ = new ArrayList<>();

    /**
     * Register a listener to be consulted when the app is about to close
     */
    public void addCloseListener(CloseListener listener) {
        closeListeners_.add(listener);
    }

    /**
     * Unregister a close listener
     */
    public void removeCloseListener(CloseListener listener) {
        closeListeners_.remove(listener);
    }

    /**
     * Called in some OS when Preferences menu item selected
     */
    @Override
    public void showPrefs() {
        String sMsg = PropertyConfig.getMessage("label.prefs.label");
        EngineUtils.displayInformationDialog(getDefaultContext(), sMsg);
    }

    /**
     * Called in some OS when About menu item selected
     */
    @Override
    public void showAbout() {
        String sMsg = PropertyConfig.getMessage("label.about.label", getVersion());
        EngineUtils.displayInformationDialog(getDefaultContext(), sMsg);
    }

    /**
     * Called in some OS when Help menu item selected
     */
    public void showHelp() {
        defaultContext_.processPhase("Help");
    }

    /**
     * Called when the Support menu item is selected
     */
    public void showSupport() {
        defaultContext_.processPhase("Support");
    }

    /**
     * Return the engine
     */
    public static AppEngine getAppEngine() {
        return engine_;
    }

    /**
     * Get AppdefConfig used by this engine
     */
    public AppdefConfig getAppdefConfig() {
        return appdef_;
    }

    /// /
    /// / Keep track of contexts
    /// /

    // list of contexts
    private final Map<String, ContextTracker> contexts_ = new HashMap<>();

    /**
     * note that a context was created
     */
    void contextInited(AppContext context) {
        DDWindow window = context.getWindow();
        String sName = window.getName();
        ContextTracker tracker = contexts_.get(sName);
        if (tracker == null) {
            tracker = new ContextTracker(sName);
            contexts_.put(sName, tracker);
        }

        tracker.add(context);
    }

    /**
     * note that a window was destroyed
     */
    void contextDestroyed(AppContext context) {
        DDWindow window = context.getWindow();
        String sName = window.getName();
        ContextTracker tracker = contexts_.get(sName);
        ApplicationError.assertNotNull(tracker, "No tracker for window", sName);
        if (tracker.remove(context)) {
            contexts_.remove(sName);
        }
    }

    /**
     * Get first context of given name
     */
    AppContext getContext(String sName) {
        ContextTracker tracker = contexts_.get(sName);
        if (tracker != null) {
            return tracker.get();
        }

        return null;
    }

    /**
     * class to track all instances of a window
     */
    private static class ContextTracker {
        int nNum;
        String sName;
        List<AppContext> contexts = new ArrayList<>();

        // constructor
        ContextTracker(String sName) {
            this.sName = sName;
        }

        // add window to list
        void add(AppContext context) {
            contexts.add(context);
            nNum++;

            if (nNum > 1) {
                String sTitle = context.getWindow().getTitle() + " - " + nNum;
                context.getWindow().setTitle(sTitle);
            }
        }

        // remove window, return true if tracker now empty
        boolean remove(AppContext context) {
            ApplicationError.assertTrue(contexts.remove(context), "Window not found in list", context.getWindow().getName());
            return contexts.isEmpty();
        }

        // AppContext 1st window
        AppContext get() {
            return contexts.getFirst();
        }
    }
}
