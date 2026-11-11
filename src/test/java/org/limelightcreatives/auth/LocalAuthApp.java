package org.limelightcreatives.auth;

import java.nio.file.Path;

public class LocalAuthApp {

    public static void main(String[] args) {
        String url = TestPostgres.persistent(Path.of("build/dev-database"), 54329);
        System.setProperty("spring.datasource.url", url);
        System.setProperty("spring.profiles.active", "dev");
        AuthApp.main(args);
    }
}
