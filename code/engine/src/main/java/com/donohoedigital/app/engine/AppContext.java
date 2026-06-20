package com.donohoedigital.app.engine;

import com.donohoedigital.base.ApplicationError;
import com.donohoedigital.base.ErrorCodes;
import com.donohoedigital.base.TypedHashMap;
import com.donohoedigital.base.Utils;
import com.donohoedigital.config.PropertyConfig;
import com.donohoedigital.app.config.*;
import com.donohoedigital.gui.DDWindow;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static com.donohoedigital.config.DebugConfig.TESTING;

public class AppContext
{
    private final Logger logger;

    // engine
    private AppEngine engine_;

    // context-specific items
    private DDWindow window_;
    private EngineWindow frame_;
    private EngineDialog dialog_;

    // Holds last phase to be set as main panel in this context
    private Phase currentMainUIPhase_ = null;

    // Current phase being executed - this is set whenever a phase is
    // run unless the definition of the phase says it is transient (typically
    // set for info dialog type phases
    private Phase currentPhase_ = null;

    // Cached phases are Phase instances that are saved
    // for reuse because they typically retain state (e.g., loop phases and
    // menu phases which has user input
    private final Map<String, Phase> cachedPhases_ = new HashMap<>();

    /**
     * Constructor - new internal window in given context
     */
    public AppContext(AppContext context, String sName, int nDesiredMinWidth, int nDesiredMinHeight)
    {
        logger = LogManager.getLogger(AppContext.class);

        engine_ = context.engine_;

        frame_ = context.frame_;
        dialog_ = new EngineDialog(this, sName, nDesiredMinWidth, nDesiredMinHeight);
        window_ = dialog_;

        // close handler created when displayed
    }

    /**
     * Constructor - new window
     */
    public AppContext(AppEngine engine, String sName,
                      int nDesiredMinWidth, int nDesiredMinHeight,
                      boolean bQuitOnClose)
    {
        logger = LogManager.getLogger(AppContext.class);

        engine_ = engine;

        frame_ = createEngineWindow(engine, sName, nDesiredMinWidth, nDesiredMinHeight);
        dialog_ = null;
        window_ = frame_;

        // close handler
        frame_.addWindowListener(new AppContextWindowAdapter(bQuitOnClose));
    }

    /**
     * create EngineWindow (allows for overrides)
     */
    protected EngineWindow createEngineWindow(AppEngine engine, String sName, int nDesiredMinWidth, int nDesiredMinHeight)
    {
        return new EngineWindow(engine, this, sName, nDesiredMinWidth, nDesiredMinHeight);
    }

    /**
     * Get dd window this app is in
     */
    public DDWindow getWindow()
    {
        return window_;
    }

    /**
     * return whether this is an internal window
     */
    public boolean isExternal()
    {
        return dialog_ == null;
    }

    /**
     * get dialog (null if isInternal is false - use getFrame instead)
     */
    public EngineDialog getDialog()
    {
        return dialog_;
    }

    /**
     * Get BaseFrame this context uses
     */
    public EngineWindow getFrame()
    {
        return frame_;
    }

    /**
     * process button in given phase.  calls phase.processButton(),
     * which can return false to prevent processing phase associated
     * with this button.  Returns result of processButton, which
     * indicates whether next phase processed.
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean buttonPressed(AppButton button, Phase phase)
    {
        if (phase.processButton(button))
        {
            String sPhase = button.getGotoPhase();
            if (sPhase != null)
            {
                TypedHashMap params = null;
                String sParam = button.getGenericParam();
                if (sParam != null)
                {
                    params = new TypedHashMap();
                    params.setString(AppButton.PARAM_GENERIC, sParam);
                }
                processPhase(sPhase, params);
            }
            return true;
        }
        return false;
    }

    /**
     * Display a phase in BasePanel.  If a phase is already set, it is
     * removed and finish() is called on it.
     */
    public void setMainUIComponent(Phase phase, JComponent comp, boolean bBorderLayout, JComponent cFocus)
    {
        if (currentMainUIPhase_ != null)
        {
            currentMainUIPhase_.finish();
        }
        currentMainUIPhase_ = phase;

        if (isExternal())
        {
            frame_.getBasePanel().setCenterComponent(comp, bBorderLayout, cFocus);
        }
        else
        {
            dialog_.setCenterComponent(comp, cFocus);
        }

        // TODO: option to resize window to component (timing might not work due to fact window displayed b4 this is called)
    }

