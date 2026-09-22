package com.donohoedigital.ddphotos.sync;

import com.donohoedigital.ddphotos.config.AlbumsFile;
import com.donohoedigital.ddphotos.config.ImmichCredentialsFile;
import com.donohoedigital.ddphotos.config.SyncEntry;

/** <a href="https://immich.app">Immich</a>, with credentials in the site's {@code immich.env}. */
public final class ImmichProvider implements SyncProvider {

    public static final ImmichProvider INSTANCE = new ImmichProvider();

    private ImmichProvider() {}

    @Override public String id() { return SyncEntry.PROVIDER_IMMICH; }

    @Override public String displayName() { return "Immich"; }

    @Override public boolean isValidAlbumId(String albumId) { return SyncEntry.isImmichAlbumId(albumId); }

    @Override
    public boolean hasCredentials(AlbumsFile albumsFile) {
        ImmichCredentialsFile f = albumsFile != null ? albumsFile.getImmichCredentialsFile() : null;
        return f != null && f.isComplete();
    }

    @Override public String credentialsDialogPhase() { return "ImmichCredentialsDialog"; }

    @Override
    public SyncClient client(AlbumsFile albumsFile) throws SyncException {
        ImmichCredentialsFile f = albumsFile != null ? albumsFile.getImmichCredentialsFile() : null;
        if (f == null || !f.isComplete()) {
            throw new SyncException("Immich credentials are not set for this site.");
        }
        return new ImmichClient(f.getUrl(), f.getApiKey());
    }
}
