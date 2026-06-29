package com.cts.uam.service;

import java.util.Optional;

import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cts.uam.dao.UserDAO;
import com.cts.uam.dao.UserDAOImpl;
import com.cts.uam.enums.AuthResultReason;
import com.cts.uam.model.AuthResult;
import com.cts.uam.model.User;

public class UserServiceImpl implements UserService {

    private static final Logger LOG =
            LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserDAO userDAO = new UserDAOImpl();

    @Override
    public AuthResult authenticate(String username, String password) {

        Optional<User> found = userDAO.findByUsername(username);

        if (found.isEmpty()) {
            return AuthResult.failure(AuthResultReason.USER_NOT_FOUND);
        }

        User user = found.get();

        if (user.isLocked()) {
            return AuthResult.failure(AuthResultReason.ACCOUNT_LOCKED);
        }

        if (!user.isActive()) {
            return AuthResult.failure(AuthResultReason.ACCOUNT_INACTIVE);
        }

        boolean passwordMatches =
                BCrypt.checkpw(password, user.getPasswordHash());

        if (!passwordMatches) {

            userDAO.incrementFailedAttempts(user);

            User refreshed = userDAO.findByUsername(username).orElse(user);

            return refreshed.isLocked()
                    ? AuthResult.failure(AuthResultReason.ACCOUNT_LOCKED)
                    : AuthResult.wrongPassword(refreshed);
        }

        userDAO.recordSuccessfulLogin(user);

        LOG.info("User '{}' authenticated successfully.", username);

        return AuthResult.success(user);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return userDAO.findByUsername(username);
    }

    @Override
    public void changeOwnPassword(String username,
                                  String currentPassword,
                                  String newPassword) {

        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "User session not found. Please log in again.");
        }

        User user = userDAO.findByUsername(username)
                .orElseThrow(() ->
                        new IllegalArgumentException("User not found."));

        if (!BCrypt.checkpw(currentPassword,
                user.getPasswordHash())) {

            throw new IllegalArgumentException(
                    "Current password is incorrect.");
        }

        validatePasswordStrength(newPassword);

        String newHash =
                BCrypt.hashpw(newPassword, BCrypt.gensalt());

        userDAO.updatePasswordHash(user.getId(), newHash);

        LOG.info("User '{}' changed own password.", username);
    }

    /**
     * Password Rule:
     * - Minimum 8 characters
     * - One uppercase letter
     * - One digit
     * - One special character
     */
    private void validatePasswordStrength(String password) {

        if (password == null ||
            !password.matches(
                "^(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$")) {

            throw new IllegalArgumentException(
                "Password must be 8+ chars with 1 uppercase, 1 digit, and 1 special character.");
        }
    }
}