package org.limelightcreatives.auth.web;

import org.limelightcreatives.auth.oauth.Apps;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;

import java.util.Arrays;
import java.util.List;

public record AppForm(String name, boolean publicClient, String redirectUrls, String signOutUrls, String idTokenAlgorithm) {

    static AppForm blank() {
        return new AppForm("", false, "", "", SignatureAlgorithm.RS256.getName());
    }

    static AppForm of(Apps.Settings settings) {
        return new AppForm(settings.name(), settings.publicClient(), String.join("\n", settings.redirectUrls()),
                String.join("\n", settings.signOutUrls()), settings.idTokenAlgorithm().getName());
    }

    AppForm withPublicClient(boolean publicClient) {
        return new AppForm(this.name, publicClient, this.redirectUrls, this.signOutUrls, this.idTokenAlgorithm);
    }

    Apps.Settings settings() {
        SignatureAlgorithm algorithm = SignatureAlgorithm.from(this.idTokenAlgorithm == null ? "" : this.idTokenAlgorithm);
        return new Apps.Settings(this.name, this.publicClient, lines(this.redirectUrls), lines(this.signOutUrls), algorithm);
    }

    private static List<String> lines(String text) {
        if (text == null) return List.of();
        return Arrays.stream(text.split("\\R")).map(String::strip).filter(line -> !line.isEmpty()).distinct().toList();
    }
}
