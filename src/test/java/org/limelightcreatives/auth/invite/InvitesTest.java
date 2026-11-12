package org.limelightcreatives.auth.invite;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.limelightcreatives.auth.DatabaseTest;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

class InvitesTest extends DatabaseTest {

    private static final String IP = "192.0.2.1";

    @Autowired
    Invites invites;

    @BeforeEach
    void people() {
        this.db.sql("""
                INSERT INTO "users" ("id", "email") VALUES ('a', 'a@example.com'), ('b', 'b@example.com')
                """).update();
    }

    private void createInvite(String code, String role, Integer maxUses, String expiresIn) {
        this.db.sql("""
                INSERT INTO "invite_links" ("id", "code", "role", "maxUses", "expiresAt")
                VALUES (?, ?, ?::"Role", ?, (now() at time zone 'utc') + ?::interval)
                """).params(UUID.randomUUID().toString(), code, role, maxUses, expiresIn).update();
    }

    private String roleOf(String id) {
        return this.db.sql("SELECT \"role\"::text FROM \"users\" WHERE \"id\" = ?").param(id).query(String.class).single();
    }

    private int uses(String code) {
        return this.db.sql("SELECT \"uses\" FROM \"invite_links\" WHERE \"code\" = ?").param(code).query(Integer.class).single();
    }

    @Test
    void anInviteMakesYouStaff() {
        createInvite("mentors-2026", "STAFF", null, "7 days");
        assertThat(this.invites.redeem("a", " Mentors-2026 ", IP)).isEqualTo(Invites.Result.REDEEMED);
        assertThat(roleOf("a")).isEqualTo("STAFF");
        assertThat(uses("mentors-2026")).isEqualTo(1);
    }

    @Test
    void usingTheSameLinkAgainDoesntTakeAnotherSlot() {
        createInvite("mentors-2026", "STAFF", 1, "7 days");
        assertThat(this.invites.redeem("a", "mentors-2026", IP)).isEqualTo(Invites.Result.REDEEMED);
        assertThat(this.invites.redeem("a", "mentors-2026", IP)).isEqualTo(Invites.Result.REDEEMED);
        assertThat(uses("mentors-2026")).isEqualTo(1);
    }

    @Test
    void fullExpiredAndUnknownLinksDontWork() {
        createInvite("full", "STAFF", 1, "7 days");
        createInvite("old", "STAFF", null, "-1 day");
        this.invites.redeem("a", "full", IP);

        for (String code : new String[]{"full", "old", "nope", "", "x".repeat(65)})
            assertThat(this.invites.redeem("b", code, IP)).as(code).isEqualTo(Invites.Result.INACTIVE);
        assertThat(roleOf("b")).isEqualTo("PARTICIPANT");
        assertThat(uses("full")).isEqualTo(1);
        assertThat(this.db.sql("SELECT count(*) FROM \"invite_redemptions\" WHERE \"userId\" = 'b'").query(Integer.class).single())
                .as("a refused invite leaves no redemption behind").isZero();
    }

    @Test
    void linksCanNeverMakeAdmins() {
        createInvite("sneaky", "ADMIN", null, "7 days");
        assertThat(this.invites.redeem("a", "sneaky", IP)).isEqualTo(Invites.Result.INACTIVE);
        assertThat(roleOf("a")).isEqualTo("PARTICIPANT");
    }

    @Test
    void invitesNeverDowngradeAnyone() {
        createInvite("participants", "PARTICIPANT", null, "7 days");
        this.db.sql("UPDATE \"users\" SET \"role\" = 'ADMIN' WHERE \"id\" = 'a'").update();
        this.invites.redeem("a", "participants", IP);
        assertThat(roleOf("a")).isEqualTo("ADMIN");
    }

    @Test
    void guessingCodesIsLimitedPerIp() {
        for (int i = 0; i < 20; i++)
            assertThat(invites.redeem("a", "guess-" + i, IP)).isEqualTo(Invites.Result.INACTIVE);
        createInvite("real", "STAFF", null, "7 days");
        assertThat(invites.redeem("a", "real", IP)).isEqualTo(Invites.Result.TOO_MANY_ATTEMPTS);
        assertThat(invites.redeem("a", "real", "192.0.2.2")).isEqualTo(Invites.Result.REDEEMED);
    }

    @Test
    void twoPeopleCantBothTakeTheLastSlot() throws Exception {
        createInvite("last-one", "STAFF", 1, "7 days");
        for (int round = 0; round < 10; round++) {
            this.db.sql("UPDATE \"invite_links\" SET \"uses\" = 0").update();
            this.db.sql("DELETE FROM \"invite_redemptions\"").update();
            this.db.sql("UPDATE \"users\" SET \"role\" = 'PARTICIPANT'").update();

            Callable<Invites.Result> a = () -> this.invites.redeem("a", "last-one", "192.0.2.10");
            Callable<Invites.Result> b = () -> this.invites.redeem("b", "last-one", "192.0.2.11");
            try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
                long redeemed = 0;
                for (Future<Invites.Result> result : pool.invokeAll(List.of(a, b)))
                    if (result.get() == Invites.Result.REDEEMED) redeemed++;
                assertThat(redeemed).isEqualTo(1);
            }
            assertThat(uses("last-one")).isEqualTo(1);
            assertThat(this.db.sql("SELECT count(*) FROM \"users\" WHERE \"role\" = 'STAFF'").query(Integer.class).single()).isEqualTo(1);
        }
    }

    @Test
    void theSamePersonTwiceTakesOneSlot() throws ExecutionException, InterruptedException {
        createInvite("mentors", "STAFF", 5, "7 days");
        Callable<Invites.Result> redeem = () -> this.invites.redeem("a", "mentors", IP);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            for (Future<Invites.Result> result : pool.invokeAll(List.of(redeem, redeem)))
                assertThat(result.get()).isEqualTo(Invites.Result.REDEEMED);
        }
        assertThat(uses("mentors")).isEqualTo(1);
    }
}
