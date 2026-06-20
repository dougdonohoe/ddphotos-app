/*
 * OptionText.java
 *
 * Created on April 16, 2003, 2:01 PM
 */

package com.donohoedigital.gui;

import com.donohoedigital.base.*;
import com.donohoedigital.config.*;

import javax.swing.*;
import java.awt.*;
import java.beans.*;
/**
 *
 * @author  donohoe
 */
public class OptionText extends DDOption implements PropertyChangeListener
{
    //static Logger logger = LogManager.getLogger(OptionText.class);
    private DDLabel label_;
    private final DDTextField text_;
    private final String sDefault_;

    public OptionText(String sPrefNode, String sName, String sStyle,
                    TypedHashMap map, int nLengthLimit, String sRegExp, int nWidth)
    {
        this(sPrefNode, sName, sStyle, map, nLengthLimit, sRegExp, nWidth, true);
    }

    public OptionText(String sPrefNode, String sName, String sStyle,
                      TypedHashMap map, int nLengthLimit, String sRegExp, int nWidth, boolean bLabel)
    {
        super(sPrefNode, sName, sStyle, map);
        sDefault_ = PropertyConfig.getRequiredStringProperty(getDefaultKey());

        // base
        setBorderLayoutGap(0, 8);

        // text
        text_ = new DDTextField(GuiManager.DEFAULT, STYLE);
        text_.setTextLengthLimit(nLengthLimit);
        if (sRegExp != null) text_.setRegExp(sRegExp);
        resetToPrefs();
        saveToMap();
        Dimension pref = text_.getPreferredSize(); // tweak size
        text_.setPreferredSize(new Dimension(nWidth, pref.height));
        text_.addPropertyChangeListener("value", this);
        text_.addMouseListener(this);

        // label
        if (bLabel)
        {
            label_ = new DDLabel(GuiManager.DEFAULT, STYLE);
            label_.setText(getLabel());
            label_.addMouseListener(this);

            // put it all together
            add(text_, BorderLayout.CENTER);
            add(label_, BorderLayout.WEST);
        }
        else
        {
            add(text_, BorderLayout.CENTER);
            setPreferredSize(text_.getPreferredSize());
        }
    }
    
    /**
     * Get the spinner
     */
    public DDTextField getTextField()
    {
        return text_;
    }

    public String getText() { return text_.getText(); }
    public void setText(String value) { text_.setText(value); }

    /**
     * Get the label
     */
    public JComponent getLabelComponent()
    {
        return label_;
    }

    /**
     * Is valid?
     */
    public boolean isValidData()
    {
        return text_.isValidData();
    }
    
    /**
     * Set display only
     */
    public void setDisplayOnly(boolean b)
    {
        text_.setDisplayOnly(b);
    }
    
    /**
     * set disabled
     */
    public void setEnabled(boolean b)
    {
        text_.setEnabled(b);
        if (label_ != null) label_.setEnabled(b);
    }
    
    /**
     * Is enabled?
     */
    public boolean isEnabled()
    {
        return text_.isEnabled();
    }
    
    /**
     * Only disabled spinner
     */
    public void setEnabledEmbedded(boolean b)
    {
        text_.setEnabled(b);
    }

    /** 
     * text field change
     */
    public void propertyChange(PropertyChangeEvent evt) 
    {
        fireStateChanged(); // for validation listeners
        if (!text_.isValidData()) return;
        prefs_.put(sName_, text_.getText().trim());
        saveToMap();
    }
    
    
    /**
     * Save value to map
     */
    public void saveToMap()
    {
        //logger.debug("Setting map " + sName_ +  " from text  "+ text_.getText().trim());
        map_.setString(sName_, text_.getText().trim());
    }
    
    /** reset to default value
     *
     */
    public void resetToDefault()
    {
        text_.setText(sDefault_);
    }

    public void resetToPrefs()
    {
        text_.setText(prefs_.get(sName_, sDefault_));
    }

    /**
     * reset to value in map
     */
    public void resetToMap()
    {
        //only update if changed (faster to compare then repaint text)
        String value = map_.getString(sName_, sDefault_);
        String current = text_.getText();
        if (!value.equals(current))
        {
            //logger.debug("Setting text " + sName_ + " from map " + value);
            text_.setText(value);
        }
    }
}
