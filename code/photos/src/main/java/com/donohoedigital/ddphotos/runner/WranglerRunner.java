package com.donohoedigital.ddphotos.runner;

import com.donohoedigital.config.PropertyConfig;
import com.donohoedigital.ddphotos.config.Site;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.donohoedigital.ddphotos.runner.FlagVisibility.*;

public class WranglerRunner extends DdphotosRunner {

    // Non-empty required: disables Run until the user fills it in
    private static final String PROJECT_NAME_PATTERN = "[a-zA-Z0-9-]+";

    @Override
    public String getSubCommand() { return "wrangler"; }

    @Override
    public boolean showsCompletionFeedback() { return true; }

    @Override
    protected List<FlagDef> subCommandFlagDefs(Site site) {
        return List.of(
            new FlagDef.Constant("pages", VIEW_ONLY),
            new FlagDef.Constant("deploy", VIEW_ONLY),
            new FlagDef.ValidatedTextField("--project-name", PROJECT_NAME_PATTERN, EDITABLE, 25),
            new FlagDef.ChoiceField("export dir", false, ExportRunner.exportDirChoices(site), EDITABLE)
        );
    }

    @Override
    public Prerequisite getPrerequisite(Site site, Map<String, String> userValues) {
        String projectName = userValues.getOrDefault("--project-name", "").trim();

        List<String> whoamiCmd = buildWrappedCommand(site, Map.of(), "wrangler", "whoami");
        List<String> loginCmd  = buildWrappedCommand(site, Map.of(), "wrangler", "login");
        List<String> listCmd   = buildWrappedCommand(site, Map.of(), "wrangler", "pages", "project", "list");
        List<String> createCmd = buildWrappedCommand(site, Map.of(),
                "wrangler", "pages", "project", "create", projectName,
                "--production-branch", "production");

        Prerequisite projectCheck = new Prerequisite(listCmd) {
            @Override
            public boolean passed(String output, int exitCode) {
                if (exitCode != 0 || projectName.isBlank()) return false;
                return Arrays.stream(output.split("\n"))
                             .anyMatch(line -> {
                                 String[] cells = line.split("│"); // │ U+2502
                                 return cells.length > 1 && cells[1].trim().equals(projectName);
                             });
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
        };

        return new Prerequisite(whoamiCmd) {
            @Override
            public boolean passed(String output, int exitCode) {
                return output.contains("You are logged in");
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
            public Prerequisite next() { return projectCheck; }
        };
    }
}
