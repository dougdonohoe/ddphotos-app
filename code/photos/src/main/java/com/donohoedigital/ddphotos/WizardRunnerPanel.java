package com.donohoedigital.ddphotos;

import com.donohoedigital.app.engine.AppContext;
import com.donohoedigital.app.engine.EngineUtils;
import com.donohoedigital.config.PropertyConfig;
import com.donohoedigital.ddphotos.config.Site;
import com.donohoedigital.ddphotos.runner.CommandRunner;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Runner panel used in wizard steps. Shares the control row, flag rows, console and process
 * plumbing with {@link AbstractRunnerPanel}; differs in that the runner is built fresh at
 * click time (so it can read live wizard state), completion runs an {@code onSuccess} callback,
 * and success/failure are shown via the supplied message keys.
 */
public class WizardRunnerPanel extends AbstractRunnerPanel {

    private final Supplier<CommandRunner> runnerSupplier_;
    private final Supplier<Site> siteSupplier_;
    private final Runnable onSuccess_;
    private final String successMsgKey_;
    private final String failureMsgKey_;

    /**
     * @param runnerSupplier builds the runner at click time (may read live wizard UI state)
     * @param siteSupplier   supplies the (non-null) site that drives the flag rows
     * @param onSuccess      run when the command exits with code 0
     * @param successMsgKey  message shown in an information dialog on exit code 0
     * @param failureMsgKey  message shown when the command exits non-zero or fails to launch
     */
    public WizardRunnerPanel(AppContext context, Supplier<CommandRunner> runnerSupplier,
                             Supplier<Site> siteSupplier, Runnable onSuccess,
                             String successMsgKey, String failureMsgKey) {
        super(context);
        runnerSupplier_ = runnerSupplier;
        siteSupplier_ = siteSupplier;
        onSuccess_ = onSuccess;
        successMsgKey_ = successMsgKey;
        failureMsgKey_ = failureMsgKey;
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // Variation points
    // ──────────────────────────────────────────────────────────────────────────────

    // The wizard's surrounding content (HTML area) sits flush left, so no extra left inset.
    @Override
    protected int contentLeftInset() { return 0; }

    // Transparent flag rows so they blend into the wizard's parent panel (no white band).
    @Override
    protected java.awt.Color flagRowBackground() { return null; }

    @Override
    protected Site getCurrentSite() { return siteSupplier_.get(); }

    @Override
    protected CommandRunner resolveRunner() { return runnerSupplier_.get(); }

    @Override
    protected void onRun() {
        activeRunner_ = runnerSupplier_.get();
        Site site = getCurrentSite();
        Map<String, String> userValues = collectUserValues();
        List<String> cmd = activeRunner_.buildCommand(site, userValues);

        RunnerConsole.clearForRun(console_);
        console_.appendSystem(PropertyConfig.getMessage("msg.cmd.running", String.join(" ", activeRunner_.finalCommand(cmd))));
        try {
            process_ = activeRunner_.launch(site, userValues);
            updateButtonState();
            startReaders(process_, this::handleCompletion);
        } catch (IOException e) {
            process_ = null;
            console_.appendSystemError(PropertyConfig.getMessage("msg.cmd.startFailed", activeRunner_.getDisplayName(), e.getMessage()));
            updateButtonState();
            showFailureDialog();
        }
    }

    private void handleCompletion(int code) {
        if (code == 0) {
            if (onSuccess_ != null) onSuccess_.run();
            showSuccessDialog();
        } else if (!wasUserStop(code)) {
            showFailureDialog();
        }
    }

    private void showSuccessDialog() {
        EngineUtils.displayInformationDialog(context_,
                PropertyConfig.getMessage(successMsgKey_),
                "msg.windowtitle.cmdSuccess", null);
    }

    /** Shown both when the process exits non-zero and when it fails to launch at all. */
    private void showFailureDialog() {
        EngineUtils.displayErrorDialog(context_,
                PropertyConfig.getMessage(failureMsgKey_),
                "msg.windowtitle.cmdFailure", null);
    }
}
