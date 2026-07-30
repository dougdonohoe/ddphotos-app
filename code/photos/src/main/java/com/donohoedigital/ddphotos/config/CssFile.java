package com.donohoedigital.ddphotos.config;

import java.nio.file.Path;

/**
 * In-memory model of a site's custom stylesheet.  Its location is {@code albums.yaml}'s
 * {@code settings.css}, resolved against the config dir (see {@link AlbumsFile#resolveCssPath()}).
 *
 * <p>photogen copies the file into the site output as {@code custom.css} and the frontend injects it
 * as a {@code <link>} after the built-in styles, so its rules win by normal cascade order.
 *
 * <p>Unlike {@code settings.passwords}, a {@code settings.css} naming a file that isn't there is a
 * hard error in photogen ({@code css: file %q does not exist}), which is why {@link AlbumsFile} only
 * adopts the setting once the file has actually been written - see
 * {@link AlbumsFile#saveCssFile()}.
 */
public class CssFile extends TextFile {

    public static final String FILE_NAME = "custom.css";

    /** @param path the {@code custom.css} file, which may or may not exist yet. */
    public CssFile(Path path) {
        super(path);
    }

    @Override
    public CssFile load() {
        super.load();
        return this;
    }
}
