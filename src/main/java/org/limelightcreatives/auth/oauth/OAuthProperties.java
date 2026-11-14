package org.limelightcreatives.auth.oauth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Settings for signing in to apps, under {@code auth.oauth:}. The apps themselves are in the
 * database; see {@link Apps}.
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
 */
@ConfigurationProperties("auth.oauth")
public record OAuthProperties(String jwtKey, String jwtRsaKey, boolean generateJwtKey, Duration accessTokenLifetime,
                              Duration refreshTokenLifetime) {

    public OAuthProperties {
        if (accessTokenLifetime == null) accessTokenLifetime = Duration.ofMinutes(10);
        if (refreshTokenLifetime == null) refreshTokenLifetime = Duration.ofDays(30);
    }
}
