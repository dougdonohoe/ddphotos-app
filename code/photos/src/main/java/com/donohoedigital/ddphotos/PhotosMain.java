package com.donohoedigital.ddphotos;

import com.donohoedigital.app.engine.AppEngine;
import com.donohoedigital.base.ApplicationError;
import com.donohoedigital.base.Utils;
import com.donohoedigital.base.Version;
import com.donohoedigital.config.DebugConfig;
import com.donohoedigital.config.StylesConfig;
import com.formdev.flatlaf.FlatLightLaf;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;

public class PhotosMain extends AppEngine
{
    private static Logger logger;

    static {
        // Mac: Menu Name
        System.setProperty("com.apple.mrj.application.apple.menu.about.name", PhotosConstants.APP_DISPLAY_NAME);
        System.setProperty("apple.awt.application.name", PhotosConstants.APP_DISPLAY_NAME);
        System.setProperty("apple.laf.useScreenMenuBar", "true");
    }

    /**
     * Run Photos
     */
    static void main(String[] args)
    {
        try
        {
            PhotosMain main = new PhotosMain(PhotosConstants.APP_NAME, "ddphotos", args);
            main.init();
        }
        catch (ApplicationError ae)
        {
            System.err.println("Photos ending due to ApplicationError: " + ae);
            if (logger != null) {
                logger.error("Photos ending due to ApplicationError: {}", String.valueOf(ae));
            }
            System.exit(1);
        }
        catch (OutOfMemoryError nomem)
        {
            System.err.println("Out of memory: " + nomem);
            System.err.println(Utils.formatExceptionText(nomem));
            if (logger != null) {
                logger.error("Out of memory: {}", String.valueOf(nomem));
                logger.error(Utils.formatExceptionText(nomem));
            }
            System.exit(1);
        }
    }

    /**
     * Create Photos from config file
     */
    public PhotosMain(String sConfigName, String sMainModule, String[] args)
            throws ApplicationError
    {
        super(sConfigName, sMainModule, PhotosConstants.VERSION.getMajorAsString(), args);
        logger = LogManager.getLogger(PhotosMain.class);
    }

    /**
     * init
     */
    @Override
    public void init()
    {
        super.init();

        DockerStatus.start();

        // show main window
        initMainWindow();
    }

    @Override
    protected void setLookAndFeel() {
        String rgb = StylesConfig.getRGB("app.accent");
        FlatLightLaf.setGlobalExtraDefaults(Collections.singletonMap("@accentColor", rgb));
        FlatLightLaf.setup();
        UIManager.put("Panel.background", StylesConfig.getColor("app.panel.bg"));

        // explore L&F vi CTRL-SHIFT-OPT X (whatever mouse is over)
        if (DebugConfig.isTestingOn()) {
            com.formdev.flatlaf.extras.FlatInspector.install("ctrl shift alt X");
        }
    }

    @Override
    protected Dimension getStartingSize()
    {
        int height = Math.max(DESIRED_MIN_HEIGHT, 980);
        int width = Math.max(DESIRED_MIN_WIDTH, 1500);
        return new Dimension(width, height);
    }

    @Override
    public Version getVersion() {
        return PhotosConstants.VERSION;
    }

    @Override
    protected String getSplashIconFile()
    {
        return "logo_256x256.png";
    }

    // FlatLaF schedules layoutDock via invokeLater; if the desktop pane is GC'd first the
    // reference is null by the time the event fires. Swallow that specific NPE only.
    //   Exception in thread "AWT-EventQueue-0" java.lang.NullPointerException: Cannot invoke "javax.swing.JDesktopPane.getComponents()" because "this.desktop" is null
    //      at com.formdev.flatlaf.ui.FlatDesktopPaneUI.layoutDock(FlatDesktopPaneUI.java:94)
    //      at com.formdev.flatlaf.ui.FlatDesktopPaneUI.lambda$layoutDockLaterOnce$0(FlatDesktopPaneUI.java:84)
    @Override
    protected boolean handleDispatchException(AWTEvent event, Throwable e) {
        if (e instanceof NullPointerException) {
            StackTraceElement[] stack = e.getStackTrace();
            if (stack.length > 0 && stack[0].getClassName().contains("FlatDesktopPaneUI")) {
                logger.warn("Suppressed exception in FlatDesktopPaneUI");
                return true;
            }
        }
        return false;
    }
}
