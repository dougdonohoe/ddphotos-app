/*
 * OptionBoolean.java
 *
 * Created on April 16, 2003, 2:01 PM
 */

package com.donohoedigital.gui;

import com.donohoedigital.base.*;
import com.donohoedigital.config.*;

import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;

/**
 *
 * @author  donohoe
 */
public class OptionBoolean extends DDOption implements ActionListener, ChangeListener
{
    //static Logger logger = LogManager.getLogger(OptionBoolean.class);
    
    private final DDCheckBox box_;
    private final boolean bDefault_;
    private final DDOption extra_;

    /** 
     * Creates a new instance of OptionBoolean 
     */
    public OptionBoolean(String sPrefNode, String sName, String sStyle, TypedHashMap map)
    {
        this(sPrefNode, sName, sStyle, map, null);
    }

    /**
     * Creates a new instance of OptionBoolean. Extra option is added to EAST
     * and disabled when this checkbox is not selected
     */
    public OptionBoolean(String sPrefNode, String sName, String sStyle,
                    TypedHashMap map, DDOption extra)
    {
        super(sPrefNode, sName, sStyle, map);
        bDefault_ = PropertyConfig.getRequiredBooleanProperty(getDefaultKey());
        extra_ = extra;

        DDPanel base = new DDPanel();

        box_ = new DDCheckBox(GuiManager.DEFAULT, STYLE);
        box_.setText(getLabel());
        box_.setSelected(prefs_.getBoolean(sName_, bDefault_));
        saveToMap();
        box_.addActionListener(this);
        box_.addMouseListener(this);
        box_.addChangeListener(this);
        
        base.add(box_, BorderLayout.CENTER);
        if (extra_ != null) 
        {
            base.add(extra_, BorderLayout.EAST);
        
            // call to force extra component to be correct state
            stateChanged(null);
        }
        
        add(base, BorderLayout.WEST);
    }
    
    public DDCheckBox getCheckBox()
    {
        return box_;
    }

    /**
     * Set button enabled
     */
    public void setEnabled(boolean b)
    {
        box_.setEnabled(b);
        if (extra_ != null) extra_.setEnabled(b);
    }

    public void setDisplayOnly(boolean b)
    {
        box_.setDisplayOnly(b);
    }
    
    /**
     * Is enabled?
     */
    public boolean isEnabled()
    {
        return box_.isEnabled();
    }
    
    /** Invoked when an action occurs.
     *
     */
    public void actionPerformed(ActionEvent e)
    {
        prefs_.putBoolean(sName_, box_.isSelected());
        saveToMap();
        fireStateChanged();
    }
    
    /**
     * Save value to map
     */
    public void saveToMap()
    {
        map_.setBoolean(sName_, box_.isSelected() ? Boolean.TRUE : Boolean.FALSE);
    }
    
    /** reset to default value
     *
     */
    public void resetToDefault()
    {
        box_.setSelected(bDefault_);
        actionPerformed(null);
    }

    public void resetToPrefs()
    {
        box_.setSelected(prefs_.getBoolean(sName_, bDefault_));
        actionPerformed(null);
    }

    /**
     * Used to enabled when button selected
     */
    public void stateChanged(ChangeEvent e)
    {
        if (extra_ != null) extra_.setEnabledEmbedded(box_.isSelected());
    }
    
    /** 
     * reset to value in map
     */
    public void resetToMap()
    {
        box_.setSelected(map_.getBoolean(sName_, bDefault_));
        actionPerformed(null);
    }
    
}
