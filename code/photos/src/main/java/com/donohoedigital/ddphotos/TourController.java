package com.donohoedigital.ddphotos;

import com.donohoedigital.app.config.AppButton;
import com.donohoedigital.app.engine.AppContext;
import com.donohoedigital.app.engine.DialogPhase;
import com.donohoedigital.app.engine.DisplayMessage;
import com.donohoedigital.app.engine.Phase;
import com.donohoedigital.base.TypedHashMap;
import com.donohoedigital.gui.OptionTabbedPane;

import javax.swing.SwingUtilities;
import java.awt.Component;
import java.util.List;
import java.util.function.Consumer;

/**
 * Guided tour for new users. Walks through the core workflow - photogen, run, build, serve,
 * publishing - as a sequence of non-modal narration dialogs (so the user can interact with the
 * main UI - mouse-over help, scrolling command output - while a step is shown). Each dialog
 * selects the relevant tab; its "Next" button runs that tab's command (where applicable) and the
 * tour advances when the command's process exits (success for one-shot commands, or a user Stop
 * for the dev servers). The flow is driven asynchronously off each dialog's result listener.
 *
 * <p>See {@link CommandRunnerPanel#startTourRun} for the run/advance integration and the
 * {@code TourWelcome}/{@code TourStep}/{@code TourEnd} phases in appdef.xml for the dialogs.
 */
public class TourController {

    /**
     * One narration step. {@code tab} is selected before the dialog is shown; if {@code runner}
     * is non-null, pressing Next runs that command and the tour advances on completion.
     */
    private record Step(String phase, String msgKey, Component tab, CommandRunnerPanel runner) {}

    /**
     * No-show preference key for the Welcome dialog (matches {@code dialog-no-show-key} on the
     * {@code TourWelcome} phase in appdef.xml). Set when the user opts out via the checkbox; we
     * also set it once the tour runs to completion so it isn't re-offered on the next launch.
     */
    private static final String NO_SHOW_KEY = "show.tour";

    private final AppContext context_;
    private final OptionTabbedPane tabs_;
    private final List<Step> steps_;

    // True while a tour is being offered or walked through; the dialogs are now non-modal,
    // so nothing else stops start()/startFromMenu() from launching a second, overlapping tour.
    private boolean running_;

    public TourController(AppContext context, OptionTabbedPane tabs, Component configTab,
                          Component deployTab, CommandRunnerPanel photogen, CommandRunnerPanel run,
                          CommandRunnerPanel build, CommandRunnerPanel serve) {
        context_ = context;
        tabs_ = tabs;
        steps_ = List.of(
                new Step("TourStep", "msg.tour.overview",      configTab, null),
                new Step("TourStep", "msg.tour.photogen",      photogen,  photogen),
                // Success ack after the one-shot photogen; tab=null keeps the Photogen tab
                // selected so its output stays visible behind this dialog before we move on.
                new Step("TourStep", "msg.tour.photogen.done", null,      null),
                new Step("TourStep", "msg.tour.run",           run,       run),
                new Step("TourStep", "msg.tour.build",         build,     build),
                new Step("TourStep", "msg.tour.build.done",    null,      null),
                new Step("TourStep", "msg.tour.serve",         serve,     serve),
                new Step("TourStep", "msg.tour.deploy",        deployTab, null),
                // Final step returns to the Config tab so the user can start customizing
                // the starter site (rename the temporary album, define a base, etc.).
                new Step("TourEnd",  "msg.tour.finish",        configTab, null)
        );
    }

    /**
     * Offer the tour via the Welcome dialog. Called on every launch until the tour is completed or
     * opted out of: once the user checks "Don't ask me to do the tour again." (or finishes the
     * tour - see {@link #runStep}), the framework's no-show option auto-triggers the default (Skip)
     * button on subsequent launches and the tour simply ends. Ignored if a tour is already running.
     */
    public void start() {
        if (running_) return;
        running_ = true;
        showDialog("TourWelcome", "msg.tour.welcome", button -> {
            if (button != null && "tourstart".equals(button.getName())) {
                runStep(0);
            } else {
                running_ = false;   // declined or closed - never entered the walkthrough
            }
        });
    }

    /** Replay the tour from the first step, bypassing the Welcome opt-out gate. */
    public void startFromMenu() {
        if (running_) return;
        running_ = true;
        runStep(0);
    }

    private void runStep(int i) {
        if (i < 0 || i >= steps_.size()) return;
        Step step = steps_.get(i);

        if (step.tab() != null) {
            tabs_.setSelectedComponent(step.tab());
        }

        // Dialogs are non-modal, so showDialog returns immediately and delivers the pressed
        // button via the callback once the user acts - everything past here is the continuation.
        showDialog(step.phase(), step.msgKey(), button -> {
            String name = button != null ? button.getName() : null;

            boolean isLast = i == steps_.size() - 1;
            if (isLast) {
                // Ran to completion ([Done] on TourEnd): suppress the Welcome offer on future
                // launches, the same as if the user had checked the opt-out box.
                DialogPhase.setDialogHidden(NO_SHOW_KEY, true);
                running_ = false;
                return;
            }
            if (!"tournext".equals(name)) { running_ = false; return; } // Skip / window close

            if (step.runner() != null) {
                // Advance only when the command finishes successfully; on failure the panel has
                // already shown the standard error dialog, so the tour just stops here.
                step.runner().startTourRun(ok -> {
                    if (ok) runStep(i + 1);
                    else running_ = false;
                });
            } else {
                runStep(i + 1);
            }
        });
    }

    /**
     * Show a (non-modal) tour dialog and invoke {@code onResult} with the pressed button once the
     * user acts. We are on the EDT, so the click cannot be dispatched until this returns, which
     * makes registering the listener after start() race-free. Modal/no-show dialogs that resolve
     * synchronously simply fire the listener immediately on registration.
     */
    private void showDialog(String phaseName, String msgKey, Consumer<AppButton> onResult) {
        TypedHashMap params = new TypedHashMap();
        params.setString(DisplayMessage.PARAM_MESSAGE_KEY, msgKey);
        Phase phase = context_.processPhaseNow(phaseName, params);
        // Defer the continuation a tick so the current dialog fully closes (processButton calls
        // setResult then removeDialog) before the next step opens its dialog - otherwise the two
        // would briefly overlap and the next phase would start reentrantly inside the button press.
        phase.setResultListener((p, result) ->
                SwingUtilities.invokeLater(() -> onResult.accept((AppButton) result)));
    }
}
