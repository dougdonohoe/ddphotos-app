package com.donohoedigital.ddphotos;

import com.donohoedigital.app.engine.AppContext;
import com.donohoedigital.app.engine.Phase;
import com.donohoedigital.base.TypedHashMap;
import com.donohoedigital.gui.OptionFileChooser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;
import java.util.prefs.Preferences;

/**
 * Installs the DD photo chooser ({@link PhotoChooserDialog}) on an image field in place of the
 * platform file dialog.
 *
 * <p>Used on every platform.  Swing's chooser lists filenames and nothing else, and even macOS's
 * native panel - which does preview - is slower to pick a photo with than a wall of thumbnails.
 */
public final class PhotoChooser {

    /** Remembers where the chooser was last left when the field has no base/source to root it. */
    private static final String PREF_LAST_DIR = "photochooser.lastdir";

    private PhotoChooser() {}

    /**
     * Points {@code option}'s browse button at the DD photo chooser.
     *
     * <p>The chooser opens on the field's current photo when it has one, so someone stepping
     * through albums lands where that album already points rather than back at the top.
     *
     * @param titleKey     message key for the dialog's window title
     * @param rootSupplier the directory browsing is confined to - the album's source folder for a
     *                     cover, the selected base for a hero - re-evaluated on every open so
     *                     changing the base/source combo is picked up.  A null result means the
     *                     field has nothing to relativize against, so browsing roams free.
     * @param allowVideo   whether clips are offered alongside photos: true for a cover, which
     *                     photogen renders from the video's poster frame, false for a hero, which
     *                     photogen rejects outright since it is a libvips crop
     */
    public static void install(OptionFileChooser option, AppContext context, String titleKey,
                               Supplier<Path> rootSupplier, boolean allowVideo) {
        option.setCustomPicker((_, startDir) -> {
            Path root = rootSupplier != null ? rootSupplier.get() : null;
            Path current = currentPhoto(option.getText(), root);
            Path start = current != null ? current.getParent() : startDir(startDir, root);

            TypedHashMap params = new TypedHashMap();
            params.setObject(PhotoChooserDialog.PARAM_START_DIR, start);
            params.setObject(PhotoChooserDialog.PARAM_ROOT_DIR, root);
            params.setObject(PhotoChooserDialog.PARAM_SELECT, current);
            params.setObject(PhotoChooserDialog.PARAM_WINDOW_TITLE_KEY, titleKey);
            params.setBoolean(PhotoChooserDialog.PARAM_ALLOW_VIDEO, allowVideo);

            // Modal, so this returns only once the dialog is gone and its result is in.
            Phase chooser = context.processPhaseNow(PhotoChooserDialog.PHASE_NAME, params);
            if (!(chooser.getResult() instanceof Path picked)) return null;
            if (root == null) prefs().put(PREF_LAST_DIR, picked.getParent().toString());
            return picked.toString();
        });
    }

    /**
     * The photo the field currently holds, as an absolute path, or null when it is empty, points
     * somewhere that isn't a readable file (a Docker path, say), or names a file that has since
     * been moved.  Relative values are resolved against the base/source, exactly as the field's
     * own validation resolves them.
     */
    private static Path currentPhoto(String value, Path root) {
        if (value == null || value.isBlank()) return null;
        Path p = Path.of(value.trim());
        if (!p.isAbsolute()) {
            if (root == null) return null;
            p = root.resolve(p);
        }
        p = p.normalize();
        return Files.isRegularFile(p) ? p : null;
    }

    /**
     * Where to open the chooser when the field has no usable value: its own suggestion when it
     * exists, else the last place the user browsed to, else home.  A suggestion that no longer
     * exists falls back to its nearest existing ancestor rather than dumping the user at home.
     */
    private static Path startDir(String suggested, Path root) {
        if (suggested != null && !suggested.isBlank()) {
            Path p = existingAncestor(Path.of(suggested));
            if (p != null) return p;
        }
        if (root != null) return root;
        return existingAncestor(Path.of(prefs().get(PREF_LAST_DIR, System.getProperty("user.home"))));
    }

    /** The path itself if it is a directory, else its closest existing ancestor (null if none). */
    private static Path existingAncestor(Path path) {
        for (Path p = path; p != null; p = p.getParent()) {
            if (Files.isDirectory(p)) return p;
        }
        return null;
    }

    private static Preferences prefs() {
        return PhotosConstants.getAppPreferences();
    }
}
