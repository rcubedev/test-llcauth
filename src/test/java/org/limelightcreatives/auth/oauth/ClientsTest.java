package org.limelightcreatives.auth.oauth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.limelightcreatives.auth.DatabaseTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientsTest extends DatabaseTest {

    @Autowired
    RegisteredClientRepository repository;

    @Autowired
    Clients clients;

    @Autowired
    OAuthProperties properties;

    @AfterEach
    void putTheTestClientsBack() {
        this.clients.register(this.properties);
    }

    @Test
    void confidentialAppsGetASecretAndRefreshTokens() {
        RegisteredClient app = this.repository.findByClientId("test-app");
        assertThat(app).isNotNull();
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
    void publicAppsHaveNoSecretOrRefreshTokens() {
        RegisteredClient spa = this.repository.findByClientId("test-spa");
        assertThat(spa.getClientSecret()).isNull();
        assertThat(spa.getClientAuthenticationMethods()).containsExactly(ClientAuthenticationMethod.NONE);
        assertThat(spa.getAuthorizationGrantTypes()).containsExactly(AuthorizationGrantType.AUTHORIZATION_CODE);
    }

    @Test
    void aClientWithoutItsSecretIsSkipped() {
        assertThat(this.repository.findByClientId("dashboard")).isNull();
    }

    @Test
    void clientsTakenOutOfTheSettingsAreRemoved() {
        Map<String, OAuthProperties.Client> onlyTheApp = Map.of("test-app", this.properties.clients().get("test-app"));
        this.clients.register(new OAuthProperties(null, true, null, null, onlyTheApp));
        assertThat(this.repository.findByClientId("test-app")).isNotNull();
        assertThat(this.repository.findByClientId("test-spa")).isNull();
    }

    @Test
    void shortSecretsAreRefused() {
        Map<String, OAuthProperties.Client> weak = Map.of("weak", new OAuthProperties.Client(null, "hunter2", false, List.of("https://x.example/cb"), null));
        assertThatThrownBy(() -> this.clients.register(new OAuthProperties(null, true, null, null, weak)))
                .hasMessageContaining("at least 32 characters");
    }
}
