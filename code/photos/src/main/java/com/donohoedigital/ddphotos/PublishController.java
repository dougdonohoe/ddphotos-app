package com.donohoedigital.ddphotos;

import com.donohoedigital.app.engine.AppContext;
import com.donohoedigital.app.engine.DialogPhase;
import com.donohoedigital.app.engine.DisplayMessage;
import com.donohoedigital.app.engine.EngineUtils;
import com.donohoedigital.base.TypedHashMap;
import com.donohoedigital.config.PropertyConfig;
import com.donohoedigital.ddphotos.config.Site;
import com.donohoedigital.gui.OptionTabbedPane;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Runs a site's publishing sequence - {@code photogen}, {@code build} and then either
 * {@code deploy} or {@code export} plus an uploader - without the user visiting each tab and
 * pressing Run.  Which of those steps run is {@link PublishSettings}, chosen per site in
 * {@link PublishSettingsDialog}.
 *
 * <p>A step selects its tab, runs it, and keeps a dialog up until the command exits.  The
 * dialog is <b>modal</b>: the steps feed each other (photogen writes what build consumes,
 * export writes the directory the uploader pushes), so editing config, starting another
 * command or switching sites partway through would undercut the very sequence being run.  The
 * tab behind the dialog still shows the output.
 *
 * <p>That modality is what shapes this class.  {@code processPhaseNow} on a modal dialog blocks
 * in a nested event loop until the dialog is removed, so the run has to be started <i>before</i>
 * the dialog goes up, and the loop below reads as the sequence it is rather than as a chain of
 * callbacks: {@link CommandRunnerPanel.RunWatcher} only records how the step ended and closes
 * the dialog, which returns control to {@link #runStep}.  Each step's loop exits before the
 * next one starts, so they nest no deeper than one.
 *
 * <p>Nothing here knows what any command does: each panel already holds the flags the user last
 * ran that tab with, so publish is a sequence of Run clicks and nothing more.  A step that fails
 * (or never starts - Docker down, an unusable flag, a prerequisite that wants a login) ends the
 * run where it stands and says so; the console on the tab that is already selected has the detail.
 *
 * <p>See the {@code PublishStep} phase in appdef.xml and {@link PublishStepDialog}.
 */
public class PublishController {

    /** How a step ended.  Null while it is still running - see {@link #runStep}. */
    private enum Outcome {
        SUCCEEDED,
        /** Non-zero exit. */
        FAILED,
        /** The command never launched (no Docker, an unusable flag, a failed prerequisite). */
        NOT_STARTED,
        /** The process was stopped rather than allowed to finish. */
        STOPPED
    }

    private final AppContext context_;
    private final OptionTabbedPane tabs_;
    private final SiteBarPanel siteBar_;
    private final Map<PublishSettings.Step, CommandRunnerPanel> panels_;

    // Re-labels and re-enables the Publish menus, which are disabled for the duration of a run.
    private final Runnable menuRefresh_;

    // True for the whole run. Menu accelerators are not blocked by an internal modal dialog
    // (it blocks the mouse), so the menu items are disabled while this is set - and this stays
    // as the backstop.
    private boolean running_;

    // The step dialog currently up, handed over by PublishStepDialog as it opens so the watcher
    // can close it. Null between steps.
    private DialogPhase stepDialog_;

    // How the running step ended, set by the watcher. Null means "not yet" - and, once the step
    // dialog has closed, means the Stop button is what closed it.
    private Outcome outcome_;

    public PublishController(AppContext context, OptionTabbedPane tabs, SiteBarPanel siteBar,
                             Map<PublishSettings.Step, CommandRunnerPanel> panels,
                             Runnable menuRefresh) {
        context_ = context;
        tabs_ = tabs;
        siteBar_ = siteBar;
        panels_ = new EnumMap<>(panels);
        menuRefresh_ = menuRefresh;
    }

    /** True while a publish run is in progress; the Publish menu items are disabled meanwhile. */
    public boolean isRunning() {
        return running_;
    }

    /**
     * Runs the selected site's publish sequence, start to finish, before returning.  The menu
     * item is disabled when there is no site or its settings have never been opened, so both
     * checks here are just belt and braces.
     */
    public void start() {
        if (running_) return;
        Site site = siteBar_.getSelectedSite();
        if (!PublishSettings.isConfigured(site)) return;

        List<PublishSettings.Step> steps = PublishSettings.load(site).steps();
        running_ = true;
        menuRefresh_.run();
        try {
            for (int i = 0; i < steps.size(); i++) {
                if (!runStep(steps, i)) return;
            }
            finished(site);
        } finally {
            running_ = false;
            menuRefresh_.run();
        }
    }

    /** Runs one step, returning true if it succeeded and publishing should carry on. */
    private boolean runStep(List<PublishSettings.Step> steps, int i) {
        PublishSettings.Step step = steps.get(i);
        CommandRunnerPanel panel = panels_.get(step);

        tabs_.setSelectedComponent(panel);
        // The tab's UI is built lazily, and we are about to press its Run button. Harmless if the
        // selection above already triggered it - initUI() only builds once.
        panel.initUI();

        // Started by hand before publishing began: the panel would refuse this anyway, but not
        // with a message that says which run is in the way.
        if (panel.isRunning()) {
            failed(step, "msg.publish.busy");
            return false;
        }

        outcome_ = null;
        panel.setRunWatcher(watcher(panel));
        panel.startRun();

        // startRun reports synchronously when nothing launches - no point opening a dialog for
        // a step that is already over.
        if (outcome_ == null) {
            // Blocks (while dispatching events) until the watcher closes this dialog on the
            // process exit, or the user presses Stop.
            showStepDialog(steps, i, step);
            stepDialog_ = null;
        }
        panel.setRunWatcher(null);

        if (outcome_ == null) {
            // Stop: no outcome was ever recorded, so the button is what closed the dialog. The
            // user cannot reach the tab's own Stop button under a modal dialog, so stop the
            // command for them rather than leaving one running that they have to chase.
            panel.stopRun();
            return false;
        }

        return switch (outcome_) {
            case SUCCEEDED -> true;
            // The user did the stopping in this case too (see the watcher) - saying so would
            // only tell them what they just did.
            case STOPPED -> false;
            case FAILED -> {
                failed(step, "msg.publish.failed");
                yield false;
            }
            case NOT_STARTED -> {
                failed(step, "msg.publish.notStarted");
                yield false;
            }
        };
    }

    /** Records how the step ended and lets {@link #runStep} carry on by closing its dialog. */
    private CommandRunnerPanel.RunWatcher watcher(CommandRunnerPanel panel) {
        return new CommandRunnerPanel.RunWatcher() {
            @Override
            public void runStarted() {
                // Nothing to do - the dialog for this step goes up as soon as we return.
            }

            @Override
            public void runFinished(boolean ok) {
                // A Stop is success to the tour, whose steps include the dev servers that only
                // end that way. Here the only path left to one is quitting the app mid-step
                // (okayToClose stops the command) - which must not read as a step that passed
                // and send us into the next one during shutdown.
                outcome_ = panel.wasStoppedByUser() ? Outcome.STOPPED
                         : ok ? Outcome.SUCCEEDED : Outcome.FAILED;
                closeStepDialog();
            }

            @Override
            public void runAborted() {
                outcome_ = Outcome.NOT_STARTED;
                closeStepDialog();
            }

            /** Publish reports the failure itself - see {@link PublishController#failed}. */
            @Override
            public boolean suppressFailureDialog() {
                return true;
            }
        };
    }

    // -------------------------------------------------------------------------
    // The step dialog
    // -------------------------------------------------------------------------

    /**
     * Shows the "step N of M" dialog and does not return until the step is over.  Modal, so this
     * blocks in a nested event loop - which is what runs the process-exit notification that ends
     * up closing it.
     */
    private void showStepDialog(List<PublishSettings.Step> steps, int i, PublishSettings.Step step) {
        TypedHashMap params = new TypedHashMap();
        params.setString(DisplayMessage.PARAM_MESSAGE,
                PropertyConfig.getMessage("msg.publish.running",
                                          i + 1, steps.size(), commandName(step)));
        params.setObject(PublishStepDialog.PARAM_CONTROLLER, this);
        context_.processPhaseNow("PublishStep", params);
    }

    /** Called by {@link PublishStepDialog} as it opens, before its modal loop starts. */
    void stepDialogOpened(DialogPhase dialog) {
        stepDialog_ = dialog;
    }

    /**
     * Dismisses the step dialog, without a result.  {@code DialogPhase.processButton} is what
     * normally sets one, so closing this way leaves {@link #outcome_} as the only record of how
     * the step ended - and a null outcome unambiguously means the Stop button.
     */
    private void closeStepDialog() {
        DialogPhase dialog = stepDialog_;
        stepDialog_ = null;
        if (dialog != null) dialog.removeDialog();
    }

    // -------------------------------------------------------------------------
    // Outcome dialogs
    // -------------------------------------------------------------------------

    private void failed(PublishSettings.Step step, String msgKey) {
        EngineUtils.displayErrorDialog(context_,
                PropertyConfig.getMessage(msgKey, commandName(step)),
                "msg.windowtitle.publish", null);
    }

    private void finished(Site site) {
        EngineUtils.displayInformationDialog(context_,
                PropertyConfig.getMessage("msg.publish.succeeded", site.getDisplayName()),
                "msg.windowtitle.publish", null);
    }

    /** The command as the console names it (e.g. "ddphotos build"). */
    private String commandName(PublishSettings.Step step) {
        CommandRunnerPanel panel = panels_.get(step);
        return panel != null ? panel.getRunnerDisplayName() : step.name().toLowerCase();
    }
}
