/*
 * OptionSlider.java
 *
 * Created on June 05, 2003, 6:06 PM
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
public class OptionSlider extends DDOption implements ChangeListener
{
    //static Logger logger = LogManager.getLogger(OptionSlider.class);
    
    private final DDLabel label_;
    private final DDSlider slider_;
    private final Integer nDefault_;

    /** 
     * Creates a new instance of OptionSlider 
     */
    public OptionSlider(String sPrefNode, String sName, String sStyle,
                    TypedHashMap map, Integer nDefaultOverride,
                    int nMin, int nMax, int nWidth)
    {
        super(sPrefNode, sName, sStyle, map);
        nDefault_ = Objects.requireNonNullElseGet(nDefaultOverride, () -> PropertyConfig.getRequiredIntegerProperty(getDefaultKey()));
        
        int nStep = PropertyConfig.getIntegerProperty("option." + sName_ + ".step", 10);
        
        // base
        setBorderLayoutGap(0, 8);
        
        // slider
        slider_ = new DDSlider(GuiManager.DEFAULT, STYLE);
        slider_.setMaximum(nMax);
        slider_.setMinimum(nMin);
        slider_.setMajorTickSpacing(nStep);
        slider_.setMinorTickSpacing(nStep);
        slider_.setPaintTicks(false);
        slider_.setValue(nDefaultOverride != null ? nDefaultOverride : prefs_.getInt(sName_, nDefault_));

        Dimension pref = slider_.getPreferredSize(); // tweak size
        slider_.setPreferredSize(new Dimension(nWidth,pref.height));
        slider_.addChangeListener(this);
        slider_.addMouseListener(this);

        // label
        label_ = new DDLabel(GuiManager.DEFAULT, STYLE);
        label_.setText(getLabel());
        label_.addMouseListener(this);
        saveToMap();
        
        // put it all together
        add(GuiUtils.CENTER(slider_), BorderLayout.EAST);
        add(label_, BorderLayout.CENTER);
    }

    /**
     * Get the slider
     */
    public DDSlider getSlider()
    {
        return slider_;
    }
    
    /**
     * Get the label
     */
    public JComponent getLabelComponent()
    {
        return label_;
    }

    /**
     * set disabled
     */
    public void setEnabled(boolean b)
    {
        slider_.setEnabled(b);
        label_.setEnabled(b);
    }
    
    /**
     * Is enabled?
     */
    public boolean isEnabled()
    {
        return slider_.isEnabled();
    }
    
    /**
     * Only disabled slider
     */
    public void setEnabledEmbedded(boolean b)
    {
        slider_.setEnabled(b);
        label_.setEnabled(b);
    }
    
    /** 
     * Invoked when slider changed
     */
    public void stateChanged(ChangeEvent e) 
    {
        prefs_.putInt(sName_, slider_.getValue());
        saveToMap();
        fireStateChanged();
    }
    
    /**
     * Save value to map
     */
    public void saveToMap()
    {
        map_.setInteger(sName_, slider_.getValue());
    }
    
    /** reset to default value
     *
     */
    public void resetToDefault()
    {
        slider_.setValue(nDefault_);
        stateChanged(null);
    }

    public void resetToPrefs()
    {
        slider_.setValue(prefs_.getInt(sName_, nDefault_));
        stateChanged(null);
    }

    /**
     * reset to value in map
     */
    public void resetToMap()
    {
        slider_.setValue(map_.getInteger(sName_, nDefault_));
        stateChanged(null);
    }
    
}
