package org.limelightcreatives.auth;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

// base for tests that need the real DB
@SpringBootTest
@ActiveProfiles("test")
public abstract class DatabaseTest {

    static final String TABLES = """
            "users", "verification_codes", "rate_limit_events", "invite_links", "invite_redemptions"
            """;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TestPostgres::jdbcUrl);
    }

    @Autowired
    protected JdbcClient db;

    @BeforeEach
    void emptyTables() {
        this.db.sql("TRUNCATE " + TABLES.strip() + " CASCADE").update();
    }
}
