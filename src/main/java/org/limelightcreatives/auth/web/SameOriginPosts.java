package org.limelightcreatives.auth.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.limelightcreatives.auth.AuthProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Refuses form posts that come from another site (cross-site request forgery). The session
 * cookie is SameSite=Lax, which already stops most of these, but every *.limelightcreatives.org
 * subdomain counts as the same "site"; the Origin header names the exact page a post came from.
 */
@Component
public class SameOriginPosts extends OncePerRequestFilter {

    private final AuthProperties properties;

    public SameOriginPosts(AuthProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        // Browsers send Origin with every POST; tools like curl don't, and can't carry a victim's cookie.
        if ("POST".equals(request.getMethod()) && origin != null && !origin.equals(properties.origin())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Cross-site form post refused");
            return;
        }
        chain.doFilter(request, response);
    }
}
