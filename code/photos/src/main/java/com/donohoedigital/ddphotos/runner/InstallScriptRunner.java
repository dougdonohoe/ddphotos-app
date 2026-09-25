package com.donohoedigital.ddphotos.runner;

import com.donohoedigital.base.Utils;
import com.donohoedigital.config.DebugConfig;
import com.donohoedigital.config.PropertyConfig;
import com.donohoedigital.ddphotos.DockerStatus;
import com.donohoedigital.ddphotos.config.Site;
import com.donohoedigital.app.config.AppConfigUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.donohoedigital.ddphotos.runner.FlagVisibility.VIEW_ONLY;

public class InstallScriptRunner extends CommandRunner {

    private static final String RELEASE_IMAGE = "dougdonohoe/ddphotos:latest";
    private static final String LOCAL_IMAGE = "ddphotos";

    private static boolean useLocalImage() { return DebugConfig.TESTING("settings.debug.local.image"); }

    @Override
    public String getBinary() { return "docker"; }

    @Override
    public String getSubCommand() { return "run"; }

    @Override
    public List<String> buildCommand(Site ignored, Map<String, String> ignored2) {
        String docker = DockerStatus.dockerPath();
        List<String> cmd = new java.util.ArrayList<>(List.of(docker, getSubCommand()));
        cmd.addAll(toArgs(getSubCommandFlagDefs(null), null));
        return cmd;
    }

    /**
     * Pull the image with 'docker pull' before running it. 'docker run --pull always' would do the
     * same, but it writes the pull progress to stderr, which the console shows in red and users
     * read as an error. 'docker pull' writes it to stdout. None when testing a local image,
     * which is never pulled.
     */
    @Override
    public Prerequisite getPrerequisite(Site site, Map<String, String> userValues) {
        if (useLocalImage()) return null;

        List<String> pullCmd = List.of(DockerStatus.dockerPath(), "pull", RELEASE_IMAGE);
        return new Prerequisite(pullCmd) {
            @Override
            public Result check(String output, int exitCode) {
                return exitCode == 0 ? Result.PASSED : Result.ERROR;
            }

            // Unreachable: check() never returns FAILED.
            @Override
            public Remediation remediation() {
                return new ShowMessage(failedMessage());
            }

            @Override
            public String checkingMessage() {
                return PropertyConfig.getMessage("msg.wizard.script.pulling");
            }
        };
    }

    @Override
    public List<FlagDef> getCommandFlagDefs(Site s) { return List.of(); }

    @Override
    public List<FlagDef> getSubCommandFlagDefs(Site s) {
        String binDir = AppConfigUtils.getBinDir().toString();
        List<FlagDef> defs = new ArrayList<>(List.of(
                new FlagDef.Constant("--rm", VIEW_ONLY),
                new FlagDef.Constant("--pull", VIEW_ONLY),
                // the prerequisite has already pulled the image
                new FlagDef.Constant("never", VIEW_ONLY),
                new FlagDef.FixedFlag("-v", binDir + ":/ddphotos", VIEW_ONLY),
                new FlagDef.FixedField("image", useLocalImage() ? LOCAL_IMAGE : RELEASE_IMAGE, VIEW_ONLY),
                new FlagDef.Constant("init", VIEW_ONLY),
                new FlagDef.Constant("--script-only", VIEW_ONLY)
        ));
        // On Windows, ask the image to also install the ddphotos.cmd wrapper.
        if (Utils.ISWINDOWS) {
            defs.add(new FlagDef.Constant("--windows", VIEW_ONLY));
        }
        return defs;
    }
}
