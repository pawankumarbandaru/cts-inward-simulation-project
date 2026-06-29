package com.cts.filter;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cts.security.SecurityUtil;
import com.cts.uam.model.User;
import com.cts.uam.service.UserService;
import com.cts.uam.service.UserServiceImpl;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Runs first. Blocks unauthenticated access to .zul pages and
 * re-validates the logged-in user's status (active / not locked)
 * periodically so a disabled account is kicked out quickly.
 */
public class SecurityFilter implements Filter {

    private static final Logger LOG = LoggerFactory.getLogger(SecurityFilter.class);

    private static final long RECHECK_INTERVAL_MS = 30_000L;

    private static final Set<String> PUBLIC_PAGES = Set.of(
            "/", "/zul/index.zul");

    private static final Set<String> LOGIN_PAGES = Set.of("/zul/index.zul");

    private final UserService userService = new UserServiceImpl();

    @Override
    public void init(FilterConfig cfg) {
        LOG.info("SecurityFilter initialized");
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        String contextPath = request.getContextPath();
        String path = stripContextPath(request.getRequestURI(), contextPath);

        // Let static assets and ZK internals through; only guard .zul pages
        if (isStaticOrInternal(path) || !path.endsWith(".zul")) {
            chain.doFilter(req, res);
            return;
        }

        HttpSession session = request.getSession(false);
        User currentUser = (session == null)
                ? null
                : (User) session.getAttribute(SecurityUtil.SESSION_USER_KEY);

        // Re-validate the logged-in user against the DB every 30s
        if (currentUser != null) {
            currentUser = revalidate(session, currentUser, contextPath, response);
            if (currentUser == null) {
                return; // revalidate already redirected
            }
            if (!currentUser.isActive() || currentUser.isLocked()) {
                session.invalidate();
                LOG.info("SecurityFilter: invalidated session for '{}' (status={})",
                        currentUser.getUsername(), currentUser.getStatus());
                response.sendRedirect(contextPath + "/zul/index.zul");
                return;
            }
        }

        boolean loggedIn = (currentUser != null);

        // Logged-in user hitting the login page → send to their dashboard
        if (loggedIn && LOGIN_PAGES.contains(path)) {
            response.sendRedirect(contextPath + SecurityUtil.getHomePageFor(currentUser));
            return;
        }

        // Not logged in + non-public page → login
        if (!loggedIn && !PUBLIC_PAGES.contains(path)) {
            LOG.debug("Unauthenticated request for {} → redirect to login", path);
            response.sendRedirect(contextPath + "/zul/index.zul");
            return;
        }

        chain.doFilter(req, res);
    }

    @Override
    public void destroy() {
    }

    private User revalidate(HttpSession session, User user, String contextPath,
                            HttpServletResponse response) throws IOException {
        Long lastCheck = (Long) session.getAttribute("SEC_LAST_CHECK");
        long now = System.currentTimeMillis();

        if (lastCheck != null && (now - lastCheck) <= RECHECK_INTERVAL_MS) {
            return user;
        }

        Optional<User> fresh = userService.findByUsername(user.getUsername());
        if (fresh.isEmpty() || !fresh.get().isActive() || fresh.get().isLocked()) {
            session.invalidate();
            LOG.info("SecurityFilter: DB re-check failed for '{}'", user.getUsername());
            response.sendRedirect(contextPath + "/zul/index.zul");
            return null;
        }

        session.setAttribute(SecurityUtil.SESSION_USER_KEY, fresh.get());
        session.setAttribute("SEC_LAST_CHECK", now);
        return fresh.get();
    }

    private static String stripContextPath(String uri, String contextPath) {
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }

    private static boolean isStaticOrInternal(String path) {
        return path.startsWith("/zkau")
                || path.startsWith("/zkcomet")
                || path.endsWith(".dsp")  || path.endsWith(".wpd")
                || path.endsWith(".js")   || path.endsWith(".css")
                || path.endsWith(".png")  || path.endsWith(".gif")
                || path.endsWith(".jpg")  || path.endsWith(".jpeg")
                || path.endsWith(".ico")  || path.endsWith(".woff2")
                || path.endsWith(".woff") || path.endsWith(".ttf");
    }
}