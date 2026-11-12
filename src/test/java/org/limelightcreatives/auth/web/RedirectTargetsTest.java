package org.limelightcreatives.auth.web;

import org.junit.jupiter.api.Test;
import org.limelightcreatives.auth.AuthProperties;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RedirectTargetsTest {

    static AuthProperties properties(boolean secure) {
        return new AuthProperties(URI.create("https://auth.limelightcreatives.org"), "limelightcreatives.org",
                ".limelightcreatives.org", secure, URI.create("https://auth.limelightcreatives.org/account"),
                Duration.ofDays(30));
    }

    private final RedirectTargets targets = new RedirectTargets(properties(true));
    private static final String DEFAULT = "https://auth.limelightcreatives.org/account";

    @Test
    void ourSitesAreAllowed() {
        assertThat(this.targets.safe("https://limelightcreatives.org/")).isEqualTo("https://limelightcreatives.org/");
        assertThat(this.targets.safe("https://dashboard.limelightcreatives.org/staff?x=1"))
                .isEqualTo("https://dashboard.limelightcreatives.org/staff?x=1");
        assertThat(this.targets.safe("https://a.b.LimelightCreatives.org/")).isEqualTo("https://a.b.LimelightCreatives.org/");
    }

    @Test
    void pathsGoToTheDefaultSite() {
        assertThat(this.targets.safe("/participant")).isEqualTo("https://auth.limelightcreatives.org/participant");
    }

    @Test
    void everythingGoesToTheDefault() {
        for (String next : new String[]{null, "", "https://evil.example/", "https://evil-limelightcreatives.org/",
                "https://limelightcreatives.org.evil.example/", "//evil.example/", "http://dashboard.limelightcreatives.org/",
                "https://limelightcreatives.org@evil.example/", "https://user@dashboard.limelightcreatives.org/",
                "javascript:alert(1)", "data:text/html,hi", "https://exa mple/", "ftp://limelightcreatives.org/"})
            assertThat(this.targets.safe(next)).as(String.valueOf(next)).isEqualTo(DEFAULT);
    }

    @Test
    void plainHttpOnlyWhenTestingLocally() {
        RedirectTargets local = new RedirectTargets(properties(false));
        assertThat(local.safe("http://dashboard.limelightcreatives.org/")).isEqualTo("http://dashboard.limelightcreatives.org/");
    }
}