    /**
     * calls processPhase(sPhaseName, null)
     */
    public void processPhase(String sPhaseName)
    {
        processPhase(sPhaseName, null);
    }

    /**
     * Process next phase (uses invokeLater to avoid any
     * possible swing locking issues).  Any params passed
     * in are added to the AppPhase's list of parameters,
     * possibly overriding default values permanently.
     */
    public void processPhase(String sPhaseName, TypedHashMap params)
    {
        processPhase(sPhaseName, params, true);
    }

    /**
     * Same as above, but specify history flag (overrides history setting in appdef.xml)
     */
    public void processPhase(String sPhaseName, TypedHashMap params, boolean bHistory)
    {
        SwingUtilities.invokeLater(new ProcessPhaseRunnable(sPhaseName, params, bHistory));
    }

    /**
     * Runnable for processing phase later in swing loop
     */
    private class ProcessPhaseRunnable implements Runnable
    {
        String _sPhaseName;
        TypedHashMap _params;
        boolean _bHistory;

        private ProcessPhaseRunnable(String sPhaseName, TypedHashMap params, boolean bHistory)
        {
            _sPhaseName = sPhaseName;
            _params = params;
            _bHistory = bHistory;
        }

        public void run()
        {
            _processPhase(_sPhaseName, _params);
        }
    }

    /**
     * Process given phase now forcing history to false.
     * Returns phase after start() done.  Typically used to display modal dialogs
     * based on DialogPhase
     */
    public Phase processPhaseNow(String sPhaseName, TypedHashMap params)
    {
        return _processPhase(sPhaseName, params);
    }

    /**
     * process phase actual logic
     */
    private Phase _processPhase(String sPhaseName, TypedHashMap params)
    {
        try
        {
            AppPhase phase = engine_.getAppdefConfig().getAppPhases().get(sPhaseName);
            ApplicationError.assertNotNull(phase, "AppPhase not found", sPhaseName);

            return createAndStartPhase(phase, params, false);
        }
        catch (ApplicationError ae)
        {
            logger.error("AppContext - ApplicationError caught processing phase {}", sPhaseName);
            switch (ae.getErrorCode())
            {
                case ErrorCodes.ERROR_NULL:
                case ErrorCodes.ERROR_ASSERTION_FAILED:
                case ErrorCodes.ERROR_UNEXPECTED_EXCEPTION:
                case ErrorCodes.ERROR_CODE_ERROR:
                    logger.error(ae.toString());
                    logger.error(Utils.formatExceptionText(ae));
                    break;

                default:
                    logger.error(ae.toStringNoStackTrace());
                    break;
            }
            _handleProcessPhaseException(ae);
        }
        catch (Throwable e)
        {
            logger.error("AppContext - Exception caught processing phase {}", sPhaseName);
            logger.error(Utils.formatExceptionText(e));
            _handleProcessPhaseException(e);
        }
        return null;
    }

    // guards against recursion: displaying the error dialog runs another
    // phase, which could itself fail and route back here
    private boolean handlingException_ = false;

    /**
     * subclass logging catch
     */
    private void _handleProcessPhaseException(Throwable e)
    {
        if (handlingException_)
        {
            logger.warn("AppContext - exception while handling a prior error; not showing another dialog");
            return;
        }

        handlingException_ = true;
        try
        {
            handleProcessPhaseException(e);
        }
        catch (Throwable t)
        {
            logger.warn("AppContext - Exception caught handling above error");
            logger.warn(Utils.formatExceptionText(t));
        }
        finally
        {
            handlingException_ = false;
        }
    }

