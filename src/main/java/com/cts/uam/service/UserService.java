package com.cts.uam.service;

import java.util.Optional;

import com.cts.uam.model.AuthResult;
import com.cts.uam.model.User;

public interface UserService {

    /**
     * Authenticate the user.
     */
    AuthResult authenticate(String username, String password);

    /**
     * Find user by username.
     */
    Optional<User> findByUsername(String username);

    /**
     * Change logged-in user's password.
     */
    void changeOwnPassword(String username,
                           String currentPassword,
                           String newPassword);
}