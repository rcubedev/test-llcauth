package org.limelightcreatives.auth.oauth;

import org.junit.jupiter.api.Test;
import org.limelightcreatives.auth.DatabaseTest;
import org.limelightcreatives.auth.web.Visitor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.limelightcreatives.auth.oauth.App.json;
import static org.limelightcreatives.auth.web.Visitor.location;

@AutoConfigureMockMvc
class SignOutOfAppsTest extends DatabaseTest {

    @Autowired
    MockMvc mvc;

    private static MvcResult open(Visitor visitor, String url) throws Exception {
        return visitor.send(MockMvcRequestBuilders.get(URI.create(url)).accept(MediaType.TEXT_HTML));
    }

    private String signedInToTheApp(Visitor visitor, App app) throws Exception {
        visitor.signIn("sam@example.com", this.mailer);
        visitor.post("/login/name", "firstName", "Sam", "lastName", "Lee");
        return json(app.token(app.code(open(visitor, app.authorizeUrl()))), "$.id_token");
    }

    private static String logoutUrl(String idToken, String postLogout) {
        return UriComponentsBuilder.fromUriString(App.ISSUER + "/connect/logout")
                .queryParam("id_token_hint", idToken)
                .queryParam("post_logout_redirect_uri", postLogout)
                .queryParam("state", "bye")
                .encode().toUriString();
    }

    @Test
    void signingOutOfAnAppSignsYouOutHereAndGoesBack() throws Exception {
        Visitor visitor = new Visitor(this.mvc);
        App app = App.confidential(this.mvc);
        String idToken = signedInToTheApp(visitor, app);

        MvcResult result = open(visitor, logoutUrl(idToken, "https://app.example/"));
        assertThat(location(result)).isEqualTo("https://app.example/?state=bye");
        assertThat(visitor.signedIn()).isFalse();
    }

    @Test
    void signingOutHereStopsAppsRefreshing() throws Exception {
        Visitor visitor = new Visitor(this.mvc);
        App app = App.confidential(this.mvc);
        visitor.signIn("sam@example.com", this.mailer);
        visitor.post("/login/name", "firstName", "Sam", "lastName", "Lee");
        MvcResult tokens = app.token(app.code(open(visitor, app.authorizeUrl())));

        visitor.post("/logout");

        assertThat(app.refresh(json(tokens, "$.refresh_token")).getResponse().getStatus()).isEqualTo(400);
        assertThat(app.userInfo(json(tokens, "$.access_token")).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void signingOutOfOneAppStopsTheOthersRefreshing() throws Exception {
        Visitor visitor = new Visitor(this.mvc);
        App app = App.confidential(this.mvc);
        String idToken = signedInToTheApp(visitor, app);
        App other = App.confidential(this.mvc);
        MvcResult otherTokens = other.token(other.code(open(visitor, other.authorizeUrl())));

        open(visitor, logoutUrl(idToken, "https://app.example/"));

        assertThat(other.refresh(json(otherTokens, "$.refresh_token")).getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void someoneElsesSignInsAreLeftAlone() throws Exception {
        Visitor sam = new Visitor(this.mvc);
        App app = App.confidential(this.mvc);
        MvcResult samsTokens = app.token(app.code(open(sam, firstSignIn(sam, "sam@example.com", app))));

        Visitor alex = new Visitor(this.mvc);
        alex.signIn("alex@example.com", this.mailer);
        alex.post("/logout");

        assertThat(app.refresh(json(samsTokens, "$.refresh_token")).getResponse().getStatus()).isEqualTo(200);
    }

    private String firstSignIn(Visitor visitor, String email, App app) throws Exception {
        visitor.signIn(email, this.mailer);
        visitor.post("/login/name", "firstName", "Sam", "lastName", "Lee");
        return app.authorizeUrl();
    }

    @Test
    void onlyToTheAppsOwnAddresses() throws Exception {
        Visitor visitor = new Visitor(this.mvc);
        String idToken = signedInToTheApp(visitor, App.confidential(this.mvc));

        MvcResult result = open(visitor, logoutUrl(idToken, "https://evil.example/"));
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(location(result)).isNull();
        assertThat(visitor.signedIn()).isTrue();
    }
}
