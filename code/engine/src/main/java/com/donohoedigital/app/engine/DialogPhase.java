/*
 * DialogPhase.java
 *
 * Created on November 26, 2002, 5:47 PM
 */

package com.donohoedigital.app.engine;

import com.donohoedigital.base.ApplicationError;
import com.donohoedigital.base.ErrorCodes;
import com.donohoedigital.config.ImageConfig;
import com.donohoedigital.config.Prefs;
import com.donohoedigital.config.PropertyConfig;
import com.donohoedigital.app.config.AppButton;
import com.donohoedigital.app.config.AppPhase;
import com.donohoedigital.gui.BaseFrame;
import com.donohoedigital.gui.DDButton;
import com.donohoedigital.gui.DialogType;
import com.donohoedigital.gui.GuiUtils;
import com.donohoedigital.gui.InternalDialog;

import javax.swing.*;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * @author Doug Donohoe
 */
public abstract class DialogPhase extends BasePhase implements InternalDialog.DialogOpened, InternalDialog.DialogClosed
{
    /**
     * "dialog-modal" prop value
     */
    protected boolean bModal_ = true;

    /**
     * "dialog-no-show-option" prop value
     */
    protected boolean bNoShowOption_ = false;

    /**
     * "dialog-no-show-key" prop value
     */
    protected String sNoShowKey_;

    /**
     * "style" prop value
     */
    protected String STYLE = null;

    /**
     * "dialog-type" prop value (info/warn/error) - drives title-bar color
     */
    protected DialogType dialogType_ = DialogType.INFO;

    /**
     * Window title param
     */
    public static final String PARAM_WINDOW_TITLE_KEY = "dialog-windowtitle-prop";

    /**
     * Dialog no-show-option param
     */
    public static final String PARAM_NO_SHOW_OPTION = "dialog-no-show-option";

    /**
     * Dialog no-show-key param
     */
    public static final String PARAM_NO_SHOW_KEY = "dialog-no-show-key";

    /**
     * Dialog no-show-checkbox-name param
     */
    public static final String PARAM_NO_SHOW_NAME = "dialog-no-show-checkbox-name";

    /**
     * Option to create, but don't show immediately
     */
    public static final String PARAM_CREATE_NO_SHOW = "dialog-create-no-show";

    /**
     * modal
     */
    public static final String PARAM_MODAL = "dialog-modal";

    /**
     * dialog type (info/warn/error)
     */
    public static final String PARAM_DIALOG_TYPE = "dialog-type";

    /**
     * style
     */
    public static final String PARAM_STYLE = "style";

    // set if we don't show this because user asked not to see it again
    private boolean bDontShow_ = false;
    private boolean bCheckCreateNoShow_ = false;

    // key used for question
    private String sNoShowCheckboxName_ = null;

    /**
     * Init phase, storing engine and phase.  Called createUI()
     */
    public void init(AppEngine engine, AppContext context, AppPhase phase)
    {
        super.init(engine, context, phase);

        bModal_ = phase_.getBoolean(PARAM_MODAL, false);
        bNoShowOption_ = phase_.getBoolean(PARAM_NO_SHOW_OPTION, false);

        if (bNoShowOption_)
        {
            sNoShowCheckboxName_ = phase.getString(PARAM_NO_SHOW_NAME, "noshowdialog");
            sNoShowKey_ = phase.getString(PARAM_NO_SHOW_KEY, null);
            ApplicationError.assertNotNull(sNoShowKey_, "dialog-no-show-key not defined");

            if (isNoShowSelected())
            {
                bDontShow_ = true;
            }
        }

        // style for this stuff
        STYLE = phase_.getString(PARAM_STYLE, "default");

        // dialog type (info/warn/error) drives title-bar color
        dialogType_ = DialogType.fromString(phase_.getString(PARAM_DIALOG_TYPE, null), DialogType.INFO);

        if (!bDontShow_)
        {
            createUI();
        }
    }

    /**
     * Can be used by subclass to mimic behavior of don't show
     * functionality - basically, don't show the UI and activate
     * the button associated with the enter key (the default button).
     * Needs to be called before calling init method.
     */
    protected void setDontShow(boolean bDontShow)
    {
        bDontShow_ = bDontShow;
    }

    /**
     * Return value of dont show
     */
    protected boolean isDontShow()
    {
        return bDontShow_;
    }

    /**
     * Display the dialog without a visible UI.
     */
    protected boolean isFaceless()
    {
        return false;
    }

    /**
     * Background for dialog
     */
    protected DialogBackground back_;

    /**
     * Internal Dialog used for dialog
     */
    private InternalDialog dialog_;

    /**
     * Represents default button (window close)
     */
    protected DDButton cancelButton_;

    /**
     * Represents default button (enter key)
     */
    protected DDButton okayButton_;

