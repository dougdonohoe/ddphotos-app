package com.donohoedigital.ddphotos.sync;

/**
 * One upstream album as a provider lists it, for the album chooser.
 *
 * @param id          the provider's album id, as written to {@code sync.album_id}
 * @param name        the upstream name, raw
 * @param description the upstream description, raw (not yet escaped for HTML)
 * @param assetCount  photos and videos in the album, or -1 when the provider does not say
 * @param owner       the owner's display name, or null
 */
public record SyncAlbumInfo(String id, String name, String description, int assetCount, String owner) {}
