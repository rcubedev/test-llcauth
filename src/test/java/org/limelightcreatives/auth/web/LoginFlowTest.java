package org.limelightcreatives.auth.web;

import org.junit.jupiter.api.Test;
import org.limelightcreatives.auth.DatabaseTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.limelightcreatives.auth.web.Visitor.body;
import static org.limelightcreatives.auth.web.Visitor.location;

@AutoConfigureMockMvc
public class LoginFlowTest extends DatabaseTest {

    private static final String EMAIL = "sam@example.com";
    private static final String DEFAULT = "https://auth.limelightcreatives.org/account";
    private static final String DASHBOARD = "https://dashboard.limelightcreatives.org/";

    @Autowired
    MockMvc mvc;

    private void named(String email) {
        this.db.sql("UPDATE \"users\" SET \"firstName\" = 'Sam', \"lastName\" = 'Lee' WHERE \"email\" = ?").param(email).update();
    }

    @Test
    void rootGoesToTheSignInPage() throws Exception {
        assertThat(location(new Visitor(this.mvc).get("/"))).isEqualTo("/login");
    }

    @Test
    void signingInGoesToNext() throws Exception {
        Visitor visitor = new Visitor(this.mvc);
        String next = DASHBOARD + "participant";

        assertThat(body(visitor.get("/login", "next", next))).contains("Sign in", "We’ll email you a 6-digit code.");

        MvcResult sent = visitor.post("/login", "email", "  Sam@Example.com ");
        assertThat(location(sent)).isEqualTo("/login/code");
        assertThat(body(visitor.follow(sent))).contains("Enter your code", "<span>sam@example.com</span>");

        MvcResult verified = visitor.post("/login/code", "code", this.mailer.lastCodeFor(EMAIL));
        assertThat(location(verified)).isEqualTo("/login/continue");
        named(EMAIL);
        assertThat(location(visitor.follow(verified))).isEqualTo(next);
        assertThat(visitor.signedIn()).isTrue();
    }

    @Test
    void withoutNextYouGoToYourAccount() throws Exception {
        Visitor visitor = new Visitor(this.mvc);
        visitor.get("/login");
        MvcResult verified = visitor.signIn(EMAIL, this.mailer);
        named(EMAIL);
        assertThat(location(visitor.follow(verified))).isEqualTo(DEFAULT);
    }

    @Test
    void signingInGivesANewSessionId() throws Exception {
        Visitor visitor = new Visitor(this.mvc);
        visitor.post("/login", "email", EMAIL);
        String before = visitor.session.getValue();
        visitor.post("/login/code", "code", this.mailer.lastCodeFor(EMAIL));
        assertThat(visitor.session.getValue()).isNotEqualTo(before);
        assertThat(this.db.sql("SELECT principal_name FROM spring_session").query(String.class).list())
                .containsExactly(this.db.sql("SELECT \"id\" FROM \"users\"").query(String.class).single());
    }

    @Test
    void signedInSessionsLastThirtyDays() throws Exception {
        new Visitor(this.mvc).signIn(EMAIL, this.mailer);
        assertThat(this.db.sql("SELECT max_inactive_interval FROM spring_session").query(Integer.class).single())
                .isEqualTo(30 * 24 * 60 * 60);
    }

    @Test
    void theFirstSignInAsksForANameThenGoesOn() throws Exception {
        Visitor visitor = new Visitor(mvc);
        String next = DASHBOARD + "participant";
        visitor.get("/login", "next", next);

        MvcResult continued = visitor.follow(visitor.signIn(EMAIL, mailer));
        assertThat(location(continued)).isEqualTo("/login/name");
        assertThat(body(visitor.follow(continued))).contains("Welcome to Limelight Creatives!");

        MvcResult named = visitor.post("/login/name", "firstName", " Sam ", "lastName", "Lee");
        assertThat(location(visitor.follow(named))).isEqualTo(next);
        assertThat(db.sql("SELECT \"firstName\" || ' ' || \"lastName\" FROM \"users\"").query(String.class).single())
                .isEqualTo("Sam Lee");
    }

    @Test
    void theNameIsRequired() throws Exception {
        Visitor visitor = new Visitor(this.mvc);
        visitor.signIn(EMAIL, this.mailer);

        assertThat(body(visitor.post("/login/name", "firstName", "  ", "lastName", "Lee")))
                .contains("First and last name are required", "value=\"Lee\"");
        assertThat(body(visitor.post("/login/name", "firstName", "x".repeat(101), "lastName", "Lee")))
                .contains("First and last name are required");
        assertThat(this.db.sql("SELECT \"firstName\" FROM \"users\"").query(String.class).optional()).isEmpty();
    }

    @Test
    void theOldDashboardsNameStepStillWorks() throws Exception {
        Visitor visitor = new Visitor(this.mvc);
        visitor.signIn(EMAIL, this.mailer);
        MvcResult result = visitor.follow(visitor.get("/login", "step", "name"));
        assertThat(location(result)).isEqualTo("/login/name");
    }

    @Test
    void peopleWithANameSkipTheNameStep() throws Exception {
        Visitor visitor = new Visitor(this.mvc);
        visitor.signIn(EMAIL, this.mailer);
        named(EMAIL);
        assertThat(location(visitor.get("/login/name"))).isEqualTo("/login/continue");
    }

    @Test
    void savingANameNeedsASignIn() throws Exception {
        MvcResult result = new Visitor(this.mvc).post("/login/name", "firstName", "Sam", "lastName", "Lee");
        assertThat(location(result)).endsWith("/login?resume");
        assertThat(this.db.sql("SELECT count(*) FROM \"users\"").query(Integer.class).single()).isZero();
    }

