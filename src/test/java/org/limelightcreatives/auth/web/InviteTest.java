package org.limelightcreatives.auth.web;

import org.junit.jupiter.api.Test;
import org.limelightcreatives.auth.DatabaseTest;
import org.limelightcreatives.auth.invite.Invites;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.limelightcreatives.auth.web.Visitor.body;

/** Invite links: /login?invite=<code> makes whoever signs in through it staff. */
@AutoConfigureMockMvc
class InviteTest extends DatabaseTest {

    @Autowired MockMvc mvc;
    @Autowired Invites invites;

    private void createInvite(String code, String role, Integer maxUses, String expiresIn) {
        db.sql("""
                INSERT INTO "invite_links" ("id", "code", "role", "maxUses", "expiresAt")
                VALUES (?, ?, ?::"Role", ?, (now() at time zone 'utc') + ?::interval)
                """).params(UUID.randomUUID().toString(), code, role, maxUses, expiresIn).update();
    }

    private String roleOf(String email) {
        return db.sql("SELECT \"role\"::text FROM \"users\" WHERE \"email\" = ?").param(email).query(String.class).single();
    }

    private int uses(String code) {
        return db.sql("SELECT \"uses\" FROM \"invite_links\" WHERE \"code\" = ?").param(code).query(Integer.class).single();
    }

    /** Signs in through the invite link; returns the last page. */
    private Visitor joinThrough(String invite, String email) throws Exception {
        var visitor = new Visitor(mvc);
        assertThat(body(visitor.get("/login?invite=" + invite))).contains("Join Limelight Creatives",
                "You have been invited to join as a staff member!", "name=\"invite\" value=\"" + invite + "\"");
        visitor.post("/login/code", "email", email, "invite", invite);
        visitor.signIn(email, mailer.lastCodeFor(email), "invite", invite);
        return visitor;
    }

    @Test
    void signingInThroughAnInviteMakesYouStaff() throws Exception {
        createInvite("mentors-2026", "STAFF", null, "7 days");
        joinThrough("Mentors-2026", "mentor@example.com"); // codes are case-insensitive
        assertThat(roleOf("mentor@example.com")).isEqualTo("STAFF");
        assertThat(uses("mentors-2026")).isEqualTo(1);
    }

    @Test
    void alreadySignedInPeopleJustOpenTheLink() throws Exception {
        createInvite("mentors-2026", "STAFF", null, "7 days");
        var visitor = new Visitor(mvc);
        visitor.post("/login/code", "email", "sam@example.com");
        visitor.signIn("sam@example.com", mailer.lastCodeFor("sam@example.com"));
        visitor.post("/login/name", "firstName", "Sam", "lastName", "Lee");

        var result = visitor.get("/login?invite=mentors-2026&next=https://dashboard.limelightcreatives.org/staff");
        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("https://dashboard.limelightcreatives.org/staff");
        assertThat(roleOf("sam@example.com")).isEqualTo("STAFF");
    }

    @Test
    void usingTheSameLinkAgainDoesntTakeAnotherSlot() throws Exception {
        createInvite("mentors-2026", "STAFF", 1, "7 days");
        joinThrough("mentors-2026", "mentor@example.com");
        joinThrough("mentors-2026", "mentor@example.com");
        assertThat(uses("mentors-2026")).isEqualTo(1);
    }

    @Test
    void fullExpiredAndUnknownLinksDontWork() throws Exception {
        createInvite("full", "STAFF", 1, "7 days");
        joinThrough("full", "first@example.com");
        createInvite("old", "STAFF", null, "-1 day");

        for (String code : new String[]{"full", "old", "nope"}) {
            String email = code + "@example.com";
            var visitor = new Visitor(mvc);
            visitor.post("/login/code", "email", email, "invite", code);
            var result = visitor.signIn(email, mailer.lastCodeFor(email), "invite", code);
            assertThat(body(result)).as(code).contains("You’re signed in",
                    "This invite link is no longer active. Ask an organiser for help.");
            assertThat(roleOf(email)).as(code).isEqualTo("PARTICIPANT");
            assertThat(visitor.session).as("still signed in").isNotNull();
        }
        assertThat(uses("full")).isEqualTo(1);
    }

    @Test
    void linksCanNeverMakeAdmins() throws Exception {
        createInvite("sneaky", "ADMIN", null, "7 days");
        joinThrough("sneaky", "someone@example.com");
        assertThat(roleOf("someone@example.com")).isEqualTo("PARTICIPANT");
    }

    @Test
    void invitesNeverDowngradeAnyone() throws Exception {
        createInvite("participants", "PARTICIPANT", null, "7 days");
        var visitor = new Visitor(mvc);
        visitor.post("/login/code", "email", "boss@example.com");
        visitor.signIn("boss@example.com", mailer.lastCodeFor("boss@example.com"));
        db.sql("UPDATE \"users\" SET \"role\" = 'ADMIN'").update();

        visitor.get("/login?invite=participants");
        assertThat(roleOf("boss@example.com")).isEqualTo("ADMIN");
    }

    @Test
    void guessingCodesIsLimitedPerIp() throws Exception {
        db.sql("INSERT INTO \"users\" (\"id\", \"email\") VALUES ('u1', 'guess@example.com')").update();
        for (int i = 0; i < 20; i++) {
            assertThat(invites.redeem("u1", "guess-" + i, "192.0.2.1")).isEqualTo(Invites.Result.INACTIVE);
        }
        createInvite("real", "STAFF", null, "7 days");
        assertThat(invites.redeem("u1", "real", "192.0.2.1")).isEqualTo(Invites.Result.TOO_MANY_ATTEMPTS);
        assertThat(invites.redeem("u1", "real", "192.0.2.2")).isEqualTo(Invites.Result.REDEEMED);
    }

    @Test
    void twoPeopleCantBothTakeTheLastSlot() throws Exception {
        createInvite("last-one", "STAFF", 1, "7 days");
        for (int round = 0; round < 10; round++) {
            db.sql("UPDATE \"invite_links\" SET \"uses\" = 0").update();
            db.sql("DELETE FROM \"invite_redemptions\"").update();
            db.sql("DELETE FROM \"users\"").update();
            db.sql("INSERT INTO \"users\" (\"id\", \"email\") VALUES ('a', 'a@example.com'), ('b', 'b@example.com')").update();

            try (var pool = Executors.newFixedThreadPool(2)) {
                Callable<Invites.Result> a = () -> invites.redeem("a", "last-one", "192.0.2.10");
                Callable<Invites.Result> b = () -> invites.redeem("b", "last-one", "192.0.2.11");
                var results = pool.invokeAll(java.util.List.of(a, b));
                long redeemed = results.stream().filter(f -> {
                    try { return f.get() == Invites.Result.REDEEMED; } catch (Exception e) { throw new RuntimeException(e); }
                }).count();
                assertThat(redeemed).isEqualTo(1);
            }
            assertThat(uses("last-one")).isEqualTo(1);
            assertThat(db.sql("SELECT count(*) FROM \"users\" WHERE \"role\" = 'STAFF'").query(Integer.class).single()).isEqualTo(1);
        }
    }
}
