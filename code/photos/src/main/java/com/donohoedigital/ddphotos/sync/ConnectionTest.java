package com.donohoedigital.ddphotos.sync;

import java.util.List;

/**
 * The result of a successful connection test: the server answered and accepted the key.
 *
 * @param keyName            the key's name upstream, or null
 * @param missingPermissions permissions the key needs but lacks; empty when it has them all
 */
public record ConnectionTest(String keyName, List<String> missingPermissions) {
    public boolean isComplete() {
        return missingPermissions.isEmpty();
    }
}
