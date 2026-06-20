package com.donohoedigital.base;

public class ErrorCodes
{
    // Error codes - system
    public static final int ERROR_UNEXPECTED_EXCEPTION = 1000;
    public static final int ERROR_NULL = 1001;
    public static final int ERROR_ASSERTION_FAILED = 1002;

    // Error codes - internal
    public static final int ERROR_CODE_ERROR = 2000;
    public static final int ERROR_CLASS_NOT_FOUND = 2001;

    // Error codes - config
    public static final int ERROR_FILE_NOT_FOUND = 3000;
    public static final int ERROR_JDOM_PARSE_FAILED = 3001;
    public static final int ERROR_RENAME = 3002;
    public static final int ERROR_XSD_PARSE_FAILED = 3003;
    public static final int ERROR_CREATE = 3004;

    // Error codes - validation
    public static final int ERROR_VALIDATION = 4000;
    public static final int ERROR_NOT_FOUND = 4001;
}