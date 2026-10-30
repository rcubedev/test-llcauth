package org.limelightcreatives.auth.web;

import org.limelightcreatives.auth.AuthProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;

/**
 * Where it's safe to send someone after sign-in: only sites on the root domain (e.g.
 * dashboard.limelightcreatives.org), never an address a crafted "next" link smuggles in.
 */
@Component
public class RedirectTargets {

    private final AuthProperties properties;

    public RedirectTargets(AuthProperties properties) {
        this.properties = properties;
    }

    /** {@code next} if it's allowed, otherwise the default redirect. */
    public String safe(String next) {
        if (next == null || next.isBlank()) return properties.defaultRedirect().toString();
        try {
            URI uri = properties.defaultRedirect().resolve(next.trim()); // "/schedule" -> on the default site
            return isAllowed(uri) ? uri.toString() : properties.defaultRedirect().toString();
        } catch (IllegalArgumentException e) {
            return properties.defaultRedirect().toString();
        }
    }

    /** True for https (or http when testing locally) addresses on the root domain or its subdomains. */
    public boolean isAllowed(URI uri) {
        String scheme = uri.getScheme();
        boolean schemeOk = "https".equals(scheme) || (!properties.secureCookies() && "http".equals(scheme));
        return schemeOk && uri.getRawUserInfo() == null && isOurHost(uri.getHost());
    }

    private boolean isOurHost(String host) {
        if (host == null) return false;
        String h = host.toLowerCase(Locale.ROOT);
        String root = properties.rootDomain().toLowerCase(Locale.ROOT);
        // "evil-limelightcreatives.org" ends with the root domain too; only a dot boundary counts.
        return h.equals(root) || h.endsWith("." + root);
    }
}
