package com.donohoedigital.gui;

import javax.swing.text.JTextComponent;

/**
 * Encapsulates the help-text-widget display logic shared by the
 * DDWindow implementations (BaseFrame and InternalDialog): tracking
 * the widget that displays help messages, showing help for a
 * DDComponent (falling back to the default help text), and the
 * "ignore next" flag used to suppress help on the mouse-enter that
 * follows certain UI actions (e.g. dismissing a dialog).
 */
public class HelpTextManager
{
    private static final String EMPTY = "";

    /**
     * Global fallback widget, used when a window has no help widget of its own.
     * GuiUtils.getHelpManager() resolves to the nearest DDWindow ancestor, which
     * for a widget inside an internal dialog (or a secondary window like Help /
     * Support) is that dialog/window - not the main frame.  Those windows never
     * get their own help widget, so without this fallback their hover-help is
     * silently dropped.  Set once by the main phase (see PhotosBasePhase).
     */
    private static JTextComponent globalHelp_ = null;

    /**
     * Set the global fallback help widget (see globalHelp_)
     */
    public static void setGlobalHelpTextWidget(JTextComponent t)
    {
        globalHelp_ = t;
    }

    /**
     * Get the global fallback help widget
     */
    public static JTextComponent getGlobalHelpTextWidget()
    {
        return globalHelp_;
    }

    private JTextComponent tHelp_ = null;
    private boolean bIgnore_ = false;

    /**
     * Set widget used to display help text
     */
    public void setHelpTextWidget(JTextComponent t)
    {
        tHelp_ = t;
    }

    /**
     * Get current widget
     */
    public JTextComponent getHelpTextWidget()
    {
        return tHelp_;
    }

    /**
     * Widget to write to: this window's own widget, else the global fallback.
     */
    private JTextComponent target()
    {
        return tHelp_ != null ? tHelp_ : globalHelp_;
    }

    /**
     * Set message in help text area (ignores null/0 length messages so that
     * widgets w/no message don't erase previous message)
     */
    public void setHelpMessage(String sMessage)
    {
        JTextComponent t = target();
        if (t != null && sMessage != null && !sMessage.isEmpty())
        {
            t.setText(sMessage);
            t.repaint();
        }
    }

    /**
     * Set message in help text area
     */
    public void setMessage(String sMessage)
    {
        JTextComponent t = target();
        if (t != null)
        {
            if (sMessage == null) sMessage = EMPTY;
            t.setText(sMessage);
            t.repaint();
        }
    }

    /**
     * Clear message in help text area
     */
    public void clearMessage()
    {
        setMessage(EMPTY);
    }

    /**
     * Show help for this component
     */
    public void showHelp(DDComponent source)
    {
        // if skipping next, do so
        if (bIgnore_)
        {
            bIgnore_ = false;
            return;
        }

        if (target() != null && source != null)
        {
            String sHelp = null;
            if (source instanceof DDCustomHelp)
            {
                sHelp = ((DDCustomHelp) source).getHelpText();
            }

            if (sHelp == null)
            {
                sHelp = GuiManager.getDefaultHelp(source);
            }

            setHelpMessage(sHelp);
        }
    }

    /**
     * Set to ignore next mouse enter (so we don't show help)
     */
    public void ignoreNextHelp()
    {
        bIgnore_ = true;
    }
}
