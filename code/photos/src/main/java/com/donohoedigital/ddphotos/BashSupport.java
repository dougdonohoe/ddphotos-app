package com.donohoedigital.ddphotos;

import com.donohoedigital.gui.DDOption;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Windows-only support for running the (bash) {@code ddphotos} script. Windows cannot execute a
 * shebang script directly, so on Windows every ddphotos invocation is run by Git Bash:
 * {@code <bash.exe> <script> <args...>}. We launch {@code bash.exe} directly rather than through
 * the {@code ddphotos.cmd} wrapper because {@code cmd /c} mangles the command line when more than
 * one argument is quoted (e.g. a script path under a user home containing a space plus a
 * directory argument containing a space), and would also interpret {@code & | ^ %} in arguments.
 * <p>
 * Mirrors the binary-location half of {@link DockerStatus}: the wizard pre-populates its UI
 * field from {@link #findBashBinary()} and stores the chosen path in prefs; runtime invocations
 * use {@link #bashPath()} / {@link #wrap}.
 */
public final class BashSupport {

    /** Prefs key (under {@link PhotosConstants#PREFS_NODE_APP}) where the bash binary path is stored. */
    public static final String PREFS_KEY_BASH_BINARY = "wizard.bashbinary";

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
     * Returns the Git Bash binary used to run the ddphotos script: the path stored in prefs by the
     * wizard UI field, or {@code ""} when none is stored. There is deliberately no bare
     * {@code "bash.exe"} fallback: Windows resolves a bare name via System32 before PATH, which
     * finds WSL's {@code bash.exe} rather than Git Bash. A blank path fails the setup-complete
     * check, so the wizard runs again before any command can launch.
     */
    public static String bashPath() {
        return DDOption.getOptionPrefs(PhotosConstants.PREFS_NODE_APP)
                       .get(PREFS_KEY_BASH_BINARY, "").trim();
    }

    /**
     * Wraps a command whose first element is the ddphotos script path so it runs under Git Bash:
     * {@code [<bash.exe>, <script>, <args...>]}. The script path and arguments are passed through
     * untouched.
     */
    public static List<String> wrap(List<String> cmd) {
        List<String> wrapped = new ArrayList<>(cmd.size() + 1);
        wrapped.add(bashPath());
        wrapped.addAll(cmd);
        return wrapped;
    }
}
