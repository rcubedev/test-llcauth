package org.limelightcreatives.auth.oauth;

import org.limelightcreatives.auth.AuthProperties;
import org.limelightcreatives.auth.user.Users;
import org.limelightcreatives.auth.web.LoginController;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.oidc.web.authentication.OidcLogoutAuthenticationSuccessHandler;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.logout.CompositeLogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

@Configuration
public class AuthorizationServerConfig {

    @Bean
    @Order(1)
    SecurityFilterChain authorizationServer(HttpSecurity http, SecurityContextRepository contexts, RequestCache requestCache,
                                            TokenClaims claims, Users users, AuthorizationServerSettings settings,
                                            SignOutOfApps signOutOfApps) {
        OidcLogoutAuthenticationSuccessHandler signedOut = new OidcLogoutAuthenticationSuccessHandler();
        signedOut.setLogoutHandler(new CompositeLogoutHandler(signOutOfApps, new SecurityContextLogoutHandler()));

        http.oauth2AuthorizationServer(server -> {
            http.securityMatcher(server.getEndpointsMatcher());
            server.oidc(oidc -> oidc
                    .userInfoEndpoint(userInfo -> userInfo.userInfoMapper(claims.userInfo()))
                    .logoutEndpoint(logout -> logout.logoutResponseHandler(signedOut))
                    .providerConfigurationEndpoint(provider -> provider.providerConfigurationCustomizer(config -> config
                            .idTokenSigningAlgorithms(algorithms -> {
                                algorithms.clear();
                                algorithms.add("ES256");
                            })
                            .scopes(scopes -> {
                                scopes.add("profile");
                                scopes.add("email");
                            }))));
        })
                .securityContext(context -> context.securityContextRepository(contexts))
                .requestCache(cache -> cache.requestCache(requestCache))
                .authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
                .addFilterBefore(new NameBeforeApps(users, requestCache, settings.getAuthorizationEndpoint()),
                        AbstractPreAuthenticatedProcessingFilter.class)
                .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint(LoginController.RESUME), new MediaTypeRequestMatcher(MediaType.TEXT_HTML)))
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()));
        return http.build();
    }

    @Bean
    AuthorizationServerSettings authorizationServerSettings(AuthProperties properties) {
        return AuthorizationServerSettings.builder().issuer(properties.baseUrl().toString()).build();
    }

    @Bean
    RegisteredClientRepository registeredClientRepository(JdbcTemplate jdbc) {
        return new JdbcRegisteredClientRepository(jdbc);
    }

    @Bean
    OAuth2AuthorizationService authorizationService(JdbcTemplate jdbc, RegisteredClientRepository clients) {
        return new JdbcOAuth2AuthorizationService(jdbc, clients);
    }

    @Bean
    OAuth2AuthorizationConsentService authorizationConsentService(JdbcTemplate jdbc, RegisteredClientRepository clients) {
        return new JdbcOAuth2AuthorizationConsentService(jdbc, clients);
    }
}
