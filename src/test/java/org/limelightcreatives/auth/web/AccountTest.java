package org.limelightcreatives.auth.web;

import org.junit.jupiter.api.Test;
import org.limelightcreatives.auth.DatabaseTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.limelightcreatives.auth.web.Visitor.body;
import static org.limelightcreatives.auth.web.Visitor.location;

@AutoConfigureMockMvc
class AccountTest extends DatabaseTest {

    @Autowired
    MockMvc mvc;

    private Visitor signedIn() throws Exception {
        Visitor visitor = new Visitor(mvc);
        visitor.signIn("sam@example.com", this.mailer);
        this.db.sql("UPDATE \"users\" SET \"firstName\" = 'Sam', \"lastName\" = 'Lee', \"role\" = 'STAFF'").update();
        return visitor;
    }

    @Test
    void showsWhoIsSignedIn() throws Exception {
        assertThat(body(signedIn().get("/account"))).contains("Sam Lee", "sam@example.com", "<strong>Staff</strong>", "Sign out");
    }

    @Test
    void signingInFromAPrivatePageGoesBackToIt() throws Exception {
        Visitor visitor = new Visitor(this.mvc);
        assertThat(location(visitor.get("/account"))).endsWith("/login");

        visitor.get("/login");
        MvcResult continued = visitor.follow(visitor.signIn("sam@example.com", this.mailer));
        assertThat(location(continued)).isEqualTo("/login/name");
        MvcResult named = visitor.post("/login/name", "firstName", "Sam", "lastName", "Lee");
        assertThat(location(visitor.follow(named))).isEqualTo("https://auth.limelightcreatives.org/account");
    }

    @Test
    void signingOutEndsTheSession() throws Exception {
        Visitor visitor = signedIn();
        MvcResult result = visitor.post("/logout");
        assertThat(location(result)).isEqualTo("/login");
        assertThat(visitor.session).as("the cookie is deleted").isNull();
        assertThat(this.db.sql("SELECT count(*) FROM spring_session WHERE principal_name IS NOT NULL").query(Integer.class).single())
                .isZero();
    }

    @Test
    void signingOutNeedsTheFormsToken() throws Exception {
        Visitor visitor = signedIn();
        MvcResult result = visitor.send(MockMvcRequestBuilders.post("/logout"));
        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(visitor.signedIn()).isTrue();
    }

    @Test
    void aDeletedAccountIsSignedOut() throws Exception {
        Visitor visitor = signedIn();
        this.db.sql("DELETE FROM \"users\"").update();
        assertThat(location(visitor.follow(visitor.get("/account")))).isEqualTo("/login");
        assertThat(visitor.signedIn()).isFalse();
    }
}
