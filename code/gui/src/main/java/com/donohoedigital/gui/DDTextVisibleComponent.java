/*
 * DDTextVisibleComponent.java
 *
 * Created on November 17, 2002, 3:08 PM
 */

package com.donohoedigital.gui;

import java.awt.*;

/**
 *
 * @author  Doug Donohoe
 */
public interface DDTextVisibleComponent extends DDComponent {

    void setFont(Font f);
    Font getFont();
}
