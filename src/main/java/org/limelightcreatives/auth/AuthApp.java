package org.limelightcreatives.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

/**
 * Limelight Creatives accounts: email-code sign in, shared across every *.limelightcreatives.org app.
 * Other apps ask {@code GET /api/permission} who the signed-in person is (see PermissionController).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class AuthApp {

    public static void main(String[] args) {
        SpringApplication.run(AuthApp.class, args);
    }

    /** All times are UTC, like the database (Prisma stored UTC in timestamp columns). */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
