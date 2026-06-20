package com.donohoedigital.ddphotos.runner;

import com.donohoedigital.base.Utils;
import com.donohoedigital.config.DebugConfig;
import com.donohoedigital.ddphotos.DockerStatus;
import com.donohoedigital.ddphotos.config.Site;
import com.donohoedigital.app.config.AppConfigUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.donohoedigital.ddphotos.runner.FlagVisibility.*;

public class InstallScriptRunner extends CommandRunner {

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

    @Override
    public List<FlagDef> getCommandFlagDefs(Site s) { return List.of(); }

    @Override
    public List<FlagDef> getSubCommandFlagDefs(Site s) {
        boolean debug = DebugConfig.TESTING("settings.debug.local.image");
        String binDir = AppConfigUtils.getBinDir().toString();
        List<FlagDef> defs = new ArrayList<>(List.of(
                new FlagDef.Constant("--rm", VIEW_ONLY),
                new FlagDef.Constant("--pull", VIEW_ONLY),
                new FlagDef.Constant(debug ? "never" : "always", VIEW_ONLY),
                new FlagDef.FixedFlag("-v", binDir + ":/ddphotos", VIEW_ONLY),
                new FlagDef.FixedField("image", debug ? "ddphotos" : "dougdonohoe/ddphotos:latest", VIEW_ONLY),
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
