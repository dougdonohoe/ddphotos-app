/*
 * DDMenuItem.java
 *
 * Created on January 10, 2002, 11:39 AM
 */

package com.donohoedigital.gui;


import com.donohoedigital.base.*;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;

/**
 * Should be identical to DDMenu since JMenu extends JMenuItem
 *
 * @author  Doug Donohoe
 */
public class DDMenuItem extends JMenuItem implements DDHasLabelComponent 
{
    private final Border BORDER_INDENT1 = BorderFactory.createEmptyBorder(1, 20, 1, 1);
    private final Border BORDER_INDENT2 = BorderFactory.createEmptyBorder(1, 40, 1, 1);
    private final Border BORDER_TITLE = BorderFactory.createEmptyBorder(1, 1, 1, 30);

    public static final int MODE_TITLE = 1;
    public static final int MODE_NORMAL = 2;
    public static final int MODE_INDENT1 = 3;
    public static final int MODE_INDENT2 = 4;

    Border swingBorder_;

    public DDMenuItem(String sName)
    {
        super();
        init(sName, GuiManager.DEFAULT);
    }

    private void init(String sName, String sStyle)
    {
        GuiManager.init(this, sName, sStyle);
        swingBorder_ = getBorder();
    }

    public void setDisplayMode(int nType)
    {
        switch (nType)
        {
            case MODE_TITLE:
                setBorder(BORDER_TITLE);
                // FIX: determine workaround for JDK 1.6 JMenuItem bug
                setHorizontalAlignment(SwingConstants.CENTER);
                setBackground(Color.white);
                setDisplayOnly(true);
                break;

            case MODE_NORMAL:
                setHorizontalAlignment(SwingConstants.LEFT);
                break;

            case MODE_INDENT1:
                setBorder(BORDER_INDENT1);
                setHorizontalAlignment(SwingConstants.LEFT);
                break;

            case MODE_INDENT2:
                setBorder(BORDER_INDENT2);
                setHorizontalAlignment(SwingConstants.LEFT);
                break;
        }
    }

    public String getType() 
    {
        return "menuitem";
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
