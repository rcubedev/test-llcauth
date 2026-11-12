package org.limelightcreatives.auth.web;

import org.junit.jupiter.api.Test;
import org.limelightcreatives.auth.DatabaseTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.limelightcreatives.auth.web.Visitor.body;

@AutoConfigureMockMvc
public class InviteLinkTest extends DatabaseTest {

    @Autowired
    MockMvc mvc;

    private void createInvite(String code, String expiresIn) {
        this.db.sql("""
                INSERT INTO "invite_links" ("id", "code", "role", "expiresAt")
                VALUES (?, ?, 'STAFF', (now() at time zone 'utc') + ?::interval)
                """).params(UUID.randomUUID().toString(), code, expiresIn).update();
    }

    private String roleOf(String email) {
        return this.db.sql("SELECT \"role\"::text FROM \"users\" WHERE \"email\" = ?").param(email).query(String.class).single();
    }

    @Test
    void signingInThroughAnInviteMakesYouStaff() throws Exception {
        createInvite("mentors-2026", "7 days");
        Visitor visitor = new Visitor(mvc);
        assertThat(body(visitor.get("/login", "invite", "Mentors-2026")))
                .contains("Join Limelight Creatives", "You have been invited to join as a staff member!");

        visitor.follow(visitor.signIn("mentor@example.com", this.mailer));
        assertThat(roleOf("mentor@example.com")).isEqualTo("STAFF");
    }

    @Test
    void alreadySignedInPeopleJustOpenTheLink() throws Exception {
        createInvite("mentors-2026", "7 days");
        Visitor visitor = new Visitor(mvc);
        visitor.follow(visitor.signIn("sam@example.com", this.mailer));

        visitor.follow(visitor.get("/login", "invite", "mentors-2026"));
        assertThat(roleOf("sam@example.com")).isEqualTo("STAFF");
    }

    @Test
    void aDeadLinkStillSignsYouInAndSaysWhy() throws Exception {
        createInvite("old", "-1 day");
        Visitor visitor = new Visitor(mvc);
        visitor.get("/login", "invite", "old");
        var page = visitor.follow(visitor.signIn("sam@example.com", this.mailer));

        assertThat(body(page)).contains("You’re signed in", "This invite link is no longer active. Ask an organiser for help.");
        assertThat(roleOf("sam@example.com")).isEqualTo("PARTICIPANT");
        assertThat(visitor.signedIn()).isTrue();
    }
}
