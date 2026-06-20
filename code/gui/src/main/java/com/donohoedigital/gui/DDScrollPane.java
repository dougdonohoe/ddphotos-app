package com.donohoedigital.gui;

import javax.swing.*;
import java.awt.*;

/**
 * Created by IntelliJ IDEA.
 * User: donohoe
 * Date: Mar 16, 2005
 * Time: 8:31:40 PM
 * To change this template use File | Settings | File Templates.
 */
public class DDScrollPane extends JScrollPane implements DDComponent
{
    public DDScrollPane(Component view, String sStyle, int nVerticalPolicy, int nHorizPolicy)
    {
        super(view);
        setOpaque(false);
        setHorizontalScrollBarPolicy(nHorizPolicy);
        setVerticalScrollBarPolicy(nVerticalPolicy);
        setBorder(BorderFactory.createEmptyBorder());
        GuiManager.init(this, GuiManager.DEFAULT, sStyle);
    }

    public void setOpaque(boolean b)
    {
        super.setOpaque(b);
        getViewport().setOpaque(b);
    }

    public String getType()
    {
        return "scrollpane";
    }
}
