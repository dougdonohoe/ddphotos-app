/*
 * DDPanel.java
 *
 * Created on November 17, 2002, 3:47 PM
 */

package com.donohoedigital.gui;

import org.apache.logging.log4j.*;

import javax.swing.*;
import java.awt.*;

/**
 * @author Doug Donohoe
 */
public class DDPanel extends JPanel implements DDComponent
{
    /**
     * Create panel with CenterLayout
     */
    public static DDPanel CENTER()
    {
        DDPanel newpanel = new DDPanel();
        newpanel.setLayout(new CenterLayout());
        return newpanel;
    }

    /**
     * Adjust border layout.  Will throw ClassCastException if
     * this panel is using another layout
     */
    public void setBorderLayoutGap(int vGap, int hGap)
    {
        BorderLayout layout = (BorderLayout) getLayout();
        layout.setVgap(vGap);
        layout.setHgap(hGap);
    }

    /**
     * Creates a new instance of DDPanel
     */
    public DDPanel()
    {
        super();
        init(GuiManager.DEFAULT, GuiManager.DEFAULT);
    }

    public DDPanel(String sName)
    {
        super();
        init(sName, GuiManager.DEFAULT);
    }

    public DDPanel(String sName, String sStyle)
    {
        super();
        init(sName, sStyle);
    }

    private void init(String sName, String sStyle)
    {
        setOpaque(false);
        setLayout(new BorderLayout());
        GuiManager.init(this, sName, sStyle);
    }

    /**
     * Swing doesn't exactly do semi-transparent correctly unless
     * you start with the highest parent w/ no transparency
     */
    @Override
    public void repaint(long tm, int x, int y, int width, int height)
    {
        if (debug) logger.debug("REPAINT: {}", this);
        if (!GuiUtils.repaint(this, x, y, width, height)) super.repaint(tm, x, y, width, height);
    }

    Rectangle bounds_ = new Rectangle();
    private static final Logger logger = LogManager.getLogger(DDPanel.class);
    private static int CNT = 0;

    /**
     * Override to set antialiasing hit if isAntiAlias() is true
     */
    @Override
    public void paintComponent(Graphics g1)
    {
        Graphics2D g = (Graphics2D) g1;

        if (debug)
        {
            Component foo = GuiUtils.getSolidRepaintComponent(this);
            if (foo != null && foo != this)
            {
                logger.debug("painting {}\nbut solid repaint is: {}", this, foo);
            }
            g.getClipBounds(bounds_);
            logger.debug("REPAINT COMPONENT {} ({}) portion {},{} {}x{}", CNT++, ImageComponent.getDebugColorName(), bounds_.x, bounds_.y, bounds_.width, bounds_.height);
            g.setColor(ImageComponent.getDebugColor());
            g.drawRect(bounds_.x, bounds_.y, bounds_.width - 1, bounds_.height - 1);
        }
        
        super.paintComponent(g);
    }

    private boolean debug = false;

    public void setDebug(boolean debug)
    {
        this.debug = debug;
    }

    public String getType()
    {
        return "panel";
    }

    public void setPreferredHeight(int height)
    {
        setPreferredSize(new Dimension((int) getPreferredSize().getWidth(), height));
    }

    public void setPreferredWidth(int width)
    {
        setPreferredSize(new Dimension(width, (int) getPreferredSize().getHeight()));
    }
}
