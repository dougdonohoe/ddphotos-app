package com.donohoedigital.ddphotos.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * In-memory model of a plain-text config file - the whole file as one string, with no structure
 * imposed on it.  {@link CssFile} is the first user; {@code site.env} is expected to be the next.
 *
 * <p>Shares {@link SitesFile}'s shape: the constructor stores the target only, {@link #load()} is a
 * non-throwing wrapper returning {@code this}, and the save methods throw {@link TextFileException}.
 * As with {@link PhotogenFile}, {@link #save()} never creates a file that was not present at load
 * time; {@link #saveOrCreate()} is the variant an editor uses to write a brand-new file.
 *
 * <p>Line endings are normalized to {@code \n} in memory - Swing text components do that anyway, so
 * pretending otherwise would only make dirty-checking lie - but the separator the file was written
 * with is remembered and restored on save, so editing a CRLF file on Windows doesn't silently
 * rewrite every line.  An untouched load/save cycle is byte-for-byte identical.
 */
public abstract class TextFile {

    private static final Logger logger = LogManager.getLogger(TextFile.class);

    private static final String LF   = "\n";
    private static final String CRLF = "\r\n";

    private final Path path_;
    private boolean existed_;

    /** File contents with line endings normalized to {@code \n}. */
    private String content_ = "";

    /** The separator {@link #content_} came from, restored on save. */
    private String separator_ = LF;

    // ── public API ──────────────────────────────────────────────────────────

    /** @param path the file, which may or may not exist yet. */
    protected TextFile(Path path) {
        this.path_ = path;
    }

    public TextFile load() {
        try {
            if (Files.exists(path_)) {
                loadInternal();
            }
        } catch (TextFileException e) {
            logger.warn("Failed to load text file: {}", path_, e);
        }
        return this;
    }

    TextFile loadInternal() throws TextFileException {
        String content;
        try {
            content = Files.readString(path_, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new TextFileException("read " + path_ + ": " + FileErrors.reason(e), e);
        }

        logger.info("read {}", path_);
        existed_ = true;
        separator_ = content.contains(CRLF) ? CRLF : LF;
        content_ = content.replace(CRLF, LF);
        return this;
    }

    /**
     * Writes the file, but never creates one that was not there in the first place - it is a no-op
     * when the file did not exist at load time.
     */
    public void save() throws TextFileException {
        if (!existed_) {
            return;
        }
        write();
    }

    /**
     * Like {@link #save()}, but creates the file when it was absent - as long as there is something
     * to write.  Used by the editor, which legitimately needs to create the file for a site that has
     * none once the user types into it.  Writing nothing to a site that had no file remains a no-op,
     * so an untouched site never gets a stray file.
     */
    public void saveOrCreate() throws TextFileException {
        if (!existed_ && isEmpty()) {
            return;
        }
        // A freshly created file should end with a trailing newline, as handwritten ones do.
        if (!existed_ && !content_.endsWith(LF)) {
            content_ += LF;
        }
        write();
    }

    private void write() throws TextFileException {
        logger.info("save {}", path_);
        String out = CRLF.equals(separator_) ? content_.replace(LF, CRLF) : content_;
        try {
            AtomicWrite.writeString(path_, out);
        } catch (IOException e) {
            throw new TextFileException("write " + path_ + ": " + FileErrors.reason(e), e);
        }
        existed_ = true;
    }

    // ── content ─────────────────────────────────────────────────────────────

    public Path getPath() { return path_; }

    /** True if the file was present on disk when {@link #load()} ran (or has since been saved). */
    public boolean existsOnDisk() { return existed_; }

    /** The file's text, line endings normalized to {@code \n}.  Never null. */
    public String getContent() { return content_; }

    /** Replaces the file's text.  A null value is treated as empty. */
    public void setContent(String content) {
        content_ = content == null ? "" : content.replace(CRLF, LF);
    }

    /** True when the file has no content worth writing. */
    public boolean isEmpty() { return content_.isBlank(); }
}
