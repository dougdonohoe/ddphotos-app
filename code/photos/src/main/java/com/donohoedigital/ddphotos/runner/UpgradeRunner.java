package com.donohoedigital.ddphotos.runner;

import com.donohoedigital.config.ConfigManager;
import com.donohoedigital.config.PropertyConfig;
import com.donohoedigital.ddphotos.PhotosUtils;
import com.donohoedigital.ddphotos.ScriptSync;
import com.donohoedigital.ddphotos.config.Site;
import com.donohoedigital.ddphotos.config.SitesFile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

public class UpgradeRunner extends DdphotosRunner {

    private static final Logger logger = LogManager.getLogger(UpgradeRunner.class);

    private final SitesFile sitesFile_;

    public UpgradeRunner(SitesFile sitesFile) {
        sitesFile_ = sitesFile;
    }

    @Override
    public String getSubCommand() { return "upgrade"; }

    @Override
    public boolean showsCompletionFeedback() { return true; }

    @Override
    protected List<FlagDef> subCommandFlagDefs(Site site) {
        return List.of();
    }

    /**
     * 'ddphotos upgrade' removes the current image, which docker refuses while any container created
     * from it is still running - so no other command may be running when the upgrade starts.
     */
    @Override
    public String busyMessage(String runningDisplayName) {
        return PropertyConfig.getMessage("msg.upgrade.busy", runningDisplayName);
    }

    @Override
    public String busyTitleKey() { return "msg.windowtitle.upgradeBusy"; }

    /**
     * Classifies 'ddphotos version --image'. "No upgrade available" is only a safe conclusion when
     * the version command actually reported a version - the 'Image:' line proves that, and without
     * it a failed check would otherwise be reported to the user as "you are up to date".
     */
    static Prerequisite.Result checkVersion(String output, int exitCode) {
        String text = PhotosUtils.stripAnsi(output);
        if (exitCode != 0) return Prerequisite.Result.ERROR;
        if (text.contains("Update available")) return Prerequisite.Result.PASSED;
        if (text.contains("Image:")) return Prerequisite.Result.FAILED;
        return Prerequisite.Result.ERROR;
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
            public Result check(String output, int exitCode) {
                return checkVersion(output, exitCode);
            }

            @Override
            public Remediation remediation() {
                return new ShowMessage(PropertyConfig.getMessage("msg.upgrade.noUpgrade"));
            }

            @Override
            public String checkingMessage() {
                return PropertyConfig.getMessage("msg.upgrade.checking");
            }

            @Override
            public String errorDialogMessage(int exitCode) {
                return PropertyConfig.getMessage("msg.upgrade.checkError");
            }
        };
    }

    /**
     * An upgrade rewrites only the app's own copy of the {@code ddphotos} script, leaving the copy
     * {@code ddphotos init} put in each site directory on the old release - so catch them up here,
     * once the run is over.  Unconditional: this is also where a site added from a directory the
     * app never initialized, or one left behind by an upgrade that failed part way, gets its
     * current copy, and there is nothing to do when they are all in step anyway (see
     * {@link ScriptSync}, which compares before it writes).
     */
    @Override
    public void afterRun() {
        ScriptSync.syncSites(sitesFile_.getSites());
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
