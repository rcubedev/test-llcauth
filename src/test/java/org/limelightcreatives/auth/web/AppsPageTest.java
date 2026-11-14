package org.limelightcreatives.auth.web;

import org.junit.jupiter.api.Test;
import org.limelightcreatives.auth.DatabaseTest;
import org.limelightcreatives.auth.oauth.Apps;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.limelightcreatives.auth.web.Visitor.body;
import static org.limelightcreatives.auth.web.Visitor.location;

@AutoConfigureMockMvc
class AppsPageTest extends DatabaseTest {

    @Autowired
    MockMvc mvc;

    private Visitor signedIn(String role) throws Exception {
        Visitor visitor = new Visitor(this.mvc);
        visitor.signIn("sam@example.com", this.mailer);
        this.db.sql("UPDATE \"users\" SET \"firstName\" = 'Sam', \"lastName\" = 'Lee', \"role\" = ?::\"Role\"")
                .param(role).update();
        return visitor;
    }

    @Test
    void onlyStaffCanSeeApps() throws Exception {
        assertThat(location(new Visitor(this.mvc).get("/apps"))).endsWith("/login?resume");
        Visitor participant = signedIn("PARTICIPANT");
        assertThat(participant.get("/apps").getResponse().getStatus()).isEqualTo(403);
        assertThat(participant.post("/apps", "name", "Sneaky", "redirectUrls", "https://evil.example/cb",
                "idTokenAlgorithm", "RS256").getResponse().getStatus()).isEqualTo(403);
        assertThat(this.apps.list()).hasSize(2);
        assertThat(body(participant.get("/account"))).doesNotContain("Manage apps");
    }

    @Test
    void staffSeeEveryApp() throws Exception {
        Visitor staff = signedIn("STAFF");
        assertThat(body(staff.get("/account"))).contains("Manage apps");
        assertThat(body(staff.get("/apps"))).contains("Test app", "test-app", "Test SPA", "test-spa");
    }

    @Test
    void addingAnAppShowsItsSecretOnce() throws Exception {
        Visitor staff = signedIn("ADMIN");
        MvcResult created = staff.post("/apps", "name", "Dashboard", "publicClient", "false",
                "redirectUrls", "https://dash.example/login/oauth2/code/limelight\r\n\r\nhttps://dash.example/other\n",
                "signOutUrls", "https://dash.example/", "idTokenAlgorithm", "ES256");
        Apps.App app = this.apps.list().stream().filter(a -> a.settings().name().equals("Dashboard")).findFirst().orElseThrow();
        assertThat(location(created)).isEqualTo("/apps/" + app.id());
        assertThat(app.settings().redirectUrls())
                .containsExactly("https://dash.example/login/oauth2/code/limelight", "https://dash.example/other");
        assertThat(app.settings().idTokenAlgorithm()).isEqualTo(SignatureAlgorithm.ES256);

        String page = body(staff.follow(created));
        assertThat(page).contains("Copy the client secret now", app.clientId());
        assertThat(body(staff.get("/apps/" + app.id()))).doesNotContain("Copy the client secret now");
    }

    @Test
    void mistakesAreExplainedAndKept() throws Exception {
        Visitor staff = signedIn("STAFF");
        MvcResult result = staff.post("/apps", "name", "Broken", "publicClient", "false",
                "redirectUrls", "http://broken.example/cb", "idTokenAlgorithm", "RS256");
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(result)).contains("must be a full https:// URL", "Broken", "http://broken.example/cb");
        assertThat(this.apps.list()).hasSize(2);
    }

    @Test
    void editingKeepsTheType() throws Exception {
        Visitor staff = signedIn("STAFF");
        MvcResult saved = staff.post("/apps/test-spa", "name", "Renamed", "publicClient", "false",
                "redirectUrls", "https://spa.example/callback", "idTokenAlgorithm", "RS256");
        assertThat(location(saved)).isEqualTo("/apps/test-spa");
        Apps.Settings settings = this.apps.find("test-spa").orElseThrow().settings();
        assertThat(settings.name()).isEqualTo("Renamed");
        assertThat(settings.publicClient()).isTrue();
        assertThat(body(staff.follow(saved))).contains("Saved.");
    }

    @Test
    void staffCanReplaceASecretAndDeleteApps() throws Exception {
        Visitor staff = signedIn("STAFF");
        assertThat(body(staff.follow(staff.post("/apps/test-app/secret")))).contains("Copy the client secret now");
        assertThat(staff.post("/apps/test-spa/secret").getResponse().getStatus()).isEqualTo(404);

        assertThat(location(staff.post("/apps/test-app/delete"))).isEqualTo("/apps");
        assertThat(this.apps.find("test-app")).isEmpty();
        assertThat(staff.get("/apps/test-app").getResponse().getStatus()).isEqualTo(404);
    }
}
