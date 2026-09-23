package com.donohoedigital.ddphotos.config;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * An album's {@code sync:} block: the album is downloaded from an upstream photo manager by
 * photogen instead of being read from a local folder (see ddphotos
 * {@code pkg/photogen/albums_config.go} {@code SyncEntry}).
 *
 * <p>{@code captions} is a {@link Boolean} so an absent key can be told apart from an explicit
 * {@code false}; absent means true, as it does in photogen.  The {@code mock:} sub-block is a
 * test fixture for photogen and is not editable here.  It is read so validation can match
 * photogen, and it survives a save because the album's {@code sync} node is edited in place.
 */
public class SyncEntry {

    /** The Immich provider key, as written in {@code albums.yaml}. */
    public static final String PROVIDER_IMMICH = "immich";

    /** photogen's offline test provider.  Recognized, preserved, never offered in the UI. */
    public static final String PROVIDER_MOCK = "mock";

    /** Every provider photogen accepts, sorted as photogen lists them in its errors. */
    public static final List<String> KNOWN_PROVIDERS = List.of(PROVIDER_IMMICH, PROVIDER_MOCK);

    /**
     * photogen's {@code validateImmichAlbumID}: 8-4-4-4-12 hex, either case.  Deliberately
     * looser than Immich's own check, so a later Immich id format is not rejected by guesswork.
     */
    private static final Pattern IMMICH_ALBUM_ID = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    public static boolean isImmichAlbumId(String id) {
        return id != null && IMMICH_ALBUM_ID.matcher(id).matches();
    }

    private String provider;
    private String albumId;
    private Boolean captions;
    private MockSyncEntry mock;

    public SyncEntry() {}

    public SyncEntry(String provider, String albumId, Boolean captions) {
        this.provider = provider;
        this.albumId  = albumId;
        this.captions = captions;
    }

    public SyncEntry(SyncEntry other) {
        this.provider = other.provider;
        this.albumId  = other.albumId;
        this.captions = other.captions;
        this.mock     = other.mock == null ? null : new MockSyncEntry(other.mock);
    }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getAlbumId() { return albumId; }
    public void setAlbumId(String albumId) { this.albumId = albumId; }

    /** The raw {@code captions} value; null when the key is absent. */
    public Boolean getCaptions() { return captions; }
    public void setCaptions(Boolean captions) { this.captions = captions; }

    /** Whether photogen maintains {@code photogen.txt} from upstream descriptions (default true). */
    public boolean isCaptionsEnabled() { return captions == null || captions; }

    public MockSyncEntry getMock() { return mock; }
    public void setMock(MockSyncEntry mock) { this.mock = mock; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SyncEntry e)) return false;
        return isCaptionsEnabled() == e.isCaptionsEnabled()
            && Objects.equals(provider, e.provider)
            && Objects.equals(albumId,  e.albumId)
            && Objects.equals(mock,     e.mock);
    }

    @Override
    public int hashCode() {
        return Objects.hash(provider, albumId, isCaptionsEnabled(), mock);
    }

    /** The {@code sync.mock:} sub-block (ddphotos {@code MockSyncEntry}). */
    public static class MockSyncEntry {
        private String assets;
        private String mediaDir;
        private String fail;

        public MockSyncEntry() {}

        public MockSyncEntry(MockSyncEntry other) {
            this.assets   = other.assets;
            this.mediaDir = other.mediaDir;
            this.fail     = other.fail;
        }

        public String getAssets() { return assets; }
        public void setAssets(String assets) { this.assets = assets; }

        public String getMediaDir() { return mediaDir; }
        public void setMediaDir(String mediaDir) { this.mediaDir = mediaDir; }

        public String getFail() { return fail; }
        public void setFail(String fail) { this.fail = fail; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof MockSyncEntry m)) return false;
            return Objects.equals(assets, m.assets)
                && Objects.equals(mediaDir, m.mediaDir)
                && Objects.equals(fail, m.fail);
        }

        @Override
        public int hashCode() {
            return Objects.hash(assets, mediaDir, fail);
        }
    }
}
