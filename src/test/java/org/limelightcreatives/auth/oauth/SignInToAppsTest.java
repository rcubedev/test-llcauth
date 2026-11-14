package org.limelightcreatives.auth.oauth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.Test;
import org.limelightcreatives.auth.DatabaseTest;
import org.limelightcreatives.auth.web.Visitor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.limelightcreatives.auth.oauth.App.json;
import static org.limelightcreatives.auth.web.Visitor.body;
import static org.limelightcreatives.auth.web.Visitor.location;

@AutoConfigureMockMvc
class SignInToAppsTest extends DatabaseTest {

    private static final String EMAIL = "sam@example.com";

    @Autowired
    MockMvc mvc;

    private static MvcResult open(Visitor visitor, String url) throws Exception {
        return visitor.send(MockMvcRequestBuilders.get(URI.create(url)).accept(MediaType.TEXT_HTML));
    }

    private String userId() {
        return this.db.sql("SELECT \"id\" FROM \"users\" WHERE \"email\" = ?").param(EMAIL).query(String.class).single();
    }

    private String signInThroughTheApp(Visitor visitor, App app) throws Exception {
        MvcResult toLogin = open(visitor, app.authorizeUrl());
        assertThat(location(toLogin)).endsWith("/login?resume");
        assertThat(body(visitor.follow(toLogin))).contains("Sign in");

        MvcResult toName = visitor.follow(visitor.signIn(EMAIL, this.mailer));
        assertThat(location(toName)).isEqualTo("/login/name");
        MvcResult backToApp = visitor.follow(visitor.post("/login/name", "firstName", "Sam", "lastName", "Lee"));
        assertThat(location(backToApp)).startsWith(App.ISSUER + "/oauth2/authorize?");
        return app.code(open(visitor, location(backToApp)));
    }

    @Test
    void anAppGetsTokensSayingWhoSignedIn() throws Exception {
        App app = App.confidential(this.mvc);
        String code = signInThroughTheApp(new Visitor(this.mvc), app);

        MvcResult tokens = app.token(code);
        assertThat(tokens.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(tokens, "$.token_type")).isEqualTo("Bearer");

        assertThat(App.algorithm(json(tokens, "$.id_token"))).isEqualTo(JWSAlgorithm.RS256);
        assertThat(App.algorithm(json(tokens, "$.access_token"))).isEqualTo(JWSAlgorithm.ES256);
        JWTClaimsSet id = app.verified(json(tokens, "$.id_token"));
        assertThat(id.getIssuer()).isEqualTo(App.ISSUER);
        assertThat(id.getSubject()).isEqualTo(userId());
        assertThat(id.getAudience()).containsExactly("test-app");
        assertThat(id.getStringClaim("nonce")).isEqualTo(app.nonce);
        assertThat(id.getDateClaim("auth_time")).isNotNull();
        assertThat(id.getStringClaim("email")).isEqualTo(EMAIL);
        assertThat(id.getBooleanClaim("email_verified")).isTrue();
        assertThat(id.getStringClaim("name")).isEqualTo("Sam Lee");
        assertThat(id.getStringClaim("given_name")).isEqualTo("Sam");
        assertThat(id.getStringClaim("family_name")).isEqualTo("Lee");
        assertThat(id.getStringClaim("role")).isEqualTo("PARTICIPANT");
        assertThat(id.getBooleanClaim("needs_profile")).isFalse();

        JWTClaimsSet access = app.verified(json(tokens, "$.access_token"));
        assertThat(access.getSubject()).isEqualTo(userId());
        assertThat(access.getStringClaim("role")).isEqualTo("PARTICIPANT");
        assertThat(access.getExpirationTime().getTime() - access.getIssueTime().getTime()).isEqualTo(10 * 60 * 1000);
    }

    @Test
    void userInfoIsFreshFromTheDatabase() throws Exception {
        App app = App.confidential(this.mvc);
        MvcResult tokens = app.token(signInThroughTheApp(new Visitor(this.mvc), app));
        this.db.sql("UPDATE \"users\" SET \"role\" = 'STAFF'").update();

        MvcResult info = app.userInfo(json(tokens, "$.access_token"));
        assertThat(info.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(info, "$.sub")).isEqualTo(userId());
        assertThat(json(info, "$.email")).isEqualTo(EMAIL);
        assertThat(json(info, "$.name")).isEqualTo("Sam Lee");
        assertThat(json(info, "$.role")).isEqualTo("STAFF");
    }

    @Test
    void refreshingGivesNewTokensWithTheCurrentRole() throws Exception {
        App app = App.confidential(this.mvc);
        MvcResult tokens = app.token(signInThroughTheApp(new Visitor(this.mvc), app));
        String refreshToken = json(tokens, "$.refresh_token");
        this.db.sql("UPDATE \"users\" SET \"role\" = 'STAFF'").update();

        MvcResult refreshed = app.refresh(refreshToken);
        assertThat(refreshed.getResponse().getStatus()).isEqualTo(200);
        assertThat(app.verified(json(refreshed, "$.access_token")).getStringClaim("role")).isEqualTo("STAFF");
        assertThat(json(refreshed, "$.refresh_token")).as("a new refresh token each time").isNotEqualTo(refreshToken);

        assertThat(app.refresh(refreshToken).getResponse().getStatus()).as("the old one is used up").isEqualTo(400);
    }

