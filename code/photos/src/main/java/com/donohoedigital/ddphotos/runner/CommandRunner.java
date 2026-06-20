package com.donohoedigital.ddphotos.runner;

import com.donohoedigital.base.Utils;
import com.donohoedigital.config.PropertyConfig;
import com.donohoedigital.ddphotos.BashSupport;
import com.donohoedigital.ddphotos.DockerStatus;
import com.donohoedigital.ddphotos.config.Site;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class CommandRunner {

    private static final Logger logger = LogManager.getLogger(CommandRunner.class);

    /** Env var the {@code ddphotos} script reads to give its container a deterministic --name. */
    public static final String ENV_CONTAINER_NAME = "DDPHOTOS_CONTAINER_NAME";

    /** Name of the container started by the live process, captured at {@link #launch}; null if none. */
    private volatile String activeContainerName_;

    public abstract String getBinary();
    public abstract String getSubCommand();

    /** All flags for this command (including VIEW_ONLY and HIDDEN), computed from site context. */
    public abstract List<FlagDef> getCommandFlagDefs(Site site);

    /** All flags for this sub-command (including VIEW_ONLY and HIDDEN), computed from site context. */
    public abstract List<FlagDef> getSubCommandFlagDefs(Site site);

    /** Full command array to pass to ProcessBuilder. */
    public abstract List<String> buildCommand(Site site, Map<String, String> userValues);

    public String getDisplayName() {
        return getSubCommand() != null ? getBinary() + " " + getSubCommand() : getBinary();
    }

    public String getPrefsKey(boolean bIncludeSubCommand) {
        return bIncludeSubCommand && getSubCommand() != null ? getBinary() + "." + getSubCommand() : getBinary();
    }

    /** Return true if Docker must be running before this command can be launched. */
    public boolean isDockerRequired() { return false; }

    /** Return true to show a completion feedback dialog after success or failure. */
    public boolean showsCompletionFeedback() { return false; }

    /** Return true to show a success dialog on exit code 0. Defaults to showsCompletionFeedback(). */
    public boolean showsSuccessFeedback() { return showsCompletionFeedback(); }

    /** Return true to show a failure dialog on non-zero, non-stop exit codes. Defaults to showsCompletionFeedback(). */
    public boolean showsFailureFeedback() { return showsCompletionFeedback(); }

    /** Return a prerequisite check to run before the main command, or null if none. */
    public Prerequisite getPrerequisite(Site site, Map<String, String> userValues) { return null; }

    public Process launch(Site site, Map<String, String> userValues) throws IOException {
        // Capture the container name (if any) so stop()/kill() can target it directly - reading
        // process command lines to find the container is unreliable (empty on Windows).
        activeContainerName_ = containerName(site, userValues);
        return launchCommand(finalCommand(buildCommand(site, userValues)), activeContainerName_);
    }

    /**
     * The docker container that {@link #launch} starts and that stop()/kill() must tear down, or
     * null if this runner starts no long-lived container we manage. When non-null, the name is
     * passed to the {@code ddphotos} script via {@link #ENV_CONTAINER_NAME} so the script applies it
     * as {@code --name}. Only the long-running, published-port commands (serve/run) override this.
     */
    protected String containerName(Site site, Map<String, String> userValues) { return null; }

    /**
     * Start a command exactly as given, with this runner's environment applied. Used for the main
     * command as well as prerequisite checks and remediation commands, so they all get the same
     * PATH/env setup (e.g. docker on PATH) - see {@link #configureEnvironment}. The command must
     * already be in final form (see {@link #finalCommand}); this method does not transform it, so
     * the displayed "Running:" line and what executes are guaranteed to match.
     */
    public Process launchCommand(List<String> cmd) throws IOException {
        return launchCommand(cmd, null);
    }

    private Process launchCommand(List<String> cmd, String containerName) throws IOException {
        logger.info("launch: {}", String.join(" ", cmd));
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(false);
        configureEnvironment(pb);
        // The ddphotos script names its container with this so stop()/kill() can target it.
        if (containerName != null) pb.environment().put(ENV_CONTAINER_NAME, containerName);
        // The ddphotos.cmd wrapper reads the confirmed Git Bash location from this env var.
        if (Utils.ISWINDOWS && usesBashWrapper()) BashSupport.applyBashEnv(pb);
        return pb.start();
    }

    /**
     * Resolves a freshly-built command to the form it must actually run in. On Windows the ddphotos
     * script is a bash script and can't be run directly, so it is wrapped to run through its
     * companion {@code ddphotos.cmd} under {@code cmd /c} (only ddphotos-script runners opt in via
     * {@link #usesBashWrapper()} - docker invocations stay native). Callers resolve a raw command
     * here once, then pass the result to both the "Running:" echo and {@link #launchCommand}.
     *
     * <p>Not idempotent: pass a freshly built (raw) command, never an already-resolved one.
     */
    public List<String> finalCommand(List<String> cmd) {
        if (Utils.ISWINDOWS && usesBashWrapper()) {
            return BashSupport.wrap(cmd);
        }
        return cmd;
    }

    /**
     * Windows only: when true, the command (whose first element is the ddphotos script path) is run
     * through the {@code ddphotos.cmd} wrapper via {@link BashSupport#wrap}, with the Git Bash
     * location supplied through {@link BashSupport#applyBashEnv}. Default false; overridden by
     * {@link DdphotosRunner}. Runners that shell out to docker directly leave this off.
     */
    protected boolean usesBashWrapper() { return false; }

    /**
     * Hook for subclasses to modify the process environment before launch. The default applies the
     * docker PATH (see {@link DockerStatus#applyDockerPath}); every runner here ultimately shells out
     * to docker, so they all need it. Override if a runner needs different handling.
     */
    protected void configureEnvironment(ProcessBuilder pb) {
        DockerStatus.applyDockerPath(pb);
    }

    public void stop(Process process) {
        stop(process, null);
    }

    /**
     * Graceful stop. If this runner started a named container, ask docker to stop it first: that
     * lets the container shut down cleanly, {@code --rm} remove it, and the attached client exit on
     * its own. Force-killing the local client (as kill() does) would instead orphan the container,
     * since the docker daemon - not the client process - owns its lifecycle. The local process tree
     * is then signaled as a backstop. Container teardown runs off the calling (EDT) thread because
     * {@code docker stop} blocks for the container's stop grace period (up to ~10s).
     */
    public void stop(Process process, OutputSink sink) {
        teardown(process, false, sink);
    }

    public void kill(Process process, OutputSink sink) {
        teardown(process, true, sink);
    }

    private void teardown(Process process, boolean force, OutputSink sink) {
        if (process == null || !process.isAlive()) return;
        String verb = force ? "kill" : "stop";
        String container = activeContainerName_;
        logger.info("{}: root pid={} container={}", verb, process.pid(),
                container != null ? container : "(none)");
        if (container != null) {
            // Stop/kill the container, then the now-detachable local process tree, off-thread.
            Thread t = new Thread(() -> {
                stopContainer(container, force, sink);
                killProcessTree(process, force);
            }, "container-teardown");
            t.setDaemon(true);
            t.start();
        } else {
            killProcessTree(process, force);
        }
    }

    private void killProcessTree(Process process, boolean force) {
        String verb = force ? "kill" : "stop";
        // Materialize the full tree before signaling anything; reverse so leaves die first,
        // preventing grandchildren from being orphaned before they're signaled.
        List<ProcessHandle> descendants = process.descendants().collect(Collectors.toList());
        logger.info("{}: {} descendant(s) found", verb, descendants.size());
        Collections.reverse(descendants);
        for (ProcessHandle h : descendants) {
            boolean sent = force ? h.destroyForcibly() : h.destroy();
            logger.info("{}: signal pid={} accepted={} stillAlive={}", verb, h.pid(), sent, h.isAlive());
        }
        if (force) process.destroyForcibly(); else process.destroy();
        logger.info("{}: root stillAlive={}", verb, process.isAlive());
    }

    /** Tell docker to stop ({@code docker stop}) or force-kill ({@code docker kill}) the container. */
    private void stopContainer(String name, boolean force, OutputSink sink) {
        String docker = DockerStatus.dockerPath();
        String verb = force ? "kill" : "stop";
        try {
            if (sink != null) sink.system(PropertyConfig.getMessage("msg.cmd.running", docker + " " + verb + " " + name));
            Process p = DockerStatus.dockerProcessBuilder(verb, name)
                    .redirectErrorStream(true)
                    .start();
            String out = new String(p.getInputStream().readAllBytes());
            p.waitFor();
            logger.info("{}: docker {} {} exit={}", verb, verb, name, p.exitValue());
            // docker echoes the container name on success, or "No such container" if already gone.
            if (sink != null && !out.isBlank()) sink.output(out);
        } catch (Exception e) {
            logger.info("{}: docker error for {}: {}", verb, name, e.getMessage());
            if (sink != null) sink.error(PropertyConfig.getMessage("msg.cmd.containerStopError", name, e.getMessage()));
        }
    }

    /** Exit codes produced by stop()/kill() (SIGTERM/SIGKILL) — not a real command failure. */
    private static final Set<Integer> USER_STOP_EXIT_CODES = Set.of(137, 143);

    public static boolean isUserStopCode(int exitCode) {
        return USER_STOP_EXIT_CODES.contains(exitCode);
    }

    /** Sink for surfacing stop/kill-path activity to the UI console. Methods may be called off-EDT. */
    public interface OutputSink {
        void system(String line);   // green informational / echoed command
        void output(String text);   // raw command output (caller appends newline as needed)
        void error(String line);    // red — e.g. container failed to stop
    }

    protected List<String> toArgs(List<FlagDef> defs, Map<String, String> userValues) {
        List<String> args = new ArrayList<>();
        for (FlagDef def : defs) {
            // don't call def.defaultValue() unless needed
            String value = userValues == null ? null : userValues.getOrDefault(def.name(), null);
            args.addAll(def.toArgs(value));
        }
        return args;
    }
}
