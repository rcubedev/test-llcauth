package org.limelightcreatives.auth.session;

import org.limelightcreatives.auth.AuthProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * The session cookie's value: {@code v1.<user id>.<expiry, epoch seconds>.<HMAC-SHA256 signature>}.
 *
 * <p>It only says who is signed in and until when. Role and name are read from the database on
 * every check, so a role change (e.g. a redeemed invite) applies straight away.
 */
@Component
public class SessionTokens {

    private static final String VERSION = "v1";

    private final SecretKeySpec key;
    private final AuthProperties properties;
    private final Clock clock;

    public SessionTokens(AuthProperties properties, Clock clock) {
        this.key = new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.properties = properties;
        this.clock = clock;
    }

    public String issue(String userId) {
        long expires = Instant.now(clock).plus(properties.sessionLifetime()).getEpochSecond();
        String payload = VERSION + "." + userId + "." + expires;
        return payload + "." + sign(payload);
    }

    /** The signed-in user's id, if the token is genuine and hasn't expired. */
    public Optional<String> userId(String token) {
        if (token == null) return Optional.empty();
        String[] parts = token.split("\\.");
        if (parts.length != 4 || !parts[0].equals(VERSION)) return Optional.empty();

        String payload = parts[0] + "." + parts[1] + "." + parts[2];
        byte[] expected = sign(payload).getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(expected, parts[3].getBytes(StandardCharsets.US_ASCII))) return Optional.empty();

        try {
            if (Instant.now(clock).getEpochSecond() >= Long.parseLong(parts[2])) return Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        return Optional.of(parts[1]);
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            byte[] signature = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e); // HmacSHA256 is always available
        }
    }
}
