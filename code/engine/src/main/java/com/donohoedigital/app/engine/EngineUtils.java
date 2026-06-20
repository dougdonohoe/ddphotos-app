/*
 * EngineUtils.java
 *
 * Created on December 28, 2002, 2:22 PM
 */

package com.donohoedigital.app.engine;

import com.donohoedigital.base.TypedHashMap;
import com.donohoedigital.app.config.*;

import javax.swing.*;
import java.awt.*;

/**
 *
 * @author  Doug Donohoe
 */
public class EngineUtils 
{  
    //static Logger logger = LogManager.getLogger(EngineUtils.class);

    public static final int STANDARD_BORDER_GAP = 10;

    /**
     * Show message in a confirmation dialog. Return true if 'yes'
     * pressed, false otherwise.
     */
    public static boolean displayConfirmationDialog(AppContext context,
                                                    String sMsg)
    {
        return displayConfirmationDialog(context, sMsg, null);
    }
    
    /**
     * Same as above, except display "don't show" option if sNoShowKey is non-null
     */
    public static boolean displayConfirmationDialog(AppContext context,
                                                    String sMsg, String sNoShowKey)
    {
        return displayConfirmationDialog(context, sMsg, null, sNoShowKey);
    }
    
    /**
     * Same as above, except display "don't show" option if sNoShowKey is non-null.
     * Title is set to title 
     */
    public static boolean displayConfirmationDialog(AppContext context,
                                                    String sMsg, String sTitleKey, String sNoShowKey)
    {
        return displayConfirmationDialog(context, sMsg, sTitleKey, sNoShowKey, null);
    }
        
    /**
     * Same as above, except display "don't show" option if sNoShowKey is non-null.
     * Title is set to title 
     */
    public static boolean displayConfirmationDialog(AppContext context,
                                                    String sMsg,
                                                    String sTitleKey,
                                                    String sNoShowKey,
                                                    String sNoShowCheckBoxName)
    {
        AppButton buttonpressed = displayConfirmationDialogCustom(context, "DisplayConfirmation",
                        sMsg, sTitleKey, sNoShowKey, sNoShowCheckBoxName);

        return buttonpressed != null && buttonpressed.getName().equals("yes");
    }

    
    /**
     * confirmation dialog where button pressed is returned to allow for custom
     * buttons.  Should check for null
     */
    private static AppButton displayConfirmationDialogCustom(AppContext context,
                                                             String sPhase,
                                                             String sMsg,
                                                             String sTitleKey,
                                                             String sNoShowKey,
                                                             String sNoShowCheckBoxName) // only used with cancelable timeout
    {

        TypedHashMap params = new TypedHashMap();
        if (sMsg != null) params.setString(DisplayMessage.PARAM_MESSAGE, sMsg);
        setParams(sTitleKey, sNoShowKey, sNoShowCheckBoxName, params);
        Phase confirm = context.processPhaseNow(sPhase, params);

        return (AppButton) confirm.getResult();
    }

    /**
     * Show message in an information dialog
     */
    public static void displayInformationDialog(AppContext context,
                                                String sMsg)
    {
        displayInformationDialog(context, sMsg, null);
    }

    /**
     * Show message in an information dialog
     */
    public static void displayInformationDialog(AppContext context,
                                                String sMsg, boolean bModal)
    {
        displayInformationDialog(context, sMsg, null, null, null, bModal);
    }

    /**
     * Same as above, except display "don't show" option if sNoShowKey is non-null
     */
    public static void displayInformationDialog(AppContext context,
                                                String sMsg, String sNoShowKey)
    {
        displayInformationDialog(context, sMsg, null, sNoShowKey);
    }
    
    /**
     * Show message in an information dialog for current player.  If no-show-key
     * is not null, then a "don't show this dialog" option is shown
     */
    public static void displayInformationDialog(AppContext context,
                                                String sMsg,
                                                String sTitleKey,
                                                String sNoShowKey)
    {
        displayInformationDialog(context, sMsg, sTitleKey, sNoShowKey, null);
    }
    
