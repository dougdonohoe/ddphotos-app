package com.donohoedigital.ddphotos.runner;

import com.donohoedigital.config.PropertyConfig;
import com.donohoedigital.ddphotos.PhotosUtils;
import com.donohoedigital.ddphotos.config.Site;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static com.donohoedigital.ddphotos.runner.FlagVisibility.*;
import static com.donohoedigital.ddphotos.runner.Prerequisite.Result.*;

public class WranglerRunner extends DdphotosRunner {

    // Non-empty required: disables Run until the user fills it in
    private static final String PROJECT_NAME_PATTERN = "[a-zA-Z0-9-]+";

    /** Printed by 'wrangler whoami' for every authenticated variant (OAuth token, API token, ...). */
    private static final String LOGGED_IN = "You are logged in";

    /**
     * The two ways wrangler reports "log in first": never logged in (exits 0, on stdout), and a
     * token that expired or could not be refreshed (exits 1, on stderr). Both are the condition
     * the login remediation fixes, so neither can be keyed off the exit code.
     */
    private static final String[] NOT_LOGGED_IN = {
            "You are not authenticated",
            "Not logged in."
    };

    /** A row of 'pages project list --json': {@code "Project Name": "my-site"}. */
    private static final String PROJECT_NAME_KEY = "\"Project Name\"";

    /** 'pages project list --json' with no projects at all. */
    private static final Pattern EMPTY_JSON_LIST = Pattern.compile("(?m)^\\s*\\[\\s*]\\s*$");

    @Override
    public String getSubCommand() { return "wrangler"; }

    @Override
    public boolean showsCompletionFeedback() { return true; }

    @Override
    protected List<FlagDef> subCommandFlagDefs(Site site) {
        return List.of(
            new FlagDef.Constant("pages", VIEW_ONLY),
            new FlagDef.Constant("deploy", VIEW_ONLY),
            new FlagDef.ValidatedTextField("--project-name", PROJECT_NAME_PATTERN, EDITABLE, 300),
            new FlagDef.ChoiceField("export dir", false, ExportRunner.exportDirChoices(site), EDITABLE)
        );
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // Check logic - static and free of config/Swing/process so it can be unit tested
    // ──────────────────────────────────────────────────────────────────────────────

    /**
     * Classifies 'wrangler whoami'. The not-logged-in phrases are tested first because one of
     * them exits 1: keying off the exit code would turn "please log in" into an unhelpful error.
     */
    static Prerequisite.Result checkAuth(String output, int exitCode) {
        String text = PhotosUtils.stripAnsi(output);
        if (Prerequisite.containsAny(text, NOT_LOGGED_IN)) return FAILED;
        if (exitCode == 0 && Prerequisite.containsAny(text, LOGGED_IN)) return PASSED;
        return ERROR;
    }

    /**
     * Classifies 'wrangler pages project list --json'. Only a list we actually parsed can say a
     * project is missing - an errored or unrecognized response must not be reported as "not found",
     * since that offers to create a project that may well already exist.
     */
    static Prerequisite.Result checkProject(String output, int exitCode, String projectName) {
        if (projectName.isBlank() || exitCode != 0) return ERROR;
        String text = PhotosUtils.stripAnsi(output);
        if (matchesProject(text, projectName)) return PASSED;
        // A parsed list without our project - either other projects, or none at all.
        if (text.contains(PROJECT_NAME_KEY) || EMPTY_JSON_LIST.matcher(text).find()) return FAILED;
        return ERROR;
    }

    /** True if the JSON list holds a project named exactly {@code projectName}. */
    private static boolean matchesProject(String text, String projectName) {
        Pattern row = Pattern.compile(Pattern.quote(PROJECT_NAME_KEY) + "\\s*:\\s*\""
                                      + Pattern.quote(projectName) + "\"");
        return row.matcher(text).find();
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // Prerequisites
    // ──────────────────────────────────────────────────────────────────────────────

    @Override
    public Prerequisite getPrerequisite(Site site, Map<String, String> userValues) {
        String projectName = userValues.getOrDefault("--project-name", "").trim();

        List<String> whoamiCmd = buildWrappedCommand(site, Map.of(), "wrangler", "whoami");
        List<String> loginCmd  = buildWrappedCommand(site, Map.of(), "wrangler", "login");
        // --json: an exact, parseable answer instead of a colorized box-drawing table whose
        // column widths depend on the longest project name.
        List<String> listCmd   = buildWrappedCommand(site, Map.of(),
                "wrangler", "pages", "project", "list", "--json");
        List<String> createCmd = buildWrappedCommand(site, Map.of(),
                "wrangler", "pages", "project", "create", projectName,
                "--production-branch", "production");

        Prerequisite projectCheck = new Prerequisite(listCmd) {
            @Override
            public Result check(String output, int exitCode) {
                return checkProject(output, exitCode, projectName);
            }

            @Override
            public Remediation remediation() {
                return new ConfirmThenRun(
                        "msg.wrangler.confirmCreateProject",
                        "msg.windowtitle.wranglerCreateProject",
                        createCmd,
                        projectName);
            }

            @Override
            public String checkingMessage() {
                return PropertyConfig.getMessage("msg.wrangler.checkingProject", projectName);
            }

            @Override
            public String failedMessage() {
                return PropertyConfig.getMessage("msg.wrangler.projectNotFound", projectName);
            }

            @Override
            public String errorDialogMessage(int exitCode) {
                return PropertyConfig.getMessage("msg.wrangler.projectCheckError", projectName);
            }
        };

        return new Prerequisite(whoamiCmd) {
            @Override
            public Result check(String output, int exitCode) {
                return checkAuth(output, exitCode);
            }

            @Override
            public Remediation remediation() {
                return new ConfirmThenRun(
                        "msg.wrangler.confirmLogin",
                        "msg.windowtitle.wranglerLogin",
                        loginCmd);
            }

            @Override
            public String checkingMessage() { return PropertyConfig.getMessage("msg.wrangler.checkingAuth"); }

            @Override
            public String failedMessage() { return PropertyConfig.getMessage("msg.wrangler.notAuthenticated"); }

            @Override
            public String errorDialogMessage(int exitCode) {
                return PropertyConfig.getMessage("msg.wrangler.authCheckError");
            }

            @Override
            public Prerequisite next() { return projectCheck; }
        };
    }
}
