package com.donohoedigital.ddphotos;

import com.donohoedigital.ddphotos.config.AlbumEntry;
import com.donohoedigital.ddphotos.config.SyncEntry;
import com.donohoedigital.ddphotos.config.AlbumsFile;
import com.donohoedigital.ddphotos.config.SyncMetadataFile;
import com.donohoedigital.ddphotos.sync.SyncAlbumInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers {@link SyncUi#switchesSyncedAlbum}, which decides when saving an album should warn that
 * it now syncs a different upstream album, and {@link SyncUi#startFresh}, which clears what
 * belonged to the old upstream album once the user goes ahead.
 */
public class SyncUiTest {

    @TempDir
    Path tmp;

    private static final String ID_A = "ef8acfb8-43fb-4c63-90c0-307b88b8f97a";
    private static final String ID_B = "9c16a3ab-74c9-4067-9335-c0092b9afb4c";

    private static AlbumEntry synced(String provider, String albumId) {
        AlbumEntry e = new AlbumEntry();
        e.setSlug("trip");
        e.setSync(new SyncEntry(provider, albumId, true));
        return e;
    }

    private static AlbumEntry local() {
        AlbumEntry e = new AlbumEntry();
        e.setSlug("trip");
        e.setName("Trip");
        e.setSource("/tmp/trip");
        return e;
    }

    @Test
    public void differentAlbum_warns() {
        // Whether or not photogen has synced it yet: the name, description and cover set so far
        // belong to the old album either way.
        assertTrue(SyncUi.switchesSyncedAlbum(synced("immich", ID_A), synced("immich", ID_B)));
    }

    @Test
    public void otherProvider_warns() {
        assertTrue(SyncUi.switchesSyncedAlbum(synced("immich", ID_A), synced("mock", ID_A)));
        assertTrue(SyncUi.switchesSyncedAlbum(synced("immich", ID_A), synced("photoprism", "atmyuuj99diyoq1s")));
    }

    @Test
    public void sameAlbum_doesNotWarn() {
        assertFalse(SyncUi.switchesSyncedAlbum(synced("immich", ID_A), synced("immich", ID_A.toUpperCase())));
    }

    @Test
    public void toOrFromLocal_doesNotWarn() {
        assertFalse(SyncUi.switchesSyncedAlbum(synced("immich", ID_A), local()));
        assertFalse(SyncUi.switchesSyncedAlbum(local(), synced("immich", ID_B)));
    }

    @Test
    public void startFresh_clearsWhatBelongedToTheOldAlbum() {
        AlbumEntry e = synced("immich", ID_B);
        e.setName("Camino");
        e.setDescription("Walking");
        e.setCover("IMG_1234.jpg");
        e.setManualSortOrder(true);

        SyncUi.startFresh(e);

        assertNull(e.getName());
        assertNull(e.getDescription());
        assertNull(e.getCover());
        assertEquals("trip", e.getSlug(), "the slug is the user's, not the old album's");
        assertEquals(ID_B, e.getSync().getAlbumId());
        assertTrue(e.isManualSortOrder(), "a setting, kept");
    }

    @Test
    public void writeStub_leavesAnUnreadableMetadataFileAlone() throws Exception {
        // photogen's sync record; replacing it with a stub loses what the next sync compares against.
        AlbumsFile af = new AlbumsFile();
        af.setSiteDir(tmp);
        af.getSettings().setId("site");
        AlbumEntry entry = synced("immich", ID_A);
        Path dir = af.resolveSyncPath(entry);
        Files.createDirectories(dir);
        Path f = dir.resolve(SyncMetadataFile.FILE_NAME);
        String broken = "provider: immich\nalbum_id: [unclosed\n";
        Files.writeString(f, broken);

        SyncUi.writeStub(af, entry, new SyncAlbumInfo(ID_A, "Trip", "", 1, "me"));

        assertEquals(broken, Files.readString(f));
    }
}
