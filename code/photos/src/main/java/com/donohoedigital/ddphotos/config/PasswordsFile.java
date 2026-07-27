package com.donohoedigital.ddphotos.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.snakeyaml.engine.v2.api.Dump;
import org.snakeyaml.engine.v2.api.DumpSettings;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.api.lowlevel.Compose;
import org.snakeyaml.engine.v2.common.FlowStyle;
import org.snakeyaml.engine.v2.exceptions.YamlEngineException;
import org.snakeyaml.engine.v2.nodes.MappingNode;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.NodeTuple;
import org.snakeyaml.engine.v2.nodes.Tag;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.donohoedigital.ddphotos.config.YamlNodes.*;

/**
 * In-memory model of a {@code passwords.yaml} file — the site-wide and per-album passwords for
 * one site.  Its location is {@code albums.yaml}'s {@code settings.passwords}, resolved against
 * the config dir (see {@link AlbumsFile#resolvePasswordsPath()}).
 *
 * <p>The format is defined by the Go generator ({@code ddphotos/pkg/photogen/encrypt.go}):
 * <pre>
 * key: hmac-secret
 *
 * site:
 *   password: site-wide-password
 *   hint: Optional hint
 *
 * albums:
 *   album-slug:
 *     password: per-album-password
 *     hint: Optional hint
 * </pre>
 *
 * <p>Albums are keyed by {@link AlbumEntry#getSlug() slug}.  An album with no entry falls back
 * to the site password, so {@link #isAlbumProtected(String)} — not {@link #hasAlbumEntry(String)}
 * — is the question worth asking.  The {@code key} is an HMAC-SHA256 secret used to derive the
 * obfuscated {@code .webp} filenames of every encrypted photo, so <b>changing it renames every
 * such photo</b> and forces a full re-upload.
 *
 * <p>This class mirrors {@link SitesFile}'s shape: the constructor stores the target only,
 * {@link #load()} is a non-throwing wrapper returning {@code this}, and {@link #save()} throws
 * {@link PasswordsFileException}.  Like {@link AlbumsFile} it keeps the parsed node tree, so
 * comments, blank lines, key order and quoting style survive a load/save cycle unchanged.
 *
 * <p>As with {@link PhotogenFile}, {@link #save()} never creates a file that was not present at
 * load time; {@link #saveOrCreate()} is the variant the editor uses to write a brand-new file.
 */
public class PasswordsFile {

    private static final Logger logger = LogManager.getLogger(PasswordsFile.class);

    public static final String FILE_NAME = "passwords.yaml";

    /** photogen's {@code minPasswordLen} — see {@code encrypt.go}. */
    public static final int MIN_PASSWORD_LENGTH = 5;

    private final Path path_;
    private boolean existed_;
    private MappingNode rootNode_;

    private String key_;
    private PasswordEntry site_;
    private final Map<String, PasswordEntry> albums_ = new LinkedHashMap<>();

    // ── public API ──────────────────────────────────────────────────────────

    /** @param path the {@code passwords.yaml} file, which may or may not exist yet. */
    public PasswordsFile(Path path) {
        this.path_ = path;
    }

    public PasswordsFile load() {
        try {
            if (Files.exists(path_)) {
                loadInternal();
            }
        } catch (PasswordsFileException e) {
            logger.warn("Failed to load passwords file: {}", path_, e);
        }
        return this;
    }

