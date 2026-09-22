package com.donohoedigital.ddphotos.sync;

import java.util.List;

/**
 * A connection to one provider instance with one set of credentials.  Calls block on the
 * network, so the UI makes them off the event thread.
 */
public interface SyncClient {

    /**
     * Checks that the server is reachable and accepts the credentials, and reports any
     * permission photogen needs that they lack.  A bad key or an unreachable server throws.
     */
    ConnectionTest test() throws SyncException;

    /** Every album the credentials can see, in the provider's order. */
    List<SyncAlbumInfo> listAlbums() throws SyncException;
}
