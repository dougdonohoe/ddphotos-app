/*
 * OptionTextArea.java
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
public class OptionTextArea extends DDOption implements PropertyChangeListener
{
    //static Logger logger = LogManager.getLogger(OptionTextArea.class);
    
    private final DDLabel label_;
    private final DDTextArea text_;
    private final String sDefault_;

    /** 
     * Creates a new instance of OptionTextArea 
     */
    public OptionTextArea(String sPrefNode, String sName, String sStyle,
                          String sBevelStyle, TypedHashMap map, int nLengthLimit, String sRegExp, int nRows, int nWidth)
    {
        super(sPrefNode, sName, sStyle, map);
        sDefault_ = PropertyConfig.getRequiredStringProperty(getDefaultKey());
        
        // base
        setBorderLayoutGap(0, 8);
        
        // text
        text_ = new DDTextArea(GuiManager.DEFAULT, STYLE);
        text_.setRows(nRows);
        text_.setTextLengthLimit(nLengthLimit);
        if (sRegExp != null) text_.setRegExp(sRegExp);
        Dimension pref = text_.getPreferredSize(); // get size before set text
        
        // set text, save to map
        resetToPrefs();
        saveToMap();        
        
        // create scroll and add listeners
        JScrollPane scroll = new DDScrollPane(text_, STYLE, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setPreferredSize(new Dimension(nWidth,pref.height+4));
        scroll.setOpaque(false);
        // FlatLaf draws a text area's border (and focus ring) from its scroll
        // pane, not the area; DDScrollPane clears that border, so restore it here
        // for parity with DDTextField.  Scoped to this option (shared DDScrollPane
        // left unchanged).
        scroll.setBorder(UIManager.getBorder("ScrollPane.border"));
        text_.setScrollPane(scroll);
        text_.setTabChangesFocus(true);
        if (sBevelStyle != null) text_.setBorder(BorderFactory.createEmptyBorder(2,2,2,2));
        text_.addPropertyChangeListener("value", this);
        text_.addMouseListener(this);
        text_.setWrapStyleWord(true);
        text_.setLineWrap(true);
        text_.setCaretPosition(0);

        // label
        label_ = new DDLabel(GuiManager.DEFAULT, STYLE);
        label_.setVerticalAlignment(SwingConstants.TOP);
        label_.setText(getLabel());
        label_.addMouseListener(this);
        
        // put it all together
        add(scroll, BorderLayout.CENTER);
        add(label_, BorderLayout.WEST);
    }
    
    /**
     * Get the spinner
     */
    public DDTextArea getTextArea()
    {
        return text_;
    }

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
        label_.setEnabled(b);
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
        text_.setText(map_.getString(sName_, sDefault_));
    }
}
