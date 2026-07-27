package com.donohoedigital.ddphotos.config;

import com.donohoedigital.ddphotos.PathValidation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * In-memory model of a {@code photogen.txt} file for a single photo directory.
 *
 * <p>{@code photogen.txt} is a line-based (not YAML) file read by the Go generator
 * (see {@code ddphotos/pkg/photogen/album.go} {@code loadPhotoDescriptions} and
 * {@code util.go} {@code scanLines}).  Each significant line is
 * {@code key [description]}, where {@code key} is a photo basename (with or without an
 * image extension) or a subfolder name.  Blank lines and lines beginning with {@code #}
 * are ignored by the generator but preserved here.
 *
 * <p>This class mirrors {@link SitesFile}'s shape: the constructor stores the target only,
 * {@link #load()} is a non-throwing wrapper returning {@code this}, and {@link #save()}
 * throws {@link PhotogenFileException}.  Editing is round-trip safe: unmodified lines
 * (including blanks and comments) are re-emitted verbatim, so a load/save with no changes
 * reproduces the file byte-for-byte.
 *
 * <p>Per spec, {@link #save()} never creates a {@code photogen.txt} that was not present at
 * load time — it is a no-op when the file did not exist.
 */
public class PhotogenFile {

    private static final Logger logger = LogManager.getLogger(PhotogenFile.class);

    public static final String FILE_NAME = "photogen.txt";

    private final Path dir_;
    private final Path path_;
    private boolean existed_;
    private boolean endsWithNewline_;
    private final List<Line> lines_ = new ArrayList<>();

    // ── public API ──────────────────────────────────────────────────────────

    /** @param photosDir the photo directory that may contain a {@code photogen.txt} file. */
    public PhotogenFile(Path photosDir) {
        this.dir_ = photosDir;
        this.path_ = photosDir.resolve(FILE_NAME);
    }

    public PhotogenFile load() {
        try {
            if (Files.exists(path_)) {
                loadInternal();
            }
        } catch (PhotogenFileException e) {
            logger.warn("Failed to load photogen file: {}", path_, e);
        }
        return this;
    }

    PhotogenFile loadInternal() throws PhotogenFileException {
        String content;
        try {
            content = Files.readString(path_, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new PhotogenFileException("read " + path_ + ": " + FileErrors.reason(e), e);
        }
        existed_ = true;
        parse(content);
        return this;
    }

    public void save() throws PhotogenFileException {
        // Never create a photogen.txt that wasn't there in the first place.
        if (!existed_) {
            return;
        }
        write();
    }

    /**
     * Like {@link #save()}, but creates the file when it was absent — as long as there is content
     * to write.  Used by the editor, which legitimately needs to create a {@code photogen.txt} for
     * a folder that had none once the user adds captions or a manual order.  Writing nothing to a
     * folder that had no file (empty model) remains a no-op, so an untouched folder is never created.
     */
    public void saveOrCreate() throws PhotogenFileException {
        if (!existed_ && lines_.isEmpty()) {
            return;
        }
        // A freshly created file should end with a trailing newline like the samples do.
        if (!existed_) {
            endsWithNewline_ = true;
        }
        write();
    }

    private void write() throws PhotogenFileException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines_.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(lines_.get(i).render());
        }
        if (endsWithNewline_) sb.append('\n');
        try {
            Files.writeString(path_, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new PhotogenFileException("write " + path_ + ": " + FileErrors.reason(e), e);
        }
    }

    // ── getters ─────────────────────────────────────────────────────────────

    public Path getDir()  { return dir_; }
    public Path getPath() { return path_; }

    /** True if the {@code photogen.txt} file was present on disk when {@link #load()} ran. */
    public boolean existsOnDisk() { return existed_; }

    /** Entry keys (photo basenames / subfolder names) in file order. */
    public List<String> getEntryKeys() {
        List<String> keys = new ArrayList<>();
        for (Line l : lines_) {
            if (l.kind == Kind.ENTRY) keys.add(l.key);
        }
        return keys;
    }

    /** @param name a photo basename (with or without image extension) or a subfolder name. */
    public boolean hasEntry(String name) {
        return findEntry(name) != null;
    }

    /**
     * Returns the caption for the given photo/subfolder, or {@code null} if there is no entry.
     * An entry with no caption returns an empty string.
     */
    public String getCaption(String name) {
        Line l = findEntry(name);
        return l == null ? null : l.description;
    }

    // ── mutators ────────────────────────────────────────────────────────────

    /**
     * Sets the caption for the given photo/subfolder.  Updates an existing entry in place
     * (preserving surrounding blanks/comments) or appends a new entry at the end.
     */
    public void setCaption(String name, String caption) {
        String desc = caption == null ? "" : caption;
        Line l = findEntry(name);
        if (l != null) {
            l.description = desc;
            l.raw = null; // force regeneration on save
            return;
        }
        lines_.add(Line.entry(name.trim(), desc));
    }

    /** Removes the entry for the given photo/subfolder, if present.  Blanks/comments are untouched. */
    public void removeEntry(String name) {
        String target = normalizeKey(name);
        lines_.removeIf(l -> l.kind == Kind.ENTRY && normalizeKey(l.key).equals(target));
    }

    /**
     * Reorders the entry lines to match the given key order.  Keys not present are ignored;
     * existing entries not listed are appended (in their original relative order).  Blank and
     * comment lines keep their absolute positions.
     */
    public void setEntryOrder(List<String> keys) {
        Map<String, Line> byKey = new LinkedHashMap<>();
        for (Line l : lines_) {
            if (l.kind == Kind.ENTRY) byKey.put(normalizeKey(l.key), l);
        }
        List<Line> ordered = new ArrayList<>();
        Set<String> used = new HashSet<>();
        for (String k : keys) {
            String nk = normalizeKey(k);
            Line l = byKey.get(nk);
            if (l != null && used.add(nk)) ordered.add(l);
        }
        for (Line l : lines_) {
            if (l.kind == Kind.ENTRY && used.add(normalizeKey(l.key))) ordered.add(l);
        }
        int idx = 0;
        for (int i = 0; i < lines_.size(); i++) {
            if (lines_.get(i).kind == Kind.ENTRY) {
                lines_.set(i, ordered.get(idx++));
            }
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private Line findEntry(String name) {
        String target = normalizeKey(name);
        for (Line l : lines_) {
            if (l.kind == Kind.ENTRY && normalizeKey(l.key).equals(target)) return l;
        }
        return null;
    }

    /**
     * Normalizes a key for matching: lowercased, with a trailing image extension stripped so
     * {@code "img_001.jpg"} and {@code "img_001"} match.  Mirrors the Go generator, which treats
     * an entry's leading token case-insensitively and strips a known image extension.  Reuses
     * {@link PathValidation#isImageFile(String)} for the recognized-extension set.
     */
    public static String normalizeKey(String name) {
        if (name == null) return "";
        String s = name.trim().toLowerCase(Locale.ROOT);
        if (PathValidation.isImageFile(s)) {
            int dot = s.lastIndexOf('.');
            if (dot > 0) s = s.substring(0, dot);
        }
        return s;
    }

    private void parse(String content) {
        lines_.clear();
        endsWithNewline_ = content.endsWith("\n");
        String body = endsWithNewline_ ? content.substring(0, content.length() - 1) : content;
        if (body.isEmpty() && !endsWithNewline_) {
            return; // truly empty file
        }
        String[] raws = body.split("\n", -1);
        for (String raw : raws) {
            String trimmed = raw.strip();
            if (trimmed.isEmpty()) {
                lines_.add(Line.raw(Kind.BLANK, raw));
            } else if (trimmed.startsWith("#")) {
                lines_.add(Line.raw(Kind.COMMENT, raw));
            } else {
                int sp = trimmed.indexOf(' ');
                String key = sp < 0 ? trimmed : trimmed.substring(0, sp);
                String desc = sp < 0 ? "" : trimmed.substring(sp + 1);
                lines_.add(Line.parsedEntry(raw, key, desc));
            }
        }
    }

    private enum Kind { BLANK, COMMENT, ENTRY }

    /**
     * One physical line.  BLANK/COMMENT lines carry only their original raw text and are always
     * emitted verbatim.  An ENTRY carries its parsed key/description; when {@code raw} is non-null
     * (untouched since load) it is emitted verbatim, otherwise it is regenerated from key/desc.
     */
    private static final class Line {
        final Kind kind;
        String raw;          // original text; null for a generated/edited ENTRY
        final String key;    // ENTRY only
        String description;  // ENTRY only

        private Line(Kind kind, String raw, String key, String description) {
            this.kind = kind;
            this.raw = raw;
            this.key = key;
            this.description = description;
        }

        static Line raw(Kind kind, String raw) {
            return new Line(kind, raw, null, null);
        }

        static Line parsedEntry(String raw, String key, String description) {
            return new Line(Kind.ENTRY, raw, key, description);
        }

        static Line entry(String key, String description) {
            return new Line(Kind.ENTRY, null, key, description);
        }

        String render() {
            if (kind != Kind.ENTRY || raw != null) return raw;
            return description == null || description.isEmpty() ? key : key + " " + description;
        }
    }
}
