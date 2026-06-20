package com.donohoedigital.ddphotos.runner;

import com.donohoedigital.app.config.AppConfigUtils;
import com.donohoedigital.ddphotos.config.Site;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.donohoedigital.ddphotos.runner.FlagVisibility.VIEW_ONLY;

public abstract class DdphotosRunner extends CommandRunner {

    @Override
    public String getBinary() {
        return "ddphotos";
    }

    @Override
    public boolean isDockerRequired() { return true; }

    /** The ddphotos script is a bash script; on Windows it must run under Git Bash. */
    @Override
    protected boolean usesBashWrapper() { return true; }

    /**
     * True for the long-running, published-port subcommands (serve/run) whose container can be left
     * behind when the GUI stops the command. Those get a deterministic {@code --name} so stop()/kill()
     * can target the container directly. Short-lived commands (build/export/...) exit on their own.
     */
    protected boolean usesNamedContainer() { return false; }

    @Override
    protected String containerName(Site site, Map<String, String> userValues) {
        if (!usesNamedContainer()) return null;
        String id = site != null ? site.getIdOrDefault() : "";
        // Docker names must match [a-zA-Z0-9][a-zA-Z0-9_.-]*; sanitize the (user-controlled) site id.
        String safeId = id.replaceAll("[^A-Za-z0-9_.-]", "-");
        return "ddphotos-" + getSubCommand() + "-" + safeId;
    }

    /** Wrapper-level flags that appear before the subcommand name. */
    protected List<FlagDef> wrapperFlagDefs(Site site) {
        String dir = site.getDirPath() != null ? site.getDirPath() : "";
        List<FlagDef> defs = new ArrayList<>();
        defs.add(new FlagDef.FixedFlag("--dir", dir, VIEW_ONLY));
        if (site.hasCustomConfigPath()) {
            defs.add(new FlagDef.FixedFlag("--config-dir", site.getConfigPath(), VIEW_ONLY));
        }
        return defs;
    }

    /** Subcommand-specific flags that appear after the subcommand name. */
    protected abstract List<FlagDef> subCommandFlagDefs(Site site);

    @Override
    public List<FlagDef> getCommandFlagDefs(Site site) {
        return new ArrayList<>(wrapperFlagDefs(site));
    }

    @Override
    public List<FlagDef> getSubCommandFlagDefs(Site site) {
        return new ArrayList<>(subCommandFlagDefs(site));
    }

    @Override
    public List<String> buildCommand(Site site, Map<String, String> userValues) {
        List<String> cmd = baseCommand(site, userValues);
        if (getSubCommand() != null) cmd.add(getSubCommand());
        cmd.addAll(toArgs(subCommandFlagDefs(site), userValues));
        return cmd;
    }

    /**
     * Builds a wrapped ddphotos command with wrapper flags but an arbitrary set of trailing
     * sub-arguments — used by runners to construct prerequisite check and remediation commands.
     */
    protected List<String> buildWrappedCommand(Site site, Map<String, String> userValues,
                                               String... subArgs) {
        List<String> cmd = baseCommand(site, userValues);
        cmd.addAll(Arrays.asList(subArgs));
        return cmd;
    }

    protected List<String> baseCommand(Site site, Map<String, String> userValues) {
        List<String> cmd = new ArrayList<>();
        cmd.add(AppConfigUtils.getBinDir().toPath().resolve(getBinary()).toString());
        cmd.addAll(toArgs(wrapperFlagDefs(site), userValues));
        cmd.add("--non-interactive");
        cmd.add("--show-mounts");
        return cmd;
    }
}
