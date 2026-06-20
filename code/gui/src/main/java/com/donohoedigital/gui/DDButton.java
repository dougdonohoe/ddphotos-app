package com.donohoedigital.gui;

import javax.swing.*;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import java.awt.event.KeyEvent;

/**
 *
 * @author  Doug Donohoe
 */
public class DDButton extends JButton implements DDHasLabelComponent, AncestorListener
{
    //static Logger logger = LogManager.getLogger(DDButton.class);

    private final String style_;

    /**
     * Creates a new instance of DDButton - sets name to sName
     */
    public DDButton(String sName, String sStyleName) {
        super();
        this.style_ = sStyleName;
        init(sName, sStyleName);
        GuiUtils.addKeyAction(this, JComponent.WHEN_FOCUSED,
                    "buttonenter", new GuiUtils.InvokeButton(this),
                    KeyEvent.VK_ENTER, 0);
    }

    /**
     * Return our type
     */
    public String getType()
    {
        return "button";
    }

    /**
     * Set the UI to DDButtonUI
     */
    protected void init(String sName, String sStyleName)
    {
        GuiManager.init(this, sName, sStyleName);
        setRolloverEnabled(true);
        setRequestFocusEnabled(false);
        addAncestorListener(this);
    }

    /**
     * Rename button to re-initialize label, help
     */
    public void rename(String sName) {
        GuiManager.init(this, sName, style_);
    }

    /**
     * Swing doesn't exactly do semi-transparent correctly unless
     * you start with the highest parent w/ no transparency
     */
    public void repaint(long tm, int x, int y, int width, int height)
    {
        if (!GuiUtils.repaint(this, x, y, width, height)) super.repaint(tm, x, y, width, height);
    }

    /**
     * Override to increase millis
     */
    public void doClick()
    {
        // must be less than 95 on Mac
        super.doClick(90);
    }
    
    /**
     * Implemented to set rollover to false when this
     * widget is added - swing doesn't reset when this (or parent)
     * is removed and later readded.
     */
    public void ancestorAdded(AncestorEvent event) 
    {
        getModel().setRollover(false);
    }

    /**
     * Empty
     */
    public void ancestorMoved(AncestorEvent event) {
    }

    /**
     * Empty
     */
    public void ancestorRemoved(AncestorEvent event) {
    }
    
}
