package org.limelightcreatives.auth.session;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.limelightcreatives.auth.DatabaseTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@AutoConfigureMockMvc
class SessionCookieTest extends DatabaseTest {

    @Autowired
    MockMvc mvc;

    @Test
    void theCookieIsTheOneTheDashboardReads() throws Exception {
        MockHttpServletResponse response = this.mvc.perform(get("/account").secure(true)).andReturn().getResponse();

        Cookie cookie = response.getCookie("__Secure-authjs.session-token");
        assertThat(cookie).isNotNull();
        assertThat(cookie.getDomain()).isEqualTo("limelightcreatives.org");
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.getAttribute("SameSite")).isEqualTo("Lax");
        assertThat(cookie.getMaxAge()).isEqualTo(30 * 24 * 60 * 60);
    }

    @Test
    void sessionsAreStoredInTheDatabase() throws Exception {
        this.mvc.perform(get("/account").secure(true));
        assertThat(this.db.sql("SELECT count(*) FROM spring_session").query(Integer.class).single()).isEqualTo(1);
        assertThat(this.db.sql("SELECT max_inactive_interval FROM spring_session").query(Integer.class).single())
                .as("not signed in yet: two hours").isEqualTo(2 * 60 * 60);
    }
}
