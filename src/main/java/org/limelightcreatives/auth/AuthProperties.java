package org.limelightcreatives.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

/**
 * Settings under {@code auth:} in application.yml, mostly filled from environment variables.
 *
 * @param secret          signs session cookies; at least 32 characters, and the same on every instance
 * @param baseUrl         this app's public address, e.g. https://auth.limelightcreatives.org
 * @param rootDomain      the domain whose sites may be redirected to after sign-in and may call the API
 * @param cookieDomain    the session cookie's domain, e.g. ".limelightcreatives.org" so every subdomain
 *                        sees it; empty for this host only
 * @param secureCookies   HTTPS-only cookies (named __Secure-...); off only for local development
 * @param defaultRedirect where to go after sign-in when no (allowed) "next" address was given
 * @param sessionLifetime how long a sign-in lasts
 * @param mail            how login codes are emailed
 */
@ConfigurationProperties("auth")
public record AuthProperties(
        String secret,
        URI baseUrl,
        String rootDomain,
        String cookieDomain,
        boolean secureCookies,
        URI defaultRedirect,
        Duration sessionLifetime,
        Mail mail
) {

    /**
     * @param resendApiKey     Resend API key; without one, codes are only written to the log
     * @param logCodeIfUnsent  when an email can't be sent, write its code to the log instead, so you can
     *                         still sign in while testing. Local development only: never in production.
     */
    public record Mail(String resendApiKey, String from, boolean logCodeIfUnsent) {}

    public AuthProperties {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException(
                    "auth.secret (AUTH_SECRET) must be at least 32 characters, e.g. from `openssl rand -base64 48`");
        }
    }

    /** The scheme://host[:port] of this app, for comparing against Origin headers. */
    public String origin() {
        return baseUrl.getScheme() + "://" + baseUrl.getRawAuthority();
    }
}
