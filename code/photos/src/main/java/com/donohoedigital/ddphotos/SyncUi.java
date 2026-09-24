package com.donohoedigital.ddphotos;

import com.donohoedigital.app.engine.AppContext;
import com.donohoedigital.base.TypedHashMap;
import com.donohoedigital.ddphotos.config.AlbumEntry;
import com.donohoedigital.ddphotos.config.AlbumsFile;
import com.donohoedigital.ddphotos.config.Site;
import com.donohoedigital.ddphotos.config.SyncMetadataFile;
import com.donohoedigital.ddphotos.config.SyncText;
import com.donohoedigital.ddphotos.sync.SyncAlbumInfo;
import com.donohoedigital.ddphotos.sync.SyncProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** UI glue shared by the places that configure a synced album. */
final class SyncUi {

    private static final Logger logger = LogManager.getLogger(SyncUi.class);

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

    /**
     * Whether saving {@code updated} over {@code saved} points a synced album at a different
     * upstream album: another album id, or another provider.  The next photogen run replaces the
     * album's photos, and the name, description and cover set so far belong to the old album.
     */
    static boolean switchesSyncedAlbum(AlbumEntry saved, AlbumEntry updated) {
        if (!saved.isSynced() || !updated.isSynced()) return false;
        String was = saved.getSync().getAlbumId();
        String now = updated.getSync().getAlbumId();
        return !Objects.equals(saved.getSync().getProvider(), updated.getSync().getProvider())
                || (was == null ? now != null : !was.equalsIgnoreCase(now));
    }

    /**
     * Clears what belonged to the previous upstream album once the user confirms a switch (see
     * {@link #switchesSyncedAlbum}): the name and description overrides, which the new album's
     * own values replace, and the cover, a photo the next photogen run prunes.
     */
    static void startFresh(AlbumEntry album) {
        album.setName(null);
        album.setDescription(null);
        album.setCover(null);
    }

    /**
     * Writes the stub {@code metadata.yaml} for a newly chosen album, unless the sync folder
     * already holds a record for this same upstream album (left behind by an earlier album with
     * this slug, say), which is better than a stub.
     */
    static void writeStub(AlbumsFile af, AlbumEntry entry, SyncAlbumInfo info) {
        Path dir = af.resolveSyncPath(entry);
        if (dir == null) return;
        SyncMetadataFile existing = new SyncMetadataFile(dir).load();
        if (existing.existsOnDisk() && entry.getSync().getAlbumId().equalsIgnoreCase(existing.getAlbumId())) return;
        try {
            SyncMetadataFile.writeStub(dir, entry.getSync().getProvider(), entry.getSync().getAlbumId(),
                                       info.name(), SyncText.escape(info.description()));
        } catch (IOException e) {
            // Only a display convenience: photogen writes the real file on its first sync.
            logger.warn("Could not write {}: {}", dir.resolve(SyncMetadataFile.FILE_NAME), e.toString());
        }
    }
}
