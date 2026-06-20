/*
 * DDText.java
 *
 * Created on May 20, 2004, 2:47 PM
 */

package com.donohoedigital.gui;

import javax.swing.border.*;
import java.awt.*;

/**
 *
 * @author  donohoe
 */
public interface DDText {
    
    void setText(String s);
    String getText();
    void setForeground(Color c);
    void setBackground(Color c);
    Color getForeground();
    Color getBackground();
    void setBorder(Border border);
    Border getBorder();
    void setOpaque(boolean b);
    boolean isOpaque();
    
}
