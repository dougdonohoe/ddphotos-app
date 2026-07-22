package com.donohoedigital.ddphotos;

import com.donohoedigital.app.config.AppConfigUtils;
import com.donohoedigital.app.engine.AppContext;
import com.donohoedigital.app.engine.EngineUtils;
import com.donohoedigital.base.TypedHashMap;
import com.donohoedigital.ddphotos.config.Site;
import com.donohoedigital.ddphotos.config.SitesFile;
import com.donohoedigital.gui.DDHtmlArea;
import com.donohoedigital.gui.GuiManager;

import javax.swing.BorderFactory;
import java.awt.Dimension;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public class PhotosUtils {

    /** Path to the private ddphotos wrapper script installed by the New User wizard. */
    public static Path scriptPath() {
        return AppConfigUtils.getBinDir().toPath().resolve("ddphotos");
    }

    /**
     * Creates the standard bottom hover-help widget used by DD Photos windows: a display-only,
     * fixed-height {@link DDHtmlArea}.  Add the returned widget to the SOUTH of the window's content
     * and register it with the window via {@code context.getWindow().setHelpTextWidget(...)} so the
     * window's component hover-help renders here.
     */
    public static DDHtmlArea createHelpText(String style) {
        DDHtmlArea help = new DDHtmlArea(GuiManager.DEFAULT, style);
        help.setDisplayOnly(true);
        help.setOpaque(true);
        help.setBorder(BorderFactory.createEmptyBorder(EngineUtils.STANDARD_BORDER_GAP, EngineUtils.STANDARD_BORDER_GAP,
                EngineUtils.STANDARD_BORDER_GAP, EngineUtils.STANDARD_BORDER_GAP));
        help.setPreferredSize(new Dimension(10000, 40)); // fix height as diff fonts can make it twitchy
        return help;
    }

    /**
     * Builds the shared HTML description of a site: bold display name, muted {@code (id)}, and
     * muted config path.  Used by the site combo renderer and the caption editor's top bar.
     * The fragment is <em>not</em> wrapped in {@code <html>} tags, so callers can append more and
     * wrap it themselves.
     */
    public static String siteLabelHtml(Site site) {
        String name = escapeHtml(site.getDisplayName() != null ? site.getDisplayName() : "");
        String id = escapeHtml(site.getIdOrDefault());
        StringBuilder html = new StringBuilder("<b>").append(name)
                .append("</b> <span style='color:#808080'>(").append(id).append(")</span>");
        String cfg = site.getActualConfigPath();
        if (cfg != null && !cfg.isBlank()) {
            html.append(" <span style='color:#5B7C99'>").append(escapeHtml(cfg)).append("</span>");
        }
        return html.toString();
    }

    /** Escapes the HTML metacharacters {@code & < >} for safe inclusion in an HTML label. */
    public static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public static Site openAddSiteDialog(AppContext context, SitesFile sitesFile) {
        return openAddSiteDialog(context, sitesFile, null);
    }

    public static Site openAddSiteDialog(AppContext context, SitesFile sitesFile, Path initialDir) {
        Set<Site> before = new HashSet<>(sitesFile.getSites());

        TypedHashMap params = new TypedHashMap();
        params.setObject(SiteDialog.PARAM_SITES_FILE, sitesFile);
        params.setObject("dialog-windowtitle-prop", "msg.windowtitle.AddSiteDialog");
        if (initialDir != null) {
            params.setObject(SiteDialog.PARAM_INITIAL_DIR, initialDir);
            params.setString(SiteDialog.PARAM_INITIAL_DISPLAY_NAME, "My Photos");
        }
        context.processPhaseNow("SiteDialog", params);

        return sitesFile.getSites().stream()
                .filter(s -> !before.contains(s))
                .findFirst()
                .orElse(null);
    }
}
