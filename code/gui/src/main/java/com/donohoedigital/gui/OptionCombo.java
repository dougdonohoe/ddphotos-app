/*
 * OptionCombo.java
 *
 * Created on April 13, 2005, 9:09 PM
 */

package com.donohoedigital.gui;

import com.donohoedigital.base.*;
import com.donohoedigital.config.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
/**
 *
 * @author  donohoe
 */
public class OptionCombo<E> extends DDOption implements ItemListener
{
    //static Logger logger = LogManager.getLogger(OptionCombo.class);
    private DDLabel label_;
    private final DDComboBox<E> combo_;
    private String sDefault_;
    private final boolean bIndexPrefs_;

    /**
     * Shared init: wires up the pre-built combo, sizes it, and adds it to the layout.
     * Callers are responsible for setting sDefault_ and calling resetToPrefs/saveToMap.
     */
    protected OptionCombo(DDComboBox<E> combo, boolean bIndexPrefs,
                          String sPrefNode, String sName, String sStyle,
                          TypedHashMap map, int nWidth, boolean bLabel)
    {
        super(sPrefNode, sName, sStyle, map);
        bIndexPrefs_ = bIndexPrefs;
        combo_ = combo;

        setBorderLayoutGap(0, 8);

        Dimension pref = combo_.getPreferredSize();
        combo_.setPreferredSize(new Dimension(nWidth, pref.height));
        combo_.addItemListener(this);
        combo_.addMouseListener(this);

        if (bLabel)
        {
            label_ = new DDLabel(GuiManager.DEFAULT, STYLE);
            label_.setText(getLabel());
            label_.addMouseListener(this);
            add(GuiUtils.CENTER(combo_), BorderLayout.EAST);
            add(label_, BorderLayout.CENTER);
        }
        else
        {
            add(combo_, BorderLayout.CENTER);
            setPreferredSize(combo_.getPreferredSize());
        }
    }

    /**
     * Creates a new instance of OptionCombo backed by a DataElement (index-based prefs).
     */
    public OptionCombo(DataElement<E> element, String sPrefNode, String sName, String sStyle,
                       TypedHashMap map, int nWidth, boolean bLabel)
    {
        this(new DDComboBox<>(element, sStyle), true,
             sPrefNode, sName, sStyle, map, nWidth, bLabel);
        sDefault_ = "";
        resetToPrefs();
        saveToMap();
    }
    
    /**
     * Get the combox box
     */
    public DDComboBox<E> getComboBox()
    {
        return combo_;
    }

    public Object getSelectedItem() { return combo_.getSelectedItem(); }

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
        return combo_.isValidData();
    }

    public void setRequired(boolean bRequired)
    {
        combo_.setRequired(bRequired);
    }
    
    /**
     * Set display only
     */
    public void setDisplayOnly(boolean b)
    {
        combo_.setDisplayOnly(b);
    }
    
    /**
     * set disabled
     */
    public void setEnabled(boolean b)
    {
        combo_.setEnabled(b);
        if (label_ != null) label_.setEnabled(b);
    }
    
    /**
     * Is enabled?
     */
    public boolean isEnabled()
    {
        return combo_.isEnabled();
    }
    
    /**
     * Only disabled spinner
     */
    public void setEnabledEmbedded(boolean b)
    {
        combo_.setEnabled(b);
    }

    /**
     * Selected item changed
     */
    public void itemStateChanged(ItemEvent e)
    {
        fireStateChanged(); // for validation listeners
        if (!combo_.isValid()) return;
        if (bIndexPrefs_) {
            int idx = combo_.getSelectedIndex();
            if (idx >= 0) prefs_.putInt(sName_ + ".idx", idx);
        } else {
            prefs_.put(sName_, (String) combo_.getSelectedItem());
        }
        saveToMap();
    }

    /**
     * Save value to map
     */
    public void saveToMap()
    {
        if (!bIndexPrefs_) {
            map_.setString(sName_, (String) combo_.getSelectedItem());
        }
    }

    /** reset to default value */
    public void resetToDefault()
    {
        if (bIndexPrefs_) {
            if (combo_.getItemCount() > 0) combo_.setSelectedIndex(0);
        } else {
            combo_.setSelectedItem(sDefault_);
        }
    }

    public void resetToPrefs()
    {
        if (bIndexPrefs_) {
            int count = combo_.getItemCount();
            if (count > 0) {
                int idx = prefs_.getInt(sName_ + ".idx", 0);
                combo_.setSelectedIndex(idx >= 0 && idx < count ? idx : 0);
            }
        } else {
            combo_.setSelectedItem(prefs_.get(sName_, sDefault_));
        }
    }

    /**
     * reset to value in map
     */
    public void resetToMap()
    {
        if (bIndexPrefs_) return;
        String value = map_.getString(sName_, sDefault_);
        String current = (String) combo_.getSelectedItem();
        if (!value.equals(current))
        {
            combo_.setSelectedItem(value);
        }
    }
}
