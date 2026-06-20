/*
 * DDRadioButton.java
 *
 * Created on March 30, 2003, 3:19 PM
 */

package com.donohoedigital.gui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 *
 * @author  Doug Donohoe
 */
public class DDRadioButton extends JRadioButton implements DDHasLabelComponent
{
    //static Logger logger = LogManager.getLogger(DDRadioButton.class);
    private Color cDotColor_ = null;

    /** 
     * Creates a new instance of DDRadioButton - sets name to sName
     */
    public DDRadioButton(String sName, String sStyleName) {
        super();
        init(sName, sStyleName);
    }
    
    /**
     * Return our type
     */
    public String getType() 
    {
        return "radio";
    }
    
    /**
     * Set the UI to DDRadioButtonUI
     */
    protected void init(String sName, String sStyleName)
    {
        GuiManager.init(this, sName, sStyleName);
        cDotColor_ = getForeground();
        setOpaque(false);
        setIconTextGap(8);
        applyIconStyle();
    }

    /**
     * Override dot color (also used as the dot/border color)
     */
    public void setDotColor(Color c)
    {
        cDotColor_ = c;
        applyIconStyle();
    }

    /**
     * Style FlatLaf's radio-button icon so the circle is filled with the component
     * background (the parent shows through) and the dot + border are drawn in the
     * foreground/dot color.  See {@link GuiUtils#applyFlatIconStyle}.
     */
    private void applyIconStyle()
    {
        GuiUtils.applyFlatIconStyle(this, cDotColor_, bDisplayOnly_);
    }

    private boolean bDisplayOnly_ = false;

    /**
     * Display-only mode: the radio looks normal (enabled) but is not
     * interactive - no focus, no selection change.  Mirrors DDCheckBox so
     * read-only radios are not greyed out.
     */
    public void setDisplayOnly(boolean b)
    {
        bDisplayOnly_ = b;
        setFocusable(!b);
        applyIconStyle();
    }

    /**
     * Is this radio display-only?
     */
    public boolean isDisplayOnly()
    {
        return bDisplayOnly_;
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
    
    /**
     * Swing doesn't exactly do semi-transparent correctly unless
     * you start with the highest parent w/ no transparency
     */
    public void repaint()
    {
        if (!GuiUtils.repaint(this)) super.repaint();
    }
}
