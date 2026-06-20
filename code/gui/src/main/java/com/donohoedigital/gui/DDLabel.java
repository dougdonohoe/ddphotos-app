/*
 * DDLabel.java
 *
 * Created on November 17, 2002, 3:47 PM
 */

package com.donohoedigital.gui;

import javax.swing.*;
import java.awt.*;

/**
 * @author Doug Donohoe
 */
public class DDLabel extends JLabel implements DDHasLabelComponent, DDText, DDCustomHelp
{
    /**
     * Creates a new instance of DDLabel
     */
    public DDLabel()
    {
        super();
        init(GuiManager.DEFAULT, GuiManager.DEFAULT);
    }

    public DDLabel(String sName)
    {
        super();
        init(sName, GuiManager.DEFAULT);
    }

    public DDLabel(String sName, String sStyle)
    {
        super();
        init(sName, sStyle);
    }

    private void init(String sName, String sStyle)
    {
        setOpaque(false);
        //addComponentListener(this);
        GuiManager.init(this, sName, sStyle);
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

    public String getType()
    {
        return "label";
    }

    public void setPreferredHeight(int height)
    {
        setPreferredSize(new Dimension((int) getPreferredSize().getWidth(), height));
    }

    public void setPreferredWidth(int width)
    {
        setPreferredSize(new Dimension(width, (int) getPreferredSize().getHeight()));
    }

    @Override
    public void setText(String s)
    {
        GuiUtils.requireSwingThread();

        // empty length strings cause issues, so avoid it
        if (s != null && s.isEmpty()) s = " ";

        // 133 - don't set if equal to current (saves memory when processing HTML)
        String sCurrent = getText();
        if (sCurrent != null && sCurrent.equals(s)) return;
        super.setText(s);
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
