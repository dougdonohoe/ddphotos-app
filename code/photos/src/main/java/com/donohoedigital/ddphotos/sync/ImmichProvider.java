package com.donohoedigital.ddphotos.sync;

import com.donohoedigital.ddphotos.PhotosConstants;
import com.donohoedigital.ddphotos.config.SyncCredentialsFile;
import com.donohoedigital.ddphotos.config.SyncEntry;

/** <a href="https://immich.app">Immich</a>, with credentials in the site's {@code immich.env}. */
public final class ImmichProvider implements SyncProvider {

    public static final ImmichProvider INSTANCE = new ImmichProvider();

    private ImmichProvider() {}

    @Override public String id() { return SyncEntry.PROVIDER_IMMICH; }

    @Override public String displayName() { return "Immich"; }

    @Override public boolean isValidAlbumId(String albumId) { return SyncEntry.isImmichAlbumId(albumId); }

    @Override public SyncCredentialsFile.Kind credentialsKind() { return SyncCredentialsFile.IMMICH; }

    @Override public String secretRegExp() { return PhotosConstants.REGEXP_IMMICH_API_KEY; }

    @Override public String messagePrefix() { return "immich"; }

    @Override public String credentialsDialogPhase() { return "ImmichCredentialsDialog"; }

    @Override
    public HttpSyncClient client(String url, String secret) throws SyncException {
        return new ImmichClient(url, secret);
    }
}
