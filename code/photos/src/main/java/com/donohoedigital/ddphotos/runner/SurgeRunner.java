package com.donohoedigital.ddphotos.runner;

import com.donohoedigital.config.PropertyConfig;
import com.donohoedigital.ddphotos.PhotosUtils;
import com.donohoedigital.ddphotos.config.Site;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static com.donohoedigital.ddphotos.runner.FlagVisibility.EDITABLE;
import static com.donohoedigital.ddphotos.runner.Prerequisite.Result.*;

public class SurgeRunner extends DdphotosRunner {

    // Non-empty required: disables Run until the user fills it in
    // domain like my-site.surge.sh or any hostname
    private static final String DOMAIN_PATTERN = "[a-zA-Z0-9.-]+";

    /** What 'surge whoami' prints when there are no credentials in ~/.netrc. */
    private static final String NOT_AUTHENTICATED = "Not Authenticated";

    /**
     * What 'surge whoami' prints when authenticated: the account, as {@code email - plan}
     * (e.g. {@code doug@donohoe.info - Student}). Anchored to a whole line and disallowing '/'
     * so it can't be satisfied by a path in the mount banner the wrapper prints.
     */
    private static final Pattern ACCOUNT_LINE =
            Pattern.compile("(?m)^\\s*[^\\s@/]+@[^\\s@/]+\\.[^\\s@/]+\\s*(-.*)?$");

    /** What 'surge login' prints once the browser sign-in completes. */
    private static final String LOGIN_SUCCESS = "Success - Logged in";

    @Override
    public String getSubCommand() { return "surge"; }

    @Override
    public boolean showsCompletionFeedback() { return true; }

    @Override
    public boolean isPublishTarget() { return true; }

    @Override
    protected List<FlagDef> subCommandFlagDefs(Site site) {
        return List.of(
            new FlagDef.ValidatedTextField("--domain", DOMAIN_PATTERN, EDITABLE, 300),
            new FlagDef.ChoiceField("export dir", false, ExportRunner.exportDirChoices(site), EDITABLE)
        );
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // Check logic - static and free of config/Swing/process so it can be unit tested
    // ──────────────────────────────────────────────────────────────────────────────

    /**
     * Classifies 'surge whoami'. Surge exits 0 whether it is authenticated, so the exit
     * code proves nothing on its own and the account line has to be matched positively - the old
     * "no 'not authenticated' in the output" test passed on any wording surge might change to.
     */
    static Prerequisite.Result checkAuth(String output, int exitCode) {
        String text = PhotosUtils.stripAnsi(output);
        if (Prerequisite.containsAny(text, NOT_AUTHENTICATED)) return FAILED;
        if (exitCode == 0 && ACCOUNT_LINE.matcher(text).find()) return PASSED;
        return ERROR;
    }

    /** Whether 'surge login' reported success. The exit code alone is not trusted. */
    static boolean checkLogin(String output, int exitCode) {
        return exitCode == 0 && Prerequisite.containsAny(PhotosUtils.stripAnsi(output), LOGIN_SUCCESS);
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // Prerequisites
    // ──────────────────────────────────────────────────────────────────────────────

    @Override
    public Prerequisite getPrerequisite(Site site, Map<String, String> userValues) {
        List<String> checkCmd = buildWrappedCommand(site, Map.of(), "surge", "account", "whoami");
        // --browser: sign in on a web page instead of answering email/password prompts, which
        // the app has no way to do. The ddphotos script (v1.33.3+) opens the page it prints.
        List<String> loginCmd = buildWrappedCommand(site, Map.of(), "surge", "login", "--browser");
        return new Prerequisite(checkCmd) {
            @Override
            public Result check(String output, int exitCode) {
                return checkAuth(output, exitCode);
            }

            @Override
            public Remediation remediation() {
                return new ConfirmThenRun(
                        "msg.surge.confirmLogin",
                        "msg.windowtitle.surgeLogin",
                        loginCmd);
            }

            @Override
            public boolean remediationSucceeded(String output, int exitCode) {
                return checkLogin(output, exitCode);
            }

            @Override
            public String checkingMessage() { return PropertyConfig.getMessage("msg.surge.checkingAuth"); }

            @Override
            public String failedMessage() { return PropertyConfig.getMessage("msg.surge.notAuthenticated"); }

            @Override
            public String errorDialogMessage(int exitCode) {
                return PropertyConfig.getMessage("msg.surge.authCheckError");
            }
        };
    }
}
