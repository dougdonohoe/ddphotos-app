/*
 * DDMenu.java
 *
 * Created on January 12, 2002, 5:57 PM
 */

package com.donohoedigital.gui;


import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;

/**
 * Should be identical to DDMenuItem since JMenu extends JMenuItem
 * 
 * @author  Doug Donohoe
 */
public class DDMenu extends JMenu implements DDHasLabelComponent 
{
    Border swingBorder_;

    public DDMenu(String sName)
    {
        super();
        init(sName, GuiManager.DEFAULT);
    }

    private void init(String sName, String sStyle)
    {
        GuiManager.init(this, sName, sStyle);
        swingBorder_ = getBorder();
    }

    public String getType()
    {
        return "menu";
    }
    
    boolean bDisplayOnly_ = false;
    
    /**
     * Set item display only - useful for
     * informational menu items
     */
    public void setDisplayOnly(boolean b)
    {
        bDisplayOnly_ = b;
        setEnabled(!b);
        if (b && getIcon() != null)
        {
            setDisabledIcon(getIcon());
        }
        
        if (b)
        {
            setHorizontalAlignment(SwingConstants.CENTER);
        }
        else
        {
            setHorizontalAlignment(SwingConstants.LEFT);
        }
    }
    
    /**
     * Is menuitem display only?
     */
    public boolean isDisplayOnly()
    {
        return bDisplayOnly_;
    }

    /**
     * Overridden to set disabled icon
     * to icon also if isDisplayOnly() true
     */
    public void setIcon(Icon icon)
    {
        super.setIcon(icon);
        if (bDisplayOnly_)
        {
            setDisabledIcon(icon);
        }
    }
    
    Border newBorder_;
    
    public void setBorder(Border b)
    {
        newBorder_ = b;
        super.setBorder(b);
    }
    
    /**
     * Overridden to paint user border if set
     */
    protected void paintBorder(Graphics g) {    
        if (isBorderPainted()) {
            if (this.getModel().isArmed())
            {
                swingBorder_.paintBorder(this, g, 0, 0, getWidth(), getHeight());
            }
            else
            {
                newBorder_.paintBorder(this, g, 0, 0, getWidth(), getHeight());
            }
        }
    }
}
