package org.limelightcreatives.auth.oauth;

import org.junit.jupiter.api.Test;
import org.limelightcreatives.auth.DatabaseTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppsTest extends DatabaseTest {

    @Autowired
    RegisteredClientRepository repository;

    private static Apps.Settings server(String name) {
        return new Apps.Settings(name, false, List.of("https://new.example/callback"), List.of("https://new.example/"),
                SignatureAlgorithm.RS256);
    }

    @Test
    void serverAppsGetASecretAndRefreshTokens() {
        RegisteredClient app = this.repository.findByClientId("test-app");
        assertThat(app.getClientName()).isEqualTo("Test app");
        assertThat(app.getClientSecret()).startsWith("{bcrypt}").doesNotContain("test-app-secret");
        assertThat(app.getClientAuthenticationMethods()).contains(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
        assertThat(app.getAuthorizationGrantTypes())
                .containsExactlyInAnyOrder(AuthorizationGrantType.AUTHORIZATION_CODE, AuthorizationGrantType.REFRESH_TOKEN);
        assertThat(app.getRedirectUris()).containsExactly("https://app.example/callback");
        assertThat(app.getScopes()).containsExactlyInAnyOrder("openid", "profile", "email");
        assertThat(app.getClientSettings().isRequireProofKey()).isTrue();
        assertThat(app.getClientSettings().isRequireAuthorizationConsent()).isFalse();
        assertThat(app.getTokenSettings().getAccessTokenTimeToLive()).hasMinutes(10);
        assertThat(app.getTokenSettings().isReuseRefreshTokens()).isFalse();
    }

    @Test
    void browserAppsHaveNoSecretOrRefreshTokens() {
        RegisteredClient spa = this.repository.findByClientId("test-spa");
        assertThat(spa.getClientSecret()).isNull();
        assertThat(spa.getClientAuthenticationMethods()).containsExactly(ClientAuthenticationMethod.NONE);
        assertThat(spa.getAuthorizationGrantTypes()).containsExactly(AuthorizationGrantType.AUTHORIZATION_CODE);
    }

    @Test
    void idTokensUseTheAlgorithmTheAppChose() {
        assertThat(this.repository.findByClientId("test-app").getTokenSettings().getIdTokenSignatureAlgorithm())
                .isEqualTo(SignatureAlgorithm.RS256);
        assertThat(this.repository.findByClientId("test-spa").getTokenSettings().getIdTokenSignatureAlgorithm())
                .isEqualTo(SignatureAlgorithm.ES256);
    }

    @Test
    void newAppsGetARandomIdAndASecretThatWorks() {
        Apps.Created created = this.apps.create(server("New"));
        assertThat(created.app().clientId()).startsWith("app_").hasSizeGreaterThan(20);
        assertThat(created.secret()).hasSizeGreaterThanOrEqualTo(32);
        RegisteredClient stored = this.repository.findByClientId(created.app().clientId());
        assertThat(PasswordEncoderFactories.createDelegatingPasswordEncoder().matches(created.secret(), stored.getClientSecret()))
                .isTrue();
        assertThat(this.apps.create(server("Other")).app().clientId()).isNotEqualTo(created.app().clientId());
    }

    @Test
    void appsAreListedByName() {
        this.apps.create(server("Alpha"));
        assertThat(this.apps.list()).extracting(app -> app.settings().name()).containsExactly("Alpha", "Test app", "Test SPA");
    }

    @Test
    void updatingKeepsTheIdAndSecret() {
        String secret = this.repository.findById("test-app").getClientSecret();
        this.apps.update("test-app", new Apps.Settings("Renamed", false, List.of("https://other.example/cb"), List.of(),
                SignatureAlgorithm.ES256));
        RegisteredClient app = this.repository.findById("test-app");
        assertThat(app.getClientName()).isEqualTo("Renamed");
        assertThat(app.getClientId()).isEqualTo("test-app");
        assertThat(app.getClientSecret()).isEqualTo(secret);
        assertThat(app.getRedirectUris()).containsExactly("https://other.example/cb");
        assertThat(app.getPostLogoutRedirectUris()).isEmpty();
        assertThat(app.getTokenSettings().getIdTokenSignatureAlgorithm()).isEqualTo(SignatureAlgorithm.ES256);
    }

    @Test
    void anAppCantSwitchType() {
        Apps.Settings asBrowser = new Apps.Settings("Test app", true, List.of("https://app.example/callback"), List.of(),
                SignatureAlgorithm.RS256);
        assertThatThrownBy(() -> this.apps.update("test-app", asBrowser)).isInstanceOf(Apps.InvalidApp.class);
    }

    @Test
    void aNewSecretReplacesTheOldOne() {
        String before = this.repository.findById("test-app").getClientSecret();
        String secret = this.apps.newSecret("test-app");
        String after = this.repository.findById("test-app").getClientSecret();
        assertThat(after).isNotEqualTo(before);
        assertThat(PasswordEncoderFactories.createDelegatingPasswordEncoder().matches(secret, after)).isTrue();
        assertThatThrownBy(() -> this.apps.newSecret("test-spa")).isInstanceOf(Apps.InvalidApp.class);
    }

    @Test
    void deletingAnAppSignsEveryoneOutOfIt() {
        this.db.sql("""
                INSERT INTO oauth2_authorization (id, registered_client_id, principal_name, authorization_grant_type)
                VALUES ('a', 'test-app', 'someone', 'authorization_code'), ('b', 'test-spa', 'someone', 'authorization_code')
                """).update();
        this.apps.delete("test-app");
        assertThat(this.repository.findById("test-app")).isNull();
        assertThat(this.db.sql("SELECT registered_client_id FROM oauth2_authorization").query(String.class).list())
                .containsExactly("test-spa");
    }

    @Test
    void badSettingsAreRefused() {
        List<Apps.Settings> bad = List.of(
                new Apps.Settings(" ", false, List.of("https://a.example/cb"), List.of(), SignatureAlgorithm.RS256),
                new Apps.Settings("A", false, List.of(), List.of(), SignatureAlgorithm.RS256),
                new Apps.Settings("A", false, List.of("http://a.example/cb"), List.of(), SignatureAlgorithm.RS256),
                new Apps.Settings("A", false, List.of("https://a.example/cb#x"), List.of(), SignatureAlgorithm.RS256),
                new Apps.Settings("A", false, List.of("https://user@a.example/cb"), List.of(), SignatureAlgorithm.RS256),
                new Apps.Settings("A", false, List.of("/cb"), List.of(), SignatureAlgorithm.RS256),
                new Apps.Settings("A", false, List.of("https://a.example/cb"), List.of("javascript:alert(1)"), SignatureAlgorithm.RS256),
                new Apps.Settings("A", false, List.of("https://a.example/cb"), List.of(), SignatureAlgorithm.PS256),
                new Apps.Settings("A", false, List.of("https://a.example/cb"), List.of(), null));
        for (Apps.Settings settings : bad)
            assertThatThrownBy(() -> this.apps.create(settings)).as(settings.toString()).isInstanceOf(Apps.InvalidApp.class);
    }

    @Test
    void localhostMayUseHttp() {
        Apps.Settings local = new Apps.Settings("Local", false, List.of("http://localhost:3000/cb"), List.of(),
                SignatureAlgorithm.RS256);
        assertThat(this.apps.create(local).app().settings().redirectUrls()).containsExactly("http://localhost:3000/cb");
    }

    @Test
    void shortSecretsAreRefused() {
        assertThatThrownBy(() -> this.apps.register("weak", "hunter2", server("Weak")))
                .hasMessageContaining("at least 32 characters");
    }
}
