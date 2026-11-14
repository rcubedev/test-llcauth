package org.limelightcreatives.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

/**
 * Settings under {@code auth:}
 *
 * @param baseUrl the app's public address (e.g. https://auth.limelightcreatives.org); also the
 *                OpenID Connect issuer so it must never change once apps use it
 * @param rootDomain the domain whose sites may be redirected to after sign in and may call
 *                   {@code /api/permission} from a browser
 * @param cookieDomain the session cookie's domain, e.g. ".limelightcreatives.org" so the old dashboard
 *                     can forward it to {@code /api/permission}. Empty for this host only
 * @param secureCookies HTTPS-only cookies (named __Secure-...). Off for local development
 * @param defaultRedirect where to go after sign-in when no (allowed) "next" address was given
 * @param sessionLifetime how long a sign in lasts without being used
 */
@ConfigurationProperties("auth")
public record AuthProperties(
        URI baseUrl,
        String rootDomain,
        String cookieDomain,
        boolean secureCookies,
        URI defaultRedirect,
        Duration sessionLifetime
) {

    public String cookieName() {
        return secureCookies ? "__Secure-authjs.session-token" : "authjs.session-token";
    }
}
