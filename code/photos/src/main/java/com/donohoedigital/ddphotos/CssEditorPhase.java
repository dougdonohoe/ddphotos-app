package com.donohoedigital.ddphotos;

import com.donohoedigital.app.engine.AppContext;
import com.donohoedigital.base.TypedHashMap;
import com.donohoedigital.config.PropertyConfig;
import com.donohoedigital.ddphotos.config.AlbumsFile;
import com.donohoedigital.ddphotos.config.AlbumsFileException;
import com.donohoedigital.ddphotos.config.CssFile;
import com.donohoedigital.ddphotos.config.Site;
import com.donohoedigital.ddphotos.config.TextFile;
import com.donohoedigital.ddphotos.config.TextFileException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Edits a site's custom stylesheet - {@code albums.yaml}'s {@code settings.css}, {@code custom.css}
 * by convention.  See {@link CssFile} for what photogen does with it.
 *
 * <p>Saving may have to write {@code albums.yaml} as well: a site that had no stylesheet has no
 * {@code settings.css} either, and {@link AlbumsFile#saveCssFile()} adopts the setting once the
 * file is on disk.
 */
public class CssEditorPhase extends TextEditorPhase {

    private static final Logger logger = LogManager.getLogger(CssEditorPhase.class);

    private static final String PHASE_NAME = "CssEditor";
    private static final String WINDOW_NAME = "css-editor";

    private AlbumsFile albumsFile_;

    /** Opens (or focuses the existing) custom CSS window for the given site - one per site. */
    public static void open(AppContext context, Site site) {
        if (site == null) return;
        open(context, site, PHASE_NAME, WINDOW_NAME + "-" + site.getIdOrDefault(),
             PropertyConfig.getMessage("msg.windowtitle.CssEditor.full",
                     site.getDisplayName() != null ? site.getDisplayName() : site.getIdOrDefault()),
             new TypedHashMap());
    }

    @Override
    protected TextFile openFile() {
        albumsFile_ = site_.getOrCreateAlbumsFile();
        if (albumsFile_ == null) return null;
        // Re-read on every open so the editor never shows stale content.
        albumsFile_.reloadCssFile();
        return albumsFile_.getOrCreateCssFile();
    }

    @Override
    protected String instructionsHtml() {
        return PropertyConfig.getMessage("msg.css.instructions");
    }

    @Override
    protected String textAreaName() {
        return "cssbody";
    }

    @Override
    protected boolean saveFile() {
        try {
            albumsFile_.saveCssFile();
        } catch (TextFileException e) {
            showSaveError(e);
            return false;
        }

        // Writing the stylesheet may have defaulted settings.css, which lives in albums.yaml.
        if (albumsFile_.isCssSettingUnsaved()) {
            try {
                site_.saveAlbumsFile();
            } catch (AlbumsFileException e) {
                logger.error("Failed to save albums file: {}", site_.getAlbumsFilePath(), e);
                PhotosUtils.showSaveError(context_, site_.getAlbumsFilePath(), e);
                return false;
            }
        }
        return true;
    }

    /** photogen copies the stylesheet into the generated site, so it has to run again. */
    @Override
    protected boolean needsPhotogen() {
        return true;
    }
}