    /**
     * Tell the user an error occurred (details already logged) and point them
     * at the logs.  Override for app-specific handling.
     */
    protected void handleProcessPhaseException(Throwable e)
    {
        EngineUtils.displayErrorDialog(this, PropertyConfig.getMessage("msg.error.unexpected", e.getMessage()));
    }

    /**
     * Create phase and start it.
     */
    private Phase createAndStartPhase(AppPhase phase, TypedHashMap params, boolean bAvoidRecursion)
            throws ApplicationError
    {
        // window phase - run in new (or existing) window [bNewWindow used to prevent infinite loops]
        if (phase.isWindow() && !bAvoidRecursion)
        {
            // name, get title and starting width/height
            String sWindowName = phase.getWindowName();
            boolean bMulti = phase.getBoolean("window-multi", false);

            // look for existing context if not multi-instance window
            AppContext context = null;
            if (!bMulti)
            {
                context = engine_.getContext(sWindowName);

                // if existing, bring it to front
                if (context != null) context.getWindow().toFront();
            }

            // no existing (or multi-instance), create new window
            if (context == null)
            {
                String sTitle = PropertyConfig.getStringProperty(phase.getString("window-title"), "msg.application.name");
                int nHeight = phase.getInteger("window-height", 400);
                int nWidth = phase.getInteger("window-width", 400);
                int nMinHeight = phase.getInteger("window-height-min", 25);
                int nMinWidth = phase.getInteger("window-width-min", 100);
                boolean bResizable = phase.getBoolean("window-resize", true);

                // By default, we create an external window
                if (!TESTING(EngineConstants.TESTING_NO_EXTERNAL))
                {
                    context = engine_.createAppContext(sWindowName, nMinWidth, nMinHeight, false);
                    EngineWindow window = context.getFrame();
                    window.init(phase, false, new Dimension(nWidth, nHeight), sTitle, bResizable);
                    engine_.contextInited(context);
                    window.display();
                }
                else // Creates an internal window that can be minimized on the swing "desktop"
                {
                    context = engine_.createInternalAppContext(this, sWindowName, nWidth, nHeight);
                    EngineDialog dialog = (EngineDialog) context.getWindow();
                    dialog.init(phase, sTitle, bResizable);
                    engine_.contextInited(context);
                    dialog.display(new AppContextInternalAdapter(context));
                }
            }

            if (context != this)
            {
                return context.createAndStartPhase(phase, params, true);
            }
        }

        // if we have override params, clone phase
        if (params != null)
        {

            phase = (AppPhase) phase.clone();
            phase.putAll(params);
        }

        // get instance of phase
        Phase phaseInstance = getInstance(phase);

        // notify phase of the current phase (non-transient) that
        // invoked them
        phaseInstance.setFromPhase(currentPhase_);

        // if this phase isn't transient, store it as the current phase
        // dialog phases tend to be driven by other phases.  Current
        // phase should be the last gui phase (that which changed the
        // base component) or the last phase invoked that is driving
        // responses to user interaction
        if (!phase.isTransient())
        {
            currentPhase_ = phaseInstance;
        }

        // start the phase
        //logger.debug("STARTING phase: " + phaseInstance.getName());
        phaseInstance.start();
        return phaseInstance;
    }

    /**
     * close this app context (also closes associated window)
     */
    public void close()
    {
        // close window
        if (isExternal())
        {
            frame_.setVisible(false);
            frame_.cleanup();
        }
        else
        {
            dialog_.removeDialog();
        }

        // cleanup any dialogs, etc.
        clean();

        // remove from context list
        engine_.contextDestroyed(this);

        // allow gc to do work
        frame_ = null;
        dialog_ = null;
        window_ = null;
        engine_ = null;
    }

