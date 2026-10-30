package org.limelightcreatives.auth.web;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.limelightcreatives.auth.DatabaseTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.limelightcreatives.auth.web.Visitor.body;

/** Signing in from start to finish, as a browser would. */
@AutoConfigureMockMvc
class LoginFlowTest extends DatabaseTest {

    @Autowired MockMvc mvc;

    private static final String EMAIL = "sam@example.com";

    private String userColumn(String column) {
        return db.sql("SELECT \"" + column + "\"::text FROM \"users\" WHERE \"email\" = ?").param(EMAIL)
                .query(String.class).single();
    }

    @Test
    void rootGoesToTheSignInPage() throws Exception {
        var result = new Visitor(mvc).get("/");
        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/login");
    }

    @Test
    void firstSignInAsksForANameThenGoesToNext() throws Exception {
        var visitor = new Visitor(mvc);
        String next = "https://dashboard.limelightcreatives.org/participant";

        assertThat(body(visitor.get("/login?next=" + next))).contains("Sign in", "We’ll email you a 6-digit code.");

        var sent = visitor.post("/login/code", "email", "  Sam@Example.com ", "next", next);
        assertThat(body(sent)).contains("Enter your code", "Sent to <span>sam@example.com</span>");
        assertThat(userColumn("role")).isEqualTo("PARTICIPANT");

        var verified = visitor.signIn(EMAIL, mailer.lastCodeFor(EMAIL), "next", next);
        assertThat(body(verified)).contains("Welcome to Limelight Creatives!");
        assertThat(visitor.session).isNotNull();

        var named = visitor.post("/login/name", "firstName", " Sam ", "lastName", "Lee", "next", next);
        assertThat(named.getResponse().getRedirectedUrl()).isEqualTo(next);
        assertThat(userColumn("firstName")).isEqualTo("Sam");
    }

