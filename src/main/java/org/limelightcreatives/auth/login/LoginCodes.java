package org.limelightcreatives.auth.login;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Repository;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

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

    public String issue(String userId) {
        String code = "%06d".formatted(this.random.nextInt(1_000_000));
        LocalDateTime now = LocalDateTime.now(this.clock);
        this.db.sql("""
                    INSERT INTO "verification_codes" ("id", "userId", "codeHash", "expiresAt", "createdAt")
                    VALUES (?, ?, ?, ?, ?)
                    """)
                .params(UUID.randomUUID().toString(), userId, this.bcrypt.encode(code), now.plus(LIFETIME), now)
                .update();
        return code;
    }

    public int issuedSince(String userId, Duration window) {
        return this.db.sql("SELECT count(*) FROM \"verification_codes\" WHERE \"userId\" = ? AND \"createdAt\" > ?")
                .params(userId, LocalDateTime.now(this.clock).minus(window))
                .query(Integer.class).single();
    }

    public boolean verify(String userId, String code) {
        if (code == null || !code.matches("\\d{6}")) return false;
        LocalDateTime now = LocalDateTime.now(this.clock);

        record Attempt(String id, String codeHash) {}
        Optional<Attempt> attempt = this.db.sql("""
                UPDATE "verification_codes" SET "attempts" = "attempts" + 1
                WHERE "id" = (
                    SELECT "id" FROM "verification_codes"
                    WHERE "userId" = ? AND "usedAt" IS NULL AND "expiresAt" > ?
                    ORDER BY "createdAt" DESC LIMIT 1
                ) AND ATTEMPTS < ?
                RETURNING "id", "codeHash"
                """)
                .params(userId, now, MAX_ATTEMPTS)
                .query((rs, row) -> new Attempt(rs.getString("id"), rs.getString("codeHash")))
                .optional();

        if (attempt.isEmpty() || !this.bcrypt.matches(code, attempt.get().codeHash())) return false;

        int used = this.db.sql("UPDATE \"verification_codes\" SET \"usedAt\" = ? WHERE \"id\" = ? AND \"usedAt\" IS NULL")
                .params(now, attempt.get().id())
                .update();
        return used == 1;
    }
}
