package org.limelightcreatives.auth.oauth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for signing in to apps, under {@code auth.oauth:}.
 *
 * @param jwtKey the EC (P-256) key pair used to sign tokens ({@code AUTH_JWT_KEY}), as a PEM
 *               containing the PKCS#8 private key and its public key, or that PEM encoded in
 *               base64
 * @param generateJwtKey whether to generate a new key at startup when none is configured. Intended for
 *                       local development and tests only
 */
@ConfigurationProperties("auth.oauth")
public record OAuthProperties(String jwtKey, boolean generateJwtKey) {
}
