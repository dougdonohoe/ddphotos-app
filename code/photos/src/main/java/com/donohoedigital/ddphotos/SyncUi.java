package com.donohoedigital.ddphotos;

import com.donohoedigital.app.engine.AppContext;
import com.donohoedigital.base.TypedHashMap;
import com.donohoedigital.ddphotos.config.AlbumEntry;
import com.donohoedigital.ddphotos.config.AlbumsFile;
import com.donohoedigital.ddphotos.config.Site;
import com.donohoedigital.ddphotos.sync.SyncProvider;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** UI glue shared by the places that configure a synced album. */
final class SyncUi {

    private SyncUi() {}

    /**
     * Opens the provider's credentials dialog for a site and waits for it to close.  Every
     * credentials dialog takes the site as {@code "site"} and returns {@link Boolean#TRUE} on save.
     *
     * @return true when the user saved
     */
    static boolean editCredentials(AppContext context, Site site, SyncProvider provider) {
        TypedHashMap params = new TypedHashMap();
        params.setObject(ImmichCredentialsDialog.PARAM_SITE, site);
        return Boolean.TRUE.equals(context.processPhaseNow(provider.credentialsDialogPhase(), params).getResult());
    }

    /**
     * The album ids, lowercased, that albums other than {@code self} already sync from
     * {@code providerId}.  photogen rejects a second album syncing the same upstream album.
     */
    static Set<String> albumIdsInUse(AlbumsFile af, String providerId, AlbumEntry self) {
        Set<String> ids = new HashSet<>();
        if (af == null) return ids;
        for (AlbumEntry a : af.getAlbums()) {
            if (a == self || !a.isSynced() || a.getSync().getAlbumId() == null) continue;
            if (providerId.equals(a.getSync().getProvider())) {
                ids.add(a.getSync().getAlbumId().toLowerCase(Locale.ROOT));
            }
        }
        return ids;
    }
}
