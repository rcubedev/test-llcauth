package org.limelightcreatives.auth.login;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.limelightcreatives.auth.DatabaseTest;
import org.limelightcreatives.auth.mail.LoginCodeMailer;
import org.limelightcreatives.auth.mail.MailProperties;
import org.limelightcreatives.auth.user.Users;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class SignInTest extends DatabaseTest {

    @Autowired
    Users users;

    @Autowired
    LoginCodes codes;

    @Autowired
    RateLimits rateLimits;

    @Autowired
    MailProperties mail;

    private static final LoginCodeMailer REFUSES = (_, _) -> {
        throw new IllegalStateException("Resend refused the email (403 FORBIDDEN): testing addresses only");
    };

    private SignIn signIn(boolean logCodeIfUnsent) {
        return new SignIn(this.users, this.codes, this.rateLimits, REFUSES, new MailProperties("", "from@example.com", logCodeIfUnsent, true));
    }

    @Autowired
    SignIn signIn;

    @Test
    void theFirstCodeCreatesTheAccount() {
        assertThat(this.signIn.sendCode(" New@Example.com", "192.0.2.1")).isEqualTo(SignIn.SendResult.SENT);
        assertThat(this.users.findByEmail("new@example.com")).isPresent();
        assertThat(this.signIn.verify("new@example.com", this.mailer.lastCodeFor("new@example.com"))).isPresent();
    }

    @Test
    void badAddressesAreRefused() {
        for (String email : new String[]{"", "nope", "a@b", "two@@example.com", "a b@example.com"})
            assertThat(this.signIn.sendCode(email, "192.168.0.1")).as(email).isEqualTo(SignIn.SendResult.INVALID_EMAIL);
        assertThat(this.db.sql("SELECT count(*) FROM \"users\"").query(Integer.class).single()).isZero();
    }

    @Test
    void fiveCodesAnHourPerEmail() {
        for (int i = 0; i < 5; i++)
            assertThat(this.signIn.sendCode("sam@example.com", "192.0.2." + i)).isEqualTo(SignIn.SendResult.SENT);
        assertThat(this.signIn.sendCode("sam@example.com", "192.0.2.99")).isEqualTo(SignIn.SendResult.TOO_MANY_FOR_EMAIL);
    }

    @Test
    void fifteenCodesAnHourPerIp() {
        for (int i = 0; i < 15; i++)
            assertThat(this.signIn.sendCode("p" + i + "@example.com", "192.0.2.1")).isEqualTo(SignIn.SendResult.SENT);
        assertThat(this.signIn.sendCode("another@example.com", "192.0.2.1")).isEqualTo(SignIn.SendResult.TOO_MANY_FOR_IP);
        assertThat(this.signIn.sendCode("another@example.com", "192.0.2.2")).isEqualTo(SignIn.SendResult.SENT);
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
        Matcher code = Pattern.compile("login code for sam@example.com: (\\d{6})").matcher(log.getOut());
        assertThat(code.find()).isTrue();
        assertThat(signIn(true).verify("sam@example.com", code.group(1))).isPresent();
    }

    @Test
    void productionSettingsKeepItOff() {
        assertThat(this.mail.logCodeIfUnsent()).isFalse();
    }
}