    PasswordsFile loadInternal() throws PasswordsFileException {
        String content;
        try {
            content = Files.readString(path_, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new PasswordsFileException("read " + path_ + ": " + FileErrors.reason(e), e);
        }

        logger.info("read {}", path_);
        existed_ = true;

        Optional<Node> opt;
        try {
            LoadSettings ls = LoadSettings.builder().setParseComments(true).build();
            opt = new Compose(ls).composeReader(new StringReader(content));
        } catch (YamlEngineException e) {
            throw new PasswordsFileException("parse " + path_ + ": " + e.getMessage(), e);
        }

        // An empty file is legal — it just means "nothing configured yet".
        if (opt.isEmpty()) {
            return this;
        }
        if (!(opt.get() instanceof MappingNode root)) {
            throw new PasswordsFileException("parse " + path_ + ": expected mapping at root");
        }
        rootNode_ = root;
        readFromNode(root);
        return this;
    }

    /**
     * Writes the file, but never creates a {@code passwords.yaml} that was not there in the
     * first place — it is a no-op when the file did not exist at load time.
     */
    public void save() throws PasswordsFileException {
        if (!existed_) {
            return;
        }
        write();
    }

    /**
     * Like {@link #save()}, but creates the file when it was absent — as long as there is
     * something to write.  Used by the editor, which legitimately needs to create the file for a
     * site that has none once the user sets a password.  Writing nothing to a site that had no
     * file (empty model) remains a no-op, so an untouched site never gets a stray file.
     */
    public void saveOrCreate() throws PasswordsFileException {
        if (!existed_ && isEmpty()) {
            return;
        }
        write();
    }

    private void write() throws PasswordsFileException {
        validate();
        logger.info("save {}", path_);

        String yaml;
        if (rootNode_ == null && isEmpty()) {
            // Nothing to say; don't turn an empty file into "{}".
            yaml = "";
        } else {
            MappingNode node = rootNode_ != null ? rootNode_ : buildRootNode();
            syncToNode(node);
            DumpSettings ds = DumpSettings.builder()
                    .setDumpComments(true)
                    .setIndent(2)
                    .setWidth(4096)
                    .setSplitLines(false)
                    .build();
            StringStreamWriter sw = new StringStreamWriter();
            new Dump(ds).dumpNode(node, sw);
            yaml = sw.toString();
            if (rootNode_ == null) rootNode_ = node;
        }

        try {
            AtomicWrite.writeString(path_, yaml);
        } catch (IOException e) {
            throw new PasswordsFileException("write " + path_ + ": " + FileErrors.reason(e), e);
        }
        existed_ = true;
    }

    // ── getters ─────────────────────────────────────────────────────────────

    public Path getPath() { return path_; }

    /** True if the file was present on disk when {@link #load()} ran (or has since been saved). */
    public boolean existsOnDisk() { return existed_; }

    /** True when nothing is configured: no key, no site entry, no album entries. */
    public boolean isEmpty() {
        return isBlank(key_) && site_ == null && albums_.isEmpty();
    }

    // ── key ─────────────────────────────────────────────────────────────────

    public String getKey() { return key_; }

    /**
     * Sets the HMAC key.  Changing an existing key renames every encrypted photo, so callers
     * should confirm with the user first — see {@link #hasKey()}.
     */
    public void setKey(String key) { this.key_ = isBlank(key) ? null : key.trim(); }

    public boolean hasKey() { return !isBlank(key_); }

    /**
     * Generates a key for a new file: the site id (for readability, matching the style of
     * handwritten keys) followed by a random UUID.  Falls back to a bare UUID when the site
     * has no id yet.
     */
    public static String generateKey(String siteId) {
        String uuid = UUID.randomUUID().toString();
        return isBlank(siteId) ? uuid : siteId.trim() + "-" + uuid;
    }

    // ── site ────────────────────────────────────────────────────────────────

    /** The site-wide entry, or null when the whole site is not password protected. */
    public PasswordEntry getSite() { return site_; }

    public String getSitePassword() { return site_ == null ? null : site_.getPassword(); }
    public String getSiteHint()     { return site_ == null ? null : site_.getHint(); }

    public boolean isSiteProtected() { return site_ != null && site_.hasPassword(); }

    /**
     * Sets the site-wide password.  A blank password {@linkplain #clearSite() clears the whole
     * site entry} — a hint with no password protects nothing.
     */
    public void setSitePassword(String password) {
        if (isBlank(password)) {
            clearSite();
            return;
        }
        if (site_ == null) site_ = new PasswordEntry();
        site_.setPassword(password);
    }

    /** Sets the site hint, creating the site entry if needed. */
    public void setSiteHint(String hint) {
        if (site_ == null) {
            if (isBlank(hint)) return;
            site_ = new PasswordEntry();
        }
        site_.setHint(isBlank(hint) ? null : hint);
    }

    /** Removes the entire {@code site:} block, leaving the site unprotected. */
    public void clearSite() { site_ = null; }

    // ── albums (keyed by slug) ──────────────────────────────────────────────

    /** Album slugs with their own entry, in file order. */
    public Set<String> getAlbumSlugs() { return new LinkedHashSet<>(albums_.keySet()); }

    /** The album's own entry, or null when it has none (and so inherits the site password). */
    public PasswordEntry getAlbum(String slug) { return slug == null ? null : albums_.get(slug); }

    public boolean hasAlbumEntry(String slug) { return getAlbum(slug) != null; }

    public String getAlbumPassword(String slug) {
        PasswordEntry e = getAlbum(slug);
        return e == null ? null : e.getPassword();
    }

    public String getAlbumHint(String slug) {
        PasswordEntry e = getAlbum(slug);
        return e == null ? null : e.getHint();
    }

    /**
     * The password actually in effect for an album: its own if it has one, otherwise the site
     * password.  Mirrors photogen's {@code AlbumPassword()}.  Returns null when neither is set.
     */
    public String getEffectiveAlbumPassword(String slug) {
        PasswordEntry e = getAlbum(slug);
        if (e != null && e.hasPassword()) return e.getPassword();
        return isSiteProtected() ? site_.getPassword() : null;
    }

    /** True when the album is protected, whether by its own password or the site's. */
    public boolean isAlbumProtected(String slug) {
        return getEffectiveAlbumPassword(slug) != null;
    }

    /**
     * Sets an album's own password.  A blank password {@linkplain #removeAlbum removes the whole
     * entry}, so the album falls back to the site password — photogen rejects an entry whose
     * password is empty, because it would silently override the site password with no encryption.
     */
    public void setAlbumPassword(String slug, String password) {
        if (isBlank(slug)) return;
        if (isBlank(password)) {
            removeAlbum(slug);
            return;
        }
        albums_.computeIfAbsent(slug, _ -> new PasswordEntry()).setPassword(password);
    }

    /** Sets an album's hint, creating its entry if needed. */
    public void setAlbumHint(String slug, String hint) {
        if (isBlank(slug)) return;
        PasswordEntry e = albums_.get(slug);
        if (e == null) {
            if (isBlank(hint)) return;
            e = new PasswordEntry();
            albums_.put(slug, e);
        }
        e.setHint(isBlank(hint) ? null : hint);
    }

    /** Removes an album's entry, so it falls back to the site password (if any). */
    public void removeAlbum(String slug) {
        if (slug != null) albums_.remove(slug);
    }

    // ── validation ──────────────────────────────────────────────────────────

    /**
     * Checks the rules photogen enforces in {@code EncryptConfig.Validate()}, so problems surface
     * in the editor rather than at generation time.  Called by {@link #save()} and
     * {@link #saveOrCreate()}.
     */
    public void validate() throws PasswordsFileException {
        if (site_ != null && site_.hasPassword()) {
            checkLength("site", site_.getPassword());
        }
        for (Map.Entry<String, PasswordEntry> e : albums_.entrySet()) {
            String label = "album \"" + e.getKey() + "\"";
            PasswordEntry entry = e.getValue();
            // An empty per-album password silently overrides the site password with no
            // encryption, so photogen rejects it outright.
            if (!entry.hasPassword()) {
                throw new PasswordsFileException(label + ": password is required");
            }
            checkLength(label, entry.getPassword());
        }
        if (isBlank(key_) && isAnythingEncrypted()) {
            throw new PasswordsFileException("key is required when any album is encrypted");
        }
    }

    private boolean isAnythingEncrypted() {
        if (isSiteProtected()) return true;
        for (PasswordEntry e : albums_.values()) {
            if (e.hasPassword()) return true;
        }
        return false;
    }

    private static void checkLength(String label, String password) throws PasswordsFileException {
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new PasswordsFileException(
                    label + ": password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
    }

    // ── reading from node tree ──────────────────────────────────────────────

    private void readFromNode(MappingNode root) {
        key_ = getString(root, "key");

        MappingNode siteNode = getMappingNode(root, "site");
        if (siteNode != null) site_ = readEntry(siteNode);

        MappingNode albumsNode = getMappingNode(root, "albums");
        if (albumsNode != null) {
            for (NodeTuple t : albumsNode.getValue()) {
                String slug = scalarKey(t);
                if (!slug.isEmpty() && t.getValueNode() instanceof MappingNode mn) {
                    albums_.put(slug, readEntry(mn));
                }
            }
        }
    }

    private static PasswordEntry readEntry(MappingNode n) {
        PasswordEntry e = new PasswordEntry();
        e.setPassword(getString(n, "password"));
        e.setHint(getString(n, "hint"));
        return e;
    }

    // ── syncing domain objects back to node tree ────────────────────────────

    private void syncToNode(MappingNode root) {
        setOptionalString(root, "key", key_);
        syncSite(root);
        syncAlbums(root);
        orderTopLevelKeys(root);
    }

    private void syncSite(MappingNode root) {
        if (site_ == null || (!site_.hasPassword() && isBlank(site_.getHint()))) {
            removeKey(root, "site");
            return;
        }
        boolean created = findTupleIndex(root, "site") < 0;
        MappingNode siteNode = ensureMappingNode(root, "site");
        syncEntry(siteNode, site_);
        if (created) blankLineBefore(root, "site");
    }

    private void syncAlbums(MappingNode root) {
        if (albums_.isEmpty()) {
            // Don't leave behind an empty album's node.
            removeKey(root, "albums");
            return;
        }
        boolean created = findTupleIndex(root, "albums") < 0;
        MappingNode albumsNode = ensureMappingNode(root, "albums");
        albumsNode.getValue().removeIf(t -> !albums_.containsKey(scalarKey(t)));
        for (Map.Entry<String, PasswordEntry> e : albums_.entrySet()) {
            syncEntry(ensureMappingNode(albumsNode, e.getKey()), e.getValue());
        }
        if (created) blankLineBefore(root, "albums");
    }

    private static void syncEntry(MappingNode n, PasswordEntry e) {
        setOptionalString(n, "password", e.getPassword());
        setOptionalString(n, "hint", e.getHint());
    }

    private MappingNode buildRootNode() {
        return new MappingNode(Tag.MAP, new ArrayList<>(), FlowStyle.BLOCK);
    }

    // ── layout helpers ──────────────────────────────────────────────────────

    /**
     * Restores the canonical {@code key} / {@code site} / {@code albums} order — SnakeYAML
     * appends newly-added keys at the end of the mapping.  A no-op when the file is already in
     * that order, so an existing file's layout is untouched.
     */
    private static void orderTopLevelKeys(MappingNode root) {
        moveBefore(root, "key", "site");
        moveBefore(root, "key", "albums");
        moveBefore(root, "site", "albums");
    }

    private static void moveBefore(MappingNode root, String first, String second) {
        int firstIdx  = findTupleIndex(root, first);
        int secondIdx = findTupleIndex(root, second);
        if (secondIdx < 0 || firstIdx < secondIdx) return;
        NodeTuple tuple = root.getValue().remove(firstIdx);
        root.getValue().add(findTupleIndex(root, second), tuple);
    }

    /** Separates a newly-added top-level block from the one above it. */
    private static void blankLineBefore(MappingNode root, String key) {
        int idx = findTupleIndex(root, key);
        if (idx >= 0) ensureLeadingBlankLine(root.getValue().get(idx).getKeyNode());
    }
}
