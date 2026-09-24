package com.donohoedigital.ddphotos.sync;

import java.util.List;

/**
 * The result of a successful connection test: the server answered and accepted the key.
 *
 * @param missingPermissions   permissions the key needs but lacks
 * @param uncheckedPermissions permissions the key needs that the server could not report on, so
 *                             they may or may not be granted
 */
public record ConnectionTest(List<String> missingPermissions, List<String> uncheckedPermissions) {
    /** True only when every needed permission was checked and is granted. */
    public boolean isComplete() {
        return missingPermissions.isEmpty() && uncheckedPermissions.isEmpty();
    }
}
