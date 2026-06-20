package com.donohoedigital.gui;

import javax.swing.text.*;

/**
 * Created by IntelliJ IDEA.
 * User: donohoe
 * Date: May 22, 2006
 * Time: 10:14:30 AM
 * To change this template use File | Settings | File Templates.
 */
public interface DDWindow
{
    // swing methods
    String getName();
    void setName(String sName);
    String getTitle();
    void setTitle(String sTitle);
    void toFront();
    void repaint();

    // help text methods
    void setHelpTextWidget(JTextComponent t);
    JTextComponent getHelpTextWidget();
    void setHelpMessage(String sMessage);
    void setMessage(String sMessage);
    void clearMessage();
    void showHelp(DDComponent source);
    void ignoreNextHelp();
}
