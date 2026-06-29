package com.cts.uam.enums;

/**
 * Lifecycle status of a user account (shared cts_users table).
 * Only ACTIVE users may log in. LOCKED / DISABLED / TERMINATED are blocked.
 */
public enum UserStatus {
    PENDING,
    ACTIVE,
    REJECTED,
    INACTIVE,
    LOCKED,
    DISABLED,
    TERMINATED
}