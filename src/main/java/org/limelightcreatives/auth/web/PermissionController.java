package org.limelightcreatives.auth.web;

import jakarta.servlet.http.HttpServletRequest;
import org.limelightcreatives.auth.session.Sessions;
import org.limelightcreatives.auth.user.User;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * {@code GET /api/permission}: who is signed in. Other apps (the dashboard) call this server-to-server,
 * forwarding the visitor's session cookie; pages on our subdomains can also call it from the browser.
 *
 * <p>The response is the same as the original app's, field for field.
 */
@RestController
public class PermissionController {

    /** {@code name} is null until the person has entered it, which is when {@code needsProfile} is true. */
    public record Permission(boolean authenticated, String id, String email, String name, String role,
                             boolean needsProfile) {

        static Permission of(User user) {
            return new Permission(true, user.id(), user.email(), user.name(), user.role().name(), user.needsProfile());
        }
    }

    public record NoPermission(boolean authenticated) {}

    private final Sessions sessions;
    private final RedirectTargets sites;

    public PermissionController(Sessions sessions, RedirectTargets sites) {
        this.sessions = sessions;
        this.sites = sites;
    }

    @GetMapping("/api/permission")
    public ResponseEntity<?> permission(HttpServletRequest request,
                                        @RequestHeader(value = HttpHeaders.ORIGIN, required = false) String origin) {
        var user = sessions.current(request);
        var response = user.isPresent()
                ? ResponseEntity.ok()
                : ResponseEntity.status(HttpStatus.UNAUTHORIZED);
        response.cacheControl(CacheControl.noStore());
        cors(origin, response);
        return user.<ResponseEntity<?>>map(u -> response.body(Permission.of(u)))
                .orElseGet(() -> response.body(new NoPermission(false)));
    }

    @RequestMapping(value = "/api/permission", method = RequestMethod.OPTIONS)
    public ResponseEntity<Void> preflight(@RequestHeader(value = HttpHeaders.ORIGIN, required = false) String origin) {
        var response = ResponseEntity.noContent()
                .header(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET, OPTIONS")
                .header(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "Content-Type");
        cors(origin, response);
        return response.build();
    }

    /** Lets our own sites read the answer from the browser, with the cookie (and nobody else). */
    private void cors(String origin, ResponseEntity.HeadersBuilder<?> response) {
        if (origin == null) return;
        try {
            if (!sites.isAllowed(URI.create(origin))) return;
        } catch (IllegalArgumentException e) {
            return;
        }
        response.header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin)
                .header(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true")
                .header(HttpHeaders.VARY, HttpHeaders.ORIGIN);
    }
}
