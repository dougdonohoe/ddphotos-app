/*
 * DDSlider.java
 *
 * Created on June 5, 2003, 6:03 PM
 */

package com.donohoedigital.gui;

import javax.swing.*;
import java.awt.*;

/**
 *
 * @author  Doug Donohoe
 */
public class DDSlider extends JSlider implements DDComponent 
{
    //static Logger logger = LogManager.getLogger(DDSlider.class);

    private Color thumbFocusOverlay_=  null;
    private Color thumbBg_ = null;

    public DDSlider(String sName, String sStyle)
    {
        super();
        init(sName, sStyle);
    }
    
    private void init(String sName, String sStyle)
    {
        GuiManager.init(this, sName, sStyle);
        setOpaque(false);
    }
    
    public String getType() 
    {
        return "slider";
    }

    public Color getThumbFocusColor()
    {
        return thumbFocusOverlay_;
    }

    public void setThumbFocusColor(Color c)
    {
        thumbFocusOverlay_ = c;
    }

    public Color getThumbBackgroundColor()
    {
        return thumbBg_;
    }

    public void setThumbBackgroundColor(Color c)
    {
        thumbBg_ = c;
    }

    /**
     * Swing doesn't exactly do semi-transparent correctly unless
     * you start with the highest parent w/ no transparency
     */
    public void repaint(long tm, int x, int y, int width, int height)
    {
        if (!GuiUtils.repaint(this, x, y, width, height)) super.repaint(tm, x, y, width, height);
    }
}
