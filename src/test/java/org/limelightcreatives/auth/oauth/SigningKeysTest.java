package org.limelightcreatives.auth.oauth;

import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
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

    private static String block(String label, byte[] der) {
        String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der);
        return "-----BEGIN " + label + "-----\n" + body + "\n-----END " + label + "-----\n";
    }

    @Test
    void theKeyAndItsIdAreTheSameEveryTime() throws Exception {
        String pem = pem(SigningKeys.generate());
        ECKey first = SigningKeys.signingKey(new OAuthProperties(pem, false));
        ECKey again = SigningKeys.signingKey(new OAuthProperties(pem, false));
        assertThat(again.getKeyID()).isEqualTo(first.getKeyID()).isNotBlank();
        assertThat(again.toPublicJWK()).isEqualTo(first.toPublicJWK());
        assertThat(first.isPrivate()).isTrue();
        assertThat(first.getCurve()).isEqualTo(Curve.P_256);
    }

    @Test
    void base64AndEscapedNewlinesWorkToo() throws Exception {
        String pem = pem(SigningKeys.generate());
        String kid = SigningKeys.parse(pem).getKeyID();
        assertThat(SigningKeys.parse(Base64.getEncoder().encodeToString(pem.getBytes(StandardCharsets.US_ASCII))).getKeyID())
                .isEqualTo(kid);
        assertThat(SigningKeys.parse(pem.replace("\n", "\\n")).getKeyID()).isEqualTo(kid);
    }

    @Test
    void theBlocksCanComeInEitherOrder() throws Exception {
        ECKey key = SigningKeys.generate();
        String swapped = block("PUBLIC KEY", key.toPublicKey().getEncoded()) + block("PRIVATE KEY", key.toPrivateKey().getEncoded());
        assertThat(SigningKeys.parse(swapped).getKeyID()).isEqualTo(key.getKeyID());
    }

    @Test
    void theOpensslOutputWorks() throws Exception {
        Process openssl = new ProcessBuilder("sh", "-c", "openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 "
                + "| tee /dev/stderr | openssl pkey -pubout").redirectErrorStream(false).start();
        String publicPart = new String(openssl.getInputStream().readAllBytes(), StandardCharsets.US_ASCII);
        String privatePart = new String(openssl.getErrorStream().readAllBytes(), StandardCharsets.US_ASCII);
        org.junit.jupiter.api.Assumptions.assumeTrue(openssl.waitFor() == 0 && publicPart.contains("PUBLIC KEY"), "openssl not available");
        assertThat(SigningKeys.parse(privatePart + publicPart).isPrivate()).isTrue();
    }

    @Test
    void productionWontStartWithoutAKey() {
        assertThatThrownBy(() -> SigningKeys.signingKey(new OAuthProperties("", false)))
                .hasMessageContaining("(AUTH_JWT_KEY) must be set");
    }

    @Test
    void developmentMakesOneUp() {
        assertThat(SigningKeys.signingKey(new OAuthProperties(null, true)).isPrivate()).isTrue();
    }

    @Test
    void aPrivateKeyWithoutItsPublicKeyIsExplained() {
        ECKey key = SigningKeys.generate();
        assertThatThrownBy(() -> SigningKeys.parse(block("PRIVATE KEY", key.toPrivateKey().getEncoded())))
                .hasMessageContaining("followed by its public key").hasMessageContaining("no -----BEGIN PUBLIC KEY-----");
    }

    @Test
    void halvesOfDifferentKeysAreRefused() throws Exception {
        ECKey one = SigningKeys.generate();
        ECKey other = SigningKeys.generate();
        String mixed = block("PRIVATE KEY", one.toPrivateKey().getEncoded()) + block("PUBLIC KEY", other.toPublicKey().getEncoded());
        assertThatThrownBy(() -> SigningKeys.parse(mixed)).hasMessageContaining("the public key isn't the private key's");
    }

    @Test
    void anRsaKeyIsExplained() throws Exception {
        KeyPair rsa = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        String pem = block("PRIVATE KEY", rsa.getPrivate().getEncoded()) + block("PUBLIC KEY", rsa.getPublic().getEncoded());
        assertThatThrownBy(() -> SigningKeys.parse(pem)).hasMessageContaining("isn't an EC P-256 private key");
    }
}
