package org.limelightcreatives.auth.oauth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Settings for signing in to apps, under {@code auth.oauth:}.
 *
 * @param jwtKey the EC (P-256) key pair used to sign tokens ({@code AUTH_JWT_KEY}), as a PEM
 *               containing the PKCS#8 private key and its public key, or that PEM encoded in
 *               base64
 * @param generateJwtKey whether to generate a new key at startup when none is configured. Intended for
 *                       local development and tests only
 * @param accessTokenLifetime how long an access token (and ID token) works
 * @param refreshTokenLifetime how long an app stays signed in without the person coming back
 * @param clients the apps that may sign people in by client id
 */
@ConfigurationProperties("auth.oauth")
public record OAuthProperties(String jwtKey, boolean generateJwtKey, Duration accessTokenLifetime, Duration refreshTokenLifetime,
                              Map<String, Client> clients) {

    public OAuthProperties {
        if (accessTokenLifetime == null) accessTokenLifetime = Duration.ofMinutes(10);
        if (refreshTokenLifetime == null) refreshTokenLifetime = Duration.ofDays(30);
        clients = clients == null ? Map.of() : Map.copyOf(clients);
    }

    /**
     * A client application that signs users in through the authorisation server
     *
     * @param name the client's display name
     * @param secret a client's secret (confidential and server side), at least 32 chars and
     *               shared with the client. Clients without one are not registered
     * @param publicClient whether the client cannot keep a secret (e.g. it runs in a browser) so
     *                     it authenticates with PKCE alone
     * @param redirectUrls the exact URIs the client may receive authorisation codes at
     * @param postLogoutRedirectUrls the exact URIs users may be returned to after signing out of
     *                               the client
     */
    public record Client(String name, String secret, boolean publicClient, List<String> redirectUrls,
                         List<String> postLogoutRedirectUrls) {

        public Client {
            redirectUrls = redirectUrls == null ? List.of() : List.copyOf(redirectUrls);
            postLogoutRedirectUrls = postLogoutRedirectUrls == null ? List.of() : List.copyOf(postLogoutRedirectUrls);
        }
    }
}
