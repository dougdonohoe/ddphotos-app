package com.donohoedigital.ddphotos;

import com.donohoedigital.base.Utils;
import com.donohoedigital.gui.DDOption;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

public class DockerStatus {

    private static final Logger logger = LogManager.getLogger(DockerStatus.class);
    private static final int POLL_INTERVAL_MS = 10_000;

    /** Prefs key (under {@link PhotosConstants#PREFS_NODE_APP}) where the docker binary path is stored. */
    public static final String PREFS_KEY_DOCKER_BINARY = "wizard.dockerbinary";

    public interface Listener {
        void onDockerStatusChanged(boolean running);
    }

    private static volatile boolean running_ = false;
    private static volatile boolean initialized_ = false;

    private static final List<Listener> listeners_ = new CopyOnWriteArrayList<>();

    private DockerStatus() {}

    public static void start() {
        checkAsync();
        Timer timer = new Timer(POLL_INTERVAL_MS, _ -> checkAsync());
        timer.start();
    }

    public static boolean isDockerRunning() {
        return running_;
    }

    public static void addListener(Listener l) {
        listeners_.add(l);
    }

    public static void removeListener(Listener l) {
        listeners_.remove(l);
    }

    /**
     * Searches well-known system locations for an executable docker binary and returns the first
     * path found, or {@code ""} if none is found. Used only to pre-populate the wizard UI field.
     * Does not consult prefs.
     */
    public static String findDockerBinary() {
        List<String> candidates;
        if (Utils.ISMAC) {
            String home = System.getProperty("user.home");
            candidates = List.of(
                    "/usr/local/bin/docker",
                    "/usr/bin/docker",
                    home + "/.docker/bin/docker",
                    "/Applications/Docker.app/Contents/Resources/bin/docker"
            );
        } else if (Utils.ISLINUX) {
            candidates = List.of(
                    "/usr/bin/docker",
                    "/usr/local/bin/docker",
                    "/snap/bin/docker"
            );
        } else if (Utils.ISWINDOWS) {
            candidates = List.of(
                    // Docker Desktop native install location
                    "C:\\Program Files\\Docker\\Docker\\resources\\bin\\docker.exe"
            );
        } else {
            candidates = List.of();
        }
        for (String path : candidates) {
            if (Files.isExecutable(Path.of(path))) {
                return path;
            }
        }
        return "";
    }

    /**
     * Returns the docker binary to pass to {@link ProcessBuilder}: the path stored in prefs by
     * the wizard UI field, or {@code "docker"} as a PATH fallback when prefs is empty.
     * Always call this — not {@link #findDockerBinary()} — when actually running docker.
     */
    public static String dockerPath() {
        String stored = DDOption.getOptionPrefs(PhotosConstants.PREFS_NODE_APP)
                                .get(PREFS_KEY_DOCKER_BINARY, "").trim();
        return stored.isBlank() ? (Utils.ISWINDOWS ? "docker.exe" : "docker") : stored;
    }

    /** Minimal PATH a Mac .app gets from LaunchServices when launched from Finder. */
    private static final String MAC_MINIMAL_PATH = "/usr/bin:/bin:/usr/sbin:/sbin";

    /**
     * Build a {@link ProcessBuilder} that invokes the docker binary ({@link #dockerPath()}) with the
     * given arguments and the docker PATH applied (see {@link #applyDockerPath}). Use this for every
     * docker invocation so they all resolve docker and its credential helpers consistently.
     */
    public static ProcessBuilder dockerProcessBuilder(String... args) {
        List<String> cmd = new ArrayList<>();
        cmd.add(dockerPath());
        cmd.addAll(Arrays.asList(args));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        applyDockerPath(pb);
        return pb;
    }

    /**
     * Prepend docker's directory to PATH so the 'ddphotos' script and docker client (and its credential
     * helpers like docker-credential-desktop, which live alongside the docker binary) can be found when
     * launched outside a terminal.
     *
     * On macOS, we deliberately do NOT inherit the dev shell's PATH - we replace it with the minimal PATH
     * LaunchServices hands a Finder-launched .app. Inheriting masks bugs where something depends on a
     * directory that happens to be on PATH in a terminal but won't be in the packaged app.
     *
     * On other platforms (Linux, Windows/WSL) we keep the inherited PATH. The minimal-PATH trick is
     * Mac-specific: there is no LaunchServices, and on WSL Docker Desktop injects the cli-tools dir
     * (holding docker-credential-desktop.exe) into PATH - that dir is NOT alongside the docker binary,
     * so stripping PATH would resurrect the very credential-helper-not-found failure we are avoiding.
     */
    public static void applyDockerPath(ProcessBuilder pb) {
        Path dockerDir = Path.of(dockerPath()).getParent();
        if (dockerDir == null) return;
        Map<String, String> env = pb.environment();
        String base = Utils.ISMAC ? MAC_MINIMAL_PATH : env.getOrDefault("PATH", MAC_MINIMAL_PATH);
        env.put("PATH", dockerDir + File.pathSeparator + base);
    }

    /** Called when the user changes the docker binary path in the wizard UI; triggers an immediate re-probe. */
    public static void onBinaryChanged() {
        checkAsync();
    }

    private static void checkAsync() {
        Thread.ofVirtual().start(() -> {
            boolean r = probe();
            SwingUtilities.invokeLater(() -> updateStatus(r));
        });
    }

    private static void updateStatus(boolean r) {
        boolean changed = !initialized_ || r != running_;
        running_ = r;
        initialized_ = true;
        if (changed) {
            listeners_.forEach(l -> l.onDockerStatusChanged(r));
        }
    }

    private static boolean probe() {
        try {
            ProcessBuilder pb = dockerProcessBuilder("info");
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            Process p = pb.start();
            return p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            logger.debug("Docker check failed: {}", e.getMessage());
            return false;
        }
    }
}