    @Test
    void theSessionCookieMatchesWhatTheDashboardReads() throws Exception {
        var visitor = new Visitor(mvc);
        visitor.post("/login/code", "email", EMAIL);
        visitor.signIn(EMAIL, mailer.lastCodeFor(EMAIL));

        Cookie cookie = visitor.session;
        assertThat(cookie.getName()).isEqualTo("__Secure-authjs.session-token");
        assertThat(cookie.getDomain()).isEqualTo(".limelightcreatives.org");
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.getAttribute("SameSite")).isEqualTo("Lax");
        assertThat(cookie.getMaxAge()).isEqualTo(30 * 24 * 60 * 60);
    }

    @Test
    void returningUserGoesStraightToNext() throws Exception {
        var first = new Visitor(mvc);
        first.post("/login/code", "email", EMAIL);
        first.signIn(EMAIL, mailer.lastCodeFor(EMAIL));
        first.post("/login/name", "firstName", "Sam", "lastName", "Lee");

        var later = new Visitor(mvc);
        later.post("/login/code", "email", EMAIL);
        var result = later.signIn(EMAIL, mailer.lastCodeFor(EMAIL), "next", "https://dashboard.limelightcreatives.org/staff");
        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("https://dashboard.limelightcreatives.org/staff");
    }

    @Test
    void alreadySignedInSkipsTheForm() throws Exception {
        var visitor = new Visitor(mvc);
        visitor.post("/login/code", "email", EMAIL);
        visitor.signIn(EMAIL, mailer.lastCodeFor(EMAIL));

        // No name yet: the dashboard sends people here with step=name, and they get the name form.
        assertThat(body(visitor.get("/login?step=name"))).contains("Welcome to Limelight Creatives!");

        visitor.post("/login/name", "firstName", "Sam", "lastName", "Lee");
        var result = visitor.get("/login?next=https://dashboard.limelightcreatives.org/staff");
        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("https://dashboard.limelightcreatives.org/staff");
    }

    @Test
    void wrongCodeShowsAnErrorAndKeepsTheEmail() throws Exception {
        var visitor = new Visitor(mvc);
        visitor.post("/login/code", "email", EMAIL);
        String wrong = mailer.lastCodeFor(EMAIL).equals("000000") ? "111111" : "000000";

        var result = visitor.signIn(EMAIL, wrong);
        assertThat(body(result)).contains("That code didn&#39;t work.", "value=\"sam@example.com\"");
        assertThat(visitor.session).isNull();
    }

    @Test
    void aCodeWorksOnlyOnce() throws Exception {
        var visitor = new Visitor(mvc);
        visitor.post("/login/code", "email", EMAIL);
        String code = mailer.lastCodeFor(EMAIL);
        visitor.signIn(EMAIL, code);

        var again = new Visitor(mvc);
        assertThat(body(again.signIn(EMAIL, code))).contains("That code didn&#39;t work.");
        assertThat(again.session).isNull();
    }

    @Test
    void fiveWrongTriesUseUpTheCode() throws Exception {
        var visitor = new Visitor(mvc);
        visitor.post("/login/code", "email", EMAIL);
        String code = mailer.lastCodeFor(EMAIL);
        String wrong = code.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 5; i++) visitor.signIn(EMAIL, wrong);

        visitor.signIn(EMAIL, code);
        assertThat(visitor.session).isNull();
    }

    @Test
    void expiredCodesDontWork() throws Exception {
        var visitor = new Visitor(mvc);
        visitor.post("/login/code", "email", EMAIL);
        db.sql("UPDATE \"verification_codes\" SET \"expiresAt\" = \"expiresAt\" - interval '11 minutes'").update();

        visitor.signIn(EMAIL, mailer.lastCodeFor(EMAIL));
        assertThat(visitor.session).isNull();
    }

    @Test
    void onlyTheNewestCodeWorks() throws Exception {
        var visitor = new Visitor(mvc);
        visitor.post("/login/code", "email", EMAIL);
        String old = mailer.lastCodeFor(EMAIL);
        visitor.post("/login/code", "email", EMAIL);
        if (old.equals(mailer.lastCodeFor(EMAIL))) return; // 1 in a million: both codes are the same

        visitor.signIn(EMAIL, old);
        assertThat(visitor.session).isNull();
    }

    @Test
    void fiveCodesPerEmailPerHour() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(body(new Visitor(mvc, "198.51.100." + i).post("/login/code", "email", EMAIL))).contains("Enter your code");
        }
        assertThat(body(new Visitor(mvc, "198.51.100.99").post("/login/code", "email", EMAIL)))
                .contains("Too many codes requested. Please try again later.");
    }

    @Test
    void fifteenCodesPerIpPerHour() throws Exception {
        var visitor = new Visitor(mvc);
        for (int i = 0; i < 15; i++) visitor.post("/login/code", "email", "person" + i + "@example.com");
        assertThat(body(visitor.post("/login/code", "email", "another@example.com")))
                .contains("Too many requests. Please try again later.");
        assertThat(mailer.sentTo("another@example.com")).isFalse();
    }

    @Test
    void invalidEmailsAreRejected() throws Exception {
        assertThat(body(new Visitor(mvc).post("/login/code", "email", "not-an-email")))
                .contains("Enter a valid email address.");
    }

    @Test
    void nextCanOnlyPointAtOurOwnSites() throws Exception {
        var visitor = new Visitor(mvc);
        visitor.post("/login/code", "email", EMAIL);
        visitor.signIn(EMAIL, mailer.lastCodeFor(EMAIL));
        visitor.post("/login/name", "firstName", "Sam", "lastName", "Lee");

        for (String evil : new String[]{"https://evil.example/", "https://evil-limelightcreatives.org/",
                "//evil.example", "https://limelightcreatives.org@evil.example/", "javascript:alert(1)"}) {
            var result = visitor.get("/login", "next", evil);
            assertThat(result.getResponse().getRedirectedUrl()).as(evil).isEqualTo("https://dashboard.limelightcreatives.org/");
        }
    }

    @Test
    void nameIsRequired() throws Exception {
        var visitor = new Visitor(mvc);
        visitor.post("/login/code", "email", EMAIL);
        visitor.signIn(EMAIL, mailer.lastCodeFor(EMAIL));

        assertThat(body(visitor.post("/login/name", "firstName", "  ", "lastName", "Lee")))
                .contains("First and last name are required");
        assertThat(db.sql("SELECT \"firstName\" FROM \"users\"").query(String.class).optional()).isEmpty();
    }

    @Test
    void savingANameNeedsASession() throws Exception {
        var result = new Visitor(mvc).post("/login/name", "firstName", "Sam", "lastName", "Lee");
        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/login");
    }

    @Test
    void formPostsFromOtherSitesAreRefused() throws Exception {
        var result = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/login/code")
                .header("Origin", "https://dashboard.limelightcreatives.org")
                .param("email", EMAIL)).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(mailer.sentTo(EMAIL)).isFalse();
    }
}
