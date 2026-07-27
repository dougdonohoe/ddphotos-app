package com.donohoedigital.ddphotos.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Replaces a file's contents without ever leaving it half-written.
 *
 * <p>{@link Files#writeString} truncates the file as it opens it, so anything that goes wrong
 * afterwards - a full disk, an external drive pulled mid-save - leaves the user with a truncated
 * config file and no copy of what was there before.  Writing a sibling temp file and renaming it
 * over the original means the file the user sees is always either the old contents or the new
 * ones, never a mixture.
 *
 * <p>The temp file is a sibling so the rename stays within one filesystem, which is what makes
 * it atomic.  These config files live wherever the user's photos live, so the enclosing directory
 * is the only location guaranteed to be on the same volume as the target.
 */
final class AtomicWrite {

    private AtomicWrite() {}

    /**
     * Writes {@code content} to {@code path} as UTF-8, atomically.
     *
     * <p>The failures the caller cares about are reported against {@code path} rather than the
     * temp file: a missing parent directory as {@link NoSuchFileException}, an unwritable target
     * as {@link AccessDeniedException}.  The second is a deliberate check - a rename only needs
     * write permission on the <em>directory</em>, so without it a read-only file would be
     * silently replaced.
     */
    static void writeString(Path path, String content) throws IOException {
        Path target = resolveLinks(path.toAbsolutePath());
        Path dir = target.getParent();
        if (dir == null || !Files.isDirectory(dir)) {
            throw new NoSuchFileException(path.toString());
        }
        if (Files.exists(target) && !Files.isWritable(target)) {
            throw new AccessDeniedException(path.toString());
        }

        // Named rather than Files.createTempFile() so the new file is created by the same
        // writeString() call as before, and so picks up the same default permissions.
        Path tmp = dir.resolve(target.getFileName() + ".tmp" + ProcessHandle.current().pid());
        try {
            Files.writeString(tmp, content, StandardCharsets.UTF_8);
            copyPermissions(target, tmp);
            move(tmp, target);
        } catch (IOException | RuntimeException e) {
            deleteQuietly(tmp);
            throw e;
        }
    }

    /** How many links deep to follow before assuming the chain loops. */
    private static final int MAX_LINK_DEPTH = 10;

    /**
     * Follows a symlink to the file it names, so a save writes through the link instead of
     * replacing it with a regular file - a rename would otherwise break a config the user had
     * deliberately pointed somewhere else (a passwords.yaml shared between sites, say).
     *
     * <p>Resolved by hand rather than with {@code toRealPath()}, which insists the destination
     * already exist; a link pointing at a file not yet created still needs to work.
     */
    private static Path resolveLinks(Path path) throws IOException {
        Path resolved = path;
        for (int i = 0; i < MAX_LINK_DEPTH && Files.isSymbolicLink(resolved); i++) {
            Path parent = resolved.getParent();
            Path linked = Files.readSymbolicLink(resolved);
            // A link's contents may be relative to the directory the link itself sits in.
            resolved = (parent != null ? parent.resolve(linked) : linked).normalize();
        }
        return resolved;
    }

    /**
     * Carries the target's permissions over to its replacement.  Without this a file the user
     * (or photogen's container) had deliberately opened up or locked down would silently revert
     * to whatever the default is on the next save.
     */
    private static void copyPermissions(Path target, Path tmp) {
        if (!Files.exists(target)) return;     // new file: the default is what it would have had
        try {
            Files.setPosixFilePermissions(tmp, Files.getPosixFilePermissions(target));
        } catch (IOException | UnsupportedOperationException e) {
            // Not a POSIX filesystem (Windows) - there is nothing to carry over.
        }
    }

    private static void move(Path tmp, Path target) throws IOException {
        try {
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            // Windows can refuse an atomic replace; a plain one still beats a truncating write.
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Best-effort cleanup - the write already failed, and its error is the one worth reporting. */
    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }
}
