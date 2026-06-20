/*
 * EngineBasePanel.java
 *
 * Created on November 15, 2002, 2:38 PM
 */

package com.donohoedigital.app.engine;

import static com.donohoedigital.config.DebugConfig.*;
import com.donohoedigital.config.*;
import com.donohoedigital.app.config.*;
import com.donohoedigital.gui.*;
import org.apache.logging.log4j.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;

/**
 * @author Doug Donohoe
 */
public class EngineBasePanel extends JPanel
{
    static Logger logger = LogManager.getLogger(EngineBasePanel.class);

    JComponent bottom_;
    Component center_ = null;
    Component focus_ = null;
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

        if (TESTING(EngineConstants.TESTING_PERFORMANCE))
        {
            GuiUtils.addKeyAction(this, JComponent.WHEN_IN_FOCUSED_WINDOW,
                                  "perf", new DebugPerf(), KeyEvent.VK_P, 0);
            GuiUtils.addKeyAction(this, JComponent.WHEN_IN_FOCUSED_WINDOW,
                                  "gc", new DebugGC(), KeyEvent.VK_G, 0);
            GuiUtils.addKeyAction(this, JComponent.WHEN_IN_FOCUSED_WINDOW,
                                  "objcount", new DebugCount(), KeyEvent.VK_O, 0);
        }
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

    /**
     * Called when 'p' pressed - toggles Perf on/off
     */
    private static class DebugPerf extends AbstractAction
    {
        public void actionPerformed(ActionEvent e)
        {
            if (Perf.isStarted())
            {
                Perf.stop();
            }
            else
            {
                Perf.start();
            }
        }
    }

    /**
     * Called when 'o' pressed - display object count
     */
    private static class DebugCount extends AbstractAction
    {
        public void actionPerformed(ActionEvent e)
        {
            Perf.displayCurrentCount();
        }
    }

    /**
     * Called when 'g' pressed - run GC
     */
    private static class DebugGC extends AbstractAction
    {
        public void actionPerformed(ActionEvent e)
        {
            System.gc();
            logger.debug("Running GC....");
        }
    }
}
