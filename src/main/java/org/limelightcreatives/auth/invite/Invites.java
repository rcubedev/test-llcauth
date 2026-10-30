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

/**
 * Invite links ({@code /login?invite=<code>}): rows in "invite_links" that give whoever signs in
 * through them a role, usually STAFF. Create one with SQL (see README); delete the row to burn it.
 */
@Service
public class Invites {

    static final Duration FAIL_WINDOW = Duration.ofHours(1);
    // Only wrong codes count, so a room of mentors on one venue wifi isn't throttled.
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

    private enum Outcome { OK, UNKNOWN, CLOSED }

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

    /** Uses the invite for this (signed-in) user. Using the same link again is fine and changes nothing. */
    public Result redeem(String userId, String rawCode, String ip) {
        String code = rawCode == null ? "" : rawCode.trim().toLowerCase(Locale.ROOT);
        if (code.isEmpty() || code.length() > 64) return Result.INACTIVE;

        if (rateLimits.count(ip, RateLimits.INVITE_FAIL, FAIL_WINDOW) >= MAX_BAD_CODES_PER_IP) {
            return Result.TOO_MANY_ATTEMPTS;
        }

        Outcome outcome = transaction.execute(status -> {
            var invite = db.sql("SELECT \"id\", \"role\"::text AS \"role\" FROM \"invite_links\" WHERE \"code\" = ?")
                    .param(code)
                    .query((rs, row) -> new Invite(rs.getString("id"), Role.valueOf(rs.getString("role"))))
                    .optional();
            // Never let a shared link create admins, whatever the row says.
            if (invite.isEmpty() || invite.get().role() == Role.ADMIN) return Outcome.UNKNOWN;

            // One row per person per link: signing in through it again doesn't use another slot.
            // (If the same person redeems twice at once, the second waits for the first, then finds its row.)
            boolean firstTime = db.sql("""
                    INSERT INTO "invite_redemptions" ("inviteId", "userId", "redeemedAt") VALUES (?, ?, ?)
                    ON CONFLICT DO NOTHING
                    """)
                    .params(invite.get().id(), userId, LocalDateTime.now(clock))
                    .update() == 1;

            if (firstTime) {
                // Expiry and the use limit are checked in the same UPDATE that takes the slot,
                // so two people grabbing the last slot can't both get it.
                int claimed = db.sql("""
                        UPDATE "invite_links" SET "uses" = "uses" + 1
                        WHERE "id" = ? AND "expiresAt" > ? AND ("maxUses" IS NULL OR "uses" < "maxUses")
                        """)
                        .params(invite.get().id(), LocalDateTime.now(clock))
                        .update();
                if (claimed == 0) {
                    status.setRollbackOnly(); // undo the redemption row
                    return Outcome.CLOSED;
                }
            }

            users.promoteParticipant(userId, invite.get().role());
            return Outcome.OK;
        });

        return switch (outcome) {
            case OK -> Result.REDEEMED;
            case CLOSED -> Result.INACTIVE;
            case UNKNOWN -> {
                rateLimits.record(ip, RateLimits.INVITE_FAIL);
                yield Result.INACTIVE;
            }
        };
    }
}
