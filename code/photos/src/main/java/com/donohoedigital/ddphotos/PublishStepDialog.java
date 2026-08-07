package com.donohoedigital.ddphotos;

import com.donohoedigital.app.engine.DisplayMessage;

/**
 * The dialog {@link PublishController} keeps up for the duration of one publish step - see the
 * {@code PublishStep} phase in appdef.xml.  Identical to {@link DisplayMessage} except that it
 * hands the controller a handle to itself, which is the whole reason this class exists.
 *
 * <p>The dialog is modal, so {@code processPhaseNow} blocks inside its event loop and does not
 * return the phase until the dialog is gone - by which time the controller no longer needs it.
 * It has to be able to close this dialog itself when the command exits, and {@link #opened()}
 * is the one place that runs early enough: it fires from {@code internalFrameOpened}, inside
 * {@code DialogPhase._showDialog} and therefore before {@code beginModal()}.  Nothing the
 * controller watches for can be dispatched before then, since it all arrives on the EDT and
 * the EDT is inside this call.
 */
public class PublishStepDialog extends DisplayMessage
{
    /** The {@link PublishController} to report to. */
    public static final String PARAM_CONTROLLER = "publish-controller";

    @Override
    protected void opened()
    {
        super.opened();
        PublishController controller = (PublishController) phase_.getObject(PARAM_CONTROLLER);
        if (controller != null) controller.stepDialogOpened(this);
    }
}
