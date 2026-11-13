package org.limelightcreatives.auth.login;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class LoginCodes {

    static final Duration LIFETIME = Duration.ofMinutes(10);
    static final int MAX_ATTEMPTS = 5;
    static final Duration ATTEMPT_WINDOW = Duration.ofMinutes(15);
    static final int CODES_THAT_WORK = 3;

    private final JdbcClient db;
    private final Clock clock;
    private final TransactionTemplate transaction;
    private final SecureRandom random = new SecureRandom();
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(10);

    public LoginCodes(JdbcClient db, Clock clock, TransactionTemplate transaction) {
        this.db = db;
        this.clock = clock;
        this.transaction = transaction;
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

        return Boolean.TRUE.equals(transaction.execute(_ -> {
            this.db.sql("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))").param("verify-code:" + userId)
                    .query((_, _) -> 0).single();

            record Candidate(String id, String codeHash) {}
            List<Candidate> candidates = this.db.sql("""
                    SELECT "id", "codeHash" FROM "verification_codes"
                    WHERE "userId" = ? AND "usedAt" IS NULL AND "expiresAt" > ?
                    ORDER BY "createdAt" DESC LIMIT ?
                    """)
                    .params(userId, now, CODES_THAT_WORK)
                    .query((rs, _) -> new Candidate(rs.getString("id"), rs.getString("codeHash")))
                    .list();
            if (candidates.isEmpty()) return false;

            int attempts = this.db.sql("""
                    SELECT coalesce(sum("attempts"), 0) FROM "verification_codes" WHERE "userId" = ? AND "createdAt" > ?
                    """)
                    .params(userId, now.minus(ATTEMPT_WINDOW))
                    .query(Integer.class).single();
            if (attempts >= MAX_ATTEMPTS) return false;

            this.db.sql("UPDATE \"verification_codes\" SET \"attempts\" = \"attempts\" + 1 WHERE \"id\" = ?")
                    .param(candidates.getFirst().id()).update();

            if (candidates.stream().noneMatch(candidate -> this.bcrypt.matches(code, candidate.codeHash()))) return false;

            this.db.sql("UPDATE \"verification_codes\" SET \"usedAt\" = ? WHERE \"userId\" = ? AND \"usedAt\" IS NULL")
                    .params(now, userId)
                    .update();
            return true;
        }));
    }
}
