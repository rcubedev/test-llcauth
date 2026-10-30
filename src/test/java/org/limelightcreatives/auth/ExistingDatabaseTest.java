package org.limelightcreatives.auth;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.limelightcreatives.auth.login.SignIn;
import org.limelightcreatives.auth.user.Role;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Switching over: the app starts on the database the old Prisma app created, leaves its tables
 * alone (Flyway records it as already at version 1), and existing people sign in as themselves.
 */
class ExistingDatabaseTest {

    private static final String SCHEMA = "legacy_prisma";
    private static ConfigurableApplicationContext app;

    private static String url() {
        String base = DatabaseTest.jdbcUrl();
        return base + (base.contains("?") ? "&" : "?") + "currentSchema=" + SCHEMA;
    }

    @BeforeAll
    static void createTheOldDatabase() throws Exception {
        String sql = new String(ExistingDatabaseTest.class.getResourceAsStream("/prisma-migration.sql").readAllBytes(),
                StandardCharsets.UTF_8);
        try (var connection = DriverManager.getConnection(DatabaseTest.jdbcUrl());
             var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
            statement.execute("CREATE SCHEMA " + SCHEMA);
            statement.execute("SET search_path TO " + SCHEMA);
            statement.execute(sql);
            statement.execute("""
                    INSERT INTO "users" ("id", "email", "firstName", "lastName", "role")
                    VALUES ('cm-existing-staff', 'ontik@example.com', 'Ontik', 'Reza', 'STAFF')
                    """);
        }

        app = new SpringApplicationBuilder(AuthApp.class, RecordingMailer.Config.class)
                .web(WebApplicationType.NONE)
                .profiles("test")
                .run("--spring.datasource.url=" + url(), "--spring.flyway.schemas=" + SCHEMA);
    }

    @AfterAll
    static void stop() {
        if (app != null) app.close();
    }

    @Test
    void theOldTablesAreLeftAsTheyWere() {
        var db = app.getBean(JdbcClient.class);
        var history = db.sql("SELECT \"version\", \"type\" FROM flyway_schema_history ORDER BY installed_rank")
                .query((rs, row) -> rs.getString(1) + " " + rs.getString(2)).list();
        assertThat(history).containsExactly("1 BASELINE");
        assertThat(db.sql("SELECT count(*) FROM \"users\"").query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    void existingPeopleSignInAsThemselves() {
        var signIn = app.getBean(SignIn.class);
        assertThat(signIn.sendCode("Ontik@Example.com", "192.0.2.1")).isEqualTo(SignIn.SendResult.SENT);

        String code = app.getBean(RecordingMailer.class).lastCodeFor("ontik@example.com");
        var user = signIn.verify("ontik@example.com", code).orElseThrow();
        assertThat(user.id()).isEqualTo("cm-existing-staff");
        assertThat(user.role()).isEqualTo(Role.STAFF);
        assertThat(user.name()).isEqualTo("Ontik Reza");
    }
}
