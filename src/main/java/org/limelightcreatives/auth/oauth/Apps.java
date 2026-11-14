package org.limelightcreatives.auth.oauth;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class Apps {

    private static final int SECRET_BYTES = 32;
    private static final int MAX_NAME_LENGTH = 200;
    private static final int MAX_URLS_LENGTH = 1000;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RegisteredClientRepository repository;
    private final JdbcClient db;
    private final OAuthProperties properties;
    private final PasswordEncoder secrets = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    public Apps(RegisteredClientRepository repository, JdbcClient db, OAuthProperties properties) {
        this.repository = repository;
        this.db = db;
        this.properties = properties;
    }

    /**
     * What staff can set for an app.
     *
     * @param name the name shown to staff
     * @param publicClient whether the app runs in a browser or on a device and so has no secret
     * @param redirectUrls the exact addresses sign-in codes may be sent to
     * @param signOutUrls the exact addresses people may be sent back to after signing out
     * @param idTokenAlgorithm {@code RS256} or {@code ES256}
     */
    public record Settings(String name, boolean publicClient, List<String> redirectUrls, List<String> signOutUrls,
                           SignatureAlgorithm idTokenAlgorithm) {

        public Settings {
            redirectUrls = redirectUrls == null ? List.of() : List.copyOf(redirectUrls);
            signOutUrls = signOutUrls == null ? List.of() : List.copyOf(signOutUrls);
        }
    }

    public record App(String id, String clientId, Settings settings) {
    }

    public record Created(App app, String secret) {
    }

    public static class InvalidApp extends IllegalArgumentException {

        InvalidApp(String message) {
            super(message);
        }
    }

    public List<App> list() {
        return this.db.sql("SELECT id FROM oauth2_registered_client ORDER BY lower(client_name), id")
                .query(String.class).list().stream()
                .map(this.repository::findById)
                .filter(Objects::nonNull)
                .map(Apps::toApp)
                .toList();
    }

    public Optional<App> find(String id) {
        return Optional.ofNullable(this.repository.findById(id)).map(Apps::toApp);
    }

    public Created create(Settings settings) {
        String clientId = "app_" + random(16);
        String secret = settings.publicClient() ? null : random(SECRET_BYTES);
        return new Created(register(clientId, secret, settings), secret);
    }

    public App register(String clientId, String secret, Settings settings) {
        validate(settings);
        if (!settings.publicClient() && (secret == null || secret.length() < 32))
            throw new InvalidApp("A server app's secret must be at least 32 characters");
        return save(clientId, clientId, settings.publicClient() ? null : this.secrets.encode(secret), Instant.now(), settings);
    }

    public App update(String id, Settings settings) {
        RegisteredClient existing = require(id);
        if (isPublic(existing) != settings.publicClient())
            throw new InvalidApp("An app cannot switch between having a secret and not; add a new app instead");
        validate(settings);
        return save(id, existing.getClientId(), existing.getClientSecret(), existing.getClientIdIssuedAt(), settings);
    }

    public String newSecret(String id) {
        RegisteredClient existing = require(id);
        if (isPublic(existing)) throw new InvalidApp("Browser and mobile apps don't have a secret");
        String secret = random(SECRET_BYTES);
        save(id, existing.getClientId(), this.secrets.encode(secret), existing.getClientIdIssuedAt(), toApp(existing).settings());
        return secret;
    }

    @Transactional
    public void delete(String id) {
        this.db.sql("DELETE FROM oauth2_authorization WHERE registered_client_id = ?").param(id).update();
        this.db.sql("DELETE FROM oauth2_authorization_consent WHERE registered_client_id = ?").param(id).update();
        this.db.sql("DELETE FROM oauth2_registered_client WHERE id = ?").param(id).update();
    }

    private RegisteredClient require(String id) {
        RegisteredClient client = this.repository.findById(id);
        if (client == null) throw new InvalidApp("There's no app " + id);
        return client;
    }

    private App save(String id, String clientId, String encodedSecret, Instant issuedAt, Settings settings) {
        RegisteredClient.Builder builder = RegisteredClient.withId(id)
                .clientId(clientId)
                .clientIdIssuedAt(issuedAt)
                .clientName(settings.name().strip())
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUris(uris -> uris.addAll(settings.redirectUrls()))
                .postLogoutRedirectUris(uris -> uris.addAll(settings.signOutUrls()))
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .scope(OidcScopes.EMAIL)
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(true)
                        .requireAuthorizationConsent(false)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(this.properties.accessTokenLifetime())
                        .refreshTokenTimeToLive(this.properties.refreshTokenLifetime())
                        .reuseRefreshTokens(false)
                        .idTokenSignatureAlgorithm(settings.idTokenAlgorithm())
                        .build());

        if (settings.publicClient()) {
            builder.clientAuthenticationMethod(ClientAuthenticationMethod.NONE);
        } else {
            builder.clientSecret(encodedSecret)
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                    .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN);
        }
        RegisteredClient client = builder.build();
        this.repository.save(client);
        return toApp(client);
    }

    private static void validate(Settings settings) {
        if (settings.name() == null || settings.name().isBlank()) throw new InvalidApp("Give the app a name");
        if (settings.name().strip().length() > MAX_NAME_LENGTH)
            throw new InvalidApp("The name can be at most " + MAX_NAME_LENGTH + " characters");
        if (settings.redirectUrls().isEmpty()) throw new InvalidApp("Add at least one redirect URL");
        settings.redirectUrls().forEach(Apps::checkUrl);
        settings.signOutUrls().forEach(Apps::checkUrl);
        if (String.join(",", settings.redirectUrls()).length() > MAX_URLS_LENGTH
                || String.join(",", settings.signOutUrls()).length() > MAX_URLS_LENGTH)
            throw new InvalidApp("The URLs can be at most " + MAX_URLS_LENGTH + " characters altogether");
        if (settings.idTokenAlgorithm() != SignatureAlgorithm.RS256 && settings.idTokenAlgorithm() != SignatureAlgorithm.ES256)
            throw new InvalidApp("ID tokens must be signed with RS256 or ES256");
    }

    private static void checkUrl(String url) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new InvalidApp(url + " isn't a URL");
        }
        boolean local = "localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost());
        boolean allowedScheme = "https".equals(uri.getScheme()) || ("http".equals(uri.getScheme()) && local);
        if (!allowedScheme || uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawFragment() != null
                || url.contains(","))
            throw new InvalidApp(url + " must be a full https:// URL without a #fragment (http:// only for localhost)");
    }

    private static boolean isPublic(RegisteredClient client) {
        return client.getClientAuthenticationMethods().contains(ClientAuthenticationMethod.NONE);
    }

    private static App toApp(RegisteredClient client) {
        return new App(client.getId(), client.getClientId(), new Settings(
                client.getClientName(),
                isPublic(client),
                client.getRedirectUris().stream().sorted().toList(),
                client.getPostLogoutRedirectUris().stream().sorted().toList(),
                client.getTokenSettings().getIdTokenSignatureAlgorithm()));
    }

    private static String random(int bytes) {
        byte[] value = new byte[bytes];
        RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