    /**
     * Used to handle window closing events, etc.
     */
    protected DialogListener myListener_;


    /**
     * Trigger the default no-show button if defined, otherwise default button (as if the user pressed it)
     */
    public void triggerNoShowButton()
    {
        AppButton button = null;
        String sEnterButton = phase_.getString(ButtonBox.PARAM_DEFAULT_NO_SHOW_BUTTON,
                phase_.getString(ButtonBox.PARAM_DEFAULT_BUTTON));
        if (sEnterButton != null)
        {
            List<?> buttons = phase_.getList("buttons");
            String sButtonDef;
            for (Object o : buttons) {
                sButtonDef = (String) o;
                if (AppButton.isMatch(sButtonDef, sEnterButton)) {
                    button = new AppButton(sButtonDef);
                }
            }
            context_.buttonPressed(button, this);
        }
    }

    /**
     * Displays dialog modal/not modal (dialog-modal) prop.
     * Window close associated with default button (dialog-window-close-activates-button) prop
     */
    public void start()
    {
        // if we don't show this, then get figure out the desired button to call processButton on
        if (bDontShow_)
        {
            triggerNoShowButton();
            return;
        }

        // for cached phases, if calling start on a dialog already visible,
        // just move it to the front and selected it
        if (dialog_.isVisible())
        {
            dialog_.moveToFrontSelected();
            return;
        }

        // see if we create this but don't show - only check 1st time (creation)
        if (!bCheckCreateNoShow_)
        {
            bCheckCreateNoShow_ = true;
            if (phase_.getBoolean(PARAM_CREATE_NO_SHOW, false)) return;
        }

        // "show", faceless or otherwise
        _showDialog(isFaceless());

        // since already shown, if redisplayed, go back to where it was
        // useful for cached phases
        pos_ = InternalDialog.POSITION_NONE;
    }

    private int pos_ = InternalDialog.POSITION_CENTER;

    /**
     * show dialog logic
     */
    private void _showDialog(boolean bFaceless)
    {
        int nLocation = getDialogPosition();
        Component c = getFocusComponent();
        dialog_.showDialog(myListener_, nLocation, c, !bFaceless);
    }

    /**
     * Get location for dialog.  Can be overriden to position dialog elsewhere.
     * Returns InternalDialog.POSITION_CENTER by default.
     */
    protected int getDialogPosition()
    {
        return pos_;
    }

    /**
     * Get focus component.  Can be overriden to specify starting focus component.
     * Returns the dialog by default
     */
    protected Component getFocusComponent()
    {
        return null; //  (okayButton_ == null ? (Component) dialog_ : (Component) okayButton_);
    }

    /**
     * Default processButton calls closes dialog on any button press
     */
    public boolean processButton(AppButton button)
    {
        setResult(button);
        removeDialog();
        return true;
    }

    /**
     * Return dialog used by this phase
     */
    public InternalDialog getDialog()
    {
        return dialog_;
    }

    /**
     * Remove dialog from view
     */
    public void removeDialog()
    {
        if (dialog_ != null)
        {
            dialog_.removeDialog();
        }
    }

    /**
     * Override this to provide contents of dialog
     */
    public abstract JComponent createDialogContents();

