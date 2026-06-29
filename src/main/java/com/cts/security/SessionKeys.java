package com.cts.security;

/**
 * Central place for session attribute names used across the inward app.
 * Keeping them here avoids typos like "loggedUser" vs "loggeduser"
 * in different files.
 */
public final class SessionKeys {

    private SessionKeys() {
    }

    /** The logged-in User object (com.cts.uam.model.User). */
    public static final String CURRENT_USER = "CURRENT_USER";
}