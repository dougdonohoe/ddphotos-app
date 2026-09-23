package com.donohoedigital.ddphotos.sync;

import java.util.List;

/**
 * The providers the app can configure.  photogen also accepts {@code mock}, a test provider the
 * app recognizes and preserves but does not offer (see
 * {@link com.donohoedigital.ddphotos.config.SyncEntry#KNOWN_PROVIDERS}).
 */
public final class SyncProviders {

    private static final List<SyncProvider> ALL = List.of(ImmichProvider.INSTANCE);

    private SyncProviders() {}

    /** Providers offered in the UI, in display order. */
    public static List<SyncProvider> forUi() {
        return ALL;
    }

    /** The provider with the given {@code albums.yaml} key, or null when the app does not offer it. */
    public static SyncProvider get(String id) {
        for (SyncProvider p : ALL) {
            if (p.id().equals(id)) return p;
        }
        return null;
    }

    /** The provider selected by default for a new synced album. */
    public static SyncProvider getDefault() {
        return ALL.getFirst();
    }
}
