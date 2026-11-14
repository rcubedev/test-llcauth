package org.limelightcreatives.auth.oauth;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;

/* generate EC (AUTH_JWT_KEY) with:
 *   openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out key.pem
 *   openssl pkey -in key.pem -pubout >> key.pem
 *   base64 < key.pem | tr -d '\n'
 *
 * RSA (AUTH_JWT_RSA_KEY) with:
 *   openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out rsa.pem
 *   openssl pkey -in rsa.pem -pubout >> rsa.pem
 *   base64 < rsa.pem | tr -d '\n'
 */
@Configuration
public class SigningKeys {

    private static final Logger log = LoggerFactory.getLogger(SigningKeys.class);
    private static final Pattern BLOCK = Pattern.compile("-----BEGIN ([A-Z ]+)-----(.*?)-----END \\1-----", Pattern.DOTALL);
    private static final int MIN_RSA_BITS = 2048;

    @Bean
    JWKSource<SecurityContext> jwkSource(OAuthProperties properties) {
        return new ImmutableJWKSet<>(new JWKSet(List.<JWK>of(ecKey(properties), rsaKey(properties))));
    }

    static ECKey ecKey(OAuthProperties properties) {
        if (present(properties.jwtKey())) return parseEc(properties.jwtKey());
        requireGeneration(properties, "jwt-key (AUTH_JWT_KEY)");
        return generateEc();
    }

    static RSAKey rsaKey(OAuthProperties properties) {
        if (present(properties.jwtRsaKey())) return parseRsa(properties.jwtRsaKey());
        requireGeneration(properties, "jwt-rsa-key (AUTH_JWT_RSA_KEY)");
        return generateRsa();
    }

    static ECKey parseEc(String value) {
        String pem = pem(value);
        try {
            KeyFactory factory = KeyFactory.getInstance("EC");
            ECPrivateKey privateKey = (ECPrivateKey) factory.generatePrivate(new PKCS8EncodedKeySpec(block(pem, "PRIVATE KEY")));
            ECPublicKey publicKey = (ECPublicKey) factory.generatePublic(new X509EncodedKeySpec(block(pem, "PUBLIC KEY")));
            ECKey key = new ECKey.Builder(Curve.P_256, publicKey).privateKey(privateKey).keyIDFromThumbprint().build();
            if (!matches(new ECDSASigner(key), new ECDSAVerifier(key.toPublicJWK()), JWSAlgorithm.ES256))
                throw new IllegalArgumentException("the public key isn't the private key's");
            return key;
        } catch (GeneralSecurityException | IllegalArgumentException | ClassCastException | JOSEException e) {
            throw new IllegalStateException("auth.oauth.jwt-key (AUTH_JWT_KEY) isn't an EC P-256 private key (PKCS#8, "
                    + "-----BEGIN PRIVATE KEY-----) followed by its public key (-----BEGIN PUBLIC KEY-----), or that PEM "
                    + "in base64: " + e.getMessage(), e);
        }
    }

    static RSAKey parseRsa(String value) {
        String pem = pem(value);
        try {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            RSAPrivateKey privateKey = (RSAPrivateKey) factory.generatePrivate(new PKCS8EncodedKeySpec(block(pem, "PRIVATE KEY")));
            RSAPublicKey publicKey = (RSAPublicKey) factory.generatePublic(new X509EncodedKeySpec(block(pem, "PUBLIC KEY")));
            if (!privateKey.getModulus().equals(publicKey.getModulus()))
                throw new IllegalArgumentException("the public key isn't the private key's");
            if (publicKey.getModulus().bitLength() < MIN_RSA_BITS)
                throw new IllegalArgumentException("the key is shorter than " + MIN_RSA_BITS + " bits");
            RSAKey key = new RSAKey.Builder(publicKey).privateKey(privateKey).keyIDFromThumbprint().build();
            if (!matches(new RSASSASigner(key), new RSASSAVerifier(key.toPublicJWK()), JWSAlgorithm.RS256))
                throw new IllegalArgumentException("the public key isn't the private key's");
            return key;
        } catch (GeneralSecurityException | IllegalArgumentException | ClassCastException | JOSEException e) {
            throw new IllegalStateException("auth.oauth.jwt-rsa-key (AUTH_JWT_RSA_KEY) isn't an RSA private key of at "
                    + "least " + MIN_RSA_BITS + " bits (PKCS#8, -----BEGIN PRIVATE KEY-----) followed by its public key "
                    + "(-----BEGIN PUBLIC KEY-----), or that PEM in base64: " + e.getMessage(), e);
        }
    }

    static ECKey generateEc() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1")); // P-256
            KeyPair pair = generator.generateKeyPair();
            return new ECKey.Builder(Curve.P_256, (ECPublicKey) pair.getPublic()).privateKey(pair.getPrivate())
                    .keyIDFromThumbprint().build();
        } catch (GeneralSecurityException | JOSEException e) {
            throw new IllegalStateException(e); // every JVM has EC
        }
    }

    static RSAKey generateRsa() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(MIN_RSA_BITS);
            KeyPair pair = generator.generateKeyPair();
            return new RSAKey.Builder((RSAPublicKey) pair.getPublic()).privateKey(pair.getPrivate())
                    .keyIDFromThumbprint().build();
        } catch (GeneralSecurityException | JOSEException e) {
            throw new IllegalStateException(e); // every JVM has RSA
        }
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private static void requireGeneration(OAuthProperties properties, String setting) {
        if (!properties.generateJwtKey())
            throw new IllegalStateException("auth.oauth." + setting + " must be set");
        log.warn("No auth.oauth.{}: signing tokens with a new key, so every restart signs everyone out of every app.", setting);
    }

    private static String pem(String value) {
        String pem = value.strip();
        if (!pem.startsWith("-----")) pem = new String(Base64.getMimeDecoder().decode(pem), StandardCharsets.US_ASCII);
        return pem.replace("\\n", "\n");
    }

    private static byte[] block(String pem, String label) {
        var matcher = BLOCK.matcher(pem);
        while (matcher.find())
            if (matcher.group(1).equals(label)) return Base64.getMimeDecoder().decode(matcher.group(2).strip());
        throw new IllegalArgumentException("no -----BEGIN " + label + "----- block");
    }

    private static boolean matches(JWSSigner signer, JWSVerifier verifier, JWSAlgorithm algorithm) throws JOSEException {
        JWSHeader header = new JWSHeader(algorithm);
        byte[] input = "limelight".getBytes(StandardCharsets.US_ASCII);
        return verifier.verify(header, input, signer.sign(header, input));
    }
}
