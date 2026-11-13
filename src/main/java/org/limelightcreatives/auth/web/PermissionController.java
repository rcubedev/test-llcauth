package org.limelightcreatives.auth.web;

import org.limelightcreatives.auth.AuthProperties;
import org.limelightcreatives.auth.user.User;
import org.limelightcreatives.auth.user.Users;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.security.Principal;
import java.util.Optional;

@RestController
public class PermissionController {

    public record Permission(boolean authenticated, String id, String email, String name, String role,
                             boolean needsProfile) {

        static Permission of(User user) {
            return new Permission(true, user.id(), user.email(), user.name(), user.role().name(), user.needsProfile());
        }
    }

    public record NoPermission(boolean authenticated) {}

    private final Users users;

    public PermissionController(Users users) {
        this.users = users;
    }

    @GetMapping("/api/permission")
    public ResponseEntity<?> permission(Principal principal) {
        Optional<User> user = principal == null ? Optional.empty() : this.users.findById(principal.getName());
        ResponseEntity.BodyBuilder response = user.isPresent() ? ResponseEntity.ok() : ResponseEntity.status(HttpStatus.UNAUTHORIZED);
        response.cacheControl(CacheControl.noStore());
        return user.<ResponseEntity<?>>map(u -> response.body(Permission.of(u)))
                .orElseGet(() -> response.body(new NoPermission(false)));
    }

    @Configuration
    static class Cors implements WebMvcConfigurer {

        private final AuthProperties properties;

        Cors(AuthProperties properties) {
            this.properties = properties;
        }

        @Override
        public void addCorsMappings(CorsRegistry registry) {
            String scheme = this.properties.secureCookies() ? "https" : "http";
            String root = this.properties.rootDomain();
            registry.addMapping("/api/permission")
                    .allowedOriginPatterns(scheme + "://" + root, scheme + "://*." + root,
                            scheme + "://" + root + ":[*]", scheme + "://*." + root + ":[*]")
                    .allowedMethods("GET")
                    .allowCredentials(true);
        }
    }
}
