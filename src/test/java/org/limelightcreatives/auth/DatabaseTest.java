package org.limelightcreatives.auth;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base for tests that need the real database (PostgreSQL, like production).
 *
 * <p>Starts a PostgreSQL of its own (see {@link TestPostgres}), or uses {@code TEST_DATABASE_URL}
 * if it's set (a JDBC URL with user and password, e.g.
 * {@code jdbc:postgresql://localhost/auth_test?user=auth&password=auth}). Every test starts with empty tables.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(RecordingMailer.Config.class)
public abstract class DatabaseTest {

    /** The JDBC URL of the test database, with credentials. */
    public static String jdbcUrl() {
        return TestPostgres.jdbcUrl();
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DatabaseTest::jdbcUrl);
    }

    @Autowired
    protected JdbcClient db;

    @Autowired
    protected RecordingMailer mailer;

    @BeforeEach
    void emptyTables() {
        db.sql("TRUNCATE \"users\", \"verification_codes\", \"rate_limit_events\", \"invite_links\", \"invite_redemptions\" CASCADE")
                .update();
        mailer.clear();
    }
}
