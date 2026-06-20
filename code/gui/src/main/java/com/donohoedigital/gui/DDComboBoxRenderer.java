/*
 * DDComboBoxRenderer.java
 *
 * Created on November 20, 2002, 8:35 PM
 */

package com.donohoedigital.gui;

import javax.swing.*;
import javax.swing.plaf.basic.*;
import java.awt.*;

/**
 *
 * @author  Doug Donohoe
 */
public class DDComboBoxRenderer extends BasicComboBoxRenderer
{
    DDComboBox<?> list_;

    /**
     * Creates a new instance of DDComboBoxRenderer
     */
    public DDComboBoxRenderer(DDComboBox<?> list) {
        super();
        list_ = list;
    }

    /**
     * Draws display value of chosen item
     */
    public Component getListCellRendererComponent(
                                                 JList<?> list,
                                                 Object value,
                                                 int index, 
                                                 boolean isSelected, 
                                                 boolean cellHasFocus)
    {
        if (isSelected) {
            setBackground(list_.getSelectionBackground());
            setForeground(list_.getSelectionForeground());
        }
        else 
        {
            setBackground(list_.getBackground());
            setForeground(list_.getForeground());
        }
        
        setFont(list_.getFont());
        setText(list_.getDisplayValue(value));
        return this;
    }
}
