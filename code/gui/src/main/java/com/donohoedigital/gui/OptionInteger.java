/*
 * OptionInteger.java
 *
 * Created on April 16, 2003, 2:01 PM
 */

package com.donohoedigital.gui;

import com.donohoedigital.base.TypedHashMap;
import com.donohoedigital.config.PropertyConfig;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.util.Objects;

/**
 *
 * @author  donohoe
 */
public class OptionInteger extends DDOption implements ChangeListener
{
    //static Logger logger = LogManager.getLogger(OptionInteger.class);

    private DDLabel label_;
    private DDLabel leftlabel_;
    private final DDSpinner spinner_;
    private final Integer nDefault_;

    /**
     * Creates a new instance of OptionInteger
     */
    public OptionInteger(String sPrefNode, String sName, String sStyle,
                    TypedHashMap map, Integer nDefaultOverride,
                    int nMin, int nMax, int nWidth)
    {
        this(sPrefNode, sName, sStyle, map, nDefaultOverride,  nMin, nMax, nWidth, false);
    }

    /**
     * Creates a new instance of OptionInteger
     */
    public OptionInteger(String sPrefNode, String sName, String sStyle,
                    TypedHashMap map, Integer nDefaultOverride,
                    int nMin, int nMax, int nWidth, boolean bEditable)
    {
        this(sPrefNode, sName, sStyle, map, nDefaultOverride,  nMin, nMax, nWidth, bEditable, false);
    }

    public OptionInteger(String sPrefNode, String sName, String sStyle,
                    TypedHashMap map, Integer nDefaultOverride,
                    int nMin, int nMax, int nWidth, boolean bEditable, boolean bAlignTop)
    {
        super(sPrefNode, sName, sStyle, map);
        nDefault_ = Objects.requireNonNullElseGet(nDefaultOverride, () -> PropertyConfig.getRequiredIntegerProperty(getDefaultKey()));

        int nStep = PropertyConfig.getIntegerProperty("option." + sName_ + ".step", 1);

        // base
        setBorderLayoutGap(0, 8);

        // spinner
        int nValue = nDefaultOverride != null ? nDefaultOverride : prefs_.getInt(sName_, nDefault_);
        spinner_ = new DDSpinner(new SpinnerNumberModel(nValue, nMin, nMax, nStep), sName, STYLE);
        if (nWidth > 0)
        {
            Dimension pref = spinner_.getPreferredSize(); // tweak size
            spinner_.setPreferredSize(new Dimension(nWidth, pref.height));
        }
        spinner_.addChangeListener(this);
        spinner_.addMouseListener(this);
        spinner_.setEditable(bEditable);

        // label
        String sLabel = getLabel();
        if (!sLabel.isEmpty())
        {
            label_ = new DDLabel(GuiManager.DEFAULT, STYLE);
            if (bAlignTop) label_.setVerticalAlignment(SwingConstants.TOP);
            label_.setText(getLabel());
            label_.addMouseListener(this);
        }

        // see if we have a left label
        String sLeft = getLeftLabel();
        if (sLeft != null)
        {
            leftlabel_ = new DDLabel(GuiManager.DEFAULT, STYLE);
            leftlabel_.setText(sLeft);
            leftlabel_.addMouseListener(this);
        }

        // save
        saveToMap();

        // put it all together

        if (label_ != null) add(label_, BorderLayout.CENTER);
        if (leftlabel_ != null)
        {
            DDPanel left = new DDPanel();
            left.setBorderLayoutGap(0, getLeftGap());
            left.add(leftlabel_, BorderLayout.WEST);
            left.add(bAlignTop ? GuiUtils.NORTH(spinner_):GuiUtils.CENTER(spinner_), BorderLayout.CENTER);
            add(left, BorderLayout.WEST);
        }
        else
        {
            add(bAlignTop ? GuiUtils.NORTH(spinner_):GuiUtils.CENTER(spinner_), BorderLayout.WEST);
        }
    }

    /**
     * Get the spinner
     */
    public DDSpinner getSpinner()
    {
        return spinner_;
    }

    /**
     * Get the current value
     */
    public int getValue()
    {
        return (Integer) spinner_.getValue();
    }

    /**
     * Get the label
     */
    public JComponent getLabelComponent()
    {
        if (leftlabel_ != null) return leftlabel_;
        return label_;
    }

    /**
     * Is valid?
     */
    public boolean isValidData()
    {
        return true;
    }

    /**
     * Set spinner text field editable (not editable by default)
     */
    public void setEditable(boolean b)
    {
        spinner_.setEditable(b);
    }

    public void setDisplayOnly(boolean b)
    {
        spinner_.setDisplayOnly(b);
    }

    /**
     * Change maximum
     */
    public void setMaximum(int n)
    {
        SpinnerNumberModel model = (SpinnerNumberModel) spinner_.getModel();
        model.setMaximum(n);
        if (((Number) spinner_.getValue()).intValue() > n) spinner_.setValue(n);
    }

    /**
     * set disabled
     */
    public void setEnabled(boolean b)
    {
        spinner_.setEnabled(b);
        if (label_ != null) label_.setEnabled(b);
        if (leftlabel_ != null) leftlabel_.setEnabled(b);
    }

    /**
     * Is enabled?
     */
    public boolean isEnabled()
    {
        return spinner_.isEnabled();
    }

    /**
     * Only disabled spinner
     */
    public void setEnabledEmbedded(boolean b)
    {
        spinner_.setEnabled(b);
    }

    /**
     * Invoked when spinner changed
     */
    public void stateChanged(ChangeEvent e)
    {
        prefs_.putInt(sName_, (Integer) spinner_.getValue());
        saveToMap();
        fireStateChanged();
    }

    /**
     * Save value to map
     */
    public void saveToMap()
    {
        map_.setInteger(sName_, (Integer) spinner_.getValue());
    }

    /** reset to default value (triggers stateChanged())
     *
     */
    public void resetToDefault()
    {
        spinner_.setValue(nDefault_);
    }

    public void resetToPrefs()
    {
        spinner_.setValue(prefs_.getInt(sName_, nDefault_));
    }

    /**
     * reset to value in map (triggers stateChanged())
     */
    public void resetToMap()
    {
        spinner_.setValue(map_.getInteger(sName_, nDefault_));
    }

}
