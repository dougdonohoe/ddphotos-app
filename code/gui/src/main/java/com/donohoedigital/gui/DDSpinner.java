/*
 * DDSpinner.java
 */

package com.donohoedigital.gui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseListener;

/**
 * A JSpinner that follows the DD widget conventions (colors/fonts picked
 * up via {@link GuiManager#init}, display-only mode, custom help, etc.),
 * mirroring the pattern used by {@link DDTextField}.
 *
 * @author Doug Donohoe
 */
public class DDSpinner extends JSpinner implements DDTextVisibleComponent, DDText, DDCustomHelp, DDValidatable
{
    private final JFormattedTextField text_;
    private final Color origBg_;
    private String sHelp_;

    public DDSpinner(SpinnerNumberModel model, String sName, String sStyle)
    {
        super(model);
        setEditor(new NumberEditor(this, "#"));
        text_ = ((DefaultEditor) getEditor()).getTextField();

        GuiManager.init(this, sName, sStyle);
        origBg_ = getBackground();
    }

    /**
     * Return our type
     */
    public String getType()
    {
        return "spinner";
    }

    /**
     * Set spinner text field editable
     */
    public void setEditable(boolean b)
    {
        text_.setEditable(b);
    }

    public boolean isEditable()
    {
        return text_.isEditable();
    }

    /**
     * Display-only: show value without editing controls, no disabled appearance.
     */
    public void setDisplayOnly(boolean b)
    {
        text_.setEditable(!b);
        text_.setFocusable(!b);
        setOpaque(!b); // this seems to be ignored, which is why we set the background below
        setBackground(b ? getParent().getBackground() : origBg_);

        // change spinner buttons
        for (Component c : getComponents())
        {
            if (c != getEditor()) c.setEnabled(!b);
        }
        repaint();
    }

    ////
    //// DDText
    ////

    public void setText(String s)
    {
        text_.setText(s);
    }

    public String getText()
    {
        return text_.getText();
    }

    ////
    //// DDCustomHelp
    ////

    public String getHelpText()
    {
        return sHelp_;
    }

    public void setHelpText(String s)
    {
        sHelp_ = s;
    }

    ////
    //// DDValidatable
    ////

    public boolean isValidData()
    {
        return text_.isEditValid();
    }

    public void addValidationListener(Runnable onChange)
    {
        text_.addPropertyChangeListener("value", _ -> onChange.run());
    }

    /**
     * Propagate GuiManager's mouse listener to children so help text
     * shows when hovering over the text field or spin buttons
     */
    @Override
    public void addMouseListener(MouseListener listener)
    {
        if (listener instanceof GuiManager)
        {
            GuiUtils.addMouseListenerChildren(this, listener);
        }
        super.addMouseListener(listener);
    }

    @Override
    public void removeMouseListener(MouseListener listener)
    {
        if (listener instanceof GuiManager)
        {
            GuiUtils.removeMouseListenerChildren(this, listener);
        }
        super.removeMouseListener(listener);
    }
}
