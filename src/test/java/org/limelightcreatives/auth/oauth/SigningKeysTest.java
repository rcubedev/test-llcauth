package org.limelightcreatives.auth.oauth;

import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SigningKeysTest {

    private static String pem(ECKey key) throws Exception {
        return block("PRIVATE KEY", key.toPrivateKey().getEncoded()) + block("PUBLIC KEY", key.toPublicKey().getEncoded());
    }

    private static String pem(RSAKey key) throws Exception {
        return block("PRIVATE KEY", key.toPrivateKey().getEncoded()) + block("PUBLIC KEY", key.toPublicKey().getEncoded());
    }

    private static String block(String label, byte[] der) {
        String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der);
        return "-----BEGIN " + label + "-----\n" + body + "\n-----END " + label + "-----\n";
    }

    @Test
    void theKeyAndItsIdAreTheSameEveryTime() throws Exception {
        String pem = pem(SigningKeys.generateEc());
        ECKey first = SigningKeys.ecKey(new OAuthProperties(pem, null, false, null, null));
        ECKey again = SigningKeys.ecKey(new OAuthProperties(pem, null, false, null, null));
        assertThat(again.getKeyID()).isEqualTo(first.getKeyID()).isNotBlank();
        assertThat(again.toPublicJWK()).isEqualTo(first.toPublicJWK());
        assertThat(first.isPrivate()).isTrue();
        assertThat(first.getCurve()).isEqualTo(Curve.P_256);
    }

    @Test
    void base64AndEscapedNewlinesWorkToo() throws Exception {
        String pem = pem(SigningKeys.generateEc());
        String kid = SigningKeys.parseEc(pem).getKeyID();
        assertThat(SigningKeys.parseEc(Base64.getEncoder().encodeToString(pem.getBytes(StandardCharsets.US_ASCII))).getKeyID())
                .isEqualTo(kid);
        assertThat(SigningKeys.parseEc(pem.replace("\n", "\\n")).getKeyID()).isEqualTo(kid);
    }

    @Test
    void theBlocksCanComeInEitherOrder() throws Exception {
        ECKey key = SigningKeys.generateEc();
        String swapped = block("PUBLIC KEY", key.toPublicKey().getEncoded()) + block("PRIVATE KEY", key.toPrivateKey().getEncoded());
        assertThat(SigningKeys.parseEc(swapped).getKeyID()).isEqualTo(key.getKeyID());
    }

    @Test
    void theOpensslOutputWorks() throws Exception {
        Process openssl = new ProcessBuilder("sh", "-c", "openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 "
                + "| tee /dev/stderr | openssl pkey -pubout").redirectErrorStream(false).start();
        String publicPart = new String(openssl.getInputStream().readAllBytes(), StandardCharsets.US_ASCII);
        String privatePart = new String(openssl.getErrorStream().readAllBytes(), StandardCharsets.US_ASCII);
        org.junit.jupiter.api.Assumptions.assumeTrue(openssl.waitFor() == 0 && publicPart.contains("PUBLIC KEY"), "openssl not available");
        assertThat(SigningKeys.parseEc(privatePart + publicPart).isPrivate()).isTrue();
    }

    @Test
    void productionWontStartWithoutAKey() {
        assertThatThrownBy(() -> SigningKeys.ecKey(new OAuthProperties("", null, false, null, null)))
                .hasMessageContaining("(AUTH_JWT_KEY) must be set");
    }

    @Test
    void developmentMakesOneUp() {
        assertThat(SigningKeys.ecKey(new OAuthProperties(null, null, true, null, null)).isPrivate()).isTrue();
    }

    @Test
    void aPrivateKeyWithoutItsPublicKeyIsExplained() {
        ECKey key = SigningKeys.generateEc();
        assertThatThrownBy(() -> SigningKeys.parseEc(block("PRIVATE KEY", key.toPrivateKey().getEncoded())))
                .hasMessageContaining("followed by its public key").hasMessageContaining("no -----BEGIN PUBLIC KEY-----");
    }

    @Test
    void halvesOfDifferentKeysAreRefused() throws Exception {
        ECKey one = SigningKeys.generateEc();
        ECKey other = SigningKeys.generateEc();
        String mixed = block("PRIVATE KEY", one.toPrivateKey().getEncoded()) + block("PUBLIC KEY", other.toPublicKey().getEncoded());
        assertThatThrownBy(() -> SigningKeys.parseEc(mixed)).hasMessageContaining("the public key isn't the private key's");
    }

    @Test
    void anRsaKeyIsExplained() throws Exception {
        KeyPair rsa = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        String pem = block("PRIVATE KEY", rsa.getPrivate().getEncoded()) + block("PUBLIC KEY", rsa.getPublic().getEncoded());
        assertThatThrownBy(() -> SigningKeys.parseEc(pem)).hasMessageContaining("isn't an EC P-256 private key");
    }

    @Test
    void theRsaKeyAndItsIdAreTheSameEveryTime() throws Exception {
        String pem = pem(SigningKeys.generateRsa());
        RSAKey first = SigningKeys.rsaKey(new OAuthProperties(null, pem, false, null, null));
        RSAKey again = SigningKeys.rsaKey(new OAuthProperties(null, pem, false, null, null));
        assertThat(again.getKeyID()).isEqualTo(first.getKeyID()).isNotBlank();
        assertThat(again.toPublicJWK()).isEqualTo(first.toPublicJWK());
        assertThat(first.isPrivate()).isTrue();
        assertThat(first.size()).isGreaterThanOrEqualTo(2048);
    }

    @Test
    void anRsaKeyInBase64WithEscapedNewlinesWorks() throws Exception {
        String pem = pem(SigningKeys.generateRsa());
        String kid = SigningKeys.parseRsa(pem).getKeyID();
        assertThat(SigningKeys.parseRsa(Base64.getEncoder().encodeToString(pem.getBytes(StandardCharsets.US_ASCII))).getKeyID())
                .isEqualTo(kid);
        assertThat(SigningKeys.parseRsa(pem.replace("\n", "\\n")).getKeyID()).isEqualTo(kid);
    }

    @Test
    void productionWontStartWithoutAnRsaKey() throws Exception {
        String ec = pem(SigningKeys.generateEc());
        assertThatThrownBy(() -> SigningKeys.rsaKey(new OAuthProperties(ec, "", false, null, null)))
                .hasMessageContaining("(AUTH_JWT_RSA_KEY) must be set");
    }

    @Test
    void developmentMakesUpTwoDifferentKeys() {
        OAuthProperties properties = new OAuthProperties(null, null, true, null, null);
        ECKey ec = SigningKeys.ecKey(properties);
        RSAKey rsa = SigningKeys.rsaKey(properties);
        assertThat(rsa.isPrivate()).isTrue();
        assertThat(rsa.getKeyID()).isNotEqualTo(ec.getKeyID());
    }

    @Test
    void halvesOfDifferentRsaKeysAreRefused() throws Exception {
        RSAKey one = SigningKeys.generateRsa();
        RSAKey other = SigningKeys.generateRsa();
        String mixed = block("PRIVATE KEY", one.toPrivateKey().getEncoded()) + block("PUBLIC KEY", other.toPublicKey().getEncoded());
        assertThatThrownBy(() -> SigningKeys.parseRsa(mixed)).hasMessageContaining("the public key isn't the private key's");
    }

    @Test
    void shortRsaKeysAreRefused() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(1024);
        KeyPair weak = generator.generateKeyPair();
        String pem = block("PRIVATE KEY", weak.getPrivate().getEncoded()) + block("PUBLIC KEY", weak.getPublic().getEncoded());
        assertThatThrownBy(() -> SigningKeys.parseRsa(pem)).hasMessageContaining("shorter than 2048 bits");
    }

    @Test
    void anEcKeyIsntTakenAsRsa() {
        assertThatThrownBy(() -> SigningKeys.parseRsa(pem(SigningKeys.generateEc())))
                .hasMessageContaining("isn't an RSA private key");
    }
}
