/*
 * DDInternalFrameUI.java
 *
 * Created on August 21, 2003, 3:29 PM
 */

package com.donohoedigital.gui;

import javax.swing.*;
import javax.swing.plaf.basic.BasicInternalFrameUI;

/**
 * Our implementation of JInternalFrame.
 */
public class DDInternalFrameUI extends BasicInternalFrameUI {

    public DDInternalFrameUI(InternalDialog b) {
        super(b);
    }

    protected JComponent createNorthPane(JInternalFrame w) {
        return new DDInternalFrameTitlePane((InternalDialog) w);
    }
}