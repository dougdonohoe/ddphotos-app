/*
 * DDHasLabelComponent.java
 *
 * Created on November 17, 2002, 3:08 PM
 */

package com.donohoedigital.gui;



/**
 *
 * @author  Doug Donohoe
 */
public interface DDHasLabelComponent extends DDTextVisibleComponent {
    
    void setText(String s);
    String getText();
}
