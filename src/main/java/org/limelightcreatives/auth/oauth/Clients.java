package org.limelightcreatives.auth.oauth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class Clients implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(Clients.class);
    static final int MIN_SECRET_LENGTH = 32;

    private final RegisteredClientRepository repository;
    private final JdbcClient db;
    private final OAuthProperties properties;
    private final PasswordEncoder secrets = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    public Clients(RegisteredClientRepository repository, JdbcClient db, OAuthProperties properties) {
        this.repository = repository;
        this.db = db;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        register(this.properties);
    }

    void register(OAuthProperties properties) {
        List<String> registered = new ArrayList<>();
        for (Map.Entry<String, OAuthProperties.Client> entry : properties.clients().entrySet()) {
            String id = entry.getKey();
            OAuthProperties.Client client = entry.getValue();
            if (!client.publicClient() && (client.secret() == null || client.secret().isBlank())) {
                log.warn("OAuth client '{}' has no secret so it isn't registered", id);
                continue;
            }
            if (!client.publicClient() && client.secret().length() < MIN_SECRET_LENGTH) {
                throw new IllegalStateException("The secret of OAuth client '" + id + "' must be at least "
                        + MIN_SECRET_LENGTH + " characters");
            }
            this.repository.save(toRegisteredClient(id, client, properties));
            registered.add(id);
        }

        if (registered.isEmpty()) {
            this.db.sql("DELETE FROM oauth2_registered_client").update();
        } else {
            this.db.sql("DELETE FROM oauth2_registered_client WHERE id NOT IN (:ids)").param("ids", registered).update();
        }
        log.info("OAuth clients: {}", registered.isEmpty() ? "none" : String.join(", ", registered));
    }

    private RegisteredClient toRegisteredClient(String id, OAuthProperties.Client client, OAuthProperties properties) {
        RegisteredClient.Builder builder = RegisteredClient.withId(id)
                .clientId(id)
                .clientName(client.name() == null || client.name().isBlank() ? id : client.name())
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUris(uris -> uris.addAll(client.redirectUrls()))
                .postLogoutRedirectUris(uris -> uris.addAll(client.postLogoutRedirectUrls()))
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .scope(OidcScopes.EMAIL)
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(true)
                        .requireAuthorizationConsent(false)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(properties.accessTokenLifetime())
                        .refreshTokenTimeToLive(properties.refreshTokenLifetime())
                        .reuseRefreshTokens(false)
                        .idTokenSignatureAlgorithm(SignatureAlgorithm.ES256)
                        .build());

        if (client.publicClient()) {
            builder.clientAuthenticationMethod(ClientAuthenticationMethod.NONE);
        } else {
            builder.clientSecret(this.secrets.encode(client.secret()))
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                    .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN);
        }
        return builder.build();
    }
}
