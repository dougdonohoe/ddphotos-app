package com.donohoedigital.ddphotos;

import com.donohoedigital.app.config.AppConfigUtils;
import com.donohoedigital.app.engine.AppContext;
import com.donohoedigital.base.TypedHashMap;
import com.donohoedigital.ddphotos.config.Site;
import com.donohoedigital.ddphotos.config.SitesFile;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public class PhotosUtils {

    /** Path to the private ddphotos wrapper script installed by the New User wizard. */
    public static Path scriptPath() {
        return AppConfigUtils.getBinDir().toPath().resolve("ddphotos");
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
