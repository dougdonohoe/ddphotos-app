package com.donohoedigital.ddphotos.config;

import java.nio.file.Path;

/**
 * The part every config file model has in common: it was read from a particular file at a
 * particular moment, and that file may since have been rewritten by something other than this app
 * - a Claude session editing albums, a vim session fixing a caption.
 *
 * <p>Subclasses take a {@link FileStamp} of their target at the end of every successful load and
 * every successful write, via {@link #restamp()}.  Stamping on <em>write</em> is what keeps the
 * app's own saves from reading as external changes: by the time {@code save()} returns, the model
 * and the file agree again.  That single call inside each save method covers every call site that
 * saves through it, so no caller has to remember anything.
 *
 * <p>{@link #getPath()} is abstract rather than a field here because each subclass already stores
 * its own path (and {@link AlbumsFile} can legitimately have none until it is first written).
 */
public abstract class ConfigFile {

    /** What the file looked like when this model was last in step with it. */
    private FileStamp stamp_ = FileStamp.MISSING;

    /** The file this model was read from, or null when it has no location yet. */
    public abstract Path getPath();

    /**
     * True when the file has been written by someone else since this model was loaded or last
     * saved.  False for a model with no path - there is nothing it could disagree with.
     */
    public final boolean isChangedOnDisk() {
        Path path = getPath();
        if (path == null) return false;
        return !stamp_.equals(FileStamp.of(path));
    }

    /**
     * Records the file's current state as this model's baseline, so {@link #isChangedOnDisk()}
     * reads false until it is written again.
     */
    public final void restamp() {
        stamp_ = FileStamp.of(getPath());
    }
}
