package org.limelightcreatives.auth;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class DatabaseUrl implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String url = environment.getProperty("DATABASE_URL");
        if (url == null || !(url.startsWith("postgres://") || url.startsWith("postgresql://"))) return;

        Map<String, Object> properties = toDataSourceProperties(url);
        MapPropertySource source = new MapPropertySource("databaseUrl", properties);
        if (environment.getPropertySources().contains(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)) {
            environment.getPropertySources().addAfter(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, source);
        } else {
            environment.getPropertySources().addLast(source);
        }
    }

    static Map<String, Object> toDataSourceProperties(String url) {
        URI uri = URI.create(url);
        String port = uri.getPort() == -1 ? "" : ":" + uri.getPort();
        String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();

        Map<String, Object> properties = new HashMap<>();
        properties.put("spring.datasource.url", "jdbc:postgresql://" + uri.getHost() + port + uri.getRawPath() + query);
        String userInfo = uri.getRawUserInfo();
        if (userInfo != null) {
            String[] parts = userInfo.split(":", 2);
            properties.put("spring.datasource.username", decode(parts[0]));
            if (parts.length == 2) properties.put("spring.datasource.password", decode(parts[1]));
        }
        return properties;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
    }
}
