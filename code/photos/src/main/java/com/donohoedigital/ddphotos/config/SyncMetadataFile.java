package com.donohoedigital.ddphotos.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.snakeyaml.engine.v2.api.Dump;
import org.snakeyaml.engine.v2.api.DumpSettings;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.common.FlowStyle;
import org.snakeyaml.engine.v2.exceptions.YamlEngineException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Read-only model of a synced album's {@code metadata.yaml}, which photogen writes into the
 * album's sync folder after each sync (ddphotos {@code pkg/photogen/sync_metadata.go}).
 *
 * <p>The app needs only the album-level fields: the upstream {@code name} and
 * {@code description} are the fallback when {@code albums.yaml} leaves them out, and
 * {@code synced_at} says whether photos have arrived yet.  The per-photo records are photogen's
 * business and are not modeled.
 *
 * <p>The one write is {@link #writeStub}: a new synced album has no {@code metadata.yaml} until
 * photogen first runs, so the app writes one holding just the name and description the user saw
 * when choosing the album.  photogen reads the file strictly (unknown keys are fatal), so the stub
 * holds only keys photogen defines, and photogen replaces it wholesale on its first sync.
 *
 * <p>Mirrors {@link TextFile}'s shape: the constructor stores the target, and {@link #load()}
 * never throws.  A missing or unreadable file loads as empty.
 */
public class SyncMetadataFile extends ConfigFile {

    private static final Logger logger = LogManager.getLogger(SyncMetadataFile.class);

    public static final String FILE_NAME = "metadata.yaml";

    /** Same header photogen writes. */
    static final String HEADER = "# Written by photogen sync. Do not edit.\n";

    private final Path path_;
    private boolean existed_;
    private String provider_;
    private String albumId_;
    private String syncedAt_;
    private String name_;
    private String description_;

    /** @param syncDir the album's sync folder, which may not exist yet. */
    public SyncMetadataFile(Path syncDir) {
        this.path_ = syncDir.resolve(FILE_NAME);
    }

    public SyncMetadataFile load() {
        existed_ = false;
        provider_ = albumId_ = syncedAt_ = name_ = description_ = null;
        // Stamp before reading, so a write that lands mid-read still reads as a change.
        restamp();
        if (!Files.exists(path_)) return this;
        try {
            String content = Files.readString(path_, StandardCharsets.UTF_8);
            Object obj = new Load(LoadSettings.builder().build()).loadFromString(content);
            existed_ = true;
            if (obj instanceof Map<?, ?> map) {
                provider_    = string(map.get("provider"));
                albumId_     = string(map.get("album_id"));
                syncedAt_    = string(map.get("synced_at"));
                name_        = string(map.get("name"));
                description_ = string(map.get("description"));
            }
            logger.info("read {}", path_);
        } catch (IOException | YamlEngineException e) {
            logger.warn("Failed to load sync metadata: {} ({})", path_, e.getMessage());
        }
        return this;
    }

    @Override
    public Path getPath() { return path_; }

    public boolean existsOnDisk() { return existed_; }

    public String getProvider() { return provider_; }
    public String getAlbumId() { return albumId_; }

    /** The upstream album name, or null.  Not escaped: photogen stores the name as it came. */
    public String getName() { return name_; }

    /** The upstream album description, already escaped for HTML by photogen, or null. */
    public String getDescription() { return description_; }

    /**
     * True once photogen has synced this album.  A stub written by {@link #writeStub} has no
     * {@code synced_at}, and Go writes its zero time as year 1 when a run records nothing.
     */
    public boolean hasSynced() {
        return syncedAt_ != null && !syncedAt_.startsWith("0001-");
    }

    /**
     * Writes a stub {@code metadata.yaml} into {@code syncDir}, creating the folder and its
     * parents.  {@code description} must already be escaped with {@link SyncText#escape}; the
     * name is written as given, matching photogen.
     */
    public static void writeStub(Path syncDir, String provider, String albumId,
                                 String name, String description) throws IOException {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("provider", provider);
        root.put("album_id", albumId);
        root.put("name", name == null ? "" : name);
        root.put("description", description == null ? "" : description);
        DumpSettings ds = DumpSettings.builder()
                .setDefaultFlowStyle(FlowStyle.BLOCK)
                .setWidth(4096)
                .setSplitLines(false)
                .build();
        String yaml = HEADER + new Dump(ds).dumpToString(root);
        Files.createDirectories(syncDir);
        AtomicWrite.writeString(syncDir.resolve(FILE_NAME), yaml);
        logger.info("wrote stub {}", syncDir.resolve(FILE_NAME));
    }

    private static String string(Object o) {
        if (o == null) return null;
        String s = o.toString();
        return s.isEmpty() ? null : s;
    }
}
