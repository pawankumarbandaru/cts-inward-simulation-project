package com.cts.exceptions;

/**
 * File    : CtsBusinessException.java
 * Package : com.cts.exceptions
 * Purpose : Base exception class for all custom CTS application exceptions.
 *           Extends RuntimeException so callers are not forced to use try-catch
 *           everywhere, but specific subtypes can still be caught when needed.
 * Author  : Ramana
 * Date    : 24-06-2025
 */
public class CtsBusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CtsBusinessException(String message) {
        super(message);
    }

    public CtsBusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}