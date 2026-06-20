/*
 * DDCheckBox.java
 *
 * Created on February 23, 2003, 3:19 PM
 */

package com.donohoedigital.gui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * @author Doug Donohoe
 */
public class DDCheckBox extends JCheckBox implements DDHasLabelComponent, DDValidatable, DDCustomHelp
{
    private Color cCheckColor_ = null;

    /**
     * Creates a new instance of DDCheckBox - sets name to sName
     */
    public DDCheckBox(String sName, String sStyleName)
    {
        super();
        init(sName, sStyleName);
    }

    /**
     * Return our type
     */
    public String getType()
    {
        return "checkbox";
    }

    /**
     * init
     */
    protected void init(String sName, String sStyleName)
    {
        GuiManager.init(this, sName, sStyleName);
        cCheckColor_ = getForeground();
        setOpaque(false);
        setIconTextGap(8);
        applyIconStyle();
    }

    /**
     * Style FlatLaf's checkbox icon so the box is filled with the component
     * background (the parent shows through) and the checkmark + border are drawn
     * in the foreground/check color.  See {@link GuiUtils#applyFlatIconStyle}.
     */
    private void applyIconStyle()
    {
        GuiUtils.applyFlatIconStyle(this, cCheckColor_, bDisplayOnly_);
    }

    /**
     * Override to check swing thread
     */
    @Override
    public void setText(String sMsg)
    {
        GuiUtils.requireSwingThread();
        super.setText(sMsg);
    }

    /**
     * Override checkbox color (also used as the checkmark color)
     */
    public void setCheckBoxColor(Color c)
    {
        cCheckColor_ = c;
        applyIconStyle();
    }

    /**
     * Swing doesn't exactly do semi-transparent correctly unless
     * you start with the highest parent w/ no transparency
     */
    @Override
    public void repaint(long tm, int x, int y, int width, int height)
    {
        if (!GuiUtils.repaint(this, x, y, width, height)) super.repaint(tm, x, y, width, height);
    }

    private boolean bDisplayOnly_ = false;

    public void setDisplayOnly(boolean b)
    {
        bDisplayOnly_ = b;
        setFocusable(!b);
        applyIconStyle();
    }

    @Override
    protected void processMouseEvent(MouseEvent e)
    {
        if (bDisplayOnly_)
        {
            switch (e.getID())
            {
                case MouseEvent.MOUSE_ENTERED:
                case MouseEvent.MOUSE_EXITED:
                    break; // allow these through so help text listeners still fire
                default:
                    return; // suppress press/release/click/etc.
            }
        }
        super.processMouseEvent(e);
    }

    @Override
    public boolean isValidData()
    {
        return true;
    }

    @Override
    public void addValidationListener(Runnable onChange)
    {
        addActionListener(_ -> onChange.run());
    }

    ///
    /// Custom help
    ///

    private String sHelp_;

    public String getHelpText()
    {
        return sHelp_;
    }

    public void setHelpText(String s)
    {
        sHelp_ = s;
    }
}
