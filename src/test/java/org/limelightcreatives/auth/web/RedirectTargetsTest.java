package org.limelightcreatives.auth.web;

import org.junit.jupiter.api.Test;
import org.limelightcreatives.auth.AuthProperties;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RedirectTargetsTest {

    static AuthProperties properties(boolean secure) {
        return new AuthProperties("x".repeat(32), URI.create("https://auth.limelightcreatives.org"),
                "limelightcreatives.org", ".limelightcreatives.org", secure,
                URI.create("https://dashboard.limelightcreatives.org/"), Duration.ofDays(30),
                new AuthProperties.Mail("", "from@example.com", false));
    }

    private final RedirectTargets targets = new RedirectTargets(properties(true));
    private static final String DEFAULT = "https://dashboard.limelightcreatives.org/";

    @Test
    void ourSitesAreAllowed() {
        assertThat(targets.safe("https://limelightcreatives.org/")).isEqualTo("https://limelightcreatives.org/");
        assertThat(targets.safe("https://dashboard.limelightcreatives.org/staff?x=1"))
                .isEqualTo("https://dashboard.limelightcreatives.org/staff?x=1");
        assertThat(targets.safe("https://a.b.LimelightCreatives.org/")).isEqualTo("https://a.b.LimelightCreatives.org/");
    }

    @Test
    void pathsGoToTheDefaultSite() {
        assertThat(targets.safe("/participant")).isEqualTo("https://dashboard.limelightcreatives.org/participant");
    }

    @Test
    void everythingElseGoesToTheDefault() {
        for (String next : new String[]{null, "", "https://evil.example/", "https://evil-limelightcreatives.org/",
                "https://limelightcreatives.org.evil.example/", "//evil.example/", "http://dashboard.limelightcreatives.org/",
                "https://limelightcreatives.org@evil.example/", "https://user@dashboard.limelightcreatives.org/",
                "javascript:alert(1)", "data:text/html,hi", "https://exa mple/", "ftp://limelightcreatives.org/"}) {
            assertThat(targets.safe(next)).as(String.valueOf(next)).isEqualTo(DEFAULT);
        }
    }

    @Test
    void plainHttpOnlyWhenTestingLocally() {
        var local = new RedirectTargets(properties(false));
        assertThat(local.safe("http://dashboard.limelightcreatives.org/")).isEqualTo("http://dashboard.limelightcreatives.org/");
    }
}
