package com.cts.security;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.Session;

import com.cts.uam.model.User;

/**
 * Central security helper for the inward app.
 *
 * Responsibilities:
 *   - Read the logged-in User from the ZK session.
 *   - Tell callers which page a role may open (role-based access).
 *   - Provide the login page + each role's landing page.
 *
 * Roles in inward: MAKER, TV1, TV2.
 */
public final class SecurityUtil {

    private static final Logger LOG = LoggerFactory.getLogger(SecurityUtil.class);

    /** Session attribute holding the logged-in User (set by LoginComposer). */
    public static final String SESSION_USER_KEY = SessionKeys.CURRENT_USER;

    /** Public pages reachable without logging in. */
    private static final Set<String> PUBLIC_PAGES = Set.of(
            "/", "/zul/index.zul");

    /** The login page (where we send unauthenticated users). */
    private static final String LOGIN_PAGE = "/zul/index.zul";

    private SecurityUtil() {
    }

    // ── Session reads ──────────────────────────────────────────────

    public static Session getCurrentSession() {
        return (Executions.getCurrent() != null)
                ? Executions.getCurrent().getSession()
                : null;
    }

    /** The logged-in User, or null if nobody is logged in. */
    public static User getCurrentUser() {
        Session session = getCurrentSession();
        Object obj = (session != null) ? session.getAttribute(SESSION_USER_KEY) : null;
        return (obj instanceof User user) ? user : null;
    }

    public static String getCurrentUserId() {
        User user = getCurrentUser();
        return (user != null) ? user.getUsername() : "unknown";
    }

    public static boolean isLoggedIn() {
        return getCurrentUser() != null;
    }

    /** Role label (MAKER / TV1 / TV2), or "" if none. */
    public static String getCurrentRole() {
        User user = getCurrentUser();
        return (user != null && user.getRoleLabel() != null)
                ? user.getRoleLabel().trim().toUpperCase()
                : "";
    }

    // ── Landing pages ──────────────────────────────────────────────

    public static String getLoginPage() {
        return LOGIN_PAGE;
    }

    /** Where a user goes right after login, based on role. */
    public static String getHomePageFor(User user) {
        String role = (user != null && user.getRoleLabel() != null)
                ? user.getRoleLabel().trim().toUpperCase()
                : "";
        return switch (role) {
            case "TV1" -> "/component/checkerDashboard.zul";
            case "TV2" -> "/component/tv2Dashboard.zul";
            default    -> "/zul/dashboard.zul"; // MAKER + fallback
        };
    }

    // ── Page access ────────────────────────────────────────────────

    /** Convenience overload using the current session user. */
    public static boolean canAccessPage(String page) {
        return canAccessPage(page, getCurrentUser());
    }

    /**
     * Role-based page access.
     * Rules:
     *   - Public pages: always allowed.
     *   - Not logged in: deny everything else.
     *   - Shared pages (header, sidebar components, common popups): any logged-in user.
     *   - MAKER:  /zul/dashboard.zul, /zul/inward/** maker pages.
     *   - TV1/TV2: their dashboards + /zul/inward/** review pages.
     *
     * To keep it simple and safe, all three roles may open inward
     * clearing pages (the page's own composer still controls actions);
     * outward pages are blocked for inward roles.
     */
    public static boolean canAccessPage(String page, User user) {
        String path = normalize(page);
        if (path.isEmpty()) {
            return false;
        }

        // 1) Public pages — no login needed
        if (PUBLIC_PAGES.contains(path)) {
            return true;
        }

        // 2) Must be logged in for anything else
        if (user == null) {
            return false;
        }

        String role = (user.getRoleLabel() != null)
                ? user.getRoleLabel().trim().toUpperCase()
                : "";

        // 3) Shared UI components — any logged-in user
        if (path.startsWith("/component/")) {
            // Component pages are dashboards/sidebars/popups used by all roles.
            // The role still only reaches its own dashboard via login redirect.
            return true;
        }

        // 4) Block outward pages for inward roles
        if (path.startsWith("/zul/outward/")) {
            return false;
        }

        // 5) Role rules for inward pages
        switch (role) {
            case "MAKER":
                // Maker dashboard + all inward clearing/maker pages
                return path.equals("/zul/dashboard.zul")
                        || path.startsWith("/zul/inward/");

            case "TV1":
            case "TV2":
                // Checkers also work inside inward pages; their dashboards
                // live under /component/ (already allowed in step 3).
                return path.equals("/zul/dashboard.zul")
                        || path.startsWith("/zul/inward/");

            default:
                LOG.warn("Unknown role '{}' for user '{}' accessing {}",
                        role, user.getUsername(), path);
                return false;
        }
    }

    // ── Helper ─────────────────────────────────────────────────────

    private static String normalize(String path) {
        String value = (path == null) ? "" : path.trim();
        if (value.isEmpty()) {
            return "";
        }
        return value.startsWith("/") ? value : "/" + value;
    }
}