    @Test
    void someoneAlreadySignedInGoesStraightBackToTheApp() throws Exception {
        Visitor visitor = new Visitor(this.mvc);
        signInThroughTheApp(visitor, App.confidential(this.mvc));

        App app = App.confidential(this.mvc);
        assertThat(app.token(app.code(open(visitor, app.authorizeUrl()))).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void someoneSignedInWithoutANameIsAskedForItFirst() throws Exception {
        Visitor visitor = new Visitor(this.mvc);
        visitor.signIn(EMAIL, this.mailer);

        App app = App.confidential(this.mvc);
        assertThat(location(open(visitor, app.authorizeUrl()))).isEqualTo("/login/name");
        MvcResult back = visitor.follow(visitor.post("/login/name", "firstName", "Sam", "lastName", "Lee"));
        assertThat(app.code(open(visitor, location(back)))).isNotBlank();
    }

    @Test
    void deletedAccountsGetNoMoreTokens() throws Exception {
        App app = App.confidential(this.mvc);
        MvcResult tokens = app.token(signInThroughTheApp(new Visitor(this.mvc), app));
        this.db.sql("DELETE FROM \"users\"").update();

        assertThat(app.refresh(json(tokens, "$.refresh_token")).getResponse().getStatus()).isEqualTo(400);
        assertThat(app.userInfo(json(tokens, "$.access_token")).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void aDeletedAccountStillSignedInHereHasToSignInAgain() throws Exception {
        Visitor visitor = new Visitor(this.mvc);
        signInThroughTheApp(visitor, App.confidential(this.mvc));
        this.db.sql("DELETE FROM \"users\"").update();

        App app = App.confidential(this.mvc);
        assertThat(location(open(visitor, app.authorizeUrl()))).isEqualTo("/login?resume");
        assertThat(visitor.signedIn()).isFalse();
    }

    @Test
    void anAppSignInThatWasGivenUpOnIsntPickedUpLater() throws Exception {
        Visitor visitor = new Visitor(this.mvc);
        open(visitor, App.confidential(this.mvc).authorizeUrl());

        visitor.get("/login");
        MvcResult continued = visitor.follow(visitor.signIn(EMAIL, this.mailer));
        MvcResult after = visitor.follow(visitor.post("/login/name", "firstName", "Sam", "lastName", "Lee"));
        assertThat(continued.getResponse().getRedirectedUrl()).isEqualTo("/login/name");
        assertThat(location(after)).isEqualTo("https://auth.limelightcreatives.org/account");
    }

    @Test
    void aMistypedEmailStillGoesBackToTheApp() throws Exception {
        Visitor visitor = new Visitor(this.mvc);
        App app = App.confidential(this.mvc);
        visitor.follow(open(visitor, app.authorizeUrl()));
        visitor.follow(visitor.post("/login", "email", "not-an-email"));

        visitor.follow(visitor.signIn(EMAIL, this.mailer));
        MvcResult back = visitor.follow(visitor.post("/login/name", "firstName", "Sam", "lastName", "Lee"));
        assertThat(app.code(open(visitor, location(back)))).isNotBlank();
    }

    @Test
    void browserAppsSignInWithPkceAlone() throws Exception {
        App app = App.browser(this.mvc);
        MvcResult tokens = app.token(signInThroughTheApp(new Visitor(this.mvc), app));
        assertThat(tokens.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(tokens)).contains("id_token").doesNotContain("refresh_token");
        assertThat(App.algorithm(json(tokens, "$.id_token"))).isEqualTo(JWSAlgorithm.ES256);
        assertThat(app.verified(json(tokens, "$.id_token")).getAudience()).containsExactly("test-spa");
    }

    @Test
    void aCodeOnlyWorksWithItsVerifierAndOnlyOnce() throws Exception {
        App app = App.confidential(this.mvc);
        String code = signInThroughTheApp(new Visitor(this.mvc), app);

        MvcResult wrongVerifier = app.tokenRequest("grant_type", "authorization_code", "code", code,
                "redirect_uri", app.redirectUri, "code_verifier", "x".repeat(43));
        assertThat(wrongVerifier.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void codesAreOneUse() throws Exception {
        App app = App.confidential(this.mvc);
        String code = signInThroughTheApp(new Visitor(this.mvc), app);
        assertThat(app.token(code).getResponse().getStatus()).isEqualTo(200);
        assertThat(app.token(code).getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void theWrongSecretGetsNothing() throws Exception {
        App app = App.confidential(this.mvc);
        String code = signInThroughTheApp(new Visitor(this.mvc), app);
        App impostor = new App(this.mvc, "test-app", "not-the-secret-not-the-secret-12345", app.redirectUri);
        assertThat(impostor.tokenRequest("grant_type", "authorization_code", "code", code,
                "redirect_uri", app.redirectUri, "code_verifier", app.verifier).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void anUnregisteredRedirectIsNeverFollowed() throws Exception {
        Visitor visitor = new Visitor(this.mvc);
        signInThroughTheApp(visitor, App.confidential(this.mvc));

        App app = App.confidential(this.mvc);
        MvcResult result = open(visitor, app.authorizeUrl(Map.of("redirect_uri", "https://evil.example/callback")));
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(location(result)).isNull();
    }

    @Test
    void pkceIsRequired() throws Exception {
        Visitor visitor = new Visitor(this.mvc);
        signInThroughTheApp(visitor, App.confidential(this.mvc));

        App app = App.confidential(this.mvc);
        Map<String, String> params = new HashMap<>();
        params.put("code_challenge", null);
        params.put("code_challenge_method", null);
        MvcResult result = open(visitor, app.authorizeUrl(params));
        assertThat(location(result)).startsWith(app.redirectUri + "?").contains("error=invalid_request").doesNotContain("code=");
    }

    @Test
    void unknownAppsAreRefused() throws Exception {
        Visitor visitor = new Visitor(mvc);
        App app = new App(mvc, "nobody", null, "https://app.example/callback");
        assertThat(open(visitor, app.authorizeUrl()).getResponse().getStatus()).isEqualTo(400);
    }
}
