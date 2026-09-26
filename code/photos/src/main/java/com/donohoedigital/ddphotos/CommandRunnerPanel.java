package com.donohoedigital.ddphotos;

import com.donohoedigital.app.engine.AppContext;
import com.donohoedigital.app.engine.EngineUtils;
import com.donohoedigital.config.PropertyConfig;
import com.donohoedigital.ddphotos.config.Site;
import com.donohoedigital.ddphotos.runner.CommandRunner;
import com.donohoedigital.ddphotos.runner.Prerequisite;
import com.donohoedigital.gui.DDTabbedPane;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Tabbed runner panel: drives a single {@link CommandRunner} against the site selected in the
 * shared {@link SiteBarPanel}, with Docker checks and the {@link RunWatcher} hook the guided tour
 * and {@link PublishController} follow a run through.  The control row, flag rows, console,
 * process plumbing and prerequisite flow live in {@link AbstractRunnerPanel}.
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
     * Observer of a run, so the guided tour can follow along rather than press Run itself, and so
     * {@link PublishController} can chain one command into the next.
     */
    public interface RunWatcher {

        /** The main command's process has started. */
        void runStarted();

        /**
         * The process ended. {@code ok} is an exit code of 0, or a deliberate user Stop - which is
         * how the long-running dev servers ({@code run}, {@code serve}) are meant to end.
         */
        void runFinished(boolean ok);

        /**
         * The run ended before the main command ever started - no site, Docker down, another
         * command busy, an unusable flag, or a prerequisite that failed or could not be evaluated.
         * The reason has already been put in front of the user (console line or dialog).
         *
         * <p>The tour ignores this and keeps waiting for the user to try again; publish, which
         * pressed Run itself, has to stop.
         */
        default void runAborted() {}

        /**
         * True to suppress this panel's own "command failed" dialog, for a watcher that reports
         * the failure itself and would otherwise stack a second dialog on top of it.
         */
        default boolean suppressFailureDialog() { return false; }
    }

    /** Register the tour's or publish's observer for this command, or pass null to clear it. */
    public void setRunWatcher(RunWatcher watcher) {
        runWatcher_ = watcher;
    }

    /**
     * Starts this tab's command exactly as the Run button does, for {@link PublishController}.
     * The button's own enabled state is the only guard {@link #onRun} has - an invalid flag (a
     * missing export dir, a blank project name) merely disables it - so that has to be checked
     * here rather than launching a command the user could not have launched by hand.
     */
    public void startRun() {
        // Some flag choices are derived from the filesystem (the uploaders' export dir, which
        // the export step we may have just run creates), so re-read them before judging validity.
        rebuildFlagsRow();
        updateButtonState();
        if (runBtn_ == null || !runBtn_.isEnabled()) {
            console_.appendSystemError(PropertyConfig.getMessage("msg.publish.cannotRun",
                                                                 runner_.getDisplayName()));
            notifyAborted();
            return;
        }
        onRun();
    }

    /** This tab's command as the console names it (e.g. "ddphotos build"). */
    public String getRunnerDisplayName() {
        return runner_.getDisplayName();
    }

    @Override
    protected void onRunAborted() {
        notifyAborted();
    }

    /** Tells the runner and the watcher (if any) that the run is over with nothing launched. */
    private void notifyAborted() {
        runner_.afterRun();
        RunWatcher watcher = runWatcher_;
        if (watcher != null) watcher.runAborted();
    }

    @Override
    protected void onRun() {
        RunnerConsole.clearForRun(console_);
        clearUserStop();

        if (currentSite_ == null) {
            console_.appendSystem(PropertyConfig.getMessage("msg.cmd.noSiteSelected"));
            notifyAborted();
            return;
        }

        if (runner_.isDockerRequired() && !DockerStatus.isDockerRunning()) {
            EngineUtils.displayWarningDialog(context_,
                    PropertyConfig.getMessage("msg.docker.required", runner_.getDisplayName()),
                    "msg.windowtitle.dockerRequired", null);
            notifyAborted();
            return;
        }

        String running = otherRunningCommand();
        String busy = running == null ? null : runner_.busyMessage(running);
        if (busy != null) {
            EngineUtils.displayWarningDialog(context_, busy, runner_.busyTitleKey(), null);
            notifyAborted();
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

    @Override
    protected void launchMainCommand(Map<String, String> userValues) {
        List<String> cmd = runner_.buildCommand(currentSite_, userValues);
        console_.appendSystem(PropertyConfig.getMessage("msg.cmd.running", String.join(" ", runner_.finalCommand(cmd))));
        try {
            process_ = runner_.launch(currentSite_, userValues);
            updateButtonState();
            if (runWatcher_ != null) runWatcher_.runStarted();
            startReaders(process_, code -> {
                RunWatcher watcher = runWatcher_;
                // Before any dialog: whatever the runner does here (the Upgrade tab refreshes the
                // sites' scripts) should be done by the time the user is told the command is over.
                runner_.afterRun();
                // Feedback first: on a failure it is a modal dialog, and the tour's own dialog
                // should not open behind it.
                showCompletionFeedback(code, watcher);
                // A user Stop on a long-running dev server (run/serve) counts as
                // success for the tour: it's how the user advances those steps.
                if (watcher != null) watcher.runFinished(code == 0 || wasUserStop(code));
            });
        } catch (IOException e) {
            process_ = null;
            console_.appendSystemError(PropertyConfig.getMessage("msg.cmd.startFailed", "process", e.getMessage()));
            updateButtonState();
            if (runner_.showsFailureFeedback() && !suppressesFailureDialog()) {
                EngineUtils.displayErrorDialog(context_,
                        PropertyConfig.getMessage("msg.cmd.launchFailure",
                                                   runner_.getDisplayName(), e.getMessage()),
                        "msg.windowtitle.cmdFailure",
                        "cmd.failure." + runner_.getPrefsKey(true), "cmdnoshow");
            }
            notifyAborted();
        }
    }

    private void showCompletionFeedback(int code, RunWatcher watcher) {
        if (wasUserStop(code)) return;
        String displayName = runner_.getDisplayName();
        String noShowKey = runner_.getPrefsKey(true);
        // While the tour or a publish run is driving this panel, their own dialog is the
        // acknowledgment, so skip the standard success popup.
        if (code == 0 && watcher != null) return;
        if (code == 0 && runner_.showsSuccessFeedback()) {
            EngineUtils.displayInformationDialog(context_,
                    PropertyConfig.getMessage("msg.cmd.success", displayName),
                    "msg.windowtitle.cmdSuccess",
                    "cmd.success." + noShowKey, "cmdnoshow");
            maybeOfferPublish();
        } else if (code != 0 && runner_.showsFailureFeedback()
                   && !(watcher != null && watcher.suppressFailureDialog())) {
            EngineUtils.displayErrorDialog(context_,
                    PropertyConfig.getMessage("msg.cmd.failure", displayName, code),
                    "msg.windowtitle.cmdFailure",
                    "cmd.failure." + noShowKey, "cmdnoshow");
        }
    }

    private boolean suppressesFailureDialog() {
        RunWatcher watcher = runWatcher_;
        return watcher != null && watcher.suppressFailureDialog();
    }

    /**
     * Points out the Publish menu after a hand-run publishing step, since nothing else in the UI
     * hints that the photogen-build-deploy sequence can be run in one go.  Only after the step
     * that ends a publish ({@code deploy} / {@code wrangler} / {@code surge}) - by then the user
     * has just done the whole sequence by hand - and only until they set publishing up for this
     * site (or tick the do-not-show box).
     */
    private void maybeOfferPublish() {
        if (!runner_.isPublishTarget() || PublishSettings.isConfigured(currentSite_)) return;
        EngineUtils.displayInformationDialog(context_,
                PropertyConfig.getMessage("msg.publish.offer"),
                "msg.windowtitle.publishOffer",
                "publish.offer", "cmdnoshow");
    }
}
