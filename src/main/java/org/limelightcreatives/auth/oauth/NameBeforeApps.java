package org.limelightcreatives.auth.oauth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.limelightcreatives.auth.user.User;
import org.limelightcreatives.auth.user.Users;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

class NameBeforeApps extends OncePerRequestFilter {

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
        if (this.authorize.matches(request) && needsName()) {
            this.requestCache.saveRequest(request, response);
            response.sendRedirect(request.getContextPath() + "/login/name");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean needsName() {
        Authentication authentication = SecurityContextHolder.getContextHolderStrategy().getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken)
            return false;
        return this.users.findById(authentication.getName()).map(User::needsProfile).orElse(false);
    }
}