    /**
     * cleanup phase (for restart or close)
     */
    void clean()
    {
        // cleanup any dialogs lying around
        if (isExternal())
        {
            frame_.removeAllDialogs();
            frame_.endModalLogged();
        }

        // cleanup current phase
        if (currentPhase_ != null)
        {
            if (!(currentPhase_ instanceof DialogPhase)) {
                currentPhase_.finish();
            } // else do nothing - removeAllDialogs() should have called finish()
        }

        // cleanup current ui phase
        if (currentMainUIPhase_ != null && currentMainUIPhase_ != currentPhase_)
        {
            currentMainUIPhase_.finish();
        }

        // cleanup any remaining cached phases
        Iterator<String> iter = cachedPhases_.keySet().iterator();
        String sName;
        Phase phase;
        while (iter.hasNext())
        {
            sName = iter.next();
            phase = cachedPhases_.get(sName);

            // skip any handled above
            if (phase == currentPhase_) continue;
            if (phase == currentMainUIPhase_) continue;
            if (phase instanceof DialogPhase) continue;

            phase.finish();
        }

        // refresh lists and phase stuff
        currentMainUIPhase_ = null;
        currentPhase_ = null;
        cachedPhases_.clear();
    }

    /**
     * Get instance of class associated with this phase
     */
    private Phase getInstance(AppPhase phase) throws ApplicationError
    {
        String sName = phase.getName();

        // first see if it is in the cache
        Phase phaseInstance = cachedPhases_.get(sName);
        // if exists, reset it
        if (phaseInstance != null)
        {
            //logger.debug("REUSING: " + phase.getAppPhase().getName());
            phaseInstance.reinit(phase);
        }
        // if new, create it
        else {
            String sClass = phase.getClassName();
            try
            {
                Class<? extends Phase> cClass = phase.getClassObject();

                if (cClass == null)
                {
                    throw new ApplicationError(ErrorCodes.ERROR_CLASS_NOT_FOUND,
                                               "Phase " + sName +
                                               " class not found: " + sClass,
                                               "Make sure class exists");
                }

                phaseInstance = cClass.getDeclaredConstructor().newInstance();

                // otherwise init
                phaseInstance.init(engine_, this, phase);

                if (phase.isCached())
                {
                    cachedPhases_.put(sName, phaseInstance);
                }
            }
            catch (ClassCastException ce)
            {
                throw new ApplicationError(ErrorCodes.ERROR_CLASS_NOT_FOUND,
                                           "The class (" + sClass + ") for phase " + sName +
                                           " does not implement Phase.",
                                           ce,
                                           "Make sure this class implements com.donohoedigital.app.engine.Phase " +
                                           Utils.formatExceptionText(ce));
            }
            catch (Exception e)
            {
                throw new ApplicationError(ErrorCodes.ERROR_UNEXPECTED_EXCEPTION,
                                           "The class (" + sClass + ") for phase " + sName +
                                           " could not be created",
                                           e,
                                           "Resolve the condition indicated by the exception");
            }
        }

        return phaseInstance;
    }

    //
    // WindowListener
    //

    /**
     * Class to handle window closing events plus state changes issues
     */
    private class AppContextWindowAdapter extends WindowAdapter
    {
        boolean bQuitOnClose;

        AppContextWindowAdapter(boolean bQuitOnClose)
        {
            this.bQuitOnClose = bQuitOnClose;
        }

        /**
         * Calls okayToClose(), which if it returns true, then exit is called
         */
        @Override
        public void windowClosing(WindowEvent e)
        {
            if (bQuitOnClose)
            {
                engine_.quit();
            }
            else
            {
                close();
            }
        }
    }

    /**
     * ditto for internal frames
     */
    private static class AppContextInternalAdapter extends InternalFrameAdapter
    {
        AppContext context;

        AppContextInternalAdapter(AppContext context)
        {
            this.context = context;
        }

        /**
         * Detect when window close icon is pressed - activate associated button
         */
        @Override
        public void internalFrameClosing(InternalFrameEvent e)
        {
            context.close();
        }
    }
}