    @Test
    void aWrongCodeShowsAnError() throws Exception {
        Visitor visitor = new Visitor(this.mvc);
        visitor.post("/login", "email", EMAIL);
        String wrong = this.mailer.lastCodeFor(EMAIL).equals("000000") ? "111111" : "000000";

        MvcResult result = visitor.post("/login/code", "code", wrong);
        assertThat(location(result)).isEqualTo("/login/code");
        assertThat(body(visitor.follow(result))).contains("That code didn't work.", "<span>sam@example.com</span>");
        assertThat(visitor.signedIn()).isFalse();
    }

    @Test
    void aCodeWorksOnlyOnce() throws Exception {
        Visitor visitor = new Visitor(this.mvc);
        visitor.post("/login", "email", EMAIL);
        String code = this.mailer.lastCodeFor(EMAIL);
        visitor.post("/login/code", "code", code);

        Visitor again = new Visitor(this.mvc);
        again.post("/login", "email", "other@example.com"); // a session that has reached the code step
        again.post("/login/code", "code", code);
        assertThat(again.signedIn()).isFalse();
    }

    @Test
    void theCodeStepNeedsAnEmailFirst() throws Exception {
        Visitor visitor = new Visitor(this.mvc);
        assertThat(location(visitor.get("/login/code"))).isEqualTo("/login");
        assertThat(location(visitor.post("/login/code", "code", "123456"))).isEqualTo("/login");
    }

    @Test
    void tooManyCodesShowsWhyAndKeepsTheEmail() throws Exception {
        for (int i = 0; i < 10; i++) new Visitor(this.mvc, "198.51.100." + i).post("/login", "email", EMAIL);

        Visitor visitor = new Visitor(this.mvc, "198.51.100.99");
        MvcResult result = visitor.post("/login", "email", EMAIL);
        assertThat(location(result)).isEqualTo("/login");
        assertThat(body(visitor.follow(result)))
                .contains("Too many codes requested. Please try again later.", "value=\"sam@example.com\"");
    }

    @Test
    void whatPeopleTypeIsEscaped() throws Exception {
        Visitor visitor = new Visitor(this.mvc);
        visitor.signIn(EMAIL, this.mailer);
        String page = body(visitor.post("/login/name", "firstName", "<script>alert(1)</script>", "lastName", " "));
        assertThat(page).contains("value=\"&lt;script>alert(1)&lt;/script>\"").doesNotContain("<script>alert(1)");
    }

    @Test
    void invalidEmailsAreRejected() throws Exception {
        Visitor visitor = new Visitor(this.mvc);
        assertThat(body(visitor.follow(visitor.post("/login", "email", "not-an-email"))))
                .contains("Enter a valid email address.");
    }

    @Test
    void alreadySignedInSkipsTheForm() throws Exception {
        Visitor visitor = new Visitor(this.mvc);
        visitor.signIn(EMAIL, this.mailer);
        named(EMAIL);

        MvcResult result = visitor.get("/login", "next", DASHBOARD + "staff");
        assertThat(location(result)).isEqualTo("/login/continue");
        assertThat(location(visitor.follow(result))).isEqualTo(DASHBOARD + "staff");
    }

    @Test
    void nextCanOnlyPointAtOurOwnSites() throws Exception {
        Visitor visitor = new Visitor(this.mvc);
        visitor.signIn(EMAIL, this.mailer);
        named(EMAIL);

        for (String evil : new String[]{"https://evil.example/", "https://evil-limelightcreatives.org/",
                "//evil.example", "https://limelightcreatives.org@evil.example/", "javascript:alert(1)"}) {
            MvcResult result = visitor.follow(visitor.get("/login", "next", evil));
            assertThat(location(result)).as(evil).isEqualTo(DEFAULT);
        }
    }

    @Test
    void errorPagesUseTheCard() throws Exception {
        MvcResult missing = new Visitor(this.mvc).send(MockMvcRequestBuilders.get("/error")
                .accept("text/html")
                .requestAttr("jakarta.servlet.error.status_code", 404)
                .requestAttr("jakarta.servlet.error.request_uri", "/no-such-page"));
        assertThat(missing.getResponse().getStatus()).isEqualTo(404);
        assertThat(body(missing)).contains("Page not found", "/css/app.css?v=", "Back to sign in");
    }

    @Test
    void errorPagesLeadSignedInPeopleToTheirAccount() throws Exception {
        Visitor visitor = new Visitor(this.mvc);
        visitor.signIn(EMAIL, this.mailer);
        named(EMAIL);
        MvcResult missing = visitor.send(MockMvcRequestBuilders.get("/error")
                .accept("text/html")
                .requestAttr("jakarta.servlet.error.status_code", 404)
                .requestAttr("jakarta.servlet.error.request_uri", "/no-such-page"));
        assertThat(body(missing)).contains("Page not found", "Back to your account").doesNotContain("Back to sign in");
    }

    @Test
    void forbiddenPagesSayWhy() throws Exception {
        Visitor visitor = new Visitor(this.mvc);
        visitor.signIn(EMAIL, this.mailer);
        named(EMAIL);
        MvcResult denied = visitor.send(MockMvcRequestBuilders.get("/error")
                .accept("text/html")
                .requestAttr("jakarta.servlet.error.status_code", 403)
                .requestAttr("jakarta.servlet.error.request_uri", "/apps"));
        assertThat(body(denied)).contains("Not allowed", "Your account can’t use this page.");
    }
}
