package org.limelightcreatives.auth.oauth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.limelightcreatives.auth.user.User;
import org.limelightcreatives.auth.user.Users;
import org.limelightcreatives.auth.web.LoginController;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

class NameBeforeApps extends OncePerRequestFilter {

    private static final String SAVED_REQUEST = "SPRING_SECURITY_SAVED_REQUEST";

    private final Users users;
    private final RequestCache requestCache;
    private final RequestMatcher authorize;

    NameBeforeApps(Users users, RequestCache requestCache, String authorizationEndpoint) {
        this.users = users;
        this.requestCache = requestCache;
        this.authorize = PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, authorizationEndpoint);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContextHolderStrategy().getContext().getAuthentication();
        boolean signedIn = authentication != null && authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken);
        if (!this.authorize.matches(request) || !signedIn) {
            chain.doFilter(request, response);
            return;
        }

        Optional<User> user = this.users.findById(authentication.getName());
        if (user.isEmpty()) {
            this.requestCache.saveRequest(request, response);
            HttpSession session = request.getSession(false);
            Object saved = session == null ? null : session.getAttribute(SAVED_REQUEST);
            if (session != null) session.invalidate();
            if (saved != null) request.getSession().setAttribute(SAVED_REQUEST, saved);
            SecurityContextHolder.getContextHolderStrategy().clearContext();
            response.sendRedirect(request.getContextPath() + LoginController.RESUME);
        } else if (user.get().needsProfile()) {
            this.requestCache.saveRequest(request, response);
            response.sendRedirect(request.getContextPath() + "/login/name");
        } else {
            chain.doFilter(request, response);
        }
    }
}
