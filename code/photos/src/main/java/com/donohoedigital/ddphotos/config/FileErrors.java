package com.donohoedigital.ddphotos.config;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.ReadOnlyFileSystemException;

/**
 * Explains why a file operation failed.
 *
 * <p>The NIO exceptions carry the failing path as their message and nothing else, so
 * {@code "write " + path + ": " + e.getMessage()} says the path twice and never says why.
 * {@link #reason} supplies the missing half for logs and exception messages; {@link #messageKey}
 * names a property holding the same thing in a sentence for the user.
 *
 * <p>Like {@link com.donohoedigital.ddphotos.PathValidation}, this returns a message <em>key</em>
 * rather than localized text, keeping it free of the config/UI layers and directly unit-testable.
 */
public final class FileErrors {

    private FileErrors() {}

    /** How deep a cause chain is followed before assuming it loops. */
    private static final int MAX_CAUSE_DEPTH = 20;

    /** The deepest cause of the given throwable, or the throwable itself when it has none. */
    public static Throwable rootCause(Throwable t) {
        Throwable root = t;
        for (int i = 0; i < MAX_CAUSE_DEPTH; i++) {
            Throwable cause = root.getCause();
            if (cause == null || cause == root) break;
            root = cause;
        }
        return root;
    }

    /**
     * A short technical reason - "no such file or directory", "permission denied" - suitable for
     * a log line or an exception message.  Never null.
     */
    public static String reason(Throwable t) {
        Throwable root = rootCause(t);
        if (root instanceof NoSuchFileException)         return "no such file or directory";
        if (root instanceof AccessDeniedException)       return "permission denied";
        if (root instanceof NotDirectoryException)       return "not a directory";
        if (root instanceof ReadOnlyFileSystemException) return "read-only file system";
        // Other FileSystemExceptions have a real reason when the OS gave one; their getMessage()
        // is just the path(s), so fall through to the class name rather than repeat it.
        if (root instanceof FileSystemException fse) {
            return notBlank(fse.getReason()) ? fse.getReason().trim() : simpleName(root);
        }
        return notBlank(root.getMessage()) ? root.getMessage() : simpleName(root);
    }

    /**
     * The property key holding a user-facing explanation of the failure, or null when the cause
     * isn't one we can describe better than the exception already does.
     */
    public static String messageKey(Throwable t) {
        Throwable root = rootCause(t);
        if (root instanceof NoSuchFileException)         return "msg.error.file.missing";
        if (root instanceof AccessDeniedException)       return "msg.error.file.denied";
        if (root instanceof NotDirectoryException)       return "msg.error.file.notdirectory";
        if (root instanceof ReadOnlyFileSystemException) return "msg.error.file.readonly";
        return null;
    }

    /** True when the failure was an I/O problem rather than, say, a validation failure. */
    public static boolean isIoFailure(Throwable t) {
        Throwable root = rootCause(t);
        return root instanceof IOException || root instanceof ReadOnlyFileSystemException;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String simpleName(Throwable t) {
        return t.getClass().getSimpleName();
    }
}
