package com.donohoedigital.ddphotos.sync;

import com.donohoedigital.ddphotos.config.AlbumsFile;
import com.donohoedigital.ddphotos.config.SyncCredentialsFile;

/**
 * One kind of upstream photo manager an album can sync from.  Implementations are stateless
 * singletons, registered in {@link SyncProviders}; the per-site state is the credentials file,
 * found through the site's {@link AlbumsFile}.
 */
public interface SyncProvider {

    /** The key written to {@code sync.provider} in {@code albums.yaml}. */
    String id();

    /** The name shown to the user. */
    String displayName();

    /** Whether {@code albumId} has the shape this provider's album ids have. */
    boolean isValidAlbumId(String albumId);

    /** Which credentials file this provider reads. */
    SyncCredentialsFile.Kind credentialsKind();

    /** The pattern a valid secret (API key or app password) matches, for the credentials dialog. */
    String secretRegExp();

    /**
     * The prefix of this provider's names in {@code client.properties}, such as
     * {@code immich} in {@code label.immichurl} and {@code msg.immichcreds.instructions}.
     */
    String messagePrefix();

    /** The dialog phase (in {@code appdef.xml}) that edits this provider's credentials. */
    String credentialsDialogPhase();

    /** A client for the given URL and secret, as typed or as saved. */
    HttpSyncClient client(String url, String secret) throws SyncException;

    /** The site's credentials file for this provider; null when the config dir is unknown. */
    default SyncCredentialsFile credentialsFile(AlbumsFile albumsFile) {
        return albumsFile != null ? albumsFile.getSyncCredentialsFile(credentialsKind()) : null;
    }

    /** Whether the site has the credentials this provider needs. */
    default boolean hasCredentials(AlbumsFile albumsFile) {
        SyncCredentialsFile f = credentialsFile(albumsFile);
        return f != null && f.isComplete();
    }

    /** A client using the site's saved credentials. */
    default SyncClient client(AlbumsFile albumsFile) throws SyncException {
        SyncCredentialsFile f = credentialsFile(albumsFile);
        if (f == null || !f.isComplete()) {
            throw new SyncException(displayName() + " credentials are not set for this site.");
        }
        return client(f.getUrl(), f.getSecret());
    }
}
