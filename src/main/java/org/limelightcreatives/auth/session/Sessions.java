package org.limelightcreatives.auth.session;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.limelightcreatives.auth.AuthProperties;
import org.limelightcreatives.auth.user.User;
import org.limelightcreatives.auth.user.Users;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Optional;

/**
 * Who is signed in, kept in the shared session cookie.
 *
 * <p>The cookie keeps the original app's name ({@code __Secure-authjs.session-token}, or
 * {@code authjs.session-token} without HTTPS) and domain, because the dashboard reads it by that
 * name and forwards it to {@code /api/permission}.
 */
@Component
public class Sessions {

    private final SessionTokens tokens;
    private final Users users;
    private final AuthProperties properties;

    public Sessions(SessionTokens tokens, Users users, AuthProperties properties) {
        this.tokens = tokens;
        this.users = users;
        this.properties = properties;
    }

    public String cookieName() {
        return properties.secureCookies() ? "__Secure-authjs.session-token" : "authjs.session-token";
    }

    /** The signed-in user, if the cookie is valid and their account still exists. */
    public Optional<User> current(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return Optional.empty();
        return Arrays.stream(cookies)
                .filter(cookie -> cookie.getName().equals(cookieName()))
                .map(Cookie::getValue)
                .findFirst()
                .flatMap(tokens::userId)
                .flatMap(users::findById);
    }

    public void signIn(HttpServletResponse response, User user) {
        setCookie(response, tokens.issue(user.id()), properties.sessionLifetime().toSeconds());
    }

    public void signOut(HttpServletResponse response) {
        setCookie(response, "", 0);
    }

    private void setCookie(HttpServletResponse response, String value, long maxAgeSeconds) {
        var cookie = ResponseCookie.from(cookieName(), value)
                .path("/")
                .httpOnly(true)
                .secure(properties.secureCookies())
                .sameSite("Lax")
                .maxAge(maxAgeSeconds);
        if (properties.cookieDomain() != null && !properties.cookieDomain().isBlank()) {
            cookie.domain(properties.cookieDomain());
        }
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.build().toString());
    }
}
