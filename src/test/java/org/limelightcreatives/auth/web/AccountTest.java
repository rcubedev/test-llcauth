package org.limelightcreatives.auth.web;

import org.junit.jupiter.api.Test;
import org.limelightcreatives.auth.DatabaseTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.limelightcreatives.auth.web.Visitor.body;

@AutoConfigureMockMvc
class AccountTest extends DatabaseTest {

    @Autowired MockMvc mvc;

    @Test
    void showsWhoIsSignedIn() throws Exception {
        var visitor = new Visitor(mvc);
        visitor.post("/login/code", "email", "sam@example.com");
        visitor.signIn("sam@example.com", mailer.lastCodeFor("sam@example.com"));
        visitor.post("/login/name", "firstName", "Sam", "lastName", "Lee");

        assertThat(body(visitor.get("/account"))).contains("<title>Account</title>", "Sam Lee", "sam@example.com",
                "<strong>Participant</strong>", "Sign out");
    }

    @Test
    void signedOutVisitorsAreSentToSignIn() throws Exception {
        assertThat(new Visitor(mvc).get("/account").getResponse().getRedirectedUrl())
                .isEqualTo("/login?next=https://auth.limelightcreatives.org/account");
    }

    @Test
    void signingOutClearsTheSharedCookie() throws Exception {
        var visitor = new Visitor(mvc);
        visitor.post("/login/code", "email", "sam@example.com");
        visitor.signIn("sam@example.com", mailer.lastCodeFor("sam@example.com"));

        var result = visitor.post("/logout");
        var cookie = result.getResponse().getCookie(Visitor.COOKIE);
        assertThat(cookie.getMaxAge()).isZero();
        assertThat(cookie.getDomain()).isEqualTo(".limelightcreatives.org");
        assertThat(visitor.session).isNull();
        assertThat(visitor.get("/account").getResponse().getRedirectedUrl()).startsWith("/login");
    }
}
