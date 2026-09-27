package com.donohoedigital.ddphotos.config;

import com.donohoedigital.ddphotos.PathValidation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
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
 * {@code key [description]}, where {@code key} is a media file name, a bare stem (the file
 * name without its extension) or a subfolder name.  A full file name names that one file;
 * a bare stem names every file sharing it ({@code IMG_1} covers {@code IMG_1.jpg} and
 * {@code IMG_1.png}), and a full name beats a stem.  The Go side is {@code photoMatcher}.
 * The editor writes full names, and {@link #canonicalizeEntries} converts older stem lines.
 * A key containing a space is double-quoted
 * ({@code "Chicago 2009.jpg" A cool trip}); one that doesn't is written bare, as before.
 * There is no escape for a literal quote inside a key, so such a name isn't representable.
 * Blank lines and lines beginning with {@code #} are ignored by the generator but preserved here.
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
public class PhotogenFile extends ConfigFile {

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
        restamp();
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
            AtomicWrite.writeString(path_, sb.toString());
        } catch (IOException e) {
            throw new PhotogenFileException("write " + path_ + ": " + FileErrors.reason(e), e);
        }
        existed_ = true;
        restamp();
    }

    // ── getters ─────────────────────────────────────────────────────────────

    public Path getDir()  { return dir_; }

    @Override
    public Path getPath() { return path_; }

    /** True if the {@code photogen.txt} file was present on disk when {@link #load()} ran. */
    public boolean existsOnDisk() { return existed_; }

    /** Entry keys (file names, stems or subfolder names), as written, in file order. */
    public List<String> getEntryKeys() {
        List<String> keys = new ArrayList<>();
        for (Line l : lines_) {
            if (l.kind == Kind.ENTRY) keys.add(l.key);
        }
        return keys;
    }

    /** @param name a media file name, a bare stem, or a subfolder name.  See {@link #findEntry}. */
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

    /**
     * Removes the entries for the given photo/subfolder, if present, matched as {@link #findEntry}
     * matches: lines naming it exactly, or failing those, a stem line.  Blanks/comments are untouched.
     */
    public void removeEntry(String name) {
        String exact = exactKey(name);
        if (lines_.stream().anyMatch(l -> l.kind == Kind.ENTRY && exactKey(l.key).equals(exact))) {
            lines_.removeIf(l -> l.kind == Kind.ENTRY && exactKey(l.key).equals(exact));
        } else {
            lines_.removeIf(l -> l.kind == Kind.ENTRY && stemMatches(l.key, name));
        }
    }

    /**
     * Reorders the entry lines to match the given key order.  Keys not present are ignored;
     * existing entries not listed are appended (in their original relative order).  Blank and
     * comment lines keep their absolute positions.
     */
    public void setEntryOrder(List<String> keys) {
        // Line has identity equality, so this tracks lines, not keys: every entry line is placed
        // exactly once, which the slot-filling loop below depends on.
        List<Line> ordered = new ArrayList<>();
        Set<Line> used = new HashSet<>();
        for (String k : keys) {
            Line l = findEntry(k);
            if (l != null && used.add(l)) ordered.add(l);
        }
        for (Line l : lines_) {
            if (l.kind == Kind.ENTRY && used.add(l)) ordered.add(l);
        }
        int idx = 0;
        for (int i = 0; i < lines_.size(); i++) {
            if (lines_.get(i).kind == Kind.ENTRY) {
                lines_.set(i, ordered.get(idx++));
            }
        }
    }

    /**
     * Rewrites every entry that names media files by stem, rather than in full, as one line per
     * file it names, in its place and with its caption.  After this, each file in
     * {@code mediaFileNames} is found by its exact name, which is what the editor writes.
     *
     * <p>That covers a bare stem ({@code IMG_1}) and a stale extension ({@code IMG_1.jpeg} after
     * the file became {@code IMG_1.jpg}), both of which the Go generator resolves by stem.  A file
     * that also has a line of its own keeps that line's caption, and is placed at whichever of
     * the two lines comes first, as the generator does.  Subfolder lines, comments and lines
     * naming nothing are untouched.
     *
     * <p>Changes the model only; it reaches disk when the folder is next saved.
     *
     * @return true if any line changed
     */
    public boolean canonicalizeEntries(Collection<String> mediaFileNames) {
        Map<String, String> byName = new HashMap<>();
        Map<String, List<String>> byStem = new HashMap<>();
        for (String f : mediaFileNames.stream().sorted().toList()) {
            byName.put(exactKey(f), f);
            byStem.computeIfAbsent(normalizeKey(f), _ -> new ArrayList<>()).add(f);
        }
        // A file's own line wins over a stem line.  The last one wins, as in the generator.
        Map<String, String> ownCaption = new HashMap<>();
        for (Line l : lines_) {
            if (l.kind == Kind.ENTRY && byName.containsKey(exactKey(l.key))) {
                ownCaption.put(exactKey(l.key), l.description);
            }
        }

        List<Line> out = new ArrayList<>();
        Set<String> placed = new HashSet<>();        // files with a line in out
        Set<String> placedByStem = new HashSet<>();  // ... because a stem line was expanded
        boolean changed = false;
        for (Line l : lines_) {
            if (l.kind != Kind.ENTRY) {
                out.add(l);
                continue;
            }
            String ek = exactKey(l.key);
            if (byName.containsKey(ek)) {
                // Placed already by an earlier stem line, which carried this line's caption.
                if (placedByStem.contains(ek)) {
                    changed = true;
                    continue;
                }
                placed.add(ek);
                out.add(l);
                continue;
            }
            List<String> files = byStem.get(normalizeKey(l.key));
            if (files == null) {
                out.add(l);
                continue;
            }
            changed = true;
            for (String f : files) {
                String fk = exactKey(f);
                if (placed.add(fk)) {
                    placedByStem.add(fk);
                    out.add(Line.entry(f, ownCaption.getOrDefault(fk, l.description)));
                }
            }
        }
        if (changed) {
            lines_.clear();
            lines_.addAll(out);
        }
        return changed;
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    /**
     * Finds the line for a name in two tiers, mirroring the generator's {@code photoMatcher}.
     * A line whose key equals the name (case-insensitively) wins.  Failing that, a line with the
     * same stem, provided one side is a bare stem: {@code IMG_1} matches {@code IMG_1.png}, but
     * {@code IMG_1.jpg} does not, since a full name names that one file.
     */
    private Line findEntry(String name) {
        String exact = exactKey(name);
        for (Line l : lines_) {
            if (l.kind == Kind.ENTRY && exactKey(l.key).equals(exact)) return l;
        }
        for (Line l : lines_) {
            if (l.kind == Kind.ENTRY && stemMatches(l.key, name)) return l;
        }
        return null;
    }

    /** The first-tier key: trimmed and lowercased, extension kept. */
    public static String exactKey(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    /** Same stem, and at least one of the two is a bare stem rather than a media file name. */
    private static boolean stemMatches(String a, String b) {
        return normalizeKey(a).equals(normalizeKey(b)) && (isBareStem(a) || isBareStem(b));
    }

    private static boolean isBareStem(String name) {
        String s = exactKey(name);
        return !s.isEmpty() && !PathValidation.isMediaFile(s);
    }

    /**
     * Reduces a key to its stem: lowercased, with a trailing media extension stripped, so
     * {@code "img_001.jpg"} and {@code "img_001"} give the same result, as do {@code "clip.mov"}
     * and {@code "clip"}.  This is the second matching tier (see {@link #findEntry}); it mirrors
     * the Go generator's {@code photogenID}.  Reuses {@link PathValidation#isMediaFile(String)}
     * for the recognized-extension set.
     */
    public static String normalizeKey(String name) {
        if (name == null) return "";
        String s = name.trim().toLowerCase(Locale.ROOT);
        if (PathValidation.isMediaFile(s)) {
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
                lines_.add(parseEntry(raw, trimmed));
            }
        }
    }

    /**
     * Splits one entry line into key and description, mirroring the Go generator's
     * {@code parsePhotogenLine}.  The key runs to the first space, so a key containing spaces
     * has to be double-quoted:
     *
     * <pre>
     * "Doug and Cindy Chicago.jpg" A cool trip to Chicago
     * doug-and-cindy-chicago.jpg A cool trip to Chicago
     * </pre>
     *
     * A line that opens with a quote but never closes it falls back to the unquoted split, so a
     * stray quote can't swallow the rest of the line.
     */
    private static Line parseEntry(String raw, String trimmed) {
        if (trimmed.startsWith("\"")) {
            int close = trimmed.indexOf('"', 1);
            if (close >= 0) {
                return Line.parsedEntry(raw, trimmed.substring(1, close),
                                        trimmed.substring(close + 1).strip());
            }
        }
        int sp = trimmed.indexOf(' ');
        String key = sp < 0 ? trimmed : trimmed.substring(0, sp);
        String desc = sp < 0 ? "" : trimmed.substring(sp + 1).strip();
        return Line.parsedEntry(raw, key, desc);
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
            // Only quote when needed, so files without spaces in their names read as they always have.
            String name = key.indexOf(' ') < 0 ? key : "\"" + key + "\"";
            return description == null || description.isEmpty() ? name : name + " " + description;
        }
    }
}
