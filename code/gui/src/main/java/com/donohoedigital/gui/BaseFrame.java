package com.donohoedigital.gui;

import com.donohoedigital.base.ApplicationError;
import com.donohoedigital.config.ImageConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

public class BaseFrame extends JFrame implements DDWindow
{
    static Logger logger = LogManager.getLogger(JFrame.class);

    // Window/taskbar icon sizes (Windows + Linux); ignored on macOS, where the Dock
    // icon comes from the app bundle's .icns (or -Xdock:icon for the raw jar).
    private static final int[] ICON_SIZES = {16, 32, 48, 64, 128, 256};

    GraphicsDevice device_;
    BaseFrame thisFrame;
    private final List<InternalDialog> allDialogs_ = new ArrayList<>();

    public BaseFrame()
    {
        super();
        thisFrame = this;
        device_ = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        loadWindowIcons();
    }

    /**
     * Set the window icon(s) from the multi-resolution 'gui.icon.{size}' set, so each
     * context - title bar, taskbar, Alt-Tab - picks the best match.
     */
    private void loadWindowIcons()
    {
        List<Image> icons = new ArrayList<>();
        for (int size : ICON_SIZES)
        {
            ImageIcon icon = ImageConfig.getImageIcon("gui.icon." + size, null);
            if (icon != null) icons.add(icon.getImage());
        }

        if (!icons.isEmpty())
        {
            setIconImages(icons);
        }
        else
        {
            logger.warn("No window icon found in images.xml: 'gui.icon.{size}'");
        }
    }

    public GraphicsDevice getGraphicsDevice()
    {
        return device_;
    }

    public void center()
    {
        Dimension size = getSize();
        Point center =  GraphicsEnvironment.getLocalGraphicsEnvironment().getCenterPoint();
        int nX = center.x - (size.width / 2);
        int nY = center.y - (size.height / 2);
        setLocation(nX, nY);
    }

    /**
     * Display
     */
    public void display()
    {
        setVisible(true);
    }

    /**
     * Cleanup for exit (dispose, turn off full screen mode)
     */
    public void cleanup()
    {
        dispose();
    }

    /**
     * return if minimized
     */
    public boolean isMinimized()
    {
        return (getExtendedState() & Frame.ICONIFIED) == Frame.ICONIFIED;
    }

    /**
     * Sets frame to normal size.  Does nothing if frame isFullScreen()
     */
    public void setNormal()
    {
        setExtendedState(NORMAL);
    }

    /**
     * Add dialog, return true if added, false if already there
     */
    public boolean addDialog(InternalDialog dialog)
    {
        if (!allDialogs_.contains(dialog))
        {
            allDialogs_.add(dialog);
            return true;
        }
        return false;
    }

    /**
     * Remove dialog, return true if removed, false if not there
     */
    public boolean removeDialog(InternalDialog dialog)
    {
        return allDialogs_.remove(dialog);
    }

    /**
     * Call removeDialog() on all open dialogs
     */
    public void removeAllDialogs()
    {
        List<InternalDialog> all = new ArrayList<>(allDialogs_);
        for (InternalDialog dialog : all)
        {
            dialog.removeDialog();
        }
    }

    /**
     * Restore focus to last dialog opened, return false if you cannot do so
     */
    private boolean restoreFocusLastDialog()
    {
        if (allDialogs_.isEmpty()) return false;
        InternalDialog dialog;
        InternalDialog backup = null;

        // first look for visible/selected
        for (int i = allDialogs_.size() - 1; i >= 0; i--)
        {
            dialog = allDialogs_.get(i);
            if (dialog.isVisible() && !dialog.isIcon())
            {
                if (dialog.isSelected())
                {
                    //logger.debug("Setting focus to selected window " + dialog.getTitle());
                    dialog.moveToFrontSelected();
                    return true;
                }
                else if (backup == null)
                {
                    backup = dialog;
                }
            }
        }

        if (backup != null)
        {
            //logger.debug("Setting focus to unselected window " + backup.getTitle());
            backup.moveToFrontSelected();
            return true;
        }

        return false;
    }

    /**
     * Restore focus to right place (widget or internal dialog)
     */
    public void restoreFocus(Component restoreTo)
    {
        // first attempt to restore to a dialog
        if (restoreFocusLastDialog())
        {
            //logger.debug("restore to dialog");
            return;
        }

        // then restore to passed in component if it is visible
        if (restoreTo == null || !restoreTo.isVisible())
        {
            //logger.debug("XX BASE window activated focus to " + getContentPane());
            restoreTo = getContentPane();
        }

        restoreTo.requestFocus();
    }

    ////
    //// Convenience methods
    ////

    /**
     * Get DisplayMode
     */
    public DisplayMode getDisplayMode()
    {
        return device_.getDisplayMode();
    }

    ///
    /// Help widget stuff - delegated to HelpTextManager (shared with InternalDialog)
    ///

    private final HelpTextManager helpText_ = new HelpTextManager();

    public void setHelpTextWidget(JTextComponent t)
    {
        helpText_.setHelpTextWidget(t);
    }

    public JTextComponent getHelpTextWidget()
    {
        return helpText_.getHelpTextWidget();
    }

    public void setHelpMessage(String sMessage)
    {
        helpText_.setHelpMessage(sMessage);
    }

    public void setMessage(String sMessage)
    {
        helpText_.setMessage(sMessage);
    }

    public void clearMessage()
    {
        helpText_.clearMessage();
    }

    public void showHelp(DDComponent source)
    {
        helpText_.showHelp(source);
    }

    public void ignoreNextHelp()
    {
        helpText_.ignoreNextHelp();
    }

    ///
    /// Modal stuff
    ///

    // list of all logged modals
    private final List<Modal> logged_ = new ArrayList<>();

    /**
     * get new Modal handler
     */
    public Modal newModal()
    {
        return new Modal();
    }

    /**
     * End all logged gui modal
     */
    public void endModalLogged()
    {
        if (logged_.isEmpty()) return;
        List<Modal> clone = new ArrayList<>(logged_);
        for (Modal modal : clone)
        {
            modal.endModal();
        }
    }

    /**
     * class for doing modal dialogs
     */
    public class Modal
    {
        // The nested event loop that keeps events pumping while the dialog is
        // up.  Created in beginModal(), ended by endModal().  EDT-only.
        private SecondaryLoop loop_;

        /**
         * Begin a modal event loop.  Blocks here - while continuing to
         * dispatch events - until endModal() is called.
         */
        void beginModal(boolean bLog)
        {
            ApplicationError.assertTrue(SwingUtilities.isEventDispatchThread(), "Not in swing thread", Thread.currentThread().getName());

            if (bLog) logged_.add(this);
            try
            {
                loop_ = Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();

                // enter() blocks until endModal() (loop.exit()) is called, while
                // events keep flowing through the normal EventQueue.dispatchEvent
                // (so e.g. AppEngine's exception-handling queue still applies)
                if (!loop_.enter())
                {
                    logger.warn("GUI modal loop failed to start");
                }
            }
            finally
            {
                loop_ = null;
                if (bLog) logged_.remove(this);
            }
        }

        /*
         * Ends the event loop started by beginModal().  Safe to call more than
         * once, or after the loop has already ended.
         */
        public void endModal()
        {
            SecondaryLoop loop = loop_;
            if (loop != null) loop.exit();
        }
    }
}
