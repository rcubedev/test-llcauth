package org.limelightcreatives.auth.web;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.limelightcreatives.auth.DatabaseTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.limelightcreatives.auth.web.Visitor.body;
import static org.limelightcreatives.auth.web.Visitor.location;

@AutoConfigureMockMvc
@TestPropertySource(properties = "test.application=real-csrf-tokens")
class SignInPageTest extends DatabaseTest {

    private static String EMAIL = "sam@example.com";

    @Autowired
    MockMvc mvc;

    @Test
    void justOpeningThePageStoresNothing() throws Exception {
        Visitor visitor = new Visitor(this.mvc);
        assertThat(body(visitor.get("/login"))).contains("Sign in");
        assertThat(visitor.session).isNull();
        assertThat(this.db.sql("SELECT count(*) FROM spring_session").query(Integer.class).single()).isZero();
    }

    @Test
    void theFormCarriesItsOwnCsrfToken() throws Exception {
        Visitor visitor = new Visitor(this.mvc);
        MvcResult page = visitor.get("/login");
        Matcher token = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(body(page));
        assertThat(token.find()).isTrue();

        Cookie cookie = page.getResponse().getCookie("__Host-csrf");
        assertThat(cookie).isNotNull();
        assertThat(cookie.getDomain()).isNull();
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.isHttpOnly()).isTrue();

        MvcResult post = visitor.send(MockMvcRequestBuilders.post("/login").cookie(cookie)
                .param("email", EMAIL).param("_csrf", token.group(1)));
        assertThat(location(post)).isEqualTo("/login/code");
        assertThat(this.mailer.sentTo(EMAIL)).isTrue();
    }

    @Test
    void aTokenWithoutItsCookieIsRefused() throws Exception {
        var visitor = new Visitor(this.mvc);
        var token = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(body(visitor.get("/login")));
        assertThat(token.find()).isTrue();

        var post = visitor.send(MockMvcRequestBuilders.post("/login").param("email", EMAIL).param("_csrf", token.group(1)));
        assertThat(post.getResponse().getStatus()).isEqualTo(403);
        assertThat(this.mailer.sentTo(EMAIL)).isFalse();
    }
}
