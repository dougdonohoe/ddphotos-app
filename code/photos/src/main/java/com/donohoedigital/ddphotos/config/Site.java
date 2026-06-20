package com.donohoedigital.ddphotos.config;

import com.donohoedigital.base.ApplicationError;
import com.donohoedigital.base.NamedObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public class Site implements NamedObject, Comparable<Site> {
    private static final Logger logger = LogManager.getLogger(Site.class);

    private String displayName;
    private String dirPath;
    private String configPath;
    private AlbumsFile albumsFile_;

    public Site() {}

    public Site(String displayName, String dirPath, String configPath) {
        this.displayName = displayName;
        this.dirPath = dirPath;
        this.configPath = configPath;
    }

    /**
     * Copy everything except the albumsFile_ (intended use of this is to create
     * a copy for use in editing to determine when things change)
     */
    @SuppressWarnings("CopyConstructorMissesField")
    public Site(Site other) {
        this(other.displayName, other.dirPath, other.configPath);
    }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    /**
     * Controls what is seen in non-customized DDComboBox
     */
    @Override
    public String getName() {
        String dir = (dirPath != null && !dirPath.isBlank()) ? " [" + dirPath + "]" : "";
        return (displayName != null ? displayName : "") + " (" + getIdOrDefault() + ")" + dir;
    }

    /**
     * Returns the album file's id, or "TBD" if not yet set.
     */
    public String getIdOrDefault() {
        AlbumsFile af = getAlbumsFile();
        String id = (af != null && af.getSettings() != null) ? af.getSettings().getId() : null;
        return (id != null && !id.isBlank()) ? id : "TBD";
    }

    @Override public void setName(String name) { setDisplayName(name); }

    public Path getAlbumsFilePath() {
        String configDir = getActualConfigPath();
        if (configDir == null) return null;
        return Path.of(configDir).resolve("albums.yaml");
    }

    /**
     * Returns the config directory actually in effect: the custom configPath
     * override if set, otherwise {@code <dirPath>/config}.
     */
    public String getActualConfigPath() {
        if (configPath != null && !configPath.isEmpty()) return configPath;
        return (dirPath != null && !dirPath.isEmpty()) ? dirPath + "/config" : null;
    }

    /** Returns the cached AlbumsFile, loading from disk on first access. Returns null if the file doesn't exist. */
    public AlbumsFile getAlbumsFile() {
        if (albumsFile_ == null) {
            albumsFile_ = loadFromDisk();
        }
        return albumsFile_;
    }

    /**
     * Returns the cached AlbumsFile, creating an empty one if neither a cached instance
     * nor an on-disk file exists. The created instance is cached for subsequent saves.
     */
    public AlbumsFile getOrCreateAlbumsFile() {
        if (getAlbumsFile() == null) {
            albumsFile_ = new AlbumsFile();
            if (dirPath != null && !dirPath.isBlank()) albumsFile_.setSiteDir(Path.of(dirPath));
        }
        return albumsFile_;
    }

    /** Forces a fresh load from disk, replacing the cached instance. */
    public void reloadAlbumsFile() {
        logger.info("reload '{}' {}", displayName, getAlbumsFilePath());
        albumsFile_ = loadFromDisk();
    }

    /** Saves the cached AlbumsFile to disk. No-op if nothing is cached or path is unknown. */
    public void saveAlbumsFile() throws AlbumsFileException {
        if (albumsFile_ == null) return;
        Path path = getAlbumsFilePath();
        if (path == null) return;
        albumsFile_.save(path);
    }

    private AlbumsFile loadFromDisk() {
        Path path = getAlbumsFilePath();
        if (path == null || !Files.exists(path)) return null;
        try {
            AlbumsFile af = AlbumsFile.load(path);
            if (dirPath != null && !dirPath.isBlank()) af.setSiteDir(Path.of(dirPath));
            return af;
        } catch (AlbumsFileException e) {
            throw new ApplicationError(e);
        }
    }

    public String getDirPath() { return dirPath; }
    public void setDirPath(String dirPath) { this.dirPath = dirPath; }

    public String getConfigPath() { return configPath; }
    public void setConfigPath(String configPath) { this.configPath = configPath; }

    /**
     * Returns true when configPath is explicitly set AND differs from the default
     * {@code <dirPath>/config}. This is the condition under which {@code --config-dir}
     * must be passed to the ddphotos command.
     */
    public boolean hasCustomConfigPath() {
        return isCustomConfigPath(dirPath, configPath);
    }

    public static boolean isCustomConfigPath(String dirPath, String configPath) {
        if (configPath == null || configPath.isEmpty()) return false;
        if (dirPath == null || dirPath.isEmpty()) return true;
        return !Path.of(configPath).equals(Path.of(dirPath).resolve("config"));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Site other)) return false;
        return Objects.equals(displayName, other.displayName)
            && Objects.equals(dirPath,     other.dirPath)
            && Objects.equals(configPath,  other.configPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(displayName, dirPath, configPath);
    }

    @Override
    public int compareTo(Site other) {
        String a = displayName != null ? displayName : "";
        String b = other.displayName != null ? other.displayName : "";
        return a.compareToIgnoreCase(b);
    }

    @Override
    public String toString() { return displayName != null ? displayName : ""; }
}
