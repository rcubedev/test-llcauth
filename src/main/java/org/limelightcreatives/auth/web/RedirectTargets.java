package org.limelightcreatives.auth.web;

import org.limelightcreatives.auth.AuthProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;

@Component
public class RedirectTargets {

    private final AuthProperties properties;

    public RedirectTargets(AuthProperties properties) {
        this.properties = properties;
    }

    public String safe(String next) {
        if (next == null || next.isBlank()) return this.properties.defaultRedirect().toString();
        try {
            URI uri = this.properties.defaultRedirect().resolve(next.trim());
            return isAllowed(uri) ? uri.toString() : this.properties.defaultRedirect().toString();
        } catch (IllegalArgumentException e) {
            return this.properties.defaultRedirect().toString();
        }
    }

    public boolean isAllowed(URI uri) {
        String scheme = uri.getScheme();
        boolean schemeOk = "https".equals(scheme) || (!this.properties.secureCookies() && "http".equals(scheme));
        return schemeOk && uri.getUserInfo() == null && isOurHost(uri.getHost());
    }

    private boolean isOurHost(String host) {
        if (host == null) return false;
        String h = host.toLowerCase(Locale.ROOT);
        String root = this.properties.rootDomain().toLowerCase(Locale.ROOT);
        return h.equals(root) || h.endsWith("." + root);
    }
}
