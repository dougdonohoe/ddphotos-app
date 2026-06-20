package com.donohoedigital.gui;

import org.intellij.lang.annotations.MagicConstant;

import javax.swing.*;
import java.awt.*;

/**
 * Created by IntelliJ IDEA.
 * User: donohoe
 * Date: Jun 20, 2006
 * Time: 7:20:39 PM
 * To change this template use File | Settings | File Templates.
 */
public class DDSplitPane extends JSplitPane implements DDComponent
{
    private Color thumbFocusOverlay_=  null;

    public DDSplitPane(String sName, String sStyle,
                       @MagicConstant(intValues = {JSplitPane.HORIZONTAL_SPLIT,JSplitPane.VERTICAL_SPLIT})
                       int newOrientation, Component newLeftComponent, Component newRightComponent,
                       boolean bOpaque)
    {
        super(newOrientation, newLeftComponent, newRightComponent);
        setDividerSize(10);
        setOpaque(bOpaque);
        setContinuousLayout(true);
        setResizeWeight(.5);
        setBorder(BorderFactory.createEmptyBorder());
        setFocusable(true);

        GuiManager.init(this, sName, sStyle);
    }

    /**
     *  DD component type
     */
    public String getType()
    {
        return "split";
    }

    public Color getThumbFocusColor()
    {
        return thumbFocusOverlay_;
    }

    public void setThumbFocusColor(Color c)
    {
        thumbFocusOverlay_ = c;
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
