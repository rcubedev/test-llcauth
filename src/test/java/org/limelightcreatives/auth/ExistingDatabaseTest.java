package org.limelightcreatives.auth;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExistingDatabaseTest {

    private static final String SCHEMA = "legacy_prisma";
    private static ConfigurableApplicationContext app;

    @BeforeAll
    static void startOnTheOldDatabase() throws Exception {
        try (Connection connection = DriverManager.getConnection(TestPostgres.jdbcUrl());
             Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
            statement.execute("CREATE SCHEMA " + SCHEMA);
            statement.execute("SET search_path TO " + SCHEMA);
            statement.execute(resource("/prisma-migration.sql"));
            statement.execute("""
                    INSERT INTO "users" ("id", "email", "firstName", "lastName", "role")
                    VALUES ('cm-existing-staff', 'ontik@example.com', 'Ontik', 'Reza', 'STAFF')
                    """);
        }

        String base = TestPostgres.jdbcUrl();
        String url = base + (base.contains("?") ? "&" : "?") + "currentSchema=" + SCHEMA;
        app = new SpringApplicationBuilder(AuthApp.class)
                .web(WebApplicationType.NONE)
                .profiles("test")
                .run("--spring.datasource.url=" + url, "--spring.flyway.schemas=" + SCHEMA);
    }

    @AfterAll
    static void stop() {
        if (app != null) app.close();
    }

    @Test
    void theOldTablesAreRecordedAsVersionOne() {
        List<String> history = db().sql("SELECT \"version\", \"type\" FROM flyway_schema_history ORDER BY installed_rank")
                .query((rs, role) -> rs.getString(1) + " " + rs.getString(2)).list();
        assertThat(history.getFirst()).isEqualTo("1 BASELINE");
        assertThat(history.subList(1, history.size())).allSatisfy(migration -> assertThat(migration).endsWith(" SQL"));
    }

    @Test
    void everyoneIsStillThere() {
        String role = db().sql("SELECT \"role\"::text FROM \"users\" WHERE \"email\" = 'ontik@example.com'")
                .query(String.class).single();
        assertThat(role).isEqualTo("STAFF");
    }

    private static JdbcClient db() {
        return app.getBean(JdbcClient.class);
    }

    private static String resource(String name) throws IOException {
        try (InputStream in = ExistingDatabaseTest.class.getResourceAsStream(name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
