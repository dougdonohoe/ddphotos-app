/*
 * DisplayMessage.java
 *
 * Created on December 27, 2002, 4:05 PM
 */

package com.donohoedigital.app.engine;

import com.donohoedigital.config.*;
import com.donohoedigital.gui.*;

import javax.swing.*;
import java.awt.*;

import static com.donohoedigital.app.engine.EngineUtils.STANDARD_BORDER_GAP;

/**
 * Class to show a message.  It first calls getMessage() - this
 * is used to enable subclasses.  If that returns null, it looks
 * for a "msgkey" param, which it looks up in the PropertyConfig.
 * If there is no message key, it looks for a "msg" param and
 * displays that.
 *
 * @author Doug Donohoe
 */
public class DisplayMessage extends DialogPhase
{
    public static final String PARAM_MESSAGE = "msg";
    public static final String PARAM_MESSAGE_KEY = "msgkey";

    /**
     * create UI to show message
     */
    @Override
    public JComponent createDialogContents()
    {
        DDPanel info = new DDPanel();

        String sMsgKey = phase_.getString(PARAM_MESSAGE_KEY, null);
        String sMsg = getMessage();
        if (sMsg == null)
        {
            if (sMsgKey != null)
            {
                sMsg = PropertyConfig.getMessage(sMsgKey);
            }
            else {
                sMsg = phase_.getString(PARAM_MESSAGE, "No message (msg) or message key (msgkey) found");
            }
        }

        // message area
        DDHtmlArea label = new DDHtmlArea(GuiManager.DEFAULT, STYLE);
        label.addHyperlinkListener(GuiUtils.HYPERLINK_HANDLER);
        label.setText(sMsg);
        label.setFocusable(true);
        GuiUtils.setDoNothingCaret(label);
        label.setBorder(BorderFactory.createEmptyBorder(STANDARD_BORDER_GAP,STANDARD_BORDER_GAP,15,STANDARD_BORDER_GAP));
        info.add(label, BorderLayout.CENTER);

        return info;
    }

    public String getMessage()
    {
        return null;
    }

    /**
     * Focus the default button (OK / Yes / Next / ...) when the dialog opens, so the
     * keyboard works immediately: Enter via the button's own WHEN_FOCUSED binding and
     * Space via the focused button. Otherwise, these button-only dialogs open with focus
     * left on the main window and Enter just beeps (nothing in the dialog consumes it).
     */
    @Override
    protected Component getFocusComponent()
    {
        return okayButton_ != null ? okayButton_ : super.getFocusComponent();
    }
}
