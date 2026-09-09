/*
 * EngineBasePanel.java
 *
 * Created on November 15, 2002, 2:38 PM
 */

package com.donohoedigital.app.engine;

import com.donohoedigital.config.ImageConfig;
import com.donohoedigital.config.StylesConfig;
import com.donohoedigital.app.config.AppPhase;
import com.donohoedigital.gui.BaseFrame;
import com.donohoedigital.gui.CenterLayout;
import com.donohoedigital.gui.ImageComponent;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.LayoutManager;
import java.awt.image.BufferedImage;

/**
 * @author Doug Donohoe
 */
public class EngineBasePanel extends JPanel
{
    JComponent bottom_;
    Component center_;
    Component focus_;
    CenterLayout centerLayout_ = new CenterLayout();
    BorderLayout borderLayout_ = new BorderLayout();
    BaseFrame frame_;

    /**
     * Creates a new instance of EngineBasePanel
     */
    @SuppressWarnings({"ThisEscapedInObjectConstruction"})
    public EngineBasePanel(BaseFrame frame, AppPhase phase)
    {
        frame_ = frame;

        String sBackGroundImage = "engine.basepanel";
        if (phase != null)
        {
            sBackGroundImage = phase.getString("window-background", sBackGroundImage);
        }
        BufferedImage bi = ImageConfig.getBufferedImage(sBackGroundImage, false);

        if (bi != null)
        {
            ImageComponent ic;
            bottom_ = ic = new ImageComponent(sBackGroundImage, 1.0d);
            ic.setTile(true);
            setLayout(new BorderLayout());
            setOpaque(true);
            add(bottom_, BorderLayout.CENTER);

        }
        else
        {
            bottom_ = this;

        }

        setBackground(StylesConfig.getColor(sBackGroundImage, Color.black));
        setForeground(Color.white);
    }

    /**
     * Sets the current visible component
     */
    public void setCenterComponent(Component c, boolean bBorderLayout, Component cFocus)
    {
        if (center_ != null)
        {
            bottom_.remove(center_);
        }

        focus_ = cFocus;
        LayoutManager layout = bottom_.getLayout();

        if (bBorderLayout)
        {
            if (layout != borderLayout_)
            {
                bottom_.setLayout(borderLayout_);
            }
            bottom_.add(c, BorderLayout.CENTER);
        }
        else
        {
            if (layout != centerLayout_)
            {
                bottom_.setLayout(centerLayout_);
            }
            bottom_.add(c);
        }

        center_ = c;
        validate();
        repaint();

        // Upon change, change focus to this panel (old focus may have been
        // on widget in removed component)
        SwingUtilities.invokeLater(this::requestFocus);
    }

    /**
     * Override request focus to give focus to the specified component
     */
    @Override
    public void requestFocus()
    {
        //logger.debug("Requesting focus for " + focus_.getClass().getName());
        // BUG 26 - don't set focus if null (fixes problem where multiple
        // calls to this in a row not ordered)
        if (focus_ != null)
        {
            focus_.requestFocus();
        }
    }

    /**
     * Return base frame this is in
     */
    public BaseFrame getBaseFrame()
    {
        return frame_;
    }
}
