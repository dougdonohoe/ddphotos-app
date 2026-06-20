/*
 * DDImageButton.java
 *
 * Created on January 4, 2003, 8:06 PM
 */

package com.donohoedigital.gui;

import com.donohoedigital.config.ImageConfig;

import javax.swing.*;
import java.awt.*;

/**
 * @author Doug Donohoe
 */
public class DDImageButton extends DDButton
{
    private boolean bTransparencyIgnored_ = true;

    /**
     * Creates a new instance of DDImageButton
     */
    public DDImageButton(String sName)
    {
        super(sName, null);
    }

    /**
     * init - get image icons and adjust other items
     */
    @Override
    protected void init(String sName, String sStyleName)
    {
        super.init(sName, GuiManager.DEFAULT);
        ImageIcon icon = ImageConfig.getImageIcon("button." + sName + ".up", null);
        ImageIcon roll = ImageConfig.getImageIcon("button." + sName + ".over", icon);
        ImageIcon press = ImageConfig.getImageIcon("button." + sName + ".down", icon);
        ImageIcon disable = ImageConfig.getImageIcon("button." + sName + ".disable", icon);
        setIcon(icon);
        setRolloverIcon(roll);
        setPressedIcon(press);
        setDisabledIcon(disable);
        setSize(getIcon().getIconWidth(), getIcon().getIconHeight());
        setText(null);
        setOpaque(false);
        setBorderPainted(false);
        setFocusable(false);
        setFocusPainted(false);
        setContentAreaFilled(false);
        setHorizontalAlignment(SwingConstants.CENTER);
        setVerticalAlignment(SwingConstants.CENTER);
    }

    /**
     * Need to repaint after rename, due to changed images.
     */
    @Override
    public void rename(String sName)
    {
        super.rename(sName);
        init(sName, (String) null);
    }

    /**
     * Is transparent portion of image ignored?
     */
    public boolean isTransparentIgnored()
    {
        return bTransparencyIgnored_;
    }

    /**
     * Set transparent ignored (default is true)
     */
    public void setTransparentIgnored(boolean b)
    {
        bTransparencyIgnored_ = b;
    }

    /**
     * Override to make public
     */
    @Override
    public void paintComponent(Graphics g1)
    {
        super.paintComponent(g1);
    }
}
