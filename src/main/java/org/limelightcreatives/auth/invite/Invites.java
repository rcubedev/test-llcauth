package org.limelightcreatives.auth.invite;

import org.limelightcreatives.auth.login.RateLimits;
import org.limelightcreatives.auth.user.Role;
import org.limelightcreatives.auth.user.Users;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

@Service
public class Invites {

    static final Duration FAIL_WINDOW = Duration.ofHours(1);
    static final int MAX_BAD_CODES_PER_IP = 20;

    public enum Result {
        REDEEMED(null),
        INACTIVE("This invite link is no longer active. Ask an organiser for help."),
        TOO_MANY_ATTEMPTS("Too many attempts. Please try again later.");

        public final String message;

        Result(String message) {
            this.message = message;
        }
    }

    private enum Outcome {
        OK,
        UNKNOWN,
        CLOSED
    }

    private record Invite(String id, Role role) {}

    private final JdbcClient db;
    private final Users users;
    private final RateLimits rateLimits;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public Invites(JdbcClient db, Users users, RateLimits rateLimits, TransactionTemplate transaction, Clock clock) {
        this.db = db;
        this.users = users;
        this.rateLimits = rateLimits;
        this.transaction = transaction;
        this.clock = clock;
    }

    public Result redeem(String userId, String rawCode, String ip) {
        String code = rawCode == null ? "" : rawCode.trim().toLowerCase(Locale.ROOT);
        if (code.isEmpty() || code.length() > 64) return Result.INACTIVE;

        if (this.rateLimits.count(ip, RateLimits.INVITE_FAIL, FAIL_WINDOW) >= MAX_BAD_CODES_PER_IP)
            return Result.TOO_MANY_ATTEMPTS;

        Outcome outcome = this.transaction.execute(status -> {
            Optional<Invite> invite = this.db.sql("SELECT \"id\", \"role\"::text AS \"role\" FROM \"invite_links\" WHERE \"code\" = ?")
                    .param(code)
                    .query((rs, row) -> new Invite(rs.getString("id"), Role.valueOf(rs.getString("role"))))
                    .optional();

            if (invite.isEmpty() || invite.get().role() == Role.ADMIN) return Outcome.UNKNOWN;

            boolean firstTime = this.db.sql("""
                    INSERT INTO "invite_redemptions" ("inviteId", "userId", "redeemedAt") VALUES (?, ?, ?)
                    ON CONFLICT DO NOTHING
                    """)
                    .params(invite.get().id(), userId, LocalDateTime.now(this.clock))
                    .update() == 1;

            if (firstTime) {
                int claimed = this.db.sql("""
                        UPDATE "invite_links" SET "uses" = "uses" + 1
                        WHERE "id" = ? AND "expiresAt" > ? AND ("maxUses" IS NULL OR "uses" < "maxUses")
                        """)
                        .params(invite.get().id(), LocalDateTime.now(this.clock))
                        .update();
                if (claimed == 0) {
                    status.setRollbackOnly();
                    return Outcome.CLOSED;
                }
            }

            this.users.promoteParticipant(userId, invite.get().role());
            return Outcome.OK;
        });

        return switch (outcome) {
            case OK -> Result.REDEEMED;
            case CLOSED -> Result.INACTIVE;
            case UNKNOWN -> {
                this.rateLimits.record(ip, RateLimits.INVITE_FAIL);
                yield Result.INACTIVE;
            }
        };
    }
}
