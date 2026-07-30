package com.donohoedigital.ddphotos;

import com.donohoedigital.app.engine.AppContext;
import com.donohoedigital.base.TypedHashMap;
import com.donohoedigital.config.PropertyConfig;
import com.donohoedigital.ddphotos.config.Site;
import com.donohoedigital.ddphotos.config.SiteEnvFile;
import com.donohoedigital.ddphotos.config.TextFile;
import com.donohoedigital.ddphotos.config.TextFileException;

import java.nio.file.Path;

/**
 * Edits a site's {@code site.env} - the deploy settings described by {@link SiteEnvFile}.  Reached
 * from the button beside {@code --site-env} on the Deploy tab, which is the only place the file is
 * exposed; it is an advanced setting and most sites never touch it.
 *
 * <p>Which file that is follows the same rule {@code deploy} itself uses: the {@code --site-env}
 * value when one is set, otherwise the site's {@code [config]/site.env}.  Since those are different
 * files, each gets its own window rather than one window per site.
 */
public class SiteEnvEditorPhase extends TextEditorPhase {

    private static final String PHASE_NAME = "SiteEnvEditor";
    private static final String WINDOW_NAME = "site-env-editor";

    /** The file being edited; a string because phase params are read back by type. */
    private static final String PARAM_PATH = "site-env-path";

    /**
     * Opens (or focuses the existing) site.env window.
     *
     * @param siteEnvValue the {@code --site-env} field's current value; blank means the default
     */
    public static void open(AppContext context, Site site, String siteEnvValue) {
        if (site == null) return;
        Path path = resolvePath(site, siteEnvValue);
        if (path == null) return;   // no config dir and no override - nothing to point at

        TypedHashMap params = new TypedHashMap();
        params.setString(PARAM_PATH, path.toString());

        open(context, site, PHASE_NAME, windowName(site, path),
             PropertyConfig.getMessage("msg.windowtitle.SiteEnvEditor.full",
                     site.getDisplayName() != null ? site.getDisplayName() : site.getIdOrDefault()),
             params);
    }

    /** Mirrors deploy-photos.sh: {@code --site-env} wins, otherwise {@code <config-dir>/site.env}. */
    private static Path resolvePath(Site site, String siteEnvValue) {
        if (siteEnvValue != null && !siteEnvValue.isBlank()) {
            return Path.of(siteEnvValue.trim()).toAbsolutePath().normalize();
        }
        return site.getSiteEnvPath();
    }

    /**
     * The window's identity.  The default file gets a plain, readable name; an overriding path gets
     * a hash of itself appended - enough to keep the two windows apart without turning the name
     * (which becomes a {@link java.util.prefs.Preferences} key, capped at 80 characters) into a
     * whole filesystem path.
     */
    private static String windowName(Site site, Path path) {
        String base = WINDOW_NAME + "-" + site.getIdOrDefault();
        return path.equals(site.getSiteEnvPath()) ? base
                : base + "-" + Integer.toHexString(path.toString().hashCode());
    }

    @Override
    protected TextFile openFile() {
        String path = phase_.getString(PARAM_PATH, null);
        return path == null ? null : new SiteEnvFile(Path.of(path)).load();
    }

    @Override
    protected String instructionsHtml() {
        return PropertyConfig.getMessage("msg.siteenv.instructions");
    }

    @Override
    protected String textAreaName() {
        return "siteenvbody";
    }

    @Override
    protected boolean saveFile() {
        try {
            getFile().saveOrCreate();
        } catch (TextFileException e) {
            showSaveError(e);
            return false;
        }
        return true;
    }
}
