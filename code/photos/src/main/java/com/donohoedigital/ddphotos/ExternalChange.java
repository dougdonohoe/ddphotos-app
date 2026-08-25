package com.donohoedigital.ddphotos;

import com.donohoedigital.app.engine.AppContext;
import com.donohoedigital.app.engine.EngineUtils;
import com.donohoedigital.config.PropertyConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;

/**
 * What the app says, and what it writes to the log, when {@link ConfigWatcher} reports that a file
 * it is holding has been rewritten by something else.
 *
 * <p>The dialog and the log line live together here on purpose.  The log is the only lasting record
 * that any of this happened - the user is answering a dialog that vanishes - so the two must never
 * drift apart, and every path through a conflict has to end in a line saying which way it went.
 *
 * <p>All the questions are warnings (the orange title bar, as {@code Reset Preferences} uses):
 * each one stands to lose work whichever way it is answered, so none should be answered on
 * autopilot.
 */
final class ExternalChange {

    private static final Logger logger = LogManager.getLogger(ExternalChange.class);

    private static final String TITLE = "msg.windowtitle.externalChange";

    private ExternalChange() {}

    /**
     * Asks whether to throw away unsaved edits in favor of the version now on disk, and records
     * the answer.  Call only when there is genuinely something to lose - with nothing pending, use
     * {@link #logReloaded} and reload.
     *
     * @return true when the caller should reload
     */
    static boolean confirmDiscard(AppContext context, Path path) {
        boolean discard = EngineUtils.displayWarningConfirmationDialog(context,
                PropertyConfig.getMessage("msg.confirm.external.reload", name(path)),
                TITLE, null);
        // The choice only; whether the reload then succeeded is logReloaded's to say.
        if (discard) {
            logger.info("user chose to discard unsaved changes: {}", path);
        } else {
            logger.info("user chose to keep unsaved changes, not reloading: {}", path);
        }
        return discard;
    }

    /**
     * Asks whether to save over a file that has changed since it was read, and records the answer.
     * This is the second half of a {@link #confirmDiscard} the user answered "keep mine" to, but it
     * stands on its own: it fires on the file's actual state, so it still catches a user who never
     * saw the first question.
     *
     * @return true when the caller should go ahead and write
     */
    static boolean confirmOverwrite(AppContext context, Path path) {
        boolean overwrite = EngineUtils.displayWarningConfirmationDialog(context,
                PropertyConfig.getMessage("msg.confirm.overwrite.external", name(path)),
                TITLE, null);
        if (overwrite) {
            logger.info("user chose to overwrite external changes: {}", path);
        } else {
            logger.info("user chose to keep external changes, save canceled: {}", path);
        }
        return overwrite;
    }

    /**
     * Records a reload that actually happened.  Always called on success, whether a
     * question was asked first, so the log has one line per reload rather than leaving the reader
     * to infer it from an answer.
     */
    static void logReloaded(Path path) {
        logger.info("reloaded after external change: {}", path);
    }

    /** The file name alone; the full path is in the log, and would crowd the dialog. */
    private static String name(Path path) {
        if (path == null) return "";
        Path name = path.getFileName();
        return name != null ? name.toString() : path.toString();
    }
}
