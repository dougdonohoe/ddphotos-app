package com.donohoedigital.ddphotos.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * A cheap snapshot of a file's identity on disk - whether it is there, when it was last written,
 * and how big it is - taken so it can be compared against the same file later to answer "has this
 * changed since I read it?".
 *
 * <p>Deliberately not a content hash: this is taken every couple of seconds by
 * {@code ConfigWatcher} for every file the app is holding, and a stat is orders of magnitude
 * cheaper than a read.  Comparing the modification time <em>and</em> the size leaves one blind
 * spot, a rewrite landing in the same millisecond that happens to produce the same byte count.
 * That is a missed change, never a false alarm, and on APFS (nanosecond timestamps) it is close
 * to unreachable.
 *
 * <p>{@link #of} never throws.  An absent file, a path the app cannot stat, and a path that is
 * not a file at all stamp as {@link #MISSING} - so a file appearing or disappearing reads as
 * a change, which is what the caller wants.
 */
public record FileStamp(boolean exists, long mtime, long size) {

    /** The stamp of a file that is not there, or cannot be read. */
    public static final FileStamp MISSING = new FileStamp(false, 0L, 0L);

    /** Stamps the given file, or returns {@link #MISSING} for a null, absent or unreadable path. */
    public static FileStamp of(Path path) {
        if (path == null) return MISSING;
        try {
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            if (!attrs.isRegularFile()) return MISSING;
            return new FileStamp(true, attrs.lastModifiedTime().toMillis(), attrs.size());
        } catch (IOException | RuntimeException e) {
            // Missing, denied, or a path this filesystem cannot represent - all "not there as far
            // as we are concerned", and all reported by comparison rather than by throwing.
            return MISSING;
        }
    }
}
