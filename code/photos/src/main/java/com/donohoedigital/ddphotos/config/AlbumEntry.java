package com.donohoedigital.ddphotos.config;

import java.util.Objects;

public class AlbumEntry {
    private String slug;
    private String name;
    private String description;
    private String base;
    private String source;
    private String cover;
    private boolean manualSortOrder;
    private boolean recurse;
    private SyncEntry sync;

    public AlbumEntry() {}

    public AlbumEntry(AlbumEntry other) {
        this.slug          = other.slug;
        this.name          = other.name;
        this.description   = other.description;
        this.base          = other.base;
        this.source        = other.source;
        this.cover         = other.cover;
        this.manualSortOrder = other.manualSortOrder;
        this.recurse       = other.recurse;
        this.sync          = other.sync == null ? null : new SyncEntry(other.sync);
    }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getBase() { return base; }
    public void setBase(String base) { this.base = base; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getCover() { return cover; }
    public void setCover(String cover) { this.cover = cover; }

    public boolean isManualSortOrder() { return manualSortOrder; }
    public void setManualSortOrder(boolean manualSortOrder) { this.manualSortOrder = manualSortOrder; }

    public boolean isRecurse() { return recurse; }
    public void setRecurse(boolean recurse) { this.recurse = recurse; }

    /** The {@code sync:} block; null for an album read from a local folder. */
    public SyncEntry getSync() { return sync; }
    public void setSync(SyncEntry sync) { this.sync = sync; }

    /** True when photogen downloads this album from a provider rather than reading {@code source}. */
    public boolean isSynced() { return sync != null; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AlbumEntry e)) return false;
        return manualSortOrder == e.manualSortOrder
            && recurse == e.recurse
            && Objects.equals(slug,        e.slug)
            && Objects.equals(name,        e.name)
            && Objects.equals(description, e.description)
            && Objects.equals(base,        e.base)
            && Objects.equals(source,      e.source)
            && Objects.equals(cover,       e.cover)
            && Objects.equals(sync,        e.sync);
    }

    @Override
    public int hashCode() {
        return Objects.hash(slug, name, description, base, source, cover, manualSortOrder, recurse, sync);
    }
}
