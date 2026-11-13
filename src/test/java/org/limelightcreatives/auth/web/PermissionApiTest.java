package org.limelightcreatives.auth.web;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.limelightcreatives.auth.DatabaseTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;

@AutoConfigureMockMvc
class PermissionApiTest extends DatabaseTest {

    @Autowired
    MockMvc mvc;

    private Cookie signIn(boolean withName) throws Exception {
        Visitor visitor = new Visitor(this.mvc);
        visitor.signIn("sam@example.com", this.mailer);
        if (withName) visitor.post("/login/name", "firstName", "Sam", "lastName", "Lee");
        return visitor.session;
    }

    private MvcResult ask(Cookie cookie) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/permission").secure(true);
        if (cookie != null) request.cookie(cookie);
        return this.mvc.perform(request).andReturn();
    }

    @Test
    void signedIn() throws Exception {
        MvcResult result = ask(signIn(true));
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String id = this.db.sql("SELECT \"id\" FROM \"users\"").query(String.class).single();
        assertThat(result.getResponse().getContentAsString()).isEqualTo(
                "{\"authenticated\":true,\"id\":\"" + id + "\",\"email\":\"sam@example.com\",\"name\":\"Sam Lee\","
                        + "\"role\":\"PARTICIPANT\",\"needsProfile\":false}");
        assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
    }

    @Test
    void signedInWithoutANameYet() throws Exception {
        assertThat(ask(signIn(false)).getResponse().getContentAsString()).contains("\"name\":null", "\"needsProfile\":true");
    }

    @Test
    void notSignedIn() throws Exception {
        MvcResult result = ask(null);
        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(result.getResponse().getContentAsString()).isEqualTo("{\"authenticated\":false}");
    }

    @Test
    void madeUpAndOldCookiesAreRejected() throws Exception {
        Cookie real = signIn(true);
        for (String value : new String[]{real.getValue() + "x", "bm90LWEtc2Vzc2lvbg",
                "eyJhbGciOiJkaXIiLCJlbmMiOiJBMjU2Q0JDLUhTNTEyIn0..abc.def.ghi", // old NextAuth cookie
                ""}) {
            assertThat(ask(new Cookie(real.getName(), value)).getResponse().getStatus()).as(value).isEqualTo(401);
        }
    }

    @Test
    void roleChangesShowUpStraightAway() throws Exception {
        Cookie cookie = signIn(true);
        this.db.sql("UPDATE \"users\" SET \"role\" = 'STAFF'").update();
        assertThat(ask(cookie).getResponse().getContentAsString()).contains("\"role\":\"STAFF\"");
    }

    @Test
    void deletedAccountsAreSignedOut() throws Exception {
        Cookie cookie = signIn(true);
        this.db.sql("DELETE FROM \"users\"").update();
        assertThat(ask(cookie).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void ourSitesMayCallItFromTheBrowser() throws Exception {
        MvcResult result = this.mvc.perform(get("/api/permission").secure(true)
                .header("Origin", "https://dashboard.limelightcreatives.org")).andReturn();
        assertThat(result.getResponse().getHeader("Access-Control-Allow-Origin")).isEqualTo("https://dashboard.limelightcreatives.org");
        assertThat(result.getResponse().getHeader("Access-Control-Allow-Credentials")).isEqualTo("true");

        MvcResult preflight = this.mvc.perform(options("/api/permission").secure(true)
                .header("Origin", "https://limelightcreatives.org")
                .header("Access-Control-Request-Method", "GET")).andReturn();
        assertThat(preflight.getResponse().getStatus()).isEqualTo(200);
        assertThat(preflight.getResponse().getHeader("Access-Control-Allow-Origin")).isEqualTo("https://limelightcreatives.org");
        assertThat(preflight.getResponse().getHeader("Access-Control-Allow-Methods")).isEqualTo("GET");
    }

    @Test
    void otherSitesMayNot() throws Exception {
        for (String origin : new String[]{"https://evil.example", "https://evil-limelightcreatives.org",
                "https://limelightcreatives.org.evil.example", "http://dashboard.limelightcreatives.org"}) {
            MvcResult result = this.mvc.perform(get("/api/permission").secure(true).header("Origin", origin)).andReturn();
            assertThat(result.getResponse().getHeader("Access-Control-Allow-Origin")).as(origin).isNull();
        }
    }
}
