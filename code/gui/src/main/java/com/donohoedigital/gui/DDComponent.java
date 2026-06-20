/*
 * DDComponent.java
 *
 * Created on November 17, 2002, 2:52 PM
 */

package com.donohoedigital.gui;

import java.awt.*;
import java.awt.event.*;

/**
 *
 * @author  Doug Donohoe
 */
public interface DDComponent {
    
    String getType();
    
    // naming of component (provided by Component)
    void setName(String s);
    String getName();
    
    void setForeground(Color c);
    Color getForeground();
    
    void setBackground(Color c);
    Color getBackground();
    
    void addMouseListener(MouseListener m);
    void removeMouseListener(MouseListener m);

    void setToolTipText(String s);
}
