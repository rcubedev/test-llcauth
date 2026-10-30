package org.limelightcreatives.auth.login;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.limelightcreatives.auth.AuthProperties;
import org.limelightcreatives.auth.DatabaseTest;
import org.limelightcreatives.auth.mail.LoginCodeMailer;
import org.limelightcreatives.auth.user.Users;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

/** When an email can't be sent: an error page, and the code only in the log when that's switched on. */
@ExtendWith(OutputCaptureExtension.class)
class SignInTest extends DatabaseTest {

    @Autowired Users users;
    @Autowired LoginCodes codes;
    @Autowired RateLimits rateLimits;
    @Autowired AuthProperties properties;

    private static final LoginCodeMailer REFUSES = (email, code) -> {
        throw new IllegalStateException("Resend refused the email (403 FORBIDDEN): testing addresses only");
    };

    private SignIn signIn(boolean logCodeIfUnsent) {
        var mail = new AuthProperties.Mail("", "from@example.com", logCodeIfUnsent);
        var withMail = new AuthProperties(properties.secret(), properties.baseUrl(), properties.rootDomain(),
                properties.cookieDomain(), properties.secureCookies(), properties.defaultRedirect(),
                properties.sessionLifetime(), mail);
        return new SignIn(users, codes, rateLimits, REFUSES, withMail);
    }

    @Test
    void locallyTheCodeIsLoggedWithAWarning(CapturedOutput log) {
        assertThat(signIn(true).sendCode("sam@example.com", "192.0.2.1")).isEqualTo(SignIn.SendResult.LOGGED_INSTEAD);
        assertThat(log.getOut()).contains("ERROR", "Couldn't email a login code to sam@example.com", "403 FORBIDDEN")
                .containsPattern("WARN .*here is the login code for sam@example.com: \\d{6}");
    }

    @Test
    void inProductionTheCodeIsNeverLogged(CapturedOutput log) {
        assertThat(signIn(false).sendCode("sam@example.com", "192.0.2.1")).isEqualTo(SignIn.SendResult.NOT_DELIVERED);
        assertThat(log.getOut()).contains("Couldn't email a login code").doesNotContain("here is the login code");
    }

    @Test
    void theLoggedCodeStillSignsYouIn(CapturedOutput log) {
        signIn(true).sendCode("sam@example.com", "192.0.2.1");
        var code = java.util.regex.Pattern.compile("login code for sam@example.com: (\\d{6})").matcher(log.getOut());
        assertThat(code.find()).isTrue();
        assertThat(signIn(true).verify("sam@example.com", code.group(1))).isPresent();
    }

    @Test
    void productionSettingsKeepItOff() {
        assertThat(properties.mail().logCodeIfUnsent()).isFalse(); // tests use application.yml's value
    }
}
