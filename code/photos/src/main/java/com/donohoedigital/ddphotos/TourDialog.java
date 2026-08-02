package com.donohoedigital.ddphotos;

import com.donohoedigital.app.config.AppButton;
import com.donohoedigital.app.engine.DisplayMessage;
import com.donohoedigital.app.engine.EngineUtils;
import com.donohoedigital.config.PropertyConfig;

/**
 * The narration dialog used by the tour steps that can be abandoned - {@code TourStep} and
 * {@code TourWait} in appdef.xml.  Identical to {@link DisplayMessage} except that <b>Stop Tour</b>
 * asks for confirmation first.
 *
 * <p>The confirmation is raised here, from the button press, rather than in {@link TourController}
 * once the result comes back: vetoing the press leaves this dialog open underneath, so the user
 * sees the step they are about to abandon and simply carries on with it when they answer No.
 *
 * <p>Also covers the window close, which the phase routes through this same button
 * ({@code dialog-window-close-activates-button}); the dialog is DO_NOTHING_ON_CLOSE while visible,
 * so a veto keeps it up there too.
 */
public class TourDialog extends DisplayMessage
{
    /** The Stop Tour button, on both phases that use this class. */
    static final String BUTTON_CANCEL = "tourcancel";

    @Override
    public boolean processButton(AppButton button)
    {
        if (button != null && BUTTON_CANCEL.equals(button.getName()) && !confirmStop()) {
            return false;   // vetoed - the step stays up and the tour carries on
        }
        return super.processButton(button);
    }

    /**
     * Asks whether the user really means to abandon the tour, reminding them it can be replayed.
     * Modal, so it sits over this (non-modal) step dialog until answered, and a warning (orange
     * title bar) so it reads as something to stop and answer rather than click through.
     */
    private boolean confirmStop()
    {
        return EngineUtils.displayWarningConfirmationDialog(context_,
                PropertyConfig.getMessage("msg.tour.confirm.stop"),
                "msg.windowtitle.tourStop", null);
    }
}
