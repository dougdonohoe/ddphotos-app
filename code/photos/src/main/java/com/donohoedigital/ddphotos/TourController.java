package com.donohoedigital.ddphotos;

import com.donohoedigital.app.config.AppButton;
import com.donohoedigital.app.engine.AppContext;
import com.donohoedigital.app.engine.DialogPhase;
import com.donohoedigital.app.engine.DisplayMessage;
import com.donohoedigital.app.engine.Phase;
import com.donohoedigital.base.TypedHashMap;
import com.donohoedigital.config.PropertyConfig;
import com.donohoedigital.gui.OptionTabbedPane;

import javax.swing.SwingUtilities;
import java.awt.Component;
import java.util.List;
import java.util.function.Consumer;

/**
 * Guided tour for new users. Walks through the core workflow - photogen, run, build, serve,
 * publishing - as a sequence of non-modal narration dialogs (so the user can interact with the
 * main UI - mouse-over help, scrolling command output - while a step is shown). Each dialog
 * selects the relevant tab.
 *
 * <p>The tour never runs a command itself: on a command step it asks the user to click that tab's
 * Run button (which pulses while we wait) and watches the panel via
 * {@link CommandRunnerPanel.RunWatcher}. Once the command starts, the instruction is replaced by a
 * dialog that stays up for the whole run - for the dev servers that is where the user is told to
 * click the console link and then Stop when they are done. The tour advances when the process
 * exits; if it failed, the "click Run" instruction comes back so the user can retry.
 *
 * <p>See the {@code TourWelcome}/{@code TourStep}/{@code TourWait}/{@code TourEnd} phases in
 * appdef.xml for the dialogs - {@code TourWait} is the button-less one used while we watch a run.
 */
public class TourController {

    /**
     * One narration step. {@code tab} is selected before the dialog is shown. If {@code runner} is
     * non-null this is a command step: {@code msgKey} asks the user to click Run and
     * {@code runningMsgKey} is shown for the duration of the run.
     */
    private record Step(String phase, String msgKey, Component tab, CommandRunnerPanel runner,
                        String runningMsgKey, boolean stopToContinue) {

        /** Narration step: Next advances, no command involved. */
        static Step narrate(String phase, String msgKey, Component tab) {
            return new Step(phase, msgKey, tab, null, null, false);
        }

        /** One-shot command ({@code photogen}, {@code build}): it ends on its own. */
        static Step command(String msgKey, CommandRunnerPanel runner, String runningMsgKey) {
            return new Step("TourWait", msgKey, runner, runner, runningMsgKey, false);
        }

        /**
         * Dev server ({@code run}, {@code serve}): it runs until the user stops it, so the Stop
         * button pulses while we wait for them.
         */
        static Step server(String msgKey, CommandRunnerPanel runner, String runningMsgKey) {
            return new Step("TourWait", msgKey, runner, runner, runningMsgKey, true);
        }
    }

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
                Step.narrate("TourStep", "msg.tour.overview", configTab),
                Step.command("msg.tour.photogen", photogen, "msg.tour.photogen.running"),
                // Success ack after the one-shot photogen; tab=null keeps the Photogen tab
                // selected so its output stays visible behind this dialog before we move on.
                Step.narrate("TourStep", "msg.tour.photogen.done", null),
                Step.server("msg.tour.run", run, "msg.tour.run.running"),
                Step.command("msg.tour.build", build, "msg.tour.build.running"),
                Step.narrate("TourStep", "msg.tour.build.done", null),
                Step.server("msg.tour.serve", serve, "msg.tour.serve.running"),
                Step.narrate("TourStep", "msg.tour.deploy", deployTab),
                // Final step returns to the Config tab so the user can start customizing
                // the starter site (rename the temporary album, define a base, etc.).
                Step.narrate("TourEnd", "msg.tour.finish", configTab)
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

