package com.donohoedigital.app.engine;

import com.donohoedigital.base.*;
import com.donohoedigital.gui.*;
import org.apache.logging.log4j.*;
import com.donohoedigital.config.*;
import com.donohoedigital.app.config.*;

import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;

public class EngineDialog extends InternalDialog
{
    protected static Logger logger = LogManager.getLogger(EngineDialog.class);

    private final AppContext context_;
    private DialogBackground base_;
    private Component focus_;
    private final int DESIRED_WIDTH;
    private final int DESIRED_HEIGHT;

    /**
     * Constructor
     */
    EngineDialog(AppContext context, String sName, int nDesiredMinWidth, int nDesiredMinHeight)
    {
        super(context.getFrame(), sName, sName, false);
        context_ = context;
        DESIRED_WIDTH = nDesiredMinWidth;
        DESIRED_HEIGHT = nDesiredMinHeight;

        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    }

    /**
     * init to given size (or full screen if passed in)
     */
    public void init(AppPhase phase, String sTitle, boolean bResizable)
    {
        ImageIcon winicon = ImageConfig.getImageIcon(phase.getString("dialog-windowtitle-image", "dialog-windowtitle-image"));
        setFrameIcon(winicon);
        setResizable(bResizable);

        // create base panel and setup frame
        base_ = new DialogBackground(context_, phase, null, false, null);
        base_.setPreferredSize(new Dimension(DESIRED_WIDTH, DESIRED_HEIGHT));

        // focus stuff
        base_.setFocusTraversalKeysEnabled(false); // prevent focus from leaving panel via tab

        // add actions for alt-q and ctrl-q, meta-w, alt-w, ctrl-w
        AbstractAction close = new CloseAction();

        // alt-w and ctrl-w close window on windows/linux (or quit if main window)
        if (!Utils.ISMAC)
        {
            GuiUtils.addKeyAction(base_, JComponent.WHEN_IN_FOCUSED_WINDOW,
                            "appengineclose", close,
                            KeyEvent.VK_W, KeyEvent.CTRL_DOWN_MASK);
            GuiUtils.addKeyAction(base_, JComponent.WHEN_IN_FOCUSED_WINDOW,
                            "appengineclose", close,
                            KeyEvent.VK_W, KeyEvent.ALT_DOWN_MASK);
        }
        // Mac version is apple-w
        else
        {
            GuiUtils.addKeyAction(base_, JComponent.WHEN_IN_FOCUSED_WINDOW,
                        "appengineclose", close,
                        KeyEvent.VK_W, KeyEvent.META_DOWN_MASK);
        }


        // the base_ is the content pane for the BaseApp frame_
        setContentPane(base_);
        setBackground(base_.getBackground());
        setTitle(sTitle);
    }

    /**
     * display
     */
    void display(InternalFrameListener listener)
    {
        showDialog(listener, InternalDialog.POSITION_CENTER, focus_);
    }

    /**
     * Close action
     */
    private class CloseAction extends AbstractAction
    {
       public void actionPerformed(ActionEvent e)
       {
            context_.close();
       }
    }

    /**
     * Get context
     */
    public AppContext getAppContext()
    {
        return context_;
    }

    /**
     * Set center component
     */
    void setCenterComponent(JComponent c, Component focus)
    {
        base_.setCenterContents(c);
        focus_ = focus;
    }

    /**
     * Get base panel
     */
    DDPanel getBasePanel()
    {
        return base_;
    }
}
