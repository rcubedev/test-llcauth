package org.limelightcreatives.auth.session;

import org.limelightcreatives.auth.AuthProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.session.jdbc.PostgreSqlJdbcIndexedSessionRepositoryCustomizer;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

import java.time.Duration;

@Configuration
@ImportRuntimeHints(SessionHints.class)
class SessionConfig {

    static final Duration COOKIE_AGE = Duration.ofDays(400);

    @Bean
    CookieSerializer cookieSerializer(AuthProperties properties) {
        DefaultCookieSerializer cookie = new DefaultCookieSerializer();
        cookie.setCookieName(properties.cookieName());
        if (properties.cookieDomain() != null && !properties.cookieDomain().isBlank())
            cookie.setDomainName(properties.cookieDomain().replaceFirst("^\\.", ""));
        cookie.setCookiePath("/");
        cookie.setUseHttpOnlyCookie(true);
        cookie.setUseSecureCookie(properties.secureCookies());
        cookie.setSameSite("Lax");
        cookie.setCookieMaxAge((int) COOKIE_AGE.toSeconds());
        return cookie;
    }

    @Bean
    PostgreSqlJdbcIndexedSessionRepositoryCustomizer postgresSessions() {
        return new PostgreSqlJdbcIndexedSessionRepositoryCustomizer();
    }
}
