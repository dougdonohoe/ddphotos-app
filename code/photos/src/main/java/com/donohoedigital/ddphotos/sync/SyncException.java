package com.donohoedigital.ddphotos.sync;

/** A provider call failed.  The message is written for the user and never contains a secret. */
public class SyncException extends Exception {
    public SyncException(String message) {
        super(message);
    }

    public SyncException(String message, Throwable cause) {
        super(message, cause);
    }
}
