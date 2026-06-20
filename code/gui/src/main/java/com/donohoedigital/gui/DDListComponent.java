/*
 * DDHasLabelComponent.java
 *
 * Created on December 30, 2002, 5:08 PM
 */

package com.donohoedigital.gui;

import java.awt.*;

/**
 *
 * @author  Doug Donohoe
 */
public interface DDListComponent {
    
    Color getSelectionBackground();
    void setSelectionBackground(Color c);
    
    Color getSelectionForeground();
    void setSelectionForeground(Color c);
}
