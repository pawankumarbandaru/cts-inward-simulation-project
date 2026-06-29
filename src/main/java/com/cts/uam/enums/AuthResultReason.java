package com.cts.uam.enums;

/**
 * Outcome of a login attempt. The LoginComposer uses this to show
 * the correct message to the user.
 */
public enum AuthResultReason {
    SUCCESS,
    USER_NOT_FOUND,
    ACCOUNT_LOCKED,
    ACCOUNT_INACTIVE,
    WRONG_PASSWORD
}