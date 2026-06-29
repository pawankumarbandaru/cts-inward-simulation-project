package com.cts.uam.dao;

import java.util.Optional;

import com.cts.uam.model.User;

public interface UserDAO {

    /**
     * Find a user by exact username.
     */
    Optional<User> findByUsername(String username);

    /**
     * Increment failed login attempts.
     */
    void incrementFailedAttempts(User user);

    /**
     * Record successful login.
     */
    void recordSuccessfulLogin(User user);

    /**
     * Update password hash.
     */
    void updatePasswordHash(long userId, String newPasswordHash);
}