    /**
     * create UI to show dice rolls
     */
    public void createUI()
    {
        String titleKey = phase_.getString(PARAM_WINDOW_TITLE_KEY, PARAM_WINDOW_TITLE_KEY);
        String sWindowTitle = PropertyConfig.getStringProperty(
                titleKey,
                "This Space For Rent"); // no title found so leave funny title

        // use window title key + ".minwidth" to allow for per-message change of min width
        String minWidthKey = titleKey + ".minwidth";
        int minWidth = PropertyConfig.getIntegerProperty(minWidthKey, 0);

        dialog_ = new InternalDialog(sWindowTitle, sWindowTitle, bModal_, dialogType_);
        dialog_.setDialogOpened(this);
        dialog_.setDialogClosed(this);
        dialog_.getRootPane().setName(sWindowTitle);

        if (isFaceless())
        {
            myListener_ = new DialogListener(this);
        }
        else
        {
            myListener_ = new PhaseDialogListener(this);
        }

        // set baseframe
        BaseFrame frame = context_.getFrame();
        ApplicationError.assertNotNull(frame, "BaseFrame is null");
        dialog_.setBaseFrame(frame);

        ImageIcon winicon = ImageConfig.getImageIcon(phase_.getString("dialog-windowtitle-image", "dialog-windowtitle-image"));
        dialog_.setFrameIcon(winicon);
        back_ = new DialogBackground(context_, phase_, this, bNoShowOption_, sNoShowCheckboxName_, STYLE, minWidth);
        dialog_.setContentPane(back_);

        if (bNoShowOption_)
        {
            back_.getNoShowCheckBox().addActionListener(
                    _ -> {
                        EnginePrefs.getDialogPrefs().putBoolean(sNoShowKey_, back_.getNoShowCheckBox().isSelected());
                    });
        }

        ///
        /// Handle Window close / delete events - typically cancel
        ///
        String sCloseButton = phase_.getString("dialog-window-close-activates-button", null);
        if (sCloseButton != null && !sCloseButton.isEmpty())
        {
            cancelButton_ = getMatchingButton(sCloseButton);
            if (cancelButton_ == null)
            {
                throw new ApplicationError(ErrorCodes.ERROR_NOT_FOUND,
                                           "dialog-window-close-activates-button not found: " + sCloseButton,
                                           "Make sure this button is defined");
            }
            myListener_.setButton(cancelButton_);

            GuiUtils.InvokeButton hk = new GuiUtils.InvokeButton(cancelButton_);
            // add key action so "delete" triggers close button
            GuiUtils.addKeyAction(back_, JComponent.WHEN_IN_FOCUSED_WINDOW,
                                  "handledelete", hk,
                                  KeyEvent.VK_DELETE, 0);
        }

        ///
        /// Handle ENTER key presses - typically okay
        ///
        String sEnterButton = phase_.getString(ButtonBox.PARAM_DEFAULT_BUTTON, null);
        if (sEnterButton != null && !sEnterButton.isEmpty())
        {
            okayButton_ = getMatchingButton(sEnterButton);
            if (okayButton_ == null)
            {
                throw new ApplicationError(ErrorCodes.ERROR_NOT_FOUND,
                                           ButtonBox.PARAM_DEFAULT_BUTTON + " not found: " + sEnterButton,
                                           "Make sure this button is defined");
            }
        }

        // get contents (from subclass)
        // put everything in a nice dialog background
        JComponent contents = createDialogContents();
        back_.setCenterContents(contents);

        // layout
        dialog_.pack(); // needed to get proper layout
    }

    /**
     * Get DDButton from dialog background's button box
     */
    public DDButton getMatchingButton(String sButtonName)
    {
        return back_.getButtonBox().getButton(sButtonName);
    }

    /**
     * should this dialog be displayed?
     */
    private boolean isNoShowSelected()
    {
        return isDialogHidden(sNoShowKey_);
    }

    /**
     * Return whether the "do not show" checkbox associated with
     * the given key is selected (indicating the dialog won't
     * be displayed)
     */
    public static boolean isDialogHidden(String sKey)
    {
        return EnginePrefs.getDialogPrefs().getBoolean(sKey, false);
    }

    /**
     * Set (or clear) the "do not show" preference for the given key, as if the
     * user had toggled the checkbox. Let's code suppress a no-show dialog for
     * reasons other than the checkbox (e.g. a guided flow that ran to completion).
     */
    public static void setDialogHidden(String sKey, boolean bHidden)
    {
        EnginePrefs.getDialogPrefs().putBoolean(sKey, bHidden);
    }

    /**
     * Used to activate default button
     */
    public static class DialogListener extends InternalFrameAdapter
    {
        DialogPhase phase_;
        DDButton button_;

        public DialogListener(DialogPhase phase)
        {
            phase_ = phase;
        }

        public void setButton(DDButton button)
        {
            button_ = button;
        }

        /**
         * Detect when window close icon is pressed - activate associated button
         */
        public void internalFrameClosing(InternalFrameEvent e)
        {
            if (button_ != null)
            {
                button_.doClick(120);
            }
        }
    }

    /**
     * Used to start/finish the phase
     */
    private static class PhaseDialogListener extends DialogListener
    {
        public PhaseDialogListener(DialogPhase phase)
        {
            super(phase);
        }

        /**
         * Catch actual closing so finish() can be called
         */
        public void internalFrameClosed(InternalFrameEvent e)
        {
            phase_.finish();
        }

        /**
         * When window displayed, call this
         */
        public void internalFrameOpened(InternalFrameEvent e)
        {
            phase_.opened();
        }

    }

    /**
     * called when dialog is opened
     */
    protected void opened()
    {
    }

    /**
     * Called when the dialog is added - does initialization
     * for faceless dialogs
     */
    public void dialogAdded(InternalDialog dialog)
    {
        if (isFaceless())
        {
            opened();
        }
    }

    /**
     * Called when the dialog is removed - does cleanup
     * so subclass needs to call it if overriden
     */
    public void dialogRemoved(InternalDialog dialog)
    {
        GuiUtils.requireSwingThread();

        if (isFaceless())
        {
            finish();
        }

        // cleaning dialog because not cached
        if (!phase_.isCached())
        {
            dialog_.clean();
        }
    }
}
