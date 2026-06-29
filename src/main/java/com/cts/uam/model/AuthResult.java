package com.cts.uam.model;

import com.cts.uam.enums.AuthResultReason;

/**
 * Simple holder for the outcome of an authentication attempt.
 * Carries the reason and (when useful) the matched user — e.g. to
 * show "N attempts remaining" on a wrong password.
 */
public class AuthResult {

    public final AuthResultReason reason;
    public final User user;

    private AuthResult(AuthResultReason reason, User user) {
        this.reason = reason;
        this.user = user;
    }

    public boolean isSuccess() {
        return reason == AuthResultReason.SUCCESS;
    }

    public static AuthResult success(User user) {
        return new AuthResult(AuthResultReason.SUCCESS, user);
    }

    public static AuthResult failure(AuthResultReason reason) {
        return new AuthResult(reason, null);
    }

    public static AuthResult wrongPassword(User user) {
        return new AuthResult(AuthResultReason.WRONG_PASSWORD, user);
    }
}