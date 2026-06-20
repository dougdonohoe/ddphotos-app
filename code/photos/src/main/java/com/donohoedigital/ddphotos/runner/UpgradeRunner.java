package com.donohoedigital.ddphotos.runner;

import com.donohoedigital.config.ConfigManager;
import com.donohoedigital.config.PropertyConfig;
import com.donohoedigital.ddphotos.config.Site;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

public class UpgradeRunner extends DdphotosRunner {

    private static final Logger logger = LogManager.getLogger(UpgradeRunner.class);

    @Override
    public String getSubCommand() { return "upgrade"; }

    @Override
    public boolean showsCompletionFeedback() { return true; }

    @Override
    protected List<FlagDef> subCommandFlagDefs(Site site) {
        return List.of();
    }

    /**
     * Before running 'ddphotos upgrade', check 'ddphotos version --image'. Only proceed with the
     * upgrade when an update is actually available; otherwise log a message and stop.
     */
    @Override
    public Prerequisite getPrerequisite(Site site, Map<String, String> userValues) {
        // 'ddphotos' writes a "last-check" file to skip update checks until it expires. Remove it
        // so the version check below always runs against the latest available image.
        removeLastCheckFile();

        List<String> versionCmd = buildWrappedCommand(site, Map.of(), "version", "--image");

        return new Prerequisite(versionCmd) {
            @Override
            public boolean passed(String output, int exitCode) {
                return output.contains("Update available");
            }

            @Override
            public Remediation remediation() {
                return new ShowMessage(PropertyConfig.getMessage("msg.upgrade.noUpgrade"));
            }

            @Override
            public String checkingMessage() {
                return PropertyConfig.getMessage("msg.upgrade.checking");
            }
        };
    }

    private void removeLastCheckFile() {
        File lastCheck = new File(ConfigManager.getUserHome(), "last-check");
        try {
            Files.deleteIfExists(lastCheck.toPath());
        } catch (IOException e) {
            // Non-fatal: if we can't remove it, the version check may use a cached result.
            logger.warn("Could not remove {}: {}", lastCheck, e.getMessage());
        }
    }
}
