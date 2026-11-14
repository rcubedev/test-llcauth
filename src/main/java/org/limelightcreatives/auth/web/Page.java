package org.limelightcreatives.auth.web;

import org.jspecify.annotations.Nullable;
import org.springframework.security.web.csrf.CsrfToken;

public record Page(Assets assets, @Nullable CsrfToken csrf, boolean signedIn) {

    public String asset(String path) {
        return this.assets.url(path);
    }
}
