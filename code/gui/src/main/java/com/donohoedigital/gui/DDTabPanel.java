package com.donohoedigital.gui;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: donohoe
 * Date: Apr 3, 2005
 * Time: 4:04:34 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class DDTabPanel extends DDPanel implements AncestorListener
{
    private DDTabbedPane tabPane_;
    private int nTabNum_;
    private Icon icon_;
    private Icon error_;
    private final List<DDOption> options_ = new ArrayList<>();
    private String sHelp_;

    public DDTabPanel(Border border) {
        super();
        setBorder(border);
        addAncestorListener(this);
    }

    void setTabPane(DDTabbedPane tab)
    {
        tabPane_ = tab;
    }

    void setHelpText(String sText)
    {
        sHelp_ = sText;
    }

    public String getHelpText()
    {
        return sHelp_;
    }

    public DDTabbedPane getTabPane()
    {
        return tabPane_;
    }

    public boolean isSelectedTab()
    {
        return tabPane_.getSelectedIndex() == nTabNum_;
    }

    void setTabNum(int n)
    {
        nTabNum_ = n;
    }

    public int getTabNum()
    {
        return nTabNum_;
    }

    public void setIcon(Icon icon)
    {
        icon_ = icon;
    }

    public Icon getIcon()
    {
        return icon_;
    }

    public void setErrorIcon(Icon icon)
    {
        error_ = icon;
    }

    public Icon getErrorIcon()
    {
        return error_;
    }

    private void setValid(boolean b)
    {
        tabPane_.setIconAt(nTabNum_, b ? icon_ : error_);
    }

    /**
     * Do valid check (which sets error icon appropriately),
     * and return whether tab contents is valid
     */
    protected boolean doValidCheck()
    {
        options_.clear();
        GuiUtils.getDDOptions(this, options_);
        DDOption dd;
        boolean bValid = true;
        for (int i = 0; bValid && i < options_.size(); i++)
        {
            dd = options_.get(i);
            if (!dd.isEnabled()) continue;
            bValid = dd.isValidData();
        }

        // also do isValidCheck
        bValid &= isValidCheck();

        // set icon
        setValid(bValid);

        return bValid;
    }

    /**
     * subclass can chime in on validity
     */
    protected boolean isValidCheck()
    {
        return true;
    }

    /**
     * Create the UI components (called lazily from ancestorAdded())
     */
    protected abstract void createUI();

    /**
     * initialize the UI
     */
    public void initUI()
    {
        if (getComponentCount() > 0) return;

        createUI();
        repaint();
    }

    public void ancestorAdded(AncestorEvent event)
    {
        initUI();
    }

    public void ancestorMoved(AncestorEvent event)
    {
    }

    public void ancestorRemoved(AncestorEvent event)
    {
    }

}