package com.donohoedigital.ddphotos;

import com.donohoedigital.gui.DDOption;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Windows-only support for running the (bash) {@code ddphotos} script. Windows cannot execute a
 * shebang script directly, so on Windows every ddphotos invocation is launched through the
 * companion {@code ddphotos.cmd} wrapper installed alongside the script:
 * {@code cmd /c <script>.cmd <args...>}. The wrapper finds the Git Bash to run the script with via
 * the {@link #ENV_DDPHOTOS_BASH} environment variable, which we set to the path located here.
 * <p>
 * Mirrors the binary-location half of {@link DockerStatus}: the wizard pre-populates its UI
 * field from {@link #findBashBinary()} and stores the chosen path in prefs; runtime invocations
 * use {@link #bashPath()} / {@link #wrap} / {@link #applyBashEnv}.
 */
public class BashSupport {

    /** Prefs key (under {@link PhotosConstants#PREFS_NODE_APP}) where the bash binary path is stored. */
    public static final String PREFS_KEY_BASH_BINARY = "wizard.bashbinary";

    /** Environment variable the {@code ddphotos.cmd} wrapper reads to locate {@code bash.exe}. */
    public static final String ENV_DDPHOTOS_BASH = "DDPHOTOS_BASH";

    private BashSupport() {}

    /**
     * Searches well-known Git for Windows locations for an executable {@code bash.exe} and returns
     * the first path found, or {@code ""} if none is found. Used only to pre-populate the wizard UI
     * field. Does not consult prefs.
     */
    public static String findBashBinary() {
        List<String> candidates = new ArrayList<>();
        candidates.add("C:\\Program Files\\Git\\bin\\bash.exe");
        candidates.add("C:\\Program Files (x86)\\Git\\bin\\bash.exe");
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && !localAppData.isBlank()) {
            // Per-user Git install (default for non-admin installs)
            candidates.add(localAppData + "\\Programs\\Git\\bin\\bash.exe");
        }
        for (String path : candidates) {
            if (Files.isExecutable(Path.of(path))) {
                return path;
            }
        }
        return "";
    }

    /**
     * Returns the bash binary the {@code ddphotos.cmd} wrapper should use, passed via
     * {@link #ENV_DDPHOTOS_BASH}: the path stored in prefs by the wizard UI field, or
     * {@code "bash.exe"} as a PATH fallback when prefs is empty.
     */
    public static String bashPath() {
        String stored = DDOption.getOptionPrefs(PhotosConstants.PREFS_NODE_APP)
                                .get(PREFS_KEY_BASH_BINARY, "").trim();
        return stored.isBlank() ? "bash.exe" : stored;
    }

    /**
     * Wraps a command whose first element is the ddphotos script path so it runs through the
     * companion {@code ddphotos.cmd} wrapper under {@code cmd}:
     * {@code [cmd, /c, <script>.cmd, <args...>]}. The {@code .cmd} extension is appended to the
     * script path (the wrapper lives next to the script); the remaining arguments are passed
     * through untouched. The Git Bash location is supplied separately via {@link #applyBashEnv}.
     */
    public static List<String> wrap(List<String> cmd) {
        List<String> wrapped = new ArrayList<>(cmd.size() + 2);
        wrapped.add("cmd");
        wrapped.add("/c");
        wrapped.add(cmd.getFirst() + ".cmd");
        wrapped.addAll(cmd.subList(1, cmd.size()));
        return wrapped;
    }

    /** Sets {@link #ENV_DDPHOTOS_BASH} on the process so {@code ddphotos.cmd} can find Git Bash. */
    public static void applyBashEnv(ProcessBuilder pb) {
        pb.environment().put(ENV_DDPHOTOS_BASH, bashPath());
    }
}
