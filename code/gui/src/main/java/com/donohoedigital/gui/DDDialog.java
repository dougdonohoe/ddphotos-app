package com.donohoedigital.gui;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;

/**
 * A JDialog that implements DDWindow so that DD widgets inside it can
 * wire their mouse-over help text to a help area in the dialog.
 * GuiUtils.getHelpManager() walks the Swing parent chain; since JDialog
 * is a Container it appears in that chain and will be found.
 */
public class DDDialog extends JDialog implements DDWindow
{
    // help-text display logic shared with BaseFrame and InternalDialog (gets the
    // global fallback widget when this dialog has none of its own)
    private final HelpTextManager helpText_ = new HelpTextManager();

    public DDDialog(Frame owner, String title, boolean modal)
    {
        super(owner, title, modal);
    }

    @Override
    public void setHelpTextWidget(JTextComponent t)
    {
        helpText_.setHelpTextWidget(t);
    }

    @Override
    public JTextComponent getHelpTextWidget()
    {
        return helpText_.getHelpTextWidget();
    }

    @Override
    public void setHelpMessage(String sMessage)
    {
        helpText_.setHelpMessage(sMessage);
    }

    @Override
    public void setMessage(String sMessage)
    {
        helpText_.setMessage(sMessage);
    }

    @Override
    public void clearMessage()
    {
        helpText_.clearMessage();
    }

    @Override
    public void showHelp(DDComponent source)
    {
        helpText_.showHelp(source);
    }

    @Override
    public void ignoreNextHelp()
    {
        helpText_.ignoreNextHelp();
    }
}
