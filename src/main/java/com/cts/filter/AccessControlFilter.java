package com.cts.filter;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cts.security.SecurityUtil;
import com.cts.uam.model.User;

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
 * Runs second (after SecurityFilter). For logged-in users, enforces
 * the role→page rules defined in SecurityUtil.canAccessPage().
 */
public class AccessControlFilter implements Filter {

    private static final Logger LOG = LoggerFactory.getLogger(AccessControlFilter.class);

    @Override
    public void init(FilterConfig cfg) {
        LOG.info("AccessControlFilter initialized");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (!(request instanceof HttpServletRequest httpRequest)
                || !(response instanceof HttpServletResponse httpResponse)) {
            chain.doFilter(request, response);
            return;
        }

        String path = stripContextPath(httpRequest.getRequestURI(), httpRequest.getContextPath());

        if (isStaticOrInternal(path)) {
            chain.doFilter(request, response);
            return;
        }

        HttpSession session = httpRequest.getSession(false);
        User currentUser = (session == null)
                ? null
                : (User) session.getAttribute(SecurityUtil.SESSION_USER_KEY);

        if (SecurityUtil.canAccessPage(path, currentUser)) {
            chain.doFilter(request, response);
            return;
        }

        // Not allowed
        if (currentUser == null) {
            httpResponse.sendRedirect(httpRequest.getContextPath() + "/zul/index.zul");
            return;
        }

        // Logged in but wrong role → send to their own dashboard
        LOG.info("Access denied for '{}' (role={}) to {}",
                currentUser.getUsername(), currentUser.getRoleLabel(), path);
        httpResponse.sendRedirect(
                httpRequest.getContextPath() + SecurityUtil.getHomePageFor(currentUser));
    }

    @Override
    public void destroy() {
    }

    private static String stripContextPath(String uri, String contextPath) {
        if (uri == null) {
            return "";
        }
        if (contextPath != null && !contextPath.isBlank() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }

    private static boolean isStaticOrInternal(String path) {
        return path == null
                || path.startsWith("/zkau")
                || path.startsWith("/zkcomet")
                || path.endsWith(".js")   || path.endsWith(".css")
                || path.endsWith(".png")  || path.endsWith(".gif")
                || path.endsWith(".jpg")  || path.endsWith(".jpeg")
                || path.endsWith(".ico")  || path.endsWith(".woff2")
                || path.endsWith(".woff") || path.endsWith(".ttf")
                || path.endsWith(".dsp")  || path.endsWith(".wpd");
    }
}