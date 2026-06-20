/*
 * DDTabbedPane.java
 *
 * Created on June 16, 2003, 6:36 PM
 */

package com.donohoedigital.gui;

import com.donohoedigital.config.*;
import org.intellij.lang.annotations.MagicConstant;

import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;

/**
 *
 * @author  Doug Donohoe
 */
public class DDTabbedPane extends JTabbedPane implements DDHasLabelComponent, ChangeListener,
                                                         MouseMotionListener, MouseListener
{
    //static Logger logger = LogManager.getLogger(DDTabbedPane.class);

    private static final Color DEFAULT_SELECTED = new Color(255,255,255,175);
    private Color cSelectedTab_ = DEFAULT_SELECTED;

    public DDTabbedPane(String sStyle,
                        @MagicConstant(intValues = {SwingConstants.TOP, SwingConstants.BOTTOM,
                                                    SwingConstants.LEFT, SwingConstants.RIGHT})
                        int nPlacement)
    {
        super(nPlacement);
        init(sStyle);
    }

    private void init(String sStyle)
    {        
        GuiManager.init(this, GuiManager.DEFAULT, sStyle);
        addChangeListener(this);
        addMouseMotionListener(this);
        addMouseListener(this);
        setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
    }

    public void addTab(String sTitleKey, Icon icon, Icon error, DDTabPanel tab)
    {
        super.addTab(PropertyConfig.getMessage(sTitleKey), icon, tab, null);
        tab.setTabNum(getTabCount() - 1);
        tab.setIcon(icon);
        tab.setErrorIcon(error);
        tab.setTabPane(this);
        tab.setHelpText(PropertyConfig.getStringProperty(sTitleKey + ".help", null, false));
    }


    /**
     * tab changed - clear help
     */
    public void stateChanged(ChangeEvent e)
    {
        setHelp(getSelectedIndex());
    }

    /**
     * set help text for given tab
     */
    private void setHelp(int i)
    {
        Component c = getComponentAt(i);
        if (c instanceof DDTabPanel)
        {
            String sHelp = ((DDTabPanel)c).getHelpText();
            DDWindow window = GuiUtils.getHelpManager(c);
            if (sHelp != null && window != null) {
                window.setHelpMessage(sHelp);
            }
        }
    }

    /**
     * Do valid check over all tabs
     */
    public boolean doValidCheck()
    {
        boolean bValid = true;
        int nNum = getTabCount();
        Component c;
        for (int i = 0; i < nNum; i++)
        {
            c = getComponentAt(i);
            if (c instanceof DDTabPanel)
            {
                bValid &= ((DDTabPanel)c).doValidCheck();
            }
        }

        return bValid;
    }

    public String getType()
    {
        return "tab";
    }
    
    // needed for DDHasLabel interface
    public String getText() {
        return null;
    }
    
    public void setText(String s) {
    }

    public Color getSelectedTabColor()
    {
        return cSelectedTab_;
    }

    public void setSelectedTabColor(Color c)
    {
        cSelectedTab_ = c;
    }


    /**
     * Swing doesn't exactly do semi-transparent correctly unless
     * you start with the highest parent w/ no transparency
     */
    public void repaint(long tm, int x, int y, int width, int height)
    {
        if (!GuiUtils.repaint(this, x, y, width, height)) super.repaint(tm, x, y, width, height);
    }

    ///
    /// mouse events to do mouse over help
    ///

    public void mouseDragged(MouseEvent e)
    {
    }

    private int nLastTab = -2;
    public void mouseMoved(MouseEvent e)
    {
        int i = getUI().tabForCoordinate(this, e.getX(), e.getY());
        if (i != nLastTab)
        {
            nLastTab = i;
            if (i != -1) setHelp(i);
        }
    }

    public void mouseClicked(MouseEvent e)
    {
    }

    public void mousePressed(MouseEvent e)
    {
    }

    public void mouseReleased(MouseEvent e)
    {
    }

    public void mouseEntered(MouseEvent e)
    {
    }

    public void mouseExited(MouseEvent e)
    {
        nLastTab = -2;
    }
}
