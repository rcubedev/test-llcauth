package org.limelightcreatives.auth.oauth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Settings for signing in to apps, under {@code auth.oauth:}.
 *
 * @param jwtKey the EC (P-256) key pair used to sign tokens ({@code AUTH_JWT_KEY}) for clients that support
 *               ES256, as a PEM containing the PKCS#8 private key and its public key, or that PEM encoded
 *               in base64
 * @param jwtRsaKey the RSA key pair (at least 2048 bits) used to sign ID tokens with RS256
 *                  ({@code AUTH_JWT_RSA_KEY}), in the same format as {@code jwtKey}
 * @param generateJwtKey whether to generate new keys at startup when none are configured. Intended for
 *                       local development and tests only
 * @param accessTokenLifetime how long an access token (and ID token) works
 * @param refreshTokenLifetime how long an app stays signed in without the person coming back
 * @param clients the apps that may sign people in by client id
 */
@ConfigurationProperties("auth.oauth")
public record OAuthProperties(String jwtKey, String jwtRsaKey, boolean generateJwtKey, Duration accessTokenLifetime,
                              Duration refreshTokenLifetime, Map<String, Client> clients) {

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
     * @param idTokenAlgorithm the algorithm the client's ID tokens are signed with, either
     *                         {@code RS256} (the default, which OpenID Connect requires) or
     *                         {@code ES256}
     */
    public record Client(String name, String secret, boolean publicClient, List<String> redirectUrls,
                         List<String> postLogoutRedirectUrls, SignatureAlgorithm idTokenAlgorithm) {

        public Client {
            if (idTokenAlgorithm == null) idTokenAlgorithm = SignatureAlgorithm.RS256;
            if (idTokenAlgorithm != SignatureAlgorithm.RS256 && idTokenAlgorithm != SignatureAlgorithm.ES256)
                throw new IllegalArgumentException("id-token-algorithm must be RS256 or ES256");
            redirectUrls = redirectUrls == null ? List.of() : List.copyOf(redirectUrls);
            postLogoutRedirectUrls = postLogoutRedirectUrls == null ? List.of() : List.copyOf(postLogoutRedirectUrls);
        }
    }
}