        if (step.runner() != null) {
            awaitRun(i, step);
        } else {
            awaitNext(i, step);
        }
    }

    /** A narration step: the tour moves on when the user presses Next. */
    private void awaitNext(int i, Step step) {
        // Dialogs are non-modal, so showDialog returns immediately and delivers the pressed
        // button via the callback once the user acts - everything past here is the continuation.
        showDialog(step.phase(), step.msgKey(), button -> {
            String name = button != null ? button.getName() : null;

            if (i == steps_.size() - 1) {
                // Ran to completion ([Done] on TourEnd): suppress the Welcome offer on future
                // launches, the same as if the user had checked the opt-out box.
                DialogPhase.setDialogHidden(NO_SHOW_KEY, true);
                running_ = false;
                return;
            }
            if (!"tournext".equals(name)) { running_ = false; return; } // Skip / window close

            runStep(i + 1);
        });
    }

    /**
     * A command step: ask the user to click Run (pulsing the button so they can find it) and watch
     * the panel. The dialog has no Next - we swap it for the "while it runs" one ourselves as soon
     * as the command starts.
     */
    private void awaitRun(int i, Step step) {
        CommandRunnerPanel runner = step.runner();
        // Tab UI is built lazily, and we are about to point at (and pulse) its Run button. Harmless
        // if the tab selection above already triggered it - initUI() only builds once.
        runner.initUI();

        // The user may have started this command before the tour reached the step (the dialogs are
        // non-modal, so the main window stayed live) - pick up where they are rather than asking
        // them to press a Run button that is disabled because it is already running.
        if (runner.isRunning()) {
            showRunning(i, step);
            return;
        }

        runner.pulseRunButton();
        Phase dialog = showDialog(step.phase(), step.msgKey(), _ -> {
            // Only Cancel (or the window close) can resolve this dialog - a run resolves it via
            // the watcher below, which dismisses it without a result.
            endStep(runner);
        });

        runner.setRunWatcher(new CommandRunnerPanel.RunWatcher() {
            @Override
            public void runStarted() {
                runner.clearPulse();
                closeDialog(dialog);
                // We are inside the Run button's action, so open the next dialog a tick later
                // rather than reentrantly. The process cannot report its exit before this runs:
                // that notification is itself queued on the EDT, and queued after this one.
                SwingUtilities.invokeLater(() -> showRunning(i, step));
            }

            @Override
            public void runFinished(boolean ok) {
                // Never called: showRunning() has taken over as the watcher by the time the
                // process exits.
            }
        });
    }

    /** The command is running: keep a dialog up until the process exits, then advance (or retry). */
    private void showRunning(int i, Step step) {
        CommandRunnerPanel runner = step.runner();

        // The dev servers only end when the user stops them, so point at the Stop button the same
        // way we pointed at Run. The one-shot commands end by themselves - nothing to click.
        if (step.stopToContinue()) runner.pulseStopButton();

        // {0} is the site id, which serve echoes in its "Serving [id] at:" line.
        Phase dialog = showDialog(step.phase(), step.runningMsgKey(), _ -> endStep(runner),
                                  runner.getSiteId());

        runner.setRunWatcher(new CommandRunnerPanel.RunWatcher() {
            @Override
            public void runStarted() {
                // Can't happen - a process is already running.
            }

            @Override
            public void runFinished(boolean ok) {
                runner.setRunWatcher(null);
                runner.clearPulse();
                closeDialog(dialog);
                // On failure the panel has already shown its error dialog; go back to the
                // instruction so the user can fix the problem and click Run again.
                SwingUtilities.invokeLater(() -> runStep(ok ? i + 1 : i));
            }
        });
    }

    /** The user canceled (or closed) a command step's dialog - stop watching and end the tour. */
    private void endStep(CommandRunnerPanel runner) {
        runner.setRunWatcher(null);
        runner.clearPulse();
        running_ = false;
    }

    /**
     * Dismiss a dialog we opened, without a result. {@link DialogPhase#processButton} is what
     * normally sets one, so closing this way leaves the (one-shot) result listener unfired - which
     * is what we want: the tour is driving this transition, not the user.
     */
    private void closeDialog(Phase dialog) {
        if (dialog instanceof DialogPhase dp) dp.removeDialog();
    }

    /**
     * Show a (non-modal) tour dialog and invoke {@code onResult} with the pressed button once the
     * user acts. We are on the EDT, so the click cannot be dispatched until this returns, which
     * makes registering the listener after start() race-free. Modal/no-show dialogs that resolve
     * synchronously simply fire the listener immediately on registration.
     */
    private Phase showDialog(String phaseName, String msgKey, Consumer<AppButton> onResult,
                             Object... msgArgs) {
        TypedHashMap params = new TypedHashMap();
        if (msgArgs.length == 0) {
            // No args: let DisplayMessage look the key up, so the text is used verbatim.
            params.setString(DisplayMessage.PARAM_MESSAGE_KEY, msgKey);
        } else {
            // DisplayMessage's key lookup takes no arguments, so resolve the text ourselves and
            // hand over the finished message.  Note this puts it through MessageFormat - see the
            // quoting warning on these messages in client.properties.
            params.setString(DisplayMessage.PARAM_MESSAGE, PropertyConfig.getMessage(msgKey, msgArgs));
        }
        Phase phase = context_.processPhaseNow(phaseName, params);
        // Defer the continuation a tick so the current dialog fully closes (processButton calls
        // setResult then removeDialog) before the next step opens its dialog - otherwise the two
        // would briefly overlap and the next phase would start reentrantly inside the button press.
        phase.setResultListener((_, result) ->
                SwingUtilities.invokeLater(() -> onResult.accept((AppButton) result)));
        return phase;
    }
}
