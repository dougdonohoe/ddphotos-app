package com.donohoedigital.ddphotos.sync;

import com.donohoedigital.ddphotos.PhotosConstants;
import com.donohoedigital.ddphotos.config.SyncCredentialsFile;
import com.donohoedigital.ddphotos.config.SyncEntry;

/** <a href="https://www.photoprism.app">PhotoPrism</a>, with credentials in the site's {@code photoprism.env}. */
public final class PhotoPrismProvider implements SyncProvider {

    public static final PhotoPrismProvider INSTANCE = new PhotoPrismProvider();

    private PhotoPrismProvider() {}

    @Override public String id() { return SyncEntry.PROVIDER_PHOTOPRISM; }

    @Override public String displayName() { return "PhotoPrism"; }

    @Override public boolean isValidAlbumId(String albumId) { return SyncEntry.isPhotoPrismAlbumId(albumId); }

    @Override public SyncCredentialsFile.Kind credentialsKind() { return SyncCredentialsFile.PHOTOPRISM; }

    @Override public String secretRegExp() { return PhotosConstants.REGEXP_PHOTOPRISM_APP_PASSWORD; }

    @Override public String messagePrefix() { return "photoprism"; }

    @Override public String credentialsDialogPhase() { return "PhotoPrismCredentialsDialog"; }

    @Override
    public HttpSyncClient client(String url, String secret) throws SyncException {
        return new PhotoPrismClient(url, secret);
    }
}
