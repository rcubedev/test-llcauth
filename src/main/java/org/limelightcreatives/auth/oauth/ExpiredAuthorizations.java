package org.limelightcreatives.auth.oauth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
public class ExpiredAuthorizations {

    private static final Logger log = LoggerFactory.getLogger(ExpiredAuthorizations.class);

    private final JdbcClient db;

    public ExpiredAuthorizations(JdbcClient db) {
        this.db = db;
    }

    @Scheduled(cron = "0 30 * * * *")
    public int delete() {
        int deleted = this.db.sql("""
                DELETE FROM oauth2_authorization
                WHERE coalesce(authorization_code_expires_at, access_token_expires_at, refresh_token_expires_at) IS NOT NULL
                  AND greatest(authorization_code_expires_at, access_token_expires_at, refresh_token_expires_at) < now()
                """).update();
        if (deleted > 0) log.info("Deleted {} expired app sign-ins", deleted);
        return deleted;
    }
}
