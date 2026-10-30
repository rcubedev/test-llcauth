package org.limelightcreatives.auth.login;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Repository;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * The "verification_codes" table: 6-digit sign-in codes, stored only as BCrypt hashes.
 * A code works once, for 10 minutes, and allows {@value #MAX_ATTEMPTS} tries.
 */
@Repository
public class LoginCodes {

    static final Duration LIFETIME = Duration.ofMinutes(10);
    static final int MAX_ATTEMPTS = 5;

    private final JdbcClient db;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(10);

    public LoginCodes(JdbcClient db, Clock clock) {
        this.db = db;
        this.clock = clock;
    }

    /** Creates a new code for this user and returns it (the database only keeps its hash). */
    public String issue(String userId) {
        String code = "%06d".formatted(random.nextInt(1_000_000));
        LocalDateTime now = LocalDateTime.now(clock);
        db.sql("""
                INSERT INTO "verification_codes" ("id", "userId", "codeHash", "expiresAt", "createdAt")
                VALUES (?, ?, ?, ?, ?)
                """)
                .params(UUID.randomUUID().toString(), userId, bcrypt.encode(code), now.plus(LIFETIME), now)
                .update();
        return code;
    }

    /** How many codes this user was sent in the last {@code window}. */
    public int issuedSince(String userId, Duration window) {
        return db.sql("SELECT count(*) FROM \"verification_codes\" WHERE \"userId\" = ? AND \"createdAt\" > ?")
                .params(userId, LocalDateTime.now(clock).minus(window))
                .query(Integer.class).single();
    }

    /**
     * Checks a code against the user's latest unused, unexpired one, and uses it up if it matches.
     * Each check takes one of the code's attempts before comparing, in the same UPDATE that checks
     * the limit, so many guesses sent at once still can't get past it.
     */
    public boolean verify(String userId, String code) {
        if (code == null || !code.matches("\\d{6}")) return false;
        LocalDateTime now = LocalDateTime.now(clock);

        record Attempt(String id, String codeHash) {}
        var attempt = db.sql("""
                UPDATE "verification_codes" SET "attempts" = "attempts" + 1
                WHERE "id" = (
                    SELECT "id" FROM "verification_codes"
                    WHERE "userId" = ? AND "usedAt" IS NULL AND "expiresAt" > ?
                    ORDER BY "createdAt" DESC LIMIT 1
                ) AND "attempts" < ?
                RETURNING "id", "codeHash"
                """)
                .params(userId, now, MAX_ATTEMPTS)
                .query((rs, row) -> new Attempt(rs.getString("id"), rs.getString("codeHash")))
                .optional();

        if (attempt.isEmpty() || !bcrypt.matches(code, attempt.get().codeHash())) return false;

        // Only one request can use the code, even if two arrive together with the right one.
        int used = db.sql("UPDATE \"verification_codes\" SET \"usedAt\" = ? WHERE \"id\" = ? AND \"usedAt\" IS NULL")
                .params(now, attempt.get().id())
                .update();
        return used == 1;
    }
}
