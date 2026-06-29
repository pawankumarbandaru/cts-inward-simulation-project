package com.cts.composer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.Session;
import org.zkoss.zk.ui.Sessions;
import org.zkoss.zk.ui.select.SelectorComposer;
import org.zkoss.zk.ui.select.annotation.Listen;
import org.zkoss.zk.ui.select.annotation.Wire;
import org.zkoss.zul.Button;
import org.zkoss.zul.Label;
import org.zkoss.zul.Textbox;

import com.cts.security.SessionKeys;
import com.cts.uam.model.AuthResult;
import com.cts.uam.model.User;
import com.cts.uam.service.UserService;
import com.cts.uam.service.UserServiceImpl;

/**
 * LoginComposer — Inward ClearPay CTS
 *
 * Authenticates against the shared cts_users table using UserService
 * (BCrypt + account lockout). On success, stores the User object in
 * the ZK session and redirects based on role_label.
 *
 * Role → landing page:
 *   MAKER → /zul/dashboard.zul
 *   TV1   → /component/checkerDashboard.zul
 *   TV2   → /component/tv2Dashboard.zul
 */
public class LoginComposer extends SelectorComposer<Component> {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(LoginComposer.class);

    // Keep in sync with UserDAO lockout rule
    private static final int MAX_FAILED_ATTEMPTS = 5;

    @Wire("#userId")
    private Textbox usernameField;

    @Wire("#password")
    private Textbox passwordField;

    @Wire("#btnLogin")
    private Button loginButton;

    @Wire("#lblError")
    private Label errorLabel;

    private final UserService userService = new UserServiceImpl();

    @Override
    public void doAfterCompose(Component comp) throws Exception {
        super.doAfterCompose(comp);

        // If already logged in, skip the login page
        if (isUserAlreadyLoggedIn()) {
            Executions.sendRedirect(homePageFor(currentUser()));
            return;
        }

        if (usernameField != null) {
            usernameField.setFocus(true);
        }
        if (errorLabel != null) {
            errorLabel.setVisible(false);
        }
    }

    @Listen("onClick = #btnLogin; onOK = #userId; onOK = #password")
    public void onLogin() {
        clearError();

        String username = usernameField.getValue().trim();
        String password = passwordField.getValue();

        if (username.isEmpty()) {
            showError("Please enter your User ID.");
            usernameField.setFocus(true);
            return;
        }
        if (password.isEmpty()) {
            showError("Please enter your Password.");
            passwordField.setFocus(true);
            return;
        }

        // Prevent double-submit while authenticating
        loginButton.setDisabled(true);
        loginButton.setLabel("Authenticating...");
        try {
            authenticate(username, password);
        } finally {
            loginButton.setDisabled(false);
            loginButton.setLabel("LOGIN TO SYSTEM");
        }
    }

    // ── Authentication ─────────────────────────────────────────────

    private void authenticate(String username, String password) {

        AuthResult result = userService.authenticate(username, password);

        switch (result.reason) {

            case USER_NOT_FOUND ->
                showError("Invalid User ID or Password.");

            case ACCOUNT_LOCKED ->
                showError("Your account is temporarily locked due to too many "
                        + "failed attempts. Please try again after 30 minutes "
                        + "or contact your administrator.");

            case ACCOUNT_INACTIVE ->
                showError("Your account is not active. Please contact your administrator.");

            case WRONG_PASSWORD -> {
                User failed = result.user;
                int remaining = (failed != null)
                        ? Math.max(0, MAX_FAILED_ATTEMPTS - failed.getFailedAttempts())
                        : 0;
                if (remaining == 0) {
                    showError("Too many failed attempts. Account locked for 30 minutes.");
                } else {
                    showError("Invalid User ID or Password. " + remaining + " attempt(s) remaining.");
                }
            }

            case SUCCESS -> {
                User user = result.user;
                storeUserInSession(user);
                LOG.info("Inward login success for user '{}' (role={})",
                        user.getUsername(), user.getRoleLabel());
                Executions.sendRedirect(homePageFor(user));
            }
        }
    }

    // ── Session helpers ────────────────────────────────────────────

    private void storeUserInSession(User user) {
        Session session = Sessions.getCurrent();
        session.setAttribute(SessionKeys.CURRENT_USER, user);
    }

    private boolean isUserAlreadyLoggedIn() {
        return currentUser() != null;
    }

    private User currentUser() {
        Session session = Sessions.getCurrent();
        Object obj = (session != null) ? session.getAttribute(SessionKeys.CURRENT_USER) : null;
        return (obj instanceof User user) ? user : null;
    }

    /** Decide the landing page from the user's role_label. */
    private String homePageFor(User user) {
        String role = (user != null && user.getRoleLabel() != null)
                ? user.getRoleLabel().trim().toUpperCase()
                : "";

        // IMPORTANT: keep the ?role= parameter. The inward dashboards read it
        // (DashboardComposer / CheckerDashboardComposer / TV2DashboardComposer)
        // and store it on the desktop as "userRole", which the cheque popup
        // and list composers depend on. Dropping it breaks Maker's edit/save.
        return switch (role) {
            case "TV1" -> "/component/checkerDashboard.zul?role=TV1";
            case "TV2" -> "/component/tv2Dashboard.zul?role=TV2";
            default    -> "/zul/dashboard.zul?role=MAKER"; // MAKER and fallback
        };
    }

    // ── Error display ──────────────────────────────────────────────

    private void showError(String message) {
        if (errorLabel != null) {
            errorLabel.setValue(message);
            errorLabel.setVisible(true);
        }
        // Never leave the password on screen after an error
        if (passwordField != null) {
            passwordField.setValue("");
            passwordField.setFocus(true);
        }
    }

    private void clearError() {
        if (errorLabel != null) {
            errorLabel.setValue("");
            errorLabel.setVisible(false);
        }
    }
}