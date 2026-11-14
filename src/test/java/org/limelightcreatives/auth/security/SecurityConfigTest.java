package org.limelightcreatives.auth.security;

import org.junit.jupiter.api.Test;
import org.limelightcreatives.auth.DatabaseTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@AutoConfigureMockMvc
class SecurityConfigTest extends DatabaseTest {

    @Autowired
    MockMvc mvc;

    @Test
    void privatePagesSendYouToSignIn() throws Exception {
        var response = this.mvc.perform(get("/account")).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl()).endsWith("/login?resume");
    }

    @Test
    void healthIsPublic() throws Exception {
        assertThat(this.mvc.perform(get("/actuator/health")).andReturn().getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void formsWithoutACsrfTokenAreRefused() throws Exception {
        assertThat(this.mvc.perform(post("/login").param("email", "sam@example.com")).andReturn().getResponse().getStatus())
                .isEqualTo(403);
        assertThat(this.mailer.sentTo("sam@example.com")).isFalse();
    }
}
