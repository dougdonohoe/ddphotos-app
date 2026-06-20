package com.donohoedigital.ddphotos.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.snakeyaml.engine.v2.api.Dump;
import org.snakeyaml.engine.v2.api.DumpSettings;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.api.StreamDataWriter;
import org.snakeyaml.engine.v2.api.lowlevel.Compose;
import org.snakeyaml.engine.v2.comments.CommentLine;
import org.snakeyaml.engine.v2.comments.CommentType;
import org.snakeyaml.engine.v2.common.FlowStyle;
import org.snakeyaml.engine.v2.common.ScalarStyle;
import org.snakeyaml.engine.v2.exceptions.YamlEngineException;
import org.snakeyaml.engine.v2.nodes.*;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

public class AlbumsFile {

    private static final Logger logger = LogManager.getLogger(AlbumsFile.class);

    /** Valid values for the {@code default_theme} setting. */
    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK  = "dark";

    private MappingNode rootNode;
    private AlbumsSettings settings;
    private Map<String, String> bases;
    private List<AlbumEntry> albums;
    private Path siteDir;

    public AlbumsFile() {
        settings = new AlbumsSettings();
        bases = new LinkedHashMap<>();
        albums = new ArrayList<>();
    }

    // ── public API ──────────────────────────────────────────────────────────

    public static AlbumsFile load(Path path) throws AlbumsFileException {
        String content;
        try {
            content = Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AlbumsFileException("read " + path + ": " + e.getMessage(), e);
        }

        logger.info("read {}", path);

        Node rawNode;
        try {
            LoadSettings ls = LoadSettings.builder().setParseComments(true).build();
            Optional<Node> opt = new Compose(ls).composeReader(new StringReader(content));
            rawNode = opt.orElseThrow(() -> new AlbumsFileException("empty file: " + path));
        } catch (YamlEngineException e) {
            throw new AlbumsFileException("parse " + path + ": " + e.getMessage(), e);
        }

        if (!(rawNode instanceof MappingNode root)) {
            throw new AlbumsFileException("parse " + path + ": expected mapping at root");
        }

        AlbumsFile af = new AlbumsFile();
        af.rootNode = root;
        af.readFromNode(root);
        try {
            af.validate();
        } catch (AlbumsFileException e) {
            throw new AlbumsFileException(path + ": " + e.getMessage(), e.getCause());
        }
        return af;
    }

