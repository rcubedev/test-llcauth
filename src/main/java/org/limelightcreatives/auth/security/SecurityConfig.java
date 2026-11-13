package org.limelightcreatives.auth.security;

import org.limelightcreatives.auth.AuthProperties;
import org.limelightcreatives.auth.login.LoginCodes;
import org.limelightcreatives.auth.oauth.SignOutOfApps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;

@Configuration
public class SecurityConfig {

    static final String[] PUBLIC = {
            "/", "/login", "/login/code", "/error", "/actuator/health", "/api/permission",
            "/css/**", "/js/**", "/fonts/**", "/favicon.ico"
    };
    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    AuthenticationManager authenticationManager(EmailCodeAuthenticationProvider emailCodes) {
        return new ProviderManager(emailCodes);
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new DelegatingSecurityContextRepository(
                new RequestAttributeSecurityContextRepository(), new HttpSessionSecurityContextRepository());
    }

    @Bean
    RequestCache requestCache() {
        HttpSessionRequestCache cache = new HttpSessionRequestCache();
        //noinspection DataFlowIssue spring supports null here
        cache.setMatchingRequestParameterName(null);
        return cache;
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository(AuthProperties properties) {
        CookieCsrfTokenRepository tokens = new CookieCsrfTokenRepository();
        tokens.setCookieName(properties.secureCookies() ? "__Host-csrf" : "csrf");
        tokens.setCookiePath("/");
        tokens.setCookieCustomizer(cookie -> cookie.secure(properties.secureCookies()).sameSite("Lax"));
        return tokens;
    }

    @Bean
    @Order(2)
    SecurityFilterChain app(HttpSecurity http, SecurityContextRepository contexts, RequestCache requestCache,
                            CsrfTokenRepository csrfTokens, SignOutOfApps signOutOfApps) {
        http.securityContext(context -> context.securityContextRepository(contexts))
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokens))
                .requestCache(cache -> cache.requestCache(requestCache))
                .authorizeHttpRequests(requests ->
                        requests.requestMatchers(PUBLIC)
                                .permitAll()
                                .anyRequest().authenticated())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptions ->
                        exceptions.authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login")))
                .logout(logout -> logout.addLogoutHandler(signOutOfApps).logoutSuccessUrl("/login"));
        return http.build();
    }
}
