package org.limelightcreatives.auth;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

// base for tests that need the real DB
@SpringBootTest
@ActiveProfiles("test")
@Import(RecordingMailer.Config.class)
public abstract class DatabaseTest {

    static final String TABLES = """
            "users", "verification_codes", "rate_limit_events", "invite_links", "invite_redemptions",
            spring_session, oauth2_authorization, oauth2_authorization_consent
            """;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TestPostgres::jdbcUrl);
    }

    @Autowired
    protected JdbcClient db;

    @Autowired
    protected RecordingMailer mailer;

    @BeforeEach
    void emptyTables() {
        this.db.sql("TRUNCATE " + TABLES.strip() + " CASCADE").update();
        this.mailer.clear();
    }
}
