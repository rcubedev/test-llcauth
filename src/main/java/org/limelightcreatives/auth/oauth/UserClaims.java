package org.limelightcreatives.auth.oauth;

import org.limelightcreatives.auth.user.User;

import java.util.LinkedHashMap;
import java.util.Map;

public final class UserClaims {

    public static final String ROLE = "role";
    public static final String NEED_PROFILE = "needs_profile";

    private UserClaims() {}

    public static Map<String, Object> of(User user) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("email", user.email());
        claims.put("email_verified", true);
        if (!user.needsProfile()) {
            claims.put("name", user.name());
            claims.put("given_name", user.firstName());
            claims.put("family_name", user.lastName());
        }
        claims.put(ROLE, user.role().name());
        claims.put(NEED_PROFILE, user.needsProfile());
        return claims;
    }
}
