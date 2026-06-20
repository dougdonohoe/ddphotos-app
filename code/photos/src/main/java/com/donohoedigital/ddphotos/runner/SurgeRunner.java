package com.donohoedigital.ddphotos.runner;

import com.donohoedigital.config.PropertyConfig;
import com.donohoedigital.ddphotos.PhotosUtils;
import com.donohoedigital.ddphotos.config.Site;

import java.util.List;
import java.util.Map;

import static com.donohoedigital.ddphotos.runner.FlagVisibility.EDITABLE;

public class SurgeRunner extends DdphotosRunner {

    // Non-empty required: disables Run until the user fills it in
    // domain like my-site.surge.sh or any hostname
    private static final String DOMAIN_PATTERN = "[a-zA-Z0-9.-]+";

    @Override
    public String getSubCommand() { return "surge"; }

    @Override
    public boolean showsCompletionFeedback() { return true; }

    @Override
    protected List<FlagDef> subCommandFlagDefs(Site site) {
        return List.of(
            new FlagDef.ValidatedTextField("--domain", DOMAIN_PATTERN, EDITABLE, 25),
            new FlagDef.ChoiceField("export dir", false, ExportRunner.exportDirChoices(site), EDITABLE)
        );
    }

    @Override
    public Prerequisite getPrerequisite(Site site, Map<String, String> userValues) {
        List<String> checkCmd = buildWrappedCommand(site, Map.of(), "surge", "whoami");
        String notAuthMsg = PropertyConfig.getMessage("msg.surge.notAuthenticated",
                PhotosUtils.scriptPath());
        return new Prerequisite(checkCmd) {
            @Override
            public boolean passed(String output, int exitCode) {
                return exitCode == 0 && !output.toLowerCase().contains("not authenticated");
            }

            @Override
            public Remediation remediation() {
                return new ShowDialog(notAuthMsg, "msg.windowtitle.surgeLogin");
            }

            @Override
            public String checkingMessage() { return PropertyConfig.getMessage("msg.surge.checkingAuth"); }
        };
    }
}
