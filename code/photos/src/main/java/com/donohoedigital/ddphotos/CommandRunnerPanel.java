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

    // Set for the duration of a tour-driven run; notified true (advance) or false (stop tour).
    private java.util.function.Consumer<Boolean> tourCallback_;

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
     * Run this command as part of the guided tour. {@code onDone} is invoked with
     * {@code true} when the command finished in a way the tour treats as success
     * (exit code 0, or a user Stop for long-running dev servers), and {@code false}
     * when it could not start or failed - so the tour can advance or stop accordingly.
     */
    public void startTourRun(java.util.function.Consumer<Boolean> onDone) {
        tourCallback_ = onDone;
        onRun();
    }

    private void fireTour(boolean advance) {
        java.util.function.Consumer<Boolean> cb = tourCallback_;
        tourCallback_ = null;
        if (cb != null) cb.accept(advance);
    }

    @Override
    protected void onRun() {
        RunnerConsole.clearForRun(console_);

        if (currentSite_ == null) {
            console_.appendSystem(PropertyConfig.getMessage("msg.cmd.noSiteSelected"));
            fireTour(false);
            return;
        }

        if (runner_.isDockerRequired() && !DockerStatus.isDockerRunning()) {
            EngineUtils.displayWarningDialog(context_,
                    PropertyConfig.getMessage("msg.docker.required", runner_.getDisplayName()),
                    "msg.windowtitle.dockerRequired", null);
            fireTour(false);
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
                    if (prereq.passed(output, code)) {
                        // add a little space after check
                        console_.appendSystem("");
                        console_.appendSystem("---");

                        Prerequisite next = prereq.next();
                        if (next != null) {
                            runWithPrerequisite(next, userValues);
                        } else {
                            launchMainCommand(userValues);
                        }
                    } else {
                        handlePrerequisiteFailure(prereq, userValues);
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
            startReaders(process_, code -> {
                // A user Stop on a long-running dev server (run/serve) counts as
                // success for the tour: it's how the user advances those steps.
                boolean wasTour = tourCallback_ != null;
                fireTour(code == 0 || wasUserStop(code));
                showCompletionFeedback(code, wasTour);
            });
        } catch (IOException e) {
            process_ = null;
            console_.appendSystemError(PropertyConfig.getMessage("msg.cmd.startFailed", "process", e.getMessage()));
            updateButtonState();
            fireTour(false);
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
