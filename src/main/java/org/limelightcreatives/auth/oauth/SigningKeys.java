package org.limelightcreatives.auth.oauth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
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
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.regex.Pattern;

/* generate with:
   openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out key.pem
   openssl pkey -in key.pem -pubout >> key.pem
   base64 &lt; key.pem | tr -d '\n'
 */
@Configuration
public class SigningKeys {

    private static final Logger log = LoggerFactory.getLogger(SigningKeys.class);
    private static final Pattern BLOCK = Pattern.compile("-----BEGIN ([A-Z ]+)-----(.*?)-----END \\1-----", Pattern.DOTALL);

    @Bean
    JWKSource<SecurityContext> jwkSource(OAuthProperties properties) {
        return new ImmutableJWKSet<>(new JWKSet(signingKey(properties)));
    }

    static ECKey signingKey(OAuthProperties properties) {
        if (properties.jwtKey() != null && !properties.jwtKey().isBlank()) return parse(properties.jwtKey());
        if (!properties.generateJwtKey())
            throw new IllegalStateException("auth.oauth.jwt-key (AUTH_JWT_KEY) must be set");
        log.warn("No AUTH_JWT_KEY: signing tokens with a new key, so every restart signs everyone out of every app.");
        return generate();
    }

    static ECKey parse(String value) {
        String pem = value.strip();
        if (!pem.startsWith("-----")) pem = new String(Base64.getMimeDecoder().decode(pem), StandardCharsets.US_ASCII);
        pem = pem.replace("\\n", "\n");
        try {
            KeyFactory factory = KeyFactory.getInstance("EC");
            ECPrivateKey privateKey = (ECPrivateKey) factory.generatePrivate(new PKCS8EncodedKeySpec(block(pem, "PRIVATE KEY")));
            ECPublicKey publicKey = (ECPublicKey) factory.generatePublic(new X509EncodedKeySpec(block(pem, "PUBLIC KEY")));
            ECKey key = withThumbprintId(new ECKey.Builder(Curve.P_256, publicKey).privateKey(privateKey));
            if (!matches(key))
                throw new IllegalArgumentException("the public key isn't the private key's");
            return key;
        } catch (GeneralSecurityException | IllegalArgumentException | ClassCastException | JOSEException e) {
            throw new IllegalStateException("auth.oauth.jwt-key (AUTH_JWT_KEY) isn't an EC P-256 private key (PKCS#8, "
                    + "-----BEGIN PRIVATE KEY-----) followed by its public key (-----BEGIN PUBLIC KEY-----), or that PEM "
                    + "in base64: " + e.getMessage(), e);
        }
    }

    static ECKey generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1")); // P-256
            var pair = generator.generateKeyPair();
            return withThumbprintId(new ECKey.Builder(Curve.P_256, (ECPublicKey) pair.getPublic()).privateKey(pair.getPrivate()));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e); // every JVM has EC
        }
    }

    private static byte[] block(String pem, String label) {
        var matcher = BLOCK.matcher(pem);
        while (matcher.find())
            if (matcher.group(1).equals(label)) return Base64.getMimeDecoder().decode(matcher.group(2).strip());
        throw new IllegalArgumentException("no -----BEGIN " + label + "----- block");
    }

    private static boolean matches(ECKey key) throws JOSEException {
        ECDSASigner signer = new ECDSASigner(key);
        ECDSAVerifier verifier = new ECDSAVerifier(key.toPublicJWK());
        JWSHeader header = new JWSHeader(JWSAlgorithm.ES256);
        byte[] input = "limelight".getBytes(StandardCharsets.US_ASCII);
        return verifier.verify(header, input, signer.sign(header, input));
    }

    private static ECKey withThumbprintId(ECKey.Builder key) {
        try {
            return key.keyIDFromThumbprint().build();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }
}
