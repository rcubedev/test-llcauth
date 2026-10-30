package org.limelightcreatives.auth;

import java.nio.file.Path;

/**
 * Runs the app locally with its own PostgreSQL: {@code ./gradlew bootTestRun}, then open
 * http://localhost:8080. Login codes are printed to the console. The database keeps its data in
 * build/dev-database between runs ({@code ./gradlew clean} empties it).
 */
public class LocalAuthApp {

    public static void main(String[] args) {
        String url = TestPostgres.persistent(Path.of("build/dev-database"), 54329);
        System.setProperty("spring.datasource.url", url);
        System.setProperty("spring.profiles.active", "dev");
        AuthApp.main(args);
    }
}
