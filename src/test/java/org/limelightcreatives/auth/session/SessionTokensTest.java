package org.limelightcreatives.auth.session;

import org.junit.jupiter.api.Test;
import org.limelightcreatives.auth.AuthProperties;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionTokensTest {

    private static final Instant NOW = Instant.parse("2026-10-30T00:00:00Z");

    private static SessionTokens tokens(String secret, Instant now) {
        var properties = new AuthProperties(secret, URI.create("https://auth.example"), "example", "", true,
                URI.create("https://example/"), Duration.ofDays(30), new AuthProperties.Mail("", "", false));
        return new SessionTokens(properties, Clock.fixed(now, ZoneOffset.UTC));
    }

    private final SessionTokens tokens = tokens("s".repeat(32), NOW);

    @Test
    void roundTrip() {
        assertThat(tokens.userId(tokens.issue("user-1"))).contains("user-1");
    }

    @Test
    void expiresAfterTheSessionLifetime() {
        String token = tokens.issue("user-1");
        assertThat(tokens("s".repeat(32), NOW.plus(Duration.ofDays(30)).minusSeconds(1)).userId(token)).contains("user-1");
        assertThat(tokens("s".repeat(32), NOW.plus(Duration.ofDays(30))).userId(token)).isEmpty();
    }

    @Test
    void onlyTheRightSecretWorks() {
        assertThat(tokens("t".repeat(32), NOW).userId(tokens.issue("user-1"))).isEmpty();
    }

    @Test
    void tamperedTokensAreRejected() {
        String[] parts = tokens.issue("user-1").split("\\.");
        for (String forged : new String[]{
                "v1.user-2." + parts[2] + "." + parts[3],
                "v1.user-1." + (Long.parseLong(parts[2]) + 1) + "." + parts[3],
                "v2.user-1." + parts[2] + "." + parts[3],
                "v1.user-1." + parts[2],
                "v1.user-1.notanumber." + parts[3],
                null, "", "...."}) {
            assertThat(tokens.userId(forged)).as(String.valueOf(forged)).isEmpty();
        }
    }

    @Test
    void shortSecretsAreRefusedAtStartup() {
        assertThatThrownBy(() -> tokens("too-short", NOW)).hasMessageContaining("at least 32 characters");
    }
}
