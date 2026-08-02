package com.donohoedigital.ddphotos;

import com.donohoedigital.app.engine.AppContext;
import com.donohoedigital.app.engine.EngineUtils;
import com.donohoedigital.config.PropertyConfig;
import com.donohoedigital.ddphotos.config.Site;
import com.donohoedigital.ddphotos.runner.CommandRunner;
import com.donohoedigital.ddphotos.runner.Prerequisite;
import com.donohoedigital.gui.DDTabbedPane;

import javax.swing.*;
import java.io.IOException;
import java.util.*;
import java.util.List;

/**
 * Tabbed runner panel: drives a single {@link CommandRunner} against the site selected in the
 * shared {@link SiteBarPanel}, with prerequisite handling, Docker checks and guided-tour support.
 * The control row, flag rows, console and process plumbing live in {@link AbstractRunnerPanel}.
 */
public class CommandRunnerPanel extends AbstractRunnerPanel {

    private final SiteBarPanel siteBar_;
    private final CommandRunner runner_;

    private Site currentSite_;

    // Set while the tour is on this panel's step; notified as the user's run starts and ends.
    private RunWatcher runWatcher_;

    public CommandRunnerPanel(SiteBarPanel siteBar, CommandRunner runner, AppContext context) {
        super(context);
        siteBar_ = siteBar;
        runner_ = runner;
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // Variation points
    // ──────────────────────────────────────────────────────────────────────────────

    @Override
    protected Site getCurrentSite() { return currentSite_; }

    @Override
    protected CommandRunner resolveRunner() { return runner_; }

    @Override
    protected void onBeforeBuild() {
        currentSite_ = siteBar_.getSelectedSite();
        activeRunner_ = runner_;
    }

    @Override
    protected void onAfterBuild() {
        siteBar_.addSiteListener(this::onSiteChanged);

        // Some flag choices (e.g., WranglerRunner/SurgeRunner "export dir") are derived from the
        // filesystem and can change as a result of running commands in other tabs (e.g., Export).
        // Refresh them whenever this tab becomes the selected one so they reflect the latest state.
        DDTabbedPane tabs = getTabPane();
        if (tabs != null) {
            tabs.addChangeListener(_ -> {
                if (isSelectedTab()) {
                    rebuildFlagsRow();
                    updateButtonState();
                }
            });
        }
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // Site changes
    // ──────────────────────────────────────────────────────────────────────────────

    private void onSiteChanged(Site site) {
        currentSite_ = site;
        rebuildFlagsRow();
        updateButtonState();
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // Run / Stop / Kill
    // ──────────────────────────────────────────────────────────────────────────────

    /**
     * Observer of a user-driven run, so the guided tour can follow along rather than press Run
     * itself. Only a run that actually launches is reported: if the command never starts (no site,
     * Docker down, another command busy, a failed prerequisite) nothing fires and the tour simply
     * keeps waiting for the user to try again.
     */
    public interface RunWatcher {

        /** The main command's process has started. */
        void runStarted();

        /**
         * The process ended. {@code ok} is an exit code of 0, or a deliberate user Stop - which is
         * how the long-running dev servers ({@code run}, {@code serve}) are meant to end.
         */
        void runFinished(boolean ok);
    }

    /** Register the tour's observer for this command, or pass null to clear it. */
    public void setRunWatcher(RunWatcher watcher) {
        runWatcher_ = watcher;
    }

    @Override
    protected void onRun() {
        RunnerConsole.clearForRun(console_);
        clearUserStop();

        if (currentSite_ == null) {
            console_.appendSystem(PropertyConfig.getMessage("msg.cmd.noSiteSelected"));
            return;
        }

        if (runner_.isDockerRequired() && !DockerStatus.isDockerRunning()) {
            EngineUtils.displayWarningDialog(context_,
                    PropertyConfig.getMessage("msg.docker.required", runner_.getDisplayName()),
                    "msg.windowtitle.dockerRequired", null);
            return;
        }

        String running = otherRunningCommand();
        String busy = running == null ? null : runner_.busyMessage(running);
        if (busy != null) {
            EngineUtils.displayWarningDialog(context_, busy, runner_.busyTitleKey(), null);
            return;
        }

        Map<String, String> userValues = collectUserValues();
        Prerequisite prereq = runner_.getPrerequisite(currentSite_, userValues);
        if (prereq != null) {
            runWithPrerequisite(prereq, userValues);
        } else {
            launchMainCommand(userValues);
        }
    }

    private void runWithPrerequisite(Prerequisite prereq, Map<String, String> userValues) {
        console_.appendSystem(prereq.checkingMessage());
        List<String> checkCmd = runner_.finalCommand(prereq.checkCommand());
        console_.appendSystem(PropertyConfig.getMessage("msg.cmd.running", String.join(" ", checkCmd)));
        try {
            process_ = runner_.launchCommand(checkCmd);
            updateButtonState();
            startCheckReaders(process_, prereq, userValues);
        } catch (IOException e) {
            process_ = null;
            console_.appendSystemError(PropertyConfig.getMessage("msg.cmd.startFailed", "prerequisite check", e.getMessage()));
            updateButtonState();
        }
    }

    private void startCheckReaders(Process p, Prerequisite prereq, Map<String, String> userValues) {
        StringBuffer captured = new StringBuffer();
        Thread out = new Thread(() -> console_.pumpStreamCapturing(p.getInputStream(), captured, false));
        Thread err = new Thread(() -> console_.pumpStreamCapturing(p.getErrorStream(), captured, true));
        Thread mon = new Thread(() -> {
            try {
                int code = p.waitFor();
                // wait for both readers to drain so captured holds the full output
                out.join();
                err.join();
                String output = captured.toString();
                SwingUtilities.invokeLater(() -> {
                    process_ = null;
                    // A stopped check produced no verdict - its exit code and partial output are
                    // artifacts of how we killed it, not an answer about the user's login.
                    if (wasUserStop(code)) {
                        console_.appendSystem(PropertyConfig.getMessage("msg.cmd.stopped"));
                        updateButtonState();
                        return;
                    }
                    switch (prereq.check(output, code)) {
                        case PASSED -> {
                            // add a little space after check
                            console_.appendSystem("");
                            console_.appendSystem("---");

                            Prerequisite next = prereq.next();
                            if (next != null) {
                                runWithPrerequisite(next, userValues);
                            } else {
                                launchMainCommand(userValues);
                            }
                        }
                        case FAILED -> handlePrerequisiteFailure(prereq, userValues);
                        case ERROR -> handlePrerequisiteError(prereq, code);
                    }
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        out.setDaemon(true);
        err.setDaemon(true);
        mon.setDaemon(true);
        out.start();
        err.start();
        mon.start();
    }

    /**
     * The check could not be evaluated - report what actually happened and stop. Deliberately does
     * not run {@link Prerequisite#remediation}: offering to log in or to create a project when we
     * never got an answer is a guess, and in the create case a guess that changes remote state.
     */
    private void handlePrerequisiteError(Prerequisite prereq, int exitCode) {
        console_.appendSystemError(prereq.errorMessage(exitCode));
        updateButtonState();
        String html = prereq.errorDialogMessage(exitCode);
        if (html != null) {
            EngineUtils.displayErrorDialog(context_, html, prereq.errorTitleKey(), null);
        }
    }

    private void handlePrerequisiteFailure(Prerequisite prereq, Map<String, String> userValues) {
        Prerequisite next = prereq.next();
        switch (prereq.remediation()) {
            case Prerequisite.RunCommand(List<String> cmd) -> {
                console_.appendSystem(prereq.failedMessage());
                runNext(userValues, next, cmd);
            }
            case Prerequisite.ShowDialog(String html, String titleKey) -> {
                updateButtonState();
                EngineUtils.displayWarningDialog(context_, html, titleKey, null);
            }
            case Prerequisite.ShowMessage(String message) -> {
                console_.appendSystem(message);
                updateButtonState();
            }
            case Prerequisite.ConfirmThenRun(String msgKey, String titleKey, List<String> cmd, Object[] msgArgs) -> {
                console_.appendSystem(prereq.failedMessage());
                updateButtonState();
                String html = PropertyConfig.getMessage(msgKey, msgArgs);
                boolean confirmed = EngineUtils.displayConfirmationDialog(context_, html, titleKey, null);
                if (!confirmed) {
                    console_.appendSystem(PropertyConfig.getMessage("msg.cmd.aborted"));
                    return;
                }
                runNext(userValues, next, cmd);
            }
        }
    }

    private void runNext(Map<String, String> userValues, Prerequisite next, List<String> cmd) {
        cmd = runner_.finalCommand(cmd);
        console_.appendSystem(PropertyConfig.getMessage("msg.cmd.running", String.join(" ", cmd)));
        try {
            process_ = runner_.launchCommand(cmd);
            updateButtonState();
            startRemediationReaders(process_, next, userValues);
        } catch (IOException e) {
            process_ = null;
            console_.appendSystemError(PropertyConfig.getMessage("msg.cmd.startFailed", "remediation", e.getMessage()));
            updateButtonState();
        }
    }

    private void startRemediationReaders(Process p, Prerequisite nextPrereq,
                                         Map<String, String> userValues) {
        Thread out = new Thread(() -> console_.pumpStream(p.getInputStream(), false));
        Thread err = new Thread(() -> console_.pumpStream(p.getErrorStream(), true));
        Thread mon = new Thread(() -> {
            try {
                int code = p.waitFor();
                SwingUtilities.invokeLater(() -> {
                    process_ = null;
                    if (code == 0) {
                        if (nextPrereq != null) {
                            runWithPrerequisite(nextPrereq, userValues);
                        } else {
                            launchMainCommand(userValues);
                        }
                    } else {
                        console_.appendSystem(PropertyConfig.getMessage("msg.cmd.failedExit", code));
                        updateButtonState();
                    }
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        out.setDaemon(true);
        err.setDaemon(true);
        mon.setDaemon(true);
        out.start();
        err.start();
        mon.start();
    }

    private void launchMainCommand(Map<String, String> userValues) {
        List<String> cmd = runner_.buildCommand(currentSite_, userValues);
        console_.appendSystem(PropertyConfig.getMessage("msg.cmd.running", String.join(" ", runner_.finalCommand(cmd))));
        try {
            process_ = runner_.launch(currentSite_, userValues);
            updateButtonState();
            if (runWatcher_ != null) runWatcher_.runStarted();
            startReaders(process_, code -> {
                RunWatcher watcher = runWatcher_;
                // Feedback first: on a failure it is a modal dialog, and the tour's own dialog
                // should not open behind it.
                showCompletionFeedback(code, watcher != null);
                // A user Stop on a long-running dev server (run/serve) counts as
                // success for the tour: it's how the user advances those steps.
                if (watcher != null) watcher.runFinished(code == 0 || wasUserStop(code));
            });
        } catch (IOException e) {
            process_ = null;
            console_.appendSystemError(PropertyConfig.getMessage("msg.cmd.startFailed", "process", e.getMessage()));
            updateButtonState();
            if (runner_.showsFailureFeedback()) {
                EngineUtils.displayErrorDialog(context_,
                        PropertyConfig.getMessage("msg.cmd.launchFailure",
                                                   runner_.getDisplayName(), e.getMessage()),
                        "msg.windowtitle.cmdFailure",
                        "cmd.failure." + runner_.getPrefsKey(true), "cmdnoshow");
            }
        }
    }

    private void showCompletionFeedback(int code, boolean wasTour) {
        if (wasUserStop(code)) return;
        String displayName = runner_.getDisplayName();
        String noShowKey = runner_.getPrefsKey(true);
        // During the tour the next tour dialog is the acknowledgment, so skip the
        // standard success popup (failures still surface so the user can read them).
        if (code == 0 && wasTour) return;
        if (code == 0 && runner_.showsSuccessFeedback()) {
            EngineUtils.displayInformationDialog(context_,
                    PropertyConfig.getMessage("msg.cmd.success", displayName),
                    "msg.windowtitle.cmdSuccess",
                    "cmd.success." + noShowKey, "cmdnoshow");
        } else if (code != 0 && runner_.showsFailureFeedback()) {
            EngineUtils.displayErrorDialog(context_,
                    PropertyConfig.getMessage("msg.cmd.failure", displayName, code),
                    "msg.windowtitle.cmdFailure",
                    "cmd.failure." + noShowKey, "cmdnoshow");
        }
    }
}
