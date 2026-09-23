package com.donohoedigital.ddphotos.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * In-memory model of a site's {@code immich.env}: the Immich instance URL and API key photogen
 * uses to sync albums (ddphotos {@code docs/CONFIGURATION.md}, "Immich credentials").  It lives
 * in the config dir beside {@code albums.yaml} and is read by photogen only when an album names
 * the {@code immich} provider.
 *
 * <p>Parsing follows photogen's {@code ParseEnvFile}: lines are trimmed, blank and {@code #}
 * lines are skipped, a line splits on its first {@code =}, and one pair of surrounding quotes is
 * stripped from the value.  Saving rewrites only the two keys' lines, so comments and any other
 * variables survive; a key with no line yet is appended.
 *
 * <p>A file this class creates is readable by its owner only, because it holds a secret.  An
 * existing file keeps its permissions ({@link AtomicWrite} carries them over).
 */
public class ImmichCredentialsFile extends ConfigFile {

    private static final Logger logger = LogManager.getLogger(ImmichCredentialsFile.class);

    public static final String FILE_NAME = "immich.env";
    public static final String KEY_API_KEY = "IMMICH_API_KEY";
    public static final String KEY_URL     = "IMMICH_INSTANCE_URL";

    private final Path path_;
    private boolean existed_;
    private final List<String> lines_ = new ArrayList<>();
    private String apiKey_;
    private String url_;

    /** @param path the {@code immich.env} file, which may or may not exist yet. */
    public ImmichCredentialsFile(Path path) {
        this.path_ = path;
    }

    public ImmichCredentialsFile load() {
        existed_ = false;
        lines_.clear();
        apiKey_ = url_ = null;
        restamp();
        if (!Files.exists(path_)) return this;
        try {
            String content = Files.readString(path_, StandardCharsets.UTF_8);
            existed_ = true;
            lines_.addAll(content.lines().toList());
            for (String line : lines_) {
                String[] kv = parse(line);
                if (kv == null) continue;
                if (KEY_API_KEY.equals(kv[0])) apiKey_ = kv[1];
                else if (KEY_URL.equals(kv[0])) url_ = kv[1];
            }
            logger.info("read {}", path_);
        } catch (IOException e) {
            logger.warn("Failed to load {}: {}", path_, FileErrors.reason(e));
        }
        return this;
    }

    /**
     * Writes the file, creating it if needed.  Only the lines holding the two keys change;
     * a blank value writes an empty assignment rather than dropping the line.
     */
    public void save() throws IOException {
        boolean sawKey = false, sawUrl = false;
        List<String> out = new ArrayList<>(lines_.size() + 2);
        for (String line : lines_) {
            String[] kv = parse(line);
            if (kv != null && KEY_API_KEY.equals(kv[0])) {
                if (!sawKey) out.add(KEY_API_KEY + "=" + nvl(apiKey_));
                sawKey = true;
            } else if (kv != null && KEY_URL.equals(kv[0])) {
                if (!sawUrl) out.add(KEY_URL + "=" + nvl(url_));
                sawUrl = true;
            } else {
                out.add(line);
            }
        }
        if (!sawKey) out.add(KEY_API_KEY + "=" + nvl(apiKey_));
        if (!sawUrl) out.add(KEY_URL + "=" + nvl(url_));

        if (path_.getParent() != null) Files.createDirectories(path_.getParent());
        AtomicWrite.writeStringOwnerOnly(path_, String.join("\n", out) + "\n");
        logger.info("save {}", path_);

        lines_.clear();
        lines_.addAll(out);
        existed_ = true;
        restamp();
    }

    @Override
    public Path getPath() { return path_; }

    public boolean existsOnDisk() { return existed_; }

    public String getApiKey() { return apiKey_; }
    public void setApiKey(String apiKey) { apiKey_ = trimToNull(apiKey); }

    public String getUrl() { return url_; }
    public void setUrl(String url) { url_ = trimToNull(url); }

    /** True when both values are present, which is what photogen needs to sync. */
    public boolean isComplete() {
        return apiKey_ != null && url_ != null;
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    /** Returns {key, value} for an assignment line, or null for a blank, comment or other line. */
    static String[] parse(String raw) {
        String line = raw.strip();
        if (line.isEmpty() || line.startsWith("#")) return null;
        int eq = line.indexOf('=');
        if (eq < 0) return null;
        String key = line.substring(0, eq).strip();
        String value = line.substring(eq + 1).strip();
        if (value.length() >= 2) {
            char first = value.charAt(0), last = value.charAt(value.length() - 1);
            if ((first == '"' || first == '\'') && first == last) {
                value = value.substring(1, value.length() - 1);
            }
        }
        return new String[]{key, value.isEmpty() ? null : value};
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.strip();
        return t.isEmpty() ? null : t;
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }
}