    /**
     * Show message in an information dialog for current player.  If no-show-key
     * is not null, then a "don't show this dialog" option is shown
     */
    public static void displayInformationDialog(AppContext context,
                                                String sMsg,
                                                String sTitleKey,
                                                String sNoShowKey,
                                                String sNoShowCheckBoxName)
    {
        displayInformationDialog(context, sMsg, sTitleKey, sNoShowKey, sNoShowCheckBoxName, true);
    }
    
        /**
     * Show message in an information dialog for current player.  If no-show-key
     * is not null, then a "don't show this dialog" option is shown
     */
    public static void displayInformationDialog(AppContext context,
                                                String sMsg,
                                                String sTitleKey,
                                                String sNoShowKey,
                                                String sNoShowCheckBoxName,
                                                boolean bModal)
    {
        displayMessageDialog(context, "DisplayInfoMessage", sMsg, sTitleKey, sNoShowKey, sNoShowCheckBoxName, bModal);
    }

    /**
     * Show message in a warning dialog (orange title bar)
     */
    public static void displayWarningDialog(AppContext context, String sMsg)
    {
        displayMessageDialog(context, "DisplayWarning", sMsg, null, null, null, true);
    }

    /**
     * Show message in a warning dialog (orange title bar) with the given title
     */
    public static void displayWarningDialog(AppContext context,
                                            String sMsg,
                                            String sTitleKey,
                                            String sNoShowKey)
    {
        displayMessageDialog(context, "DisplayWarning", sMsg, sTitleKey, sNoShowKey, null, true);
    }

    /**
     * Show message in a warning dialog (orange title bar) with the given title and no-show option
     */
    public static void displayWarningDialog(AppContext context,
                                            String sMsg,
                                            String sTitleKey,
                                            String sNoShowKey,
                                            String sNoShowCheckBoxName)
    {
        displayMessageDialog(context, "DisplayWarning", sMsg, sTitleKey, sNoShowKey, sNoShowCheckBoxName, true);
    }

    /**
     * Show message in an error dialog (red title bar)
     */
    public static void displayErrorDialog(AppContext context, String sMsg)
    {
        displayMessageDialog(context, "DisplayError", sMsg, null, null, null, true);
    }

    /**
     * Show message in an error dialog (red title bar) with the given title
     */
    public static void displayErrorDialog(AppContext context,
                                          String sMsg,
                                          String sTitleKey,
                                          String sNoShowKey)
    {
        displayMessageDialog(context, "DisplayError", sMsg, sTitleKey, sNoShowKey, null, true);
    }

    /**
     * Show message in an error dialog (red title bar) with the given title and no-show option
     */
    public static void displayErrorDialog(AppContext context,
                                          String sMsg,
                                          String sTitleKey,
                                          String sNoShowKey,
                                          String sNoShowCheckBoxName)
    {
        displayMessageDialog(context, "DisplayError", sMsg, sTitleKey, sNoShowKey, sNoShowCheckBoxName, true);
    }

    /**
     * Shared implementation - show sMsg using the given message-display phase
     */
    private static void displayMessageDialog(AppContext context,
                                             String sPhase,
                                             String sMsg,
                                             String sTitleKey,
                                             String sNoShowKey,
                                             String sNoShowCheckBoxName,
                                             boolean bModal)
    {
        TypedHashMap params = new TypedHashMap();
        params.setString(DisplayMessage.PARAM_MESSAGE, sMsg);
        setParams(sTitleKey, sNoShowKey, sNoShowCheckBoxName, params);
        params.setBoolean(DialogPhase.PARAM_MODAL, bModal ? Boolean.TRUE : Boolean.FALSE);
        context.processPhaseNow(sPhase, params);
    }

    private static void setParams(String sTitleKey, String sNoShowKey, String sNoShowCheckBoxName, TypedHashMap params) {
        if (sTitleKey != null) params.setString(DisplayMessage.PARAM_WINDOW_TITLE_KEY, sTitleKey);
        if (sNoShowKey != null)
        {
            params.setBoolean(DialogPhase.PARAM_NO_SHOW_OPTION, Boolean.TRUE);
            params.setString(DialogPhase.PARAM_NO_SHOW_KEY, sNoShowKey);
        }
        if (sNoShowCheckBoxName != null)
        {
            params.setString(DialogPhase.PARAM_NO_SHOW_NAME, sNoShowCheckBoxName);
        }
    }
}
