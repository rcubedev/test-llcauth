package org.limelightcreatives.auth.oauth;

import org.limelightcreatives.auth.AuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;

@Configuration
@EnableScheduling
public class ExpiredAuthorizations {

    private static final Logger log = LoggerFactory.getLogger(ExpiredAuthorizations.class);

    private final JdbcClient db;
    private final Clock clock;
    private final Duration keep;

    public ExpiredAuthorizations(JdbcClient db, Clock clock, AuthProperties properties) {
        this.db = db;
        this.clock = clock;
        this.keep = properties.sessionLifetime();
    }

    @Scheduled(cron = "0 30 * * * *")
    public int delete() {
        int deleted = this.db.sql("""
                DELETE FROM oauth2_authorization
                WHERE coalesce(authorization_code_expires_at, access_token_expires_at, refresh_token_expires_at) IS NOT NULL
                  AND greatest(authorization_code_expires_at, access_token_expires_at, refresh_token_expires_at) < ?
                """).param(OffsetDateTime.now(this.clock).minus(this.keep)).update();
        if (deleted > 0) log.info("Deleted {} expired app sign-ins", deleted);
        return deleted;
    }
}