    public void save(Path path) throws AlbumsFileException {
        logger.info("save {}", path);
        validate();
        MappingNode node = rootNode != null ? rootNode : buildRootNode();
        syncToNode(node);
        // Relocate comments that SnakeYAML stores on the first-key node (causing them
        // to appear inside sequence items) to locations that emit them before '-'.
        fixSequenceItemComments(node);
        DumpSettings ds = DumpSettings.builder()
                .setDumpComments(true)
                .setIndicatorIndent(2)
                .setIndentWithIndicator(true)
                .setIndent(2)
                .setWidth(4096)
                .setSplitLines(false)
                .build();
        StringStreamWriter sw = new StringStreamWriter();
        new Dump(ds).dumpNode(node, sw);
        String yaml = sw.toString();
        // Move block comments from after '-' to before '-' for the first sequence item.
        yaml = fixBlockCommentsBeforeFirstItem(yaml);
        // Re-insert blank lines between sequence items (removed when clearing firstKey
        // blockComments to inline the '-' indicator with the first key).
        yaml = insertBlankLinesBetweenSequenceItems(yaml);
        try {
            Files.writeString(path, yaml, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AlbumsFileException("write " + path + ": " + e.getMessage(), e);
        }
        if (rootNode == null) rootNode = node;
    }

    // ── getters / setters ───────────────────────────────────────────────────

    public AlbumsSettings getSettings() { return settings; }
    @SuppressWarnings("unused")
    public void setSettings(AlbumsSettings settings) { this.settings = settings; }

    public Map<String, String> getBases() { return bases; }
    public void setBases(Map<String, String> bases) { this.bases = bases; }

    public List<AlbumEntry> getAlbums() { return albums; }
    @SuppressWarnings("unused")
    public void setAlbums(List<AlbumEntry> albums) { this.albums = albums; }

    public Path getSiteDir() { return siteDir; }
    public void setSiteDir(Path siteDir) { this.siteDir = siteDir; }

    // ── path resolution ──────────────────────────────────────────────────────

    /**
     * Resolves a base name to its absolute directory path.
     * Returns null if the base is unknown, or if the stored path is relative
     * and siteDir has not been set.
     */
    public Path resolveBasePath(String baseName) {
        if (baseName == null) return null;
        String raw = bases.get(baseName);
        if (raw == null || raw.isBlank()) return null;
        Path p = Path.of(raw);
        if (p.isAbsolute()) return p;
        if (siteDir == null) return null;
        return siteDir.resolve(raw).normalize();
    }

    /**
     * Resolves an album's source to an absolute directory path.
     * Returns null if source is empty, is a Docker path (/ddphotos prefix),
     * or cannot be resolved (relative source with unresolvable base).
     */
    public Path resolveSourcePath(AlbumEntry album) {
        if (album == null) return null;
        String source = album.getSource();
        if (source == null || source.isBlank()) return null;
        if (source.startsWith("/ddphotos")) return null;
        Path sourcePath = Path.of(source);
        if (sourcePath.isAbsolute()) return sourcePath.normalize();
        Path baseAbsPath = resolveBasePath(album.getBase());
        if (baseAbsPath == null) return null;
        return baseAbsPath.resolve(source).normalize();
    }

    /**
     * Resolves an album's cover photo to its absolute path.
     * Returns null if cover is not set or the source cannot be resolved.
     */
    public Path resolveCoverPath(AlbumEntry album) {
        if (album == null) return null;
        String cover = album.getCover();
        if (cover == null || cover.isBlank()) return null;
        Path sourceDir = resolveSourcePath(album);
        if (sourceDir == null) return null;
        return sourceDir.resolve(cover);
    }

    /**
     * If the given absolute path falls within the site dir, returns the relative path.
     * Otherwise, returns the original string unchanged.
     */
    public String toRelativeBasePath(String path) {
        if (siteDir == null || path == null || path.isBlank()) return path;
        try {
            Path absolute = Path.of(path);
            if (!absolute.isAbsolute()) return path;
            Path realSiteDir = siteDir.toRealPath();
            Path realPath    = absolute.toRealPath();
            Path relative    = realSiteDir.relativize(realPath);
            if (!relative.startsWith("..")) return relative.toString();
        } catch (IOException | IllegalArgumentException ignored) {}
        return path;
    }

    // ── validation ──────────────────────────────────────────────────────────

    void validate() throws AlbumsFileException {
        for (int i = 0; i < albums.size(); i++) {
            AlbumEntry a = albums.get(i);
            if (isBlank(a.getSlug())) {
                throw new AlbumsFileException("album[" + i + "]: slug is required");
            }
            if (isBlank(a.getName())) {
                throw new AlbumsFileException("album \"" + a.getSlug() + "\": name is required");
            }
            if (isBlank(a.getSource())) {
                throw new AlbumsFileException("album \"" + a.getSlug() + "\": source is required");
            }
            if (!isBlank(a.getBase()) && !bases.containsKey(a.getBase())) {
                throw new AlbumsFileException(
                        "album \"" + a.getSlug() + "\": base \"" + a.getBase() + "\" not defined in bases");
            }
        }
        String theme = settings.getDefaultTheme();
        if (!isBlank(theme) && !theme.equals(THEME_LIGHT) && !theme.equals(THEME_DARK)) {
            throw new AlbumsFileException(
                    "settings: default_theme must be \"" + THEME_LIGHT + "\" or \"" + THEME_DARK
                            + "\", got \"" + theme + "\"");
        }
        HeroEntry hero = settings.getHero();
        if (hero != null) {
            if (isBlank(hero.getImage())) {
                throw new AlbumsFileException("hero: image is required");
            }
            if (!isBlank(hero.getBase()) && !bases.containsKey(hero.getBase())) {
                throw new AlbumsFileException("hero: base \"" + hero.getBase() + "\" not defined in bases");
            }
        }
    }

    // ── reading from node tree ──────────────────────────────────────────────

    private void readFromNode(MappingNode root) {
        MappingNode settingsNode = getMappingNode(root, "settings");
        if (settingsNode != null) settings = readSettings(settingsNode);

        MappingNode basesNode = getMappingNode(root, "bases");
        if (basesNode != null) {
            for (NodeTuple t : basesNode.getValue()) {
                bases.put(scalarKey(t), scalarValue(t));
            }
        }

        SequenceNode albumsNode = getSequenceNode(root, "albums");
        if (albumsNode != null) {
            for (Node n : albumsNode.getValue()) {
                if (n instanceof MappingNode mn) albums.add(readAlbumEntry(mn));
            }
        }
    }

    private AlbumsSettings readSettings(MappingNode n) {
        AlbumsSettings s = new AlbumsSettings();
        s.setId(getString(n, "id"));
        s.setSiteName(getString(n, "site_name"));
        s.setSiteUrl(getString(n, "site_url"));
        s.setSiteDescription(getString(n, "site_description"));
        s.setCopyrightOwner(getString(n, "copyright_owner"));
        s.setCopyrightYear(getInt(n, "copyright_year"));
        s.setAllowCrawling(getBoolean(n, "allow_crawling"));
        s.setDescriptions(getString(n, "descriptions"));
        s.setPasswords(getString(n, "passwords"));
        s.setCss(getString(n, "css"));
        s.setDefaultTheme(getString(n, "default_theme"));
        s.setSiteTitleHtml(getString(n, "site_title_html"));
        s.setSiteSubtitleHtml(getString(n, "site_subtitle_html"));
        s.setSiteOverviewHtml(getString(n, "site_overview_html"));

        MappingNode heroNode = getMappingNode(n, "hero");
        if (heroNode != null) {
            HeroEntry hero = new HeroEntry();
            hero.setImage(getString(heroNode, "image"));
            hero.setBase(getString(heroNode, "base"));
            hero.setCrop(getString(heroNode, "crop"));
            s.setHero(hero);
        }
        return s;
    }

    private AlbumEntry readAlbumEntry(MappingNode n) {
        AlbumEntry a = new AlbumEntry();
        a.setSlug(getString(n, "slug"));
        a.setName(getString(n, "name"));
        a.setDescription(getString(n, "description"));
        a.setBase(getString(n, "base"));
        a.setSource(getString(n, "source"));
        a.setCover(getString(n, "cover"));
        a.setManualSortOrder(getBoolean(n, "manual_sort_order"));
        a.setRecurse(getBoolean(n, "recurse"));
        return a;
    }

    // ── syncing domain objects back to node tree ────────────────────────────

    private void syncToNode(MappingNode root) {
        syncSettings(root);
        syncBases(root);
        syncAlbums(root);
        orderBasesBeforeAlbums(root);
    }

    /**
     * Ensures the {@code bases} key appears immediately before {@code albums} (SnakeYAML
     * appends newly-added keys at the end of the mapping), with a blank line both before
     * the bases block and between it and {@code albums}.  No-op if either key is absent.
     */
    private static void orderBasesBeforeAlbums(MappingNode root) {
        int basesIdx  = findTupleIndex(root, "bases");
        int albumsIdx = findTupleIndex(root, "albums");
        if (basesIdx < 0 || albumsIdx < 0) return;

        if (basesIdx > albumsIdx) {
            NodeTuple basesTuple = root.getValue().remove(basesIdx);
            root.getValue().add(findTupleIndex(root, "albums"), basesTuple);
        }

        // Blank line before 'bases:' (i.e. after the settings block) and before 'albums:'.
        ensureLeadingBlankLine(root.getValue().get(findTupleIndex(root, "bases")).getKeyNode());
        ensureLeadingBlankLine(root.getValue().get(findTupleIndex(root, "albums")).getKeyNode());
    }

    /** Prepends a blank-line comment to the node unless it already starts with one. */
    private static void ensureLeadingBlankLine(Node keyNode) {
        List<CommentLine> comments = keyNode.getBlockComments();
        comments = (comments == null) ? new ArrayList<>() : new ArrayList<>(comments);
        if (comments.isEmpty() || comments.getFirst().getCommentType() != CommentType.BLANK_LINE) {
            comments.add(0, new CommentLine(Optional.empty(), Optional.empty(), "", CommentType.BLANK_LINE));
            keyNode.setBlockComments(comments);
        }
    }

    private void syncSettings(MappingNode root) {
        MappingNode sNode = ensureMappingNode(root, "settings");
        AlbumsSettings s = settings;
        setOptionalString(sNode, "id", s.getId());
        setOptionalString(sNode, "site_name", s.getSiteName());
        setOptionalString(sNode, "site_url", s.getSiteUrl());
        setOptionalString(sNode, "site_description", s.getSiteDescription());
        setOptionalString(sNode, "copyright_owner", s.getCopyrightOwner());
        setOptionalInt(sNode, "copyright_year", s.getCopyrightYear());
        setBoolean(sNode, "allow_crawling", s.isAllowCrawling());
        setOptionalString(sNode, "descriptions", s.getDescriptions());
        setOptionalString(sNode, "passwords", s.getPasswords());
        setOptionalString(sNode, "css", s.getCss());
        // Always emit default_theme (like the boolean flags), defaulting a blank value to
        // photogen's "dark" default so the file states the theme explicitly.
        setOptionalString(sNode, "default_theme", isBlank(s.getDefaultTheme()) ? THEME_DARK : s.getDefaultTheme());
        setOptionalString(sNode, "site_title_html", s.getSiteTitleHtml());
        setOptionalString(sNode, "site_subtitle_html", s.getSiteSubtitleHtml());
        setOptionalString(sNode, "site_overview_html", s.getSiteOverviewHtml());

        if (s.getHero() != null) {
            MappingNode heroNode = ensureMappingNode(sNode, "hero");
            setOptionalString(heroNode, "image", s.getHero().getImage());
            setOptionalString(heroNode, "base", s.getHero().getBase());
            setOptionalString(heroNode, "crop", s.getHero().getCrop());
        } else {
            removeKey(sNode, "hero");
        }
    }

    private void syncBases(MappingNode root) {
        if (bases.isEmpty()) {
            // Don't create an empty bases node; remove it if it somehow got added.
            removeKey(root, "bases");
            return;
        }
        MappingNode basesNode = ensureMappingNode(root, "bases");
        basesNode.getValue().removeIf(t -> !bases.containsKey(scalarKey(t)));
        for (Map.Entry<String, String> e : bases.entrySet()) {
            setOptionalString(basesNode, e.getKey(), e.getValue());
        }
    }

    private void syncAlbums(MappingNode root) {
        SequenceNode seqNode = ensureSequenceNode(root, "albums");
        List<Node> oldItems = new ArrayList<>(seqNode.getValue());

        // Build slug → original node map so reorder/insert reuses the right node,
        // preserving each album's ScalarStyle (quoted vs plain) and key ordering.
        Map<String, MappingNode> nodeBySlug = new LinkedHashMap<>();
        for (Node n : oldItems) {
            if (n instanceof MappingNode mn) {
                String slug = getString(mn, "slug");
                if (slug != null) nodeBySlug.put(slug, mn);
            }
        }

        List<Node> newItems = new ArrayList<>(albums.size());
        for (AlbumEntry a : albums) {
            MappingNode existing = nodeBySlug.get(a.getSlug());
            if (existing != null) {
                syncAlbumEntry(existing, a);
                newItems.add(existing);
            } else {
                newItems.add(buildAlbumNode(a));
            }
        }

        seqNode.getValue().clear();
        seqNode.getValue().addAll(newItems);
    }

    private void syncAlbumEntry(MappingNode n, AlbumEntry a) {
        setOptionalString(n, "slug", a.getSlug());
        setOptionalString(n, "name", a.getName());
        setOptionalString(n, "description", a.getDescription());
        setOptionalString(n, "base", a.getBase());
        setOptionalString(n, "source", a.getSource());
        setOptionalString(n, "cover", a.getCover());
        setBoolean(n, "manual_sort_order", a.isManualSortOrder());
        setBoolean(n, "recurse", a.isRecurse());
    }

    // ── node building (for new AlbumsFile or new list items) ────────────────

    private MappingNode buildRootNode() {
        return new MappingNode(Tag.MAP, new ArrayList<>(), FlowStyle.BLOCK);
    }

    private MappingNode buildAlbumNode(AlbumEntry a) {
        MappingNode node = new MappingNode(Tag.MAP, new ArrayList<>(), FlowStyle.BLOCK);
        syncAlbumEntry(node, a);
        return node;
    }

    // ── comment fix: move comments to produce correct `-` + key formatting ────

    /**
     * SnakeYAML Engine stores blank lines and block comments that appear before a
     * sequence item on that item's FIRST KEY node's blockComments.  The emitter then
     * outputs them between the '-' indicator and the first key, producing broken output.
     *
     * This method relocates those comments:
     *   BLOCK comments (actual # text): moved to the SequenceNode (i==0) or the
     *     previous item's endComments (i>0) so they appear before the '-' indicator.
     *   BLANK_LINE comments: removed entirely.  A post-processing pass re-inserts
     *     blank lines between items based on the text pattern.
     */
    private static void fixSequenceItemComments(Node node) {
        if (node instanceof MappingNode mn) {
            for (NodeTuple t : mn.getValue()) {
                fixSequenceItemComments(t.getValueNode());
            }
        } else if (node instanceof SequenceNode sn) {
            List<Node> items = sn.getValue();
            for (int i = 0; i < items.size(); i++) {
                if (!(items.get(i) instanceof MappingNode item)) continue;
                if (item.getValue().isEmpty()) continue;

                Node firstKeyNode = item.getValue().getFirst().getKeyNode();
                List<CommentLine> keyComments = firstKeyNode.getBlockComments();
                if (keyComments == null || keyComments.isEmpty()) continue;

                // Separate real block comments from blank-line placeholders.
                List<CommentLine> blockOnly = new ArrayList<>();
                for (CommentLine cl : keyComments) {
                    if (cl.getCommentType() != CommentType.BLANK_LINE) blockOnly.add(cl);
                }

                if (blockOnly.isEmpty()) {
                    // Only blank-line placeholders: clear them; post-processing re-inserts blanks.
                    firstKeyNode.setBlockComments(null);
                } else if (i == 0) {
                    // Before first item: setting SequenceNode.blockComments causes SnakeYAML to
                    // indent the entire sequence an extra level (engine bug).  Instead, leave
                    // block comments on firstKey (removing only BLANK_LINE entries) so the emitter
                    // outputs "  - # comment\n    key: ...".  Post-processing in
                    // fixBlockCommentsBeforeFirstItem() then reorders them to "  # comment\n  - key:".
                    firstKeyNode.setBlockComments(blockOnly);
                } else {
                    // Between items: attach block comments to previous item's endComments so they
                    // appear before the next '-' indicator.
                    Node prev = items.get(i - 1);
                    List<CommentLine> existing = prev.getEndComments();
                    List<CommentLine> combined = existing != null ? new ArrayList<>(existing) : new ArrayList<>();
                    combined.addAll(blockOnly);
                    prev.setEndComments(combined);
                    // Clear firstKey's blockComments (blank lines re-inserted via post-processing).
                    firstKeyNode.setBlockComments(null);
                }
            }
            for (Node item : items) {
                fixSequenceItemComments(item);
            }
        }
    }

    // Matches a sequence indicator immediately followed by block comment(s):
    //   "  - # comment\n    # more\n    " → groups: indent, comments, trailing-indent
    private static final Pattern FIRST_ITEM_BLOCK_COMMENT_PAT = Pattern.compile(
            "^( +)- (#[^\n]*(?:\n +#[^\n]*)*)(\n +)", Pattern.MULTILINE);

    /**
     * When SnakeYAML emits block comments left on the first key of the first sequence
     * item, they appear between '-' and the key: "  - # comment\n    slug: ...".
     * This method reorders them to appear before '-': "  # comment\n  - slug: ...".
     */
    private static String fixBlockCommentsBeforeFirstItem(String yaml) {
        return FIRST_ITEM_BLOCK_COMMENT_PAT.matcher(yaml).replaceAll(mr -> {
            String seqIndent = mr.group(1);  // e.g. "  "
            String comments  = mr.group(2);  // e.g. "# line1\n    # line2"
            // group(3) is consumed ("\n    ") so the replacement can supply "  - " instead
            String reindented = comments.replaceAll("\n +", "\n" + seqIndent);
            return seqIndent + reindented + "\n" + seqIndent + "- ";
        });
    }

    /**
     * Inserts a blank line before each block-sequence item indicator that follows a
     * non-key line.  The first item in a sequence follows "key:" so it gets no blank
     * line; subsequent items follow a value line so they do.
     *
     * Pattern: any character that is not ':' or newline, followed by newline, followed
     * by an indented '-' indicator — replace with the same but with a blank line inserted.
     */
    private static String insertBlankLinesBetweenSequenceItems(String yaml) {
        return yaml.replaceAll("(?m)([^:\n])\n( *- )", "$1\n\n$2");
    }

    // ── node helpers ────────────────────────────────────────────────────────

    private static MappingNode getMappingNode(MappingNode parent, String key) {
        int idx = findTupleIndex(parent, key);
        if (idx < 0) return null;
        Node v = parent.getValue().get(idx).getValueNode();
        return v instanceof MappingNode mn ? mn : null;
    }

    private static SequenceNode getSequenceNode(MappingNode parent, String key) {
        int idx = findTupleIndex(parent, key);
        if (idx < 0) return null;
        Node v = parent.getValue().get(idx).getValueNode();
        return v instanceof SequenceNode sn ? sn : null;
    }

    private static MappingNode ensureMappingNode(MappingNode parent, String key) {
        MappingNode existing = getMappingNode(parent, key);
        if (existing != null) return existing;
        MappingNode node = new MappingNode(Tag.MAP, new ArrayList<>(), FlowStyle.BLOCK);
        parent.getValue().add(new NodeTuple(scalar(key), node));
        return node;
    }

    private static SequenceNode ensureSequenceNode(MappingNode parent, String key) {
        SequenceNode existing = getSequenceNode(parent, key);
        if (existing != null) return existing;
        SequenceNode node = new SequenceNode(Tag.SEQ, new ArrayList<>(), FlowStyle.BLOCK);
        parent.getValue().add(new NodeTuple(scalar(key), node));
        return node;
    }

    private static String getString(MappingNode node, String key) {
        int idx = findTupleIndex(node, key);
        if (idx < 0) return null;
        Node v = node.getValue().get(idx).getValueNode();
        if (v instanceof ScalarNode sn) {
            String val = sn.getValue();
            return (val == null || val.isEmpty()) ? null : val;
        }
        return null;
    }

    private static boolean getBoolean(MappingNode node, String key) {
        return "true".equalsIgnoreCase(getString(node, key));
    }

    private static int getInt(MappingNode node, String key) {
        String v = getString(node, key);
        if (v == null) return 0;
        try { return Integer.parseInt(v); } catch (NumberFormatException e) { return 0; }
    }

    /**
     * Sets a string value in a mapping node.
     * - Non-blank value: update existing node (preserving style/comments) or add new.
     * - Blank/null value + key exists: remove the key.
     * - Blank/null value + key missing: skip (don't add).
     */
    private static void setOptionalString(MappingNode node, String key, String value) {
        int idx = findTupleIndex(node, key);
        if (!isBlank(value)) {
            if (idx >= 0) {
                NodeTuple old = node.getValue().get(idx);
                if (old.getValueNode() instanceof ScalarNode oldVal) {
                    ScalarNode newVal = new ScalarNode(Tag.STR, value, oldVal.getScalarStyle());
                    newVal.setBlockComments(oldVal.getBlockComments());
                    newVal.setInLineComments(oldVal.getInLineComments());
                    newVal.setEndComments(oldVal.getEndComments());
                    node.getValue().set(idx, new NodeTuple(old.getKeyNode(), newVal));
                } else {
                    node.getValue().set(idx, new NodeTuple(old.getKeyNode(), scalar(value)));
                }
            } else {
                node.getValue().add(new NodeTuple(scalar(key), scalar(value)));
            }
        } else if (idx >= 0) {
            removeKey(node, key);
        }
    }

    /**
     * Sets a boolean value in a mapping node, always emitting an explicit
     * "true"/"false" for clarity (updating the existing key or adding it).
     */
    private static void setBoolean(MappingNode node, String key, boolean value) {
        int idx = findTupleIndex(node, key);
        ScalarNode boolNode = new ScalarNode(Tag.BOOL, value ? "true" : "false", ScalarStyle.PLAIN);
        if (idx >= 0) {
            node.getValue().set(idx, new NodeTuple(node.getValue().get(idx).getKeyNode(), boolNode));
        } else {
            node.getValue().add(new NodeTuple(scalar(key), boolNode));
        }
    }

    private static void setOptionalInt(MappingNode node, String key, int value) {
        if (value == 0) {
            removeKey(node, key);
        } else {
            int idx = findTupleIndex(node, key);
            ScalarNode intNode = new ScalarNode(Tag.INT, Integer.toString(value), ScalarStyle.PLAIN);
            if (idx >= 0) {
                node.getValue().set(idx, new NodeTuple(node.getValue().get(idx).getKeyNode(), intNode));
            } else {
                node.getValue().add(new NodeTuple(scalar(key), intNode));
            }
        }
    }

    private static void removeKey(MappingNode node, String key) {
        node.getValue().removeIf(t -> scalarKey(t).equals(key));
    }

    private static int findTupleIndex(MappingNode node, String key) {
        List<NodeTuple> tuples = node.getValue();
        for (int i = 0; i < tuples.size(); i++) {
            if (scalarKey(tuples.get(i)).equals(key)) return i;
        }
        return -1;
    }

    private static String scalarKey(NodeTuple t) {
        return t.getKeyNode() instanceof ScalarNode sn ? sn.getValue() : "";
    }

    private static String scalarValue(NodeTuple t) {
        return t.getValueNode() instanceof ScalarNode sn ? (sn.getValue() != null ? sn.getValue() : "") : "";
    }

    private static ScalarNode scalar(String value) {
        return new ScalarNode(Tag.STR, value, ScalarStyle.PLAIN);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static final class StringStreamWriter implements StreamDataWriter {
        private final StringBuilder sb = new StringBuilder();

        @Override
        public void write(String str) { sb.append(str); }

        @Override
        public void write(String str, int off, int len) { sb.append(str, off, off + len); }

        @Override
        public String toString() { return sb.toString(); }
    }
}
