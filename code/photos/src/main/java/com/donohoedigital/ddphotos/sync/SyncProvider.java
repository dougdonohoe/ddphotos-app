package com.donohoedigital.ddphotos.sync;

import com.donohoedigital.ddphotos.config.AlbumsFile;

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

    /** Whether the site has the credentials this provider needs. */
    boolean hasCredentials(AlbumsFile albumsFile);

    /** The dialog phase (in {@code appdef.xml}) that edits this provider's credentials. */
    String credentialsDialogPhase();

    /** A client using the site's saved credentials. */
    SyncClient client(AlbumsFile albumsFile) throws SyncException;
}
