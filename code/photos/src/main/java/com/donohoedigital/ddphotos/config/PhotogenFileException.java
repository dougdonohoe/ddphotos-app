package com.donohoedigital.ddphotos.config;

public class PhotogenFileException extends Exception {
    public PhotogenFileException(String message) {
        super(message);
    }

    public PhotogenFileException(String message, Throwable cause) {
        super(message, cause);
    }
}
