package org.limelightcreatives.auth.web;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.limelightcreatives.auth.DatabaseTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;

/**
 * {@code /api/permission}, exactly as the dashboard uses it: it forwards the visitor's cookie
 * server-to-server and reads authenticated, id, email, name, role and needsProfile.
 */
@AutoConfigureMockMvc
class PermissionApiTest extends DatabaseTest {

    @Autowired MockMvc mvc;

    private Cookie signIn(String email, boolean withName) throws Exception {
        var visitor = new Visitor(mvc);
        visitor.post("/login/code", "email", email);
        visitor.signIn(email, mailer.lastCodeFor(email));
        if (withName) visitor.post("/login/name", "firstName", "Sam", "lastName", "Lee");
        return visitor.session;
    }

    private MvcResult ask(Cookie cookie) throws Exception {
        var request = get("/api/permission");
        if (cookie != null) request.cookie(cookie);
        return mvc.perform(request).andReturn();
    }

    @Test
    void signedIn() throws Exception {
        var result = ask(signIn("sam@example.com", true));
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String id = db.sql("SELECT \"id\" FROM \"users\"").query(String.class).single();
        assertThat(result.getResponse().getContentAsString()).isEqualTo(
                "{\"authenticated\":true,\"id\":\"" + id + "\",\"email\":\"sam@example.com\",\"name\":\"Sam Lee\","
                        + "\"role\":\"PARTICIPANT\",\"needsProfile\":false}");
        assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
    }

    @Test
    void signedInWithoutANameYet() throws Exception {
        String json = ask(signIn("sam@example.com", false)).getResponse().getContentAsString();
        assertThat(json).contains("\"name\":null", "\"needsProfile\":true");
    }

    @Test
    void notSignedIn() throws Exception {
        var result = ask(null);
        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(result.getResponse().getContentAsString()).isEqualTo("{\"authenticated\":false}");
    }

    @Test
    void forgedOrOldCookiesAreRejected() throws Exception {
        Cookie real = signIn("sam@example.com", true);
        String[] parts = real.getValue().split("\\.");
        String id = db.sql("SELECT \"id\" FROM \"users\"").query(String.class).single();

        for (String value : new String[]{
                "v1." + "someone-else" + "." + parts[2] + "." + parts[3],    // different user
                "v1." + id + "." + (Long.parseLong(parts[2]) + 999) + "." + parts[3], // longer expiry
                real.getValue() + "x",
                "eyJhbGciOiJkaXIiLCJlbmMiOiJBMjU2Q0JDLUhTNTEyIn0..abc.def.ghi", // an old NextAuth cookie
                ""}) {
            assertThat(ask(new Cookie(real.getName(), value)).getResponse().getStatus()).as(value).isEqualTo(401);
        }
    }

    @Test
    void roleChangesShowUpStraightAway() throws Exception {
        Cookie cookie = signIn("sam@example.com", true);
        db.sql("UPDATE \"users\" SET \"role\" = 'STAFF'").update();
        assertThat(ask(cookie).getResponse().getContentAsString()).contains("\"role\":\"STAFF\"");
    }

    @Test
    void deletedAccountsAreSignedOut() throws Exception {
        Cookie cookie = signIn("sam@example.com", true);
        db.sql("DELETE FROM \"users\"").update();
        assertThat(ask(cookie).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void ourSitesMayCallItFromTheBrowser() throws Exception {
        var result = mvc.perform(get("/api/permission").header("Origin", "https://dashboard.limelightcreatives.org")).andReturn();
        assertThat(result.getResponse().getHeader("Access-Control-Allow-Origin")).isEqualTo("https://dashboard.limelightcreatives.org");
        assertThat(result.getResponse().getHeader("Access-Control-Allow-Credentials")).isEqualTo("true");

        var preflight = mvc.perform(options("/api/permission").header("Origin", "https://limelightcreatives.org")).andReturn();
        assertThat(preflight.getResponse().getStatus()).isEqualTo(204);
        assertThat(preflight.getResponse().getHeader("Access-Control-Allow-Origin")).isEqualTo("https://limelightcreatives.org");
        assertThat(preflight.getResponse().getHeader("Access-Control-Allow-Methods")).isEqualTo("GET, OPTIONS");
    }

    @Test
    void otherSitesMayNot() throws Exception {
        for (String origin : new String[]{"https://evil.example", "https://evil-limelightcreatives.org",
                "http://dashboard.limelightcreatives.org", "null"}) {
            var result = mvc.perform(get("/api/permission").header("Origin", origin)).andReturn();
            assertThat(result.getResponse().getHeader("Access-Control-Allow-Origin")).as(origin).isNull();
        }
    }
}
